package com.cafeteria.gestion.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val priceVersion: String = ""
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
    val syncedAt: String? = null
)
