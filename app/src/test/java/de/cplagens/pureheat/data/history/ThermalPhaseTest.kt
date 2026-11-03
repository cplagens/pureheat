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

class ThermalPhaseTest {
    private val quarter = 15 * 60_000L

    private fun room(i: Int, temp: Double?, valve: Double?) = RoomSample("r", i * quarter, temp, 22.0, valve)

    @Test
    fun splitsIntoHeatingAndCoolingPhases() {
        // 2 h valve open, warming 20 → 21; then 2 h closed, cooling 21 → 20.6
        val samples = (0..8).map { room(it, 20.0 + it * 0.125, 1.0) } +
            (9..17).map { room(it, 21.0 - (it - 9) * 0.05, 0.0) }
        val weather = (0..17).map { WeatherSample(it * quarter, 8.0 + it * 0.1) }

        val phases = HistoryAnalysis.phases(samples, weather)

        assertEquals(listOf(PhaseKind.HEATING, PhaseKind.COOLING), phases.map { it.kind })
        val heat = phases[0]
        assertEquals(2.0, heat.hours, 0.001)
        assertEquals(0.5, heat.ratePerHour, 0.001)
        assertEquals(8.4, heat.meanOutside!!, 0.001)
        assertTrue(phases[1].ratePerHour < 0)
    }

    @Test
    fun shortPhasesAndPartialValveAreIgnored() {
        // 45 min open (too short), then 2 h at 30 % (neither heating nor cooling)
        val samples = (0..3).map { room(it, 20.0, 1.0) } + (4..12).map { room(it, 20.0, 0.3) }
        assertTrue(HistoryAnalysis.phases(samples, emptyList()).isEmpty())
    }

    @Test
    fun gapEndsPhase() {
        val before = (0..2).map { room(it, 20.0, 0.0) }          // 30 min
        val after = (10..13).map { room(it, 19.8, 0.0) }         // 45 min, after a 2 h gap
        assertTrue(HistoryAnalysis.phases(before + after, emptyList()).isEmpty())
    }

    @Test
    fun missingOutsideTemperatureGivesNullMean() {
        val samples = (0..8).map { room(it, 21.0 - it * 0.05, 0.0) }
        assertNull(HistoryAnalysis.phases(samples, emptyList()).single().meanOutside)
    }
}
