/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

/** Parameters of either model as [RoomSimulator] needs them. */
data class SimParams(val k: Double, val power: Double, val deadTimeHours: Double, val gain: Double = 0.0) {
    companion object {
        /** Physics model; null while it is still learning (same as the card's text). */
        fun of(model: RoomModel): SimParams? {
            if (!model.confident) return null
            val k = model.k ?: return null
            val power = model.power ?: return null
            return SimParams(k, power, model.deadTimeHours ?: DEFAULT_DEAD_TIME_HOURS)
        }

        fun of(model: GreyBoxModel) = SimParams(model.k, model.power, model.lagHours.toDouble(), model.gain)

        /** Same default as the probe run's starting values. */
        private const val DEFAULT_DEAD_TIME_HOURS = 1.5
    }
}

/**
 * Model curves for the room model card and the temperature chart. The typical day: [SETBACK_BEFORE_HOURS]
 * at the setback temperature, [COMFORT_HOURS] at comfort, then back to setback until [HOURS] (used
 * when the active profile can't be loaded). Simulated with [RoomSimulator] like the probe run, so
 * the charts show what the models predict. Pure Kotlin.
 */
object ModelCurve {
    const val HOURS = 24
    const val SETBACK_BEFORE_HOURS = 2
    const val COMFORT_HOURS = 12
    private const val HOUR_MS = 3_600_000L

    /** Setpoint [hoursFromStart] into the typical day. */
    fun setPoint(levels: SetPointLevels, hoursFromStart: Double): Double =
        if (hoursFromStart >= SETBACK_BEFORE_HOURS && hoursFromStart < SETBACK_BEFORE_HOURS + COMFORT_HOURS) levels.comfort
        else levels.setback

    /** Physics model; null while it is still learning. */
    fun physics(model: RoomModel, levels: SetPointLevels, outside: Double, start: Long): List<SimPoint>? =
        SimParams.of(model)?.let { typicalDay(it, levels, outside, start) }

    fun greyBox(model: GreyBoxModel, levels: SetPointLevels, outside: Double, start: Long): List<SimPoint> =
        typicalDay(SimParams.of(model), levels, outside, start)

    fun typicalDay(params: SimParams, levels: SetPointLevels, outside: Double, start: Long): List<SimPoint> =
        simulate(params, levels.setback, start, start + HOURS * HOUR_MS, { t -> setPoint(levels, (t - start).toDouble() / HOUR_MS) }, { outside })

    fun simulate(
        params: SimParams,
        startTemperature: Double,
        start: Long,
        end: Long,
        setPointAt: (Long) -> Double?,
        outsideAt: (Long) -> Double,
    ): List<SimPoint> = RoomSimulator.simulate(
        k = params.k,
        power = params.power,
        deadTimeHours = params.deadTimeHours,
        startTemperature = startTemperature,
        start = start,
        end = end,
        setPointAt = setPointAt,
        outsideAt = outsideAt,
        gain = params.gain,
    )
}
