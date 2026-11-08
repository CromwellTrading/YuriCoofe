package com.cafeteria.gestion.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cafeteria.gestion.CafeteriaApplication
import com.cafeteria.gestion.data.SalePayload
import com.cafeteria.gestion.data.remote.ApiEnvelope
import com.cafeteria.gestion.data.Roles
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

class SalesSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val gson = Gson()

    private data class Operation(val id: String, val at: Long, val kind: String)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as CafeteriaApplication
        val activeSession = app.sessionStore.getSession()
        // A LOCAL_STATION is deliberately an offline vault. It must never call the backend,
        // even if the device currently has internet access.
        if (activeSession?.role == Roles.LOCAL_STATION) return@withContext Result.success()
        if (!app.apiClient.isConfigured) return@withContext Result.success()
        // Customer registration is a separate public outbox. It must still sync after a logout,
        // and never be allowed to replace a different active staff session.
        if (runCatching { app.sessionStore.hasPendingCustomerRegistration() }.getOrDefault(false)) {
            try {
                app.repository.syncPendingCustomerRegistration()
            } catch (_: Exception) {
                // Keep the encrypted payload for a later retry; never delete an unconfirmed signup.
                return@withContext Result.retry()
            }
        }
        val session = app.sessionStore.getSession() ?: return@withContext Result.success()
        val salesDao = app.database.saleQueueDao()
        val businessId = session.businessId ?: return@withContext Result.success()
        val productDao = app.database.productQueueDao()
        val adminDao = app.database.adminOperationDao()
        val pendingSales = salesDao.getPendingForBusiness(businessId, session.userId)
        val pendingProducts = productDao.getPending()
        val pendingAdminOperations = adminDao.getPending()
        if (pendingSales.isEmpty() && pendingProducts.isEmpty() && pendingAdminOperations.isEmpty()) return@withContext Result.success()

        val operations = buildList {
            pendingProducts.forEach { add(Operation(it.id, it.createdAtEpochMs, "PRODUCT")) }
            pendingAdminOperations.forEach { add(Operation(it.id, it.createdAtEpochMs, "ADMIN")) }
            pendingSales.forEach { add(Operation(it.id, it.createdAtEpochMs, "SALE")) }
        }.sortedWith(compareBy<Operation> { it.at }.thenBy { if (it.kind == "PRODUCT") 0 else if (it.kind == "ADMIN") 1 else 2 })

        val service = try {
            app.apiClient.service()
        } catch (_: Exception) {
            return@withContext Result.retry()
        }

        for (operation in operations) {
            if (operation.kind == "PRODUCT") {
                val item = productDao.findById(operation.id) ?: continue
                // Product catalog changes are owner-only and must sync under the owner who made them.
                if (session.role != Roles.OWNER || session.userId != item.ownerId || session.businessId != item.businessId) continue
                // A failed earlier catalogue operation blocks later changes to the same product.
                // This prevents a deactivation or edit from overtaking a change that Yuri still
                // needs to review.
                if (productDao.hasEarlierUnresolved(item.productId, item.createdAtEpochMs) > 0) {
                    productDao.updateStatus(
                        item.id,
                        "NEEDS_REVIEW",
                        "Este cambio depende de una modificación anterior del mismo producto que necesita revisión. No se aplicó fuera de orden."
                    )
                    continue
                }
                try {
                    app.repository.syncQueuedProduct(item)
                } catch (e: com.cafeteria.gestion.data.CafeRepository.QueuedOperationException) {
                    if (e.httpCode == 401 || e.httpCode >= 500) return@withContext Result.retry()
                    if (e.httpCode == 409 || e.httpCode == 422 || e.httpCode in 400..499) {
                        productDao.updateStatus(item.id, "NEEDS_REVIEW", e.message ?: "El servidor necesita revisar este cambio de producto.")
                    } else return@withContext Result.retry()
                } catch (_: Exception) {
                    return@withContext Result.retry()
                }
                continue
            }

            if (operation.kind == "ADMIN") {
                val item = adminDao.findById(operation.id) ?: continue
                if (session.role != Roles.OWNER || session.userId != item.ownerId || session.businessId != item.businessId) continue
                // Do not apply later movements on top of an unresolved earlier movement for
                // the same product. Their causal order must be reviewed together.
                if (adminDao.hasEarlierUnresolved(item.productId, item.localSequence) > 0) {
                    adminDao.updateStatus(
                        item.id,
                        "NEEDS_REVIEW",
                        "Este movimiento depende de un ajuste anterior del mismo producto que necesita revisión. No se aplicó automáticamente.",
                        null
                    )
                    continue
                }
                try {
                    app.repository.syncQueuedInventoryAdjustment(item)
                } catch (e: com.cafeteria.gestion.data.CafeRepository.QueuedOperationException) {
                    if (e.httpCode == 401 || e.httpCode >= 500) return@withContext Result.retry()
                    if (e.httpCode == 409 || e.httpCode == 422 || e.httpCode in 400..499) {
                        adminDao.updateStatus(item.id, "NEEDS_REVIEW", e.message ?: "El servidor necesita revisar este ajuste.", null)
                    } else return@withContext Result.retry()
                } catch (_: Exception) {
                    return@withContext Result.retry()
                }
                continue
            }

            val sale = salesDao.findById(operation.id) ?: continue
            if (session.role != Roles.OWNER && session.role != Roles.WORKER) continue
            // Worker sessions may only submit their own sales. An OWNER can forward
            // an imported worker sale through the separate, owner-protected endpoint.
            if (sale.workerId != session.userId && session.role != Roles.OWNER) continue
            val payload = runCatching { gson.fromJson(sale.payloadJson, SalePayload::class.java) }.getOrNull()
            if (payload == null) {
                salesDao.updateStatus(sale.id, "NEEDS_REVIEW", "No se pudo leer el registro local; la venta no se ha eliminado.", null)
                continue
            }
            // A product created offline must reach the server before a sale referencing its local ID.
            if (payload.items.any { it.productId.startsWith("local-") }) continue

            try {
                val response = if (sale.workerId == session.userId) {
                    service.submitSale(sale.id, payload)
                } else {
                    service.submitForwardedSale(sale.id, payload)
                }
                when {
                    response.isSuccessful -> {
                        salesDao.updateStatus(sale.id, "SYNCED", null, OffsetDateTime.now().toString())
                    }
                    response.code() == 409 || response.code() == 422 -> {
                        val raw = response.errorBody()?.string()
                        val reason = runCatching { gson.fromJson(raw, ApiEnvelope::class.java)?.message as? String }.getOrNull()
                            ?: "El servidor necesita revisar esta venta por las existencias o los datos del producto. El registro local se conserva."
                        salesDao.updateStatus(sale.id, "NEEDS_REVIEW", reason, null)
                    }
                    response.code() == 401 || response.code() in 500..599 -> return@withContext Result.retry()
                    else -> {
                        val raw = response.errorBody()?.string()
                        val reason = runCatching { gson.fromJson(raw, ApiEnvelope::class.java)?.message as? String }.getOrNull()
                            ?: "El servidor no pudo aceptar esta venta. El registro local se conserva para revisión."
                        salesDao.updateStatus(sale.id, "NEEDS_REVIEW", reason, null)
                    }
                }
            } catch (_: Exception) {
                // Retry the exact same ID and payload: the server's idempotency key prevents duplicates
                // if its confirmation was lost after a successful commit.
                return@withContext Result.retry()
            }
        }

        app.repository.applySyncStockFromServer()
        val salesStillPending = salesDao.getPendingForBusiness(businessId, session.userId).any {
            session.role == Roles.OWNER || it.workerId == session.userId
        }
        val productsStillPending = productDao.getPending().any { it.ownerId == session.userId }
        val adminStillPending = adminDao.getPending().any { it.ownerId == session.userId }
        if (salesStillPending || productsStillPending || adminStillPending) Result.retry() else Result.success()
    }
}
