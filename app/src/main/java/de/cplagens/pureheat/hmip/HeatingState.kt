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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** One heating circuit = one channel of a floor heating actuator (e.g. HmIP-FAL230-C10). */
data class HeatingCircuit(
    val deviceId: String,
    val deviceLabel: String,
    val deviceType: String,
    val channelIndex: Int,
    val channelLabel: String?,
    /** 0.0–1.0, or null if the device does not report a position for this channel. */
    val valvePosition: Double?,
    val valveState: String?,
    /** `emergencyOperationActive`: runs a fixed emergency value (lost its thermostat). */
    val emergency: Boolean = false,
) {
    val isHeating: Boolean get() = (valvePosition ?: 0.0) > 0.0
}

/** Reference to one of the (up to six) weekly heating profiles of a room. */
data class ProfileRef(
    /** "PROFILE_1" … "PROFILE_6" */
    val index: String,
    val profileId: String,
    val name: String?,
    val visible: Boolean,
) {
    /** "1" … "6"; the display name (user's name or "Profile n") is built in `ui/ProfileTexts.kt`. */
    val number: String get() = index.substringAfterLast('_')
}

/** A room as configured in the app (HEATING group in the API). */
data class Room(
    val id: String,
    val label: String,
    val actualTemperature: Double?,
    val setPointTemperature: Double?,
    val humidity: Int?,
    /** Aggregated valve position reported by the group, 0.0–1.0. */
    val valvePosition: Double?,
    val windowState: String?,
    val controlMode: String?,
    val circuits: List<HeatingCircuit>,
    val activeProfile: String? = null,
    val profiles: List<ProfileRef> = emptyList(),
    val minTemperature: Double = 5.0,
    val maxTemperature: Double = 30.0,
) {
    val isHeating: Boolean
        get() = circuits.any { it.isHeating } || (valvePosition ?: 0.0) > 0.0
}

data class HeatingState(
    val rooms: List<Room>,
    /** Circuits that are not assigned to any room. */
    val unassignedCircuits: List<HeatingCircuit>,
    /** Current outside temperature from the cloud's weather service (`home.weather.temperature`). */
    val outsideTemperature: Double? = null,
    /** Outside relative humidity in % (`home.weather.humidity`). */
    val outsideHumidity: Int? = null,
    /** Outside water vapour in g/m³ (`home.weather.vaporAmount`, checked against T and %rH). */
    val outsideVapour: Double? = null,
    /** Location of the home, already rounded to ~1 km ([GeoPoint.rounded]); used for the forecast. */
    val location: GeoPoint? = null,
    /** Valve and device problems, most severe first ([Faults.sorted]). */
    val faults: List<Fault> = emptyList(),
)

/** Coordinates rounded to 2 decimals (~1 km) – the exact address never leaves the app. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    companion object {
        fun rounded(latitude: Double, longitude: Double): GeoPoint? {
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            if (latitude == 0.0 && longitude == 0.0) return null
            fun r(x: Double) = Math.round(x * 100) / 100.0
            return GeoPoint(r(latitude), r(longitude))
        }
    }
}

/**
 * Turns the raw `getCurrentState` JSON into rooms and heating circuits.
 *
 * Deliberately lenient: field names come from community reverse-engineering and
 * vary between actuator models, so every value is optional.
 */
object HeatingStateParser {

    fun parse(state: JsonObject): HeatingState {
        val devices = state["devices"].asObject()
        val groups = state["groups"].asObject()

        // Collect circuits from all floor heating actuators.
        val circuits = mutableListOf<Pair<HeatingCircuit, List<String>>>()
        for ((deviceId, deviceEl) in devices) {
            val device = deviceEl.asObject()
            val type = device.str("type") ?: continue
            if (!type.contains("FLOOR_TERMINAL_BLOCK")) continue
            val label = device.str("label") ?: deviceId
            val channels = device["functionalChannels"].asObject()
            for ((indexKey, channelEl) in channels) {
                val channel = channelEl.asObject()
                val channelType = channel.str("functionalChannelType") ?: continue
                if (channelType == "DEVICE_BASE" || channelType.endsWith("_BASE")) continue
                val hasValveInfo = channel.containsKey("valvePosition") || channel.containsKey("valveState")
                if (!hasValveInfo) continue
                val circuit = HeatingCircuit(
                    deviceId = deviceId,
                    deviceLabel = label,
                    deviceType = type,
                    channelIndex = channel.int("index") ?: indexKey.toIntOrNull() ?: -1,
                    channelLabel = channel.str("label")?.takeIf { it.isNotBlank() },
                    valvePosition = channel.dbl("valvePosition"),
                    valveState = channel.str("valveState"),
                    emergency = channel.bool("emergencyOperationActive") == true,
                )
                val groupIds = (channel["groups"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                    ?: emptyList()
                // Unused outputs (nothing connected, e.g. valveState ADJUSTMENT_TOO_SMALL) belong to no
                // group and report no position; they would only clutter the dashboard.
                if (groupIds.isEmpty() && circuit.valvePosition == null) continue
                circuits += circuit to groupIds
            }
        }

        val assigned = mutableSetOf<HeatingCircuit>()
        val rooms = groups.mapNotNull { (groupId, groupEl) ->
            val group = groupEl.asObject()
            if (group.str("type") != "HEATING") return@mapNotNull null
            val roomCircuits = circuits.filter { (_, ids) -> groupId in ids }.map { it.first }
            assigned += roomCircuits
            Room(
                id = groupId,
                label = group.str("label") ?: groupId,
                actualTemperature = group.dbl("actualTemperature"),
                setPointTemperature = group.dbl("setPointTemperature"),
                humidity = group.int("humidity"),
                valvePosition = group.dbl("valvePosition"),
                windowState = group.str("windowState"),
                controlMode = group.str("controlMode"),
                circuits = roomCircuits.sortedBy { it.channelIndex },
                activeProfile = group.str("activeProfile"),
                profiles = group["profiles"].asObject().mapNotNull { (key, el) ->
                    val p = el.asObject()
                    if (p.bool("enabled") == false) return@mapNotNull null
                    ProfileRef(
                        index = p.str("index") ?: key,
                        profileId = p.str("profileId") ?: return@mapNotNull null,
                        name = p.str("name")?.trim()?.takeIf { it.isNotEmpty() },
                        visible = p.bool("visible") ?: true,
                    )
                }.sortedBy { it.index },
                minTemperature = group.dbl("minTemperature") ?: 5.0,
                maxTemperature = group.dbl("maxTemperature") ?: 30.0,
            )
        }.sortedBy { it.label.lowercase() }

        val roomLabels = rooms.associate { it.id to it.label }
        val faults = mutableListOf<Fault>()
        for ((c, ids) in circuits) {
            val roomId = ids.firstOrNull { it in roomLabels }
            fun add(kind: FaultKind, code: String) = faults.add(
                Fault(kind, code, c.deviceId, c.deviceLabel, c.channelIndex, c.channelLabel, roomId, roomLabels[roomId]),
            )
            Faults.valveKind(c.valveState)?.let { add(it, c.valveState.orEmpty()) }
            if (c.emergency) add(FaultKind.EMERGENCY, "emergencyOperationActive")
        }
        faults += deviceFaults(devices, roomLabels)

        return HeatingState(
            rooms = rooms,
            unassignedCircuits = circuits.map { it.first }.filter { it !in assigned },
            outsideTemperature = state["home"].asObject()["weather"].asObject().dbl("temperature"),
            outsideHumidity = state["home"].asObject()["weather"].asObject().int("humidity"),
            outsideVapour = state["home"].asObject()["weather"].asObject().dbl("vaporAmount"),
            location = state["home"].asObject()["location"].asObject().let { loc ->
                // The cloud sends the coordinates as strings.
                val lat = loc.dbl("latitude") ?: loc.str("latitude")?.toDoubleOrNull()
                val lon = loc.dbl("longitude") ?: loc.str("longitude")?.toDoubleOrNull()
                if (lat != null && lon != null) GeoPoint.rounded(lat, lon) else null
            },
            faults = Faults.sorted(faults),
        )
    }

    /**
     * Device-wide problems (unreachable, low battery, overheated, …) of floor heating actuators and of
     * every device that belongs to a heating room (thermostats, window contacts).
     */
    private fun deviceFaults(devices: JsonObject, roomLabels: Map<String, String>): List<Fault> = buildList {
        for ((deviceId, deviceEl) in devices) {
            val device = deviceEl.asObject()
            val type = device.str("type") ?: continue
            val channels = device["functionalChannels"].asObject().values.map { it.asObject() }
            val roomIds = channels.flatMap { ch ->
                (ch["groups"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
            }.filter { it in roomLabels }.distinct()
            if (!type.contains("FLOOR_TERMINAL_BLOCK") && roomIds.isEmpty()) continue
            val base = channels.firstOrNull { it.str("functionalChannelType")?.startsWith("DEVICE_BASE") == true }
                ?: continue
            val label = device.str("label") ?: deviceId
            val roomId = roomIds.singleOrNull()
            fun add(kind: FaultKind, code: String) =
                add(Fault(kind, code, deviceId, label, roomId = roomId, roomLabel = roomLabels[roomId]))
            // An unreachable device's other flags are stale; one message is enough.
            if (base.bool("unreach") == true) {
                add(FaultKind.UNREACHABLE, "unreach")
                continue
            }
            if (base.bool("lowBat") == true) add(FaultKind.LOW_BATTERY, "lowBat")
            Faults.DEVICE_FLAGS.filter { base.bool(it) == true }.forEach { add(FaultKind.DEVICE, it) }
        }
    }

    private fun JsonElement?.asObject(): JsonObject = (this as? JsonObject) ?: JsonObject(emptyMap())
    private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
    private fun JsonObject.str(key: String): String? = prim(key)?.takeIf { it.isString }?.content
    private fun JsonObject.dbl(key: String): Double? = prim(key)?.doubleOrNull
    private fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull ?: prim(key)?.doubleOrNull?.toInt()
    private fun JsonObject.bool(key: String): Boolean? = prim(key)?.booleanOrNull
}
