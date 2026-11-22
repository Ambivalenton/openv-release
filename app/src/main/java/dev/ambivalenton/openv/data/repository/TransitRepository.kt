// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.localization.tr

import dev.ambivalenton.openv.data.model.*
import java.io.IOException
import kotlin.math.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last

/** [kind] says why (H05); [retryAt] is the earliest sensible retry after a pause the service asked for. */
class TransitException(
    message: String,
    val kind: FailureKind = FailureKind.OTHER,
    val retryAt: Long? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

class AlreadyAtDestinationException : IOException(tr("Start und Ziel liegen am selben Ort."))

/**
 * The provider result and its native pagination reference, when supported. [hidden] names
 * connections that a comparable, clearly better one makes superfluous ([JourneyRanking]). They stay
 * in [trips] as pagination anchors; lists and choices use [shown].
 */
data class JourneyPage(val trips: List<Trip>, val nextCursor: String? = null, val hidden: Set<String> = emptySet()) {
    val shown: List<Trip> get() = if (hidden.isEmpty()) trips else trips.filter { it.id !in hidden }
}

data class JourneyWindow(
    val trips: List<Trip> = emptyList(),
    val from: Long,
    val until: Long,
    val coveredFraction: Float = 0f,
    val pages: Int = 0,
    val finished: Boolean = false,
    val complete: Boolean = false,
    val problem: String? = null,
)

data class PagingPolicy(val maxRequests: Int = dev.ambivalenton.openv.data.model.TimetableProfile.pagingRequests, val pauseMillis: Long = 1000)

/** Providers expose one page; the shared algorithm always searches a full six-hour window. */
interface TransitRepository {
    val name: String

    suspend fun locations(query: String): List<Location>

    /**
     * Address fallback of the place search: only asked when the fast sources (stops, Photon) found
     * nothing usable in time. Providers whose [locations] already return addresses keep the default.
     */
    suspend fun addresses(query: String): List<Location> = emptyList()

    suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int = 500): List<Location>

    suspend fun journeyPage(query: JourneyQuery, cursor: String? = null): JourneyPage

    /** Whether [JourneyQuery.routeFocus] changes this provider's answer (EFA `routeType`). */
    val supportsRouteFocus: Boolean get() = false

    /** Fast seed for a transactional route editor; background windows remain independently paged. */
    suspend fun firstJourney(query:JourneyQuery):Trip? = journeyPage(query).shown
        .filter {validWaypointLeg(it,query) && it.matchesTransportFilter(query.transportFilter) && if(query.timeMode==TimeMode.DEPARTURE) it.queryStart(query.ignoreWalking)>=query.time else it.queryEnd(query.ignoreWalking)<=query.time}
        .let {routes->if(query.timeMode==TimeMode.DEPARTURE) routes.minByOrNull {it.endTime} else routes.maxByOrNull {it.startTime}}


    suspend fun departures(stop: Location): List<Departure>

    /** Bounded demand: callers request 8, 16, 24 and finally at most 30 services. */
    suspend fun departures(stop: Location, limit: Int): List<Departure> =
        departures(stop).take(limit.coerceIn(1, 30))

    suspend fun refresh(trip: Trip): Trip

    fun journeyWindow(
        query: JourneyQuery,
        policy: PagingPolicy = PagingPolicy(),
    ): Flow<JourneyWindow> = fetchJourneyWindow(this, query, policy)

    suspend fun journeys(query: JourneyQuery): List<Trip> =
        journeyWindow(query).last().let {
            if (!it.complete)
                throw TransitException(
                    it.problem ?: tr("Der Zeitraum wurde nicht vollständig geladen.")
                )
            it.trips
        }

    suspend fun resolveStop(place: Location): Location {
        if (place.kind == LocationKind.STOP && place.id != null) return place
        if (!place.hasCoordinates) throw TransitException(tr("Für diesen Ort fehlen Koordinaten."))
        return nearby(place.latitude!!, place.longitude!!, 2500).minByOrNull {
            distanceMeters(place.latitude, place.longitude, it.latitude!!, it.longitude!!)
        } ?: throw TransitException(tr("Im Umkreis von 2,5 km wurde keine Haltestelle gefunden."))
    }
}

/**
 * Native cursors avoid losing simultaneous departures. EFA uses an overlapping minute boundary.
 * Empty windows are explored, duplicates collapse by planned identity, stalled/cyclic cursors and
 * rate limits never result in a false 'complete' state. Arrival searches cover the six hours
 * before the requested arrival deadline, preserving its meaning.
 */
internal fun fetchJourneyWindow(
    provider: TransitRepository,
    query: JourneyQuery,
    policy: PagingPolicy,
): Flow<JourneyWindow> = flow {
    val duration = JOURNEY_WINDOW_MILLIS
    val forward = query.timeMode == TimeMode.DEPARTURE
    val from = if (forward) query.time else query.time - duration
    val until = if (forward) query.time + duration else query.time
    val collected = linkedMapOf<String, Trip>()
    val seenCursors = mutableSetOf<String>()
    val seenTrips = mutableSetOf<String>()
    var furthestSeen = query.time
    var frontier = query.time
    var cursor: String? = null
    var pages = 0
    var fraction = 0f
    var stalled = 0
    fun snapshot(finished: Boolean, complete: Boolean, problem: String? = null) =
        JourneyWindow(
            collected.values.sortedBy { it.startTime },
            from,
            until,
            fraction,
            pages,
            finished,
            complete,
            problem,
        )
    emit(snapshot(false, false))
    try {
        require(query.products.isNotEmpty()) { tr("Mindestens ein Verkehrsmittel auswählen.") }
        repeat(policy.maxRequests.coerceIn(1, 256)) { request ->
            currentCoroutineContext().ensureActive()
            if (request > 0) delay(policy.pauseMillis.coerceAtLeast(0))
            val page = provider.journeyPage(query.copy(time = frontier), cursor)
            pages++
            val previousSize = seenTrips.size
            page.trips.forEach { seenTrips += it.id }
            // Hidden connections still move the frontier, but never become bars.
            page.shown.forEach { trip ->
                val anchor = if (forward) trip.queryStart(query.ignoreWalking) else trip.queryEnd(query.ignoreWalking)
                val allowed =
                    trip.segments.all { segment ->
                        segment.isWalk ||
                            (segment.lineType in query.products &&
                                (LineType.ICE in query.products || !segment.isLongDistance()))
                    }
                if (allowed && trip.matchesTransportFilter(query.transportFilter) && trip.hasUsableTransfers(query) && anchor in from..until) collected[trip.id] = trip
            }
            val positions =
                page.trips.map { trip ->
                    if (forward) trip.segments.minOfOrNull { it.plannedStartTime } ?: trip.startTime
                    else trip.segments.maxOfOrNull { it.plannedEndTime } ?: trip.endTime
                }
            val furthest = if (forward) positions.maxOrNull() else positions.minOrNull()
            val nativePaging = cursor != null || !page.nextCursor.isNullOrBlank()
            val crossed = furthest != null && if (forward) furthest >= until else furthest <= from
            val atBoundary = frontier == if (forward) until else from
            if (crossed && (nativePaging || atBoundary)) {
                fraction = 1f
                emit(snapshot(true, true))
                return@flow
            }
            val advanced =
                furthest != null &&
                    if (forward) furthest > furthestSeen else furthest < furthestSeen
            val stalePage =
                furthest != null && (if (forward) furthest < frontier else furthest > frontier)
            stalled =
                if (
                    !advanced &&
                        seenTrips.size == previousSize &&
                        page.trips.isNotEmpty() &&
                        (nativePaging || stalePage)
                )
                    stalled + 1
                else 0
            if (advanced) furthestSeen = furthest
            if (stalled >= 4) {
                emit(
                    snapshot(
                        true,
                        false,
                        tr("Der Anbieter liefert keine weiteren Verbindungen. Bitte erneut laden."),
                    )
                )
                return@flow
            }
            // An optimized first page can contain a far-future express service. Without a
            // cursor this does not prove that the intervening local connections were searched.
            val progressTime = if (nativePaging) furthest ?: frontier else frontier
            val progress =
                if (forward) (progressTime - from).toDouble() / duration
                else (until - progressTime).toDouble() / duration
            fraction = maxOf(fraction, progress.toFloat().coerceIn(0f, .999f))
            val next = page.nextCursor
            if (!next.isNullOrBlank()) {
                if (!seenCursors.add(next)) {
                    emit(
                        snapshot(
                            true,
                            false,
                            tr("Weitere Verbindungen konnten nicht geladen werden. Bitte erneut versuchen."),
                        )
                    )
                    return@flow
                }
                cursor = next
            } else {
                cursor = null
                // Request the last minute again, so departures sharing its timestamp are retained.
                val overlap =
                    furthest?.let {
                        if (forward) Math.floorDiv(it, 60_000) * 60_000
                        else Math.floorDiv(it + 59_999, 60_000) * 60_000
                    }
                frontier =
                    when {
                        page.trips.isEmpty() -> {
                            // Query the exact boundary before claiming completion. A service may
                            // start there even when every earlier interval returned no journeys.
                            val boundary = if (forward) until else from
                            if (frontier == boundary) {
                                fraction = 1f
                                emit(snapshot(true, true))
                                return@flow
                            }
                            val nextTime = frontier + (if (forward) 15 else -15) * 60_000L
                            if (forward) minOf(nextTime, boundary) else maxOf(nextTime, boundary)
                        }
                        overlap != null &&
                            (if (forward) overlap > frontier else overlap < frontier) ->
                            if (forward) minOf(overlap, frontier + 15 * 60_000L)
                            else maxOf(overlap, frontier - 15 * 60_000L)
                        else -> frontier + (if (forward) 1 else -1) * 60_000L
                    }
                val explored = if (forward) frontier >= until else frontier <= from
                if (explored) {
                    frontier = if (forward) until else from
                }
            }
            emit(snapshot(false, false))
        }
        emit(
            snapshot(
                true,
                false,
                tr("Der Sechs-Stunden-Zeitraum ist noch nicht vollständig. Bitte erneut laden."),
            )
        )
    } catch (e: AlreadyAtDestinationException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emit(
            snapshot(
                true,
                false,
                if (pages > 0)
                    tr("Weitere Verbindungen konnten nicht geladen werden. %1\$s", e.message.orEmpty())
                else e.message ?: tr("Verbindungen konnten nicht geladen werden."),
            )
        )
    }
}

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 6_371_000.0 * 2 * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1 - a).coerceIn(0.0, 1.0)))
}
