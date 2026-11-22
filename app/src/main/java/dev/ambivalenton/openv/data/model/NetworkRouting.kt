// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/*
 * Proximity routing (build 66). Within 500 m of a favourite the whole app starts its calculations
 * there instead of at the raw GPS position (the departures view and the journey companion keep the
 * real position). Hysteresis: a node stays the anchor up to 600 m and is replaced only by a node
 * that is clearly (100 m) nearer. Missing or coarse positions never create an anchor.
 */

const val PROXIMITY_ENTER_METERS = 500.0
const val PROXIMITY_EXIT_METERS = 600.0
const val PROXIMITY_SWITCH_MARGIN_METERS = 100.0
/** The radius is a setting since build 67 (favourite settings): 0 = off, up to 2000 m, or "always". */
const val FAVORITE_RADIUS_DEFAULT = 500
const val FAVORITE_RADIUS_MAX = 2000
/** Right end of the slider: the nearest favourite is the start, however far away it is. */
const val FAVORITE_RADIUS_ALWAYS = Int.MAX_VALUE
fun normalizedFavoriteRadius(value: Int) = if (value > FAVORITE_RADIUS_MAX) FAVORITE_RADIUS_ALWAYS else value.coerceAtLeast(0)
/** Minimum transfer at a forced via point: two separate requests must not form a 0-minute change. */
const val VIA_TRANSFER_BUFFER_MINUTES = 4f
const val OVERVIEW_MAX_NETWORK_ROWS = 8
const val OVERVIEW_MAX_VIA_TARGETS = 3

data class ProximityAnchor(val nodeId: String, val distanceMeters: Double, val since: Long)

/** Shared hysteresis memory of overview and search (process-local, like the line orientation). */
object ProximityMemory {
    @Volatile var anchor: ProximityAnchor? = null
    /** Source the [anchor] was derived from: the hysteresis smooths the GPS signal only, it never
     * carries a manual start's node over to the GPS position (build 69). */
    @Volatile var source: OriginSource? = null
    /** "Genauer Standort": this node does not snap again until the person has left its radius. */
    @Volatile var suppressedNodeId: String? = null
    fun reset() { anchor = null; source = null; suppressedNodeId = null }
}

/** Pure anchor rule; [previous] carries the hysteresis. A device fix must be fresh (≤ 50 m, ≤ 5 min).
 * [radiusMeters] is the person's setting: the node is kept up to 1.2 × the radius and replaced only
 * by a node that is 0.2 × the radius nearer (500 m → 600 m and 100 m, as in build 66). */
fun proximityAnchor(favorites: List<Favorite>, position: Location?, presence: DevicePresence?, manual: Boolean,
                    previous: ProximityAnchor?, now: Long, radiusMeters: Int = FAVORITE_RADIUS_DEFAULT): ProximityAnchor? {
    val radius = normalizedFavoriteRadius(radiusMeters)
    if (radius == 0 || position == null || !position.hasCoordinates) return null
    val always = radius == FAVORITE_RADIUS_ALWAYS
    val enter = if (always) Double.MAX_VALUE else radius.toDouble()
    val exit = if (always) Double.MAX_VALUE else radius * (PROXIMITY_EXIT_METERS / PROXIMITY_ENTER_METERS)
    val margin = if (always) PROXIMITY_SWITCH_MARGIN_METERS else radius * (PROXIMITY_SWITCH_MARGIN_METERS / PROXIMITY_ENTER_METERS)
    if (!manual && presence?.takeIf { it.location == position }?.isFresh(now) != true) return null
    val distances = favorites.mapNotNull { f -> distanceMeters(position, f.location)?.let { f.id to it } }
    val nearest = distances.minWithOrNull(compareBy({ it.second }, { it.first })) ?: return null
    if (previous != null) {
        val current = distances.firstOrNull { it.first == previous.nodeId }
        if (current != null && current.second <= exit && nearest.second > current.second - margin)
            return previous.copy(distanceMeters = current.second)
    }
    return nearest.takeIf { it.second <= enter }?.let { ProximityAnchor(it.first, it.second, now) }
}

/** Where the app's start position comes from right now. */
enum class OriginSource { OVERRIDE, MANUAL, DEVICE }

fun StoredState.originSource(): OriginSource = when {
    locationOverride != null && settings.originMode == OriginMode.MANUAL && settings.manualOrigin == locationOverride.location -> OriginSource.OVERRIDE
    settings.originMode == OriginMode.MANUAL && settings.manualOrigin?.isDevicePosition != true -> OriginSource.MANUAL
    else -> OriginSource.DEVICE
}

/** Current anchor for [position] (default: the app's routing position); updates the shared memory. */
fun StoredState.currentAnchor(now: Long = System.currentTimeMillis(), position: Location? = routingPosition(now),
                              presence: DevicePresence? = this.presence): ProximityAnchor? {
    val source = originSource()
    // A time-dependent location is the favourite's own place: the calculations start at that node.
    val scheduled = scheduledStart(now)?.takeIf { position != null && it.location == position }
    val anchor = if (source == OriginSource.OVERRIDE) locationOverride?.let { ProximityAnchor(it.nodeId, 0.0, it.activatedAt) }
        else if (scheduled != null) ProximityAnchor(scheduled.id, 0.0, now)
        else proximityAnchor(favorites, position, presence, source == OriginSource.MANUAL,
            // Hysteresis smooths GPS noise only: a deliberately chosen manual start is judged as it is.
            ProximityMemory.anchor?.takeIf { source == OriginSource.DEVICE && ProximityMemory.source == source }, now, settings.favoriteRadiusMeters)
    ProximityMemory.anchor = anchor
    ProximityMemory.source = source
    // Leaving every radius ends a "Genauer Standort" choice; the next arrival snaps again.
    if (anchor == null) ProximityMemory.suppressedNodeId = null
    return anchor
}

/** Favourite whose place replaces the raw GPS start (null: start at the measured position).
 * Override and manual starts are positions of their own and are never replaced. */
fun StoredState.snappedStart(now: Long = System.currentTimeMillis(), position: Location? = routingPosition(now),
                             presence: DevicePresence? = this.presence): Favorite? {
    if (originSource() != OriginSource.DEVICE) return null
    val anchor = currentAnchor(now, position, presence) ?: return null
    if (anchor.nodeId == ProximityMemory.suppressedNodeId) return null
    return favorites.firstOrNull { it.id == anchor.nodeId }
}

/** One overview row in anchored mode: from the anchor to a reachable destination. */
data class NetworkRoute(
    val anchor: Favorite,
    val target: Favorite,
    val paths: List<NetworkPath>,
    val directEdge: Boolean,
) {
    val rowKey get() = "${anchor.id}>${target.id}"
    /** First lines of the paths, used to place the departure chips at the anchor. */
    val firstEdgeIds get() = paths.mapNotNull { it.edgeIds.firstOrNull() }.toSet()
    /** Presentation adapter for the sentence generator: pair texts without the word "Paar". */
    fun routingFavorite() = target.copy(id = rowKey, counterpole = anchor.location, originAccentSeed = anchor.accentSeed,
        originLabel = anchor.label)
}

/** Destinations reachable from [anchorId]; empty when the anchor has no (active) outgoing lines. */
fun StoredState.networkRoutes(anchorId: String, now: ZonedDateTime): List<NetworkRoute> {
    val byId = favorites.associateBy { it.id }
    val anchor = byId[anchorId] ?: return emptyList()
    val edges = network.activeEdges(now)
    val roles = network.effectiveRoles(byId)
    return reachableTargets(anchorId, edges, roles).mapNotNull { (id, paths) ->
        val target = byId[id] ?: return@mapNotNull null
        if (sameLocation(target.location, anchor.location)) return@mapNotNull null
        NetworkRoute(anchor, target, paths, paths.any { it.via.isEmpty() })
    }.sortedWith(compareByDescending<NetworkRoute> { it.target.rule?.matches(now) == true }
        .thenByDescending { r -> r.paths.any { p -> p.edgeIds.any { e -> edges.firstOrNull { it.id == e }?.rule?.matches(now) == true } } }
        .thenBy { r -> favorites.indexOfFirst { it.id == r.target.id } })
        .take(OVERVIEW_MAX_NETWORK_ROWS)
}

/** One request option for a row: the direct search or the forced way over transfer points. */
data class RouteCandidate(val viaNodeIds: List<String>, val query: JourneyQuery) {
    val key get() = viaNodeIds.joinToString(",")
}

/**
 * Direct search always (the chain's intermediate points are skipped, the provider finds a way over
 * them itself if it is best). With a redundancy (direct line plus chain to the same target) the way
 * over the chain is requested too, with a minimum transfer; the faster one is shown.
 */
fun StoredState.routeCandidates(route: NetworkRoute, departure: Long, allowVia: Boolean): List<RouteCandidate> {
    val s = settings
    val target = route.target
    val products = target.transportFilter?.map { it.lineType }?.toSet() ?: s.products
    val direct = JourneyQuery(route.anchor.location, target.location, departure, TimeMode.DEPARTURE, products,
        walkingSpeed = s.walkingSpeed, ignoreWalking = s.ignoreWalking, transportFilter = target.transportFilter)
    if (!allowVia || !route.directEdge) return listOf(RouteCandidate(emptyList(), direct))
    val chain = route.paths.firstOrNull { it.via.isNotEmpty() } ?: return listOf(RouteCandidate(emptyList(), direct))
    val stops = chain.via.mapNotNull { id -> favorites.firstOrNull { it.id == id }?.location?.takeIf { it.routable } }
    if (stops.size != chain.via.size) return listOf(RouteCandidate(emptyList(), direct))
    val via = direct.copy(waypoints = stops, waypointDwellMinutes = stops.associate { waypointIdentity(it) to VIA_TRANSFER_BUFFER_MINUTES })
    return listOf(RouteCandidate(emptyList(), direct), RouteCandidate(chain.via, via))
}

/** Earliest realistic arrival wins; the shown option stays unless another arrives ≥ 1 min earlier. */
fun pickFastest(results: List<Pair<RouteCandidate, Trip>>, shownKey: String?): Pair<RouteCandidate, Trip>? {
    val valid = results.filter { !it.second.cancelled }
    val best = valid.minWithOrNull(compareBy({ it.second.endTime }, { it.second.totalDuration },
        { it.second.transfers }, { it.first.viaNodeIds.size })) ?: return null
    val kept = valid.firstOrNull { it.first.key == shownKey }
    return if (kept != null && kept.second.endTime - best.second.endTime < 60_000) kept else best
}

fun zonedNow(now: Long): ZonedDateTime = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
