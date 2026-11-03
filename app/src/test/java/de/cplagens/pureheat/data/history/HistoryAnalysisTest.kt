/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryAnalysisTest {
    private val minute = 60_000L

    /** One sample every 15 min; [temps] and [valves] are per sample. */
    private fun samples(temps: List<Double?>, valves: List<Double?>, setPoint: Double = 26.0) =
        temps.indices.map { i -> RoomSample("room", i * 15 * minute, temps[i], setPoint, valves[i]) }

    @Test
    fun `valve open 2,5 h without temperature rise is reported`() {
        val s = samples(List(11) { 21.6 }, List(11) { 1.0 })
        val result = HistoryAnalysis.stalledHeating(s)
        assertNotNull(result)
        assertEquals(0L, result!!.openSince)
        assertEquals(0.0, result.temperatureRise, 0.001)
    }

    @Test
    fun `temperature rising is not reported`() {
        val s = samples(List(11) { 21.6 + it * 0.1 }, List(11) { 1.0 })
        assertNull(HistoryAnalysis.stalledHeating(s))
    }

    @Test
    fun `valve open for less than 2 h is not reported`() {
        val s = samples(List(8) { 21.6 }, List(8) { 1.0 })
        assertNull(HistoryAnalysis.stalledHeating(s))
    }

    @Test
    fun `room at setpoint holding temperature is not reported`() {
        val s = samples(List(11) { 22.0 }, List(11) { 0.6 }, setPoint = 22.0)
        assertNull(HistoryAnalysis.stalledHeating(s))
    }

    @Test
    fun `currently closed valve is not reported`() {
        val s = samples(List(11) { 21.6 }, List(10) { 1.0 } + 0.0)
        assertNull(HistoryAnalysis.stalledHeating(s))
    }

    @Test
    fun `open span starts after the valve was last closed`() {
        val valves = List(3) { 0.0 } + List(10) { 1.0 }
        val s = samples(List(13) { 21.6 }, valves)
        assertEquals(3 * 15 * minute, HistoryAnalysis.stalledHeating(s)!!.openSince)
    }

    @Test
    fun `gap longer than 1 h splits the open span`() {
        val before = (0 until 6).map { RoomSample("room", it * 15 * minute, 21.6, 26.0, 1.0) }
        val after = (0 until 5).map { RoomSample("room", 5 * 60 * minute + it * 15 * minute, 21.6, 26.0, 1.0) }
        // 1 h of samples after the gap is not enough on its own.
        assertNull(HistoryAnalysis.stalledHeating(before + after))
    }
}
