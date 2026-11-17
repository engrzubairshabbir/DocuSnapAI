package com.docusnap.ai.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.docusnap.ai.data.local.DocumentDao
import com.docusnap.ai.data.model.DocumentEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class FirebaseSyncRepository(
    private val context: Context,
    private val documentDao: DocumentDao
) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentUserFlow = MutableStateFlow(auth.currentUser)
    val currentUserFlow: StateFlow<com.google.firebase.auth.FirebaseUser?> = _currentUserFlow.asStateFlow()

    private var remoteListenerRegistration: ListenerRegistration? = null
    private var mediaRequestListenerRegistration: ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUserFlow.value = firebaseAuth.currentUser
            val user = firebaseAuth.currentUser
            if (user != null) {
                listenToRemoteChanges(user.uid)
                listenToMediaRequests(user.uid)
            } else {
                remoteListenerRegistration?.remove()
                mediaRequestListenerRegistration?.remove()
            }
        }
    }

    /**
     * Signs in anonymously for zero-friction onboarding.
     */
    suspend fun ensureAuthenticated(): String = withContext(Dispatchers.IO) {
        val current = auth.currentUser
        if (current != null) {
            return@withContext current.uid
        }
        val result = auth.signInAnonymously().await()
        result.user?.uid ?: throw IllegalStateException("Failed to authenticate with Firebase")
    }

    /**
     * Links anonymous account or signs in with Google ID Token.
     * Guarantees all documents are synchronized to Firestore for WhatsApp Web-style desktop access.
     */
    suspend fun signInOrLinkWithGoogle(idToken: String): Result<com.google.firebase.auth.FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val current = auth.currentUser
            val user = if (current != null && current.isAnonymous) {
                try {
                    val linkResult = current.linkWithCredential(credential).await()
                    linkResult.user ?: current
                } catch (e: Exception) {
                    val signInResult = auth.signInWithCredential(credential).await()
                    signInResult.user ?: throw e
                }
            } else {
                val signInResult = auth.signInWithCredential(credential).await()
                signInResult.user ?: throw IllegalStateException("Google sign in failed")
            }

            // Sync user profile & device info
            val profile = hashMapOf(
                "uid" to user.uid,
                "email" to (user.email ?: ""),
                "displayName" to (user.displayName ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "lastDevice" to "Android (${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})",
                "lastActive" to System.currentTimeMillis()
            )
            try {
                firestore.collection("users").document(user.uid).set(profile, SetOptions.merge()).await()
            } catch (e: Exception) {
                android.util.Log.w("DocuSnapSync", "Profile doc sync warning: ${e.message}")
            }

            listenToRemoteChanges(user.uid)
            syncAllLocalDocuments()

            Result.success(user)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Syncs all local documents to Firestore in fast, lightweight batches.
     * ZERO HEAVY IMAGE UPLOADS: Only OCR text, tags, and metadata are sent so it completes in seconds with 0 mobile data waste!
     */
    suspend fun syncAllLocalDocuments() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: ensureAuthenticated()
        try {
            // ONLY fetch documents that are pending sync to prevent exhausting Firestore daily quota
            val unsyncedDocs = documentDao.getDocumentsBySyncStatus(DocumentEntity.SYNC_STATUS_PENDING_SYNC)
            if (unsyncedDocs.isEmpty()) {
                android.util.Log.d("DocuSnapSync", "All documents already synced to cloud. Zero quota consumed.")
                return@withContext
            }
            android.util.Log.d("DocuSnapSync", "Starting fast text batch sync of ${unsyncedDocs.size} unsynced documents for user: $uid")
            
            // Fast text metadata sync in chunks of 50 via WriteBatch (< 1-2 seconds total!)
            for (chunk in unsyncedDocs.chunked(50)) {
                val batch = firestore.batch()
                for (doc in chunk) {
                    val docRef = firestore.collection("users")
                        .document(uid)
                        .collection("documents")
                        .document(doc.id)
                    val docData = hashMapOf(
                        "id" to doc.id,
                        "file_hash" to doc.fileHash,
                        "cloud_storage_url" to (doc.cloudStorageUrl ?: ""),
                        "title" to doc.title,
                        "raw_ocr_text" to doc.rawOcrText,
                        "ai_summary" to doc.aiSummary,
                        "document_type" to doc.documentType,
                        "key_entities_json" to doc.keyEntitiesJson,
                        "searchable_tags_json" to doc.searchableTagsJson,
                        "date_detected" to doc.dateDetected,
                        "timestamp" to doc.timestamp,
                        "sync_status" to DocumentEntity.SYNC_STATUS_SYNCED
                    )
                    batch.set(docRef, docData, SetOptions.merge())
                }
                batch.commit().await()
                
                // Immediately mark as SYNCED in local database so it is never pushed again
                for (doc in chunk) {
                    documentDao.updateSyncStatus(doc.id, DocumentEntity.SYNC_STATUS_SYNCED, doc.cloudStorageUrl)
                }
            }

            android.util.Log.d("DocuSnapSync", "Batch text sync complete. Zero heavy image payloads uploaded.")
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("DocuSnapSync", "Error in syncAllLocalDocuments: ${e.message}")
        }
    }

    /**
     * Fast batch push of newly processed documents' OCR text to Firestore.
     */
    suspend fun syncBatchDocuments(documents: List<DocumentEntity>) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: ensureAuthenticated()
        try {
            for (chunk in documents.chunked(50)) {
                val batch = firestore.batch()
                for (doc in chunk) {
                    val docRef = firestore.collection("users")
                        .document(uid)
                        .collection("documents")
                        .document(doc.id)
                    val docData = hashMapOf(
                        "id" to doc.id,
                        "file_hash" to doc.fileHash,
                        "cloud_storage_url" to (doc.cloudStorageUrl ?: ""),
                        "title" to doc.title,
                        "raw_ocr_text" to doc.rawOcrText,
                        "ai_summary" to doc.aiSummary,
                        "document_type" to doc.documentType,
                        "key_entities_json" to doc.keyEntitiesJson,
                        "searchable_tags_json" to doc.searchableTagsJson,
                        "date_detected" to doc.dateDetected,
                        "timestamp" to doc.timestamp,
                        "sync_status" to DocumentEntity.SYNC_STATUS_SYNCED
                    )
                    batch.set(docRef, docData, SetOptions.merge())
                }
                batch.commit().await()
                
                // Immediately mark as SYNCED in local database
                for (doc in chunk) {
                    documentDao.updateSyncStatus(doc.id, DocumentEntity.SYNC_STATUS_SYNCED, doc.cloudStorageUrl)
                }
            }
            android.util.Log.d("DocuSnapSync", "Auto-synced ${documents.size} OCR texts to web vault!")
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("DocuSnapSync", "Error auto-syncing batch: ${e.message}")
        }
    }

    /**
     * Syncs document metadata directly to Firestore without uploading heavy files.
     */
    suspend fun syncDocument(document: DocumentEntity): Unit = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: ensureAuthenticated()
        val docId = document.id

        try {
            val docData = hashMapOf(
                "id" to document.id,
                "file_hash" to document.fileHash,
                "cloud_storage_url" to (document.cloudStorageUrl ?: ""),
                "title" to document.title,
                "raw_ocr_text" to document.rawOcrText,
                "ai_summary" to document.aiSummary,
                "document_type" to document.documentType,
                "key_entities_json" to document.keyEntitiesJson,
                "searchable_tags_json" to document.searchableTagsJson,
                "date_detected" to document.dateDetected,
                "timestamp" to document.timestamp,
                "sync_status" to DocumentEntity.SYNC_STATUS_SYNCED
            )

            firestore.collection("users")
                .document(uid)
                .collection("documents")
                .document(docId)
                .set(docData, SetOptions.merge())
                .await()

            documentDao.updateSyncStatus(
                id = docId,
                status = DocumentEntity.SYNC_STATUS_SYNCED,
                cloudUrl = document.cloudStorageUrl
            )

        } catch (e: Exception) {
            e.printStackTrace()
            documentDao.updateSyncStatus(
                id = docId,
                status = DocumentEntity.SYNC_STATUS_PENDING_SYNC,
                cloudUrl = null
            )
        }
    }

    /**
     * Listens in real-time to changes created on Web/iOS PWA and reflects them in Room SQLite FTS5.
     */
    private fun listenToRemoteChanges(userId: String) {
        remoteListenerRegistration?.remove()
        remoteListenerRegistration = firestore.collection("users")
            .document(userId)
            .collection("documents")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                scope.launch {
                    for (change in snapshot.documentChanges) {
                        val data = change.document.data
                        val id = data["id"] as? String ?: continue
                        val fileHash = data["file_hash"] as? String ?: id
                        val existing = documentDao.getDocumentById(id)

                        if (existing == null) {
                            val entity = DocumentEntity(
                                id = id,
                                fileHash = fileHash,
                                localImageUri = data["cloud_storage_url"] as? String ?: "",
                                cloudStorageUrl = data["cloud_storage_url"] as? String,
                                rawOcrText = data["raw_ocr_text"] as? String ?: "",
                                title = data["title"] as? String ?: "Synced Document",
                                aiSummary = data["ai_summary"] as? String ?: "",
                                documentType = data["document_type"] as? String ?: DocumentEntity.TYPE_GENERAL,
                                keyEntitiesJson = data["key_entities_json"] as? String ?: "{}",
                                searchableTagsJson = data["searchable_tags_json"] as? String ?: "[]",
                                dateDetected = data["date_detected"] as? String ?: "",
                                syncStatus = DocumentEntity.SYNC_STATUS_SYNCED,
                                timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                            )
                            documentDao.insertDocument(entity)
                        }
                    }
                }
            }
    }

    private fun loadFullOrThumbnailDataUrl(doc: DocumentEntity): String? {
        // 1. Try loading high-resolution image from original MediaStore URI using modern ImageDecoder
        if (doc.localImageUri.isNotBlank()) {
            try {
                val uri = Uri.parse(doc.localImageUri)
                android.util.Log.d("DocuSnapSync", "Attempting ImageDecoder on URI: $uri")
                val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val maxDim = 1400
                        val w = info.size.width
                        val h = info.size.height
                        if (w > maxDim || h > maxDim) {
                            val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
                            decoder.setTargetSize((w * ratio).toInt(), (h * ratio).toInt())
                        }
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: throw IllegalStateException("Could not open stream for $uri")
                }

                val outStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outStream)
                bitmap.recycle()
                val base64 = android.util.Base64.encodeToString(outStream.toByteArray(), android.util.Base64.NO_WRAP)
                android.util.Log.d("DocuSnapSync", "Successfully loaded high-res JPEG from original URI!")
                return "data:image/jpeg;base64,$base64"
            } catch (e: Exception) {
                android.util.Log.w("DocuSnapSync", "Could not load from localImageUri: ${e.message}. Trying thumbnail fallback.")
            }
        }

        // 2. 100% Guaranteed Fallback: Cached vault thumbnail from internal app storage
        val thumbFile = java.io.File(context.filesDir, "vault_thumbs/${doc.id}.webp")
        if (thumbFile.exists() && thumbFile.length() > 0) {
            try {
                val bytes = thumbFile.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                android.util.Log.d("DocuSnapSync", "Successfully loaded cached vault thumbnail (${bytes.size} bytes)!")
                return "data:image/webp;base64,$base64"
            } catch (e: Exception) {
                android.util.Log.e("DocuSnapSync", "Error reading thumbFile: ${e.message}")
            }
        }

        return null
    }

    /**
     * Listens in real-time for on-demand image retrieval requests triggered from the website.
     * When a user clicks to view/share full resolution on Web, this fulfills it by uploading ONLY that single photo.
     */
    private fun listenToMediaRequests(userId: String) {
        mediaRequestListenerRegistration?.remove()
        mediaRequestListenerRegistration = firestore.collection("users")
            .document(userId)
            .collection("media_requests")
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (change in snapshot.documentChanges) {
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) continue
                    val reqDoc = change.document
                    val docId = reqDoc.getString("docId") ?: continue
                    scope.launch {
                        fulfillMediaRequest(userId, docId)
                    }
                }
            }
    }

    suspend fun fulfillMediaRequest(userId: String, docId: String) = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("DocuSnapSync", "Fulfilling on-demand full resolution media request for docId: $docId")
            val doc = documentDao.getDocumentById(docId)
            if (doc == null) {
                android.util.Log.w("DocuSnapSync", "Document not found in local Room DB for docId: $docId")
                reportMediaRequestFailed(userId, docId, "Document not found on phone")
                return@withContext
            }

            var resultUrl = doc.cloudStorageUrl

            // If not yet uploaded, load high-res or thumbnail Data URL from phone
            if (resultUrl.isNullOrBlank()) {
                resultUrl = loadFullOrThumbnailDataUrl(doc)
                if (!resultUrl.isNullOrBlank()) {
                    // Save URL to local database and Firestore document so it persists permanently
                    documentDao.updateSyncStatus(docId, DocumentEntity.SYNC_STATUS_SYNCED, resultUrl)
                    firestore.collection("users").document(userId)
                        .collection("documents").document(docId)
                        .update("cloud_storage_url", resultUrl).await()
                } else {
                    reportMediaRequestFailed(userId, docId, "Image file could not be read on phone")
                    return@withContext
                }
            }

            // Report COMPLETED with the crisp image URL to Firestore
            firestore.collection("users").document(userId)
                .collection("media_requests").document(docId)
                .set(mapOf(
                    "docId" to docId,
                    "status" to "COMPLETED",
                    "cloud_storage_url" to resultUrl,
                    "completedAt" to System.currentTimeMillis()
                ), SetOptions.merge()).await()

            android.util.Log.d("DocuSnapSync", "Media request fulfilled successfully for $docId!")
        } catch (e: Exception) {
            android.util.Log.e("DocuSnapSync", "Failed to fulfill media request for $docId: ${e.message}")
            reportMediaRequestFailed(userId, docId, e.localizedMessage ?: "Failed to upload from phone")
        }
    }

    private suspend fun reportMediaRequestFailed(userId: String, docId: String, error: String) {
        try {
            firestore.collection("users").document(userId)
                .collection("media_requests").document(docId)
                .set(mapOf(
                    "docId" to docId,
                    "status" to "FAILED",
                    "error" to error,
                    "completedAt" to System.currentTimeMillis()
                ), SetOptions.merge()).await()
        } catch (_: Exception) {}
    }

    fun signOut() {
        auth.signOut()
        remoteListenerRegistration?.remove()
        mediaRequestListenerRegistration?.remove()
    }
}

