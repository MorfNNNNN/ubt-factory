package ru.ubt.familyshield.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.ubt.familyshield.ShieldApp
import java.util.concurrent.TimeUnit

private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/** Pulls and pushes family messages. Runs periodically, on push wake-ups and after failures. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as ShieldApp).graph
        val result = graph.sync.syncNow()
        graph.onPeriodicTick()
        return if (result.error == null) Result.success() else Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, periodic)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(network)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE, request)
        }

        fun retryLater(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(network)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-retry", ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** Daily maintenance: threat database updates, entitlement refresh, history cleanup. */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as ShieldApp).graph
        runCatching { graph.threats.updateAll() }
        runCatching { graph.entitlements.refresh() }
        runCatching { graph.family.refreshMembers() }
        runCatching { graph.cleanupHistory() }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("maintenance", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "maintenance-now",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<MaintenanceWorker>().setConstraints(network).build(),
            )
        }
    }
}
