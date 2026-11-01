package de.cplagens.pureheat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SetupScreen(state: UiState.Setup, onRegister: (sgtin: String, pin: String) -> Unit, modifier: Modifier = Modifier) {
    var sgtin by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }

    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Access Point verbinden", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Die SGTIN (Access-Point-ID) steht auf dem Aufkleber unter dem Access Point, " +
                "z. B. 3014-F711-A000-…",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = sgtin,
            onValueChange = { sgtin = it },
            label = { Text("SGTIN") },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("PIN (nur falls in der App gesetzt)") },
            singleLine = true,
            enabled = !state.busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onRegister(sgtin, pin) },
            enabled = !state.busy && sgtin.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Verbinden") }

        if (state.waitingForButton) {
            CircularProgressIndicator()
            Text("Jetzt den blauen Knopf am Access Point drücken …", style = MaterialTheme.typography.titleMedium)
        } else if (state.busy) {
            CircularProgressIndicator()
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
