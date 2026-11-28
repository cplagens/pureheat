/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.comfort

import de.cplagens.pureheat.data.model.SimParams
import de.cplagens.pureheat.hmip.HeatingPeriod
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.ProfileEditing
import de.cplagens.pureheat.hmip.Weekday
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * The room should be at least [temperature] from [start] ("warm by") to [end] ("may cool from"),
 * minutes after midnight (end up to 1440).
 */
data class ComfortWindow(val start: Int, val end: Int, val temperature: Double)

/** What the user wants on one weekday: comfort windows, setback at [ecoTemperature] otherwise. */
data class ComfortDay(val ecoTemperature: Double, val windows: List<ComfortWindow>)

/** How one window was planned: heat-up lead (incl. dead time), expected start temperature. */
data class PlannedWindow(
    val window: ComfortWindow,
    val leadHours: Double,
    val startTemperature: Double,
    /** False if the room can't be warm in time (even with [ComfortPlanner.MAX_LEAD_HOURS] or right after the previous window). */
    val reachable: Boolean,
)

/** Suggested profile day plus the reasoning per window. */
data class PlannedDay(val profileDay: ProfileDay, val windows: List<PlannedWindow>)

/**
 * Step 6: turns comfort windows into heating periods with the room model.
 * Per window: start = window start − heat-up time (from the temperature the room has cooled to
 * since the previous window, at least eco) − [BUFFER_MINUTES]; end = window end − the time the
 * screed keeps the room within [ALLOWED_DROP] K. Snapped to the profile editor's 15 min grid,
 * overlapping periods merged, at most [ProfileEditing.MAX_PERIODS] per day (the closest pair is
 * merged first). A suggestion only – saving goes through the normal confirmed save path. Pure Kotlin.
 *
 * Simplifications (v1): one outdoor temperature per window (`outsideAt` at the window start);
 * periods don't reach into the previous day (a window at 00:30 starts at 00:00 at the earliest).
 */
object ComfortPlanner {
    const val BUFFER_MINUTES = 15
    const val ALLOWED_DROP = 0.5
    /** Lead when the target is out of reach or the model has no heating power: start this early at most. */
    const val MAX_LEAD_HOURS = 6.0
    /**
     * Stop at most this long before the window end. A very slow room with much heat from other
     * sources looks as if it never cooled down (hold time ∞), but over days it drifts (measured
     * 2026-11-22: k 0.007/h, gain 0.125 K/h → heating stopped 15 min into every window, comfort 38 %).
     */
    const val MAX_HOLD_HOURS = 3.0

    fun plan(
        day: Weekday,
        week: Map<Weekday, ComfortDay>,
        params: SimParams,
        outsideAt: (Weekday, Int) -> Double,
    ): PlannedDay? {
        val comfort = week[day] ?: return null
        val windows = comfort.windows.filter { it.end > it.start }.sortedBy { it.start }
        if (windows.isEmpty()) return PlannedDay(ProfileDay(comfort.ecoTemperature, emptyList()), emptyList())

        val planned = ArrayList<PlannedWindow>()
        val periods = ArrayList<HeatingPeriod>()
        var prevEnd: Int? = null // end of the previous *period* today (minutes)
        var prevTemp: Double? = null
        windows.forEachIndexed { i, w ->
            val outside = outsideAt(day, w.start)
            // Where the room comes from: the previous window today, else yesterday's last window.
            val (fromTemp, gapMinutes) = if (i > 0) {
                prevTemp!! to (w.start - prevEnd!!).toDouble()
            } else {
                previousDayLast(day, week, params, outsideAt)?.let { (last, endMin) -> last.temperature to (w.start + 1440 - endMin).toDouble() }
                    ?: (comfort.ecoTemperature to Double.POSITIVE_INFINITY)
            }
            // The setback ends when heating starts, so the lead L solves L = heatUp(cooled(gap − L)).
            // The right side falls as L grows (shorter setback, warmer start): bisection.
            val gapHours = gapMinutes / 60.0
            val maxLead = min(MAX_LEAD_HOURS, gapHours)
            fun startAt(lead: Double) = cooledTo(fromTemp, gapHours - lead, params, outside, comfort.ecoTemperature)
            fun need(lead: Double) = heatUpHours(startAt(lead), w.temperature, params, outside) ?: Double.POSITIVE_INFINITY
            val reachable: Boolean
            val lead: Double
            if (need(maxLead) > maxLead) {
                // Not warm in time even with the longest lead (or the windows are back to back).
                reachable = false
                lead = maxLead
            } else {
                var lo = 0.0
                var hi = maxLead
                repeat(40) {
                    val mid = (lo + hi) / 2
                    if (need(mid) > mid) lo = mid else hi = mid
                }
                reachable = true
                lead = hi
            }
            val startTemp = startAt(lead)
            val earliest = prevEnd ?: 0
            val start = snapDown(max(earliest, w.start - (lead * 60).toInt() - BUFFER_MINUTES))

            // Out of reach: the room is behind all the time, so heat until the window ends.
            val holdHours = if (!reachable) 0.0 else holdHours(w, params, outside)
            var end = snapNearest(w.end - (holdHours * 60).toInt())
            end = end.coerceIn(min(snapUp(w.start) + ProfileEditing.STEP_MINUTES, 1440), 1440)
            if (end <= start) end = min(start + ProfileEditing.STEP_MINUTES, 1440)

            periods += HeatingPeriod(start, end, w.temperature)
            planned += PlannedWindow(w, lead, startTemp, reachable)
            prevEnd = end
            prevTemp = w.temperature
        }
        return PlannedDay(ProfileDay(comfort.ecoTemperature, limit(merge(periods))), planned)
    }

    fun planWeek(week: Map<Weekday, ComfortDay>, params: SimParams, outsideAt: (Weekday, Int) -> Double) =
        Weekday.entries.mapNotNull { d -> plan(d, week, params, outsideAt)?.let { d to it } }.toMap()

    /** Merges overlapping or touching periods (the higher temperature wins). */
    fun merge(periods: List<HeatingPeriod>): List<HeatingPeriod> {
        val out = ArrayList<HeatingPeriod>()
        for (p in periods.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && p.start <= last.end) {
                out[out.lastIndex] = HeatingPeriod(last.start, max(last.end, p.end), max(last.temperature, p.temperature))
            } else {
                out += p
            }
        }
        return out
    }

    /** At most [ProfileEditing.MAX_PERIODS]: bridge the smallest gap until it fits. */
    fun limit(periods: List<HeatingPeriod>): List<HeatingPeriod> {
        var list = periods
        while (list.size > ProfileEditing.MAX_PERIODS) {
            val i = (0 until list.lastIndex).minBy { list[it + 1].start - list[it].end }
            val a = list[i]
            val b = list[i + 1]
            list = list.take(i) + HeatingPeriod(a.start, b.end, max(a.temperature, b.temperature)) + list.drop(i + 2)
        }
        return list
    }

    /** Hours from [from] to [to] with the valve open (incl. dead time); null if out of reach. */
    fun heatUpHours(from: Double, to: Double, p: SimParams, outside: Double): Double? {
        if (to <= from) return 0.0
        if (p.k < 1e-4) return if (p.power + p.gain > 0) (to - from) / (p.power + p.gain) + p.deadTimeHours else null
        val eq = outside + (p.power + p.gain) / p.k
        if (to >= eq) return null
        return ln((eq - from) / (eq - to)) / p.k + p.deadTimeHours
    }

    /** Hours the room takes to cool from [from] to [to] with the valve closed (∞ if it never gets there). */
    fun coolHoursFor(from: Double, to: Double, p: SimParams, outside: Double): Double {
        if (to >= from) return 0.0
        if (p.k < 1e-4) return Double.POSITIVE_INFINITY
        val eq = outside + p.gain / p.k
        if (to <= eq) return Double.POSITIVE_INFINITY
        return ln((from - eq) / (to - eq)) / p.k
    }

    /**
     * Temperature after [hours] of setback, starting at [from]; the thermostat holds [eco]. With
     * much heat from other sources the passive equilibrium lies above [from]: the room warms up.
     */
    fun cooledTo(from: Double, hours: Double, p: SimParams, outside: Double, eco: Double): Double {
        val eq = outside + p.gain / max(p.k, 1e-4)
        if (hours.isInfinite()) return max(eco, eq)
        return max(eco, eq + (from - eq) * exp(-p.k * hours))
    }

    /** How long before the window end the heating may stop (residual heat of the screed). */
    private fun holdHours(w: ComfortWindow, p: SimParams, outside: Double) =
        min(MAX_HOLD_HOURS, coolHoursFor(w.temperature, w.temperature - ALLOWED_DROP, p, outside))

    /**
     * Yesterday's last window and when its heating stopped (minutes): the room cools from there,
     * not from the window end. Approximation: as if that window was reachable.
     */
    private fun previousDayLast(
        day: Weekday,
        week: Map<Weekday, ComfortDay>,
        p: SimParams,
        outsideAt: (Weekday, Int) -> Double,
    ): Pair<ComfortWindow, Int>? {
        val prev = Weekday.entries[(day.ordinal + 6) % 7]
        val last = week[prev]?.windows?.filter { it.end > it.start }?.maxByOrNull { it.end } ?: return null
        val stop = last.end - (holdHours(last, p, outsideAt(prev, last.start)) * 60).toInt()
        return last to snapNearest(stop).coerceIn(last.start, last.end)
    }

    private fun snapDown(m: Int) = m / ProfileEditing.STEP_MINUTES * ProfileEditing.STEP_MINUTES
    private fun snapUp(m: Int) = (m + ProfileEditing.STEP_MINUTES - 1) / ProfileEditing.STEP_MINUTES * ProfileEditing.STEP_MINUTES
    private fun snapNearest(m: Int) = ProfileEditing.snapMinutes(m)
}
