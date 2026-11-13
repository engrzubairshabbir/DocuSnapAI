package com.docusnap.ai.data.model

import org.json.JSONArray
import org.json.JSONObject

data class KeyEntities(
    val doctorName: String? = null,
    val medicines: List<String> = emptyList(),
    val totalAmount: Double? = null,
    val vendorOrHospital: String? = null,
    val date: String = ""
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("doctor_name", doctorName ?: JSONObject.NULL)
        val medArray = JSONArray()
        medicines.forEach { medArray.put(it) }
        obj.put("medicines", medArray)
        obj.put("total_amount", totalAmount ?: JSONObject.NULL)
        obj.put("vendor_or_hospital", vendorOrHospital ?: JSONObject.NULL)
        obj.put("date", date)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): KeyEntities {
            return try {
                val obj = JSONObject(jsonStr)
                val medList = mutableListOf<String>()
                val medArray = obj.optJSONArray("medicines")
                if (medArray != null) {
                    for (i in 0 until medArray.length()) {
                        medList.add(medArray.getString(i))
                    }
                }
                KeyEntities(
                    doctorName = if (obj.isNull("doctor_name")) null else obj.optString("doctor_name", null),
                    medicines = medList,
                    totalAmount = if (obj.isNull("total_amount")) null else obj.optDouble("total_amount"),
                    vendorOrHospital = if (obj.isNull("vendor_or_hospital")) null else obj.optString("vendor_or_hospital", null),
                    date = obj.optString("date", "")
                )
            } catch (e: Exception) {
                KeyEntities()
            }
        }
    }
}

data class GeminiExtractionResult(
    val documentType: String,
    val title: String,
    val summary: String,
    val keyEntities: KeyEntities,
    val searchableTags: List<String>
) {
    companion object {
        fun fromJson(jsonStr: String): GeminiExtractionResult {
            // Clean markdown code fence if returned
            val clean = jsonStr.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val obj = JSONObject(clean)
            val tagsList = mutableListOf<String>()
            val tagsArray = obj.optJSONArray("searchable_tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    tagsList.add(tagsArray.getString(i))
                }
            }

            val keyEntObj = obj.optJSONObject("key_entities") ?: JSONObject()
            val entities = KeyEntities.fromJson(keyEntObj.toString())

            return GeminiExtractionResult(
                documentType = obj.optString("document_type", DocumentEntity.TYPE_GENERAL),
                title = obj.optString("title", "Scanned Document"),
                summary = obj.optString("summary", ""),
                keyEntities = entities,
                searchableTags = tagsList
            )
        }
    }
}

