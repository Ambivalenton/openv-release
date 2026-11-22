// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Bounded overview lookup, independent of the three-hour results paginator. */
class OverviewRepository(
    private val provider: (AppSettings) -> TransitRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Key(
        val origin: Location,
        val target: Location,
        val products: Set<LineType>,
        val timetable: String,
        val buffer: Float,
        val preferences: OverviewRoutingPreferences,
        val walkingSpeed:WalkingSpeed,
        val ignoreWalking:Boolean,
        val transportFilter:Set<FavoriteTransport>?,
        /** Forced via points of the network plan; a via request never shares the direct cache. */
        val waypoints:List<String>,
        val dwell:Map<String,Float>,
    )
    // Keep alternatives, not only yesterday's winner: every cache hit rechecks the new time floor.
    private data class Cached(val trips: List<Trip>, val capturedAt: Long)
    private val cache = object : LinkedHashMap<Key, Cached>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Cached>?) = size > 64
    }
    private val mutex = Mutex()
    private val requests = Semaphore(3)

    /** [accept] drops options the person cannot reach (network plan start up to 500 m away). */
    suspend fun next(query: JourneyQuery, settings: AppSettings, force: Boolean = false, accept: (Trip) -> Boolean = { true }): Trip? {
        require(query.timeMode == TimeMode.DEPARTURE)
        require(query.products.isNotEmpty()) { tr("Bitte mindestens ein Verkehrsmittel auswählen.") }
        val preferences = settings.overview.routingPreferences
        val key = Key(query.origin, query.destination, query.products, TimetableProfile.identity(settings), settings.overview.bufferMinutes, preferences,settings.walkingSpeed,settings.ignoreWalking,query.transportFilter,
            query.waypoints.map(::waypointIdentity),query.waypointDwellMinutes)
        if (!force) {
            val cached = mutex.withLock { cache[key] }
                ?.takeIf { now() - it.capturedAt in 0 until 60_000 }
            if (cached != null) SmartRouteSelector.select(cached.trips.filter(accept), query.withWalking(settings), preferences)?.let { return it }
        }
        return requests.withPermit {
            val engine = provider(settings)
            val request = TimetableProfile.requestTime(query, settings)
            val candidates = LinkedHashMap<String, Trip>()
            var cursor: String? = null
            val visited = mutableSetOf<String?>()
            suspend fun result(): Trip? {
                currentCoroutineContext().ensureActive()
                val trips = candidates.values.toList()
                val chosen = SmartRouteSelector.select(trips.filter(accept), query.withWalking(settings), preferences)
                if (chosen != null) mutex.withLock { cache[key] = Cached(trips, now()) }
                return chosen
            }
            // Maximum two native pages, even when every preference is enabled.
            repeat(2) {
                currentCoroutineContext().ensureActive()
                if (!visited.add(cursor)) return@withPermit result()
                val page = try { if (force) kotlinx.coroutines.withContext(FreshTransitResponse) { engine.journeyPage(request, cursor) }
                    else engine.journeyPage(request, cursor) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    // An optional second-page outage cannot erase a valid first-page trip.
                    return@withPermit result() ?: throw e
                }
                page.shown.take(256).forEach { candidates[it.id] = it }
                val best = SmartRouteSelector.select(candidates.values.toList().filter(accept), query.withWalking(settings), preferences)
                if (best != null && !preferences.enabled) return@withPermit result()
                val next = page.nextCursor
                if (next.isNullOrBlank() || next in visited) return@withPermit result()
                cursor = next
            }
            result()
        }
    }
}
