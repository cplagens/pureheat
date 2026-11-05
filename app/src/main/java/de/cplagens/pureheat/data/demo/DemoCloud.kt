/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.demo

import android.content.Context
import de.cplagens.pureheat.R
import de.cplagens.pureheat.data.history.CircuitSample
import de.cplagens.pureheat.data.history.ForecastSample
import de.cplagens.pureheat.data.history.PredictionEntity
import de.cplagens.pureheat.data.history.RoomSample
import de.cplagens.pureheat.data.history.WeatherSample
import de.cplagens.pureheat.data.probe.ModelSource
import de.cplagens.pureheat.data.probe.Outcome
import de.cplagens.pureheat.data.probe.Strategy
import de.cplagens.pureheat.data.room.Direction
import de.cplagens.pureheat.data.room.FloorCovering
import de.cplagens.pureheat.data.room.Glazing
import de.cplagens.pureheat.data.room.HeatSource
import de.cplagens.pureheat.data.room.RoomDescription
import de.cplagens.pureheat.data.room.RoomProperties
import de.cplagens.pureheat.data.room.Usage
import de.cplagens.pureheat.hmip.HeatingCloud
import de.cplagens.pureheat.hmip.HeatingProfile
import de.cplagens.pureheat.hmip.HeatingProfileCodec
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.Weekday
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Offline stand-in for the Homematic IP Cloud in demo mode. Answers with the same raw JSON shape as
 * the real cloud (so the real parser, model and screens run unchanged) and keeps profile edits in
 * memory – nothing is ever sent anywhere. Room names come from string resources, in the app language.
 */
class DemoCloud(private val context: Context) : HeatingCloud {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private class RoomProfiles(var active: String, val days: MutableMap<String, Map<Weekday, ProfileDay>>, val names: MutableMap<String, String>)

    private val profiles = DemoHome.rooms.associate { room ->
        val holiday = Weekday.entries.associateWith { ProfileDay(if (room.kind == DemoHome.Kind.BATH) 19.0 else 17.0, emptyList()) }
        room.id to RoomProfiles(
            active = PROFILE_1,
            days = mutableMapOf(PROFILE_1 to DemoHome.defaultProfile(room.kind), PROFILE_2 to holiday),
            names = mutableMapOf(PROFILE_2 to context.getString(R.string.demo_profile_away)),
        )
    }

    private fun activeDays(roomId: String) = profiles.getValue(roomId).let { it.days.getValue(it.active) }

    override suspend fun getCurrentState(sgtin: String, authToken: String, restUrl: String?): JsonObject {
        val now = System.currentTimeMillis()
        val current = DemoHome.rooms.associate { room ->
            room.id to DemoHome.simulate(room, activeDays(room.id), now - LEAD_TIME_MS, now, zone).last()
        }
        return buildJsonObject {
            putJsonObject("home") {
                putJsonObject("weather") { put("temperature", round1(DemoHome.outsideAt(now, zone))) }
                // No location: the demo never asks Open-Meteo, its forecast is invented as well.
            }
            putJsonObject("devices") {
                putJsonObject(DemoHome.DEVICE_ID) {
                    put("type", "FLOOR_TERMINAL_BLOCK_10")
                    put("label", "FAL")
                    putJsonObject("functionalChannels") {
                        putJsonObject("0") { put("functionalChannelType", "DEVICE_BASE") }
                        DemoHome.rooms.forEach { room ->
                            val point = current.getValue(room.id)
                            room.channels.forEach { ch ->
                                putJsonObject(ch.toString()) {
                                    put("functionalChannelType", "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL")
                                    put("index", ch)
                                    put("valvePosition", round2(point.valve))
                                    put("valveState", "ADAPTION_DONE")
                                    put("groups", buildJsonArray { add(JsonPrimitive(room.id)) })
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("groups") {
                DemoHome.rooms.forEach { room ->
                    val point = current.getValue(room.id)
                    val p = profiles.getValue(room.id)
                    putJsonObject(room.id) {
                        put("type", "HEATING")
                        put("label", label(room.kind))
                        put("actualTemperature", round1(point.temperature))
                        put("setPointTemperature", point.setPoint)
                        put("humidity", point.humidity)
                        put("controlMode", "AUTOMATIC")
                        put("activeProfile", p.active)
                        put("minTemperature", 5.0)
                        put("maxTemperature", 30.0)
                        putJsonObject("profiles") {
                            listOf(PROFILE_1, PROFILE_2).forEach { index ->
                                putJsonObject(index) {
                                    put("profileId", profileId(room.id, index))
                                    put("index", index)
                                    p.names[index]?.let { put("name", it) }
                                    put("enabled", true)
                                    put("visible", true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun getProfile(
        sgtin: String,
        authToken: String,
        rest: String,
        groupId: String,
        profileIndex: String,
        profileId: String,
    ): JsonObject {
        val days = profiles.getValue(groupId).days.getValue(profileIndex)
        val base = buildJsonObject {
            put("id", profileId)
            put("groupId", groupId)
            put("homeId", "demo")
            put("type", "HEATING")
        }
        return HeatingProfileCodec.encode(HeatingProfile(groupId, profileIndex, profileId, days, base))
    }

    override suspend fun updateProfile(sgtin: String, authToken: String, rest: String, groupId: String, profileIndex: String, profile: JsonObject) {
        val p = profiles.getValue(groupId)
        p.days[profileIndex] = HeatingProfileCodec.parse(profile, groupId, profileIndex).days
        (profile["name"] as? JsonPrimitive)?.content?.let { p.names[profileIndex] = it }
    }

    override suspend fun setActiveProfile(sgtin: String, authToken: String, rest: String, groupId: String, profileIndex: String) {
        profiles.getValue(groupId).active = profileIndex
    }

    /** Hourly invented forecast from a few hours ago to three days ahead. */
    fun forecast(now: Long): List<ForecastSample> {
        val hour = 3_600_000L
        val first = now - now % hour - 6 * hour
        return (0 until 78).map { i ->
            val t = first + i * hour
            ForecastSample(t, round1(DemoHome.outsideAt(t, zone)), now)
        }
    }

    class History(
        val rooms: List<RoomSample>,
        val circuits: List<CircuitSample>,
        val weather: List<WeatherSample>,
        val predictions: List<PredictionEntity>,
    )

    /**
     * Three weeks of recordings every 15 min (as the background job takes them) up to [now], plus
     * evaluated probe-run predictions of the last week so the hit rate is filled.
     */
    fun history(now: Long): History {
        val end = now - RECORD_INTERVAL_MS
        val start = end - HISTORY_MS
        val rooms = ArrayList<RoomSample>()
        val circuits = ArrayList<CircuitSample>()
        val predictions = ArrayList<PredictionEntity>()
        DemoHome.rooms.forEachIndexed { i, room ->
            val points = DemoHome.simulate(room, DemoHome.defaultProfile(room.kind), start - LEAD_TIME_MS, end, zone)
                .filter { it.time >= start && it.time % RECORD_INTERVAL_MS == 0L }
            points.forEach { p ->
                rooms += RoomSample(room.id, p.time, round1(p.temperature), p.setPoint, round2(p.valve), p.humidity)
                room.channels.forEach { ch -> circuits += CircuitSample(DemoHome.DEVICE_ID, ch, p.time, round2(p.valve)) }
            }
            predictions += probeResults(room.id, i, points, now)
        }
        val weather = (start..end step RECORD_INTERVAL_MS).map { WeatherSample(it, round1(DemoHome.outsideAt(it, zone))) }
        return History(rooms, circuits, weather, predictions)
    }

    /** For every setpoint rise of the last week: when it was reached, and a prediction near it. */
    private fun probeResults(roomId: String, roomIndex: Int, points: List<DemoHome.Point>, now: Long): List<PredictionEntity> =
        points.zipWithNext().mapIndexedNotNull { idx, (a, b) ->
            if (b.time < now - PROBE_DAYS_MS || b.setPoint - a.setPoint < 0.4) return@mapIndexedNotNull null
            // Like ProbeRun.evaluate: only while the raised setpoint lasts, at most 12 h.
            val reached = points.subList(idx + 1, points.size)
                .takeWhile { it.time <= b.time + 12 * 3_600_000L && it.setPoint >= b.setPoint - 0.05 }
                .firstOrNull { it.temperature >= b.setPoint - 0.2 }?.time
                ?: return@mapIndexedNotNull null
            // Repeatable error between −40 and +40 min; about three in four are within ±30 min (a hit).
            val errorMin = (DemoHome.noise(roomIndex + 7, b.time) * 80).roundToInt()
            val predicted = reached - errorMin * 60_000L
            val diff = (reached - predicted) / 60_000L
            PredictionEntity(
                roomId = roomId,
                strategy = Strategy.PHYSICS,
                eventTime = b.time,
                createdAt = b.time - 12 * 3_600_000L,
                targetTemperature = b.setPoint,
                startTemperature = round1(a.temperature),
                predictedReach = predicted,
                modelSource = ModelSource.FIT,
                evaluatedAt = reached + 3_600_000L,
                actualReach = reached,
                outcome = when {
                    abs(diff) <= 30 -> Outcome.HIT
                    diff < 0 -> Outcome.EARLY
                    else -> Outcome.LATE
                },
            )
        }

    /** What the demo "user" wrote about a few rooms. */
    fun descriptions(now: Long): Map<String, RoomDescription> = mapOf(
        "demo-living" to RoomDescription(
            context.getString(R.string.demo_desc_living),
            RoomProperties(
                areaM2 = 28.0, windowCount = 3, windowDirections = listOf(Direction.S, Direction.W),
                glazing = Glazing.DOUBLE, exteriorWalls = 2, floorCovering = FloorCovering.WOOD, usage = Usage.LIVING,
            ),
            now,
        ),
        "demo-bedroom" to RoomDescription(
            context.getString(R.string.demo_desc_bedroom),
            RoomProperties(
                areaM2 = 14.0, windowCount = 1, windowDirections = listOf(Direction.E), glazing = Glazing.DOUBLE,
                floorCovering = FloorCovering.LAMINATE, usage = Usage.BEDROOM, ventilatesAtNight = true,
            ),
            now,
        ),
        "demo-bath" to RoomDescription(
            context.getString(R.string.demo_desc_bath),
            RoomProperties(
                areaM2 = 7.0, windowCount = 1, floorCovering = FloorCovering.TILE, usage = Usage.BATH,
                shower = true, bathtub = false, heatSources = listOf(HeatSource.TOWEL_RADIATOR),
            ),
            now,
        ),
    )

    private fun label(kind: DemoHome.Kind) = context.getString(
        when (kind) {
            DemoHome.Kind.LIVING -> R.string.demo_room_living
            DemoHome.Kind.KITCHEN -> R.string.demo_room_kitchen
            DemoHome.Kind.BEDROOM -> R.string.demo_room_bedroom
            DemoHome.Kind.CHILD -> R.string.demo_room_child
            DemoHome.Kind.OFFICE -> R.string.demo_room_office
            DemoHome.Kind.BATH -> R.string.demo_room_bath
        },
    )

    private fun profileId(roomId: String, index: String) = "$roomId-$index"
    private fun round1(x: Double) = (x * 10).roundToInt() / 10.0
    private fun round2(x: Double) = (x * 100).roundToInt() / 100.0

    private companion object {
        const val PROFILE_1 = "PROFILE_1"
        const val PROFILE_2 = "PROFILE_2"
        /** The simulation starts at the setpoint; two days before the shown part it has settled. */
        const val LEAD_TIME_MS = 2 * 86_400_000L
        const val RECORD_INTERVAL_MS = 15 * 60_000L
        const val HISTORY_MS = 21 * 86_400_000L
        const val PROBE_DAYS_MS = 7 * 86_400_000L
    }
}
