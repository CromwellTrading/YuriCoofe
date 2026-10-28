package com.cafeteria.gestion.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)

    @Query("DELETE FROM products")
    suspend fun clearAll()

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SaleQueueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(sale: SaleQueueEntity)

    @Query("SELECT * FROM sale_queue WHERE status = 'PENDING' ORDER BY createdAtEpochMs ASC")
    suspend fun getPending(): List<SaleQueueEntity>

    @Query("SELECT * FROM sale_queue WHERE status IN ('PENDING', 'NEEDS_REVIEW') ORDER BY createdAtEpochMs ASC")
    suspend fun getUnresolved(): List<SaleQueueEntity>

    @Query("SELECT * FROM sale_queue ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<SaleQueueEntity>

    @Query("SELECT * FROM sale_queue WHERE workerId = :workerId ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun getRecentForWorker(workerId: String, limit: Int = 100): List<SaleQueueEntity>

    @Query("SELECT COUNT(*) FROM sale_queue WHERE status = 'PENDING' AND workerId = :workerId")
    suspend fun pendingUploadCountForWorker(workerId: String): Int

    @Query("SELECT COUNT(*) FROM sale_queue WHERE status IN ('PENDING', 'NEEDS_REVIEW') AND workerId = :workerId")
    suspend fun attentionCountForWorker(workerId: String): Int

    @Query("UPDATE sale_queue SET status = :status, lastError = :error, syncedAt = :syncedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String?, syncedAt: String?)

    @Query("SELECT COUNT(*) FROM sale_queue WHERE status = 'PENDING'")
    suspend fun pendingUploadCount(): Int

    @Query("SELECT COUNT(*) FROM sale_queue WHERE status IN ('PENDING', 'NEEDS_REVIEW')")
    suspend fun attentionCount(): Int

    @Query("SELECT * FROM sale_queue WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SaleQueueEntity?

    @Query("UPDATE sale_queue SET status = 'PENDING', lastError = NULL, syncedAt = NULL WHERE id = :id AND status = 'NEEDS_REVIEW'")
    suspend fun retryAfterReview(id: String): Int
}
