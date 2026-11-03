package de.cplagens.pureheat.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sections of the room detail view, in display order. */
enum class DetailSection { TEMPERATURE, VALVES, HUMIDITY, PROPERTIES, MODEL, PROBE, WINDOWS, PHASES }

/**
 * Which sections of the room detail view are shown (same for all rooms). Stores the hidden ones,
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

    private fun load(): Set<DetailSection> =
        prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()
            .mapNotNull { name -> DetailSection.entries.firstOrNull { it.name == name } }
            .toSet()

    private companion object {
        const val KEY_HIDDEN = "hidden_detail_sections"
    }
}
