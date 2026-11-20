/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

/**
 * One typical heating cycle for the room model card: [SETBACK_BEFORE_HOURS] at the setback
 * temperature, [COMFORT_HOURS] at comfort, then back to setback until [HOURS]. Simulated with
 * [RoomSimulator] like the probe run, so the chart shows what the models predict. Pure Kotlin.
 */
object ModelCurve {
    const val HOURS = 24
    const val SETBACK_BEFORE_HOURS = 2
    const val COMFORT_HOURS = 12
    private const val HOUR_MS = 3_600_000L

    /** Setpoint [hoursFromStart] into the cycle. */
    fun setPoint(levels: SetPointLevels, hoursFromStart: Double): Double =
        if (hoursFromStart >= SETBACK_BEFORE_HOURS && hoursFromStart < SETBACK_BEFORE_HOURS + COMFORT_HOURS) levels.comfort
        else levels.setback

    /** Physics model; null while it is still learning (same as the card's text). */
    fun physics(model: RoomModel, levels: SetPointLevels, outside: Double, start: Long): List<SimPoint>? {
        if (!model.confident) return null
        val k = model.k ?: return null
        val power = model.power ?: return null
        return simulate(k, power, model.deadTimeHours ?: DEFAULT_DEAD_TIME_HOURS, 0.0, levels, outside, start)
    }

    fun greyBox(model: GreyBoxModel, levels: SetPointLevels, outside: Double, start: Long): List<SimPoint> =
        simulate(model.k, model.power, model.lagHours.toDouble(), model.gain, levels, outside, start)

    private fun simulate(
        k: Double,
        power: Double,
        deadTimeHours: Double,
        gain: Double,
        levels: SetPointLevels,
        outside: Double,
        start: Long,
    ): List<SimPoint> = RoomSimulator.simulate(
        k = k,
        power = power,
        deadTimeHours = deadTimeHours,
        startTemperature = levels.setback,
        start = start,
        end = start + HOURS * HOUR_MS,
        setPointAt = { t -> setPoint(levels, (t - start).toDouble() / HOUR_MS) },
        outsideAt = { outside },
        gain = gain,
    )

    /** Same default as the probe run's starting values. */
    private const val DEFAULT_DEAD_TIME_HOURS = 1.5
}
