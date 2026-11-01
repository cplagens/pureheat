package de.cplagens.pureheat.data

import android.content.Context
import android.util.Log
import de.cplagens.pureheat.BuildConfig
import de.cplagens.pureheat.data.history.CircuitSample
import de.cplagens.pureheat.data.history.HistoryDao
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.hmip.HeatingState
import de.cplagens.pureheat.hmip.HeatingStateParser
import de.cplagens.pureheat.hmip.HmipClient
import de.cplagens.pureheat.hmip.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fetches the current state from the cloud and records it in the local history.
 * Shared by the UI (polling while visible) and [HistoryWorker] (background, every 15 min).
 */
class HeatingRepository(
    private val context: Context,
    private val dao: HistoryDao,
    private val client: HmipClient = HmipClient(),
) {
    @Volatile private var restUrl: String? = null

    /** Throws on network/API errors; callers turn them into UI messages. */
    suspend fun fetch(creds: Credentials): HeatingState {
        try {
            val rest = restUrl ?: client.lookup(creds.sgtin).rest.also { restUrl = it }
            val raw = client.getCurrentState(creds.sgtin, creds.authToken, rest)
            if (BuildConfig.DEBUG) {
                // Raw state for checking field names on real hardware: adb logcat -s PureHeat
                Log.d(TAG, raw.toString())
                // Logcat truncates long lines; the full dump is readable with
                // adb exec-out run-as de.cplagens.pureheat cat files/last_state.json
                withContext(Dispatchers.IO) {
                    runCatching { File(context.filesDir, "last_state.json").writeText(raw.toString()) }
                }
            }
            val state = HeatingStateParser.parse(raw)
            record(state, System.currentTimeMillis())
            return state
        } catch (e: Exception) {
            // Look up the server again next time, in case the access point moved or DNS was stale.
            restUrl = null
            throw e
        }
    }

    fun roomHistorySince(since: Long): Flow<List<RoomSample>> = dao.roomSamplesSince(since)

    suspend fun clear() {
        restUrl = null
        dao.clearRooms()
        dao.clearCircuits()
    }

    private suspend fun record(state: HeatingState, now: Long) {
        // The UI polls every minute; five-minute resolution is plenty for floor heating.
        val last = dao.lastTimestamp()
        if (last != null && now - last < MIN_SAMPLE_INTERVAL_MS) return

        dao.insertRooms(state.rooms.map { it.toSample(now) })
        val circuits = state.rooms.flatMap { it.circuits } + state.unassignedCircuits
        dao.insertCircuits(
            circuits.distinctBy { it.deviceId to it.channelIndex }
                .map { CircuitSample(it.deviceId, it.channelIndex, now, it.valvePosition) },
        )
        dao.deleteRoomSamplesBefore(now - RETENTION_MS)
        dao.deleteCircuitSamplesBefore(now - RETENTION_MS)
    }

    private fun Room.toSample(now: Long) = RoomSample(
        roomId = id,
        timestamp = now,
        actualTemperature = actualTemperature,
        setPointTemperature = setPointTemperature,
        valvePosition = circuits.mapNotNull { it.valvePosition }.maxOrNull() ?: valvePosition,
    )

    private companion object {
        const val TAG = "PureHeat"
        const val MIN_SAMPLE_INTERVAL_MS = 4 * 60 * 1000L
        const val RETENTION_MS = 14 * 24 * 60 * 60 * 1000L
    }
}
