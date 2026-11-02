package de.cplagens.pureheat.ui

import androidx.annotation.StringRes
import de.cplagens.pureheat.R
import de.cplagens.pureheat.hmip.DayChange
import de.cplagens.pureheat.hmip.HeatingPeriod
import de.cplagens.pureheat.hmip.HeatingProfileCodec.formatTime
import de.cplagens.pureheat.hmip.ProfileRef
import de.cplagens.pureheat.hmip.Weekday

/** Translatable texts for heating profiles (kept out of the pure `hmip` package). */

@get:StringRes
val Weekday.shortRes: Int
    get() = when (this) {
        Weekday.MONDAY -> R.string.day_mo
        Weekday.TUESDAY -> R.string.day_tu
        Weekday.WEDNESDAY -> R.string.day_we
        Weekday.THURSDAY -> R.string.day_th
        Weekday.FRIDAY -> R.string.day_fr
        Weekday.SATURDAY -> R.string.day_sa
        Weekday.SUNDAY -> R.string.day_su
    }

/** The user's name for the profile, or "Profile n". */
val ProfileRef.displayName: UiText
    get() = name?.let { UiText.Plain(it) } ?: UiText.Res(R.string.profile_n, number)

/** One line per changed day for the save confirmation. */
fun DayChange.describe(): UiText {
    val day = UiText.Res(day.shortRes)
    val before = before?.periods?.describe() ?: UiText.Plain("–")
    val after = after.periods.describe()
    val oldBase = this.before?.baseTemperature
    return when {
        periodsChanged && baseChanged && oldBase != null ->
            UiText.Res(R.string.diff_both, day, before, after, oldBase, this.after.baseTemperature)
        baseChanged && oldBase != null -> UiText.Res(R.string.diff_base, day, oldBase, this.after.baseTemperature)
        else -> UiText.Res(R.string.diff_periods, day, before, after)
    }
}

private fun List<HeatingPeriod>.describe(): UiText =
    if (isEmpty()) {
        UiText.Res(R.string.diff_no_period)
    } else {
        UiText.Join(map { UiText.Res(R.string.diff_period, formatTime(it.start), formatTime(it.end), it.temperature) }, ", ")
    }
