package de.cplagens.pureheat.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Android's adaptive battery moves rarely opened apps into standby buckets that delay
 * background jobs by hours (seen on the device: no recording for 9 h). Exempting the app from
 * battery optimisation lifts that; PureHeat still only fetches every 15–60 min.
 * Shown only while the app is restricted, and can be dismissed.
 */
@Composable
fun BatteryHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var restricted by remember { mutableStateOf(isRestricted(context)) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean(KEY_DISMISSED, false)) }
    // Re-check when coming back from the system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { restricted = isRestricted(context) }

    if (!restricted || dismissed) return
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Lücken im Verlauf vermeiden", style = MaterialTheme.typography.labelLarge)
            Text(
                "Android bremst die Hintergrund-Aufzeichnung, weil die App selten geöffnet wird – " +
                    "dadurch entstehen stundenlange Lücken. Abhilfe: App-Info → Akku → „Nicht eingeschränkt“. " +
                    "PureHeat ruft trotzdem nur alle 15–60 Minuten ab.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("App-Info öffnen") }
                TextButton(onClick = {
                    prefs.edit().putBoolean(KEY_DISMISSED, true).apply()
                    dismissed = true
                }) { Text("Ausblenden") }
            }
        }
    }
}

private const val KEY_DISMISSED = "battery_hint_dismissed"

private fun isRestricted(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}
