// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

/** The public instruction is independent of telemetry source. Coarse progress is shared by card,
 * expanded timeline and notifications, so a sensor/source change cannot select a second layout. */
private fun userFocus(focus:JourneyFocus,session:PinnedJourneySession):JourneyFocus {
    val leg=focus.legIndex?.let {session.trip.segments.getOrNull(it)}
    val walking=leg?.isWalk==true
    return focus.copy(position="",timeLabel=if(focus.boarding)tr("Abfahrt")else tr("Ankunft"),
        platform=if(walking)null else focus.platform,
        action=when {walking->tr("Zu Fuß weiter");focus.boarding->tr("Einsteigen: %1\$s",focus.line)
            leg!=null->tr("Nächster Halt");else->tr("Ankunft am Ziel")})
}
private fun focusStep(focus:JourneyFocus)=listOf(focus.legIndex,focus.nextPlace,focus.boarding).joinToString("|")

/** Retain the selected instruction while taking fresh times/platforms from its same service call. */
private fun retainedFocus(old:JourneyFocus,session:PinnedJourneySession,now:Long):JourneyFocus {
    val stops=focusStops(session.trip)
    val oldStop=old.stops.getOrNull(old.nextIndex)
    val index=stops.indexOfFirst {it.key==oldStop?.key && it.location.name==old.nextPlace}
        .takeIf {it>=0} ?: stops.indexOfFirst {it.location.name==old.nextPlace && it.legId.substringBefore(':').toIntOrNull()==old.legIndex}
    val stop=stops.getOrNull(index)
    val leg=old.legIndex?.let {session.trip.segments.getOrNull(it)}
    // A retained instruction still receives the current times of the same call.
    val projection=projectSessionTimes(session,now)
    val stopLeg=stop?.legId?.substringBefore(':')?.toIntOrNull()
    val stopTime=stop?.let {call->
        if(call.key.endsWith("-end"))stopLeg?.let {projection.leg(it)?.arrival?.time} ?: call.time
        else call.time+(stopLeg?.let {projection.leg(it)?.localOffsetMillis} ?: 0L)
    }
    return old.copy(stops=stops,nextIndex=if(index>=0)index else old.nextIndex.coerceAtMost(stops.size),
        nextTime=if(old.boarding)leg?.effectiveDeparture()?:old.nextTime else stopTime?:old.nextTime,
        platform=if(leg?.isWalk==true)null else platformName(if(old.boarding)leg?.departurePlatform else stop?.platform,leg?.lineType?:LineType.OTHER),
        arrival=projection.finalArrival.time)
}

fun stabilizedAssistantState(previous:PinnedJourneyState,next:PinnedJourneyState,now:Long):PinnedJourneyState {
    val session=next.session ?: return next.copy(displayFocus=null,pendingDisplayKey=null)
    val raw=journeyFocus(session,now,next.presence,next.visualProgress)
    val candidate=userFocus(raw,session)
    val old=previous.displayFocus?.takeIf {previous.session?.id==session.id && previous.displayRouteRevision==session.routeRevision}
    if(old==null)return next.copy(displayFocus=candidate,displayRouteRevision=session.routeRevision,pendingDisplayKey=null)
    val same=focusStep(old)==focusStep(candidate)
    if(same)return next.copy(displayFocus=candidate.copy(measured=candidate.measured||old.measured,passedStopKeys=old.passedStopKeys+candidate.passedStopKeys),displayRouteRevision=session.routeRevision,pendingDisplayKey=null)
    val oldLeg=old.legIndex?:old.stops.getOrNull(old.nextIndex)?.legId?.substringBefore(':')?.toIntOrNull()?:0
    val newLeg=candidate.legIndex?:candidate.stops.getOrNull(candidate.nextIndex)?.legId?.substringBefore(':')?.toIntOrNull()?:oldLeg
    val backwards=newLeg<oldLeg || newLeg==oldLeg && (candidate.nextIndex<old.nextIndex || !old.boarding&&candidate.boarding)
    val source=next.visualProgress?.positionSource ?: resolvedJourneyProgress(session,now,next.presence).positionSource
    val measured=source==JourneyPositionSource.GPS && currentJourneyGps(next.presence,now)!=null
    val candidateStop=candidate.stops.getOrNull(candidate.nextIndex)
    val alreadyPassed=candidateStop?.key in old.passedStopKeys
    if(source==JourneyPositionSource.GPS_UNMATCHED || backwards&&(!measured || alreadyPassed || old.measured))
        return next.copy(displayFocus=retainedFocus(old,session,now),displayRouteRevision=session.routeRevision,pendingDisplayKey=null)
    val completed=old.legIndex?.let {session.trip.segments.getOrNull(it)}?.let {stationRideKey(it) in session.completedRideKeys}==true
    val predictedLegChange=newLeg>oldLeg && !measured && old.measured && !completed
    val confirmation=when {backwards->12_000L;predictedLegChange->8_000L;else->0L}
    if(confirmation>0) {
        val key=focusStep(candidate)
        val since=previous.pendingDisplaySince.takeIf {previous.pendingDisplayKey==key}?:now
        if(now-since<confirmation)return next.copy(displayFocus=retainedFocus(old,session,now),pendingDisplayKey=key,
            pendingDisplaySince=since,displayRouteRevision=session.routeRevision)
    }
    return next.copy(displayFocus=candidate,pendingDisplayKey=null,displayRouteRevision=session.routeRevision)
}

fun assistantDisplayFocus(state:PinnedJourneyState,now:Long):JourneyFocus?=state.session?.let {session->
    state.displayFocus ?: userFocus(journeyFocus(session,now,state.presence,state.visualProgress),session)
}
/** The bubble names the next change or the destination, never the next intermediate call.
 * During a ride its time is the projected arrival at the alighting stop when another ride follows,
 * otherwise the projected arrival at the destination (including a final walk). Waiting to board
 * keeps the departure; a walk already ends at the next boarding place or at the destination.
 * Stops, index and passed keys stay unchanged, so the remaining-stop count is still exact. */
fun bubbleJourneyFocus(focus:JourneyFocus,session:PinnedJourneySession,now:Long):JourneyFocus {
    if(focus.boarding)return focus
    val segments=session.trip.segments
    val index=focus.legIndex ?: return focus
    val leg=segments.getOrNull(index)?.takeUnless {it.isWalk} ?: return focus
    val projection=projectSessionTimes(session,now)
    val laterRide=segments.withIndex().any {(i,s)->i>index && !s.isWalk && !s.delay.cancelled}
    return if(laterRide)focus.copy(action=tr("Umstieg"),nextPlace=leg.destination.name,
        nextTime=projection.leg(index)?.arrival?.time ?: leg.endTime,
        platform=platformName(leg.arrivalPlatform,leg.lineType))
    else focus.copy(action=tr("Ziel"),nextPlace=session.query.destination.name,nextTime=projection.finalArrival.time,
        platform=if(segments.drop(index+1).any {it.isWalk})null else platformName(leg.arrivalPlatform,leg.lineType))
}
fun remainingRideStops(focus:JourneyFocus):Int {
    val target=focus.stops.getOrNull(focus.nextIndex)?:return 0
    if(focus.boarding || target.lineType==LineType.WALK)return 0
    return focus.stops.drop(focus.nextIndex).count {it.legId==target.legId && !it.cancelled && it.key !in focus.passedStopKeys}
}
fun assistantStepDetail(focus:JourneyFocus):String=remainingRideStops(focus).takeIf {it>0}?.let {count->
    if(count==1)tr("Noch ein Halt bis zum Ausstieg")else tr("Noch %1\$s Haltestellen bis zum Ausstieg",count)
}.orEmpty()

/** The boarding platform is shown whether or not a walk precedes the first ride. Before any
 * route match (no leg index yet) the first unfinished ride is the next boarding. A known platform
 * is never a claim that the passenger is already there. */
fun nextBoardingInstruction(session:PinnedJourneySession,focus:JourneyFocus):String {
    val (index,platform)=nextBoarding(session,focus)?:return ""
    val first=session.trip.segments.indexOfFirst {!it.isWalk}
    // Build 122: a platform changed by the operator is said as such ("jetzt Gleis 5 statt 3").
    val ride=session.trip.segments[index]
    val shown=ride.plannedDeparturePlatform?.let {planned->tr("jetzt %1\$s statt %2\$s",platform,planned.trim())} ?: platform
    return if(index==first)tr("Nächster Einstieg: %1\$s",shown)else tr("Nächster Umstieg: %1\$s",shown)
}

/** Build 122: the next boarding's platform was changed by the operator. */
fun nextBoardingPlatformChanged(session:PinnedJourneySession,focus:JourneyFocus):Boolean =
    nextBoarding(session,focus)?.let {(index,_)->session.trip.segments[index].plannedDeparturePlatform!=null}==true

/** The platform of [nextBoardingInstruction] alone ("Gleis 2"): the card falls back to it when the
 * whole instruction does not fit (very large text), so the platform itself never disappears. */
fun nextBoardingPlatform(session:PinnedJourneySession,focus:JourneyFocus):String=nextBoarding(session,focus)?.second.orEmpty()

private fun nextBoarding(session:PinnedJourneySession,focus:JourneyFocus):Pair<Int,String>? {
    val index=focus.legIndex ?: -1
    val waitingForCurrent=focus.boarding || session.gpsCursor?.let {it.legIndex==index && !it.departed}==true
    val ride=session.trip.segments.withIndex().firstOrNull {(i,s)->!s.isWalk && !s.delay.cancelled &&
        (i>index || i==index&&waitingForCurrent) && stationRideKey(s) !in session.completedRideKeys}?:return null
    val platform=platformName(ride.value.departurePlatform,ride.value.lineType)?:return null
    return ride.index to platform
}
