/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.hmip

import de.cplagens.pureheat.R
import de.cplagens.pureheat.ui.UiText
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Turns any failure from [HmipClient] into a short, translatable message for the UI. */
fun Throwable.toUiText(): UiText = when (this) {
    is HmipClient.HmipException -> uiText
    is UnknownHostException -> {
        // Android: Unable to resolve host "srv….homematic.com": No address associated with hostname
        val host = message?.let { Regex("\"([^\"]+)\"").find(it)?.groupValues?.get(1) ?: it }
        if (host != null) UiText.Res(R.string.err_unknown_host, host) else UiText.Res(R.string.err_unknown_host_cloud)
    }
    is SocketTimeoutException -> UiText.Res(R.string.err_timeout)
    is ConnectException, is NoRouteToHostException -> UiText.Res(R.string.err_no_connection)
    is SSLException -> UiText.Res(R.string.err_ssl, message ?: "SSL")
    is IOException -> UiText.Res(R.string.err_network, message ?: javaClass.simpleName)
    else -> UiText.Res(R.string.err_unexpected, message ?: javaClass.simpleName)
}

/** "<what failed>: <hint> (HTTP 400, INVALID_REQUEST)" for an HTTP error from the cloud API. */
internal fun httpError(context: UiText, code: Int, errorCode: String?): UiText {
    val hint = when {
        code == 401 || code == 403 -> R.string.err_http_forbidden
        code == 404 -> R.string.err_http_not_found
        code == 400 -> R.string.err_http_bad_request
        code == 429 -> R.string.err_http_too_many
        code >= 500 -> R.string.err_http_server
        else -> R.string.err_http_unexpected
    }
    val detail = listOfNotNull("HTTP $code", errorCode).joinToString(", ")
    return UiText.Res(R.string.err_with_context, context, UiText.Res(hint), detail)
}
