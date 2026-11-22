// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

enum class PowerLocationVerdict { NEAR_ROUTE, POSSIBLE_DEVIATION, UNKNOWN, UNAVAILABLE }
data class PowerLocationCheck(val checkedAt:Long,val verdict:PowerLocationVerdict,val distanceMeters:Double?=null)

/** A sparse check of a forecast, never evidence of boarding, alighting or passing a stop.
 * Stop-to-stop chords longer than 2.5 km are too incomplete for an off-route assertion. */
fun powerLocationCheck(session:PinnedJourneySession,now:Long,presence:DevicePresence?):PowerLocationCheck {
    val fix=currentJourneyGps(presence,now)
        ?:return PowerLocationCheck(now,PowerLocationVerdict.UNAVAILABLE)
    val index=session.trip.segments.indexOfFirst {it.effectiveArrival()>=now}
        .let {if(it<0)session.trip.segments.lastIndex else it}
    val local=focusStops(session.trip).filter {it.legId=="$index:${session.trip.segments.getOrNull(index)?.id}"}
    val projection=projectOntoStops(local,fix.location)
        ?:return PowerLocationCheck(fix.capturedAt,PowerLocationVerdict.UNKNOWN)
    val incomplete=local.zipWithNext().any {(a,b)->(distanceMeters(a.location,b.location)?:Double.MAX_VALUE)>2_500}
    val distance=(projection.cross-fix.accuracyMeters).coerceAtLeast(0.0)
    return PowerLocationCheck(fix.capturedAt,when {
        distance<=200->PowerLocationVerdict.NEAR_ROUTE
        incomplete->PowerLocationVerdict.UNKNOWN
        distance>450->PowerLocationVerdict.POSSIBLE_DEVIATION
        else->PowerLocationVerdict.UNKNOWN
    },distance)
}

/** Modest improvements are worth offering, but refresh IDs and equal duplicates are not. */
fun isBetterAlternative(current:Trip,candidate:Trip):Boolean=
    !samePinnedJourney(current,candidate) && (candidate.effectiveArrival()<current.effectiveArrival()-30_000 ||
        (candidate.effectiveArrival()<=current.effectiveArrival() && candidate.transfers<current.transfers))

fun alternativeSearchIntervalMillis(powerSaving:Boolean)=if(powerSaving)300_000L else 120_000L
