// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

/**
 * 2.2 / H09: confirmed reasons why a connection can no longer be travelled. Only structured provider
 * states count (a cancelled service, a cancelled boarding or alighting call) and, for a search "from
 * now", a departure that has already left. Notice texts never decide this (2.5): a text match explains
 * a possible special case, a hidden text never turns a confirmed impossibility into a usable trip.
 */
enum class TripImpossibility { CANCELLED, BOARDING_CANCELLED, ALIGHTING_CANCELLED, MISSED_BOARDING, ENDED }

/** Grace after the first boarding before a departure counts as missed in a current search. */
const val MISSED_BOARDING_GRACE_MILLIS = 60_000L

/** True when the provider marked the call of [leg] at [place] as cancelled (matched by stop id, then name). */
fun TripSegment.callCancelled(place: Location): Boolean {
    val call = stops.firstOrNull { sameCallPlace(it.location, place) } ?: return false
    return call.cancelled
}

private fun sameCallPlace(a: Location, b: Location): Boolean {
    val ida = a.id?.takeIf { it.isNotBlank() }; val idb = b.id?.takeIf { it.isNotBlank() }
    if (ida != null && idb != null) return ida == idb
    return a.name.trim().equals(b.name.trim(), ignoreCase = true)
}

/**
 * [currentSearch]: the search runs "from now" (departure, current time). Only then a departure in the
 * past is a missed boarding; a deliberately chosen earlier time keeps such connections findable.
 */
fun tripImpossibility(trip: Trip, now: Long, currentSearch: Boolean): TripImpossibility? {
    val rides = trip.segments.filterNot { it.isWalk }
    if (rides.isEmpty()) return null
    if (rides.any { it.delay.cancelled }) return TripImpossibility.CANCELLED
    for (ride in rides) {
        if (ride.callCancelled(ride.origin)) return TripImpossibility.BOARDING_CANCELLED
        if (ride.callCancelled(ride.destination)) return TripImpossibility.ALIGHTING_CANCELLED
    }
    if (currentSearch && rides.first().effectiveDeparture() < now - MISSED_BOARDING_GRACE_MILLIS) return TripImpossibility.MISSED_BOARDING
    return null
}

/**
 * H09: a connection that cannot be pinned. A departure in the past is allowed (the rider may already
 * be on board, "laufender Einstieg"); a journey whose final arrival has passed is not.
 */
fun pinRejection(trip: Trip, now: Long): TripImpossibility? {
    tripImpossibility(trip, now, currentSearch = false)?.let { return it }
    val arrival = trip.segments.lastOrNull { !it.isWalk }?.effectiveArrival() ?: trip.endTime
    return if (arrival < now - MISSED_BOARDING_GRACE_MILLIS) TripImpossibility.ENDED else null
}

/** Short, user-facing reason (title of the hint or dialog). */
fun TripImpossibility.title(): String = when (this) {
    TripImpossibility.CANCELLED -> tr("Diese Fahrt fällt aus")
    TripImpossibility.BOARDING_CANCELLED -> tr("Der Einstieg dieser Fahrt fällt aus")
    TripImpossibility.ALIGHTING_CANCELLED -> tr("Der Ausstieg dieser Fahrt fällt aus")
    TripImpossibility.MISSED_BOARDING -> tr("Diese Fahrt ist schon abgefahren")
    TripImpossibility.ENDED -> tr("Diese Fahrt ist schon vorbei")
}
