/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.cplagens.pureheat.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.cplagens.pureheat.hmip.HeatingPeriod
import de.cplagens.pureheat.hmip.HeatingProfileCodec.formatTime
import de.cplagens.pureheat.hmip.MINUTES_PER_DAY
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.ProfileEditing
import de.cplagens.pureheat.hmip.ProfileRef
import de.cplagens.pureheat.hmip.Room
import de.cplagens.pureheat.hmip.Weekday
import kotlin.math.abs

/**
 * Week overview and editor for the heating profiles of one room. Nothing is written to the
 * cloud until the user confirms the list of changes in the save dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    room: Room,
    allRooms: List<Room>,
    vm: ProfileViewModel,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val refs = room.profiles.filter { it.visible || it.index == room.activeProfile }
    LaunchedEffect(room.id) {
        val initial = refs.firstOrNull { it.index == room.activeProfile } ?: refs.firstOrNull()
        if (state.selected == null && initial != null) vm.select(initial)
    }

    var expandedDay by rememberSaveable { mutableStateOf<Weekday?>(null) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmActivate by remember { mutableStateOf<ProfileRef?>(null) }
    var renaming by remember { mutableStateOf<ProfileRef?>(null) }
    val profile = state.edited
    val busy = state.loading || state.saving

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
            }
            Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                refs.forEach { ref ->
                    FilterChip(
                        selected = state.selected == ref,
                        onClick = { if (!state.dirty) vm.select(ref) },
                        enabled = !busy && !state.dirty,
                        label = {
                            Text(
                                if (ref.index == room.activeProfile) {
                                    stringResource(R.string.profile_active_suffix, ref.displayName.asString())
                                } else {
                                    ref.displayName.asString()
                                },
                            )
                        },
                    )
                }
            }
            if (state.dirty) {
                Text(
                    stringResource(R.string.profile_save_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.selected?.let { sel ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sel.index != room.activeProfile && !state.dirty) {
                        OutlinedButton(onClick = { confirmActivate = sel }, enabled = !busy) {
                            Text(stringResource(R.string.profile_activate, sel.displayName.asString()))
                        }
                    }
                    if (!state.dirty) {
                        TextButton(onClick = { renaming = sel }, enabled = !busy) { Text(stringResource(R.string.profile_rename)) }
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            state.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        }

        if (profile != null) {
            item {
                val range = temperatureRange(profile.days.values)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.profile_week_hint), style = MaterialTheme.typography.labelLarge)
                        HourAxis(Modifier.padding(start = 32.dp))
                        Weekday.entries.forEach { day ->
                            val d = profile.days[day]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(enabled = d != null) {
                                    expandedDay = if (expandedDay == day) null else day
                                },
                            ) {
                                Text(
                                    stringResource(day.shortRes),
                                    fontWeight = if (expandedDay == day) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.width(32.dp),
                                )
                                if (d != null) {
                                    DayBar(d, range, Modifier.weight(1f).height(28.dp))
                                } else {
                                    Text(stringResource(R.string.profile_day_unreadable), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (expandedDay == day && d != null) {
                                DayEditor(
                                    day = day,
                                    value = d,
                                    range = range,
                                    room = room,
                                    heatUpRate = state.heatUpRate,
                                    onChange = { vm.updateDay(day, it) },
                                    onCopy = { targets -> vm.copyDay(day, targets) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    (state.heatUpRate?.let { stringResource(R.string.profile_rate, it) } ?: stringResource(R.string.profile_rate_unknown)) +
                        " " + stringResource(R.string.profile_optimum_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.dirty) {
                        Button(onClick = { confirmSave = true }, enabled = !busy) { Text(stringResource(R.string.save_ellipsis)) }
                        TextButton(onClick = vm::discard, enabled = !busy) { Text(stringResource(R.string.discard)) }
                    } else if (state.hasBackup) {
                        TextButton(onClick = { vm.undo(onChanged) }, enabled = !busy) { Text(stringResource(R.string.profile_undo)) }
                    }
                }
            }
        }
    }

    if (confirmSave) {
        SaveDialog(
            changes = state.changes,
            otherRooms = allRooms.filter { it.id != room.id && it.activeProfile != null && it.profiles.isNotEmpty() },
            onConfirm = { also ->
                confirmSave = false
                vm.save(also, onChanged)
            },
            onDismiss = { confirmSave = false },
        )
    }

    confirmActivate?.let { ref ->
        AlertDialog(
            onDismissRequest = { confirmActivate = null },
            title = { Text(stringResource(R.string.profile_activate_q, ref.displayName.asString())) },
            text = { Text(stringResource(R.string.profile_activate_text, room.label.trim())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmActivate = null
                    vm.activate(ref, onChanged)
                }) { Text(stringResource(R.string.activate)) }
            },
            dismissButton = { TextButton(onClick = { confirmActivate = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    renaming?.let { ref ->
        RenameDialog(
            current = ref.displayName.asString(),
            onConfirm = { name ->
                renaming = null
                vm.rename(ref, name, onChanged)
            },
            onDismiss = { renaming = null },
        )
    }
}

/** New profile name; the dialog itself is the confirmation for the cloud write. */
@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    val clean = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_rename_q)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ProfileViewModel.MAX_NAME_LENGTH) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.profile_name)) },
                )
                Text(stringResource(R.string.profile_rename_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(clean) }, enabled = clean.isNotEmpty() && clean != current) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SaveDialog(
    changes: List<UiText>,
    otherRooms: List<Room>,
    onConfirm: (List<Room>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_changes_q)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(changes.size) { i -> Text(changes[i].asString(), style = MaterialTheme.typography.bodyMedium) }
                item {
                    Text(
                        stringResource(R.string.save_backup_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (otherRooms.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.save_also_rooms), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(otherRooms.size) { i ->
                        val r = otherRooms[i]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selected = selected.toggle(r.id) },
                        ) {
                            Checkbox(checked = r.id in selected, onCheckedChange = { selected = selected.toggle(r.id) })
                            Text(r.label.trim())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(otherRooms.filter { it.id in selected }) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayEditor(
    day: Weekday,
    value: ProfileDay,
    range: ClosedFloatingPointRange<Double>,
    room: Room,
    heatUpRate: Double?,
    onChange: (ProfileDay) -> Unit,
    onCopy: (Set<Weekday>) -> Unit,
) {
    val minT = room.minTemperature
    val maxT = room.maxTemperature
    fun temp(t: Double) = ProfileEditing.snapTemperature(t, minT, maxT)

    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.editor_drag_hint), style = MaterialTheme.typography.bodySmall)
        DraggableDayBar(value, range, onChange, Modifier.fillMaxWidth().height(44.dp))
        HourAxis()

        value.periods.forEachIndexed { i, p ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.editor_phase, i + 1, formatTime(p.start), formatTime(p.end)),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onChange(value.copy(periods = value.periods - p)) }) { Text(stringResource(R.string.remove)) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Stepper(
                        stringResource(R.string.editor_start), formatTime(p.start),
                        onMinus = { onChange(value.copy(periods = ProfileEditing.moveEdge(value.periods, i, true, p.start - ProfileEditing.STEP_MINUTES))) },
                        onPlus = { onChange(value.copy(periods = ProfileEditing.moveEdge(value.periods, i, true, p.start + ProfileEditing.STEP_MINUTES))) },
                    )
                    Stepper(
                        stringResource(R.string.editor_end), formatTime(p.end),
                        onMinus = { onChange(value.copy(periods = ProfileEditing.moveEdge(value.periods, i, false, p.end - ProfileEditing.STEP_MINUTES))) },
                        onPlus = { onChange(value.copy(periods = ProfileEditing.moveEdge(value.periods, i, false, p.end + ProfileEditing.STEP_MINUTES))) },
                    )
                    Stepper(
                        stringResource(R.string.temperature), stringResource(R.string.temp_c, p.temperature),
                        onMinus = { onChange(value.copy(periods = value.periods.replace(i, p.copy(temperature = temp(p.temperature - 0.5))))) },
                        onPlus = { onChange(value.copy(periods = value.periods.replace(i, p.copy(temperature = temp(p.temperature + 0.5))))) },
                    )
                }
            }
        }

        val added = ProfileEditing.addPeriod(value, value.periods.lastOrNull()?.temperature ?: temp(21.0))
        TextButton(onClick = { added?.let(onChange) }, enabled = added != null, contentPadding = PaddingValues(0.dp)) {
            Text(
                if (value.periods.size >= ProfileEditing.MAX_PERIODS) {
                    stringResource(R.string.editor_max_phases, ProfileEditing.MAX_PERIODS)
                } else {
                    stringResource(R.string.editor_add_phase)
                },
            )
        }

        Stepper(
            stringResource(R.string.editor_base), stringResource(R.string.temp_c, value.baseTemperature),
            onMinus = { onChange(value.copy(baseTemperature = temp(value.baseTemperature - 0.5))) },
            onPlus = { onChange(value.copy(baseTemperature = temp(value.baseTemperature + 0.5))) },
        )

        preheatHint(LocalContext.current, value, heatUpRate)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        var targets by remember(day) { mutableStateOf(emptySet<Weekday>()) }
        Text(stringResource(R.string.editor_copy_to), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Weekday.entries.filter { it != day }.forEach { d ->
                FilterChip(selected = d in targets, onClick = { targets = targets.toggle(d) }, label = { Text(stringResource(d.shortRes)) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { targets = Weekday.WORKDAYS - day }) { Text(stringResource(R.string.editor_workdays)) }
            TextButton(onClick = { targets = Weekday.WEEKEND - day }) { Text(stringResource(R.string.editor_weekend)) }
            TextButton(onClick = { targets = Weekday.entries.toSet() - day }) { Text(stringResource(R.string.editor_all_days)) }
            Button(onClick = { onCopy(targets); targets = emptySet() }, enabled = targets.isNotEmpty()) { Text(stringResource(R.string.apply)) }
        }
    }
}

/**
 * For the first heating phase of the day: when would the heating have to start, at the
 * learned rate, to reach the phase temperature from the setback temperature in time?
 */
private fun preheatHint(ctx: Context, day: ProfileDay, rate: Double?): String? {
    if (rate == null || rate <= 0) return null
    val first = day.periods.firstOrNull() ?: return null
    val delta = first.temperature - day.baseTemperature
    if (delta <= 0) return null
    val leadMinutes = (delta / rate * 60).toInt()
    return if (leadMinutes > 8 * 60) {
        ctx.getString(R.string.preheat_too_slow, rate, day.baseTemperature, first.temperature)
    } else {
        val start = ((first.start - leadMinutes) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
        ctx.getString(
            R.string.preheat_start,
            formatTime(first.start),
            first.temperature,
            rate,
            formatTime(ProfileEditing.snapMinutes(start)),
        )
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onMinus) { Text("−") }
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
            FilledTonalIconButton(onClick = onPlus) { Text("+") }
        }
    }
}

@Composable
private fun HourAxis(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        listOf("0", "6", "12", "18", "24").forEachIndexed { i, h ->
            Text(h, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (i < 4) Spacer(Modifier.weight(1f))
        }
    }
}

/** Temperature span of a profile (setback to warmest phase), so the colour steps are visible. */
private fun temperatureRange(days: Collection<ProfileDay>): ClosedFloatingPointRange<Double> {
    val temps = days.flatMap { d -> d.periods.map { it.temperature } + d.baseTemperature }
    val lo = temps.minOrNull() ?: 16.0
    val hi = temps.maxOrNull() ?: 26.0
    return lo..maxOf(hi, lo + 1.0)
}

/** Colour of a heating phase: one hue, stronger for warmer, relative to the profile's own span. */
private fun phaseColor(t: Double, range: ClosedFloatingPointRange<Double>): Color {
    val f = ((t - range.start) / (range.endInclusive - range.start)).coerceIn(0.0, 1.0)
    return HeatingColor.copy(alpha = (0.25 + 0.75 * f).toFloat())
}

@Composable
private fun DayBar(
    day: ProfileDay,
    range: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
    handles: Boolean = false,
) {
    // Setback time must stand out from the card background.
    val base = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val handleColor = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = ink)
    Canvas(modifier) {
        fun x(m: Int) = m.toFloat() / MINUTES_PER_DAY * size.width
        drawRect(base)
        listOf(6, 12, 18).forEach { h ->
            drawLine(grid, Offset(x(h * 60), 0f), Offset(x(h * 60), size.height), strokeWidth = 1.dp.toPx())
        }
        day.periods.forEach { p ->
            val left = x(p.start)
            val width = x(p.end) - left
            // 2 px surface gap so adjacent phases stay distinguishable.
            drawRect(phaseColor(p.temperature, range), Offset(left + 1f, 0f), Size((width - 2f).coerceAtLeast(1f), size.height))
            val label = measurer.measure("${p.temperature.fmt()}°", labelStyle)
            if (label.size.width + 8 < width) {
                drawText(label, topLeft = Offset(left + (width - label.size.width) / 2, (size.height - label.size.height) / 2))
            }
            if (handles) {
                listOf(left, left + width).forEach { hx ->
                    drawRect(handleColor, Offset(hx - 2.dp.toPx(), size.height * 0.2f), Size(4.dp.toPx(), size.height * 0.6f))
                }
            }
        }
    }
}

/** Day bar whose phase edges can be dragged horizontally; snaps to 15 min. */
@Composable
private fun DraggableDayBar(
    value: ProfileDay,
    range: ClosedFloatingPointRange<Double>,
    onChange: (ProfileDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    var dragging by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    DayBar(
        value,
        range,
        handles = true,
        modifier = modifier.pointerInput(Unit) {
            val touchSlop = 24.dp.toPx()
            fun minuteAt(px: Float) = (px / size.width * MINUTES_PER_DAY).toInt()
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    // Grab the nearest phase edge within reach of the finger.
                    dragging = current.periods.flatMapIndexed { i, p ->
                        listOf(Triple(i, true, p.start), Triple(i, false, p.end))
                    }.map { (i, isStart, m) -> Triple(i, isStart, abs(m.toFloat() / MINUTES_PER_DAY * size.width - offset.x)) }
                        .filter { it.third <= touchSlop }
                        .minByOrNull { it.third }
                        ?.let { it.first to it.second }
                },
                onDragEnd = { dragging = null },
                onDragCancel = { dragging = null },
                onHorizontalDrag = { pointer, _ ->
                    val (i, isStart) = dragging ?: return@detectHorizontalDragGestures
                    pointer.consume()
                    val periods = ProfileEditing.moveEdge(current.periods, i, isStart, minuteAt(pointer.position.x))
                    if (periods != current.periods) change(current.copy(periods = periods))
                },
            )
        },
    )
}

private fun List<HeatingPeriod>.replace(i: Int, p: HeatingPeriod) = toMutableList().also { it[i] = p }
private fun <T> Set<T>.toggle(item: T) = if (item in this) this - item else this + item
private fun Double.fmt() = "%.1f".format(this)
