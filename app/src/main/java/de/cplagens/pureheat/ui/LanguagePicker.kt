/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.Lang
import de.cplagens.pureheat.R

/**
 * "Language: <current>" header that expands to a radio list of languages ("system language"
 * first). A tap switches the app language at once.
 */
@Composable
fun LanguagePicker(modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(Lang.current()) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val entries = listOf("" to stringResource(R.string.system_language)) + Lang.options
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                (entries.firstOrNull { it.first == selected }?.second.orEmpty()) + if (expanded) " ▾" else " ▸",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (expanded) entries.forEach { (tag, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = tag == selected, role = Role.RadioButton) {
                        selected = tag
                        Lang.set(tag)
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = tag == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
