/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/** Hourly means of one room; the hour is `timestamp / 3 600 000`. */
data class HourPoint(val hour: Long, val temperature: Double, val valve: Double, val outside: Double)

/**
 * Typical floor heating values the fit starts from; the data only pull the parameters away as
 * far as they allow (ridge / Gaussian prior). The standard deviations say how sure the prior is.
 */
data class GreyBoxPrior(
    val k: Double = 0.03,
    val kSd: Double = 0.02,
    val power: Double = 0.5,
    val powerSd: Double = 0.3,
    val gain: Double = 0.0,
    val gainSd: Double = 0.15,
)

/**
 * Grey-box room model fitted over all hourly data (T = room, Tₐ = outside, per hour):
 *
 *     ΔT = −k·(T − Tₐ) + P·valve(t − lag) + g
 *
 * g is the heat from other sources (neighbours, screed, towel radiator, sun). The standard errors
 * come from the posterior; a parameter counts as *measured* once the data have at least halved
 * the prior's uncertainty, otherwise it is only *estimated*. Pure Kotlin (no Android).
 */
data class GreyBoxModel(
    /** Heat loss coefficient in 1/h. */
    val k: Double,
    /** Heating power at a fully open valve in K/h. */
    val power: Double,
    /** Gain from other heat sources in K/h. */
    val gain: Double,
    /** Hours between the valve opening and the room reacting. */
    val lagHours: Int,
    val kSe: Double,
    val powerSe: Double,
    val gainSe: Double,
    /** One-step (1 h) prediction error on the fitted data in K. */
    val rmse: Double,
    /** Number of hourly steps used. */
    val n: Int,
    val prior: GreyBoxPrior,
) {
    val kMeasured: Boolean get() = kSe <= GreyBoxFitter.MEASURED_RATIO * prior.kSd
    val powerMeasured: Boolean get() = powerSe <= GreyBoxFitter.MEASURED_RATIO * prior.powerSd
    val gainMeasured: Boolean get() = gainSe <= GreyBoxFitter.MEASURED_RATIO * prior.gainSd

    /** Expected temperature change in K/h. [laggedValve] is the valve opening [lagHours] ago. */
    fun rate(temperature: Double, outside: Double, laggedValve: Double): Double =
        -k * (temperature - outside) + power * laggedValve + gain

    /** Hours to heat from [from] to [to] at [outside] with the valve fully open, including the lag. */
    fun heatUp(from: Double, to: Double, outside: Double): Prediction =
        towards(from, to, outside, power + gain)?.let { Prediction.Hours(it + lagHours) }
            ?: Prediction.Unreachable

    /** Hours for the room to cool from [from] to [to] with the valve closed. */
    fun coolDown(from: Double, to: Double, outside: Double): Prediction =
        towards(from, to, outside, gain)?.let { Prediction.Hours(it) } ?: Prediction.Unreachable

    /** Time to go from [from] to [to] under dT/dt = drive − k·(T − Tₐ); null if never reached. */
    private fun towards(from: Double, to: Double, outside: Double, drive: Double): Double? {
        if (abs(to - from) < 1e-9) return 0.0
        val rising = to > from
        if (k < 1e-4) {
            val r = drive - k * (from - outside)
            return if (rising == (r > 0)) (to - from) / r else null
        }
        val equilibrium = outside + drive / k
        if (rising && to >= equilibrium || !rising && to <= equilibrium) return null
        if (rising && from >= equilibrium || !rising && from <= equilibrium) return null
        return ln((equilibrium - from) / (equilibrium - to)) / k
    }
}

object GreyBoxFitter {
    /** Lags tried, in hours; the best one-step fit wins. */
    const val MAX_LAG = 4

    /** Fewer hourly steps than this give no model (≈ 2 days). */
    const val MIN_POINTS = 48

    /** A parameter is "measured" when its standard error is at most this share of the prior's. */
    const val MEASURED_RATIO = 0.5

    /** Outside temperature may be borrowed from this many hours around a gap. */
    private const val WEATHER_REACH_HOURS = 3

    private const val HOUR_MS = 3_600_000L

    /**
     * Hourly means. An hour with a sample without temperature or valve is dropped: window and
     * shower times are blanked that way (`withoutWindowTimes`, `withoutBathingTimes`).
     */
    fun hourly(samples: List<RoomSample>, weather: List<WeatherSample>): List<HourPoint> {
        val outside = weather.filter { it.outsideTemperature != null }
            .groupBy { it.timestamp / HOUR_MS }
            .mapValues { (_, ws) -> ws.mapNotNull { it.outsideTemperature }.average() }
        return samples.groupBy { it.timestamp / HOUR_MS }.mapNotNull { (hour, ss) ->
            if (ss.any { it.actualTemperature == null || it.valvePosition == null }) return@mapNotNull null
            val out = (0..WEATHER_REACH_HOURS).firstNotNullOfOrNull { d ->
                outside[hour - d] ?: outside[hour + d]
            } ?: return@mapNotNull null
            HourPoint(
                hour = hour,
                temperature = ss.map { it.actualTemperature!! }.average(),
                valve = ss.map { it.valvePosition!! }.average(),
                outside = out,
            )
        }.sortedBy { it.hour }
    }

    /** Best model over the lags 0…[MAX_LAG], or null with fewer than [MIN_POINTS] steps. */
    fun fit(points: List<HourPoint>, prior: GreyBoxPrior = GreyBoxPrior()): GreyBoxModel? {
        val byHour = points.associateBy { it.hour }
        return (0..MAX_LAG).mapNotNull { lag -> fitLag(rows(byHour, lag), lag, prior) }
            .minByOrNull { it.rmse }
    }

    /** One-step prediction error of [model] on [points] (e.g. the hold-out week), null if no steps. */
    fun rmse(model: GreyBoxModel, points: List<HourPoint>): Double? {
        val rows = rows(points.associateBy { it.hour }, model.lagHours)
        if (rows.isEmpty()) return null
        val theta = doubleArrayOf(model.k, model.power, model.gain)
        return sqrt(rows.sumOf { r -> sq(r.y - dot(r.x, theta)) } / rows.size)
    }

    private class Row(val x: DoubleArray, val y: Double)

    /** x = (−(T − Tₐ), valve(h − lag), 1), y = T(h + 1) − T(h); only consecutive hours. */
    private fun rows(byHour: Map<Long, HourPoint>, lag: Int): List<Row> =
        byHour.values.mapNotNull { p ->
            val next = byHour[p.hour + 1] ?: return@mapNotNull null
            val lagged = byHour[p.hour - lag] ?: return@mapNotNull null
            Row(doubleArrayOf(-(p.temperature - p.outside), lagged.valve, 1.0), next.temperature - p.temperature)
        }

    private fun fitLag(rows: List<Row>, lag: Int, prior: GreyBoxPrior): GreyBoxModel? {
        if (rows.size < MIN_POINTS) return null
        val theta0 = doubleArrayOf(prior.k, prior.power, prior.gain)
        val precision = doubleArrayOf(1 / sq(prior.kSd), 1 / sq(prior.powerSd), 1 / sq(prior.gainSd))
        val xtx = Array(3) { DoubleArray(3) }
        val xty = DoubleArray(3)
        for (r in rows) for (i in 0..2) {
            xty[i] += r.x[i] * r.y
            for (j in 0..2) xtx[i][j] += r.x[i] * r.x[j]
        }
        // Noise variance and parameters depend on each other; a few rounds settle it.
        var noise = 0.01
        var theta = theta0
        var cov = Array(3) { DoubleArray(3) }
        repeat(4) {
            val a = Array(3) { i -> DoubleArray(3) { j -> xtx[i][j] + if (i == j) noise * precision[i] else 0.0 } }
            val inv = invert3(a) ?: return null
            val b = DoubleArray(3) { i -> xty[i] + noise * precision[i] * theta0[i] }
            theta = DoubleArray(3) { i -> dot(inv[i], b) }
            cov = Array(3) { i -> DoubleArray(3) { j -> noise * inv[i][j] } }
            val rss = rows.sumOf { r -> sq(r.y - dot(r.x, theta)) }
            noise = (rss / (rows.size - 3).coerceAtLeast(1)).coerceAtLeast(1e-6)
        }
        val rss = rows.sumOf { r -> sq(r.y - dot(r.x, theta)) }
        return GreyBoxModel(
            // Negative loss or power is unphysical; the prior keeps this rare.
            k = theta[0].coerceAtLeast(0.0),
            power = theta[1].coerceAtLeast(0.0),
            gain = theta[2],
            lagHours = lag,
            kSe = sqrt(cov[0][0]),
            powerSe = sqrt(cov[1][1]),
            gainSe = sqrt(cov[2][2]),
            rmse = sqrt(rss / rows.size),
            n = rows.size,
            prior = prior,
        )
    }

    private fun invert3(m: Array<DoubleArray>): Array<DoubleArray>? {
        val (a, b, c) = m[0].let { Triple(it[0], it[1], it[2]) }
        val (d, e, f) = m[1].let { Triple(it[0], it[1], it[2]) }
        val (g, h, i) = m[2].let { Triple(it[0], it[1], it[2]) }
        val ca = e * i - f * h
        val cb = -(d * i - f * g)
        val cc = d * h - e * g
        val det = a * ca + b * cb + c * cc
        if (abs(det) < 1e-12) return null
        return arrayOf(
            doubleArrayOf(ca / det, -(b * i - c * h) / det, (b * f - c * e) / det),
            doubleArrayOf(cb / det, (a * i - c * g) / det, -(a * f - c * d) / det),
            doubleArrayOf(cc / det, -(a * h - b * g) / det, (a * e - b * d) / det),
        )
    }

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun sq(x: Double) = x * x
}
