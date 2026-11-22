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
import de.cplagens.pureheat.hmip.Room
import de.cplagens.pureheat.hmip.Weekday
import de.cplagens.pureheat.hmip.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComfortUiState(
    /** As saved on the phone; empty until the first save. */
    val original: Map<Weekday, ComfortDay>? = null,
    val edited: Map<Weekday, ComfortDay>? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null,
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

    /** Loads the saved windows; without any, starts from the active profile of [room]. */
    fun start(room: Room) {
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

    private suspend fun draftFromProfile(room: Room): Map<Weekday, ComfortDay>? {
        val ref = room.profiles.firstOrNull { it.index == room.activeProfile } ?: return null
        return try {
            ComfortEditing.fromProfile(repository.loadProfile(roomId, ref.index, ref.profileId).days)
                .takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(error = e.toUiText()) }
            null
        }
    }

    private fun edit(change: (Map<Weekday, ComfortDay>) -> Map<Weekday, ComfortDay>) =
        _ui.update { s -> s.edited?.let { s.copy(edited = change(it), message = null) } ?: s }
}
