// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import kotlinx.serialization.Serializable
import kotlin.math.abs

/*
 * The passenger took another vehicle than the pinned one.
 *
 * Example from a real trip: the passenger boarded an underground train earlier than planned. In the
 * tunnel there was no GPS; afterwards they walked on. The route cursor still waited on the access
 * walk (a jump onto the ride was never accepted) and the assistant kept asking them to board soon.
 *
 * Evidence, all local and bounded:
 * - a fresh, accurate fix lies on the corridor of the planned ride, clearly beyond the boarding
 *   station (fence + accuracy, at least 400 m along the route);
 * - vehicle travel: the GPS cursor already rides this leg, or the straight-line displacement from an
 *   earlier fix (also across a tunnel gap, via the last tracked fix) is at least 500 m at an average
 *   of 3 m/s or more, which walking cannot achieve;
 * - timing: the passenger passes that position clearly before (EARLIER) or after (LATER) the time
 *   the pinned vehicle is expected there. The expected time is the timetable at that position plus
 *   the provider's current delay. Without realtime the late tolerance is twice as large, since a
 *   missing forecast does not mean punctuality;
 * - two observations at least 15 s apart (hysteresis against single outliers).
 *
 * A confirmed observation marks the ride as boarded (so the assistant stops showing the boarding
 * instruction), then the provider is asked once per 45 s for the service that matches the observed
 * position and time. Only such a match replaces the pinned ride; the onward chain is the provider's
 * answer for that service. The vehicle identity remains an estimate: an equally fitting service of
 * the same line is preferred, never invented.
 */

@Serializable enum class RideDeviationKind { EARLIER, LATER }

@Serializable data class RideDeviationObservation(
    val rideKey: String,
    val kind: RideDeviationKind,
    val firstObservedAt: Long,
    val lastObservedAt: Long,
    val confirmations: Int,
    /** Last matched position on the pinned ride. */
    val alongMeters: Double,
    val location: Location,
    val accuracyMeters: Float,
    /** Observation time minus the time the pinned vehicle is expected at that position. */
    val offsetMillis: Long,
)

object RideDeviationPolicy {
    const val MAX_ACCURACY_METERS = 60f
    const val MIN_ALONG_METERS = 400.0
    const val MIN_TRAVEL_METERS = 500.0
    const val VEHICLE_AVERAGE_SPEED = 3.0
    const val ANCHOR_MIN_AGE_MILLIS = 20_000L
    const val ANCHOR_MAX_AGE_MILLIS = 45 * 60_000L
    const val EARLY_TOLERANCE_MILLIS = 150_000L
    const val LATE_TOLERANCE_REALTIME_MILLIS = 180_000L
    const val LATE_TOLERANCE_PLANNED_MILLIS = 360_000L
    const val CONFIRMATION_SPACING_MILLIS = 15_000L
    const val CONFIRMATION_WINDOW_MILLIS = 180_000L
    /** A confirmed observation stays usable for the provider search this long. */
    const val CONFIRMED_LIFETIME_MILLIS = 10 * 60_000L
    /** A candidate service must pass the observed position within this time (plus position slack). */
    const val MATCH_TOLERANCE_MILLIS = 180_000L
    const val SEARCH_INTERVAL_MILLIS = 45_000L
}

data class RideDeviationEvidence(val ride: IndexedValue<TripSegment>, val kind: RideDeviationKind, val alongMeters: Double,
    val offsetMillis: Long, val fix: DevicePresence)

internal fun rideStops(trip: Trip, index: Int) = focusStops(trip).filter { it.legId == "$index:${trip.segments[index].id}" }

/** Corridor half-width around a leg; the same rule matches the GPS route cursor. Without a provider
 * shape, station chords cut across real bends, so the corridor reflects that uncertainty. */
internal fun rideCorridorMeters(leg: TripSegment, stops: List<JourneyFocusStop>, accuracyMeters: Float): Double {
    val largestGap = stops.zipWithNext().maxOfOrNull { (a, b) -> distanceMeters(a.location, b.location) ?: 0.0 } ?: 0.0
    val chord = if (hasJourneyGeometry(leg)) 80.0 else if (leg.isWalk) 130.0
        else if (leg.lineType in GPS_PRIORITY_RAIL) (largestGap * .12).coerceIn(130.0, 1_500.0) else (largestGap * .08).coerceIn(130.0, 450.0)
    return maxOf(chord, accuracyMeters * 2.0)
}

/** When the pinned service is expected at [alongMeters]: timetable time there plus the provider's
 * current delay, interpolated from the departure to the arrival delay. Never adds a delay twice. */
internal fun expectedVehicleTimeAlong(leg: TripSegment, stops: List<JourneyFocusStop>, alongMeters: Double): Long? {
    val planned = plannedTimeAlong(leg, stops, alongMeters) ?: return null
    val total = journeyStopDistances(leg, stops).lastOrNull()?.takeIf { it > 0 } ?: return null
    val fraction = (alongMeters / total).coerceIn(0.0, 1.0)
    val departureDelay = leg.effectiveDeparture() - leg.plannedStartTime
    val arrivalDelay = leg.effectiveArrival() - leg.plannedEndTime
    return planned + (departureDelay + (arrivalDelay - departureDelay) * fraction).toLong()
}

/** The ride the passenger currently rides by GPS, otherwise the next ride not yet boarded. */
private fun deviationRide(session: PinnedJourneySession): IndexedValue<TripSegment>? {
    val cursorLeg = gpsCursorLeg(session)
    if (cursorLeg != null && !cursorLeg.value.isWalk && session.gpsCursor?.departed == true)
        return cursorLeg.takeIf { !it.value.delay.cancelled && stationRideKey(it.value) !in session.completedRideKeys }
    val from = cursorLeg?.index ?: 0
    return session.trip.segments.withIndex().firstOrNull { (index, leg) ->
        index >= from && !leg.isWalk && !leg.delay.cancelled && stationRideKey(leg) !in session.completedRideKeys
    }
}

private fun ridingOn(session: PinnedJourneySession, index: Int) =
    session.gpsCursor?.departed == true && gpsCursorLeg(session)?.index == index

fun rideDeviationEvidence(session: PinnedJourneySession, fix: DevicePresence?, observations: List<DevicePresence>, now: Long): RideDeviationEvidence? {
    if (session.usesServerProgress) return null
    val gps = currentJourneyGps(fix, now)?.takeIf { it.accuracyMeters <= RideDeviationPolicy.MAX_ACCURACY_METERS } ?: return null
    val ride = deviationRide(session) ?: return null
    val leg = ride.value
    val stops = rideStops(session.trip, ride.index)
    if (stops.size < 2) return null
    val projection = projectOntoJourneyLeg(leg, stops, gps.location) ?: return null
    if (projection.cross > rideCorridorMeters(leg, stops, gps.accuracyMeters)) return null
    val along = projection.along
    val originDistance = distanceMeters(gps.location, leg.origin) ?: return null
    if (originDistance <= STATION_EXIT_METERS + gps.accuracyMeters ||
        along < maxOf(RideDeviationPolicy.MIN_ALONG_METERS, gps.accuracyMeters * 3.0)) return null
    val key = stationRideKey(leg)
    val prior = session.rideDeviation?.takeIf { it.rideKey == key && now - it.lastObservedAt in 0..RideDeviationPolicy.CONFIRMED_LIFETIME_MILLIS }
    val anchors = (observations + listOfNotNull(session.lastTrackedPresence)).distinctBy { it.capturedAt }
    val vehicle = ridingOn(session, ride.index) || prior != null || anchors.any { anchor ->
        val seconds = (gps.capturedAt - anchor.capturedAt) / 1000.0
        if (gps.capturedAt - anchor.capturedAt !in RideDeviationPolicy.ANCHOR_MIN_AGE_MILLIS..RideDeviationPolicy.ANCHOR_MAX_AGE_MILLIS) return@any false
        if (!anchor.accuracyMeters.isFinite() || anchor.accuracyMeters > 100f || !anchor.location.hasCoordinates) return@any false
        val displacement = distanceMeters(anchor.location, gps.location) ?: return@any false
        displacement >= maxOf(RideDeviationPolicy.MIN_TRAVEL_METERS, (anchor.accuracyMeters + gps.accuracyMeters) * 2.0) &&
            displacement / seconds >= RideDeviationPolicy.VEHICLE_AVERAGE_SPEED
    }
    if (!vehicle) return null
    val expected = expectedVehicleTimeAlong(leg, stops, along) ?: return null
    val offset = gps.capturedAt - expected
    val slack = positionSlackMillis(leg, stops, gps.accuracyMeters)
    val realtime = leg.departureTimeSource() == TimeSource.PROGNOSIS || leg.arrivalTimeSource() == TimeSource.PROGNOSIS
    val lateTolerance = if (realtime) RideDeviationPolicy.LATE_TOLERANCE_REALTIME_MILLIS else RideDeviationPolicy.LATE_TOLERANCE_PLANNED_MILLIS
    val kind = when {
        offset <= -(RideDeviationPolicy.EARLY_TOLERANCE_MILLIS + slack) -> RideDeviationKind.EARLIER
        offset >= lateTolerance + slack -> RideDeviationKind.LATER
        else -> return null
    }
    return RideDeviationEvidence(ride, kind, along, offset, gps)
}

/** Updates the hysteresis. A confirmed observation also marks the ride as boarded at the observed
 * position: physical evidence, so the boarding instruction disappears even before any search. */
fun observeRideDeviation(session: PinnedJourneySession, fix: DevicePresence?, observations: List<DevicePresence>, now: Long): PinnedJourneySession {
    val evidence = rideDeviationEvidence(session, fix, observations, now)
    if (evidence == null) {
        val old = session.rideDeviation ?: return session
        val confirmed = old.confirmations >= 2 && now - old.lastObservedAt in 0..RideDeviationPolicy.CONFIRMED_LIFETIME_MILLIS
        val newer = currentJourneyGps(fix, now)?.takeIf { it.capturedAt > old.lastObservedAt }
        val expired = now - old.lastObservedAt !in 0..RideDeviationPolicy.CONFIRMATION_WINDOW_MILLIS
        val stillPinned = session.trip.segments.any { !it.isWalk && stationRideKey(it) == old.rideKey && stationRideKey(it) !in session.completedRideKeys }
        return if (!stillPinned || (!confirmed && (expired || newer != null))) session.copy(rideDeviation = null) else session
    }
    val key = stationRideKey(evidence.ride.value)
    val at = evidence.fix.capturedAt
    val old = session.rideDeviation?.takeIf { it.rideKey == key && it.kind == evidence.kind && at - it.lastObservedAt in 0..RideDeviationPolicy.CONFIRMATION_WINDOW_MILLIS }
    val observation = when {
        old == null -> RideDeviationObservation(key, evidence.kind, at, at, 1, evidence.alongMeters, evidence.fix.location,
            evidence.fix.accuracyMeters, evidence.offsetMillis)
        at - old.lastObservedAt >= RideDeviationPolicy.CONFIRMATION_SPACING_MILLIS -> old.copy(lastObservedAt = at, confirmations = old.confirmations + 1,
            alongMeters = evidence.alongMeters, location = evidence.fix.location, accuracyMeters = evidence.fix.accuracyMeters, offsetMillis = evidence.offsetMillis)
        else -> old
    }
    var next = session.copy(rideDeviation = observation)
    if (observation.confirmations >= 2 && !ridingOn(next, evidence.ride.index)) {
        val ride = evidence.ride
        val legKey = journeyLegKey(ride.index, ride.value)
        val stops = rideStops(next.trip, ride.index)
        val distances = journeyStopDistances(ride.value, stops)
        val passed = next.gpsCursor?.passedStopKeys.orEmpty() +
            focusStops(next.trip).filter { it.legId.substringBefore(':').toIntOrNull()?.let { i -> i < ride.index } == true }.map { it.key } +
            stops.filterIndexed { i, _ -> i < stops.lastIndex && (distances.getOrNull(i) ?: Double.MAX_VALUE) + 35.0 < evidence.alongMeters }.map { it.key }
        next = next.copy(
            gpsCursor = GpsRouteCursor(legKey, ride.index, evidence.alongMeters, true, at, passed.take(256).toSet()),
            completedRideKeys = next.completedRideKeys + next.trip.segments.take(ride.index).filterNot { it.isWalk }.map(::stationRideKey),
            stationArrival = null, missedBoarding = null, lastTrackedPresence = evidence.fix,
            activeLegAnchor = ActiveJourneyAnchor(legKey, ride.index, now),
            assistantNotice = AssistantNotice(if (evidence.kind == RideDeviationKind.EARLIER) tr("Frühere Fahrt erkannt · Zeiten werden aktualisiert")
                else tr("Spätere Fahrt erkannt · Zeiten werden aktualisiert"), now),
        )
    }
    return next
}

fun confirmedRideDeviation(session: PinnedJourneySession, now: Long): RideDeviationObservation? =
    session.rideDeviation?.takeIf { observation ->
        observation.confirmations >= 2 && now - observation.lastObservedAt in 0..RideDeviationPolicy.CONFIRMED_LIFETIME_MILLIS &&
            session.trip.segments.any { !it.isWalk && stationRideKey(it) == observation.rideKey && stationRideKey(it) !in session.completedRideKeys }
    }

/** The search for the actually taken service starts a little before its estimated departure. */
fun observedRideQuery(session: PinnedJourneySession, observation: RideDeviationObservation): JourneyQuery? {
    val index = session.trip.segments.indexOfFirst { !it.isWalk && stationRideKey(it) == observation.rideKey }.takeIf { it >= 0 } ?: return null
    val leg = session.trip.segments[index]
    val plannedAt = plannedTimeAlong(leg, rideStops(session.trip, index), observation.alongMeters) ?: return null
    val departure = observation.lastObservedAt - (plannedAt - leg.plannedStartTime).coerceAtLeast(0)
    val pending = session.query.waypoints.filterNot { waypointIdentity(it) in session.visitedWaypoints || sameLocation(it, leg.origin) }
    val keys = pending.map(::waypointIdentity).toSet()
    return session.query.copy(origin = leg.origin, time = departure - 8 * 60_000L, timeMode = TimeMode.DEPARTURE,
        waypoints = pending, waypointDwellMinutes = session.query.waypointDwellMinutes.filterKeys { it in keys },
        walkingSpeed = session.routing.walkingSpeed, ignoreWalking = session.routing.ignoreWalking)
}

data class ObservedRideMatch(val trip: Trip, val alongMeters: Double, val differenceMillis: Long, val observation: RideDeviationObservation)

/** Picks the service that passes the observed position at the observed time. The same line wins
 * over another line on the same corridor; a service that had not yet departed cannot match. */
fun matchObservedRide(session: PinnedJourneySession, observation: RideDeviationObservation, candidates: List<Trip>): ObservedRideMatch? {
    val pinned = session.trip.segments.firstOrNull { !it.isWalk && stationRideKey(it) == observation.rideKey } ?: return null
    data class Fit(val trip: Trip, val index: Int, val along: Double, val difference: Long, val sameLine: Boolean)
    val fits = candidates.mapNotNull { trip ->
        val index = trip.segments.indexOfFirst { !it.isWalk }.takeIf { it >= 0 } ?: return@mapNotNull null
        val ride = trip.segments[index]
        if (trip.cancelled || ride.delay.cancelled || ride.lineType != pinned.lineType) return@mapNotNull null
        if (stationRideKey(ride) == observation.rideKey || sameVehicleService(ride, pinned)) return@mapNotNull null
        if (!sameLocation(ride.origin, pinned.origin) && (distanceMeters(ride.origin, pinned.origin) ?: Double.MAX_VALUE) > 300.0) return@mapNotNull null
        if (ride.effectiveDeparture() > observation.lastObservedAt) return@mapNotNull null
        val earlier = ride.effectiveDeparture() < pinned.effectiveDeparture()
        if (earlier != (observation.kind == RideDeviationKind.EARLIER)) return@mapNotNull null
        val stops = rideStops(trip, index)
        val projection = projectOntoJourneyLeg(ride, stops, observation.location) ?: return@mapNotNull null
        if (projection.cross > rideCorridorMeters(ride, stops, observation.accuracyMeters)) return@mapNotNull null
        val expected = expectedVehicleTimeAlong(ride, stops, projection.along) ?: return@mapNotNull null
        val difference = observation.lastObservedAt - expected
        if (abs(difference) > RideDeviationPolicy.MATCH_TOLERANCE_MILLIS + positionSlackMillis(ride, stops, observation.accuracyMeters)) return@mapNotNull null
        Fit(trip, index, projection.along, difference, ride.lineName.trim().equals(pinned.lineName.trim(), true))
    }
    val best = fits.minWithOrNull(compareBy<Fit> { if (it.sameLine) 0 else 1 }.thenBy { abs(it.difference) }) ?: return null
    // The passenger is already on board: access walks of the answer are history.
    return ObservedRideMatch(best.trip.copy(segments = best.trip.segments.drop(best.index)), best.along, best.difference, observation)
}

/** Replaces the pinned ride and its onward chain by the matched service, already boarded. */
fun adoptObservedRide(session: PinnedJourneySession, match: ObservedRideMatch, now: Long): PinnedJourneySession {
    val trip = match.trip
    val ride = trip.segments.first()
    val key = journeyLegKey(0, ride)
    val stops = rideStops(trip, 0)
    val distances = journeyStopDistances(ride, stops)
    val passed = stops.filterIndexed { i, _ -> i < stops.lastIndex && (distances.getOrNull(i) ?: Double.MAX_VALUE) + 35.0 < match.alongMeters }.map { it.key }.toSet()
    val departure = clockTime(ride.effectiveDeparture())
    val arrival = clockTime(trip.effectiveArrival())
    val message = if (match.observation.kind == RideDeviationKind.EARLIER) tr("Frühere Fahrt erkannt · %1\$s ab %2\$s · Ankunft %3\$s", ride.lineName, departure, arrival)
        else tr("Spätere Fahrt erkannt · %1\$s ab %2\$s · Ankunft %3\$s", ride.lineName, departure, arrival)
    return session.copy(trip = trip, alternative = null, alternativeQuery = null, stationArrival = null,
        gpsCursor = GpsRouteCursor(key, 0, match.alongMeters, true, match.observation.lastObservedAt, passed),
        activeLegAnchor = ActiveJourneyAnchor(key, 0, now), missedBoarding = null, rideDeviation = null, automaticReplacement = null,
        lastSuccessAt = trip.fetchedAt, routeRevision = session.routeRevision + 1, assistantNotice = AssistantNotice(message, now))
}
