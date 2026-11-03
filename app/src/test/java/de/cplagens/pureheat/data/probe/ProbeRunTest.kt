/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.probe

import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WindowEvent
import de.cplagens.pureheat.data.model.RoomModel
import de.cplagens.pureheat.data.model.RoomSimulator
import de.cplagens.pureheat.hmip.HeatingPeriod
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ProbeRunTest {
    private val zone = ZoneId.of("UTC")
    private val hour = 3_600_000L

    /** Monday 2026-11-02 00:00 UTC */
    private val monday = ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

    // Every day: 17 °C base, 21 °C from 06:00 to 22:00.
    private val days = Weekday.entries.associateWith { ProfileDay(17.0, listOf(HeatingPeriod(6 * 60, 22 * 60, 21.0))) }
    private val model = RoomModel(k = 0.02, power = 0.8, deadTimeHours = 1.0, coolingPhases = 5, heatingPhases = 5, kSpread = null)

    @Test
    fun setPointFollowsTheProfile() {
        assertEquals(17.0, ProbeRun.setPointAt(days, monday + 5 * hour, zone)!!, 0.0)
        assertEquals(21.0, ProbeRun.setPointAt(days, monday + 6 * hour, zone)!!, 0.0)
        assertEquals(17.0, ProbeRun.setPointAt(days, monday + 22 * hour, zone)!!, 0.0)
    }

    @Test
    fun simulatorHeatsAfterDeadTimeAndHoldsSetpoint() {
        val sim = RoomSimulator.simulate(0.02, 0.8, 1.0, 18.0, 0, 12 * hour, { 21.0 }, { 5.0 })
        // During the dead time the room still cools.
        assertTrue(sim.first { it.time == hour / 2 }.temperature < 18.0)
        // Later it reaches and holds the setpoint (within the hysteresis band).
        assertTrue(sim.last().temperature in 20.7..21.1)
    }

    @Test
    fun predictsTheMorningHeatUp() {
        val predictions = ProbeRun.predict(model, startTemperature = 19.0, now = monday, days = days, zone = zone) { 5.0 }
        val morning = predictions.single()
        assertEquals(monday + 6 * hour, morning.eventTime)
        assertEquals(21.0, morning.target, 0.0)
        assertNotNull(morning.predictedReach)
        // Room cools to ~17.4 °C by 06:00; then 1 h dead time + ln((0.8−0.244)/(0.8−0.316))/0.02 ≈ 6.9 h.
        assertEquals(17.4, morning.startTemperature, 0.2)
        assertTrue((morning.predictedReach!! - morning.eventTime) / hour in 7..8)
    }

    @Test
    fun priorIsUsedUntilTheRoomHasLearned() {
        val (m, source) = ProbeRun.modelFor(RoomModel(null, null, null, 0, 0, null))
        assertEquals(ModelSource.PRIOR, source)
        assertEquals(ProbeRun.PRIOR, m)
        assertEquals(ModelSource.FIT, ProbeRun.modelFor(model).second)
    }

    private fun samples(vararg points: Pair<Double, Double>) = points.mapIndexed { i, (t, sp) ->
        RoomSample("r", monday + 6 * hour + i * 15 * 60_000L, t, sp, 1.0)
    }

    @Test
    fun evaluationHitLateAndNotReached() {
        val prediction = HeatUpPrediction(monday + 6 * hour, 21.0, 19.0, predictedReach = monday + 6 * hour + 60 * 60_000L)
        // Reached at +60 min → hit
        val hit = samples(19.0 to 21.0, 19.5 to 21.0, 20.0 to 21.0, 20.5 to 21.0, 20.9 to 21.0)
        assertEquals(Outcome.HIT, ProbeRun.evaluate(prediction, hit, emptyList(), monday + 24 * hour)!!.outcome)
        // Reached at +120 min → late
        val late = samples(*(List(8) { 19.0 + it * 0.25 to 21.0 }.toTypedArray()), 20.9 to 21.0)
        assertEquals(Outcome.LATE, ProbeRun.evaluate(prediction, late, emptyList(), monday + 24 * hour)!!.outcome)
        // Never reached and the window is over → not reached
        val never = samples(19.0 to 21.0, 19.1 to 21.0)
        assertEquals(Outcome.NOT_REACHED, ProbeRun.evaluate(prediction, never, emptyList(), monday + 24 * hour)!!.outcome)
        // Still within the window → not evaluated yet
        assertNull(ProbeRun.evaluate(prediction, never, emptyList(), monday + 7 * hour))
    }

    @Test
    fun openWindowOrManualChangeIsNotCounted() {
        val prediction = HeatUpPrediction(monday + 6 * hour, 21.0, 19.0, monday + 7 * hour)
        val ok = samples(19.0 to 21.0, 19.5 to 21.0, 20.0 to 21.0, 20.5 to 21.0, 20.9 to 21.0)
        val window = WindowEvent(monday + 5 * hour, monday + 6 * hour + 30 * 60_000L, 21.0, 19.0, null, true)
        assertEquals(Outcome.DISTURBED, ProbeRun.evaluate(prediction, ok, listOf(window), monday + 24 * hour)!!.outcome)
        val manual = samples(19.0 to 21.0, 19.5 to 23.0, 20.0 to 23.0, 20.5 to 23.0, 20.9 to 23.0)
        assertEquals(Outcome.DISTURBED, ProbeRun.evaluate(prediction, manual, emptyList(), monday + 24 * hour)!!.outcome)
    }
}
