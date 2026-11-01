package com.cafeteria.gestion.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.cafeteria.gestion.data.local.CafeDatabase
import com.cafeteria.gestion.data.local.ProductEntity
import com.cafeteria.gestion.data.local.ProductQueueEntity
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.UUID

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
    private val productQueue get() = database.productQueueDao()

    fun isBackendConfigured() = apiClient.isConfigured
    fun isNetworkAvailable(): Boolean {
        val connectivity = appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun restoreSession(): UserSession? = sessionStore.getSession()

    suspend fun login(identifier: String, password: String): UserSession {
        val loginValue = identifier.trim()
        require(loginValue.isNotBlank()) { "Escribe tu nombre o usuario." }
        require(password.isNotBlank()) { "Escribe tu contraseña." }
        if (apiClient.isConfigured && isNetworkAvailable()) {
            try {
                val response = apiClient.service().login(LoginRequest(loginValue, password))
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    val failure = if (response.code() == 401) {
                        IllegalStateException("El nombre o usuario y la contraseña no son correctos.")
                    } else apiError(response.code(), errorBody)
                    // A newly registered offline customer may still be waiting for first sync.
                    // Preserve that local session only for the exact pending credentials.
                    val pending = sessionStore.pendingCustomerRegistration()
                    if (response.code() == 401 && pending != null &&
                        pending.displayName.equals(loginValue, ignoreCase = true) && pending.password == password) {
                        val localPending = sessionStore.verifyOfflineCustomer(loginValue, password)
                        if (localPending != null) {
                            sessionStore.save(localPending)
                            SalesSyncScheduler.enqueueNow(appContext)
                            return localPending
                        }
                    }
                    throw failure
                }
                val auth = response.body()?.data ?: throw IllegalStateException(response.body()?.message ?: "El servicio no devolvió una sesión válida.")
                require(auth.role in setOf(Roles.OWNER, Roles.WORKER, Roles.CUSTOMER)) { "El servicio devolvió un rol no reconocido." }
                val session = auth.toUserSession()
                if (session.role == Roles.CUSTOMER) sessionStore.saveCustomerCredentials(session, loginValue, password)
                else sessionStore.save(session)
                SalesSyncScheduler.enqueueNow(appContext)
                return session
            } catch (error: java.io.IOException) {
                // A transport failure may be a sleeping backend or unstable connection.
                // Try the local customer verifier below; never use this fallback after a 401.
            }
        }
        val localCustomer = sessionStore.verifyOfflineCustomer(loginValue, password)
            ?: throw IllegalStateException("No se pudo validar el acceso sin conexión. Las cuentas del equipo necesitan conexión; los clientes deben haber iniciado sesión o creado su cuenta en este teléfono anteriormente.")
        sessionStore.save(localCustomer)
        SalesSyncScheduler.enqueueNow(appContext)
        return localCustomer
    }

    /** Customer registration is allowed offline. The local account is usable on this device
     * immediately and its encrypted registration payload is sent by WorkManager when online. */
    suspend fun registerCustomer(displayName: String, password: String): UserSession {
        val name = displayName.trim()
        require(name.length in 3..100) { "El nombre debe tener entre 3 y 100 caracteres para crear la cuenta." }
        require(password.length in 10..200) { "La contraseña debe tener al menos 10 caracteres." }
        require(!sessionStore.hasPendingCustomerRegistration()) {
            "Hay una cuenta creada sin conexión que todavía espera enviarse al servidor. Inicia sesión con esa cuenta y conecta el teléfono para terminar la sincronización antes de crear otra."
        }
        val identifier = "customer-${UUID.randomUUID()}"
        if (apiClient.isConfigured && isNetworkAvailable()) {
            try {
                val response = apiClient.service().registerCustomer(RegisterCustomerRequest(name, identifier, password))
                if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
                val auth = response.body()?.data ?: throw IllegalStateException("El servicio no devolvió la cuenta creada.")
                val session = auth.toUserSession()
                require(session.role == Roles.CUSTOMER) { "El servidor no creó una cuenta de cliente." }
                sessionStore.saveCustomerCredentials(session, name, password)
                SalesSyncScheduler.enqueueNow(appContext)
                return session
            } catch (error: java.io.IOException) {
                // Treat a dropped/unstable connection as offline. The same credentials will be
                // registered later, protected at rest with Android Keystore.
            }
        }
        val local = sessionStore.saveLocalCustomerAccount(name, identifier, password)
        SalesSyncScheduler.enqueueNow(appContext)
        return local
    }

    /** Backward-compatible method name; authentication now always uses the shared login endpoint. */
    suspend fun loginCustomer(identifier: String, password: String): UserSession = login(identifier, password)

    suspend fun hasPendingCustomerRegistration(): Boolean = sessionStore.hasPendingCustomerRegistration()

    /** Idempotent recovery: if the server accepted registration but its response was lost, a
     * retry receives IDENTIFIER_IN_USE and then signs in with the exact same credentials. */
    suspend fun syncPendingCustomerRegistration(): Boolean {
        val pending = sessionStore.pendingCustomerRegistration()
        if (pending == null) {
            if (sessionStore.hasPendingCustomerRegistration()) {
                throw IllegalStateException("La cuenta pendiente sigue protegida en el teléfono, pero no se pudo descifrar para enviarla. No se ha eliminado.")
            }
            return false
        }
        if (!apiClient.isConfigured || !isNetworkAvailable()) return false
        val service = apiClient.service()
        val register = service.registerCustomer(RegisterCustomerRequest(pending.displayName, pending.identifier, pending.password))
        val auth = if (register.isSuccessful) {
            register.body()?.data ?: throw IllegalStateException("El servidor no devolvió la cuenta registrada.")
        } else if (register.code() == 409) {
            val login = service.login(LoginRequest(pending.displayName, pending.password))
            if (!login.isSuccessful) throw apiError(login.code(), login.errorBody()?.string())
            login.body()?.data ?: throw IllegalStateException("El servidor no devolvió la sesión de cliente.")
        } else {
            throw apiError(register.code(), register.errorBody()?.string())
        }
        val serverSession = auth.toUserSession()
        require(serverSession.role == Roles.CUSTOMER) { "La cuenta pendiente no corresponde a un cliente." }
        sessionStore.completePendingCustomerRegistration(pending.localUserId, serverSession)
        return true
    }

    private fun com.cafeteria.gestion.data.remote.AuthResponse.toUserSession() = UserSession(
        userId = userId,
        displayName = displayName,
        role = role,
        accessToken = accessToken,
        businessId = businessId,
        businessTimeZone = businessTimeZone ?: "America/Havana"
    )

    suspend fun logout() {
        runCatching { apiClient.service().logout() }
        sessionStore.clear()
    }

    suspend fun getCachedProducts(): List<ProductEntity> = products.getAll()

    suspend fun refreshProducts(): List<ProductEntity> {
        val session = sessionStore.getSession()
        val publicView = session == null || session.role == Roles.CUSTOMER
        val visibility = if (publicView) "public" else "all"
        val response = apiClient.service().getProducts(visibility = visibility)
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        val remote = response.body()?.data ?: throw IllegalStateException(response.body()?.message ?: "No se pudo cargar el catálogo.")
        val localById = products.getAll().associateBy { it.id }
        val unresolvedSaleProductIds = queue.getUnresolved().flatMap { queued ->
            runCatching { gson.fromJson(queued.payloadJson, SalePayload::class.java).items.map { it.productId } }.getOrDefault(emptyList())
        }.toSet()
        val unresolvedProductIds = productQueue.getUnresolved().map { it.productId }.toSet()
        val merged = remote.map { dto ->
            val fresh = dto.toEntity(publicView = publicView)
            val local = localById[dto.id]
            when {
                local != null && dto.id in unresolvedProductIds -> local
                local != null && dto.id in unresolvedSaleProductIds -> fresh.copy(stockUnits = local.stockUnits, stockSubunits = local.stockSubunits, minimumStock = local.minimumStock, stockIsApproximate = local.stockIsApproximate)
                publicView && local != null -> fresh.copy(stockUnits = local.stockUnits, stockSubunits = local.stockSubunits, minimumStock = local.minimumStock, stockIsApproximate = local.stockIsApproximate)
                else -> fresh
            }
        }.toMutableList()
        val remoteIds = merged.map { it.id }.toSet()
        // A product created offline has a temporary local ID and must remain visible until the
        // owner queue successfully creates it on the server.
        localById.values.filter { (it.id in unresolvedProductIds || (publicView && !it.publicVisible)) && it.id !in remoteIds }.forEach { merged += it }
        database.withTransaction {
            products.clearAll()
            products.upsertAll(merged)
        }
        return products.getAll()
    }

    /**
     * Produces a monotonic local outbox order even when two actions happen in the same
     * millisecond or the handset clock moves backwards. The actual soldAt timestamp is
     * stored separately and is never changed by this ordering sequence.
     */
    private suspend fun nextQueueOrderTimestamp(): Long {
        val latestProduct = productQueue.getUnresolved().maxOfOrNull { it.createdAtEpochMs } ?: 0L
        val latestSale = queue.getUnresolved().maxOfOrNull { it.createdAtEpochMs } ?: 0L
        val latest = maxOf(latestProduct, latestSale)
        val nextAfterPending = if (latest > 0L && latest < Long.MAX_VALUE) latest + 1L else latest
        return maxOf(System.currentTimeMillis(), nextAfterPending)
    }

    suspend fun saveProduct(draft: ProductDraft): ProductEntity {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión como administradora para gestionar productos.")
        require(session.role == Roles.OWNER) { "Solo la administradora puede crear o editar productos." }

        val isNew = draft.id.isNullOrBlank()
        val productId = draft.id ?: "local-${UUID.randomUUID()}"
        val previous = if (isNew) null else products.findById(productId)
            ?: throw IllegalStateException("No se encontró el producto guardado en este teléfono.")
        require(previous?.stockIsApproximate != true) {
            "Este teléfono solo tiene la disponibilidad pública, no el inventario exacto. Conéctate y actualiza el inventario antes de editar este producto."
        }
        val savedImageUri = draft.imageUri?.let { copyImageIntoAppStorage(it) }
        val finalImageUri = savedImageUri ?: previous?.imageUrl
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
        if (isNew) {
            payload["stockUnits"] = draft.stockUnits
            payload["stockSubunits"] = draft.stockSubunits
        }
        val now = System.currentTimeMillis()
        val localEntity = ProductEntity(
            id = productId,
            name = draft.name.trim(),
            description = draft.description.trim(),
            categoryName = draft.categoryName.trim(),
            imageUrl = finalImageUri,
            unitName = draft.unitName.trim(),
            subunitName = draft.subunitName.trim().ifBlank { null },
            saleMode = draft.saleMode,
            subunitsPerUnit = draft.subunitsPerUnit,
            unitPriceCup = draft.unitPriceCup,
            unitPriceTransfer = draft.unitPriceTransfer,
            subunitPriceCup = draft.subunitPriceCup,
            subunitPriceTransfer = draft.subunitPriceTransfer,
            stockUnits = if (isNew) draft.stockUnits else previous!!.stockUnits,
            stockSubunits = if (draft.subunitsPerUnit != null) {
                if (isNew) draft.stockSubunits else previous!!.stockSubunits
            } else null,
            minimumStock = draft.minimumStock,
            publicVisible = draft.publicVisible,
            updatedAt = OffsetDateTime.now().toString(),
            priceVersion = "local-${UUID.randomUUID()}",
            stockIsApproximate = false
        )
        val queueItem = ProductQueueEntity(
            id = UUID.randomUUID().toString(),
            productId = productId,
            ownerId = session.userId,
            operation = if (isNew) "CREATE" else "UPDATE",
            payloadJson = gson.toJson(payload),
            imageUri = savedImageUri,
            createdAtEpochMs = now
        )
        database.withTransaction {
            products.upsert(localEntity)
            productQueue.insert(queueItem)
        }
        SalesSyncScheduler.enqueueNow(appContext)
        return localEntity
    }

    private suspend fun copyImageIntoAppStorage(uriString: String): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo leer la fotografía seleccionada.")
        val mime = contentResolver.getType(uri) ?: "image/jpeg"
        val extension = when (mime.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val directory = File(appContext.filesDir, "pending-product-images").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.$extension")
        input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }
        if (file.length() > 5L * 1024L * 1024L) {
            file.delete()
            throw IllegalArgumentException("La fotografía supera el límite de 5 MiB.")
        }
        Uri.fromFile(file).toString()
    }

    private suspend fun createImagePart(uriString: String?): MultipartBody.Part? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext null
        val uri = Uri.parse(uriString)
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("No se pudo leer la imagen seleccionada.")
        val inferredMime = contentResolver.getType(uri) ?: when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val mime = inferredMime.toMediaTypeOrNull()
        val body = bytes.toRequestBody(mime)
        MultipartBody.Part.createFormData("image", "product-image", body)
    }

    class QueuedOperationException(val httpCode: Int, message: String) : IllegalStateException(message)

    suspend fun syncQueuedProduct(item: ProductQueueEntity): ProductEntity {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión con la cuenta administradora para sincronizar el catálogo.")
        require(session.role == Roles.OWNER && session.userId == item.ownerId) {
            "Los cambios pendientes de catálogo pertenecen a la cuenta administradora que los creó. Inicia sesión con esa cuenta."
        }
        val body = item.payloadJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val image = createImagePart(item.imageUri)
        val service = apiClient.service()
        val response = if (item.operation == "CREATE") service.createProduct(body, image)
        else service.updateProduct(item.productId, body, image)
        if (!response.isSuccessful) throw QueuedOperationException(response.code(), apiError(response.code(), response.errorBody()?.string()).message ?: "No se pudo sincronizar el producto.")
        val remote = response.body()?.data?.toEntity()
            ?: throw IllegalStateException(response.body()?.message ?: "El servidor no devolvió el producto sincronizado.")
        val oldId = item.productId
        val local = products.findById(oldId)
        val futureProductEdits = productQueue.getPending().any { it.id != item.id && it.productId == oldId && it.createdAtEpochMs > item.createdAtEpochMs }
        val finalEntity = if (local != null && futureProductEdits) {
            local.copy(id = remote.id, stockUnits = local.stockUnits, stockSubunits = local.stockSubunits)
        } else if (local != null) {
            remote.copy(stockUnits = local.stockUnits, stockSubunits = local.stockSubunits)
        } else remote
        database.withTransaction {
            if (oldId != remote.id) products.deleteById(oldId)
            products.upsert(finalEntity)
            productQueue.remapProductId(oldId, remote.id)
            productQueue.deleteById(item.id)
            // Every unresolved sale is chronologically after this catalogue event because the
            // worker processes a single ordered outbox. Update its server ID/version before upload.
            queue.getPending().forEach { sale ->
                val payload = runCatching { gson.fromJson(sale.payloadJson, SalePayload::class.java) }.getOrNull() ?: return@forEach
                val changed = payload.items.any { it.productId == oldId }
                if (changed) {
                    val updatedPayload = payload.copy(items = payload.items.map { saleItem ->
                        if (saleItem.productId == oldId) saleItem.copy(productId = remote.id, priceVersionSnapshot = remote.priceVersion)
                        else saleItem
                    })
                    queue.updatePayload(sale.id, gson.toJson(updatedPayload))
                }
            }
        }
        // Remove a copied local image only after the server image replaces it in the cache.
        val previousLocalImage = local?.imageUrl
        if (!previousLocalImage.isNullOrBlank() && previousLocalImage.startsWith("file:") && finalEntity.imageUrl != previousLocalImage) {
            runCatching { File(Uri.parse(previousLocalImage).path.orEmpty()).delete() }
        }
        return finalEntity
    }

    suspend fun updateQueuedProductStatus(id: String, status: String, error: String?) {
        productQueue.updateStatus(id, status, error)
    }

    suspend fun pendingProductCountForCurrentOwner(): Int {
        val session = sessionStore.getSession() ?: return 0
        if (session.role != Roles.OWNER) return 0
        return productQueue.attentionCountForOwner(session.userId)
    }

    suspend fun retryProductChangesForCurrentOwner(): Int {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión como administradora.")
        require(session.role == Roles.OWNER) { "Solo la administradora puede reintentar cambios de catálogo." }
        val changed = productQueue.retryOwnerReview(session.userId)
        SalesSyncScheduler.enqueueNow(appContext)
        return changed
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
        require(!product.stockIsApproximate) { "Este teléfono solo tiene la disponibilidad pública del producto, no el inventario exacto. Conéctate e inicia sesión para descargar las existencias antes de vender sin conexión." }
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
        val queueOrderTimestamp = nextQueueOrderTimestamp()
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
            totalCUP = total,
            appVersion = com.cafeteria.gestion.BuildConfig.VERSION_NAME
        )
        val queued = SaleQueueEntity(
            id = saleId,
            soldAt = payload.soldAt,
            createdAtEpochMs = queueOrderTimestamp,
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
        if (product.saleMode == SaleModes.SUBUNIT && product.subunitsPerUnit == null && product.stockUnits == 0L && units == 0L) return product
        val remaining = StockCalculator.afterSale(product.stockUnits, product.stockSubunits, product.subunitsPerUnit, units, subunits, allowUntrackedSubunitSales = product.saleMode == SaleModes.SUBUNIT)
        return product.copy(stockUnits = remaining.units, stockSubunits = remaining.subunits)
    }

    /**
     * Product quantities from sales still waiting in this phone's durable outbox.
     * They are intentionally kept separate from server-confirmed totals so the owner
     * never mistakes an offline sale for a sale already accepted by the backend.
     */
    suspend fun getPendingProductSales(from: String, to: String): List<PendingProductSalesSummary> {
        val session = sessionStore.getSession() ?: return emptyList()
        if (session.role != Roles.OWNER) return emptyList()
        val fromInstant = OffsetDateTime.parse(from).toInstant()
        val toInstant = OffsetDateTime.parse(to).toInstant()
        val records = queue.getUnresolved()
        data class Key(val productName: String, val unitName: String, val subunitName: String?)
        data class Totals(var units: Long = 0, var subunits: Long = 0, var cash: Long = 0, var transfer: Long = 0)
        val totals = linkedMapOf<Key, Totals>()
        records.forEach { record ->
            if (record.status != "PENDING" && record.status != "NEEDS_REVIEW") return@forEach
            val payload = runCatching { gson.fromJson(record.payloadJson, SalePayload::class.java) }.getOrNull() ?: return@forEach
            val soldAt = runCatching { OffsetDateTime.parse(payload.soldAt).toInstant() }.getOrNull() ?: return@forEach
            if (soldAt.isBefore(fromInstant) || !soldAt.isBefore(toInstant)) return@forEach
            payload.items.orEmpty().forEach { item ->
                val key = Key(item.productName, item.unitName, item.subunitName)
                val total = totals.getOrPut(key) { Totals() }
                total.units += item.unitQuantity
                total.subunits += item.subunitQuantity
                when (payload.paymentMethod) {
                    PaymentMethods.CASH_CUP -> total.cash += item.totalCUP
                    PaymentMethods.TRANSFER_CUP -> total.transfer += item.totalCUP
                }
            }
        }
        return totals.map { (key, value) ->
            PendingProductSalesSummary(key.productName, key.unitName, key.subunitName, value.units, value.subunits, value.cash, value.transfer)
        }.sortedByDescending { it.cashCup + it.transferCup }
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
        val session = sessionStore.getSession() ?: return 0
        return when (session.role) {
            Roles.OWNER -> queue.attentionCount() + productQueue.attentionCountForOwner(session.userId)
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
        val session = sessionStore.getSession()
        val response = apiClient.service().getAnnouncements(includeScheduled = session?.role == Roles.OWNER)
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
        if (queue.getUnresolved().isNotEmpty() || productQueue.getUnresolved().isNotEmpty()) return
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
