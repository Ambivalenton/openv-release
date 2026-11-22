// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import kotlinx.serialization.Serializable
import kotlin.math.ceil

/** User intent survives rerouting. Changes are classified before the UI ever sees an offer. */
enum class AssistantRouteDecision { KEEP, UPDATE_CONNECTION, OFFER }
@Serializable data class AssistantNotice(val message:String,val changedAt:Long,val remainingViews:Int=2)

/** A line is not a service: opposite directions and the next train on the same line differ. */
fun sameVehicleService(a:TripSegment,b:TripSegment):Boolean {
    if(a.isWalk || b.isWalk || a.lineType!=b.lineType || !a.lineName.trim().equals(b.lineName.trim(),true))return false
    if(sameLocation(a.origin,b.origin) && a.plannedStartTime==b.plannedStartTime)return true
    if(a.serviceId!=null && a.serviceId==b.serviceId)return true
    // A search may shorten the current ride to another boarding stop. Compare a shared,
    // explicitly timed call, never merely the line label or provider's regenerated segment ID.
    fun plannedCalls(s:TripSegment)=listOf(s.origin to s.plannedStartTime,s.destination to s.plannedEndTime)
    return plannedCalls(a).any {(place,time)->plannedCalls(b).any {(other,at)->time==at && sameLocation(place,other)}}
}

fun frequentLocalService(leg:TripSegment,departures:List<Departure>):Boolean {
    if(leg.lineType !in setOf(LineType.BUS,LineType.TRAM,LineType.SUBWAY))return false
    val times=departures.filter {row->!row.delay.cancelled && row.lineType==leg.lineType &&
        row.lineName.trim().equals(leg.lineName.trim(),true) &&
        !leg.direction.isNullOrBlank() && row.destination.trim().equals(leg.direction.trim(),true)}
        .map {it.plannedTime}.distinct().sorted()
    // At least two observed intervals, including one around this departure. No invented headway.
    return times.size>=3 && times.any {kotlin.math.abs(it-leg.plannedStartTime)<=20*60_000L} &&
        times.zipWithNext().all {(a,b)->b-a in 1..20*60_000L}
}

fun assistantRouteDecision(session:PinnedJourneySession,complete:Trip,now:Long,
    frequentConnection:Boolean=false):AssistantRouteDecision {
    if(samePinnedJourney(session.trip,complete))return AssistantRouteDecision.KEEP
    val previous=session.trip.segments.filterNot {it.isWalk || stationRideKey(it) in session.completedRideKeys}
    val proposed=complete.segments.filterNot {it.isWalk || stationRideKey(it) in session.completedRideKeys}
    if(proposed.isEmpty())return if(previous.isEmpty())AssistantRouteDecision.KEEP else AssistantRouteDecision.OFFER
    val common=previous.zip(proposed).takeWhile {(a,b)->sameVehicleService(a,b)}.size
    val before=previous.getOrNull(common)
    val after=proposed.getOrNull(common)
    val sameTrunk=previous.firstOrNull()?.let {old->proposed.firstOrNull()?.let {sameVehicleService(old,it)}}==true
    val disrupted=session.trip.cancelled || unreachablePinnedTransfer(session.trip,session.routing.ignoreWalking,session.routing.walkingSpeed)!=null || !assistantTransfersReachable(session,session.trip,now) || session.missedBoarding!=null
    val localChange=(before==null || before.lineType in setOf(LineType.BUS,LineType.TRAM,LineType.SUBWAY)) &&
        (after==null || after.lineType in setOf(LineType.BUS,LineType.TRAM,LineType.SUBWAY))
    val better=complete.effectiveArrival()<session.trip.effectiveArrival()-60_000
    // A changed local onward connection while keeping the main ride is part of one journey.
    // The earlier service's cancellation does not count as a vehicle switch for the passenger.
    if((sameTrunk && localChange || frequentConnection && localChange) && (disrupted || better))return AssistantRouteDecision.UPDATE_CONNECTION
    if(!disrupted && complete.effectiveArrival()>session.trip.effectiveArrival()-3*60_000)return AssistantRouteDecision.KEEP
    return AssistantRouteDecision.OFFER
}

/** Earliest safe departure after the CURRENT ride, not a walking ETA from a moving vehicle. */
fun assistantReadyAt(session:PinnedJourneySession,now:Long):Long {
    val current=assistantCurrentRide(session,now) ?: return now
    return maxOf(now,assistantRideArrival(session,current,now))+(if(session.routing.ignoreWalking)0 else 60_000)
}

fun assistantCurrentRide(session:PinnedJourneySession,now:Long):IndexedValue<TripSegment>? {
    gpsCursorLeg(session)?.takeIf {session.hasRecentRouteEvidence(now) && !it.value.isWalk && session.gpsCursor?.departed==true}?.let {return it}
    if(!session.usesServerProgress && session.hasRecentRouteEvidence(now))return null
    return session.trip.segments.withIndex().firstOrNull {(_,leg)->!leg.isWalk && !leg.delay.cancelled &&
        now in leg.effectiveDeparture() until leg.effectiveArrival() && stationRideKey(leg) !in session.completedRideKeys}
}

fun reachableAssistantAlternative(session:PinnedJourneySession,query:JourneyQuery,candidate:Trip,now:Long,presence:DevicePresence?):Trip? {
    if(now-candidate.fetchedAt !in 0..180_000)return null
    if(!sameLocation(query.destination,session.query.destination))return null
    val destination=candidate.segments.lastOrNull()?.destination ?: return null
    if(!sameLocation(destination,query.destination) && (distanceMeters(destination,query.destination) ?: Double.MAX_VALUE)>80)return null
    if(query.waypoints.any {point->candidate.waypointVisits.none {visit->sameLocation(point,visit.location) &&
        visit.departureTime-visit.arrivalTime>=waypointStayMillis(point,query.waypointDwellMinutes)}})return null
    val current=assistantCurrentRide(session,now)
    if(current!=null && !sameLocation(query.origin,current.value.destination))return null
    val earliest=if(current!=null)assistantReadyAt(session,now) else now
    val safe=query.copy(time=maxOf(query.time,earliest),walkingSpeed=session.routing.walkingSpeed,ignoreWalking=session.routing.ignoreWalking,transportFilter=session.query.transportFilter)
    val reachable=reachableAlternative(candidate,safe,now,if(current!=null)null else presence,session.routing.walkingSpeed) ?: return null
    val ride=reachable.segments.firstOrNull {!it.isWalk} ?: return reachable
    val access=if(session.routing.ignoreWalking)0L else reachable.segments.takeWhile {it.isWalk}.sumOf {(it.endTime-it.startTime).coerceAtLeast(0)}
    // Boarding slack is additional to the access walk. Coarse fixes increase uncertainty.
    val slack=if(session.routing.ignoreWalking)0L else if(current!=null)30_000L else 45_000L+(presence?.accuracyMeters?.coerceIn(0f,150f)?.times(250)?.toLong() ?: 30_000L)
    if(ride.effectiveDeparture()<maxOf(now,safe.time)+access+slack)return null
    val combined=adoptPinnedAlternative(session,safe,reachable,now)?.trip ?: return null
    if(!assistantTransfersReachable(session,combined,now))return null
    if(!session.routing.ignoreWalking && combined.segments.zipWithNext().any {(a,b)->
        val gap=(distanceMeters(a.destination,b.origin) ?: if(sameLocation(a.destination,b.origin))0.0 else return null)
        // Missing platform walks cannot teleport between stops. Keep a small station allowance.
        gap>350 && b.effectiveDeparture()<a.effectiveArrival()+ceil(gap/session.routing.walkingSpeed.metersPerSecond*1000).toLong()
    })return null
    return reachable
}

/** Apply the same arrival floor to an entire refreshed chain and to offered suffixes. */
fun assistantTransfersReachable(session:PinnedJourneySession,trip:Trip,now:Long):Boolean {
    val active=assistantCurrentRide(session,now)
    val rides=trip.segments.withIndex().filterNot {it.value.isWalk}
    return rides.zipWithNext().all {(before,after)->
        if(stationRideKey(after.value) in session.completedRideKeys)return@all true
        val walk=if(session.routing.ignoreWalking)0L else trip.segments.subList(before.index+1,after.index).sumOf {walkingDurationMillis(it,session.routing.walkingSpeed)}
        val distance=distanceMeters(before.value.destination,after.value.origin)
        val physical=if(session.routing.ignoreWalking || sameLocation(before.value.destination,after.value.origin))0L else distance?.let {ceil(it/session.routing.walkingSpeed.metersPerSecond*1000).toLong()} ?: walk
        val arrival=if(active!=null && sameVehicleService(active.value,before.value))maxOf(before.value.effectiveArrival(),assistantRideArrival(session,active,now)) else before.value.effectiveArrival()
        after.value.effectiveDeparture()>=arrival+maxOf(walk,physical)+(if(session.routing.ignoreWalking)0 else 30_000)
    }
}

fun assistantNotice(session:PinnedJourneySession,now:Long):String?=session.assistantNotice?.takeIf {
    it.remainingViews>0 && now-it.changedAt in 0..10*60_000L}?.message

fun arrivalChangeMinutes(session:PinnedJourneySession,now:Long?=null):Int=
    ceil(((if(now==null)session.trip.effectiveArrival() else assistantFinalArrival(session,now))-session.initialTrip.effectiveArrival())/60_000.0).toInt().coerceAtLeast(0)
fun needsArrivalChangeNotice(session:PinnedJourneySession,now:Long?=null):Boolean=
    arrivalChangeMinutes(session,now)>15 && arrivalChangeMinutes(session,now)>session.acknowledgedArrivalDelay+15

/** Bounded, discrete cadence; no re-registration on every noisy speed sample. */
fun assistantLocationInterval(session:PinnedJourneySession?,now:Long):Long {
    if(session==null || session.usesServerProgress)return 30_000
    val leg=gpsCursorLeg(session)?.value
    // Waiting far ahead of departure needs fewer fixes. The approach to boarding gets
    // the same fast cadence as alighting; the receiver is never restarted per speed spike.
    if(session.gpsCursor?.departed!=true) {
        val departure=leg?.takeUnless {it.isWalk}?.effectiveDeparture() ?: session.trip.firstBoardingTime()
        return when {departure-now>15*60_000->20_000;departure-now>3*60_000->10_000;else->2_000}
    }
    val target=leg?.effectiveArrival() ?: session.trip.firstBoardingTime()
    val fix=session.lastTrackedPresence?.let {currentJourneyGps(it,now)}
    val near=if(leg!=null && fix!=null)(distanceMeters(fix.location,leg.destination) ?: Double.MAX_VALUE)<=maxOf(400.0,(fix.speedMetersPerSecond ?: 0f)*180.0) else false
    if(target-now<=3*60_000 || near)return 2_000
    return if(leg?.lineType in setOf(LineType.ICE,LineType.REGIONAL) && target-now>10*60_000)10_000 else 5_000
}

fun shouldPromptAssistantAlternative(session:PinnedJourneySession,candidate:Trip,now:Long):Boolean {
    val key=alternativeJourneyKey(candidate)
    if(key in session.dismissedAlternativeKeys || (session.lastAlternativePromptAt!=Long.MIN_VALUE && now-session.lastAlternativePromptAt in 0 until 10*60_000))return false
    val query=session.alternativeQuery ?: return false
    val complete=adoptPinnedAlternative(session,query,candidate,now)?.trip ?: return false
    return assistantRouteDecision(session,complete,now)==AssistantRouteDecision.OFFER
}
