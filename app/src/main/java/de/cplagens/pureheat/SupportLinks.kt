/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat

/**
 * Hier die eigenen Links eintragen. Leere Einträge werden in der App automatisch ausgeblendet.
 * Gibt es gar keinen Eintrag, zeigt die App keinen Unterstützen-Bereich an.
 *
 * Absichtlich leer: Spenden über externe Links verstößt gegen die Google-Play-Richtlinien
 * (Zahlungen nur über Play Billing). Spenden-Links stehen nur in der README.
 */
object SupportLinks {
    const val SOURCE_CODE = "https://github.com/cplagens/pureheat"

    const val KOFI = ""
    const val PAYPAL = ""
    const val GITHUB_SPONSORS = ""

    val donationOptions: List<Pair<String, String>>
        get() = listOf(
            "Ko-fi" to KOFI,
            "PayPal" to PAYPAL,
            "GitHub Sponsors" to GITHUB_SPONSORS,
        ).filter { it.second.isNotBlank() }
}
