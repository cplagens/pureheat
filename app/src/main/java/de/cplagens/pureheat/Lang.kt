/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * App language, as in PureRemote. By default the app follows the system language; a choice here
 * is stored by AppCompat and applies to the whole app at once. From Android 13 it also appears in
 * the system settings under "App languages".
 */
object Lang {
    /**
     * Language tag → name in that language. Only languages with a complete values-<tag>/strings.xml;
     * new languages also go into res/xml/locales_config.xml. Same set as in PureRemote.
     */
    val options = listOf(
        "en" to "English",
        "de" to "Deutsch",
        "fr" to "Français",
        "es" to "Español",
        "it" to "Italiano",
        "pt" to "Português",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "tr" to "Türkçe",
        "ru" to "Русский",
        "ja" to "日本語",
        "zh-CN" to "简体中文",
    )

    /** "" = system language */
    fun current(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ""
        val tag = locales[0]?.toLanguageTag() ?: return ""
        return options.firstOrNull { it.first.equals(tag, ignoreCase = true) }?.first
            ?: options.firstOrNull { it.first.substringBefore('-').equals(tag.substringBefore('-'), ignoreCase = true) }?.first
            ?: ""
    }

    fun set(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }
}
