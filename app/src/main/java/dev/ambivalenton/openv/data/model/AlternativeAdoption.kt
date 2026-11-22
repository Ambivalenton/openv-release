// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Manual acceptance must preserve a confirmed current ride, including during a GNSS gap. */
fun adoptPinnedAlternative(session:PinnedJourneySession,query:JourneyQuery,candidate:Trip,now:Long?=null):PinnedJourneySession? {
    val riding=if(now!=null)assistantCurrentRide(session,now) else if(session.usesServerProgress)null else gpsCursorLeg(session)?.takeIf {!it.value.isWalk && session.gpsCursor?.departed==true}
    if(riding!=null && !sameLocation(query.origin,riding.value.destination))return null
    val replacement=if(riding==null)candidate else {
        val prefixQuery=query.copy(origin=riding.value.origin,destination=riding.value.destination,
            time=riding.value.plannedStartTime,waypoints=emptyList(),waypointDwellMinutes=emptyMap())
        candidate.copy(id="reroute-${newId()}",segments=listOf(riding.value)+candidate.segments,refreshToken=null,
            waypointLegs=listOf(WaypointLegReference(prefixQuery,riding.value.id,segmentCount=1))+
                candidate.waypointLegs.ifEmpty {listOf(WaypointLegReference(query,candidate.id,candidate.refreshToken,candidate.segments.size))})
    }
    if(unreachablePinnedTransfer(replacement,session.routing.ignoreWalking,session.routing.walkingSpeed)!=null)return null
    val cursor=if(riding==null)null else session.gpsCursor?.let {c->c.copy(legIndex=0,
        passedStopKeys=c.passedStopKeys.filter {it.startsWith("${riding.index}-")}.map {"0-"+it.substringAfter('-')}.toSet())}
    return session.copy(id=newId(),query=if(riding==null)query else query.copy(origin=riding.value.origin,time=riding.value.plannedStartTime),
        initialTrip=session.initialTrip,trip=replacement,alternative=null,alternativeQuery=null,stationArrival=null,lastSuccessAt=candidate.fetchedAt,
        movement=if(riding==null)JourneyMovementState() else session.movement,completedRideKeys=emptySet(),
        activeLegAnchor=if(riding==null)null else session.activeLegAnchor?.copy(index=0),gpsCursor=cursor,missedBoarding=null,
        lastTrackedPresence=if(riding==null)null else session.lastTrackedPresence,automaticReplacement=null,
        routeRevision=session.routeRevision+1)
}

/** While on a vehicle, the query starts at a future alighting stop, not today's GPS point. */
fun alternativeReachabilityPresence(session:PinnedJourneySession,query:JourneyQuery,presence:DevicePresence?):DevicePresence?=
    if(session.gpsCursor?.departed==true && gpsCursorLeg(session)?.value?.let {!it.isWalk && sameLocation(it.destination,query.origin)}==true)null else presence
