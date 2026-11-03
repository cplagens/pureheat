package de.cplagens.pureheat.data.probe

import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WindowEvent
import de.cplagens.pureheat.data.model.RoomModel
import de.cplagens.pureheat.data.model.RoomSimulator
import de.cplagens.pureheat.data.room.RoomPriors
import de.cplagens.pureheat.data.room.RoomProperties
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.Weekday
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Strategies that make predictions side by side (physics now; later grey box / AI in simulation mode). */
object Strategy {
    const val PHYSICS = "physics"
}

/** How the model parameters were obtained. */
object ModelSource {
    const val FIT = "fit"
    /** Starting values from the first measurements (FINDINGS.md) while the room is still learning. */
    const val PRIOR = "prior"
}

/** Result of comparing a prediction with what happened. */
object Outcome {
    const val HIT = "hit"
    const val EARLY = "early"
    const val LATE = "late"
    const val NOT_REACHED = "not_reached"
    /** Open window or a manual setpoint change – not counted. */
    const val DISTURBED = "disturbed"
}

/** A predicted heat-up: the setpoint rises at [eventTime] to [target]; the room gets there at [predictedReach]. */
data class HeatUpPrediction(
    val eventTime: Long,
    val target: Double,
    val startTemperature: Double,
    /** null = not reached before the setpoint drops again (or within the horizon). */
    val predictedReach: Long?,
)

data class Evaluation(val actualReach: Long?, val outcome: String) {
    fun errorMinutes(predicted: Long?): Long? =
        if (predicted != null && actualReach != null) (actualReach - predicted) / 60_000 else null
}

/**
 * Probe run (step 5): predict when each heat-up of the active profile reaches its target and,
 * once it is over, compare with the recorded temperatures. Nothing is written to the heating.
 * Pure Kotlin.
 */
object ProbeRun {
    const val HORIZON_MS = 24 * 3_600_000L
    /** How long after the switch we keep looking for the target to be reached. */
    const val REACH_WINDOW_MS = 12 * 3_600_000L
    const val REACHED_MARGIN = 0.2
    const val MIN_RISE = 0.4
    const val HIT_MINUTES = 30L
    private const val GRID_MS = 15 * 60_000L

    /** Starting values while a room has not learned enough (first measurements, see FINDINGS.md). */
    val PRIOR = RoomModel(k = 0.02, power = 0.8, deadTimeHours = 1.5, coolingPhases = 0, heatingPhases = 0, kSpread = null)

    /** Setpoint of a weekly profile at time [t] in [zone]. */
    fun setPointAt(days: Map<Weekday, ProfileDay>, t: Long, zone: ZoneId): Double? {
        val local = Instant.ofEpochMilli(t).atZone(zone)
        val day = days[Weekday.entries[local.dayOfWeek.value - 1]] ?: return null
        val minute = local.hour * 60 + local.minute
        return day.periods.firstOrNull { minute >= it.start && minute < it.end }?.temperature ?: day.baseTemperature
    }

    /**
     * Model to use: the fitted one if it knows k and P, else [PRIOR] adjusted by the room's
     * [properties] (see [RoomPriors]). Returns the model and its source.
     */
    fun modelFor(fitted: RoomModel, properties: RoomProperties? = null): Pair<RoomModel, String> {
        val prior = RoomPriors.model(PRIOR, properties)
        return if (fitted.k != null && fitted.power != null) {
            fitted.copy(deadTimeHours = fitted.deadTimeHours ?: prior.deadTimeHours) to ModelSource.FIT
        } else {
            prior to ModelSource.PRIOR
        }
    }

    fun predict(
        model: RoomModel,
        startTemperature: Double,
        now: Long,
        days: Map<Weekday, ProfileDay>,
        zone: ZoneId,
        outsideAt: (Long) -> Double,
    ): List<HeatUpPrediction> {
        val k = model.k ?: return emptyList()
        val p = model.power ?: return emptyList()
        val sp: (Long) -> Double? = { setPointAt(days, it, zone) }
        val sim = RoomSimulator.simulate(k, p, model.deadTimeHours ?: 0.0, startTemperature, now, now + HORIZON_MS + REACH_WINDOW_MS, sp, outsideAt)

        val result = mutableListOf<HeatUpPrediction>()
        var t = now - now % GRID_MS + GRID_MS
        while (t <= now + HORIZON_MS) {
            val before = sp(t - GRID_MS)
            val target = sp(t)
            if (before != null && target != null && target - before >= MIN_RISE) {
                val atEvent = sim.firstOrNull { it.time >= t } ?: break
                val reach = sim.asSequence()
                    .filter { it.time >= t && it.time <= t + REACH_WINDOW_MS }
                    .takeWhile { it.setPoint >= target - 0.05 }
                    .firstOrNull { it.temperature >= target - REACHED_MARGIN }
                result += HeatUpPrediction(t, target, atEvent.temperature, reach?.time)
            }
            t += GRID_MS
        }
        return result
    }

    /**
     * Compares a prediction with the recorded samples of the room. Returns null while the
     * reach window is still open.
     */
    fun evaluate(
        prediction: HeatUpPrediction,
        samples: List<RoomSample>,
        windows: List<WindowEvent>,
        now: Long,
    ): Evaluation? {
        val until = prediction.eventTime + REACH_WINDOW_MS
        val relevant = samples.filter { it.timestamp >= prediction.eventTime && it.timestamp <= until }
        val actual = relevant.takeWhile { (it.setPointTemperature ?: prediction.target) >= prediction.target - 0.05 }
            .firstOrNull { (it.actualTemperature ?: Double.NEGATIVE_INFINITY) >= prediction.target - REACHED_MARGIN }
        if (actual == null && now < until) return null

        // Manual change: the recorded setpoint shortly after the switch doesn't match the profile.
        val setPointAfter = relevant.firstOrNull { it.timestamp >= prediction.eventTime + GRID_MS }?.setPointTemperature
        val manual = setPointAfter != null && abs(setPointAfter - prediction.target) > MIN_RISE
        val windowOpen = windows.any { w ->
            val end = w.end ?: now
            w.start <= (actual?.timestamp ?: until) && end >= prediction.eventTime - 2 * 3_600_000L
        }
        if (manual || windowOpen) return Evaluation(actual?.timestamp, Outcome.DISTURBED)

        val predicted = prediction.predictedReach
        val outcome = when {
            actual == null -> Outcome.NOT_REACHED
            predicted == null -> Outcome.EARLY // reached although we predicted it wouldn't
            abs(actual.timestamp - predicted) <= HIT_MINUTES * 60_000 -> Outcome.HIT
            actual.timestamp < predicted -> Outcome.EARLY
            else -> Outcome.LATE
        }
        return Evaluation(actual?.timestamp, outcome)
    }
}

/** Score of the evaluated, undisturbed predictions of one room. */
data class ProbeScore(
    val evaluated: Int,
    val hits: Int,
    val meanAbsErrorMinutes: Long?,
    val days: Int,
) {
    val hitRate: Double get() = if (evaluated == 0) 0.0 else hits.toDouble() / evaluated

    /** Thresholds from NEXT_STEPS for offering the optimal control (step 6). */
    val trustworthy: Boolean
        get() = days >= MIN_DAYS && hitRate >= MIN_HIT_RATE && (meanAbsErrorMinutes ?: Long.MAX_VALUE) <= MAX_MEAN_ERROR

    companion object {
        const val MIN_DAYS = 14
        const val MIN_HIT_RATE = 0.8
        const val MAX_MEAN_ERROR = 30L
    }
}
