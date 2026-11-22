// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Provider timestamps and explicit delay fields describe the same event; never add them twice. */
fun TripSegment.effectiveDeparture():Long=if(startTime==plannedStartTime)plannedStartTime+(delay.departureSeconds?:0)*1000L else startTime
/** Without an arrival forecast, a known positive departure delay is carried (journeys stored
 * before build 65 had no carried arrival timestamp). */
fun TripSegment.effectiveArrival():Long=if(endTime==plannedEndTime)plannedEndTime+
    (delay.arrivalSeconds ?: delay.departureSeconds?.takeIf {!isWalk}?.coerceAtLeast(0) ?: 0)*1000L else endTime
fun TripSegment.currentDelaySeconds(departure:Boolean=false):Int=maxOf(0,
    if(departure)delay.departureSeconds?:0 else delay.arrivalSeconds?:delay.departureSeconds?:0,
    (((if(departure)effectiveDeparture() else effectiveArrival())-(if(departure)plannedStartTime else plannedEndTime))/1000).toInt())
/** A walk after a ride starts at that ride's expected arrival. An old walk start must not hold
 * back an earlier arrival after a delay was recovered (10:05 + 10 min = 10:15, not 10:20). */
fun Trip.effectiveArrival():Long {
    var end=0L;var afterRide=false
    for(leg in segments) {
        if(leg.isWalk) end=(if(afterRide)end else maxOf(leg.startTime,end))+(leg.endTime-leg.startTime).coerceAtLeast(0)
        else {end=leg.effectiveArrival();afterRide=true}
    }
    return end
}
fun Trip.firstBoardingTime():Long=segments.firstOrNull {!it.isWalk}?.effectiveDeparture()?:startTime
fun Trip.liveDelaySeconds():Int=segments.filterNot {it.isWalk}.maxOfOrNull {maxOf(it.currentDelaySeconds(),it.currentDelaySeconds(true))}?:0
fun journeyHasDelay(session:PinnedJourneySession)=session.trip.liveDelaySeconds()>0||session.trip.cancelled||unreachablePinnedTransfer(session.trip,session.routing.ignoreWalking,session.routing.walkingSpeed)!=null
/** Stable identity for comparing an offered chain, independent of refresh IDs/timestamps. */
fun alternativeJourneyKey(trip:Trip):String {
    val chain=trip.segments.filterNot {it.isWalk}.ifEmpty {trip.segments}
    return chain.joinToString("|"){"${it.lineType}:${it.lineName}:${it.plannedStartTime}:${it.origin.id?:it.origin.name}:${it.destination.id?:it.destination.name}"}+
        "|via:"+trip.waypoints.joinToString(";"){waypointIdentity(it)}+"|dwell:"+trip.waypointDwellMinutes.toSortedMap().toString()
}
fun journeyIsMoving(session:PinnedJourneySession,presence:DevicePresence?,now:Long):Boolean {
    if(session.usesServerProgress||session.gpsCursor?.departed!=true)return false
    val fix=currentJourneyGps(presence,now)?:return false
    val speed=fix.speedMetersPerSecond?:session.movement.speedMetersPerSecond?.takeIf {now-session.movement.observedAt in 0..15_000}
    return speed!=null&&speed>=.55f
}
