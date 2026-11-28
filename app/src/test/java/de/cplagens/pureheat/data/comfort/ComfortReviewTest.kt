/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.comfort

import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import de.cplagens.pureheat.data.model.SimParams
import de.cplagens.pureheat.hmip.HeatingPeriod
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ComfortReviewTest {
    private val params = SimParams(k = 0.04, power = 1.5, deadTimeHours = 1.0)
    private val zone = ZoneOffset.UTC
    private val comfortDay = ComfortDay(17.0, listOf(ComfortWindow(6 * 60, 22 * 60, 21.0)))
    private val comfort = Weekday.entries.associateWith { comfortDay }
    /** Switches exactly at the window times: the room is late every morning. */
    private val naive = Weekday.entries.associateWith { ProfileDay(17.0, listOf(HeatingPeriod(6 * 60, 22 * 60, 21.0))) }
    private val now = ZonedDateTime.of(2026, 11, 22, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val weather = (0..7 * 24).map { WeatherSample(now - it * 3_600_000L, 5.0) }.reversed()
    private val room = listOf(RoomSample("r", now - ComfortReview.WEEK_MS, 19.0, 17.0, 0.0))

    @Test
    fun suggestionBeatsTheNaiveProfileOverTheLastWeek() {
        val s = ComfortReview.lastWeek(naive, comfort, params, weather, room, now, zone)!!
        assertTrue("suggested ${s.comparison.suggested.comfortKept}", s.comparison.suggested.comfortKept >= 0.95)
        assertTrue(s.comparison.current.comfortKept < s.comparison.suggested.comfortKept)
        assertEquals(Weekday.entries.toSet(), s.changedDays)
        assertEquals(0, s.unreachable)
        assertFalse(s.outsideEstimated)
        assertEquals(now - ComfortReview.WEEK_MS, s.start)
        assertEquals(19.0, s.comparison.current.curve.first().temperature, 0.01)
    }

    /** Measured 2026-11-22: weak heating, but other sources keep the room at ~24 °C. */
    @Test
    fun roomWarmedByOtherSourcesHasNoMissedWindows() {
        val slow = SimParams(k = 0.0071, power = 0.092, deadTimeHours = 4.0, gain = 0.125)
        val day = ComfortDay(16.0, listOf(ComfortWindow(330, 465, 23.0), ComfortWindow(465, 1320, 21.5)))
        val warmRoom = listOf(RoomSample("r", now - ComfortReview.WEEK_MS, 24.3, 16.0, 0.0))
        val warmWeather = weather.map { it.copy(outsideTemperature = 8.0) } // equilibrium 25.6 °C
        val s = ComfortReview.lastWeek(naive, Weekday.entries.associateWith { day }, slow, warmWeather, warmRoom, now, zone)!!
        assertEquals(0, s.unreachable)
        assertEquals(1.0, s.comparison.suggested.comfortKept, 1e-9)
    }

    @Test
    fun weakRoomMissesWindowStarts() {
        val weak = SimParams(k = 0.04, power = 0.4, deadTimeHours = 1.0) // equilibrium 15 °C at 5 °C outside
        val s = ComfortReview.lastWeek(naive, comfort, weak, weather, room, now, zone)!!
        assertTrue("missed ${s.unreachable}", s.unreachable >= 6)
    }

    @Test
    fun daysWithoutComfortWindowsKeepTheCurrentProfile() {
        val onlyMonday = mapOf(Weekday.MONDAY to comfortDay)
        val s = ComfortReview.lastWeek(naive, onlyMonday, params, weather, room, now, zone)!!
        assertEquals(setOf(Weekday.MONDAY), s.changedDays)
        assertEquals(naive[Weekday.TUESDAY], s.profile[Weekday.TUESDAY])
    }

    @Test
    fun withoutWeatherTheFallbackIsUsedAndFlagged() {
        val s = ComfortReview.lastWeek(naive, comfort, params, emptyList(), emptyList(), now, zone)!!
        assertTrue(s.outsideEstimated)
    }

    @Test
    fun nothingToCompareWithoutWindowsOrProfile() {
        assertNull(ComfortReview.lastWeek(naive, Weekday.entries.associateWith { ComfortDay(17.0, emptyList()) }, params, weather, room, now, zone))
        assertNull(ComfortReview.lastWeek(emptyMap(), comfort, params, weather, room, now, zone))
    }

    @Test
    fun lastWeekTimeFallsOnTheWeekdayInsideTheWeek() {
        val start = now - ComfortReview.WEEK_MS // Sunday 2026-11-15 12:00
        for (day in Weekday.entries) for (minute in listOf(0, 11 * 60, 13 * 60)) {
            val t = ComfortReview.lastWeekTime(day, minute, start, zone)
            assertTrue("$day $minute", t >= start && t < now)
            val local = Instant.ofEpochMilli(t).atZone(zone)
            assertEquals(day.ordinal, local.dayOfWeek.value - 1)
            assertEquals(minute, local.hour * 60 + local.minute)
        }
    }
}
