// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable
import kotlin.math.ceil

@Serializable data class GpsRouteCursor(val legKey:String,val legIndex:Int,val alongMeters:Double,
    val departed:Boolean,val updatedAt:Long,val passedStopKeys:Set<String> = emptySet())
@Serializable data class AutomaticReplacement(val previousLine:String,val replacementLine:String,val changedAt:Long)
fun currentJourneyGps(presence:DevicePresence?,now:Long)=crediblePresence(presence,now,30_000)
private fun legStops(trip:Trip,index:Int)=focusStops(trip).filter {it.legId=="$index:${trip.segments[index].id}"}
fun gpsCursorLeg(session:PinnedJourneySession):IndexedValue<TripSegment>? = session.gpsCursor?.let {c->
    session.trip.segments.withIndex().firstOrNull {journeyLegKey(it.index,it.value)==c.legKey}}

/** Station hysteresis uses only consecutive device fixes; lateness cannot release this latch. */
private fun gpsWaitingSession(session:PinnedJourneySession,fix:DevicePresence,movement:JourneyMovementState,previous:DevicePresence?):PinnedJourneySession? {
    if(movement.mode==JourneyMovementMode.ON_FOOT &&
        (gpsCursorLeg(session)?.value?.isWalk==true || session.gpsCursor?.let {it.legIndex>0 && !it.departed}==true))return null
    if((session.gpsCursor?.departed==true && gpsCursorLeg(session)?.value?.isWalk==false)) return null
    val held=session.stationArrival?.let {arrival->session.trip.segments.withIndex().firstOrNull {
        !it.value.isWalk && !it.value.delay.cancelled && stationRideKey(it.value)==arrival.rideKey && arrival.rideKey !in session.completedRideKeys}}
    val firstUnfinished=session.trip.segments.withIndex().firstOrNull {!it.value.isWalk && !it.value.delay.cancelled && stationRideKey(it.value) !in session.completedRideKeys}?.index
    val ride=held ?: session.trip.segments.withIndex().firstOrNull {(index,leg)->
        !leg.isWalk && !leg.delay.cancelled && stationRideKey(leg) !in session.completedRideKeys &&
            (session.gpsCursor==null || index in session.gpsCursor.legIndex..session.gpsCursor.legIndex+1) &&
            // Do not anchor to the next vehicle merely because the current one approaches its station.
            (index==firstUnfinished || ((fix.speedMetersPerSecond ?: 0f)<2.8f && movement.mode!=JourneyMovementMode.VEHICLE &&
                (distanceMeters(fix.location,leg.origin) ?: Double.MAX_VALUE)<=maxOf(50.0,fix.accuracyMeters*1.5))) &&
            (distanceMeters(fix.location,leg.origin) ?: Double.MAX_VALUE)<=STATION_ENTER_METERS
    } ?: return null
    var arrival=session.stationArrival?.takeIf {it.rideKey==stationRideKey(ride.value)} ?: StationArrival(stationRideKey(ride.value),ride.value.origin,fix.capturedAt)
    val originDistance=distanceMeters(fix.location,ride.value.origin) ?: 0.0
    val outside=originDistance>STATION_EXIT_METERS+fix.accuracyMeters.coerceAtMost(100f)
    val displacement=previous?.let {distanceMeters(it.location,fix.location)} ?: 0.0
    val dt=previous?.let {(fix.capturedAt-it.capturedAt)/1000.0} ?: 0.0
    val projection=projectOntoJourneyLeg(ride.value,legStops(session.trip,ride.index),fix.location,0.0,
        maxOf(500.0,originDistance*1.8))
    if(session.gpsCursor==null && session.stationArrival==null && (fix.speedMetersPerSecond ?: 0f)>=5f &&
        ride.value.effectiveDeparture()-fix.capturedAt<=90_000 && originDistance>maxOf(120.0,fix.accuracyMeters*2.5) &&
        projection!=null && projection.along>100 && projection.cross<=maxOf(65.0,fix.accuracyMeters*1.5))return null
    val growing=previous!=null && originDistance>(distanceMeters(previous.location,ride.value.origin) ?: originDistance)+maxOf(20.0,fix.accuracyMeters.toDouble())
    val moving=dt in 2.0..60.0 && displacement>maxOf(35.0,(fix.accuracyMeters+previous!!.accuracyMeters)*.8) &&
        displacement/dt>=1.8 && movement.mode!=JourneyMovementMode.ON_FOOT &&
        ((fix.speedMetersPerSecond ?: 0f)>=2.8f || movement.mode==JourneyMovementMode.VEHICLE)
    // A speed spike at the platform is never boarding evidence. Two displaced fixes on the
    // correct outgoing route can confirm a departure in seconds near the expected time.
    if(moving && growing && projection!=null && projection.cross<=maxOf(65.0,fix.accuracyMeters*1.5) &&
        projection.along>maxOf(45.0,fix.accuracyMeters*1.5) && ride.value.effectiveDeparture()-fix.capturedAt<=90_000)return null
    if(outside) {
        val since=arrival.outsideSince ?: fix.capturedAt
        if(fix.capturedAt>arrival.lastOutsideFix && arrival.outsideFixes>=1 && fix.capturedAt-since>=8_000) return null
        if(fix.capturedAt>arrival.lastOutsideFix) arrival=arrival.copy(outsideSince=since,lastOutsideFix=fix.capturedAt,outsideFixes=arrival.outsideFixes+1)
    } else arrival=arrival.copy(outsideSince=null,lastOutsideFix=0,outsideFixes=0)
    val passed=session.gpsCursor?.passedStopKeys.orEmpty()+focusStops(session.trip).filter {it.legId.substringBefore(':').toInt()<ride.index}.map {it.key}
    return session.copy(lastTrackedPresence=fix,stationArrival=arrival,gpsCursor=GpsRouteCursor(journeyLegKey(ride.index,ride.value),ride.index,0.0,false,fix.capturedAt,passed),
        activeLegAnchor=null,movement=movement,completedRideKeys=session.completedRideKeys+session.trip.segments.take(ride.index).filterNot {it.isWalk}.map(::stationRideKey))
}

/** GPS owns all physical progress. Timetable timestamps never select a leg or pass a stop here.
 * A fresh fix outside usable route geometry is explicit uncertainty, never timetable substitution.
 * A monotone passed-stop ledger withstands jitter; the current position itself is not extrapolated. */
fun advanceGpsJourney(session:PinnedJourneySession,fix:DevicePresence?,observations:List<DevicePresence>,now:Long,activity:DeviceActivityEvidence?=null):PinnedJourneySession {
    if(session.usesServerProgress)return session.withoutGpsEvidence()
    val measured=currentJourneyGps(fix,now) ?: return session
    // Cached duplicate / out-of-order fixes cannot progress the route twice.
    if(session.gpsCursor?.updatedAt?.let {measured.capturedAt<it}==true)return session
    val previous=observations.filter {it.capturedAt<measured.capturedAt && measured.capturedAt-it.capturedAt in 2_000..60_000}
        .maxByOrNull {it.capturedAt}?.takeIf {crediblePresence(it,now,90_000)!=null}
    if(previous!=null && (distanceMeters(previous.location,measured.location) ?: 0.0)/((measured.capturedAt-previous.capturedAt)/1000.0)>110) return session
    val speed=measured.speedMetersPerSecond?.takeIf {it.isFinite() && it in 0f..110f}
        ?: previous?.let {((distanceMeters(it.location,measured.location) ?: 0.0)/((measured.capturedAt-it.capturedAt)/1000.0)).toFloat()}
    val gpsMovement=inferJourneyMovement(observations,activity,now,session.movement.takeUnless {it.stepsConfirmed} ?: JourneyMovementState())
    gpsWaitingSession(session,measured.copy(speedMetersPerSecond=speed),gpsMovement,previous)?.let {return it}
    data class Match(val index:Int,val projection:RouteProjection,val stops:List<JourneyFocusStop>)
    val old=gpsCursorLeg(session)
    val cursor=session.gpsCursor.takeIf {old!=null}
    val matches=session.trip.segments.indices.mapNotNull {index->
        val leg=session.trip.segments[index]
        if(leg.delay.cancelled || (!leg.isWalk && stationRideKey(leg) in session.completedRideKeys)) return@mapNotNull null
        if(old!=null && (index<old.index || index>old.index+1)) return@mapNotNull null
        val stops=legStops(session.trip,index)
        val sameCursor=cursor?.takeIf {index==old?.index}
        val dt=sameCursor?.let {((measured.capturedAt-it.updatedAt)/1000.0).coerceAtLeast(0.0)} ?: 0.0
        val ceiling=when(leg.lineType) {LineType.WALK->4.0;LineType.BUS,LineType.TRAM->40.0;LineType.SUBWAY->55.0;LineType.ICE->110.0;else->95.0}
        val maxAdvance=if(sameCursor==null)Double.POSITIVE_INFINITY else
            maxOf(100.0,dt*ceiling+measured.accuracyMeters*2)
        val projection=projectOntoJourneyLeg(leg,stops,measured.location,sameCursor?.alongMeters,maxAdvance) ?: return@mapNotNull null
        // Without a provider shape, station chords cut across real bends. Their corridor must
        // reflect that uncertainty; this never claims a track, platform or exact train identity.
        if(projection.cross>rideCorridorMeters(leg,stops,measured.accuracyMeters)) return@mapNotNull null
        Match(index,projection,stops)
    }
    val current=matches.firstOrNull {it.index==old?.index}
    val following=matches.firstOrNull {it.index==(old?.index ?: -2)+1}
    val oldTotal=old?.let {journeyStopDistances(it.value,legStops(session.trip,it.index)).lastOrNull()}
    val boundary=old!=null && cursor?.departed==true && oldTotal!=null &&
        oldTotal-cursor.alongMeters<=maxOf(100.0,measured.accuracyMeters*2.0)
    val onFootNearAlighting=old!=null && cursor?.departed==true && !old.value.isWalk && following!=null && session.trip.segments[following.index].isWalk &&
        gpsMovement.mode==JourneyMovementMode.ON_FOOT &&
        (distanceMeters(measured.location,old.value.destination)?:Double.MAX_VALUE)<=320+measured.accuracyMeters &&
        following.projection.along>maxOf(12.0,measured.accuracyMeters*.7)
    val reachedAlighting=old!=null && !old.value.isWalk && current!=null && following!=null && session.trip.segments[following.index].isWalk &&
        current.projection.total-current.projection.along<=maxOf(55.0,measured.accuracyMeters*1.5) &&
        gpsMovement.mode==JourneyMovementMode.ON_FOOT
    // Providers sometimes omit the platform walk entirely. Walking at the alighting stop can
    // finish the old ride; the next vehicle remains explicitly unboarded.
    val implicitTransferWalk=old!=null && cursor?.departed==true && !old.value.isWalk && following!=null &&
        !session.trip.segments[following.index].isWalk && gpsMovement.mode==JourneyMovementMode.ON_FOOT &&
        (distanceMeters(measured.location,old.value.destination)?:Double.MAX_VALUE)<=320+measured.accuracyMeters &&
        (distanceMeters(measured.location,session.trip.segments[following.index].origin)?:Double.MAX_VALUE)<=320+measured.accuracyMeters
    var chosen=when {
        implicitTransferWalk->following?.copy(projection=following.projection.copy(along=0.0))
        onFootNearAlighting || reachedAlighting->following
        current!=null && following!=null && boundary && following.projection.along>maxOf(35.0,measured.accuracyMeters*1.5) &&
            (following.projection.cross+20<current.projection.cross ||
                (distanceMeters(measured.location,old!!.value.destination) ?: 0.0)>maxOf(60.0,measured.accuracyMeters*2.0))->following
        current!=null->current
        following!=null && boundary && session.trip.segments[following.index].isWalk &&
            (speed ?: 0f) in .5f..2.6f && following.projection.cross<=maxOf(35.0,measured.accuracyMeters*1.5) &&
            following.projection.along>maxOf(35.0,measured.accuracyMeters*1.5) &&
            (distanceMeters(measured.location,old!!.value.destination) ?: Double.MAX_VALUE)<=320+measured.accuracyMeters->following
        old!=null->null
        else->matches.minWithOrNull(compareBy<Match> {it.projection.cross.toInt()/20}
            .thenBy {if((speed ?: 0f)>=2.8f) session.trip.segments[it.index].isWalk else !session.trip.segments[it.index].isWalk}.thenBy {it.index})
    } ?: return session
    // Reaching a platform is GPS evidence for waiting, even when the train is late.
    val nextRide=session.trip.segments.getOrNull(chosen.index+1)
    if(session.trip.segments[chosen.index].isWalk && nextRide!=null && !nextRide.isWalk && !nextRide.delay.cancelled &&
        (speed ?: 0f)<2.8f && chosen.projection.total-chosen.projection.along<=maxOf(30.0,measured.accuracyMeters.toDouble()) &&
        gpsMovement.mode!=JourneyMovementMode.ON_FOOT && (distanceMeters(measured.location,nextRide.origin) ?: Double.MAX_VALUE)<=STATION_ENTER_METERS) {
        val stops=legStops(session.trip,chosen.index+1)
        projectOntoJourneyLeg(nextRide,stops,measured.location)?.let {p->chosen=Match(chosen.index+1,p.copy(along=0.0),stops)}
    }
    val leg=session.trip.segments[chosen.index];val key=journeyLegKey(chosen.index,leg)
    val same=cursor?.legKey==key
    val movement=gpsMovement
    val originDistance=distanceMeters(measured.location,leg.origin) ?: 0.0
    val departed=leg.isWalk || (same && cursor!!.departed) || (!implicitTransferWalk &&
        (originDistance>maxOf(35.0,measured.accuracyMeters*1.5) && (movement.mode==JourneyMovementMode.VEHICLE || (speed ?: 0f)>=4.2f ||
            (movement.mode!=JourneyMovementMode.ON_FOOT && (speed ?: 0f)>=1.8f && originDistance>STATION_EXIT_METERS+measured.accuracyMeters))))
    val along=if(!departed)0.0 else if(same)maxOf(cursor!!.alongMeters,chosen.projection.along) else chosen.projection.along
    val passed=(cursor?.passedStopKeys.orEmpty()+focusStops(session.trip).filter {s->
        val index=s.legId.substringBefore(':').toIntOrNull() ?: Int.MAX_VALUE
        index<chosen.index
    }.map {it.key}).toMutableSet()
    val distances=journeyStopDistances(leg,chosen.stops)
    if(departed) chosen.stops.forEachIndexed {i,s->
        if(i<chosen.stops.lastIndex && along>distances[i]+if((speed ?: 0f)>=2.8f)maxOf(12.0,measured.accuracyMeters*.8)else maxOf(35.0,measured.accuracyMeters*1.5)) passed+=s.key
    }
    val completed=session.completedRideKeys+session.trip.segments.take(chosen.index).filterNot {it.isWalk}.map(::stationRideKey)
    val freshCursor=GpsRouteCursor(key,chosen.index,along,departed,measured.capturedAt,passed.take(256).toSet())
    val waiting=if(!leg.isWalk && !departed && originDistance<=STATION_ENTER_METERS) StationArrival(stationRideKey(leg),leg.origin,session.stationArrival?.arrivedAt ?: measured.capturedAt) else null
    val visited=session.visitedWaypoints+session.query.waypoints.filter {point->focusStops(session.trip).any {it.key in passed && sameLocation(it.location,point)}}.map(::waypointIdentity)
    return session.copy(lastTrackedPresence=measured,gpsCursor=freshCursor,completedRideKeys=completed,stationArrival=waiting,visitedWaypoints=visited,
        activeLegAnchor=if(departed) ActiveJourneyAnchor(key,chosen.index,session.activeLegAnchor?.takeIf {it.key==key}?.confirmedAt ?: now) else null,
        movement=movement).let {observeAssistantCalls(it,measured,session.stationArrival!=null && session.gpsCursor?.departed!=true)}
        // Build 125: the own arrival estimate is decided here once per fix, with a memory (no flipping views).
        .let {holdLocalArrival(it,now)}
}

fun resolvedJourneyProgress(session:PinnedJourneySession,now:Long,presence:DevicePresence?):VisualJourneyProgress {
    val stops=focusStops(session.trip)
    if(session.usesServerProgress) {
        val index=stops.indexOfFirst {it.departure>=now}.let {if(it<0)stops.size else it}
        return VisualJourneyProgress(index,computedAt=now)
    }
    val fix=currentJourneyGps(presence,now)
    val cursor=session.gpsCursor
    val passed=cursor?.passedStopKeys.orEmpty()
    if(fix==null && cursor!=null && gpsCursorLeg(session)!=null && session.hasRecentRouteEvidence(now)) {
        // A signal gap may update arrival forecasts, but cannot pass a physical stop.
        // Retain the last confirmed target instead of oscillating with timetable progress.
        val remembered=session.lastTrackedPresence?:DevicePresence(gpsCursorLeg(session)!!.value.origin,cursor.updatedAt,100f)
        return resolvedJourneyProgress(session,cursor.updatedAt,remembered.copy(capturedAt=cursor.updatedAt)).copy(
            computedAt=now,physicalEtaMinutes=null,positionSource=JourneyPositionSource.GPS_LAST_KNOWN)
    }
    if(fix==null) {
        val index=stops.indexOfFirst {it.departure>=now}.let {if(it<0) stops.size else it}
        return VisualJourneyProgress(index,computedAt=now,estimated=cursor!=null,passedStopKeys=passed,completedRideKeys=session.completedRideKeys)
    }
    val leg=gpsCursorLeg(session)
    if(cursor==null || leg==null) return VisualJourneyProgress(0,true,now,positionSource=JourneyPositionSource.GPS_UNMATCHED,passedStopKeys=passed)
    val matched=cursor.updatedAt==fix.capturedAt
    val id="${leg.index}:${leg.value.id}"
    val local=legStops(session.trip,leg.index);val lengths=journeyStopDistances(leg.value,local)
    // "Next stop" only once the ride can really have left: an early departure flag while still
    // inside the boarding station area (stop coordinates far from the platform, brisk walking)
    // keeps departure and platform in view until the current departure time minus the early
    // tolerance. Clear progress away from the station (e.g. already at a later stop) is riding.
    val inBoardingStation=(distanceMeters(fix.location,leg.value.origin) ?: Double.MAX_VALUE)<=STATION_ENTER_METERS+fix.accuracyMeters
    val holding=!leg.value.isWalk && (!cursor.departed ||
        inBoardingStation && now<leg.value.effectiveDeparture()-earlyTolerance(leg.value))
    val target=local.firstOrNull {!it.cancelled && it.key !in passed && (it!=local.first() || !cursor.departed || holding)} ?: local.lastOrNull()
    val index=stops.indexOfFirst {it.key==target?.key}.coerceAtLeast(0)
    val speed=fix.speedMetersPerSecond?.takeIf {it.isFinite() && it in .7f..110f}
    val remaining=lengths.getOrNull(local.indexOf(target))?.minus(cursor.alongMeters)?.coerceAtLeast(0.0)
    val eta=if(matched && speed!=null && remaining!=null) ceil(remaining/speed/60).toInt() else null
    val totals=session.trip.segments.indices.map {journeyStopDistances(session.trip.segments[it],legStops(session.trip,it)).lastOrNull() ?: 0.0}
    val total=totals.sum().coerceAtLeast(1.0)
    val fraction=((totals.take(leg.index).sum()+cursor.alongMeters)/total).toFloat().coerceIn(0f,1f)
    return VisualJourneyProgress(index,true,now,if(holding)null else eta,fraction,false,if(holding) id else null,
        if(!holding && cursor.departed) id else null,session.completedRideKeys,if(matched) JourneyPositionSource.GPS else JourneyPositionSource.GPS_UNMATCHED,passed)
}
