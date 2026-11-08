package com.cafeteria.gestion.data

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.cafeteria.gestion.data.local.CafeDatabase
import com.cafeteria.gestion.data.local.ProductEntity
import com.cafeteria.gestion.data.local.ProductQueueEntity
import com.cafeteria.gestion.data.local.SaleQueueEntity
import com.cafeteria.gestion.data.local.AdminOperationEntity
import com.cafeteria.gestion.data.local.TransferBundleEntity
import com.cafeteria.gestion.data.transfer.TransferPackageManager
import com.cafeteria.gestion.data.transfer.TransferPackagePayload
import com.cafeteria.gestion.data.transfer.TransferPackageDocument
import com.cafeteria.gestion.data.transfer.TransferHistoryItem
import com.cafeteria.gestion.data.transfer.TransferImportSummary
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
import java.security.MessageDigest

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
    private val adminQueue get() = database.adminOperationDao()
    private val transferBundles get() = database.transferBundleDao()
    private val transferManager by lazy { TransferPackageManager(appContext, gson) }
    private val maxBusinessInteger = 9_000_000_000_000L

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
        val current = sessionStore.getSession()
        if (current?.role != Roles.LOCAL_STATION && !current?.accessToken.isNullOrBlank()) {
            runCatching { apiClient.service().logout() }
        }
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
        val unresolvedAdminProductIds = adminQueue.getUnresolved().map { it.productId }.toSet()
        val merged = remote.map { dto ->
            val fresh = dto.toEntity(publicView = publicView)
            val local = localById[dto.id]
            when {
                local != null && dto.id in unresolvedProductIds -> local
                local != null && (dto.id in unresolvedSaleProductIds || dto.id in unresolvedAdminProductIds) -> fresh.copy(
                    stockUnits = local.stockUnits, stockSubunits = local.stockSubunits,
                    minimumStock = local.minimumStock, stockIsApproximate = local.stockIsApproximate,
                    inventoryRevision = local.inventoryRevision
                )
                publicView && local != null -> fresh.copy(stockUnits = local.stockUnits, stockSubunits = local.stockSubunits, minimumStock = local.minimumStock, stockIsApproximate = local.stockIsApproximate)
                else -> fresh
            }
        }.toMutableList()
        val remoteIds = merged.map { it.id }.toSet()
        // A product created offline has a temporary local ID and must remain visible until the
        // owner queue successfully creates it on the server.
        localById.values.filter {
            (it.id in unresolvedProductIds || (!publicView && !it.active) || (publicView && it.active && !it.publicVisible)) && it.id !in remoteIds
        }.forEach { merged += it }
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
        val latestAdmin = adminQueue.getUnresolved().maxOfOrNull { it.createdAtEpochMs } ?: 0L
        val latest = maxOf(latestProduct, latestSale, latestAdmin)
        val nextAfterPending = if (latest > 0L && latest < Long.MAX_VALUE) latest + 1L else latest
        return maxOf(System.currentTimeMillis(), nextAfterPending)
    }

    suspend fun saveProduct(draft: ProductDraft): ProductEntity {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión como administradora para gestionar productos.")
        require(session.role == Roles.OWNER) { "Solo la administradora puede crear o editar productos." }
        val businessId = session.businessId ?: throw IllegalStateException("La cafetería de esta sesión no está identificada.")
        require(draft.name.trim().isNotEmpty() && draft.name.trim().length <= 160) { "El nombre del producto debe tener entre 1 y 160 caracteres." }
        require(draft.categoryName.trim().isNotEmpty() && draft.categoryName.trim().length <= 100) { "Indica una categoría válida." }
        require(draft.unitName.trim().isNotEmpty() && draft.unitName.trim().length <= 40) { "Indica un nombre de unidad válido." }
        require(draft.stockUnits in 0..maxBusinessInteger && (draft.stockSubunits == null || draft.stockSubunits in 0..maxBusinessInteger)) { "Las existencias superan el límite permitido." }
        require(draft.minimumStock in 0..maxBusinessInteger) { "El mínimo de inventario supera el límite permitido." }
        listOf(draft.unitPriceCup, draft.unitPriceTransfer, draft.subunitPriceCup, draft.subunitPriceTransfer).filterNotNull().forEach {
            require(it in 1..maxBusinessInteger) { "Los precios deben ser enteros CUP positivos dentro del límite permitido." }
        }
        require(draft.subunitsPerUnit == null || draft.subunitsPerUnit in 1..1_000_000) { "La equivalencia de subunidades no es válida." }
        if (draft.subunitsPerUnit != null) {
            val total = Math.addExact(Math.multiplyExact(draft.stockUnits, draft.subunitsPerUnit.toLong()), draft.stockSubunits ?: 0L)
            require(total <= 9_007_199_254_740_991L) { "El inventario equivalente supera el límite seguro de cálculo." }
        } else require((draft.stockSubunits ?: 0L) == 0L) { "No indiques subunidades si no se conoce su equivalencia." }

        val isNew = draft.id.isNullOrBlank()
        val productId = draft.id ?: "local-${UUID.randomUUID()}"
        val previous = if (isNew) null else products.findById(productId)
            ?: throw IllegalStateException("No se encontró el producto guardado en este teléfono.")
        require(previous?.active != false) { "Este producto está desactivado y no se puede editar." }
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
        val now = OffsetDateTime.now().toString()
        val queueOrderTimestamp = nextQueueOrderTimestamp()
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
            updatedAt = now,
            priceVersion = "local-${UUID.randomUUID()}",
            stockIsApproximate = false,
            inventoryRevision = previous?.inventoryRevision ?: 0L,
            catalogRevision = previous?.catalogRevision ?: 0L
        )
        val productOperationId = UUID.randomUUID().toString()
        payload["clientOperationId"] = productOperationId
        payload["baseCatalogRevision"] = previous?.catalogRevision
        val queueItem = ProductQueueEntity(
            id = productOperationId,
            productId = productId,
            ownerId = session.userId,
            operation = if (isNew) "CREATE" else "UPDATE",
            payloadJson = gson.toJson(payload),
            imageUri = savedImageUri,
            createdAtEpochMs = queueOrderTimestamp,
            businessId = businessId
        )
        database.withTransaction {
            products.upsert(localEntity)
            productQueue.insert(queueItem)
        }
        SalesSyncScheduler.enqueueNow(appContext)
        return localEntity
    }

    suspend fun renameCategoryOffline(oldName: String, newName: String): Int {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión como administradora para editar categorías.")
        require(session.role == Roles.OWNER) { "Solo la administradora puede editar categorías." }
        val businessId = session.businessId ?: throw IllegalStateException("La cafetería no está identificada.")
        val oldValue = oldName.trim()
        val newValue = newName.trim()
        require(oldValue.isNotBlank() && oldValue != "Todas") { "Selecciona una categoría existente." }
        require(newValue.isNotBlank() && newValue.length <= 100) { "El nombre de categoría debe tener entre 1 y 100 caracteres." }
        require(!oldValue.equals(newValue, ignoreCase = true)) { "El nuevo nombre debe ser diferente." }
        val currentProducts = products.getAll().filter { it.active && it.categoryName.equals(oldValue, ignoreCase = true) }
        require(currentProducts.isNotEmpty()) { "No hay productos activos en esa categoría." }
        val collision = products.getAll().any { it.active && it.categoryName.equals(newValue, ignoreCase = true) }
        require(!collision) { "Ya existe otra categoría con ese nombre." }
        val now = OffsetDateTime.now().toString()
        database.withTransaction {
            var sequenceTime = nextQueueOrderTimestamp()
            currentProducts.forEach { product ->
                require(!product.stockIsApproximate) {
                    "El producto ${product.name} solo tiene inventario público. Actualiza el catálogo administrativo antes de cambiar la categoría."
                }
                val operationId = UUID.randomUUID().toString()
                val payload = linkedMapOf<String, Any?>(
                    "name" to product.name,
                    "description" to product.description,
                    "categoryName" to newValue,
                    "unitName" to product.unitName,
                    "subunitName" to product.subunitName,
                    "saleMode" to product.saleMode,
                    "subunitsPerUnit" to product.subunitsPerUnit,
                    "unitPriceCup" to product.unitPriceCup,
                    "unitPriceTransfer" to product.unitPriceTransfer,
                    "subunitPriceCup" to product.subunitPriceCup,
                    "subunitPriceTransfer" to product.subunitPriceTransfer,
                    "minimumStock" to product.minimumStock,
                    "publicVisible" to product.publicVisible,
                    "clientOperationId" to operationId,
                    "baseCatalogRevision" to product.catalogRevision
                )
                productQueue.insert(
                    ProductQueueEntity(
                        id = operationId, productId = product.id, ownerId = session.userId,
                        operation = "UPDATE", payloadJson = gson.toJson(payload), imageUri = null,
                        createdAtEpochMs = sequenceTime, businessId = businessId
                    )
                )
                products.upsert(product.copy(categoryName = newValue, updatedAt = now))
                sequenceTime = Math.addExact(sequenceTime, 1L)
            }
        }
        SalesSyncScheduler.enqueueNow(appContext)
        return currentProducts.size
    }

    suspend fun deactivateProductOffline(productId: String): ProductEntity {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión como administradora para desactivar productos.")
        require(session.role == Roles.OWNER) { "Solo la administradora puede desactivar productos." }
        val businessId = session.businessId ?: throw IllegalStateException("La cafetería de esta sesión no está identificada.")
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "android-${UUID.randomUUID()}"
        lateinit var result: ProductEntity
        database.withTransaction {
            val current = products.findById(productId)
                ?: throw IllegalStateException("No se encontró el producto en este dispositivo.")
            require(current.active) { "Este producto ya está desactivado." }
            val queueItem = ProductQueueEntity(
                id = UUID.randomUUID().toString(), productId = current.id, ownerId = session.userId,
                operation = "DEACTIVATE", payloadJson = gson.toJson(mapOf("operation" to "DEACTIVATE", "productId" to current.id)),
                imageUri = null, createdAtEpochMs = nextQueueOrderTimestamp(), businessId = businessId
            )
            result = current.copy(active = false, publicVisible = false, updatedAt = OffsetDateTime.now().toString())
            productQueue.insert(queueItem)
            products.upsert(result)
        }
        SalesSyncScheduler.enqueueNow(appContext)
        return result
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
        require(session.role == Roles.OWNER && session.userId == item.ownerId && session.businessId == item.businessId) {
            "Los cambios pendientes de catálogo pertenecen a otra cuenta administradora o cafetería. Inicia sesión con la cuenta que los creó."
        }
        val service = apiClient.service()
        if (item.operation == "DEACTIVATE") {
            val response = service.deleteProduct(item.productId)
            if (!response.isSuccessful) throw QueuedOperationException(response.code(), apiError(response.code(), response.errorBody()?.string()).message ?: "No se pudo desactivar el producto.")
            val local = products.findById(item.productId)
                ?: throw IllegalStateException("El producto desactivado no existe en la copia local; la confirmación se conserva para revisión.")
            val deactivated = local.copy(active = false, publicVisible = false, updatedAt = OffsetDateTime.now().toString())
            database.withTransaction {
                products.upsert(deactivated)
                productQueue.deleteById(item.id)
            }
            return deactivated
        }
        val outgoingProductJson = if (item.operation == "CREATE") {
            val stored = runCatching { gson.fromJson(item.payloadJson, Map::class.java) as? Map<String, Any?> }
                .getOrNull() ?: throw IllegalStateException("No se pudo leer el alta local. La operación se conserva.")
            gson.toJson(stored + ("clientOperationId" to item.id))
        } else item.payloadJson
        val body = outgoingProductJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val image = createImagePart(item.imageUri)
        val response = if (item.operation == "CREATE") service.createProduct(body, image)
        else service.updateProduct(item.productId, body, image)
        if (!response.isSuccessful) throw QueuedOperationException(response.code(), apiError(response.code(), response.errorBody()?.string()).message ?: "No se pudo sincronizar el producto.")
        val remote = response.body()?.data?.toEntity()
            ?: throw IllegalStateException(response.body()?.message ?: "El servidor no devolvió el producto sincronizado.")
        val oldId = item.productId
        val local = products.findById(oldId)
        val futureProductEdits = productQueue.getPending().any { it.id != item.id && it.productId == oldId && it.createdAtEpochMs > item.createdAtEpochMs }
        val unresolvedLocalInventory = if (local == null) false else {
            val pendingSaleUsesProduct = queue.getUnresolved().any { sale ->
                runCatching { gson.fromJson(sale.payloadJson, SalePayload::class.java) }
                    .getOrNull()?.items?.any { it.productId == oldId || it.productId == remote.id } == true
            }
            val pendingAdjustmentUsesProduct = adminQueue.getUnresolved().any {
                it.productId == oldId || it.productId == remote.id
            }
            pendingSaleUsesProduct || pendingAdjustmentUsesProduct
        }
        val finalEntity = when {
            local == null -> remote
            futureProductEdits -> local.copy(id = remote.id, catalogRevision = remote.catalogRevision)
            unresolvedLocalInventory -> remote.copy(
                stockUnits = local.stockUnits,
                stockSubunits = local.stockSubunits,
                inventoryRevision = local.inventoryRevision
            )
            // No local stock events remain: the just-confirmed server response is authoritative.
            // This avoids replacing newer remote sales with a stale cached stock snapshot.
            else -> remote
        }
        database.withTransaction {
            if (oldId != remote.id) products.deleteById(oldId)
            products.upsert(finalEntity)
            productQueue.remapProductId(oldId, remote.id)
            adminQueue.remapProductId(oldId, remote.id)
            // Future edits made on this same offline chain must base themselves on the
            // revision returned for the operation just acknowledged. Their UUID remains stable.
            productQueue.getPending().filter { it.id != item.id && it.productId == remote.id && it.createdAtEpochMs > item.createdAtEpochMs }.forEach { future ->
                val futurePayload = runCatching { gson.fromJson(future.payloadJson, Map::class.java) as? Map<String, Any?> }.getOrNull()
                if (futurePayload != null) {
                    productQueue.updatePayload(future.id, gson.toJson(futurePayload + ("baseCatalogRevision" to remote.catalogRevision)))
                }
            }
            // The admin event's indexed productId and its immutable request payload must
            // reference the same official server ID after an offline CREATE is acknowledged.
            adminQueue.getUnresolved().filter { it.productId == remote.id }.forEach { movement ->
                val adjustment = runCatching { gson.fromJson(movement.payloadJson, InventoryAdjustmentRequest::class.java) }.getOrNull()
                if (adjustment?.productId == oldId && movement.createdAtEpochMs > item.createdAtEpochMs) {
                    adminQueue.updatePayload(
                        movement.id,
                        gson.toJson(adjustment.copy(productId = remote.id, baseCatalogRevision = remote.catalogRevision, basePriceVersion = remote.priceVersion))
                    )
                }
            }
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
        require(unitQuantity in 0..1_000_000_000L && subunitQuantity in 0..1_000_000_000L && (unitQuantity > 0 || subunitQuantity > 0)) { "Las cantidades deben estar entre cero y mil millones, y alguna debe ser mayor que cero." }
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
        require(total in 1..maxBusinessInteger) { "El total de la venta debe ser mayor que cero y no superar el límite CUP permitido." }

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
            status = "PENDING",
            businessId = session.businessId ?: throw IllegalStateException("La cafetería no está identificada.")
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
            val updatedProduct = calculateUpdatedStock(current, unitQuantity, subunitQuantity).copy(
                inventoryRevision = Math.addExact(current.inventoryRevision, 1L)
            )
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
                total.units = Math.addExact(total.units, item.unitQuantity)
                total.subunits = Math.addExact(total.subunits, item.subunitQuantity)
                when (payload.paymentMethod) {
                    PaymentMethods.CASH_CUP -> total.cash = Math.addExact(total.cash, item.totalCUP)
                    PaymentMethods.TRANSFER_CUP -> total.transfer = Math.addExact(total.transfer, item.totalCUP)
                }
            }
        }
        return totals.map { (key, value) ->
            PendingProductSalesSummary(key.productName, key.unitName, key.subunitName, value.units, value.subunits, value.cash, value.transfer)
        }.sortedWith { left, right ->
            val leftTotal = java.math.BigInteger.valueOf(left.cashCup).add(java.math.BigInteger.valueOf(left.transferCup))
            val rightTotal = java.math.BigInteger.valueOf(right.cashCup).add(java.math.BigInteger.valueOf(right.transferCup))
            rightTotal.compareTo(leftTotal)
        }
    }

    suspend fun getRecentSales(): List<SaleSummary> {
        val session = sessionStore.getSession() ?: return emptyList()
        val records = when (session.role) {
            Roles.OWNER -> session.businessId?.let { queue.getRecentForBusiness(it, session.userId) }.orEmpty()
            Roles.WORKER -> queue.getRecentForWorker(session.userId)
            else -> emptyList()
        }
        return records.map { SaleSummary(it.id, it.soldAt, it.workerId, it.workerName, it.summary, it.paymentMethod, it.totalCUP, it.status, it.lastError) }
    }

    suspend fun pendingCount(): Int {
        val session = sessionStore.getSession() ?: return 0
        return when (session.role) {
            Roles.OWNER -> (session.businessId?.let { queue.attentionCountForBusiness(it, session.userId) } ?: 0) + productQueue.attentionCountForOwner(session.userId) + adminQueue.attentionCountForOwner(session.userId)
            Roles.WORKER -> session.businessId?.let { queue.attentionCountForBusiness(it, session.userId) } ?: queue.attentionCountForWorker(session.userId)
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
        return adjustInventoryOffline(request.productId, request.unitDelta, request.subunitDelta, request.reason)
    }

    /** Owner stock adjustments are committed locally first; server delivery is a separate event. */
    suspend fun adjustInventoryOffline(productId: String, unitsDelta: Long, subunitsDelta: Long?, reason: String): ProductEntity {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión como administradora para ajustar el inventario.")
        require(session.role == Roles.OWNER) { "Solo la administradora puede ajustar existencias." }
        val businessId = session.businessId ?: throw IllegalStateException("La cafetería de esta sesión no está identificada.")
        require(reason.isNotBlank()) { "Indica el motivo del ajuste." }
        require(unitsDelta != 0L || (subunitsDelta ?: 0L) != 0L) { "El ajuste debe modificar alguna cantidad." }
        require(unitsDelta in -maxBusinessInteger..maxBusinessInteger && (subunitsDelta == null || subunitsDelta in -maxBusinessInteger..maxBusinessInteger)) { "El ajuste supera el límite permitido." }
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "android-${UUID.randomUUID()}"
        val now = OffsetDateTime.now()
        val operationId = UUID.randomUUID().toString()
        lateinit var updated: ProductEntity
        database.withTransaction {
            val current = products.findById(productId)
                ?: throw IllegalStateException("No se encontró el producto en el inventario de este teléfono.")
            require(!current.stockIsApproximate) {
                "Este teléfono solo tiene disponibilidad pública, no existencias exactas. Descarga el inventario completo antes de ajustarlo."
            }
            val balance = StockCalculator.afterAdjustment(
                current.stockUnits, current.stockSubunits, current.subunitsPerUnit, unitsDelta, subunitsDelta
            )
            val nextRevision = Math.addExact(current.inventoryRevision, 1L)
            val sequence = Math.addExact(adminQueue.maxSequenceForDevice(deviceId), 1L)
            val request = InventoryAdjustmentRequest(
                productId = current.id,
                unitDelta = unitsDelta,
                subunitDelta = subunitsDelta,
                reason = reason.trim(),
                operationId = operationId,
                deviceId = deviceId,
                occurredAt = now.toString(),
                localSequence = sequence,
                baseInventoryRevision = current.inventoryRevision,
                baseCatalogRevision = current.catalogRevision,
                basePriceVersion = current.priceVersion
            )
            val queueItem = AdminOperationEntity(
                id = operationId,
                productId = current.id,
                ownerId = session.userId,
                businessId = businessId,
                deviceId = deviceId,
                operation = "STOCK_ADJUSTMENT",
                payloadJson = gson.toJson(request),
                catalogRevision = null,
                inventoryRevision = current.inventoryRevision,
                occurredAt = now.toString(),
                createdAtEpochMs = nextQueueOrderTimestamp(),
                localSequence = sequence
            )
            updated = current.copy(
                stockUnits = balance.units,
                stockSubunits = balance.subunits,
                inventoryRevision = nextRevision,
                updatedAt = now.toString()
            )
            adminQueue.insert(queueItem)
            products.upsert(updated)
        }
        SalesSyncScheduler.enqueueNow(appContext)
        return updated
    }

    suspend fun recordPhysicalCountOffline(
        productId: String,
        countedUnits: Long,
        countedSubunits: Long?,
        reason: String
    ): ProductEntity {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión como administradora para registrar un conteo físico.")
        require(session.role == Roles.OWNER) { "Solo la administradora puede confirmar conteos físicos." }
        val businessId = session.businessId ?: throw IllegalStateException("La cafetería de esta sesión no está identificada.")
        require(countedUnits in 0..maxBusinessInteger && (countedSubunits == null || countedSubunits in 0..maxBusinessInteger)) {
            "Las cantidades del conteo deben ser enteros no negativos dentro del límite permitido."
        }
        require(reason.trim().length >= 3) { "Escribe el motivo o referencia del conteo físico." }
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "android-${UUID.randomUUID()}"
        val occurredAt = OffsetDateTime.now()
        val operationId = UUID.randomUUID().toString()
        lateinit var updated: ProductEntity
        database.withTransaction {
            val current = products.findById(productId)
                ?: throw IllegalStateException("No se encontró el producto en el inventario de este teléfono.")
            require(current.active) { "No se puede contar un producto desactivado." }
            require(!current.stockIsApproximate) {
                "Este teléfono solo tiene disponibilidad pública, no existencias exactas. Descarga el inventario completo antes de contar."
            }
            val unitDelta = Math.subtractExact(countedUnits, current.stockUnits)
            val subunitDelta = if (current.subunitsPerUnit != null) {
                val knownCurrent = current.stockSubunits
                    ?: throw IllegalStateException("El inventario de subunidades necesita revisión antes de hacer un conteo.")
                val requested = countedSubunits
                    ?: throw IllegalArgumentException("Indica cuántas subunidades encontraste físicamente.")
                require(requested < current.subunitsPerUnit.toLong()) {
                    "El conteo de subunidades debe ser menor que ${current.subunitsPerUnit}."
                }
                Math.subtractExact(requested, knownCurrent)
            } else {
                require(countedSubunits == null || countedSubunits == 0L) {
                    "No se pueden contar subunidades exactas mientras la equivalencia sea desconocida."
                }
                null
            }
            val balance = StockCalculator.afterAdjustment(
                current.stockUnits, current.stockSubunits, current.subunitsPerUnit, unitDelta, subunitDelta, allowNoop = true
            )
            require(balance.units == countedUnits && balance.subunits == if (current.subunitsPerUnit == null) null else countedSubunits) {
                "El conteo físico no pudo normalizarse con las equivalencias actuales."
            }
            val sequence = Math.addExact(adminQueue.maxSequenceForDevice(deviceId), 1L)
            val request = InventoryAdjustmentRequest(
                productId = current.id, unitDelta = unitDelta, subunitDelta = subunitDelta,
                reason = reason.trim(), operationId = operationId, deviceId = deviceId,
                occurredAt = occurredAt.toString(), localSequence = sequence,
                baseInventoryRevision = current.inventoryRevision, baseCatalogRevision = current.catalogRevision, basePriceVersion = current.priceVersion,
                operationType = "PHYSICAL_COUNT", countedUnits = countedUnits, countedSubunits = countedSubunits
            )
            adminQueue.insert(
                AdminOperationEntity(
                    id = operationId, productId = current.id, ownerId = session.userId, businessId = businessId,
                    deviceId = deviceId, operation = "PHYSICAL_COUNT", payloadJson = gson.toJson(request),
                    catalogRevision = null, inventoryRevision = current.inventoryRevision,
                    occurredAt = occurredAt.toString(), createdAtEpochMs = nextQueueOrderTimestamp(), localSequence = sequence
                )
            )
            updated = current.copy(
                stockUnits = balance.units, stockSubunits = balance.subunits,
                inventoryRevision = Math.addExact(current.inventoryRevision, 1L), updatedAt = occurredAt.toString()
            )
            products.upsert(updated)
        }
        SalesSyncScheduler.enqueueNow(appContext)
        return updated
    }

    suspend fun syncQueuedInventoryAdjustment(item: AdminOperationEntity): ProductEntity {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión con la cuenta administradora para sincronizar el inventario.")
        require(session.role == Roles.OWNER && session.userId == item.ownerId && session.businessId == item.businessId) {
            "Este ajuste pertenece a otra cuenta administradora o cafetería."
        }
        val request = runCatching { gson.fromJson(item.payloadJson, InventoryAdjustmentRequest::class.java) }
            .getOrNull() ?: throw IllegalStateException("No se pudo leer el ajuste local. El registro se conserva.")
        val response = apiClient.service().createInventoryAdjustment(request)
        if (!response.isSuccessful) throw QueuedOperationException(
            response.code(), apiError(response.code(), response.errorBody()?.string()).message ?: "No se pudo sincronizar el ajuste."
        )
        val remote = response.body()?.data?.toEntity()
            ?: throw IllegalStateException("El servidor no devolvió el inventario confirmado.")
        adminQueue.updateStatus(item.id, "SYNCED", null, remote.inventoryRevision)
        val current = products.findById(item.productId)
        if (current != null && adminQueue.unresolvedCountForProduct(item.productId) == 0) {
            products.upsert(current.copy(inventoryRevision = remote.inventoryRevision))
        }
        return products.findById(item.productId) ?: remote
    }

    fun generateTransferCode(): String = transferManager.newTransferCode()

    /** Owner-only local pairing. The server registration is retried with the same station UUID when online. */
    suspend fun generateStationPairingCode(): String {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión como administradora.")
        require(session.role == Roles.OWNER) { "Solo Yuri puede crear una estación local." }
        val businessId = session.businessId ?: throw IllegalStateException("La cafetería no está identificada en esta sesión.")
        val stationId = UUID.randomUUID().toString()
        val secret = transferManager.newTransferCode()
        val code = "YCST1|$businessId|$stationId|$secret"
        sessionStore.saveStationAuthorization(code, businessId, stationId)
        // Offline creation is intentionally local-pending. It is registered on the backend
        // automatically after connectivity returns; the UI must not claim remote activation.
        if (isBackendConfigured() && isNetworkAvailable()) {
            // Keep the local station identity even if the API is temporarily unavailable.
            // Owner refresh will retry registration with the same UUID and secret.
            runCatching { registerPendingStationIfOnline() }
        }
        return code
    }

    suspend fun currentStationPairingCode(): String? = sessionStore.getStationAuthorization()

    suspend fun registerPendingStationIfOnline(): Boolean {
        val session = sessionStore.getSession() ?: return false
        if (session.role != Roles.OWNER || !isBackendConfigured() || !isNetworkAvailable()) return false
        val code = sessionStore.getStationAuthorization() ?: return false
        val parts = code.split('|')
        require(parts.size == 4 && parts[0] == "YCST1" && parts[1] == session.businessId) {
            "La vinculación local guardada no pertenece a esta cafetería. Genera un código nuevo."
        }
        val response = apiClient.service().registerLocalStation(
            LocalStationRegistrationRequest(stationId = parts[2], secret = parts[3])
        )
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        return true
    }

    suspend fun loadLocalStations(): List<LocalStationDto> {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión como administradora.")
        require(session.role == Roles.OWNER) { "Solo la administradora puede consultar las estaciones." }
        if (!isBackendConfigured() || !isNetworkAvailable()) return emptyList()
        val response = apiClient.service().getLocalStations()
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        return response.body()?.data.orEmpty()
    }

    suspend fun revokeLocalStation(stationId: String): LocalStationDto {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión como administradora.")
        require(session.role == Roles.OWNER) { "Solo la administradora puede revocar estaciones." }
        require(isBackendConfigured() && isNetworkAvailable()) {
            "La revocación remota necesita conexión. La estación sigue autorizada en el servidor hasta completar esta acción."
        }
        val response = apiClient.service().revokeLocalStation(stationId)
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        val result = response.body()?.data ?: throw IllegalStateException("El servidor no confirmó la revocación.")
        val localCode = sessionStore.getStationAuthorization()
        if (localCode?.split('|')?.getOrNull(2) == stationId) sessionStore.revokeStationAuthorization()
        return result
    }

    suspend fun revokeStationPairingCode(): Boolean {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión como administradora.")
        require(session.role == Roles.OWNER) { "Solo Yuri puede revocar una estación local." }
        val code = sessionStore.getStationAuthorization()
        if (code != null && isBackendConfigured() && isNetworkAvailable()) {
            val parts = code.split('|')
            require(parts.size == 4 && parts[0] == "YCST1") { "El código guardado está dañado; no se pudo revocar remotamente." }
            val response = apiClient.service().revokeLocalStation(parts[2])
            if (!response.isSuccessful && response.code() != 404) {
                throw apiError(response.code(), response.errorBody()?.string())
            }
            sessionStore.revokeStationAuthorization()
            return true
        }
        // An offline phone cannot revoke a server-side identity. Keep the code so the
        // owner can retry revocation when online instead of falsely claiming success.
        if (code != null && isBackendConfigured()) {
            throw IllegalStateException("No hay conexión. La estación no se ha revocado en el servidor; vuelve a intentarlo cuando haya internet.")
        }
        sessionStore.revokeStationAuthorization()
        return false
    }

    suspend fun sendStationHeartbeatIfOnline(): Boolean {
        val session = sessionStore.getSession() ?: return false
        if (session.role != Roles.LOCAL_STATION || !isBackendConfigured() || !isNetworkAvailable()) return false
        val stationId = sessionStore.getLocalStationId() ?: return false
        val secret = sessionStore.getLocalStationSecret() ?: return false
        val stats = localStationStats()
        val operationCount = Math.addExact(
            Math.addExact(stats.storedSales.toLong(), stats.storedProductChanges.toLong()),
            stats.storedInventoryChanges.toLong()
        )
        val response = apiClient.service().localStationHeartbeat(
            stationId, secret, LocalStationHeartbeatRequest(storedOperationCount = operationCount)
        )
        if (!response.isSuccessful) throw apiError(response.code(), response.errorBody()?.string())
        return true
    }

    suspend fun activateLocalStation(code: String): UserSession {
        val parts = code.trim().split('|')
        require(parts.size == 4 && parts[0] == "YCST1") { "El código no es un código de estación YuriCoofe válido." }
        val businessId = parts[1]
        val stationId = parts[2]
        val secret = parts[3]
        require(runCatching { UUID.fromString(businessId) }.isSuccess) { "El código no contiene un identificador válido de cafetería." }
        require(runCatching { UUID.fromString(stationId) }.isSuccess) { "El código no contiene una identidad válida de estación." }
        require(secret.length >= 20) { "El código de estación está incompleto." }
        return sessionStore.activateLocalStation(businessId, stationId, secret)
    }

    private suspend fun resolveTransferSecret(session: UserSession, suppliedCode: String): String {
        if (session.role == Roles.LOCAL_STATION) {
            return sessionStore.getLocalStationSecret()
                ?: throw IllegalStateException("No se encontró la clave local de esta estación.")
        }
        val parts = suppliedCode.trim().split('|')
        if (parts.firstOrNull() == "YCST1") {
            require(parts.size == 4) { "El código de estación está incompleto." }
            require(parts[1] == session.businessId) { "El código pertenece a otra cafetería." }
            return parts[3]
        }
        return suppliedCode
    }

    /** Export unconfirmed work as an encrypted, portable handover file. Source rows are never deleted. */
    suspend fun exportTransferPackage(destination: Uri, transferCode: String): String = withContext(Dispatchers.IO) {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión para preparar una transferencia.")
        require(session.role == Roles.OWNER || session.role == Roles.WORKER) {
            "Esta cuenta no puede exportar operaciones de inventario o ventas."
        }
        val resolvedTransferCode = resolveTransferSecret(session, transferCode)
        val businessId = session.businessId ?: throw IllegalStateException("La cafetería de esta sesión no está identificada.")
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "android-${UUID.randomUUID()}"
        // Include every unresolved sale belonging to this business, not just the current
        // worker. This is what lets the next shift re-deliver a coworker's sales.
        val sales = queue.getUnresolved().filter { it.businessId == businessId || (it.businessId.isBlank() && it.workerId == session.userId) }
        val productOperations = productQueue.getUnresolvedForBusiness(businessId)
        val inventoryOperations = adminQueue.getUnresolved().filter { it.businessId == businessId }
        require(sales.isNotEmpty() || productOperations.isNotEmpty() || inventoryOperations.isNotEmpty()) {
            "No hay ventas ni cambios pendientes que transferir desde este dispositivo."
        }

        val images = linkedMapOf<String, String>()
        var imageBytesTotal = 0L
        productOperations.forEach { operation ->
            val imageUri = operation.imageUri ?: return@forEach
            val bytes = contentResolver.openInputStream(Uri.parse(imageUri))?.use { readLimited(it, 5 * 1024 * 1024) }
                ?: throw IllegalStateException("No se pudo incluir una fotografía pendiente del producto. La operación original se conserva.")
            require(bytes.size <= 5 * 1024 * 1024) { "Una fotografía pendiente supera el límite de 5 MiB." }
            imageBytesTotal = Math.addExact(imageBytesTotal, bytes.size.toLong())
            require(imageBytesTotal <= 20L * 1024L * 1024L) { "Las fotografías superan el límite total de 20 MiB por transferencia." }
            images[operation.id] = Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        val history = accumulatedTransferHistory()
        val bundleId = UUID.randomUUID().toString()
        val createdAt = OffsetDateTime.now().toString()
        val payload = TransferPackagePayload(
            bundleId = bundleId,
            businessId = businessId,
            sourceDeviceId = deviceId,
            sourceUserId = session.userId,
            sourceRole = session.role,
            createdAt = createdAt,
            sales = sales,
            productOperations = productOperations,
            adminOperations = inventoryOperations,
            productImagesBase64 = images,
            transferHistory = history
        )
        val payloadJson = gson.toJson(payload)
        val digest = transferManager.sha256(payloadJson)
        val document = gson.toJson(TransferPackageDocument(payload, digest))
        val encrypted = transferManager.encode(document, resolvedTransferCode)
        val output = contentResolver.openOutputStream(destination, "w")
            ?: throw IllegalStateException("Android no permitió crear el archivo de transferencia.")
        output.use { it.write(encrypted); it.flush() }

        val operationIds = sales.map { it.id } + productOperations.map { it.id } + inventoryOperations.map { it.id }
        transferBundles.insertIfAbsent(
            TransferBundleEntity(
                bundleId = bundleId,
                sourceDeviceId = deviceId,
                destinationDeviceId = null,
                direction = "EXPORTED",
                contentHash = digest,
                operationIdsJson = gson.toJson(operationIds),
                status = "EXPORTED_UNCONFIRMED",
                createdAt = createdAt,
                createdAtEpochMs = System.currentTimeMillis(),
                encryptedPayloadAtRest = transferManager.sealAtRest(payloadJson)
            )
        )
        "$bundleId · ${sales.size} venta(s), ${productOperations.size} cambio(s) de producto y ${inventoryOperations.size} ajuste(s). El origen conserva todos los datos hasta recibir confirmación del destino."
    }

    /** Import is idempotent by bundle UUID and by each operation UUID; a conflicting reuse is rejected. */
    suspend fun importTransferPackage(source: Uri, transferCode: String): TransferImportSummary = withContext(Dispatchers.IO) {
        val session = sessionStore.getSession() ?: throw IllegalStateException("Inicia sesión para recibir una transferencia.")
        require(session.role in setOf(Roles.OWNER, Roles.WORKER, Roles.LOCAL_STATION)) {
            "Esta cuenta no puede recibir paquetes de trabajo."
        }
        val businessId = session.businessId ?: throw IllegalStateException("La cafetería de esta sesión no está identificada.")
        val resolvedTransferCode = resolveTransferSecret(session, transferCode)
        val encrypted = contentResolver.openInputStream(source)?.use { input ->
            readLimited(input, 40 * 1024 * 1024)
        } ?: throw IllegalStateException("No se pudo leer el archivo recibido.")
        val decrypted = transferManager.decode(encrypted, resolvedTransferCode)
        val document = runCatching { gson.fromJson(decrypted, TransferPackageDocument::class.java) }.getOrNull()
            ?: throw IllegalArgumentException("El contenido del paquete no tiene un formato válido.")
        val payload = document.payload ?: throw IllegalArgumentException("El paquete no contiene operaciones.")
        require(document.formatVersion == 1 && payload.formatVersion == 1) { "La versión del paquete no es compatible." }
        require(payload.businessId == businessId) { "El paquete pertenece a otra cafetería. No se importó ningún dato." }
        require(payload.bundleId.isNotBlank() && payload.sourceDeviceId.isNotBlank() && payload.sourceUserId.isNotBlank()) {
            "Faltan identificadores obligatorios en el paquete."
        }
        val expectedHash = transferManager.sha256(gson.toJson(payload))
        require(MessageDigest.isEqual(expectedHash.toByteArray(), document.sha256.orEmpty().toByteArray())) {
            "La comprobación SHA-256 del paquete no coincide. No se importó ningún dato."
        }
        // A later package from a recipient can carry a durable receipt for an earlier export.
        // We only acknowledge an exact bundle ID + content hash; the sender's original rows stay.
        payload.transferHistory.forEach { receipt ->
            val original = transferBundles.findById(receipt.bundleId)
            if (original != null && original.direction == "EXPORTED" && original.contentHash == receipt.contentHash) {
                transferBundles.updateStatus(receipt.bundleId, "ACKNOWLEDGED", payload.sourceDeviceId, null)
            }
        }
        val existingBundle = transferBundles.findById(payload.bundleId)
        if (existingBundle != null) {
            require(existingBundle.contentHash == expectedHash) {
                "El identificador de transferencia ya existe con contenido diferente. Se rechazó el paquete."
            }
            return@withContext TransferImportSummary(payload.bundleId, 0, 0, 0, 0, "Este paquete ya había sido recibido; no se duplicó ninguna operación.")
        }

        // A LOCAL_STATION is a sealed inbox only: it never executes sales or admin queues,
        // never edits the catalog/stock and never receives a backend credential.
        if (session.role == Roles.LOCAL_STATION) {
            val storedPayload = gson.toJson(payload)
            val operationIds = payload.sales.map { it.id } + payload.productOperations.map { it.id } + payload.adminOperations.map { it.id }
            transferBundles.insertIfAbsent(
                TransferBundleEntity(
                    bundleId = payload.bundleId,
                    sourceDeviceId = payload.sourceDeviceId,
                    destinationDeviceId = sessionStore.getLocalStationId(),
                    direction = "IMPORTED",
                    contentHash = expectedHash,
                    operationIdsJson = gson.toJson(operationIds),
                    status = "RECEIVED_VERIFIED",
                    createdAt = payload.createdAt,
                    createdAtEpochMs = System.currentTimeMillis(),
                    encryptedPayloadAtRest = transferManager.sealAtRest(storedPayload)
                )
            )
            return@withContext TransferImportSummary(
                payload.bundleId, payload.sales.size, payload.productOperations.size, payload.adminOperations.size, 0,
                "Paquete recibido y guardado cifrado en la estación. No se modificó el inventario ni se ejecutó ninguna operación."
            )
        }

        // Reject a reused operation ID with different content before changing the database.
        payload.sales.forEach { incoming ->
            val existing = queue.findById(incoming.id)
            require(existing == null || existing.payloadJson == incoming.payloadJson) {
                "El identificador de una venta ya existe con contenido diferente. Se canceló toda la importación."
            }
        }
        payload.productOperations.forEach { incoming ->
            val existing = productQueue.findById(incoming.id)
            require(existing == null || (existing.productId == incoming.productId && existing.payloadJson == incoming.payloadJson)) {
                "El identificador de un cambio de producto ya existe con contenido diferente. Se canceló toda la importación."
            }
        }
        payload.adminOperations.forEach { incoming ->
            val existing = adminQueue.findById(incoming.id)
            require(existing == null || (existing.productId == incoming.productId && existing.payloadJson == incoming.payloadJson)) {
                "El identificador de un ajuste ya existe con contenido diferente. Se canceló toda la importación."
            }
        }

        val importedImageUris = mutableMapOf<String, String>()
        payload.productImagesBase64.forEach { (operationId, encoded) ->
            val bytes = runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrElse {
                throw IllegalArgumentException("Una fotografía del paquete está dañada.")
            }
            require(bytes.size <= 5 * 1024 * 1024) { "Una fotografía recibida supera 5 MiB." }
            val directory = File(appContext.filesDir, "pending-product-images").apply { mkdirs() }
            val file = File(directory, "received-${UUID.randomUUID()}.img")
            file.writeBytes(bytes)
            importedImageUris[operationId] = Uri.fromFile(file).toString()
        }

        var newSales = 0
        var newProducts = 0
        var newInventory = 0
        var duplicates = 0
        database.withTransaction {
            payload.sales.forEach { incoming ->
                val inserted = queue.insertIfAbsent(incoming.copy(businessId = businessId))
                if (inserted == -1L) duplicates++ else newSales++
            }
            payload.productOperations.forEach { incoming ->
                val authorizedOwner = session.role == Roles.OWNER && incoming.ownerId == session.userId
                val needsOwnerReview = session.role == Roles.OWNER && !authorizedOwner
                val copy = incoming.copy(
                    businessId = businessId,
                    imageUri = importedImageUris[incoming.id] ?: incoming.imageUri,
                    status = if (needsOwnerReview) "NEEDS_REVIEW" else incoming.status,
                    lastError = if (needsOwnerReview) "Este cambio pertenece a otra identidad administradora. Yuri debe revisarlo antes de sincronizarlo." else incoming.lastError
                )
                val inserted = productQueue.insertIfAbsent(copy)
                if (inserted == -1L) duplicates++ else newProducts++
            }
            payload.adminOperations.forEach { incoming ->
                val authorizedOwner = session.role == Roles.OWNER && incoming.ownerId == session.userId
                val needsOwnerReview = session.role == Roles.OWNER && !authorizedOwner
                val copy = incoming.copy(
                    status = if (needsOwnerReview) "NEEDS_REVIEW" else incoming.status,
                    lastError = if (needsOwnerReview) "Este ajuste pertenece a otra identidad administradora. Yuri debe revisarlo antes de sincronizarlo." else incoming.lastError
                )
                val inserted = adminQueue.insertIfAbsent(copy)
                if (inserted == -1L) duplicates++ else newInventory++
            }
            transferBundles.insertIfAbsent(
                TransferBundleEntity(
                    bundleId = payload.bundleId,
                    sourceDeviceId = payload.sourceDeviceId,
                    destinationDeviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID),
                    direction = "IMPORTED",
                    contentHash = expectedHash,
                    operationIdsJson = gson.toJson(payload.sales.map { it.id } + payload.productOperations.map { it.id } + payload.adminOperations.map { it.id }),
                    status = "RECEIVED_VERIFIED",
                    createdAt = payload.createdAt,
                    createdAtEpochMs = System.currentTimeMillis(),
                    encryptedPayloadAtRest = transferManager.sealAtRest(gson.toJson(payload))
                )
            )
        }
        SalesSyncScheduler.enqueueNow(appContext)
        TransferImportSummary(
            payload.bundleId, newSales, newProducts, newInventory, duplicates,
            "Paquete recibido y verificado. Las operaciones originales se conservaron; las que no pertenecen a esta cuenta quedaron para revisión."
        )
    }

    data class LocalStationStats(
        val receivedBundles: Int,
        val storedSales: Int,
        val storedProductChanges: Int,
        val storedInventoryChanges: Int,
        val latestReceipt: String?,
        val pendingForwardBundles: Int
    )

    suspend fun localStationStats(): LocalStationStats = withContext(Dispatchers.IO) {
        val session = sessionStore.getSession() ?: throw IllegalStateException("No hay una estación activa.")
        require(session.role == Roles.LOCAL_STATION) { "Esta pantalla es exclusiva de la estación local." }
        val bundles = transferBundles.getAllImported()
        val sales = linkedSetOf<String>()
        val productChanges = linkedSetOf<String>()
        val inventoryChanges = linkedSetOf<String>()
        bundles.forEach { bundle ->
            val json = runCatching { transferManager.openAtRest(bundle.encryptedPayloadAtRest) }.getOrNull() ?: return@forEach
            val payload = runCatching { gson.fromJson(json, TransferPackagePayload::class.java) }.getOrNull() ?: return@forEach
            payload.sales.forEach { sales.add(it.id) }
            payload.productOperations.forEach { productChanges.add(it.id) }
            payload.adminOperations.forEach { inventoryChanges.add(it.id) }
        }
        LocalStationStats(
            receivedBundles = bundles.size,
            storedSales = sales.size,
            storedProductChanges = productChanges.size,
            storedInventoryChanges = inventoryChanges.size,
            latestReceipt = bundles.maxByOrNull { it.createdAtEpochMs }?.createdAt,
            pendingForwardBundles = bundles.size
        )
    }

    /** Consolidates the station's encrypted inbox into one portable encrypted file. Original bundles remain intact. */
    suspend fun exportCollectedStationPackage(destination: Uri): String = withContext(Dispatchers.IO) {
        val session = sessionStore.getSession() ?: throw IllegalStateException("No hay una estación activa.")
        require(session.role == Roles.LOCAL_STATION) { "Solo la estación local puede consolidar su bandeja." }
        val businessId = session.businessId ?: throw IllegalStateException("La estación no tiene cafetería vinculada.")
        val secret = sessionStore.getLocalStationSecret() ?: throw IllegalStateException("Falta la clave de esta estación.")
        val bundles = transferBundles.getAllImported()
        require(bundles.isNotEmpty()) { "La estación todavía no ha recibido paquetes." }
        val payloads = bundles.map { bundle ->
            val json = transferManager.openAtRest(bundle.encryptedPayloadAtRest)
            gson.fromJson(json, TransferPackagePayload::class.java)
                ?: throw IllegalStateException("Una transferencia almacenada no se puede leer. Se conserva para revisión.")
        }
        val salesById = linkedMapOf<String, SaleQueueEntity>()
        val productsById = linkedMapOf<String, ProductQueueEntity>()
        val adminById = linkedMapOf<String, AdminOperationEntity>()
        val images = linkedMapOf<String, String>()
        payloads.forEach { payload ->
            payload.sales.forEach { row ->
                val old = salesById.putIfAbsent(row.id, row)
                require(old == null || old.payloadJson == row.payloadJson) { "Una venta repetida tiene datos distintos entre paquetes. La estación conserva todo y no exportó el conjunto." }
            }
            payload.productOperations.forEach { row ->
                val old = productsById.putIfAbsent(row.id, row)
                require(old == null || (old.productId == row.productId && old.payloadJson == row.payloadJson)) { "Un cambio de producto repetido contiene datos distintos. Requiere revisión." }
            }
            payload.adminOperations.forEach { row ->
                val old = adminById.putIfAbsent(row.id, row)
                require(old == null || (old.productId == row.productId && old.payloadJson == row.payloadJson)) { "Un ajuste repetido contiene datos distintos. Requiere revisión." }
            }
            payload.productImagesBase64.forEach { (id, base64) ->
                val old = images.putIfAbsent(id, base64)
                require(old == null || old == base64) { "Una fotografía de producto repetida es inconsistente." }
            }
        }
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "station-${UUID.randomUUID()}"
        val createdAt = OffsetDateTime.now().toString()
        val bundleId = UUID.randomUUID().toString()
        val history = accumulatedTransferHistory()
        val payload = TransferPackagePayload(
            bundleId = bundleId, businessId = businessId, sourceDeviceId = deviceId,
            sourceUserId = session.userId, sourceRole = Roles.LOCAL_STATION, createdAt = createdAt,
            sales = salesById.values.toList(), productOperations = productsById.values.toList(),
            adminOperations = adminById.values.toList(), productImagesBase64 = images, transferHistory = history
        )
        val payloadJson = gson.toJson(payload)
        val digest = transferManager.sha256(payloadJson)
        val encrypted = transferManager.encode(gson.toJson(TransferPackageDocument(payload, digest)), secret)
        contentResolver.openOutputStream(destination, "w")?.use { it.write(encrypted); it.flush() }
            ?: throw IllegalStateException("Android no permitió crear el paquete consolidado.")
        transferBundles.insertIfAbsent(
            TransferBundleEntity(
                bundleId = bundleId, sourceDeviceId = deviceId, destinationDeviceId = null,
                direction = "EXPORTED", contentHash = digest,
                operationIdsJson = gson.toJson(salesById.keys + productsById.keys + adminById.keys),
                status = "EXPORTED_UNCONFIRMED", createdAt = createdAt,
                createdAtEpochMs = System.currentTimeMillis(),
                encryptedPayloadAtRest = transferManager.sealAtRest(payloadJson)
            )
        )
        "$bundleId · ${salesById.size} ventas, ${productsById.size} cambios de producto y ${adminById.size} movimientos. La estación conserva intactos los paquetes originales."
    }

    suspend fun transferHistory() = transferBundles.getRecent(100)

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
        if (queue.getUnresolved().isNotEmpty() || productQueue.getUnresolved().isNotEmpty() || adminQueue.getUnresolved().isNotEmpty()) return
        runCatching { refreshProducts() }
    }

    /** Carries nested receipts forward so a source device can learn about later handoffs. */
    private suspend fun accumulatedTransferHistory(): List<TransferHistoryItem> {
        val local = transferBundles.getAll().map {
            TransferHistoryItem(
                it.bundleId, it.sourceDeviceId, it.destinationDeviceId, it.direction,
                it.contentHash, it.operationIdsJson, it.status, it.createdAt
            )
        }
        val inherited = transferBundles.getAllImported().flatMap { bundle ->
            val json = runCatching { transferManager.openAtRest(bundle.encryptedPayloadAtRest) }
                .getOrElse { throw IllegalStateException("No se pudo leer el historial cifrado de una transferencia recibida. No se omitió.") }
            val payload = runCatching { gson.fromJson(json, TransferPackagePayload::class.java) }
                .getOrNull() ?: throw IllegalStateException("Una transferencia recibida no contiene un historial válido.")
            payload.transferHistory
        }
        val byId = linkedMapOf<String, TransferHistoryItem>()
        fun rank(status: String): Int = when (status) {
            "SERVER_CONFIRMED" -> 6
            "ACKNOWLEDGED" -> 5
            "FORWARDED" -> 4
            "VERIFIED", "RECEIVED_VERIFIED", "RECEIVED" -> 3
            "EXPORTED_UNCONFIRMED" -> 1
            else -> 2
        }
        (local + inherited).forEach { item ->
            val previous = byId[item.bundleId]
            if (previous == null) {
                byId[item.bundleId] = item
            } else {
                require(previous.contentHash == item.contentHash && previous.sourceDeviceId == item.sourceDeviceId) {
                    "El historial contiene un identificador de paquete reutilizado con datos diferentes. Requiere revisión."
                }
                if (rank(item.status) > rank(previous.status)) byId[item.bundleId] = item
            }
        }
        return byId.values.toList()
    }

    private fun readLimited(input: java.io.InputStream, maximumBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maximumBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total = Math.addExact(total, read)
            require(total <= maximumBytes) { "El archivo supera el límite permitido de ${maximumBytes / (1024 * 1024)} MiB." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
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
