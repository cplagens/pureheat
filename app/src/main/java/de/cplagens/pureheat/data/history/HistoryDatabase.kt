package de.cplagens.pureheat.data.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One snapshot of a room (HEATING group). [valvePosition] is the most open circuit, 0.0–1.0. */
@Entity(tableName = "room_sample", primaryKeys = ["roomId", "timestamp"], indices = [Index("timestamp")])
data class RoomSample(
    val roomId: String,
    val timestamp: Long,
    val actualTemperature: Double?,
    val setPointTemperature: Double?,
    val valvePosition: Double?,
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

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRooms(samples: List<RoomSample>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCircuits(samples: List<CircuitSample>)

    @Query("SELECT MAX(timestamp) FROM room_sample")
    suspend fun lastTimestamp(): Long?

    @Query("SELECT * FROM room_sample WHERE timestamp >= :since ORDER BY timestamp")
    fun roomSamplesSince(since: Long): Flow<List<RoomSample>>

    @Query("DELETE FROM room_sample WHERE timestamp < :before")
    suspend fun deleteRoomSamplesBefore(before: Long)

    @Query("DELETE FROM circuit_sample WHERE timestamp < :before")
    suspend fun deleteCircuitSamplesBefore(before: Long)

    @Query("DELETE FROM room_sample")
    suspend fun clearRooms()

    @Query("DELETE FROM circuit_sample")
    suspend fun clearCircuits()
}

@Database(entities = [RoomSample::class, CircuitSample::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun dao(): HistoryDao

    companion object {
        fun create(context: Context): HistoryDatabase =
            Room.databaseBuilder(context, HistoryDatabase::class.java, "history.db").build()
    }
}
