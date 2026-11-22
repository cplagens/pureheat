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
import de.cplagens.pureheat.R
import de.cplagens.pureheat.data.comfort.ComfortDay
import de.cplagens.pureheat.data.comfort.ComfortEditing
import de.cplagens.pureheat.data.comfort.ComfortPreset
import de.cplagens.pureheat.data.comfort.ComfortReview
import de.cplagens.pureheat.data.comfort.ComfortSuggestion
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import de.cplagens.pureheat.data.model.SimParams
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.Room
import de.cplagens.pureheat.hmip.Weekday
import de.cplagens.pureheat.hmip.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class ComfortUiState(
    /** As saved on the phone; empty until the first save. */
    val original: Map<Weekday, ComfortDay>? = null,
    val edited: Map<Weekday, ComfortDay>? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null,
    /** The planner's suggestion for [edited], simulated against the current profile over the last week. */
    val suggestion: ComfortSuggestion? = null,
    /** Why there is no [suggestion] (no room model, no profile); null while it is calculated. */
    val suggestionMissing: UiText? = null,
) {
    val dirty: Boolean get() = edited != null && original != edited
}

/**
 * Step 6: comfort windows of one room. Local only – [save] writes the phone's database, never the
 * cloud. The only cloud call is reading the active heating profile as a first draft.
 */
class ComfortViewModel(app: Application, private val roomId: String) : AndroidViewModel(app) {
    private val repository = (app as PureHeatApp).repository

    private val _ui = MutableStateFlow(ComfortUiState())
    val ui: StateFlow<ComfortUiState> = _ui.asStateFlow()

    private var room: Room? = null
    /** Active profile of the room, read once from the cloud (null = not loaded yet). */
    private var currentProfile: Map<Weekday, ProfileDay>? = null
    private var inputs: CompareInputs? = null

    private class CompareInputs(val params: SimParams?, val weather: List<WeatherSample>, val samples: List<RoomSample>)

    init {
        // Re-plan and re-simulate whenever the windows change (after a short pause while editing).
        viewModelScope.launch {
            _ui.map { it.edited }.distinctUntilChanged().filterNotNull().collectLatest { week ->
                delay(COMPARE_DELAY_MS)
                compare(week)
            }
        }
    }

    /** Loads the saved windows; without any, starts from the active profile of [room]. */
    fun start(room: Room) {
        this.room = room
        if (_ui.value.edited != null || _ui.value.loading) return
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            val saved = repository.comfort(roomId)
            if (saved.isNotEmpty()) {
                _ui.update { it.copy(original = saved, edited = saved, loading = false) }
            } else {
                val draft = draftFromProfile(room)
                _ui.update {
                    it.copy(
                        original = emptyMap(),
                        edited = draft ?: ComfortEditing.defaultWeek(),
                        loading = false,
                        message = UiText.Res(if (draft != null) R.string.comfort_from_profile_done else R.string.comfort_default_week),
                    )
                }
            }
        }
    }

    /** Replaces the edited week with a draft from the active heating profile (read only). */
    fun takeOverProfile(room: Room) {
        _ui.update { it.copy(loading = true, error = null, message = null) }
        viewModelScope.launch {
            val draft = draftFromProfile(room)
            _ui.update {
                if (draft == null) {
                    it.copy(loading = false, error = it.error ?: UiText.Res(R.string.comfort_no_profile))
                } else {
                    it.copy(loading = false, edited = draft, message = UiText.Res(R.string.comfort_from_profile_done))
                }
            }
        }
    }

    fun updateDay(day: Weekday, value: ComfortDay) = edit { it + (day to value) }

    fun applyPreset(day: Weekday, preset: ComfortPreset) = edit { it + (day to ComfortEditing.preset(preset, it[day])) }

    fun copyDay(from: Weekday, to: Set<Weekday>) = edit { ComfortEditing.copyDay(it, from, to) }

    fun discard() = _ui.update {
        it.copy(edited = it.original?.takeIf { o -> o.isNotEmpty() } ?: it.edited, message = null)
    }

    fun save() {
        val edited = _ui.value.edited ?: return
        viewModelScope.launch {
            repository.saveComfort(roomId, edited)
            _ui.update { it.copy(original = edited, message = UiText.Res(R.string.comfort_saved), error = null) }
        }
    }

    private suspend fun draftFromProfile(room: Room): Map<Weekday, ComfortDay>? =
        activeProfile(room)?.let { ComfortEditing.fromProfile(it) }?.takeIf { it.isNotEmpty() }

    /** Reads the active profile (cloud read only); cached for the comparison. */
    private suspend fun activeProfile(room: Room): Map<Weekday, ProfileDay>? {
        currentProfile?.let { return it }
        val ref = room.profiles.firstOrNull { it.index == room.activeProfile } ?: return null
        return try {
            repository.loadProfile(roomId, ref.index, ref.profileId).days.also { currentProfile = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(error = e.toUiText()) }
            null
        }
    }

    private suspend fun compare(week: Map<Weekday, ComfortDay>) {
        val room = room ?: return
        _ui.update { it.copy(suggestionMissing = null) }
        val current = activeProfile(room)
        if (current.isNullOrEmpty()) {
            _ui.update { it.copy(suggestion = null, suggestionMissing = UiText.Res(R.string.comfort_compare_no_profile)) }
            return
        }
        val now = System.currentTimeMillis()
        val input = inputs ?: CompareInputs(
            params = repository.roomInsights(now)[roomId]?.active?.params,
            weather = repository.weatherHistorySince(now - ComfortReview.WEEK_MS - ComfortReview.NEAREST_MS).first(),
            samples = repository.roomSamples(roomId).filter { it.timestamp >= now - ComfortReview.WEEK_MS - ComfortReview.NEAREST_MS },
        ).also { inputs = it }
        val params = input.params
        if (params == null) {
            _ui.update { it.copy(suggestion = null, suggestionMissing = UiText.Res(R.string.comfort_compare_no_model)) }
            return
        }
        val result = withContext(Dispatchers.Default) {
            ComfortReview.lastWeek(current, week, params, input.weather, input.samples, now, ZoneId.systemDefault())
        }
        _ui.update {
            it.copy(suggestion = result, suggestionMissing = if (result == null) UiText.Res(R.string.comfort_compare_no_windows) else null)
        }
    }

    private fun edit(change: (Map<Weekday, ComfortDay>) -> Map<Weekday, ComfortDay>) =
        _ui.update { s -> s.edited?.let { s.copy(edited = change(it), message = null) } ?: s }

    private companion object {
        const val COMPARE_DELAY_MS = 400L
    }
}
