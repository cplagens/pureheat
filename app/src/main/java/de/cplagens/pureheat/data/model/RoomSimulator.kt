/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

/** One simulated point. */
data class SimPoint(val time: Long, val temperature: Double, val setPoint: Double, val valveOpen: Boolean)

/**
 * Forward simulation of one room with the first-order model, like a thermostat would run it:
 * the valve opens when the room is ≥ 0.2 K below the setpoint and closes when it reaches it;
 * the floor only starts heating after the dead time – unless the valve was closed only briefly
 * (< [WARM_SCREED_MS]), then the screed is still warm. Pure Kotlin; used by the probe run (step 5)
 * and later for the optimal profile (step 6) and the savings comparison (step 7).
 */
object RoomSimulator {
    const val STEP_MS = 5 * 60_000L
    const val OPEN_BELOW = 0.2
    /** After a short closing (holding the temperature) the screed is still warm: no new dead time. */
    const val WARM_SCREED_MS = 2 * 3_600_000L

    fun simulate(
        k: Double,
        power: Double,
        deadTimeHours: Double,
        startTemperature: Double,
        start: Long,
        end: Long,
        setPointAt: (Long) -> Double?,
        outsideAt: (Long) -> Double,
    ): List<SimPoint> {
        val out = ArrayList<SimPoint>(((end - start) / STEP_MS + 1).toInt())
        val dtH = STEP_MS / 3_600_000.0
        var t = start
        var temp = startTemperature
        var openSince: Long? = null
        var closedSince: Long? = null
        var warm = false
        while (t <= end) {
            val sp = setPointAt(t)
            val open = when {
                sp == null -> false
                temp < sp - OPEN_BELOW -> true
                temp >= sp -> false
                else -> openSince != null // inside the hysteresis band: keep the previous state
            }
            if (open && openSince == null) {
                // Re-opening soon after heating: the floor is still warm, it heats right away.
                warm = closedSince?.let { t - it < WARM_SCREED_MS } ?: false
                openSince = t
                closedSince = null
            } else if (!open && openSince != null) {
                openSince = null
                closedSince = t
            }
            out += SimPoint(t, temp, sp ?: Double.NaN, open)
            val heating = openSince?.let { warm || (t - it) / 3_600_000.0 >= deadTimeHours } ?: false
            temp += dtH * ((if (heating) power else 0.0) - k * (temp - outsideAt(t)))
            t += STEP_MS
        }
        return out
    }
}
