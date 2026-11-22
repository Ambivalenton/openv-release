// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import java.util.Locale
import kotlin.math.*

data class JourneyFocusStop(val key: String, val location: Location, val time: Long, val departure: Long,
    val line: String, val platform: String?, val cancelled: Boolean = false,
    val lineType: LineType = LineType.OTHER, val legId: String = "",
    /** Timetable time of this call; null for intermediate calls stored before build 65. */
    val plannedTime: Long? = null)

data class JourneyFocus(val position: String, val measured: Boolean, val action: String, val nextPlace: String,
    val nextTime: Long, val timeLabel: String, val line: String, val platform: String?, val delay: String?,
    val arrival: Long, val progress: Float, val stops: List<JourneyFocusStop>, val nextIndex: Int,
    val passedStopKeys:Set<String> = emptySet(),val gpsPosition:Boolean=false,
    val boarding:Boolean=false,val legIndex:Int?=null)

/** Elapsed-time progress is a forecast, never proof that the passenger boarded a vehicle. */
fun JourneyFocus.heading(): String = if (line.isNotBlank() && !action.contains(line)) "$line · $action" else action

fun journeyProgress(trip: Trip, now: Long): Float =
    trip.timeWindow().progress(now)

fun focusStops(trip: Trip): List<JourneyFocusStop> = buildList<JourneyFocusStop> {
    trip.segments.forEachIndexed { index, leg ->
        val candidates = buildList<JourneyFocusStop> {
            add(JourneyFocusStop("$index-start", leg.origin, leg.startTime, leg.startTime, leg.lineName, leg.departurePlatform, leg.delay.cancelled, leg.lineType, "$index:${leg.id}", leg.plannedStartTime))
            leg.stops.forEachIndexed stop@ { i, stop ->
                val time = stop.arrivalTime ?: stop.departureTime ?: return@stop
                val endpointDuplicate=(leg.stops.take(i).all {sameLocation(it.location,leg.origin)} && sameLocation(stop.location,leg.origin)) ||
                    (leg.stops.drop(i+1).all {sameLocation(it.location,leg.destination)} && sameLocation(stop.location,leg.destination))
                if(!endpointDuplicate)
                    add(JourneyFocusStop("$index-$i", stop.location, time, stop.departureTime ?: time, leg.lineName, stop.platform, stop.cancelled, leg.lineType, "$index:${leg.id}",
                        stop.plannedArrivalTime ?: stop.plannedDepartureTime))
            }
            add(JourneyFocusStop("$index-end", leg.destination, leg.endTime, leg.endTime, leg.lineName, leg.arrivalPlatform, leg.delay.cancelled, leg.lineType, "$index:${leg.id}", leg.plannedEndTime))
        }
        for (stop in candidates) {
            val last = lastOrNull()
            if (last != null && last.legId == stop.legId && sameLocation(last.location, stop.location) && last.departure == stop.time) {
                removeAt(lastIndex)
                add(last.copy(departure = maxOf(last.departure, stop.departure), platform = stop.platform ?: last.platform))
            } else add(stop)
        }
    }
}.take(256)

private fun freshFocusPresence(presence: DevicePresence?, now: Long) = presence?.takeIf {
    now - it.capturedAt in 0..90_000 && it.accuracyMeters.isFinite() && it.accuracyMeters in 0f..200f && it.location.hasCoordinates
}

/** GPS labels require a fresh fix near a known stop or the corridor between adjacent route stops. */
fun journeyFocus(session: PinnedJourneySession, now: Long, presence: DevicePresence?,visual: VisualJourneyProgress?=null): JourneyFocus {
    if(!session.usesServerProgress && (currentJourneyGps(presence,now)!=null || session.hasRecentRouteEvidence(now))) {
        val tracked=if(session.gpsCursor==null && presence!=null) advanceGpsJourney(session,presence,listOf(presence),now) else session
        val derived=resolvedJourneyProgress(tracked,now,presence)
        val provided=visual?.takeIf {it.corrected && it.positionSource==JourneyPositionSource.GPS && derived.positionSource==JourneyPositionSource.GPS &&
            now-it.computedAt in 0..30_000 && it.nextIndex==derived.nextIndex}
        return gpsJourneyFocus(tracked,now,currentJourneyGps(presence,now)?:session.lastTrackedPresence?:DevicePresence(session.query.origin,now-31_000,100f),derived.copy(physicalEtaMinutes=provided?.physicalEtaMinutes ?: derived.physicalEtaMinutes))
    }
    val trip=session.trip;val stops=focusStops(trip)
    // The shared projection decides when a walk ends: after a recovered delay it ends earlier.
    val projection=projectSessionTimes(session,now)
    fun endOf(i:Int)=projection.leg(i)?.arrival?.time ?: trip.segments[i].endTime
    val legIndex=trip.segments.indices.firstOrNull {i->endOf(i)>now && (trip.segments[i].isWalk || stationRideKey(trip.segments[i]) !in session.completedRideKeys)}
    val leg=legIndex?.let {trip.segments[it]}
    val boarding=leg!=null && !leg.isWalk && now<leg.effectiveDeparture()
    val stop=leg?.stops?.firstOrNull {!it.cancelled && (it.arrivalTime ?: it.departureTime ?: Long.MIN_VALUE)>now}
    val place=if(boarding) leg!!.origin else stop?.location ?: leg?.destination ?: session.query.destination
    val index=stops.indexOfFirst {it.departure>=now}.let {if(it<0) stops.size else it}
    return JourneyFocus(PinnedJourneyNarrator.describe(session,now,null).position,false,
        when {leg==null->tr("Zielzeit erreicht");leg.isWalk->tr("Zu Fuß weiter");boarding && leg.effectiveDeparture()-now>90_000->tr("Abfahrt: %1\$s",leg.lineName);boarding->tr("Einsteigen: %1\$s",leg.lineName);else->tr("Nächster Halt")},
        place.name,if(boarding) leg!!.effectiveDeparture() else stop?.arrivalTime ?: stop?.departureTime ?: legIndex?.let(::endOf) ?: projection.finalArrival.time,
        tr("Fahrplanzeit"),leg?.takeUnless {it.isWalk}?.lineName.orEmpty(),
        platformName(if(boarding) leg?.departurePlatform else stop?.platform ?: leg?.arrivalPlatform,leg?.lineType ?: LineType.OTHER),leg?.currentDelaySeconds(boarding)?.takeIf {it>0}?.let {tr("+%1\$s Min.",(it+59)/60)},
        projection.finalArrival.time,journeyProgress(trip,now),stops,index,session.gpsCursor?.passedStopKeys.orEmpty(),
        boarding=boarding,legIndex=legIndex)
}

/** Available GPS cannot fall through to a clock-selected stop, even off-route or at a red light. */
fun gpsJourneyFocus(session:PinnedJourneySession,now:Long,fix:DevicePresence,progress:VisualJourneyProgress):JourneyFocus {
    val projection=projectSessionTimes(session,now)
    val cursorEntry=gpsCursorLeg(session)
    // Remaining calls of the current ride move with a local correction, so the timeline,
    // card and notification show the same times.
    val offset=cursorEntry?.let {projection.leg(it.index)?.localOffsetMillis} ?: 0L
    val currentLegId=cursorEntry?.let {"${it.index}:${it.value.id}"}
    val passedKeys=progress.passedStopKeys
    val stops=focusStops(session.trip).map {stop->
        if(offset!=0L && stop.legId==currentLegId && stop.key !in passedKeys && !stop.key.endsWith("-start"))
            stop.copy(time=stop.time+offset,departure=stop.departure+offset) else stop
    }
    val leg=cursorEntry?.value
    val target=stops.getOrNull(progress.nextIndex)
    val nearest=stops.mapNotNull {s->distanceMeters(fix.location,s.location)?.let {s to it}}
        .filter {it.second<=maxOf(100.0,fix.accuracyMeters.toDouble())}.minByOrNull {it.second}
    val position=nearest?.first?.location?.name ?: if(leg?.isWalk==true || session.movement.mode==JourneyMovementMode.ON_FOOT)tr("Zu Fuß unterwegs") else if(leg!=null)tr("Unterwegs mit %1\$s",leg.lineName)else tr("Unterwegs")
    val boarding=progress.boardingLegId!=null && leg!=null
    val approaching=boarding && session.stationArrival==null && (session.movement.mode==JourneyMovementMode.ON_FOOT ||
        (distanceMeters(fix.location,leg!!.origin)?:Double.MAX_VALUE)>STATION_ENTER_METERS)
    val action=when {leg==null->tr("Deine Weiterfahrt");approaching->tr("Zum Einstieg: %1\$s",leg.lineName)
        boarding && leg!!.effectiveDeparture()-now>90_000->tr("Abfahrt: %1\$s",leg.lineName)
        boarding && now-leg!!.effectiveDeparture()>90_000->tr("Deine Weiterfahrt")
        boarding->tr("Einsteigen: %1\$s",leg.lineName)
        leg.isWalk->tr("Zu Fuß weiter");else->tr("Nächster Halt")}
    return JourneyFocus(position,progress.positionSource==JourneyPositionSource.GPS,action,if(boarding) leg!!.origin.name else if(leg==null)session.query.destination.name else target?.location?.name ?: session.query.destination.name,
        if(boarding)leg!!.effectiveDeparture() else if(leg==null)projection.finalArrival.time else (if(target?.key?.endsWith("-end")==true)cursorEntry?.let {projection.leg(it.index)?.arrival?.time}?:target.time
            else target?.time?:projection.finalArrival.time),
        if(boarding)tr("Abfahrt") else if(progress.positionSource!=JourneyPositionSource.GPS)tr("Voraussichtlich") else tr("Ankunft"),
        leg?.takeUnless {it.isWalk}?.lineName.orEmpty(),platformName(if(boarding) leg?.departurePlatform else target?.platform,leg?.lineType ?: LineType.OTHER),
        leg?.currentDelaySeconds(boarding)?.takeIf {it>0}?.let {tr("+%1\$s Min.",(it+59)/60)},
        projection.finalArrival.time,progress.fraction?:0f,stops,progress.nextIndex,progress.passedStopKeys,
        progress.positionSource!=JourneyPositionSource.TIMETABLE,boarding=boarding,legIndex=gpsCursorLeg(session)?.index)

}

/** Natural 3.4 dp/min; only journeys of at least two hours may compress, by at most 15%. */
data class ChartFit(val start: Long,val durationMinutes: Float,val heightDp: Float)
fun chartFit(trip: Trip, availableHeight: Float, padding: Float): ChartFit {
    val duration = ((trip.endTime-trip.startTime).coerceAtLeast(60_000)/60_000f).coerceAtLeast(1f)
    val natural = duration*3.4f
    val usable = (availableHeight-padding*2).coerceAtLeast(1f)
    val compression = if (duration>=120f) (usable/natural).coerceIn(.85f,1f) else 1f
    return ChartFit(trip.startTime,duration,natural*compression+padding*2)
}
fun chartSnapIndex(index: Int, offset: Int, width: Float, count: Int): Int =
    (index + if (offset >= width.coerceAtLeast(1f)/2) 1 else 0).coerceIn(0,(count-1).coerceAtLeast(0))
fun chartCrossingBucket(scroll: Float, anchor: Float, width: Float): Int =
    floor(((scroll-anchor)/width.coerceAtLeast(1f)).coerceAtLeast(0f)).toInt()
