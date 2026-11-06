/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.demo

import de.cplagens.pureheat.hmip.HeatingPeriod
import de.cplagens.pureheat.hmip.ProfileDay
import de.cplagens.pureheat.hmip.Weekday
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * An invented flat for the demo mode (reviewers, testers, screenshots): six rooms on one floor
 * heating actuator, simulated with the same first-order physics the app learns (k, P, dead time),
 * plus a bedroom that is aired at night and a bathroom with showers. Pure Kotlin and deterministic:
 * the same time always gives the same values, so the seeded history and later fetches fit together.
 * Nothing here talks to the network.
 */
object DemoHome {
    /** Stored as the SGTIN of the demo "login"; never a valid access point ID (not hex). */
    const val SGTIN = "DEMO"
    const val DEVICE_ID = "demo-actuator"
    const val STEP_MS = 5 * 60_000L

    fun isDemo(sgtin: String?) = sgtin == SGTIN

    enum class Kind { LIVING, KITCHEN, BEDROOM, CHILD, OFFICE, BATH }

    data class DemoRoom(
        val id: String,
        val kind: Kind,
        val channels: List<Int>,
        /** Heat loss per hour and K difference to outside. */
        val k: Double,
        /** Heating power in K/h while the floor delivers heat. */
        val power: Double,
        val deadTimeHours: Double,
        val humidity: Int,
        /** Window open on two of three nights (22:45–06:15), as in many bedrooms. */
        val airsAtNight: Boolean = false,
        /** Showers in the morning (and some evenings): quick humidity jump. */
        val showers: Boolean = false,
    )

    val rooms = listOf(
        DemoRoom("demo-living", Kind.LIVING, listOf(1, 2), k = 0.025, power = 0.9, deadTimeHours = 1.5, humidity = 46),
        DemoRoom("demo-kitchen", Kind.KITCHEN, listOf(3), k = 0.03, power = 1.0, deadTimeHours = 1.25, humidity = 50),
        DemoRoom("demo-bedroom", Kind.BEDROOM, listOf(4), k = 0.022, power = 0.8, deadTimeHours = 1.5, humidity = 48, airsAtNight = true),
        DemoRoom("demo-child", Kind.CHILD, listOf(5), k = 0.024, power = 0.85, deadTimeHours = 1.75, humidity = 47),
        DemoRoom("demo-office", Kind.OFFICE, listOf(6), k = 0.028, power = 0.75, deadTimeHours = 2.0, humidity = 44),
        DemoRoom("demo-bath", Kind.BATH, listOf(7), k = 0.03, power = 1.15, deadTimeHours = 1.0, humidity = 55, showers = true),
    )

    /** Weekly profile the demo starts with (workdays / weekend). */
    fun defaultProfile(kind: Kind): Map<Weekday, ProfileDay> {
        fun p(from: String, to: String, t: Double) = HeatingPeriod(minutes(from), minutes(to), t)
        val (work, weekend) = when (kind) {
            // Small setbacks, as usual with slow floor heating; the mornings start early for the dead time.
            Kind.LIVING -> ProfileDay(19.5, listOf(p("04:30", "08:30", 21.5), p("15:30", "22:30", 22.0))) to
                ProfileDay(19.5, listOf(p("06:00", "23:00", 22.0)))
            Kind.KITCHEN -> ProfileDay(19.0, listOf(p("05:00", "08:00", 21.0), p("16:00", "21:00", 21.0))) to
                ProfileDay(19.0, listOf(p("07:00", "21:00", 21.0)))
            Kind.BEDROOM -> ProfileDay(15.0, listOf(p("04:30", "07:30", 18.0), p("20:00", "22:30", 19.0))) to
                ProfileDay(15.0, listOf(p("06:00", "09:30", 18.0), p("20:00", "22:30", 19.0)))
            Kind.CHILD -> ProfileDay(19.5, listOf(p("05:00", "08:00", 21.0), p("13:00", "20:30", 21.5))) to
                ProfileDay(19.5, listOf(p("06:30", "20:30", 21.5)))
            Kind.OFFICE -> ProfileDay(18.5, listOf(p("05:30", "17:30", 21.0))) to ProfileDay(18.5, emptyList())
            Kind.BATH -> ProfileDay(20.0, listOf(p("04:00", "08:30", 23.0), p("17:00", "22:00", 23.0))) to
                ProfileDay(20.0, listOf(p("05:30", "10:30", 23.0), p("17:00", "22:00", 23.0)))
        }
        return Weekday.entries.associateWith { if (it in Weekday.WEEKEND) weekend else work }
    }

    fun setPointAt(days: Map<Weekday, ProfileDay>, t: Long, zone: ZoneId): Double =
        setPointAt(days, Instant.ofEpochMilli(t).atZone(zone))

    private fun setPointAt(days: Map<Weekday, ProfileDay>, local: ZonedDateTime): Double {
        val day = days[Weekday.entries[local.dayOfWeek.value - 1]] ?: return 18.0
        val minute = local.hour * 60 + local.minute
        return day.periods.firstOrNull { minute >= it.start && minute < it.end }?.temperature ?: day.baseTemperature
    }

    /** Mild late-autumn weather: daily swing (warmest at 15:00) and a slow change over days. */
    fun outsideAt(t: Long, zone: ZoneId): Double = outsideAt(t, Instant.ofEpochMilli(t).atZone(zone))

    private fun outsideAt(t: Long, local: ZonedDateTime): Double {
        val hour = local.hour + local.minute / 60.0
        val days = t / 86_400_000.0
        return 5.5 + 3.0 * sin(2 * PI * (hour - 9) / 24) + 2.5 * sin(2 * PI * days / 5.3)
    }

    data class Point(
        val time: Long,
        val temperature: Double,
        val setPoint: Double,
        /** 0.0–1.0 */
        val valve: Double,
        val humidity: Int,
    )

    /**
     * Simulates [room] from [start] to [end] in 5-min steps like a thermostat: valve opens 0.2 K
     * below the setpoint and closes at it; the floor delivers heat after the dead time (right
     * away if it was closed for less than 2 h). Starts at the setpoint, so give it a day or two
     * of lead time before the part that is shown.
     */
    fun simulate(room: DemoRoom, days: Map<Weekday, ProfileDay>, start: Long, end: Long, zone: ZoneId): List<Point> {
        val out = ArrayList<Point>(((end - start) / STEP_MS + 1).toInt())
        val dtH = STEP_MS / 3_600_000.0
        var t = start - start % STEP_MS
        var temp = setPointAt(days, t, zone)
        var openSince: Long? = null
        var closedSince: Long? = null
        var warm = false
        // Window state of the previous step: like the temperature, the humidity reacts only after opening.
        var aired = false
        while (t <= end) {
            // One time-zone conversion per step: three weeks for six rooms are ~36 000 steps.
            val local = Instant.ofEpochMilli(t).atZone(zone)
            val sp = setPointAt(days, local)
            val open = when {
                temp < sp - 0.2 -> true
                temp >= sp -> false
                else -> openSince != null
            }
            if (open && openSince == null) {
                warm = closedSince?.let { t - it < 2 * 3_600_000L } ?: false
                openSince = t
                closedSince = null
            } else if (!open && openSince != null) {
                openSince = null
                closedSince = t
            }
            val window = if (room.airsAtNight) windowState(local) else null
            val shower = if (room.showers) showerAmount(local) else 0.0
            val valve = if (!open) 0.0 else ((sp - temp) / 0.6).coerceIn(0.35, 1.0)
            val humidity = room.humidity + 3 * sin(2 * PI * (t / 86_400_000.0)) +
                (if (aired) -7.0 else 0.0) + 18 * shower
            out += Point(t, temp, sp, valve, humidity.toInt().coerceIn(20, 95))
            aired = window != null

            val heating = openSince?.let { warm || (t - it) / 3_600_000.0 >= room.deadTimeHours } ?: false
            val k = room.k + when (window) {
                WindowState.WIDE -> 0.25
                WindowState.TILTED -> 0.008
                null -> 0.0
            }
            temp += dtH * ((if (heating) room.power else 0.0) - k * (temp - outsideAt(t, local)) + 1.2 * shower)
            t += STEP_MS
        }
        return out
    }

    /**
     * Repeatable pseudo-random value in −0.5..0.5, e.g. for prediction errors. Not added to the
     * temperatures: together with the 0.1 K rounding it would look like a falling window curve.
     */
    fun noise(seed: Int, t: Long): Double {
        val h = ((t / STEP_MS) * 2_654_435_761L + seed * 40_503L) % 1000
        return abs(h) / 1000.0 - 0.5
    }

    private enum class WindowState { WIDE, TILTED }

    /** Opened wide at 22:45 for 15 min, then tilted until 06:15 – on two of three nights. */
    private fun windowState(local: ZonedDateTime): WindowState? {
        val minute = local.hour * 60 + local.minute
        // The night belongs to the evening it started on.
        val evening = if (minute < 12 * 60) local.toLocalDate().minusDays(1) else local.toLocalDate()
        if (evening.toEpochDay() % 3 == 0L) return null
        return when {
            minute in minutes("22:45") until minutes("23:00") -> WindowState.WIDE
            minute >= minutes("23:00") || minute < minutes("06:15") -> WindowState.TILTED
            else -> null
        }
    }

    /** 1.0 during a shower (10 min), then fading out over 150 min (slower than an opened window); 0 otherwise. */
    private fun showerAmount(local: ZonedDateTime): Double {
        val minute = local.hour * 60 + local.minute
        val weekend = local.dayOfWeek.value >= 6
        val starts = buildList {
            add(if (weekend) minutes("08:15") else minutes("06:40"))
            if (local.toLocalDate().toEpochDay() % 2 == 0L) add(minutes("20:30"))
        }
        return starts.maxOfOrNull { s ->
            val since = minute - s
            when {
                since < 0 -> 0.0
                since < 10 -> 1.0
                since < 160 -> 1.0 - (since - 10) / 150.0
                else -> 0.0
            }
        } ?: 0.0
    }

    private fun minutes(hhmm: String): Int = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()
}
