// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Revalidate at commit. A current GPS-confirmed ride is kept as a prefix while only its missed
 * onward chain is replaced. Refresh references cover each retained part; no invented vehicle. */
fun automaticallyReplaceMissedJourney(session:PinnedJourneySession,now:Long,presence:DevicePresence?):PinnedJourneySession? {
    val missed=confirmedMissedBoarding(session,now,presence) ?: return null
    val candidate=session.alternative?.let {raw->session.alternativeQuery?.let {query->reachableAssistantAlternative(session,query,raw,now,presence)}}?.takeIf {now-it.fetchedAt in 0..90_000} ?: return null
    val query=session.alternativeQuery ?: return null
    val riding=gpsCursorLeg(session)?.takeIf {!it.value.isWalk && session.gpsCursor?.departed==true}
    val replacement=if(riding!=null) {
        if(session.gpsCursor?.updatedAt!=currentJourneyGps(presence,now)?.capturedAt) return null
        if(!sameLocation(query.origin,riding.value.destination)) return null
        val prefixQuery=query.copy(origin=riding.value.origin,destination=riding.value.destination,
            time=riding.value.plannedStartTime,waypoints=emptyList(),waypointDwellMinutes=emptyMap())
        val references=listOf(WaypointLegReference(prefixQuery,riding.value.id,segmentCount=1))+
            candidate.waypointLegs.ifEmpty {listOf(WaypointLegReference(query,candidate.id,candidate.refreshToken,candidate.segments.size))}
        candidate.copy(id="reroute-${newId()}",segments=listOf(riding.value)+candidate.segments,
            refreshToken=null,waypointLegs=references).takeIf {unreachablePinnedTransfer(it,session.routing.ignoreWalking,session.routing.walkingSpeed)==null} ?: return null
    } else candidate
    val cursor=if(riding!=null) session.gpsCursor?.let {c->c.copy(legIndex=0,
        passedStopKeys=c.passedStopKeys.filter {it.startsWith("${riding.index}-")}.map {"0-"+it.substringAfter('-')}.toSet())} else null
    return session.copy(id=newId(),query=if(riding==null) query else query.copy(origin=riding.value.origin,time=riding.value.plannedStartTime),
        initialTrip=replacement,trip=replacement,alternative=null,alternativeQuery=null,stationArrival=null,
        movement=if(riding!=null) session.movement else JourneyMovementState(),completedRideKeys=emptySet(),
        activeLegAnchor=if(riding!=null) session.activeLegAnchor?.copy(index=0) else null,gpsCursor=cursor,missedBoarding=null,
        automaticReplacement=AutomaticReplacement(missed.value.lineName,candidate.segments.firstOrNull {!it.isWalk}?.lineName.orEmpty(),now))
}
