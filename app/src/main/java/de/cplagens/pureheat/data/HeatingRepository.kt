package de.cplagens.pureheat.data

import android.content.Context
import android.util.Log
import de.cplagens.pureheat.BuildConfig
import de.cplagens.pureheat.data.history.CircuitSample
import de.cplagens.pureheat.data.history.HistoryDao
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import de.cplagens.pureheat.hmip.HeatingProfile
import de.cplagens.pureheat.hmip.HeatingProfileCodec
import de.cplagens.pureheat.hmip.HeatingState
import de.cplagens.pureheat.hmip.HeatingStateParser
import de.cplagens.pureheat.hmip.HmipClient
import de.cplagens.pureheat.hmip.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Fetches the current state from the cloud and records it in the local history.
 * Shared by the UI (polling while visible) and [HistoryWorker] (background, every 15 min).
 * Also reads and writes heating profiles for the profile screen.
 */
class HeatingRepository(
    private val context: Context,
    private val store: CredentialStore,
    private val dao: HistoryDao,
    private val client: HmipClient = HmipClient(),
) {
    @Volatile private var restUrl: String? = null

    /** Throws on network/API errors; callers turn them into UI messages. */
    suspend fun fetch(creds: Credentials): HeatingState = api(creds) { rest ->
        val raw = client.getCurrentState(creds.sgtin, creds.authToken, rest)
        // Raw state for checking field names on real hardware: adb logcat -s PureHeat
        if (BuildConfig.DEBUG) Log.d(TAG, raw.toString())
        // Logcat truncates long lines; the full dump is readable with
        // adb exec-out run-as de.cplagens.pureheat cat files/last_state.json
        debugDump("last_state.json", raw)
        val state = HeatingStateParser.parse(raw)
        record(state, System.currentTimeMillis())
        state
    }

    fun roomHistorySince(since: Long): Flow<List<RoomSample>> = dao.roomSamplesSince(since)

    fun circuitHistorySince(since: Long): Flow<List<CircuitSample>> = dao.circuitSamplesSince(since)

    fun weatherHistorySince(since: Long): Flow<List<WeatherSample>> = dao.weatherSamplesSince(since)

    suspend fun roomSamples(roomId: String): List<RoomSample> = dao.roomSamples(roomId)

    /** Room samples of the last two recordings (newest first) for [PollingPolicy]. */
    suspend fun lastTwoRecordings(): Pair<List<RoomSample>, List<RoomSample>> {
        val ts = dao.lastTimestamps(2)
        return (ts.getOrNull(0)?.let { dao.roomSamplesAt(it) }.orEmpty()) to
            (ts.getOrNull(1)?.let { dao.roomSamplesAt(it) }.orEmpty())
    }

    suspend fun clear() {
        restUrl = null
        dao.clearRooms()
        dao.clearCircuits()
        dao.clearWeather()
        withContext(Dispatchers.IO) { backupDir().deleteRecursively() }
    }

    // --- Heating profiles -------------------------------------------------------------------

    suspend fun loadProfile(groupId: String, index: String, profileId: String): HeatingProfile {
        val creds = requireCreds()
        return api(creds) { rest ->
            val raw = client.getProfile(creds.sgtin, creds.authToken, rest, groupId, index, profileId)
            debugDump("last_profile.json", raw)
            HeatingProfileCodec.parse(raw, groupId, index)
        }
    }

    /**
     * Saves [edited]: backs up the version currently in the cloud, writes the new one, then
     * reads it back and checks that the cloud really stored what we sent.
     */
    suspend fun saveProfile(edited: HeatingProfile, profileId: String): HeatingProfile {
        val creds = requireCreds()
        val current = loadProfile(edited.groupId, edited.index, profileId)
        writeBackup(current)
        api(creds) { rest ->
            client.updateProfile(
                creds.sgtin, creds.authToken, rest, edited.groupId, edited.index, HeatingProfileCodec.encode(edited),
            )
        }
        val check = loadProfile(edited.groupId, edited.index, profileId)
        if (edited.days.any { (day, d) -> check.days[day] != d }) {
            throw HmipClient.HmipException(
                "Das Profil wurde gesendet, aber die Cloud meldet andere Werte zurück. " +
                    "Bitte in der Homematic-App prüfen oder „Rückgängig“ verwenden.",
            )
        }
        return check
    }

    suspend fun activateProfile(groupId: String, index: String) {
        val creds = requireCreds()
        api(creds) { rest -> client.setActiveProfile(creds.sgtin, creds.authToken, rest, groupId, index) }
    }

    fun hasBackup(groupId: String, index: String): Boolean = backupFile(groupId, index).exists()

    /** Restores the version from before the last save (and backs up the current one, so this toggles). */
    suspend fun restoreBackup(groupId: String, index: String, profileId: String): HeatingProfile {
        val raw = withContext(Dispatchers.IO) {
            Json.parseToJsonElement(backupFile(groupId, index).readText()).jsonObject
        }
        return saveProfile(HeatingProfileCodec.parse(raw, groupId, index), profileId)
    }

    // --- internals --------------------------------------------------------------------------

    private suspend fun <T> api(creds: Credentials, block: suspend (rest: String) -> T): T {
        try {
            val rest = restUrl ?: client.lookup(creds.sgtin).rest.also { restUrl = it }
            return block(rest)
        } catch (e: Exception) {
            // Look up the server again next time, in case the access point moved or DNS was stale.
            restUrl = null
            throw e
        }
    }

    private fun requireCreds(): Credentials =
        store.load() ?: throw HmipClient.HmipException("Nicht angemeldet.")

    private suspend fun debugDump(name: String, raw: JsonObject) {
        if (!BuildConfig.DEBUG) return
        withContext(Dispatchers.IO) { runCatching { File(context.filesDir, name).writeText(raw.toString()) } }
    }

    private fun backupDir() = File(context.filesDir, "profile_backups")
    private fun backupFile(groupId: String, index: String) = File(backupDir(), "${groupId}_$index.json")

    private suspend fun writeBackup(profile: HeatingProfile) = withContext(Dispatchers.IO) {
        backupDir().mkdirs()
        backupFile(profile.groupId, profile.index).writeText(profile.raw.toString())
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
        dao.insertWeather(WeatherSample(now, state.outsideTemperature))
        dao.deleteRoomSamplesBefore(now - RETENTION_MS)
        dao.deleteCircuitSamplesBefore(now - RETENTION_MS)
        dao.deleteWeatherSamplesBefore(now - RETENTION_MS)
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
        // 60 days: enough for the room model to see different outside temperatures (~2 MB).
        const val RETENTION_MS = 60 * 24 * 60 * 60 * 1000L
    }
}
