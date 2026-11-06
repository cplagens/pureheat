/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowDetectorTest {
    private val quarter = 15 * 60_000L

    private fun s(i: Int, t: Double, valve: Double = 0.0, rh: Int? = null) = RoomSample("r", i * quarter, t, 15.0, valve, rh)

    @Test
    fun detectsOpeningAndClosing() {
        // Stable, window opened (−0.5 K in 15 min), keeps cooling, closed (air warms up again).
        val temps = listOf(23.2, 23.2, 23.2, 22.7, 22.3, 22.0, 21.8, 21.6, 21.5, 21.8, 22.0)
        val events = WindowDetector.detect(temps.mapIndexed { i, t -> s(i, t) })
        val e = events.single()
        assertEquals(2 * quarter, e.start)
        assertEquals(9 * quarter, e.end)
        assertEquals(1.7, e.drop, 1e-9)
        assertTrue(e.closeDetected)
    }

    @Test
    fun slowCoolingIsNoWindow() {
        // Normal night cooling with the window closed: ~0.2 K/h.
        val samples = (0..32).map { s(it, 23.0 - it * 0.05) }
        assertTrue(WindowDetector.detect(samples).isEmpty())
    }

    @Test
    fun stillOpenAtTheEnd() {
        val temps = listOf(22.0, 22.0, 21.4, 21.1, 20.9)
        val e = WindowDetector.detect(temps.mapIndexed { i, t -> s(i, t) }).single()
        assertNull(e.end)
    }

    @Test
    fun humidityDropHelpsWithSmallTemperatureDrop() {
        val samples = listOf(s(0, 22.0, rh = 55), s(1, 21.75, rh = 48), s(2, 21.6, rh = 47))
        val e = WindowDetector.detect(samples).single()
        assertEquals(7, e.humidityDrop)
    }

    @Test
    fun noDetectionWhileHeating() {
        // Valve open: temperature changes are the heating's business.
        val samples = listOf(s(0, 22.0, valve = 1.0), s(1, 21.4, valve = 1.0))
        assertTrue(WindowDetector.detect(samples).isEmpty())
    }

    @Test
    fun heatingStartEndsEvent() {
        val samples = listOf(s(0, 22.0), s(1, 21.4), s(2, 21.0), s(3, 20.8, valve = 1.0))
        val e = WindowDetector.detect(samples).single()
        assertEquals(2 * quarter, e.end)
        // Heating started: we don't know whether the window was closed.
        assertTrue(!e.closeDetected)
    }

    private fun m(minute: Int, t: Double, rh: Int? = null) = RoomSample("r", minute * 60_000L, t, 15.0, 0.0, rh)

    @Test
    fun sensorStepAfterLongFlatStretchIsNoWindow() {
        // Real evening (2026-11-06): the sensor reported 23.9 for ~4 h, then 23.5 at once with rising
        // humidity (slow cooling, no window). The window was really opened around minute 320.
        val samples = listOf(
            m(11, 23.9, 45), m(35, 23.9, 45), m(41, 23.9, 45), m(58, 23.9, 45), m(88, 23.9, 45),
            m(120, 23.9, 45), m(154, 23.9, 45), m(185, 23.9, 45), m(201, 23.9, 45), m(216, 23.9, 45),
            m(232, 23.9, 45), m(251, 23.5, 47), m(267, 23.5, 47), m(282, 23.5, 47), m(313, 23.5, 47),
            m(331, 23.2, 43), m(337, 23.2, 43),
        )
        val e = WindowDetector.detect(samples).single()
        assertEquals(313 * 60_000L, e.start)
        assertEquals(4, e.humidityDrop)
        assertNull(e.end)
    }

    @Test
    fun stepAfterFlatStretchWithoutHumidityIsNoWindow() {
        val samples = listOf(m(0, 22.0), m(60, 22.0), m(120, 22.0), m(135, 21.6))
        assertTrue(WindowDetector.detect(samples).isEmpty())
    }

    @Test
    fun strongDropAfterFlatStretchIsStillAWindow() {
        val samples = listOf(m(0, 22.0), m(60, 22.0), m(120, 22.0), m(135, 21.4))
        assertEquals(120 * 60_000L, WindowDetector.detect(samples).single().start)
    }

    @Test
    fun risingHumidityIsNoWindow() {
        val samples = listOf(s(0, 22.0, rh = 50), s(1, 21.4, rh = 55))
        assertTrue(WindowDetector.detect(samples).isEmpty())
    }

    @Test
    fun coolingWithoutDrierAirIsNoWindow() {
        // −0.6 K, %rH rises only because the air cooled: the absolute humidity stays the same.
        val samples = listOf(s(0, 22.0, rh = 50), s(1, 21.4, rh = 52))
        assertTrue(WindowDetector.detect(samples).isEmpty())
    }

    @Test
    fun drierAirCountsEvenIfRelativeHumidityRises() {
        // −1.2 K with 50 → 51 %rH is still ~0.5 g/m³ less water: outside air came in.
        val samples = listOf(s(0, 22.0, rh = 50), s(1, 20.8, rh = 51))
        assertEquals(0L, WindowDetector.detect(samples).single().start)
    }

    @Test
    fun absoluteHumidity() {
        assertEquals(8.65, WindowDetector.absoluteHumidity(20.0, 50), 0.05)
    }

    @Test
    fun closingSeenOverSeveralSmallRises() {
        // Dense sampling: the air warms up by 0.1 K per sample after closing.
        val temps = listOf(22.0, 22.0, 21.4, 21.2, 21.3, 21.4, 21.5)
        val e = WindowDetector.detect(temps.mapIndexed { i, t -> s(i, t) }).single()
        assertEquals(5 * quarter, e.end)
        assertTrue(e.closeDetected)
    }

    @Test
    fun windowTimesAreExcludedFromPhases() {
        val temps = listOf(23.2, 23.2, 22.7, 22.3, 22.0, 21.8, 21.9, 22.1)
        val samples = temps.mapIndexed { i, t -> s(i, t) }
        val cleaned = WindowDetector.withoutWindowTimes(samples, WindowDetector.detect(samples))
        assertEquals(null, cleaned[3].valvePosition)
        assertEquals(0.0, cleaned[0].valvePosition!!, 0.0)
    }
}
