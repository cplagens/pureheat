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
import java.time.Instant

/**
 * CSV for the measurement export: comma-separated, '.' as decimal point (independent of the
 * phone's language), empty field = no value, timestamps in epoch milliseconds plus a readable UTC
 * column. Pure Kotlin so it is unit-tested; readable by spreadsheets, pandas & co. for AI training.
 */
object Csv {
    const val ROOMS = "timestamp,time_utc,room_id,actual_temperature,set_point_temperature,valve_position,humidity"
    const val CIRCUITS = "timestamp,time_utc,device_id,channel_index,valve_position"
    const val WEATHER = "timestamp,time_utc,outside_temperature,outside_vapour_g_m3"
    const val PREDICTIONS =
        "room_id,strategy,event_time,created_at,target_temperature,start_temperature,predicted_reach,model_source,evaluated_at,actual_reach,outcome"
    const val ROOM_NAMES = "room_id,label"

    fun room(s: RoomSample) = line(s.timestamp, iso(s.timestamp), s.roomId, s.actualTemperature, s.setPointTemperature, s.valvePosition, s.humidity)
    fun circuit(s: CircuitSample) = line(s.timestamp, iso(s.timestamp), s.deviceId, s.channelIndex, s.valvePosition)
    fun weather(s: WeatherSample) = line(s.timestamp, iso(s.timestamp), s.outsideTemperature, s.outsideAbsHumidity)
    fun prediction(p: PredictionEntity) = line(
        p.roomId, p.strategy, p.eventTime, p.createdAt, p.targetTemperature, p.startTemperature, p.predictedReach,
        p.modelSource, p.evaluatedAt, p.actualReach, p.outcome,
    )
    fun roomName(id: String, label: String) = line(id, label.trim())

    fun parseRoom(l: String): RoomSample? = fields(l).takeIf { it.size >= 7 }?.let { f ->
        RoomSample(
            roomId = f[2].ifEmpty { return null },
            timestamp = f[0].toLongOrNull() ?: return null,
            actualTemperature = f[3].toDoubleOrNull(),
            setPointTemperature = f[4].toDoubleOrNull(),
            valvePosition = f[5].toDoubleOrNull(),
            humidity = f[6].toIntOrNull(),
        )
    }

    fun parseCircuit(l: String): CircuitSample? = fields(l).takeIf { it.size >= 5 }?.let { f ->
        CircuitSample(
            deviceId = f[2].ifEmpty { return null },
            channelIndex = f[3].toIntOrNull() ?: return null,
            timestamp = f[0].toLongOrNull() ?: return null,
            valvePosition = f[4].toDoubleOrNull(),
        )
    }

    fun parseWeather(l: String): WeatherSample? = fields(l).takeIf { it.size >= 3 }?.let { f ->
        // The vapour column came with DB v6; older exports have only three columns.
        WeatherSample(
            timestamp = f[0].toLongOrNull() ?: return null,
            outsideTemperature = f[2].toDoubleOrNull(),
            outsideAbsHumidity = f.getOrNull(3)?.toDoubleOrNull(),
        )
    }

    fun parsePrediction(l: String): PredictionEntity? = fields(l).takeIf { it.size >= 11 }?.let { f ->
        PredictionEntity(
            roomId = f[0].ifEmpty { return null },
            strategy = f[1].ifEmpty { return null },
            eventTime = f[2].toLongOrNull() ?: return null,
            createdAt = f[3].toLongOrNull() ?: return null,
            targetTemperature = f[4].toDoubleOrNull() ?: return null,
            startTemperature = f[5].toDoubleOrNull() ?: return null,
            predictedReach = f[6].toLongOrNull(),
            modelSource = f[7],
            evaluatedAt = f[8].toLongOrNull(),
            actualReach = f[9].toLongOrNull(),
            outcome = f[10].ifEmpty { null },
        )
    }

    private fun iso(t: Long) = Instant.ofEpochMilli(t).toString()

    private fun line(vararg values: Any?) = values.joinToString(",") { v ->
        when (v) {
            null -> ""
            is String -> if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
            else -> v.toString()
        }
    }

    /** Splits one CSV line; handles quoted fields with commas and doubled quotes. */
    fun fields(l: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < l.length) {
            val c = l[i]
            when {
                quoted && c == '"' && i + 1 < l.length && l[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
