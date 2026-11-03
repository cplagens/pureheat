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
 * - **opened:** valve closed and the room falls by ≥ 0.3 K at ≥ 0.6 K/h between two samples
 *   (or by ≥ 0.2 K together with a humidity drop of ≥ 4 %rH);
 * - **closed:** the temperature rises again by ≥ 0.2 K with the valve still closed (the air
 *   warms up from walls and floor), or heating starts, or there is a long gap / > 16 h.
 *
 * Validated against the first real nights only roughly (sampling was hourly); see NEXT_STEPS.
 * Pure Kotlin so it can move to a JVM module later.
 */
object WindowDetector {
    const val MIN_DROP = 0.3
    const val MIN_DROP_RATE = 0.6
    const val HUMIDITY_DROP = 4
    const val HUMIDITY_MIN_TEMP_DROP = 0.2
    const val RECOVERY_RISE = 0.2
    const val MAX_STEP_MS = 75 * 60_000L
    const val MAX_OPEN_MS = 16 * 3_600_000L
    private const val CLOSED = 0.05

    /** [samples] of one room, sorted by time. */
    fun detect(samples: List<RoomSample>): List<WindowEvent> {
        val events = mutableListOf<WindowEvent>()
        var open: WindowEvent? = null

        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
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
                    tb - ta >= RECOVERY_RISE -> {
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
            val drop = ta - tb
            val rate = drop / (step / 3_600_000.0)
            val humidityDrop = if (a.humidity != null && b.humidity != null) a.humidity - b.humidity else null
            val byTemperature = drop >= MIN_DROP && rate >= MIN_DROP_RATE
            val byHumidity = humidityDrop != null && humidityDrop >= HUMIDITY_DROP && drop >= HUMIDITY_MIN_TEMP_DROP
            if (byTemperature || byHumidity) {
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
