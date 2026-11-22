// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

enum class LiveShareEmphasis { NORMAL, POSITION, DELAY, ARRIVAL, ON_TIME }
data class LiveSharePart(val text: String,val emphasis: LiveShareEmphasis=LiveShareEmphasis.NORMAL)
data class LiveJourneyShare(val title: String,val parts: List<LiveSharePart>,val capturedAt: Long) {
    val plainText get()=title+"\n\n"+parts.joinToString("") {it.text}
}

/** Uses provider-reported current timestamps. A future arrival is a live forecast, not an observed fact. */
fun liveJourneyShare(session: PinnedJourneySession,now: Long,presence: DevicePresence?,weather: ArrivalWeather?=null): LiveJourneyShare {
    val focus=journeyFocus(session,now,presence)
    val settings=session.routing.overview.copy(showArrivalTime=true,showDepartureTime=true,showLineNames=true)
    val parts=OverviewSentenceGenerator.sentence(session.favorite,session.trip,settings,0,weather).map {
        LiveSharePart(it.text,if(it.role==OverviewTextRole.ARRIVAL_TIME) LiveShareEmphasis.ARRIVAL else LiveShareEmphasis.NORMAL)
    }.toMutableList()
    parts+=LiveSharePart("\n\n")
    parts+=LiveSharePart(tr("%1\$s: %2\$s",if(focus.measured) tr("Aktueller Standort") else tr("Standort laut Fahrplan"),focus.position),LiveShareEmphasis.POSITION)
    parts+=LiveSharePart("\n")
    val activeLeg=session.trip.segments.firstOrNull {!it.isWalk && it.endTime>now}
    val known=if(activeLeg!=null && now<activeLeg.startTime) activeLeg.delay.departureSeconds else activeLeg?.delay?.arrivalSeconds
    val stale=now-session.lastSuccessAt>120_000
    val status=if(stale) tr("Live-Stand älter") else focus.delay ?: if(known!=null && known<=0) tr("Pünktlich") else tr("Keine Verspätung gemeldet")
    parts+=LiveSharePart(tr("Status: %1\$s",status),if(focus.delay!=null || stale) LiveShareEmphasis.DELAY else LiveShareEmphasis.ON_TIME)
    parts+=LiveSharePart("\n")
    parts+=LiveSharePart(tr("Live-Ankunft: %1\$s Uhr",clockTime(session.trip.endTime)),LiveShareEmphasis.ARRIVAL)
    parts+=LiveSharePart("\n"+if(session.trip.endTime>now) tr("Ankunft ist eine aktuelle Prognose des Anbieters.") else tr("Ankunft laut letztem Anbieterstand."))
    parts+=LiveSharePart("\n"+tr("Letzter Live-Stand: %1\$s Uhr",clockTime(session.lastSuccessAt)))
    return LiveJourneyShare(session.favorite.label,parts,now)
}
