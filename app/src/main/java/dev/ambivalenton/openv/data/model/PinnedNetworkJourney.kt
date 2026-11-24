// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Presentation of the existing assistant state; never starts another tracker or changes a route. */
data class PinnedNetworkJourney(
    val edgeId: String,
    val forward: Boolean,
    /** From the journey's start favourite to its destination, independent of the edge direction. */
    val fraction: Float?,
    val lineType: LineType,
    val lineName: String,
    val source: JourneyPositionSource,
    /** 2.6: the GPS fix is not matched to the route yet; the marker follows the timetable meanwhile. */
    val gpsPending: Boolean = false,
)

/**
 * 2.6: whether a network favourite stands for a place of the journey. Stable references first (the
 * provider's stop ID, the DHID of the station, ignoring platform parts), then the same station name
 * within the station area: trip legs often carry platform coordinates, which can lie well over 50 m
 * from the coordinates of the station that was saved as a favourite.
 */
fun planPlaceMatches(favorite: Location, place: Location): Boolean {
    if (sameLocation(favorite, place)) return true
    val a = favorite.dhid?.let(::stationDhid); val b = place.dhid?.let(::stationDhid)
    if (a != null && b != null) return a == b
    if (favorite.kind != LocationKind.STOP || place.kind != LocationKind.STOP) return false
    val meters = distanceMeters(favorite, place) ?: return false
    return meters <= STATION_ENTER_METERS && stationName(favorite) == stationName(place)
}

/**
 * Build 119 · the plan's own, broader reading of "this favourite is a place of the journey" (only for the
 * progress train; routing and timelines keep [planPlaceMatches]). A home or work address and the place
 * the person starts from (GPS) lie tens of metres apart; within [PLAN_PLACE_METERS] they count as one.
 * A station favourite also matches a platform of it close by, and a station name with or without its
 * town ("Hauptbahnhof" / "Dortmund Hbf").
 */
fun planFavoriteOnJourney(favorite: Location, place: Location): Boolean {
    if (planPlaceMatches(favorite, place)) return true
    val meters = distanceMeters(favorite, place) ?: return false
    if (favorite.kind != LocationKind.STOP || place.kind != LocationKind.STOP) return meters <= PLAN_PLACE_METERS
    if (meters <= PLAN_PLATFORM_METERS) return true
    if (meters > STATION_ENTER_METERS) return false
    val a = stationName(favorite); val b = stationName(place)
    return a.isNotEmpty() && b.isNotEmpty() && (a.endsWith(b) || b.endsWith(a))
}
/** A favourite that is not a stop (home, work) and where the journey starts or ends. */
const val PLAN_PLACE_METERS = 250.0
/** Platform coordinates of a station lie this close to its saved point. */
const val PLAN_PLATFORM_METERS = 80.0

private fun stationDhid(value: String): String? = value.split(':').takeIf { it.size >= 3 }?.take(3)?.joinToString(":")
/** Normalised words ("Hauptbahnhof" = "Hbf", "Straße" = "Str."), so spellings of one station meet. */
private fun stationName(place: Location) = noticeWords(place.name).joinToString("")

/**
 * Match places by their routing identity, never by their editable favourite names. Direct lines
 * take priority; a journey through more favourites can also follow individual sections of its plan.
 * GPS uses the assistant's corrected distance, short gaps hold it, and only the assistant's
 * timetable fallback interpolates calls. Waiting at a stop does not advance the marker.
 */
fun pinnedNetworkJourney(store: StoredState, state: PinnedJourneyState?, now: Long): PinnedNetworkJourney? {
    val session = state?.session ?: return null
    val trip = session.trip
    if (trip.segments.isEmpty()) return null
    val stops = focusStops(trip)
    val legStops = trip.segments.indices.map { i -> stops.filter { it.legId == "$i:${trip.segments[i].id}" } }
    val distances = trip.segments.indices.map { i ->
        val measured = journeyStopDistances(trip.segments[i], legStops[i])
        if ((measured.lastOrNull() ?: 0.0) > 0.0) measured else {
            val length = trip.segments[i].distanceMeters?.takeIf { it > 0 }?.toDouble()
                ?: ((trip.segments[i].endTime - trip.segments[i].startTime) / 1_000.0).coerceAtLeast(1.0)
            List(legStops[i].size) { j -> length * j / (legStops[i].size - 1).coerceAtLeast(1) }
        }
    }
    val offsets = distances.runningFold(0.0) { sum, leg -> sum + (leg.lastOrNull() ?: 0.0) }
    val total = offsets.last()
    if (total <= 0.0 || !total.isFinite()) return null
    val progress = state.visualProgress?.takeIf { !session.usesServerProgress && now - it.computedAt in 0..30_000 }
        ?: resolvedJourneyProgress(session, now, state.presence)
    var source = progress.positionSource
    var legIndex = (progress.stableLegId ?: progress.boardingLegId)?.substringBefore(':')?.toIntOrNull()
        ?.takeIf { it in trip.segments.indices }
    var along = progress.fraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)?.times(total)
    var gpsPending = false
    if (source == JourneyPositionSource.GPS_UNMATCHED) {
        if (along != null && session.hasRecentRouteEvidence(now)) source = JourneyPositionSource.GPS_LAST_KNOWN
        else {
            // 2.6: an unmatched fix must not hide the train: until GPS fits the route, the marker
            // follows the assistant's timetable fallback and says so.
            along = null; source = JourneyPositionSource.TIMETABLE; gpsPending = true
        }
    }
    if (source == JourneyPositionSource.TIMETABLE) {
        val projection = projectSessionTimes(session, now)
        legIndex = trip.segments.indices.firstOrNull { i ->
            (projection.leg(i)?.arrival?.time ?: trip.segments[i].endTime) > now &&
                (trip.segments[i].isWalk || stationRideKey(trip.segments[i]) !in session.completedRideKeys)
        } ?: trip.segments.lastIndex
        val i = legIndex
        val calls = legStops[i]
        val projected = projection.leg(i)
        fun arrival(j: Int) = when (j) {
            0 -> projected?.departure?.time ?: calls[j].time
            calls.lastIndex -> projected?.arrival?.time ?: calls[j].time
            else -> calls[j].time + (projected?.localOffsetMillis ?: 0)
        }
        fun departure(j: Int) = if (j == 0) arrival(j) else calls[j].departure + (projected?.localOffsetMillis ?: 0)
        var local = distances[i].lastOrNull() ?: 0.0
        for (j in calls.indices) {
            if (now <= departure(j)) { local = distances[i][j]; break }
            if (j < calls.lastIndex && now < arrival(j + 1)) {
                val f = ((now - departure(j)).toDouble() / (arrival(j + 1) - departure(j)).coerceAtLeast(1)).coerceIn(0.0, 1.0)
                local = distances[i][j] + (distances[i][j + 1] - distances[i][j]) * f
                break
            }
        }
        along = offsets[i] + local
    } else if (legIndex == null) legIndex = gpsCursorLeg(session)?.index

    val favorites = store.favorites.associateBy { it.id }
    val placed = store.network.placed
    data class Section(val edge: NetworkEdge, val forward: Boolean, val from: Double, val to: Double, val direct: Boolean)
    fun matches(f: Favorite, p: Location) = planFavoriteOnJourney(f.location, p)
    fun positions(f: Favorite): List<Double> = buildList {
        if (matches(f, session.query.origin)) add(0.0)
        legStops.forEachIndexed { i, calls -> calls.forEachIndexed { j, call ->
            if (matches(f, call.location)) add(offsets[i] + distances[i][j])
        } }
        if (matches(f, session.query.destination)) add(total)
    }.distinct()
    val sections = store.network.edges.filter { it.aId in placed && it.bId in placed }.flatMap { edge ->
        val a = favorites[edge.aId] ?: return@flatMap emptyList()
        val b = favorites[edge.bId] ?: return@flatMap emptyList()
        if (matches(a, session.query.origin) && matches(b, session.query.destination))
            return@flatMap listOf(Section(edge, true, 0.0, total, true))
        if (matches(b, session.query.origin) && matches(a, session.query.destination))
            return@flatMap listOf(Section(edge, false, 0.0, total, true))
        positions(a).flatMap { x -> positions(b).mapNotNull { y ->
            if (kotlin.math.abs(x - y) < 1.0) null else Section(edge, x < y, minOf(x, y), maxOf(x, y), false)
        } }
    }
    fun distance(section: Section): Double = along?.let { maxOf(section.from - it, it - section.to, 0.0) } ?: 0.0
    val section = sections.minWithOrNull(compareByDescending<Section> { it.direct }
        .thenBy { distance(it) }.thenBy { it.to - it.from }) ?: return null
    val leg = trip.segments.getOrNull(legIndex ?: 0) ?: trip.segments.first()
    return PinnedNetworkJourney(section.edge.id, section.forward,
        along?.takeIf { section.direct || it in section.from..section.to }
            ?.let { ((it - section.from) / (section.to - section.from)).toFloat().coerceIn(0f, 1f) },
        if (leg.delay.cancelled) LineType.OTHER else leg.lineType, leg.lineName, source, gpsPending)
}
