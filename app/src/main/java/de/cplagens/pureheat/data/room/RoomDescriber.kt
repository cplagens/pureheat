/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.room

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import de.cplagens.pureheat.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** How a description was turned into properties. */
enum class ExtractionSource {
    /** On-device model (Gemini Nano) plus keywords for what it left out. */
    AI,
    /** Keywords only: no on-device model on this device. */
    KEYWORDS,
    /** Keywords only for now; the on-device model is being downloaded by the system. */
    KEYWORDS_AI_DOWNLOADING,
}

data class Extraction(val properties: RoomProperties, val source: ExtractionSource)

/**
 * Turns the user's free text (typed or dictated) into [RoomProperties]. Uses Gemini Nano on the
 * device where available (ML Kit Prompt API, nothing leaves the phone) and the [KeywordExtractor]
 * otherwise and for anything the model missed. Must run while the app is in the foreground.
 */
class RoomDescriber {
    private val model: GenerativeModel by lazy { Generation.getClient() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var downloading = false

    suspend fun extract(text: String): Extraction {
        val keywords = KeywordExtractor.extract(text)
        val status = runCatching { model.checkStatus() }.getOrElse { FeatureStatus.UNAVAILABLE }
        return when (status) {
            FeatureStatus.AVAILABLE -> {
                val ai = runCatching { withTimeoutOrNull(AI_TIMEOUT_MS) { askModel(text) } }
                    .onFailure { if (BuildConfig.DEBUG) Log.d(TAG, "Nano failed: $it") }
                    .getOrNull()
                // Keyword hits are exact, tested German/English patterns and win (e.g. Nano reads
                // "1. Stock" as ground floor); the model fills in everything else and other languages.
                if (ai != null) Extraction(keywords.mergedWith(ai), ExtractionSource.AI) else Extraction(keywords, ExtractionSource.KEYWORDS)
            }
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                if (status == FeatureStatus.DOWNLOADABLE) startDownload()
                Extraction(keywords, ExtractionSource.KEYWORDS_AI_DOWNLOADING)
            }
            else -> Extraction(keywords, ExtractionSource.KEYWORDS)
        }
    }

    private suspend fun askModel(text: String): RoomProperties? {
        val response = model.generateContent(
            generateContentRequest(TextPart(PROMPT + text.take(MAX_INPUT_CHARS))) {
                temperature = 0f
                topK = 1
                maxOutputTokens = 300
            },
        )
        val raw = response.candidates.firstOrNull()?.text ?: return null
        if (BuildConfig.DEBUG) Log.d(TAG, "Nano: $raw")
        val json = raw.substringAfter('{', "").substringBeforeLast('}', "").takeIf { it.isNotBlank() } ?: return null
        return runCatching { RoomProperties.fromJson(lenient.parseToJsonElement("{$json}").jsonObject) }.getOrNull()
    }

    /** The system (AICore) downloads the shared model once; the next analysis can use it. */
    private fun startDownload() {
        if (downloading) return
        downloading = true
        scope.launch {
            runCatching {
                model.download().collect { s ->
                    if (s is DownloadStatus.DownloadCompleted || s is DownloadStatus.DownloadFailed) downloading = false
                }
            }.onFailure { downloading = false }
        }
    }

    fun close() {
        runCatching { model.close() }
    }

    private companion object {
        const val TAG = "PureHeat"
        const val AI_TIMEOUT_MS = 30_000L
        const val MAX_INPUT_CHARS = 2_000
        val lenient = Json { isLenient = true }

        /** Few fixed rules, JSON only, null for unknown – small on-device models follow that best. */
        val PROMPT = """
            Extract facts about a room from the description below. The description may be in any language.
            Answer with one JSON object only, no other text. Use null (or []) for anything not mentioned. Do not guess.
            Keys:
            "areaM2": floor area in square metres (number),
            "ceilingHeightM": ceiling height in metres (number),
            "windowCount": number of windows and glass doors (integer),
            "windowDirections": directions the windows face, list of "N","NE","E","SE","S","SW","W","NW",
            "glazing": "SINGLE", "DOUBLE" or "TRIPLE",
            "exteriorWalls": number of walls to the outside (integer 0-4),
            "position": "BASEMENT", "GROUND" (ground floor only), "UPPER" (any floor above the ground floor, e.g. German "1. Stock", "OG") or "ATTIC" (directly under the roof),
            "aboveUnheated": true if the room is above an unheated cellar or garage,
            "floorCovering": "TILE" (tiles, stone), "WOOD" (parquet, floorboards), "LAMINATE" (laminate, vinyl) or "CARPET",
            "shutters": true if there are roller shutters or blinds,
            "heatSources": list of "TOWEL_RADIATOR", "STOVE" (fireplace, wood stove), "ELECTRONICS" (computers, TV), "COOKING",
            "usage": "LIVING", "BEDROOM", "CHILD", "KITCHEN", "BATH", "OFFICE" or "HALL",
            "ventilatesAtNight": true if a window is open or tilted at night,
            "bathtub": true if there is a bathtub,
            "shower": true if there is a shower.
            Description:
        """.trimIndent() + "\n"
    }
}
