package de.cplagens.pureheat

import android.app.Application
import de.cplagens.pureheat.data.CredentialStore
import de.cplagens.pureheat.data.HeatingRepository
import de.cplagens.pureheat.data.HistoryWorker
import de.cplagens.pureheat.data.history.HistoryDatabase

class PureHeatApp : Application() {
    val credentialStore by lazy { CredentialStore(this) }
    val repository by lazy { HeatingRepository(this, HistoryDatabase.create(this).dao()) }

    override fun onCreate() {
        super.onCreate()
        if (credentialStore.load() != null) HistoryWorker.schedule(this)
    }
}
