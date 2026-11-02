package de.cplagens.pureheat.ui

import de.cplagens.pureheat.data.history.WindowEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** German texts for detected open-window times. */
internal object WindowTexts {
    private fun time(t: Long) = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(t))
    private fun dayTime(t: Long) = SimpleDateFormat("EE HH:mm", Locale.GERMANY).format(Date(t))

    /** "Fenster offen erkannt seit 21:05" or the last event of the shown 24 h; null if none. */
    fun dashboardLine(events: List<WindowEvent>): String? {
        val last = events.lastOrNull() ?: return null
        val end = last.end
        return if (end == null) {
            "Fenster vermutlich offen seit ${time(last.start)} (−${last.drop.k()} K)"
        } else {
            "Fenster vermutlich offen: ${time(last.start)}–${time(end)} (−${last.drop.k()} K)"
        }
    }

    /** One line per event for the detail view. */
    fun describe(e: WindowEvent): String = buildString {
        append(dayTime(e.start)).append("–").append(e.end?.let { time(it) } ?: "jetzt")
        append(" · ${e.startTemperature.k()} → ${e.minTemperature.k()} °C")
        e.humidityDrop?.let { append(" · Luftfeuchte −$it %") }
    }

    private fun Double.k() = String.format(Locale.GERMANY, "%.1f", this)
}
