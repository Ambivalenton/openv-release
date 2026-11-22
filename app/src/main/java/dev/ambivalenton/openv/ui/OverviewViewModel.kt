// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui

import dev.ambivalenton.openv.localization.tr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.location.CurrentPositionSource
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Network plan row (anchored mode): from the anchor to a target, possibly over transfer points.
 * [anchorIcon] lets the overview sentence fit the pair (work → home: "Feierabend …"). */
data class OverviewNetworkRow(val anchorId: String, val targetId: String, val firstEdgeIds: Set<String>,
    val candidates: List<RouteCandidate> = emptyList(), val shownKey: String? = null, val anchorIcon: FavoriteIcon? = null,
    /** The line itself when anchor and target are directly connected: its journeys tell the plan's vehicles. */
    val directEdgeId: String? = null)

data class OverviewEntry(
    val favorite: Favorite,
    val variant: Int,
    val query: JourneyQuery? = null,
    val trip: Trip? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val weather: ArrivalWeather? = null,
    val network: OverviewNetworkRow? = null,
)

/** Next departure per outgoing line of the node nearest to the person (network plan chips). */
data class NetworkCountdownChip(val edgeId: String, val targetLabel: String, val trip: Trip?, val loading: Boolean = false)
data class NetworkCountdown(val nodeId: String? = null, val anchored: Boolean = false, val chips: List<NetworkCountdownChip> = emptyList())

data class OverviewUiState(
    val entries: List<OverviewEntry> = emptyList(),
    val loading: Boolean = false,
    val ready: Boolean = false,
    val error: String? = null,
    val hiddenNearby: Int = 0,
    val updatedAt: Long = 0,
    /** Favourite within 500 m that the rows start from (null: rows start at the position). */
    val anchorId: String? = null,
    /** [error] means "no start": the overview offers the own location and a start choice instead of a retry. */
    val missingStart: Boolean = false,
)

/** The overview has no start (no manual start, no position). */
internal class MissingStartException(message: String) : Exception(message)

/** Foreground-scoped routing fan-out, with optional weather enrichment on a separate data flow. */
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModel(
    private val locations: LocationRepository,
    private val connections: OverviewRepository,
    private val positions: CurrentPositionSource,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val weather: ArrivalWeatherSource? = null,
) : ViewModel() {
    private data class WeatherInput(val id: String, val destination: Location, val arrival: Long)
    private data class WeatherBatch(val rows: List<WeatherInput>, val serial: Long = 0)
    /** [source] carries favourites, the routing part of the network (no grid coordinates: moving a
     * node must not reload) and a location override. [anchorId] reloads when the anchor changes. */
    private data class Input(val favorites: List<Favorite>, val routing: AppSettings,val hiddenCount:Int=0,val anchorId:String?=null,val source:StoredState=StoredState())

    private data class Trigger(val serial: Long = 0, val force: Boolean = false)

    private val active = MutableStateFlow(false)
    private val trigger = MutableStateFlow(Trigger())
    private val mutable = MutableStateFlow(OverviewUiState())
    val state = mutable.asStateFlow()
    private val variants = mutableMapOf<String, Int>()
    private var lastVariant = -1
    private var previousInput: Input? = null
    private var lastForcedSerial = -1L

    init {
        // A separate collector enriches already-visible journeys; toggling weather never reloads transit.
        viewModelScope.launch {
            combine(
                locations.state.map { it.settings.overview.showWeather && !it.settings.powerSaving }.distinctUntilChanged(),
                active,
                state.map { ui -> ui.entries.mapNotNull { row ->
                    row.trip?.let { WeatherInput(row.favorite.id, row.query?.destination ?: row.favorite.location, it.endTime) }
                } }.distinctUntilChanged(),
                trigger,
            ) { enabled, foreground, rows, refresh ->
                if (enabled && foreground) WeatherBatch(rows, refresh.serial) else WeatherBatch(emptyList())
            }
                .distinctUntilChanged()
                .catch { }
                .collectLatest { batch ->
                    val rows = batch.rows
                    // Keep matching hints visible during a cache refresh or another row's arrival.
                    mutable.update { ui -> ui.copy(entries = ui.entries.map { entry ->
                        val current = rows.firstOrNull { it.id == entry.favorite.id }
                        entry.copy(weather = entry.weather?.takeIf {
                            current != null && current.destination == (entry.query?.destination ?: entry.favorite.location) && it.covers(current.arrival)
                        })
                    }) }
                    coroutineScope {
                        rows.forEach { row -> launch {
                            val forecast = try {
                                withTimeoutOrNull(6_000) { weather?.atArrival(row.destination, row.arrival) }
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { null }
                            currentCoroutineContext().ensureActive()
                            mutable.update { ui -> ui.copy(entries = ui.entries.map { entry ->
                                if (entry.favorite.id == row.id && (entry.query?.destination ?: entry.favorite.location) == row.destination && entry.trip?.endTime == row.arrival)
                                    entry.copy(weather = forecast)
                                else entry
                            }) }
                        } }
                    }
                }
        }
        viewModelScope.launch {
            val input=combine(locations.state,active) {saved,foreground->saved to foreground}
                .transformLatest { (saved,foreground) ->
                    do {
                        val s=saved.settings;val clock=Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault())
                        val shown=visibleFavorites(saved.favorites,clock,saved.routingPresence(now()))
                        val routing=TimetableProfile.withTimetable(AppSettings(
                            originMode=s.originMode,manualOrigin=s.manualOrigin,manualOriginChangedAt=s.manualOriginChangedAt,favoriteRadiusMeters=s.favoriteRadiusMeters,products=s.products,walkingSpeed=s.walkingSpeed,ignoreWalking=s.ignoreWalking,
                            powerSaving=s.powerSaving,overview=OverviewSettings(bufferMinutes=s.overview.bufferMinutes,routingPreferences=s.overview.routingPreferences),
                            networkHoldAction=s.networkHoldAction), s)
                        emit(Input(shown,routing,saved.favorites.size-shown.size,saved.currentAnchor(now())?.nodeId,routingSource(saved,routing)))
                        if(!foreground)break
                        val edge=nextFavoriteChange(saved,clock)?.toEpochMilli()
                        delay((edge?.minus(now())?:60_000L).coerceIn(1,60_000L))
                    }while(currentCoroutineContext().isActive)
                }.distinctUntilChanged()
            combine(input, active, trigger) { config, foreground, refresh ->
                    Triple(config, foreground, refresh)
                }
                .catch { e ->
                    mutable.update { it.copy(ready = true, loading = false, error = errorText(e), missingStart = e is MissingStartException) }
                }
                .collectLatest { (config, foreground, refresh) ->
                    if (!foreground) {
                        mutable.update {
                            it.copy(
                                loading = false,
                                entries = it.entries.map { row -> row.copy(loading = false) },
                            )
                        }
                    } else {
                        val force = refresh.force && refresh.serial != lastForcedSerial
                        if (force) lastForcedSerial = refresh.serial
                        load(config, force)
                    }
                }
        }
    }

    fun setForeground(foreground: Boolean) {
        active.value = foreground
    }

    fun refresh(force: Boolean = true) {
        trigger.update { Trigger(it.serial + 1, force) }
    }

    private suspend fun load(input: Input, force: Boolean) {
        if(input.routing.originMode==OriginMode.AUTOMATIC && (!positions.hasPermission() || !positions.locationEnabled())) {
            // A failed write must not end the collector (and with it the app): the next input retries.
            try {locations.activateManualOrigin()}
            catch(e:CancellationException){throw e}
            catch(e:Exception){mutable.update {it.copy(loading=false,ready=true,error=errorText(e),missingStart=false)}}
            return
        }
        val previous =
            mutable.value.entries
                .takeIf { previousInput?.routing == input.routing }
                .orEmpty()
                .associateBy { it.favorite.id }
        previousInput = input
        variants.keys.retainAll(input.favorites.map { it.id }.toSet())
        if (input.favorites.isEmpty()) {
            if(force && (input.routing.originMode!=OriginMode.MANUAL || input.routing.manualOrigin?.isDevicePosition==true) && positions.hasPermission())try {
                val measured=positions.currentFresh()
                positions.lastPresence?.takeIf {it.location==measured&&it.isFresh(now())}?.let {locations.recordPresence(it)}
            }catch(e:CancellationException){throw e}catch(_:Exception){}
            mutable.value=OverviewUiState(ready=true,hiddenNearby=input.hiddenCount,updatedAt=now())
            return
        }
        mutable.value =
            OverviewUiState(
                entries =
                    input.favorites.mapNotNull {previous[it.id]}
                        .filter { row ->
                            row.trip?.startTime?.let {
                                it >=
                                    overviewDepartureTime(
                                        now(),
                                        input.routing.overview.bufferMinutes,
                                    )
                            } == true
                        }
                        .map { it.copy(loading = true, error = null) },
                ready = true,
                loading = true,
            )
        try {
            // One measurement for the entire batch. The shared provider reuses a fix for 60 s.
            val manual=input.routing.originMode==OriginMode.MANUAL && input.routing.manualOrigin?.isDevicePosition!=true
            // Time-dependent location: fallback behind GPS and a currently chosen manual start.
            val scheduledManual=if(manual) input.source.scheduledStart(now()) else null
            var positionError: Exception? = null
            val measured =
                if (!manual && positions.hasPermission())
                    try {
                        if (force) positions.currentFresh() else positions.current()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        positionError = e
                        null
                    }
                else null
            val timestamp = now()
            val presence =
                positions.lastPresence?.takeIf { it.location == measured && it.isFresh(timestamp) }
            if (presence != null) {
                try {
                    locations.recordPresence(presence)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    /* A valid route must not depend on widget presence persistence. */
                }
            }
            val scheduled=scheduledManual ?: if(!manual && measured==null) input.source.scheduledFavorite(timestamp) else null
            val visibilityPresence=if(scheduled!=null)scheduled.location.takeIf {it.hasCoordinates}?.let {DevicePresence(it,timestamp,0f)}
                else if(manual)input.routing.manualOrigin?.takeIf {it.hasCoordinates}?.let {DevicePresence(it,timestamp,0f)} else presence
            val visible =
                visibleFavorites(input.favorites,Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()),visibilityPresence)
            val hidden = input.hiddenCount + input.favorites.size - visible.size
            if (visible.isEmpty()) {
                mutable.value =
                    OverviewUiState(ready = true, hiddenNearby = hidden, updatedAt = timestamp)
                return
            }
            val s = input.routing
            val origin =
                if (manual) {
                    scheduled?.location ?: s.manualOrigin
                        ?: throw MissingStartException(
                            tr("Wähle einen Start oder nutze deinen Standort, dann zeigt die Übersicht deine Verbindungen.")
                        )
                } else measured ?: locations.state.first().presence?.takeIf {it.isFresh(timestamp)}?.location
                    ?: scheduled?.location ?: throw positionError.orMissingLocation()
            val departure = overviewDepartureTime(timestamp, s.overview.bufferMinutes)
            val clock = zonedNow(timestamp)
            // Within 500 m of a favourite the rows start there (D8); "Genauer Standort" suppresses it.
            val anchor = (if (scheduled != null && scheduled.location == origin) ProximityAnchor(scheduled.id, 0.0, timestamp).also { ProximityMemory.anchor = it }
                else input.source.currentAnchor(timestamp, origin, presence))
                ?.takeIf { it.nodeId != ProximityMemory.suppressedNodeId }
            // While a time rule highlights something, only the highlighted rows are shown (build 67).
            val activeEdges = input.source.network.activeEdges(clock)
            fun routeHighlighted(route: NetworkRoute) = route.target.rule?.matches(clock) == true ||
                route.paths.any { p -> p.edgeIds.any { e -> activeEdges.firstOrNull { it.id == e }?.rule?.matches(clock) == true } }
            // Highlighted rows and the pinned favourite; with neither,
            // all destinations. A hidden favourite stays hidden, also when it is pinned.
            val focus = s.networkHoldAction == NetworkHoldAction.OVERVIEW
            // Up to five pinned favourites (in pinning order).
            val pinnedIds = if (focus) input.source.overviewPins else emptyList()
            val pinned = pinnedIds.mapNotNull { id -> visible.firstOrNull { it.id == id } }
            val roles = if (focus) input.source.network.effectiveRoles(input.source.favorites.associateBy { it.id }) else emptyMap()
            val allRoutes = anchor?.let { input.source.networkRoutes(it.nodeId, clock) }.orEmpty()
            val routes = when {
                !focus -> onlyHighlighted(allRoutes, ::routeHighlighted)
                pinned.isNotEmpty() -> allRoutes.filter { route -> routeHighlighted(route) || pinned.any { it.id == route.target.id } }
                else -> allRoutes.filter(::routeHighlighted).ifEmpty { allRoutes }
            }
            // Reachability is judged from the real device fix, never from an override or a manual start.
            val devicePresence = presence?.takeIf { input.source.originSource() == OriginSource.DEVICE }
            // Outside every radius (or at an unconnected node): all favourites from the start. Only a
            // GPS start snaps to the nearby favourite; a manual start or an override is its own position.
            val snapped = anchor?.takeIf { input.source.originSource() == OriginSource.DEVICE || scheduled != null }
            val start = snapped?.let { a -> input.source.favorites.firstOrNull { it.id == a.nodeId }?.location } ?: origin
            fun directEntry(savedFavorite: Favorite): OverviewEntry {
                val query = savedFavorite.journeyQuery(start, s, departure)
                val cached = previous[savedFavorite.id]?.takeIf { it.query?.copy(time = query.time) == query }?.trip?.takeIf { it.startTime >= departure }
                return OverviewEntry(savedFavorite, variant(savedFavorite.id), query, trip = cached,
                    weather = previous[savedFavorite.id]?.weather?.takeIf { cached != null && it.covers(cached.endTime) })
            }
            // The pinned favourite is not reachable over the plan's lines: it gets its own direct row.
            val pinnedDirect = pinned.filter { p -> routes.isNotEmpty() && routes.none { it.target.id == p.id } && p.id != anchor?.nodeId }
            val entries =
                if (routes.isNotEmpty()) routes.mapIndexed { index, route ->
                    val favorite = route.routingFavorite()
                    val candidates = input.source.routeCandidates(route, departure, allowVia = index < OVERVIEW_MAX_VIA_TARGETS)
                    val earlier = previous[favorite.id]
                    val shownKey = earlier?.network?.shownKey
                    val cached = earlier?.trip?.takeIf { it.startTime >= departure && earlier.network?.candidates?.map { c -> c.query.copy(time = 0) } == candidates.map { c -> c.query.copy(time = 0) } }
                    OverviewEntry(favorite, variant(favorite.id), (candidates.firstOrNull { it.key == shownKey } ?: candidates.first()).query,
                        trip = cached, weather = earlier?.weather?.takeIf { cached != null && it.covers(cached.endTime) },
                        network = OverviewNetworkRow(route.anchor.id, route.target.id, route.firstEdgeIds, candidates, shownKey, route.anchor.icon,
                            route.paths.firstOrNull { it.via.isEmpty() }?.edgeIds?.singleOrNull()))
                } + pinnedDirect.map(::directEntry)
                else {
                    val candidates = visible.filterNot { snapped != null && it.id == snapped.nodeId }
                    (if (focus) overviewFocusRows(candidates, { it.rule?.matches(clock) == true }, { row -> pinned.any { it.id == row.id } },
                        { roles[it.id] != NodeRole.TRANSFER })
                    else onlyHighlighted(candidates) { it.rule?.matches(clock) == true }).map(::directEntry)
                }
            mutable.value =
                OverviewUiState(entries, loading = true, ready = true, hiddenNearby = hidden, anchorId = anchor?.nodeId.takeIf { routes.isNotEmpty() })
            fun reachable(query: JourneyQuery): (Trip) -> Boolean = { trip ->
                devicePresence == null || anchor == null || reachableAlternative(trip, query, now(), devicePresence, s.walkingSpeed) != null
            }
            fun publish(ids: Set<String>, change: (OverviewEntry) -> OverviewEntry) =
                mutable.update { state -> state.copy(entries = state.entries.map { if (it.favorite.id in ids) change(it) else it }) }
            // Share the request budget across visible favourites and alternate routes.
            val requestBudget=transitRequestBudget(24)
            // Identical saved destinations share a request, even if their labels differ.
            coroutineScope {
                entries
                    .groupBy { it.network?.candidates?.map { c -> c.query } ?: listOf(it.query) }
                    .values
                    .forEach { group ->
                        launch {
                            val ids = group.map { it.favorite.id }.toSet()
                            val row = group.first()
                            try {
                                val options = row.network?.candidates ?: listOf(RouteCandidate(emptyList(), requireNotNull(row.query)))
                                // Failures stay values: a failing async would cancel its sibling rows otherwise.
                                val outcomes = options.map { option -> async(requestBudget) {
                                    try { Result.success(connections.next(option.query, s, force, reachable(option.query))?.let { option to it }) }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { Result.failure(e) }
                                } }.awaitAll()
                                val results = outcomes.mapNotNull { it.getOrNull() }
                                if (results.isEmpty()) outcomes.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
                                val chosen = pickFastest(results, row.network?.shownKey)
                                currentCoroutineContext().ensureActive()
                                // A journey found over the direct line tells which vehicles run there (plan decoration).
                                if (chosen != null && chosen.first.viaNodeIds.isEmpty())
                                    row.network?.directEdgeId?.let { dev.ambivalenton.openv.ui.network.NetworkVehicleMemory.record(it, chosen.second) }
                                publish(ids) { entry ->
                                    entry.copy(trip = chosen?.second, query = chosen?.first?.query ?: entry.query, loading = false,
                                        network = entry.network?.copy(shownKey = chosen?.first?.key ?: entry.network.shownKey),
                                        error = if (chosen == null) tr("Zurzeit keine passende Verbindung gefunden.") else null)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: AlreadyAtDestinationException) {
                                publish(ids) { it.copy(trip = null, loading = false, error = tr("Dieses Ziel liegt bereits am gewählten Startpunkt.")) }
                            } catch (e: Exception) {
                                publish(ids) { it.copy(loading = false, error = errorText(e)) }
                            }
                        }
                    }
            }
            mutable.update { it.copy(loading = false, updatedAt = now()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutable.update { it.copy(loading = false, ready = true, error = errorText(e), missingStart = e is MissingStartException) }
        }
    }

    /** Departure chips of the network plan (search tab). Inside 500 m they reuse the overview's
     * requests through the shared 60-second cache; outside they ask at most three lines of the
     * nearest node, only while the plan is visible and never in power saving mode. */
    private val planVisible = MutableStateFlow(false)
    private val countdownState = MutableStateFlow(NetworkCountdown())
    val countdown = countdownState.asStateFlow()
    fun setPlanVisible(visible: Boolean) { planVisible.value = visible }

    init {
        viewModelScope.launch {
            combine(planVisible, locations.state.map { saved -> Triple(routingSource(saved, saved.settings), saved.routingPosition(now()), saved.presence) }.distinctUntilChanged(), trigger) { visible, source, _ -> visible to source }
                // An unreadable store is reported by AppViewModel (storageError); it must not crash here.
                .catch { countdownState.value = NetworkCountdown() }
                .collectLatest { (visible, input) ->
                    if (!visible) return@collectLatest
                    val (routing, position, presence) = input
                    val source = routing.copy(presence = presence)
                    while (currentCoroutineContext().isActive) {
                        // The chips are decoration: a failure empties them, it never ends the app.
                        try { loadCountdown(source, position) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { countdownState.value = NetworkCountdown() }
                        delay(if (source.settings.powerSaving) 5 * 60_000L else 60_000L)
                    }
                }
        }
    }

    private suspend fun loadCountdown(source: StoredState, position: Location?) {
        val timestamp = now()
        val clock = zonedNow(timestamp)
        val anchor = source.currentAnchor(timestamp, position, source.presence)?.takeIf { it.nodeId != ProximityMemory.suppressedNodeId }
        val nodeId = anchor?.nodeId ?: position?.let { p ->
            source.favorites.filter { f -> source.network.activeEdges(clock).any { it.touches(f.id) } }
                .minByOrNull { distanceMeters(p, it.location) ?: Double.MAX_VALUE }?.id
        }
        if (nodeId == null || (anchor == null && source.settings.powerSaving)) { countdownState.value = NetworkCountdown(); return }
        val routes = source.networkRoutes(nodeId, clock).take(3)
        val departure = overviewDepartureTime(timestamp, source.settings.overview.bufferMinutes)
        val rows = routes.map { route -> route to source.routeCandidates(route, departure, allowVia = false).first().query }
        val previousChips = countdownState.value.takeIf { it.nodeId == nodeId }?.chips.orEmpty()
        countdownState.value = NetworkCountdown(nodeId, anchor != null, rows.flatMap { (route, _) ->
            route.firstEdgeIds.map { edge -> previousChips.firstOrNull { it.edgeId == edge && it.targetLabel == route.target.label }?.copy(loading = true)
                ?: NetworkCountdownChip(edge, route.target.label, null, true) }
        })
        val results = coroutineScope { rows.map { (route, query) -> async {
            route to try { connections.next(query, source.settings) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        } }.awaitAll() }
        currentCoroutineContext().ensureActive()
        countdownState.value = NetworkCountdown(nodeId, anchor != null, results.flatMap { (route, trip) ->
            route.firstEdgeIds.map { NetworkCountdownChip(it, route.target.label, trip) }
        }.distinctBy { it.edgeId })
    }

    private fun Exception?.orMissingLocation(): Exception =
        MissingStartException(this?.message?.takeIf { it.isNotBlank() }
            ?: tr("Wähle einen Start oder nutze deinen Standort, dann zeigt die Übersicht deine Verbindungen."))

    private fun routingSource(saved: StoredState, routing: AppSettings) = StoredState(favorites = saved.favorites,
        network = saved.network.copy(nodes = saved.network.nodes.map { it.copy(point = it.point?.let { GridPoint(0, 0) }) }, revision = 0),
        locationOverride = saved.locationOverride, settings = routing,
        // Only while "In Übersicht anzeigen" is chosen; otherwise a pin must not reload anything.
        overviewFavoriteIds = saved.overviewPins.takeIf { routing.networkHoldAction == NetworkHoldAction.OVERVIEW }.orEmpty())

    private fun variant(id: String): Int =
        variants.getOrPut(id) {
            val options =
                (0 until OverviewSentenceGenerator.variantCount).filter { it != lastVariant }
            options[random.nextInt(options.size)].also { lastVariant = it }
        }
}
