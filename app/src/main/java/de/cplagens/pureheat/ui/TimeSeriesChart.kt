/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.data.history.HistoryAnalysis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One line (or area) in a [TimeSeriesChart]. Points must be sorted by time. */
data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Pair<Long, Double>>,
    /** Setpoints and valve positions change in steps. */
    val step: Boolean = false,
    val dashed: Boolean = false,
    /** Fill down to the baseline (used for valve opening). */
    val area: Boolean = false,
    /**
     * Break the line at recording gaps > 1 h. Off for temperature lines: Android may delay background
     * jobs for hours, and a connected line reads better (as in the overview). Valve areas keep the gaps.
     */
    val breakOnGaps: Boolean = area,
)

/**
 * Time series on a single y-axis with a shared, tappable/scrubbable time cursor. All charts on
 * a screen get the same [start]/[end] and [selected] so the cursor lines up across them.
 */
@Composable
fun TimeSeriesChart(
    series: List<ChartSeries>,
    start: Long,
    end: Long,
    yMin: Double,
    yMax: Double,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    yLabel: (Double) -> String? = { null },
    /** Highlighted time ranges drawn behind the series (end null = until [end]). */
    bands: List<Pair<Long, Long?>> = emptyList(),
    bandColor: Color = Color.Transparent,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val cursor = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val select by rememberUpdatedState(onSelect)
    val span = (end - start).coerceAtLeast(1)

    Canvas(
        modifier
            .pointerInput(start, end) {
                detectTapGestures { select(start + (it.x / size.width * span).toLong()) }
            }
            .pointerInput(start, end) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    select(start + (change.position.x.coerceIn(0f, size.width.toFloat()) / size.width * span).toLong())
                }
            },
    ) {
        fun x(t: Long) = ((t - start).toFloat() / span) * size.width
        fun y(v: Double) = (size.height * (1 - (v - yMin) / (yMax - yMin))).toFloat()

        bands.forEach { (from, to) ->
            val left = x(maxOf(from, start))
            val right = x(minOf(to ?: end, end))
            if (right > left) drawRect(bandColor, Offset(left, 0f), androidx.compose.ui.geometry.Size(right - left, size.height))
        }
        timeTicks(start, end).forEach { t ->
            drawLine(grid, Offset(x(t), 0f), Offset(x(t), size.height), strokeWidth = 1f)
        }
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        listOf(yMax, yMin).forEach { v ->
            yLabel(v)?.let { text ->
                val layout = measurer.measure(text, labelStyle)
                val top = if (v == yMax) 0f else size.height - layout.size.height - 2.dp.toPx()
                drawText(layout, topLeft = Offset(2.dp.toPx(), top))
            }
        }

        series.forEach { s ->
            splitRuns(s.points, s.breakOnGaps).forEach { run ->
                val path = Path()
                run.forEachIndexed { i, (t, v) ->
                    if (i == 0) {
                        path.moveTo(x(t), y(v))
                    } else {
                        if (s.step) path.lineTo(x(t), y(run[i - 1].second))
                        path.lineTo(x(t), y(v))
                    }
                }
                if (s.area) {
                    path.lineTo(x(run.last().first), size.height)
                    path.lineTo(x(run.first().first), size.height)
                    path.close()
                    drawPath(path, s.color)
                } else if (run.size == 1) {
                    drawCircle(s.color, 2.dp.toPx(), Offset(x(run[0].first), y(run[0].second)))
                } else {
                    drawPath(
                        path,
                        s.color,
                        style = Stroke(
                            width = (if (s.dashed) 1.5 else 2.0).dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null,
                        ),
                    )
                }
            }
        }

        selected?.takeIf { it in start..end }?.let { t ->
            drawLine(cursor, Offset(x(t), 0f), Offset(x(t), size.height), strokeWidth = 1.dp.toPx())
        }
    }
}

/** Time labels under a chart, matching the grid lines of [TimeSeriesChart]. */
@Composable
fun TimeAxis(start: Long, end: Long, modifier: Modifier = Modifier) {
    val ticks = listOf(start) + timeTicks(start, end) + end
    val pattern = if (end - start <= 26 * 3_600_000L) "HH:mm" else "EE HH'h'"
    val fmt = SimpleDateFormat(pattern, Locale.GERMANY)
    Row(modifier.fillMaxWidth()) {
        // Only start, middle and end to keep labels from colliding on a phone.
        listOf(ticks.first(), (start + end) / 2, ticks.last()).forEachIndexed { i, t ->
            if (i > 0) Spacer(Modifier.weight(1f))
            Text(fmt.format(Date(t)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Grid times: every 6 h for up to 2 days, otherwise midnight of each day. */
private fun timeTicks(start: Long, end: Long): List<Long> {
    val hour = 3_600_000L
    val step = if (end - start <= 2 * 24 * hour) 6 * hour else 24 * hour
    // Align to local time (midnight, 06:00, …); a DST switch inside the range shifts by an hour, which is fine.
    val offset = java.util.TimeZone.getDefault().getOffset(start)
    var t = ((start + offset) / step + 1) * step - offset
    val out = mutableListOf<Long>()
    while (t < end) {
        out += t
        t += step
    }
    return out
}

/** With [breakOnGaps] a gap > 1 h breaks a line. */
private fun splitRuns(points: List<Pair<Long, Double>>, breakOnGaps: Boolean): List<List<Pair<Long, Double>>> {
    val out = mutableListOf<MutableList<Pair<Long, Double>>>()
    var prev: Long? = null
    for (p in points) {
        if (prev == null || (breakOnGaps && p.first - prev > HistoryAnalysis.MAX_GAP_MILLIS)) out.add(mutableListOf())
        out.last().add(p)
        prev = p.first
    }
    return out
}
