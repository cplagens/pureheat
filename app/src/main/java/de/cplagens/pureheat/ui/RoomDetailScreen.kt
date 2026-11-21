/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.cplagens.pureheat.R
import de.cplagens.pureheat.data.history.PhaseKind
import de.cplagens.pureheat.data.history.ThermalPhase
import androidx.compose.runtime.LaunchedEffect
import de.cplagens.pureheat.data.room.RoomProperties
import de.cplagens.pureheat.data.DetailSection
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import de.cplagens.pureheat.hmip.Room
import java.util.Date
import kotlin.math.abs

/**
 * Everything recorded for one room: temperatures (actual, setpoint, outside), the valve of each
 * channel, the room model, detected open windows and heating/cooling phases. Tap or drag on a
 * chart to read exact values.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomDetailScreen(
    room: Room,
    vm: RoomDetailViewModel,
    onBack: () -> Unit,
    onOpenProperties: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val description by vm.description.collectAsStateWithLifecycle()
    val hidden by vm.hiddenSections.collectAsStateWithLifecycle()
    val shown: (DetailSection) -> Boolean = { it !in hidden }
    var customizing by rememberSaveable { mutableStateOf(false) }
    val insight by vm.insight.collectAsStateWithLifecycle()
    val greyBoxVersions by vm.greyBoxVersions.collectAsStateWithLifecycle()
    val probe by vm.probe.collectAsStateWithLifecycle()
    val outlook by vm.outlook.collectAsStateWithLifecycle()
    LaunchedEffect(room.activeProfile, room.actualTemperature) { vm.loadOutlook(room) }
    val greyBoxActive = greyBoxVersions?.any { it.adopted } == true
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val actualColor = MaterialTheme.colorScheme.primary
    val setPointColor = muted
    val outsideColor = MaterialTheme.colorScheme.tertiary
    val valveColor = HeatingColor.copy(alpha = 0.5f)
    val reachColor = MaterialTheme.colorScheme.secondary

    val actual = state.samples.mapNotNull { s -> s.actualTemperature?.let { s.timestamp to it } }
    val setPoint = state.samples.mapNotNull { s -> s.setPointTemperature?.let { s.timestamp to it } }
    val outside = state.weather.mapNotNull { w -> w.outsideTemperature?.let { w.timestamp to it } }
    val humidity = state.samples.mapNotNull { s -> s.humidity?.let { s.timestamp to it.toDouble() } }
    val humidityColor = MaterialTheme.colorScheme.secondary
    // Up to 3 days the chart runs on into the next hours with what the active model expects.
    val future = outlook?.takeIf { state.range.millis <= HistoryRange.DAYS_3.millis && state.samples.isNotEmpty() }
    val chartEnd = future?.let { maxOf(state.end, it.now + OUTLOOK_MS) } ?: state.end
    val ahead = future?.points.orEmpty().filter { it.time in state.end..chartEnd }
    val expected = ahead.map { it.time to it.temperature }
    val plannedSetPoint = ahead.filter { !it.setPoint.isNaN() }.map { it.time to it.setPoint }
    val band = future?.band.orEmpty().filter { it.first in state.end..chartEnd }
    // When the active model expects each heat-up to reach its target (past and planned).
    val reaches = probe.forActive(greyBoxActive).first
        .mapNotNull { p -> p.predictedReach?.takeIf { it in state.start..chartEnd }?.let { it to p.targetTemperature } }
        .sortedBy { it.first }
    val temps = (actual + setPoint + outside + expected + plannedSetPoint + reaches).map { it.second } + band.map { it.third }
    val yMin = (temps.minOrNull() ?: 15.0).let { kotlin.math.floor(it) - 0.5 }
    val yMax = (temps.maxOrNull() ?: 25.0).let { kotlin.math.ceil(it) + 0.5 }

    val channelName: @Composable (String?, Int) -> String = { label, index -> label ?: stringResource(R.string.channel_n, index) }
    val channels = room.circuits.map { c ->
        channelName(c.channelLabel, c.channelIndex) to state.circuits[c.deviceId to c.channelIndex].orEmpty()
            .mapNotNull { s -> s.valvePosition?.let { s.timestamp to it } }
    }.ifEmpty {
        listOf(stringResource(R.string.valve_room) to state.samples.mapNotNull { s -> s.valvePosition?.let { s.timestamp to it } })
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.back_rooms)) }
                Spacer(Modifier.width(8.dp))
                Text(room.label.trim(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { customizing = true }) { Text(stringResource(R.string.view_customize)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryRange.entries.forEach { r ->
                    FilterChip(
                        selected = state.range == r,
                        onClick = { selected = null; vm.setRange(r) },
                        label = { Text(stringResource(r.label)) },
                    )
                }
            }
        }

        if (shown(DetailSection.TEMPERATURE) || shown(DetailSection.VALVES) || shown(DetailSection.HUMIDITY)) item {
            val values = selected?.let { t ->
                buildList {
                    actual.nearest(t)?.let { add(ctx.getString(R.string.value_actual, it)) }
                    expected.nearest(t)?.let { add(ctx.getString(R.string.value_forecast, it)) }
                    (setPoint + plannedSetPoint).nearest(t)?.let { add(ctx.getString(R.string.value_setpoint, it)) }
                    outside.nearest(t)?.let { add(ctx.getString(R.string.value_outside, it)) }
                    humidity.nearest(t)?.let { add(ctx.getString(R.string.value_humidity, it.toInt())) }
                    channels.forEach { (label, pts) ->
                        pts.nearest(t)?.let { add(ctx.getString(R.string.value_valve, label, (it * 100).toInt())) }
                    }
                }
            }.orEmpty()
            Readout(selected, values, onClear = { selected = null })
        }

        if (shown(DetailSection.TEMPERATURE)) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.temperature), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Legend(stringResource(R.string.legend_actual), actualColor, dashed = false)
                        Legend(stringResource(R.string.legend_setpoint), setPointColor, dashed = true)
                        Legend(stringResource(R.string.legend_outside), outsideColor, dashed = false)
                        if (state.windows.isNotEmpty()) BandLegend(stringResource(R.string.legend_window_detected))
                        if (expected.isNotEmpty()) Legend(stringResource(R.string.legend_forecast), actualColor, dashed = true)
                        if (band.size >= 2) AreaLegend(stringResource(R.string.legend_uncertainty), actualColor.copy(alpha = 0.15f))
                        if (reaches.isNotEmpty()) DotLegend(stringResource(R.string.legend_expected_reach), reachColor)
                    }
                    if (actual.size < 2) {
                        Text(stringResource(R.string.too_little_data), style = MaterialTheme.typography.bodySmall)
                    }
                    // Time labels only where they stay readable (up to 3 days).
                    if (state.end - state.start <= HistoryRange.DAYS_3.millis) {
                        MarkerStrip(chartMarkers(ctx, state.samples, state.windows, state.start, state.end, state.bathing), state.start, chartEnd)
                    }
                    TimeSeriesChart(
                        series = listOfNotNull(
                            band.takeIf { it.size >= 2 }?.let { b ->
                                ChartSeries("band", actualColor.copy(alpha = 0.15f), b.map { it.first to it.third }, lower = b.map { it.first to it.second })
                            },
                            ChartSeries("outside", outsideColor, outside),
                            ChartSeries("setpoint", setPointColor, setPoint, step = true, dashed = true),
                            ChartSeries("planned", setPointColor, plannedSetPoint, step = true, dashed = true),
                            ChartSeries("actual", actualColor, actual),
                            ChartSeries("expected", actualColor, expected, dashed = true),
                            ChartSeries("reach", reachColor, reaches, dots = true),
                        ),
                        start = state.start,
                        end = chartEnd,
                        now = future?.let { state.end },
                        yMin = yMin,
                        yMax = yMax,
                        selected = selected,
                        onSelect = { selected = it },
                        yLabel = { ctx.getString(R.string.temp_c0, it) },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        // An open window lasts until now, not into the forecast.
                        bands = state.windows.map { it.start to (it.end ?: state.end) },
                        bandColor = WindowBandColor,
                    )
                    TimeAxis(state.start, chartEnd)
                }
            }
        }

        if (shown(DetailSection.VALVES)) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.valve_opening_title), style = MaterialTheme.typography.labelLarge)
                    channels.forEach { (label, points) ->
                        Text(label, style = MaterialTheme.typography.bodySmall)
                        TimeSeriesChart(
                            series = listOf(ChartSeries(label, valveColor, points, step = true, area = true)),
                            start = state.start,
                            end = chartEnd,
                            now = future?.let { state.end },
                            yMin = 0.0,
                            yMax = 1.0,
                            selected = selected,
                            onSelect = { selected = it },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                        )
                    }
                    TimeAxis(state.start, chartEnd)
                }
            }
        }

        // Humidity has its own chart: % and °C don't share a scale (no second y-axis).
        if (humidity.size >= 2 && shown(DetailSection.HUMIDITY)) {
            item {
                val hMid = (humidity.minOf { it.second } + humidity.maxOf { it.second }) / 2
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.humidity_title), style = MaterialTheme.typography.labelLarge)
                        TimeSeriesChart(
                            series = listOf(ChartSeries("humidity", humidityColor, humidity)),
                            start = state.start,
                            end = chartEnd,
                            now = future?.let { state.end },
                            yMin = kotlin.math.floor(minOf(humidity.minOf { it.second }, hMid - 5)),
                            yMax = kotlin.math.ceil(maxOf(humidity.maxOf { it.second }, hMid + 5)),
                            selected = selected,
                            onSelect = { selected = it },
                            yLabel = { ctx.getString(R.string.percent, it.toInt()) },
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            bands = state.windows.map { it.start to (it.end ?: state.end) },
                            bandColor = WindowBandColor,
                        )
                        TimeAxis(state.start, chartEnd)
                    }
                }
            }
        }

        if (room.actualTemperature != null) {
            if (shown(DetailSection.PROPERTIES)) item { PropertiesCard(description?.properties, onOpenProperties) }
            if (shown(DetailSection.MODEL)) item { ModelCard(insight, greyBoxVersions, outlook, probe) }
            if (shown(DetailSection.PROBE)) item { ProbeCard(probe, greyBoxActive) }
            // A room described as windowless has nothing to detect.
            if (shown(DetailSection.WINDOWS) && description?.properties?.hasWindows != false) {
                item {
                    Text(stringResource(R.string.windows_title), style = MaterialTheme.typography.titleMedium)
                    if (state.windows.isEmpty()) {
                        Text(stringResource(R.string.windows_none), style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
                items(state.windows.asReversed().take(20), key = { "window-${it.start}" }) {
                    Text(WindowTexts.describe(ctx, it), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (shown(DetailSection.BATHING) && description?.properties?.hasBathing == true) {
                item {
                    Text(stringResource(R.string.bathing_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (state.bathing.isEmpty()) R.string.bathing_none else R.string.bathing_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
                items(state.bathing.asReversed().take(20), key = { "bathing-${it.start}" }) { b ->
                    Text(
                        stringResource(
                            R.string.bathing_event,
                            dayTime(ctx, b.start),
                            DateFormat.getTimeFormat(ctx).format(Date(b.end)),
                            b.humidityBefore,
                            b.humidityPeak,
                        ) + (b.temperatureRise?.let { stringResource(R.string.bathing_temp, it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (shown(DetailSection.PHASES)) {
            item {
                Text(stringResource(R.string.phases_title), style = MaterialTheme.typography.titleMedium)
                PhaseSummary(state.phases)
            }
            items(state.phases.asReversed().take(30), key = { "${it.kind}-${it.start}" }) { PhaseRow(it) }
        }
        if (shown(DetailSection.PHASES) && state.phases.isEmpty()) {
            item { Text(stringResource(R.string.phases_none), style = MaterialTheme.typography.bodySmall, color = muted) }
        }
    }

    if (customizing) {
        SectionsDialog(hidden, vm::setSectionVisible, onDismiss = { customizing = false })
    }
}

/** How far the temperature chart looks ahead (up to the 3-day range). */
private const val OUTLOOK_MS = 12 * 3_600_000L

/** Which sections the detail view shows (same for all rooms; default: all). */
@Composable
private fun SectionsDialog(hidden: Set<DetailSection>, onChange: (DetailSection, Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.view_title)) },
        text = {
            Column {
                DetailSection.entries.forEach { s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().toggleable(value = s !in hidden, onValueChange = { onChange(s, it) }),
                    ) {
                        Checkbox(checked = s !in hidden, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(sectionLabel(s)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun sectionLabel(s: DetailSection) = when (s) {
    DetailSection.TEMPERATURE -> R.string.temperature
    DetailSection.VALVES -> R.string.section_valves
    DetailSection.HUMIDITY -> R.string.legend_humidity
    DetailSection.PROPERTIES -> R.string.props_title
    DetailSection.MODEL -> R.string.model_title
    DetailSection.PROBE -> R.string.section_probe
    DetailSection.WINDOWS -> R.string.legend_window_detected
    DetailSection.BATHING -> R.string.bathing_title
    DetailSection.PHASES -> R.string.section_phases
}

/** What the user told about the room (see [RoomPropertiesScreen]). */
@Composable
private fun PropertiesCard(properties: RoomProperties?, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.props_title), style = MaterialTheme.typography.labelLarge)
            if (properties == null || properties.isEmpty) {
                Text(stringResource(R.string.props_card_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(propertiesSummary(ctx, properties), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.props_link)) }
        }
    }
}

@Composable
private fun Readout(selected: Long?, values: List<String>, onClear: () -> Unit) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (selected == null) {
                Text(
                    stringResource(R.string.readout_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dayTime(ctx, selected, withDate = true), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClear) { Text(stringResource(R.string.close)) }
                }
                Text(
                    values.joinToString(" · ").ifEmpty { stringResource(R.string.readout_no_values) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PhaseSummary(phases: List<ThermalPhase>) {
    val heating = phases.filter { it.kind == PhaseKind.HEATING }
    val cooling = phases.filter { it.kind == PhaseKind.COOLING }
    val parts = buildList {
        if (heating.isNotEmpty()) {
            add(stringResource(R.string.phase_summary_open, heating.map { it.ratePerHour }.average(),
                pluralStringResource(R.plurals.phases, heating.size, heating.size)))
        }
        if (cooling.isNotEmpty()) {
            add(stringResource(R.string.phase_summary_closed, cooling.map { it.ratePerHour }.average(),
                pluralStringResource(R.plurals.phases, cooling.size, cooling.size)))
        }
    }
    if (parts.isNotEmpty()) {
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhaseRow(p: ThermalPhase) {
    val ctx = LocalContext.current
    val minutes = ((p.end - p.start) / 60_000).toInt()
    Column {
        Text(
            // Named after the valve, not the result: a closed valve can still see rising temperatures
            // (e.g. a towel radiator in the same room).
            stringResource(
                if (p.kind == PhaseKind.HEATING) R.string.phase_open else R.string.phase_closed,
                dayTime(ctx, p.start),
                DateFormat.getTimeFormat(ctx).format(Date(p.end)),
            ),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(R.string.phase_detail, p.startTemperature, p.endTemperature, minutes / 60, minutes % 60, p.ratePerHour) +
                (p.meanOutside?.let { stringResource(R.string.phase_outside, it) } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun Legend(label: String, color: Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 14.dp, height = 10.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.dp.toPx())) else null,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BandLegend(label: String) = AreaLegend(label, WindowBandColor)

@Composable
internal fun AreaLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 14.dp, height = 10.dp)) { drawRect(color) }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DotLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 14.dp, height = 10.dp)) { drawCircle(color, 3.5.dp.toPx()) }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Value of the sample closest to [t], if one lies within 30 min. */
internal fun List<Pair<Long, Double>>.nearest(t: Long): Double? =
    minByOrNull { abs(it.first - t) }?.takeIf { abs(it.first - t) <= 30 * 60_000L }?.second
