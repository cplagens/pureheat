package de.cplagens.pureheat.data.room

import de.cplagens.pureheat.data.model.RoomModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

enum class Direction { N, NE, E, SE, S, SW, W, NW }
enum class Glazing { SINGLE, DOUBLE, TRIPLE }
enum class Position { BASEMENT, GROUND, UPPER, ATTIC }
enum class FloorCovering { TILE, WOOD, LAMINATE, CARPET }
enum class HeatSource { TOWEL_RADIATOR, STOVE, ELECTRONICS, COOKING }
enum class Usage { LIVING, BEDROOM, CHILD, KITCHEN, BATH, OFFICE, HALL }

/**
 * What the user knows about a room and the history can't tell: size, windows and their direction,
 * building envelope, floor covering, other heat sources and habits. Every field is optional.
 * Used as starting values for the room model (see [RoomPriors]) and later as features for
 * solar gain (south windows) and comfort times (usage).
 */
@Serializable
data class RoomProperties(
    val areaM2: Double? = null,
    val ceilingHeightM: Double? = null,
    val windowCount: Int? = null,
    val windowDirections: List<Direction> = emptyList(),
    val glazing: Glazing? = null,
    val exteriorWalls: Int? = null,
    val position: Position? = null,
    /** Above an unheated cellar or garage. */
    val aboveUnheated: Boolean? = null,
    val floorCovering: FloorCovering? = null,
    val shutters: Boolean? = null,
    val heatSources: List<HeatSource> = emptyList(),
    val usage: Usage? = null,
    /** Window open or tilted at night. */
    val ventilatesAtNight: Boolean? = null,
) {
    val isEmpty: Boolean get() = this == RoomProperties()

    /** Fields set here win; the rest comes from [other] (AI result first, keywords as fallback). */
    fun mergedWith(other: RoomProperties) = RoomProperties(
        areaM2 = areaM2 ?: other.areaM2,
        ceilingHeightM = ceilingHeightM ?: other.ceilingHeightM,
        windowCount = windowCount ?: other.windowCount,
        windowDirections = windowDirections.ifEmpty { other.windowDirections },
        glazing = glazing ?: other.glazing,
        exteriorWalls = exteriorWalls ?: other.exteriorWalls,
        position = position ?: other.position,
        aboveUnheated = aboveUnheated ?: other.aboveUnheated,
        floorCovering = floorCovering ?: other.floorCovering,
        shutters = shutters ?: other.shutters,
        heatSources = heatSources.ifEmpty { other.heatSources },
        usage = usage ?: other.usage,
        ventilatesAtNight = ventilatesAtNight ?: other.ventilatesAtNight,
    )

    /** Drops implausible values (typos, misheard speech, model hallucinations). */
    fun sanitized() = copy(
        areaM2 = areaM2?.takeIf { it in 1.0..500.0 },
        ceilingHeightM = ceilingHeightM?.takeIf { it in 1.8..8.0 },
        windowCount = windowCount?.takeIf { it in 0..30 },
        exteriorWalls = exteriorWalls?.takeIf { it in 0..4 },
        windowDirections = windowDirections.distinct().sorted(),
        heatSources = heatSources.distinct().sorted(),
    )

    companion object {
        /**
         * Lenient reading of a JSON object (from the on-device model): unknown keys, wrong types
         * and unknown enum values are ignored instead of failing the whole result.
         */
        fun fromJson(o: JsonObject): RoomProperties {
            fun prim(key: String): JsonPrimitive? = (o[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
            fun double(key: String) = prim(key)?.let { it.doubleOrNull ?: it.content.replace(',', '.').toDoubleOrNull() }
            fun int(key: String) = prim(key)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
            fun bool(key: String) = prim(key)?.booleanOrNull
            fun <E : Enum<E>> enum(values: Array<E>, e: JsonElement?): E? =
                (e as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.let { s ->
                    values.firstOrNull { it.name.equals(s.replace('-', '_').replace(' ', '_'), ignoreCase = true) }
                }
            fun <E : Enum<E>> enumList(values: Array<E>, key: String): List<E> = when (val e = o[key]) {
                is JsonArray -> e.mapNotNull { enum(values, it) }
                is JsonPrimitive -> listOfNotNull(enum(values, e))
                else -> emptyList()
            }
            return RoomProperties(
                areaM2 = double("areaM2"),
                ceilingHeightM = double("ceilingHeightM"),
                windowCount = int("windowCount"),
                windowDirections = enumList(Direction.entries.toTypedArray(), "windowDirections"),
                glazing = enum(Glazing.entries.toTypedArray(), o["glazing"]),
                exteriorWalls = int("exteriorWalls"),
                position = enum(Position.entries.toTypedArray(), o["position"]),
                aboveUnheated = bool("aboveUnheated"),
                floorCovering = enum(FloorCovering.entries.toTypedArray(), o["floorCovering"]),
                shutters = bool("shutters"),
                heatSources = enumList(HeatSource.entries.toTypedArray(), "heatSources"),
                usage = enum(Usage.entries.toTypedArray(), o["usage"]),
                ventilatesAtNight = bool("ventilatesAtNight"),
            ).sanitized()
        }
    }
}

/** Stored per room: the user's own words plus the structured result (which the user may have edited). */
@Serializable
data class RoomDescription(
    val text: String = "",
    val properties: RoomProperties = RoomProperties(),
    val updatedAt: Long = 0,
)

/**
 * Starting values for the room model from the properties, used only while the model hasn't
 * learned k and P from the history yet. Rough physics: more exterior surface and glass → faster
 * heat loss (k); an insulating floor covering → less heating power (P) and a slower response;
 * a high ceiling → more air to heat. The fitted model always wins once it exists.
 */
object RoomPriors {
    fun model(base: RoomModel, p: RoomProperties?): RoomModel {
        if (p == null || p.isEmpty) return base
        val k = (base.k ?: return base) * kFactor(p)
        val power = (base.power ?: return base) * powerFactor(p)
        val dead = (base.deadTimeHours ?: return base) + deadTimeOffset(p)
        return base.copy(k = k, power = power, deadTimeHours = dead.coerceAtLeast(0.5))
    }

    fun kFactor(p: RoomProperties): Double {
        var f = 1.0
        p.exteriorWalls?.let { f *= when (it) { 0 -> 0.7; 1 -> 0.9; 2 -> 1.1; else -> 1.3 } }
        if (p.position == Position.ATTIC) f *= 1.2
        if (p.aboveUnheated == true) f *= 1.1
        // Glass share of the floor area (~1.5 m² per window); 15 % is typical and neutral.
        val area = p.areaM2
        val windows = p.windowCount
        if (area != null && windows != null) f *= (0.8 + 0.2 * (windows * 1.5 / area) / 0.15).coerceIn(0.85, 1.35)
        p.glazing?.let { f *= when (it) { Glazing.SINGLE -> 1.3; Glazing.DOUBLE -> 1.0; Glazing.TRIPLE -> 0.85 } }
        if (p.ventilatesAtNight == true) f *= 1.1
        return f.coerceIn(0.5, 2.0)
    }

    fun powerFactor(p: RoomProperties): Double {
        var f = 1.0
        p.floorCovering?.let {
            f *= when (it) {
                FloorCovering.TILE -> 1.15
                FloorCovering.LAMINATE -> 0.9
                FloorCovering.WOOD -> 0.85
                FloorCovering.CARPET -> 0.7
            }
        }
        p.ceilingHeightM?.let { f *= (2.5 / it).coerceIn(0.7, 1.2) }
        return f.coerceIn(0.5, 1.5)
    }

    fun deadTimeOffset(p: RoomProperties): Double = when (p.floorCovering) {
        FloorCovering.TILE -> -0.25
        FloorCovering.WOOD, FloorCovering.LAMINATE -> 0.25
        FloorCovering.CARPET -> 0.5
        null -> 0.0
    }
}
