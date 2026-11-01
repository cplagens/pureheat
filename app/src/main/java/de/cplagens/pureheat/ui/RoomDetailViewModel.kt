package de.cplagens.pureheat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.cplagens.pureheat.PureHeatApp
import de.cplagens.pureheat.data.history.CircuitSample
import de.cplagens.pureheat.data.history.HistoryAnalysis
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.ThermalPhase
import de.cplagens.pureheat.data.history.WeatherSample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class HistoryRange(val label: String, val millis: Long) {
    DAY("24 h", 24 * 3_600_000L),
    DAYS_3("3 Tage", 3 * 24 * 3_600_000L),
    WEEK("7 Tage", 7 * 24 * 3_600_000L),
    WEEKS_2("14 Tage", 14 * 24 * 3_600_000L),
    ALL("60 Tage", 60 * 24 * 3_600_000L),
}

data class RoomDetailState(
    val range: HistoryRange = HistoryRange.DAY,
    val start: Long = 0,
    val end: Long = 0,
    val samples: List<RoomSample> = emptyList(),
    /** Per channel, keyed by (deviceId, channelIndex). */
    val circuits: Map<Pair<String, Int>, List<CircuitSample>> = emptyMap(),
    val weather: List<WeatherSample> = emptyList(),
    val phases: List<ThermalPhase> = emptyList(),
)

/** All recorded data of one room for the selected time range (read-only). */
class RoomDetailViewModel(app: Application, private val roomId: String) : AndroidViewModel(app) {
    private val repository = (app as PureHeatApp).repository
    private val range = MutableStateFlow(HistoryRange.DAY)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<RoomDetailState> = range.flatMapLatest { r ->
        val end = System.currentTimeMillis()
        val start = end - r.millis
        combine(
            repository.roomHistorySince(start),
            repository.circuitHistorySince(start),
            repository.weatherHistorySince(start),
        ) { rooms, circuits, weather ->
            val samples = rooms.filter { it.roomId == roomId }
            RoomDetailState(
                range = r,
                start = start,
                end = maxOf(end, samples.lastOrNull()?.timestamp ?: end),
                samples = samples,
                circuits = circuits.groupBy { it.deviceId to it.channelIndex },
                weather = weather,
                phases = HistoryAnalysis.phases(samples, weather),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomDetailState())

    fun setRange(r: HistoryRange) {
        range.value = r
    }
}
