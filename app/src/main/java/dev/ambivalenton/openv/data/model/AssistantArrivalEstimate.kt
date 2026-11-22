// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

@Serializable data class ObservedJourneyCall(val rideKey:String,val place:Location,val at:Long,val departure:Boolean=false)

/** Sparse, private stop evidence, not a raw GPS track. Provider updates recalibrate the residual
 * against the SAME event, so a newly reported delay is never added twice. */
fun observeAssistantCalls(session:PinnedJourneySession,fix:DevicePresence,departureObserved:Boolean=false):PinnedJourneySession {
    val cursor=session.gpsCursor ?: return session
    val entry=gpsCursorLeg(session) ?: return session
    if(entry.value.isWalk || !cursor.departed || fix.accuracyMeters>50 || cursor.updatedAt!=fix.capturedAt)return session
    val leg=entry.value;val key=stationRideKey(leg)
    val current=session.observedCalls.toMutableList()
    fun add(place:Location,at:Long,departure:Boolean=false) {
        if(current.none {it.rideKey==key && it.departure==departure && sameLocation(it.place,place)})
            current+=ObservedJourneyCall(key,place,at,departure)
    }
    val originDistance=distanceMeters(fix.location,leg.origin) ?: Double.MAX_VALUE
    val speed=fix.speedMetersPerSecond
    if(departureObserved && speed!=null && speed>=4.2f && originDistance<=500 && cursor.alongMeters<=500) {
        val seconds=(originDistance/speed).toLong().coerceIn(0,90)
        add(leg.origin,fix.capturedAt-seconds*1000,true)
    }
    for(stop in leg.stops.filterNot {it.cancelled}) {
        if((distanceMeters(fix.location,stop.location) ?: Double.MAX_VALUE)<=maxOf(35.0,fix.accuracyMeters.toDouble()) && (speed ?: 0f)<=12f)
            add(stop.location,fix.capturedAt)
    }
    return if(current==session.observedCalls)session else session.copy(observedCalls=current.takeLast(24))
}

/** Local correction of a ride's arrival relative to its provider value (may be negative only
 * when no provider forecast exists). Derived from the shared projection, never accumulated. */
fun assistantRideResidual(session:PinnedJourneySession,leg:TripSegment,now:Long):Long {
    if(session.usesServerProgress)return 0
    val index=session.trip.segments.indexOf(leg).takeIf {it>=0} ?: return 0
    return projectSessionTimes(session,now).leg(index)?.localOffsetMillis ?: 0
}

/** Expected arrival of one ride from the shared projection (provider, carried or local). */
fun assistantRideArrival(session:PinnedJourneySession,entry:IndexedValue<TripSegment>,now:Long):Long =
    projectSessionTimes(session,now).leg(entry.index)?.arrival?.time ?: entry.value.effectiveArrival()

/** Final arrival of the remaining chain; waiting time absorbs a delay that keeps the connection. */
fun assistantFinalArrival(session:PinnedJourneySession,now:Long):Long =
    projectSessionTimes(session,now).finalArrival.time
