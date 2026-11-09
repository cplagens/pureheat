/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.hmip

import android.util.Log
import androidx.annotation.StringRes
import de.cplagens.pureheat.R
import de.cplagens.pureheat.ui.UiText
import de.cplagens.pureheat.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Minimal client for the (unofficial, reverse-engineered) Homematic IP Cloud REST API
 * used by the Homematic IP Access Point (HmIP-HAP).
 *
 * Protocol details follow the community library homematicip-rest-api (Python).
 * eQ-3 does not document this API; it may change without notice.
 */
class HmipClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : HeatingCloud {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    data class Endpoints(val rest: String, val websocket: String)

    /** Failure with a translatable message ([uiText]) for the UI. */
    class HmipException(val uiText: UiText, val code: Int? = null) : Exception(uiText.toString())

    /** Error for a non-200 response, e.g. "Lookup failed: Access denied. … (HTTP 403)". */
    private fun httpFailure(@StringRes context: Int, code: Int, body: String): HmipException {
        val errorCode = runCatching {
            json.parseToJsonElement(body).jsonObject["errorCode"]?.jsonPrimitive?.content
        }.getOrNull()
        return HmipException(httpError(UiText.Res(context), code, errorCode), code)
    }

    /** Normalizes "3014-F711-A000-0000-0000-0000" to "3014F711A0000000000000". */
    fun normalizeSgtin(raw: String): String = raw.replace("-", "").replace(" ", "").uppercase()

    private fun clientAuth(sgtin: String): String {
        val digest = MessageDigest.getInstance("SHA-512")
            .digest((sgtin + CLIENT_AUTH_SALT).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun clientCharacteristics(): JsonObject = buildJsonObject {
        put("apiVersion", "10")
        put("applicationIdentifier", "PureHeat")
        put("applicationVersion", BuildConfig.VERSION_NAME)
        put("deviceManufacturer", android.os.Build.MANUFACTURER ?: "unknown")
        put("deviceType", "Phone")
        put("language", "de_DE")
        put("osType", "Android")
        put("osVersion", android.os.Build.VERSION.RELEASE ?: "unknown")
    }

    private suspend fun post(
        url: String,
        body: JsonElement,
        headers: Map<String, String>,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonType))
            .header("content-type", "application/json")
            .header("accept", "application/json")
            .header("VERSION", API_VERSION_HEADER)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (BuildConfig.DEBUG) {
                // Data/battery check: transfer size vs. payload; OkHttp asks for gzip transparently.
                val net = resp.networkResponse
                Log.d(
                    "PureHeat",
                    "HTTP ${request.url.encodedPath} ${resp.code} " +
                        "encoding=${net?.header("Content-Encoding") ?: "none"} " +
                        "wire=${net?.header("Content-Length") ?: "?"} payload=${text.length}",
                )
            }
            resp.code to text
        }
    }

    private fun baseHeaders(sgtin: String) = mapOf(
        "CLIENTAUTH" to clientAuth(sgtin),
        "ACCESSPOINT-ID" to sgtin,
    )

    /** Asks the lookup service which cloud server hosts this access point. */
    suspend fun lookup(sgtin: String): Endpoints {
        val body = buildJsonObject {
            put("clientCharacteristics", clientCharacteristics())
            put("id", sgtin)
        }
        val (code, text) = post(LOOKUP_URL, body, baseHeaders(sgtin))
        if (code != 200) throw httpFailure(R.string.ctx_lookup, code, text)
        val obj = json.parseToJsonElement(text).jsonObject
        return Endpoints(
            rest = obj.requireString("urlREST"),
            websocket = obj.requireString("urlWebSocket"),
        )
    }

    /**
     * Registers this app as a new client. The user must press the blue button on the
     * access point while [onWaitingForButton] is shown. Returns the auth token.
     */
    suspend fun register(
        sgtin: String,
        pin: String?,
        deviceName: String = "PureHeat",
        onWaitingForButton: suspend () -> Unit = {},
        timeoutMs: Long = 120_000,
    ): String {
        val endpoints = lookup(sgtin)
        val rest = endpoints.rest
        val deviceId = UUID.randomUUID().toString()
        val headers = baseHeaders(sgtin) + (if (!pin.isNullOrBlank()) mapOf("PIN" to pin) else emptyMap())

        val (reqCode, reqText) = post(
            "$rest/hmip/auth/connectionRequest",
            buildJsonObject {
                put("deviceId", deviceId)
                put("deviceName", deviceName)
                put("sgtin", sgtin)
            },
            headers,
        )
        if (reqCode != 200) throw httpFailure(R.string.ctx_connection_request, reqCode, reqText)

        onWaitingForButton()
        val deadline = System.currentTimeMillis() + timeoutMs
        val idBody = buildJsonObject { put("deviceId", deviceId) }
        while (true) {
            val (ackCode, _) = post("$rest/hmip/auth/isRequestAcknowledged", idBody, headers)
            if (ackCode == 200) break
            if (System.currentTimeMillis() > deadline) {
                throw HmipException(UiText.Res(R.string.err_button_timeout))
            }
            delay(2_000)
        }

        val (tokCode, tokText) = post("$rest/hmip/auth/requestAuthToken", idBody, headers)
        if (tokCode != 200) throw httpFailure(R.string.ctx_token_request, tokCode, tokText)
        val authToken = json.parseToJsonElement(tokText).jsonObject.requireString("authToken")

        val (confCode, confText) = post(
            "$rest/hmip/auth/confirmAuthToken",
            buildJsonObject {
                put("deviceId", deviceId)
                put("authToken", authToken)
            },
            headers,
        )
        if (confCode != 200) throw httpFailure(R.string.ctx_token_confirm, confCode, confText)
        return authToken
    }

    /** Fetches the full current state of the home as raw JSON. */
    override suspend fun getCurrentState(sgtin: String, authToken: String, restUrl: String?): JsonObject {
        val rest = restUrl ?: lookup(sgtin).rest
        val (code, text) = post(
            "$rest/hmip/home/getCurrentState",
            buildJsonObject {
                put("clientCharacteristics", clientCharacteristics())
                put("id", sgtin)
            },
            baseHeaders(sgtin) + ("AUTHTOKEN" to authToken),
        )
        if (code != 200) throw httpFailure(R.string.ctx_state, code, text)
        return json.parseToJsonElement(text).jsonObject
    }

    // --- Heating profiles -------------------------------------------------------------------
    // Endpoints as used by homematicip-rest-api. These are the app's only write calls; they
    // are triggered solely from the profile screen after the user confirmed the changes.

    /** Reads one weekly profile of a room (HEATING group). */
    override suspend fun getProfile(
        sgtin: String,
        authToken: String,
        rest: String,
        groupId: String,
        profileIndex: String,
        profileId: String,
    ): JsonObject {
        val text = authed(
            sgtin, authToken, "$rest/hmip/group/heating/getProfile",
            buildJsonObject {
                put("groupId", groupId)
                put("profileIndex", profileIndex)
                put("profileId", profileId)
            },
            R.string.ctx_profile_load,
        )
        return json.parseToJsonElement(text).jsonObject
    }

    /**
     * Writes a complete profile (as returned by [getProfile], with edited `profileDays`).
     * Without the top-level `profileIndex` the cloud answers 400 INVALID_REQUEST (verified 2026-11).
     */
    override suspend fun updateProfile(
        sgtin: String,
        authToken: String,
        rest: String,
        groupId: String,
        profileIndex: String,
        profile: JsonObject,
    ) {
        authed(
            sgtin, authToken, "$rest/hmip/group/heating/updateProfile",
            buildJsonObject {
                put("groupId", groupId)
                put("profileIndex", profileIndex)
                put("profile", profile)
            },
            R.string.ctx_profile_save,
        )
    }

    /** Switches the room to another of its weekly profiles. */
    override suspend fun setActiveProfile(sgtin: String, authToken: String, rest: String, groupId: String, profileIndex: String) {
        authed(
            sgtin, authToken, "$rest/hmip/group/heating/setActiveProfile",
            buildJsonObject {
                put("groupId", groupId)
                put("profileIndex", profileIndex)
            },
            R.string.ctx_profile_activate,
        )
    }

    private fun JsonObject.requireString(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: throw HmipException(UiText.Res(R.string.err_unexpected, "$key missing"))

    private suspend fun authed(
        sgtin: String,
        authToken: String,
        url: String,
        body: JsonElement,
        @StringRes context: Int,
    ): String {
        val (code, text) = post(url, body, baseHeaders(sgtin) + ("AUTHTOKEN" to authToken))
        if (code != 200) throw httpFailure(context, code, text)
        return text
    }

    companion object {
        const val LOOKUP_URL = "https://lookup.homematic.com:48335/getHost"
        const val API_VERSION_HEADER = "12"
        private const val CLIENT_AUTH_SALT = "jiLpVitHvWnIGD1yo7MA"
    }
}
