// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui

import dev.ambivalenton.openv.ui.theme.withSessionTheme

import dev.ambivalenton.openv.localization.tr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.preferences.UserPreferencesRepository
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.location.DeviceLocationProvider
import dev.ambivalenton.openv.widget.WidgetUpdater
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class LoadState<T>(
    val value: T? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long = 0,
)

data class StationBoard(
    val key: String,
    val title: String,
    val place: Location,
    val stop: Location? = null,
    val rows: LoadState<List<Departure>> = LoadState(),
    val requestedLimit: Int = 8,
    val canLoadMore: Boolean = false,
)

data class AppUiState(
    val store: StoredState = StoredState(),
    val ready: Boolean = false,
    val storageError: String? = null,
    val position: LoadState<Location> = LoadState(),
    val query: JourneyQuery? = null,
    val trips: LoadState<List<Trip>> = LoadState(),
    val journeyWindow: JourneyWindow? = null,
    val regionalResults: Pair<JourneyQuery,List<Trip>>? = null,
    val regionalBoardAt: Long? = null,
    val cachedSearches:List<CachedJourneySearch> = emptyList(),
    val staleTripIds:Set<String> = emptySet(),
    val activeCacheId:String? = null,
    val historicalTrips:List<Trip> = emptyList(),
    val historicalQuery:JourneyQuery? = null,
    val historicalBatches:Int = 0,
    val historyLoading:Boolean = false,
    val historyError:String? = null,
    val canLoadPast:Boolean = true,
    val canLoadFuture:Boolean = true,
    // Per-direction paging state: one failing or exhausted direction never blocks the other.
    val pastLoading:Boolean = false,
    val futureLoading:Boolean = false,
    val pastError:String? = null,
    val futureError:String? = null,
    val nearby: LoadState<List<Location>> = LoadState(),
    val boards: Map<String, StationBoard> = emptyMap(),
    val sameLocationMessage: Int? = null,
    val isListViewVisible: Boolean = false,
    val selectedTripId: String? = null,
    /** K01: the last refresh of shown bars failed; the bars keep their confirmed stand. */
    val refreshFailure: TransitFailure? = null,
    /** K01/M15: when the last refresh of the shown bars ended (success or failure). */
    val refreshAttemptAt: Long? = null,
    /** H05: classified cause of [trips].error, for message and next step. */
    val searchFailure: TransitFailure? = null,
    /** 2.2: the search runs "from now"; a departed boarding then removes a journey. */
    val searchFromNow: Boolean = false,
) {
    /** One authoritative, duplicate-free list with unique IDs for chart, list and pager keys. */
    val showingRegionalPlan get() = trips.value.isNullOrEmpty() && regionalResults?.first == query && regionalResults?.second?.isNotEmpty() == true
    val allTrips:List<Trip> get()=mergeDuplicateConnections((if (showingRegionalPlan) regionalResults?.second.orEmpty() else trips.value.orEmpty())+historicalTrips.takeIf {historicalQuery==query}.orEmpty(),store.settings.ignoreWalking)
        .sortedBy {it.startTime}
}

/**
 * UI state is immutable. Every long operation has cancellation and retained stale-data feedback.
 */
class AppViewModel(
    private val locations: LocationRepository,
    private val device: DeviceLocationProvider,
    private val providers: TransitProviderFactory,
    private val geocoding: GeocodingRepository,
    private val widgetUpdater: WidgetUpdater,
    private val preferences: UserPreferencesRepository,
    private val boardRepository: DepartureBoardRepository =
        DepartureBoardRepository(locations, providers),
    private val journeyCache:JourneySearchCache = JourneySearchCache(),
    private val stopCatalog:StaticStopCatalog? = null,
    private val personalDesigns:PersonalDesignRepository?=null,
    private val timetable:RegionalTimetable?=null,
    private val regularRoutes:RegularRouteMemory?=null,
) : ViewModel() {
    private val mutable = MutableStateFlow(AppUiState())
    val state = mutable.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events =
        messages.receiveAsFlow().shareIn(viewModelScope, SharingStarted.WhileSubscribed(0), 0)
    private var liveFocus = false
    private var searchJob: Job? = null
    private var pastJob:Job? = null
    private var futureJob:Job? = null
    private var batchPager:JourneyBatchPager?=null
    /** K01: stand of the saved bars [batchPager] started from (null for a fresh search). */
    private var batchSeededStand:Long?=null
    /** The one pre-search of the search screen (see [prefetchSearch]). */
    private var prefetch:JourneyPrefetch?=null
    private var historyGeneration=0L
    private var cacheGeneration=0L
    private var boardCheckedAt=0L
    private var fullSearchAt=0L
    private var regularBoard:Location?=null
    private var locationJob: Job? = null
    private var sharedBoardJob: Job? = null
    private var sharedBoardRequestKey:String? = null
    private var sharedBoardRequestedRows=8
    private var nearbyJob: Job? = null
    private val boardJobs = mutableMapOf<String, Job>()
    private val visibleBoards = mutableSetOf<String>()
    private val boardSlots = kotlinx.coroutines.sync.Semaphore(3)
    private val departureCache =
        object : LinkedHashMap<String, Pair<Long, List<Departure>>>(32, .75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Pair<Long, List<Departure>>>?
            ) = size > 60
        }
    private var handledNonce: Long? = null

    private data class SearchRequest(
        val destination: Location,
        val time: Long,
        val mode: TimeMode,
        val originOverride: Location? = null,
        val cacheQuery:JourneyQuery? = null,
    )

    /** What a pre-search was started for; "now" is null, so it stays the same request while time passes. */
    private data class PrefetchRequest(val destination:Location,val time:Long?,val mode:TimeMode,val routing:List<Any?>)
    private class JourneyPrefetch(val request:PrefetchRequest,val startedAt:Long,val job:Deferred<PrefetchedJourneys?>) {
        /** The finished result; null while it runs, when it was skipped or when it failed. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun ready():PrefetchedJourneys? = if(job.isCompleted && !job.isCancelled) runCatching {job.getCompleted()}.getOrNull() else null
    }

    // Declared before init: the store collector may run during construction (immediate dispatcher).
    private val pendingFavoriteChanges=linkedMapOf<Long,(StoredState)->StoredState>()
    private var favoriteRevision=0L
    private var persistedStore:StoredState?=null
    /** Finished writes whose pending overlay ends only once an emitted store contains them. */
    private val writtenFavoriteRevisions=mutableSetOf<Long>()

    private var lastSearch: SearchRequest? = null
    private var lastFavoriteId: String? = null
    private var hasSearched = false
    private val arrivalHumor = ArrivalHumorPicker()

    init {
        viewModelScope.launch {
            journeyCache.initialize()
            journeyCache.entries.collect {entries->mutable.update {it.copy(cachedSearches=entries)}}
        }
        // K02: a store that cannot be read shows the recovery screen; the collector below waits for a
        // repaired store instead of ending, so a successful import or reload reaches this view model.
        viewModelScope.launch {
            locations.storageProblem.collect { problem ->
                if (problem != null) mutable.update { it.copy(storageError = errorText(problem), ready = true) }
            }
        }
        viewModelScope.launch {
            locations.state
                .catch { e ->
                    mutable.update { it.copy(storageError = errorText(e), ready = true) }
                }
                .collect { saved ->
                    val old = mutable.value
                    val a = old.store.settings
                    val b = saved.settings
                    val providerChanged =
                        TimetableProfile.identity(a) != TimetableProfile.identity(b)
                    val originChanged =
                        a.originMode != b.originMode || a.manualOrigin != b.manualOrigin
                    if (
                        old.ready && (providerChanged || originChanged || a.products != b.products || a.walkingSpeed!=b.walkingSpeed || a.ignoreWalking!=b.ignoreWalking)
                    ) {
                        searchJob?.cancel()
                        cancelPrefetch()
                        resetHistoricalConnections()
                        lastSearch = null
                        lastFavoriteId = null
                        mutable.update {
                            it.copy(
                                query = null,
                                trips = LoadState(),
                                journeyWindow = null,
                                sameLocationMessage = null,
                                isListViewVisible = false,
                                selectedTripId = null,
                                searchFailure = null,
                                refreshFailure = null,
                                refreshAttemptAt = null,
                            )
                        }
                    }
                    if (
                        old.ready &&
                            (providerChanged ||
                                originChanged ||
                                a.departureRadiusMeters != b.departureRadiusMeters)
                    ) {
                        nearbyJob?.cancel()
                        mutable.update { it.copy(nearby = LoadState()) }
                    }
                    if (
                        old.ready &&
                            (providerChanged ||
                                originChanged ||
                                a.includeLongDistance != b.includeLongDistance)
                    ) {
                        boardJobs.values.forEach { it.cancel() }
                        boardJobs.clear()
                        departureCache.clear()
                        mutable.update { it.copy(boards = emptyMap()) }
                    }
                    if (old.ready && originChanged) {
                        locationJob?.cancel()
                        mutable.update {
                            it.copy(
                                position =
                                    LoadState(
                                        value =
                                            b.manualOrigin.takeIf {
                                                b.originMode == OriginMode.MANUAL
                                            }
                                    )
                            )
                        }
                    }
                    persistedStore=saved
                    settleFavoriteWrites(saved)
                    mutable.update { it.copy(store = withPendingFavorites(saved), ready = true, storageError = null) }
                }
        }
    }

    /** Cancel hidden screens immediately; their retained content remains available after closing. */
    fun setLiveFocus(active: Boolean) {
        if (liveFocus == active) return
        liveFocus = active
        if (active) {
            searchJob?.cancel()
            cancelPrefetch()
            locationJob?.cancel()
            nearbyJob?.cancel()
            sharedBoardJob?.cancel()
            boardJobs.values.forEach { it.cancel() }
            boardJobs.clear()
            mutable.update { ui -> ui.copy(
                trips = ui.trips.copy(loading = false),
                position = ui.position.copy(loading = false),
                nearby = ui.nearby.copy(loading = false),
                boards = ui.boards.mapValues { (_, b) -> b.copy(rows = b.rows.copy(loading = false)) },
            ) }
        }
    }

    fun selectConnection(id: String) {
        mutable.update { it.selectConnection(id) }
    }

    fun showConnectionsList() {
        mutable.update { it.copy(isListViewVisible = true, selectedTripId = null) }
    }

    fun closeConnectionDetails() {
        mutable.update { it.copy(isListViewVisible = false, selectedTripId = null) }
    }

    /**
     * 2.10: the "Tag & Nacht" button changes to the other half of the design shown now (a random
     * start theme counts as shown) and keeps that like a normal theme choice. A random start theme
     * makes way for it for the rest of this session.
     */
    fun toggleDayNight(systemDark: Boolean) {
        val session = dev.ambivalenton.openv.ui.theme.SessionAppearance.random.value
        dev.ambivalenton.openv.ui.theme.SessionAppearance.clearRandom()
        settings { saved ->
            val shown = saved.withSessionTheme(session)
            shown.withDayNight(shown.nextDayNightIsNight(systemDark), systemDark)
        }
    }

    /** M10: a widget may still name a favourite or network line that was removed later. */
    suspend fun routeTargetExists(id: String): Boolean = loaded().let { s ->
        s.favorites.any { it.id == id } || s.network.edges.any { it.id == id }
    }

    /** M10: a widget without location asks the departures page for a fixed stop; consumed once. */
    val fixedStopRequest = MutableStateFlow(0L)
    fun requestFixedBoardStop() { fixedStopRequest.value = System.nanoTime() }
    fun consumeFixedStopRequest() { fixedStopRequest.value = 0L }

    fun consumeCommand(nonce: Long): Boolean {
        if (handledNonce == nonce) return false
        handledNonce = nonce
        return true
    }

    private suspend fun loaded() = locations.state.first()

    private suspend fun origin(saved: StoredState): Location {
        val clock=System.currentTimeMillis()
        if(!device.hasPermission() || !device.locationEnabled()) {
            locations.activateManualOrigin()
            // Without a device position the time-dependent location stands in (see RoutingPosition.kt).
            return loaded().let {it.scheduledStart(clock)?.location ?: it.settings.manualOrigin}
                ?: throw TransitException(tr("Bitte einen manuellen Startort auswählen."), FailureKind.MISSING_START)
        }
        if (
            saved.settings.originMode == OriginMode.MANUAL &&
                saved.settings.manualOrigin?.isDevicePosition != true
        )
            return saved.scheduledStart(clock)?.location ?: saved.settings.manualOrigin
                ?: throw TransitException(tr("Bitte einen manuellen Startort auswählen."), FailureKind.MISSING_START)
        val position =
            try {
                device.current()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publishPresence(null)
                // No GPS right now: a favourite's time window may know where the person is.
                saved.scheduledFavorite(clock)?.location?.let {return it}
                throw e
            }
        publishPresence(device.lastPresence?.takeIf { it.accuracyMeters.isFinite() })
        mutable.update {
            it.copy(position = LoadState(position, updatedAt = System.currentTimeMillis()))
        }
        // Within 500 m of a favourite the search starts there instead of at the raw GPS fix (D8).
        return saved.snappedStart(System.currentTimeMillis(),position,device.lastPresence)?.location ?: position
    }

    private suspend fun publishPresence(fix: DevicePresence?) {
        try {
            if (fix == null) locations.clearPresence() else locations.recordPresence(fix)
            widgetUpdater.update()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A widget/storage problem must not turn a valid GPS fix into a routing failure.
            messages.trySend(tr("Die Favoriten konnten nicht aktualisiert werden."))
        }
    }

    fun acquirePosition() {acquirePosition(false)}

    private fun acquirePosition(force:Boolean) {
        if (liveFocus) return
        if (locationJob?.isActive == true) return
        locationJob =
            viewModelScope.launch {
                mutable.update {
                    it.copy(position = it.position.copy(loading = true, error = null))
                }
                try {
                    val saved = loaded()
                    val position=if(force&&saved.settings.originMode==OriginMode.AUTOMATIC&&device.hasPermission()&&device.locationEnabled())device.currentFresh().also {publishPresence(device.lastPresence)} else origin(saved)
                    mutable.update {
                        it.copy(
                            position = LoadState(position, updatedAt = if(saved.settings.originMode==OriginMode.AUTOMATIC)device.lastPresence?.capturedAt?:System.currentTimeMillis() else System.currentTimeMillis())
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutable.update {
                        it.copy(position = it.position.copy(loading = false, error = errorText(e)))
                    }
                }
            }
    }

    fun locationPermissionGranted() {
        acquirePosition(force=true)
        if (hasSearched && mutable.value.trips.error != null) retrySearch()
    }

    private suspend fun performSearch(
        saved: StoredState,
        destination: Location,
        time: Long,
        mode: TimeMode,
        originOverride: Location? = null,
        cacheQuery:JourneyQuery? = null,
        prefetched:JourneyPrefetch? = null,
    ) {
        resetHistoricalConnections()
        val source =
            if (originOverride?.isDevicePosition == true) {if(device.hasPermission() && device.locationEnabled())device.current() else origin(saved)}
            else originOverride ?: origin(saved)
        val sourceIsDevice = device.hasPermission() && device.locationEnabled() && (
            originOverride?.isDevicePosition == true ||
                (originOverride == null &&
                    (saved.settings.originMode == OriginMode.AUTOMATIC ||
                        saved.settings.manualOrigin?.isDevicePosition == true)))
        val target =
            if (destination.isDevicePosition) {
                if (sourceIsDevice) source
                else if(device.hasPermission() && device.locationEnabled())device.current().also { device.lastPresence?.let { publishPresence(it) } } else origin(saved)
            } else destination
        val query = (cacheQuery ?: JourneyQuery(source,target,time,mode,saved.settings.products))
            .copy(origin=source,destination=target,time=time,timeMode=mode,products=cacheQuery?.transportFilter?.map {it.lineType}?.toSet() ?: saved.settings.products).withWalking(saved.settings)
        val current=isCurrentDeparture(time,mode)
        mutable.update { it.copy(query=query,searchFromNow=current) }
        // Resolve the origin first, then short-circuit *before* constructing a network provider.
        if (
            routingSameLocation(
                query.origin,
                query.destination,
                device.lastPresence ?: saved.presence,
            )
        ) {
            val index = arrivalHumor.next()
            mutable.update {
                it.copy(
                    sameLocationMessage = index,
                    journeyWindow = null,
                    trips = LoadState(emptyList(), updatedAt = System.currentTimeMillis()),
                )
            }
            return
        }
        journeyCache.initialize()
        // A pre-search of this request is awaited instead of asking the same twice; it
        // counts only for the very same query (cache rules, 200 m for a GPS start) and while young.
        val cached = journeyCache.find(query,saved.settings,sourceIsDevice,current)
        coroutineScope {
            // Read the independent GTFS timetable while the first live answer is in flight.
            // An authoritative empty answer wins even if this disk read finishes afterwards.
            var answered = false
            val generation=cacheGeneration
            val plan = if (cached == null && saved.settings.efaEndpoint.trimEnd('/') == TimetableServices.vrr.endpoint.trimEnd('/') &&
                TimetableServices.active == TimetableServices.vrr && timetable != null) async {
                val rows = timetable.direct(query)
                if (!answered && rows.isNotEmpty()) mutable.update {
                    if (it.query == query && it.trips.value.isNullOrEmpty()) it.copy(regionalResults=query to rows) else it
                }
                rows
            } else null
            if (plan != null && regularRoutes?.candidate(query,saved.settings,System.currentTimeMillis()) == true) {
                val scheduled=plan.await()
                val stop=regularRoutes.boardingStop(query,saved.settings,scheduled,System.currentTimeMillis())
                if (stop != null && generation==cacheGeneration) {
                    prefetched?.job?.cancel()
                    val confirmed=try { providers.get(saved.settings).verifyDirectDepartures(query,scheduled,stop) }
                        catch (e:CancellationException) { throw e } catch (_:Exception) { null }
                    if (confirmed != null && generation==cacheGeneration) {
                        regularBoard=stop
                        fullSearchAt=System.currentTimeMillis()
                        boardCheckedAt=fullSearchAt
                        mutable.update { it.copy(query=query,regionalResults=query to confirmed,trips=LoadState(),
                            searchFromNow=current,searchFailure=null,refreshFailure=null,activeCacheId=null,regionalBoardAt=boardCheckedAt) }
                        recordJourneySearch(query)
                        return@coroutineScope
                    }
                }
            }
            val head = if (cached != null) null else prefetched?.let { pre ->
                try { pre.job.await() } catch (e:CancellationException) { currentCoroutineContext().ensureActive(); null }
            }?.takeIf { it.matches(query,saved.settings,sourceIsDevice,current,System.currentTimeMillis()) }
            try {
                loadJourneyWindow(query,saved,sourceIsDevice,current,cached,prefetched=head)
                answered = true
                mutable.update { ui ->
                    ui.copy(regionalResults=null,regionalBoardAt=null,
                        selectedTripId=ui.selectedTripId?.takeIf { id -> ui.trips.value.orEmpty().any { it.id==id } })
                }
                val live=mutable.value.trips.value.orEmpty()
                if (plan != null && live.isNotEmpty() && batchPager?.lastFailure == null && generation==cacheGeneration) {
                    val scheduled=plan.await()
                    if (generation==cacheGeneration && scheduled.isNotEmpty())
                        regularRoutes?.observe(query,saved.settings,scheduled,live,System.currentTimeMillis())
                } else plan?.cancel()
            } catch (_:AlreadyAtDestinationException) {
                answered = true
                plan?.cancel()
                mutable.update { it.copy(sameLocationMessage=arrivalHumor.next(),journeyWindow=null,regionalResults=null,
                    trips=LoadState(emptyList(),updatedAt=System.currentTimeMillis()),staleTripIds=emptySet(),activeCacheId=null) }
            } catch (e:CancellationException) { throw e }
            catch (e:Exception) {
                if ((e as? TransitException)?.kind in setOf(FailureKind.NO_CONNECTION,FailureKind.NO_COVERAGE)) {
                    answered=true
                    plan?.cancel()
                    mutable.update { it.copy(regionalResults=null) }
                } else plan?.join()
                throw e
            }
        }
    }

    /**
     * K01: the confirmed stand of saved bars. It moves forward only once every still boardable saved
     * bar was confirmed by a provider answer; otherwise the original stand (and expiry) is kept.
     */
    private fun confirmedStand(pager:JourneyBatchPager,seededStand:Long?,now:Long):Long? =
        if(pager.unconfirmedIds(now).isEmpty()) null else seededStand

    /** K01: one way to write a window into the cache, so no path moves the stand on a failure. */
    private fun persistWindow(query:JourneyQuery,settings:AppSettings,deviceOrigin:Boolean,current:Boolean,window:JourneyWindow,
        cacheId:String?,pager:JourneyBatchPager,seededStand:Long?,immediate:Boolean):CachedJourneySearch? {
        val entry=journeyCache.put(query,settings,deviceOrigin,current,window,cacheId,
            retainSavedAt=confirmedStand(pager,seededStand,System.currentTimeMillis()),attemptFailed=pager.lastFailure!=null)
        journeyCache.persist(viewModelScope,immediate=immediate)
        return entry
    }

    /** All route entry points share the same SWR reconciliation, including waypoint refreshes. */
    private suspend fun loadJourneyWindow(query:JourneyQuery,saved:StoredState,deviceOrigin:Boolean,current:Boolean,
        cached:CachedJourneySearch?,previous:List<Trip> = cached?.trips.orEmpty(),force:Boolean=false,countSearch:Boolean=true,
        prefetched:PrefetchedJourneys?=null) {
        val generation=cacheGeneration
        val historyToken=historyGeneration
        // Pre-searched bars are fresh. They seed the pager, show at once and stay unmarked;
        // the loading plan below then runs exactly as without them.
        val pager=JourneyBatchPager(providers.get(saved.settings),query,prefetched?.trips ?: previous,seedConfirmedAt=prefetched?.fetchedAt)
        batchPager=pager
        fullSearchAt=if (countSearch && cached != null) cached.savedAt else System.currentTimeMillis()
        boardCheckedAt=cached?.lastBoardCheckAt ?: fullSearchAt
        cached?.let {journeyCache.touch(it.id)}
        var cacheId=cached?.id
        val head=prefetched?.let {pager.snapshot()}
        // K01: the stand saved bars had before this search; it stays until they are confirmed again.
        val seededStand=if(head!=null) null else cached?.savedAt ?: previous.minOfOrNull {it.fetchedAt}
        batchSeededStand=seededStand
        val refreshing=head==null && previous.isNotEmpty()
        mutable.update {it.copy(query=query,journeyWindow=head ?: cached?.window(),searchFromNow=current,
            trips=LoadState((head?.trips ?: previous).takeIf {rows->rows.isNotEmpty()},loading=true,updatedAt=prefetched?.fetchedAt ?: seededStand ?: 0),
            staleTripIds=if(head!=null) emptySet() else pager.unconfirmedIds(),
            activeCacheId=cacheId,historyLoading=false,pastLoading=false,futureLoading=false,pastError=null,futureError=null,
            canLoadPast=true,canLoadFuture=true,searchFailure=null,refreshFailure=if(refreshing) it.refreshFailure else null)}
        fun publish(window:JourneyWindow,loading:Boolean=false) {
            if(historyToken!=historyGeneration || batchPager!==pager)return
            val now=System.currentTimeMillis()
            val unconfirmed=pager.unconfirmedIds(now)
            val failure=pager.lastFailure?.let(::transitFailure)
            // K01: a failed or partial refresh keeps the saved stand; only confirmed data moves it.
            val stand=confirmedStand(pager,seededStand,now) ?: pager.confirmedAt ?: now
            // Bars on screen are never replaced by an error; the failure is reported beside them.
            val visibleError=window.problem.takeIf {window.trips.isEmpty()}
            mutable.update {it.copy(journeyWindow=window,trips=LoadState(window.trips,loading=loading,
                    error=visibleError ?: window.problem.takeIf {!refreshing},updatedAt=stand),
                staleTripIds=unconfirmed,canLoadPast=!pager.pastExhausted,canLoadFuture=!pager.futureExhausted,
                refreshFailure=if(refreshing) failure else null,
                searchFailure=window.problem?.let {failure ?: TransitFailure(FailureKind.OTHER,it)}.takeIf {!refreshing})}
            if(generation==cacheGeneration) {
                val entry=persistWindow(query,saved.settings,deviceOrigin,current,window,cacheId,pager,seededStand,immediate=!loading)
                if(entry!=null){cacheId=entry.id;mutable.update {it.copy(activeCacheId=entry.id)}}
            }
            // H08: the journey that was open before the process ended is selected again once it is back.
            pendingSelection?.let {id->if(window.trips.any {it.id==id}){pendingSelection=null;mutable.update {it.selectConnection(id)}}}
        }
        if (countSearch && !force && cached?.covers(query,System.currentTimeMillis()) == true) {
            val window = pager.snapshot()
            mutable.update { it.copy(journeyWindow=window,trips=LoadState(window.trips,updatedAt=cached.savedAt),
                refreshFailure=null,refreshAttemptAt=null) }
            pendingSelection?.let { id ->
                pendingSelection=null
                if (window.trips.any { it.id==id }) mutable.update { it.selectConnection(id) }
            }
            if (System.currentTimeMillis()-(cached.lastBoardCheckAt ?: cached.savedAt) >= boardInterval(saved.settings))
                updateBoardTimes(pager,query,generation)
        } else {
            withContext((if(force || cached!=null) FreshTransitResponse else kotlin.coroutines.EmptyCoroutineContext) + transitRequestBudget(32)) {
                // Phase 1: a small first page is usable immediately. Build 103: bars of the pre-search on the searched
                // side already are that page; asking for it again only delayed the next batches.
                if(prefetched!=null && pager.primaryCount()>0) publish(pager.snapshot(),loading=true)
                else publish(pager.initial(),loading=pager.seededFromSnapshot)
                // K01: every saved bar that is still boardable is confirmed individually or keeps its stand.
                if(pager.seededFromSnapshot) pager.refreshSeed(System.currentTimeMillis()) {window,_->publish(window,loading=true)}
                if(batchPager===pager)mutable.update {it.copy(trips=it.trips.copy(loading=false),
                    refreshAttemptAt=if(refreshing) System.currentTimeMillis() else it.refreshAttemptAt)}
                yield()
                // Phase 2 completes the context in the background: three more on the searched side, then three on
                // the other side. Each direction keeps its own loading, error and end state; more follow by scrolling.
                pager.completeWaves({batchPager===pager}) {direction,count->
                    updatePaging(direction,historyToken,loading=true,clearError=true)
                    try {publish(pager.load(direction,count))}
                    catch(e:CancellationException){throw e}
                    catch(e:Exception){updatePaging(direction,historyToken,loading=false,error=errorText(e))}
                    finally {updatePaging(direction,historyToken,loading=false)}
                }
            }
            if (pager.lastFailure == null && pager.confirmedAt != null) fullSearchAt=System.currentTimeMillis()
        }
        if (countSearch) recordJourneySearch(query)
    }

    private suspend fun recordJourneySearch(query:JourneyQuery) {
        try { preferences.recordSearch(query) }
        catch(e:CancellationException) { throw e }
        catch(_:Exception) { messages.send(tr("Verlauf konnte nicht gespeichert werden.")) }
    }

    /** H08: the journey to select again after a restored search. */
    private var pendingSelection:String?=null

    /**
     * H08: reopens the search that was shown before Android ended the process: the saved snapshot when
     * it is still valid, otherwise a new request with the same parameters ("from now" stays from now).
     */
    fun restoreResults(destination:Location,origin:Location?,time:Long,mode:TimeMode,current:Boolean,cacheId:String?,selected:String?) {
        if(liveFocus || mutable.value.query!=null || searchJob?.isActive==true) return
        pendingSelection=selected
        viewModelScope.launch {
            journeyCache.initialize()
            val entry=cacheId?.let {journeyCache.get(it)}
            if(entry!=null) openCachedSearch(entry.id)
            else search(destination,if(current) System.currentTimeMillis() else time,mode,originOverride=origin)
        }
    }

    /** Replaying a snapshot still resolves a current GPS start; moving farther than 200 m is a miss. */
    fun openCachedSearch(id:String) {
        val entry=journeyCache.get(id) ?: return
        search(entry.query.destination,if(entry.currentDeparture) System.currentTimeMillis() else entry.query.time,
            entry.query.timeMode,if(entry.deviceOrigin) Location(tr("Aktueller Standort"),isDevicePosition=true) else entry.query.origin,
            cacheQuery=entry.query)
    }
    fun saveCachedViewport(id:String,index:Int,offset:Int) {
        journeyCache.viewport(id,index,offset);journeyCache.persist(viewModelScope)
    }
    fun clearJourneyCache() {
        cacheGeneration++
        regularBoard=null
        mutable.update { it.copy(regionalBoardAt=null) }
        viewModelScope.launch { regularRoutes?.clear() }
        journeyCache.clear();journeyCache.persist(viewModelScope,immediate=true)
        mutable.update {it.copy(activeCacheId=null)}
    }

    /** A recent, accurate device fix permits synchronous cache presentation before any suspension. */
    private fun primedSearch(destination:Location,time:Long,mode:TimeMode,originOverride:Location?,cacheQuery:JourneyQuery?):CachedJourneySearch? {
        val ui=mutable.value;val settings=ui.store.settings
        val automatic=originOverride?.isDevicePosition==true || (originOverride==null &&
            (settings.originMode==OriginMode.AUTOMATIC || settings.manualOrigin?.isDevicePosition==true))
        val point=if(automatic) device.lastPresence?.takeIf {device.hasPermission() &&
            System.currentTimeMillis()-it.capturedAt in 0..59_999 && it.accuracyMeters in 0f..100f}?.location
            else originOverride ?: settings.manualOrigin
        if(point==null || destination.isDevicePosition) return null
        val query=(cacheQuery ?: JourneyQuery(point,destination,time,mode,settings.products))
            .copy(origin=point,destination=destination,time=time,timeMode=mode,products=cacheQuery?.transportFilter?.map {it.lineType}?.toSet() ?: settings.products).withWalking(settings)
        return journeyCache.find(query,settings,automatic,isCurrentDeparture(time,mode))
    }

    /** H03: when the running search was started for the same request (rapid repeated taps). */
    private var searchStartedAt=0L
    private fun sameRequest(a:SearchRequest?,b:SearchRequest):Boolean {
        if(a==null || a.destination!=b.destination || a.mode!=b.mode || a.originOverride!=b.originOverride || a.cacheQuery!=b.cacheQuery) return false
        // "Now" is a moving target: two taps a moment apart mean the same departure.
        return a.time==b.time || (isCurrentDeparture(a.time,a.mode,b.time) && isCurrentDeparture(b.time,b.mode))
    }

    fun search(
        destination: Location,
        time: Long,
        mode: TimeMode,
        originOverride: Location? = null,
        cacheQuery:JourneyQuery? = null,
    ) {
        if (liveFocus) return
        val request=SearchRequest(destination, time, mode, originOverride,cacheQuery)
        // H03: quick repeated taps join the running search instead of replacing it.
        if(sameRequest(lastSearch,request) && searchJob?.isActive==true && System.currentTimeMillis()-searchStartedAt in 0..3_000) return
        searchStartedAt=System.currentTimeMillis()
        lastFavoriteId = null
        lastSearch = request
        hasSearched = true
        val primed=primedSearch(destination,time,mode,originOverride,cacheQuery)
        // The pre-search of exactly this request becomes part of the search. When it is
        // already finished, its two bars are on screen before the start is even resolved again.
        val claimed=if(originOverride==null && cacheQuery==null) takePrefetch(destination,time,mode) else {cancelPrefetch();null}
        val early=claimed?.ready()?.takeIf {it.fresh(System.currentTimeMillis())}
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                mutable.update {
                    if(early!=null) it.copy(query=early.query.copy(time=time),journeyWindow=early.window(),sameLocationMessage=null,
                        isListViewVisible=false,selectedTripId=null,trips=LoadState(early.window().trips,loading=true,updatedAt=early.fetchedAt),
                        staleTripIds=emptySet(),activeCacheId=null,searchFailure=null,refreshFailure=null,refreshAttemptAt=null)
                    else it.copy(
                        query = primed?.query,
                        journeyWindow = primed?.window(),
                        sameLocationMessage = null,
                        isListViewVisible = false,
                        selectedTripId = null,
                        trips = LoadState(primed?.trips,loading=true,updatedAt=primed?.savedAt ?: 0),
                        staleTripIds=primed?.trips?.map {row->row.id}?.toSet().orEmpty(),activeCacheId=primed?.id,
                        searchFailure=null,refreshFailure=null,refreshAttemptAt=null,
                    )
                }
                try {
                    performSearch(loaded(), destination, time, mode, originOverride,cacheQuery,claimed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutable.update { it.copy(trips = it.trips.copy(loading=false,error=errorText(e)),searchFailure=transitFailure(e)) }
                } finally {
                    // Owned by this search now: a cancelled search does not leave it running.
                    claimed?.job?.cancel()
                }
            }
    }

    /**
     * Called while the search button forms, i.e. as soon as a routable destination is chosen.
     * Searches the next two connections and the one before them in the background, so that a tap on "Suchen"
     * opens the chart with two bars at once. At most one pre-search; the same request is not repeated
     * while it is young, a changed one replaces it. Skipped in power saving, when the start is not known
     * without side effects (GPS mode without location access) and when a saved search of the same route
     * already shows its bars at once.
     */
    fun prefetchSearch(destination:Location?,time:Long?,mode:TimeMode) {
        val settings=mutable.value.store.settings
        if(destination==null || !destination.routable || destination.isDevicePosition || liveFocus || settings.powerSaving ||
            settings.products.isEmpty()) {cancelPrefetch();return}
        val request=prefetchRequest(destination,time,mode,settings)
        val now=System.currentTimeMillis()
        val running=prefetch
        if(running!=null && running.request==request && (running.job.isActive || now-running.startedAt in 0 until PREFETCH_MAX_AGE_MILLIS))return
        running?.job?.cancel()
        prefetch=JourneyPrefetch(request,now,viewModelScope.async {
            delay(PREFETCH_SETTLE_MILLIS)
            try {runPrefetch(destination,request.time,mode)}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {null}
        })
    }

    fun cancelPrefetch() {prefetch?.job?.cancel();prefetch=null}

    /** Bars of the finished pre-search, if any (for tests and diagnostics). */
    internal val prefetchedTrips:List<Trip>? get()=prefetch?.ready()?.trips

    private fun prefetchRequest(destination:Location,time:Long?,mode:TimeMode,settings:AppSettings)=
        PrefetchRequest(destination,time?.takeUnless {isCurrentDeparture(it,mode)},mode,settings.searchRouting())

    /** Hands the pre-search of exactly this request over to the search; any other one is cancelled. */
    private fun takePrefetch(destination:Location,time:Long,mode:TimeMode):JourneyPrefetch? {
        val running=prefetch ?: return null
        prefetch=null
        if(running.request==prefetchRequest(destination,time,mode,mutable.value.store.settings))return running
        running.job.cancel()
        return null
    }

    /** The pre-search itself: the start resolved as the search resolves it, but without side effects. */
    private suspend fun runPrefetch(destination:Location,time:Long?,mode:TimeMode):PrefetchedJourneys? {
        val saved=loaded();val settings=saved.settings
        val automatic=settings.originMode==OriginMode.AUTOMATIC || settings.manualOrigin?.isDevicePosition==true
        val source=if(automatic) {
            // Without a usable device position the search itself explains what is missing.
            if(!device.hasPermission() || !device.locationEnabled())return null
            val position=device.current()
            saved.snappedStart(System.currentTimeMillis(),position,device.lastPresence)?.location ?: position
        } else saved.scheduledStart(System.currentTimeMillis())?.location ?: settings.manualOrigin ?: return null
        val query=JourneyQuery(source,destination,time ?: System.currentTimeMillis(),mode,settings.products).withWalking(settings)
        if(routingSameLocation(query.origin,query.destination,device.lastPresence ?: saved.presence))return null
        val current=isCurrentDeparture(query.time,mode)
        journeyCache.initialize()
        // A saved search of the same route already shows its bars at once.
        if(journeyCache.find(query,settings,automatic,current)!=null)return null
        if (regularRoutes?.candidate(query,settings,System.currentTimeMillis()) == true) return null
        val trips=withContext(transitRequestBudget(PREFETCH_REQUEST_BUDGET)) {providers.get(settings).prefetchFirstConnections(query)}
        return PrefetchedJourneys(query,journeyCacheProvider(settings),automatic,current,trips,System.currentTimeMillis()).takeIf {trips.isNotEmpty()}
    }

    /** Open a known route without replacing the running pinned session or silently selecting another trip. */
    fun openKnownJourney(query: JourneyQuery, trip: Trip, openDetails: Boolean = false) {
        if (liveFocus) return
        searchJob?.cancel()
        resetHistoricalConnections()
        lastSearch=SearchRequest(query.destination,query.time,query.timeMode,query.origin)
        lastFavoriteId=null
        hasSearched=true
        mutable.update { it.copy(query=query,trips=LoadState(listOf(trip),updatedAt=trip.fetchedAt),
            journeyWindow=null,staleTripIds=emptySet(),activeCacheId=null,selectedTripId=trip.id.takeIf { openDetails },isListViewVisible=false,sameLocationMessage=null,
            searchFailure=null,refreshFailure=null,refreshAttemptAt=null,searchFromNow=isCurrentDeparture(query.time,query.timeMode)) }
        if(query.waypoints.isNotEmpty()) refreshResults()
    }

    fun openKnownJourneys(query:JourneyQuery,trips:List<Trip>) {
        if(liveFocus || trips.isEmpty())return
        searchJob?.cancel();resetHistoricalConnections();lastFavoriteId=null;hasSearched=true
        lastSearch=SearchRequest(query.destination,query.time,query.timeMode,query.origin)
        mutable.update {it.copy(query=query,trips=LoadState(trips.distinctBy {t->t.id}.sortedBy {t->t.startTime},updatedAt=trips.minOf {t->t.fetchedAt}),
            journeyWindow=null,staleTripIds=emptySet(),activeCacheId=null,selectedTripId=null,isListViewVisible=false,sameLocationMessage=null,
            searchFailure=null,refreshFailure=null,refreshAttemptAt=null)}
    }

    /** Replay the route shown in history, without silently changing its saved start point. */
    fun searchHistory(history: SearchHistory) =
        search(
            history.query.destination,
            System.currentTimeMillis(),
            TimeMode.DEPARTURE,
            originOverride = history.query.origin,
        )

    suspend fun boardJourney(token:String):JourneyPreviewRequest? {
        val row=loaded().departureBoard.departures.firstOrNull {it.journey!=null && it.time>=System.currentTimeMillis()-60_000 && it.journeyToken()==token} ?: return null
        val query=row.journeyQuery ?: return null
        return JourneyPreviewRequest(query,row.journey ?: return null,JourneyDetailsContext.Comparison,
            boardJourneyGallery(loaded().departureBoard.departures,query))
    }

    // Network plan edits are shown at once and persisted in call order (same overlay as favourites),
    // so a released node or a drawn line never jumps back to an older stored state.
    fun moveNode(id:String,point:GridPoint)=optimisticFavorites({it.withNodeMoved(id,point)}) {locations.moveNode(id,point)}
    fun nodeToTray(id:String)=optimisticFavorites({it.withNodeToTray(id)}) {locations.nodeToTray(id)}
    fun setNodeRole(id:String,role:NodeRole)=optimisticFavorites({it.withNodeRole(id,role)}) {locations.setNodeRole(id,role)}
    /** Returns the new line's ID, or null when the line is not allowed (see [edgeRejection]). */
    fun connectNodes(aId:String,bId:String):String? {
        if(mutable.value.store.edgeRejection(aId,bId)!=null)return null
        val id=newId()
        optimisticFavorites({it.withEdge(aId,bId,id)}) {locations.connectNodes(aId,bId,id)}
        return id
    }
    fun setEdgeDirection(id:String,mode:DirectionMode)=optimisticFavorites({it.withEdgeDirection(id,mode)}) {locations.setEdgeDirection(id,mode)}
    /** Removes a line and returns what an undo needs. */
    fun removeEdge(id:String):Pair<NetworkEdge,Int>? {
        val edges=mutable.value.store.network.edges
        val index=edges.indexOfFirst {it.id==id}.takeIf {it>=0} ?: return null
        val edge=edges[index]
        optimisticFavorites({it.withoutEdge(id)}) {locations.removeEdge(id)}
        return edge to index
    }
    fun restoreEdge(edge:NetworkEdge,index:Int)=optimisticFavorites({it.withRestoredEdge(edge,index)}) {locations.restoreEdge(edge,index)}
    fun favoriteAt(favorite:Favorite,at:GridPoint?)=mutate {locations.saveFavoriteAt(favorite,at)}
    /** H12: grid size of the network plan; growing keeps everything, shrinking moves outside favourites to the tray. */
    fun clearScheduleNotice()=mutate(refreshWidgets=false) {locations.clearScheduleNotice()}
    fun setGridSize(columns:Int,rows:Int)=optimisticFavorites({it.withGridSize(columns,rows)}) {locations.setGridSize(columns,rows)}
    /** Hold on a node: the whole app uses its place as location; holding it again restores. */
    fun toggleLocationOverride(nodeId:String)=mutate {locations.toggleLocationOverride(nodeId)}
    /** "In Übersicht anzeigen": hold pins the favourite to the overview, holding it again
     * releases it. The mark on the plan changes at once; the write follows (idempotent overlay). */
    fun toggleOverviewFavorite(id:String) {
        val current=mutable.value.store
        // Up to five pins; a sixth is not added silently but explained.
        if(id !in current.overviewPins && current.overviewPinsFull) {
            messages.trySend(tr("Bis zu %1\$s Favoriten lassen sich in der Übersicht anzeigen. Halte einen angehefteten Favoriten gedrückt, um ihn zu lösen.",MAX_OVERVIEW_FAVORITES))
            return
        }
        val target=current.toggleOverviewFavorite(id).overviewPins
        optimisticFavorites({it.withOverviewFavorites(target)}) {locations.setOverviewFavorites(target)}
    }
    /** "Genauer Standort": the nearby favourite no longer replaces the GPS start until it is left. */
    fun useExactLocation() {
        ProximityMemory.suppressedNodeId=ProximityMemory.anchor?.nodeId
        mutable.update {it.copy(store=it.store.copy())}
    }
    fun addFavoriteToBoard(id:String)=mutate {locations.addFavoriteToBoard(id)}

    // A drop is the authoritative UI state at once; the persisted write follows asynchronously and
    // in call order (DataStore serialises updates). Pending changes are re-applied to every emitted
    // store until their own write finished, so an older emission never restores an older order.
    // The transforms are idempotent. A failed write is rolled back visibly with a message.
    // Writes are serialised: a store containing write r also contains every earlier write, so
    // those overlays end with it, even if their own emission was conflated away.
    private fun settleFavoriteWrites(saved:StoredState) {
        val settled=writtenFavoriteRevisions.sortedDescending().firstOrNull {revision->
            pendingFavoriteChanges[revision]?.let {change->change(saved)==saved} ?: true
        } ?: return
        writtenFavoriteRevisions.filter {it<=settled}.forEach {revision->
            pendingFavoriteChanges.remove(revision);writtenFavoriteRevisions.remove(revision)
        }
    }
    private fun withPendingFavorites(saved:StoredState)=pendingFavoriteChanges.values.fold(saved) {state,change->change(state)}
    private fun optimisticFavorites(change:(StoredState)->StoredState,persist:suspend ()->Any) {
        val revision=++favoriteRevision
        pendingFavoriteChanges[revision]=change
        mutable.update {it.copy(store=change(it.store))}
        viewModelScope.launch {
            try {
                persist()
                // The overlay ends only when this view model's own store emission contains the
                // write. Re-rendering an older emission here made tiles jump back for a moment.
                writtenFavoriteRevisions+=revision
                persistedStore?.let(::settleFavoriteWrites)
                try {widgetUpdater.update()}catch(e:CancellationException){throw e}catch(_:Exception){}
            }
            catch(e:CancellationException){pendingFavoriteChanges.remove(revision);throw e}
            catch(_:Exception){
                // A failed write is rolled back visibly.
                pendingFavoriteChanges.remove(revision)
                persistedStore?.let {saved->mutable.update {it.copy(store=withPendingFavorites(saved))}}
                messages.send(tr("Die Änderung der Favoriten konnte nicht gespeichert werden."))
            }
        }
    }
    /** [id] is a favourite (route from the current start) or a network line (only between its two
     * nodes, nearer → farther; a one-way line keeps its direction). */
    /** [startOverride]: Planermodus, the typed start replaces the app's own start (GPS or manual). */
    fun searchFavorite(id:String,time:Long=System.currentTimeMillis(),mode:TimeMode=TimeMode.DEPARTURE,startOverride:Location?=null) {
        if(liveFocus) return
        val store=mutable.value.store
        val favorite=store.favorites.firstOrNull {it.id==id}
        val line=store.network.edges.firstOrNull {it.id==id}
        if(favorite!=null || line!=null) {
            searchJob?.cancel()
            cancelPrefetch()
            searchJob=viewModelScope.launch {
                try {
                    val saved=loaded()
                    val place=startOverride ?: try {origin(saved)} catch(e:CancellationException) {throw e} catch(e:Exception) {
                        if(line==null)throw e else saved.presence?.takeIf {it.isFresh(System.currentTimeMillis())}?.location
                    }
                    val current=loaded()
                    val removed=tr("Dieser Favorit wurde entfernt. Bitte ein anderes Ziel wählen.")
                    val query=if(line!=null) {
                        val edge=current.network.edges.firstOrNull {it.id==id} ?: throw IllegalArgumentException(removed)
                        val members=current.edgeMembers(edge,place,current.presence?.accuracyMeters) ?: throw IllegalArgumentException(removed)
                        if(current.edgeAgainstPosition(edge,place))
                            messages.send(tr("Diese Verbindung ist nur in Richtung %1\$s → %2\$s eingerichtet.",members.near.label,members.far.label))
                        val far=members.far
                        JourneyQuery(members.near.location,far.location,time,mode,far.transportFilter?.map {it.lineType}?.toSet() ?: current.settings.products,
                            walkingSpeed=current.settings.walkingSpeed,ignoreWalking=current.settings.ignoreWalking,transportFilter=far.transportFilter)
                    } else (current.favorites.firstOrNull {it.id==id} ?: throw IllegalArgumentException(removed)).journeyQuery(place,current.settings,time,mode)
                    search(query.destination,time,mode,query.origin,query)
                    lastFavoriteId=id
                }catch(e:CancellationException){throw e}catch(e:Exception){mutable.update {it.copy(trips=LoadState(error=errorText(e)),searchFailure=transitFailure(e))}}
            }
            return
        }
        searchJob?.cancel();searchJob=viewModelScope.launch {
            mutable.update {it.copy(trips=LoadState(error=tr("Dieser Favorit wurde entfernt. Bitte ein anderes Ziel wählen.")))}
        }
    }

    fun retrySearch() {
        if (mutable.value.showingRegionalPlan || (mutable.value.query != null && mutable.value.trips.value != null)) refreshResults(force=true)
        else
            lastSearch?.let { search(it.destination, it.time, it.mode, it.originOverride,it.cacheQuery) }
                ?: lastFavoriteId?.let { searchFavorite(it) }
    }

    private fun resetHistoricalConnections() {
        historyGeneration++;pastJob?.cancel();futureJob?.cancel();batchPager=null
        regularBoard=null
        mutable.update {it.copy(regionalResults=null,regionalBoardAt=null,historicalTrips=emptyList(),historicalQuery=null,historicalBatches=0,historyLoading=false,historyError=null,
            pastLoading=false,futureLoading=false,pastError=null,futureError=null,canLoadPast=true,canLoadFuture=true)}
    }

    /** Responses of an older search generation never change the current paging state. */
    private fun updatePaging(direction:ConnectionDirection,token:Long,loading:Boolean,error:String?=null,clearError:Boolean=false) {
        if(token!=historyGeneration)return
        mutable.update {
            val past=direction==ConnectionDirection.EARLIER
            val pastLoading=if(past)loading else it.pastLoading
            val futureLoading=if(past)it.futureLoading else loading
            val pastError=if(past)error ?: it.pastError.takeUnless {clearError} else it.pastError
            val futureError=if(past)it.futureError else error ?: it.futureError.takeUnless {clearError}
            it.copy(pastLoading=pastLoading,futureLoading=futureLoading,pastError=pastError,futureError=futureError,
                historyLoading=pastLoading||futureLoading,historyError=pastError ?: futureError)
        }
    }

    fun loadHistoricalConnections()=loadConnectionBatch(past=true)
    fun loadFutureConnections()=loadConnectionBatch(past=false)
    private fun loadConnectionBatch(past:Boolean) {
        val snapshot=mutable.value;val query=snapshot.query ?: return
        if (snapshot.showingRegionalPlan) return
        val pager=batchPager ?: return
        val direction=if(past)ConnectionDirection.EARLIER else ConnectionDirection.LATER
        val running=if(past)pastJob else futureJob
        if(liveFocus || (if(past)snapshot.pastLoading else snapshot.futureLoading) || snapshot.trips.loading || running?.isActive==true ||
            (if(past)!snapshot.canLoadPast else !snapshot.canLoadFuture))return
        val token=historyGeneration
        updatePaging(direction,token,loading=true,clearError=true)
        val job=viewModelScope.launch {
            try {
                val window=withTimeout(20_000){withContext(transitRequestBudget(12)){pager.load(direction,CONNECTION_EDGE_BATCH)}}
                if(token!=historyGeneration || batchPager!==pager)return@launch
                val now=System.currentTimeMillis()
                // K01: new bars are fresh, but saved bars that were not confirmed keep their stand.
                mutable.update {it.copy(journeyWindow=window,trips=it.trips.copy(value=window.trips,
                        updatedAt=confirmedStand(pager,batchSeededStand,now) ?: pager.confirmedAt ?: now),
                    staleTripIds=pager.unconfirmedIds(now),canLoadPast=!pager.pastExhausted,canLoadFuture=!pager.futureExhausted)}
                val cache=snapshot.activeCacheId?.let {journeyCache.get(it)}
                val entry=persistWindow(query,snapshot.store.settings,cache?.deviceOrigin ?: query.origin.isDevicePosition,
                    cache?.currentDeparture ?: isCurrentDeparture(query.time,query.timeMode),window,snapshot.activeCacheId,
                    pager,batchSeededStand,immediate=true)
                if(entry!=null)mutable.update {it.copy(activeCacheId=entry.id)}
            }catch(e:CancellationException){if(e is TimeoutCancellationException)updatePaging(direction,token,loading=false,error=tr("Weitere Verbindungen konnten nicht geladen werden. Bitte erneut versuchen."))else throw e}
            catch(e:Exception){updatePaging(direction,token,loading=false,error=errorText(e))}
            finally {updatePaging(direction,token,loading=false)}
        }
        if(past)pastJob=job else futureJob=job
    }

    fun pullToRefreshResults() = refreshResults(force = true)

    fun refreshResults(force: Boolean = false) {
        if (liveFocus) return
        if (mutable.value.sameLocationMessage != null) return
        val snapshot=mutable.value
        val originalQuery=snapshot.query ?: return
        if (searchJob?.isActive == true) return
        val query=if (snapshot.searchFromNow) originalQuery.copy(time=System.currentTimeMillis()) else originalQuery
        // Keep the scheduled fallback under the new anchor until the provider responds.
        if (snapshot.showingRegionalPlan) mutable.update { it.copy(query=query,regionalResults=query to snapshot.regionalResults!!.second) }
        regularBoard=null
        searchJob=viewModelScope.launch {
            try {
                val saved=loaded();val cached=mutable.value.activeCacheId?.let {journeyCache.get(it)}
                loadJourneyWindow(query,saved,cached?.deviceOrigin ?: query.origin.isDevicePosition,
                    cached?.currentDeparture ?: isCurrentDeparture(query.time,query.timeMode),cached,
                    previous=mutable.value.trips.value.orEmpty(),force=force,countSearch=false)
                mutable.update { it.copy(regionalResults=null,selectedTripId=it.selectedTripId?.takeIf { id -> it.trips.value.orEmpty().any { row -> row.id==id } }) }
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                val failure=transitFailure(e)
                mutable.update {
                    // K01: with bars on screen a failed refresh is reported beside them; they keep their stand.
                    if(it.trips.value.isNullOrEmpty()) it.copy(trips=it.trips.copy(loading=false,error=errorText(e)),searchFailure=failure)
                    else it.copy(trips=it.trips.copy(loading=false),refreshFailure=failure,refreshAttemptAt=System.currentTimeMillis())
                }
            }
        }
    }

    private fun boardInterval(settings: AppSettings) = if (settings.powerSaving) 5*60_000L else 3*60_000L

    /** Only the foreground results screen calls this. Distant dates have no current departure board. */
    fun refreshLiveResults() {
        if (liveFocus || searchJob?.isActive == true) return
        val ui = mutable.value
        val query = ui.query ?: return
        val now = System.currentTimeMillis()
        if ((ui.refreshFailure ?: ui.searchFailure)?.retryAt?.let { it > now } == true) return
        if (now-fullSearchAt >= ROUTE_RECHECK && (ui.showingRegionalPlan ||
                ui.trips.value.orEmpty().any { it.endTime >= now && it.startTime <= now+3600_000L })) {
            fullSearchAt = now
            refreshResults(force=true)
        } else if (now-boardCheckedAt >= boardInterval(ui.store.settings) && ui.showingRegionalPlan && regularBoard != null) {
            val stop=regularBoard ?: return
            val generation=cacheGeneration
            boardCheckedAt=now
            searchJob=viewModelScope.launch {
                val updated=try { providers.get(ui.store.settings).verifyDirectDepartures(query,ui.regionalResults!!.second,stop) }
                    catch(e:CancellationException) { throw e } catch(e:Exception) {
                        if (generation==cacheGeneration && mutable.value.query==query)
                            mutable.update { it.copy(refreshFailure=transitFailure(e),refreshAttemptAt=now) }
                        return@launch
                    }
                if (generation!=cacheGeneration || mutable.value.query!=query) return@launch
                if (updated != null) mutable.update { it.copy(regionalResults=query to updated,refreshFailure=null,regionalBoardAt=now) }
                else { searchJob=null;refreshResults(force=true) }
            }
        } else if (ui.journeyWindow?.complete == true && now-boardCheckedAt >= boardInterval(ui.store.settings)) {
            val pager = batchPager ?: return
            val generation = cacheGeneration
            searchJob = viewModelScope.launch { updateBoardTimes(pager,query,generation) }
        }
    }

    private suspend fun updateBoardTimes(pager:JourneyBatchPager, query:JourneyQuery, generation:Long) {
        val now=System.currentTimeMillis()
        boardCheckedAt=now
        val ui=mutable.value
        val stops=upcomingBoardStops(ui.trips.value.orEmpty(),now)
        if (stops.isEmpty()) return
        val provider=providers.get(ui.store.settings)
        try {
            for (stop in stops) {
                val rows=withContext(FreshTransitResponse + transitRequestBudget(2)) { provider.departures(stop,30) }
                if (batchPager !== pager || mutable.value.query != query || cacheGeneration != generation) return
                val window=pager.applyBoardDepartures(rows,System.currentTimeMillis())
                if (batchPager !== pager || cacheGeneration != generation) return
                mutable.update { it.copy(journeyWindow=window,trips=it.trips.copy(value=window.trips),refreshFailure=null) }
                // A board cannot renew a whole itinerary's expiry or its confirmation timestamp.
                val cached=mutable.value.activeCacheId?.let(journeyCache::get) ?: continue
                journeyCache.put(cached.query,ui.store.settings,cached.deviceOrigin,cached.currentDeparture,window,
                    cached.id,retainSavedAt=cached.savedAt,attemptFailed=cached.lastAttemptFailed,boardCheckedAt=now)
                journeyCache.persist(viewModelScope)
            }
        } catch (e:CancellationException) { throw e }
        catch (e:Exception) {
            if (batchPager === pager && cacheGeneration == generation)
                mutable.update { it.copy(refreshFailure=transitFailure(e),refreshAttemptAt=now) }
        }
    }

    private val fastPlaceSearch=FastPlaceSearch(
        transit={query,settings->providers.get(settings).locations(query).let {stopCatalog?.enrich(it) ?: it}},
        addresses={query,bias,endpoint->geocoding.search(query,bias,endpoint)},
        offline={query,bias->stopCatalog?.search(query,bias).orEmpty()},
        networkDebounceMillis=150,
        fallback={query,settings->providers.get(settings).addresses(query)},
    )
    fun localLocationMatches(query:String)=fastPlaceSearch.local(query,mutable.value.store)
    fun locationSuggestions(query:String): Flow<List<Location>> = flow {
        val saved=loaded()
        emitAll(fastPlaceSearch.suggestions(query,saved,mutable.value.position.value))
    }
    suspend fun findLocations(query:String):List<Location> = locationSuggestions(query).last()
    suspend fun startUsageTipWeek() {
        try {locations.startUsageTipWeek()}
        catch(e:CancellationException){throw e}
        catch(_:Exception){/* Optional learning counters must never block the journey screen. */}
    }
    suspend fun nextOverviewHeader():String = locations.nextUsageTip()?.text ?: TransitHeaderRotation.next()

    /** Only the explicitly permitted device position is used for the first-run example. */
    suspend fun onboardingExample(favorite:Favorite,settings:AppSettings):Trip? {
        val manual=settings.manualOrigin?.takeIf {settings.originMode==OriginMode.MANUAL && !it.isDevicePosition}
        if((manual==null && !device.hasPermission()) || !favorite.location.routable)return null
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(15_000) {
                val position=manual ?: device.current()
                providers.get(settings).firstJourney(favorite.journeyQuery(position,settings,System.currentTimeMillis()))
            }
        }
    }

    /**
     * The next connection from the current start to the
     * favourite (the same query as a tap on it), without opening the results. The caller pins it
     * through the usual pin flow (replacing an existing pin is confirmed there). Readable failures.
     */
    suspend fun nextFavoriteJourney(id:String):Pair<JourneyQuery,Trip> {
        val saved=loaded()
        val favorite=saved.favorites.firstOrNull {it.id==id}
            ?: throw TransitException(tr("Dieser Favorit wurde entfernt. Bitte ein anderes Ziel wählen."))
        val place=origin(saved)
        val current=loaded()
        val query=favorite.journeyQuery(place,current.settings,System.currentTimeMillis(),TimeMode.DEPARTURE)
        val trip=withContext(Dispatchers.IO) {withTimeout(20_000) {providers.get(current.settings).firstJourney(query)}}
            ?: throw TransitException(tr("Zurzeit keine passende Verbindung gefunden."), FailureKind.NO_CONNECTION)
        return query to trip
    }

    /** A cancellable request owned by the waypoint dialog; no shared search state changes on failure. */
    suspend fun routeWaypoints(query:JourneyQuery):Trip {
        val saved=loaded()
        return withContext(Dispatchers.IO) {
            val result=providers.get(saved.settings).firstJourney(query)
            result ?: throw TransitException(tr("Die Verbindung konnte nicht neu berechnet werden. Deine bisherige Fahrt bleibt erhalten."))
        }
    }

    fun nearby(force: Boolean = false) {
        if (liveFocus) return
        if (nearbyJob?.isActive == true) return
        if (!force && mutable.value.nearby.value != null) return
        nearbyJob =
            viewModelScope.launch {
                mutable.update { it.copy(nearby = it.nearby.copy(loading = true, error = null)) }
                try {
                    val saved = loaded()
                    val position = origin(saved)
                    require(position.hasCoordinates) {
                        tr("Für die Umgebungssuche fehlen die Koordinaten des Startorts.")
                    }
                    val stops =
                        providers
                            .get(saved.settings)
                            .nearby(
                                position.latitude!!,
                                position.longitude!!,
                                saved.settings.departureRadiusMeters,
                            )
                    val keep = stops.map { "nearby:${it.provider}:${it.id}" }.toSet()
                    val obsolete =
                        mutable.value.boards.keys.filter { it.startsWith("nearby:") && it !in keep }
                    obsolete.forEach { boardJobs.remove(it)?.cancel() }
                    mutable.update {
                        it.copy(
                            nearby = LoadState(stops, updatedAt = System.currentTimeMillis()),
                            boards = it.boards - obsolete.toSet(),
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutable.update {
                        it.copy(nearby = it.nearby.copy(loading = false, error = errorText(e)))
                    }
                }
            }
    }

    fun setBoardVisible(key: String, visible: Boolean) {
        if (visible) visibleBoards += key else visibleBoards -= key
    }

    fun loadBoard(
        key: String,
        title: String,
        place: Location,
        force: Boolean = false,
        limit: Int? = null,
    ) {
        if (liveFocus) return
        if (boardJobs[key]?.isActive == true) return
        val old = mutable.value.boards[key]
        val demand = (limit ?: old?.requestedLimit ?: 8).coerceIn(8, 30)
        if (
            !force &&
                old != null &&
                old.place == place &&
                (old.rows.value != null || old.rows.error != null)
        )
            return
        boardJobs[key] =
            viewModelScope.launch {
                val current =
                    mutable.value.boards[key]?.takeIf { it.place == place }
                        ?: StationBoard(key, title, place)
                mutable.update {
                    it.copy(
                        boards =
                            it.boards +
                                (key to
                                    current.copy(
                                        rows = current.rows.copy(loading = true, error = null),
                                        requestedLimit = demand,
                                    ))
                    )
                }
                try {
                    boardSlots.acquire()
                    val pair =
                        try {
                            val saved = loaded()
                            val provider = providers.get(saved.settings)
                            val stop = provider.resolveStop(place)
                            val cacheKey =
                                "${TimetableProfile.identity(saved.settings)}:${stop.provider}:${stop.id}"
                            val cached =
                                departureCache[cacheKey]?.takeIf {
                                    !force &&
                                        it.second.size >= demand &&
                                        System.currentTimeMillis() - it.first < 25_000
                                }
                            val rows =
                                cached?.second
                                    ?: provider.departures(stop, demand).also {
                                        departureCache[cacheKey] = System.currentTimeMillis() to it
                                    }
                            Triple(
                                stop,
                                rows
                                    .sortedBy { it.time }
                                    .distinctBy { it.id }
                                    .take(demand)
                                    .filter {
                                        saved.settings.includeLongDistance ||
                                            !isLongDistance(it.lineType, it.lineName)
                                    },
                                rows.size >= demand,
                            )
                        } finally {
                            boardSlots.release()
                        }
                    mutable.update {
                        it.copy(
                            boards =
                                it.boards +
                                    (key to
                                        current.copy(
                                            stop = pair.first,
                                            requestedLimit = demand,
                                            canLoadMore = pair.third,
                                            rows =
                                                LoadState(
                                                    pair.second,
                                                    updatedAt = System.currentTimeMillis(),
                                                ),
                                        ))
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutable.update { state ->
                        val board = state.boards[key] ?: current
                        state.copy(
                            boards =
                                state.boards +
                                    (key to
                                        board.copy(
                                            rows =
                                                board.rows.copy(
                                                    loading = false,
                                                    error = errorText(e),
                                                )
                                        ))
                        )
                    }
                }
            }
    }

    fun refreshVisibleBoards() {
        if (liveFocus) return
        refreshBoard()
        visibleBoards.toList().forEach { key ->
            mutable.value.boards[key]?.let { loadBoard(key, it.title, it.place, force = true) }
        }
    }

    suspend fun savePersonalTheme(name:String,logo:AppLogo):Result<PersonalTheme> = try{
        Result.success(locations.savePersonalTheme(name,logo))
    }catch(e:CancellationException){throw e}catch(e:Exception){Result.failure(e)}
    fun removePersonalTheme(id:String)=mutate(refreshWidgets=false){locations.removePersonalTheme(id)}
    suspend fun applyPersonalTheme(id:String):Result<Unit>{
        val result=personalDesigns?.apply(id)?:runCatching{locations.applyPersonalTheme(id);Unit}
        if(result.isSuccess)runCatching{widgetUpdater.update()}
        return result
    }
    suspend fun changeAppLogo(logo:AppLogo):Result<AppLogo> = personalDesigns?.changeLogo(logo)?:Result.success(logo)

    fun settings(change: (AppSettings) -> AppSettings) = mutate { preferences.update(change) }

    fun quickWidgetSettings(change: (WidgetStyle) -> WidgetStyle) {
        viewModelScope.launch {
            try {
                preferences.update { it.copy(widget = change(it.widget)) }
                widgetUpdater.updateQuick()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.send(errorText(e))
            }
        }
    }

    fun manualOrigin(location: Location) = mutate {locations.setManualOrigin(location)}

    fun useManualLocationFallback()=mutate {locations.activateManualOrigin()}
    fun setManualLocation(location:Location)=mutate {locations.setManualOrigin(location)}

    fun saveJourney(query:JourneyQuery,trip:Trip,onSaved:()->Unit={})=mutate(refreshWidgets=false) {
        val destination=query.destination
        locations.saveJourney(SavedJourney(favorite=Favorite(label=destination.name.take(80),location=destination),
            query=query.copy(waypoints=trip.waypoints,waypointDwellMinutes=trip.waypointDwellMinutes),trip=trip))
        onSaved()
    }
    fun removeSavedJourney(id:String)=mutate {locations.removeSavedJourney(id)}
    fun expireSavedJourneys(now:Long)=mutate {locations.expireSavedJourneys(now)}
    fun favorite(f: Favorite) = mutate { locations.saveFavorite(f) }

    fun boardStopSuggestions(query:String):Flow<List<Location>> = flow {
        emitAll(fastPlaceSearch.stopSuggestions(query,loaded()))
    }
    suspend fun findBoardStops(query:String):List<Location> = boardStopSuggestions(query).last()

    fun selectBoardStop(stop:Location?)=mutate {
        sharedBoardJob?.cancel()
        locations.selectBoardStop(stop)
        if(stop==null && loaded().settings.originMode==OriginMode.AUTOMATIC)acquirePosition(force=true)
        boardRepository.refresh(force=true,limit=sharedBoardRequestedRows)
    }
    fun addBoardStop(stop: Location) = mutate { locations.addBoardStop(stop) }

    fun removeBoardStop(stop: Location) = mutate { locations.removeBoardStop(stop) }

    fun setBoardStopEnabled(stop: Location, enabled: Boolean) = mutate { locations.setBoardStopEnabled(stop, enabled) }

    fun deleteFavorite(id: String) = mutate { locations.deleteFavorite(id) }

    /** H12: deletes a favourite and returns what "Rückgängig" needs to bring all of it back. */
    fun deleteFavoriteWithUndo(id: String): FavoriteRemoval? {
        val removal = mutable.value.store.favoriteRemoval(id) ?: return null
        optimisticFavorites({ it.withoutFavorite(id) }) { locations.deleteFavorite(id) }
        return removal
    }
    fun restoreFavorite(removal: FavoriteRemoval) =
        optimisticFavorites({ it.withRestoredFavorite(removal) }) { locations.restoreFavorite(removal) }

    fun moveFavorite(id: String, direction: Int) = mutate { locations.moveFavorite(id, direction) }

    fun completeOnboarding() = mutate(refreshWidgets = false) { locations.completeOnboarding() }

    suspend fun completeTutorial(id:String):Boolean = try {
        locations.completeTutorial(id);true
    } catch(e:CancellationException) {throw e} catch(e:Exception) {messages.send(errorText(e));false}
    fun resetTutorials()=mutate(refreshWidgets=false) {
        locations.resetTutorials();messages.send(tr("Die Hinweise erscheinen bei der nächsten Nutzung erneut."))
    }
    fun restartOnboarding()=mutate(refreshWidgets=false) {locations.restartOnboarding()}
    /** Build 115 · a hinted gesture was used; saved quietly (a failed write only shows the hint again). */
    fun learnGestureHint(key:String) {viewModelScope.launch {runCatching {locations.learnGestureHint(key)}}}
    /** Build 117 · the person's report on a ride: kept on the device, only adjusts the reliability estimate. */
    fun reportPunctuality(segment:TripSegment,onTime:Boolean) = mutate(refreshWidgets=false) {
        val time=java.time.Instant.ofEpochMilli(segment.plannedStartTime).atZone(dev.ambivalenton.openv.data.model.transitZone)
        locations.recordPunctuality(stationRideKey(segment),dev.ambivalenton.openv.data.reliability.reliabilityLineKey(segment),
            dev.ambivalenton.openv.data.reliability.reliabilityMode(segment),dev.ambivalenton.openv.data.reliability.timeWindow(time),onTime,System.currentTimeMillis())
    }
    fun clearPunctuality() = mutate(refreshWidgets=false) {
        locations.clearPunctuality();messages.send(tr("Eigene Pünktlichkeitsangaben gelöscht"))
    }
    /** Build 115 · foreground time of one visit, counted towards the first hours of hints. */
    fun addGestureHintUse(millis:Long) {if(millis>0) viewModelScope.launch {runCatching {locations.addGestureHintUse(millis,System.currentTimeMillis())}}}

    suspend fun updateOnboarding(change: (OnboardingDraft) -> OnboardingDraft): Boolean = try {
        locations.updateOnboarding(change)
        true
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        messages.send(errorText(e)); false
    }

    suspend fun finishOnboarding(): Boolean = try {
        locations.completeOnboarding()
        // Preferences are already committed. A widget refresh cannot undo successful setup.
        runCatching { widgetUpdater.update() }
        true
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        messages.send(errorText(e)); false
    }

    /** 2.5: hide a notice for its provider and stop; the snackbar offers "Rückgängig". */
    fun dismissNotice(hit: NoticeHit) = mutate(refreshWidgets = false) { locations.dismissNotices(hit.keys) }
    fun restoreNotice(hit: NoticeHit) = mutate(refreshWidgets = false) { locations.restoreNotices(hit.keys) }
    fun restoreAllNotices() = mutate(refreshWidgets = false) { locations.restoreNotices(null) }

    /** M07: the search history, and only on request also the saved journeys of the cache. */
    fun clearHistory(alsoCache: Boolean = true) = mutate {
        if (alsoCache) clearJourneyCache()
        preferences.clearHistory()
        messages.send(if (alsoCache) tr("Suchverlauf und zwischengespeicherte Fahrten gelöscht") else tr("Suchverlauf gelöscht"))
    }

    fun updateWidgets() = mutate { Unit }

    fun loadMoreBoard(key: String, title: String, place: Location) {
        val current = mutable.value.boards[key] ?: return
        if (current.rows.loading || !current.canLoadMore || current.requestedLimit >= 30) return
        loadBoard(
            key,
            title,
            place,
            force = true,
            limit = (current.requestedLimit + 8).coerceAtMost(30),
        )
    }

    fun advanceBoardStop() {
        mutate(refreshWidgets=false) {locations.advanceBoardRotation()}
    }
    fun resetBoardRotation()=mutate(refreshWidgets=false) {locations.resetBoardRotation()}
    fun requireBoardRows(count:Int) {
        val wanted=count.coerceIn(8,30)
        if(wanted<=sharedBoardRequestedRows)return
        sharedBoardRequestedRows=wanted;refreshBoard(force=true)
    }
    fun refreshBoard(force: Boolean = false) {
        if (liveFocus) return
        val key=boardConfigurationKey(mutable.value.store)+"|choice:"+boardChoiceSignature(mutable.value.store)+"|rows:"+sharedBoardRequestedRows
        if(sharedBoardJob?.isActive==true && sharedBoardRequestKey==key && !force)return
        sharedBoardJob?.cancel()
        sharedBoardRequestKey=key
        sharedBoardJob = viewModelScope.launch {
            try {
                boardRepository.refresh(force,limit=sharedBoardRequestedRows)
                widgetUpdater.update()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.send(errorText(e))
            }
        }
    }

    fun stopColor(stop: Location, color: Long?) = mutate { locations.setStopColor(stop, color) }

    private fun mutate(refreshWidgets: Boolean = true, action: suspend () -> Any) {
        viewModelScope.launch {
            try {
                action()
                if (refreshWidgets) {
                    try { widgetUpdater.update() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { messages.send(tr("Gespeichert. Die Widgets konnten gerade nicht aktualisiert werden.")) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.send(errorText(e))
            }
        }
    }
}

private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

fun errorText(e: Throwable) =
    e.message?.takeIf { it.isNotBlank() } ?: tr("Etwas ist schiefgegangen. Bitte erneut versuchen.")

/** The same reducer is usable by previews and the ViewModel without a second selection state. */
fun AppUiState.selectConnection(id: String): AppUiState =
    if (allTrips.any { it.id == id })
        copy(selectedTripId = id, isListViewVisible = false)
    else this

fun connectionScreenTitle(ui: AppUiState): String {
    if (ui.isListViewVisible) return tr("Verbindungen")
    val trips = ui.allTrips
    val index = trips.indexOfFirst { it.id == ui.selectedTripId }
    return if (index >= 0) tr("Verbindung %1\$s/%2\$s", index + 1, trips.size) else tr("Verbindungen")
}
