package com.docusnap.ai.service

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.work.*
import com.docusnap.ai.DocuSnapApplication
import com.docusnap.ai.ai.AiBackendClient
import com.docusnap.ai.ai.GeminiReasoningEngine
import com.docusnap.ai.billing.BillingManager
import com.docusnap.ai.data.model.DocumentEntity
import com.docusnap.ai.data.model.GeminiExtractionResult
import com.docusnap.ai.data.model.KeyEntities
import com.docusnap.ai.i18n.AppLanguage
import com.docusnap.ai.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

class MediaIngestionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val ocrEngine = OcrEngine(appContext)
    private val geminiEngine = GeminiReasoningEngine()
    private val docDao = (appContext as DocuSnapApplication).database.documentDao()
    private val syncRepo = (appContext as DocuSnapApplication).syncRepository

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val inputUriString = inputData.getString(KEY_IMAGE_URI)
        val inputUrisArray = inputData.getStringArray(KEY_IMAGE_URIS_ARRAY)
        val scanLimit = inputData.getInt(KEY_SCAN_LIMIT, 50)
        val isCameraCapture = inputData.getBoolean(KEY_IS_CAMERA_CAPTURE, false)
        val urisToProcess = mutableListOf<Uri>()

        if (!inputUrisArray.isNullOrEmpty()) {
            urisToProcess.addAll(inputUrisArray.map { Uri.parse(it) })
        } else if (!inputUriString.isNullOrBlank()) {
            val parsed = Uri.parse(inputUriString)
            if (isMediaStoreCollectionUri(parsed)) {
                urisToProcess.addAll(fetchLatestGalleryUris(limit = scanLimit))
            } else {
                urisToProcess.add(parsed)
            }
        } else {
            // Find recent gallery photos and screenshots
            urisToProcess.addAll(fetchLatestGalleryUris(limit = scanLimit))
        }

        Log.d("DocuSnapIngestion", "Starting ingestion for ${urisToProcess.size} image(s) (camera=$isCameraCapture)...")

        var processedCount = 0
        for (uri in urisToProcess) {
            try {
                val success = processSingleImage(uri, isCameraCapture = isCameraCapture)
                if (success) processedCount++
            } catch (e: Exception) {
                Log.e("DocuSnapIngestion", "Failed processing uri: $uri", e)
            }
        }

        // Re-arm OS ContentUriTrigger for future screenshots and camera captures (Android 8-16)
        MediaObserverService.scheduleContentUriTrigger(
            applicationContext,
            rearmFromRunningTrigger = tags.contains(MediaObserverService.TAG_CONTENT_TRIGGER)
        )

        Log.d("DocuSnapIngestion", "Ingestion completed. Processed $processedCount new document(s).")
        Result.success()
    }

    private fun isMediaStoreCollectionUri(uri: Uri): Boolean {
        val str = uri.toString()
        return uri == MediaStore.Images.Media.EXTERNAL_CONTENT_URI ||
            (uri.authority == MediaStore.AUTHORITY && uri.lastPathSegment?.toLongOrNull() == null) ||
            str.endsWith("/images/media")
    }

    private fun isScreenshotUri(uri: Uri): Boolean {
        val rawPath = uri.toString().lowercase(Locale.ROOT)
        if (rawPath.contains("screenshot")) return true
        if (uri.scheme != "content") return false

        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
        } else {
            @Suppress("DEPRECATION")
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
        }

        return try {
            applicationContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    for (i in 0 until cursor.columnCount) {
                        val value = cursor.getString(i)?.lowercase(Locale.ROOT) ?: ""
                        if (value.contains("screenshot") || value.contains("screen_shot") || value.contains("screencapture")) {
                            return@use true
                        }
                    }
                }
                false
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun processSingleImage(uri: Uri, isCameraCapture: Boolean): Boolean {
        val contentResolver = applicationContext.contentResolver

        // 1. Inspect image dimensions to filter out tiny icons and stickers
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            Log.w("DocuSnapIngestion", "Cannot read bounds for $uri: ${e.message}")
            return false
        }

        val width = options.outWidth
        val height = options.outHeight

        // Filter: Discard very tiny icons or thumbnails (< 150px)
        if (width < 150 || height < 150) {
            Log.d("DocuSnapIngestion", "Skipping $uri: too small ($width x $height)")
            return false
        }

        // 2. Compute SHA-256 file hash to strictly prevent duplicate processing
        val fileHash = computeSha256(uri) ?: return false
        val existing = docDao.getDocumentByHash(fileHash)
        if (existing != null) {
            // Self-healing: if existing document image file is missing or temporary content URI expired, restore it!
            val existingFile = if (existing.localImageUri.startsWith("file://")) {
                File(Uri.parse(existing.localImageUri).path ?: "")
            } else null
            if (existingFile == null || !existingFile.exists()) {
                val restoredUri = saveImagePermanently(uri, existing.id)
                docDao.updateDocument(existing.copy(localImageUri = restoredUri))
                Log.d("DocuSnapIngestion", "Restored missing local image for existing doc: ${existing.id}")
            }
            return false // Already processed
        }

        // 3. Check Subscription & Tier limits (Free: 500, Silver: 2,000, Gold: 5,000, etc.)
        val totalDocs = docDao.getTotalCount()
        val maxDocs = BillingManager.getMaxAllowedDocuments(applicationContext)
        if (totalDocs >= maxDocs) {
            Log.w("DocuSnapBilling", "Limit reached ($totalDocs / $maxDocs). Upgrade subscription to add more.")
            return false
        }

        // 4. Run Google ML Kit Text Recognition (bundled, 100% on-device)
        val isScreenshot = isScreenshotUri(uri)
        var rawOcrText = ocrEngine.recognizeText(uri)

        val billingManager = BillingManager.getInstance(applicationContext)
        val has5xBalance = billingManager.hasSufficient5xBalance()
        val uiPrefs = applicationContext.getSharedPreferences("docusnap_ui_prefs", Context.MODE_PRIVATE)
        val targetLang = AppLanguage.fromCode(uiPrefs.getString("selected_lang", AppLanguage.ENGLISH.code) ?: "en")

        // Never drop in-app camera captures or OS screenshots (even if text is non-Latin like Japanese/Arabic/Korean)
        if (!isCameraCapture && !isScreenshot && (rawOcrText.isBlank() || rawOcrText.trim().length < 4)) {
            Log.d("DocuSnapIngestion", "Skipping $uri: no text detected and not screenshot/camera (len: ${rawOcrText.length})")
            return false
        }

        Log.d("DocuSnapIngestion", "Processing $uri (screenshot=$isScreenshot, camera=$isCameraCapture, localOcrLen=${rawOcrText.length})")

        // 5. Semantic & Multilingual Vision Extraction
        // If user has pre-loaded One-Time Wallet balance (>= $0.05) and this is a camera/screenshot capture
        // or needs multilingual Vision OCR, invoke Secure Backend Gemini Vision (5x rate = $0.05 deducted from wallet).
        var semanticResult: GeminiExtractionResult? = null
        if (has5xBalance && (isCameraCapture || isScreenshot || rawOcrText.trim().length < 8)) {
            val backendResult = AiBackendClient.visionExtract5x(
                context = applicationContext,
                sourceUri = uri,
                targetLanguage = targetLang,
                existingOcrText = rawOcrText
            )
            if (backendResult.extraction != null) {
                semanticResult = backendResult.extraction
                if (backendResult.rawOcr.isNotBlank()) {
                    rawOcrText = backendResult.rawOcr
                }
            }
        }

        if (semanticResult == null) {
            semanticResult = if (rawOcrText.trim().length >= 4) {
                geminiEngine.analyzeDocument(rawOcrText)
            } else {
                val todayLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val prefixTitle = if (isScreenshot) "Screenshot Capture ($todayLabel)" else "Camera Scan ($todayLabel)"
                GeminiExtractionResult(
                    documentType = DocumentEntity.TYPE_GENERAL,
                    title = prefixTitle,
                    summary = if (isScreenshot) {
                        "Auto-saved screenshot copied into Smart Vault. Top up One-Time AI Balance for 5x global language Vision OCR."
                    } else {
                        "In-app camera capture stored in Smart Vault."
                    },
                    keyEntities = KeyEntities(date = todayLabel.substring(0, 10)),
                    searchableTags = if (isScreenshot) listOf("screenshot", "vault") else listOf("camera-scan", "vault")
                )
            }
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dateDetected = if (semanticResult.keyEntities.date.isNotBlank()) {
            semanticResult.keyEntities.date
        } else {
            today
        }

        // 6. Save permanent full-resolution local copy to internal app filesDir
        val docId = UUID.randomUUID().toString()
        val permanentLocalUri = saveImagePermanently(uri, docId)

        val mergedTags = LinkedHashSet<String>().apply {
            addAll(semanticResult.searchableTags)
            if (isScreenshot) add("screenshot")
            if (isCameraCapture) add("camera-scan")
        }
        val tagsJson = org.json.JSONArray().apply {
            mergedTags.forEach { put(it) }
        }.toString()

        val documentEntity = DocumentEntity(
            id = docId,
            fileHash = fileHash,
            localImageUri = permanentLocalUri,
            cloudStorageUrl = null,
            rawOcrText = rawOcrText,
            title = semanticResult.title,
            aiSummary = semanticResult.summary,
            documentType = semanticResult.documentType,
            keyEntitiesJson = semanticResult.keyEntities.toJson(),
            searchableTagsJson = tagsJson,
            dateDetected = dateDetected,
            syncStatus = DocumentEntity.SYNC_STATUS_PENDING_SYNC,
            timestamp = System.currentTimeMillis()
        )

        docDao.insertDocument(documentEntity)
        Log.d("DocuSnapIngestion", "Successfully saved document: ${documentEntity.title} (${documentEntity.documentType})")

        // 7. Push to Firebase Sync if network is available
        try {
            syncRepo.syncDocument(documentEntity)
        } catch (e: Exception) {
            Log.w("DocuSnapIngestion", "Firebase sync deferred: ${e.message}")
        }

        return true
    }

    private fun resolveOriginalExtension(sourceUri: Uri): String {
        val mimeType = applicationContext.contentResolver.getType(sourceUri)
        if (!mimeType.isNullOrBlank()) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (!ext.isNullOrBlank()) return ext.lowercase(Locale.ROOT)
        }
        val path = sourceUri.lastPathSegment ?: ""
        val dotIndex = path.lastIndexOf('.')
        if (dotIndex != -1 && dotIndex < path.length - 1) {
            val ext = path.substring(dotIndex + 1).lowercase(Locale.ROOT)
            if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) return ext
        }
        return "jpg"
    }

    private fun saveImagePermanently(sourceUri: Uri, docId: String): String {
        return try {
            val imagesDir = File(applicationContext.filesDir, "vault_images").apply { mkdirs() }
            val thumbsDir = File(applicationContext.filesDir, "vault_thumbs").apply { mkdirs() }

            val originalExt = resolveOriginalExtension(sourceUri)
            val imageFile = File(imagesDir, "$docId.$originalExt")
            val thumbFile = File(thumbsDir, "$docId.webp")

            // 1. Copy exact raw bytes to preserve 100% FULL RESOLUTION & ORIGINAL FORMAT
            var copiedBytes = 0L
            applicationContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(imageFile).use { output ->
                    copiedBytes = input.copyTo(output)
                }
            }

            if (copiedBytes <= 0L || !imageFile.exists()) {
                return sourceUri.toString()
            }

            // 2. Generate lightweight 400px thumbnail for ultra-fast vault grid scrolling
            try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imageFile.absolutePath, boundsOpts)
                var sampleSize = 1
                while (boundsOpts.outWidth / sampleSize > 800 || boundsOpts.outHeight / sampleSize > 800) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val sampledBitmap = BitmapFactory.decodeFile(imageFile.absolutePath, decodeOpts)
                if (sampledBitmap != null) {
                    val scaledThumb = scaleDownBitmap(sampledBitmap, 400f)
                    FileOutputStream(thumbFile).use { out ->
                        scaledThumb.compress(Bitmap.CompressFormat.WEBP, 75, out)
                    }
                    if (scaledThumb != sampledBitmap) scaledThumb.recycle()
                    sampledBitmap.recycle()
                }
            } catch (thumbErr: Exception) {
                Log.w("DocuSnapIngestion", "Thumbnail generation skipped for $docId: ${thumbErr.message}")
            }

            Uri.fromFile(imageFile).toString()
        } catch (e: Exception) {
            Log.e("DocuSnapIngestion", "Failed to save full-res image permanently for doc $docId", e)
            sourceUri.toString()
        }
    }

    private fun scaleDownBitmap(realImage: Bitmap, maxImageSize: Float): Bitmap {
        val ratio = min(maxImageSize / realImage.width, maxImageSize / realImage.height)
        if (ratio >= 1.0f) return realImage
        val width = (ratio * realImage.width).roundToInt()
        val height = (ratio * realImage.height).roundToInt()
        return Bitmap.createScaledBitmap(realImage, width, height, true)
    }

    private fun fetchLatestGalleryUris(limit: Int): List<Uri> {
        val result = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            val cursor: Cursor? = applicationContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var count = 0
                while (it.moveToNext() && count < limit) {
                    val id = it.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    result.add(contentUri)
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e("DocuSnapIngestion", "Error querying MediaStore", e)
        }
        return result
    }

    private fun computeSha256(uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e("DocuSnapIngestion", "SHA-256 computation failed for $uri", e)
            null
        }
    }

    companion object {
        const val KEY_IMAGE_URI = "key_image_uri"
        const val KEY_IMAGE_URIS_ARRAY = "key_image_uris_array"
        const val KEY_SCAN_LIMIT = "key_scan_limit"
        const val KEY_IS_CAMERA_CAPTURE = "key_is_camera_capture"
        const val UNIQUE_WORK_NAME = "DocuSnapMediaIngestionWork"

        fun enqueueScan(
            context: Context,
            uris: List<Uri>? = null,
            scanRecentLimit: Int = 50,
            isCameraCapture: Boolean = false
        ) {
            val workManager = WorkManager.getInstance(context)
            val dataBuilder = Data.Builder()
                .putBoolean(KEY_IS_CAMERA_CAPTURE, isCameraCapture)

            if (!uris.isNullOrEmpty()) {
                dataBuilder.putStringArray(KEY_IMAGE_URIS_ARRAY, uris.map { it.toString() }.toTypedArray())
            } else {
                dataBuilder.putInt(KEY_SCAN_LIMIT, scanRecentLimit)
            }

            val workRequest = OneTimeWorkRequestBuilder<MediaIngestionWorker>()
                .setInputData(dataBuilder.build())
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest
            )
        }
    }
}

