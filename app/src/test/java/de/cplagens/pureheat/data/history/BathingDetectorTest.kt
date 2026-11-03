/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BathingDetectorTest {
    private val min = 60_000L

    private fun sample(minute: Long, temp: Double, humidity: Int, valve: Double = 0.0) =
        RoomSample("bath", minute * min, temp, 22.0, valve, humidity)

    @Test
    fun showerFromRealPattern() {
        // Like the real evening shower: +15 %rH in 5 min, +0.3 K, back to normal within ~1.5 h.
        val s = listOf(
            sample(0, 25.0, 50),
            sample(5, 25.3, 65),
            sample(20, 25.4, 60),
            sample(50, 25.2, 55),
            sample(80, 25.1, 52),
            sample(110, 25.0, 50),
        )
        val e = BathingDetector.detect(s).single()
        assertEquals(150_000L, e.start)
        assertEquals(80 * min, e.end)
        assertEquals(15, e.humidityRise)
        assertEquals(0.4, e.temperatureRise!!, 0.01)
    }

    @Test
    fun slowRiseOrFallingTemperatureIsNoShower() {
        // +5 %rH over 60 min = 5 %rH/h: too slow (e.g. people in the room, weather).
        val slow = listOf(sample(0, 22.0, 45), sample(60, 22.0, 50))
        assertTrue(BathingDetector.detect(slow).isEmpty())
        // Humidity up while the temperature falls fast: rather a window / rain than hot water.
        val window = listOf(sample(0, 22.0, 45), sample(10, 21.5, 52))
        assertTrue(BathingDetector.detect(window).isEmpty())
    }

    @Test
    fun twoShowersAreTwoEvents() {
        val s = listOf(
            sample(0, 24.0, 46), sample(15, 24.3, 51), sample(45, 24.2, 47),
            sample(300, 24.0, 45), sample(305, 24.2, 58), sample(320, 24.2, 54), sample(380, 24.0, 47),
        )
        val events = BathingDetector.detect(s)
        assertEquals(2, events.size)
        assertEquals(45 * min, events[0].end)
    }

    @Test
    fun bathingTimesAreLeftOutOfTheModel() {
        val s = listOf(sample(0, 25.0, 50, 1.0), sample(5, 25.3, 65, 1.0), sample(60, 25.0, 50, 1.0), sample(200, 25.0, 50, 1.0))
        val clean = BathingDetector.withoutBathingTimes(s, BathingDetector.detect(s))
        assertEquals(listOf(1.0, null, null, 1.0), clean.map { it.valvePosition })
    }
}
