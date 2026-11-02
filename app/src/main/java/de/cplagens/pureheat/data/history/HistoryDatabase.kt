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

    @Query("SELECT MAX(timestamp) FROM room_sample")
    suspend fun lastTimestamp(): Long?

    /** Timestamps of the most recent recordings, newest first. */
    @Query("SELECT DISTINCT timestamp FROM room_sample ORDER BY timestamp DESC LIMIT :n")
    suspend fun lastTimestamps(n: Int): List<Long>

    @Query("SELECT * FROM room_sample WHERE timestamp = :timestamp")
    suspend fun roomSamplesAt(timestamp: Long): List<RoomSample>

    @Query("SELECT * FROM room_sample WHERE timestamp >= :since ORDER BY timestamp")
    fun roomSamplesSince(since: Long): Flow<List<RoomSample>>

    @Query("SELECT * FROM room_sample WHERE roomId = :roomId ORDER BY timestamp")
    suspend fun roomSamples(roomId: String): List<RoomSample>

    @Query("SELECT * FROM room_sample WHERE timestamp >= :since ORDER BY timestamp")
    suspend fun roomSamplesSinceOnce(since: Long): List<RoomSample>

    @Query("SELECT * FROM weather_sample WHERE timestamp >= :since ORDER BY timestamp")
    suspend fun weatherSamplesSinceOnce(since: Long): List<WeatherSample>

    @Query("DELETE FROM room_sample WHERE timestamp < :before")
    suspend fun deleteRoomSamplesBefore(before: Long)

    @Query("DELETE FROM circuit_sample WHERE timestamp < :before")
    suspend fun deleteCircuitSamplesBefore(before: Long)

    @Query("DELETE FROM room_sample")
    suspend fun clearRooms()

    @Query("DELETE FROM circuit_sample")
    suspend fun clearCircuits()
}

@Database(
    entities = [RoomSample::class, CircuitSample::class, WeatherSample::class],
    version = 3,
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

        fun create(context: Context): HistoryDatabase =
            Room.databaseBuilder(context, HistoryDatabase::class.java, "history.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
