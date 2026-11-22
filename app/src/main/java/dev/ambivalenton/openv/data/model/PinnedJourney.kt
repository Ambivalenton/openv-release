// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import kotlinx.serialization.Serializable

/** Persistent travel intent with a replaceable onward chain and a retained original arrival baseline. */
@Serializable
data class PinnedJourneySession(
    val id: String = newId(),
    val favorite: Favorite,
    val query: JourneyQuery,
    val initialTrip: Trip,
    val trip: Trip = initialTrip,
    val routing: AppSettings,
    val alternative: Trip? = null,
    val alternativeQuery: JourneyQuery? = null,
    val lastSuccessAt: Long = initialTrip.fetchedAt,
    val minimized: Boolean = false,
    val leftDestinationFence: Boolean = false,
    val stationArrival:StationArrival? = null,
    val movement:JourneyMovementState = JourneyMovementState(),
    val completedRideKeys:Set<String> = emptySet(),
    val activeLegAnchor:ActiveJourneyAnchor? = null,
    val gpsCursor:GpsRouteCursor? = null,
    val automaticReplacement:AutomaticReplacement? = null,
    val missedBoarding:MissedBoardingObservation? = null,
    val lastTrackedPresence:DevicePresence? = null,
    val routeRevision:Int = 0,
    val assistantNotice:AssistantNotice? = null,
    val acknowledgedArrivalDelay:Int = 0,
    val dismissedAlternativeKeys:Set<String> = emptySet(),
    val lastAlternativePromptAt:Long = Long.MIN_VALUE,
    val visitedWaypoints:Set<String> = emptySet(),
    val observedCalls:List<ObservedJourneyCall> = emptyList(),
    val locationUnavailable:Boolean = false,
    /** The user closed the bubble for this pinned journey; only the bubble button reopens it. */
    val bubbleDismissed:Boolean = false,
    /** A just completed ride stays revisable for a short window (reversible alighting). */
    val provisionalAlighting:ProvisionalAlighting? = null,
    /** Manual "still on board" for one ride until a given time. */
    val stayAboard:StayAboard? = null,
    /** The passenger rides an earlier or later service than the pinned one (build 73). */
    val rideDeviation:RideDeviationObservation? = null,
)

enum class PinnedTrackingStatus { STARTING, RUNNING, PAUSED }

data class PinnedJourneyState(
    val session: PinnedJourneySession? = null,
    val status: PinnedTrackingStatus = PinnedTrackingStatus.PAUSED,
    val presence: DevicePresence? = null,
    val weather: ArrivalWeather? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    val storageError: String? = null,
    val visualProgress: VisualJourneyProgress? = null,
    val alert: JourneyAlert? = null,
    val arrivalPhase: ArrivalPhase = ArrivalPhase.TRACKING,
    val arrival: JourneyArrival? = null,
    val powerLocationCheck:PowerLocationCheck? = null,
    val displayFocus:JourneyFocus?=null,
    val pendingDisplayKey:String?=null,
    val pendingDisplaySince:Long=0,
    val displayRouteRevision:Int=0,
)

/** Provider IDs/refresh tokens can change; planned service identity must not. */
fun samePinnedJourney(a: Trip, b: Trip): Boolean {
    if(a.waypoints.map(::waypointIdentity)!=b.waypoints.map(::waypointIdentity)) return false
    val left = a.segments.filterNot { it.isWalk }
    val right = b.segments.filterNot { it.isWalk }
    if (left.isEmpty() || right.isEmpty()) return a.id == b.id
    return left.size == right.size && left.zip(right).all { (x, y) ->
        x.lineType == y.lineType && x.lineName.trim().equals(y.lineName.trim(), ignoreCase = true) &&
            x.plannedStartTime == y.plannedStartTime &&
            sameLocation(x.origin, y.origin) && sameLocation(x.destination, y.destination)
    }
}

/** Include every intermediate walking leg when judging whether a transfer is still feasible. */
fun unreachablePinnedTransfer(trip: Trip,ignoreWalking:Boolean=false,speed:WalkingSpeed=WalkingSpeed.AVERAGE): TripSegment? {
    val rides = trip.segments.withIndex().filterNot { it.value.isWalk }
    return rides.zipWithNext().firstOrNull { (before, after) ->
        val walking = if(ignoreWalking)0L else trip.segments.subList(before.index + 1, after.index)
            .sumOf { walkingDurationMillis(it,speed) }
        before.value.effectiveArrival() + walking > after.value.effectiveDeparture()
    }?.second?.value
}

data class PinnedJourneyCopy(
    val nextStep: String,
    val position: String,
    val disruption: String? = null,
    val recommendation: String? = null,
)

/** Copy is derived from facts; timetable progress is explicitly a forecast, never a boarding claim. */
object PinnedJourneyNarrator {
    fun describe(session: PinnedJourneySession, now: Long, presence: DevicePresence?): PinnedJourneyCopy {
        val trip = session.trip
        val gps=if(session.usesServerProgress)null else currentJourneyGps(presence,now)
        val spatial=if(gps!=null) resolvedJourneyProgress(session,now,gps) else null
        val atStation=if(gps!=null) gpsCursorLeg(session)?.value?.takeIf {spatial?.boardingLegId!=null} else null
        val upcoming = if(gps!=null) gpsCursorLeg(session)?.value else trip.segments.firstOrNull {it.endTime>now && (it.isWalk || stationRideKey(it) !in session.completedRideKeys)}
        val rides = trip.segments.filterNot { it.isWalk }
        val near = gps?.let { fix ->
            trip.segments.flatMap { listOf(it.origin, it.destination) + it.stops.map { stop -> stop.location } }
                .distinct().mapNotNull { stop -> distanceMeters(fix.location, stop)?.let { stop to it } }
                .filter { it.second <= maxOf(150.0, fix.accuracyMeters.toDouble()) }
                .minByOrNull { it.second }?.first
        }
        val position = when {
            near != null -> tr("Dein gemessener Standort liegt ungefähr bei %1\$s.", near.name)
            gps != null -> tr("GPS-Standort wird zugeordnet")
            upcoming != null && !upcoming.isWalk && !upcoming.delay.cancelled && now >= upcoming.startTime ->
                tr("Laut Fahrplan ist %1\$s gerade zwischen %2\$s und %3\$s unterwegs.",
                    upcoming.lineName, upcoming.origin.name, upcoming.destination.name)
            else -> tr("Deine Position auf der Verbindung ist noch nicht bestätigt.")
        }
        val nextStop = upcoming?.stops?.firstOrNull {
            !it.cancelled && (it.arrivalTime ?: it.departureTime ?: Long.MIN_VALUE) > now
        }?.location ?: upcoming?.destination
        val next = when {
            gps!=null && upcoming==null -> tr("GPS-Standort wird zugeordnet")
            gps!=null && spatial?.boardingLegId==null && upcoming?.isWalk==false -> tr("Nächster Halt: %1\$s",focusStops(trip).getOrNull(spatial?.nextIndex ?: 0)?.location?.name ?: upcoming.destination.name)
            atStation!=null -> boarding(atStation,session.routing.overview)
            upcoming == null -> tr("Die erwartete Ankunft ist erreicht. Prüfe, ob du am Ziel bist.")
            upcoming.isWalk -> tr("Gehe als Nächstes zu %1\$s.", upcoming.destination.name)
            now >= upcoming.startTime -> tr("Nächster Halt laut Fahrplan: %1\$s. Dein Ausstieg ist %2\$s.",
                nextStop?.name.orEmpty(), upcoming.destination.name)
            else -> boarding(upcoming, session.routing.overview)
        }
        val cancelled = rides.firstOrNull { it.delay.cancelled }
        val missed = unreachablePinnedTransfer(trip,session.routing.ignoreWalking,session.routing.walkingSpeed)
        val delayed = rides.maxByOrNull {
            maxOf(it.delay.departureSeconds ?: 0, it.delay.arrivalSeconds ?: 0,
                ((it.startTime - it.plannedStartTime) / 1000).toInt(),
                ((it.endTime - it.plannedEndTime) / 1000).toInt())
        }
        val delay = delayed?.let {
            maxOf(it.delay.departureSeconds ?: 0, it.delay.arrivalSeconds ?: 0,
                ((it.startTime - it.plannedStartTime) / 1000).toInt(),
                ((it.endTime - it.plannedEndTime) / 1000).toInt())
        } ?: 0
        val platforms = trip.segments.mapNotNull { updated ->
            val original = session.initialTrip.segments.firstOrNull {
                it.lineName == updated.lineName && it.plannedStartTime == updated.plannedStartTime &&
                    sameLocation(it.origin, updated.origin)
            }
            updated.departurePlatform?.takeIf {
                !original?.departurePlatform.isNullOrBlank() && it != original.departurePlatform
            }?.let { tr("%1\$s fährt jetzt ab %2\$s.", updated.lineName, platform(updated)) }
        }
        val reasons = buildList {
            if (cancelled != null) add(tr("%1\$s fällt laut Anbieter aus.", vehicleName(cancelled)))
            else if (delay > 0 && delayed != null)
                add(tr("%1\$s hat voraussichtlich %2\$s Minuten Verspätung.", vehicleName(delayed), (delay + 59) / 60))
            if (missed != null) add(tr("Mit den aktuellen Zeiten ist dein Anschluss an %1\$s nicht erreichbar.", vehicleName(missed)))
        }
        val disruption = (reasons + platforms).takeIf { it.isNotEmpty() }?.joinToString(" ")
        val alternative = availablePinnedAlternative(session,now,presence)?.segments?.firstOrNull { !it.isWalk }
        val recommendation = alternative?.let {
            tr("Alternative ab %1\$s: %2\$s", session.alternativeQuery?.origin?.name ?: it.origin.name,
                boarding(it, session.routing.overview, alternative = true))
        }
        return PinnedJourneyCopy(
            nextStep = recommendation ?: if (cancelled != null || missed != null)
                tr("Prüfe die Alternative, bevor du weiterfährst.") else next,
            position = position,
            disruption = disruption,
            recommendation = recommendation,
        )
    }

    private fun boarding(segment: TripSegment, settings: OverviewSettings, alternative: Boolean = false): String = buildString {
        append(if (alternative) tr("Nimm stattdessen %1\$s", vehicleName(segment)) else tr("Nimm %1\$s", vehicleName(segment)))
        if (settings.showDepartureTime) append(tr(" um %1\$s Uhr", clockTime(segment.startTime)))
        append(tr(" ab %1\$s", segment.origin.name))
        if (!segment.departurePlatform.isNullOrBlank()) append(tr(" an %1\$s", platform(segment)))
        append('.')
    }

    private fun vehicleName(segment: TripSegment): String =
        if (segment.lineType == LineType.BUS && !segment.lineName.startsWith("Bus", ignoreCase = true))
            tr("%1\$s %2\$s", tr("Bus"), segment.lineName) else segment.lineName

    private fun platform(segment:TripSegment):String=platformName(segment.departurePlatform,segment.lineType).orEmpty()

}

/** Recovery starts at a reachable stop, never at an arbitrary GPS coordinate while on a train. */
fun pinnedRecoveryQuery(session: PinnedJourneySession, now: Long, presence: DevicePresence?): JourneyQuery {
    val start=session.trip.segments.firstOrNull()?.effectiveDeparture()
    if(start!=null && start-now>=5*60_000L && session.gpsCursor?.departed!=true && stationArrivalRide(session,now)==null)
        return session.query.copy(origin=session.trip.segments.first().origin,time=start,timeMode=TimeMode.DEPARTURE)
    val fix=if(session.usesServerProgress)null else currentJourneyGps(presence,now)
    val tracked=if(fix!=null && session.gpsCursor==null) advanceGpsJourney(session,fix,listOf(fix),now) else session
    // A short GNSS gap does not move recovery back to a timetable-selected origin.
    val cursorLeg=if(!session.usesServerProgress && (fix!=null || tracked.hasRecentRouteEvidence(now)))gpsCursorLeg(tracked)?.value else null
    val segment=cursorLeg ?: session.trip.segments.firstOrNull {it.endTime>now && (it.isWalk || stationRideKey(it) !in session.completedRideKeys)}
    val station=cursorLeg?.takeIf {!it.isWalk && tracked.gpsCursor?.departed==false}
    val riding=assistantCurrentRide(tracked,now)?.value
    val origin=station?.origin ?: riding?.destination ?: fix?.location ?: segment?.origin ?: session.query.origin
    val gpsArrival=assistantCurrentRide(tracked,now)?.let {assistantRideArrival(tracked,it,now)} ?: now
    val time=maxOf(now,if(riding!=null) gpsArrival else now)+(if(riding!=null && !session.routing.ignoreWalking) 60_000 else 0)
    val passed=focusStops(session.trip).filter {it.key in session.gpsCursor?.passedStopKeys.orEmpty()}.map {it.location}
    val pending=if(fix!=null || session.gpsCursor!=null) session.query.waypoints.filterNot {point->waypointIdentity(point) in session.visitedWaypoints || passed.any {sameLocation(it,point)}} else remainingWaypoints(session.trip,time).filterNot {waypointIdentity(it) in session.visitedWaypoints}
    val waypoints=pending.filterNot {sameLocation(it,origin)}
    val keys=waypoints.map(::waypointIdentity).toSet()
    return JourneyQuery(origin,session.query.destination,time,TimeMode.DEPARTURE,session.query.products,waypoints,
        session.query.waypointDwellMinutes.filterKeys {it in keys},session.routing.walkingSpeed,session.routing.ignoreWalking,session.query.transportFilter)
}
