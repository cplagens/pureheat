/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.profile

import de.cplagens.pureheat.data.ai.OnDeviceAi
import de.cplagens.pureheat.data.room.ExtractionSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

data class ProfileSuggestion(val rules: List<DayRule>, val source: ExtractionSource)

/**
 * Free text ("weekdays 6–8 and 17–22 at 21 °C, weekend 8–23, otherwise 17 °C") → [DayRule]s.
 * Gemini Nano on the device understands any language and phrasing; without it the German/English
 * keyword parser is used. The result is only a suggestion in the editor – saving stays manual.
 */
class ProfileDescriber(private val ai: OnDeviceAi = OnDeviceAi()) {

    suspend fun describe(text: String): ProfileSuggestion {
        val keywords = ProfileFromText.fromKeywords(text)
        return when (ai.availability()) {
            OnDeviceAi.Availability.AVAILABLE -> {
                val model = ai.askJson(PROMPT + text.take(MAX_INPUT_CHARS), maxOutputTokens = 800)
                    ?.let { runCatching { ProfileFromText.fromModelJson(lenient.parseToJsonElement(it).jsonObject) }.getOrNull() }
                    .orEmpty()
                if (model.isNotEmpty()) ProfileSuggestion(model, ExtractionSource.AI) else ProfileSuggestion(keywords, ExtractionSource.KEYWORDS)
            }
            OnDeviceAi.Availability.DOWNLOADING -> ProfileSuggestion(keywords, ExtractionSource.KEYWORDS_AI_DOWNLOADING)
            OnDeviceAi.Availability.UNAVAILABLE -> ProfileSuggestion(keywords, ExtractionSource.KEYWORDS)
        }
    }

    fun close() = ai.close()

    private companion object {
        const val MAX_INPUT_CHARS = 1_500
        val lenient = Json { isLenient = true }

        val PROMPT = """
            Turn the description of a weekly room heating schedule below into JSON. The description may be in any language.
            Answer with one JSON object only, no other text:
            {"rules":[{"days":["MONDAY","TUESDAY"],"base":17.0,"periods":[{"start":"06:00","end":"08:00","temperature":21.0}]}]}
            Rules:
            - "days": MONDAY TUESDAY WEDNESDAY THURSDAY FRIDAY SATURDAY SUNDAY. Weekdays/workdays = MONDAY to FRIDAY, weekend = SATURDAY and SUNDAY, daily/every day = all seven.
            - "periods": times when the room should be warm, 24-hour "HH:MM", end after start, "24:00" for midnight, with the temperature in °C.
            - "base": temperature outside the periods (words like otherwise, else, at night, setback), or null if not mentioned.
            - Several rules for different days are fine. Leave out days that are not mentioned. Do not invent times or temperatures.
            Description:
        """.trimIndent() + "\n"
    }
}
