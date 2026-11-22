// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Planned service identity survives changing provider response IDs, delays and walk durations.
 * Delegates to the shared connection identity, so paging, caches and the chart agree. */
fun historicalIdentity(trip:Trip):String = connectionIdentity(trip)

/** Demand-sized departure probes replace the full three-hour window. No artificial paging delay.
 * Departure queries retain earlier journeys whose arrival overlaps the visible first departure.
 */
suspend fun TransitRepository.fetchHistoricalConnections(query:JourneyQuery,before:Long,limit:Int=3,
    excluded:Set<String> = emptySet()):List<Trip> {
    val wanted=limit.coerceIn(1,6)
    val collected=linkedMapOf<String,Trip>()
    fun rows()=collected.values.sortedByDescending {it.startTime}.take(wanted).sortedBy {it.startTime}
    val horizons=longArrayOf(10,30,90,180,360,720,1440)
    var horizon=0;var anchor=before-horizons[0]*60_000L;var cursor:String?=null
    val seen=mutableSetOf<String>()
    val complete=withTimeoutOrNull(12_000) {
        repeat(12) {
            currentCoroutineContext().ensureActive()
            val page=journeyPage(query.copy(time=anchor,timeMode=TimeMode.DEPARTURE,pageSize=wanted+2),cursor)
            val valid=page.shown.filter {trip ->trip.segments.isNotEmpty() && trip.startTime<before &&
                trip.startTime>=before-1440*60_000L && trip.segments.all {
                    it.isWalk || (it.lineType in query.products && (LineType.ICE in query.products || !it.isLongDistance()))
                } && historicalIdentity(trip) !in excluded}
            valid.forEach {trip->collected[historicalIdentity(trip)]=trip}
            val boundary=page.trips.isEmpty() || page.trips.any {it.startTime>=before}
            if(boundary) {
                if(collected.size>=wanted || horizon==horizons.lastIndex) return@withTimeoutOrNull true
                horizon++;anchor=before-horizons[horizon]*60_000L;cursor=null;seen.clear()
            } else {
                val next=page.nextCursor
                if(!next.isNullOrBlank()) {
                    if(!seen.add(next)) return@withTimeoutOrNull collected.isNotEmpty()
                    cursor=next
                } else {
                    val starts=page.trips.map {it.startTime}.distinct().sorted()
                    val gaps=starts.zipWithNext {a,b->b-a}.filter {it>0}.sorted()
                    val cadence=gaps.getOrNull(gaps.size/2) ?: 60_000L
                    // Dense services: seek close to the requested edge instead of traversing a long queue.
                    val near=before-maxOf(60_000L,cadence.coerceAtMost(30*60_000L)*(wanted+2))
                    val latest=starts.lastOrNull() ?: anchor
                    val advanced=maxOf(near,Math.floorDiv(latest,60_000L)*60_000L+60_000L).coerceAtMost(before)
                    if(advanced<=anchor) return@withTimeoutOrNull collected.isNotEmpty()
                    anchor=advanced;cursor=null
                }
            }
        }
        collected.isNotEmpty()
    }
    // A deadline without any result is a retryable failure, not the end of earlier connections.
    if(complete==null && collected.isEmpty())
        throw TransitException(tr("Frühere Verbindungen konnten nicht rechtzeitig geladen werden."), FailureKind.TIMEOUT)
    return rows()
}
