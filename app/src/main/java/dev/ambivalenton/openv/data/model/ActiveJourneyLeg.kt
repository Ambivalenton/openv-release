// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** A timetable alone never activates motion. Ambiguous transfers / overlapping legs stay still. */
fun activeJourneyLeg(trip: Trip,now: Long,presence: DevicePresence?): String? {
    val fix=crediblePresence(presence,now)?.takeIf {it.accuracyMeters<=80f} ?: return null
    data class Candidate(val id: String,val cross: Double,val current: Boolean)
    val candidates=focusStops(trip).groupBy {it.legId}.mapNotNull { (id,stops) ->
        if(stops.all {it.cancelled}) return@mapNotNull null
        val p=projectOntoStops(stops,fix.location) ?: return@mapNotNull null
        val radius=maxOf(12.0,fix.accuracyMeters*1.25)
        if(p.cross>maxOf(60.0,fix.accuracyMeters*2.0) || p.along<radius || p.total-p.along<radius) return@mapNotNull null
        Candidate(id,p.cross,now in stops.first().time..stops.last().departure)
    }.sortedBy {it.cross}
    val best=candidates.firstOrNull() ?: return null
    val nearby=candidates.filter {it.cross<=best.cross+maxOf(20.0,fix.accuracyMeters.toDouble())}
    if(nearby.size==1) return best.id
    // Only disambiguate spatially plausible candidates; no motion without a spatial match.
    return nearby.filter {it.current}.singleOrNull()?.id
}

/** Keep the spatially matched leg visible even when provider times run ahead of the passenger. */
fun journeyTimelineVisibleFrom(stops: List<JourneyFocusStop>,scheduledIndex: Int,activeLegId: String?): Int {
    val scheduled=scheduledIndex.coerceIn(0,stops.size)
    val activeStart=activeLegId?.let {id->stops.indexOfFirst {it.legId==id}}?.takeIf {it>=0}
    return minOf(scheduled,activeStart ?: scheduled)
}

/** Animate only the current vehicle; short, explicitly estimated GNSS gaps retain that vehicle. */
fun activePinnedTransitLeg(trip:Trip,now:Long,presence:DevicePresence?,progress:VisualJourneyProgress?):String? {
    if(progress?.boardingLegId!=null) return null
    progress?.stableLegId?.let {id->
        trip.segments.withIndex().firstOrNull {"${it.index}:${it.value.id}"==id && !it.value.isWalk && !it.value.delay.cancelled &&
            stationRideKey(it.value) !in progress.completedRideKeys}?.let {return id}
    }
    val measured=activeJourneyLeg(trip,now,presence)
    val physical=progress?.takeIf {it.corrected && now-it.computedAt in 0..30_000 &&
        crediblePresence(presence,now,if(it.estimated) 60_000 else 30_000)!=null}
        ?.let {focusStops(trip).getOrNull(it.nextIndex)?.legId}
    val id=measured ?: physical ?: return null
    return trip.segments.withIndex().firstOrNull {"${it.index}:${it.value.id}"==id}
        ?.takeUnless {it.value.isWalk || it.value.delay.cancelled || stationRideKey(it.value) in progress?.completedRideKeys.orEmpty()}?.let {id}
}

/** The pinned journey includes walking; unlike the comparison chart it draws a glowing rail. */
fun activePinnedLeg(trip:Trip,now:Long,presence:DevicePresence?,progress:VisualJourneyProgress?):String? {
    if(progress?.boardingLegId!=null) return null
    progress?.stableLegId?.let {id->
        trip.segments.withIndex().firstOrNull {"${it.index}:${it.value.id}"==id && !it.value.delay.cancelled &&
            (it.value.isWalk || stationRideKey(it.value) !in progress.completedRideKeys)}?.let {return id}
    }
    activePinnedTransitLeg(trip,now,presence,progress)?.let {return it}
    val id=activeJourneyLeg(trip,now,presence) ?: return null
    return trip.segments.withIndex().firstOrNull {"${it.index}:${it.value.id}"==id}
        ?.takeIf {it.value.isWalk && !it.value.delay.cancelled}?.let {id}
}
