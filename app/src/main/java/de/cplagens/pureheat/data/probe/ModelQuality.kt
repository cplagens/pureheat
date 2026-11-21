/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.probe

/** How far the room's predictions can be trusted, for the badge on the room model card. Pure Kotlin. */
enum class ModelQuality {
    /** No model yet, parameters only estimated, or the probe run keeps missing. */
    LEARNING,

    /** Measured, but the probe run hasn't confirmed it yet. */
    FAIR,

    /** The probe run confirmed it ([ProbeScore.trustworthy]). */
    GOOD;

    companion object {
        /** Below this many evaluated predictions the hit rate says nothing yet. */
        const val MIN_EVALUATED = 5
        const val POOR_HIT_RATE = 0.5

        /** [hasModel]: an active model exists; [measured]: its k and P come from the data. */
        fun of(hasModel: Boolean, measured: Boolean, score: ProbeScore?): ModelQuality = when {
            !hasModel || !measured -> LEARNING
            score?.trustworthy == true -> GOOD
            score != null && score.evaluated >= MIN_EVALUATED && score.hitRate < POOR_HIT_RATE -> LEARNING
            else -> FAIR
        }
    }
}
