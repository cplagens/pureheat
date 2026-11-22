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
import de.cplagens.pureheat.data.history.HistoryAnalysis
import de.cplagens.pureheat.data.profile.ProfileDescriber
import de.cplagens.pureheat.data.profile.ProfileFromText
import de.cplagens.pureheat.data.room.ExtractionSource
import de.cplagens.pureheat.hmip.HeatingProfile
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.ProfileEditing
import de.cplagens.pureheat.hmip.ProfileRef
import de.cplagens.pureheat.hmip.Room
import de.cplagens.pureheat.hmip.Weekday
import de.cplagens.pureheat.R
import de.cplagens.pureheat.hmip.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val selected: ProfileRef? = null,
    val original: HeatingProfile? = null,
    val edited: HeatingProfile? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null,
    /** Typical heat-up rate of this room in K/h, derived from the recorded history. */
    val heatUpRate: Double? = null,
    val hasBackup: Boolean = false,
    /** A text description is being turned into a suggestion. */
    val suggesting: Boolean = false,
) {
    val dirty: Boolean get() = original != null && edited != null && original.days != edited.days
    val changes: List<UiText>
        get() = if (original != null && edited != null) ProfileEditing.diff(original, edited).map { it.describe() } else emptyList()
}

/** State of the profile screen for one room. Writes happen only in [save], [activate] and [undo]. */
class ProfileViewModel(app: Application, private val roomId: String) : AndroidViewModel(app) {
    private val repository = (app as PureHeatApp).repository

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val rate = runCatching { HistoryAnalysis.heatUpRate(repository.roomSamples(roomId)) }.getOrNull()
            _ui.update { it.copy(heatUpRate = rate) }
        }
    }

    fun select(ref: ProfileRef) {
        if (_ui.value.selected == ref && _ui.value.original != null) return
        _ui.update {
            it.copy(selected = ref, original = null, edited = null, loading = true, error = null, message = null)
        }
        viewModelScope.launch {
            try {
                val profile = repository.loadProfile(roomId, ref.index, ref.profileId)
                _ui.update {
                    it.copy(
                        original = profile,
                        edited = profile,
                        loading = false,
                        hasBackup = repository.hasBackup(roomId, ref.index),
                    )
                }
                applyPending()
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.toUiText()) }
            }
        }
    }

    fun updateDay(day: Weekday, value: ProfileDay) = edit { it.copy(days = it.days + (day to value)) }

    fun copyDay(from: Weekday, to: Set<Weekday>) = edit { ProfileEditing.copyDay(it, from, to) }

    fun discard() = _ui.update { it.copy(edited = it.original, message = null) }

    /** Suggestion from the comfort times waiting for [ref] to be loaded. */
    private var pendingSuggestion: Pair<ProfileRef, Map<Weekday, ProfileDay>>? = null

    /**
     * Step 6: puts the comfort planner's [days] into the editor of [ref] (selecting it if needed).
     * Only the edited copy changes; saving is the normal confirmed path with the list of changes.
     */
    fun applySuggestion(ref: ProfileRef, days: Map<Weekday, ProfileDay>) {
        pendingSuggestion = ref to days
        val s = _ui.value
        if (s.selected == ref && s.original != null) applyPending() else select(ref)
    }

    private fun applyPending() {
        val (ref, days) = pendingSuggestion ?: return
        if (_ui.value.selected != ref) return
        pendingSuggestion = null
        _ui.update { s ->
            val edited = s.edited ?: return@update s
            val next = edited.copy(days = edited.days + days)
            s.copy(
                edited = next,
                error = null,
                message = UiText.Res(if (next.days == edited.days) R.string.comfort_apply_unchanged else R.string.comfort_apply_done),
            )
        }
    }

    /** Saves the edited week to this room and, if chosen, to the active profile of [alsoRooms]. */
    fun save(alsoRooms: List<Room>, onSaved: () -> Unit) {
        val state = _ui.value
        val ref = state.selected ?: return
        val edited = state.edited ?: return
        _ui.update { it.copy(saving = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                // Other rooms only get the changed days, as the save dialog says.
                val changedDays = state.original?.let { o -> ProfileEditing.diff(o, edited).map { it.day }.toSet() }
                val copied = if (changedDays == null) edited.days else edited.days.filterKeys { it in changedDays }
                val saved = repository.saveProfile(edited, ref.profileId)
                val failed = mutableListOf<String>()
                for (room in alsoRooms) {
                    val target = room.profiles.firstOrNull { it.index == room.activeProfile } ?: continue
                    runCatching {
                        val other = repository.loadProfile(room.id, target.index, target.profileId)
                        repository.saveProfile(other.copy(days = other.days + copied), target.profileId)
                    }.onFailure { failed += room.label.trim() }
                }
                _ui.update {
                    it.copy(
                        original = saved,
                        edited = saved,
                        saving = false,
                        hasBackup = true,
                        message = when {
                            failed.isNotEmpty() -> UiText.Res(R.string.profile_saved_partly, failed.joinToString())
                            alsoRooms.isNotEmpty() -> UiText.Res(R.string.profile_saved_also, alsoRooms.size)
                            else -> UiText.Res(R.string.profile_saved)
                        },
                    )
                }
                onSaved()
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, error = e.toUiText()) }
            }
        }
    }

    fun activate(ref: ProfileRef, onDone: () -> Unit) {
        _ui.update { it.copy(saving = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                repository.activateProfile(roomId, ref.index)
                _ui.update { it.copy(saving = false, message = UiText.Res(R.string.profile_now_active, ref.displayName)) }
                onDone()
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, error = e.toUiText()) }
            }
        }
    }

    private val describer = ProfileDescriber()

    /**
     * Text description → suggestion in the editor (only the edited copy changes; saving is the
     * normal confirmed path with the list of changes).
     */
    fun suggestFromText(text: String, minT: Double, maxT: Double) {
        if (_ui.value.edited == null || _ui.value.suggesting) return
        _ui.update { it.copy(suggesting = true, error = null, message = null) }
        viewModelScope.launch {
            val suggestion = describer.describe(text)
            _ui.update { s ->
                val edited = s.edited
                when {
                    edited == null -> s.copy(suggesting = false)
                    suggestion.rules.isEmpty() -> s.copy(suggesting = false, error = UiText.Res(R.string.profile_describe_nothing))
                    else -> {
                        val days = ProfileFromText.apply(edited.days, suggestion.rules, minT, maxT)
                        val source = when (suggestion.source) {
                            ExtractionSource.AI -> R.string.profile_describe_by_ai
                            else -> R.string.profile_describe_by_keywords
                        }
                        s.copy(
                            suggesting = false,
                            edited = edited.copy(days = days),
                            message = if (days == edited.days) UiText.Res(R.string.profile_describe_unchanged) else UiText.Res(source),
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        describer.close()
    }

    /** Renames [ref] in the cloud (after the user confirmed in the dialog). */
    fun rename(ref: ProfileRef, name: String, onDone: () -> Unit) {
        val clean = name.trim().take(MAX_NAME_LENGTH)
        if (clean.isEmpty() || clean == ref.name) return
        _ui.update { it.copy(saving = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                repository.renameProfile(roomId, ref.index, ref.profileId, clean)
                _ui.update { it.copy(saving = false, message = UiText.Res(R.string.profile_renamed, clean)) }
                onDone()
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, error = e.toUiText()) }
            }
        }
    }

    /** Restores the version from before the last save. */
    fun undo(onDone: () -> Unit) {
        val ref = _ui.value.selected ?: return
        _ui.update { it.copy(saving = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val restored = repository.restoreBackup(roomId, ref.index, ref.profileId)
                _ui.update {
                    it.copy(original = restored, edited = restored, saving = false, message = UiText.Res(R.string.profile_restored))
                }
                onDone()
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, error = e.toUiText()) }
            }
        }
    }

    private fun edit(change: (HeatingProfile) -> HeatingProfile) =
        _ui.update { s -> s.edited?.let { s.copy(edited = change(it), message = null) } ?: s }

    companion object {
        /** The official app allows short names; keep them readable as chips. */
        const val MAX_NAME_LENGTH = 30
    }
}
