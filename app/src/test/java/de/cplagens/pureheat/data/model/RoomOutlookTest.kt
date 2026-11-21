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
import java.time.ZoneId
import java.time.ZonedDateTime

class RoomOutlookTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val hour = 3_600_000L
    private val now = ZonedDateTime.of(2026, 11, 21, 15, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun greyBox(kSe: Double = 0.0, powerSe: Double = 0.0) = GreyBoxModel(
        k = 0.03, power = 1.0, gain = 0.0, lagHours = 1, kSe = kSe, powerSe = powerSe, gainSe = 0.0,
        rmse = 0.0, n = 100, prior = GreyBoxPrior(),
    )

    private val learning = RoomModel(k = 0.03, power = 0.6, deadTimeHours = 1.0, coolingPhases = 0, heatingPhases = 0, kSpread = null)
    private val confident = learning.copy(coolingPhases = RoomModel.MIN_PHASES, heatingPhases = RoomModel.MIN_PHASES, kSpread = 0.4)

    @Test
    fun greyBoxWinsOverPhysics() {
        assertTrue(ActiveModel.choose(greyBox(), confident)!!.isGreyBox)
        assertFalse(ActiveModel.choose(null, confident)!!.isGreyBox)
        assertNull(ActiveModel.choose(null, learning))
    }

    @Test
    fun outlookRunsUntilTheEndOfTomorrow() {
        val outlook = RoomOutlook.build(ActiveModel.Grey(greyBox()), null, 20.0, now, { 21.0 }, { 5.0 }, zone = zone)
        val tomorrow = outlook.tomorrowStart(zone)
        assertEquals(ZonedDateTime.of(2026, 11, 22, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), tomorrow)
        assertEquals(tomorrow + 24 * hour, outlook.end)
        assertEquals(now, outlook.points.first().time)
        assertTrue(outlook.points.last().time <= outlook.end)
        // No error known: no band.
        assertTrue(outlook.band.isEmpty())
        assertNull(outlook.comparison)
    }

    @Test
    fun bandGrowsWithTheSquareRootOfTimeForTwelveHours() {
        val outlook = RoomOutlook.build(
            ActiveModel.Grey(greyBox()),
            SimParams.of(confident),
            18.0,
            now,
            { 21.0 },
            { 5.0 },
            stepError = 0.25,
            zone = zone,
        )
        assertEquals(now + RoomOutlook.BAND_HOURS * hour, outlook.band.last().first)
        val width: (Long) -> Double = { t -> outlook.band.first { it.first == t }.let { (it.third - it.second) / 2 } }
        assertEquals(0.0, width(now), 1e-9)
        assertEquals(0.25, width(now + hour), 1e-9)
        assertEquals(0.5, width(now + 4 * hour), 1e-9)
        outlook.band.forEach { b ->
            val p = outlook.points.first { it.time == b.first }
            assertEquals(p.temperature, (b.second + b.third) / 2, 1e-9)
        }
        assertNotNull(outlook.comparison)
    }
}
