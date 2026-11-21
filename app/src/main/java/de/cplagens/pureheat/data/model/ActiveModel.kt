/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

/**
 * The model the room screens show first: the grey box in use (fitted over all data), else the
 * physics model once it is confident. The other one is only offered for comparison. Pure Kotlin.
 */
sealed interface ActiveModel {
    val isGreyBox: Boolean
    val params: SimParams

    /** Heat loss and heating power come from the data, not (only) from the starting values. */
    val measured: Boolean

    fun heatUp(from: Double, to: Double, outside: Double): Prediction
    fun coolDown(from: Double, to: Double, outside: Double): Prediction

    data class Grey(val model: GreyBoxModel) : ActiveModel {
        override val isGreyBox = true
        override val params = SimParams.of(model)
        override val measured get() = model.kMeasured && model.powerMeasured

        override fun heatUp(from: Double, to: Double, outside: Double) = model.heatUp(from, to, outside)
        override fun coolDown(from: Double, to: Double, outside: Double) = model.coolDown(from, to, outside)
    }

    data class Physics(val model: RoomModel, override val params: SimParams) : ActiveModel {
        override val isGreyBox = false
        override val measured = true

        override fun heatUp(from: Double, to: Double, outside: Double) = model.heatUp(from, to, outside)
        override fun coolDown(from: Double, to: Double, outside: Double) = model.coolDown(from, to, outside)
    }

    companion object {
        fun choose(greyBox: GreyBoxModel?, physics: RoomModel): ActiveModel? =
            greyBox?.let(::Grey) ?: SimParams.of(physics)?.let { Physics(physics, it) }
    }
}
