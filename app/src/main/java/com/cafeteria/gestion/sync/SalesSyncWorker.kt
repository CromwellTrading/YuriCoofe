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

    private data class Operation(val id: String, val at: Long, val product: Boolean)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as CafeteriaApplication
        if (!app.apiClient.isConfigured) return@withContext Result.success()
        val session = app.sessionStore.getSession() ?: return@withContext Result.success()
        val salesDao = app.database.saleQueueDao()
        val productDao = app.database.productQueueDao()
        val pendingSales = salesDao.getPending()
        val pendingProducts = productDao.getPending()
        if (pendingSales.isEmpty() && pendingProducts.isEmpty()) return@withContext Result.success()

        val operations = buildList {
            pendingProducts.forEach { add(Operation(it.id, it.createdAtEpochMs, true)) }
            pendingSales.forEach { add(Operation(it.id, it.createdAtEpochMs, false)) }
        }.sortedWith(compareBy<Operation> { it.at }.thenByDescending { it.product })

        val service = try {
            app.apiClient.service()
        } catch (_: Exception) {
            return@withContext Result.retry()
        }

        for (operation in operations) {
            if (operation.product) {
                val item = productDao.findById(operation.id) ?: continue
                // Product catalog changes are owner-only and must sync under the owner who made them.
                if (session.role != Roles.OWNER || session.userId != item.ownerId) continue
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

            val sale = salesDao.findById(operation.id) ?: continue
            // Never send another user's offline transaction with the current login token.
            if (sale.workerId != session.userId || (session.role != Roles.OWNER && session.role != Roles.WORKER)) continue
            val payload = runCatching { gson.fromJson(sale.payloadJson, SalePayload::class.java) }.getOrNull()
            if (payload == null) {
                salesDao.updateStatus(sale.id, "NEEDS_REVIEW", "No se pudo leer el registro local; la venta no se ha eliminado.", null)
                continue
            }
            // A product created offline must reach the server before a sale referencing its local ID.
            if (payload.items.any { it.productId.startsWith("local-") }) continue

            try {
                val response = service.submitSale(sale.id, payload)
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
        val salesStillPending = salesDao.getPending().any { it.workerId == session.userId }
        val productsStillPending = productDao.getPending().any { it.ownerId == session.userId }
        if (salesStillPending || productsStillPending) Result.retry() else Result.success()
    }
}
