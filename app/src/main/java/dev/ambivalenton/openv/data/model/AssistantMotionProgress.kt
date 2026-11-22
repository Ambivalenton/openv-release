// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** A brief GNSS gap holds the physical target; a longer gap becomes an explicit forecast. */
const val ASSISTANT_GPS_HOLD_MILLIS=90_000L
fun PinnedJourneySession.hasRecentRouteEvidence(now:Long)=gpsCursor?.let {now-it.updatedAt in 0..ASSISTANT_GPS_HOLD_MILLIS}==true

/** Local steps can confirm alighting only on the already-boarded ride, near its exit window.
 * A walk inside a long-distance train cannot finish an unrelated/future leg. No raw data is saved. */
fun advanceAssistantJourney(session:PinnedJourneySession,fix:DevicePresence?,observations:List<DevicePresence>,
    activity:DeviceActivityEvidence?,now:Long):PinnedJourneySession {
    // An earlier or later service than the pinned one is recognised first, against the
    // previous fix, before the cursor consumes this one (see RideTimingDeviation.kt).
    val observed=observeRideDeviation(session,fix,observations,now)
    return reviseAlighting(observed,advanceAssistantProgress(observed,fix,observations,activity,now),fix,observations,activity,now)
}

private fun advanceAssistantProgress(session:PinnedJourneySession,fix:DevicePresence?,observations:List<DevicePresence>,
    activity:DeviceActivityEvidence?,now:Long):PinnedJourneySession {
    val recovered=recoverLateWalkingLeg(session,fix,observations,activity,now)
    var next=advanceGpsJourney(recovered,fix,observations,now,activity)
    if(session.usesServerProgress)return next
    val ride=gpsCursorLeg(session)?.takeIf {!it.value.isWalk && session.gpsCursor?.departed==true} ?: return next
    if(stationRideKey(ride.value) in next.completedRideKeys)return next
    val steps=activity?.takeIf {it.walking(now) && it.steps>=10 && it.spanMillis>=7_000} ?: return next
    val arrival=assistantRideArrival(session,ride,now)
    if(now !in arrival-180_000..arrival+10*60_000)return next
    val gps=currentJourneyGps(fix,now)
    val near=gps?.let {(distanceMeters(it.location,ride.value.destination) ?: Double.MAX_VALUE)<=maxOf(300.0,it.accuracyMeters*2.0)}
    // A fresh fix far from the exit vetoes inertial evidence. With no signal, require a late
    // route match or a confirmed ride near its scheduled arrival, and a longer walking pattern.
    if(near==false || (gps?.speedMetersPerSecond ?: 0f)>3.8f)return next
    if(gps==null && (steps.steps<14 || steps.spanMillis<10_000 ||
        now !in arrival-60_000..arrival+5*60_000))return next
    val following=next.trip.segments.withIndex().firstOrNull {it.index>ride.index &&
        (it.value.isWalk || !it.value.delay.cancelled && stationRideKey(it.value) !in next.completedRideKeys)}
    val passed=next.gpsCursor?.passedStopKeys.orEmpty()+focusStops(next.trip).filter {it.legId.substringBefore(':').toInt()<=ride.index}.map {it.key}
    next=next.copy(completedRideKeys=next.completedRideKeys+stationRideKey(ride.value),stationArrival=null,
        movement=JourneyMovementState(JourneyMovementMode.ON_FOOT,gps?.speedMetersPerSecond,steps.capturedAt,true),
        gpsCursor=following?.let {GpsRouteCursor(journeyLegKey(it.index,it.value),it.index,0.0,it.value.isWalk,
            gps?.capturedAt ?: now-ASSISTANT_GPS_HOLD_MILLIS-1,passed)},
        activeLegAnchor=following?.takeIf {it.value.isWalk}?.let {ActiveJourneyAnchor(journeyLegKey(it.index,it.value),it.index,now)})
    return next
}

/** The wave follows a running journey, independently of speed and GPS reception. */
fun assistantWaveActive(session:PinnedJourneySession,now:Long,status:PinnedTrackingStatus)=
    status==PinnedTrackingStatus.RUNNING && now>=session.trip.startTime

/** Reacquisition can happen well beyond the station after a tunnel or a suspended receiver.
 * Two fresh, displaced walking fixes on a later walking route can release a stale ride owner.
 * Time merely opens a recovery window; actual measured route geometry selects the new leg. */
private fun recoverLateWalkingLeg(session:PinnedJourneySession,fix:DevicePresence?,observations:List<DevicePresence>,
    activity:DeviceActivityEvidence?,now:Long):PinnedJourneySession {
    val ride=gpsCursorLeg(session)?.takeIf {!it.value.isWalk && session.gpsCursor?.departed==true} ?: return session
    if(now<ride.value.effectiveArrival()+90_000)return session
    val measured=currentJourneyGps(fix,now) ?: return session
    if(measured.accuracyMeters>40f || (measured.speedMetersPerSecond ?: 0f)>3.2f)return session
    val previous=observations.filter {it.capturedAt<measured.capturedAt && measured.capturedAt-it.capturedAt in 8_000..60_000 &&
        crediblePresence(it,now,90_000)!=null && it.accuracyMeters<=40f}.maxByOrNull {it.capturedAt} ?: return session
    val delta=distanceMeters(previous.location,measured.location) ?: return session
    val seconds=(measured.capturedAt-previous.capturedAt)/1000.0
    if(delta<maxOf(12.0,(measured.accuracyMeters+previous.accuracyMeters)*.65) || delta/seconds !in .5..2.8)return session
    val movement=inferJourneyMovement(observations,activity,now,session.movement)
    if(movement.mode!=JourneyMovementMode.ON_FOOT && activity?.walking(now)!=true && (measured.speedMetersPerSecond ?: 0f) !in .5f..2.8f)return session
    val stops=focusStops(session.trip)
    val candidate=session.trip.segments.withIndex().filter {it.index>ride.index && it.value.isWalk &&
        now in it.value.startTime-180_000..it.value.endTime+20*60_000}.firstOrNull {entry->
        val local=stops.filter {it.legId=="${entry.index}:${entry.value.id}"}
        val before=projectOntoJourneyLeg(entry.value,local,previous.location) ?: return@firstOrNull false
        val current=projectOntoJourneyLeg(entry.value,local,measured.location) ?: return@firstOrNull false
        current.cross<=maxOf(35.0,measured.accuracyMeters.toDouble()) && before.cross<=maxOf(35.0,previous.accuracyMeters.toDouble()) &&
            current.along>before.along+maxOf(8.0,measured.accuracyMeters*.5) && current.along>30.0
    } ?: return session
    val local=stops.filter {it.legId=="${candidate.index}:${candidate.value.id}"}
    val projection=projectOntoJourneyLeg(candidate.value,local,measured.location) ?: return session
    val passed=session.gpsCursor!!.passedStopKeys+stops.filter {it.legId.substringBefore(':').toInt()<candidate.index}.map {it.key}
    return session.copy(gpsCursor=GpsRouteCursor(journeyLegKey(candidate.index,candidate.value),candidate.index,projection.along,true,measured.capturedAt,passed),
        completedRideKeys=session.completedRideKeys+session.trip.segments.take(candidate.index).filterNot {it.isWalk}.map(::stationRideKey),
        stationArrival=null,lastTrackedPresence=measured,
        movement=JourneyMovementState(JourneyMovementMode.ON_FOOT,measured.speedMetersPerSecond,measured.capturedAt,activity?.walking(now)==true),
        activeLegAnchor=ActiveJourneyAnchor(journeyLegKey(candidate.index,candidate.value),candidate.index,now))
}
