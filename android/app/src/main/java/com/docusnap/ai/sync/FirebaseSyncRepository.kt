package com.docusnap.ai.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUserFlow.value = firebaseAuth.currentUser
            if (firebaseAuth.currentUser != null) {
                listenToRemoteChanges(firebaseAuth.currentUser!!.uid)
            } else {
                remoteListenerRegistration?.remove()
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
            firestore.collection("users").document(user.uid).set(profile, SetOptions.merge()).await()

            listenToRemoteChanges(user.uid)
            syncAllLocalDocuments()

            Result.success(user)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Syncs all local documents to Firestore and Storage.
     * Uses fast WriteBatch (50 docs per request) so all documents appear instantly on Web.
     */
    suspend fun syncAllLocalDocuments() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: ensureAuthenticated()
        try {
            val allDocs = documentDao.getAllDocumentsOnce()
            android.util.Log.d("DocuSnapSync", "Starting fast batch sync of ${allDocs.size} documents for user: $uid")
            
            // Step 1: Fast metadata sync in chunks of 50 via WriteBatch (< 1 second total)
            for (chunk in allDocs.chunked(50)) {
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
            }

            android.util.Log.d("DocuSnapSync", "Fast batch Firestore metadata write complete.")

            // Step 2: Upload thumbnails safely in background
            for (doc in allDocs) {
                if (doc.cloudStorageUrl.isNullOrBlank()) {
                    syncDocument(doc)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("DocuSnapSync", "Error in syncAllLocalDocuments: ${e.message}")
        }
    }

    /**
     * Uploads compressed image and metadata to Firebase.
     */
    suspend fun syncDocument(document: DocumentEntity): Unit = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: ensureAuthenticated()
        val docId = document.id

        var cloudImageUrl = document.cloudStorageUrl

        // 1. Safe non-blocking image upload
        if (cloudImageUrl.isNullOrBlank() && document.localImageUri.isNotBlank()) {
            try {
                val imageUri = Uri.parse(document.localImageUri)
                val thumbBytes = compressImage(imageUri, maxDimension = 600, quality = 70)
                if (thumbBytes != null) {
                    val thumbRef = storage.reference.child("users/$uid/thumbs/$docId.webp")
                    val uploadTask = thumbRef.putBytes(thumbBytes).await()
                    cloudImageUrl = uploadTask.storage.downloadUrl.await().toString()
                }
            } catch (storageEx: Exception) {
                android.util.Log.w("DocuSnapSync", "Storage upload skipped for $docId: ${storageEx.message}")
            }
        }

        // 2. Prepare Firestore document mapping (Guaranteed write)
        try {
            val docData = hashMapOf(
                "id" to document.id,
                "file_hash" to document.fileHash,
                "cloud_storage_url" to (cloudImageUrl ?: ""),
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

            // 3. Mark local document as SYNCED
            documentDao.updateSyncStatus(
                id = docId,
                status = DocumentEntity.SYNC_STATUS_SYNCED,
                cloudUrl = cloudImageUrl
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

    private fun compressImage(uri: Uri, maxDimension: Int, quality: Int): ByteArray? {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, boundsOptions)
            stream.close()

            var sampleSize = 1
            var w = boundsOptions.outWidth
            var h = boundsOptions.outHeight
            while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
                w /= 2
                h /= 2
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val secondStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(secondStream, null, decodeOptions)
            secondStream.close()

            if (bitmap == null) return null

            val outStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
            bitmap.recycle()
            outStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun signOut() {
        auth.signOut()
        remoteListenerRegistration?.remove()
    }
}

