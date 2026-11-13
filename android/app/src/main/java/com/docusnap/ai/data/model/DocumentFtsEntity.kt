package com.docusnap.ai.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(contentEntity = DocumentEntity::class)
@Entity(tableName = "documents_fts")
data class DocumentFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "raw_ocr_text")
    val rawOcrText: String,

    @ColumnInfo(name = "ai_summary")
    val aiSummary: String,

    @ColumnInfo(name = "key_entities_json")
    val keyEntitiesJson: String,

    @ColumnInfo(name = "searchable_tags_json")
    val searchableTagsJson: String
)

