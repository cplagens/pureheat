/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.profile

import de.cplagens.pureheat.hmip.HeatingPeriod
import de.cplagens.pureheat.hmip.HeatingProfileCodec
import de.cplagens.pureheat.hmip.MINUTES_PER_DAY
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.ProfileEditing
import de.cplagens.pureheat.hmip.Weekday
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.Locale

/** One instruction from a description: these [days] get this [base] and/or these [periods]. */
data class DayRule(val days: Set<Weekday>, val base: Double? = null, val periods: List<HeatingPeriod> = emptyList())

/**
 * Text → weekly heating profile suggestion. The on-device model (or the German/English keyword
 * fallback) produces [DayRule]s; [apply] turns them into valid days (15-min grid, 0.5 °C steps,
 * room limits, no overlaps, at most [ProfileEditing.MAX_PERIODS]). Days not mentioned stay as
 * they are. Nothing is written – the user checks the suggestion and saves it the normal way.
 */
object ProfileFromText {
    /** Used when a time range has no temperature at all. */
    const val DEFAULT_COMFORT = 21.0

    fun apply(current: Map<Weekday, ProfileDay>, rules: List<DayRule>, minT: Double, maxT: Double): Map<Weekday, ProfileDay> {
        val result = current.toMutableMap()
        for (day in Weekday.entries) {
            val forDay = rules.filter { day in it.days }
            if (forDay.isEmpty()) continue
            val old = current[day] ?: ProfileDay(DEFAULT_BASE, emptyList())
            val base = forDay.lastOrNull { it.base != null }?.base ?: old.baseTemperature
            val periods = forDay.flatMap { it.periods }
            result[day] = ProfileDay(
                baseTemperature = ProfileEditing.snapTemperature(base, minT, maxT),
                periods = if (forDay.any { it.periods.isNotEmpty() }) clean(periods, minT, maxT) else old.periods,
            )
        }
        return result
    }

    /** Snap, clamp, sort, cut overlaps (the earlier phase wins), drop empty ones, limit the count. */
    fun clean(periods: List<HeatingPeriod>, minT: Double, maxT: Double): List<HeatingPeriod> {
        val snapped = periods.map {
            HeatingPeriod(
                ProfileEditing.snapMinutes(it.start),
                ProfileEditing.snapMinutes(it.end),
                ProfileEditing.snapTemperature(it.temperature, minT, maxT),
            )
        }.filter { it.end > it.start }.sortedBy { it.start }
        val out = mutableListOf<HeatingPeriod>()
        for (p in snapped) {
            val prev = out.lastOrNull()
            val start = if (prev != null && p.start < prev.end) prev.end else p.start
            if (p.end - start >= ProfileEditing.STEP_MINUTES) out += p.copy(start = start)
        }
        return out.take(ProfileEditing.MAX_PERIODS)
    }

    /** `{"rules":[{"days":["MONDAY"],"base":18,"periods":[{"start":"06:00","end":"08:00","temperature":22}]}]}` */
    fun fromModelJson(o: JsonObject): List<DayRule> = (o["rules"] as? JsonArray).orEmpty().mapNotNull { el ->
        val r = el as? JsonObject ?: return@mapNotNull null
        val days = (r["days"] as? JsonArray).orEmpty().mapNotNull { d ->
            (d as? JsonPrimitive)?.content?.trim()?.uppercase(Locale.ROOT)?.let { name -> Weekday.entries.firstOrNull { it.name == name } }
        }.toSet()
        if (days.isEmpty()) return@mapNotNull null
        val base = (r["base"] as? JsonPrimitive)?.doubleOrNull
        val periods = (r["periods"] as? JsonArray).orEmpty().mapNotNull { p ->
            val po = p as? JsonObject ?: return@mapNotNull null
            val start = HeatingProfileCodec.parseTime((po["start"] as? JsonPrimitive)?.content) ?: return@mapNotNull null
            val end = HeatingProfileCodec.parseTime((po["end"] as? JsonPrimitive)?.content) ?: return@mapNotNull null
            val t = (po["temperature"] as? JsonPrimitive)?.doubleOrNull ?: DEFAULT_COMFORT
            HeatingPeriod(start, if (end == 0) MINUTES_PER_DAY else end, t)
        }
        DayRule(days, base, periods).takeIf { base != null || periods.isNotEmpty() }
    }

    // --- Keyword fallback (German and English) ------------------------------------------------

    private const val B = "(?<![\\p{L}\\p{N}])"
    private const val E = "(?![\\p{L}])"
    private const val DEFAULT_BASE = 17.0

    // Single days only by full name: short forms like "so", "do", "mo" are ordinary words and only
    // count inside a range ("Mo-Fr").
    private val dayWords: List<Pair<Regex, Weekday>> = listOf(
        "montags?|monday" to Weekday.MONDAY,
        "dienstags?|tuesday" to Weekday.TUESDAY,
        "mittwochs?|wednesday" to Weekday.WEDNESDAY,
        "donnerstags?|thursday" to Weekday.THURSDAY,
        "freitags?|friday" to Weekday.FRIDAY,
        "samstags?|sonnabends?|saturday" to Weekday.SATURDAY,
        "sonntags?|sunday" to Weekday.SUNDAY,
    ).map { (p, d) -> Regex("$B(?:$p)$E") to d }

    private val englishShort = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    private val germanShort = listOf("mo", "di", "mi", "do", "fr", "sa", "so")

    private val dayAlternation = "(?:montags?|dienstags?|mittwochs?|donnerstags?|freitags?|samstags?|sonnabends?|sonntags?|" +
        "monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun|mo|di|mi|do|fr|sa|so)\\.?"
    private val dayRange = Regex("$B($dayAlternation)\\s*(?:-|–|bis|to|through|till|until)\\s*($dayAlternation)$E")

    private val groups: List<Pair<Regex, Set<Weekday>>> = listOf(
        "werktags?|wochentags?|unter der woche|arbeitstage?n?|weekdays?|workdays?|working days" to Weekday.WORKDAYS,
        "am wochenende|wochenende|wochenendes|weekends?|at the weekend|on weekends" to Weekday.WEEKEND,
        "t(?:ä|ae)glich|jeden tag|alle tage|immer|daily|every day|all days|always" to Weekday.entries.toSet(),
    ).map { (p, d) -> Regex("$B(?:$p)$E") to d }

    private val baseWords = Regex(
        "$B(?:sonst|ansonsten|au(?:ß|ss)erhalb|rest(?:liche|lichen)?|nachts|nacht|abgesenkt|absenkung|grundtemperatur|basis|" +
            "otherwise|else|rest of the time|at night|night|setback|base|outside (?:these|those) times)$E",
    )

    // (?!\d) keeps "20-22 Grad" from being read as the time range 20–2.
    private const val CLOCK = "(\\d{1,2})(?:[:.](\\d{2}))?(?!\\d)\\s*(am|pm|a\\.m\\.|p\\.m\\.)?\\s*(?:uhr|h|o'clock)?"
    private val timeRange = Regex("$B(?:von\\s+|from\\s+|zwischen\\s+|between\\s+)?$CLOCK\\s*(?:-|–|bis|to|until|till|and|und)\\s*$CLOCK(?!\\s*(?:°|grad|degrees?|celsius))")
    private val temperature = Regex("$B(\\d{1,2}(?:[.,]\\d)?)\\s*(?:°\\s*c?|grad|degrees?|celsius)$E")

    fun fromKeywords(input: String): List<DayRule> {
        val rules = mutableListOf<DayRule>()
        var lastTemp: Double? = null
        for (sentence in input.split(Regex("[;\\n!?]|\\.(?=\\s|$)"))) {
            var days: Set<Weekday>? = null
            // "Werktags 6-8 Uhr 22 Grad, sonst 18 Grad": "sonst" means the days of this sentence.
            val sentenceDays = mutableSetOf<Weekday>()
            val pending = mutableListOf<IntRange>()
            // Not at decimal commas: "21,5 °C" stays one clause.
            for (raw in sentence.split(Regex(",(?!\\d)"))) {
                val clause = raw.lowercase(Locale.ROOT)
                parseDays(clause, raw)?.let { days = it; sentenceDays += it }
                val ranges = parseRanges(clause)
                val temps = temperature.findAll(clause).mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }.toList()
                val target = days ?: Weekday.entries.toSet()
                if (ranges.isEmpty() && temps.isNotEmpty() && baseWords.containsMatchIn(clause)) {
                    rules += DayRule(sentenceDays.ifEmpty { Weekday.entries.toSet() }, base = temps.first())
                    continue
                }
                pending += ranges
                if (temps.isNotEmpty() && pending.isNotEmpty()) {
                    val t = temps.first()
                    lastTemp = t
                    rules += DayRule(target, periods = pending.map { HeatingPeriod(it.first, it.last, t) })
                    pending.clear()
                }
            }
            if (pending.isNotEmpty()) {
                val t = lastTemp ?: DEFAULT_COMFORT
                rules += DayRule(days ?: Weekday.entries.toSet(), periods = pending.map { HeatingPeriod(it.first, it.last, t) })
            }
        }
        return rules
    }

    /** Short day name written with a capital and followed by a time: "Fr 6:30", "Sa. 8-12", "Sun 9". */
    private val shortDayBeforeTime = Regex("(?<![\\p{L}])(Mo|Di|Mi|Do|Fr|Sa|So|Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\.?:?\\s*(?=\\d)")

    /** [clause] is lower case, [raw] the original (for capitalised short day names). */
    private fun parseDays(clause: String, raw: String): Set<Weekday>? {
        val out = mutableSetOf<Weekday>()
        if (!dayRange.containsMatchIn(clause)) {
            shortDayBeforeTime.findAll(raw).forEach { m -> dayOf(m.groupValues[1].lowercase(Locale.ROOT))?.let { out += it } }
        }
        var rest = clause
        dayRange.findAll(clause).forEach { m ->
            val from = dayOf(m.groupValues[1])
            val to = dayOf(m.groupValues[2])
            if (from != null && to != null) {
                var i = from.ordinal
                while (true) {
                    out += Weekday.entries[i]
                    if (i == to.ordinal) break
                    i = (i + 1) % 7
                }
            }
            rest = rest.replace(m.value, " ")
        }
        groups.forEach { (r, d) -> if (r.containsMatchIn(rest)) { out += d; rest = r.replace(rest, " ") } }
        dayWords.forEach { (r, d) -> if (r.containsMatchIn(rest)) out += d }
        return out.ifEmpty { null }
    }

    /** "Mo", "Montag", "mon", "Monday", "Fr." … → weekday (for the ends of a range). */
    private fun dayOf(word: String): Weekday? {
        val w = word.trim().trimEnd('.')
        if (w.startsWith("sonnabend")) return Weekday.SATURDAY
        englishShort.indexOf(w.take(3)).takeIf { it >= 0 }?.let { return Weekday.entries[it] }
        return germanShort.indexOf(w.take(2)).takeIf { it >= 0 }?.let { Weekday.entries[it] }
    }

    private fun parseRanges(clause: String): List<IntRange> = timeRange.findAll(clause).mapNotNull { m ->
        val g = m.groupValues
        // "6-8pm": am/pm given once applies to both ends; "10am-2pm": each end has its own.
        var end = minutes(g[4], g[5], g[6].ifEmpty { g[3] }) ?: return@mapNotNull null
        var start = minutes(g[1], g[2], g[3].ifEmpty { g[6] }) ?: return@mapNotNull null
        if (start >= end && g[3].isEmpty()) start = minutes(g[1], g[2], "") ?: return@mapNotNull null
        if (end == 0) end = MINUTES_PER_DAY
        if (end <= start) return@mapNotNull null
        start..end
    }.toList()

    private fun minutes(h: String, m: String, ampm: String): Int? {
        var hour = h.toIntOrNull() ?: return null
        val minute = m.toIntOrNull() ?: 0
        if (ampm.startsWith("p") && hour < 12) hour += 12
        if (ampm.startsWith("a") && hour == 12) hour = 0
        if (hour > 24 || minute > 59) return null
        return (hour * 60 + minute).coerceAtMost(MINUTES_PER_DAY)
    }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
