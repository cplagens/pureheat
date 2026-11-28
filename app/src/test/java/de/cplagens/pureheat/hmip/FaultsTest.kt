/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.hmip

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaultsTest {

    // Anonymized; channel 3 in the shape seen on the real system with an actuator off its valve.
    private val sample = """
    {
      "devices": {
        "dev-fal": {
          "type": "FLOOR_TERMINAL_BLOCK_MOTOR",
          "label": "Floor heating",
          "functionalChannels": {
            "0": { "functionalChannelType": "DEVICE_BASE_FLOOR_HEATING", "index": 0, "unreach": false,
                   "deviceOverheated": true, "lowBat": null },
            "2": { "functionalChannelType": "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL", "index": 2,
                   "valvePosition": 0.0, "valveState": "ADAPTION_DONE", "groups": ["grp-living"] },
            "3": { "functionalChannelType": "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL", "index": 3,
                   "valveState": "ADJUSTMENT_TOO_BIG", "emergencyOperationActive": true, "groups": ["grp-living"] },
            "8": { "functionalChannelType": "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL", "index": 8,
                   "valveState": "ADJUSTMENT_TOO_SMALL", "groups": [] }
          }
        },
        "dev-wth": {
          "type": "WALL_MOUNTED_THERMOSTAT_PRO", "label": "Thermostat",
          "functionalChannels": {
            "0": { "functionalChannelType": "DEVICE_BASE", "unreach": true, "lowBat": true },
            "1": { "functionalChannelType": "WALL_MOUNTED_THERMOSTAT_PRO_CHANNEL", "groups": ["grp-living"] }
          }
        },
        "dev-other": {
          "type": "PLUGABLE_SWITCH", "label": "Lamp",
          "functionalChannels": { "0": { "functionalChannelType": "DEVICE_BASE", "unreach": true } }
        }
      },
      "groups": {
        "grp-living": { "type": "HEATING", "label": "Living room", "valvePosition": 0.0 }
      }
    }
    """.trimIndent()

    private val faults = HeatingStateParser.parse(Json.parseToJsonElement(sample).jsonObject).faults

    @Test
    fun valveStateIsMappedToAKindWithTheRoom() {
        val tooBig = faults.single { it.kind == FaultKind.VALVE_TOO_BIG }
        assertEquals("ADJUSTMENT_TOO_BIG", tooBig.code)
        assertEquals(3, tooBig.channelIndex)
        assertEquals("Living room", tooBig.roomLabel)
        assertTrue(faults.any { it.kind == FaultKind.EMERGENCY && it.channelIndex == 3 })
    }

    @Test
    fun unusedOutputAndUnrelatedDevicesRaiseNothing() {
        // Channel 8 (no group, no position) is the unused output; the lamp is not part of a heating room.
        assertTrue(faults.none { it.channelIndex == 8 })
        assertTrue(faults.none { it.deviceId == "dev-other" })
    }

    @Test
    fun deviceFlags() {
        assertEquals("deviceOverheated", faults.single { it.kind == FaultKind.DEVICE }.code)
        // Unreachable wins: the stale low-battery flag of the same device is not shown on top.
        val thermostat = faults.filter { it.deviceId == "dev-wth" }
        assertEquals(listOf(FaultKind.UNREACHABLE), thermostat.map { it.kind })
        assertEquals("grp-living", thermostat.single().roomId)
        assertNull(thermostat.single().channelIndex)
    }

    @Test
    fun mostSevereFirst() {
        val severities = faults.map { it.kind.severity }
        assertEquals(severities.sortedDescending(), severities)
    }

    @Test
    fun valveKinds() {
        assertNull(Faults.valveKind("ADAPTION_DONE"))
        assertNull(Faults.valveKind(null))
        assertEquals(FaultKind.VALVE_ADAPTING, Faults.valveKind("ADAPTION_IN_PROGRESS"))
        assertEquals(FaultKind.VALVE_TOO_TIGHT, Faults.valveKind("TOO_TIGHT"))
        assertEquals(FaultKind.VALVE_UNKNOWN, Faults.valveKind("SOMETHING_NEW"))
    }
}
