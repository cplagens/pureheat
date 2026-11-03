/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.cplagens.pureheat.data.RoomOrder
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.R
import de.cplagens.pureheat.data.model.RoomInsight
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
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
    roomOrder: List<String> = emptyList(),
    onRoomOrderChange: (List<String>) -> Unit = {},
) {
    var showAbout by remember { mutableStateOf(false) }
    var showOrder by remember { mutableStateOf(false) }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
    val rooms = RoomOrder.arrange(state.state?.rooms.orEmpty(), { it.id }, { it.label }, roomOrder)
    if (showOrder) {
        RoomOrderDialog(rooms, onChange = onRoomOrderChange, onReset = { onRoomOrderChange(emptyList()) }, onDismiss = { showOrder = false })
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    val open = state.state?.rooms?.count { it.isHeating }
                    val subtitle = listOfNotNull(
                        open?.let { pluralStringResource(R.plurals.rooms_valve_open, it, it) },
                        state.lastUpdated?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) },
                    ).joinToString(" · ")
                    if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    // Outside now and the forecast span of the next 24 h (Open-Meteo, rounded location).
                    val outsideLine = listOfNotNull(
                        state.state?.outsideTemperature?.let { stringResource(R.string.outside_now, it) },
                        state.forecastRange?.let { stringResource(R.string.forecast_24h, it.start, it.endInclusive) },
                    ).joinToString(" · ")
                    if (outsideLine.isNotEmpty()) {
                        Text(outsideLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onRefresh, enabled = !state.loading) { Text(stringResource(R.string.refresh)) }
            }
            Row {
                TextButton(onClick = onOpenHelp, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.help)) }
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = { showAbout = true }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.info)) }
                Spacer(Modifier.width(16.dp))
                TextButton(onClick = { showOrder = true }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.room_order)) }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            BatteryHint(Modifier.padding(top = 8.dp))
        }

        val unassigned = state.state?.unassignedCircuits.orEmpty()
        // The device name only helps to tell actuators apart; with a single one "Channel 4" is enough.
        val showDevice = (rooms.flatMap { it.circuits } + unassigned).map { it.deviceId }.distinct().size > 1

        items(rooms, key = { it.id }) { room ->
            RoomCard(
                room,
                showDevice,
                state.history[room.id],
                state.insights[room.id],
                state.lastUpdated ?: System.currentTimeMillis(),
                onOpenProfile = { onOpenProfile(room.id) },
                onOpenHistory = { onOpenHistory(room.id) },
            )
        }

        if (unassigned.isNotEmpty()) {
            item { Text(stringResource(R.string.unassigned_circuits), style = MaterialTheme.typography.titleMedium) }
            items(unassigned, key = { "${it.deviceId}-${it.channelIndex}" }) { CircuitRow(it, showDevice) }
        }

        item {
            TextButton(onClick = onLogout) { Text(stringResource(R.string.logout)) }
        }
    }
}

/** Move rooms up/down; every move is saved right away. */
@Composable
private fun RoomOrderDialog(rooms: List<Room>, onChange: (List<String>) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.room_order_title)) },
        text = {
            LazyColumn {
                itemsIndexed(rooms, key = { _, r -> r.id }) { i, room ->
                    fun move(to: Int) = onChange(rooms.map { it.id }.toMutableList().apply { add(to, removeAt(i)) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(room.label.trim(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        val up = stringResource(R.string.move_up)
                        val down = stringResource(R.string.move_down)
                        TextButton(
                            onClick = { move(i - 1) },
                            enabled = i > 0,
                            modifier = Modifier.semantics { contentDescription = up },
                        ) { Text("↑") }
                        TextButton(
                            onClick = { move(i + 1) },
                            enabled = i < rooms.lastIndex,
                            modifier = Modifier.semantics { contentDescription = down },
                        ) { Text("↓") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = onReset) { Text(stringResource(R.string.room_order_reset)) } },
    )
}

@Composable
private fun RoomCard(
    room: Room,
    showDevice: Boolean,
    history: RoomHistory?,
    insight: RoomInsight?,
    now: Long,
    onOpenProfile: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
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
                // "Valve open" rather than "heating": the system only knows the valve position,
                // not whether warm water actually flows (stuck valve pin, cold supply, …).
                if (room.isHeating) {
                    Text(stringResource(R.string.valve_open), color = HeatingColor, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                buildString {
                    append(stringResource(R.string.room_values, room.actualTemperature.fmtTemp(), room.setPointTemperature.fmtTemp()))
                    room.humidity?.let { append(stringResource(R.string.room_humidity, it)) }
                    if (room.windowState == "OPEN") append(stringResource(R.string.room_window_contact_open))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (room.circuits.isEmpty()) {
                room.valvePosition?.let { ValveBar(stringResource(R.string.valve_room), it) }
            } else {
                room.circuits.forEach { CircuitRow(it, showDevice) }
            }
            history?.stalled?.let { stalled ->
                val hours = (now - stalled.openSince) / (60 * 60 * 1000.0)
                Text(
                    stringResource(R.string.stalled_warning, hours, stalled.temperatureRise),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            RoomHistoryChart(
                history?.samples.orEmpty(),
                now,
                Modifier.fillMaxWidth().clickable(onClick = onOpenHistory),
                windows = history?.windows.orEmpty(),
                bathing = history?.bathing.orEmpty(),
            )
            WindowTexts.dashboardLine(ctx, history?.windows.orEmpty())?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            // Rooms without a thermometer (towel radiators) have nothing to learn from.
            if (room.actualTemperature != null) {
                ModelTexts.summary(ctx, insight)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onOpenHistory, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.history_link)) }
                if (room.profiles.isNotEmpty()) {
                    TextButton(onClick = onOpenProfile, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.profile_link))
                    }
                }
            }
        }
    }
}

@Composable
private fun CircuitRow(circuit: HeatingCircuit, showDevice: Boolean) {
    val name = buildString {
        append(circuit.channelLabel ?: stringResource(R.string.channel_n, circuit.channelIndex))
        if (showDevice) append(" · ${circuit.deviceLabel}")
    }
    val position = circuit.valvePosition
    if (position != null) {
        ValveBar(name, position)
    } else {
        Text(
            stringResource(R.string.channel_state, name, circuit.valveState ?: stringResource(R.string.unknown)),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ValveBar(label: String, position: Double) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.percent, (position * 100).toInt()), style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { position.toFloat().coerceIn(0f, 1f) },
            color = HeatingColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Double?.fmtTemp(): String = this?.let { stringResource(R.string.temp_c, it) } ?: "–"
