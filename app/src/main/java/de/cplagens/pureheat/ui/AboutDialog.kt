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
import androidx.compose.ui.tooling.preview.Preview
import de.cplagens.pureheat.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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

/** Version, short description and the optional, voluntary support links (as in PureRemote). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val options = SupportLinks.donationOptions
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        icon = { Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(64.dp)) },
        title = { Text("PureHeat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (version.isNotEmpty()) Text("Version $version", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Zeigt, welcher Heizkreis deiner Fußbodenheizung gerade offen ist, zeichnet den Verlauf auf " +
                        "und macht Heizprofile bequem bearbeitbar. Kostenlos, ohne Werbung und ohne Tracking. " +
                        "Inoffizielles Projekt, nicht mit eQ-3 verbunden.",
                )
                if (options.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Wenn dir die App gefällt, freue ich mich über eine freiwillige Unterstützung. Sie ist nie nötig und schaltet nichts frei.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { (name, url) ->
                            FilledTonalButton(onClick = { uri.openUri(url) }) { Text(name) }
                        }
                    }
                }
                TextButton(onClick = { uri.openUri(SupportLinks.SOURCE_CODE) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Quellcode auf GitHub")
                }
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
