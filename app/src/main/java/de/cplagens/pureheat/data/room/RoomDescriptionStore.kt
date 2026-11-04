/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.room

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Room descriptions in app-private SharedPreferences (key = room id), not in the history DB:
 * they are settings, not measurements, and must survive the 60-day retention.
 */
class RoomDescriptionStore(context: Context) {
    private val prefs = context.getSharedPreferences("room_descriptions", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _all = MutableStateFlow(load())
    val all: StateFlow<Map<String, RoomDescription>> = _all.asStateFlow()

    fun get(roomId: String): RoomDescription? = _all.value[roomId]

    fun save(roomId: String, description: RoomDescription) {
        prefs.edit().putString(roomId, json.encodeToString(RoomDescription.serializer(), description)).apply()
        _all.value = _all.value + (roomId to description)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _all.value = emptyMap()
    }

    /** Import: replaces all descriptions. */
    fun replaceAll(descriptions: Map<String, RoomDescription>) {
        val editor = prefs.edit().clear()
        descriptions.forEach { (id, d) -> editor.putString(id, json.encodeToString(RoomDescription.serializer(), d)) }
        editor.apply()
        _all.value = descriptions
    }

    private fun load(): Map<String, RoomDescription> = prefs.all.mapNotNull { (key, value) ->
        (value as? String)?.let { s -> runCatching { key to json.decodeFromString(RoomDescription.serializer(), s) }.getOrNull() }
    }.toMap()
}
