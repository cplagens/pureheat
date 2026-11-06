/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.history

/**
 * A stretch in which a window was most likely open, detected from the temperature curve
 * (there are no window contacts). [end] is null while the window still seems to be open.
 */
data class WindowEvent(
    val start: Long,
    val end: Long?,
    val startTemperature: Double,
    /** Lowest temperature reached while open. */
    val minTemperature: Double,
    /** Humidity drop at the opening in %rH, if humidity was recorded. */
    val humidityDrop: Int?,
    /**
     * True if the closing was actually seen (temperature rose again). Otherwise [end] is only
     * the last moment we know it was still open (heating started, gap in the data, time limit).
     */
    val closeDetected: Boolean = false,
) {
    val drop: Double get() = startTemperature - minTemperature
}

/**
 * Heuristic window detection for floor-heated rooms, where the temperature otherwise changes
 * only slowly:
 * - **opened:** valve closed, humidity not rising, and the room falls by ≥ 0.3 K at ≥ 0.6 K/h
 *   (or by ≥ 0.2 K together with a humidity drop of ≥ 4 %rH, or by ≥ 0.5 K between two samples);
 * - **closed:** the temperature rises again by ≥ 0.2 K above the lowest point with the valve still
 *   closed (the air warms up from walls and floor), or heating starts, or a long gap / > 16 h.
 *
 * Some sensors only report changes of ~0.3–0.4 K, so after hours of the same value a slow cooling
 * shows up as one sudden step. The rate is therefore taken since the value *first appeared*, not
 * since the previous sample – this also makes it independent of how often we poll (FINDINGS).
 * Pure Kotlin so it can move to a JVM module later.
 */
object WindowDetector {
    const val MIN_DROP = 0.3
    const val MIN_DROP_RATE = 0.6
    const val HUMIDITY_DROP = 4
    const val HUMIDITY_MIN_TEMP_DROP = 0.2
    /** A drop this large between two samples counts even right after a long stretch of the same value. */
    const val STRONG_DROP = 0.5
    const val RECOVERY_RISE = 0.2
    const val MAX_STEP_MS = 75 * 60_000L
    const val MAX_OPEN_MS = 16 * 3_600_000L
    private const val CLOSED = 0.05

    /** Temperature differences rounded to 0.01 K, so 21.4 − 21.2 (0.1999… in Double) counts as 0.2. */
    private fun diff(a: Double, b: Double) = Math.round((a - b) * 100) / 100.0

    /** [samples] of one room, sorted by time. */
    fun detect(samples: List<RoomSample>): List<WindowEvent> {
        val events = mutableListOf<WindowEvent>()
        var open: WindowEvent? = null
        // Since when the temperature of `a` has been reported unchanged.
        var runTemperature: Double? = null
        var runStart = 0L

        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            if (a.actualTemperature != runTemperature) {
                runTemperature = a.actualTemperature
                runStart = a.timestamp
            }
            val ta = a.actualTemperature ?: continue
            val tb = b.actualTemperature ?: continue
            val step = b.timestamp - a.timestamp
            val current = open

            if (current != null) {
                val heating = (b.valvePosition ?: 0.0) > CLOSED
                val tooLong = b.timestamp - current.start > MAX_OPEN_MS
                when {
                    step > 2 * MAX_STEP_MS || heating || tooLong -> {
                        // We can't tell when it was closed; end at the last sample we trust.
                        events += current.copy(end = a.timestamp)
                        open = null
                    }
                    // Measured from the lowest point, so a slow rise over several samples counts too.
                    diff(tb, minOf(current.minTemperature, ta)) >= RECOVERY_RISE -> {
                        events += current.copy(
                            end = b.timestamp,
                            minTemperature = minOf(current.minTemperature, ta),
                            closeDetected = true,
                        )
                        open = null
                    }
                    else -> open = current.copy(minTemperature = minOf(current.minTemperature, tb))
                }
                continue
            }

            if (step > MAX_STEP_MS) continue
            if ((a.valvePosition ?: 1.0) > CLOSED || (b.valvePosition ?: 1.0) > CLOSED) continue
            val drop = diff(ta, tb)
            val humidityDrop = if (a.humidity != null && b.humidity != null) a.humidity - b.humidity else null
            // Outside air is drier: rising humidity speaks against an open window (e.g. a sensor step).
            if (humidityDrop != null && humidityDrop < 0) continue
            val rateSinceChange = drop / ((b.timestamp - runStart) / 3_600_000.0)
            val byTemperature = drop >= MIN_DROP && rateSinceChange >= MIN_DROP_RATE
            val byHumidity = humidityDrop != null && humidityDrop >= HUMIDITY_DROP && drop >= HUMIDITY_MIN_TEMP_DROP
            val byStrongDrop = drop >= STRONG_DROP && drop / (step / 3_600_000.0) >= MIN_DROP_RATE
            if (byTemperature || byHumidity || byStrongDrop) {
                open = WindowEvent(
                    start = a.timestamp,
                    end = null,
                    startTemperature = ta,
                    minTemperature = tb,
                    humidityDrop = humidityDrop?.takeIf { it > 0 },
                )
            }
        }
        open?.let { events += it }
        return events
    }

    /**
     * The same samples with the valve blanked out inside [events], so [HistoryAnalysis.phases]
     * doesn't treat an open window as normal heat loss.
     */
    fun withoutWindowTimes(samples: List<RoomSample>, events: List<WindowEvent>): List<RoomSample> =
        if (events.isEmpty()) samples else samples.map { s ->
            val inside = events.any { e -> s.timestamp >= e.start && s.timestamp <= (e.end ?: Long.MAX_VALUE) }
            if (inside) s.copy(valvePosition = null) else s
        }
}
