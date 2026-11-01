package de.cplagens.pureheat.ui

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.cplagens.pureheat.data.history.PhaseKind
import de.cplagens.pureheat.data.history.ThermalPhase
import de.cplagens.pureheat.hmip.Room
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Everything recorded for one room: temperatures (actual, setpoint, outside), the valve of each
 * channel, and the detected heating/cooling phases. Tap or drag on a chart to read exact values.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomDetailScreen(
    room: Room,
    vm: RoomDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    val actualColor = MaterialTheme.colorScheme.primary
    val setPointColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outsideColor = MaterialTheme.colorScheme.tertiary
    val valveColor = HeatingColor.copy(alpha = 0.5f)

    val actual = state.samples.mapNotNull { s -> s.actualTemperature?.let { s.timestamp to it } }
    val setPoint = state.samples.mapNotNull { s -> s.setPointTemperature?.let { s.timestamp to it } }
    val outside = state.weather.mapNotNull { w -> w.outsideTemperature?.let { w.timestamp to it } }
    val temps = (actual + setPoint + outside).map { it.second }
    val yMin = (temps.minOrNull() ?: 15.0).let { kotlin.math.floor(it) - 0.5 }
    val yMax = (temps.maxOrNull() ?: 25.0).let { kotlin.math.ceil(it) + 0.5 }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ Räume") }
                Spacer(Modifier.width(8.dp))
                Text(room.label.trim(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryRange.entries.forEach { r ->
                    FilterChip(
                        selected = state.range == r,
                        onClick = { selected = null; vm.setRange(r) },
                        label = { Text(r.label) },
                    )
                }
            }
        }

        item {
            Readout(
                selected = selected,
                values = selected?.let { t ->
                    buildList {
                        actual.nearest(t)?.let { add("Ist ${it.fmt1()} °C") }
                        setPoint.nearest(t)?.let { add("Soll ${it.fmt1()} °C") }
                        outside.nearest(t)?.let { add("Außen ${it.fmt1()} °C") }
                        room.circuits.forEach { c ->
                            val pts = state.circuits[c.deviceId to c.channelIndex].orEmpty()
                                .mapNotNull { s -> s.valvePosition?.let { s.timestamp to it } }
                            pts.nearest(t)?.let { add("${c.channelLabel ?: "Kanal ${c.channelIndex}"} ${(it * 100).toInt()} %") }
                        }
                        if (room.circuits.isEmpty()) {
                            state.samples.mapNotNull { s -> s.valvePosition?.let { s.timestamp to it } }
                                .nearest(t)?.let { add("Ventil ${(it * 100).toInt()} %") }
                        }
                    }
                }.orEmpty(),
                onClear = { selected = null },
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Temperatur", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Legend("Ist", actualColor, dashed = false)
                        Legend("Soll", setPointColor, dashed = true)
                        Legend("Außen", outsideColor, dashed = false)
                    }
                    if (actual.size < 2) {
                        Text("Noch zu wenig Daten in diesem Zeitraum.", style = MaterialTheme.typography.bodySmall)
                    }
                    TimeSeriesChart(
                        series = listOf(
                            ChartSeries("Außen", outsideColor, outside),
                            ChartSeries("Soll", setPointColor, setPoint, step = true, dashed = true),
                            ChartSeries("Ist", actualColor, actual),
                        ),
                        start = state.start,
                        end = state.end,
                        yMin = yMin,
                        yMax = yMax,
                        selected = selected,
                        onSelect = { selected = it },
                        yLabel = { "${it.fmt0()} °C" },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    TimeAxis(state.start, state.end)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ventilöffnung (0–100 %)", style = MaterialTheme.typography.labelLarge)
                    val channels = room.circuits.map { c ->
                        (c.channelLabel ?: "Kanal ${c.channelIndex}") to state.circuits[c.deviceId to c.channelIndex].orEmpty()
                            .mapNotNull { s -> s.valvePosition?.let { s.timestamp to it } }
                    }.ifEmpty {
                        listOf("Ventil (Raum)" to state.samples.mapNotNull { s -> s.valvePosition?.let { s.timestamp to it } })
                    }
                    channels.forEach { (label, points) ->
                        Text(label, style = MaterialTheme.typography.bodySmall)
                        TimeSeriesChart(
                            series = listOf(ChartSeries(label, valveColor, points, step = true, area = true)),
                            start = state.start,
                            end = state.end,
                            yMin = 0.0,
                            yMax = 1.0,
                            selected = selected,
                            onSelect = { selected = it },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                        )
                    }
                    TimeAxis(state.start, state.end)
                }
            }
        }

        item {
            Text("Phasen (Ventil ≥ 1 h offen bzw. zu)", style = MaterialTheme.typography.titleMedium)
            PhaseSummary(state.phases)
        }
        items(state.phases.asReversed().take(30), key = { "${it.kind}-${it.start}" }) { PhaseRow(it) }
        if (state.phases.isEmpty()) {
            item {
                Text(
                    "Noch keine Phase von mindestens einer Stunde erkannt. Phasen entstehen, wenn das Ventil " +
                        "länger offen (Aufheizen) oder zu (Abkühlen) ist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Readout(selected: Long?, values: List<String>, onClear: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (selected == null) {
                Text(
                    "Diagramm antippen oder darüber wischen, um die Werte zu einem Zeitpunkt zu sehen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY).format(Date(selected)),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClear) { Text("Schließen") }
                }
                Text(values.joinToString(" · ").ifEmpty { "Keine Werte zu diesem Zeitpunkt." }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PhaseSummary(phases: List<ThermalPhase>) {
    val heating = phases.filter { it.kind == PhaseKind.HEATING }
    val cooling = phases.filter { it.kind == PhaseKind.COOLING }
    val text = buildString {
        if (heating.isNotEmpty()) {
            append("Ventil offen: im Schnitt ${heating.map { it.ratePerHour }.average().fmtSigned2()} °C/h (${heating.size.phases()})")
        }
        if (cooling.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append("Ventil zu: im Schnitt ${cooling.map { it.ratePerHour }.average().fmtSigned2()} °C/h (${cooling.size.phases()})")
        }
    }
    if (text.isNotEmpty()) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhaseRow(p: ThermalPhase) {
    val fmt = SimpleDateFormat("EE HH:mm", Locale.GERMANY)
    val minutes = ((p.end - p.start) / 60_000).toInt()
    Column {
        Text(
            // Named after the valve, not the result: a closed valve can still see rising temperatures
            // (e.g. a towel radiator in the same room).
            "${if (p.kind == PhaseKind.HEATING) "▲ Ventil offen" else "▼ Ventil zu"} · ${fmt.format(Date(p.start))}–" +
                SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(p.end)),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            "${p.startTemperature.fmt1()} → ${p.endTemperature.fmt1()} °C in ${minutes / 60} h ${minutes % 60} min " +
                "(${p.ratePerHour.fmtSigned2()} °C/h)" + (p.meanOutside?.let { " · außen Ø ${it.fmt1()} °C" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Legend(label: String, color: Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 14.dp, height = 10.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Value of the sample closest to [t], if one lies within 30 min. */
private fun List<Pair<Long, Double>>.nearest(t: Long): Double? =
    minByOrNull { abs(it.first - t) }?.takeIf { abs(it.first - t) <= 30 * 60_000L }?.second

private fun Int.phases() = if (this == 1) "1 Phase" else "$this Phasen"
private fun Double.fmt0() = String.format(Locale.GERMANY, "%.0f", this)
private fun Double.fmt1() = String.format(Locale.GERMANY, "%.1f", this)
private fun Double.fmtSigned2() = String.format(Locale.GERMANY, "%+.2f", this)
