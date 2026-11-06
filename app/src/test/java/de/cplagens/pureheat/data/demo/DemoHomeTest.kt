/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.demo

import de.cplagens.pureheat.data.history.BathingDetector
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import de.cplagens.pureheat.data.history.WindowDetector
import de.cplagens.pureheat.data.model.RoomInsight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** The demo flat must show what the app is about: a learned model, an aired bedroom, showers. */
class DemoHomeTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = 1_793_000_000_000L // a fixed day in autumn 2026
    private val start = now - 21 * 86_400_000L
    private val interval = 15 * 60_000L

    private fun samples(room: DemoHome.DemoRoom): List<RoomSample> =
        DemoHome.simulate(room, DemoHome.defaultProfile(room.kind), start - 2 * 86_400_000L, now, zone)
            .filter { it.time >= start && it.time % interval == 0L }
            .map { RoomSample(room.id, it.time, Math.round(it.temperature * 10) / 10.0, it.setPoint, it.valve, it.humidity) }

    private val weather = (start..now step interval).map { WeatherSample(it, DemoHome.outsideAt(it, zone)) }

    @Test
    fun `room model becomes confident for every room`() {
        val all = DemoHome.rooms.flatMap { samples(it) }.sortedBy { it.timestamp }
        val insights = RoomInsight.forAllRooms(all, weather, now)
        DemoHome.rooms.forEach { room ->
            val model = insights.getValue(room.id).model
            assertTrue("${room.id}: $model", model.confident)
            // The fit should find roughly the physics that generated the data.
            assertEquals("${room.id} k", room.k, model.k!!, room.k * 0.6)
        }
    }

    @Test
    fun `bedroom windows are detected on aired nights`() {
        val bedroom = DemoHome.rooms.first { it.airsAtNight }
        val outside = WindowDetector.outsideHumidity(
            weather.map { it.copy(outsideAbsHumidity = WindowDetector.absoluteHumidity(it.outsideTemperature!!, DemoHome.OUTSIDE_HUMIDITY)) },
        )
        val events = WindowDetector.detect(samples(bedroom), outside)
        // Two of three nights over three weeks.
        assertTrue("found ${events.size}", events.size >= 10)
    }

    @Test
    fun `no windows in rooms that are not aired`() {
        DemoHome.rooms.filterNot { it.airsAtNight }.forEach { room ->
            assertEquals(room.id, emptyList<Any>(), WindowDetector.detect(samples(room)))
        }
    }

    @Test
    fun `showers are detected in the bathroom only`() {
        val bath = DemoHome.rooms.first { it.showers }
        assertTrue(BathingDetector.detect(samples(bath)).size >= 14)
        val living = DemoHome.rooms.first { it.kind == DemoHome.Kind.LIVING }
        assertTrue(BathingDetector.detect(samples(living)).isEmpty())
    }

    @Test
    fun `simulation is repeatable`() {
        val room = DemoHome.rooms.first()
        val a = DemoHome.simulate(room, DemoHome.defaultProfile(room.kind), start, now, zone).last()
        val b = DemoHome.simulate(room, DemoHome.defaultProfile(room.kind), start, now, zone).last()
        assertEquals(a, b)
    }
}
