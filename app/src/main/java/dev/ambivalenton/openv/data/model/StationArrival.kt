// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/** Deliberate UX tolerance: within 200 m no walking prompt is derived from imprecise GPS or
 * platform coordinates. It is not evidence of the right platform or of boarding. */
const val STATION_ENTER_METERS=200.0
const val STATION_EXIT_METERS=320.0
/** Starting value for changing platforms inside the station area (stairs, underpass). */
const val STATION_PLATFORM_ALLOWANCE_MILLIS=60_000L
@Serializable data class StationArrival(val rideKey:String,val station:Location,val arrivedAt:Long,
    val outsideSince:Long?=null,val lastOutsideFix:Long=0,val outsideFixes:Int=0)
fun stationRideKey(leg:TripSegment)="${leg.lineType}:${leg.lineName}:${leg.plannedStartTime}"
fun stationArrivalRide(session:PinnedJourneySession,now:Long):IndexedValue<TripSegment>? {
    val anchor=session.stationArrival ?: return null
    return session.trip.segments.withIndex().firstOrNull {stationRideKey(it.value)==anchor.rideKey &&
        !it.value.delay.cancelled && !it.value.isWalk && stationRideKey(it.value) !in session.completedRideKeys && now<=it.value.endTime+15*60_000L}
}

/** Enter at 200 m; leave only after two distinct credible fixes outside a larger fence for >=8 s.
 * GPS loss/one outlier never toggles a reached platform back to the incoming walking leg. */
fun rememberStationArrival(session:PinnedJourneySession,fix:DevicePresence?,now:Long):PinnedJourneySession {
    val measured=fix?.takeIf {now-it.capturedAt in 0..90_000 && it.location.hasCoordinates &&
        it.accuracyMeters.isFinite() && it.accuracyMeters in 0f..150f}
    val held=stationArrivalRide(session,now)
    if(held!=null) {
        val anchor=session.stationArrival!!
        if(measured==null) return session
        val distance=distanceMeters(measured.location,anchor.station) ?: return session
        val fence=STATION_EXIT_METERS+measured.accuracyMeters.coerceAtMost(100f)
        if(distance<=fence) return if(anchor.outsideSince==null) session else session.copy(stationArrival=anchor.copy(outsideSince=null,lastOutsideFix=0,outsideFixes=0))
        if(measured.capturedAt<=anchor.lastOutsideFix || measured.capturedAt<anchor.arrivedAt) return session
        val since=anchor.outsideSince ?: measured.capturedAt
        if(anchor.outsideFixes>=1 && measured.capturedAt-since>=8_000) return session.copy(stationArrival=null)
        return session.copy(stationArrival=anchor.copy(outsideSince=since,lastOutsideFix=measured.capturedAt,outsideFixes=anchor.outsideFixes+1))
    }
    val cleared=if(session.stationArrival!=null) session.copy(stationArrival=null) else session
    if(measured==null || (measured.speedMetersPerSecond?.takeIf {it.isFinite()} ?: 0f)>3.5f) return cleared
    val candidates=session.trip.segments.withIndex().filter {(_,leg)->
        leg.lineType in setOf(LineType.ICE,LineType.REGIONAL,LineType.SUBURBAN,LineType.SUBWAY) &&
            !leg.delay.cancelled && stationRideKey(leg) !in session.completedRideKeys && now<=leg.endTime && leg.startTime-now in -20*60_000L..90*60_000L
    }
    val next=candidates.firstOrNull {candidate->
        val before=session.trip.segments.take(candidate.index).lastOrNull {!it.isWalk}
        (before==null || now>=before.endTime-60_000) &&
            (distanceMeters(measured.location,candidate.value.origin) ?: Double.MAX_VALUE)<=STATION_ENTER_METERS
    } ?: return cleared
    return cleared.copy(stationArrival=StationArrival(stationRideKey(next.value),next.value.origin,measured.capturedAt))
}
fun stationActiveLeg(session:PinnedJourneySession,now:Long,presence:DevicePresence?):String? =
    stationArrivalRide(session,now)?.let {"${it.index}:${it.value.id}"} ?:
        activeOnFootLeg(session,now)?.let {"${it.index}:${it.value.id}"} ?: stableActiveJourneyLeg(session) ?:
        activeJourneyLeg(session.trip,now,presence)?.takeUnless {id->session.trip.segments.withIndex().any {
            "${it.index}:${it.value.id}"==id && !it.value.isWalk && stationRideKey(it.value) in session.completedRideKeys}}
fun VisualJourneyProgress.withStationArrival(session:PinnedJourneySession,now:Long):VisualJourneyProgress {
    val held=stationArrivalRide(session,now) ?: return this
    val id="${held.index}:${held.value.id}"
    val start=focusStops(session.trip).indexOfFirst {it.legId==id}.coerceAtLeast(0)
    return copy(nextIndex=minOf(nextIndex,start),boardingLegId=id)
}
