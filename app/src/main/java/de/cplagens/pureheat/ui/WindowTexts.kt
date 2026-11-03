/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.content.Context
import android.text.format.DateFormat
import de.cplagens.pureheat.R
import de.cplagens.pureheat.data.history.WindowEvent
import java.util.Date

/** Texts for detected open-window times. */
internal object WindowTexts {
    private fun time(ctx: Context, t: Long) = DateFormat.getTimeFormat(ctx).format(Date(t))
    private fun dayTime(ctx: Context, t: Long) =
        DateFormat.format(DateFormat.getBestDateTimePattern(currentLocale(ctx), "EEE Hm"), t).toString()

    /** The last event of the shown 24 h; null if none. */
    fun dashboardLine(ctx: Context, events: List<WindowEvent>): String? {
        val last = events.lastOrNull() ?: return null
        val end = last.end
        return when {
            end == null -> ctx.getString(R.string.window_open_since, time(ctx, last.start), last.drop)
            last.closeDetected -> ctx.getString(R.string.window_open_from_to, time(ctx, last.start), time(ctx, end), last.drop)
            else -> ctx.getString(R.string.window_open_at_least, time(ctx, last.start), time(ctx, end), last.drop)
        }
    }

    /** One line per event for the detail view. */
    fun describe(ctx: Context, e: WindowEvent): String {
        val end = when {
            e.end == null -> ctx.getString(R.string.window_end_now)
            e.closeDetected -> time(ctx, e.end)
            else -> ctx.getString(R.string.window_end_unknown, time(ctx, e.end))
        }
        val base = ctx.getString(R.string.window_event, dayTime(ctx, e.start), end, e.startTemperature, e.minTemperature)
        return e.humidityDrop?.let { base + ctx.getString(R.string.window_humidity_drop, it) } ?: base
    }
}

internal fun currentLocale(ctx: Context) = ctx.resources.configuration.locales[0]
