// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlin.math.ceil

/** A historical timetable departure is usable only when its real departure is still reachable.
 * Rebase the approach walk to the present; an old walking start is not a missed train. */
fun reachableAlternative(candidate:Trip,query:JourneyQuery,now:Long,presence:DevicePresence?,
    speed:WalkingSpeed=WalkingSpeed.AVERAGE):Trip? {
    if(candidate.segments.isEmpty() || candidate.cancelled || candidate.segments.any {leg->!leg.isWalk &&
        (leg.lineType !in query.products || (LineType.ICE !in query.products && leg.isLongDistance()))}) return null
    // Some compatible responses expose delay seconds without replacing their planned timestamp.
    val segments=candidate.segments.map {leg->leg.copy(
        startTime=if(leg.startTime==leg.plannedStartTime && (leg.delay.departureSeconds ?: 0)>0)
            leg.plannedStartTime+leg.delay.departureSeconds!!*1000L else leg.startTime,
        endTime=if(leg.endTime==leg.plannedEndTime && (leg.delay.arrivalSeconds ?: 0)>0)
            leg.plannedEndTime+leg.delay.arrivalSeconds!!*1000L else leg.endTime)}
    if(segments.any {it.endTime<it.startTime}) return null
    val trip=candidate.copy(segments=segments)
    if(!trip.matchesTransportFilter(query.transportFilter))return null
    if(query.ignoreWalking)return trip.takeIf {it.segments.firstOrNull {!it.isWalk}?.effectiveDeparture()?.let {at->at>=maxOf(now,query.time)}==true && unreachablePinnedTransfer(it,true)==null}
    val first=segments.indexOfFirst {!it.isWalk}
    val ready=maxOf(now,query.time)
    if(first<0) {
        if(query.waypoints.isNotEmpty()) return trip.takeIf {it.startTime>=ready}
        var clock=ready
        return trip.copy(segments=segments.map {leg->val start=clock;clock+=leg.endTime-leg.startTime;leg.copy(startTime=start,endTime=clock)})
    }
    val boarding=segments[first]
    if(boarding.startTime<ready) return null
    val fix=presence?.takeIf {now-it.capturedAt in 0..90_000 && it.location.hasCoordinates &&
        it.accuracyMeters.isFinite() && it.accuracyMeters in 0f..150f}
    val atRailStation=boarding.lineType in setOf(LineType.ICE,LineType.REGIONAL,LineType.SUBURBAN,LineType.SUBWAY) &&
        fix?.let {(distanceMeters(it.location,boarding.origin) ?: Double.MAX_VALUE)<=STATION_ENTER_METERS}==true
    val prefix=segments.take(first)
    val walking=prefix.sumOf {walkingDurationMillis(it,speed)}
    val physical=if(atRailStation || sameLocation(query.origin,boarding.origin)) 0L else
        distanceMeters(fix?.location ?: query.origin,boarding.origin)?.let {ceil(it/speed.metersPerSecond*1000).toLong()} ?: walking
    // The tolerant 200 m station area keeps the display calm, but it proves neither the right
    // platform nor boarding: a platform allowance plus the remaining straight walk stays required.
    val stationAccess=STATION_PLATFORM_ALLOWANCE_MILLIS+(fix?.let {distanceMeters(it.location,boarding.origin)}
        ?.let {ceil(it/speed.metersPerSecond*1000).toLong()} ?: 0L)
    val access=if(atRailStation) stationAccess else maxOf(walking,physical)
    if(boarding.startTime-ready<access || unreachablePinnedTransfer(trip,false,speed)!=null) return null
    // Never rewrite the chronology of an intermediate stop/dwell that has not been completed.
    if(query.waypoints.isNotEmpty() && (prefix.any {it.startTime<ready} || trip.waypointVisits.any {it.arrivalTime<ready})) return null
    if(atRailStation && prefix.isNotEmpty() && query.waypoints.isEmpty()) return trip.copy(segments=segments.drop(first))
    if(prefix.isEmpty()) return trip
    var clock=ready
    val adjusted=prefix.map {leg->val start=clock;clock+=leg.endTime-leg.startTime;leg.copy(startTime=start,endTime=clock)}
    return trip.copy(segments=adjusted+segments.drop(first))
}
fun availablePinnedAlternative(session:PinnedJourneySession,now:Long,presence:DevicePresence?)=
    session.alternative?.let {trip->session.alternativeQuery?.let {query->reachableAssistantAlternative(session,query,trip,now,presence)?.takeIf {candidate->
        adoptPinnedAlternative(session,query,candidate,now)?.trip?.let {assistantRouteDecision(session,it,now)==AssistantRouteDecision.OFFER}==true}}}
