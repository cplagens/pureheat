/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.tooling.preview.Preview
import de.cplagens.pureheat.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.SupportLinks

/** Version, short description, app language, data export/import and the optional, voluntary tip. */
@Composable
fun AboutDialog(onDismiss: () -> Unit, roomLabels: Map<String, String> = emptyMap()) {
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        icon = { Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(64.dp)) },
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (version.isNotEmpty()) Text(stringResource(R.string.version, version), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.about_text))
                HorizontalDivider()
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
                LanguagePicker()
                HorizontalDivider()
                DataSection(roomLabels)
                SupportSection()
                TextButton(onClick = { uri.openUri(SupportLinks.SOURCE_CODE) }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.source_code))
                }
                Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
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
