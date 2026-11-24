// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import kotlinx.coroutines.*

data class AlternativeSearchResult(val query:JourneyQuery,val trip:Trip?,val requests:Int,val incomplete:Boolean,val board:List<Departure> = emptyList(),
    /** Build 117: the reachable connections found, earliest arrival first (at most three). */
    val choices:List<Trip> = emptyList())

/** Search current and delayed earlier departures without loading another six-hour window.
 * A departure board supplies targeted planned times; bounded lookback probes fill provider gaps. */
suspend fun TransitRepository.findReachableAlternative(session:PinnedJourneySession,now:Long,presence:DevicePresence?,
    query:JourneyQuery=pinnedRecoveryQuery(session,now,presence),pauseMillis:Long=600,
    maxRequests:Int=6):AlternativeSearchResult {
    val budget=maxRequests.coerceIn(1,8)
    val found=linkedMapOf<String,Trip>();val anchors=linkedSetOf<Long>();var calls=0;var failures=0;var board=emptyList<Departure>()
    suspend fun request(anchor:Long,cursor:String?=null):JourneyPage? {
        if(calls>=budget) return null
        if(calls>0) delay(pauseMillis.coerceAtLeast(0))
        calls++
        return try {
            journeyPage(query.copy(time=anchor),cursor).also {page->page.trips.forEach {candidate->
                reachableAssistantAlternative(session,query,candidate,now,presence)?.takeUnless {samePinnedJourney(it,session.trip)}
                    ?.let {found[historicalIdentity(it)]=it}
            }}
        } catch(e:CancellationException) {throw e} catch(_:Exception) {failures++;null}
    }
    val finished=withTimeoutOrNull(18_000) {
        anchors+=query.time
        request(query.time)
        val delayed=if(calls<budget-1 && query.origin.kind==LocationKind.STOP && !query.origin.id.isNullOrBlank()) try {
            if(calls>0) delay(pauseMillis.coerceAtLeast(0));calls++
            departures(query.origin,30).also {board=it}.filter {row->!row.delay.cancelled && row.lineType in query.products &&
                row.plannedTime<query.time && query.time-row.plannedTime<=6*60*60_000L && row.time>=maxOf(now,query.time)}
                .sortedBy {it.time}.map {(it.plannedTime-60_000).coerceAtLeast(0)}.distinct().take(2)
        } catch(e:CancellationException) {throw e} catch(_:Exception) {failures++;emptyList()} else emptyList()
        val times=(delayed+listOf(query.time-15*60_000,query.time-45*60_000,query.time-90*60_000)).distinct()
        for(time in times) {
            if(calls>=budget) break
            if(anchors.add(time)) {
                val page=request(time)
                // A native cursor can expose another simultaneous delayed service on the same page.
                if(!page?.nextCursor.isNullOrBlank() && calls<budget && delayed.contains(time)) request(time,page!!.nextCursor)
            }
        }
        true
    }==true
    val ranked=found.values.sortedWith(compareBy<Trip> {it.effectiveArrival()}.thenBy {it.transfers}
        .thenBy {it.segments.filter {leg->leg.isWalk}.sumOf {leg->leg.endTime-leg.startTime}})
    return AlternativeSearchResult(query,ranked.firstOrNull(),calls,!finished || failures>0,board,ranked.take(3))
}
