package de.cplagens.pureheat.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.cplagens.pureheat.BuildConfig
import de.cplagens.pureheat.data.CredentialStore
import de.cplagens.pureheat.data.Credentials
import de.cplagens.pureheat.hmip.HeatingState
import de.cplagens.pureheat.hmip.HeatingStateParser
import de.cplagens.pureheat.hmip.HmipClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState {
    data class Setup(val waitingForButton: Boolean = false, val busy: Boolean = false, val error: String? = null) : UiState
    data class Dashboard(
        val state: HeatingState? = null,
        val loading: Boolean = false,
        val error: String? = null,
        val lastUpdated: Long? = null,
    ) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CredentialStore(app)
    private val client = HmipClient()
    private var restUrl: String? = null
    private var pollJob: Job? = null

    private val _ui = MutableStateFlow<UiState>(
        if (store.load() != null) UiState.Dashboard() else UiState.Setup()
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        if (store.load() != null) startPolling()
    }

    fun register(sgtinInput: String, pin: String) {
        val sgtin = client.normalizeSgtin(sgtinInput)
        _ui.value = UiState.Setup(busy = true)
        viewModelScope.launch {
            try {
                val token = client.register(
                    sgtin = sgtin,
                    pin = pin.ifBlank { null },
                    onWaitingForButton = { _ui.value = UiState.Setup(waitingForButton = true, busy = true) },
                )
                store.save(Credentials(sgtin, token))
                _ui.value = UiState.Dashboard()
                startPolling()
            } catch (e: Exception) {
                _ui.value = UiState.Setup(error = e.message ?: e.toString())
            }
        }
    }

    fun refresh() {
        val creds = store.load() ?: return
        viewModelScope.launch { load(creds) }
    }

    fun logout() {
        pollJob?.cancel()
        store.clear()
        restUrl = null
        _ui.value = UiState.Setup()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                store.load()?.let { load(it) }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun load(creds: Credentials) {
        _ui.update { (it as? UiState.Dashboard)?.copy(loading = true, error = null) ?: it }
        try {
            if (restUrl == null) restUrl = client.lookup(creds.sgtin).rest
            val raw = client.getCurrentState(creds.sgtin, creds.authToken, restUrl)
            if (BuildConfig.DEBUG) {
                // Raw state for checking field names on real hardware: adb logcat -s PureHeat
                Log.d(TAG, raw.toString())
            }
            val parsed = HeatingStateParser.parse(raw)
            _ui.value = UiState.Dashboard(state = parsed, lastUpdated = System.currentTimeMillis())
        } catch (e: Exception) {
            _ui.update { (it as? UiState.Dashboard)?.copy(loading = false, error = e.message ?: e.toString()) ?: it }
        }
    }

    private companion object {
        const val TAG = "PureHeat"
        const val POLL_INTERVAL_MS = 60_000L
    }
}
