/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.cplagens.pureheat.R
import de.cplagens.pureheat.data.FaultNotifier

/** Permission state that follows the system settings, plus a request that falls back to them. */
private class NotificationPermission(val granted: Boolean, val request: () -> Unit)

@Composable
private fun rememberNotificationPermission(): NotificationPermission {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var granted by remember { mutableStateOf(FaultNotifier.hasPermission(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = FaultNotifier.hasPermission(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    return NotificationPermission(granted) {
        // After one refusal Android shows no dialog any more; then only the system settings help.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !prefs.getBoolean(KEY_ASKED, false)) {
            prefs.edit().putBoolean(KEY_ASKED, true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Info screen: switch for fault notifications, with a way out when Android blocks them. */
@Composable
fun NotificationSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(FaultNotifier.isEnabled(context)) }
    val permission = rememberNotificationPermission()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.notify_title), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.notify_faults), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                FaultNotifier.setEnabled(context, it)
                if (it && !permission.granted) permission.request()
            })
        }
        Text(stringResource(R.string.notify_faults_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (enabled && !permission.granted) {
            Text(stringResource(R.string.notify_blocked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = permission.request) { Text(stringResource(R.string.notify_allow)) }
        }
    }
}

/** Dashboard: one-time offer to allow notifications (Android 13+ asks for them), like [BatteryHint]. */
@Composable
fun NotificationHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean(KEY_HINT_DISMISSED, false)) }
    val permission = rememberNotificationPermission()
    if (permission.granted || dismissed || !FaultNotifier.isEnabled(context)) return
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.notify_hint_title), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.notify_hint_text), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = permission.request) { Text(stringResource(R.string.notify_allow)) }
                TextButton(onClick = {
                    prefs.edit().putBoolean(KEY_HINT_DISMISSED, true).apply()
                    dismissed = true
                }) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
}

private const val KEY_ASKED = "notification_permission_asked"
private const val KEY_HINT_DISMISSED = "notification_hint_dismissed"
