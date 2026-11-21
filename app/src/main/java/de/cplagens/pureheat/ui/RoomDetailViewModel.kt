/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.app.Application
import androidx.annotation.StringRes
import de.cplagens.pureheat.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.cplagens.pureheat.PureHeatApp
import de.cplagens.pureheat.data.DetailSection
import de.cplagens.pureheat.data.history.CircuitSample
import de.cplagens.pureheat.data.history.BathingDetector
import de.cplagens.pureheat.data.history.BathingEvent
import de.cplagens.pureheat.data.history.HistoryAnalysis
import de.cplagens.pureheat.data.history.RoomModelEntity
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.ThermalPhase
import de.cplagens.pureheat.data.history.WeatherSample
import de.cplagens.pureheat.data.history.WindowDetector
import de.cplagens.pureheat.data.history.WindowEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import de.cplagens.pureheat.data.history.PredictionEntity
import de.cplagens.pureheat.data.probe.ProbeScore
import de.cplagens.pureheat.data.probe.Strategy
import kotlinx.coroutines.launch
import de.cplagens.pureheat.data.model.RoomInsight
import de.cplagens.pureheat.data.model.RoomOutlook
import de.cplagens.pureheat.data.model.SimParams
import de.cplagens.pureheat.data.probe.ProbeRun
import de.cplagens.pureheat.data.room.RoomDescription
import de.cplagens.pureheat.hmip.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import kotlin.math.abs

enum class HistoryRange(@StringRes val label: Int, val millis: Long) {
    DAY(R.string.range_24h, 24 * 3_600_000L),
    DAYS_3(R.string.range_3d, 3 * 24 * 3_600_000L),
    WEEK(R.string.range_7d, 7 * 24 * 3_600_000L),
    WEEKS_2(R.string.range_14d, 14 * 24 * 3_600_000L),
    ALL(R.string.range_60d, 60 * 24 * 3_600_000L),
}

data class RoomDetailState(
    val range: HistoryRange = HistoryRange.DAY,
    val start: Long = 0,
    val end: Long = 0,
    val samples: List<RoomSample> = emptyList(),
    /** Per channel, keyed by (deviceId, channelIndex). */
    val circuits: Map<Pair<String, Int>, List<CircuitSample>> = emptyMap(),
    val weather: List<WeatherSample> = emptyList(),
    val phases: List<ThermalPhase> = emptyList(),
    /** Detected open-window times in the range. */
    val windows: List<WindowEvent> = emptyList(),
    /** Detected showering/bathing (only for rooms described as bathroom or with shower/bathtub). */
    val bathing: List<BathingEvent> = emptyList(),
)

/** All recorded data of one room for the selected time range (read-only). */
class RoomDetailViewModel(app: Application, private val roomId: String) : AndroidViewModel(app) {
    private val repository = (app as PureHeatApp).repository
    private val range = MutableStateFlow(HistoryRange.DAY)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<RoomDetailState> = range.flatMapLatest { r ->
        val end = System.currentTimeMillis()
        val start = end - r.millis
        combine(
            repository.roomHistorySince(start),
            repository.circuitHistorySince(start),
            repository.weatherHistorySince(start),
            repository.descriptions.all,
        ) { rooms, circuits, weather, descriptions ->
            val samples = rooms.filter { it.roomId == roomId }
            val props = descriptions[roomId]?.properties
            val windows = if (props?.hasWindows == false) emptyList() else WindowDetector.detect(samples, WindowDetector.outsideHumidity(weather))
            val allBathing = BathingDetector.detect(samples)
            RoomDetailState(
                range = r,
                start = start,
                end = maxOf(end, samples.lastOrNull()?.timestamp ?: end),
                samples = samples,
                circuits = circuits.groupBy { it.deviceId to it.channelIndex },
                weather = weather,
                // Same as the model: open-window and hot-water times are not the floor heating's doing.
                phases = HistoryAnalysis.phases(
                    BathingDetector.withoutBathingTimes(WindowDetector.withoutWindowTimes(samples, windows), allBathing),
                    weather,
                ),
                windows = windows,
                bathing = if (props?.hasBathing == true) allBathing else emptyList(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomDetailState())

    fun setRange(r: HistoryRange) {
        range.value = r
    }

    private val viewSettings = (app as PureHeatApp).viewSettings

    /** Sections the user has hidden in the detail view (same for all rooms). */
    val hiddenSections: StateFlow<Set<DetailSection>> = viewSettings.hidden

    fun setSectionVisible(section: DetailSection, visible: Boolean) = viewSettings.setVisible(section, visible)

    /** The user's description of the room (properties screen). */
    val description: StateFlow<RoomDescription?> = repository.descriptions.all
        .map { it[roomId] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.descriptions.get(roomId))

    /** Learned model of this room (always from the full 60 days, independent of [range]). */
    private val _insight = MutableStateFlow<RoomInsight?>(null)
    val insight: StateFlow<RoomInsight?> = _insight

    /** Grey-box model versions of this room, newest first (fitted weekly over all data). */
    private val _greyBoxVersions = MutableStateFlow<List<RoomModelEntity>?>(null)
    val greyBoxVersions: StateFlow<List<RoomModelEntity>?> = _greyBoxVersions

    private val modelsLoaded = viewModelScope.launch {
        launch { _insight.value = runCatching { repository.roomInsights()[roomId] }.getOrNull() }
        launch { _greyBoxVersions.value = runCatching { repository.roomModelVersions(roomId) }.getOrDefault(emptyList()) }
    }

    /** What the active model expects until the end of tomorrow; null without profile or model. */
    private val _outlook = MutableStateFlow<RoomOutlook?>(null)
    val outlook: StateFlow<RoomOutlook?> = _outlook
    private var outlookFor: Pair<String?, Double?>? = null

    /**
     * Loads the room's active profile (one read request, nothing is written) and simulates the
     * active model with it and the forecast. Again only when the profile or temperature changes.
     */
    fun loadOutlook(room: Room) {
        val key = room.activeProfile to room.actualTemperature
        if (key == outlookFor) return
        outlookFor = key
        viewModelScope.launch {
            _outlook.value = try {
                buildOutlook(room)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun buildOutlook(room: Room): RoomOutlook? {
        val temperature = room.actualTemperature ?: return null
        val ref = room.profiles.firstOrNull { it.index == room.activeProfile } ?: return null
        modelsLoaded.join()
        val insight = _insight.value ?: return null
        val active = insight.active ?: return null
        // The grey box's 1-h error on the hold-out week (else on its fit); the physics model has none.
        val adopted = _greyBoxVersions.value?.firstOrNull { it.adopted }
        val stepError = if (active.isGreyBox) adopted?.holdoutRmse ?: adopted?.rmse else null
        val profile = repository.loadProfile(room.id, ref.index, ref.profileId)
        val now = System.currentTimeMillis()
        val forecast = repository.forecastSince(now - 3_600_000L).first()
        val fallback = insight.outside ?: 5.0
        val zone = ZoneId.systemDefault()
        return RoomOutlook.build(
            active = active,
            comparison = if (active.isGreyBox) SimParams.of(insight.model) else null,
            startTemperature = temperature,
            now = now,
            setPointAt = { ProbeRun.setPointAt(profile.days, it, zone) },
            // Same as the probe run: nearest forecast hour, else the last known outside temperature.
            outsideAt = { t ->
                forecast.minByOrNull { abs(it.timestamp - t) }?.takeIf { abs(it.timestamp - t) <= 2 * 3_600_000L }?.temperature ?: fallback
            },
            stepError = stepError,
            zone = zone,
        )
    }

    /** Probe run of this room: predictions of the last 14 days plus the planned ones, and the score. */
    val probe: StateFlow<ProbeState> = repository.predictionsSince(roomId, System.currentTimeMillis() - ProbeState.SCORE_WINDOW_MS)
        .map { ProbeState.from(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProbeState())
}

data class ProbeState(
    /** Predictions of the physics model. */
    val predictions: List<PredictionEntity> = emptyList(),
    val score: ProbeScore = ProbeScore(0, 0, null, 0),
    /** Predictions of the grey-box model side by side (empty until it has made some). */
    val greyBoxPredictions: List<PredictionEntity> = emptyList(),
    /** Score of the grey-box model; null until it has made predictions. */
    val greyBoxScore: ProbeScore? = null,
) {
    /** Predictions and score of the active model (the grey box once it predicts), then of the other one. */
    fun forActive(greyBox: Boolean): Pair<List<PredictionEntity>, ProbeScore> =
        if (greyBox && greyBoxScore != null) greyBoxPredictions to greyBoxScore else predictions to score

    fun forOther(greyBox: Boolean): Pair<List<PredictionEntity>, ProbeScore>? = when {
        greyBox && greyBoxScore != null -> predictions to score
        greyBoxScore != null -> greyBoxPredictions to greyBoxScore
        else -> null
    }

    companion object {
        const val SCORE_WINDOW_MS = ProbeScore.WINDOW_MS

        fun from(predictions: List<PredictionEntity>): ProbeState {
            val byStrategy = predictions.groupBy { it.strategy }
            val physics = byStrategy[Strategy.PHYSICS].orEmpty()
            return ProbeState(
                predictions = physics,
                score = ProbeScore.of(physics),
                greyBoxPredictions = byStrategy[Strategy.GREYBOX].orEmpty(),
                greyBoxScore = byStrategy[Strategy.GREYBOX]?.let(ProbeScore::of),
            )
        }
    }
}
