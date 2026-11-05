/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data

import android.content.Context
import android.util.Log
import de.cplagens.pureheat.BuildConfig
import de.cplagens.pureheat.R
import de.cplagens.pureheat.ui.UiText
import de.cplagens.pureheat.data.history.CircuitSample
import de.cplagens.pureheat.data.history.HistoryDao
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import de.cplagens.pureheat.data.history.ForecastSample
import de.cplagens.pureheat.data.model.RoomInsight
import de.cplagens.pureheat.data.model.RoomModelFitter
import de.cplagens.pureheat.data.history.PredictionEntity
import de.cplagens.pureheat.data.history.WindowDetector
import de.cplagens.pureheat.data.probe.HeatUpPrediction
import de.cplagens.pureheat.data.probe.ProbeRun
import de.cplagens.pureheat.data.probe.Strategy
import de.cplagens.pureheat.data.room.RoomDescriptionStore
import de.cplagens.pureheat.data.demo.DemoCloud
import de.cplagens.pureheat.data.demo.DemoHome
import de.cplagens.pureheat.hmip.HeatingCloud
import java.time.ZoneId
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
import kotlinx.serialization.json.JsonPrimitive
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
    private val forecastClient: ForecastClient = ForecastClient(),
    /** What the user wrote about each room; adjusts the model's starting values. */
    val descriptions: RoomDescriptionStore = RoomDescriptionStore(context),
) {
    /** Offline sample flat; used instead of [client] when logged in with [DemoHome.SGTIN]. */
    private val demo by lazy { DemoCloud(context) }

    private fun cloud(creds: Credentials): HeatingCloud = if (DemoHome.isDemo(creds.sgtin)) demo else client

    /**
     * REST server from the lookup, kept across processes: every app start and every background
     * job used to do a lookup, and the lookup service answered 418 when asked too often.
     */
    private val serverPrefs = context.getSharedPreferences("hmip_server", Context.MODE_PRIVATE)

    @Volatile private var restUrl: String? = serverPrefs.getString(KEY_REST_URL, null)
        set(value) {
            field = value
            serverPrefs.edit().putString(KEY_REST_URL, value).apply()
        }

    /** Throws on network/API errors; callers turn them into UI messages. */
    suspend fun fetch(creds: Credentials): HeatingState = api(creds) { rest ->
        val raw = cloud(creds).getCurrentState(creds.sgtin, creds.authToken, rest)
        // Raw state for checking field names on real hardware: adb logcat -s PureHeat
        if (BuildConfig.DEBUG) Log.d(TAG, raw.toString())
        // Logcat truncates long lines; the full dump is readable with
        // adb exec-out run-as de.cplagens.pureheat cat files/last_state.json
        debugDump("last_state.json", raw)
        val state = HeatingStateParser.parse(raw)
        val now = System.currentTimeMillis()
        record(state, now)
        refreshForecastIfDue(state, now, demo = DemoHome.isDemo(creds.sgtin))
        state
    }

    /**
     * Starts the demo mode: three weeks of invented history, a few room descriptions and probe-run
     * results. Only called from the setup screen, i.e. without a real login and with an empty
     * history; logging out clears it like real data.
     */
    suspend fun seedDemo() {
        val now = System.currentTimeMillis()
        val history = withContext(Dispatchers.Default) { demo.history(now) }
        dao.insertRooms(history.rooms)
        dao.insertCircuits(history.circuits)
        dao.insertWeatherAll(history.weather)
        dao.insertPredictions(history.predictions)
        dao.insertForecast(demo.forecast(now))
        descriptions.replaceAll(demo.descriptions(now))
    }

    /**
     * Piggybacks on the regular fetch (no extra wake-up): at most every [FORECAST_INTERVAL_MS]
     * one small request (~5 KB) to Open-Meteo with the rounded location. Failures are ignored –
     * the app then works with the measured outside temperature.
     */
    private suspend fun refreshForecastIfDue(state: HeatingState, now: Long, demo: Boolean) {
        val location = state.location
        if (location == null && !demo) return
        val last = dao.lastForecastFetch()
        if (last != null && now - last < FORECAST_INTERVAL_MS) return
        runCatching {
            val samples = if (location == null) this.demo.forecast(now)
            else forecastClient.hourlyTemperature(location).map { (t, v) -> ForecastSample(t, v, now) }
            if (samples.isEmpty()) return
            dao.insertForecast(samples)
            dao.pruneForecast(before = now - FORECAST_KEEP_PAST_MS, olderFetchThan = now)
        }.onFailure { if (BuildConfig.DEBUG) Log.d(TAG, "Forecast failed: $it") }
    }

    fun forecastSince(since: Long): Flow<List<ForecastSample>> = dao.forecastSince(since)

    // --- Probe run (step 5) -----------------------------------------------------------------

    private val probePrefs = context.getSharedPreferences("probe", Context.MODE_PRIVATE)

    fun predictionsSince(roomId: String, since: Long): Flow<List<PredictionEntity>> = dao.predictionsSince(roomId, since)

    /**
     * Runs the probe at most once per [PROBE_INTERVAL_MS]: in the background only while charging
     * (or if the last run is older than [PROBE_OVERDUE_MS]), in the foreground always when due.
     * Loads the active profile of each room (one small request per room) – reads only.
     */
    suspend fun maybeRunProbe(state: HeatingState, now: Long, charging: Boolean, foreground: Boolean) {
        val last = probePrefs.getLong(KEY_PROBE_RUN, 0L)
        val since = now - last
        if (since < PROBE_INTERVAL_MS) return
        if (!foreground && !charging && since < PROBE_OVERDUE_MS) return
        probePrefs.edit().putLong(KEY_PROBE_RUN, now).apply()
        runCatching { runProbe(state, now) }.onFailure { if (BuildConfig.DEBUG) Log.d(TAG, "Probe run failed: $it") }
    }

    private suspend fun runProbe(state: HeatingState, now: Long) {
        val zone = ZoneId.systemDefault()
        val samples = dao.roomSamplesSinceOnce(now - PROBE_EVAL_LOOKBACK_MS).groupBy { it.roomId }

        // 1) Evaluate predictions whose time has come.
        val evaluated = dao.openPredictions(now).mapNotNull { p ->
            val roomSamples = samples[p.roomId].orEmpty()
            val result = ProbeRun.evaluate(
                HeatUpPrediction(p.eventTime, p.targetTemperature, p.startTemperature, p.predictedReach),
                roomSamples,
                if (descriptions.get(p.roomId)?.properties?.hasWindows == false) emptyList() else WindowDetector.detect(roomSamples),
                now,
            ) ?: return@mapNotNull null
            p.copy(evaluatedAt = now, actualReach = result.actualReach, outcome = result.outcome)
        }
        dao.insertPredictions(evaluated)

        // 2) Predict the next 24 h from the active profiles, the room models and the forecast.
        val insights = roomInsights(now)
        val forecast = dao.forecastFrom(now - 3_600_000L)
        val fallbackOutside = state.outsideTemperature ?: 5.0
        val outsideAt: (Long) -> Double = { t ->
            forecast.minByOrNull { kotlin.math.abs(it.timestamp - t) }
                ?.takeIf { kotlin.math.abs(it.timestamp - t) <= 2 * 3_600_000L }?.temperature ?: fallbackOutside
        }
        val predictions = state.rooms.flatMap { room ->
            val temp = room.actualTemperature ?: return@flatMap emptyList()
            val ref = room.profiles.firstOrNull { it.index == room.activeProfile } ?: return@flatMap emptyList()
            val profile = runCatching { loadProfile(room.id, ref.index, ref.profileId) }.getOrNull()
                ?: return@flatMap emptyList()
            val fitted = insights[room.id]?.model ?: RoomModelFitter.fit(emptyList())
            val (model, source) = ProbeRun.modelFor(fitted, descriptions.get(room.id)?.properties)
            ProbeRun.predict(model, temp, now, profile.days, zone, outsideAt).map {
                PredictionEntity(
                    roomId = room.id,
                    strategy = Strategy.PHYSICS,
                    eventTime = it.eventTime,
                    createdAt = now,
                    targetTemperature = it.target,
                    startTemperature = it.startTemperature,
                    predictedReach = it.predictedReach,
                    modelSource = source,
                )
            }
        }
        dao.insertPredictions(predictions)
        dao.deletePredictionsBefore(now - RETENTION_MS)
        if (BuildConfig.DEBUG) Log.d(TAG, "Probe run: ${evaluated.size} evaluated, ${predictions.size} predicted")
    }

    fun roomHistorySince(since: Long): Flow<List<RoomSample>> = dao.roomSamplesSince(since)

    fun circuitHistorySince(since: Long): Flow<List<CircuitSample>> = dao.circuitSamplesSince(since)

    fun weatherHistorySince(since: Long): Flow<List<WeatherSample>> = dao.weatherSamplesSince(since)

    /** Samples of one room within the model window (not the whole two-year history). */
    suspend fun roomSamples(roomId: String): List<RoomSample> =
        dao.roomSamples(roomId, System.currentTimeMillis() - MODEL_WINDOW_MS)

    /** Room model, setpoint levels and predictions for every room, from the last [MODEL_WINDOW_MS]. */
    suspend fun roomInsights(now: Long = System.currentTimeMillis()): Map<String, RoomInsight> {
        val since = now - MODEL_WINDOW_MS
        val samples = dao.roomSamplesSinceOnce(since)
        val weather = dao.weatherSamplesSinceOnce(since)
        // Predictions are about the coming hours: use the forecast for them when available.
        val forecast = dao.forecastBetween(now, now + PREDICTION_HORIZON_MS).map { it.temperature }
        val forecastOutside = forecast.takeIf { it.isNotEmpty() }?.average()
        val properties = descriptions.all.value.mapValues { it.value.properties }
        return withContext(Dispatchers.Default) { RoomInsight.forAllRooms(samples, weather, now, forecastOutside, properties) }
    }

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
        dao.clearForecast()
        dao.clearPredictions()
        probePrefs.edit().clear().apply()
        descriptions.clear()
        withContext(Dispatchers.IO) { backupDir().deleteRecursively() }
    }

    // --- Heating profiles -------------------------------------------------------------------

    suspend fun loadProfile(groupId: String, index: String, profileId: String): HeatingProfile {
        val creds = requireCreds()
        return api(creds) { rest ->
            val raw = cloud(creds).getProfile(creds.sgtin, creds.authToken, rest, groupId, index, profileId)
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
            cloud(creds).updateProfile(
                creds.sgtin, creds.authToken, rest, edited.groupId, edited.index, HeatingProfileCodec.encode(edited),
            )
        }
        val check = loadProfile(edited.groupId, edited.index, profileId)
        if (edited.days.any { (day, d) -> check.days[day] != d }) {
            throw HmipClient.HmipException(UiText.Res(R.string.err_profile_mismatch))
        }
        return check
    }

    /**
     * Renames a weekly profile. There is no documented rename endpoint; the name travels in the
     * profile object of `updateProfile` (days unchanged). getProfile doesn't return the name, so the
     * check reads it back from the current state. Throws if the cloud didn't store it.
     */
    suspend fun renameProfile(groupId: String, index: String, profileId: String, name: String) {
        val creds = requireCreds()
        val current = loadProfile(groupId, index, profileId)
        writeBackup(current)
        api(creds) { rest ->
            cloud(creds).updateProfile(
                creds.sgtin, creds.authToken, rest, groupId, index, JsonObject(current.raw + ("name" to JsonPrimitive(name))),
            )
        }
        val stored = fetch(creds).rooms.firstOrNull { it.id == groupId }?.profiles?.firstOrNull { it.index == index }?.name
        if (stored != name) throw HmipClient.HmipException(UiText.Res(R.string.err_profile_name_not_stored))
    }

    suspend fun activateProfile(groupId: String, index: String) {
        val creds = requireCreds()
        api(creds) { rest -> cloud(creds).setActiveProfile(creds.sgtin, creds.authToken, rest, groupId, index) }
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
        if (DemoHome.isDemo(creds.sgtin)) return block("demo")
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
        store.load() ?: throw HmipClient.HmipException(UiText.Res(R.string.err_not_logged_in))

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
        humidity = humidity,
    )

    private companion object {
        const val TAG = "PureHeat"
        const val KEY_REST_URL = "rest_url"
        const val FORECAST_INTERVAL_MS = 6 * 3_600_000L
        const val PREDICTION_HORIZON_MS = 12 * 3_600_000L
        const val KEY_PROBE_RUN = "last_run"
        const val PROBE_INTERVAL_MS = 20 * 3_600_000L
        const val PROBE_OVERDUE_MS = 36 * 3_600_000L
        const val PROBE_EVAL_LOOKBACK_MS = 3 * 24 * 3_600_000L
        // Keep a few past hours so the probe run (step 5) can compare forecast and measurement.
        const val FORECAST_KEEP_PAST_MS = 6 * 3_600_000L
        const val MIN_SAMPLE_INTERVAL_MS = 4 * 60 * 1000L
        // Two years: training data for a later AI/grey-box model in simulation mode (whole heating
        // seasons). Measured ~120 KB/day → roughly 90 MB after two years.
        const val RETENTION_MS = 730 * 24 * 60 * 60 * 1000L
        // The room model only looks at recent weeks: fast to compute and follows changes in the building.
        const val MODEL_WINDOW_MS = 60 * 24 * 60 * 60 * 1000L
    }
}
