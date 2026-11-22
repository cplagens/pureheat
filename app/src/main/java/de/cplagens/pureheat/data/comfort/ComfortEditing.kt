/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.comfort

import de.cplagens.pureheat.hmip.HeatingPeriod
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.Weekday

/** Starting points for one day in the comfort editor. */
enum class ComfortPreset { WORKDAY, HOME_OFFICE, WEEKEND }

/**
 * Step 6: pure helpers for the comfort editor. A comfort day has the same shape as a profile day
 * (setback + up to 6 windows), so the profile editor's day editor is reused through [toProfileDay]
 * / [fromProfileDay]; only the meaning of the bars differs ("warm by" / "may cool from").
 */
object ComfortEditing {
    const val DEFAULT_COMFORT = 21.0
    const val DEFAULT_ECO = 17.0

    fun toProfileDay(day: ComfortDay) =
        ProfileDay(day.ecoTemperature, day.windows.map { HeatingPeriod(it.start, it.end, it.temperature) })

    fun fromProfileDay(day: ProfileDay) =
        ComfortDay(day.baseTemperature, day.periods.map { ComfortWindow(it.start, it.end, it.temperature) })

    /**
     * First draft from the current heating profile: its warm phases become the comfort windows
     * (phases at or below the setback are not comfort). Ignores that the profile already starts
     * early – the user adjusts the times.
     */
    fun fromProfile(days: Map<Weekday, ProfileDay>): Map<Weekday, ComfortDay> =
        days.mapValues { (_, d) ->
            ComfortDay(
                d.baseTemperature,
                d.periods.filter { it.temperature > d.baseTemperature }
                    .map { ComfortWindow(it.start, it.end, it.temperature) },
            )
        }

    /** Windows of [preset]; temperatures come from [like] (its warmest window and setback) if given. */
    fun preset(preset: ComfortPreset, like: ComfortDay? = null): ComfortDay {
        val t = like?.windows?.maxOfOrNull { it.temperature } ?: DEFAULT_COMFORT
        val eco = like?.ecoTemperature ?: DEFAULT_ECO
        val windows = when (preset) {
            ComfortPreset.WORKDAY -> listOf(6 * 60 to 8 * 60, 17 * 60 to 22 * 60 + 30)
            ComfortPreset.HOME_OFFICE -> listOf(6 * 60 + 30 to 22 * 60 + 30)
            ComfortPreset.WEEKEND -> listOf(7 * 60 + 30 to 23 * 60)
        }.map { (s, e) -> ComfortWindow(s, e, t) }
        return ComfortDay(eco, windows)
    }

    /** Week for a room without a profile: workday preset Mon–Fri, weekend preset Sat/Sun. */
    fun defaultWeek(): Map<Weekday, ComfortDay> = Weekday.entries.associateWith {
        preset(if (it in Weekday.WEEKEND) ComfortPreset.WEEKEND else ComfortPreset.WORKDAY)
    }

    fun copyDay(days: Map<Weekday, ComfortDay>, from: Weekday, to: Set<Weekday>): Map<Weekday, ComfortDay> {
        val source = days[from] ?: return days
        return days + to.associateWith { source }
    }
}
