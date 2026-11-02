package de.cplagens.pureheat.data

import de.cplagens.pureheat.data.history.RoomSample
import kotlin.math.abs

/**
 * Decides when the background job fetches next. Every `getCurrentState` costs ~100 KB
 * (the cloud doesn't compress), so we only poll often while something is happening.
 * Pure Kotlin so it can move to a JVM module (Raspberry Pi) later.
 */
object PollingPolicy {
    const val ACTIVE_MINUTES = 15L
    const val STABLE_MINUTES = 30L
    const val NIGHT_MINUTES = 60L

    /**
     * A valve moving by this much, a setpoint change or a quick temperature change (e.g. a window
     * being opened) counts as "something is happening".
     */
    const val VALVE_CHANGE = 0.1
    const val SETPOINT_CHANGE = 0.25
    const val TEMPERATURE_CHANGE = 0.3

    /**
     * @param latest   room samples of the most recent recording
     * @param previous room samples of the recording before that (empty if none)
     * @param localHour hour of day (0–23) in local time
     */
    fun nextDelayMinutes(latest: List<RoomSample>, previous: List<RoomSample>, localHour: Int): Long {
        if (changed(latest, previous)) return ACTIVE_MINUTES
        return if (localHour >= 22 || localHour < 5) NIGHT_MINUTES else STABLE_MINUTES
    }

    fun changed(latest: List<RoomSample>, previous: List<RoomSample>): Boolean {
        val before = previous.associateBy { it.roomId }
        return latest.any { now ->
            val prev = before[now.roomId] ?: return@any false
            differs(now.valvePosition, prev.valvePosition, VALVE_CHANGE) ||
                differs(now.setPointTemperature, prev.setPointTemperature, SETPOINT_CHANGE) ||
                differs(now.actualTemperature, prev.actualTemperature, TEMPERATURE_CHANGE)
        }
    }

    private fun differs(a: Double?, b: Double?, threshold: Double): Boolean =
        a != null && b != null && abs(a - b) >= threshold
}
