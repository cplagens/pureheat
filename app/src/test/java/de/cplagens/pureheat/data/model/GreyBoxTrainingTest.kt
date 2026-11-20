/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class GreyBoxTrainingTest {
    private val day = 24 * 3_600_000L

    /** Hourly room following the model exactly; [gainOf] lets the room change over time. */
    private fun hours(count: Int, k: Double = 0.03, p: Double = 0.5, lag: Int = 1, gainOf: (Int) -> Double = { 0.1 }): List<HourPoint> {
        val rnd = Random(7)
        val valve = List(count / 3 + 1) { listOf(0.0, 0.0, 0.3, 0.7, 1.0)[rnd.nextInt(5)] }
        val out = { h: Int -> 5 + 5 * sin(2 * PI * h / 24) }
        var t = 20.0
        return List(count) { h ->
            val point = HourPoint(h.toLong(), t, valve[h / 3], out(h))
            val lagged = if (h >= lag) valve[(h - lag) / 3] else 0.0
            t += -k * (t - out(h)) + p * lagged + gainOf(h)
            point
        }
    }

    private fun model(k: Double, p: Double, g: Double, lag: Int = 1) =
        GreyBoxModel(k, p, g, lag, 0.01, 0.01, 0.01, 0.0, 100, GreyBoxPrior())

    @Test
    fun dueWeeklyWithSlack() {
        assertTrue(GreyBoxTraining.due(null, 0))
        assertFalse(GreyBoxTraining.due(0, 6 * day))
        assertTrue(GreyBoxTraining.due(0, 7 * day - 3_600_000L))
        assertTrue(GreyBoxTraining.due(0, 8 * day))
    }

    @Test
    fun noModelBeforeSevenDays() {
        assertNull(GreyBoxTraining.train(hours(6 * 24), current = null))
        assertNull(GreyBoxTraining.train(emptyList(), current = null))
    }

    @Test
    fun firstModelAfterSevenDaysUsesAllData() {
        val r = GreyBoxTraining.train(hours(7 * 24 + 12), current = null)
        assertNotNull(r)
        r!!
        assertTrue(r.adopted)
        assertNull(r.holdoutRmse)
        assertEquals(7 * 24 + 12 - 2, r.model.n) // all consecutive steps with lag 1
    }

    @Test
    fun noRefitWhileTheTrainingPartIsTooShort() {
        assertNull(GreyBoxTraining.train(hours(7 * 24 + 12), current = model(0.03, 0.5, 0.1)))
    }

    @Test
    fun betterCandidateReplacesAPoorModel() {
        val r = GreyBoxTraining.train(hours(21 * 24), current = model(0.2, 0.0, 0.0))!!
        assertTrue(r.adopted)
        assertTrue(r.holdoutRmse!! < r.previousHoldoutRmse!!)
        assertEquals(0.03, r.model.k, 0.005)
        assertEquals(0.5, r.model.power, 0.05)
    }

    @Test
    fun candidateFromAnOddPeriodIsRejected() {
        // Two weeks with a strong extra source (e.g. guests, oven), then normal again: the model in
        // use still fits the normal last week better than a candidate learnt from the odd weeks.
        val points = hours(21 * 24, gainOf = { h -> if (h < 14 * 24) 0.4 else 0.1 })
        val r = GreyBoxTraining.train(points, current = model(0.03, 0.5, 0.1))!!
        assertFalse(r.adopted)
        assertTrue(r.holdoutRmse!! > r.previousHoldoutRmse!!)
    }

    @Test
    fun entityRoundTrip() {
        val m = GreyBoxTraining.train(hours(21 * 24), current = null)!!
        val e = m.toEntity("room", fittedAt = 42)
        assertEquals("room", e.roomId)
        assertEquals(42L, e.fittedAt)
        assertEquals(m.model, e.toModel())
    }
}
