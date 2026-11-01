package de.cplagens.pureheat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.hmip.HeatingCircuit
import de.cplagens.pureheat.hmip.Room
import java.text.DateFormat
import java.util.Date

internal val HeatingColor = Color(0xFFE65100)

@Composable
fun DashboardScreen(
    state: UiState.Dashboard,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenProfile: (roomId: String) -> Unit,
    onOpenHistory: (roomId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAbout by remember { mutableStateOf(false) }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PureHeat", style = MaterialTheme.typography.headlineSmall)
                    val open = state.state?.rooms?.count { it.isHeating }
                    val subtitle = buildString {
                        if (open != null) append(if (open == 1) "Ventil offen in 1 Raum" else "Ventil offen in $open Räumen")
                        state.lastUpdated?.let {
                            if (isNotEmpty()) append(" · ")
                            append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)))
                        }
                    }
                    if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onRefresh, enabled = !state.loading) { Text("Aktualisieren") }
                TextButton(onClick = { showAbout = true }) { Text("Info") }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        val rooms = state.state?.rooms.orEmpty()
        val unassigned = state.state?.unassignedCircuits.orEmpty()
        // The device name only helps to tell actuators apart; with a single one "Kanal 4" is enough.
        val showDevice = (rooms.flatMap { it.circuits } + unassigned).map { it.deviceId }.distinct().size > 1

        items(rooms, key = { it.id }) { room ->
            RoomCard(
                room,
                showDevice,
                state.history[room.id],
                state.lastUpdated ?: System.currentTimeMillis(),
                onOpenProfile = { onOpenProfile(room.id) },
                onOpenHistory = { onOpenHistory(room.id) },
            )
        }

        if (unassigned.isNotEmpty()) {
            item { Text("Ohne Raumzuordnung", style = MaterialTheme.typography.titleMedium) }
            items(unassigned, key = { "${it.deviceId}-${it.channelIndex}" }) { CircuitRow(it, showDevice) }
        }

        item {
            TextButton(onClick = onLogout) { Text("Abmelden") }
        }
    }
}

@Composable
private fun RoomCard(
    room: Room,
    showDevice: Boolean,
    history: RoomHistory?,
    now: Long,
    onOpenProfile: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Card(
        colors = if (room.isHeating) {
            CardDefaults.cardColors(containerColor = HeatingColor.copy(alpha = 0.12f))
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(room.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // "Ventil offen" rather than "heizt": the system only knows the valve position,
                // not whether warm water actually flows (stuck valve pin, cold supply, …).
                if (room.isHeating) Text("Ventil offen", color = HeatingColor, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                buildString {
                    append("Ist ${room.actualTemperature.fmtTemp()} · Soll ${room.setPointTemperature.fmtTemp()}")
                    room.humidity?.let { append(" · $it % rF") }
                    if (room.windowState == "OPEN") append(" · Fenster offen")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (room.circuits.isEmpty()) {
                room.valvePosition?.let { ValveBar("Ventil (Raum)", it) }
            } else {
                room.circuits.forEach { CircuitRow(it, showDevice) }
            }
            history?.stalled?.let { stalled ->
                val hours = (now - stalled.openSince) / (60 * 60 * 1000.0)
                Text(
                    "⚠ Ventil seit %.1f h offen, Temperatur nur %+.1f °C – kommt Wärme an?".format(hours, stalled.temperatureRise),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            RoomHistoryChart(history?.samples.orEmpty(), now, Modifier.fillMaxWidth().clickable(onClick = onOpenHistory))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onOpenHistory, contentPadding = PaddingValues(0.dp)) { Text("Verlauf ›") }
                if (room.profiles.isNotEmpty()) {
                    TextButton(onClick = onOpenProfile, contentPadding = PaddingValues(0.dp)) { Text("Heizprofil ›") }
                }
            }
        }
    }
}

@Composable
private fun CircuitRow(circuit: HeatingCircuit, showDevice: Boolean) {
    val name = buildString {
        append(circuit.channelLabel ?: "Kanal ${circuit.channelIndex}")
        if (showDevice) append(" · ${circuit.deviceLabel}")
    }
    val position = circuit.valvePosition
    if (position != null) {
        ValveBar(name, position)
    } else {
        Text("$name: ${circuit.valveState ?: "unbekannt"}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ValveBar(label: String, position: Double) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("${(position * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { position.toFloat().coerceIn(0f, 1f) },
            color = HeatingColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Double?.fmtTemp(): String = this?.let { "%.1f °C".format(it) } ?: "–"
