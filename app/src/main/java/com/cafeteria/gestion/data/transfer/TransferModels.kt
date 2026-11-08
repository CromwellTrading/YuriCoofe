package com.cafeteria.gestion.data.transfer

import com.cafeteria.gestion.data.local.AdminOperationEntity
import com.cafeteria.gestion.data.local.ProductQueueEntity
import com.cafeteria.gestion.data.local.SaleQueueEntity

/** Wire format v1. No authentication token or password is ever included in a handover package. */
data class TransferPackagePayload(
    val formatVersion: Int = 1,
    val bundleId: String,
    val businessId: String,
    val sourceDeviceId: String,
    val sourceUserId: String,
    val sourceRole: String,
    val createdAt: String,
    val sales: List<SaleQueueEntity>,
    val productOperations: List<ProductQueueEntity>,
    val adminOperations: List<AdminOperationEntity>,
    val productImagesBase64: Map<String, String> = emptyMap(),
    val transferHistory: List<TransferHistoryItem> = emptyList()
)

data class TransferPackageDocument(
    val payload: TransferPackagePayload,
    val sha256: String,
    val formatVersion: Int = 1
)

data class TransferHistoryItem(
    val bundleId: String,
    val sourceDeviceId: String,
    val destinationDeviceId: String?,
    val direction: String,
    val contentHash: String,
    val operationIdsJson: String,
    val status: String,
    val createdAt: String
)

data class TransferImportSummary(
    val bundleId: String,
    val salesAdded: Int,
    val productOperationsAdded: Int,
    val inventoryOperationsAdded: Int,
    val duplicateOperations: Int,
    val message: String
)
