package com.cafeteria.gestion.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.cafeteria.gestion.data.local.CafeDatabase
import com.cafeteria.gestion.data.local.ProductEntity
import com.cafeteria.gestion.data.local.SaleQueueEntity
import com.cafeteria.gestion.data.remote.*
import com.cafeteria.gestion.sync.SalesSyncScheduler
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class CafeRepository(
    private val database: CafeDatabase,
    val sessionStore: SessionStore,
    private val apiClient: ApiClient,
    private val contentResolver: ContentResolver,
    private val appContext: android.content.Context
) {
    private val gson = Gson()
    private val products get() = database.productDao()
    private val queue get() = database.saleQueueDao()

    fun isBackendConfigured() = apiClient.isConfigured
    fun isNetworkAvailable(): Boolean {
        val connectivity = appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun restoreSession(): UserSession? = sessionStore.getSession()

    suspend fun login(identifier: String, password: String): UserSession {
        val response = apiClient.service().login(LoginRequest(identifier.trim(), password))
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        val auth = response.body()?.data ?: throw IllegalStateException(response.body()?.message ?: "El servicio no devolvió una sesión válida.")
        require(auth.role in setOf(Roles.OWNER, Roles.WORKER, Roles.CUSTOMER)) { "El servicio devolvió un rol no reconocido." }
        val session = UserSession(auth.userId, auth.displayName, auth.role, auth.accessToken, auth.businessId, auth.businessTimeZone ?: "America/Havana")
        sessionStore.save(session)
        SalesSyncScheduler.enqueueNow(appContext)
        return session
    }

    suspend fun logout() {
        runCatching { apiClient.service().logout() }
        sessionStore.clear()
    }

    suspend fun getCachedProducts(): List<ProductEntity> = products.getAll()

    suspend fun refreshProducts(): List<ProductEntity> {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión para actualizar el catálogo.")
        val visibility = if (session.role == Roles.CUSTOMER) "public" else "all"
        val response = apiClient.service().getProducts(visibility = visibility)
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        val remote = response.body()?.data ?: throw IllegalStateException(response.body()?.message ?: "No se pudo cargar el catálogo.")
        val localById = products.getAll().associateBy { it.id }
        val pendingProductIds = queue.getUnresolved().flatMap { queued ->
            runCatching { gson.fromJson(queued.payloadJson, SalePayload::class.java).items.map { it.productId } }.getOrDefault(emptyList())
        }.toSet()
        val merged = remote.map { dto ->
            val fresh = dto.toEntity(publicView = session.role == Roles.CUSTOMER)
            val local = localById[dto.id]
            // A local stock count already includes this device's unsent sales. Preserve it until those
            // sales are acknowledged, so a refresh cannot accidentally apply the same deduction twice.
            if (local != null && dto.id in pendingProductIds) {
                fresh.copy(stockUnits = local.stockUnits, stockSubunits = local.stockSubunits)
            } else fresh
        }
        // Replace the cache so products hidden/removed from the server do not remain visible
        // indefinitely on customer and worker devices. Pending sale payloads are independent rows.
        database.withTransaction {
            products.clearAll()
            products.upsertAll(merged)
        }
        return products.getAll()
    }

    suspend fun saveProduct(draft: ProductDraft): ProductEntity {
        val payload = linkedMapOf<String, Any?>(
            "name" to draft.name.trim(),
            "description" to draft.description.trim(),
            "categoryName" to draft.categoryName.trim(),
            "unitName" to draft.unitName.trim(),
            "subunitName" to draft.subunitName.trim().ifBlank { null },
            "saleMode" to draft.saleMode,
            "subunitsPerUnit" to draft.subunitsPerUnit,
            "unitPriceCup" to draft.unitPriceCup,
            "unitPriceTransfer" to draft.unitPriceTransfer,
            "subunitPriceCup" to draft.subunitPriceCup,
            "subunitPriceTransfer" to draft.subunitPriceTransfer,
            "minimumStock" to draft.minimumStock,
            "publicVisible" to draft.publicVisible
        )
        // Existing stock is changed only through the audited inventory-adjustment endpoint.
        if (draft.id.isNullOrBlank()) {
            payload["stockUnits"] = draft.stockUnits
            payload["stockSubunits"] = draft.stockSubunits
        }
        val json = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val imagePart = createImagePart(draft.imageUri)
        val service = apiClient.service()
        val response = if (draft.id.isNullOrBlank()) service.createProduct(json, imagePart) else service.updateProduct(draft.id, json, imagePart)
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        val entity = response.body()?.data?.toEntity() ?: throw IllegalStateException(response.body()?.message ?: "No se pudo guardar el producto.")
        products.upsert(entity)
        return entity
    }

    private suspend fun createImagePart(uriString: String?): MultipartBody.Part? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext null
        val uri = Uri.parse(uriString)
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("No se pudo leer la imagen seleccionada.")
        val mime = contentResolver.getType(uri)?.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()
        val body = bytes.toRequestBody(mime)
        MultipartBody.Part.createFormData("image", "product-image", body)
    }

    suspend fun recordSale(
        expectedProduct: ProductEntity,
        unitQuantity: Long,
        subunitQuantity: Long,
        paymentMethod: String
    ): SaleQueueEntity {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Tu sesión no está disponible. Conéctate e inicia sesión de nuevo antes de vender.")
        if (session.role != Roles.WORKER && session.role != Roles.OWNER) throw IllegalStateException("Esta cuenta no tiene permiso para registrar ventas.")
        require(unitQuantity >= 0 && subunitQuantity >= 0 && (unitQuantity > 0 || subunitQuantity > 0)) { "Indica una cantidad mayor que cero." }
        require(paymentMethod in setOf(PaymentMethods.CASH_CUP, PaymentMethods.TRANSFER_CUP)) { "Método de pago no válido." }

        val product = products.findById(expectedProduct.id) ?: throw IllegalStateException("El producto no está guardado en este teléfono. Conéctate para actualizar el catálogo.")
        require(
            product.priceVersion == expectedProduct.priceVersion &&
                product.saleMode == expectedProduct.saleMode &&
                product.subunitsPerUnit == expectedProduct.subunitsPerUnit &&
                product.unitPriceCup == expectedProduct.unitPriceCup &&
                product.unitPriceTransfer == expectedProduct.unitPriceTransfer &&
                product.subunitPriceCup == expectedProduct.subunitPriceCup &&
                product.subunitPriceTransfer == expectedProduct.subunitPriceTransfer
        ) {
            "El precio o formato de este producto cambió desde que abriste la venta. Actualiza el catálogo y vuelve a intentarlo."
        }
        require(product.saleMode == SaleModes.BOTH || (product.saleMode == SaleModes.UNIT && subunitQuantity == 0L) || (product.saleMode == SaleModes.SUBUNIT && unitQuantity == 0L)) {
            "Este producto no admite el formato de cantidad seleccionado."
        }
        if (unitQuantity > 0) require((product.unitPriceCup ?: 0) > 0) { "El producto no tiene un precio en CUP válido para vender por unidad." }
        if (subunitQuantity > 0) require((product.subunitPriceCup ?: 0) > 0) { "El producto no tiene un precio CUP configurado para la subunidad." }
        if (paymentMethod == PaymentMethods.TRANSFER_CUP) {
            if (unitQuantity > 0) require((product.unitPriceTransfer ?: 0) > 0) { "Este producto no tiene habilitado el pago por transferencia para la unidad." }
            if (subunitQuantity > 0) require((product.subunitPriceTransfer ?: 0) > 0) { "Este producto no tiene habilitado el pago por transferencia para la subunidad." }
        }

        val unitPrice = if (paymentMethod == PaymentMethods.CASH_CUP) product.unitPriceCup ?: 0L else product.unitPriceTransfer ?: 0L
        val subunitPrice = if (paymentMethod == PaymentMethods.CASH_CUP) product.subunitPriceCup else product.subunitPriceTransfer
        val total = try {
            Math.addExact(
                Math.multiplyExact(unitQuantity, unitPrice),
                Math.multiplyExact(subunitQuantity, subunitPrice ?: 0L)
            )
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("El importe de la venta supera el máximo permitido.")
        }
        require(total > 0) { "El total de la venta debe ser mayor que cero." }

        // Inventory is revalidated inside the Room transaction below. If another local sale wins
        // the race, the stock calculation is repeated against that new balance before insertion.

        val now = OffsetDateTime.now()
        val saleId = newSaleId()
        val summaryParts = buildList {
            if (unitQuantity > 0) add("$unitQuantity ${product.unitName}")
            if (subunitQuantity > 0) add("$subunitQuantity ${product.subunitName ?: "subunidades"}")
        }
        val item = SaleItemPayload(
            productId = product.id,
            productName = product.name,
            unitName = product.unitName,
            subunitName = product.subunitName,
            unitQuantity = unitQuantity,
            subunitQuantity = subunitQuantity,
            subunitsPerUnitSnapshot = product.subunitsPerUnit,
            priceVersionSnapshot = product.priceVersion,
            unitPriceAppliedCUP = unitPrice,
            subunitPriceAppliedCUP = subunitPrice,
            totalCUP = total
        )
        val payload = SalePayload(
            clientSaleId = saleId,
            soldAt = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            deviceTimeZoneId = currentDeviceZone(),
            deviceOffsetMinutes = currentOffsetMinutes(),
            workerId = session.userId,
            workerName = session.displayName,
            paymentMethod = paymentMethod,
            items = listOf(item),
            totalCUP = total
        )
        val queued = SaleQueueEntity(
            id = saleId,
            soldAt = payload.soldAt,
            createdAtEpochMs = now.toInstant().toEpochMilli(),
            workerId = session.userId,
            workerName = session.displayName,
            summary = "${product.name}: ${summaryParts.joinToString(" + ")}",
            paymentMethod = paymentMethod,
            totalCUP = total,
            payloadJson = gson.toJson(payload),
            status = "PENDING"
        )

        // Save the sale and its stock deduction in one SQLite transaction. Once this returns,
        // the record survives process death and can be retried with exactly the same event time/ID.
        database.withTransaction {
            val current = products.findById(product.id) ?: throw IllegalStateException("El producto ya no está disponible en el inventario local.")
            // Allow stock-only changes caused by another local sale, but do not let a stale dialog
            // charge an old price or apply an old unit/subunit conversion to a changed product.
            require(
                current.priceVersion == product.priceVersion &&
                    current.saleMode == product.saleMode &&
                    current.subunitsPerUnit == product.subunitsPerUnit &&
                    current.unitPriceCup == product.unitPriceCup &&
                    current.unitPriceTransfer == product.unitPriceTransfer &&
                    current.subunitPriceCup == product.subunitPriceCup &&
                    current.subunitPriceTransfer == product.subunitPriceTransfer
            ) { "El precio o formato del producto cambió. Actualiza el catálogo y vuelve a registrar la venta." }
            val updatedProduct = calculateUpdatedStock(current, unitQuantity, subunitQuantity)
            queue.insert(queued)
            products.upsert(updatedProduct)
        }
        SalesSyncScheduler.enqueueNow(appContext)
        return queued
    }

    private fun calculateUpdatedStock(product: ProductEntity, units: Long, subunits: Long): ProductEntity {
        val remaining = StockCalculator.afterSale(product.stockUnits, product.stockSubunits, product.subunitsPerUnit, units, subunits)
        return product.copy(stockUnits = remaining.units, stockSubunits = remaining.subunits)
    }

    suspend fun getRecentSales(): List<SaleSummary> {
        val session = sessionStore.getSession() ?: return emptyList()
        val records = when (session.role) {
            Roles.OWNER -> queue.getRecent()
            Roles.WORKER -> queue.getRecentForWorker(session.userId)
            else -> emptyList()
        }
        return records.map { SaleSummary(it.id, it.soldAt, it.workerId, it.workerName, it.summary, it.paymentMethod, it.totalCUP, it.status, it.lastError) }
    }

    suspend fun pendingCount(): Int {
        val session = sessionStore.getSession() ?: return queue.attentionCount()
        return when (session.role) {
            Roles.OWNER -> queue.attentionCount()
            Roles.WORKER -> queue.attentionCountForWorker(session.userId)
            else -> 0
        }
    }

    suspend fun pendingUploadCountForCurrentUser(): Int {
        val session = sessionStore.getSession() ?: return 0
        return queue.pendingUploadCountForWorker(session.userId)
    }

    suspend fun retrySale(saleId: String) {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión para reintentar la venta.")
        val record = queue.findById(saleId) ?: throw IllegalStateException("No se encontró la venta local.")
        require(record.workerId == session.userId) { "Esta venta solo puede sincronizarse con la cuenta que la registró. Inicia sesión con esa cuenta en este teléfono." }
        require(record.status == "NEEDS_REVIEW") { "Esta venta no necesita reintento." }
        val updated = queue.retryAfterReview(saleId)
        require(updated == 1) { "No se pudo preparar el reintento." }
        SalesSyncScheduler.enqueueNow(appContext)
    }

    suspend fun refreshAnnouncements(): List<Announcement> {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión para consultar los avisos.")
        val response = apiClient.service().getAnnouncements(includeScheduled = session.role == Roles.OWNER)
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        return response.body()?.data.orEmpty().map { it.toModel() }
    }

    suspend fun publishAnnouncement(request: CreateAnnouncementRequest): Announcement {
        val response = apiClient.service().createAnnouncement(request)
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        return response.body()?.data?.toModel() ?: throw IllegalStateException(response.body()?.message ?: "No se pudo publicar el aviso.")
    }

    suspend fun cancelAnnouncement(id: String) {
        val response = apiClient.service().cancelAnnouncement(id)
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
    }

    suspend fun loadDashboard(from: String? = null, to: String? = null): DashboardSummary {
        val response = apiClient.service().getDashboard(from, to)
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        return response.body()?.data?.toModel() ?: throw IllegalStateException(response.body()?.message ?: "No se pudieron cargar las estadísticas.")
    }

    suspend fun loadTeam(): List<TeamMember> {
        val response = apiClient.service().getTeam()
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        return response.body()?.data.orEmpty().map { it.toModel() }
    }

    suspend fun createWorker(request: CreateWorkerRequest): TeamMember {
        val response = apiClient.service().createWorker(request)
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        return response.body()?.data?.toModel() ?: throw IllegalStateException(response.body()?.message ?: "No se pudo crear la cuenta.")
    }

    suspend fun setWorkerActive(workerId: String, active: Boolean): TeamMember {
        val response = apiClient.service().updateWorkerStatus(workerId, mapOf("active" to active))
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        return response.body()?.data?.toModel() ?: throw IllegalStateException(response.body()?.message ?: "No se pudo actualizar la cuenta.")
    }

    suspend fun submitInventoryAdjustment(request: InventoryAdjustmentRequest): ProductEntity {
        val response = apiClient.service().createInventoryAdjustment(request)
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        val product = response.body()?.data?.toEntity() ?: throw IllegalStateException(response.body()?.message ?: "No se pudo actualizar el inventario.")
        products.upsert(product)
        return product
    }

    suspend fun getRemoteSales(from: String? = null, to: String? = null, workerId: String? = null): List<SaleSummary> {
        val response = apiClient.service().getSales(from, to, workerId, 200)
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        return response.body()?.data.orEmpty().map {
            SaleSummary(it.clientSaleId ?: it.id, it.soldAt, it.workerId, it.workerName, it.summary, it.paymentMethod, it.totalCUP, it.status)
        }
    }

    suspend fun registerPushToken(token: String, deviceId: String) {
        if (!apiClient.isConfigured) throw ApiNotConfiguredException()
        val response = apiClient.service().registerPushToken(mapOf(
            "token" to token,
            "deviceId" to deviceId,
            "platform" to "ANDROID"
        ))
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
    }

    suspend fun applySyncStockFromServer() {
        // Keep local deductions while any sale is unresolved, including rejected rows awaiting review.
        if (queue.getUnresolved().isNotEmpty()) return
        runCatching { refreshProducts() }
    }

    private fun apiError(code: Int, body: String?): IllegalStateException {
        val message = runCatching { gson.fromJson(body, ApiEnvelope::class.java)?.message }.getOrNull()
        return IllegalStateException(message?.takeIf { it.isNotBlank() } ?: when (code) {
            401 -> "La sesión caducó. Conéctate e inicia sesión de nuevo."
            403 -> "Tu cuenta no tiene permiso para realizar esta operación."
            409 -> "El inventario cambió mientras se registraba la operación. Revisa el producto."
            422 -> "Hay datos que el servicio no pudo validar."
            in 500..599 -> "El servicio no está disponible en este momento. Inténtalo más tarde."
            else -> "No se pudo completar la operación (código $code)."
        })
    }
}
