/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.history

import kotlin.math.max

/**
 * Showering or bathing, detected from a quick humidity rise (hot water vapour) while the
 * temperature does not fall. [end] is the first sample where the humidity is back near the level
 * before, or the last one within [BathingDetector.MAX_DURATION_MS].
 */
data class BathingEvent(
    val start: Long,
    val end: Long,
    val humidityBefore: Int,
    val humidityPeak: Int,
    /** Highest temperature during the event minus the temperature before, in K. */
    val temperatureRise: Double?,
) {
    val humidityRise: Int get() = humidityPeak - humidityBefore
}

/**
 * Heuristic, calibrated on the first real data (2026-11-03): showers showed +15 %rH in 5 min and
 * +5 %rH in 15 min with +0.3 K. Detected: a rise of ≥ [MIN_RISE] %rH between two samples at
 * ≥ [MIN_RATE] %rH/h (gap ≤ [MAX_GAP_MS]) while the temperature doesn't drop by more than 0.1 K.
 *
 * Cooking can look the same; the app only *shows* events for rooms described as bathroom or with
 * a shower/bathtub, but the model leaves all of them out (extra heat that isn't the floor heating).
 */
object BathingDetector {
    const val MIN_RISE = 5
    const val MIN_RATE = 10.0
    const val MAX_GAP_MS = 60 * 60_000L
    const val MAX_DURATION_MS = 3 * 3_600_000L
    const val SETTLED_MARGIN = 3
    private const val MAX_TEMP_DROP = 0.1

    fun detect(samples: List<RoomSample>): List<BathingEvent> {
        val s = samples.filter { it.humidity != null }
        val out = mutableListOf<BathingEvent>()
        var i = 1
        while (i < s.size) {
            val a = s[i - 1]
            val b = s[i]
            val gap = b.timestamp - a.timestamp
            val before = a.humidity!!
            val rise = b.humidity!! - before
            val tempDelta = if (a.actualTemperature != null && b.actualTemperature != null) b.actualTemperature - a.actualTemperature else 0.0
            if (gap !in 1..MAX_GAP_MS || rise < MIN_RISE || rise * 3_600_000.0 / gap < MIN_RATE || tempDelta < -MAX_TEMP_DROP) {
                i++
                continue
            }
            var peak: Int = b.humidity
            var maxTemp = b.actualTemperature
            var end = b.timestamp
            var j = i + 1
            while (j < s.size) {
                val c = s[j]
                if (c.timestamp - s[j - 1].timestamp > 2 * MAX_GAP_MS || c.timestamp - a.timestamp > MAX_DURATION_MS) break
                val h = c.humidity!!
                peak = max(peak, h)
                c.actualTemperature?.let { t -> maxTemp = maxTemp?.let { max(it, t) } ?: t }
                end = c.timestamp
                if (h <= before + SETTLED_MARGIN) break
                j++
            }
            out += BathingEvent(
                start = a.timestamp + gap / 2,
                end = end,
                humidityBefore = before,
                humidityPeak = peak,
                temperatureRise = a.actualTemperature?.let { t0 -> maxTemp?.let { it - t0 } },
            )
            i = max(j, i) + 1
        }
        return out
    }

    /** Same as [WindowDetector.withoutWindowTimes]: hot water is not the floor heating's doing. */
    fun withoutBathingTimes(samples: List<RoomSample>, events: List<BathingEvent>): List<RoomSample> =
        if (events.isEmpty()) samples else samples.map { s ->
            if (events.any { s.timestamp >= it.start && s.timestamp <= it.end }) s.copy(valvePosition = null) else s
        }
}
