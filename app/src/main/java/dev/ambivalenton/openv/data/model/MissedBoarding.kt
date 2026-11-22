// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

@Serializable data class MissedBoardingObservation(val rideKey:String,val firstObservedAt:Long,val lastObservedAt:Long,val confirmations:Int)

/** Distance is a GPS lower bound. Absence from a board and a planned time are never evidence.
 * Require two fresh monitored responses, separated by >=20 s, while far beyond the access fence.
 * A rider already in that vehicle, or near the platform, must never be automatically displaced.
 *
 * The passenger stayed at the boarding station. Accurate fixes inside the
 * station fence, no vehicle movement and the departure clearly past (2.5 min with a forecast,
 * 6 min without one, because a missing forecast does not mean punctuality) show that the
 * vehicle left without them. In a tunnel there are no such fixes, so a departed rider is never
 * mistaken for one who stayed behind. */
const val MISSED_AT_STATION_REALTIME_MILLIS=150_000L
const val MISSED_AT_STATION_PLANNED_MILLIS=360_000L

fun missedBoardingRide(session:PinnedJourneySession,now:Long,presence:DevicePresence?):IndexedValue<TripSegment>? {
    val gps=currentJourneyGps(presence,now) ?: return null
    val current=gpsCursorLeg(session)
    fun farAway(leg:TripSegment)=leg.realtimeDeparture && now-leg.startTime in 60_000..6*3_600_000L &&
        (distanceMeters(gps.location,leg.origin) ?: 0.0)-gps.accuracyMeters>450.0
    fun stayedBehind(leg:TripSegment):Boolean {
        val forecast=leg.realtimeDeparture || leg.departureTimeSource()==TimeSource.PROGNOSIS
        val grace=if(forecast) MISSED_AT_STATION_REALTIME_MILLIS else MISSED_AT_STATION_PLANNED_MILLIS
        return gps.capturedAt-leg.effectiveDeparture() in grace..3*3_600_000L && gps.accuracyMeters<=50f &&
            (distanceMeters(gps.location,leg.origin) ?: Double.MAX_VALUE)<=STATION_ENTER_METERS+gps.accuracyMeters &&
            (gps.speedMetersPerSecond ?: 0f)<2.5f && session.movement.mode!=JourneyMovementMode.VEHICLE
    }
    return session.trip.segments.withIndex().firstOrNull {(index,leg)->
        !leg.isWalk && !leg.delay.cancelled && stationRideKey(leg) !in session.completedRideKeys &&
            (current==null || index>=current.index) &&
            !(current?.index==index && session.gpsCursor?.departed==true) &&
            (farAway(leg) || stayedBehind(leg))
    }
}
fun observeMissedBoarding(session:PinnedJourneySession,now:Long,presence:DevicePresence?,freshServer:Boolean):PinnedJourneySession {
    if(!freshServer) return session.copy(missedBoarding=null)
    val ride=missedBoardingRide(session,now,presence) ?: return session.copy(missedBoarding=null)
    val key=stationRideKey(ride.value);val old=session.missedBoarding?.takeIf {it.rideKey==key && now-it.lastObservedAt in 0..180_000}
    val next=when {old==null->MissedBoardingObservation(key,now,now,1)
        now-old.lastObservedAt>=20_000->old.copy(lastObservedAt=now,confirmations=old.confirmations+1)
        else->old}
    return session.copy(missedBoarding=next)
}
fun confirmedMissedBoarding(session:PinnedJourneySession,now:Long,presence:DevicePresence?)=
    missedBoardingRide(session,now,presence)?.takeIf {ride->session.missedBoarding?.let {
        it.rideKey==stationRideKey(ride.value) && it.confirmations>=2 && it.lastObservedAt-it.firstObservedAt>=20_000 && now-it.lastObservedAt in 0..90_000}==true}

/** Proactive advice may be offered before a miss, but never silently adopted on ETA alone. */
fun upcomingRideUnreachableFromGps(session:PinnedJourneySession,now:Long,presence:DevicePresence?):Boolean {
    val gps=currentJourneyGps(presence,now) ?: return false
    val current=gpsCursorLeg(session)
    if(current!=null && !current.value.isWalk && session.gpsCursor?.departed==true) return false
    val ride=session.trip.segments.withIndex().firstOrNull {(index,leg)->!leg.isWalk && !leg.delay.cancelled &&
        (current==null || index>=current.index) && stationRideKey(leg) !in session.completedRideKeys}?.value ?: return false
    val distance=(distanceMeters(gps.location,ride.origin) ?: return false)-gps.accuracyMeters
    if(distance<=STATION_EXIT_METERS) return false
    return now+(if(session.routing.ignoreWalking)0L else ((distance/session.routing.walkingSpeed.metersPerSecond)*1000).toLong())>ride.startTime
}
