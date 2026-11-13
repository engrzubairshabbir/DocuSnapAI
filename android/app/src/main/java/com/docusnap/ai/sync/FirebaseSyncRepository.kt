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
     */
    suspend fun syncAllLocalDocuments() = withContext(Dispatchers.IO) {
        try {
            val allDocs = documentDao.getAllDocumentsOnce()
            for (doc in allDocs) {
                syncDocument(doc)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Uploads compressed image and metadata to Firebase.
     */
    suspend fun syncDocument(document: DocumentEntity): Unit = withContext(Dispatchers.IO) {
        val uid = ensureAuthenticated()
        val docId = document.id

        try {
            var cloudImageUrl = document.cloudStorageUrl

            // 1. Upload image to Firebase Storage if not already uploaded
            if (cloudImageUrl.isNullOrBlank() && document.localImageUri.isNotBlank()) {
                val imageUri = Uri.parse(document.localImageUri)
                val fullImageBytes = compressImage(imageUri, maxDimension = 1600, quality = 82)
                val thumbBytes = compressImage(imageUri, maxDimension = 400, quality = 70)

                val imageRef = storage.reference.child("users/$uid/images/$docId.jpg")
                val thumbRef = storage.reference.child("users/$uid/thumbs/$docId.webp")

                if (fullImageBytes != null) {
                    val uploadTask = imageRef.putBytes(fullImageBytes).await()
                    cloudImageUrl = uploadTask.storage.downloadUrl.await().toString()
                }

                if (thumbBytes != null) {
                    thumbRef.putBytes(thumbBytes).await()
                }
            }

            // 2. Prepare Firestore document mapping
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

