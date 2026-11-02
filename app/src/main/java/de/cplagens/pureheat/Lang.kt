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
     * new languages also go into res/xml/locales_config.xml. Planned (as in PureRemote): fr, es, it,
     * pt, nl, pl, tr, ru, ja, zh-CN – see docs/NEXT_STEPS.md.
     */
    val options = listOf(
        "en" to "English",
        "de" to "Deutsch",
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
