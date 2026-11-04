/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.export

import de.cplagens.pureheat.data.history.CircuitSample
import de.cplagens.pureheat.data.history.PredictionEntity
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvTest {
    @Test
    fun roundTripsAllRowTypes() {
        val room = RoomSample("room-1", 1_762_200_000_000, 21.5, 22.0, 0.35, 48)
        assertEquals(room, Csv.parseRoom(Csv.room(room)))
        val empty = RoomSample("room-2", 1_762_200_000_000, null, null, null, null)
        assertEquals(empty, Csv.parseRoom(Csv.room(empty)))
        val circuit = CircuitSample("dev-1", 4, 1_762_200_000_000, 1.0)
        assertEquals(circuit, Csv.parseCircuit(Csv.circuit(circuit)))
        val weather = WeatherSample(1_762_200_000_000, -3.5)
        assertEquals(weather, Csv.parseWeather(Csv.weather(weather)))
        val p = PredictionEntity("room-1", "physics", 1L, 2L, 23.0, 20.5, null, "prior", 3L, 4L, "hit")
        assertEquals(p, Csv.parsePrediction(Csv.prediction(p)))
    }

    @Test
    fun decimalPointAndReadableTime() {
        val line = Csv.room(RoomSample("r", 0, 21.5, null, 0.5, 40))
        assertEquals("0,1970-01-01T00:00:00Z,r,21.5,,0.5,40", line)
    }

    @Test
    fun quotedLabels() {
        val line = Csv.roomName("id", "Bad, oben \"Gäste\"")
        assertEquals(listOf("id", "Bad, oben \"Gäste\""), Csv.fields(line))
    }

    @Test
    fun brokenLinesAreSkipped() {
        assertNull(Csv.parseRoom("not,a,valid"))
        assertNull(Csv.parseRoom("abc,x,room,1,2,3,4"))
    }
}
