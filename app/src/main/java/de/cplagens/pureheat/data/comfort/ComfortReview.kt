/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.comfort

import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import de.cplagens.pureheat.data.model.SimParams
import de.cplagens.pureheat.data.probe.ProbeRun
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.Weekday
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** The planner's suggestion for the comfort windows, compared with the current profile over the last week. */
data class ComfortSuggestion(
    val plan: Map<Weekday, PlannedDay>,
    /** Suggested week; days without comfort windows keep the current profile's day. */
    val profile: Map<Weekday, ProfileDay>,
    val comparison: ProfileComparison,
    val start: Long,
    val end: Long,
    /** Days where the suggestion differs from the current profile. */
    val changedDays: Set<Weekday>,
    /** Comfort windows the room can't reach in time, even with the longest lead. */
    val unreachable: Int,
    /** True if no outdoor temperature was recorded in the week ([ComfortReview.FALLBACK_OUTSIDE] was used). */
    val outsideEstimated: Boolean,
)

/**
 * Step 6: "what would the suggestion have done last week?" Plans the comfort windows with the
 * room model at the outdoor temperatures measured in the last 7 days, then simulates the current
 * profile and the suggestion over that week from the room's measured temperature at its start.
 * Pure Kotlin; nothing is written anywhere.
 */
object ComfortReview {
    const val WEEK_MS = 7 * 24 * 3_600_000L
    /** A weather sample further away than this doesn't count for a point in time. */
    const val NEAREST_MS = 2 * 3_600_000L
    const val FALLBACK_OUTSIDE = 5.0

    fun lastWeek(
        current: Map<Weekday, ProfileDay>,
        comfort: Map<Weekday, ComfortDay>,
        params: SimParams,
        weather: List<WeatherSample>,
        roomSamples: List<RoomSample>,
        now: Long,
        zone: ZoneId,
    ): ComfortSuggestion? {
        if (current.isEmpty() || comfort.values.none { d -> d.windows.any { it.end > it.start } }) return null
        val start = now - WEEK_MS
        val outside = Outside(weather.filter { it.outsideTemperature != null && it.timestamp in (start - NEAREST_MS)..now })
        val plan = ComfortPlanner.planWeek(comfort, params) { day, minute -> outside.at(lastWeekTime(day, minute, start, zone)) }
        val suggested = current + plan.mapValues { it.value.profileDay }
        val startTemperature = roomSamples
            .filter { it.actualTemperature != null }
            .minByOrNull { abs(it.timestamp - start) }
            ?.takeIf { abs(it.timestamp - start) <= NEAREST_MS }
            ?.actualTemperature
            // Without a measurement the room starts at the current profile's setpoint.
            ?: ProbeRun.setPointAt(current, start, zone)
            ?: 20.0
        val comparison = ComfortEvaluation.compare(current, suggested, comfort, params, startTemperature, start, now, outside::at, zone)
        return ComfortSuggestion(
            plan = plan,
            profile = suggested,
            comparison = comparison,
            start = start,
            end = now,
            changedDays = Weekday.entries.filter { current[it] != suggested[it] }.toSet(),
            unreachable = plan.values.sumOf { d -> d.windows.count { !it.reachable } },
            outsideEstimated = outside.empty,
        )
    }

    /** The time in [start, start + 7 days) that falls on [day] at [minute] (local time). */
    fun lastWeekTime(day: Weekday, minute: Int, start: Long, zone: ZoneId): Long {
        val first = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        for (offset in 0..7) {
            val date = first.plusDays(offset.toLong())
            if (date.dayOfWeek.value - 1 != day.ordinal) continue
            val t = date.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
            if (t >= start) return t
        }
        return start
    }

    /** Nearest measured outdoor temperature (binary search), else the week's mean, else [FALLBACK_OUTSIDE]. */
    private class Outside(samples: List<WeatherSample>) {
        private val times = samples.map { it.timestamp }.toLongArray()
        private val values = samples.map { it.outsideTemperature!! }.toDoubleArray()
        private val mean = values.average().takeUnless { it.isNaN() } ?: FALLBACK_OUTSIDE
        val empty = times.isEmpty()

        fun at(t: Long): Double {
            if (empty) return FALLBACK_OUTSIDE
            val i = times.binarySearch(t).let { if (it >= 0) it else -it - 1 }
            val best = listOfNotNull(i.takeIf { it < times.size }, (i - 1).takeIf { it >= 0 })
                .minBy { abs(times[it] - t) }
            return if (abs(times[best] - t) <= NEAREST_MS) values[best] else mean
        }
    }
}
