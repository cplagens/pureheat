/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One snapshot of a room (HEATING group). [valvePosition] is the most open circuit, 0.0–1.0. */
@Entity(tableName = "room_sample", primaryKeys = ["roomId", "timestamp"], indices = [Index("timestamp")])
data class RoomSample(
    val roomId: String,
    val timestamp: Long,
    val actualTemperature: Double?,
    val setPointTemperature: Double?,
    val valvePosition: Double?,
    /** Relative humidity in %, helps to detect an open window (since DB v3). */
    val humidity: Int? = null,
)

/** One snapshot of a single floor heating channel. */
@Entity(
    tableName = "circuit_sample",
    primaryKeys = ["deviceId", "channelIndex", "timestamp"],
    indices = [Index("timestamp")],
)
data class CircuitSample(
    val deviceId: String,
    val channelIndex: Int,
    val timestamp: Long,
    val valvePosition: Double?,
)

/** Outside temperature at the time of a snapshot; one row per recording, shared by all rooms. */
@Entity(tableName = "weather_sample")
data class WeatherSample(
    @PrimaryKey val timestamp: Long,
    val outsideTemperature: Double?,
)

/** Hourly outside temperature forecast (Open-Meteo); replaced on every fetch (since DB v4). */
@Entity(tableName = "forecast_sample")
data class ForecastSample(
    @PrimaryKey val timestamp: Long,
    val temperature: Double,
    val fetchedAt: Long,
)

/**
 * One heat-up prediction of the probe run (step 5, since DB v5). [strategy] lets other models
 * (grey box, AI in simulation mode) predict side by side with the physics model.
 */
@Entity(
    tableName = "prediction",
    primaryKeys = ["roomId", "strategy", "eventTime"],
    indices = [Index("eventTime")],
)
data class PredictionEntity(
    val roomId: String,
    val strategy: String,
    val eventTime: Long,
    val createdAt: Long,
    val targetTemperature: Double,
    val startTemperature: Double,
    val predictedReach: Long?,
    val modelSource: String,
    val evaluatedAt: Long? = null,
    val actualReach: Long? = null,
    val outcome: String? = null,
)

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRooms(samples: List<RoomSample>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCircuits(samples: List<CircuitSample>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(sample: WeatherSample)

    @Query("SELECT * FROM circuit_sample WHERE timestamp >= :since ORDER BY timestamp")
    fun circuitSamplesSince(since: Long): Flow<List<CircuitSample>>

    @Query("SELECT * FROM weather_sample WHERE timestamp >= :since ORDER BY timestamp")
    fun weatherSamplesSince(since: Long): Flow<List<WeatherSample>>

    @Query("DELETE FROM weather_sample WHERE timestamp < :before")
    suspend fun deleteWeatherSamplesBefore(before: Long)

    @Query("DELETE FROM weather_sample")
    suspend fun clearWeather()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForecast(samples: List<ForecastSample>)

    /** Old forecast hours are dropped; past hours are covered by the measured weather_sample. */
    @Query("DELETE FROM forecast_sample WHERE timestamp < :before OR fetchedAt < :olderFetchThan")
    suspend fun pruneForecast(before: Long, olderFetchThan: Long)

    @Query("SELECT * FROM forecast_sample WHERE timestamp >= :since ORDER BY timestamp")
    fun forecastSince(since: Long): Flow<List<ForecastSample>>

    @Query("SELECT * FROM forecast_sample WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun forecastBetween(from: Long, to: Long): List<ForecastSample>

    @Query("SELECT MAX(fetchedAt) FROM forecast_sample")
    suspend fun lastForecastFetch(): Long?

    @Query("DELETE FROM forecast_sample")
    suspend fun clearForecast()

    @Query("SELECT * FROM forecast_sample WHERE timestamp >= :from ORDER BY timestamp")
    suspend fun forecastFrom(from: Long): List<ForecastSample>

    /** Replaces predictions that are not evaluated yet (re-planned with newer data). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPredictions(predictions: List<PredictionEntity>)

    @Query("SELECT * FROM prediction WHERE evaluatedAt IS NULL AND eventTime < :now")
    suspend fun openPredictions(now: Long): List<PredictionEntity>

    @Query("SELECT * FROM prediction WHERE roomId = :roomId AND eventTime >= :since ORDER BY eventTime")
    fun predictionsSince(roomId: String, since: Long): Flow<List<PredictionEntity>>

    @Query("SELECT * FROM prediction WHERE eventTime >= :since ORDER BY eventTime")
    suspend fun allPredictionsSince(since: Long): List<PredictionEntity>

    @Query("DELETE FROM prediction WHERE eventTime < :before")
    suspend fun deletePredictionsBefore(before: Long)

    @Query("DELETE FROM prediction")
    suspend fun clearPredictions()

    @Query("SELECT MAX(timestamp) FROM room_sample")
    suspend fun lastTimestamp(): Long?

    /** Timestamps of the most recent recordings, newest first. */
    @Query("SELECT DISTINCT timestamp FROM room_sample ORDER BY timestamp DESC LIMIT :n")
    suspend fun lastTimestamps(n: Int): List<Long>

    @Query("SELECT * FROM room_sample WHERE timestamp = :timestamp")
    suspend fun roomSamplesAt(timestamp: Long): List<RoomSample>

    @Query("SELECT * FROM room_sample WHERE timestamp >= :since ORDER BY timestamp")
    fun roomSamplesSince(since: Long): Flow<List<RoomSample>>

    @Query("SELECT * FROM room_sample WHERE roomId = :roomId AND timestamp >= :since ORDER BY timestamp")
    suspend fun roomSamples(roomId: String, since: Long): List<RoomSample>

    @Query("SELECT * FROM room_sample WHERE timestamp >= :since ORDER BY timestamp")
    suspend fun roomSamplesSinceOnce(since: Long): List<RoomSample>

    @Query("SELECT * FROM weather_sample WHERE timestamp >= :since ORDER BY timestamp")
    suspend fun weatherSamplesSinceOnce(since: Long): List<WeatherSample>

    @Query("DELETE FROM room_sample WHERE timestamp < :before")
    suspend fun deleteRoomSamplesBefore(before: Long)

    @Query("DELETE FROM circuit_sample WHERE timestamp < :before")
    suspend fun deleteCircuitSamplesBefore(before: Long)

    // --- Export/import (in time slices, so two years don't have to fit into memory at once) ---

    @Query("SELECT MIN(timestamp) FROM room_sample")
    suspend fun firstTimestamp(): Long?

    @Query("SELECT * FROM room_sample WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    suspend fun roomSamplesBetween(from: Long, to: Long): List<RoomSample>

    @Query("SELECT * FROM circuit_sample WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    suspend fun circuitSamplesBetween(from: Long, to: Long): List<CircuitSample>

    @Query("SELECT * FROM weather_sample WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    suspend fun weatherSamplesBetween(from: Long, to: Long): List<WeatherSample>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherAll(samples: List<WeatherSample>)

    @Query("DELETE FROM room_sample")
    suspend fun clearRooms()

    @Query("DELETE FROM circuit_sample")
    suspend fun clearCircuits()
}

@Database(
    entities = [RoomSample::class, CircuitSample::class, WeatherSample::class, ForecastSample::class, PredictionEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun dao(): HistoryDao

    companion object {
        /** v2: outside temperature (weather_sample). Existing history is kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weather_sample` " +
                        "(`timestamp` INTEGER NOT NULL, `outsideTemperature` REAL, PRIMARY KEY(`timestamp`))",
                )
            }
        }

        /** v3: humidity per room sample. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `room_sample` ADD COLUMN `humidity` INTEGER")
            }
        }

        /** v4: outside temperature forecast. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `forecast_sample` (`timestamp` INTEGER NOT NULL, " +
                        "`temperature` REAL NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`timestamp`))",
                )
            }
        }

        /** v5: probe run predictions. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prediction` (`roomId` TEXT NOT NULL, `strategy` TEXT NOT NULL, " +
                        "`eventTime` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `targetTemperature` REAL NOT NULL, " +
                        "`startTemperature` REAL NOT NULL, `predictedReach` INTEGER, `modelSource` TEXT NOT NULL, " +
                        "`evaluatedAt` INTEGER, `actualReach` INTEGER, `outcome` TEXT, " +
                        "PRIMARY KEY(`roomId`, `strategy`, `eventTime`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_prediction_eventTime` ON `prediction` (`eventTime`)")
            }
        }

        fun create(context: Context): HistoryDatabase =
            Room.databaseBuilder(context, HistoryDatabase::class.java, "history.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
