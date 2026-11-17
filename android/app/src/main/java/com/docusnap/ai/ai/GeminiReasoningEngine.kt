package com.docusnap.ai.ai

import com.docusnap.ai.BuildConfig
import com.docusnap.ai.data.model.DocumentEntity
import com.docusnap.ai.data.model.GeminiExtractionResult
import com.docusnap.ai.data.model.KeyEntities
import com.docusnap.ai.i18n.AppLanguage
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class GeminiReasoningEngine(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
) {
    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.2f
                topK = 32
                topP = 0.8f
                responseMimeType = "application/json"
            }
        )
    }

    /**
     * Extracts structured semantic information from raw OCR text using Gemini Flash.
     */
    suspend fun analyzeDocument(rawOcrText: String): GeminiExtractionResult = withContext(Dispatchers.IO) {
        if (rawOcrText.isBlank()) {
            return@withContext fallbackExtraction(rawOcrText)
        }

        if (apiKey.isBlank() || apiKey.startsWith("AIzaSyMockKey")) {
            return@withContext fallbackExtraction(rawOcrText)
        }

        val prompt = """
            You are an expert document parser and semantic classifier for DocuSnap AI.
            Analyze the following raw OCR text extracted from a photographed document, bill, or prescription.
            
            Return ONLY a valid JSON object strictly matching this schema:
            {
              "document_type": "Medical Prescription | Expense Receipt | Utility Bill | Bank Slip | General Document",
              "title": "Short descriptive title (e.g. Lab Report - City Medical or Monthly Electric Utility Bill)",
              "summary": "1-2 sentence conversational summary explaining what this document is and its main details",
              "key_entities": {
                "doctor_name": "Dr. Name or null",
                "medicines": ["List of medicine names with dosages if present"],
                "total_amount": 1250.00 or null,
                "vendor_or_hospital": "Name of pharmacy, hospital, restaurant, or utility company or null",
                "date": "YYYY-MM-DD or today's date if unparseable"
              },
              "searchable_tags": ["tag1", "tag2", "tag3"]
            }

            RAW OCR TEXT:
            $rawOcrText
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val jsonText = response.text ?: ""
            GeminiExtractionResult.fromJson(jsonText)
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackExtraction(rawOcrText)
        }
    }

    /**
     * Synthesizes conversational answer from natural language query and local FTS5 search matches.
     * Supports any global language including English, Japanese, Korean, Arabic, Spanish, etc.
     */
    suspend fun answerConversationalQuery(
        userQuery: String,
        candidateDocs: List<DocumentEntity>,
        targetLanguage: AppLanguage = AppLanguage.ENGLISH
    ): String = withContext(Dispatchers.IO) {
        if (candidateDocs.isEmpty()) {
            return@withContext when (targetLanguage) {
                AppLanguage.JAPANESE -> "保管庫に一致するドキュメントが見つかりませんでした。新しい書類をスキャンまたは取り込んでください。"
                AppLanguage.KOREAN -> "보관함에서 일치하는 문서를 찾을 수 없습니다. 새 문서를 스캔하거나 가져오세요."
                AppLanguage.URDU -> "آپ کے والٹ میں اس سے متعلق کوئی دستاویز نہیں ملی۔ براہ کرم تصویر کھینچیں یا امپورٹ کریں۔"
                AppLanguage.ARABIC -> "لم يتم العثور على أي مستند مطابق في خزنتك. يُرجى التقاط أو استيراد مستند جديد."
                AppLanguage.SPANISH -> "No se encontró ningún documento coincidente en su bóveda. Escanee o importe uno nuevo."
                AppLanguage.FRENCH -> "Aucun document correspondant trouvé dans votre coffre. Veuillez en numériser ou importer un."
                AppLanguage.GERMAN -> "Kein passendes Dokument im Tresor gefunden. Bitte scannen oder importieren Sie ein Dokument."
                AppLanguage.CHINESE -> "在您的保险库中未找到相关文档，请拍照或导入新文件。"
                AppLanguage.HINDI -> "आपके वॉल्ट में इस संबंध में कोई दस्तावेज़ नहीं मिला। कृपया नई फोटो लें या चुनें।"
                else -> "No matching document was found in your Smart Vault. Scan a photo or take a screenshot to index it automatically."
            }
        }

        if (apiKey.isBlank() || apiKey.startsWith("AIzaSyMockKey")) {
            val first = candidateDocs.first()
            return@withContext "Document: **${first.title}** (${first.documentType}). Summary: ${first.aiSummary}"
        }

        val contextBuilder = StringBuilder()
        candidateDocs.take(5).forEachIndexed { index, doc ->
            contextBuilder.append("--- DOCUMENT ${index + 1} (ID: ${doc.id}) ---\n")
            contextBuilder.append("Title: ${doc.title}\n")
            contextBuilder.append("Type: ${doc.documentType}\n")
            contextBuilder.append("Date: ${doc.dateDetected}\n")
            contextBuilder.append("Entities: ${doc.keyEntitiesJson}\n")
            contextBuilder.append("Summary: ${doc.aiSummary}\n")
            contextBuilder.append("OCR Excerpt: ${doc.rawOcrText.take(400)}\n\n")
        }

        val prompt = """
            You are the conversational search engine for DocuSnap AI (Bill & Rx Finder).
            The user is asking a question about their saved receipts, bills, or medical prescriptions.
            
            Target Language: ${targetLanguage.nativeName} (${targetLanguage.code}).
            Reply in the same language as the user's question (or ${targetLanguage.nativeName} if ambiguous) in 1-3 clear sentences.
            Directly cite medicine names, dosages, bill amounts, due dates, merchants, or doctor names accurately.
            
            USER QUESTION:
            "$userQuery"
            
            CANDIDATE DOCUMENTS FROM LOCAL VAULT:
            $contextBuilder
        """.trimIndent()

        try {
            val chatModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.3f
                }
            )
            val response = chatModel.generateContent(prompt)
            response.text ?: "Matching document found. Open the card below to inspect full details."
        } catch (e: Exception) {
            e.printStackTrace()
            val doc = candidateDocs.first()
            "Found relevant document: **${doc.title}**. ${doc.aiSummary}"
        }
    }

    /**
     * Robust on-device fallback extraction when Gemini is offline or API key is not yet set.
     */
    fun fallbackExtraction(rawText: String): GeminiExtractionResult {
        val lower = rawText.lowercase(Locale.ROOT)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val isMedical = lower.contains("rx") || lower.contains("dr.") || lower.contains("doctor") ||
                lower.contains("clinic") || lower.contains("hospital") || lower.contains("mg") ||
                lower.contains("tablet") || lower.contains("syrup") || lower.contains("capsule")

        val isUtility = lower.contains("electric") || lower.contains("utility") || lower.contains("water bill") ||
                lower.contains("gas bill") || lower.contains("internet") || lower.contains("billing period") ||
                lower.contains("due date") || lower.contains("account number") || lower.contains("kwh") ||
                lower.contains("電気") || lower.contains("水道") || lower.contains("ガス")

        val isReceipt = lower.contains("receipt") || lower.contains("total") || lower.contains("subtotal") ||
                lower.contains("cash") || lower.contains("tax") || lower.contains("invoice") ||
                lower.contains("amount") || lower.contains("paid") || lower.contains("合計") ||
                lower.contains("領収書") || lower.contains("合算") || lower.contains("영수증")

        val isBank = lower.contains("bank") || lower.contains("account") || lower.contains("iban") ||
                lower.contains("swift") || lower.contains("deposit") || lower.contains("branch") ||
                lower.contains("transfer")

        val docType = when {
            isMedical -> DocumentEntity.TYPE_MEDICAL
            isUtility -> DocumentEntity.TYPE_UTILITY
            isReceipt -> DocumentEntity.TYPE_RECEIPT
            isBank -> DocumentEntity.TYPE_BANK
            else -> DocumentEntity.TYPE_GENERAL
        }

        // Extract international currency amount if present
        var totalAmount: Double? = null
        val amountPattern = Pattern.compile(
            """(?:total|amount|subtotal|合計|請求額|합계|usd|eur|gbp|jpy|rs\.?|pkr|inr|¥|\$|€|£)\s*[:=]?\s*(?:rs\.?|pkr|inr|\$|€|£|¥)?\s*([0-9]+(?:[.,][0-9]{1,2})?)""",
            Pattern.CASE_INSENSITIVE
        )
        val amountMatcher = amountPattern.matcher(rawText)
        if (amountMatcher.find()) {
            val numStr = amountMatcher.group(1)?.replace(",", "")
            totalAmount = numStr?.toDoubleOrNull()
        }

        // Extract medicines if medical
        val medicines = mutableListOf<String>()
        if (isMedical) {
            val medPattern = Pattern.compile("""(?:tab|cap|syp|inj|tablet|capsule|syrup)\.?\s*([A-Za-z0-9\-]+(?:\s*\d+\s*(?:mg|ml))?)""", Pattern.CASE_INSENSITIVE)
            val medMatcher = medPattern.matcher(rawText)
            var count = 0
            while (medMatcher.find() && count < 10) {
                val match = medMatcher.group().trim()
                if (match.length > 3 && !match.equals("prescription", true)) {
                    medicines.add(match)
                    count++
                }
            }
        }

        val tags = mutableListOf<String>()
        tags.add(docType.lowercase(Locale.ROOT))
        if (isMedical) tags.addAll(listOf("medical", "health", "medicine", "prescription"))
        if (isReceipt || isUtility) tags.addAll(listOf("finance", "expense", "bill"))

        val firstLine = rawText.lines().firstOrNull { it.isNotBlank() }?.take(40) ?: "Captured Image"
        val title = "$docType: $firstLine"
        val wordCount = if (rawText.isBlank()) 0 else rawText.trim().split("\\s+".toRegex()).size
        val summary = if (wordCount > 0) {
            "Auto-detected $docType containing $wordCount words."
        } else {
            "Captured visual document stored offline in Smart Vault."
        }

        return GeminiExtractionResult(
            documentType = docType,
            title = title,
            summary = summary,
            keyEntities = KeyEntities(
                doctorName = if (isMedical) "Consultant Physician" else null,
                medicines = medicines,
                totalAmount = totalAmount,
                vendorOrHospital = if (isMedical) "Medical Center" else if (isUtility) "Utility Provider" else "Store",
                date = today
            ),
            searchableTags = tags
        )
    }
}

