/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

import de.cplagens.pureheat.data.history.HistoryAnalysis
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class RoomModelTest {
    private val quarterH = 0.25
    private val quarterMs = 15 * 60_000L

    /**
     * Simulates [days] days of a room with known k, P and dead time: setback 17 °C at night,
     * comfort 21 °C from 06:00 to 22:00, valve open whenever below setpoint − 0.2 K.
     */
    private fun simulate(k: Double, p: Double, deadH: Double, outside: Double, days: Int):
        Pair<List<RoomSample>, List<WeatherSample>> {
        val rooms = mutableListOf<RoomSample>()
        val weather = mutableListOf<WeatherSample>()
        var t = 19.0
        var openSinceH: Double? = null
        for (i in 0 until days * 96) {
            val hourOfDay = (i * quarterH) % 24
            val setPoint = if (hourOfDay in 6.0..22.0) 21.0 else 17.0
            val open = t < setPoint - 0.2 || (openSinceH != null && t < setPoint)
            if (open && openSinceH == null) openSinceH = i * quarterH
            if (!open) openSinceH = null
            rooms += RoomSample("r", i * quarterMs, t, setPoint, if (open) 1.0 else 0.0)
            weather += WeatherSample(i * quarterMs, outside)
            val heating = openSinceH != null && i * quarterH - openSinceH!! >= deadH
            t += quarterH * ((if (heating) p else 0.0) - k * (t - outside))
        }
        return rooms to weather
    }

    @Test
    fun recoversParametersFromSimulatedRoom() {
        val (rooms, weather) = simulate(k = 0.04, p = 1.2, deadH = 1.0, outside = 5.0, days = 5)
        val model = RoomModelFitter.fit(HistoryAnalysis.phases(rooms, weather))

        assertTrue("cooling phases: ${model.coolingPhases}", model.coolingPhases >= 3)
        assertTrue("heating phases: ${model.heatingPhases}", model.heatingPhases >= 3)
        assertTrue(model.confident)
        // Euler steps + the mean-temperature approximation cost a few percent.
        assertEquals(0.04, model.k!!, 0.006)
        assertEquals(1.2, model.power!!, 0.15)
        assertEquals(1.0, model.deadTimeHours!!, 0.3)
    }

    @Test
    fun predictionsFollowTheFormulas() {
        val m = RoomModel(k = 0.05, power = 1.0, deadTimeHours = 0.5, coolingPhases = 5, heatingPhases = 5, kSpread = null)
        val expectedHeat = ln((1.0 - 0.05 * (17 - 5)) / (1.0 - 0.05 * (21 - 5))) / 0.05 + 0.5
        assertEquals(expectedHeat, (m.heatUp(17.0, 21.0, 5.0) as Prediction.Hours).hours, 1e-9)
        val expectedCool = ln((21.0 - 5) / (17.0 - 5)) / 0.05
        assertEquals(expectedCool, (m.coolDown(21.0, 17.0, 5.0) as Prediction.Hours).hours, 1e-9)
    }

    @Test
    fun unreachableWhenLossExceedsPower() {
        val m = RoomModel(k = 0.1, power = 1.0, deadTimeHours = null, coolingPhases = 5, heatingPhases = 5, kSpread = null)
        // Loss at 24 °C with -5 °C outside: 0.1 * 29 = 2.9 K/h > 1.0 K/h
        assertEquals(Prediction.Unreachable, m.heatUp(18.0, 24.0, -5.0))
        assertEquals(Prediction.Unreachable, m.coolDown(20.0, 4.0, 5.0))
    }

    @Test
    fun unknownWithoutData() {
        val m = RoomModelFitter.fit(emptyList())
        assertNull(m.k)
        assertEquals(Prediction.Unknown, m.heatUp(17.0, 21.0, 5.0))
        assertTrue(!m.confident)
    }

    @Test
    fun setPointLevelsIgnoreShortManualChanges() {
        val samples = (0 until 96).map { i ->
            val sp = when {
                i == 50 -> 26.0                  // one short manual test value
                i % 96 in 24..88 -> 21.0
                else -> 17.0
            }
            RoomSample("r", i * quarterMs, 20.0, sp, 0.0)
        }
        val levels = SetPointLevels.from(samples)!!
        assertEquals(17.0, levels.setback, 0.0)
        assertEquals(21.0, levels.comfort, 0.0)
        assertNull(SetPointLevels.from(samples.map { it.copy(setPointTemperature = 21.0) }))
    }
}
