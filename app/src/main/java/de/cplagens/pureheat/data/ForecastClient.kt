package de.cplagens.pureheat.data

import de.cplagens.pureheat.hmip.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Hourly outside temperature forecast from Open-Meteo (free, no account, no API key).
 * Only the rounded coordinates ([GeoPoint], ~1 km) are sent – agreed with the user.
 */
class ForecastClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    /** (timestamp in ms, °C) for the next [days] days, hourly. */
    suspend fun hourlyTemperature(location: GeoPoint, days: Int = 3): List<Pair<Long, Double>> = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                "&hourly=temperature_2m&forecast_days=%d&timeformat=unixtime&timezone=UTC",
            location.latitude,
            location.longitude,
            days,
        )
        http.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("Open-Meteo HTTP ${resp.code}")
            parse(resp.body?.string().orEmpty())
        }
    }

    companion object {
        /** Lenient: missing or null values are skipped. */
        fun parse(body: String): List<Pair<Long, Double>> {
            val root = Json.parseToJsonElement(body).jsonObject
            val hourly = root["hourly"] as? JsonObject ?: return emptyList()
            val times = (hourly["time"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.longOrNull }
            val temps = (hourly["temperature_2m"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.doubleOrNull }
            return times.zip(temps).mapNotNull { (t, v) -> if (t != null && v != null) t * 1000 to v else null }
        }
    }
}
