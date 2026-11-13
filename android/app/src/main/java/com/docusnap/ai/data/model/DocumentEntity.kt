package com.docusnap.ai.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["file_hash"], unique = true),
        Index(value = ["document_type"]),
        Index(value = ["date_detected"]),
        Index(value = ["sync_status"])
    ]
)
data class DocumentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "file_hash")
    val fileHash: String,

    @ColumnInfo(name = "local_image_uri")
    val localImageUri: String,

    @ColumnInfo(name = "cloud_storage_url")
    val cloudStorageUrl: String? = null,

    @ColumnInfo(name = "raw_ocr_text")
    val rawOcrText: String,

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "ai_summary")
    val aiSummary: String,

    @ColumnInfo(name = "document_type")
    val documentType: String, // "Medical Prescription", "Expense Receipt", "Utility Bill", "Bank Slip", "General Document"

    @ColumnInfo(name = "key_entities_json")
    val keyEntitiesJson: String, // Serialized KeyEntities

    @ColumnInfo(name = "searchable_tags_json")
    val searchableTagsJson: String = "[]",

    @ColumnInfo(name = "date_detected")
    val dateDetected: String, // YYYY-MM-DD

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = SYNC_STATUS_LOCAL_ONLY, // "LOCAL_ONLY", "PENDING_SYNC", "SYNCED"

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val SYNC_STATUS_LOCAL_ONLY = "LOCAL_ONLY"
        const val SYNC_STATUS_PENDING_SYNC = "PENDING_SYNC"
        const val SYNC_STATUS_SYNCED = "SYNCED"

        const val TYPE_MEDICAL = "Medical Prescription"
        const val TYPE_RECEIPT = "Expense Receipt"
        const val TYPE_UTILITY = "Utility Bill"
        const val TYPE_BANK = "Bank Slip"
        const val TYPE_GENERAL = "General Document"
    }
}

