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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.cplagens.pureheat.BuildConfig
import de.cplagens.pureheat.PureHeatApp
import java.util.concurrent.TimeUnit

/**
 * Re-fits the grey-box room models over the whole history. Checks daily, but each room is only
 * fitted once a week (`GreyBoxTraining.due`); runs while charging, needs no network.
 */
class RoomModelWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PureHeatApp
        if (app.credentialStore.load() == null) return Result.success()
        try {
            app.repository.refitGreyBoxModels()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Tried again on the next daily run.
            if (BuildConfig.DEBUG) Log.d("PureHeat", "RoomModelWorker failed: $e")
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "room-model"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<RoomModelWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
