/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.cplagens.pureheat.data.support.CoffeeBilling
import de.cplagens.pureheat.data.support.CoffeeState
import kotlinx.coroutines.flow.StateFlow

/** The optional "buy me a coffee" tip in the Info dialog (Google Play Billing). */
class SupportViewModel(app: Application) : AndroidViewModel(app) {
    private val billing = CoffeeBilling(app).also { it.connect() }
    val state: StateFlow<CoffeeState> = billing.state

    fun buy(activity: Activity) = billing.buy(activity)

    override fun onCleared() = billing.close()
}
