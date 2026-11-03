package de.cplagens.pureheat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.cplagens.pureheat.PureHeatApp
import de.cplagens.pureheat.data.room.ExtractionSource
import de.cplagens.pureheat.data.room.RoomDescriber
import de.cplagens.pureheat.data.room.RoomDescription
import de.cplagens.pureheat.data.room.RoomProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomPropertiesState(
    val text: String = "",
    val properties: RoomProperties = RoomProperties(),
    val analyzing: Boolean = false,
    /** Set after an analysis; tells the user how the properties were found. */
    val source: ExtractionSource? = null,
    /** The last analysis found nothing. */
    val nothingFound: Boolean = false,
    val dirty: Boolean = false,
    val saved: Boolean = false,
)

/** Free text → structured room properties (on-device AI or keywords), checked and saved by the user. Local only. */
class RoomPropertiesViewModel(app: Application, private val roomId: String) : AndroidViewModel(app) {
    private val store = (app as PureHeatApp).repository.descriptions
    private val describer = RoomDescriber()

    private val _ui = MutableStateFlow(
        store.get(roomId).let { RoomPropertiesState(text = it?.text.orEmpty(), properties = it?.properties ?: RoomProperties()) },
    )
    val ui: StateFlow<RoomPropertiesState> = _ui.asStateFlow()

    fun setText(text: String) = _ui.update { it.copy(text = text, dirty = true, saved = false) }

    /** Adds a dictated sentence or a tapped example to the description. */
    fun append(part: String) = _ui.update { s ->
        val text = s.text.trimEnd().let { if (it.isEmpty()) part else if (it.endsWith('.') || it.endsWith(',')) "$it $part" else "$it, $part" }
        s.copy(text = text, dirty = true, saved = false)
    }

    fun setProperties(p: RoomProperties) = _ui.update { it.copy(properties = p.sanitized(), dirty = true, saved = false) }

    fun analyze() {
        val text = _ui.value.text
        if (text.isBlank() || _ui.value.analyzing) return
        _ui.update { it.copy(analyzing = true) }
        viewModelScope.launch {
            val result = describer.extract(text)
            _ui.update {
                it.copy(
                    // Newly recognised values win; fields set by hand before stay when nothing new was found.
                    properties = result.properties.mergedWith(it.properties),
                    analyzing = false,
                    source = result.source,
                    nothingFound = result.properties.isEmpty,
                    dirty = true,
                    saved = false,
                )
            }
        }
    }

    fun save() {
        val s = _ui.value
        store.save(roomId, RoomDescription(s.text.trim(), s.properties, System.currentTimeMillis()))
        _ui.update { it.copy(dirty = false, saved = true) }
    }

    override fun onCleared() {
        describer.close()
    }
}
