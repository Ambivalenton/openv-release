// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

fun pinnedStopLabel(focus:JourneyFocus,trip:Trip?):Pair<String,Boolean> {
    val target=focus.stops.getOrNull(focus.nextIndex)
    val leg=trip?.segments?.firstOrNull {segment->segment.origin.name==focus.nextPlace || segment.destination.name==focus.nextPlace || segment.stops.any {it.location.name==focus.nextPlace}}
    val location=target?.location?.takeIf {it.name==focus.nextPlace}
        ?:leg?.let {segment->(listOf(segment.origin,segment.destination)+segment.stops.map {it.location}).firstOrNull {it.name==focus.nextPlace}}
        ?:Location(focus.nextPlace)
    val type=target?.takeIf {it.location.name==focus.nextPlace}?.lineType?:leg?.lineType?:target?.lineType?:LineType.OTHER
    return displayStopName(location,type) to location.isRailStationName()
}

/** The current-position slot uses the same stop-label policy as the next-stop slot. */
fun pinnedPositionLabel(focus:JourneyFocus,trip:Trip?):String {
    val candidate=trip?.segments?.asSequence()?.flatMap{leg->
        (listOf(leg.origin,leg.destination)+leg.stops.map{it.location}).asSequence().map{it to leg.lineType}
    }?.firstOrNull{it.first.name==focus.position}
    return candidate?.let{displayStopName(it.first,it.second)}?:focus.position
}
