package com.cafeteria.gestion.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(sale: SaleQueueEntity): Long

    @Query("SELECT * FROM sale_queue WHERE status = 'PENDING' ORDER BY createdAtEpochMs ASC")
    suspend fun getPending(): List<SaleQueueEntity>

    @Query("SELECT * FROM sale_queue WHERE status = 'PENDING' AND (businessId = :businessId OR (businessId = '' AND workerId = :userId)) ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun getPendingForBusiness(businessId: String, userId: String): List<SaleQueueEntity>

    @Query("SELECT * FROM sale_queue WHERE status IN ('PENDING', 'NEEDS_REVIEW') ORDER BY createdAtEpochMs ASC")
    suspend fun getUnresolved(): List<SaleQueueEntity>

    @Query("SELECT * FROM sale_queue ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<SaleQueueEntity>

    @Query("SELECT * FROM sale_queue WHERE workerId = :workerId ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun getRecentForWorker(workerId: String, limit: Int = 100): List<SaleQueueEntity>

    @Query("SELECT * FROM sale_queue WHERE businessId = :businessId OR (businessId = '' AND workerId = :ownerId) ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun getRecentForBusiness(businessId: String, ownerId: String, limit: Int = 100): List<SaleQueueEntity>

    @Query("SELECT COUNT(*) FROM sale_queue WHERE status IN ('PENDING','NEEDS_REVIEW') AND (businessId = :businessId OR (businessId = '' AND workerId = :userId))")
    suspend fun attentionCountForBusiness(businessId: String, userId: String): Int

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

    @Query("UPDATE sale_queue SET payloadJson = :payloadJson WHERE id = :id")
    suspend fun updatePayload(id: String, payloadJson: String)

    @Query("UPDATE sale_queue SET status = 'PENDING', lastError = NULL, syncedAt = NULL WHERE id = :id AND status = 'NEEDS_REVIEW'")
    suspend fun retryAfterReview(id: String): Int
}

@Dao
interface ProductQueueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ProductQueueEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(item: ProductQueueEntity): Long

    @Query("SELECT * FROM product_queue WHERE status = 'PENDING' ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun getPending(): List<ProductQueueEntity>

    @Query("SELECT * FROM product_queue WHERE status IN ('PENDING','NEEDS_REVIEW') ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun getUnresolved(): List<ProductQueueEntity>

    @Query("SELECT * FROM product_queue WHERE status IN ('PENDING','NEEDS_REVIEW') AND businessId = :businessId ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun getUnresolvedForBusiness(businessId: String): List<ProductQueueEntity>

    @Query("SELECT * FROM product_queue WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProductQueueEntity?

    @Query("SELECT COUNT(*) FROM product_queue WHERE productId = :productId AND createdAtEpochMs < :createdAtEpochMs AND status IN ('PENDING','NEEDS_REVIEW')")
    suspend fun hasEarlierUnresolved(productId: String, createdAtEpochMs: Long): Int

    @Query("SELECT COUNT(*) FROM product_queue WHERE status IN ('PENDING','NEEDS_REVIEW') AND ownerId = :ownerId")
    suspend fun attentionCountForOwner(ownerId: String): Int

    @Query("UPDATE product_queue SET status = :status, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String?)

    /** Updates the persisted JSON payload while preserving the original operation UUID and ordering. */
    @Query("UPDATE product_queue SET payloadJson = :payloadJson WHERE id = :id")
    suspend fun updatePayload(id: String, payloadJson: String)

    @Query("UPDATE product_queue SET status = 'PENDING', lastError = NULL WHERE ownerId = :ownerId AND status = 'NEEDS_REVIEW'")
    suspend fun retryOwnerReview(ownerId: String): Int

    @Query("UPDATE product_queue SET productId = :newId WHERE productId = :oldId")
    suspend fun remapProductId(oldId: String, newId: String)

    @Query("DELETE FROM product_queue WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AdminOperationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: AdminOperationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(item: AdminOperationEntity): Long

    @Query("SELECT * FROM admin_operation_queue WHERE status = 'PENDING' ORDER BY localSequence ASC")
    suspend fun getPending(): List<AdminOperationEntity>

    @Query("SELECT * FROM admin_operation_queue WHERE status IN ('PENDING','NEEDS_REVIEW') ORDER BY localSequence ASC")
    suspend fun getUnresolved(): List<AdminOperationEntity>

    @Query("SELECT * FROM admin_operation_queue WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AdminOperationEntity?

    @Query("SELECT COUNT(*) FROM admin_operation_queue WHERE status IN ('PENDING','NEEDS_REVIEW') AND ownerId = :ownerId")
    suspend fun attentionCountForOwner(ownerId: String): Int

    @Query("SELECT COALESCE(MAX(localSequence), 0) FROM admin_operation_queue WHERE ownerId = :ownerId")
    suspend fun maxSequenceForOwner(ownerId: String): Long

    @Query("SELECT COALESCE(MAX(localSequence), 0) FROM admin_operation_queue WHERE deviceId = :deviceId")
    suspend fun maxSequenceForDevice(deviceId: String): Long

    @Query("SELECT COUNT(*) FROM admin_operation_queue WHERE productId = :productId AND status IN ('PENDING','NEEDS_REVIEW')")
    suspend fun unresolvedCountForProduct(productId: String): Int

    @Query("SELECT COUNT(*) FROM admin_operation_queue WHERE productId = :productId AND localSequence < :sequence AND status IN ('PENDING','NEEDS_REVIEW')")
    suspend fun hasEarlierUnresolved(productId: String, sequence: Long): Int

    @Query("UPDATE admin_operation_queue SET productId = :newId WHERE productId = :oldId")
    suspend fun remapProductId(oldId: String, newId: String)

    @Query("UPDATE admin_operation_queue SET payloadJson = :payloadJson WHERE id = :id")
    suspend fun updatePayload(id: String, payloadJson: String)

    @Query("UPDATE admin_operation_queue SET status = :status, lastError = :error, serverInventoryRevision = :serverRevision WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String?, serverRevision: Long?)
}

@Dao
interface TransferBundleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(bundle: TransferBundleEntity): Long

    @Query("SELECT * FROM transfer_bundles ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<TransferBundleEntity>

    @Query("SELECT * FROM transfer_bundles WHERE direction = 'IMPORTED' ORDER BY createdAtEpochMs ASC")
    suspend fun getAllImported(): List<TransferBundleEntity>

    @Query("SELECT * FROM transfer_bundles ORDER BY createdAtEpochMs ASC")
    suspend fun getAll(): List<TransferBundleEntity>

    @Query("SELECT * FROM transfer_bundles WHERE bundleId = :id LIMIT 1")
    suspend fun findById(id: String): TransferBundleEntity?

    @Query("SELECT COUNT(*) FROM transfer_bundles WHERE direction = 'IMPORTED'")
    suspend fun receivedCount(): Int

    @Query("UPDATE transfer_bundles SET status = :status, destinationDeviceId = :destinationDeviceId, lastError = :error WHERE bundleId = :id")
    suspend fun updateStatus(id: String, status: String, destinationDeviceId: String?, error: String?)
}
