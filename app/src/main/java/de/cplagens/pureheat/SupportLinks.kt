package de.cplagens.pureheat

/**
 * Hier die eigenen Links eintragen. Leere Einträge werden in der App automatisch ausgeblendet.
 * Gibt es gar keinen Eintrag, zeigt die App keinen Unterstützen-Bereich an.
 */
object SupportLinks {
    const val SOURCE_CODE = "https://github.com/cplagens/pureheat"

    const val KOFI = ""             // z. B. "https://ko-fi.com/deinname"
    const val PAYPAL = "https://paypal.me/ChristianPlagens"
    const val GITHUB_SPONSORS = "https://github.com/sponsors/cplagens"

    val donationOptions: List<Pair<String, String>>
        get() = listOf(
            "Ko-fi" to KOFI,
            "PayPal" to PAYPAL,
            "GitHub Sponsors" to GITHUB_SPONSORS,
        ).filter { it.second.isNotBlank() }
}
