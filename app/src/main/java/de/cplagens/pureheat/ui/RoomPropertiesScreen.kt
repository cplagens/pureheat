/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.cplagens.pureheat.R
import de.cplagens.pureheat.data.probe.ProbeRun
import de.cplagens.pureheat.data.room.Direction
import de.cplagens.pureheat.data.room.ExtractionSource
import de.cplagens.pureheat.data.room.FloorCovering
import de.cplagens.pureheat.data.room.Glazing
import de.cplagens.pureheat.data.room.HeatSource
import de.cplagens.pureheat.data.room.Position
import de.cplagens.pureheat.data.room.RoomPriors
import de.cplagens.pureheat.data.room.RoomProperties
import de.cplagens.pureheat.data.room.Usage
import de.cplagens.pureheat.hmip.Room

/**
 * The user describes a room in their own words (typed or dictated); the app recognises the
 * properties that matter for heating, shows them for checking/correcting and stores them locally.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomPropertiesScreen(
    room: Room,
    vm: RoomPropertiesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val p = state.properties

    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::append)
    }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale(ctx).toLanguageTag())
    }
    val canDictate = remember { speechIntent.resolveActivity(ctx.packageManager) != null }

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
            Text(stringResource(R.string.props_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.props_intro), style = MaterialTheme.typography.bodySmall, color = muted)
        }

        item {
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::setText,
                label = { Text(stringResource(R.string.props_text_label)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (canDictate) {
                    OutlinedButton(onClick = { runCatching { dictate.launch(speechIntent) } }) { Text(stringResource(R.string.props_dictate)) }
                }
                Button(onClick = vm::analyze, enabled = state.text.isNotBlank() && !state.analyzing) {
                    Text(stringResource(if (state.analyzing) R.string.props_analyzing else R.string.props_analyze))
                }
            }
            state.source?.let { source ->
                Text(
                    stringResource(
                        when (source) {
                            ExtractionSource.AI -> R.string.props_source_ai
                            ExtractionSource.KEYWORDS -> R.string.props_source_keywords
                            ExtractionSource.KEYWORDS_AI_DOWNLOADING -> R.string.props_source_downloading
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (state.nothingFound) {
                Text(stringResource(R.string.props_nothing), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }

        item {
            Text(stringResource(R.string.props_ideas), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stringArrayResource(R.array.props_examples).forEach { example ->
                    SuggestionChip(onClick = { vm.append(example) }, label = { Text(example) })
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(R.string.prop_area, p.areaM2, Modifier.weight(1f)) { vm.setProperties(p.copy(areaM2 = it)) }
                        NumberField(R.string.prop_height, p.ceilingHeightM, Modifier.weight(1f)) { vm.setProperties(p.copy(ceilingHeightM = it)) }
                    }
                    NumberField(R.string.prop_windows, p.windowCount?.toDouble(), Modifier.fillMaxWidth(), integer = true) {
                        vm.setProperties(p.copy(windowCount = it?.toInt()))
                    }
                    MultiChoice(R.string.prop_directions, Direction.entries, p.windowDirections, { directionLabel(it) }) {
                        vm.setProperties(p.copy(windowDirections = it))
                    }
                    SingleChoice(R.string.prop_glazing, Glazing.entries, p.glazing, { glazingLabel(it) }) { vm.setProperties(p.copy(glazing = it)) }
                    SingleChoice(R.string.prop_walls, (0..4).toList(), p.exteriorWalls, { it.toString() }) { vm.setProperties(p.copy(exteriorWalls = it)) }
                    SingleChoice(R.string.prop_position, Position.entries, p.position, { positionLabel(it) }) { vm.setProperties(p.copy(position = it)) }
                    YesNo(R.string.prop_above_unheated, p.aboveUnheated) { vm.setProperties(p.copy(aboveUnheated = it)) }
                    SingleChoice(R.string.prop_floor, FloorCovering.entries, p.floorCovering, { floorLabel(it) }) { vm.setProperties(p.copy(floorCovering = it)) }
                    YesNo(R.string.prop_shutters, p.shutters) { vm.setProperties(p.copy(shutters = it)) }
                    MultiChoice(R.string.prop_heat_sources, HeatSource.entries, p.heatSources, { heatSourceLabel(it) }) {
                        vm.setProperties(p.copy(heatSources = it))
                    }
                    SingleChoice(R.string.prop_usage, Usage.entries, p.usage, { usageLabel(it) }) { vm.setProperties(p.copy(usage = it)) }
                    YesNo(R.string.prop_night_vent, p.ventilatesAtNight) { vm.setProperties(p.copy(ventilatesAtNight = it)) }
                    YesNo(R.string.prop_shower, p.shower) { vm.setProperties(p.copy(shower = it)) }
                    YesNo(R.string.prop_bathtub, p.bathtub) { vm.setProperties(p.copy(bathtub = it)) }
                }
            }
        }

        item {
            val prior = RoomPriors.model(ProbeRun.PRIOR, p)
            Text(
                stringResource(
                    R.string.props_effect,
                    ctx.getString(R.string.model_k, prior.k ?: 0.0),
                    ctx.getString(R.string.model_p, prior.power ?: 0.0),
                    ModelTexts.duration(ctx, prior.deadTimeHours ?: 0.0),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = vm::save, enabled = state.dirty) { Text(stringResource(R.string.save)) }
                if (state.saved) {
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.props_saved), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Short summary for the room detail card, e.g. "20 m² · 2.5 m · Windows to: S, W · Tiles/stone". */
fun propertiesSummary(ctx: Context, p: RoomProperties): String = buildList {
    p.areaM2?.let { add(ctx.getString(R.string.props_value, ctx.getString(R.string.prop_area), formatNumber(it))) }
    p.ceilingHeightM?.let { add(ctx.getString(R.string.props_value, ctx.getString(R.string.prop_height), formatNumber(it))) }
    p.windowCount?.let { add(ctx.getString(R.string.props_value, ctx.getString(R.string.prop_windows), it.toString())) }
    if (p.windowDirections.isNotEmpty()) {
        add(ctx.getString(R.string.props_value, ctx.getString(R.string.prop_directions), p.windowDirections.joinToString(", ") { ctx.directionLabel(it) }))
    }
    p.glazing?.let { add(ctx.getString(R.string.props_value, ctx.getString(R.string.prop_glazing), ctx.glazingLabel(it))) }
    p.exteriorWalls?.let { add(ctx.getString(R.string.props_value, ctx.getString(R.string.prop_walls), it.toString())) }
    p.position?.let { add(ctx.positionLabel(it)) }
    if (p.aboveUnheated == true) add(ctx.getString(R.string.prop_above_unheated))
    p.floorCovering?.let { add(ctx.floorLabel(it)) }
    if (p.shutters == true) add(ctx.getString(R.string.prop_shutters))
    p.heatSources.forEach { add(ctx.heatSourceLabel(it)) }
    p.usage?.let { add(ctx.usageLabel(it)) }
    if (p.ventilatesAtNight == true) add(ctx.getString(R.string.prop_night_vent))
    if (p.shower == true) add(ctx.getString(R.string.prop_shower))
    if (p.bathtub == true) add(ctx.getString(R.string.prop_bathtub))
}.joinToString(" · ")

private fun formatNumber(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else java.text.NumberFormat.getInstance().format(v)

private fun Context.directionLabel(d: Direction) = getString(
    when (d) {
        Direction.N -> R.string.dir_n
        Direction.NE -> R.string.dir_ne
        Direction.E -> R.string.dir_e
        Direction.SE -> R.string.dir_se
        Direction.S -> R.string.dir_s
        Direction.SW -> R.string.dir_sw
        Direction.W -> R.string.dir_w
        Direction.NW -> R.string.dir_nw
    },
)

private fun Context.glazingLabel(g: Glazing) = getString(
    when (g) {
        Glazing.SINGLE -> R.string.glazing_single
        Glazing.DOUBLE -> R.string.glazing_double
        Glazing.TRIPLE -> R.string.glazing_triple
    },
)

private fun Context.positionLabel(p: Position) = getString(
    when (p) {
        Position.BASEMENT -> R.string.pos_basement
        Position.GROUND -> R.string.pos_ground
        Position.UPPER -> R.string.pos_upper
        Position.ATTIC -> R.string.pos_attic
    },
)

private fun Context.floorLabel(f: FloorCovering) = getString(
    when (f) {
        FloorCovering.TILE -> R.string.floor_tile
        FloorCovering.WOOD -> R.string.floor_wood
        FloorCovering.LAMINATE -> R.string.floor_laminate
        FloorCovering.CARPET -> R.string.floor_carpet
    },
)

private fun Context.heatSourceLabel(h: HeatSource) = getString(
    when (h) {
        HeatSource.TOWEL_RADIATOR -> R.string.heat_towel
        HeatSource.STOVE -> R.string.heat_stove
        HeatSource.ELECTRONICS -> R.string.heat_electronics
        HeatSource.COOKING -> R.string.heat_cooking
    },
)

private fun Context.usageLabel(u: Usage) = getString(
    when (u) {
        Usage.LIVING -> R.string.usage_living
        Usage.BEDROOM -> R.string.usage_bedroom
        Usage.CHILD -> R.string.usage_child
        Usage.KITCHEN -> R.string.usage_kitchen
        Usage.BATH -> R.string.usage_bath
        Usage.OFFICE -> R.string.usage_office
        Usage.HALL -> R.string.usage_hall
    },
)

@Composable private fun directionLabel(d: Direction) = LocalContext.current.directionLabel(d)
@Composable private fun glazingLabel(g: Glazing) = LocalContext.current.glazingLabel(g)
@Composable private fun positionLabel(p: Position) = LocalContext.current.positionLabel(p)
@Composable private fun floorLabel(f: FloorCovering) = LocalContext.current.floorLabel(f)
@Composable private fun heatSourceLabel(h: HeatSource) = LocalContext.current.heatSourceLabel(h)
@Composable private fun usageLabel(u: Usage) = LocalContext.current.usageLabel(u)

@Composable
private fun NumberField(
    @StringRes label: Int,
    value: Double?,
    modifier: Modifier,
    integer: Boolean = false,
    onChange: (Double?) -> Unit,
) {
    // Own text state so "2," can be typed on the way to "2,5"; follows the value when it changes
    // from outside (analysis, clearing).
    var text by remember { mutableStateOf(value?.let(::formatNumber).orEmpty()) }
    if (text.trim().replace(',', '.').toDoubleOrNull() != value && !(text.isBlank() && value == null)) {
        text = value?.let(::formatNumber).orEmpty()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            text = s
            val parsed = s.trim().replace(',', '.').toDoubleOrNull()
            if (s.isBlank() || parsed != null) onChange(parsed?.let { if (integer) kotlin.math.floor(it) else it })
        },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Chips; tapping the selected one clears the value ("not known"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> SingleChoice(@StringRes label: Int, options: List<T>, selected: T?, name: @Composable (T) -> String, onSelect: (T?) -> Unit) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onSelect(if (o == selected) null else o) }, label = { Text(name(o)) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MultiChoice(@StringRes label: Int, options: List<T>, selected: List<T>, name: @Composable (T) -> String, onChange: (List<T>) -> Unit) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                FilterChip(
                    selected = o in selected,
                    onClick = { onChange(if (o in selected) selected - o else selected + o) },
                    label = { Text(name(o)) },
                )
            }
        }
    }
}

@Composable
private fun YesNo(@StringRes label: Int, value: Boolean?, onChange: (Boolean?) -> Unit) {
    SingleChoice(label, listOf(true, false), value, { stringResource(if (it) R.string.yes else R.string.no) }, onChange)
}
