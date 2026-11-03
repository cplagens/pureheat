/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
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
      "home": { "weather": { "temperature": 10.6, "humidity": 80 } },
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
                   "valvePosition": 0.2, "groups": [] },
            "4": { "functionalChannelType": "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL", "index": 4,
                   "valveState": "ADJUSTMENT_TOO_SMALL", "groups": [] }
          }
        },
        "dev-wth": { "type": "WALL_MOUNTED_THERMOSTAT_PRO", "label": "Thermostat Bad", "functionalChannels": {} }
      },
      "groups": {
        "grp-bad": { "type": "HEATING", "label": "Bad", "actualTemperature": 21.5,
                     "setPointTemperature": 23.0, "humidity": 55, "valvePosition": 0.45, "windowState": "CLOSED",
                     "activeProfile": "PROFILE_1", "minTemperature": 5.0, "maxTemperature": 30.0,
                     "profiles": {
                       "PROFILE_2": { "index": "PROFILE_2", "profileId": "p2", "name": "Sommer ", "enabled": true, "visible": true },
                       "PROFILE_1": { "index": "PROFILE_1", "profileId": "p1", "name": "", "enabled": true, "visible": true },
                       "PROFILE_3": { "index": "PROFILE_3", "profileId": "p3", "name": "", "enabled": false, "visible": false }
                     } },
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

        // Channel 4 (no group, no position) is an unused output and hidden.
        assertEquals(listOf(3), state.unassignedCircuits.map { it.channelIndex })

        assertEquals(10.6, state.outsideTemperature!!, 0.0)
        assertEquals("PROFILE_1", bad.activeProfile)
        // Disabled profile 3 is dropped; blank name stays null (UI shows "Profile 1"), names are trimmed.
        assertEquals(listOf("1", "2"), bad.profiles.map { it.number })
        assertEquals(listOf(null, "Sommer"), bad.profiles.map { it.name })
    }
}
