/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.ai

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

/**
 * Gemini Nano on the device via the ML Kit Prompt API (Android AICore): nothing leaves the phone.
 * Shared by room descriptions and heating profile descriptions. Only works while the app is in
 * the foreground; callers always have a non-AI fallback.
 */
class OnDeviceAi {
    enum class Availability { AVAILABLE, DOWNLOADING, UNAVAILABLE }

    private val model: GenerativeModel by lazy { Generation.getClient() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var downloading = false

    /** Checks the model; if it can be downloaded, the system download is started. */
    suspend fun availability(): Availability =
        when (runCatching { model.checkStatus() }.getOrElse { FeatureStatus.UNAVAILABLE }) {
            FeatureStatus.AVAILABLE -> Availability.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> { startDownload(); Availability.DOWNLOADING }
            FeatureStatus.DOWNLOADING -> Availability.DOWNLOADING
            else -> Availability.UNAVAILABLE
        }

    /**
     * Asks for a JSON answer and returns the outermost `{…}` of it (Nano wraps JSON in a
     * ```json block), or null on error/timeout.
     */
    suspend fun askJson(prompt: String, maxOutputTokens: Int, timeoutMs: Long = 30_000): String? = runCatching {
        withTimeoutOrNull(timeoutMs) {
            val response = model.generateContent(
                generateContentRequest(TextPart(prompt)) {
                    temperature = 0f
                    topK = 1
                    this.maxOutputTokens = maxOutputTokens
                },
            )
            val raw = response.candidates.firstOrNull()?.text ?: return@withTimeoutOrNull null
            if (BuildConfig.DEBUG) Log.d(TAG, "Nano: $raw")
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) null else raw.substring(start, end + 1)
        }
    }.onFailure { if (BuildConfig.DEBUG) Log.d(TAG, "Nano failed: $it") }.getOrNull()

    /** The system (AICore) downloads the shared model once; the next request can use it. */
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
    }
}
