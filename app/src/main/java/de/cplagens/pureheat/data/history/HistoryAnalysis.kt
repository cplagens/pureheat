package de.cplagens.pureheat.data.history

/** A room whose valve has been open for a while without the temperature following. */
data class StalledHeating(val openSince: Long, val temperatureRise: Double)

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
}
