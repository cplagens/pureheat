/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sections of the room detail view, in display order. */
enum class DetailSection { TEMPERATURE, VALVES, HUMIDITY, PROPERTIES, MODEL, PROBE, WINDOWS, BATHING, PHASES }

/**
 * Display settings: the order of the room cards and which sections of the room detail view are
 * shown (same for all rooms). Stores the hidden ones,
 * so the default – and any section added later – is visible.
 */
class ViewSettings(context: Context) {
    private val prefs = context.getSharedPreferences("view_settings", Context.MODE_PRIVATE)
    private val _hidden = MutableStateFlow(load())
    val hidden: StateFlow<Set<DetailSection>> = _hidden.asStateFlow()

    fun setVisible(section: DetailSection, visible: Boolean) {
        val next = if (visible) _hidden.value - section else _hidden.value + section
        prefs.edit().putStringSet(KEY_HIDDEN, next.map { it.name }.toSet()).apply()
        _hidden.value = next
    }

    private val _roomOrder = MutableStateFlow(prefs.getString(KEY_ROOM_ORDER, null)?.split(',')?.filter { it.isNotEmpty() }.orEmpty())

    /** Room ids in the order the user chose on the dashboard; empty = default order (see [RoomOrder]). */
    val roomOrder: StateFlow<List<String>> = _roomOrder.asStateFlow()

    fun setRoomOrder(ids: List<String>) {
        prefs.edit().putString(KEY_ROOM_ORDER, ids.joinToString(",")).apply()
        _roomOrder.value = ids
    }

    private fun load(): Set<DetailSection> =
        prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()
            .mapNotNull { name -> DetailSection.entries.firstOrNull { it.name == name } }
            .toSet()

    private companion object {
        const val KEY_HIDDEN = "hidden_detail_sections"
        const val KEY_ROOM_ORDER = "room_order"
    }
}
