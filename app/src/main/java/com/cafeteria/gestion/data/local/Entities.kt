package com.cafeteria.gestion.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val categoryName: String,
    val imageUrl: String? = null,
    val unitName: String = "unidad",
    val subunitName: String? = null,
    val saleMode: String = "UNIT",
    val subunitsPerUnit: Int? = null,
    val unitPriceCup: Long?,
    val unitPriceTransfer: Long? = null,
    val subunitPriceCup: Long? = null,
    val subunitPriceTransfer: Long? = null,
    val stockUnits: Long,
    val stockSubunits: Long? = null,
    val minimumStock: Long = 0,
    val publicVisible: Boolean = true,
    val updatedAt: String = "",
    val priceVersion: String = "",
    @ColumnInfo(defaultValue = "0") val stockIsApproximate: Boolean = false,
    @ColumnInfo(defaultValue = "0") val inventoryRevision: Long = 0L,
    @ColumnInfo(defaultValue = "1") val active: Boolean = true,
    @ColumnInfo(defaultValue = "0") val catalogRevision: Long = 0L
)

@Entity(tableName = "sale_queue")
data class SaleQueueEntity(
    @PrimaryKey val id: String,
    val soldAt: String,
    val createdAtEpochMs: Long,
    val workerId: String,
    val workerName: String,
    val summary: String,
    val paymentMethod: String,
    val totalCUP: Long,
    val payloadJson: String,
    val status: String = "PENDING",
    val lastError: String? = null,
    val syncedAt: String? = null,
    @ColumnInfo(defaultValue = "''") val businessId: String = ""
)

/** Durable owner-only outbox for product creates/updates made while disconnected. */
@Entity(tableName = "product_queue", indices = [Index(value = ["productId"])])
data class ProductQueueEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val ownerId: String,
    val operation: String,
    val payloadJson: String,
    val imageUri: String? = null,
    val createdAtEpochMs: Long,
    val status: String = "PENDING",
    val lastError: String? = null,
    @ColumnInfo(defaultValue = "''") val businessId: String = ""
)

/** Durable event log for owner-side stock operations created without network access. */
@Entity(
    tableName = "admin_operation_queue",
    indices = [Index(value = ["productId"]), Index(value = ["deviceId", "localSequence"], unique = true)]
)
data class AdminOperationEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val ownerId: String,
    val businessId: String,
    val deviceId: String,
    val operation: String,
    val payloadJson: String,
    val catalogRevision: Long?,
    val inventoryRevision: Long?,
    val occurredAt: String,
    val createdAtEpochMs: Long,
    val localSequence: Long,
    val status: String = "PENDING",
    val lastError: String? = null,
    val serverInventoryRevision: Long? = null
)

/** Transfer receipts are metadata-only; payloads remain encrypted at rest with Android Keystore. */
@Entity(tableName = "transfer_bundles", indices = [Index(value = ["sourceDeviceId"]), Index(value = ["createdAtEpochMs"])])
data class TransferBundleEntity(
    @PrimaryKey val bundleId: String,
    val sourceDeviceId: String,
    val destinationDeviceId: String?,
    val direction: String,
    val contentHash: String,
    val operationIdsJson: String,
    val status: String,
    val createdAt: String,
    val createdAtEpochMs: Long,
    val encryptedPayloadAtRest: String,
    val lastError: String? = null
)
