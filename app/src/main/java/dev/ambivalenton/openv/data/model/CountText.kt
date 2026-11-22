// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

/** N02: one formatting for transfers in every list ("0 Umstiege", "1 Umstieg", "2 Umstiege"). */
fun transferCount(count: Int): String = if (count == 1) tr("1 Umstieg") else tr("%1\$s Umstiege", count)

/** N02: minutes spelled out with singular and plural ("1 Minute", "5 Minuten"). */
fun minutesText(minutes: Long): String = if (minutes == 1L) tr("1 Minute") else tr("%1\$s Minuten", minutes)

/** N02: the platform of a departure row, named after its vehicle ("Gleis 7", "Steig 2", "Bussteig B"). */
fun departurePlatformText(departure: Departure): String? = platformName(departure.platform, departure.lineType)
