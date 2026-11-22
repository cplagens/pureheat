/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.comfort

import de.cplagens.pureheat.hmip.HeatingPeriod
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfortEditingTest {
    @Test
    fun profileDayRoundTrip() {
        val day = ComfortDay(17.5, listOf(ComfortWindow(360, 480, 21.0)))
        assertEquals(day, ComfortEditing.fromProfileDay(ComfortEditing.toProfileDay(day)))
    }

    @Test
    fun fromProfileDropsPhasesAtSetback() {
        val profile = mapOf(
            Weekday.MONDAY to ProfileDay(
                17.0,
                listOf(HeatingPeriod(300, 480, 21.0), HeatingPeriod(480, 1020, 17.0), HeatingPeriod(1020, 1380, 21.5)),
            ),
        )
        val comfort = ComfortEditing.fromProfile(profile).getValue(Weekday.MONDAY)
        assertEquals(17.0, comfort.ecoTemperature, 0.0)
        assertEquals(listOf(ComfortWindow(300, 480, 21.0), ComfortWindow(1020, 1380, 21.5)), comfort.windows)
    }

    @Test
    fun presetTakesTemperaturesFromTheDay() {
        val like = ComfortDay(16.0, listOf(ComfortWindow(600, 700, 20.0), ComfortWindow(800, 900, 22.0)))
        val day = ComfortEditing.preset(ComfortPreset.HOME_OFFICE, like)
        assertEquals(16.0, day.ecoTemperature, 0.0)
        assertEquals(listOf(ComfortWindow(390, 1350, 22.0)), day.windows)
        assertEquals(ComfortEditing.DEFAULT_COMFORT, ComfortEditing.preset(ComfortPreset.WORKDAY).windows[0].temperature, 0.0)
    }

    @Test
    fun defaultWeekAndCopy() {
        val week = ComfortEditing.defaultWeek()
        assertEquals(7, week.size)
        assertEquals(2, week.getValue(Weekday.MONDAY).windows.size)
        assertEquals(1, week.getValue(Weekday.SUNDAY).windows.size)
        val copied = ComfortEditing.copyDay(week, Weekday.SUNDAY, setOf(Weekday.FRIDAY))
        assertEquals(week[Weekday.SUNDAY], copied[Weekday.FRIDAY])
        assertTrue(ComfortEditing.copyDay(emptyMap(), Weekday.MONDAY, setOf(Weekday.FRIDAY)).isEmpty())
    }
}
