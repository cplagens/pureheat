/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

import java.time.Instant
import java.time.ZoneId
import kotlin.math.sqrt

/**
 * What the active model expects from now until the end of tomorrow, with the room's real profile
 * and the forecast: the continuation of the temperature chart and the "tomorrow" chart of the room
 * model card. [band] is ± the model's measured 1-h error growing with √hours (a rough random-walk
 * estimate), only for the first [BAND_HOURS]; [comparison] is the other model. Pure Kotlin.
 */
data class RoomOutlook(
    val now: Long,
    val end: Long,
    val points: List<SimPoint>,
    val band: List<Triple<Long, Double, Double>>,
    val comparison: List<SimPoint>?,
) {
    /** Start of tomorrow (local midnight). */
    fun tomorrowStart(zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        /** Further ahead the band says little: the error doesn't simply add up while the thermostat regulates. */
        const val BAND_HOURS = 12
        private const val HOUR_MS = 3_600_000L

        /** [stepError]: the model's 1-h prediction error in K (hold-out week); null = no band. */
        fun build(
            active: ActiveModel,
            comparison: SimParams?,
            startTemperature: Double,
            now: Long,
            setPointAt: (Long) -> Double?,
            outsideAt: (Long) -> Double,
            stepError: Double? = null,
            zone: ZoneId = ZoneId.systemDefault(),
        ): RoomOutlook {
            val end = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
            val run: (SimParams) -> List<SimPoint> = { ModelCurve.simulate(it, startTemperature, now, end, setPointAt, outsideAt) }
            val points = run(active.params)
            return RoomOutlook(
                now = now,
                end = end,
                points = points,
                band = stepError?.let { e -> band(points, now, e) }.orEmpty(),
                comparison = comparison?.let(run),
            )
        }

        private fun band(points: List<SimPoint>, now: Long, stepError: Double) =
            points.filter { it.time <= now + BAND_HOURS * HOUR_MS }.map { p ->
                val w = stepError * sqrt((p.time - now).toDouble() / HOUR_MS)
                Triple(p.time, p.temperature - w, p.temperature + w)
            }
    }
}
