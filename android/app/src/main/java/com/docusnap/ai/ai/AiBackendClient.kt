package com.docusnap.ai.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.docusnap.ai.billing.BillingManager
import com.docusnap.ai.data.model.DocumentEntity
import com.docusnap.ai.data.model.GeminiExtractionResult
import com.docusnap.ai.i18n.AppLanguage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min
import kotlin.math.roundToInt

data class VisionExtractBackendResult(
    val extraction: GeminiExtractionResult? = null,
    val rawOcr: String = "",
    val remainingBalanceCents: Int? = null,
    val insufficientBalance: Boolean = false
)

data class MultilingualSearchBackendResult(
    val answer: String? = null,
    val matchedIds: List<String> = emptyList(),
    val remainingBalanceCents: Int? = null,
    val insufficientBalance: Boolean = false,
    val error: String? = null
)

/**
 * Communicates strictly with our Secure Firebase Functions Backend.
 * Gemini API keys NEVER reside inside the Android client APK; all 5x billing charges
 * are verified and deducted atomically server-side from the user's loaded One-Time Balance.
 */
object AiBackendClient {

    private const val BASE_URL = "https://us-central1-webproject-58306.cloudfunctions.net"
    private const val TAG = "DocuSnapBackendAI"

    private suspend fun getFirebaseIdToken(): String? {
        return try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser ?: auth.signInAnonymously().await().user
            user?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            Log.w(TAG, "Unable to obtain Firebase Auth ID token: ${e.message}")
            null
        }
    }

    private fun postJson(endpoint: String, token: String, payload: JSONObject): Pair<Int, String> {
        val url = URL("$BASE_URL/$endpoint")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Authorization", "Bearer $token")
        }

        conn.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to body
    }

    private fun encodeUriToCompactBase64(context: Context, uri: Uri): String? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            var sample = 1
            while (boundsOpts.outWidth / sample > 1600 || boundsOpts.outHeight / sample > 1600) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val ratio = min(1400f / bitmap.width, 1400f / bitmap.height)
            val scaled = if (ratio < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).roundToInt(),
                    (bitmap.height * ratio).roundToInt(),
                    true
                )
            } else bitmap

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()

            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed preparing image for backend AI: ${e.message}")
            null
        }
    }

    /**
     * Calls `/aiVisionExtract` on secure backend (5x rate deducted from loaded balance).
     */
    suspend fun visionExtract5x(
        context: Context,
        sourceUri: Uri,
        targetLanguage: AppLanguage,
        existingOcrText: String = ""
    ): VisionExtractBackendResult = withContext(Dispatchers.IO) {
        val token = getFirebaseIdToken() ?: return@withContext VisionExtractBackendResult()
        val base64 = encodeUriToCompactBase64(context, sourceUri) ?: return@withContext VisionExtractBackendResult()

        try {
            val payload = JSONObject().apply {
                put("imageBase64", base64)
                put("mimeType", "image/jpeg")
                put("targetLanguage", targetLanguage.nativeName)
                put("existingOcrText", existingOcrText)
            }

            val (code, body) = postJson("aiVisionExtract", token, payload)
            if (code == 402) {
                return@withContext VisionExtractBackendResult(insufficientBalance = true)
            }
            if (code !in 200..299 || body.isBlank()) {
                Log.w(TAG, "aiVisionExtract HTTP $code: $body")
                return@withContext VisionExtractBackendResult()
            }

            val json = JSONObject(body)
            val billing = json.optJSONObject("billing")
            val remaining = billing?.optInt("remainingBalanceCents", -1)?.takeIf { it >= 0 }
            if (remaining != null) {
                BillingManager.getInstance(context).updateBalanceFromBackend(remaining)
            }

            val resultObj = json.optJSONObject("result") ?: return@withContext VisionExtractBackendResult()
            val rawOcr = resultObj.optString("raw_ocr", existingOcrText)
            val extraction = GeminiExtractionResult.fromJson(resultObj.toString())

            VisionExtractBackendResult(
                extraction = extraction,
                rawOcr = rawOcr,
                remainingBalanceCents = remaining
            )
        } catch (e: Exception) {
            Log.e(TAG, "visionExtract5x error", e)
            VisionExtractBackendResult()
        }
    }

    /**
     * Calls `/aiMultilingualSearch` on secure backend (5x rate deducted from loaded balance).
     * Enables Japanese/Korean/Chinese/Arabic/Urdu/any-language queries across all documents.
     */
    suspend fun multilingualSearch5x(
        context: Context,
        query: String,
        documents: List<DocumentEntity>,
        uiLanguage: AppLanguage
    ): MultilingualSearchBackendResult = withContext(Dispatchers.IO) {
        val token = getFirebaseIdToken()
            ?: return@withContext MultilingualSearchBackendResult(error = "Sign in or network required")

        try {
            val docsArray = JSONArray()
            documents.take(40).forEach { doc ->
                docsArray.put(
                    JSONObject().apply {
                        put("id", doc.id)
                        put("title", doc.title)
                        put("type", doc.documentType)
                        put("date", doc.dateDetected)
                        put("summary", doc.aiSummary)
                        put("entities", doc.keyEntitiesJson)
                        put("tags", doc.searchableTagsJson)
                        put("ocr", doc.rawOcrText.take(500))
                    }
                )
            }

            val payload = JSONObject().apply {
                put("query", query)
                put("uiLanguage", uiLanguage.nativeName)
                put("documents", docsArray)
            }

            val (code, body) = postJson("aiMultilingualSearch", token, payload)
            if (code == 402) {
                return@withContext MultilingualSearchBackendResult(insufficientBalance = true)
            }
            if (code !in 200..299 || body.isBlank()) {
                return@withContext MultilingualSearchBackendResult(error = "Backend HTTP $code")
            }

            val json = JSONObject(body)
            val billing = json.optJSONObject("billing")
            val remaining = billing?.optInt("remainingBalanceCents", -1)?.takeIf { it >= 0 }
            if (remaining != null) {
                BillingManager.getInstance(context).updateBalanceFromBackend(remaining)
            }

            val idsArray = json.optJSONArray("matchedIds")
            val matchedIds = mutableListOf<String>()
            if (idsArray != null) {
                for (i in 0 until idsArray.length()) {
                    val id = idsArray.optString(i)
                    if (id.isNotBlank()) matchedIds.add(id)
                }
            }

            MultilingualSearchBackendResult(
                answer = json.optString("answer", ""),
                matchedIds = matchedIds,
                remainingBalanceCents = remaining
            )
        } catch (e: Exception) {
            Log.e(TAG, "multilingualSearch5x error", e)
            MultilingualSearchBackendResult(error = e.message)
        }
    }

    /**
     * Credits a verified Google Play One-Time INAPP purchase ($99-$999) into Firebase wallet.
     */
    suspend fun creditOneTimePurchase(
        context: Context,
        productId: String,
        purchaseToken: String
    ): Int? = withContext(Dispatchers.IO) {
        val token = getFirebaseIdToken() ?: return@withContext null
        try {
            val payload = JSONObject().apply {
                put("productId", productId)
                put("purchaseToken", purchaseToken)
            }
            val (code, body) = postJson("creditOneTimePurchase", token, payload)
            if (code in 200..299 && body.isNotBlank()) {
                val json = JSONObject(body)
                val balanceCents = json.optInt("balanceCents", -1)
                if (balanceCents >= 0) {
                    BillingManager.getInstance(context).updateBalanceFromBackend(balanceCents)
                    return@withContext balanceCents
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "creditOneTimePurchase failed", e)
        }
        null
    }

    /**
     * Refreshes wallet balance from Firebase Functions backend.
     */
    suspend fun syncWalletStatus(context: Context): Int? = withContext(Dispatchers.IO) {
        val token = getFirebaseIdToken() ?: return@withContext null
        try {
            val url = URL("$BASE_URL/getWalletStatus")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                val cents = json.optInt("balanceCents", 0)
                BillingManager.getInstance(context).updateBalanceFromBackend(cents)
                return@withContext cents
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "syncWalletStatus skipped: ${e.message}")
        }
        null
    }
}

