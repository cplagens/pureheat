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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import de.cplagens.pureheat.ui.ProfileScreen
import de.cplagens.pureheat.ui.ProfileViewModel
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
                    var profileRoomId by rememberSaveable { mutableStateOf<String?>(null) }
                    when (val s = ui) {
                        is UiState.Setup -> SetupScreen(s, viewModel::register, Modifier.padding(padding))
                        is UiState.Dashboard -> {
                            val rooms = s.state?.rooms.orEmpty()
                            val room = rooms.firstOrNull { it.id == profileRoomId }
                            if (room != null) {
                                BackHandler { profileRoomId = null }
                                ProfileScreen(
                                    room = room,
                                    allRooms = rooms,
                                    vm = viewModel(key = "profile-${room.id}") { ProfileViewModel(application, room.id) },
                                    onBack = { profileRoomId = null },
                                    onChanged = viewModel::refresh,
                                    modifier = Modifier.padding(padding),
                                )
                            } else {
                                DashboardScreen(
                                    s,
                                    onRefresh = viewModel::refresh,
                                    onLogout = viewModel::logout,
                                    onOpenProfile = { profileRoomId = it },
                                    modifier = Modifier.padding(padding),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
