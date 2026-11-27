// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import java.util.Locale
import kotlinx.coroutines.withContext

private const val MINUTE = 60_000L
internal const val ROUTE_RECHECK = 15 * MINUTE

/** The full itinerary was obtained for this date, mode and routing configuration. Its
 * first requested departures must still cover the anchor before skipping a new route search. */
internal fun CachedJourneySearch.covers(query: JourneyQuery, now: Long): Boolean {
    if (!complete || lastAttemptFailed || now - savedAt !in 0 until ROUTE_RECHECK) return false
    val offset = query.time - this.query.time
    if (offset !in 0 until ROUTE_RECHECK) return false
    return trips.count {
        !it.cancelled && if (query.timeMode == TimeMode.DEPARTURE) it.queryStart(query.ignoreWalking) >= query.time
        else it.queryEnd(query.ignoreWalking) <= query.time
    } >= INITIAL_FUTURE_CONNECTIONS
}

/** One board can update several bars. Select at most two stations near their next boarding. */
internal fun upcomingBoardStops(rows: List<Trip>, now: Long): List<Location> =
    rows.asSequence().filterNot { it.cancelled }.flatMap { it.segments.asSequence() }
        .filter { !it.isWalk && it.origin.kind == LocationKind.STOP && it.origin.id != null &&
            it.effectiveDeparture() - now in -MINUTE..60*MINUTE }
        .sortedBy { it.effectiveDeparture() }.map { it.origin }.distinctBy { it.provider to it.id }.take(2).toList()

private val regionalBrand = Regex("""\s*\(RRX\)\s*$""",RegexOption.IGNORE_CASE)
private val platformSuffix = Regex("""\s+(?:Bstg|Gleis)\s+.*$""",RegexOption.IGNORE_CASE)
internal fun boardLine(value:String)=value.replace(regionalBrand,"").filterNot(Char::isWhitespace).uppercase(Locale.ROOT)
private fun boardDirection(value:String)=value.replace(platformSuffix,"").filter(Char::isLetterOrDigit).lowercase(Locale.ROOT)

private fun sameBoardStop(a: Location, b: Location): Boolean =
    (a.id != null && a.id == b.id && a.provider == b.provider) ||
        (efaGlobalStopId(a)?.let { it == efaGlobalStopId(b) } == true)

/** A departure board confirms only a boarding event. It cannot reinstate a cancelled chain,
 * confirm arrival times or establish that a service missing from a busy board was withdrawn. */
internal fun Trip.withBoardDepartures(departures: List<Departure>, received: Long): Trip {
    var changed = false
    val updated = segments.map { leg ->
        if (leg.isWalk || leg.delay.cancelled || (leg.realtimeReceivedAt ?: 0) > received) return@map leg
        val row = departures.singleOrNull { d ->
            (d.plannedTime == leg.plannedStartTime ||
                (d.plannedTime % MINUTE == 0L && d.plannedTime == Math.floorDiv(leg.plannedStartTime,MINUTE)*MINUTE)) &&
                d.lineType == leg.lineType && boardLine(d.lineName)==boardLine(leg.lineName) && sameBoardStop(leg.origin,d.stop) &&
                !leg.direction.isNullOrBlank() && boardDirection(d.destination)==boardDirection(leg.direction)
        } ?: return@map leg
        val delay = row.delay.departureSeconds
        if (delay == null && !row.delay.cancelled && row.platform == null) return@map leg
        val start = if (delay == null) leg.startTime else leg.plannedStartTime + delay*1000L
        // Keep a real arrival forecast. When it conflicts with the new departure, a new route
        // answer is needed. A carried arrival, on the other hand, can be recomputed from the delay.
        val carry = delay != null && leg.arrivalSource in setOf(TimeSource.PLANNED,TimeSource.CARRIED)
        val end = if (carry) leg.plannedEndTime + maxOf(0,delay ?: 0)*1000L else leg.endTime
        if (end < start && !row.delay.cancelled) return@map leg
        changed = true
        leg.copy(startTime=if(end < start) leg.startTime else start, endTime=end,
            delay=leg.delay.copy(departureSeconds=delay ?: leg.delay.departureSeconds,cancelled=row.delay.cancelled),
            departurePlatform=row.platform ?: leg.departurePlatform,
            plannedDeparturePlatform=if (row.platform != null && row.platform != leg.departurePlatform)
                leg.plannedDeparturePlatform ?: leg.departurePlatform else leg.plannedDeparturePlatform,
            realtimeDeparture=delay != null || leg.realtimeDeparture,
            departureSource=if(delay != null) TimeSource.PROGNOSIS else leg.departureSource,
            arrivalSource=if(carry && end>leg.plannedEndTime) TimeSource.CARRIED else if(carry) TimeSource.PLANNED else leg.arrivalSource,
            // Both events share one receipt timestamp in the model. A boarding-only observation
            // must not freshen a pre-existing arrival prognosis.
            realtimeReceivedAt=if(delay != null && leg.arrivalSource != TimeSource.PROGNOSIS) received else leg.realtimeReceivedAt,
            notices=(leg.notices + row.notices).distinct())
    }
    return if (changed) copy(segments=updated) else this
}

/** A known direct pattern still needs today's first boarding on the real station board.
 * On a changed pattern, cancellations or ambiguous/missing matches the normal planner takes over. */
internal suspend fun TransitRepository.verifyDirectDepartures(query:JourneyQuery,scheduled:List<Trip>,stop:Location):List<Trip>? {
    val board=withContext(FreshTransitResponse + transitRequestBudget(1)) { departures(stop,30) }
    val now=System.currentTimeMillis()
    val rows=scheduled.map { trip ->
        val leg=trip.segments.single()
        val input=trip.copy(segments=listOf(leg.copy(origin=stop)))
        val update=input.withBoardDepartures(board,now)
        if (update === input) trip else update.copy(segments=listOf(update.segments.single().copy(origin=query.origin)))
    }
    if (rows.any { it.cancelled }) return null
    val next=rows.indices.filter { index ->
        maxOf(rows[index].firstBoardingTime(),scheduled[index].segments.single().plannedStartTime) >= now
    }.take(INITIAL_FUTURE_CONNECTIONS)
    return rows.takeIf { next.size==INITIAL_FUTURE_CONNECTIONS && next.all { rows[it] !== scheduled[it] } }
}
