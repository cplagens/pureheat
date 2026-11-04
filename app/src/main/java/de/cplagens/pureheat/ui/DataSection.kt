/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.cplagens.pureheat.R

/**
 * Export of measurements + settings as ZIP – via the share sheet (Drive, mail, …) or "save as"
 * (the system file picker also offers Google Drive) – and import of such a ZIP.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataSection(roomLabels: Map<String, String>, vm: DataViewModel = viewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val chooserTitle = stringResource(R.string.data_share_chooser)

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.exportTo(it, roomLabels) }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.data_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.data_text), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !state.busy, onClick = {
                vm.exportForSharing(roomLabels) { uri ->
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    send.clipData = ClipData.newRawUri("", uri)
                    ctx.startActivity(Intent.createChooser(send, chooserTitle))
                }
            }) { Text(stringResource(R.string.data_share)) }
            OutlinedButton(enabled = !state.busy, onClick = { saveAs.launch(vm.fileName()) }) {
                Text(stringResource(R.string.data_save))
            }
            OutlinedButton(enabled = !state.busy, onClick = { pick.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) }) {
                Text(stringResource(R.string.data_import))
            }
        }
        if (state.busy) {
            Text(stringResource(R.string.data_busy), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        state.message?.let {
            Text(
                it.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.data_import_q)) },
            text = { Text(stringResource(R.string.data_import_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    vm.import(uri)
                }) { Text(stringResource(R.string.data_import)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
