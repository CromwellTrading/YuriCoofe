package com.cafeteria.gestion.data.remote

import com.cafeteria.gestion.data.Announcement
import com.cafeteria.gestion.data.DashboardSummary
import com.cafeteria.gestion.data.SalePayload
import com.cafeteria.gestion.data.ProductSalesSummary
import com.cafeteria.gestion.data.WorkerSalesSummary
import com.cafeteria.gestion.data.TeamMember
import com.cafeteria.gestion.data.local.ProductEntity

/** Every endpoint returns the response envelope implemented by the Cafeteria backend API v1. */
data class ApiEnvelope<T>(
    val data: T? = null,
    val message: String? = null,
    val code: String? = null
)

data class LoginRequest(val identifier: String, val password: String)
data class RegisterCustomerRequest(val displayName: String, val identifier: String, val password: String)
data class CustomerLoginRequest(val displayName: String, val password: String)
data class AuthResponse(
    val accessToken: String,
    val userId: String,
    val displayName: String,
    val role: String,
    val businessId: String? = null,
    val businessTimeZone: String? = "America/Havana"
)

data class ProductDto(
    val id: String,
    val name: String,
    val description: String? = null,
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
    val updatedAt: String? = null,
    val priceVersion: String = "",
    val available: Boolean? = null,
    val inventoryRevision: Long? = null,
    val active: Boolean = true,
    val catalogRevision: Long? = null
) {
    /** Public catalog responses carry only availability, never exact stock levels. */
    fun toEntity(publicView: Boolean = false) = ProductEntity(
        id = id,
        name = name,
        description = description.orEmpty(),
        categoryName = categoryName,
        imageUrl = imageUrl,
        unitName = unitName,
        subunitName = subunitName,
        saleMode = saleMode,
        subunitsPerUnit = subunitsPerUnit,
        unitPriceCup = unitPriceCup,
        unitPriceTransfer = unitPriceTransfer,
        subunitPriceCup = subunitPriceCup,
        subunitPriceTransfer = subunitPriceTransfer,
        stockUnits = if (publicView) if (available == true) 1L else 0L else stockUnits,
        stockSubunits = if (publicView) null else stockSubunits,
        minimumStock = if (publicView) 0L else minimumStock,
        publicVisible = publicVisible,
        updatedAt = updatedAt.orEmpty(),
        priceVersion = priceVersion.ifBlank { updatedAt.orEmpty() },
        stockIsApproximate = publicView,
        inventoryRevision = if (publicView) 0L else inventoryRevision ?: 0L,
        active = active,
        catalogRevision = if (publicView) 0L else catalogRevision ?: 0L
    )
}

data class ProductSalesDto(
    val productId: String? = null,
    val productName: String,
    val unitName: String,
    val subunitName: String? = null,
    val unitQuantity: Long = 0,
    val subunitQuantity: Long = 0,
    val totalCup: Long = 0,
    val cashCup: Long = 0,
    val transferCup: Long = 0
) {
    fun toModel() = ProductSalesSummary(productId, productName, unitName, subunitName, unitQuantity, subunitQuantity, totalCup, cashCup, transferCup)
}

data class WorkerSalesDto(
    val workerId: String,
    val workerName: String,
    val saleCount: Long = 0L,
    val cashCup: Long = 0,
    val transferCup: Long = 0,
    val totalCup: Long = 0,
    val unitQuantity: Long = 0,
    val subunitQuantity: Long = 0
) {
    fun toModel() = WorkerSalesSummary(workerId, workerName, saleCount, cashCup, transferCup, totalCup, unitQuantity, subunitQuantity)
}

data class DashboardDto(
    val cashCup: Long = 0,
    val transferCup: Long = 0,
    val saleCount: Long = 0L,
    val lowStockCount: Long = 0L,
    val totalInventoryValueCash: Long? = null,
    val totalInventoryValueTransfer: Long? = null,
    val productSales: List<ProductSalesDto> = emptyList(),
    val workerSales: List<WorkerSalesDto> = emptyList()
) {
    fun toModel() = DashboardSummary(
        cashCup, transferCup, saleCount, lowStockCount,
        totalInventoryValueCash, totalInventoryValueTransfer,
        productSales.map { it.toModel() }, workerSales.map { it.toModel() }
    )
}

data class AnnouncementDto(
    val id: String,
    val title: String,
    val message: String,
    val publishAt: String,
    val expiresAt: String? = null,
    val status: String = "PUBLISHED",
    val imageUrl: String? = null
) {
    fun toModel() = Announcement(id, title, message, publishAt, expiresAt, status, imageUrl)
}

data class TeamMemberDto(
    val id: String,
    val displayName: String,
    val identifier: String,
    val role: String,
    val active: Boolean
) {
    fun toModel() = TeamMember(id, displayName, identifier, role, active)
}

data class CreateAnnouncementRequest(
    val title: String,
    val message: String,
    val publishAt: String,
    val expiresAt: String? = null,
    val sendPushNotification: Boolean = true
)

data class CreateWorkerRequest(
    val displayName: String,
    val identifier: String,
    val temporaryPassword: String,
    val role: String = "WORKER"
)

data class InventoryAdjustmentRequest(
    val productId: String,
    val unitDelta: Long,
    val subunitDelta: Long? = null,
    val reason: String,
    val operationId: String? = null,
    val deviceId: String? = null,
    val occurredAt: String? = null,
    val localSequence: Long? = null,
    val baseInventoryRevision: Long? = null,
    val baseCatalogRevision: Long? = null,
    val basePriceVersion: String? = null,
    val operationType: String = "STOCK_ADJUSTMENT",
    val countedUnits: Long? = null,
    val countedSubunits: Long? = null
)


data class LocalStationRegistrationRequest(
    val stationId: String,
    val secret: String,
    val label: String = "Estación local"
)

data class LocalStationHeartbeatRequest(
    val storedOperationCount: Long
)

data class LocalStationDto(
    val id: String,
    val label: String,
    val status: String,
    val registeredAt: String? = null,
    val lastSeenAt: String? = null,
    val lastReceivedAt: String? = null,
    val storedOperationCount: Long = 0L,
    val revokedAt: String? = null
)
