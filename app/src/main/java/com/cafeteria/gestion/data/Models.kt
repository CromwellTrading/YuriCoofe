package com.cafeteria.gestion.data

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

object Roles {
    const val OWNER = "OWNER"
    const val WORKER = "WORKER"
    const val CUSTOMER = "CUSTOMER"
}

object SaleModes {
    const val UNIT = "UNIT"
    const val SUBUNIT = "SUBUNIT"
    const val BOTH = "BOTH"
}

object PaymentMethods {
    const val CASH_CUP = "CASH_CUP"
    const val TRANSFER_CUP = "TRANSFER_CUP"
}

data class UserSession(
    val userId: String,
    val displayName: String,
    val role: String,
    val accessToken: String,
    val businessId: String? = null,
    val businessTimeZone: String = "America/Havana"
)

data class ProductDraft(
    val id: String? = null,
    val name: String,
    val description: String = "",
    val categoryName: String,
    val unitName: String = "unidad",
    val subunitName: String = "",
    val saleMode: String = SaleModes.UNIT,
    val subunitsPerUnit: Int? = null,
    val unitPriceCup: Long?,
    val unitPriceTransfer: Long? = null,
    val subunitPriceCup: Long? = null,
    val subunitPriceTransfer: Long? = null,
    val stockUnits: Long,
    val stockSubunits: Long? = null,
    val minimumStock: Long = 0,
    val publicVisible: Boolean = true,
    val imageUri: String? = null
)

data class SaleItemPayload(
    val productId: String,
    val productName: String,
    val unitName: String,
    val subunitName: String?,
    val unitQuantity: Long,
    val subunitQuantity: Long,
    val subunitsPerUnitSnapshot: Int?,
    val priceVersionSnapshot: String,
    val unitPriceAppliedCUP: Long,
    val subunitPriceAppliedCUP: Long?,
    val totalCUP: Long
)

data class SalePayload(
    val clientSaleId: String,
    val soldAt: String,
    val deviceTimeZoneId: String,
    val deviceOffsetMinutes: Int,
    val workerId: String,
    val workerName: String,
    val paymentMethod: String,
    val currency: String = "CUP",
    val items: List<SaleItemPayload>,
    val totalCUP: Long,
    val appVersion: String = "1.0.0"
)

data class SaleSummary(
    val id: String,
    val soldAt: String,
    val workerId: String,
    val workerName: String,
    val summary: String,
    val paymentMethod: String,
    val totalCUP: Long,
    val status: String,
    val error: String? = null
)

data class DashboardSummary(
    val cashCup: Long = 0,
    val transferCup: Long = 0,
    val saleCount: Int = 0,
    val lowStockCount: Int = 0,
    val totalInventoryValueCash: Long? = null,
    val totalInventoryValueTransfer: Long? = null
)

data class Announcement(
    val id: String,
    val title: String,
    val message: String,
    val publishAt: String,
    val expiresAt: String? = null,
    val status: String = "PUBLISHED",
    val imageUrl: String? = null
)

data class TeamMember(
    val id: String,
    val displayName: String,
    val identifier: String,
    val role: String,
    val active: Boolean
)

fun newSaleId(): String = UUID.randomUUID().toString()

fun currentSaleTimestamp(): String = OffsetDateTime.now().toString()
fun currentDeviceZone(): String = ZoneId.systemDefault().id
fun currentOffsetMinutes(): Int = ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds / 60

fun formatCup(value: Long): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale("es", "ES")).format(value) + " CUP"
