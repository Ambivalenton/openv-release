// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/*
 * Reversible alighting (build 65). Automatic alighting decisions (steps near the exit, route
 * matching onto the following walk) are hypotheses: for a short window they can be withdrawn when
 * time-consistent evidence contradicts them, i.e. the passenger moves at vehicle speed along the
 * same ride before its alighting stop (a halt at a signal, walking to the toilet, a standing train).
 * A manual correction is offered sparingly. Steps are never required: wheelchair users and people
 * without detectable steps are handled by route progress and time.
 *
 * Coarse journey states for presentation and decisions:
 *  APPROACHING / WAITING / POSSIBLE_BOARDING / IN_VEHICLE / POSSIBLE_ALIGHTING / TRANSFER / ARRIVED,
 *  each with UNCERTAIN when the evidence is old or missing.
 */
enum class TravelPhase {APPROACHING,WAITING,POSSIBLE_BOARDING,IN_VEHICLE,POSSIBLE_ALIGHTING,TRANSFER,ARRIVED}
data class TravelPhaseEstimate(val phase:TravelPhase,val uncertain:Boolean,val legIndex:Int?)

@Serializable data class ProvisionalAlighting(val rideKey:String,val legIndex:Int,val at:Long,
    val cursorBefore:GpsRouteCursor?=null,val anchorBefore:ActiveJourneyAnchor?=null)
@Serializable data class StayAboard(val rideKey:String,val until:Long)

object AlightingPolicy {
    /** An automatic alighting stays revisable this long (configurable starting value). */
    const val REVERT_WINDOW_MILLIS=120_000L
    /** A manual "still on board" suppresses automatic alighting of that ride this long. */
    const val STAY_ABOARD_MILLIS=10*60_000L
    /** "I got off" is offered only this long after the expected arrival without a decision. */
    const val ASK_AFTER_ARRIVAL_MILLIS=2*60_000L
    /** Moving on the ride's own route at least this far before its end contradicts alighting. */
    const val BEFORE_END_METERS=120.0
}

private fun legStopsOf(trip:Trip,index:Int)=focusStops(trip).filter {it.legId=="$index:${trip.segments[index].id}"}

/** The fix lies in the corridor of ride [index] and clearly before its alighting stop. */
internal fun onRideBeforeItsEnd(trip:Trip,index:Int,fix:DevicePresence):Boolean {
    val leg=trip.segments.getOrNull(index)?.takeUnless {it.isWalk} ?: return false
    val stops=legStopsOf(trip,index)
    val projection=projectOntoJourneyLeg(leg,stops,fix.location) ?: return false
    val total=journeyStopDistances(leg,stops).lastOrNull() ?: return false
    val corridor=maxOf(150.0,fix.accuracyMeters*2.0)
    return projection.cross<=corridor && projection.along<total-AlightingPolicy.BEFORE_END_METERS
}

private fun revert(session:PinnedJourneySession,provisional:ProvisionalAlighting,now:Long,movement:JourneyMovementState?):PinnedJourneySession =
    session.copy(completedRideKeys=session.completedRideKeys-provisional.rideKey,
        gpsCursor=provisional.cursorBefore?.copy(departed=true) ?: session.gpsCursor,
        activeLegAnchor=provisional.anchorBefore,provisionalAlighting=null,
        movement=movement ?: session.movement)

/**
 * Applied after every automatic progress step: records new automatic alightings as provisional,
 * withdraws them on contradicting vehicle motion within the window, and honours a manual
 * "still on board". Idempotent; events of another session never reach it (session-local state).
 */
fun reviseAlighting(before:PinnedJourneySession,after:PinnedJourneySession,fix:DevicePresence?,
    observations:List<DevicePresence>,activity:DeviceActivityEvidence?,now:Long):PinnedJourneySession {
    if(before.id!=after.id || before.routeRevision!=after.routeRevision)return after
    var next=after
    val stay=next.stayAboard?.takeIf {now<it.until}
    if(next.stayAboard!=null && stay==null)next=next.copy(stayAboard=null)
    val gained=next.completedRideKeys-before.completedRideKeys
    if(gained.isNotEmpty()) {
        val rides=next.trip.segments.withIndex().filter {!it.value.isWalk && stationRideKey(it.value) in gained}
        val latest=rides.maxByOrNull {it.index}
        if(latest!=null) {
            val provisional=ProvisionalAlighting(stationRideKey(latest.value),latest.index,now,before.gpsCursor,before.activeLegAnchor)
            next=if(stay!=null && stay.rideKey==provisional.rideKey)revert(next,provisional,now,null)
                else next.copy(provisionalAlighting=provisional)
        }
    }
    val provisional=next.provisionalAlighting ?: return next
    if(now-provisional.at !in 0..AlightingPolicy.REVERT_WINDOW_MILLIS)return next.copy(provisionalAlighting=null)
    val gps=currentJourneyGps(fix,now) ?: return next
    val movement=inferJourneyMovement(observations,activity,now)
    val ride=next.trip.segments.getOrNull(provisional.legIndex)
    if(movement.mode==JourneyMovementMode.VEHICLE && ride!=null && stationRideKey(ride)==provisional.rideKey &&
        onRideBeforeItsEnd(next.trip,provisional.legIndex,gps))
        return revert(next,provisional,now,movement)
    return next
}

/** Manual "I am still on board": withdraws a provisional alighting and holds this ride. */
fun stayAboardManually(session:PinnedJourneySession,now:Long):PinnedJourneySession {
    session.provisionalAlighting?.let {p->
        return revert(session,p,now,null).copy(stayAboard=StayAboard(p.rideKey,now+AlightingPolicy.STAY_ABOARD_MILLIS))
    }
    val ride=assistantCurrentRide(session,now) ?: gpsCursorLeg(session)?.takeUnless {it.value.isWalk} ?: return session
    return session.copy(stayAboard=StayAboard(stationRideKey(ride.value),now+AlightingPolicy.STAY_ABOARD_MILLIS))
}

/** Manual "I got off": completes the current ride now; the following leg becomes current. */
fun alightManually(session:PinnedJourneySession,now:Long):PinnedJourneySession {
    val ride=gpsCursorLeg(session)?.takeIf {!it.value.isWalk && stationRideKey(it.value) !in session.completedRideKeys}
        ?: session.trip.segments.withIndex().firstOrNull {!it.value.isWalk && !it.value.delay.cancelled &&
            stationRideKey(it.value) !in session.completedRideKeys && it.value.effectiveDeparture()<=now}
        ?: return session
    val following=session.trip.segments.withIndex().firstOrNull {it.index>ride.index}
    val passed=session.gpsCursor?.passedStopKeys.orEmpty()+focusStops(session.trip).filter {it.legId.substringBefore(':').toInt()<=ride.index}.map {it.key}
    return session.copy(completedRideKeys=session.completedRideKeys+session.trip.segments.take(ride.index+1).filterNot {it.isWalk}.map(::stationRideKey),
        stationArrival=null,provisionalAlighting=null,stayAboard=null,
        gpsCursor=following?.let {GpsRouteCursor(journeyLegKey(it.index,it.value),it.index,0.0,it.value.isWalk,now,passed)},
        activeLegAnchor=following?.takeIf {it.value.isWalk}?.let {ActiveJourneyAnchor(journeyLegKey(it.index,it.value),it.index,now)})
}

/** Sparse correction offers: only for a fresh provisional alighting or a clearly overdue ride. */
data class AlightingCorrection(val offerStillAboard:Boolean,val offerAlighted:Boolean)
fun alightingCorrection(session:PinnedJourneySession,now:Long):AlightingCorrection {
    val provisional=session.provisionalAlighting?.takeIf {now-it.at in 0..AlightingPolicy.REVERT_WINDOW_MILLIS}
    if(provisional!=null)return AlightingCorrection(offerStillAboard=true,offerAlighted=false)
    val ride=assistantCurrentRide(session,now) ?: gpsCursorLeg(session)?.takeIf {!it.value.isWalk && session.gpsCursor?.departed==true}
    val overdue=ride!=null && stationRideKey(ride.value) !in session.completedRideKeys && session.stayAboard?.takeIf {now<it.until}==null &&
        now-projectSessionTimes(session,now).leg(ride.index)?.arrival?.time.let {it ?: Long.MAX_VALUE}>=AlightingPolicy.ASK_AFTER_ARRIVAL_MILLIS
    return AlightingCorrection(offerStillAboard=false,offerAlighted=overdue)
}

/** Coarse, explainable phase for presentation; uncertainty is explicit, never hidden. */
fun travelPhase(session:PinnedJourneySession,now:Long):TravelPhaseEstimate {
    val fresh=session.hasRecentRouteEvidence(now)
    val uncertain=!fresh && !session.usesServerProgress
    if(session.trip.segments.isEmpty())return TravelPhaseEstimate(TravelPhase.ARRIVED,uncertain,null)
    session.provisionalAlighting?.takeIf {now-it.at in 0..AlightingPolicy.REVERT_WINDOW_MILLIS}
        ?.let {return TravelPhaseEstimate(TravelPhase.POSSIBLE_ALIGHTING,true,it.legIndex)}
    stationArrivalRide(session,now)?.let {return TravelPhaseEstimate(TravelPhase.WAITING,uncertain,it.index)}
    val cursor=gpsCursorLeg(session)
    if(cursor!=null && fresh) {
        val leg=cursor.value
        return when {
            leg.isWalk && session.completedRideKeys.isNotEmpty()->TravelPhaseEstimate(TravelPhase.TRANSFER,false,cursor.index)
            leg.isWalk->TravelPhaseEstimate(TravelPhase.APPROACHING,false,cursor.index)
            session.gpsCursor?.departed==true->TravelPhaseEstimate(TravelPhase.IN_VEHICLE,false,cursor.index)
            else->TravelPhaseEstimate(TravelPhase.POSSIBLE_BOARDING,true,cursor.index)
        }
    }
    val end=projectSessionTimes(session,now).finalArrival.time
    if(now>=end && session.trip.segments.filterNot {it.isWalk}.all {stationRideKey(it) in session.completedRideKeys})
        return TravelPhaseEstimate(TravelPhase.ARRIVED,uncertain,session.trip.segments.lastIndex)
    val ride=assistantCurrentRide(session,now)
    if(ride!=null)return TravelPhaseEstimate(TravelPhase.IN_VEHICLE,true,ride.index)
    return TravelPhaseEstimate(TravelPhase.APPROACHING,true,null)
}
