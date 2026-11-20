/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCurveTest {
    private val levels = SetPointLevels(setback = 18.0, comfort = 21.0)
    private val hour = 3_600_000L

    private fun greyBox(k: Double = 0.03, p: Double = 1.0, g: Double = 0.0, lag: Int = 1) = GreyBoxModel(
        k = k, power = p, gain = g, lagHours = lag, kSe = 0.0, powerSe = 0.0, gainSe = 0.0,
        rmse = 0.0, n = 100, prior = GreyBoxPrior(),
    )

    private fun at(curve: List<SimPoint>, h: Double) = curve.minBy { kotlin.math.abs(it.time - (h * hour).toLong()) }

    @Test
    fun setPointFollowsTheCycle() {
        assertEquals(18.0, ModelCurve.setPoint(levels, 1.0), 0.0)
        assertEquals(21.0, ModelCurve.setPoint(levels, 2.0), 0.0)
        assertEquals(21.0, ModelCurve.setPoint(levels, 13.9), 0.0)
        assertEquals(18.0, ModelCurve.setPoint(levels, 14.0), 0.0)
    }

    @Test
    fun greyBoxHeatsUpDuringComfortAndCoolsAfter() {
        val curve = ModelCurve.greyBox(greyBox(), levels, outside = 5.0, start = 0)
        assertEquals(ModelCurve.HOURS * 12 + 1, curve.size)
        // Before comfort the room sinks a little below setback (valve opens only 0.2 K below it).
        assertTrue(at(curve, 2.0).temperature < 18.0)
        // Close to comfort by the end of the 12 h comfort phase.
        assertEquals(21.0, at(curve, 13.5).temperature, 0.3)
        // Cooling after the comfort phase.
        assertTrue(at(curve, 20.0).temperature < at(curve, 14.0).temperature)
    }

    @Test
    fun otherHeatSourcesKeepTheRoomWarmer() {
        val without = ModelCurve.greyBox(greyBox(g = 0.0), levels, 5.0, 0)
        val with = ModelCurve.greyBox(greyBox(g = 0.2), levels, 5.0, 0)
        assertTrue(at(with, 20.0).temperature > at(without, 20.0).temperature)
    }

    @Test
    fun physicsOnlyOnceConfident() {
        val learning = RoomModel(k = 0.03, power = 0.6, deadTimeHours = 1.0, coolingPhases = 0, heatingPhases = 0, kSpread = null)
        assertNull(ModelCurve.physics(learning, levels, 5.0, 0))
        val confident = learning.copy(coolingPhases = RoomModel.MIN_PHASES, heatingPhases = RoomModel.MIN_PHASES)
        assertNotNull(ModelCurve.physics(confident, levels, 5.0, 0))
    }
}
