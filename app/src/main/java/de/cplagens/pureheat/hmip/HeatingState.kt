package de.cplagens.pureheat.hmip

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * One heating circuit = one valve channel of a floor heating controller.
 *
 * For motorised controllers (HmIP-FALMOT-C8 = `FLOOR_TERMINAL_BLOCK_8`, FALMOT-C12 = `_12`)
 * these are `FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL`s with `valvePosition` and `valveState`.
 */
data class HeatingCircuit(
    val deviceId: String,
    val deviceLabel: String,
    val deviceType: String,
    val channelIndex: Int,
    val channelLabel: String?,
    /** 0.0–1.0, or null if the device does not report a position for this channel. */
    val valvePosition: Double?,
    val valveState: String?,
) {
    val isHeating: Boolean get() = (valvePosition ?: 0.0) > 0.0

    /** True if the valve drive reports a problem (anything other than normal operation). */
    val hasValveProblem: Boolean get() = valveState != null && valveState !in NORMAL_VALVE_STATES

    private companion object {
        val NORMAL_VALVE_STATES = setOf("ADAPTION_DONE")
    }
}

/** German description of a `ValveState` value. */
fun valveStateText(state: String?): String = when (state) {
    null -> "unbekannt"
    "ADAPTION_DONE" -> "OK"
    "STATE_NOT_AVAILABLE" -> "kein Status"
    "RUN_TO_START" -> "fährt in Startposition"
    "WAIT_FOR_ADAPTION" -> "wartet auf Anlernen"
    "ADAPTION_IN_PROGRESS" -> "lernt an"
    "TOO_TIGHT" -> "Ventil schwergängig"
    "ADJUSTMENT_TOO_BIG" -> "Stellweg zu groß"
    "ADJUSTMENT_TOO_SMALL" -> "Stellweg zu klein / nicht angeschlossen"
    "ERROR_POSITION" -> "Positionsfehler"
    else -> state
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
) {
    val isHeating: Boolean
        get() = circuits.any { it.isHeating } || (valvePosition ?: 0.0) > 0.0
}

data class HeatingState(
    val rooms: List<Room>,
    /** Circuits that are not assigned to any room. */
    val unassignedCircuits: List<HeatingCircuit>,
)

/**
 * Turns the raw `getCurrentState` JSON into rooms and heating circuits.
 *
 * Deliberately lenient: field names come from community reverse-engineering
 * (homematicip-rest-api) and vary between models, so every value is optional.
 *
 * Room assignment: the HEATING group lists its members in `channels`
 * (`[{deviceId, channelIndex}]`); the actuator channels' own `groups` array is often
 * empty, so both are checked.
 */
object HeatingStateParser {

    private data class ChannelRef(val deviceId: String, val channelIndex: Int)

    fun parse(state: JsonObject): HeatingState {
        val devices = state["devices"].asObject()
        val groups = state["groups"].asObject()

        // Collect circuits from all floor heating controllers.
        val circuits = linkedMapOf<ChannelRef, Pair<HeatingCircuit, List<String>>>()
        for ((deviceId, deviceEl) in devices) {
            val device = deviceEl.asObject()
            val type = device.str("type") ?: continue
            if (!type.contains("FLOOR_TERMINAL_BLOCK")) continue
            val label = device.str("label") ?: deviceId
            for ((indexKey, channelEl) in device["functionalChannels"].asObject()) {
                val channel = channelEl.asObject()
                val channelType = channel.str("functionalChannelType") ?: continue
                if (channelType.startsWith("DEVICE_BASE")) continue
                val isValveChannel = channelType == "FLOOR_TERMINAL_BLOCK_MECHANIC_CHANNEL" ||
                    channelType == "FLOOR_TERMINAL_BLOCK_CHANNEL" ||
                    channel.containsKey("valvePosition") || channel.containsKey("valveState")
                if (!isValveChannel) continue
                val index = channel.int("index") ?: indexKey.toIntOrNull() ?: -1
                val circuit = HeatingCircuit(
                    deviceId = deviceId,
                    deviceLabel = label,
                    deviceType = type,
                    channelIndex = index,
                    channelLabel = channel.str("label")?.takeIf { it.isNotBlank() },
                    valvePosition = channel.dbl("valvePosition"),
                    valveState = channel.str("valveState"),
                )
                val groupIds = (channel["groups"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                    .orEmpty()
                circuits[ChannelRef(deviceId, index)] = circuit to groupIds
            }
        }

        val assigned = mutableSetOf<ChannelRef>()
        val rooms = groups.mapNotNull { (groupId, groupEl) ->
            val group = groupEl.asObject()
            if (group.str("type") != "HEATING") return@mapNotNull null

            val memberRefs = (group["channels"] as? JsonArray).orEmpty().mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val dev = obj.str("deviceId") ?: return@mapNotNull null
                val idx = obj.int("channelIndex") ?: return@mapNotNull null
                ChannelRef(dev, idx)
            }.toSet()
            val roomRefs = circuits.filter { (ref, pair) -> ref in memberRefs || groupId in pair.second }.keys
            assigned += roomRefs

            Room(
                id = groupId,
                label = group.str("label") ?: groupId,
                actualTemperature = group.dbl("actualTemperature"),
                setPointTemperature = group.dbl("setPointTemperature"),
                humidity = group.int("humidity"),
                valvePosition = group.dbl("valvePosition"),
                windowState = group.str("windowState"),
                controlMode = group.str("controlMode"),
                circuits = roomRefs.map { circuits.getValue(it).first }.sortedBy { it.channelIndex },
            )
        }.sortedBy { it.label.lowercase() }

        return HeatingState(
            rooms = rooms,
            unassignedCircuits = circuits.filterKeys { it !in assigned }.values.map { it.first }
                .sortedWith(compareBy({ it.deviceLabel }, { it.channelIndex })),
        )
    }

    private fun JsonElement?.asObject(): JsonObject = (this as? JsonObject) ?: JsonObject(emptyMap())
    private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
    private fun JsonObject.str(key: String): String? = prim(key)?.takeIf { it.isString }?.content
    private fun JsonObject.dbl(key: String): Double? = prim(key)?.doubleOrNull
    private fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull ?: prim(key)?.doubleOrNull?.toInt()
}
