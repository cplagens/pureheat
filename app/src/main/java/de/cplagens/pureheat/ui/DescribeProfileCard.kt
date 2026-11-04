/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.R

/**
 * "Describe the week in your own words" (typed or dictated) → the app fills the week view with a
 * suggestion. Nothing is saved here; the normal save dialog with the list of changes follows.
 */
@Composable
fun DescribeProfileCard(busy: Boolean, onSuggest: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
            text = if (text.isBlank()) spoken else "${text.trimEnd()} $spoken"
        }
    }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale(ctx).toLanguageTag())
    }
    val canDictate = remember { speechIntent.resolveActivity(ctx.packageManager) != null }

    if (!open) {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.profile_describe_open)) }
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.profile_describe_title), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.profile_describe_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.props_text_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canDictate) {
                    OutlinedButton(onClick = { runCatching { dictate.launch(speechIntent) } }) { Text(stringResource(R.string.props_dictate)) }
                }
                Button(onClick = { onSuggest(text) }, enabled = text.isNotBlank() && !busy) {
                    Text(stringResource(R.string.profile_describe_go))
                }
            }
        }
    }
}
