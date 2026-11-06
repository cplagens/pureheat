/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.export

import de.cplagens.pureheat.data.DetailSection
import de.cplagens.pureheat.data.ViewSettings
import de.cplagens.pureheat.data.history.HistoryDao
import de.cplagens.pureheat.data.room.RoomDescription
import de.cplagens.pureheat.data.room.RoomDescriptionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PushbackInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Settings that can be moved to another phone. **No credentials** (SGTIN, token) and no server
 * cache: after an import the user connects the access point again.
 */
@Serializable
data class SettingsBackup(
    val format: Int = FORMAT,
    val exportedAt: Long = 0,
    val appVersion: String = "",
    /** Per-app language tag, "" = system language. */
    val language: String = "",
    val hiddenSections: List<String> = emptyList(),
    val roomOrder: List<String> = emptyList(),
    val roomDescriptions: Map<String, RoomDescription> = emptyMap(),
) {
    companion object {
        const val FORMAT = 1
    }
}

data class ImportResult(val settings: SettingsBackup?, val rows: Int)

/**
 * Export of all measurements (CSV) and settings (JSON) into one ZIP, and the import of such a
 * ZIP (or a bare settings JSON). Measurements are merged (same timestamp = replaced), settings
 * are replaced. Streams in time slices, so two years of history don't need much memory.
 */
class DataTransfer(
    private val dao: HistoryDao,
    private val descriptions: RoomDescriptionStore,
    private val view: ViewSettings,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    suspend fun export(out: OutputStream, roomLabels: Map<String, String>, appVersion: String, language: String) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            ZipOutputStream(out.buffered()).use { zip ->
                val writer = OutputStreamWriter(zip, Charsets.UTF_8)
                suspend fun entry(name: String, write: suspend () -> Unit) {
                    zip.putNextEntry(ZipEntry(name))
                    write()
                    writer.flush()
                    zip.closeEntry()
                }
                fun line(s: String) = writer.write(s + "\n")

                entry(README) { writer.write(readme(now, appVersion)) }
                entry(SETTINGS) { writer.write(json.encodeToString(SettingsBackup.serializer(), settings(now, appVersion, language))) }
                entry(ROOM_NAMES) {
                    line(Csv.ROOM_NAMES)
                    roomLabels.forEach { (id, label) -> line(Csv.roomName(id, label)) }
                }
                val first = dao.firstTimestamp() ?: now
                entry(ROOMS) {
                    line(Csv.ROOMS)
                    slices(first, now) { from, to -> dao.roomSamplesBetween(from, to).forEach { line(Csv.room(it)) } }
                }
                entry(CIRCUITS) {
                    line(Csv.CIRCUITS)
                    slices(first, now) { from, to -> dao.circuitSamplesBetween(from, to).forEach { line(Csv.circuit(it)) } }
                }
                entry(WEATHER) {
                    line(Csv.WEATHER)
                    slices(first, now) { from, to -> dao.weatherSamplesBetween(from, to).forEach { line(Csv.weather(it)) } }
                }
                entry(PREDICTIONS) {
                    line(Csv.PREDICTIONS)
                    dao.allPredictionsSince(0).forEach { line(Csv.prediction(it)) }
                }
            }
        }

    /** Reads a PureHeat ZIP or a bare settings JSON; applies settings and merges measurements. */
    suspend fun import(input: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val stream = PushbackInputStream(input.buffered(), 2)
        val magic = ByteArray(2)
        val n = stream.read(magic)
        if (n > 0) stream.unread(magic, 0, n)
        if (n == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) importZip(stream) else {
            val settings = parseSettings(stream.readBytes().toString(Charsets.UTF_8))
            settings?.let(::applySettings)
            ImportResult(settings, 0)
        }
    }

    private suspend fun importZip(input: InputStream): ImportResult {
        var settings: SettingsBackup? = null
        var rows = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // A fresh reader per entry (not closed: that would close the zip); the zip stream
                // ends each entry with EOF, so nothing of the next entry is read ahead.
                val reader = BufferedReader(InputStreamReader(zip, Charsets.UTF_8))
                when (entry.name) {
                    SETTINGS -> settings = parseSettings(reader.readText())
                    ROOMS -> rows += batches(reader, Csv::parseRoom) { dao.insertRooms(it) }
                    CIRCUITS -> rows += batches(reader, Csv::parseCircuit) { dao.insertCircuits(it) }
                    WEATHER -> rows += batches(reader, Csv::parseWeather) { dao.insertWeatherAll(it) }
                    PREDICTIONS -> rows += batches(reader, Csv::parsePrediction) { dao.insertPredictions(it) }
                }
            }
        }
        settings?.let(::applySettings)
        return ImportResult(settings, rows)
    }

    private suspend fun <T> batches(reader: BufferedReader, parse: (String) -> T?, insert: suspend (List<T>) -> Unit): Int {
        var count = 0
        val batch = ArrayList<T>(BATCH)
        reader.readLine() // header
        while (true) {
            // An entry ends where the ZipInputStream reports end-of-entry (readLine() returns null).
            val l = reader.readLine() ?: break
            parse(l)?.let { batch += it }
            if (batch.size >= BATCH) {
                insert(batch.toList()); count += batch.size; batch.clear()
            }
        }
        if (batch.isNotEmpty()) { insert(batch.toList()); count += batch.size }
        return count
    }

    private fun parseSettings(text: String): SettingsBackup? =
        runCatching { json.decodeFromString(SettingsBackup.serializer(), text) }.getOrNull()?.takeIf { it.format <= SettingsBackup.FORMAT }

    private fun applySettings(s: SettingsBackup) {
        descriptions.replaceAll(s.roomDescriptions)
        view.replaceAll(
            hidden = s.hiddenSections.mapNotNull { name -> DetailSection.entries.firstOrNull { it.name == name } }.toSet(),
            roomOrder = s.roomOrder,
        )
    }

    private fun settings(now: Long, appVersion: String, language: String) = SettingsBackup(
        exportedAt = now,
        appVersion = appVersion,
        language = language,
        hiddenSections = view.hidden.value.map { it.name },
        roomOrder = view.roomOrder.value,
        roomDescriptions = descriptions.all.value,
    )

    private suspend inline fun slices(first: Long, now: Long, block: suspend (Long, Long) -> Unit) {
        var from = first
        while (from <= now) {
            val to = from + SLICE_MS
            block(from, to)
            from = to
        }
    }

    private fun readme(now: Long, appVersion: String) = """
        PureHeat export (${java.time.Instant.ofEpochMilli(now)}, app $appVersion)

        settings.json     settings (room descriptions, view, language) – no access data
        room_names.csv    room id -> name at export time
        rooms.csv         per room: actual/target temperature (°C), valve 0..1, humidity (%RH)
        circuits.csv      per floor heating channel: valve 0..1
        weather.csv       outside temperature (°C), outside water vapour (g/m³)
        predictions.csv   probe run: predicted vs. actual time a target was reached

        Timestamps: epoch milliseconds and UTC (ISO 8601). Decimal point '.', empty = no value.
        Import: PureHeat → Info → Import (measurements are merged, settings replaced).
    """.trimIndent() + "\n"

    companion object {
        const val README = "README.txt"
        const val SETTINGS = "settings.json"
        const val ROOM_NAMES = "room_names.csv"
        const val ROOMS = "rooms.csv"
        const val CIRCUITS = "circuits.csv"
        const val WEATHER = "weather.csv"
        const val PREDICTIONS = "predictions.csv"
        private const val BATCH = 2_000
        private const val SLICE_MS = 7 * 24 * 3_600_000L
    }
}
