package de.cplagens.pureheat.hmip

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatingStateParserTest {

    // Shortened, anonymized sample in the shape returned by getCurrentState for an
    // HmIP-FALMOT-C8. As in real data, room membership is only listed in the group's
    // `channels`, while the actuator channels' own `groups` arrays are empty.
    private val sample = """
    {
      "devices": {
        "3014F7110000000000000001": {
          "type": "FLOOR_TERMINAL_BLOCK_8",
          "modelType": "HmIP-FALMOT-C8",
          "label": "Fußbodenheizung EG",
          "functionalChannels": {
            "0": { "functionalChannelType": "DEVICE_BASE_FLOOR_HEATING", "index": 0, "groups": [] },
            "1": { "functionalChannelType": "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL", "index": 1,
                   "label": "Bad", "valvePosition": 0.45, "valveState": "ADAPTION_DONE", "groups": [] },
            "2": { "functionalChannelType": "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL", "index": 2,
                   "label": "", "valvePosition": 0.0, "valveState": "ADAPTION_DONE", "groups": [] },
            "3": { "functionalChannelType": "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL", "index": 3,
                   "label": "", "valvePosition": 0.3, "valveState": "ADAPTION_DONE", "groups": [] },
            "8": { "functionalChannelType": "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL", "index": 8,
                   "label": "", "valveState": "ADJUSTMENT_TOO_SMALL", "groups": [] },
            "9": { "functionalChannelType": "HEAT_DEMAND_CHANNEL", "index": 9, "groups": [] }
          }
        },
        "3014F7110000000000000002": {
          "type": "WALL_MOUNTED_THERMOSTAT_PRO", "label": "Thermostat Bad", "functionalChannels": {}
        }
      },
      "groups": {
        "grp-bad": {
          "type": "HEATING", "label": "Bad", "actualTemperature": 21.5, "setPointTemperature": 23.0,
          "humidity": 55, "valvePosition": 0.45, "windowState": "CLOSED",
          "channels": [
            { "deviceId": "3014F7110000000000000002", "channelIndex": 1 },
            { "deviceId": "3014F7110000000000000001", "channelIndex": 1 }
          ]
        },
        "grp-wohnen": {
          "type": "HEATING", "label": "Wohnzimmer", "actualTemperature": 21.0,
          "setPointTemperature": 20.0, "valvePosition": 0.0,
          "channels": [
            { "deviceId": "3014F7110000000000000001", "channelIndex": 2 },
            { "deviceId": "3014F7110000000000000001", "channelIndex": 3 }
          ]
        },
        "grp-meta": { "type": "META", "label": "EG" }
      }
    }
    """.trimIndent()

    private val state = HeatingStateParser.parse(Json.parseToJsonElement(sample).jsonObject)

    @Test
    fun assignsCircuitsToRoomsViaGroupChannels() {
        assertEquals(listOf("Bad", "Wohnzimmer"), state.rooms.map { it.label })

        val bad = state.rooms.first { it.label == "Bad" }
        assertEquals(listOf(1), bad.circuits.map { it.channelIndex })
        assertEquals(0.45, bad.circuits.single().valvePosition!!, 0.0001)
        assertEquals(55, bad.humidity)

        val wohnen = state.rooms.first { it.label == "Wohnzimmer" }
        assertEquals(listOf(2, 3), wohnen.circuits.map { it.channelIndex })
        assertNull(wohnen.circuits.first().channelLabel) // blank label -> null
    }

    @Test
    fun detectsHeatingPerCircuit() {
        val bad = state.rooms.first { it.label == "Bad" }
        assertTrue(bad.isHeating)

        val wohnen = state.rooms.first { it.label == "Wohnzimmer" }
        // Room aggregate says 0 %, but circuit 3 is open -> the room counts as heating.
        assertTrue(wohnen.isHeating)
        assertFalse(wohnen.circuits.first { it.channelIndex == 2 }.isHeating)
    }

    @Test
    fun reportsUnassignedCircuitsAndValveProblems() {
        val unassigned = state.unassignedCircuits
        assertEquals(listOf(8), unassigned.map { it.channelIndex })
        assertTrue(unassigned.single().hasValveProblem)
        assertNull(unassigned.single().valvePosition)
        assertEquals("Stellweg zu klein / nicht angeschlossen", valveStateText(unassigned.single().valveState))
    }
}
