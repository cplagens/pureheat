/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.comfort

import de.cplagens.pureheat.data.model.RoomSimulator
import de.cplagens.pureheat.data.model.SimParams
import de.cplagens.pureheat.data.model.SimPoint
import de.cplagens.pureheat.data.probe.ProbeRun
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.Weekday
import java.time.Instant
import java.time.ZoneId

/** Result of simulating one profile against the comfort windows. */
data class ProfileScore(
    /** Share of the comfort time in which the room was at most [ComfortPlanner.ALLOWED_DROP] below the target (0–1). */
    val comfortKept: Double,
    val valveHours: Double,
    val meanTemperature: Double,
    val curve: List<SimPoint>,
)

/** The current profile and the suggestion, simulated over the same weather. */
data class ProfileComparison(val current: ProfileScore, val suggested: ProfileScore)

/**
 * Step 6: simulates a weekly profile with [RoomSimulator] (thermostat, dead time, other heat
 * sources) over a past period, e.g. the last week's measured outdoor temperature, and scores it
 * against the comfort windows. Pure Kotlin.
 */
object ComfortEvaluation {
    fun score(
        profile: Map<Weekday, ProfileDay>,
        comfort: Map<Weekday, ComfortDay>,
        params: SimParams,
        startTemperature: Double,
        start: Long,
        end: Long,
        outsideAt: (Long) -> Double,
        zone: ZoneId,
    ): ProfileScore {
        val curve = RoomSimulator.simulate(
            k = params.k,
            power = params.power,
            deadTimeHours = params.deadTimeHours,
            startTemperature = startTemperature,
            start = start,
            end = end,
            setPointAt = { ProbeRun.setPointAt(profile, it, zone) },
            outsideAt = outsideAt,
            gain = params.gain,
        )
        var comfortSteps = 0
        var keptSteps = 0
        var openSteps = 0
        for (p in curve) {
            if (p.valveOpen) openSteps++
            val target = comfortTargetAt(comfort, p.time, zone) ?: continue
            comfortSteps++
            if (p.temperature >= target - ComfortPlanner.ALLOWED_DROP) keptSteps++
        }
        return ProfileScore(
            comfortKept = if (comfortSteps == 0) 1.0 else keptSteps.toDouble() / comfortSteps,
            valveHours = openSteps * RoomSimulator.STEP_MS / 3_600_000.0,
            meanTemperature = curve.map { it.temperature }.average().takeUnless { it.isNaN() } ?: startTemperature,
            curve = curve,
        )
    }

    fun compare(
        current: Map<Weekday, ProfileDay>,
        suggested: Map<Weekday, ProfileDay>,
        comfort: Map<Weekday, ComfortDay>,
        params: SimParams,
        startTemperature: Double,
        start: Long,
        end: Long,
        outsideAt: (Long) -> Double,
        zone: ZoneId,
    ) = ProfileComparison(
        score(current, comfort, params, startTemperature, start, end, outsideAt, zone),
        score(suggested, comfort, params, startTemperature, start, end, outsideAt, zone),
    )

    /** Target of the comfort window at [t], or null outside every window. */
    fun comfortTargetAt(comfort: Map<Weekday, ComfortDay>, t: Long, zone: ZoneId): Double? {
        val local = Instant.ofEpochMilli(t).atZone(zone)
        val day = comfort[Weekday.entries[local.dayOfWeek.value - 1]] ?: return null
        val minute = local.hour * 60 + local.minute
        return day.windows.filter { minute >= it.start && minute < it.end }.maxOfOrNull { it.temperature }
    }
}
