package com.cafeteria.gestion.sync

import android.content.Context
import com.cafeteria.gestion.BuildConfig
import com.cafeteria.gestion.CafeteriaApplication
import com.cafeteria.gestion.data.CafeRepository
import com.cafeteria.gestion.data.Roles
import com.cafeteria.gestion.data.SalePayload
import com.cafeteria.gestion.data.remote.ApiEnvelope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/** Resultado de un intento de sincronización. Nunca se queda en silencio: siempre hay un motivo legible. */
sealed class SyncOutcome {
    object NothingToSend : SyncOutcome()
    data class Done(val sent: Int, val needsReview: Int) : SyncOutcome()
    /** Fallo temporal (red, servidor dormido, 5xx): se reintenta solo. */
    data class Retry(val reason: String) : SyncOutcome()
    /** Requiere acción de la persona (sesión caducada, otra cuenta, app sin servidor): no tiene sentido reintentar ciego. */
    data class Blocked(val reason: String) : SyncOutcome()

    fun userMessage(): String = when (this) {
        is NothingToSend -> "No hay nada pendiente por enviar."
        is Done -> when {
            sent == 0 && needsReview == 0 -> "Todo está al día."
            needsReview > 0 -> "Se enviaron $sent operación(es). $needsReview necesitan revisión (míralas en Productos o Ventas)."
            else -> "Sincronizado: se enviaron $sent operación(es) al servidor."
        }
        is Retry -> "No se pudo sincronizar todavía: $reason Todo sigue guardado en este teléfono y se reintentará."
        is Blocked -> "No se pudo sincronizar: $reason"
    }
}

/** Guarda el último error para mostrarlo en la app aunque la sincronización haya corrido en segundo plano. */
object SyncStatus {
    private fun prefs(context: Context) = context.getSharedPreferences("sync_status", Context.MODE_PRIVATE)

    fun record(context: Context, outcome: SyncOutcome) {
        val error = when (outcome) {
            is SyncOutcome.Retry -> outcome.reason
            is SyncOutcome.Blocked -> outcome.reason
            else -> null
        }
        prefs(context).edit().putLong("attempt_at", System.currentTimeMillis()).putString("error", error).apply()
    }

    fun lastError(context: Context): String? = prefs(context).getString("error", null)
}

/**
 * Lógica de sincronización compartida por el botón "Sincronizar" (en primer plano, con resultado visible)
 * y por WorkManager (segundo plano). El mutex evita que corran dos a la vez.
 */
object SyncRunner {
    private val mutex = Mutex()
    private val gson = Gson()

    private data class Operation(val id: String, val at: Long, val kind: String)

    suspend fun run(app: CafeteriaApplication): SyncOutcome = mutex.withLock { withContext(Dispatchers.IO) { runLocked(app) } }

    fun describe(e: Throwable): String = when (e) {
        is java.net.SocketTimeoutException -> "El servidor tardó demasiado en responder (si está en Render gratis puede estar despertando; prueba de nuevo en un minuto)."
        is java.net.UnknownHostException -> "No se encontró el servidor. Revisa la conexión a internet."
        is javax.net.ssl.SSLException -> "No se pudo establecer una conexión segura con el servidor."
        is java.net.ConnectException -> "No se pudo conectar con el servidor."
        is java.io.IOException -> "Se cortó la conexión con el servidor."
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }

    private const val SESSION_EXPIRED =
        "La sesión caducó o se cerró en otro dispositivo. Cierra sesión y vuelve a entrar con internet; lo guardado en este teléfono no se pierde."

    /** Render gratis duerme el servicio: la primera petición puede tardar ~1 minuto. Se despierta con /health antes de enviar. */
    private fun warmUp() {
        runCatching {
            val base = BuildConfig.API_BASE_URL.trim()
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(75, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(base + "health").get().build()).execute().close()
        }
    }

    private suspend fun runLocked(app: CafeteriaApplication): SyncOutcome {
        val activeSession = app.sessionStore.getSession()
        if (activeSession?.role == Roles.LOCAL_STATION) return SyncOutcome.NothingToSend
        if (!app.apiClient.isConfigured) {
            return SyncOutcome.Blocked("Esta instalación no tiene la dirección del servidor (API_BASE_URL). Hay que compilar la app con ese valor.")
        }
        if (runCatching { app.sessionStore.hasPendingCustomerRegistration() }.getOrDefault(false)) {
            try {
                app.repository.syncPendingCustomerRegistration()
            } catch (e: Exception) {
                return SyncOutcome.Retry(describe(e))
            }
        }
        val session = app.sessionStore.getSession() ?: return SyncOutcome.NothingToSend
        val salesDao = app.database.saleQueueDao()
        val businessId = session.businessId ?: return SyncOutcome.NothingToSend
        val productDao = app.database.productQueueDao()
        val adminDao = app.database.adminOperationDao()
        val pendingSales = salesDao.getPendingForBusiness(businessId, session.userId)
        val pendingProducts = productDao.getPending()
        val pendingAdminOperations = adminDao.getPending()
        if (pendingSales.isEmpty() && pendingProducts.isEmpty() && pendingAdminOperations.isEmpty()) return SyncOutcome.NothingToSend

        val operations = buildList {
            pendingProducts.forEach { add(Operation(it.id, it.createdAtEpochMs, "PRODUCT")) }
            pendingAdminOperations.forEach { add(Operation(it.id, it.createdAtEpochMs, "ADMIN")) }
            pendingSales.forEach { add(Operation(it.id, it.createdAtEpochMs, "SALE")) }
        }.sortedWith(compareBy<Operation> { it.at }.thenBy { if (it.kind == "PRODUCT") 0 else if (it.kind == "ADMIN") 1 else 2 })

        val service = try {
            app.apiClient.service()
        } catch (e: Exception) {
            return SyncOutcome.Blocked(describe(e))
        }
        warmUp()

        var sent = 0
        var review = 0
        var foreignCatalog = 0
        var waitingOnProduct = 0

        for (operation in operations) {
            if (operation.kind == "PRODUCT") {
                val item = productDao.findById(operation.id) ?: continue
                if (session.role != Roles.OWNER || session.userId != item.ownerId || session.businessId != item.businessId) { foreignCatalog++; continue }
                if (productDao.hasEarlierUnresolved(item.productId, item.createdAtEpochMs) > 0) {
                    productDao.updateStatus(item.id, "NEEDS_REVIEW", "Este cambio depende de una modificación anterior del mismo producto que necesita revisión. No se aplicó fuera de orden.")
                    review++
                    continue
                }
                try {
                    app.repository.syncQueuedProduct(item)
                    sent++
                } catch (e: CafeRepository.QueuedOperationException) {
                    if (e.httpCode == 401) return SyncOutcome.Blocked(SESSION_EXPIRED)
                    if (e.httpCode >= 500) return SyncOutcome.Retry("El servidor respondió con error ${e.httpCode}.")
                    productDao.updateStatus(item.id, "NEEDS_REVIEW", e.message ?: "El servidor necesita revisar este cambio de producto.")
                    review++
                } catch (e: Exception) {
                    return SyncOutcome.Retry(describe(e))
                }
                continue
            }

            if (operation.kind == "ADMIN") {
                val item = adminDao.findById(operation.id) ?: continue
                if (session.role != Roles.OWNER || session.userId != item.ownerId || session.businessId != item.businessId) { foreignCatalog++; continue }
                if (adminDao.hasEarlierUnresolved(item.productId, item.localSequence) > 0) {
                    adminDao.updateStatus(item.id, "NEEDS_REVIEW", "Este movimiento depende de un ajuste anterior del mismo producto que necesita revisión. No se aplicó automáticamente.", null)
                    review++
                    continue
                }
                try {
                    app.repository.syncQueuedInventoryAdjustment(item)
                    sent++
                } catch (e: CafeRepository.QueuedOperationException) {
                    if (e.httpCode == 401) return SyncOutcome.Blocked(SESSION_EXPIRED)
                    if (e.httpCode >= 500) return SyncOutcome.Retry("El servidor respondió con error ${e.httpCode}.")
                    adminDao.updateStatus(item.id, "NEEDS_REVIEW", e.message ?: "El servidor necesita revisar este ajuste.", null)
                    review++
                } catch (e: Exception) {
                    return SyncOutcome.Retry(describe(e))
                }
                continue
            }

            val sale = salesDao.findById(operation.id) ?: continue
            if (session.role != Roles.OWNER && session.role != Roles.WORKER) continue
            if (sale.workerId != session.userId && session.role != Roles.OWNER) continue
            val payload = runCatching { gson.fromJson(sale.payloadJson, SalePayload::class.java) }.getOrNull()
            if (payload == null) {
                salesDao.updateStatus(sale.id, "NEEDS_REVIEW", "No se pudo leer el registro local; la venta no se ha eliminado.", null)
                review++
                continue
            }
            // Un producto creado offline debe llegar antes al servidor que una venta que lo menciona.
            if (payload.items.any { it.productId.startsWith("local-") }) { waitingOnProduct++; continue }

            try {
                val response = if (sale.workerId == session.userId) service.submitSale(sale.id, payload) else service.submitForwardedSale(sale.id, payload)
                when {
                    response.isSuccessful -> {
                        salesDao.updateStatus(sale.id, "SYNCED", null, OffsetDateTime.now().toString())
                        sent++
                    }
                    response.code() == 401 -> return SyncOutcome.Blocked(SESSION_EXPIRED)
                    response.code() in 500..599 -> return SyncOutcome.Retry("El servidor respondió con error ${response.code()}.")
                    else -> {
                        val raw = response.errorBody()?.string()
                        val reason = runCatching { gson.fromJson(raw, ApiEnvelope::class.java)?.message as? String }.getOrNull()
                            ?: if (response.code() == 409 || response.code() == 422) "El servidor necesita revisar esta venta por las existencias o los datos del producto. El registro local se conserva."
                            else "El servidor no pudo aceptar esta venta. El registro local se conserva para revisión."
                        salesDao.updateStatus(sale.id, "NEEDS_REVIEW", reason, null)
                        review++
                    }
                }
            } catch (e: Exception) {
                // Mismo ID y mismo contenido en el reintento: la clave de idempotencia del servidor evita duplicados.
                return SyncOutcome.Retry(describe(e))
            }
        }

        try {
            app.repository.applySyncStockFromServer()
        } catch (e: Exception) {
            return SyncOutcome.Retry(describe(e))
        }

        if (sent == 0 && foreignCatalog > 0) {
            return SyncOutcome.Blocked("Hay $foreignCatalog cambio(s) de catálogo creados con otra cuenta o en otra cafetería. Entra con la cuenta administradora que los creó.")
        }
        if (sent == 0 && waitingOnProduct > 0) {
            return SyncOutcome.Blocked("Hay $waitingOnProduct venta(s) esperando a que se envíe primero su producto nuevo. Sincroniza el catálogo con la cuenta administradora.")
        }
        val salesStillPending = salesDao.getPendingForBusiness(businessId, session.userId).any { session.role == Roles.OWNER || it.workerId == session.userId }
        val productsStillPending = productDao.getPending().any { it.ownerId == session.userId }
        val adminStillPending = adminDao.getPending().any { it.ownerId == session.userId }
        return if (salesStillPending || productsStillPending || adminStillPending) {
            SyncOutcome.Retry("Quedan operaciones por enviar.")
        } else SyncOutcome.Done(sent, review)
    }
}
