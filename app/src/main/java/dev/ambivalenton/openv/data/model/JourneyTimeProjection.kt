// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlin.math.abs

/**
 * One shared, current time projection of a journey (build 65). Card, header, expanded timeline,
 * notification, bubble and the assistant's decisions read the same values, so a delay that grows
 * or shrinks reaches all views together.
 *
 * Rules:
 * - Provider timestamps already contain their delay; it is never added a second time.
 * - A walk after a ride starts when that ride is expected to arrive, not at an old walk start.
 * - A later ride keeps its own forecast. A delay of the current ride is absorbed by waiting time;
 *   it is not pushed through the remaining chain. Only the transfer reserve shrinks.
 * - Local route evidence of the passenger (GPS cursor on the ride) may only make an arrival later
 *   than a provider forecast. Without any arrival forecast it is the best available estimate in
 *   both directions, bounded by the timetable.
 */
object JourneyTimePolicy {
    /** Local evidence must disagree by more than this before it overrides a provider forecast. */
    const val LOCAL_OVERRIDE_MILLIS=120_000L
    /** A route position older than this is not used (tunnels, GPS gaps, process restarts). */
    const val LOCAL_EVIDENCE_MAX_AGE_MILLIS=5*60_000L
    /** Received realtime older than this is labelled as an old data state. */
    const val STALE_REALTIME_MILLIS=10*60_000L
    /** A forecast kept although the provider stopped sending it is used for at most this long. */
    const val RETAINED_FORECAST_MILLIS=20*60_000L
    /** Boarding reserve on top of the transfer walk (also used by the reachability rules). */
    const val BOARDING_RESERVE_MILLIS=30_000L
    /** Route matching uses stop chords without shapes: position slack before time conclusions. */
    const val POSITION_SLACK_METERS=150.0
    /** A carried departure delay is only superseded once the vehicle clearly left (metres along). */
    const val DEPARTED_EVIDENCE_METERS=300.0
    /** Stop-passage residuals older than this are not used (they cannot hold a forecast forever). */
    const val CALL_RESIDUAL_MAX_AGE_MILLIS=10*60_000L
    /** Smaller residuals are noise. */
    const val CALL_RESIDUAL_MIN_MILLIS=90_000L
    /** Trains rarely arrive much earlier than planned; road vehicles occasionally do. */
    const val RAIL_EARLY_TOLERANCE_MILLIS=60_000L
    const val ROAD_EARLY_TOLERANCE_MILLIS=120_000L
    /** Expected spread at or above which a time is shown as approximate ("ca."). */
    const val APPROXIMATE_SPREAD_SECONDS=180
    /** Transfers with less reserve than this are tight. */
    const val TIGHT_TRANSFER_MILLIS=3*60_000L
}

/** Normalised source of the departure, also for journeys stored before provenance existed. */
fun TripSegment.departureTimeSource():TimeSource = when {
    isWalk->TimeSource.PLANNED
    departureSource!=TimeSource.UNKNOWN->departureSource
    delay.departureSeconds!=null->TimeSource.PROGNOSIS
    else->TimeSource.PLANNED
}

fun TripSegment.arrivalTimeSource():TimeSource = when {
    isWalk->TimeSource.PLANNED
    arrivalSource!=TimeSource.UNKNOWN->arrivalSource
    delay.arrivalSeconds!=null->TimeSource.PROGNOSIS
    delay.departureSeconds!=null->TimeSource.CARRIED
    else->TimeSource.PLANNED
}

data class ProjectedEvent(val time:Long,val planned:Long,val source:TimeSource,val spreadSeconds:Int) {
    /** A carried, local or uncertain time is shown with "ca.", never as an exact certainty. */
    val approximate:Boolean get()=source==TimeSource.CARRIED || source==TimeSource.LOCAL ||
        spreadSeconds>=JourneyTimePolicy.APPROXIMATE_SPREAD_SECONDS
    val delaySeconds:Int get()=((time-planned)/1000).toInt()
}

data class ProjectedLeg(val index:Int,val departure:ProjectedEvent,val arrival:ProjectedEvent,val providerArrival:Long) {
    /** Local correction relative to the provider value; 0 when no local evidence was used. */
    val localOffsetMillis:Long get()=if(arrival.source==TimeSource.LOCAL)arrival.time-providerArrival else 0
}

enum class TransferStatus {COMFORTABLE,TIGHT,AT_RISK,BROKEN}

/** reserve = connecting departure − own arrival − transfer walk − boarding reserve. */
data class TransferProjection(val fromIndex:Int,val toIndex:Int,val arrival:Long,val transferMillis:Long,
    val departure:Long,val boardingReserveMillis:Long,val reserveMillis:Long,val uncertaintyMillis:Long,val status:TransferStatus)

data class JourneyTimeProjection(val legs:List<ProjectedLeg>,val transfers:List<TransferProjection>,val finalArrival:ProjectedEvent) {
    fun leg(index:Int)=legs.getOrNull(index)
    /** False if a remaining transfer is no longer achievable with the current times. */
    val chainReliable:Boolean get()=transfers.none {it.status==TransferStatus.BROKEN}
}

/** The passenger's matched position on a departed ride, from the GPS route cursor. */
data class RideProgressEvidence(val legIndex:Int,val alongMeters:Double,val observedAt:Long,val physicalFloor:Long?=null,
    val accuracyMeters:Float?=null)

/** Stop passages observed on the ride (sparse GPS evidence), used when no fresh cursor exists. */
data class ObservedCallEvidence(val legIndex:Int,val residualMillis:Long,val observedAt:Long)

private fun spread(source:TimeSource,receivedAt:Long?,now:Long):Int {
    val age=receivedAt?.let {((now-it)/1000).coerceAtLeast(0)} ?: 0
    return when(source) {
        TimeSource.PROGNOSIS->if(age*1000<=JourneyTimePolicy.STALE_REALTIME_MILLIS)60 else (60+age/10).toInt().coerceAtMost(900)
        TimeSource.CARRIED->180
        TimeSource.LOCAL->120
        // A plain timetable value says nothing about the current operation.
        TimeSource.PLANNED,TimeSource.UNKNOWN->120
    }
}

internal fun earlyTolerance(leg:TripSegment)=if(leg.lineType in setOf(LineType.BUS,LineType.TRAM,LineType.OTHER))
    JourneyTimePolicy.ROAD_EARLY_TOLERANCE_MILLIS else JourneyTimePolicy.RAIL_EARLY_TOLERANCE_MILLIS

/** Timetable time at a position along the ride, interpolated between calls with known plans. */
internal fun plannedTimeAlong(leg:TripSegment,stops:List<JourneyFocusStop>,alongMeters:Double):Long? {
    if(stops.size<2)return null
    val distances=journeyStopDistances(leg,stops)
    if(distances.size!=stops.size)return null
    val planned=stops.mapIndexed {i,stop->when(i) {0->leg.plannedStartTime;stops.lastIndex->leg.plannedEndTime;else->stop.plannedTime}}
    val along=alongMeters.coerceIn(0.0,distances.last())
    val k=distances.indexOfLast {it<=along}.coerceIn(0,stops.size-2)
    val before=(k downTo 0).first {planned[it]!=null}
    val after=(k+1..stops.lastIndex).first {planned[it]!=null}
    val t0=planned[before]!!;val t1=planned[after]!!
    val d0=distances[before];val d1=distances[after]
    val fraction=if(d1>d0)((along-d0)/(d1-d0)).coerceIn(0.0,1.0) else 1.0
    return t0+((t1-t0)*fraction).toLong()
}

/** Arrival estimate from the passenger's own progress: observation time + remaining timetable travel. */
internal fun localRideArrival(leg:TripSegment,stops:List<JourneyFocusStop>,evidence:RideProgressEvidence):ProjectedEvent? {
    val atPosition=plannedTimeAlong(leg,stops,evidence.alongMeters) ?: return null
    val remaining=(leg.plannedEndTime-atPosition).coerceAtLeast(0)
    val estimate=maxOf(evidence.observedAt+remaining,leg.plannedEndTime-earlyTolerance(leg))
    return ProjectedEvent(estimate,leg.plannedEndTime,TimeSource.LOCAL,(90+remaining/1000*15/100).toInt())
}

/** Time equivalent of the position uncertainty along this ride at its planned average speed. */
internal fun positionSlackMillis(leg:TripSegment,stops:List<JourneyFocusStop>,accuracyMeters:Float?):Long {
    val length=journeyStopDistances(leg,stops).lastOrNull()?.takeIf {it>0} ?: return JourneyTimePolicy.LOCAL_OVERRIDE_MILLIS
    val duration=(leg.plannedEndTime-leg.plannedStartTime).coerceAtLeast(60_000L)
    val slack=JourneyTimePolicy.POSITION_SLACK_METERS+2.0*(accuracyMeters?.toDouble() ?: 50.0)
    return (slack/length*duration).toLong()
}

/**
 * Local evidence may make a forecast later only; it never shortens a provider prognosis. A carried
 * departure delay may be shortened only once the vehicle clearly left; a pure timetable value in
 * both directions. [threshold] includes the position uncertainty.
 */
private fun combine(provider:ProjectedEvent,local:ProjectedEvent?,threshold:Long,departedClearly:Boolean):ProjectedEvent {
    if(local==null)return provider
    val mayShorten=when(provider.source) {
        TimeSource.PROGNOSIS->false
        TimeSource.CARRIED->departedClearly
        else->true
    }
    return when {
        local.time>provider.time+threshold->local
        mayShorten && provider.time-local.time>threshold->local
        else->provider
    }
}

/** Pure projection of a trip. [evidence] is optional local progress on the current ride. */
fun projectJourneyTimes(trip:Trip,now:Long,evidence:RideProgressEvidence?=null,speed:WalkingSpeed=WalkingSpeed.AVERAGE,
    ignoreWalking:Boolean=false,completedRideKeys:Set<String> = emptySet(),calls:ObservedCallEvidence?=null):JourneyTimeProjection {
    val legs=ArrayList<ProjectedLeg>(trip.segments.size)
    var previous:ProjectedEvent?=null
    var afterRide=false
    val usable=evidence?.takeIf {now-it.observedAt in 0..JourneyTimePolicy.LOCAL_EVIDENCE_MAX_AGE_MILLIS}
    val focus=if(usable!=null)focusStops(trip) else emptyList()
    trip.segments.forEachIndexed {index,leg->
        if(leg.isWalk) {
            val duration=if(ignoreWalking)0L else walkingDurationMillis(leg,speed)
            val origin=previous
            val start=if(afterRide && origin!=null)origin.time else maxOf(leg.startTime,origin?.time ?: Long.MIN_VALUE)
            val startEvent=ProjectedEvent(start,leg.plannedStartTime,if(afterRide)origin!!.source else TimeSource.PLANNED,
                if(afterRide)origin!!.spreadSeconds else 0)
            val endEvent=ProjectedEvent(start+duration,leg.plannedEndTime,startEvent.source,startEvent.spreadSeconds)
            legs+=ProjectedLeg(index,startEvent,endEvent,leg.endTime)
            previous=endEvent
        } else {
            val departureSource=leg.departureTimeSource()
            val departure=ProjectedEvent(leg.effectiveDeparture(),leg.plannedStartTime,departureSource,spread(departureSource,leg.realtimeReceivedAt,now))
            val arrivalSource=leg.arrivalTimeSource()
            val providerArrival=leg.effectiveArrival()
            var arrival=ProjectedEvent(providerArrival,leg.plannedEndTime,arrivalSource,spread(arrivalSource,leg.realtimeReceivedAt,now))
            if(usable!=null && usable.legIndex==index && stationRideKey(leg) !in completedRideKeys) {
                val stops=focus.filter {it.legId=="$index:${leg.id}"}
                val threshold=maxOf(JourneyTimePolicy.LOCAL_OVERRIDE_MILLIS,positionSlackMillis(leg,stops,usable.accuracyMeters))
                arrival=combine(arrival,localRideArrival(leg,stops,usable),threshold,usable.alongMeters>=JourneyTimePolicy.DEPARTED_EVIDENCE_METERS)
                usable.physicalFloor?.let {floor->if(floor>arrival.time)arrival=arrival.copy(time=floor,source=TimeSource.LOCAL)}
            } else if(calls!=null && calls.legIndex==index && stationRideKey(leg) !in completedRideKeys &&
                now-calls.observedAt in 0..JourneyTimePolicy.CALL_RESIDUAL_MAX_AGE_MILLIS) {
                // Fallback without a fresh cursor: the observed stop passage compared with the
                // provider's CURRENT time for the same call, so a delay is never added twice.
                val local=ProjectedEvent(providerArrival+calls.residualMillis,leg.plannedEndTime,TimeSource.LOCAL,150)
                arrival=combine(arrival,local,JourneyTimePolicy.CALL_RESIDUAL_MIN_MILLIS-1,true)
            }
            legs+=ProjectedLeg(index,departure,arrival,providerArrival)
            previous=arrival;afterRide=true
        }
    }
    val transfers=projectTransfers(trip,legs,speed,ignoreWalking,completedRideKeys)
    val last=legs.lastOrNull()?.arrival ?: ProjectedEvent(trip.endTime,trip.endTime,TimeSource.PLANNED,0)
    val final=if(transfers.any {it.status==TransferStatus.BROKEN || it.status==TransferStatus.AT_RISK})
        last.copy(spreadSeconds=maxOf(last.spreadSeconds,JourneyTimePolicy.APPROXIMATE_SPREAD_SECONDS)) else last
    return JourneyTimeProjection(legs,transfers,final)
}

private fun projectTransfers(trip:Trip,legs:List<ProjectedLeg>,speed:WalkingSpeed,ignoreWalking:Boolean,
    completedRideKeys:Set<String>):List<TransferProjection> {
    val rides=trip.segments.withIndex().filterNot {it.value.isWalk}
    return rides.zipWithNext().mapNotNull {(before,after)->
        if(stationRideKey(after.value) in completedRideKeys || after.value.delay.cancelled)return@mapNotNull null
        val walk=if(ignoreWalking)0L else trip.segments.subList(before.index+1,after.index).sumOf {walkingDurationMillis(it,speed)}
        val physical=if(ignoreWalking || sameLocation(before.value.destination,after.value.origin))0L
            else distanceMeters(before.value.destination,after.value.origin)?.let {kotlin.math.ceil(it/speed.metersPerSecond*1000).toLong()} ?: walk
        val transfer=maxOf(walk,physical)
        val reserveForBoarding=if(ignoreWalking)0L else JourneyTimePolicy.BOARDING_RESERVE_MILLIS
        val arrival=legs[before.index].arrival;val departure=legs[after.index].departure
        val reserve=departure.time-arrival.time-transfer-reserveForBoarding
        val uncertainty=(arrival.spreadSeconds+departure.spreadSeconds)*1000L/2
        val status=when {
            reserve< -uncertainty->TransferStatus.BROKEN
            reserve<uncertainty.coerceAtMost(JourneyTimePolicy.TIGHT_TRANSFER_MILLIS) && reserve<60_000->TransferStatus.AT_RISK
            reserve<JourneyTimePolicy.TIGHT_TRANSFER_MILLIS->TransferStatus.TIGHT
            else->TransferStatus.COMFORTABLE
        }
        TransferProjection(before.index,after.index,arrival.time,transfer,departure.time,reserveForBoarding,reserve,uncertainty,status)
    }
}

/** Fresh straight-line lower bound: a vehicle cannot be faster than this. Only ever a floor. */
private fun physicalArrivalFloor(session:PinnedJourneySession,leg:TripSegment,now:Long):Long? {
    val gps=session.lastTrackedPresence?.let {currentJourneyGps(it,now)} ?: return null
    val maxSpeed=when(leg.lineType){LineType.BUS,LineType.TRAM->25.0;LineType.SUBWAY->35.0;else->100.0}
    return distanceMeters(gps.location,leg.destination)?.let {now+(it/maxSpeed*1000).toLong()}
}

/** The projection of a pinned journey, including the passenger's matched route progress. */
fun projectSessionTimes(session:PinnedJourneySession,now:Long):JourneyTimeProjection {
    val evidence=if(session.usesServerProgress)null else session.gpsCursor?.takeIf {it.departed}?.let {cursor->
        gpsCursorLeg(session)?.takeUnless {it.value.isWalk}?.let {leg->
            RideProgressEvidence(leg.index,cursor.alongMeters,cursor.updatedAt,physicalArrivalFloor(session,leg.value,now),
                session.lastTrackedPresence?.accuracyMeters)
        }
    }
    return projectJourneyTimes(session.trip,now,evidence,session.routing.walkingSpeed,session.routing.ignoreWalking,session.completedRideKeys,
        if(session.usesServerProgress)null else observedCallEvidence(session,now))
}

/** Median residual of the last stop passages of one ride against the provider's current times. */
internal fun observedCallEvidence(session:PinnedJourneySession,now:Long):ObservedCallEvidence? {
    val recent=session.observedCalls.filter {now-it.at in 0..JourneyTimePolicy.CALL_RESIDUAL_MAX_AGE_MILLIS}
    val key=recent.lastOrNull()?.rideKey ?: return null
    val entry=session.trip.segments.withIndex().firstOrNull {!it.value.isWalk && stationRideKey(it.value)==key} ?: return null
    val leg=entry.value
    val residuals=recent.filter {it.rideKey==key}.takeLast(3).mapNotNull {call->
        val expected=if(call.departure && sameLocation(call.place,leg.origin))leg.effectiveDeparture()
            else leg.stops.firstOrNull {sameLocation(it.location,call.place)}?.let {it.arrivalTime ?: it.departureTime}
        expected?.let {(call.at-it).coerceIn(-5*60_000L,30*60_000L)}
    }.sorted()
    val residual=residuals.getOrNull(residuals.size/2) ?: return null
    if(abs(residual)<JourneyTimePolicy.CALL_RESIDUAL_MIN_MILLIS)return null
    return ObservedCallEvidence(entry.index,residual,recent.last {it.rideKey==key}.at)
}

/** The oldest receipt of realtime data among the rides that still matter, if any is old. */
fun staleRealtimeSince(session:PinnedJourneySession,now:Long):Long? {
    val relevant=session.trip.segments.filter {!it.isWalk && !it.delay.cancelled && stationRideKey(it) !in session.completedRideKeys &&
        it.effectiveArrival()>=now}.take(2)
    val oldest=relevant.mapNotNull {it.realtimeReceivedAt}.minOrNull() ?: return null
    return oldest.takeIf {now-it>JourneyTimePolicy.STALE_REALTIME_MILLIS}
}

/** "ca." marker for approximate times; empty for exact provider values. */
fun approximateMarker(event:ProjectedEvent):String=if(event.approximate)dev.ambivalenton.openv.localization.tr("ca.")+" " else ""
