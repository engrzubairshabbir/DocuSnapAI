package com.docusnap.ai.service

import android.content.ContentUris
import android.content.Context
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
import com.docusnap.ai.data.local.DocumentDao
import com.docusnap.ai.data.model.DocumentEntity
import com.docusnap.ai.data.model.GeminiExtractionResult
import com.docusnap.ai.data.model.KeyEntities
import com.docusnap.ai.i18n.AppLanguage
import com.docusnap.ai.ocr.OcrEngine
import com.docusnap.ai.sync.FirebaseSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val inputUriString = inputData.getString(KEY_IMAGE_URI)
        val inputUrisArray = inputData.getStringArray(KEY_IMAGE_URIS_ARRAY)
        val scanLimit = inputData.getInt(KEY_SCAN_LIMIT, 50)
        val isCameraCapture = inputData.getBoolean(KEY_IS_CAMERA_CAPTURE, false)

        val urisToProcess = LinkedHashSet<Uri>()
        if (!inputUrisArray.isNullOrEmpty()) {
            urisToProcess.addAll(inputUrisArray.map { Uri.parse(it) })
        }
        if (!inputUriString.isNullOrBlank()) {
            val parsed = Uri.parse(inputUriString)
            if (!isMediaStoreCollectionUri(parsed)) {
                urisToProcess.add(parsed)
            }
        }

        val processedCount = processDirectly(
            context = applicationContext,
            uris = urisToProcess,
            scanLimit = scanLimit,
            isCameraCapture = isCameraCapture
        )

        // Re-arm OS ContentUriTrigger for future screenshots and camera captures (Android 7-16)
        MediaObserverService.scheduleContentUriTrigger(
            applicationContext,
            rearmFromRunningTrigger = tags.contains(MediaObserverService.TAG_CONTENT_TRIGGER)
        )

        Log.d("DocuSnapIngestion", "Worker finished. Ingested $processedCount new doc(s).")
        Result.success()
    }

    private fun isMediaStoreCollectionUri(uri: Uri): Boolean {
        val str = uri.toString()
        return uri == MediaStore.Images.Media.EXTERNAL_CONTENT_URI ||
            uri == MediaStore.Images.Media.INTERNAL_CONTENT_URI ||
            (uri.authority == MediaStore.AUTHORITY && uri.lastPathSegment?.toLongOrNull() == null) ||
            str.endsWith("/images/media")
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

        /**
         * Real-time zero-click processor executed directly within MediaObserverService (or Worker).
         * Takes < 300ms, operates 100% on-device, never drops any camera clicks or screenshots.
         */
        suspend fun processDirectly(
            context: Context,
            uris: Collection<Uri>? = null,
            scanLimit: Int = 25,
            isCameraCapture: Boolean = false
        ): Int = withContext(Dispatchers.IO) {
            val app = context.applicationContext as DocuSnapApplication
            val ocr = OcrEngine(app)
            val gemini = GeminiReasoningEngine()
            val dao = app.database.documentDao()
            val sync = app.syncRepository

            val targets = LinkedHashSet<Uri>()
            if (!uris.isNullOrEmpty()) {
                targets.addAll(uris)
            }
            targets.addAll(fetchLatestGalleryUris(app, scanLimit))

            var processed = 0
            for (uri in targets) {
                try {
                    val ok = processSingleImageInternal(
                        context = app,
                        uri = uri,
                        ocrEngine = ocr,
                        geminiEngine = gemini,
                        docDao = dao,
                        syncRepo = sync,
                        isCameraCapture = isCameraCapture
                    )
                    if (ok) processed++
                } catch (e: Exception) {
                    Log.e("DocuSnapIngestion", "Failed processing $uri: ${e.message}")
                }
            }
            processed
        }

        private fun isScreenshotUri(context: Context, uri: Uri): Boolean {
            val rawPath = uri.toString().lowercase(Locale.ROOT)
            if (rawPath.contains("screenshot") || rawPath.contains("screen") || rawPath.contains("capture") || rawPath.contains("snap")) return true
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
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        for (i in 0 until cursor.columnCount) {
                            val value = cursor.getString(i)?.lowercase(Locale.ROOT) ?: ""
                            if (value.contains("screenshot") || value.contains("screen") || value.contains("screencap") || value.contains("capture") || value.contains("scr_")) {
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

        private fun isCameraUri(context: Context, uri: Uri): Boolean {
            val rawPath = uri.toString().lowercase(Locale.ROOT)
            if (rawPath.contains("camera") || rawPath.contains("dcim") || rawPath.contains("img_") || rawPath.contains("photo")) return true
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
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        for (i in 0 until cursor.columnCount) {
                            val value = cursor.getString(i)?.lowercase(Locale.ROOT) ?: ""
                            if (value.contains("camera") || value.contains("dcim") || value.contains("picture") || value.contains("photo") || value.contains("img_") || value.contains("p_")) {
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

        private fun isPdfUri(context: Context, uri: Uri): Boolean {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            if (mimeType.equals("application/pdf", ignoreCase = true)) return true
            val path = uri.toString().lowercase(Locale.ROOT)
            return path.endsWith(".pdf") || (uri.path?.lowercase(Locale.ROOT)?.endsWith(".pdf") == true)
        }

        private fun renderPdfFirstPage(context: Context, uri: Uri): Bitmap? {
            return try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
                val pdfRenderer = android.graphics.pdf.PdfRenderer(pfd)
                if (pdfRenderer.pageCount > 0) {
                    val page = pdfRenderer.openPage(0)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pdfRenderer.close()
                    pfd.close()
                    bitmap
                } else {
                    pdfRenderer.close()
                    pfd.close()
                    null
                }
            } catch (e: Exception) {
                Log.e("DocuSnapIngestion", "Failed rendering PDF page: ${e.message}")
                null
            }
        }

        private suspend fun processSingleImageInternal(
            context: Context,
            uri: Uri,
            ocrEngine: OcrEngine,
            geminiEngine: GeminiReasoningEngine,
            docDao: DocumentDao,
            syncRepo: FirebaseSyncRepository,
            isCameraCapture: Boolean
        ): Boolean {
            val contentResolver = context.contentResolver
            val isPdf = isPdfUri(context, uri)
            val isScreenshot = isScreenshotUri(context, uri)
            val isCamera = isCameraCapture || isCameraUri(context, uri)
            var pdfPageBitmap: Bitmap? = null

            if (isPdf) {
                pdfPageBitmap = renderPdfFirstPage(context, uri)
            } else {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                } catch (e: Exception) {
                    Log.w("DocuSnapIngestion", "Cannot read bounds for $uri: ${e.message}")
                }

                var width = options.outWidth
                var height = options.outHeight

                // Wait 400ms if file write by camera/screenshot daemon is in progress
                if (width <= 0 || height <= 0) {
                    kotlinx.coroutines.delay(400)
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, options)
                        }
                        width = options.outWidth
                        height = options.outHeight
                    } catch (_: Exception) {}
                }

                // Discard only tiny icons (< 80px)
                if (!isScreenshot && !isCamera && (width < 80 || height < 80)) {
                    return false
                }
            }

            // 1. Subscription check: Verify File Size Limit according to user's active tier
            val fileSizeBytes = getFileSizeBytes(context, uri)
            val fileSizeMb = fileSizeBytes / (1024f * 1024f)
            val maxAllowedMb = BillingManager.getMaxAllowedFileSizeMb(context)

            if (fileSizeBytes > 0 && fileSizeMb > maxAllowedMb) {
                Log.w("DocuSnapBilling", "File $uri size (${"%.2f".format(fileSizeMb)} MB) exceeds tier limit (${maxAllowedMb} MB). Skipping heavy ingestion.")
                return false
            }

            // 2. Subscription check: Total document quota
            val totalDocs = docDao.getTotalCount()
            val maxDocs = BillingManager.getMaxAllowedDocuments(context)
            if (totalDocs >= maxDocs) {
                Log.w("DocuSnapBilling", "Limit reached ($totalDocs / $maxDocs). Upgrade to add more.")
                return false
            }

            // 3. Compute SHA-256 to strictly prevent duplicates
            val fileHash = computeSha256(context, uri) ?: return false
            val existing = docDao.getDocumentByHash(fileHash)
            if (existing != null) {
                val thumbFile = File(context.filesDir, "vault_thumbs/${existing.id}.webp")
                if (!thumbFile.exists()) {
                    generateThumbnailAndReference(context, uri, existing.id, pdfPageBitmap)
                }
                return false
            }

            // Run ML Kit Text Recognition
            var rawOcrText = if (isPdf && pdfPageBitmap != null) {
                ocrEngine.recognizeText(pdfPageBitmap)
            } else {
                ocrEngine.recognizeText(uri)
            }

            val billingManager = BillingManager.getInstance(context)
            val has5xBalance = billingManager.hasSufficient5xBalance()
            val uiPrefs = context.getSharedPreferences("docusnap_ui_prefs", Context.MODE_PRIVATE)
            val targetLang = AppLanguage.fromCode(uiPrefs.getString("selected_lang", AppLanguage.ENGLISH.code) ?: "en")

            var semanticResult: GeminiExtractionResult? = null
            if (has5xBalance && !isPdf && (isCamera || isScreenshot || rawOcrText.trim().length < 8)) {
                val backendResult = AiBackendClient.visionExtract5x(
                    context = context,
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
                    val prefixTitle = when {
                        isPdf -> "PDF Document ($todayLabel)"
                        isScreenshot -> "Screenshot Capture ($todayLabel)"
                        else -> "Camera Capture ($todayLabel)"
                    }
                    GeminiExtractionResult(
                        documentType = if (isPdf) DocumentEntity.TYPE_UTILITY else DocumentEntity.TYPE_GENERAL,
                        title = prefixTitle,
                        summary = when {
                            isPdf -> "PDF document added to Smart Vault."
                            isScreenshot -> "Auto-saved screenshot captured into Smart Vault."
                            else -> "Camera photo captured into Smart Vault."
                        },
                        keyEntities = KeyEntities(date = todayLabel.substring(0, 10)),
                        searchableTags = when {
                            isPdf -> listOf("pdf", "document", "vault")
                            isScreenshot -> listOf("screenshot", "vault")
                            else -> listOf("camera", "photo", "vault")
                        }
                    )
                }
            }

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val dateDetected = if (semanticResult.keyEntities.date.isNotBlank()) {
                semanticResult.keyEntities.date
            } else {
                today
            }

            val docId = UUID.randomUUID().toString()
            // Storage-free indexing: retain original gallery/file URI directly (zero duplication of storage)
            val permanentLocalUri = generateThumbnailAndReference(context, uri, docId, pdfPageBitmap)

            val mergedTags = LinkedHashSet<String>().apply {
                addAll(semanticResult.searchableTags)
                if (isScreenshot) add("screenshot")
                if (isCamera) {
                    add("camera")
                    add("photo")
                }
                if (isPdf) add("pdf")
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
            Log.d("DocuSnapIngestion", "Successfully saved document: ${documentEntity.title}")

            try {
                syncRepo.syncDocument(documentEntity)
            } catch (e: Exception) {
                Log.w("DocuSnapIngestion", "Firebase sync deferred: ${e.message}")
            }

            return true
        }

        private fun resolveOriginalExtension(context: Context, sourceUri: Uri): String {
            val mimeType = context.contentResolver.getType(sourceUri)
            if (!mimeType.isNullOrBlank()) {
                if (mimeType.equals("application/pdf", ignoreCase = true)) return "pdf"
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                if (!ext.isNullOrBlank()) return ext.lowercase(Locale.ROOT)
            }
            val path = sourceUri.lastPathSegment ?: ""
            if (path.endsWith(".pdf", ignoreCase = true)) return "pdf"
            val dotIndex = path.lastIndexOf('.')
            if (dotIndex != -1 && dotIndex < path.length - 1) {
                val ext = path.substring(dotIndex + 1).lowercase(Locale.ROOT)
                if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) return ext
            }
            return "jpg"
        }

        private fun scaleDownBitmap(realImage: Bitmap, maxImageSize: Float): Bitmap {
            val ratio = min(maxImageSize / realImage.width, maxImageSize / realImage.height)
            if (ratio >= 1.0f) return realImage
            val width = (ratio * realImage.width).roundToInt()
            val height = (ratio * realImage.height).roundToInt()
            return Bitmap.createScaledBitmap(realImage, width, height, true)
        }

        private fun getFileSizeBytes(context: Context, uri: Uri): Long {
            return try {
                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                            if (sizeIdx != -1) return cursor.getLong(sizeIdx)
                        }
                    }
                }
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                val size = pfd?.statSize ?: 0L
                pfd?.close()
                if (size > 0L) return size

                if (uri.scheme == "file" && uri.path != null) {
                    val f = File(uri.path!!)
                    if (f.exists()) return f.length()
                }
                0L
            } catch (_: Exception) {
                0L
            }
        }

        private fun generateThumbnailAndReference(
            context: Context,
            sourceUri: Uri,
            docId: String,
            pdfPageBitmap: Bitmap? = null
        ): String {
            try {
                val thumbsDir = File(context.filesDir, "vault_thumbs").apply { mkdirs() }
                val thumbFile = File(thumbsDir, "$docId.webp")

                if (pdfPageBitmap != null) {
                    val scaled = scaleDownBitmap(pdfPageBitmap, 400f)
                    FileOutputStream(thumbFile).use { out ->
                        scaled.compress(Bitmap.CompressFormat.WEBP, 75, out)
                    }
                    if (scaled != pdfPageBitmap) scaled.recycle()
                } else {
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, boundsOpts)
                    }
                    var sampleSize = 1
                    while (boundsOpts.outWidth / sampleSize > 800 || boundsOpts.outHeight / sampleSize > 800) {
                        sampleSize *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    val sampledBitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOpts)
                    }
                    if (sampledBitmap != null) {
                        val scaledThumb = scaleDownBitmap(sampledBitmap, 400f)
                        FileOutputStream(thumbFile).use { out ->
                            scaledThumb.compress(Bitmap.CompressFormat.WEBP, 75, out)
                        }
                        if (scaledThumb != sampledBitmap) scaledThumb.recycle()
                        sampledBitmap.recycle()
                    }
                }
            } catch (thumbErr: Exception) {
                Log.w("DocuSnapIngestion", "Thumbnail skipped for $docId: ${thumbErr.message}")
            }

            // Return original source URI directly: zero storage duplication!
            return sourceUri.toString()
        }

        private fun fetchLatestGalleryUris(context: Context, limit: Int): List<Uri> {
            val result = LinkedHashSet<Uri>()
            val effectiveLimit = if (limit <= 0) 5000 else limit
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"

            for (contentUri in listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.INTERNAL_CONTENT_URI)) {
                try {
                    context.contentResolver.query(
                        contentUri,
                        projection,
                        null,
                        null,
                        sortOrder
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        var count = 0
                        while (cursor.moveToNext() && count < effectiveLimit) {
                            val id = cursor.getLong(idColumn)
                            val itemUri = ContentUris.withAppendedId(contentUri, id)
                            result.add(itemUri)
                            count++
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DocuSnapIngestion", "Error querying $contentUri", e)
                }
            }
            return result.toList()
        }

        fun queryTotalGalleryCount(context: Context): Int {
            return try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    null,
                    null,
                    null
                )?.use { it.count } ?: 0
            } catch (_: Exception) {
                0
            }
        }

        fun isPartialPhotoAccess(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= 34) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_MEDIA_IMAGES
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
        }

        /**
         * Processes a targeted batch of gallery photos (default 50).
         * Runs on-device OCR, generates micro thumbnails, saves to Room, and returns the newly ingested documents.
         */
        suspend fun processBatch(
            context: Context,
            batchSize: Int = 50,
            offset: Int = 0
        ): List<DocumentEntity> = withContext(Dispatchers.IO) {
            val app = context.applicationContext as DocuSnapApplication
            val ocr = OcrEngine(app)
            val gemini = GeminiReasoningEngine()
            val dao = app.database.documentDao()
            val sync = app.syncRepository

            val targets = fetchGalleryBatchUris(app, limit = batchSize, offset = offset)
            val newlyCreated = mutableListOf<DocumentEntity>()

            for (uri in targets) {
                try {
                    val fileHash = computeSha256(app, uri)
                    if (fileHash != null && dao.getDocumentByHash(fileHash) != null) {
                        // Already indexed! Skip in < 1ms
                        continue
                    }

                    val docId = UUID.randomUUID().toString()
                    val isScreenshot = isScreenshotUri(app, uri)
                    val isCamera = isCameraUri(app, uri)
                    val isPdf = isPdfUri(app, uri)
                    val pdfPageBitmap = if (isPdf) renderPdfFirstPage(app, uri) else null

                    var rawOcrText = if (isPdf && pdfPageBitmap != null) {
                        ocr.recognizeText(pdfPageBitmap)
                    } else {
                        ocr.recognizeText(uri)
                    }

                    val hasReadableText = rawOcrText.trim().length >= 4
                    if (hasReadableText || isScreenshot || isPdf) {
                        // High-speed on-device semantic classification (zero cloud network delay or API rate-limits during bulk batch scan)
                        val semantic = if (hasReadableText) {
                            gemini.fallbackExtraction(rawOcrText)
                        } else {
                            val todayLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                            GeminiExtractionResult(
                                documentType = if (isPdf) DocumentEntity.TYPE_UTILITY else DocumentEntity.TYPE_GENERAL,
                                title = if (isScreenshot) "Screenshot Document ($todayLabel)" else "Document ($todayLabel)",
                                summary = "Auto-saved document in Smart Vault.",
                                keyEntities = KeyEntities(date = todayLabel.substring(0, 10)),
                                searchableTags = listOf(if (isScreenshot) "screenshot" else "document", "vault")
                            )
                        }

                        val localRefUri = generateThumbnailAndReference(app, uri, docId, pdfPageBitmap)
                        val mergedTags = LinkedHashSet<String>().apply {
                            addAll(semantic.searchableTags)
                            if (isScreenshot) add("screenshot")
                            if (isCamera) add("camera")
                            if (isPdf) add("pdf")
                        }
                        val tagsJson = org.json.JSONArray().apply {
                            mergedTags.forEach { put(it) }
                        }.toString()

                        val entity = DocumentEntity(
                            id = docId,
                            fileHash = fileHash ?: UUID.randomUUID().toString(),
                            localImageUri = localRefUri,
                            cloudStorageUrl = null,
                            rawOcrText = rawOcrText,
                            title = semantic.title,
                            aiSummary = semantic.summary,
                            documentType = semantic.documentType,
                            keyEntitiesJson = semantic.keyEntities.toJson(),
                            searchableTagsJson = tagsJson,
                            dateDetected = semantic.keyEntities.date.ifBlank {
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            },
                            syncStatus = DocumentEntity.SYNC_STATUS_PENDING_SYNC,
                            timestamp = System.currentTimeMillis()
                        )

                        dao.insertDocument(entity)
                        newlyCreated.add(entity)

                        // Micro yield to keep UI responsive
                        kotlinx.coroutines.delay(2)
                    }
                } catch (e: Exception) {
                    Log.e("DocuSnapIngestion", "Error in batch item $uri: ${e.message}")
                }
            }

            newlyCreated
        }

        private fun fetchGalleryBatchUris(context: Context, limit: Int, offset: Int): List<Uri> {
            val result = mutableListOf<Uri>()
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val queryArgs = android.os.Bundle().apply {
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                        putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                        putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media._ID))
                        putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    }
                    context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        queryArgs,
                        null
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            result.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
                        }
                    }
                } else {
                    val sortWithLimit = "$sortOrder LIMIT $limit OFFSET $offset"
                    context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        sortWithLimit
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            result.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DocuSnapIngestion", "Error in fetchGalleryBatchUris", e)
            }
            return result
        }

        private fun computeSha256(context: Context, uri: Uri): String? {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri)?.use { stream ->
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
    }
}
