/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.profile

import de.cplagens.pureheat.hmip.HeatingPeriod
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.Weekday
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFromTextTest {
    private val current = Weekday.entries.associateWith { ProfileDay(16.0, listOf(HeatingPeriod(7 * 60, 21 * 60, 20.0))) }

    private fun build(text: String) = ProfileFromText.apply(current, ProfileFromText.fromKeywords(text), 5.0, 30.0)

    @Test
    fun germanWeekdaysWeekendAndBase() {
        val days = build("Werktags 6 bis 8 Uhr und 17-22 Uhr 22 Grad. Am Wochenende von 8 bis 23 Uhr 21,5 °C. Sonst 18 Grad.")
        assertEquals(
            listOf(HeatingPeriod(360, 480, 22.0), HeatingPeriod(1020, 1320, 22.0)),
            days.getValue(Weekday.WEDNESDAY).periods,
        )
        assertEquals(listOf(HeatingPeriod(480, 1380, 21.5)), days.getValue(Weekday.SUNDAY).periods)
        assertTrue(Weekday.entries.all { days.getValue(it).baseTemperature == 18.0 })
    }

    @Test
    fun dayRangesAndCommas() {
        val days = build("Mo-Do 6:30-8:00 Uhr 21 Grad, Fr 6:30-8:00 und 14-23 Uhr 21 Grad, nachts 17 Grad")
        assertEquals(listOf(HeatingPeriod(390, 480, 21.0)), days.getValue(Weekday.THURSDAY).periods)
        assertEquals(listOf(HeatingPeriod(390, 480, 21.0), HeatingPeriod(840, 1380, 21.0)), days.getValue(Weekday.FRIDAY).periods)
        // "nachts" refers to all days of the sentence (Mo–Fr), not to the weekend.
        assertEquals(17.0, days.getValue(Weekday.MONDAY).baseTemperature, 0.0)
        assertEquals(17.0, days.getValue(Weekday.FRIDAY).baseTemperature, 0.0)
        assertEquals(16.0, days.getValue(Weekday.SUNDAY).baseTemperature, 0.0)
    }

    @Test
    fun englishWithAmPm() {
        val days = build("On weekdays heat from 6am to 8am and 5-10pm at 21 degrees. Weekends 8am-11pm 20.5 degrees. Otherwise 17 degrees.")
        assertEquals(
            listOf(HeatingPeriod(360, 480, 21.0), HeatingPeriod(1020, 1320, 21.0)),
            days.getValue(Weekday.MONDAY).periods,
        )
        assertEquals(listOf(HeatingPeriod(480, 1380, 20.5)), days.getValue(Weekday.SATURDAY).periods)
        assertEquals(17.0, days.getValue(Weekday.FRIDAY).baseTemperature, 0.0)
    }

    @Test
    fun temperatureRangeIsNotATime() {
        // "20-22 Grad" must not become a phase from 20:00 to 02:00 or 20:00 to 22:00.
        assertTrue(ProfileFromText.fromKeywords("Wohnzimmer zwischen 20-22 Grad").isEmpty())
    }

    @Test
    fun unmentionedDaysStay() {
        val days = build("Samstag 9-12 Uhr 22 Grad")
        assertEquals(current.getValue(Weekday.MONDAY), days.getValue(Weekday.MONDAY))
        assertEquals(listOf(HeatingPeriod(540, 720, 22.0)), days.getValue(Weekday.SATURDAY).periods)
        assertEquals(16.0, days.getValue(Weekday.SATURDAY).baseTemperature, 0.0)
    }

    @Test
    fun cleanSnapsClampsAndRemovesOverlaps() {
        val cleaned = ProfileFromText.clean(
            listOf(HeatingPeriod(370, 485, 22.3), HeatingPeriod(470, 600, 40.0), HeatingPeriod(700, 700, 20.0)),
            5.0, 30.0,
        )
        assertEquals(listOf(HeatingPeriod(375, 480, 22.5), HeatingPeriod(480, 600, 30.0)), cleaned)
    }

    @Test
    fun modelJson() {
        val o = Json.parseToJsonElement(
            """{"rules":[{"days":["monday","TUESDAY","Funday"],"base":18,"periods":[{"start":"06:00","end":"08:00","temperature":22},{"start":"bad","end":"x"}]},
               {"days":["SATURDAY"],"periods":[{"start":"22:00","end":"00:00","temperature":20}]}]}""",
        ).jsonObject
        val rules = ProfileFromText.fromModelJson(o)
        assertEquals(setOf(Weekday.MONDAY, Weekday.TUESDAY), rules[0].days)
        assertEquals(listOf(HeatingPeriod(360, 480, 22.0)), rules[0].periods)
        assertEquals(listOf(HeatingPeriod(1320, 1440, 20.0)), rules[1].periods)
    }
}
