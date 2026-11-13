package com.cafeteria.gestion.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cafeteria.gestion.CafeteriaApplication

/** Sincronización en segundo plano. La lógica vive en [SyncRunner]; aquí solo se decide si reintentar. */
class SalesSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CafeteriaApplication
        val outcome = SyncRunner.run(app)
        SyncStatus.record(applicationContext, outcome)
        return if (outcome is SyncOutcome.Retry) Result.retry() else Result.success()
    }
}
