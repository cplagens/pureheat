package de.cplagens.pureheat.ui

import de.cplagens.pureheat.data.model.Prediction
import de.cplagens.pureheat.data.model.RoomInsight
import de.cplagens.pureheat.data.model.RoomModel
import java.util.Locale
import kotlin.math.roundToInt

/** German texts for model predictions, shared by the dashboard and the detail view. */
internal object ModelTexts {

    /**
     * One line for the room card, e.g. "15,0 → 24,0 °C in ca. 6 h 45 min · zurück auf 15,0 °C in
     * ca. 16 h (bei 7 °C außen)", or a "lernt noch" note; null if there is nothing to say.
     */
    fun summary(insight: RoomInsight?): String? {
        insight ?: return null
        val m = insight.model
        if (!m.confident) {
            return "Raummodell lernt noch (Ventil offen ${m.heatingPhases}/${RoomModel.MIN_PHASES}, " +
                "Ventil zu ${m.coolingPhases}/${RoomModel.MIN_PHASES} verwertbare Phasen)"
        }
        val levels = insight.levels ?: return "Keine Absenkung im Heizprofil erkannt."
        val outside = insight.outside ?: return null
        return "${levels.setback.t()} → ${levels.comfort.t()} °C ${phrase(insight.heatUp, levels.comfort, outside)} · " +
            "zurück auf ${levels.setback.t()} °C ${phrase(insight.coolDown, levels.setback, outside)} " +
            "(bei ${outside.t0()} °C außen)"
    }

    fun phrase(p: Prediction, target: Double, outside: Double): String = when (p) {
        is Prediction.Hours -> "in ca. ${duration(p.hours)}"
        Prediction.Unreachable -> "bei ${outside.t0()} °C außen nicht erreichbar (${target.t()} °C)"
        Prediction.Unknown -> "– noch unbekannt"
    }

    /** Rounded to 15 min: "45 min", "6 h", "6 h 45 min"; "über 24 h" beyond a day. */
    fun duration(hours: Double): String {
        if (hours > 24) return "über 24 h"
        val quarters = (hours * 4).roundToInt().coerceAtLeast(1)
        val h = quarters / 4
        val min = (quarters % 4) * 15
        return when {
            h == 0 -> "$min min"
            min == 0 -> "$h h"
            else -> "$h h $min min"
        }
    }

    fun Double.t(): String = String.format(Locale.GERMANY, "%.1f", this)
    fun Double.t0(): String = String.format(Locale.GERMANY, "%.0f", this)
}
