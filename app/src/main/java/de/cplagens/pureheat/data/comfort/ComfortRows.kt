/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.comfort

import de.cplagens.pureheat.data.history.ComfortDayEntity
import de.cplagens.pureheat.data.history.ComfortWindowEntity
import de.cplagens.pureheat.hmip.Weekday

/**
 * Step 6: maps a room's comfort settings to the `comfort_day` / `comfort_window` rows and back.
 * Pure Kotlin. Reading is lenient: unknown weekdays, empty or out-of-range windows and windows of a
 * weekday without a `comfort_day` row (the editor always saves both) are dropped.
 */
object ComfortRows {
    private const val DAY_MINUTES = 24 * 60

    fun toEntities(
        roomId: String,
        days: Map<Weekday, ComfortDay>,
    ): Pair<List<ComfortDayEntity>, List<ComfortWindowEntity>> {
        val dayRows = days.map { (weekday, day) -> ComfortDayEntity(roomId, weekday.name, day.ecoTemperature) }
        val windowRows = days.flatMap { (weekday, day) ->
            day.windows.filter(::isValid)
                // Primary key is (room, weekday, start): the first window per start wins.
                .distinctBy { it.start }
                .map { ComfortWindowEntity(roomId, weekday.name, it.start, it.end, it.temperature) }
        }
        return dayRows to windowRows
    }

    fun fromEntities(days: List<ComfortDayEntity>, windows: List<ComfortWindowEntity>): Map<Weekday, ComfortDay> {
        val byDay = windows.groupBy { it.weekday }
        return days.mapNotNull { row ->
            val weekday = Weekday.entries.firstOrNull { it.name == row.weekday } ?: return@mapNotNull null
            val list = byDay[row.weekday].orEmpty()
                .map { ComfortWindow(it.start, it.end, it.temperature) }
                .filter(::isValid)
                .sortedBy { it.start }
            weekday to ComfortDay(row.ecoTemperature, list)
        }.toMap()
    }

    private fun isValid(w: ComfortWindow) =
        w.start in 0 until DAY_MINUTES && w.end in (w.start + 1)..DAY_MINUTES && w.temperature.isFinite()
}
