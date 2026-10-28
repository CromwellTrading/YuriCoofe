package com.cafeteria.gestion.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cafeteria.gestion.CafeteriaApplication
import com.cafeteria.gestion.data.SalePayload
import com.cafeteria.gestion.data.remote.ApiEnvelope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

class SalesSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val gson = Gson()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as CafeteriaApplication
        val allPending = app.database.saleQueueDao().getPending()
        if (allPending.isEmpty()) return@withContext Result.success()
        // No server address means no request can be made. Leave the durable queue untouched;
        // later builds with a configured address will pick it up on the next scheduled run.
        if (!app.apiClient.isConfigured) return@withContext Result.success()
        val session = app.sessionStore.getSession() ?: return@withContext Result.success()
        // Offline sales are uploaded under the same worker account that recorded them.
        // Never attach another person's pending sale to the currently logged-in account.
        val pending = allPending.filter { it.workerId == session.userId }
        if (pending.isEmpty()) return@withContext Result.success()
        val service = try {
            app.apiClient.service()
        } catch (_: Exception) {
            return@withContext Result.retry()
        }

        for (sale in pending) {
            val maybePayload = runCatching { gson.fromJson(sale.payloadJson, SalePayload::class.java) }.getOrNull()
            if (maybePayload == null) {
                app.database.saleQueueDao().updateStatus(
                    sale.id,
                    "NEEDS_REVIEW",
                    "No se pudo leer el registro local; la venta no se ha eliminado.",
                    null
                )
                continue
            }
            val payload = maybePayload
            try {
                val response = service.submitSale(sale.id, payload)
                when {
                    response.isSuccessful -> {
                        app.database.saleQueueDao().updateStatus(
                            sale.id, "SYNCED", null,
                            OffsetDateTime.now().toString()
                        )
                    }
                    response.code() == 409 || response.code() == 422 -> {
                        val raw = response.errorBody()?.string()
                        val reason = runCatching { gson.fromJson(raw, ApiEnvelope::class.java)?.message as? String }.getOrNull()
                            ?: "El servidor necesita revisar esta venta por las existencias o los datos del producto. El registro local se conserva."
                        app.database.saleQueueDao().updateStatus(sale.id, "NEEDS_REVIEW", reason, null)
                    }
                    response.code() == 401 -> return@withContext Result.retry()
                    response.code() in 500..599 -> return@withContext Result.retry()
                    else -> {
                        val raw = response.errorBody()?.string()
                        val reason = runCatching { gson.fromJson(raw, ApiEnvelope::class.java)?.message as? String }.getOrNull()
                            ?: "El servidor no pudo aceptar esta venta. El registro local se conserva para revisión."
                        app.database.saleQueueDao().updateStatus(sale.id, "NEEDS_REVIEW", reason, null)
                    }
                }
            } catch (_: Exception) {
                // Network loss after server acceptance is safe: the same clientSaleId and Idempotency-Key
                // are reused on the next attempt, so the backend must return the original result.
                return@withContext Result.retry()
            }
        }
        app.repository.applySyncStockFromServer()
        val thisUserStillPending = app.database.saleQueueDao().getPending().any { it.workerId == session.userId }
        if (thisUserStillPending) Result.retry() else Result.success()
    }
}
