// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import kotlinx.coroutines.*

/** [complete]: the whole remaining chain was received. A per-leg fallback is fresh only for
 * the legs it actually received; their own realtimeReceivedAt carries that information. */
data class AssistantRefresh(val trip:Trip,val fresh:Boolean,val complete:Boolean=fresh)

/** Refresh tokens are optional. EFA may stop returning the old complete chain as soon as an
 * onward connection is missed. Refresh its selected services separately instead of losing them. */
suspend fun TransitRepository.refreshAssistantJourney(session:PinnedJourneySession,now:Long):AssistantRefresh {
    val old=session.trip
    if(!old.refreshToken.isNullOrBlank() || old.waypointLegs.isNotEmpty())try {
        val next=withTimeout(8_000){refresh(old)}
        if(samePinnedJourney(old,next))return AssistantRefresh(continuousAssistantJourney(old,next),true)
    }catch(e:TimeoutCancellationException){currentCoroutineContext().ensureActive()}
    catch(e:CancellationException){throw e}catch(_:Exception){}
    try {
        val page=withTimeout(8_000){journeyPage(session.query.copy(time=old.segments.minOf {it.plannedStartTime},timeMode=TimeMode.DEPARTURE))}
        page.trips.firstOrNull {samePinnedJourney(old,it)}?.let {return AssistantRefresh(continuousAssistantJourney(old,it),true)}
        val first=old.segments.firstOrNull {!it.isWalk && stationRideKey(it) !in session.completedRideKeys}
        val update=page.trips.filter {candidate->
            val head=candidate.segments.firstOrNull {!it.isWalk}
            first!=null && head!=null && sameVehicleService(first,head) &&
                assistantRouteDecision(session,candidate,now)==AssistantRouteDecision.UPDATE_CONNECTION &&
                !candidate.cancelled && unreachablePinnedTransfer(candidate,session.routing.ignoreWalking,session.routing.walkingSpeed)==null && assistantTransfersReachable(session,candidate,now)
        }.minByOrNull {it.effectiveArrival()}
        if(update!=null)return AssistantRefresh(continuousAssistantJourney(old,update),true)
    }catch(e:TimeoutCancellationException){currentCoroutineContext().ensureActive()}
    catch(e:CancellationException){throw e}catch(_:Exception){}
    val active=assistantCurrentRide(session,now)?.index
    val indices=old.segments.indices.filter {i->!old.segments[i].isWalk &&
        stationRideKey(old.segments[i]) !in session.completedRideKeys && (active==null || i>=active)}.take(2)
    val segments=old.segments.toMutableList();var changed=false
    for(i in indices)try {
        val leg=segments[i]
        val query=session.query.copy(origin=leg.origin,destination=leg.destination,time=leg.plannedStartTime,
            timeMode=TimeMode.DEPARTURE,waypoints=emptyList(),waypointDwellMinutes=emptyMap())
        val page=withTimeout(4_000){journeyPage(query)}
        val match=page.trips.flatMap {it.segments}.firstOrNull {sameVehicleService(leg,it) &&
            sameLocation(leg.origin,it.origin) && sameLocation(leg.destination,it.destination)}
        if(match!=null){segments[i]=match.copy(id=leg.id);changed=true}
    }catch(e:TimeoutCancellationException){currentCoroutineContext().ensureActive()}
    catch(e:CancellationException){throw e}catch(_:Exception){}
    return AssistantRefresh(if(changed)continuousAssistantJourney(old,old.copy(segments=segments,fetchedAt=now))else old,changed,complete=false)
}

/** One or two journey pages from the boarding stop around the estimated departure of the
 * service the passenger was observed on. Returns only a service that passes the observed position
 * at the observed time (see matchObservedRide); otherwise nothing changes. */
suspend fun TransitRepository.findObservedRide(session:PinnedJourneySession,observation:RideDeviationObservation):ObservedRideMatch? {
    val query=observedRideQuery(session,observation) ?: return null
    val first=withTimeout(8_000){journeyPage(query)}
    matchObservedRide(session,observation,first.trips)?.let {return it}
    // The page may end before the observed service; one more page at most.
    val covered=first.trips.mapNotNull {trip->trip.segments.firstOrNull {!it.isWalk}?.effectiveDeparture()}.maxOrNull()
    val cursor=first.nextCursor ?: return null
    if(covered!=null && covered>=observation.lastObservedAt)return null
    val second=withTimeout(6_000){journeyPage(query,cursor)}
    return matchObservedRide(session,observation,first.trips+second.trips)
}
