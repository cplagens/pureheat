package de.cplagens.pureheat.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.cplagens.pureheat.PureHeatApp
import java.util.concurrent.TimeUnit

/**
 * Records the heating state in the background so the history keeps filling while the
 * app is closed. One short request every ~15 min (Android's minimum for periodic work);
 * the system batches it with other jobs and stretches it in Doze/battery saver.
 */
class HistoryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PureHeatApp
        val creds = app.credentialStore.load() ?: return Result.success()
        return try {
            app.repository.fetch(creds)
            Result.success()
        } catch (e: Exception) {
            // A missed sample just leaves a gap; the next periodic run tries again.
            Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "history"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HistoryWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
