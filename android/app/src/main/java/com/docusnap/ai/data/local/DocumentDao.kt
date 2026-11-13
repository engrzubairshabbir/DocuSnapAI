package com.docusnap.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.docusnap.ai.data.model.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(documents: List<DocumentEntity>)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun getDocumentFlowById(id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE file_hash = :hash LIMIT 1")
    suspend fun getDocumentByHash(hash: String): DocumentEntity?

    @Query("SELECT COUNT(*) FROM documents")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM documents ORDER BY timestamp DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE document_type = :type ORDER BY timestamp DESC")
    fun getDocumentsByType(type: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE date_detected BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun getDocumentsByDateRange(startDate: String, endDate: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE sync_status = :status")
    suspend fun getDocumentsBySyncStatus(status: String): List<DocumentEntity>

    @Query("UPDATE documents SET sync_status = :status, cloud_storage_url = :cloudUrl WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, cloudUrl: String?)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    // Full-Text Search (FTS) MATCH Query
    @Query("""
        SELECT documents.* FROM documents 
        JOIN documents_fts ON documents.rowid = documents_fts.rowid 
        WHERE documents_fts MATCH :searchQuery 
        ORDER BY documents.timestamp DESC
    """)
    fun searchFts(searchQuery: String): Flow<List<DocumentEntity>>

    @Query("""
        SELECT documents.* FROM documents 
        JOIN documents_fts ON documents.rowid = documents_fts.rowid 
        WHERE documents_fts MATCH :searchQuery 
        ORDER BY documents.timestamp DESC
    """)
    suspend fun searchFtsOnce(searchQuery: String): List<DocumentEntity>

    // Combined query: search across OCR, title, AI summary, and tags
    @Query("""
        SELECT * FROM documents 
        WHERE raw_ocr_text LIKE '%' || :query || '%' 
           OR title LIKE '%' || :query || '%' 
           OR ai_summary LIKE '%' || :query || '%' 
           OR key_entities_json LIKE '%' || :query || '%'
           OR searchable_tags_json LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchLike(query: String): Flow<List<DocumentEntity>>

    @Query("""
        SELECT * FROM documents 
        WHERE raw_ocr_text LIKE '%' || :query || '%' 
           OR title LIKE '%' || :query || '%' 
           OR ai_summary LIKE '%' || :query || '%' 
           OR key_entities_json LIKE '%' || :query || '%'
           OR searchable_tags_json LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    suspend fun searchLikeOnce(query: String): List<DocumentEntity>

    @Query("SELECT * FROM documents ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentDocumentsOnce(limit: Int): List<DocumentEntity>

    @Query("SELECT * FROM documents ORDER BY timestamp DESC")
    suspend fun getAllDocumentsOnce(): List<DocumentEntity>
}

