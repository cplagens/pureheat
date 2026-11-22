/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.cplagens.pureheat.R
import de.cplagens.pureheat.data.comfort.ComfortEditing
import de.cplagens.pureheat.data.comfort.ComfortPreset
import de.cplagens.pureheat.hmip.Room
import de.cplagens.pureheat.hmip.Weekday

/**
 * Step 6: when should the room be warm? Same week view and day editor as the heating profile, but
 * the bars are comfort windows. Saved on the phone only; nothing is written to the heating.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComfortScreen(room: Room, vm: ComfortViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(room.id) { vm.start(room) }
    var expandedDay by rememberSaveable { mutableStateOf<Weekday?>(null) }
    val week = state.edited

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.width(8.dp))
                Text(room.label.trim(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.comfort_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.comfort_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            state.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        }

        if (week != null) {
            item {
                val days = week.mapValues { ComfortEditing.toProfileDay(it.value) }
                val range = temperatureRange(days.values)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.comfort_week_hint), style = MaterialTheme.typography.labelLarge)
                        HourAxis(Modifier.padding(start = 32.dp))
                        Weekday.entries.forEach { day ->
                            val d = days[day]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { expandedDay = if (expandedDay == day) null else day },
                            ) {
                                Text(
                                    stringResource(day.shortRes),
                                    fontWeight = if (expandedDay == day) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.width(32.dp),
                                )
                                if (d != null) {
                                    DayBar(d, range, Modifier.weight(1f).height(28.dp))
                                } else {
                                    Text(stringResource(R.string.comfort_day_empty), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (expandedDay == day) {
                                Text(stringResource(R.string.comfort_presets), style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PresetButton(R.string.comfort_preset_workday) { vm.applyPreset(day, ComfortPreset.WORKDAY) }
                                    PresetButton(R.string.comfort_preset_home_office) { vm.applyPreset(day, ComfortPreset.HOME_OFFICE) }
                                    PresetButton(R.string.comfort_preset_weekend) { vm.applyPreset(day, ComfortPreset.WEEKEND) }
                                }
                                if (d != null) {
                                    DayEditor(
                                        day = day,
                                        value = d,
                                        range = range,
                                        room = room,
                                        heatUpRate = null,
                                        onChange = { vm.updateDay(day, ComfortEditing.fromProfileDay(it)) },
                                        onCopy = { targets -> vm.copyDay(day, targets) },
                                        comfort = true,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = vm::save, enabled = state.dirty && !state.loading) { Text(stringResource(R.string.save)) }
                    if (state.dirty && !state.original.isNullOrEmpty()) {
                        TextButton(onClick = vm::discard) { Text(stringResource(R.string.discard)) }
                    }
                }
                if (room.profiles.isNotEmpty()) {
                    TextButton(
                        onClick = { vm.takeOverProfile(room) },
                        enabled = !state.loading,
                        contentPadding = PaddingValues(0.dp),
                    ) { Text(stringResource(R.string.comfort_take_profile)) }
                }
            }
        }
    }
}

@Composable
private fun PresetButton(label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(stringResource(label)) }
}
