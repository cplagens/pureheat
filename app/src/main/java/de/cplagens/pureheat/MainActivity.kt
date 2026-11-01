package de.cplagens.pureheat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.cplagens.pureheat.ui.DashboardScreen
import de.cplagens.pureheat.ui.MainViewModel
import de.cplagens.pureheat.ui.SetupScreen
import de.cplagens.pureheat.ui.UiState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Poll only while visible; WorkManager records the history in the background.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.pollWhileVisible() }
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                Scaffold(Modifier.fillMaxSize()) { padding ->
                    when (val s = ui) {
                        is UiState.Setup -> SetupScreen(s, viewModel::register, Modifier.padding(padding))
                        is UiState.Dashboard -> DashboardScreen(
                            s,
                            onRefresh = viewModel::refresh,
                            onLogout = viewModel::logout,
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }
        }
    }
}
