/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.cplagens.pureheat.Lang
import de.cplagens.pureheat.PureHeatApp
import de.cplagens.pureheat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

data class DataState(val busy: Boolean = false, val message: UiText? = null, val error: Boolean = false)

/** Export (share or save as file) and import of measurements and settings; see DataTransfer. */
class DataViewModel(app: Application) : AndroidViewModel(app) {
    private val transfer = (app as PureHeatApp).dataTransfer
    private val _ui = MutableStateFlow(DataState())
    val ui: StateFlow<DataState> = _ui.asStateFlow()

    private val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()

    /** File name suggested for "save as". */
    fun fileName() = "pureheat-export-${LocalDate.now()}.zip"

    /** Writes the export into the app cache and hands a content:// URI to [onReady] for the share sheet. */
    fun exportForSharing(roomLabels: Map<String, String>, onReady: (Uri) -> Unit) = run {
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, EXPORT_DIR)
        // Only the latest export is kept; older ones would just fill the cache.
        withContext(Dispatchers.IO) { dir.deleteRecursively(); dir.mkdirs() }
        val file = File(dir, fileName())
        withContext(Dispatchers.IO) { file.outputStream().use { transfer.export(it, roomLabels, version, Lang.current()) } }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        _ui.value = DataState()
        onReady(uri)
    }

    /** Writes the export to a file the user picked (e.g. on Google Drive). */
    fun exportTo(uri: Uri, roomLabels: Map<String, String>) = run {
        val resolver = getApplication<Application>().contentResolver
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri)?.use { transfer.export(it, roomLabels, version, Lang.current()) } ?: error("no stream")
        }
        _ui.value = DataState(message = UiText.Res(R.string.data_exported))
    }

    fun import(uri: Uri) = run {
        val resolver = getApplication<Application>().contentResolver
        val result = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { transfer.import(it) } ?: error("no stream") }
        if (result.settings == null && result.rows == 0) {
            _ui.value = DataState(message = UiText.Res(R.string.data_import_failed), error = true)
        } else {
            _ui.value = DataState(message = UiText.Res(R.string.data_imported, result.rows))
            // Last, because it recreates the activity.
            result.settings?.language?.let { if (it != Lang.current()) Lang.set(it) }
        }
    }

    private fun run(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.value = DataState(busy = true)
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (de.cplagens.pureheat.BuildConfig.DEBUG) android.util.Log.d("PureHeat", "Export/import failed: $e")
                _ui.value = DataState(message = UiText.Res(R.string.data_failed), error = true)
            }
        }
    }

    private companion object {
        const val EXPORT_DIR = "exports"
    }
}
