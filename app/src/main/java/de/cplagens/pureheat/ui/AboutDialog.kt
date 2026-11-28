/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.R
import de.cplagens.pureheat.SupportLinks

/**
 * Info: version and short description, then one card per setting – app language, fault
 * notifications, data export/import, the optional tip – and the open-source note. Same layout as
 * the help screen (back + title, cards).
 */
@Composable
fun InfoScreen(onBack: () -> Unit, roomLabels: Map<String, String>, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.info), style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        if (version.isNotEmpty()) {
                            Text(
                                stringResource(R.string.version, version),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { InfoCard { LanguagePicker() } }
        item { InfoCard { NotificationSection() } }
        item { InfoCard { DataSection(roomLabels) } }
        // Draws its own card, and nothing when Play can't offer the tip.
        item { SupportSection() }
        item {
            InfoCard {
                Text(stringResource(R.string.help_oss_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.help_oss_text), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { uri.openUri(SupportLinks.SOURCE_CODE) }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.source_code))
                }
                Text(
                    stringResource(R.string.about_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The card of every info/help section. */
@Composable
internal fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Logo at a few sizes, plus the round launcher crop. */
@Preview(widthDp = 360)
@Composable
private fun LogoPreview() {
    Surface {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(108.dp))
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(48.dp))
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(96.dp).clip(CircleShape))
        }
    }
}
