/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeTicksTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val end = ZonedDateTime.of(2026, 11, 6, 22, 47, 0, 0, zone).toInstant().toEpochMilli()
    private val day = 86_400_000L

    private fun local(t: Long) = Instant.ofEpochMilli(t).atZone(zone)

    @Test
    fun day24hEverySixHours() {
        val ticks = timeTicks(end - day, end, zone)
        assertEquals(4, ticks.size)
        ticks.forEach { assertEquals(0, local(it).hour % 6); assertEquals(0, local(it).minute) }
    }

    @Test
    fun sevenDaysAtMidnight() {
        val ticks = timeTicks(end - 7 * day, end, zone)
        assertEquals(7, ticks.size)
        ticks.forEach { assertEquals(0, local(it).hour) }
    }

    @Test
    fun sixtyDaysWeeklyOnMondays() {
        // Includes the switch to winter time (2026-10-25): ticks stay at local midnight.
        val ticks = timeTicks(end - 60 * day, end, zone)
        assertTrue("${ticks.size}", ticks.size in 8..9)
        ticks.forEach {
            assertEquals(DayOfWeek.MONDAY, local(it).dayOfWeek)
            assertEquals(0, local(it).hour)
        }
    }
}
