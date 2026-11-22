// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.*

/** H07 (build 93): the preferred view of the results. */
@Serializable enum class ResultsView { CHART, LIST }

/** 2.10: when the day and the night design change by themselves. */
@Serializable enum class DayNightSchedule {
    /** Only the "Tag & Nacht" button changes it ("nie"). */
    MANUAL,
    /** Fixed times for day and night. */
    TIMES,
    /** Sunrise and sunset at the current location ("automatisch"). */
    SUN,
}

/**
 * 2.10: the app has a day and a night design. The one shown lives in the normal settings; [other]
 * keeps the design of the other half. [lastAutoNight] is the half the last automatic change applied,
 * so a manual change holds until the next sunrise, sunset or set time.
 */
@Serializable data class DayNightSettings(
    val night: Boolean = false,
    val other: AppearanceProfile? = null,
    val schedule: DayNightSchedule = DayNightSchedule.MANUAL,
    val dayStartMinute: Int = 7 * 60,
    val nightStartMinute: Int = 20 * 60,
    val lastAutoNight: Boolean? = null,
)

/** True when the appearance shown now is dark; [systemDark] answers for "follow the system". */
fun AppSettings.appearanceIsDark(systemDark: Boolean): Boolean = appTheme?.dark ?: when (themeMode) {
    ThemeMode.DARK, ThemeMode.AMOLED -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> systemDark
}

/** The real app theme of the other brightness: first from the same design group, else the classic one. */
fun counterpartTheme(theme: AppTheme, night: Boolean, quiet: Boolean = false): AppTheme {
    if (theme.dark == night) return theme
    if (quiet) return if (night) AppTheme.CLASSIC_DARK else AppTheme.CLASSIC_LIGHT
    val group = appThemeGroup(theme)
    return groupedAppThemes().firstOrNull { it.first == group }?.second?.firstOrNull { it.dark == night }
        ?: if (night) AppTheme.CLASSIC_DARK else AppTheme.CLASSIC_LIGHT
}

/**
 * 2.10: shows the night ([night] true) or the day design. The design shown so far is kept as the
 * other half. The first use from any theme derives the other half from the real theme catalogue:
 * light → "Nacht" (a dark theme), dark → "Tag" (a light one).
 */
fun AppSettings.withDayNight(night: Boolean, systemDark: Boolean = false): AppSettings {
    val current = appearanceProfile()
    if (dayNight.other != null && dayNight.night == night) return this
    val target = dayNight.other ?: run {
        val theme = appTheme
        if (theme == null) current.copy(themeMode = if (night) ThemeMode.DARK else ThemeMode.LIGHT)
        else current.copy(appTheme = counterpartTheme(theme, night, quietMode))
    }
    return applyProfile(target).copy(dayNight = dayNight.copy(night = night, other = current))
}

/** 2.10: the half the button switches to next (first use: the other brightness of the current theme). */
fun AppSettings.nextDayNightIsNight(systemDark: Boolean): Boolean =
    if (dayNight.other == null) !appearanceIsDark(systemDark) else !dayNight.night

/** 2.10: sunrise and sunset of one day (null in polar day or night). */
data class SunTimes(val sunrise: Instant, val sunset: Instant)

/**
 * 2.10: local sunrise/sunset (NOAA "Almanac for Computers" method, official zenith 90.833°), accurate
 * to a few minutes. No network or service is involved.
 */
fun sunTimes(date: LocalDate, latitude: Double, longitude: Double): SunTimes? {
    fun event(rising: Boolean): Instant? {
        val n = date.dayOfYear
        val lngHour = longitude / 15.0
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(Math.toRadians(m)) + 0.020 * sin(Math.toRadians(2 * m)) + 282.634
        l = ((l % 360) + 360) % 360
        var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
        ra = ((ra % 360) + 360) % 360
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15.0
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(Math.toRadians(90.833)) - sinDec * sin(Math.toRadians(latitude))) / (cosDec * cos(Math.toRadians(latitude)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))) / 15.0
        val localT = h + ra - 0.06571 * t - 6.622
        val ut = ((localT - lngHour) % 24 + 24) % 24
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().plusMillis((ut * 3_600_000).roundToLong())
    }
    val rise = event(true) ?: return null
    val set = event(false) ?: return null
    // West of Greenwich the UTC sunset can fall on the next UTC day.
    return SunTimes(rise, if (set.isBefore(rise)) set.plusSeconds(86_400) else set)
}

/**
 * 2.10: whether it is night now by the schedule; null for "only by hand". Without a known place the
 * sun schedule falls back to the set times.
 */
fun DayNightSettings.scheduledNight(now: ZonedDateTime, latitude: Double?, longitude: Double?): Boolean? = when (schedule) {
    DayNightSchedule.MANUAL -> null
    // Polar day or night has no sunrise or sunset that day: the set times apply then as well.
    DayNightSchedule.SUN -> if (latitude != null && longitude != null)
        sunTimes(now.toLocalDate(), latitude, longitude)?.let { now.toInstant() !in it.sunrise..it.sunset } ?: timesNight(now)
        else timesNight(now)
    DayNightSchedule.TIMES -> timesNight(now)
}

private fun DayNightSettings.timesNight(now: ZonedDateTime): Boolean {
    val minute = now.hour * 60 + now.minute
    val day = dayStartMinute.coerceIn(0, 1439); val night = nightStartMinute.coerceIn(0, 1439)
    return if (day <= night) minute !in day until night else minute in night until day
}

/**
 * 2.10: the automatic change, once per boundary: it applies when the scheduled half differs from the
 * half the previous automatic change applied; a manual choice in between is left alone.
 */
fun AppSettings.withScheduledDayNight(night: Boolean?, systemDark: Boolean): AppSettings {
    if (night == null || dayNight.lastAutoNight == night) return this
    val changed = withDayNight(night, systemDark)
    return changed.copy(dayNight = changed.dayNight.copy(lastAutoNight = night))
}
