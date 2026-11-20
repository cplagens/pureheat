/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class GreyBoxFitterTest {
    private val stepH = 0.25
    private val stepMs = 15 * 60_000L

    /**
     * [days] days of a room following dT/dt = −k(T − Tₐ) + P·valve(t − lag) + g, sampled every
     * 15 min with 0.1 K resolution. The valve changes every 3 h between partial openings.
     */
    private fun simulate(
        k: Double, p: Double, g: Double, lagH: Double, days: Int,
        valveOf: (Int) -> Double = run {
            val rnd = Random(1)
            val blocks = List(days * 8 + 1) { listOf(0.0, 0.0, 0.3, 0.7, 1.0)[rnd.nextInt(5)] }
            ({ i: Int -> blocks[i / 12] })
        },
        outsideOf: (Double) -> Double = { h -> 5 + 5 * sin(2 * PI * h / 24) },
    ): Pair<List<RoomSample>, List<WeatherSample>> {
        val rooms = mutableListOf<RoomSample>()
        val weather = mutableListOf<WeatherSample>()
        val lagSteps = (lagH / stepH).roundToInt()
        var t = 20.0
        for (i in 0 until days * 96) {
            val h = i * stepH
            val out = outsideOf(h)
            rooms += RoomSample("r", i * stepMs, (t * 10).roundToInt() / 10.0, 21.0, valveOf(i))
            weather += WeatherSample(i * stepMs, out)
            val lagged = if (i >= lagSteps) valveOf(i - lagSteps) else 0.0
            t += stepH * (-k * (t - out) + p * lagged + g)
        }
        return rooms to weather
    }

    @Test
    fun recoversParametersWhenTheValveMoves() {
        val (rooms, weather) = simulate(k = 0.03, p = 0.5, g = 0.1, lagH = 1.0, days = 14)
        val m = GreyBoxFitter.fit(GreyBoxFitter.hourly(rooms, weather))
        assertNotNull(m)
        m!!
        assertEquals(1, m.lagHours)
        assertEquals(0.03, m.k, 0.008)
        assertEquals(0.5, m.power, 0.1)
        assertEquals(0.1, m.gain, 0.06)
        assertTrue(m.kMeasured && m.powerMeasured && m.gainMeasured)
        assertTrue(m.rmse < 0.1)
    }

    @Test
    fun powerStaysAtThePriorWhenTheValveNeverOpens() {
        val (rooms, weather) = simulate(
            k = 0.02, p = 0.5, g = 0.15, lagH = 1.0, days = 10, valveOf = { 0.0 }, outsideOf = { 5.0 },
        )
        val m = GreyBoxFitter.fit(GreyBoxFitter.hourly(rooms, weather), GreyBoxPrior(power = 0.4))!!
        assertEquals(0.4, m.power, 0.05)
        assertFalse(m.powerMeasured)
    }

    @Test
    fun tooFewHoursGiveNoModel() {
        val (rooms, weather) = simulate(k = 0.03, p = 0.5, g = 0.1, lagH = 1.0, days = 1)
        assertNull(GreyBoxFitter.fit(GreyBoxFitter.hourly(rooms, weather)))
    }

    @Test
    fun blankedSamplesDropTheirHour() {
        val (rooms, weather) = simulate(k = 0.03, p = 0.5, g = 0.1, lagH = 1.0, days = 1)
        // Window open in hour 5: WindowDetection.withoutWindowTimes nulls the valve.
        val blanked = rooms.map { if (it.timestamp / 3_600_000L == 5L) it.copy(valvePosition = null) else it }
        val hours = GreyBoxFitter.hourly(blanked, weather).map { it.hour }
        assertEquals(23, hours.size)
        assertFalse(5L in hours)
    }

    @Test
    fun holdOutRmseIsLowerForTheTrueModel() {
        val (rooms, weather) = simulate(k = 0.03, p = 0.5, g = 0.1, lagH = 1.0, days = 14)
        val points = GreyBoxFitter.hourly(rooms, weather)
        val train = points.filter { it.hour < 7 * 24 }
        val test = points.filter { it.hour >= 7 * 24 }
        val fitted = GreyBoxFitter.fit(train)!!
        val wrong = fitted.copy(power = 0.2, gain = 0.0)
        assertTrue(GreyBoxFitter.rmse(fitted, test)!! < GreyBoxFitter.rmse(wrong, test)!!)
    }

    @Test
    fun predictionsIncludeOtherHeatSources() {
        val m = GreyBoxModel(
            k = 0.02, power = 0.5, gain = 0.1, lagHours = 1, kSe = 0.0, powerSe = 0.0, gainSe = 0.0,
            rmse = 0.0, n = 100, prior = GreyBoxPrior(),
        )
        // Closed valve: equilibrium Tₐ + g/k = 5 + 5 = 10 °C.
        val cool = m.coolDown(20.0, 15.0, 5.0) as Prediction.Hours
        assertEquals(ln(10.0 / 5.0) / 0.02, cool.hours, 1e-9)
        assertEquals(Prediction.Unreachable, m.coolDown(20.0, 9.0, 5.0))
        // Open valve: equilibrium 5 + 0.6 / 0.02 = 35 °C, plus 1 h lag.
        val heat = m.heatUp(17.0, 21.0, 5.0) as Prediction.Hours
        assertEquals(ln(18.0 / 14.0) / 0.02 + 1, heat.hours, 1e-9)
        assertEquals(Prediction.Unreachable, m.heatUp(17.0, 36.0, 5.0))
    }
}
