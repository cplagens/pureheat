/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.hmip

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/** Weekdays as keys of `profileDays`; display names come from string resources (`ui/ProfileTexts.kt`). */
enum class Weekday {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    companion object {
        val WORKDAYS = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
        val WEEKEND = setOf(SATURDAY, SUNDAY)
    }
}

/** A heating phase: from [start] to [end] (minutes after midnight, end may be 1440) at [temperature]. */
data class HeatingPeriod(val start: Int, val end: Int, val temperature: Double)

/** Outside the periods the room is kept at [baseTemperature] (setback). */
data class ProfileDay(val baseTemperature: Double, val periods: List<HeatingPeriod>)

/**
 * A weekly heating profile of one room. [raw] is the profile as the cloud returned it;
 * [HeatingProfileCodec.encode] writes the edited days back into it so unknown fields survive.
 */
data class HeatingProfile(
    val groupId: String,
    val index: String,
    val profileId: String?,
    val days: Map<Weekday, ProfileDay>,
    val raw: JsonObject,
)

/**
 * Reads and writes the `profileDays` structure:
 * `{"MONDAY": {"baseValue": 15.0, "periods": [{"starttime": "06:00", "endtime": "22:00", "value": 21.0}]}}`.
 * The real cloud (verified 2026-11) calls the setback temperature `baseValue`; homematicip-rest-api
 * documents `baseTemperature`. Both are read, and the key the cloud used is written back.
 * Lenient like the rest of the parser; days it can't read are left untouched.
 */
object HeatingProfileCodec {

    fun parse(response: JsonObject, groupId: String, index: String): HeatingProfile {
        // Some API versions wrap the profile, others return it directly.
        val profile = (response["profile"] as? JsonObject) ?: response
        val daysJson = profile["profileDays"] as? JsonObject ?: JsonObject(emptyMap())
        val days = buildMap {
            for ((key, el) in daysJson) {
                val day = Weekday.entries.firstOrNull { it.name == key } ?: continue
                val obj = el as? JsonObject ?: continue
                val base = BASE_KEYS.firstNotNullOfOrNull { (obj[it] as? JsonPrimitive)?.doubleOrNull } ?: continue
                val periods = (obj["periods"] as? JsonArray).orEmpty().mapNotNull { p ->
                    val po = p as? JsonObject ?: return@mapNotNull null
                    HeatingPeriod(
                        start = parseTime(po.str("starttime")) ?: return@mapNotNull null,
                        end = parseTime(po.str("endtime")) ?: return@mapNotNull null,
                        temperature = (po["value"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null,
                    )
                }.sortedBy { it.start }
                put(day, ProfileDay(base, periods))
            }
        }
        return HeatingProfile(
            groupId = profile.str("groupId") ?: groupId,
            index = profile.str("index") ?: index,
            profileId = profile.str("id") ?: profile.str("profileId"),
            days = days,
            raw = profile,
        )
    }

    fun encode(profile: HeatingProfile): JsonObject {
        val oldDays = profile.raw["profileDays"] as? JsonObject ?: JsonObject(emptyMap())
        val newDays = oldDays.toMutableMap()
        for ((day, d) in profile.days) {
            val old = oldDays[day.name] as? JsonObject ?: JsonObject(emptyMap())
            val baseKey = BASE_KEYS.firstOrNull { it in old } ?: BASE_KEYS.first()
            newDays[day.name] = JsonObject(
                old + mapOf(
                    baseKey to JsonPrimitive(d.baseTemperature),
                    "periods" to buildJsonArray {
                        d.periods.forEach { p ->
                            add(buildJsonObject {
                                put("starttime", formatTime(p.start))
                                put("endtime", formatTime(p.end))
                                put("value", p.temperature)
                            })
                        }
                    },
                ),
            )
        }
        return JsonObject(profile.raw + ("profileDays" to JsonObject(newDays)))
    }

    private val BASE_KEYS = listOf("baseValue", "baseTemperature")

    fun parseTime(s: String?): Int? {
        val (h, m) = s?.split(":")?.takeIf { it.size == 2 } ?: return null
        val minutes = (h.toIntOrNull() ?: return null) * 60 + (m.toIntOrNull() ?: return null)
        return minutes.takeIf { it in 0..MINUTES_PER_DAY }
    }

    fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}

const val MINUTES_PER_DAY = 24 * 60

/** Pure editing helpers for the profile screen; everything snaps to 15 min and 0.5 °C. */
object ProfileEditing {
    const val STEP_MINUTES = 15
    const val TEMPERATURE_STEP = 0.5

    /** Homematic IP allows a limited number of heating phases per day; 6 is a safe upper bound. */
    const val MAX_PERIODS = 6

    fun snapMinutes(minutes: Int): Int =
        ((minutes.toDouble() / STEP_MINUTES).roundToInt() * STEP_MINUTES).coerceIn(0, MINUTES_PER_DAY)

    fun snapTemperature(t: Double, min: Double, max: Double): Double =
        ((t / TEMPERATURE_STEP).roundToInt() * TEMPERATURE_STEP).coerceIn(min, max)

    /**
     * Moves the start or end of period [index] to [minutes], kept between its neighbours
     * so periods never overlap and always last at least 15 min.
     */
    fun moveEdge(periods: List<HeatingPeriod>, index: Int, isStart: Boolean, minutes: Int): List<HeatingPeriod> {
        val p = periods[index]
        val m = snapMinutes(minutes)
        val updated = if (isStart) {
            val lower = periods.getOrNull(index - 1)?.end ?: 0
            p.copy(start = m.coerceIn(lower, p.end - STEP_MINUTES))
        } else {
            val upper = periods.getOrNull(index + 1)?.start ?: MINUTES_PER_DAY
            p.copy(end = m.coerceIn(p.start + STEP_MINUTES, upper))
        }
        return periods.toMutableList().also { it[index] = updated }
    }

    /** Adds a period in the largest free gap (up to 2 h long), or returns null if there is no room. */
    fun addPeriod(day: ProfileDay, temperature: Double): ProfileDay? {
        if (day.periods.size >= MAX_PERIODS) return null
        val bounds = listOf(0) + day.periods.flatMap { listOf(it.start, it.end) } + MINUTES_PER_DAY
        val gaps = bounds.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }
        val (from, to) = gaps.maxByOrNull { it.second - it.first } ?: return null
        if (to - from < STEP_MINUTES * 2) return null
        val length = minOf(120, to - from)
        val start = snapMinutes(from + (to - from - length) / 2).coerceAtLeast(from)
        val end = (start + length).coerceAtMost(to)
        val periods = (day.periods + HeatingPeriod(start, end, temperature)).sortedBy { it.start }
        return day.copy(periods = periods)
    }

    fun copyDay(profile: HeatingProfile, from: Weekday, to: Set<Weekday>): HeatingProfile {
        val source = profile.days[from] ?: return profile
        return profile.copy(days = profile.days + to.associateWith { source })
    }

    /** Human-readable list of changes for the confirmation dialog. */
    /** Days whose periods or base temperature differ, for the confirmation dialog. */
    fun diff(old: HeatingProfile, new: HeatingProfile): List<DayChange> = Weekday.entries.mapNotNull { day ->
        val b = new.days[day] ?: return@mapNotNull null
        val a = old.days[day]
        if (a == b) null else DayChange(day, a, b)
    }
}

/** One changed day; [before] is null if the day couldn't be read before. */
data class DayChange(val day: Weekday, val before: ProfileDay?, val after: ProfileDay) {
    val periodsChanged: Boolean get() = before == null || before.periods != after.periods
    val baseChanged: Boolean get() = before != null && before.baseTemperature != after.baseTemperature
}
