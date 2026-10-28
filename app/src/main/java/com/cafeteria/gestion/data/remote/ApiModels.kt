package com.cafeteria.gestion.data.remote

import com.cafeteria.gestion.data.Announcement
import com.cafeteria.gestion.data.DashboardSummary
import com.cafeteria.gestion.data.SalePayload
import com.cafeteria.gestion.data.TeamMember
import com.cafeteria.gestion.data.local.ProductEntity

/** Every endpoint returns a consistent envelope. The future backend should keep this contract. */
data class ApiEnvelope<T>(
    val data: T? = null,
    val message: String? = null,
    val code: String? = null
)

data class LoginRequest(val identifier: String, val password: String)
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
    val available: Boolean? = null
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
        priceVersion = priceVersion.ifBlank { updatedAt.orEmpty() }
    )
}

data class DashboardDto(
    val cashCup: Long = 0,
    val transferCup: Long = 0,
    val saleCount: Int = 0,
    val lowStockCount: Int = 0,
    val totalInventoryValueCash: Long? = null,
    val totalInventoryValueTransfer: Long? = null
) {
    fun toModel() = DashboardSummary(cashCup, transferCup, saleCount, lowStockCount, totalInventoryValueCash, totalInventoryValueTransfer)
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
    val reason: String
)
