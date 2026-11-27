// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.screens

import dev.ambivalenton.openv.ui.theme.languageCardShape
import dev.ambivalenton.openv.ui.theme.cardElevation
import dev.ambivalenton.openv.ui.theme.appFrameShadow
import dev.ambivalenton.openv.ui.theme.appFrame
import dev.ambivalenton.openv.ui.components.CalmVisibility
import dev.ambivalenton.openv.localization.tr

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.FailureKind
import dev.ambivalenton.openv.data.repository.TransitFailure
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,kotlinx.coroutines.FlowPreview::class)
@Composable
fun ResultsScreen(
    ui: AppUiState,
    onRetry: () -> Unit,
    onPermission: () -> Unit,
    onManual: () -> Unit,
    onSaveFavorite: (Location) -> Unit,
    onConnectionSelected: (String) -> Unit,
    onShowList: () -> Unit,
    onCloseDetails: () -> Unit,
    onRefresh: () -> Unit = onRetry,
    onPinRequested: ((Trip)->Unit)? = null,
    onBookmark:((Trip)->Unit)?=null,
    pinTransition: PinTransitionController? = null,
    onWaypointRouted:((JourneyQuery,Trip)->Unit)?=null,
    onHistoricalConnections:()->Unit = {},
    onFutureConnections:()->Unit = {},
    onCachedViewport:(String,Int,Int)->Unit={_,_,_->},
    /** H05: the step a failure or empty answer offers (choose start, change time, check modes …). */
    onFailureStep:(FailureStep)->Unit={step->if(step==FailureStep.RETRY||step==FailureStep.RELOAD)onRetry()},
    /** N01/M12: back to the search form with the focus in the destination field. */
    onChangeDestination:()->Unit={},
    /** H08: reopens the last search after the process was ended. */
    onRestore:(ResultsRestore)->Unit={},
    /** 2.2: "Andere Fahrt suchen" for a journey that is no longer available. */
    onSearchOther:()->Unit=onRetry,
) {
    var waypointTarget by remember {mutableStateOf<Trip?>(null)}
    val waypointGateway=LocalWaypointRoutingGateway.current
    val openDetails: (Trip) -> Unit = { trip -> onConnectionSelected(trip.id) }
    val selectedId = ui.selectedTripId
    val chartScroll = rememberLazyListState()
    val resultsScroll = rememberLazyListState()
    val viewportCallback by rememberUpdatedState(onCachedViewport)
    LaunchedEffect(ui.activeCacheId) {
        val id=ui.activeCacheId ?: return@LaunchedEffect
        val entry=ui.cachedSearches.firstOrNull {it.id==id}
        if(entry!=null && ui.staleTripIds.isNotEmpty() && entry.viewIndex in ui.trips.value.orEmpty().indices)
            chartScroll.scrollToItem(entry.viewIndex,entry.viewOffset)
        snapshotFlow {chartScroll.firstVisibleItemIndex to chartScroll.firstVisibleItemScrollOffset}
            .debounce(350).collect {(index,offset)->viewportCallback(id,index,offset)}
    }
    var arrivalDismissed by
        rememberSaveable(ui.query?.time, ui.sameLocationMessage) { mutableStateOf(false) }
    val handOff = rememberJourneyHandOff()
    val allTrips = ui.allTrips
    // 2.2: a confirmed cancellation or a cancelled call removes a bar. Departed journeys stay:
    // the earlier connections loaded as context (one in the pre-search, three in the next wave) are
    // part of the chart again.
    val clock = rememberWallClock(tickMillis = 15_000).toInstant().toEpochMilli()
    val removed = remember(allTrips, clock / 15_000) {
        allTrips.mapNotNull { trip -> tripImpossibility(trip, maxOf(clock, System.currentTimeMillis()), currentSearch = false)?.let { trip.id to it } }.toMap()
    }
    val trips = remember(allTrips, removed) { allTrips.filter { it.id !in removed } }
    val timetableSource=if (ui.showingRegionalPlan) {
        val checked=ui.regionalBoardAt
        if (checked==null) tr("Direktfahrten laut VRR-Sollfahrplan · ohne Echtzeit")
        else tr("VRR-Sollfahrplan · Abfahrten geprüft um %1\$s Uhr",clockTime(checked))
    } else null
    val selected = allTrips.firstOrNull { it.id == selectedId }
    // H08: small search parameters survive the process; after a restart they reopen the search.
    var restoreToken by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.query, ui.activeCacheId, ui.selectedTripId, ui.searchFromNow) {
        val query = ui.query ?: return@LaunchedEffect
        val cache = ui.cachedSearches.firstOrNull { it.id == ui.activeCacheId }
        restoreToken = ResultsRestore(query.destination, query.origin.takeUnless { cache?.deviceOrigin == true || it.isDevicePosition },
            query.time, query.timeMode, ui.searchFromNow, ui.activeCacheId, ui.selectedTripId).encode()
    }
    val waitingForRestore = ui.query == null && ui.trips.value == null && !ui.trips.loading && ui.trips.error == null && ui.sameLocationMessage == null
    var restoreAttempted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(waitingForRestore, restoreToken) {
        if (waitingForRestore && !restoreAttempted) restoreToken?.let(ResultsRestore::decode)?.let { restoreAttempted = true; onRestore(it) }
    }
    val headerAnchor=remember {ChartHeaderAnchor()}
    val window = ui.journeyWindow
    var areaTop by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { areaTop = it.positionInRoot().y }) {
        // The chart fills the screen down to the navigation; its bars fit between the duration
        // row and just above the navigation pill. In the minimalist mode nothing above it moves: the gap
        // below the top bar's rule equals the gap between the title and that rule.
        val minimal = ui.store.settings.minimalistMode
        val density = LocalDensity.current
        val navigation = LocalNavigationClearance.current
        val titleGap = dev.ambivalenton.openv.ui.components.LocalScreenTitleGap.current
        val chartHeight = (maxHeight - if (minimal) titleGap else 8.dp).coerceAtLeast(200.dp)
        val fontScale = density.fontScale
        val columns = remember(constraints.maxWidth, fontScale, density.density) {
            dev.ambivalenton.openv.ui.components.chartColumns(constraints.maxWidth - with(density) { timelineAxisWidth(fontScale).roundToPx() },
                with(density) { timelineColumnWidth(fontScale).toPx() })
        }
        val pillTop = with(density) { (maxHeight - navigation + dev.ambivalenton.openv.ui.components.PillNavigationTop -
            dev.ambivalenton.openv.ui.components.CHART_ABOVE_PILL).toPx() }
        val fitBottom = remember(pillTop) { { areaTop + pillTop } }
        JourneyPullRefresh(ui.trips.loading,onRefresh) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("results-content"),
            state = resultsScroll,
            contentPadding = PaddingValues(top=if (minimal) 0.dp else 20.dp,bottom=if (trips.isEmpty()) 20.dp+navigation else 0.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            // Collapsed by default (destination or favourite name and the time range); the
            // minimalist mode leaves the whole block out and starts with the chart.
            ui.query?.takeIf { !ui.store.settings.minimalistMode }?.let { query ->
                item(key = "query-header") {
                    val favorite = remember(query.destination, ui.store.favorites) {
                        val wanted = dev.ambivalenton.openv.data.repository.placeIdentity(query.destination)
                        ui.store.favorites.firstOrNull { dev.ambivalenton.openv.data.repository.placeIdentity(it.location) == wanted }?.label
                    }
                    ChartQueryHeader(query, window, favorite)
                }
            }
            item(key = "results-status-band") {
                val loading=ui.trips.loading || ui.historyLoading
                // Minimalist mode: a fixed margin as tall as the title's distance to the rule; loading shows inside it.
                Box(Modifier.fillMaxWidth().height(if (minimal) titleGap else 22.dp),
                    contentAlignment = if (minimal) Alignment.Center else Alignment.TopStart) {
                    if(loading) OverviewDottedDivider(Modifier.testTag("results-loading"),isLoading=true)
                }
            }
            ui.trips.error?.let { error ->
                item(key = "results-error") {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val failure = ui.searchFailure
                        // H05: cause and matching step; bars that are already shown stay above.
                        if (failure != null) FailurePanel(failure, FailureContext.RESULTS, onFailureStep,
                            provider = dev.ambivalenton.openv.data.model.TimetableProfile.shortName(ui.store.settings.engine),
                            alternative = coverageAlternative(ui),
                            kept = ui.query?.takeIf { failure.kind != FailureKind.NO_CONNECTION && failure.kind != FailureKind.NO_COVERAGE }
                                ?.let { q -> "${q.origin.name} → ${q.destination.name} · ${dev.ambivalenton.openv.data.model.clockTime(q.time)}" })
                        else ErrorPanel(error, onRetry)
                        // A missing GPS fix in automatic mode: check the permission or choose a start.
                        if (trips.isEmpty() && ui.store.settings.originMode == OriginMode.AUTOMATIC &&
                            failure?.kind !in setOf(FailureKind.OFFLINE, FailureKind.TIMEOUT, FailureKind.RATE_LIMITED,
                                FailureKind.UNAVAILABLE, FailureKind.INVALID_RESPONSE, FailureKind.NO_CONNECTION, FailureKind.NO_COVERAGE)) {
                            Row {
                                TextButton(onPermission, Modifier.heightIn(min = 48.dp)) { Text(tr("Standort prüfen")) }
                                TextButton(onManual, Modifier.heightIn(min = 48.dp)) { Text(tr("Start wählen")) }
                            }
                        }
                    }
                }
            }
            if (timetableSource!=null) item(key = "regional-timetable-source") {
                Text(timetableSource,Modifier.padding(horizontal=20.dp).testTag("regional-timetable-source"),
                    style=MaterialTheme.typography.bodySmall)
            }
            if (trips.isNotEmpty()) {
                item(key = COMPARISON_CHART_KEY) {
                    Column(Modifier.fillMaxWidth().height(chartHeight).testTag("comparison-chart-container").semantics {
                        customActions=buildList {
                            if(!ui.showingRegionalPlan && !ui.pastLoading && ui.canLoadPast)add(CustomAccessibilityAction(tr("Frühere Verbindungen laden")){onHistoricalConnections();true})
                            if(!ui.showingRegionalPlan && !ui.futureLoading && ui.canLoadFuture)add(CustomAccessibilityAction(tr("Weitere Verbindungen laden")){onFutureConnections();true})
                        }
                    }) {
                        ConnectionPagingStatus(ui,onHistoricalConnections,onFutureConnections)
                        TimelineDurationHeader(trips, chartScroll, openDetails, headerAnchor, columns)
                        OeffiTimelineGraph(
                            trips,
                            openDetails,
                            Modifier.weight(1f),
                            onTripLongPress = when(if (ui.showingRegionalPlan) ChartLongPressAction.NOTHING else ui.store.settings.chartLongPress) {
                                ChartLongPressAction.NOTHING->null
                                ChartLongPressAction.SHARE->handOff.run
                                ChartLongPressAction.SAVE->onBookmark
                                ChartLongPressAction.PIN->onPinRequested
                                ChartLongPressAction.WAYPOINT->if(waypointGateway!=null&&onWaypointRouted!=null)({trip:Trip->waypointTarget=trip})else null
                            },
                            scrollState = chartScroll,
                            staleTripIds=ui.staleTripIds,
                            headerAnchor=headerAnchor,
                            onPullToPast=onHistoricalConnections.takeIf {!ui.showingRegionalPlan && !ui.pastLoading && ui.canLoadPast},
                            onPullToFuture=onFutureConnections.takeIf {!ui.showingRegionalPlan && !ui.futureLoading && ui.canLoadFuture},
                            onEdgeReached={later->if(later)onFutureConnections()else onHistoricalConnections()},
                            columns = columns,
                            fitBottomInRoot = fitBottom,
                        )
                    }
                }
            }
            ui.sameLocationMessage?.let { index -> item(key = "same-location") {
                SameLocationEasterEgg(index, Modifier.padding(horizontal = 20.dp), onChange = onChangeDestination) } }
            if (
                trips.isEmpty() &&
                    !ui.trips.loading &&
                    ui.trips.error == null &&
                    ui.sameLocationMessage == null &&
                    !waitingForRestore
            )
                item(key = "results-empty") { Text(tr("Keine passenden Verbindungen in diesem Zeitraum."), Modifier.padding(horizontal = 20.dp)) }
        }
    }
    }
    waypointTarget?.let {trip->
        if(waypointGateway!=null&&trip.segments.isNotEmpty()){
            val query=waypointQueryForTrip(ui.query,trip)
            WaypointPlannerDialog(query,trip,waypointGateway,{waypointTarget=null}){updated,result->waypointTarget=null;onWaypointRouted?.invoke(updated,result)}
        }
    }
    if (selected != null || ui.isListViewVisible)
        JourneyDetailsSheet(
            // 2.2: a journey that left the offer while it is open stays in its sheet with an explanation.
            (trips + listOfNotNull(selected?.takeIf { it.id in removed })).sortedBy { it.startTime },
            selected?.id,
            onCloseDetails,
            onConnectionSelected,
            isListViewVisible = ui.isListViewVisible,
            onShowList = onShowList,
            onPinRequested=onPinRequested.takeUnless { ui.showingRegionalPlan },pinTransition=pinTransition,
            onBookmark=onBookmark.takeUnless { ui.showingRegionalPlan },
            journeyQuery=ui.query,onWaypointRouted=onWaypointRouted.takeUnless { ui.showingRegionalPlan },
            unavailable=removed,onSearchOther={onCloseDetails();onSearchOther()},timetableSource=timetableSource,
        )
}

/** H08: the small state that reopens a search after the process was ended (no trips, no positions). */
@kotlinx.serialization.Serializable
data class ResultsRestore(val destination: Location, val origin: Location?, val time: Long, val mode: TimeMode, val current: Boolean,
    val cacheId: String?, val selectedTripId: String?) {
    fun encode(): String = restoreJson.encodeToString(serializer(), this)
    companion object {
        private val restoreJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        fun decode(text: String): ResultsRestore? = runCatching { restoreJson.decodeFromString(serializer(), text) }.getOrNull()
    }
}

/**
 * [alwaysOpen] (build 71): the details of the only connection there is. Nothing to collapse or to
 * choose from, so there is no arrow button and a tap does nothing; a long press opens the compact view.
 */
@Composable
fun TripCard(trip: Trip, initiallyExpanded: Boolean = false, onToggle: (() -> Unit)? = null, alwaysOpen: Boolean = false) {
    val walkingSettings=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current
    val display=remember(trip,walkingSettings.ignoreWalking,walkingSettings.walkingSpeed) {if(walkingSettings.ignoreWalking)assembleTimeline(trip,walkingSettings).trip else trip}
    var expandedState by rememberSaveable(trip.id) { mutableStateOf(initiallyExpanded) }
    val expanded = alwaysOpen || expandedState
    val handOff = rememberJourneyHandOff()
    val toggle = onToggle ?: { expandedState = !expandedState }
    AppCard(
        shape = languageCardShape(RoundedCornerShape(28.dp), 28.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (alwaysOpen) Modifier
                    .pointerInput(trip.id) { detectTapGestures(onLongPress = { handOff.run(trip) }) }
                    .semantics { onLongClick(handOff.actionLabel) { handOff.run(trip); true } }
                else Modifier
                    .combinedClickable(
                        role = Role.Button,
                        onClick = toggle,
                        onLongClickLabel = handOff.actionLabel,
                        onLongClick = { handOff.run(trip) },
                    )
                    .semantics {
                        stateDescription =
                            if (expanded) tr("Verbindungsdetails ausgeklappt")
                            else tr("Verbindungsdetails eingeklappt")
                    })
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FittedText(
                    tr("%1\$s – %2\$s", clockTime(display.startTime), clockTime(display.endTime)),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!alwaysOpen) FilledTonalIconButton(toggle,shape=dev.ambivalenton.openv.ui.theme.appCorner(13.dp),modifier=Modifier.size(48.dp).pressScale(.9f).testTag("collapse-connection-details")) {
                    // One chevron that flips with a spring instead of swapping two symbols.
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    val flip by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f,
                        MaterialTheme.motionScheme.fastSpatialSpec(), label = "details-chevron")
                    AppIcon(
                        R.drawable.ic_expand,
                        if (expanded && onToggle != null) tr("Alle Verbindungen anzeigen")
                        else if (expanded) tr("Details schließen") else tr("Details öffnen"),
                        Modifier.graphicsLayer { rotationZ = flip },
                    )
                }
            }
            if (!expanded && onToggle != null)
                FittedText(
                    trip.segments.lastOrNull()?.destination?.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                )
            Text(
                tr("%1\$s · %2\$s", minutesText((display.totalDuration + 59999) / 60000), transferCount(trip.transfers)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if(trip.waypoints.isNotEmpty()) Text(tr("Über %1\$s",trip.waypoints.joinToString(" → ") {it.name}),
                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
        }
        CalmVisibility(
            expanded,
            enter = expandVertically(spring()) + fadeIn(),
            exit = shrinkVertically(spring()) + fadeOut(),
        ) {
            ModernRouteTimeline(trip, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp))
        }
    }
}

/** One line under the chart; [past]/[future] name what its retry loads again. */
internal data class PagingLine(val text:String,val retry:Boolean,val past:Boolean,val future:Boolean)

/**
 * Partial failures keep loaded bars. Build 103: one message at a time – none while the search's own error
 * card is shown (its retry repeats everything), one shared line when both directions failed. End notes
 * ("Keine früheren Verbindungen gefunden.") only with hints switched on and without a failure.
 */
internal fun pagingStatusLines(searchFailed:Boolean,pastError:String?,futureError:String?,canLoadPast:Boolean,pastLoading:Boolean,
    canLoadFuture:Boolean,futureLoading:Boolean,hints:Boolean):List<PagingLine> = buildList {
    if(searchFailed) return@buildList
    when {
        pastError!=null && futureError!=null -> add(PagingLine(tr("Weitere Verbindungen konnten nicht geladen werden."),true,true,true))
        pastError!=null -> add(PagingLine(tr("Frühere Verbindungen: %1\$s",pastError),true,true,false))
        futureError!=null -> add(PagingLine(tr("Spätere Verbindungen: %1\$s",futureError),true,false,true))
        hints -> {
            if(!canLoadPast && !pastLoading) add(PagingLine(tr("Keine früheren Verbindungen gefunden."),false,true,false))
            if(!canLoadFuture && !futureLoading) add(PagingLine(tr("Keine späteren Verbindungen gefunden."),false,false,true))
        }
    }
}

@Composable
private fun ConnectionPagingStatus(ui:AppUiState,onPast:()->Unit,onFuture:()->Unit) {
    val lines=pagingStatusLines(ui.trips.error!=null,ui.pastError,ui.futureError,ui.canLoadPast,ui.pastLoading,
        ui.canLoadFuture,ui.futureLoading,dev.ambivalenton.openv.ui.components.hintsShown)
    if(lines.isEmpty())return
    Column(Modifier.padding(horizontal=20.dp).testTag("connection-paging-status")) {
        lines.forEach {line->
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(line.text,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(line.retry)TextButton({if(line.past)onPast();if(line.future)onFuture()}) {Text(tr("Erneut versuchen"))}
            }
        }
    }
}

/**
 * The text above the comparison chart. Collapsed: the destination (a favourite's name) in
 * small type and the shown time range, next to a button exactly as tall as those two lines. Expanded:
 * the full former block (destination, start, time range). It scrolls away with the chart as before.
 */
@Composable private fun ChartQueryHeader(query: JourneyQuery, window: dev.ambivalenton.openv.data.repository.JourneyWindow?, favorite: String?) {
    var expanded by rememberSaveable(query.destination.name, query.origin.name) { mutableStateOf(false) }
    val quiet = dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val turn by animateFloatAsState(if (expanded) 180f else 0f, if (quiet) snap() else spring(dampingRatio = .55f, stiffness = 420f), label = "chart-header-chevron")
    val small = MaterialTheme.typography.bodyMedium
    val range = MaterialTheme.typography.labelMedium
    val buttonHeight = with(LocalDensity.current) { (small.lineHeight.value + range.lineHeight.value).sp.toDp() }
    // Build 122: on a phone set to another zone the times say whose time they are, with the day.
    val rangeText = window?.let { w -> tr("%1\$s – %2\$s", clockTime(w.from), clockTime(w.until)) +
        (dev.ambivalenton.openv.data.model.transitZoneNote(w.from)?.let { " · $it" }.orEmpty()) }
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().calmContentSize(spring(dampingRatio = .8f, stiffness = 420f,
        visibilityThreshold = androidx.compose.ui.unit.IntSize.VisibilityThreshold)).testTag("chart-query-header"),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            if (expanded) {
                FittedText(query.destination.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                if (favorite != null && favorite != query.destination.name)
                    Text(favorite, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(tr("Ab %1\$s", query.origin.name), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (rangeText != null) Text(rangeText, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
            } else {
                Text(favorite ?: query.destination.name, Modifier.testTag("chart-query-destination"), style = small,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (rangeText != null) Text(rangeText, style = range, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        val shape = dev.ambivalenton.openv.ui.theme.appCorner(12.dp)
        Box(Modifier.height(buttonHeight).widthIn(min = 48.dp)
            .clip(shape).background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = if (expanded) tr("Suchangaben einklappen") else tr("Suchangaben ausklappen")) { expanded = !expanded }
            .semantics { stateDescription = if (expanded) tr("Ausgeklappt") else tr("Eingeklappt") }
            .testTag("chart-query-toggle"), contentAlignment = Alignment.Center) {
            AppIcon(R.drawable.ic_expand, if (expanded) tr("Suchangaben einklappen") else tr("Suchangaben ausklappen"),
                Modifier.size(20.dp).graphicsLayer { rotationZ = turn }, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** H06: another built-in service whose region fits this search (only when a build offers several), as its short name. */
internal fun coverageAlternative(ui: AppUiState): String? {
    val query = ui.query ?: return null
    val engine = dev.ambivalenton.openv.data.model.TimetableProfile.alternativeEngine(query.origin, query.destination, ui.store.settings.engine)
        ?: return null
    return dev.ambivalenton.openv.data.model.TimetableProfile.shortName(engine)
}
