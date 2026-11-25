// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.TimeMode
import dev.ambivalenton.openv.localization.tr
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * The label of the search button says what the chosen time does, without places:
 * "Nächste Verbindung suchen", "Verbindung ab 17:50 suchen", "Verbindung suchen, damit du am
 * Mittwoch um 08:15 ankommst". Today and tomorrow are named as such, a day of the current week by
 * its weekday, anything else by its date. Days are judged in [zone]; in the repeated hour of a clock
 * change the zone name tells the two times apart.
 */
fun searchButtonLabel(time: Long?, mode: TimeMode, now: Long, zone: ZoneId = dev.ambivalenton.openv.data.model.transitZone,
    locale: Locale = Locale.getDefault()): String {
    if (time == null) return if (mode == TimeMode.DEPARTURE) tr("Nächste Verbindung suchen") else tr("Verbindung suchen")
    val at = Instant.ofEpochMilli(time).atZone(zone)
    val nowAt = Instant.ofEpochMilli(now).atZone(zone)
    val today = nowAt.toLocalDate()
    val date = at.toLocalDate()
    val ambiguous = zone.rules.getValidOffsets(at.toLocalDateTime()).size > 1
    val clock = at.format(DateTimeFormatter.ofPattern("HH:mm", locale)) + if (ambiguous) " " + at.format(DateTimeFormatter.ofPattern("zzz", locale)) else ""
    val sameWeek = date.get(IsoFields.WEEK_BASED_YEAR) == today.get(IsoFields.WEEK_BASED_YEAR) &&
        date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val day: String? = when {
        date == today -> null
        date == today.plusDays(1) -> tr("morgen")
        date == today.minusDays(1) -> tr("gestern")
        sameWeek -> tr("am %1\$s", at.format(DateTimeFormatter.ofPattern("EEEE", locale)))
        else -> tr("am %1\$s", at.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d.M." else "d.M.yyyy", locale)))
    }
    return if (mode == TimeMode.DEPARTURE) {
        if (day == null) tr("Verbindung ab %1\$s suchen", clock) else tr("Verbindung %1\$s ab %2\$s suchen", day, clock)
    } else {
        if (day == null) tr("Verbindung suchen, damit du um %1\$s ankommst", clock)
        else tr("Verbindung suchen, damit du %1\$s um %2\$s ankommst", day, clock)
    }
}
