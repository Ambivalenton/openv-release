// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** EFA accepts whole minutes. Round up to respect the requested departure floor. */
internal fun JourneyQuery.minuteDeparture(): JourneyQuery {
    val remainder = Math.floorMod(time, 60_000L)
    return if (remainder == 0L) this else copy(time = time + 60_000L - remainder)
}
