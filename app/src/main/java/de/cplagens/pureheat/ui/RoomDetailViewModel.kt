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
import de.cplagens.pureheat.data.probe.Outcome
import de.cplagens.pureheat.data.probe.ProbeScore
import kotlinx.coroutines.launch
import de.cplagens.pureheat.data.model.RoomInsight
import de.cplagens.pureheat.data.room.RoomDescription

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
            val windows = if (props?.hasWindows == false) emptyList() else WindowDetector.detect(samples)
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

    init {
        viewModelScope.launch {
            _insight.value = runCatching { repository.roomInsights()[roomId] }.getOrNull()
        }
    }

    /** Probe run of this room: predictions of the last 14 days plus the planned ones, and the score. */
    val probe: StateFlow<ProbeState> = repository.predictionsSince(roomId, System.currentTimeMillis() - SCORE_WINDOW_MS)
        .map { ProbeState.from(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProbeState())

    private companion object {
        const val SCORE_WINDOW_MS = 14 * 24 * 3_600_000L
    }
}

data class ProbeState(
    val predictions: List<PredictionEntity> = emptyList(),
    val score: ProbeScore = ProbeScore(0, 0, null, 0),
) {
    companion object {
        fun from(predictions: List<PredictionEntity>): ProbeState {
            // Disturbed ones (open window, manual change) don't count.
            val counted = predictions.filter { it.outcome != null && it.outcome != Outcome.DISTURBED }
            val errors = counted.mapNotNull { p ->
                val a = p.actualReach
                val b = p.predictedReach
                if (a != null && b != null) kotlin.math.abs(a - b) / 60_000 else null
            }
            val days = counted.map { it.eventTime / 86_400_000L }.distinct().size
            return ProbeState(
                predictions = predictions,
                score = ProbeScore(
                    evaluated = counted.size,
                    hits = counted.count { it.outcome == Outcome.HIT },
                    meanAbsErrorMinutes = errors.takeIf { it.isNotEmpty() }?.average()?.toLong(),
                    days = days,
                ),
            )
        }
    }
}
