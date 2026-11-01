package de.cplagens.pureheat.data.history

/** A room whose valve has been open for a while without the temperature following. */
data class StalledHeating(val openSince: Long, val temperatureRise: Double)

enum class PhaseKind { HEATING, COOLING }

/**
 * A stretch of at least an hour in which the valve stayed open ([PhaseKind.HEATING]) or
 * closed ([PhaseKind.COOLING]). These are the raw material for the room model.
 */
data class ThermalPhase(
    val kind: PhaseKind,
    val start: Long,
    val end: Long,
    val startTemperature: Double,
    val endTemperature: Double,
    /** Mean outside temperature during the phase, if recorded. */
    val meanOutside: Double?,
    /** Mean room temperature during the phase. */
    val meanTemperature: Double = (startTemperature + endTemperature) / 2,
    /** Setpoint at the end of the phase (tells whether the room was still heating up). */
    val setPointAtEnd: Double? = null,
    /**
     * Heating only: the low point from which the room starts warming up (the room keeps
     * cooling while the screed heats). The time before it is the dead time; null if the room
     * never rose ≥ 0.1 K above its start temperature.
     */
    val responseStart: Long? = null,
    val responseStartTemperature: Double? = null,
    /**
     * Heating only: end of the actual heat-up – first moment within [APPROACH_MARGIN] of the
     * setpoint (after that the valve merely holds the temperature), else the phase end.
     */
    val heatUpEnd: Long? = null,
    val heatUpEndTemperature: Double? = null,
) {
    val hours: Double get() = (end - start) / 3_600_000.0
    val ratePerHour: Double get() = (endTemperature - startTemperature) / hours
}

object HistoryAnalysis {
    const val MIN_OPEN_MILLIS = 2 * 60 * 60 * 1000L
    const val MIN_VALVE = 0.5
    const val MAX_RISE = 0.3
    const val MIN_BELOW_SETPOINT = 0.5

    /** Samples further apart than this split the history: we don't know what happened in between. */
    const val MAX_GAP_MILLIS = 60 * 60 * 1000L

    /**
     * Detects "valve open for at least 2 h, room still clearly below the setpoint and
     * warmed up by less than 0.3 K". A valve that stays open while the room sits at its
     * setpoint is normal (holding temperature) and is not reported.
     *
     * [samples] must belong to one room and be sorted by timestamp.
     */
    fun stalledHeating(samples: List<RoomSample>): StalledHeating? {
        val last = samples.lastOrNull() ?: return null
        if ((last.valvePosition ?: 0.0) < MIN_VALVE) return null
        val actual = last.actualTemperature ?: return null
        val setPoint = last.setPointTemperature ?: return null
        if (setPoint - actual < MIN_BELOW_SETPOINT) return null

        var start = samples.lastIndex
        while (start > 0) {
            val prev = samples[start - 1]
            if ((prev.valvePosition ?: 0.0) < MIN_VALVE) break
            if (samples[start].timestamp - prev.timestamp > MAX_GAP_MILLIS) break
            start--
        }
        val openSince = samples[start].timestamp
        if (last.timestamp - openSince < MIN_OPEN_MILLIS) return null

        val first = samples.subList(start, samples.size).firstNotNullOfOrNull { it.actualTemperature } ?: return null
        val rise = actual - first
        return if (rise < MAX_RISE) StalledHeating(openSince, rise) else null
    }

    /** Valve at or below this counts as closed for cooling phases. */
    const val CLOSED_VALVE = 0.05

    /** Rise that marks the end of the dead time in a heating phase. */
    const val RESPONSE_DELTA = 0.1

    /** Closer than this to the setpoint the room is no longer heating up freely. */
    const val APPROACH_MARGIN = 0.3

    /**
     * Splits one room's history into heating (valve ≥ 50 %) and cooling (valve closed)
     * phases of at least an hour. Gaps > 1 h and missing temperatures end a phase.
     * [samples] and [weather] must be sorted by timestamp.
     */
    fun phases(samples: List<RoomSample>, weather: List<WeatherSample>): List<ThermalPhase> {
        fun kindOf(s: RoomSample): PhaseKind? {
            if (s.actualTemperature == null) return null
            val v = s.valvePosition ?: return null
            return when {
                v >= MIN_VALVE -> PhaseKind.HEATING
                v <= CLOSED_VALVE -> PhaseKind.COOLING
                else -> null
            }
        }

        val result = mutableListOf<ThermalPhase>()
        var run = mutableListOf<RoomSample>()
        var runKind: PhaseKind? = null
        fun close() {
            val kind = runKind
            if (kind != null && run.size >= 2 && run.last().timestamp - run.first().timestamp >= MIN_HEATUP_MILLIS) {
                val start = run.first().timestamp
                val end = run.last().timestamp
                val outside = weather.filter { it.timestamp in start..end }.mapNotNull { it.outsideTemperature }
                val startTemp = run.first().actualTemperature!!
                val responseIndex = if (kind == PhaseKind.HEATING) {
                    val risen = run.indexOfFirst { it.actualTemperature!! >= startTemp + RESPONSE_DELTA }
                    // Last minimum before the rise (lastIndexOf: a flat bottom ends where warming begins).
                    if (risen < 0) null else run.subList(0, risen + 1).let { head ->
                        val min = head.minOf { it.actualTemperature!! }
                        head.indexOfLast { it.actualTemperature!! == min }
                    }
                } else {
                    null
                }
                val response = responseIndex?.let { run[it] }
                val heatUpEnd = responseIndex?.let { from ->
                    run.drop(from).firstOrNull { s ->
                        val sp = s.setPointTemperature
                        sp != null && s.actualTemperature!! >= sp - APPROACH_MARGIN
                    } ?: run.last()
                }
                result += ThermalPhase(
                    kind = kind,
                    start = start,
                    end = end,
                    startTemperature = startTemp,
                    endTemperature = run.last().actualTemperature!!,
                    meanOutside = outside.takeIf { it.isNotEmpty() }?.average(),
                    meanTemperature = run.map { it.actualTemperature!! }.average(),
                    setPointAtEnd = run.last().setPointTemperature,
                    responseStart = response?.timestamp,
                    responseStartTemperature = response?.actualTemperature,
                    heatUpEnd = heatUpEnd?.timestamp,
                    heatUpEndTemperature = heatUpEnd?.actualTemperature,
                )
            }
            run = mutableListOf()
            runKind = null
        }
        for (s in samples) {
            val kind = kindOf(s)
            val gap = run.lastOrNull()?.let { s.timestamp - it.timestamp > MAX_GAP_MILLIS } ?: false
            if (kind == null || kind != runKind || gap) close()
            if (kind != null) {
                runKind = kind
                run += s
            }
        }
        close()
        return result
    }

    /** Heat-up phases shorter than this are too noisy to derive a rate from. */
    const val MIN_HEATUP_MILLIS = 60 * 60 * 1000L

    /**
     * Typical heat-up rate of a room in K per hour: the median over all phases in which the
     * valve was at least half open while the room was still clearly below its setpoint.
     * Null if the history doesn't contain such a phase of at least an hour yet.
     */
    fun heatUpRate(samples: List<RoomSample>): Double? {
        fun heatingUp(s: RoomSample): Boolean {
            val actual = s.actualTemperature ?: return false
            val setPoint = s.setPointTemperature ?: return false
            return (s.valvePosition ?: 0.0) >= MIN_VALVE && setPoint - actual >= 0.3
        }

        val rates = mutableListOf<Double>()
        var phase = mutableListOf<RoomSample>()
        fun closePhase() {
            if (phase.size >= 2) {
                val millis = phase.last().timestamp - phase.first().timestamp
                val rise = phase.last().actualTemperature!! - phase.first().actualTemperature!!
                if (millis >= MIN_HEATUP_MILLIS && rise > 0) rates += rise / (millis / 3_600_000.0)
            }
            phase = mutableListOf()
        }
        for (s in samples) {
            val gap = phase.lastOrNull()?.let { s.timestamp - it.timestamp > MAX_GAP_MILLIS } ?: false
            if (gap || !heatingUp(s)) closePhase()
            if (heatingUp(s)) phase += s
        }
        closePhase()
        return rates.sorted().takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
    }
}
