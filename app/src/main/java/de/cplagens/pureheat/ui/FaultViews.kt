/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.R
import de.cplagens.pureheat.hmip.Fault
import de.cplagens.pureheat.hmip.FaultKind
import de.cplagens.pureheat.hmip.FaultKind.Severity

/** String resources of one [FaultKind]: short title, what it means, likely causes, steps to fix. */
internal data class FaultText(
    @StringRes val title: Int,
    @StringRes val meaning: Int,
    @StringRes val causes: Int,
    @StringRes val steps: Int,
)

internal fun FaultKind.text(): FaultText = when (this) {
    FaultKind.VALVE_TOO_BIG -> FaultText(
        R.string.fault_too_big_title, R.string.fault_too_big_meaning, R.string.fault_too_big_causes, R.string.fault_too_big_steps,
    )
    FaultKind.VALVE_TOO_SMALL -> FaultText(
        R.string.fault_too_small_title, R.string.fault_too_small_meaning, R.string.fault_too_small_causes, R.string.fault_too_small_steps,
    )
    FaultKind.VALVE_TOO_TIGHT -> FaultText(
        R.string.fault_too_tight_title, R.string.fault_too_tight_meaning, R.string.fault_too_tight_causes, R.string.fault_too_tight_steps,
    )
    FaultKind.VALVE_POSITION -> FaultText(
        R.string.fault_position_title, R.string.fault_position_meaning, R.string.fault_position_causes, R.string.fault_position_steps,
    )
    FaultKind.VALVE_ADAPTING -> FaultText(
        R.string.fault_adapting_title, R.string.fault_adapting_meaning, R.string.fault_adapting_causes, R.string.fault_adapting_steps,
    )
    FaultKind.VALVE_UNKNOWN -> FaultText(
        R.string.fault_unknown_title, R.string.fault_unknown_meaning, R.string.fault_unknown_causes, R.string.fault_unknown_steps,
    )
    FaultKind.EMERGENCY -> FaultText(
        R.string.fault_emergency_title, R.string.fault_emergency_meaning, R.string.fault_emergency_causes, R.string.fault_emergency_steps,
    )
    FaultKind.UNREACHABLE -> FaultText(
        R.string.fault_unreach_title, R.string.fault_unreach_meaning, R.string.fault_unreach_causes, R.string.fault_unreach_steps,
    )
    FaultKind.LOW_BATTERY -> FaultText(
        R.string.fault_battery_title, R.string.fault_battery_meaning, R.string.fault_battery_causes, R.string.fault_battery_steps,
    )
    FaultKind.DEVICE -> FaultText(
        R.string.fault_device_title, R.string.fault_device_meaning, R.string.fault_device_causes, R.string.fault_device_steps,
    )
}

@Composable
internal fun Severity.color(): Color = when (this) {
    Severity.ERROR -> MaterialTheme.colorScheme.error
    Severity.WARNING -> MaterialTheme.colorScheme.tertiary
    Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** "Living room · Channel 3", "Floor heating actuator", … */
@Composable
internal fun Fault.place(): String {
    val parts = if (channelIndex != null) {
        listOf(roomLabel ?: deviceLabel, channelLabel ?: stringResource(R.string.channel_n, channelIndex))
    } else {
        listOfNotNull(deviceLabel, roomLabel)
    }
    return parts.distinct().joinToString(" · ")
}

/** Top of the dashboard: every fault at a glance, tap one for the details. */
@Composable
internal fun FaultBanner(faults: List<Fault>, onOpen: (Fault) -> Unit, modifier: Modifier = Modifier) {
    if (faults.isEmpty()) return
    val worst = faults.maxOf { it.kind.severity }
    val colors = when (worst) {
        Severity.ERROR -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        Severity.WARNING -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Severity.INFO -> CardDefaults.cardColors()
    }
    Card(colors = colors, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(if (worst == Severity.INFO) R.string.fault_banner_info else R.string.fault_banner_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            faults.forEach { fault ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(fault) }.padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(fault.place(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(fault.kind.text().title), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(R.string.fault_details), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** One line in the room card instead of / below the valve bar. */
@Composable
internal fun FaultRow(label: String, fault: Fault, onOpen: (Fault) -> Unit) {
    val severity = fault.kind.severity
    val prefix = if (severity == Severity.INFO) "" else "⚠ "
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpen(fault) }) {
        Text(
            prefix + stringResource(R.string.channel_state, label, stringResource(fault.kind.text().title)),
            color = severity.color(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (severity == Severity.ERROR) FontWeight.SemiBold else null,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.fault_details), color = severity.color(), style = MaterialTheme.typography.labelMedium)
    }
}

/** Meaning, likely causes and the steps to fix it. */
@Composable
internal fun FaultDialog(fault: Fault, onDismiss: () -> Unit) {
    val text = fault.kind.text()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(text.title), color = fault.kind.severity.color()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fault.place(), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(text.meaning), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.fault_causes_heading), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(text.causes), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.fault_steps_heading), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(text.steps), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.fault_code, fault.code), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
