/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.probe

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelQualityTest {
    @Test
    fun learningWithoutMeasuredModel() {
        assertEquals(ModelQuality.LEARNING, ModelQuality.of(hasModel = false, measured = false, score = null))
        assertEquals(ModelQuality.LEARNING, ModelQuality.of(hasModel = true, measured = false, score = ProbeScore(20, 20, 5, 14)))
    }

    @Test
    fun fairUntilTheProbeRunConfirms() {
        assertEquals(ModelQuality.FAIR, ModelQuality.of(true, true, null))
        assertEquals(ModelQuality.FAIR, ModelQuality.of(true, true, ProbeScore(3, 0, 90, 2)))
        assertEquals(ModelQuality.FAIR, ModelQuality.of(true, true, ProbeScore(10, 7, 20, 8)))
    }

    @Test
    fun goodOnceTrustworthy() {
        assertEquals(ModelQuality.GOOD, ModelQuality.of(true, true, ProbeScore(20, 18, 15, 14)))
    }

    @Test
    fun backToLearningWhenItKeepsMissing() {
        assertEquals(ModelQuality.LEARNING, ModelQuality.of(true, true, ProbeScore(10, 4, 80, 6)))
    }
}
