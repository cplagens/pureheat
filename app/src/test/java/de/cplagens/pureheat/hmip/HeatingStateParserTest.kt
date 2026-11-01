package de.cplagens.pureheat.hmip

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatingStateParserTest {

    // Shortened, anonymized sample in the shape returned by getCurrentState.
    private val sample = """
    {
      "devices": {
        "dev-fal": {
          "type": "FLOOR_TERMINAL_BLOCK_10",
          "label": "Fußbodenheizung EG",
          "functionalChannels": {
            "0": { "functionalChannelType": "DEVICE_BASE_FLOOR_HEATING", "index": 0 },
            "1": { "functionalChannelType": "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL", "index": 1,
                   "label": "Bad", "valvePosition": 0.45, "valveState": "ADAPTION_DONE", "groups": ["grp-bad"] },
            "2": { "functionalChannelType": "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL", "index": 2,
                   "label": "", "valvePosition": 0.0, "valveState": "ADAPTION_DONE", "groups": ["grp-wohnen"] },
            "3": { "functionalChannelType": "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL", "index": 3,
                   "valvePosition": 0.2, "groups": [] }
          }
        },
        "dev-wth": { "type": "WALL_MOUNTED_THERMOSTAT_PRO", "label": "Thermostat Bad", "functionalChannels": {} }
      },
      "groups": {
        "grp-bad": { "type": "HEATING", "label": "Bad", "actualTemperature": 21.5,
                     "setPointTemperature": 23.0, "humidity": 55, "valvePosition": 0.45, "windowState": "CLOSED" },
        "grp-wohnen": { "type": "HEATING", "label": "Wohnzimmer", "actualTemperature": 21.0,
                        "setPointTemperature": 20.0, "valvePosition": 0.0 },
        "grp-meta": { "type": "META", "label": "EG" }
      }
    }
    """.trimIndent()

    @Test
    fun parsesRoomsAndCircuits() {
        val state = HeatingStateParser.parse(Json.parseToJsonElement(sample).jsonObject)

        assertEquals(listOf("Bad", "Wohnzimmer"), state.rooms.map { it.label })

        val bad = state.rooms.first { it.label == "Bad" }
        assertTrue(bad.isHeating)
        assertEquals(1, bad.circuits.size)
        assertEquals(0.45, bad.circuits.single().valvePosition!!, 0.0001)
        assertEquals(55, bad.humidity)

        val wohnen = state.rooms.first { it.label == "Wohnzimmer" }
        assertFalse(wohnen.isHeating)
        assertEquals(null, wohnen.circuits.single().channelLabel) // blank label -> null

        assertEquals(listOf(3), state.unassignedCircuits.map { it.channelIndex })
    }
}
