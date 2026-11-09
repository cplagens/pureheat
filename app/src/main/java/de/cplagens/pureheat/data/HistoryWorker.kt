/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.cplagens.pureheat.BuildConfig
import de.cplagens.pureheat.PureHeatApp
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Records the heating state in the background while the app is closed.
 *
 * Each `getCurrentState` costs ~100 KB (no compression), so instead of a fixed 15-min period
 * this is a self-rescheduling chain: after each run [PollingPolicy] picks the next delay
 * (15 min while valves/setpoints change, 30 min when stable, 60 min at night).
 * [HistoryWatchdogWorker] restarts the chain if it ever breaks (process death, update, …).
 */
class HistoryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PureHeatApp
        val creds = app.credentialStore.load() ?: return Result.success()
        val delay = try {
            val state = app.repository.fetch(creds)
            // Daily probe run (step 5): preferably while charging, so it costs no battery.
            app.repository.maybeRunProbe(state, System.currentTimeMillis(), isCharging(), foreground = false)
            val (latest, previous) = app.repository.lastTwoRecordings()
            PollingPolicy.nextDelayMinutes(latest, previous, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Stopped by WorkManager (logout, constraints): don't re-enqueue from here.
            throw e
        } catch (e: Exception) {
            // A missed sample just leaves a gap; try again after the normal interval.
            PollingPolicy.STABLE_MINUTES
        }
        if (BuildConfig.DEBUG) Log.d("PureHeat", "HistoryWorker: next run in $delay min")
        enqueueNext(applicationContext, delay, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    private fun isCharging(): Boolean {
        val bm = applicationContext.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return bm.isCharging
    }

    companion object {
        private const val CHAIN = "history-chain"
        private const val WATCHDOG = "history-watchdog"
        private const val LEGACY_PERIODIC = "history" // fixed 15-min job of earlier versions

        internal fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        /**
         * APPEND_OR_REPLACE from inside the worker (REPLACE would cancel the running worker itself);
         * KEEP from outside so an existing chain isn't reset.
         */
        internal fun enqueueNext(context: Context, delayMinutes: Long, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<HistoryWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(CHAIN, policy, request)
        }

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(LEGACY_PERIODIC)
            enqueueNext(context, PollingPolicy.ACTIVE_MINUTES, ExistingWorkPolicy.KEEP)
            val watchdog = PeriodicWorkRequestBuilder<HistoryWatchdogWorker>(1, TimeUnit.HOURS).build()
            wm.enqueueUniquePeriodicWork(WATCHDOG, ExistingPeriodicWorkPolicy.KEEP, watchdog)
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(CHAIN)
            wm.cancelUniqueWork(WATCHDOG)
            wm.cancelUniqueWork(LEGACY_PERIODIC)
        }
    }
}

/** Hourly, no network needed: restarts the [HistoryWorker] chain if it isn't pending any more. */
class HistoryWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PureHeatApp
        if (app.credentialStore.load() != null) {
            // KEEP: no-op while the chain is enqueued or running.
            HistoryWorker.enqueueNext(applicationContext, 0, ExistingWorkPolicy.KEEP)
        }
        return Result.success()
    }
}
