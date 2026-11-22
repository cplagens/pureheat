/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.comfort

import de.cplagens.pureheat.data.history.ComfortDayEntity
import de.cplagens.pureheat.data.history.ComfortWindowEntity
import de.cplagens.pureheat.hmip.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfortRowsTest {
    private val days = mapOf(
        Weekday.MONDAY to ComfortDay(17.0, listOf(ComfortWindow(1080, 1380, 21.0), ComfortWindow(360, 480, 20.5))),
        Weekday.SUNDAY to ComfortDay(18.0, emptyList()),
    )

    @Test
    fun roundTripKeepsDaysAndSortsWindows() {
        val (dayRows, windowRows) = ComfortRows.toEntities("room", days)
        assertEquals(2, dayRows.size)
        assertEquals(2, windowRows.size)
        val back = ComfortRows.fromEntities(dayRows, windowRows)
        assertEquals(listOf(360, 1080), back.getValue(Weekday.MONDAY).windows.map { it.start })
        assertEquals(17.0, back.getValue(Weekday.MONDAY).ecoTemperature, 0.0)
        assertEquals(ComfortDay(18.0, emptyList()), back[Weekday.SUNDAY])
    }

    @Test
    fun invalidAndDuplicateWindowsAreNotSaved() {
        val bad = mapOf(
            Weekday.TUESDAY to ComfortDay(
                17.0,
                listOf(
                    ComfortWindow(600, 600, 21.0),
                    ComfortWindow(-10, 60, 21.0),
                    ComfortWindow(1200, 1500, 21.0),
                    ComfortWindow(300, 400, 21.0),
                    ComfortWindow(300, 500, 22.0),
                ),
            ),
        )
        val (_, rows) = ComfortRows.toEntities("room", bad)
        assertEquals(listOf(ComfortWindowEntity("room", "TUESDAY", 300, 400, 21.0)), rows)
    }

    @Test
    fun readingIsLenient() {
        val back = ComfortRows.fromEntities(
            listOf(ComfortDayEntity("room", "MONDAY", 17.0), ComfortDayEntity("room", "FUNDAY", 16.0)),
            listOf(
                ComfortWindowEntity("room", "MONDAY", 360, 1440, 21.0),
                ComfortWindowEntity("room", "MONDAY", 900, 800, 21.0),
                // No comfort_day row for Friday: dropped.
                ComfortWindowEntity("room", "FRIDAY", 360, 480, 21.0),
            ),
        )
        assertEquals(setOf(Weekday.MONDAY), back.keys)
        assertEquals(listOf(ComfortWindow(360, 1440, 21.0)), back.getValue(Weekday.MONDAY).windows)
        assertTrue(ComfortRows.fromEntities(emptyList(), emptyList()).isEmpty())
    }
}
