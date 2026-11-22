// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import java.security.MessageDigest
import java.util.Locale

/*
 * Two different identities are deliberately kept apart:
 *
 * 1. The *connection identity* describes the travel offer itself: which services are used, from
 *    which stop and at which PLANNED time, and where walks lead. Mutable forecasts, provider
 *    response IDs, refresh tokens, walking durations and the exact device coordinate are not part
 *    of it, so the same connection is recognised across live refreshes, overlapping pages and
 *    restored cache snapshots.
 * 2. A *render key* is only required to be unique inside one displayed list. It is derived from the
 *    stable trip ID and never generated per recomposition.
 *
 * Favourites are user entries with their own IDs and are never deduplicated by these functions.
 */

private val whitespace = Regex("\\s+")

private fun normalizedLine(name: String) = name.trim().replace(whitespace, " ").lowercase(Locale.ROOT)

/** Namespaced place key. Coordinates are not used: a moving device position is the same start. */
fun connectionPlaceKey(location: Location): String {
    val id = location.id?.trim()?.takeIf { it.isNotEmpty() }
    if (id != null && location.kind == LocationKind.STOP) return "stop:${location.provider.orEmpty()}:$id"
    if (location.isDevicePosition || location.kind == LocationKind.COORDINATE) return "position"
    return "place:" + location.name.trim().replace(whitespace, " ").lowercase(Locale.ROOT)
}

private fun legIdentity(leg: TripSegment): String =
    if (leg.isWalk) {
        // A walk is identified by where it starts and ends when those are stops. Its duration and
        // the device coordinate change while the user moves and do not create a new connection.
        val from = if (leg.origin.kind == LocationKind.STOP) connectionPlaceKey(leg.origin) else "start"
        val to = if (leg.destination.kind == LocationKind.STOP) connectionPlaceKey(leg.destination) else "end"
        "W>$from>$to"
    } else {
        "R>${normalizedLine(leg.lineName)}>${leg.plannedStartTime}>${connectionPlaceKey(leg.origin)}>${connectionPlaceKey(leg.destination)}"
    }

/** Stable, provider-independent identity of one connection offer (see file comment). */
fun connectionIdentity(trip: Trip, ignoreWalking: Boolean = false): String {
    // With "Fußwege vollständig ignorieren" two offers that differ only in their walks are
    // the same connection for the person (and looked identical in the chart).
    val vehicles = trip.segments.filterNot { it.isWalk }
    val legs = (if (ignoreWalking && vehicles.isNotEmpty()) vehicles else trip.segments).joinToString("|", transform = ::legIdentity)
    // A walk-only result has no service time; keep separate offers at different times apart.
    val walkOnly = trip.segments.isNotEmpty() && trip.segments.all { it.isWalk }
    val time = if (walkOnly) "|t>${Math.floorDiv(trip.segments.first().plannedStartTime, 60_000L)}" else ""
    val via = if (trip.waypoints.isEmpty()) "" else "|via>" + trip.waypoints.joinToString(";", transform = ::waypointIdentity)
    return legs + time + via
}

/** Short deterministic digest, used only to disambiguate colliding provider IDs. */
internal fun identityDigest(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(6).joinToString("") { "%02x".format(it) }

private fun Trip.realtimeLegs() = segments.count { !it.isWalk && (it.realtimeDeparture || it.delay.arrivalSeconds != null) }

/** The fresher of two copies of the same connection; a newer response wins, then realtime coverage. */
fun fresherConnection(a: Trip, b: Trip): Trip = when {
    b.fetchedAt > a.fetchedAt -> b
    b.fetchedAt < a.fetchedAt -> a
    b.realtimeLegs() > a.realtimeLegs() -> b
    else -> a
}

/**
 * Merge real duplicates (same connection identity) and keep the first-seen stable ID. Different
 * connections on the same route — another service, another alighting stop or an additional walk —
 * remain separate entries.
 */
fun mergeDuplicateConnections(trips: List<Trip>, ignoreWalking: Boolean = false): List<Trip> {
    val merged = LinkedHashMap<String, Trip>()
    for (trip in trips) {
        val identity = connectionIdentity(trip, ignoreWalking)
        val existing = merged[identity]
        merged[identity] = if (existing == null) trip else fresherConnection(existing, trip).copy(id = existing.id)
    }
    return withUniqueTripIds(merged.values.toList())
}

/** Repair colliding IDs deterministically; the same connection receives the same repaired ID. */
fun withUniqueTripIds(trips: List<Trip>): List<Trip> {
    if (trips.map { it.id }.toSet().size == trips.size) return trips
    val used = HashSet<String>()
    return trips.map { trip ->
        if (used.add(trip.id)) trip else {
            val base = "${trip.id}~${identityDigest(connectionIdentity(trip))}"
            var candidate = base
            var n = 2
            while (!used.add(candidate)) candidate = "$base~${n++}"
            trip.copy(id = candidate)
        }
    }
}

/** Last line of defence for lazy lists: duplicate keys get an occurrence suffix instead of a crash. */
fun uniqueRenderKeys(keys: List<String>): List<String> {
    if (keys.toSet().size == keys.size) return keys
    val used = HashSet<String>(keys.size * 2)
    return keys.map { key ->
        var candidate = key
        var n = 2
        while (!used.add(candidate)) candidate = "$key#${n++}"
        candidate
    }
}
