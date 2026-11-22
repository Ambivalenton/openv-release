// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

/** The card and expanded timeline use the same useful travel facts, without diagnostic prose. */
data class AssistantTravelSummary(val change:String,val arrival:String,val notice:String?)
fun assistantTravelSummary(session:PinnedJourneySession,now:Long,focus:JourneyFocus?=null):AssistantTravelSummary {
    val focusedLeg=focus?.legIndex?.let {session.trip.segments.withIndex().firstOrNull {entry->entry.index==it}}
    val current=if(focus!=null)focusedLeg?.takeUnless {it.value.isWalk} else gpsCursorLeg(session)?.takeIf {session.hasRecentRouteEvidence(now)}?.takeUnless {it.value.isWalk} ?: assistantCurrentRide(session,now)
    val ride=current ?: session.trip.segments.withIndex().firstOrNull {(_,s)->!s.isWalk && (focusedLeg==null && s.effectiveArrival()>=now || focusedLeg!=null && session.trip.segments.indexOf(s)>focusedLeg.index) && stationRideKey(s) !in session.completedRideKeys}
    val waiting=focus?.let {it.boarding || focusedLeg?.value?.isWalk==true} ?: ((session.hasRecentRouteEvidence(now) && session.gpsCursor?.departed==false && gpsCursorLeg(session)?.value?.isWalk==false) ||
        (session.gpsCursor==null && ride?.value?.effectiveDeparture()?.let {it>now}==true))
    val change=if(waiting)ride?.value?.let {tr("Einstieg: %1\$s · %2\$s",displayStopName(it.origin,it.lineType),clockTime(it.effectiveDeparture()))}.orEmpty() else ride?.let {entry->
        val onward=session.trip.segments.drop(entry.index+1).firstOrNull {!it.isWalk}
        val place=displayStopName(entry.value.destination,entry.value.lineType)
        // Transfer reserve = connecting departure − own arrival − transfer walk − boarding reserve.
        val reserve=projectSessionTimes(session,now).transfers.firstOrNull {it.fromIndex==entry.index}
        val risk=when(reserve?.status) {
            TransferStatus.TIGHT->tr(" · knapp")
            TransferStatus.AT_RISK->tr(" · Anschluss gefährdet")
            TransferStatus.BROKEN->tr(" · Anschluss nicht erreichbar")
            else->""
        }
        if(onward!=null)tr("Umstieg: %1\$s · %2\$s",place,clockTime(assistantRideArrival(session,entry,now)))+
            tr(" · weiter %1\$s um %2\$s",onward.lineName,clockTime(onward.effectiveDeparture()))+risk
        else tr("Ausstieg: %1\$s · %2\$s",place,clockTime(assistantRideArrival(session,entry,now)))
    }.orEmpty()
    val delay=arrivalChangeMinutes(session,now)
    // Carried, local or uncertain arrivals are marked instead of looking exact.
    val final=projectSessionTimes(session,now).finalArrival
    val arrival=tr("Am Ziel %1\$s",approximateMarker(final)+clockTime(final.time))+
        if(delay>0)tr(" · %1\$s Min. später",delay) else ""
    val cancelled=session.trip.segments.firstOrNull {!it.isWalk && it.delay.cancelled && stationRideKey(it) !in session.completedRideKeys}
    val wait=ride?.let {entry->session.trip.segments.drop(entry.index+1).firstOrNull {!it.isWalk}?.let {next->
        ((next.effectiveDeparture()-assistantRideArrival(session,entry,now))/60_000).toInt()}}
    val notice=assistantNotice(session,now) ?: cancelled?.let {tr("%1\$s fällt aus.",it.lineName)}
        ?: wait?.takeIf {it>20}?.let {tr("Beim Umstieg wartest du voraussichtlich %1\$s Minuten.",it)}
    return AssistantTravelSummary(change,arrival,notice)
}
