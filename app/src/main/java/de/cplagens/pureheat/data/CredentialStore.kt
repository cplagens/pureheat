package de.cplagens.pureheat.data

import android.content.Context

data class Credentials(val sgtin: String, val authToken: String)

/**
 * Stores the access point ID and auth token in app-private storage.
 * Never commit these values to the repository.
 */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("hmip_credentials", Context.MODE_PRIVATE)

    fun load(): Credentials? {
        val sgtin = prefs.getString(KEY_SGTIN, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return Credentials(sgtin, token)
    }

    fun save(credentials: Credentials) {
        prefs.edit()
            .putString(KEY_SGTIN, credentials.sgtin)
            .putString(KEY_TOKEN, credentials.authToken)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_SGTIN = "sgtin"
        const val KEY_TOKEN = "auth_token"
    }
}
