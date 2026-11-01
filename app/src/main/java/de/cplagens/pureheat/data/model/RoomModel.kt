package de.cplagens.pureheat.data.model

import de.cplagens.pureheat.data.history.PhaseKind
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.ThermalPhase
import kotlin.math.ln

/**
 * First-order thermal model of one room (T = room, Tₐ = outside, times in hours):
 *
 * - valve closed: dT/dt = −k·(T − Tₐ)
 * - valve open:   dT/dt = P − k·(T − Tₐ), starting after a dead time d (screed)
 *
 * Learned from [ThermalPhase]s; pure Kotlin (no Android) so it can run on a Raspberry Pi later.
 */
data class RoomModel(
    /** Heat loss coefficient in 1/h, null until enough cooling phases were seen. */
    val k: Double?,
    /** Heating power in K/h, null until enough heating phases (and k) are known. */
    val power: Double?,
    /** Dead time between opening the valve and the room starting to warm up. */
    val deadTimeHours: Double?,
    val coolingPhases: Int,
    val heatingPhases: Int,
    /** Relative spread (IQR / median) of the k estimates; lower is more consistent. */
    val kSpread: Double?,
) {
    /** Enough observations to show predictions without a "lernt noch" caveat. */
    val confident: Boolean
        get() = k != null && power != null && coolingPhases >= MIN_PHASES && heatingPhases >= MIN_PHASES

    /**
     * Hours to heat from [from] to [to] at [outside], including the dead time.
     * [Prediction.Unreachable] if the heating can't overcome the heat loss at [to].
     */
    fun heatUp(from: Double, to: Double, outside: Double): Prediction {
        val k = k ?: return Prediction.Unknown
        val p = power ?: return Prediction.Unknown
        if (to <= from) return Prediction.Hours(0.0)
        val lossAtTarget = k * (to - outside)
        if (p <= lossAtTarget) return Prediction.Unreachable
        val hours = if (k < 1e-4) {
            (to - from) / p
        } else {
            ln((p - k * (from - outside)) / (p - lossAtTarget)) / k
        }
        return Prediction.Hours(hours + (deadTimeHours ?: 0.0))
    }

    /** Hours for the room to cool from [from] to [to] with the valve closed. */
    fun coolDown(from: Double, to: Double, outside: Double): Prediction {
        val k = k ?: return Prediction.Unknown
        if (to >= from) return Prediction.Hours(0.0)
        if (to <= outside) return Prediction.Unreachable
        return Prediction.Hours(ln((from - outside) / (to - outside)) / k)
    }

    companion object {
        const val MIN_PHASES = 3
    }
}

sealed interface Prediction {
    data class Hours(val hours: Double) : Prediction
    /** Target can't be reached (too little heating power / below outside temperature). */
    data object Unreachable : Prediction
    /** Model doesn't know enough yet. */
    data object Unknown : Prediction
}

object RoomModelFitter {
    /** Cooling phases with a smaller indoor/outdoor difference are too noisy for k. */
    const val MIN_DELTA_T = 3.0

    /** Heating needs this long after the dead time to give a usable rate. */
    const val MIN_RESPONSE_HOURS = 0.75

    fun fit(phases: List<ThermalPhase>): RoomModel {
        val kEstimates = phases.filter { it.kind == PhaseKind.COOLING }.mapNotNull { p ->
            val out = p.meanOutside ?: return@mapNotNull null
            val delta = p.meanTemperature - out
            // A closed valve with rising temperature (towel radiator, sun, …) says nothing about loss.
            if (delta < MIN_DELTA_T || p.ratePerHour >= 0) return@mapNotNull null
            -p.ratePerHour / delta
        }
        val k = median(kEstimates)

        val heating = phases.filter { it.kind == PhaseKind.HEATING }
        val deadTimes = heating.mapNotNull { p -> p.responseStart?.let { (it - p.start) / 3_600_000.0 } }
        val pEstimates = if (k == null) emptyList() else heating.mapNotNull { p ->
            val out = p.meanOutside ?: return@mapNotNull null
            // Only the free heat-up: from the end of the dead time until the room nears its setpoint.
            val rs = p.responseStart ?: return@mapNotNull null
            val rsTemp = p.responseStartTemperature ?: return@mapNotNull null
            val he = p.heatUpEnd ?: return@mapNotNull null
            val heTemp = p.heatUpEndTemperature ?: return@mapNotNull null
            val hours = (he - rs) / 3_600_000.0
            if (hours < MIN_RESPONSE_HOURS) return@mapNotNull null
            val rate = (heTemp - rsTemp) / hours
            (rate + k * ((rsTemp + heTemp) / 2 - out)).takeIf { it > 0 }
        }

        return RoomModel(
            k = k,
            power = median(pEstimates),
            deadTimeHours = median(deadTimes),
            coolingPhases = kEstimates.size,
            heatingPhases = pEstimates.size,
            kSpread = spread(kEstimates),
        )
    }

    private fun median(xs: List<Double>): Double? {
        if (xs.isEmpty()) return null
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun spread(xs: List<Double>): Double? {
        if (xs.size < 4) return null
        val s = xs.sorted()
        val q1 = s[s.size / 4]
        val q3 = s[(3 * s.size) / 4]
        val m = median(xs) ?: return null
        return if (m > 0) (q3 - q1) / m else null
    }
}

/**
 * Setback and comfort temperature of a room, derived from the recorded setpoints (so no
 * extra API calls for the profile): 10th and 90th percentile, which ignores short manual
 * changes. Null if there is no clear setback (difference < 1 K).
 */
data class SetPointLevels(val setback: Double, val comfort: Double) {
    companion object {
        fun from(samples: List<RoomSample>): SetPointLevels? {
            val sp = samples.mapNotNull { it.setPointTemperature }.sorted()
            if (sp.size < 8) return null
            val low = sp[(sp.size * 0.1).toInt()]
            val high = sp[((sp.size * 0.9).toInt()).coerceAtMost(sp.lastIndex)]
            return if (high - low >= 1.0) SetPointLevels(low, high) else null
        }
    }
}
