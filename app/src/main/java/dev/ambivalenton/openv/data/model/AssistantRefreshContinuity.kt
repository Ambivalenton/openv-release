// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Forecast values that decide whether a leg's prognosis actually changed. */
private fun TripSegment.forecastSignature()=listOf(effectiveDeparture(),effectiveArrival(),delay.cancelled)

/**
 * Freshness is tracked per leg. A response that repeats the same values keeps the time at which
 * the forecast last changed; a response without realtime for a leg that had a forecast keeps the
 * last known forecast for a bounded time (it ages visibly) instead of falling back to "on time".
 */
internal fun mergeLegFreshness(old:TripSegment,next:TripSegment,receivedAt:Long):TripSegment {
    if(next.isWalk)return next
    val oldReceived=old.realtimeReceivedAt
    val oldHadForecast=old.departureTimeSource()!=TimeSource.PLANNED || old.arrivalTimeSource()!=TimeSource.PLANNED
    val nextHasNone=next.realtimeReceivedAt==null && next.departureTimeSource()==TimeSource.PLANNED && next.arrivalTimeSource()==TimeSource.PLANNED
    if(nextHasNone && oldHadForecast && oldReceived!=null && !next.delay.cancelled &&
        old.plannedStartTime==next.plannedStartTime && old.plannedEndTime==next.plannedEndTime) {
        if(receivedAt-oldReceived in 0..JourneyTimePolicy.RETAINED_FORECAST_MILLIS)
            return next.copy(startTime=old.startTime,endTime=old.endTime,delay=old.delay,
                departureSource=old.departureSource,arrivalSource=old.arrivalSource,realtimeDeparture=old.realtimeDeparture,
                realtimeReceivedAt=oldReceived,realtimeGeneratedAt=old.realtimeGeneratedAt,forecastChangedAt=old.forecastChangedAt,
                stops=next.stops.ifEmpty {old.stops})
    }
    val changed=old.forecastSignature()!=next.forecastSignature()
    return next.copy(forecastChangedAt=if(changed)next.realtimeReceivedAt ?: next.forecastChangedAt else old.forecastChangedAt ?: next.forecastChangedAt)
}

/** Timetable refreshes may omit intermediate calls or shapes. Retain their travel order;
 * explicit updates (including cancellations) win, and new calls are inserted by travel time. */
fun continuousAssistantJourney(previous:Trip,updated:Trip):Trip = updated.copy(segments=updated.segments.mapIndexed {index,next->
    if(next.isWalk) {
        val old=previous.segments.getOrNull(index)?.takeIf {it.isWalk && sameLocation(it.origin,next.origin) && sameLocation(it.destination,next.destination)}
        return@mapIndexed if(old==null)next else next.copy(id=old.id,plannedStartTime=old.plannedStartTime,plannedEndTime=old.plannedEndTime,
            routeShape=next.routeShape.ifEmpty {old.routeShape})
    }
    val old=previous.segments.firstOrNull {sameVehicleService(it,next) && sameLocation(it.origin,next.origin) && sameLocation(it.destination,next.destination)}
        ?: return@mapIndexed next
    val fresh=mergeLegFreshness(old,next,updated.fetchedAt)
    val unused=fresh.stops.toMutableList()
    val delta=fresh.effectiveDeparture()-old.effectiveDeparture()
    val calls=old.stops.map {before->
        val index=unused.indexOfFirst {sameLocation(it.location,before.location)}
        if(index>=0)unused.removeAt(index) else before.copy(arrivalTime=before.arrivalTime?.plus(delta),departureTime=before.departureTime?.plus(delta),realtime=false)
    }.toMutableList()
    unused.forEach {call->
        val time=call.arrivalTime ?: call.departureTime ?: Long.MAX_VALUE
        val at=calls.indexOfFirst {(it.arrivalTime ?: it.departureTime ?: Long.MAX_VALUE)>time}
        if(at<0)calls+=call else calls.add(at,call)
    }
    // A response that omits a platform is "not stated", not a platform change.
    fresh.copy(id=old.id,stops=calls.take(128),routeShape=fresh.routeShape.ifEmpty {old.routeShape},
        departurePlatform=fresh.departurePlatform?.takeIf {it.isNotBlank()} ?: old.departurePlatform,
        arrivalPlatform=fresh.arrivalPlatform?.takeIf {it.isNotBlank()} ?: old.arrivalPlatform)
})
