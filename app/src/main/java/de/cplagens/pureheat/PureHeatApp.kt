/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat

import android.app.Application
import de.cplagens.pureheat.data.CredentialStore
import de.cplagens.pureheat.data.HeatingRepository
import de.cplagens.pureheat.data.HistoryWorker
import de.cplagens.pureheat.data.ViewSettings
import de.cplagens.pureheat.data.export.DataTransfer
import de.cplagens.pureheat.data.history.HistoryDatabase

class PureHeatApp : Application() {
    val credentialStore by lazy { CredentialStore(this) }
    private val database by lazy { HistoryDatabase.create(this) }
    val repository by lazy { HeatingRepository(this, credentialStore, database.dao()) }
    val viewSettings by lazy { ViewSettings(this) }
    val dataTransfer by lazy { DataTransfer(database.dao(), repository.descriptions, viewSettings) }

    override fun onCreate() {
        super.onCreate()
        if (credentialStore.load() != null) HistoryWorker.schedule(this)
    }
}
