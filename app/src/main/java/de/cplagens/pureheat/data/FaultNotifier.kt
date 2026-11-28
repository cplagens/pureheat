/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.cplagens.pureheat.MainActivity
import de.cplagens.pureheat.R
import de.cplagens.pureheat.hmip.Fault
import de.cplagens.pureheat.hmip.FaultKind
import de.cplagens.pureheat.ui.place
import de.cplagens.pureheat.ui.text

/**
 * Tells the user about a new valve or device fault while the app is closed (from [HistoryWorker]).
 * Each fault is announced once; it is announced again only after it was gone in between.
 * In the foreground the dashboard banner shows it, so faults seen there are only marked as known.
 */
object FaultNotifier {

    private const val PREFS = "ui"
    private const val KEY_ENABLED = "fault_notifications"
    private const val KEY_KNOWN = "known_faults"
    private const val CHANNEL = "faults"
    private const val NOTIFICATION_ID = 1

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Android 13+ asks for the permission; below it is granted with the install. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Faults worth a notification; the info-only "being adapted" isn't. */
    fun relevant(faults: List<Fault>): List<Fault> = faults.filter { it.kind.severity != FaultKind.Severity.INFO }

    /** Faults in [current] that weren't there at the last check. */
    fun newFaults(knownKeys: Set<String>, current: List<Fault>): List<Fault> =
        relevant(current).filter { it.key !in knownKeys }

    fun update(context: Context, faults: List<Fault>, background: Boolean) {
        val prefs = prefs(context)
        val current = relevant(faults)
        val fresh = newFaults(prefs.getStringSet(KEY_KNOWN, emptySet()).orEmpty(), faults)
        prefs.edit().putStringSet(KEY_KNOWN, current.map { it.key }.toSet()).apply()
        val manager = NotificationManagerCompat.from(context)
        when {
            current.isEmpty() -> manager.cancel(NOTIFICATION_ID)
            background && fresh.isNotEmpty() && isEnabled(context) && hasPermission(context) -> post(context, current, fresh)
        }
    }

    @android.annotation.SuppressLint("MissingPermission") // checked in hasPermission()
    private fun post(context: Context, all: List<Fault>, fresh: List<Fault>) {
        ensureChannel(context)
        val lines = all.map { context.getString(R.string.channel_state, it.place(context), context.getString(it.kind.text().title)) }
        val first = fresh.first()
        val text = context.getString(R.string.channel_state, first.place(context), context.getString(first.kind.text().title))
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fault)
            .setContentTitle(context.getString(R.string.fault_banner_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_faults), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = context.getString(R.string.notif_channel_faults_desc)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
