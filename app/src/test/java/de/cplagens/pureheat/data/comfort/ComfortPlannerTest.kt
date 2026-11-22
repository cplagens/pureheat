/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.comfort

import de.cplagens.pureheat.data.model.SimParams
import de.cplagens.pureheat.hmip.HeatingPeriod
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.ProfileEditing
import de.cplagens.pureheat.hmip.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ComfortPlannerTest {
    private val params = SimParams(k = 0.04, power = 1.5, deadTimeHours = 1.0)
    private val daily = ComfortDay(ecoTemperature = 17.0, windows = listOf(ComfortWindow(6 * 60, 22 * 60, 21.0)))
    private val week = Weekday.entries.associateWith { daily }
    private val zone = ZoneOffset.UTC
    private val monday = ZonedDateTime.of(2026, 11, 16, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val weekMs = 7 * 24 * 3_600_000L

    private fun planAt(outside: Double) = ComfortPlanner.planWeek(week, params) { _, _ -> outside }

    @Test
    fun suggestionStartsBeforeTheWindowOnTheGrid() {
        val day = planAt(5.0).getValue(Weekday.TUESDAY)
        val p = day.profileDay.periods.single()
        assertTrue("starts before 06:00: ${p.start}", p.start < 6 * 60)
        assertTrue("at most MAX_LEAD earlier: ${p.start}", p.start >= 6 * 60 - (ComfortPlanner.MAX_LEAD_HOURS * 60).toInt() - 15)
        assertTrue("stops before 22:00: ${p.end}", p.end in (6 * 60 + 1)..(22 * 60))
        assertEquals(0, p.start % ProfileEditing.STEP_MINUTES)
        assertEquals(0, p.end % ProfileEditing.STEP_MINUTES)
        assertEquals(17.0, day.profileDay.baseTemperature, 0.0)
        assertTrue(day.windows.single().reachable)
    }

    @Test
    fun startsEarlierWhenColderOutside() {
        val cold = planAt(-5.0).getValue(Weekday.WEDNESDAY).profileDay.periods.single().start
        val mild = planAt(12.0).getValue(Weekday.WEDNESDAY).profileDay.periods.single().start
        assertTrue("cold $cold < mild $mild", cold < mild)
    }

    @Test
    fun suggestionKeepsComfortBetterThanSwitchingAtTheWindow() {
        val suggested = planAt(5.0).mapValues { it.value.profileDay }
        val naive = week.mapValues { (_, c) -> ProfileDay(c.ecoTemperature, c.windows.map { HeatingPeriod(it.start, it.end, it.temperature) }) }
        val cmp = ComfortEvaluation.compare(naive, suggested, week, params, 17.0, monday, monday + weekMs, { 5.0 }, zone)
        assertTrue("suggested ${cmp.suggested.comfortKept}", cmp.suggested.comfortKept >= 0.95)
        assertTrue("naive ${cmp.current.comfortKept}", cmp.current.comfortKept < cmp.suggested.comfortKept)
    }

    @Test
    fun outOfReachTargetUsesTheLongestLead() {
        val weak = SimParams(k = 0.04, power = 0.4, deadTimeHours = 1.0) // equilibrium 15 °C at 5 °C outside
        val day = ComfortPlanner.plan(Weekday.MONDAY, week, weak) { _, _ -> 5.0 }!!
        val w = day.windows.single()
        assertFalse(w.reachable)
        assertEquals(ComfortPlanner.MAX_LEAD_HOURS, w.leadHours, 1e-9)
    }

    /** Measured 2026-11-22: very slow room, weak heating, much heat from other sources. */
    @Test
    fun slowRoomWithLargeGainIsNotWorseThanSwitchingAtTheWindow() {
        val slow = SimParams(k = 0.0071, power = 0.092, deadTimeHours = 4.0, gain = 0.125)
        val day = ComfortDay(16.0, listOf(ComfortWindow(330, 465, 23.0), ComfortWindow(465, 1320, 21.5)))
        val comfort = Weekday.entries.associateWith { day }
        val suggested = ComfortPlanner.planWeek(comfort, slow) { _, _ -> 2.0 }.mapValues { it.value.profileDay }
        val naive = comfort.mapValues { (_, c) -> ProfileDay(c.ecoTemperature, c.windows.map { HeatingPeriod(it.start, it.end, it.temperature) }) }
        val cmp = ComfortEvaluation.compare(naive, suggested, comfort, slow, 22.0, monday, monday + weekMs, { 2.0 }, zone)
        assertTrue("suggested ${cmp.suggested.comfortKept} vs ${cmp.current.comfortKept}", cmp.suggested.comfortKept >= cmp.current.comfortKept - 0.02)
        // Out of reach in the morning: heat right through the window, no early stop.
        assertTrue(suggested.getValue(Weekday.MONDAY).periods.any { it.start < 330 && it.end >= 465 })
    }

    @Test
    fun backToBackWindowsBecomeOnePeriod() {
        val split = ComfortDay(17.0, listOf(ComfortWindow(6 * 60, 9 * 60, 21.0), ComfortWindow(9 * 60 + 30, 12 * 60, 22.0)))
        val day = ComfortPlanner.plan(Weekday.MONDAY, mapOf(Weekday.MONDAY to split), params) { _, _ -> 5.0 }!!
        val p = day.profileDay.periods.single()
        assertEquals(22.0, p.temperature, 0.0)
        assertTrue(p.start < 6 * 60 && p.end <= 12 * 60)
    }

    @Test
    fun limitBridgesTheSmallestGaps() {
        val many = (0 until 8).map { HeatingPeriod(it * 120, it * 120 + 60, 20.0) }.toMutableList()
        many[3] = HeatingPeriod(360, 470, 21.0) // gap to the next one only 10 min
        val limited = ComfortPlanner.limit(ComfortPlanner.merge(many))
        assertEquals(ProfileEditing.MAX_PERIODS, limited.size)
        assertTrue(limited.any { it.start == 360 && it.end == 540 && it.temperature == 21.0 })
    }

    @Test
    fun mergeJoinsOverlapsWithTheHigherTemperature() {
        val merged = ComfortPlanner.merge(listOf(HeatingPeriod(300, 600, 21.0), HeatingPeriod(540, 700, 22.5), HeatingPeriod(800, 900, 20.0)))
        assertEquals(listOf(HeatingPeriod(300, 700, 22.5), HeatingPeriod(800, 900, 20.0)), merged)
    }

    @Test
    fun closedFormsMatchTheExponentialModel() {
        val h = ComfortPlanner.heatUpHours(17.0, 21.0, params, 5.0)!!
        // eq = 5 + 1.5 / 0.04 = 42.5 °C
        assertEquals(1.0 + kotlin.math.ln(25.5 / 21.5) / 0.04, h, 1e-9)
        val c = ComfortPlanner.coolHoursFor(21.0, 20.5, params, 5.0)
        assertEquals(kotlin.math.ln(16.0 / 15.5) / 0.04, c, 1e-9)
        assertEquals(17.0, ComfortPlanner.cooledTo(21.0, 100.0, params, 5.0, 17.0), 1e-9)
    }
}
