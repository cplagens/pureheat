package de.cplagens.pureheat

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.cplagens.pureheat.ui.DashboardScreen
import de.cplagens.pureheat.ui.HelpScreen
import de.cplagens.pureheat.ui.MainViewModel
import de.cplagens.pureheat.ui.ProfileScreen
import de.cplagens.pureheat.ui.ProfileViewModel
import de.cplagens.pureheat.ui.RoomDetailScreen
import de.cplagens.pureheat.ui.RoomDetailViewModel
import de.cplagens.pureheat.ui.RoomPropertiesScreen
import de.cplagens.pureheat.ui.RoomPropertiesViewModel
import de.cplagens.pureheat.ui.SetupScreen
import de.cplagens.pureheat.ui.UiState
import kotlinx.coroutines.launch

/** AppCompatActivity is needed for the per-app language (AppCompatDelegate.setApplicationLocales). */
class MainActivity : AppCompatActivity() {
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
                    var historyRoomId by rememberSaveable { mutableStateOf<String?>(null) }
                    var propertiesRoomId by rememberSaveable { mutableStateOf<String?>(null) }
                    var showHelp by rememberSaveable { mutableStateOf(false) }
                    val screen = Modifier.padding(padding)

                    if (showHelp) {
                        BackHandler { showHelp = false }
                        HelpScreen(onBack = { showHelp = false }, modifier = screen)
                        return@Scaffold
                    }
                    when (val s = ui) {
                        is UiState.Setup -> SetupScreen(s, viewModel::register, onOpenHelp = { showHelp = true }, modifier = screen)
                        is UiState.Dashboard -> {
                            val rooms = s.state?.rooms.orEmpty()
                            val profileRoom = rooms.firstOrNull { it.id == profileRoomId }
                            val historyRoom = rooms.firstOrNull { it.id == historyRoomId }
                            val propertiesRoom = rooms.firstOrNull { it.id == propertiesRoomId }
                            when {
                                propertiesRoom != null -> {
                                    BackHandler { propertiesRoomId = null }
                                    RoomPropertiesScreen(
                                        room = propertiesRoom,
                                        vm = viewModel(key = "properties-${propertiesRoom.id}") {
                                            RoomPropertiesViewModel(application, propertiesRoom.id)
                                        },
                                        onBack = { propertiesRoomId = null },
                                        modifier = screen,
                                    )
                                }
                                profileRoom != null -> {
                                    BackHandler { profileRoomId = null }
                                    ProfileScreen(
                                        room = profileRoom,
                                        allRooms = rooms,
                                        vm = viewModel(key = "profile-${profileRoom.id}") { ProfileViewModel(application, profileRoom.id) },
                                        onBack = { profileRoomId = null },
                                        onChanged = viewModel::refresh,
                                        modifier = screen,
                                    )
                                }
                                historyRoom != null -> {
                                    BackHandler { historyRoomId = null }
                                    RoomDetailScreen(
                                        room = historyRoom,
                                        vm = viewModel(key = "history-${historyRoom.id}") { RoomDetailViewModel(application, historyRoom.id) },
                                        onBack = { historyRoomId = null },
                                        onOpenProperties = { propertiesRoomId = historyRoom.id },
                                        modifier = screen,
                                    )
                                }
                                else -> DashboardScreen(
                                    s,
                                    onRefresh = viewModel::refresh,
                                    onLogout = viewModel::logout,
                                    onOpenProfile = { profileRoomId = it },
                                    onOpenHistory = { historyRoomId = it },
                                    onOpenHelp = { showHelp = true },
                                    modifier = screen,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
