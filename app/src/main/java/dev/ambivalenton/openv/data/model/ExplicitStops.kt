// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/**
 * A stop chosen on purpose (a stop from the search, a stop favourite, a manual start at a
 * stop) is where the journey begins or ends: no walk to or from another stop is added. Device
 * positions, addresses and points of interest keep their walking legs.
 */
fun Location.isExplicitStop(): Boolean = kind == LocationKind.STOP && !isDevicePosition && routable

/**
 * Whether [trip] starts at the explicit start stop and ends at the explicit destination
 * stop of [query]. A walk at the start or end is allowed only within the same station (another
 * platform of it); a walk to or from another stop is not.
 */
fun Trip.keepsExplicitStops(query: JourneyQuery): Boolean {
    val firstRide = segments.firstOrNull { !it.isWalk } ?: return true
    val lastRide = segments.lastOrNull { !it.isWalk } ?: return true
    if (query.origin.isExplicitStop() && otherStop(query.origin, firstRide.origin)) return false
    if (query.destination.isExplicitStop() && otherStop(query.destination, lastRide.destination)) return false
    return true
}

/**
 * Journeys that walk from a chosen stop to another one give way to journeys from the chosen
 * stop itself, but only when there are such journeys. A stop that is moved or closed (every journey
 * leaves from a replacement stop) keeps all of them, so the result is never emptied by this rule.
 */
fun List<Trip>.preferExplicitStops(query: JourneyQuery): List<Trip> {
    if (!query.origin.isExplicitStop() && !query.destination.isExplicitStop()) return this
    val direct = filter { it.keepsExplicitStops(query) }
    return if (direct.isEmpty()) this else direct
}

/** A place's own words without its leading city ("Herne, Bf" and "Herne Bf" → city [herne], core [bf]). */
private fun placeWords(place: Location): Pair<List<String>, List<String>> {
    val parts = place.name.split(",", limit = 2)
    val city = (if (parts.size == 2) noticeWords(parts[0]) else emptyList()).ifEmpty { place.city?.let(::noticeWords).orEmpty() }
    var core = noticeWords(place.name)
    // "Herne, Herne Bahnhof": the city may lead the name more than once.
    while (city.isNotEmpty() && core.size > city.size && core.take(city.size) == city) core = core.drop(city.size)
    return city to core
}

/** Distance up to which a stop of the same name is the same place despite differing coordinates. */
const val ENTERED_PLACE_METERS = 500.0

/**
 * Whether [stop] (where the first ride begins or the last one ends) is the place the user
 * entered: the same stop or station (IDs, DHID, name in the station area), or the same name in the
 * same city within [ENTERED_PLACE_METERS], since the directory and the timetable service may keep
 * slightly different coordinates for one stop. A device position is never an entered place.
 */
fun isEnteredPlace(entered: Location, stop: Location): Boolean {
    if (entered.isDevicePosition) return false
    if (planPlaceMatches(entered, stop)) return true
    val meters = distanceMeters(entered, stop)
    if (meters != null && meters > ENTERED_PLACE_METERS) return false
    if (noticeWords(entered.name).let { it.isNotEmpty() && it == noticeWords(stop.name) }) return true
    val (enteredCity, enteredCore) = placeWords(entered); val (stopCity, stopCore) = placeWords(stop)
    if (enteredCore.isEmpty() || enteredCore != stopCore) return false
    // The same core name: cities must agree; without a city on one side only matching coordinates count.
    return if (enteredCity.isNotEmpty() && stopCity.isNotEmpty()) enteredCity == stopCity else meters != null
}

/**
 * The provider sometimes starts a journey with a walk from a point near the entered place
 * (its coordinates, named like "Baukau-Ost (Herne), Konrad-Adenauer-Platz 5") to that very place. Such
 * a walk at the start or end is dropped: the journey begins at the stop the user entered. Walks to
 * another stop stay. Journeys with stopovers keep their legs as they are (their legs are referenced).
 */
fun Trip.withoutWalksAtEnteredPlaces(origin: Location, destination: Location): Trip {
    if (waypoints.isNotEmpty() || waypointLegs.isNotEmpty()) return this
    val firstRide = segments.indexOfFirst { !it.isWalk }.takeIf { it >= 0 } ?: return this
    val lastRide = segments.indexOfLast { !it.isWalk }
    val dropStart = firstRide > 0 && isEnteredPlace(origin, segments[firstRide].origin)
    val dropEnd = lastRide < segments.lastIndex && isEnteredPlace(destination, segments[lastRide].destination)
    if (!dropStart && !dropEnd) return this
    return copy(segments = segments.subList(if (dropStart) firstRide else 0, (if (dropEnd) lastRide else segments.lastIndex) + 1))
}

/**
 * A refreshed journey keeps the shape it had: when the shown journey began (or ended) at
 * its stop, a walk the refresh adds before (or after) that same stop is dropped again.
 */
fun Trip.withWalksOf(shown: Trip): Trip {
    if (waypoints.isNotEmpty() || waypointLegs.isNotEmpty()) return this
    val shownFirst = shown.segments.firstOrNull() ?: return this
    val shownLast = shown.segments.last()
    val firstRide = segments.indexOfFirst { !it.isWalk }.takeIf { it >= 0 } ?: return this
    val lastRide = segments.indexOfLast { !it.isWalk }
    val dropStart = firstRide > 0 && !shownFirst.isWalk && isEnteredPlace(shownFirst.origin, segments[firstRide].origin)
    val dropEnd = lastRide < segments.lastIndex && !shownLast.isWalk && isEnteredPlace(shownLast.destination, segments[lastRide].destination)
    if (!dropStart && !dropEnd) return this
    return copy(segments = segments.subList(if (dropStart) firstRide else 0, (if (dropEnd) lastRide else segments.lastIndex) + 1))
}

/**
 * Only a stop that is certainly another one counts: not the same stop or station (IDs, DHID, name in
 * the station area) and farther than the station area, or, without coordinates, of another name.
 * Uncertain data never removes a journey.
 */
internal fun otherStop(explicit: Location, place: Location): Boolean {
    if (planPlaceMatches(explicit, place)) return false
    val meters = distanceMeters(explicit, place)
    return if (meters != null) meters > STATION_ENTER_METERS
    else noticeWords(explicit.name).joinToString("") != noticeWords(place.name).joinToString("")
}
