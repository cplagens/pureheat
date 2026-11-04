/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.room

import de.cplagens.pureheat.data.probe.ProbeRun
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPropertiesTest {
    @Test
    fun germanDescription() {
        val p = KeywordExtractor.extract(
            "Schlafzimmer im Dachgeschoss, ca. 18,5 qm, Raumhöhe 2,40 m. Zwei Fenster nach Süden und ein Dachfenster " +
                "nach Westen, Dreifachverglasung, Rollläden. 2 Außenwände, Parkett. Nachts ist das Fenster gekippt.",
        )
        assertEquals(18.5, p.areaM2!!, 0.01)
        assertEquals(2.4, p.ceilingHeightM!!, 0.01)
        assertEquals(3, p.windowCount)
        assertEquals(listOf(Direction.S, Direction.W), p.windowDirections)
        assertEquals(Glazing.TRIPLE, p.glazing)
        assertEquals(2, p.exteriorWalls)
        assertEquals(Position.ATTIC, p.position)
        assertEquals(FloorCovering.WOOD, p.floorCovering)
        assertEquals(true, p.shutters)
        assertEquals(Usage.BEDROOM, p.usage)
        assertEquals(true, p.ventilatesAtNight)
    }

    @Test
    fun englishDescription() {
        val p = KeywordExtractor.extract(
            "Living room on the ground floor above an unheated cellar, 32 m2, one large window facing south-west, " +
                "tiles, wood stove and a TV. Corner room.",
        )
        assertEquals(32.0, p.areaM2!!, 0.01)
        assertEquals(1, p.windowCount)
        assertEquals(listOf(Direction.SW), p.windowDirections)
        assertEquals(Position.GROUND, p.position)
        assertEquals(true, p.aboveUnheated)
        assertEquals(FloorCovering.TILE, p.floorCovering)
        assertEquals(listOf(HeatSource.STOVE, HeatSource.ELECTRONICS), p.heatSources)
        assertEquals(Usage.LIVING, p.usage)
        assertEquals(2, p.exteriorWalls)
    }

    @Test
    fun noFalseDirectionsOrCounts() {
        // "so", "kosten", "at least" must not become directions; window glass area is not the room area.
        val p = KeywordExtractor.extract("Das Bad ist so klein, Fensterfläche 2 m², kostet viel Energie, at least it is warm. Handtuchheizkörper.")
        assertTrue(p.windowDirections.isEmpty())
        assertNull(p.areaM2)
        assertEquals(Usage.BATH, p.usage)
        assertEquals(listOf(HeatSource.TOWEL_RADIATOR), p.heatSources)
        assertNull(p.position)
    }

    @Test
    fun aboveCellarIsNotBasement() {
        val p = KeywordExtractor.extract("Erdgeschoss über dem Keller, Fliesen")
        assertEquals(Position.GROUND, p.position)
        assertEquals(true, p.aboveUnheated)
        assertEquals(Position.BASEMENT, KeywordExtractor.extract("Hobbyraum im Keller").position)
    }

    @Test
    fun lenientJsonFromModel() {
        val o = Json.parseToJsonElement(
            """{"areaM2":"21,5","ceilingHeightM":12,"windowCount":2.0,"windowDirections":["s","South","NW"],
               "glazing":"double","floorCovering":"marble","heatSources":"STOVE","usage":null,"extra":1}""",
        ).jsonObject
        val p = RoomProperties.fromJson(o)
        assertEquals(21.5, p.areaM2!!, 0.01)
        assertNull("implausible height dropped", p.ceilingHeightM)
        assertEquals(2, p.windowCount)
        assertEquals(listOf(Direction.S, Direction.NW), p.windowDirections)
        assertEquals(Glazing.DOUBLE, p.glazing)
        assertNull("unknown enum ignored", p.floorCovering)
        assertEquals(listOf(HeatSource.STOVE), p.heatSources)
    }

    @Test
    fun configurationLimitsDetection() {
        // Windows: only an explicit "0" switches the detection off; unknown keeps it on.
        assertTrue(RoomProperties().hasWindows)
        assertTrue(!RoomProperties(windowCount = 0).hasWindows)
        assertTrue(KeywordExtractor.extract("keine Fenster, Fliesen").let { !it.hasWindows })
        // Bathing: bathroom by default yes, but not if shower and bathtub were both answered "no".
        assertTrue(RoomProperties(usage = Usage.BATH).hasBathing)
        assertTrue(!RoomProperties(usage = Usage.BATH, shower = false, bathtub = false).hasBathing)
        assertTrue(RoomProperties(usage = Usage.BEDROOM, shower = true).hasBathing)
        assertTrue(!RoomProperties(usage = Usage.KITCHEN).hasBathing)
    }

    @Test
    fun showerAndBathtub() {
        val de = KeywordExtractor.extract("Bad mit Dusche und Badewanne, 8 qm")
        assertEquals(true, de.shower)
        assertEquals(true, de.bathtub)
        assertTrue(de.hasBathing)
        val en = KeywordExtractor.extract("Guest WC with a shower")
        assertEquals(true, en.shower)
        assertNull(en.bathtub)
        assertTrue(!KeywordExtractor.extract("Bedroom, 14 m2").hasBathing)
    }

    @Test
    fun modelFalseMeansUnknown() {
        // Gemini Nano answered like this for a text that mentions neither shutters nor ventilation.
        val o = Json.parseToJsonElement("""{"shutters":false,"ventilatesAtNight":false,"aboveUnheated":true}""").jsonObject
        val p = RoomProperties.fromJson(o)
        assertNull(p.shutters)
        assertNull(p.ventilatesAtNight)
        assertEquals(true, p.aboveUnheated)
    }

    @Test
    fun keywordsWinOverModelForFloor() {
        // Real case: Nano said GROUND for "1. Stock" (first floor above ground in German).
        val text = "keine Fenster, Raumhöhe 2,75 m, Fliesen, 1. Stock, über unbeheizter Tiefgarage, 10 m²"
        val keywords = KeywordExtractor.extract(text)
        val model = RoomProperties(position = Position.GROUND, areaM2 = 10.0, windowCount = 0)
        val merged = keywords.mergedWith(model)
        assertEquals(Position.UPPER, merged.position)
        assertEquals(true, merged.aboveUnheated)
        assertEquals(FloorCovering.TILE, merged.floorCovering)
        assertEquals(2.75, merged.ceilingHeightM!!, 0.01)
        assertEquals(10.0, merged.areaM2!!, 0.01)
        assertEquals(0, merged.windowCount)
    }

    @Test
    fun mergeKeepsAiValuesAndFillsGaps() {
        val ai = RoomProperties(areaM2 = 20.0, glazing = Glazing.TRIPLE)
        val keywords = RoomProperties(areaM2 = 25.0, floorCovering = FloorCovering.TILE)
        val m = ai.mergedWith(keywords)
        assertEquals(20.0, m.areaM2!!, 0.0)
        assertEquals(Glazing.TRIPLE, m.glazing)
        assertEquals(FloorCovering.TILE, m.floorCovering)
    }

    @Test
    fun priorsFollowPhysics() {
        val base = ProbeRun.PRIOR
        assertEquals(base, RoomPriors.model(base, RoomProperties()))
        val attic = RoomPriors.model(base, RoomProperties(position = Position.ATTIC, exteriorWalls = 3, glazing = Glazing.SINGLE))
        val inner = RoomPriors.model(base, RoomProperties(exteriorWalls = 0, glazing = Glazing.TRIPLE))
        assertTrue(attic.k!! > base.k!! && inner.k!! < base.k!!)
        val carpet = RoomPriors.model(base, RoomProperties(floorCovering = FloorCovering.CARPET))
        val tiles = RoomPriors.model(base, RoomProperties(floorCovering = FloorCovering.TILE))
        assertTrue(carpet.power!! < base.power!! && tiles.power!! > base.power!!)
        assertTrue(carpet.deadTimeHours!! > tiles.deadTimeHours!!)
    }

    @Test
    fun fittedModelWinsOverProperties() {
        val fitted = ProbeRun.PRIOR.copy(k = 0.05, power = 1.2, deadTimeHours = 2.0, coolingPhases = 5, heatingPhases = 5)
        val (m, source) = ProbeRun.modelFor(fitted, RoomProperties(floorCovering = FloorCovering.CARPET))
        assertEquals(fitted, m)
        assertEquals(de.cplagens.pureheat.data.probe.ModelSource.FIT, source)
    }
}
