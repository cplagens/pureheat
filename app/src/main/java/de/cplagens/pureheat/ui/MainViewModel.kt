/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.cplagens.pureheat.PureHeatApp
import de.cplagens.pureheat.data.Credentials
import de.cplagens.pureheat.data.HistoryWorker
import de.cplagens.pureheat.data.demo.DemoHome
import de.cplagens.pureheat.data.history.BathingDetector
import de.cplagens.pureheat.data.history.BathingEvent
import de.cplagens.pureheat.data.history.HistoryAnalysis
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.StalledHeating
import de.cplagens.pureheat.data.history.WindowDetector
import de.cplagens.pureheat.data.history.WindowEvent
import de.cplagens.pureheat.data.model.RoomInsight
import de.cplagens.pureheat.hmip.HeatingState
import de.cplagens.pureheat.hmip.HmipClient
import de.cplagens.pureheat.hmip.toUiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Last 24 h of one room plus the derived warning and detected open-window times. */
data class RoomHistory(
    val samples: List<RoomSample>,
    val stalled: StalledHeating?,
    val windows: List<WindowEvent> = emptyList(),
    /** Only for rooms described as bathroom or with shower/bathtub. */
    val bathing: List<BathingEvent> = emptyList(),
)

sealed interface UiState {
    data class Setup(val waitingForButton: Boolean = false, val busy: Boolean = false, val error: UiText? = null) : UiState
    data class Dashboard(
        val state: HeatingState? = null,
        val loading: Boolean = false,
        val error: UiText? = null,
        val lastUpdated: Long? = null,
        val history: Map<String, RoomHistory> = emptyMap(),
        /** Learned room model and heat-up/cool-down predictions per room id. */
        val insights: Map<String, RoomInsight> = emptyMap(),
        /** Forecast min/max outside temperature for the next 24 h, if available. */
        val forecastRange: ClosedFloatingPointRange<Double>? = null,
        /** Invented sample flat instead of a real access point. */
        val demo: Boolean = false,
    ) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val pureHeat = app as PureHeatApp
    private val store = pureHeat.credentialStore
    private val repository = pureHeat.repository
    private val client = HmipClient()

    private val _ui = MutableStateFlow<UiState>(
        store.load()?.let { UiState.Dashboard(demo = DemoHome.isDemo(it.sgtin)) } ?: UiState.Setup()
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repository.forecastSince(System.currentTimeMillis()).collect { hours ->
                val now = System.currentTimeMillis()
                val next = hours.filter { it.timestamp in now..(now + HISTORY_WINDOW_MS) }.map { it.temperature }
                val range = if (next.isEmpty()) null else next.min()..next.max()
                _ui.update { (it as? UiState.Dashboard)?.copy(forecastRange = range) ?: it }
            }
        }
        viewModelScope.launch {
            var insightsAt = 0L
            var descriptionsSeen = repository.descriptions.all.value
            kotlinx.coroutines.flow.combine(
                repository.roomHistorySince(System.currentTimeMillis() - HISTORY_WINDOW_MS),
                repository.descriptions.all,
            ) { samples, descriptions -> samples to descriptions }.collect { (samples, descriptions) ->
                val history = samples.groupBy { it.roomId }
                    .mapValues { (id, s) ->
                        // The room description decides what can happen there: no windows → no open window.
                        val props = descriptions[id]?.properties
                        val windows = if (props?.hasWindows == false) emptyList() else WindowDetector.detect(s)
                        val bathing = if (props?.hasBathing == true) BathingDetector.detect(s) else emptyList()
                        RoomHistory(s, HistoryAnalysis.stalledHeating(s), windows, bathing)
                    }
                _ui.update { (it as? UiState.Dashboard)?.copy(history = history) ?: it }
                // The model uses up to 60 days of data and changes slowly: refit at most every 30 min.
                val now = System.currentTimeMillis()
                // … or right away when a room description changed (it affects window detection and priors).
                if (now - insightsAt >= INSIGHTS_INTERVAL_MS || descriptions !== descriptionsSeen) {
                    insightsAt = now
                    descriptionsSeen = descriptions
                    val insights = runCatching { repository.roomInsights(now) }.getOrDefault(emptyMap())
                    _ui.update { (it as? UiState.Dashboard)?.copy(insights = insights) ?: it }
                }
            }
        }
    }

    fun register(sgtinInput: String, pin: String) {
        val sgtin = client.normalizeSgtin(sgtinInput)
        _ui.value = UiState.Setup(busy = true)
        viewModelScope.launch {
            try {
                val token = client.register(
                    sgtin = sgtin,
                    pin = pin.ifBlank { null },
                    onWaitingForButton = { _ui.value = UiState.Setup(waitingForButton = true, busy = true) },
                )
                store.save(Credentials(sgtin, token))
                HistoryWorker.schedule(getApplication())
                _ui.value = UiState.Dashboard()
                load()
            } catch (e: Exception) {
                _ui.value = UiState.Setup(error = e.toUiText())
            }
        }
    }

    /** Sample flat without an access point (for reviewers, testers and a first look). */
    fun startDemo() {
        _ui.value = UiState.Setup(busy = true)
        viewModelScope.launch {
            try {
                repository.seedDemo()
                store.save(Credentials(DemoHome.SGTIN, "demo"))
                _ui.value = UiState.Dashboard(demo = true)
                load()
            } catch (e: Exception) {
                _ui.value = UiState.Setup(error = e.toUiText())
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun logout() {
        store.clear()
        HistoryWorker.cancel(getApplication())
        viewModelScope.launch { repository.clear() }
        _ui.value = UiState.Setup()
    }

    /**
     * Polls every minute. Called from the activity inside `repeatOnLifecycle(STARTED)`, so it
     * only runs while the app is visible; in the background [HistoryWorker] takes over.
     */
    suspend fun pollWhileVisible() {
        while (true) {
            load()
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun load() {
        val creds = store.load() ?: return
        _ui.update { (it as? UiState.Dashboard)?.copy(loading = true, error = null) ?: it }
        try {
            val parsed = repository.fetch(creds)
            // While the app is open the probe run may happen right away if it is due.
            viewModelScope.launch { repository.maybeRunProbe(parsed, System.currentTimeMillis(), charging = false, foreground = true) }
            _ui.update {
                (it as? UiState.Dashboard)?.copy(
                    state = parsed,
                    loading = false,
                    error = null,
                    lastUpdated = System.currentTimeMillis(),
                ) ?: it
            }
        } catch (e: Exception) {
            _ui.update { (it as? UiState.Dashboard)?.copy(loading = false, error = e.toUiText()) ?: it }
        }
    }

    private companion object {
        // Each refresh is ~100 KB uncompressed; floor heating changes slowly. "Aktualisieren" is instant.
        const val POLL_INTERVAL_MS = 5 * 60_000L
        const val HISTORY_WINDOW_MS = 24 * 60 * 60 * 1000L
        const val INSIGHTS_INTERVAL_MS = 30 * 60 * 1000L
    }
}
