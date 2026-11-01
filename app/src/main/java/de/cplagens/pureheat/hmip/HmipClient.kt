package de.cplagens.pureheat.hmip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    data class Endpoints(val rest: String, val websocket: String)

    class HmipException(message: String, val code: Int? = null) : Exception(message)

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
        put("applicationVersion", "0.1")
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
            resp.code to (resp.body?.string() ?: "")
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
        if (code != 200) throw HmipException("Lookup fehlgeschlagen (HTTP $code): $text", code)
        val obj = json.parseToJsonElement(text).jsonObject
        return Endpoints(
            rest = obj["urlREST"]!!.jsonPrimitive.content,
            websocket = obj["urlWebSocket"]!!.jsonPrimitive.content,
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
        if (reqCode != 200) throw HmipException("Verbindungsanfrage abgelehnt (HTTP $reqCode): $reqText", reqCode)

        onWaitingForButton()
        val deadline = System.currentTimeMillis() + timeoutMs
        val idBody = buildJsonObject { put("deviceId", deviceId) }
        while (true) {
            val (ackCode, _) = post("$rest/hmip/auth/isRequestAcknowledged", idBody, headers)
            if (ackCode == 200) break
            if (System.currentTimeMillis() > deadline) {
                throw HmipException("Der blaue Knopf am Access Point wurde nicht rechtzeitig gedrückt.")
            }
            delay(2_000)
        }

        val (tokCode, tokText) = post("$rest/hmip/auth/requestAuthToken", idBody, headers)
        if (tokCode != 200) throw HmipException("Token-Anfrage fehlgeschlagen (HTTP $tokCode): $tokText", tokCode)
        val authToken = json.parseToJsonElement(tokText).jsonObject["authToken"]!!.jsonPrimitive.content

        val (confCode, confText) = post(
            "$rest/hmip/auth/confirmAuthToken",
            buildJsonObject {
                put("deviceId", deviceId)
                put("authToken", authToken)
            },
            headers,
        )
        if (confCode != 200) throw HmipException("Token-Bestätigung fehlgeschlagen (HTTP $confCode): $confText", confCode)
        return authToken
    }

    /** Fetches the full current state of the home as raw JSON. */
    suspend fun getCurrentState(sgtin: String, authToken: String, restUrl: String? = null): JsonObject {
        val rest = restUrl ?: lookup(sgtin).rest
        val (code, text) = post(
            "$rest/hmip/home/getCurrentState",
            clientCharacteristics(),
            baseHeaders(sgtin) + ("AUTHTOKEN" to authToken),
        )
        if (code != 200) throw HmipException("Status konnte nicht geladen werden (HTTP $code): $text", code)
        return json.parseToJsonElement(text).jsonObject
    }

    companion object {
        const val LOOKUP_URL = "https://lookup.homematic.com:48335/getHost"
        const val API_VERSION_HEADER = "12"
        private const val CLIENT_AUTH_SALT = "jiLpVitHvWnIGD1yo7MA"
    }
}
