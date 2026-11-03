/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.model

import de.cplagens.pureheat.data.history.BathingDetector

import de.cplagens.pureheat.data.history.HistoryAnalysis
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import de.cplagens.pureheat.data.history.WindowDetector
import de.cplagens.pureheat.data.history.WindowEvent

/** What the app has learned about one room, plus the inputs for its predictions. */
data class RoomInsight(
    val model: RoomModel,
    /** Setback/comfort temperature from the last 7 days of setpoints. */
    val levels: SetPointLevels?,
    /** Outside temperature used for the predictions: forecast mean of the next 12 h, else the last measurement. */
    val outside: Double?,
    /** True if [outside] comes from the forecast. */
    val outsideIsForecast: Boolean = false,
    /** Detected open-window times in the analysed history; excluded from the model. */
    val windowEvents: List<WindowEvent> = emptyList(),
) {
    /** Setback → comfort, e.g. in the morning. */
    val heatUp: Prediction
        get() = if (levels == null || outside == null) Prediction.Unknown
        else model.heatUp(levels.setback, levels.comfort, outside)

    /** Comfort → setback after the heating phase ends. */
    val coolDown: Prediction
        get() = if (levels == null || outside == null) Prediction.Unknown
        else model.coolDown(levels.comfort, levels.setback, outside)

    companion object {
        private const val LEVEL_WINDOW_MS = 7 * 24 * 3_600_000L

        /** [samples] and [weather] sorted by time; [samples] of all rooms. */
        fun forAllRooms(
            samples: List<RoomSample>,
            weather: List<WeatherSample>,
            now: Long,
            forecastOutside: Double? = null,
        ): Map<String, RoomInsight> {
            val outside = forecastOutside ?: weather.lastOrNull { it.outsideTemperature != null }?.outsideTemperature
            return samples.groupBy { it.roomId }.mapValues { (_, s) ->
                val windows = WindowDetector.detect(s)
                // An open window is not the room's normal heat loss, hot water not its heating: leave those times out.
                val clean = BathingDetector.withoutBathingTimes(WindowDetector.withoutWindowTimes(s, windows), BathingDetector.detect(s))
                RoomInsight(
                    model = RoomModelFitter.fit(HistoryAnalysis.phases(clean, weather)),
                    levels = SetPointLevels.from(s.filter { it.timestamp >= now - LEVEL_WINDOW_MS }),
                    outside = outside,
                    outsideIsForecast = forecastOutside != null,
                    windowEvents = windows,
                )
            }
        }
    }
}
