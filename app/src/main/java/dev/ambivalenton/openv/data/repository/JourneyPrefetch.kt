// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * While the search button forms, the first connection on each side of the requested time
 * is searched in the background (the next one and the one before it), so that a tap on "Suchen"
 * opens the chart with two bars at once. Everything after that tap loads exactly as before.
 *
 * A pre-search is only a head start, never a second truth: it is used for the very same request
 * (the matching rules of the journey cache, including the 200 m tolerance of a GPS start) and only
 * while it is young; otherwise the search runs as if there had been none.
 */
const val PREFETCH_MAX_AGE_MILLIS = 90_000L
/** Pause after the last change of destination, time or mode before a pre-search starts. */
const val PREFETCH_SETTLE_MILLIS = 250L
/** Maximum requests for prefetching earlier and later journeys together. */
const val PREFETCH_REQUEST_BUDGET = 8

data class PrefetchedJourneys(
    val query: JourneyQuery,
    val provider: String,
    val deviceOrigin: Boolean,
    val currentDeparture: Boolean,
    val trips: List<Trip>,
    val fetchedAt: Long,
) {
    fun fresh(now: Long) = trips.isNotEmpty() && now - fetchedAt in 0..PREFETCH_MAX_AGE_MILLIS

    /** The bars as the chart shows them (unique IDs, like a pager snapshot). */
    fun window() = JourneyWindow(withUniqueTripIds(trips), trips.minOfOrNull { it.startTime } ?: query.time,
        trips.maxOfOrNull { it.endTime } ?: query.time, coveredFraction = 1f, pages = 1, finished = true, complete = true)

    /** Same request as [candidate] under the journey cache's rules, and still young. */
    fun matches(candidate: JourneyQuery, settings: AppSettings, isDevice: Boolean, isNow: Boolean, now: Long): Boolean =
        fresh(now) && CachedJourneySearch("prefetch", query, provider, deviceOrigin, currentDeparture, fetchedAt, fetchedAt,
            trips, trips.minOf { it.startTime }, trips.maxOf { it.endTime }, 1f, 1, finished = false, complete = false)
            .matches(candidate, settings, isDevice, isNow, now)
}

/**
 * The next two connections on the searched side (departing at/after the time, or arriving at/before it)
 * and the nearest one on the other side. A failure on the other side still leaves the first bars; a
 * failure on the searched side fails the pre-search.
 *
 * Departure search: both sides at once; the look back assumes a common urban rhythm
 * ([PREFETCH_CADENCE_HINT_MILLIS]). Arrival search (build 103): the later side continues after the
 * connections found first. Started from the arrival time itself it found the departures after that time
 * and the chart then had a gap of hours between both sides.
 */
suspend fun TransitRepository.prefetchFirstConnections(query: JourneyQuery): List<Trip> = coroutineScope {
    val searched = JourneyBatchPager(this@prefetchFirstConnections, query, initialCount = 2)
    if (query.timeMode == TimeMode.ARRIVAL) {
        val first = searched.initial().trips
        val later = try { searched.load(searched.secondaryDirection, 1).trips }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { first }
        return@coroutineScope mergeDuplicateConnections(later, query.ignoreWalking).sortedBy { it.startTime }
    }
    val otherSide = async {
        try { fetchHistoricalConnections(query, query.time, 1, cadenceHint = PREFETCH_CADENCE_HINT_MILLIS) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { emptyList() }
    }
    val first = searched.initial().trips
    mergeDuplicateConnections(first + otherSide.await(), query.ignoreWalking).sortedBy { it.startTime }
}

/** A common urban rhythm; the pre-search looks back twice as far for the one earlier connection. */
const val PREFETCH_CADENCE_HINT_MILLIS = 15 * 60_000L

/** The settings that shape a search request; a pre-search counts only while they are unchanged. */
fun AppSettings.searchRouting(): List<Any?> =
    listOf(TimetableProfile.identity(this), originMode, manualOrigin, products, walkingSpeed, ignoreWalking,
        // Every preference shapes the request (extra goals) and the shown connections.
        overview.routingPreferences.routingIdentity())
