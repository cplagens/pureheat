/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.cplagens.pureheat.R
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.data.history.HistoryAnalysis
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WindowEvent
import de.cplagens.pureheat.data.history.BathingEvent

private const val WINDOW_MS = 24 * 60 * 60 * 1000L

/** Background band for detected open-window times (also used in the detail view). */
internal val WindowBandColor = Color(0xFF29B6F6).copy(alpha = 0.22f)

/**
 * Last 24 h of one room as two small panels on a shared time axis: temperature (actual
 * line + setpoint step line) on top, valve opening (0–100 %) below. Two panels instead of
 * one chart with two y-axes, because °C and % have nothing in common.
 */
@Composable
fun RoomHistoryChart(
    samples: List<RoomSample>,
    now: Long,
    modifier: Modifier = Modifier,
    windows: List<WindowEvent> = emptyList(),
    bathing: List<BathingEvent> = emptyList(),
) {
    val start = now - WINDOW_MS
    val visible = samples.filter { it.timestamp >= start }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (visible.size < 2) {
        Text(stringResource(R.string.history_recording), style = MaterialTheme.typography.labelSmall, color = muted, modifier = modifier)
        return
    }

    val actualColor = MaterialTheme.colorScheme.primary
    val setPointColor = muted
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    // Overview: temperatures as one connected line even across recording gaps (Android may delay
    // background jobs for hours); the detail view keeps the gaps visible.
    val actualRuns = runs(visible, breakOnGaps = false) { it.actualTemperature }
    val setPointRuns = runs(visible, breakOnGaps = false) { it.setPointTemperature }
    val valveRuns = runs(visible, breakOnGaps = true) { it.valvePosition?.coerceIn(0.0, 1.0) }
    // Humidity in its own panel: % and °C don't share a scale (no second y-axis).
    val humidityRuns = runs(visible, breakOnGaps = false) { it.humidity?.toDouble() }
    val humidities = humidityRuns.flatten().map { it.second }
    val humidityColor = MaterialTheme.colorScheme.secondary

    val temps = (actualRuns + setPointRuns).flatten().map { it.second }
    val actualTemps = actualRuns.flatten().map { it.second }
    // At least 2 K of range so sensor noise doesn't look like a heat wave.
    val mid = ((temps.minOrNull() ?: 20.0) + (temps.maxOrNull() ?: 20.0)) / 2
    val minT = minOf(temps.minOrNull() ?: mid, mid - 1.0) - 0.3
    val maxT = maxOf(temps.maxOrNull() ?: mid, mid + 1.0) + 0.3

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(stringResource(R.string.legend_actual), actualColor, dashed = false)
            LegendItem(stringResource(R.string.legend_setpoint), setPointColor, dashed = true)
            LegendItem(stringResource(R.string.legend_valve), HeatingColor.copy(alpha = 0.5f), block = true)
            if (windows.any { (it.end ?: now) >= start }) {
                LegendItem(stringResource(R.string.legend_window), WindowBandColor, block = true)
            }
            Spacer(Modifier.weight(1f))
            if (actualTemps.isNotEmpty()) {
                Text(
                    stringResource(R.string.actual_range, actualTemps.min(), actualTemps.max()),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }

        MarkerStrip(chartMarkers(LocalContext.current, visible, windows, start, now, bathing), start, now)

        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            fun x(t: Long) = ((t - start).toFloat() / WINDOW_MS) * size.width
            fun y(v: Double) = (size.height * (1 - (v - minT) / (maxT - minT))).toFloat()

            // Detected open-window times as a light band behind the lines.
            windows.forEach { w ->
                val left = x(maxOf(w.start, start))
                val right = x(w.end ?: now)
                if (right > left) drawRect(WindowBandColor, Offset(left, 0f), Size(right - left, size.height))
            }
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            setPointRuns.forEach { run ->
                drawPath(
                    stepPath(run, ::x, ::y),
                    setPointColor,
                    // Long dashes: short ones looked like a dotted, broken line on a phone.
                    style = Stroke(
                        1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            actualRuns.forEach { run -> drawLineRun(run, ::x, ::y, actualColor) }
        }

        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            fun x(t: Long) = ((t - start).toFloat() / WINDOW_MS) * size.width
            fun y(v: Double) = (size.height * (1 - v)).toFloat()

            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            valveRuns.forEach { run ->
                val path = stepPath(run, ::x, ::y).apply {
                    lineTo(x(run.last().first), size.height)
                    lineTo(x(run.first().first), size.height)
                    close()
                }
                drawPath(path, HeatingColor.copy(alpha = 0.5f))
            }
        }

        if (humidities.size >= 2) {
            val hMid = (humidities.min() + humidities.max()) / 2
            // At least 10 %rH of range, so small fluctuations stay small.
            val hMin = minOf(humidities.min(), hMid - 5)
            val hMax = maxOf(humidities.max(), hMid + 5)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendItem(stringResource(R.string.legend_humidity), humidityColor)
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.humidity_range, humidities.min().toInt(), humidities.max().toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                fun x(t: Long) = ((t - start).toFloat() / WINDOW_MS) * size.width
                fun y(v: Double) = (size.height * (1 - (v - hMin) / (hMax - hMin))).toFloat()
                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                humidityRuns.forEach { run -> drawLineRun(run, ::x, ::y, humidityColor) }
            }
        }

        Row {
            Text(stringResource(R.string.ago_24h), style = MaterialTheme.typography.labelSmall, color = muted)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.now), style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, dashed: Boolean = false, block: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 10.dp)) {
            Canvas(Modifier.size(width = 14.dp, height = 10.dp)) {
                if (block) {
                    drawRect(color)
                } else {
                    drawLine(
                        color,
                        Offset(0f, size.height / 2),
                        Offset(size.width, size.height / 2),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.dp.toPx())) else null,
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Splits the samples into continuous runs; a missing value (and with [breakOnGaps] a gap > 1 h) breaks the line. */
private fun runs(
    samples: List<RoomSample>,
    breakOnGaps: Boolean,
    value: (RoomSample) -> Double?,
): List<List<Pair<Long, Double>>> {
    val out = mutableListOf<MutableList<Pair<Long, Double>>>()
    var prev: Long? = null
    for (s in samples) {
        val v = value(s)
        if (v == null) {
            prev = null
            continue
        }
        if (prev == null || (breakOnGaps && s.timestamp - prev > HistoryAnalysis.MAX_GAP_MILLIS)) out.add(mutableListOf())
        out.last() += s.timestamp to v
        prev = s.timestamp
    }
    return out
}

/** Setpoints and valve positions change in steps, so they are drawn as a step line. */
private fun stepPath(run: List<Pair<Long, Double>>, x: (Long) -> Float, y: (Double) -> Float) = Path().apply {
    run.forEachIndexed { i, (t, v) ->
        if (i == 0) moveTo(x(t), y(v)) else {
            lineTo(x(t), y(run[i - 1].second))
            lineTo(x(t), y(v))
        }
    }
}

private fun DrawScope.drawLineRun(run: List<Pair<Long, Double>>, x: (Long) -> Float, y: (Double) -> Float, color: Color) {
    if (run.size == 1) {
        drawCircle(color, radius = 2.dp.toPx(), center = Offset(x(run[0].first), y(run[0].second)))
        return
    }
    val path = Path().apply {
        run.forEachIndexed { i, (t, v) -> if (i == 0) moveTo(x(t), y(v)) else lineTo(x(t), y(v)) }
    }
    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun Double.fmt() = "%.1f".format(this)

@Preview(name = "Hell", widthDp = 360)
@Preview(name = "Dunkel", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RoomHistoryChartPreview() {
    val now = 24 * 60 * 60 * 1000L
    // Setpoint raised at noon, valve opens, floor heating slowly catches up; 2 h gap at night.
    val samples = (0 until 96).filter { it !in 10..17 }.map { i ->
        val t = i * 15 * 60 * 1000L
        val heating = i >= 48
        RoomSample(
            roomId = "r",
            timestamp = t,
            actualTemperature = 21.6 + (if (heating) (i - 48) * 0.02 else 0.0) + kotlin.math.sin(i / 5.0) * 0.05,
            setPointTemperature = if (heating) 23.0 else 21.0,
            valvePosition = if (heating) 1.0 else if (i in 30..36) 0.4 else 0.0,
        )
    }
    val dark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface {
            RoomHistoryChart(samples, now, Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}
