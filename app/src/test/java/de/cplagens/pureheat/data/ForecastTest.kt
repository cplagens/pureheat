package de.cplagens.pureheat.data

import de.cplagens.pureheat.hmip.GeoPoint
import de.cplagens.pureheat.hmip.HeatingStateParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForecastTest {

    @Test
    fun parsesOpenMeteoHourly() {
        val body = """
            {"latitude":52.5,"longitude":13.4,"hourly_units":{"time":"unixtime","temperature_2m":"°C"},
             "hourly":{"time":[1793577600,1793581200,1793584800],"temperature_2m":[6.1,null,5.4]}}
        """.trimIndent()
        val hours = ForecastClient.parse(body)
        assertEquals(listOf(1793577600_000L to 6.1, 1793584800_000L to 5.4), hours)
    }

    @Test
    fun missingHourlyGivesEmptyList() {
        assertEquals(emptyList<Pair<Long, Double>>(), ForecastClient.parse("""{"error":true,"reason":"x"}"""))
    }

    @Test
    fun locationIsRoundedToTwoDecimals() {
        assertEquals(GeoPoint(52.52, 13.41), GeoPoint.rounded(52.51834, 13.40621))
        assertNull(GeoPoint.rounded(0.0, 0.0))
        assertNull(GeoPoint.rounded(91.0, 13.0))
    }

    @Test
    fun parserReadsLocationStrings() {
        // Made-up coordinates; the cloud sends them as strings.
        val state = HeatingStateParser.parse(
            Json.parseToJsonElement(
                """{"home":{"location":{"city":"X","latitude":"52.51834","longitude":"13.40621"}},"devices":{},"groups":{}}""",
            ).jsonObject,
        )
        assertEquals(GeoPoint(52.52, 13.41), state.location)
    }
}
