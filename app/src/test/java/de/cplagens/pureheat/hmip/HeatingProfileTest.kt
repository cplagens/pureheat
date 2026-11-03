/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.hmip

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatingProfileTest {

    // Anonymized, in the shape returned by the real cloud (setback temperature is "baseValue").
    private val sample = """
    {
      "id": "p1", "groupId": "grp-1", "homeId": "home-1", "type": "HEATING_COOLING",
      "profileDays": {
        "MONDAY": { "baseValue": 17.0, "periods": [
          { "starttime": "06:00", "endtime": "08:00", "value": 21.0 },
          { "starttime": "17:00", "endtime": "22:00", "value": 21.5 } ] },
        "SUNDAY": { "baseValue": 18.0, "periods": [
          { "starttime": "08:00", "endtime": "24:00", "value": 22.0 } ], "unknownKey": 1 }
      }
    }
    """.trimIndent()

    private fun parse() = HeatingProfileCodec.parse(Json.parseToJsonElement(sample).jsonObject, "grp-1", "PROFILE_1")

    @Test
    fun parsesDaysAndPeriods() {
        val p = parse()
        assertEquals(setOf(Weekday.MONDAY, Weekday.SUNDAY), p.days.keys)
        val mon = p.days.getValue(Weekday.MONDAY)
        assertEquals(17.0, mon.baseTemperature, 0.0)
        assertEquals(HeatingPeriod(360, 480, 21.0), mon.periods.first())
        assertEquals(1440, p.days.getValue(Weekday.SUNDAY).periods.single().end)
    }

    @Test
    fun encodeKeepsUnknownFieldsAndWritesEdits() {
        val p = parse()
        val edited = p.copy(days = p.days + (Weekday.SUNDAY to ProfileDay(16.5, listOf(HeatingPeriod(450, 1320, 20.5)))))
        val json = HeatingProfileCodec.encode(edited)
        assertEquals(JsonPrimitive("HEATING_COOLING"), json["type"])
        val sunday = json["profileDays"]!!.jsonObject["SUNDAY"]!!.jsonObject
        assertEquals(JsonPrimitive(1), sunday["unknownKey"])
        // Written back under the key the cloud used, no second key added.
        assertEquals(JsonPrimitive(16.5), sunday["baseValue"])
        assertEquals(null, sunday["baseTemperature"])
        val period = sunday["periods"]!!.jsonArray.single() as JsonObject
        assertEquals("07:30", (period["starttime"] as JsonPrimitive).content)
        assertEquals("22:00", (period["endtime"] as JsonPrimitive).content)
        // Round trip
        assertEquals(edited.days, HeatingProfileCodec.parse(json, "grp-1", "PROFILE_1").days)
    }

    @Test
    fun readsDocumentedBaseTemperatureKeyToo() {
        val legacy = Json.parseToJsonElement(
            """{"profileDays": {"MONDAY": {"baseTemperature": 16.0, "periods": []}}}""",
        ).jsonObject
        val p = HeatingProfileCodec.parse(legacy, "g", "PROFILE_1")
        assertEquals(16.0, p.days.getValue(Weekday.MONDAY).baseTemperature, 0.0)
        val monday = HeatingProfileCodec.encode(p)["profileDays"]!!.jsonObject["MONDAY"]!!.jsonObject
        assertEquals(JsonPrimitive(16.0), monday["baseTemperature"])
        assertEquals(null, monday["baseValue"])
    }

    @Test
    fun moveEdgeStaysBetweenNeighboursAndSnaps() {
        val periods = listOf(HeatingPeriod(360, 480, 21.0), HeatingPeriod(1020, 1320, 21.5))
        // End of the first phase can't run into the second one.
        assertEquals(1020, ProfileEditing.moveEdge(periods, 0, false, 1200)[0].end)
        // Start can't pass its own end (min. 15 min).
        assertEquals(465, ProfileEditing.moveEdge(periods, 0, true, 600)[0].start)
        // Snaps to 15 min.
        assertEquals(375, ProfileEditing.moveEdge(periods, 0, true, 371)[0].start)
    }

    @Test
    fun addPeriodUsesLargestGapAndRespectsLimit() {
        val day = ProfileDay(17.0, listOf(HeatingPeriod(360, 480, 21.0), HeatingPeriod(1020, 1320, 21.5)))
        val added = ProfileEditing.addPeriod(day, 21.0)
        assertNotNull(added)
        val new = added!!.periods[1]
        assertTrue(new.start >= 480 && new.end <= 1020)
        assertEquals(120, new.end - new.start)

        val full = ProfileDay(17.0, (0 until ProfileEditing.MAX_PERIODS).map { HeatingPeriod(it * 120, it * 120 + 60, 20.0) })
        assertNull(ProfileEditing.addPeriod(full, 21.0))
    }

    @Test
    fun diffDescribesChangedDaysOnly() {
        val p = parse()
        val edited = ProfileEditing.copyDay(p, Weekday.MONDAY, setOf(Weekday.SUNDAY))
        val change = ProfileEditing.diff(p, edited).single()
        assertEquals(Weekday.SUNDAY, change.day)
        assertTrue(change.periodsChanged)
        assertTrue(change.baseChanged)
        assertEquals(18.0, change.before!!.baseTemperature, 0.0)
        assertEquals(17.0, change.after.baseTemperature, 0.0)
    }
}
