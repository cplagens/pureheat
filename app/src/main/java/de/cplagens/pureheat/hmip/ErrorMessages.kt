package de.cplagens.pureheat.hmip

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Turns any failure from [HmipClient] into a short German message for the UI. */
fun Throwable.toUserMessage(): String = when (this) {
    is HmipClient.HmipException -> message ?: "Unbekannter Fehler der Homematic IP Cloud."
    is UnknownHostException -> {
        // Android: Unable to resolve host "srv….homematic.com": No address associated with hostname
        val host = message?.let { Regex("\"([^\"]+)\"").find(it)?.groupValues?.get(1) ?: it }
        "Server ${host ?: "der Cloud"} nicht erreichbar. Bitte Internetverbindung prüfen und erneut versuchen."
    }
    is SocketTimeoutException -> "Zeitüberschreitung: Die Cloud hat nicht rechtzeitig geantwortet."
    is ConnectException, is NoRouteToHostException ->
        "Keine Verbindung zur Cloud möglich. Bitte Internetverbindung prüfen."
    is SSLException -> "Sichere Verbindung zur Cloud fehlgeschlagen (${message ?: "SSL-Fehler"})."
    is IOException -> "Netzwerkfehler: ${message ?: javaClass.simpleName}"
    else -> "Unerwarteter Fehler: ${message ?: javaClass.simpleName}"
}

/** Hint for an HTTP error from the cloud API, based on status and the `errorCode` in the body. */
internal fun httpErrorHint(code: Int, errorCode: String?): String {
    val hint = when {
        code == 401 || code == 403 ->
            "Zugriff verweigert. Falls die App am Access Point entfernt wurde: abmelden und neu verbinden."
        code == 404 -> "Access Point nicht gefunden. Bitte SGTIN prüfen."
        code == 400 -> "Ungültige Anfrage."
        code == 429 -> "Zu viele Anfragen. Bitte etwas warten."
        code >= 500 -> "Die Homematic IP Cloud hat gerade ein Problem. Bitte später erneut versuchen."
        else -> "Unerwartete Antwort der Cloud."
    }
    val detail = listOfNotNull("HTTP $code", errorCode).joinToString(", ")
    return "$hint ($detail)"
}
