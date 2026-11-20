/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.content.Context
import de.cplagens.pureheat.R
import de.cplagens.pureheat.data.model.GreyBoxModel
import de.cplagens.pureheat.data.model.Prediction
import de.cplagens.pureheat.data.model.RoomInsight
import de.cplagens.pureheat.data.model.RoomModel
import kotlin.math.roundToInt

/** Texts for model predictions, shared by the dashboard and the detail view. */
internal object ModelTexts {

    /**
     * One line for the room card, e.g. "15.0 → 24.0 °C in about 6 h 45 min · back to 15.0 °C in
     * about 16 h (at 7 °C outside)", or a "still learning" note; null if there is nothing to say.
     */
    fun summary(ctx: Context, insight: RoomInsight?): String? {
        insight ?: return null
        val m = insight.model
        if (!m.confident) {
            return ctx.getString(R.string.model_learning, m.heatingPhases, RoomModel.MIN_PHASES, m.coolingPhases, RoomModel.MIN_PHASES)
        }
        val levels = insight.levels ?: return ctx.getString(R.string.model_no_setback)
        val outside = insight.outside ?: return null
        return ctx.getString(
            if (insight.outsideIsForecast) R.string.model_summary_forecast else R.string.model_summary,
            levels.setback,
            levels.comfort,
            phrase(ctx, insight.heatUp, levels.comfort, outside),
            phrase(ctx, insight.coolDown, levels.setback, outside),
            outside,
        )
    }

    /** Same line as [summary] with the grey-box model; no "still learning": it always has values. */
    fun greyBoxSummary(ctx: Context, model: GreyBoxModel, insight: RoomInsight): String? {
        val levels = insight.levels ?: return null
        val outside = insight.outside ?: return null
        return ctx.getString(
            if (insight.outsideIsForecast) R.string.model_summary_forecast else R.string.model_summary,
            levels.setback,
            levels.comfort,
            phrase(ctx, model.heatUp(levels.setback, levels.comfort, outside), levels.comfort, outside),
            phrase(ctx, model.coolDown(levels.comfort, levels.setback, outside), levels.setback, outside),
            outside,
        )
    }

    /** "k: 0.031/h measured · P: 0.48 °C/h estimated · other sources: … · dead time: 2 h". */
    fun greyBoxParameters(ctx: Context, m: GreyBoxModel): String {
        val tag: (String, Boolean) -> String = { value, measured ->
            ctx.getString(if (measured) R.string.greybox_measured else R.string.greybox_estimated, value)
        }
        return ctx.getString(
            R.string.greybox_parameters,
            tag(ctx.getString(R.string.model_k, m.k), m.kMeasured),
            tag(ctx.getString(R.string.model_p, m.power), m.powerMeasured),
            tag(ctx.getString(R.string.greybox_gain, m.gain), m.gainMeasured),
            ctx.getString(R.string.duration_h, m.lagHours),
        )
    }

    fun phrase(ctx: Context, p: Prediction, target: Double, outside: Double): String = when (p) {
        is Prediction.Hours -> ctx.getString(R.string.model_in_about, duration(ctx, p.hours))
        Prediction.Unreachable -> ctx.getString(R.string.model_unreachable, outside, target)
        Prediction.Unknown -> ctx.getString(R.string.model_unknown)
    }

    /** Rounded to 15 min: "45 min", "6 h", "6 h 45 min"; "over 24 h" beyond a day. */
    fun duration(ctx: Context, hours: Double): String {
        if (hours > 24) return ctx.getString(R.string.duration_over_day)
        val quarters = (hours * 4).roundToInt().coerceAtLeast(1)
        val h = quarters / 4
        val min = (quarters % 4) * 15
        return when {
            h == 0 -> ctx.getString(R.string.duration_min, min)
            min == 0 -> ctx.getString(R.string.duration_h, h)
            else -> ctx.getString(R.string.duration_h_min, h, min)
        }
    }
}
