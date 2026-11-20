/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

import de.cplagens.pureheat.data.history.RoomModelEntity

/** Outcome of one training run: the candidate and whether it replaces the current model. */
data class GreyBoxTrainingResult(
    val model: GreyBoxModel,
    /** One-step error of [model] on the hold-out week (not used for fitting); null for the first model. */
    val holdoutRmse: Double?,
    /** Error of the model in use on the same week; null if there was none. */
    val previousHoldoutRmse: Double?,
    val adopted: Boolean,
)

/**
 * When and how the grey-box model of a room is (re-)fitted. Pure Kotlin.
 *
 * The first model comes after [MIN_SPAN_HOURS] of data, fitted on everything. From then on, once a
 * week, a candidate is fitted on all data except the last week and both it and the model in use
 * predict that week; the candidate is adopted only if it does at least as well. Both models have
 * never seen the hold-out week, so the comparison is fair.
 */
object GreyBoxTraining {
    const val MIN_SPAN_HOURS = 7 * 24
    const val HOLDOUT_HOURS = 7 * 24
    const val REFIT_INTERVAL_MS = 7 * 24 * 3_600_000L

    /** Margin so a worker that runs a little early on the seventh day still refits. */
    private const val DUE_SLACK_MS = 6 * 3_600_000L

    fun due(lastFitAt: Long?, now: Long): Boolean =
        lastFitAt == null || now - lastFitAt >= REFIT_INTERVAL_MS - DUE_SLACK_MS

    /** [points] sorted by hour; null while there is too little data or nothing to compare. */
    fun train(points: List<HourPoint>, current: GreyBoxModel?, prior: GreyBoxPrior = GreyBoxPrior()): GreyBoxTrainingResult? {
        if (points.isEmpty() || points.last().hour - points.first().hour < MIN_SPAN_HOURS) return null
        val cut = points.last().hour - HOLDOUT_HOURS + 1
        val holdout = points.filter { it.hour >= cut }
        val candidate = GreyBoxFitter.fit(points.filter { it.hour < cut }, prior)
        if (candidate == null) {
            // Too little data before the hold-out week: only a first model, fitted on everything.
            if (current != null) return null
            val first = GreyBoxFitter.fit(points, prior) ?: return null
            return GreyBoxTrainingResult(first, holdoutRmse = null, previousHoldoutRmse = null, adopted = true)
        }
        val newError = GreyBoxFitter.rmse(candidate, holdout)
        val oldError = current?.let { GreyBoxFitter.rmse(it, holdout) }
        val adopted = current == null || newError != null && (oldError == null || newError <= oldError)
        return GreyBoxTrainingResult(candidate, newError, oldError, adopted)
    }
}

fun GreyBoxTrainingResult.toEntity(roomId: String, fittedAt: Long) = RoomModelEntity(
    roomId = roomId,
    fittedAt = fittedAt,
    k = model.k,
    power = model.power,
    gain = model.gain,
    lagHours = model.lagHours,
    kSe = model.kSe,
    powerSe = model.powerSe,
    gainSe = model.gainSe,
    rmse = model.rmse,
    n = model.n,
    holdoutRmse = holdoutRmse,
    previousHoldoutRmse = previousHoldoutRmse,
    adopted = adopted,
    priorK = model.prior.k,
    priorKSd = model.prior.kSd,
    priorPower = model.prior.power,
    priorPowerSd = model.prior.powerSd,
    priorGain = model.prior.gain,
    priorGainSd = model.prior.gainSd,
)

fun RoomModelEntity.toModel() = GreyBoxModel(
    k = k,
    power = power,
    gain = gain,
    lagHours = lagHours,
    kSe = kSe,
    powerSe = powerSe,
    gainSe = gainSe,
    rmse = rmse,
    n = n,
    prior = GreyBoxPrior(priorK, priorKSd, priorPower, priorPowerSd, priorGain, priorGainSd),
)
