/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.R
import de.cplagens.pureheat.data.history.PredictionEntity
import de.cplagens.pureheat.data.history.RoomModelEntity
import de.cplagens.pureheat.data.model.ActiveModel
import de.cplagens.pureheat.data.model.ModelCurve
import de.cplagens.pureheat.data.model.Prediction
import de.cplagens.pureheat.data.model.RoomInsight
import de.cplagens.pureheat.data.model.RoomOutlook
import de.cplagens.pureheat.data.model.SimParams
import de.cplagens.pureheat.data.model.SimPoint
import de.cplagens.pureheat.data.model.toModel
import de.cplagens.pureheat.data.probe.ModelQuality
import de.cplagens.pureheat.data.probe.ModelSource
import de.cplagens.pureheat.data.probe.Outcome
import de.cplagens.pureheat.data.probe.ProbeRun
import de.cplagens.pureheat.data.probe.ProbeScore
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.math.ceil
import kotlin.math.floor

private const val HOUR_MS = 3_600_000L

/**
 * What the room model has learned: first the answer (heat-up, cool-down, reaction time, how
 * reliable), then tomorrow as the model sees it; parameters, the other model and the grey-box
 * versions only under "Details".
 */
@Composable
internal fun ModelCard(insight: RoomInsight?, greyBoxVersions: List<RoomModelEntity>?, outlook: RoomOutlook?, probe: ProbeState) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var info by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var compare by rememberSaveable { mutableStateOf(false) }
    val greyBox = remember(greyBoxVersions) { greyBoxVersions?.firstOrNull { it.adopted }?.toModel() }
    val active = remember(insight, greyBox) { insight?.let { ActiveModel.choose(greyBox, it.model) } }
    val comparison = remember(insight, active) { insight?.model?.let(SimParams::of)?.takeIf { active?.isGreyBox == true } }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.model_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (insight != null) {
                    val score = if (active?.isGreyBox == true) probe.greyBoxScore else probe.score
                    QualityBadge(ModelQuality.of(active != null, active?.measured == true, score))
                }
                TextButton(onClick = { info = !info }) { Text(stringResource(R.string.info)) }
            }
            if (info) Text(stringResource(R.string.model_explanation), style = MaterialTheme.typography.bodySmall, color = muted)
            if (insight == null) {
                Text(stringResource(R.string.model_computing), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            if (active == null) {
                ModelTexts.summary(ctx, insight)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (greyBoxVersions?.none { it.adopted } != false) {
                    Text(stringResource(R.string.greybox_pending), style = MaterialTheme.typography.bodySmall, color = muted)
                }
            } else {
                KeyFigures(active, insight)
                if (outlook != null) {
                    TomorrowChart(outlook, showComparison = compare)
                } else {
                    TypicalDayChart(active, comparison.takeIf { compare }, insight)
                }
            }
            TextButton(onClick = { details = !details }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(if (details) R.string.details_hide else R.string.details_show))
            }
            if (details) {
                if (comparison != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().toggleable(value = compare, onValueChange = { compare = it }),
                    ) {
                        Text(stringResource(R.string.model_compare), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = compare, onCheckedChange = null)
                    }
                }
                PhysicsDetails(insight)
                greyBoxVersions?.let { GreyBoxDetails(insight, it) }
            }
        }
    }
}

@Composable
private fun QualityBadge(quality: ModelQuality) {
    val (text, color) = when (quality) {
        ModelQuality.GOOD -> R.string.model_quality_good to MaterialTheme.colorScheme.primaryContainer
        ModelQuality.FAIR -> R.string.model_quality_fair to MaterialTheme.colorScheme.secondaryContainer
        ModelQuality.LEARNING -> R.string.model_quality_learning to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(stringResource(text), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** Heat-up, cool-down and reaction time of the active model as three tiles. */
@Composable
private fun KeyFigures(active: ActiveModel, insight: RoomInsight) {
    val ctx = LocalContext.current
    val levels = insight.levels
    val outside = insight.outside
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (levels != null && outside != null) {
            Tile(tileValue(ctx, active.heatUp(levels.setback, levels.comfort, outside)),
                stringResource(R.string.model_tile_heat_up, levels.setback, levels.comfort), Modifier.weight(1f))
            Tile(tileValue(ctx, active.coolDown(levels.comfort, levels.setback, outside)),
                stringResource(R.string.model_tile_cool_down, levels.setback), Modifier.weight(1f))
        }
        // The grey box works in whole hours: a lag of 0 means within the first hour.
        val reaction = if (active.isGreyBox && active.params.deadTimeHours < 1) stringResource(R.string.model_tile_within_hour)
        else ModelTexts.duration(ctx, active.params.deadTimeHours)
        Tile(reaction, stringResource(R.string.model_tile_reaction), Modifier.weight(1f))
    }
    when {
        levels == null -> Text(stringResource(R.string.model_no_setback), style = MaterialTheme.typography.bodySmall)
        outside != null -> Text(
            stringResource(if (insight.outsideIsForecast) R.string.model_tiles_outside_forecast else R.string.model_tiles_outside, outside),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun tileValue(ctx: Context, p: Prediction): String = when (p) {
    is Prediction.Hours -> ModelTexts.duration(ctx, p.hours)
    Prediction.Unreachable -> ctx.getString(R.string.model_tile_unreachable)
    Prediction.Unknown -> "–"
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            // Keep "°C" with its number when the narrow tile wraps.
            Text(label.replace(" °C", " °C"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Tomorrow 0–24 h with the active profile and the forecast and (optionally) the other model. */
@Composable
private fun TomorrowChart(outlook: RoomOutlook, showComparison: Boolean) {
    val start = remember(outlook) { outlook.tomorrowStart() }
    val end = start + ModelCurve.HOURS * HOUR_MS
    val inDay: (Long) -> Boolean = { it in start..end }
    DayChart(
        title = stringResource(R.string.model_tomorrow_title),
        start = start,
        end = end,
        model = outlook.points.filter { inDay(it.time) },
        comparison = outlook.comparison?.takeIf { showComparison }?.filter { inDay(it.time) },
    )
}

/** Fallback without the profile: the typical day at the setpoint levels of the last week. */
@Composable
private fun TypicalDayChart(active: ActiveModel, comparison: SimParams?, insight: RoomInsight) {
    val levels = insight.levels ?: return
    val outside = insight.outside ?: return
    // Local midnight, so the grid lines fall on 6, 12 and 18 h.
    val start = remember { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val model = remember(active, levels, outside, start) { ModelCurve.typicalDay(active.params, levels, outside, start) }
    val other = remember(comparison, levels, outside, start) { comparison?.let { ModelCurve.typicalDay(it, levels, outside, start) } }
    DayChart(
        title = stringResource(R.string.model_curve_title, outside),
        start = start,
        end = start + ModelCurve.HOURS * HOUR_MS,
        model = model,
        comparison = other,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChart(
    title: String,
    start: Long,
    end: Long,
    model: List<SimPoint>,
    comparison: List<SimPoint>?,
) {
    if (model.size < 2) return
    val ctx = LocalContext.current
    val setPointColor = MaterialTheme.colorScheme.onSurfaceVariant
    val modelColor = MaterialTheme.colorScheme.primary
    val physicsColor = MaterialTheme.colorScheme.tertiary
    val setPoints = model.filter { !it.setPoint.isNaN() }.map { it.time to it.setPoint }
    val modelPoints = model.map { it.time to it.temperature }
    val otherPoints = comparison?.map { it.time to it.temperature }.orEmpty()
    val temps = modelPoints.map { it.second } + otherPoints.map { it.second } + setPoints.map { it.second }
    var selected by remember { mutableStateOf<Long?>(null) }

    Text(title, style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Legend(stringResource(R.string.legend_setpoint), setPointColor, dashed = true)
        Legend(stringResource(R.string.legend_model), modelColor, dashed = false)
        if (otherPoints.isNotEmpty()) Legend(stringResource(R.string.legend_physics), physicsColor, dashed = true)
    }
    TimeSeriesChart(
        series = listOf(
            ChartSeries("setpoint", setPointColor, setPoints, step = true, dashed = true),
            ChartSeries("physics", physicsColor, otherPoints, dashed = true),
            ChartSeries("model", modelColor, modelPoints),
        ),
        start = start,
        end = end,
        yMin = floor(temps.min()) - 0.5,
        yMax = ceil(temps.max()) + 0.5,
        selected = selected,
        onSelect = { selected = it },
        yLabel = { ctx.getString(R.string.temp_c0, it) },
        modifier = Modifier.fillMaxWidth().height(140.dp),
    )
    TimeAxis(start, end)
    selected?.let { t ->
        val values = buildList {
            setPoints.nearest(t)?.let { add(ctx.getString(R.string.model_curve_value, ctx.getString(R.string.legend_setpoint), it)) }
            modelPoints.nearest(t)?.let { add(ctx.getString(R.string.model_curve_value, ctx.getString(R.string.legend_model), it)) }
            otherPoints.nearest(t)?.let { add(ctx.getString(R.string.model_curve_value, ctx.getString(R.string.legend_physics), it)) }
        }
        Text(
            stringResource(R.string.model_curve_at_time, DateFormat.getTimeFormat(ctx).format(Date(t)), values.joinToString(" · ")),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The physics model in numbers (learned from the heating and cooling phases). */
@Composable
private fun PhysicsDetails(insight: RoomInsight) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val m = insight.model
    val unknown = stringResource(R.string.unknown)
    Text(stringResource(R.string.legend_physics), style = MaterialTheme.typography.labelMedium)
    ModelTexts.summary(ctx, insight)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text(
        stringResource(
            R.string.model_parameters,
            m.k?.let { ctx.getString(R.string.model_k, it) } ?: unknown,
            m.power?.let { ctx.getString(R.string.model_p, it) } ?: unknown,
            m.deadTimeHours?.let { ModelTexts.duration(ctx, it) } ?: unknown,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    Text(
        buildString {
            append(stringResource(R.string.model_phases, m.heatingPhases, m.coolingPhases))
            m.kSpread?.let { append(stringResource(R.string.model_spread, (it * 50).toInt())) }
            if (insight.windowEvents.isNotEmpty()) {
                append(pluralStringResource(R.plurals.model_windows_excluded, insight.windowEvents.size, insight.windowEvents.size))
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    insight.levels?.let {
        Text(stringResource(R.string.model_levels, it.setback, it.comfort), style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

/** The grey-box model in use (weekly fit over all data) and its versions with the test-week error. */
@Composable
private fun GreyBoxDetails(insight: RoomInsight, versions: List<RoomModelEntity>) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val current = versions.firstOrNull { it.adopted } ?: return
    val date: (Long) -> String = { DateFormat.getDateFormat(ctx).format(Date(it)) }
    Text(stringResource(R.string.greybox_title, date(current.fittedAt)), style = MaterialTheme.typography.labelMedium)
    ModelTexts.greyBoxSummary(ctx, current.toModel(), insight)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text(ModelTexts.greyBoxParameters(ctx, current.toModel()), style = MaterialTheme.typography.bodySmall, color = muted)
    Text(
        stringResource(
            R.string.greybox_versions_friendly,
            versions.joinToString(" · ") { v ->
                val e = v.holdoutRmse
                when {
                    e == null -> ctx.getString(R.string.greybox_version_first, date(v.fittedAt))
                    v.adopted -> ctx.getString(R.string.greybox_version, date(v.fittedAt), e)
                    else -> ctx.getString(R.string.greybox_version_rejected, date(v.fittedAt), e)
                }
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    VersionsChart(versions)
}

/** Test-week error of each grey-box version over time; only once there are two to compare. */
@Composable
private fun VersionsChart(versions: List<RoomModelEntity>) {
    val ctx = LocalContext.current
    val points = versions.mapNotNull { v -> v.holdoutRmse?.let { v.fittedAt to it } }.sortedBy { it.first }
    if (points.size < 2) return
    val pad = 12 * HOUR_MS
    val start = points.first().first - pad
    val end = points.last().first + pad
    var selected by remember { mutableStateOf<Long?>(null) }
    Text(stringResource(R.string.greybox_versions_chart), style = MaterialTheme.typography.labelMedium)
    TimeSeriesChart(
        series = listOf(ChartSeries("holdout", MaterialTheme.colorScheme.primary, points)),
        start = start,
        end = end,
        yMin = 0.0,
        yMax = points.maxOf { it.second } * 1.2,
        selected = selected,
        onSelect = { selected = it },
        yLabel = { ctx.getString(R.string.greybox_rmse_axis, it) },
        modifier = Modifier.fillMaxWidth().height(80.dp),
    )
    TimeAxis(start, end)
}

/**
 * Probe run (step 5): how well the active model's predictions matched reality – the hit rate,
 * progress towards "reliable", the deviation per heat-up and the next check. Nothing is written
 * to the heating.
 */
@Composable
internal fun ProbeCard(probe: ProbeState, greyBoxActive: Boolean) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var info by rememberSaveable { mutableStateOf(false) }
    var all by rememberSaveable { mutableStateOf(false) }
    val (predictions, score) = probe.forActive(greyBoxActive)
    val other = probe.forOther(greyBoxActive)
    val activeIsGreyBox = greyBoxActive && probe.greyBoxScore != null
    val label: (Boolean) -> String = { grey -> ctx.getString(if (grey) R.string.legend_greybox else R.string.legend_physics) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.probe_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { info = !info }) { Text(stringResource(R.string.info)) }
            }
            if (info) {
                Text(stringResource(R.string.probe_explanation), style = MaterialTheme.typography.bodySmall, color = muted)
                Text(stringResource(R.string.probe_not_ready), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            if (predictions.isEmpty()) {
                Text(stringResource(R.string.probe_none), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            ScoreHeadline(score)
            other?.second?.takeIf { it.evaluated > 0 }?.let { s ->
                Text(
                    stringResource(R.string.probe_other, label(!activeIsGreyBox), (s.hitRate * 100).toInt(), s.meanAbsErrorMinutes?.toString() ?: "–"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            if (score.evaluated > 0) {
                DeviationChart(predictions, other?.first, label(activeIsGreyBox), label(!activeIsGreyBox))
            }

            val now = System.currentTimeMillis()
            predictions.filter { it.eventTime >= now }.minByOrNull { it.eventTime }?.let { next ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.probe_next, dayTime(ctx, next.eventTime), next.targetTemperature, reachText(ctx, next.predictedReach)) +
                            prior(ctx, next),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
            val past = predictions.filter { it.eventTime < now }.asReversed()
            (if (all) past else past.take(3)).forEach { ResultRow(it) }
            if (past.size > 3) {
                TextButton(onClick = { all = !all }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (all) stringResource(R.string.probe_show_less) else stringResource(R.string.probe_show_all, past.size))
                }
            }
        }
    }
}

/** Big hit rate and the way to "reliable" (14 days, ≥ 80 % on time, ≤ 30 min off on average). */
@Composable
private fun ScoreHeadline(score: ProbeScore) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (score.evaluated == 0) {
        Text(stringResource(R.string.probe_waiting), style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.percent, (score.hitRate * 100).toInt()), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.probe_on_time), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.probe_mean_error, score.meanAbsErrorMinutes?.toString() ?: "–", score.evaluated),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
    if (score.trustworthy) {
        Text(stringResource(R.string.probe_ready), style = MaterialTheme.typography.bodySmall, color = muted)
    } else {
        LinearProgressIndicator(
            progress = { (score.days.toFloat() / ProbeScore.MIN_DAYS).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.probe_progress, minOf(score.days, ProbeScore.MIN_DAYS), ProbeScore.MIN_DAYS),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
    }
}

/**
 * Minutes between predicted and actual reach per heat-up over the probe window: up = later than
 * predicted, down = earlier; the band is "on time". Not reached (or reached although predicted
 * not to) sits as a ring on the edge; disturbed ones are left out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviationChart(active: List<PredictionEntity>, other: List<PredictionEntity>?, activeLabel: String, otherLabel: String) {
    val activeColor = MaterialTheme.colorScheme.primary
    val otherColor = MaterialTheme.colorScheme.tertiary
    val bandColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val later = stringResource(R.string.probe_chart_late)
    val earlier = stringResource(R.string.probe_chart_early)
    val now = remember { System.currentTimeMillis() }
    val start = now - ProbeState.SCORE_WINDOW_MS
    val description = stringResource(R.string.probe_chart_title)

    Text(description, style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DotLegend(activeLabel, activeColor)
        if (!other.isNullOrEmpty()) DotLegend(otherLabel, otherColor)
        AreaLegend(stringResource(R.string.probe_on_time), bandColor)
    }
    Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = description }) {
        val span = (now - start).toFloat()
        fun x(t: Long) = (t - start) / span * size.width
        fun y(minutes: Double) = (size.height / 2 - minutes.coerceIn(-MAX_MINUTES, MAX_MINUTES) / MAX_MINUTES * (size.height / 2 - 6.dp.toPx())).toFloat()
        val hit = ProbeRun.HIT_MINUTES.toDouble()
        drawRect(bandColor, Offset(0f, y(hit)), Size(size.width, y(-hit) - y(hit)))
        drawLine(grid, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.dp.toPx())
        drawText(measurer.measure(later, labelStyle), topLeft = Offset(2.dp.toPx(), 0f))
        val bottom = measurer.measure(earlier, labelStyle)
        drawText(bottom, topLeft = Offset(2.dp.toPx(), size.height - bottom.size.height))
        fun dots(list: List<PredictionEntity>, color: Color) = list.forEach { p ->
            if (p.outcome == null || p.outcome == Outcome.DISTURBED || p.eventTime < start) return@forEach
            val a = p.actualReach
            val b = p.predictedReach
            val center = Offset(x(p.eventTime), y(
                when {
                    a != null && b != null -> (a - b) / 60_000.0
                    a == null -> MAX_MINUTES // not reached: as late as it gets
                    else -> -MAX_MINUTES // reached although predicted not to
                },
            ))
            if (a != null && b != null) drawCircle(color, 3.5.dp.toPx(), center)
            else drawCircle(color, 3.5.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
        }
        other?.let { dots(it, otherColor) }
        dots(active, activeColor)
    }
    TimeAxis(start, now)
}

/** ±2 h: further off is drawn on the edge. */
private const val MAX_MINUTES = 120.0

@Composable
private fun ResultRow(p: PredictionEntity) {
    val ctx = LocalContext.current
    val delta = if (p.actualReach != null && p.predictedReach != null) {
        ctx.getString(R.string.probe_delta, (p.actualReach - p.predictedReach) / 60_000)
    } else {
        ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutcomeChip(p.outcome)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                R.string.probe_row,
                dayTime(ctx, p.eventTime),
                p.targetTemperature,
                reachText(ctx, p.predictedReach),
                if (p.outcome == null) "–" else reachText(ctx, p.actualReach),
                delta,
            ) + prior(ctx, p),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OutcomeChip(outcome: String?) {
    val scheme = MaterialTheme.colorScheme
    val (text, color) = when (outcome) {
        Outcome.HIT -> R.string.outcome_hit to scheme.primaryContainer
        Outcome.EARLY -> R.string.outcome_early to scheme.tertiaryContainer
        Outcome.LATE -> R.string.outcome_late to scheme.tertiaryContainer
        Outcome.NOT_REACHED -> R.string.outcome_not_reached to scheme.errorContainer
        Outcome.DISTURBED -> R.string.outcome_disturbed_short to scheme.surfaceVariant
        else -> R.string.outcome_pending to scheme.surfaceVariant
    }
    Surface(color = color, shape = MaterialTheme.shapes.extraSmall) {
        Text(stringResource(text), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private fun reachText(ctx: Context, t: Long?): String =
    t?.let { DateFormat.getTimeFormat(ctx).format(Date(it)) } ?: ctx.getString(R.string.probe_not_reached)

private fun prior(ctx: Context, p: PredictionEntity): String =
    if (p.modelSource == ModelSource.PRIOR) ctx.getString(R.string.probe_prior) else ""
