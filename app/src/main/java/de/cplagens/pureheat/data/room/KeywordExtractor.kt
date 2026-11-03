package de.cplagens.pureheat.data.room

import java.util.Locale

/**
 * Rule-based fallback for devices without an on-device model (Gemini Nano): recognises the most
 * common German and English phrasings. Pure Kotlin, so it is unit-tested; the user checks and
 * corrects the result anyway before saving.
 */
object KeywordExtractor {
    // Word boundaries that also work for umlauts (\b is ASCII-only on newer JVMs).
    private const val B = "(?<![\\p{L}\\p{N}])"
    private const val E = "(?![\\p{L}])"
    private const val NUM = "(\\d+(?:[.,]\\d+)?)"

    private val numberWords = mapOf(
        "ein" to 1, "eine" to 1, "einem" to 1, "einer" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5,
        "sechs" to 6, "one" to 1, "a" to 1, "an" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "keine" to 0, "kein" to 0, "no" to 0,
    )
    private val countWord = "(\\d+|${numberWords.keys.sortedByDescending { it.length }.joinToString("|")})"

    fun extract(input: String): RoomProperties {
        val t = input.lowercase(Locale.ROOT).replace('\n', ' ')
        return RoomProperties(
            areaM2 = area(t),
            ceilingHeightM = ceilingHeight(t),
            windowCount = windowCount(t),
            windowDirections = directions(t),
            glazing = glazing(t),
            exteriorWalls = exteriorWalls(t),
            position = position(t),
            aboveUnheated = aboveUnheated(t),
            floorCovering = floorCovering(t),
            shutters = if (has(t, "roll(?:l)?(?:a|ä)den", "rollo", "jalousie", "fensterl(?:a|ä)den", "shutters?", "blinds")) true else null,
            heatSources = heatSources(t),
            usage = usage(t),
            ventilatesAtNight = ventilatesAtNight(t),
        ).sanitized()
    }

    private fun has(t: String, vararg patterns: String) =
        patterns.any { Regex("$B(?:$it)").containsMatchIn(t) }

    private fun number(s: String) = s.replace(',', '.').toDoubleOrNull()
    private fun count(s: String) = s.toIntOrNull() ?: numberWords[s]

    private val areaUnit = "(?:m²|m2|qm|quadratmeter|sqm|square met(?:er|re)s?)$E"

    private fun area(t: String): Double? =
        Regex("$NUM\\s*$areaUnit").findAll(t)
            // "3 m² Fenster", "Fensterfläche 4 m²" is glass, not the room.
            .filterNot { m ->
                val around = t.substring(maxOf(0, m.range.first - 20), minOf(t.length, m.range.last + 12))
                Regex("fenster|window|glas").containsMatchIn(around)
            }
            .firstNotNullOfOrNull { number(it.groupValues[1]) }

    private fun ceilingHeight(t: String): Double? {
        val key = "(?:raumh(?:ö|oe)he|deckenh(?:ö|oe)he|ceiling(?: height)?|room height|h(?:ö|oe)he)"
        Regex("$key\\D{0,15}$NUM\\s*(m|cm)$E").find(t)?.let { return metres(it.groupValues[1], it.groupValues[2]) }
        Regex("$NUM\\s*(m|cm)\\s*(?:hoch|hohe|hohen|high|tall|$key)").find(t)?.let { return metres(it.groupValues[1], it.groupValues[2]) }
        return null
    }

    private fun metres(value: String, unit: String) = number(value)?.let { if (unit == "cm") it / 100 else it }

    private fun windowCount(t: String): Int? {
        val adjective = "(?:(?:gro(?:ß|ss)e?[ns]?|kleine?[ns]?|bodentiefe?[ns]?|large|big|small|floor-to-ceiling)\\s+)?"
        val nouns = "(?:fenster|dachfenster|terrassent(?:ü|ue)r(?:en)?|balkont(?:ü|ue)r(?:en)?|windows?|patio doors?)"
        val counts = Regex("$B$countWord\\s+$adjective$nouns").findAll(t).mapNotNull { count(it.groupValues[1]) }.toList()
        return counts.takeIf { it.isNotEmpty() }?.sum()
    }

    private fun directions(t: String): List<Direction> {
        val out = mutableSetOf<Direction>()
        var rest = t
        // No abbreviations (SW, NO, …): in lower case "so" and "no" are ordinary words.
        val compound = mapOf(
            "s(?:ü|ue)d[- ]?west|south[- ]?west" to Direction.SW,
            "s(?:ü|ue)d[- ]?ost|south[- ]?east" to Direction.SE,
            "nord[- ]?west|north[- ]?west" to Direction.NW,
            "nord[- ]?ost|north[- ]?east" to Direction.NE,
        )
        val simple = mapOf(
            "s(?:ü|ue)d|south" to Direction.S,
            "nord|north" to Direction.N,
            "ost|east" to Direction.E,
            "west" to Direction.W,
        )
        // German suffixes: Süden, Südseite, Südfenster, südlich, Ostausrichtung; English: southern, south-facing.
        val suffix = "(?:en|seite|fenster|lich|liche[nrms]?|ausrichtung|ern|ward|-facing)?"
        for ((pattern, dir) in compound) {
            val r = Regex("$B(?:$pattern)$suffix$E")
            if (r.containsMatchIn(rest)) {
                out += dir
                rest = r.replace(rest, " ")
            }
        }
        for ((pattern, dir) in simple) {
            if (Regex("$B(?:$pattern)$suffix$E").containsMatchIn(rest)) out += dir
        }
        return out.sorted()
    }

    private fun glazing(t: String): Glazing? = when {
        has(t, "dreifach", "3-fach", "3fach", "triple") -> Glazing.TRIPLE
        has(t, "zweifach", "2-fach", "2fach", "doppelverglas", "doppelfenster", "isolierglas", "double[- ]glaz") -> Glazing.DOUBLE
        has(t, "einfachverglas", "einfach verglast", "einfachglas", "single[- ]glaz") -> Glazing.SINGLE
        else -> null
    }

    private fun exteriorWalls(t: String): Int? {
        val walls = "(?:au(?:ß|ss)enw(?:a|ä)nd(?:e|en)?|exterior walls?|outside walls?|external walls?|outer walls?)"
        Regex("$B$countWord\\s+$walls").find(t)?.let { m -> count(m.groupValues[1])?.let { return it } }
        if (has(t, "eckzimmer", "eckraum", "corner room")) return 2
        if (has(t, "innenraum", "keine au(?:ß|ss)enwand", "no exterior wall", "interior room")) return 0
        return null
    }

    private fun aboveUnheated(t: String): Boolean? =
        if (has(t, "(?:ü|ue)ber (?:dem |einem |der |einer )?(?:unbeheizten |kalten )?(?:keller|garage)",
                "above (?:the |an |a )?(?:unheated |cold )?(?:basement|cellar|garage)", "(?:ü|ue)ber unbeheizt")
        ) true else null

    private fun position(t: String): Position? = when {
        has(t, "dachgeschoss", "dachgescho(?:ß|ss)", "unterm dach", "unter dem dach", "dachschr(?:ä|ae)ge", "dachboden",
            "attic", "top floor", "under the roof", "loft") -> Position.ATTIC
        has(t, "obergeschoss", "obergescho(?:ß|ss)", "og$E", "\\d\\.\\s*stock", "erste[nm]? stock", "oben$E",
            "upper floor", "first floor", "second floor", "upstairs") -> Position.UPPER
        has(t, "erdgeschoss", "erdgescho(?:ß|ss)", "eg$E", "parterre", "ground floor", "downstairs") -> Position.GROUND
        // "über dem Keller" is a ground floor room above an unheated cellar, not a cellar room.
        aboveUnheated(t) == null && has(t, "im keller", "kellerraum", "souterrain", "untergeschoss", "basement", "cellar") -> Position.BASEMENT
        else -> null
    }

    private fun floorCovering(t: String): FloorCovering? = when {
        has(t, "fliese", "kachel", "naturstein", "steinboden", "terrazzo", "tiles?$E", "tiled", "stone floor") -> FloorCovering.TILE
        has(t, "teppich", "carpet") -> FloorCovering.CARPET
        has(t, "parkett", "holzboden", "holzdielen", "dielenboden", "dielen$E", "parquet", "hardwood", "wooden floor", "floorboards") -> FloorCovering.WOOD
        has(t, "laminat", "vinyl", "linoleum", "designboden", "laminate") -> FloorCovering.LAMINATE
        else -> null
    }

    private fun heatSources(t: String): List<HeatSource> = buildList {
        if (has(t, "handtuch", "towel")) add(HeatSource.TOWEL_RADIATOR)
        if (has(t, "kamin", "kaminofen", "schwedenofen", "holzofen", "kachelofen", "fireplace", "wood ?burner", "stove")) add(HeatSource.STOVE)
        if (has(t, "computer", "pc$E", "rechner", "server", "gaming", "fernseher", "elektronik", "electronics", "tv$E")) add(HeatSource.ELECTRONICS)
        if (has(t, "herd", "backofen", "kochen", "cooking", "oven")) add(HeatSource.COOKING)
    }

    private fun usage(t: String): Usage? = when {
        has(t, "kinderzimmer", "kids'? room", "children'?s room", "nursery") -> Usage.CHILD
        has(t, "schlafzimmer", "schlafraum", "bedroom") -> Usage.BEDROOM
        has(t, "wohnzimmer", "wohnraum", "wohnküche", "living room", "lounge") -> Usage.LIVING
        has(t, "küche", "kueche", "kitchen") -> Usage.KITCHEN
        has(t, "badezimmer", "bad$E", "duschbad", "gäste-?wc", "bathroom", "shower room") -> Usage.BATH
        has(t, "büro", "buero", "arbeitszimmer", "homeoffice", "home office", "office", "study") -> Usage.OFFICE
        has(t, "flur", "diele$E", "treppenhaus", "eingang", "hallway", "corridor", "hall$E") -> Usage.HALL
        else -> null
    }

    private fun ventilatesAtNight(t: String): Boolean? {
        val night = "(?:nachts|nacht|abends|beim schlafen|at night|overnight|while sleeping)"
        val open = "(?:gekippt|offen|auf$E|ge(?:ö|oe)ffnet|l(?:ü|ue)ft|open|tilted|ajar)"
        return if (Regex("$night[^.;]{0,40}$open|$open[^.;]{0,40}$night").containsMatchIn(t)) true else null
    }
}
