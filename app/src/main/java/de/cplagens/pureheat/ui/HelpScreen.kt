/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import de.cplagens.pureheat.R
import de.cplagens.pureheat.SupportLinks

@androidx.compose.ui.tooling.preview.Preview(name = "de", locale = "de", widthDp = 360, heightDp = 900)
@androidx.compose.ui.tooling.preview.Preview(name = "en", locale = "en", widthDp = 360, heightDp = 900)
@Composable
private fun HelpScreenPreview() {
    androidx.compose.material3.Surface { HelpScreen(onBack = {}) }
}

/** Guide and explanation: what the app shows, how to read it and what it can't know. */
@Composable
fun HelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sections = listOf(
        R.string.help_start_title to R.string.help_start_text,
        R.string.help_overview_title to R.string.help_overview_text,
        R.string.help_valve_title to R.string.help_valve_text,
        R.string.help_chart_title to R.string.help_chart_text,
        R.string.help_window_title to R.string.help_window_text,
        R.string.help_model_title to R.string.help_model_text,
        R.string.help_probe_title to R.string.help_probe_text,
        R.string.help_props_title to R.string.help_props_text,
        R.string.help_bathing_title to R.string.help_bathing_text,
        R.string.help_profiles_title to R.string.help_profiles_text,
        R.string.help_battery_title to R.string.help_battery_text,
        R.string.help_language_title to R.string.help_language_text,
        R.string.help_privacy_title to R.string.help_privacy_text,
    )
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.help_title), style = MaterialTheme.typography.titleLarge)
            }
            Text(stringResource(R.string.help_intro), style = MaterialTheme.typography.bodyMedium)
        }
        items(sections) { (title, text) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // Open source: link to the repository (issues, contributions, licence).
        item {
            val uri = LocalUriHandler.current
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.help_oss_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.help_oss_text), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { uri.openUri(SupportLinks.SOURCE_CODE) }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.source_code))
                    }
                }
            }
        }
    }
}
