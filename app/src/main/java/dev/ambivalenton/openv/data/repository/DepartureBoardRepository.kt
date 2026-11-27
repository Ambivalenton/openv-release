// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.localization.tr

import dev.ambivalenton.openv.data.model.*
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

fun boardLocationKey(place: Location): String =
    if (!place.id.isNullOrBlank()) "${place.provider}:${place.id}"
    else "${place.latitude}:${place.longitude}:${place.name}"

/** Independent of favorite visibility: a hidden place still participates in station selection. */
fun boardCandidates(state: StoredState): List<Location> =
    boardPlaces(state).filterNot { boardLocationKey(it) in state.boardDisabledKeys }

internal fun boardConfigurationKey(state: StoredState): String {
    val settings = state.settings
    val input =
        "${settings.originMode}|${settings.manualOrigin}|${TimetableProfile.identity(settings)}|${settings.includeLongDistance}|${settings.walkingSpeed}|${settings.ignoreWalking}|${settings.board.products.sortedBy { it.name }}|${settings.board.selectedStop}|" +
            "${activeBoardSelection(state)}|"+boardCandidates(state).joinToString {
                "${boardLocationKey(it)}:${it.latitude}:${it.longitude}"
            }
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") {
        "%02x".format(it)
    }
}

/**
 * What a new position can change on the board: the nearest saved place in automatic mode. The board
 * shows departures only; favourites and the network plan never influence it. A GPS update that keeps this choice must neither cancel nor
 * discard a board request; otherwise frequent fixes (an active journey) never let a slower
 * automatic request finish, while a manually chosen stop still loads.
 */
fun boardChoiceSignature(state: StoredState, now: Long = System.currentTimeMillis()): String {
    val fixed = activeBoardSelection(state)
    // A fix that arrived after the request started is judged at its own time, not as "future".
    val at = maxOf(now, state.presence?.capturedAt ?: now)
    val position = fixed ?: boardPosition(state, at)
    val place = fixed ?: position?.let { from ->
        boardCandidates(state).filter { it.hasCoordinates }.minWithOrNull(
            compareBy<Location> { distanceMeters(from, it) ?: Double.MAX_VALUE }.thenBy(::boardLocationKey))
    }
    return "${place?.let(::boardLocationKey)}|${position != null}"
}

/** Position for "nächstgelegene Haltestelle": the app's start position, else the last known device
 * position even if it is older or coarse (approximate location). The nearest list member of a
 * rough position is still the best answer; the board labels an older position as outdated. */
internal fun boardPosition(state: StoredState, now: Long): Location? =
    state.routingPosition(now) ?: state.routingPresence(now)?.location?.takeIf { it.hasCoordinates }

internal fun nearestBoardStop(origin: Location, stops: List<Location>): Location? =
    stops
        .filter { it.kind == LocationKind.STOP && it.hasCoordinates && !it.id.isNullOrBlank() }
        .minWithOrNull(
            compareBy<Location> { distanceMeters(origin, it) ?: Double.MAX_VALUE }
                .thenBy(::boardLocationKey)
        )

/**
 * Resolve all configured places, then choose the physically closest station, never an arbitrary API
 * result.
 */
internal suspend fun resolveBoardStops(
    candidates: List<Location>,
    provider: TransitRepository,
): List<Location> = coroutineScope {
    val slots = Semaphore(3)
    candidates
        .map { candidate ->
            async {
                slots.withPermit {
                    try {
                        // A station ID from a different provider must be resolved through the
                        // selected service.
                        val belongs =
                            candidate.provider == provider.name ||
                                (provider.name.startsWith("VRR") && candidate.provider == "VRR") ||
                                (provider.name.startsWith("VBB") && candidate.provider == "VBB")
                        val stop =
                            if (candidate.kind == LocationKind.STOP && belongs) candidate
                            else if (candidate.hasCoordinates)
                                provider
                                    .nearby(candidate.latitude!!, candidate.longitude!!, 2500)
                                    .filter { it.hasCoordinates }
                                    .minByOrNull {
                                        distanceMeters(candidate, it) ?: Double.MAX_VALUE
                                    }
                            else
                                provider.locations(candidate.name).firstOrNull {
                                    it.kind == LocationKind.STOP && it.hasCoordinates
                                }
                        if (stop?.hasCoordinates == true) stop
                        else
                            stop?.let { missing ->
                                provider.locations(missing.name).firstOrNull {
                                    it.kind == LocationKind.STOP &&
                                        it.id == missing.id &&
                                        it.hasCoordinates
                                }
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
        .mapNotNull { it.await() }
        .distinctBy(::boardLocationKey)
}

class DepartureBoardRepository(
    private val locations: LocationRepository,
    private val providerFor: (AppSettings) -> TransitRepository,
    private val paused: () -> Boolean,
    /** Build 129: planned departures of the person's stations when no live answer comes. */
    private val timetable: RegionalTimetable? = null,
) {
    constructor(locations: LocationRepository, providerFor: (AppSettings) -> TransitRepository) :
        this(locations, providerFor, { false })

    constructor(
        locations: LocationRepository,
        providers: TransitProviderFactory,
    ) : this(locations, providers::get)

    private val lock = Mutex()
    private var cachedStops: Pair<String, List<Location>>? = null
    private var cachedUntil = 0L

    suspend fun refresh(force: Boolean = false, now: Long = System.currentTimeMillis(),limit:Int=8) =
        lock.withLock {
            if (paused()) return@withLock
            val state = locations.state.first()
            val key = boardConfigurationKey(state)
            val previous = state.departureBoard
            val wanted=maxOf(limit,previous.requestedRows).coerceIn(8,30)
            var chosen: Location? = null
            if (
                !force &&
                    previous.configurationKey == key &&
                    previous.error == null && previous.requestedRows>=wanted &&
                    (previous.presenceAt == (state.presence?.capturedAt ?: 0L) ||
                        previous.choice.isNotEmpty() && previous.choice == boardChoiceSignature(state, now)) &&
                    now - previous.fetchedAt in 0..59_999
            )
                return@withLock
            try {
                val candidates = boardCandidates(state)
                require(candidates.isNotEmpty()) { tr("In der Haltestellen-Liste einen Ort hinzufügen oder aktivieren.") }
                val presence = state.routingPresence(now)
                val fixedStop = activeBoardSelection(state)
                val directionPosition=fixedStop ?: boardPosition(state, now)
                val effectiveFixed=fixedStop
                require(
                    fixedStop != null ||
                        (presence != null &&
                            presence.location.hasCoordinates &&
                            presence.accuracyMeters.isFinite())
                ) {
                    tr("Für die nächstgelegene Haltestelle bitte den Standort aktualisieren.")
                }
                // Android only permits a fresh fix while the app or the explicit refresh activity
                // is visible.
                // With an older fix, we retain the selected board and label its position as
                // outdated.
                val provider = providerFor(state.settings)
                // Resolving saved addresses/foreign IDs is independent of the visible rotation slot.
                val stopKey="${TimetableProfile.identity(state.settings)}|$candidates"
                val stops =
                    cachedStops?.takeIf { it.first == stopKey && now < cachedUntil }?.second
                        ?: resolveBoardStops(candidates, provider).also {
                            cachedStops = if (it.isEmpty()) null else stopKey to it
                            cachedUntil =
                                now + if (it.size < candidates.size) 60_000 else 30 * 60_000
                        }
                val nearest =
                    (effectiveFixed?.let { selected ->
                        stops.firstOrNull { boardLocationKey(it) == boardLocationKey(selected) }
                            ?: nearestBoardStop(selected, stops)
                    } ?: directionPosition?.let { nearestBoardStop(it, stops) })
                        ?: throw TransitException(
                            tr("Keine der gespeicherten Haltestellen konnte aufgelöst werden.")
                        )
                chosen = nearest
                // Departures only: no connections, no favourite pairs and no network plan nodes.
                val rows =
                    provider.departures(nearest,wanted)
                        .filter {
                            it.time >= now - 60_000 &&
                                it.lineType in state.settings.board.products &&
                                (state.settings.includeLongDistance ||
                                    !isLongDistance(it.lineType, it.lineName))
                        }
                        .sortedBy { it.time }
                        .distinctBy { it.id }
                        .take(30)
                // Do not publish a response to a list/provider that was changed during the request.
                val latest = locations.state.first()
                if (key == boardConfigurationKey(latest) && boardChoiceSignature(latest, now) == boardChoiceSignature(state, now))
                    locations.saveDepartureBoard(
                        DepartureBoardState(nearest, rows, now, state.presence?.capturedAt ?: 0L, key,requestedRows=wanted,choice=boardChoiceSignature(state, now))
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val latest = locations.state.first()
                if (key == boardConfigurationKey(latest)) {
                    val retained =
                        if (previous.configurationKey == key) previous
                        else DepartureBoardState(configurationKey = key)
                    // Build 129: the last live rows that are still ahead, then the local timetable.
                    val stop = chosen ?: retained.stop ?: activeBoardSelection(state)
                    val planned = stop?.let { place ->
                        try { timetable?.departures(place, now - 60_000, wanted, state.settings.board.products) }
                        catch (c: CancellationException) { throw c } catch (_: Exception) { null }
                    }.orEmpty().filter { state.settings.includeLongDistance || !isLongDistance(it.lineType, it.lineName) }
                    val live = retained.departures.takeIf { retained.stop?.let(::boardLocationKey) == stop?.let(::boardLocationKey) }.orEmpty()
                    locations.saveDepartureBoard(
                        retained.copy(
                            stop = stop ?: retained.stop,
                            departures = if (planned.isEmpty()) retained.departures else mergeScheduledDepartures(live, planned, now),
                            scheduled = planned.isNotEmpty(),
                            error = e.message ?: tr("Abfahrten konnten nicht geladen werden."),
                            errorKind = transitFailure(e).kind.name,
                        )
                    )
                }
            }
        }
}
