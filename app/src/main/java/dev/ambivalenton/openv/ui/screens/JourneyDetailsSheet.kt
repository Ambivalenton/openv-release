// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.screens

import dev.ambivalenton.openv.data.model.TripImpossibility
import dev.ambivalenton.openv.data.model.title
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import dev.ambivalenton.openv.data.model.waypointQueryForTrip

import dev.ambivalenton.openv.data.model.reduceMotion

import dev.ambivalenton.openv.localization.tr

import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.semantics
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.Trip
import dev.ambivalenton.openv.data.model.JourneyQuery
import dev.ambivalenton.openv.data.model.TimeMode
import dev.ambivalenton.openv.data.model.JourneyDetailsContext
import dev.ambivalenton.openv.data.model.journeyDetailsTitle
import dev.ambivalenton.openv.data.model.uniqueRenderKeys
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.tutorial.*

val JourneySheetOffsetKey=DirectSheetOffsetKey

/** The same route detail, swipe and share UI is used by search results and the overview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailsSheet(
    trips: List<Trip>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit = {},
    isListViewVisible: Boolean = false,
    onShowList: () -> Unit = {},
    onPinRequested: ((Trip)->Unit)?=null,
    pinTransition: PinTransitionController?=null,
    detailsContext: JourneyDetailsContext=JourneyDetailsContext.Comparison,
    journeyQuery:JourneyQuery?=null,
    onWaypointRouted:((JourneyQuery,Trip)->Unit)?=null,
    onBookmark:((Trip)->Unit)?=null,
    /** 2.2: journeys that left the current offer while their sheet was open, with the reason. */
    unavailable:Map<String,TripImpossibility> = emptyMap(),
    onSearchOther:(()->Unit)?=null,
    timetableSource:String?=null,
) {
    if (trips.isEmpty()) return
    val handOff = rememberJourneyHandOff()
    val waypointGateway=LocalWaypointRoutingGateway.current
    var waypointTarget by remember {mutableStateOf<String?>(null)}
    val editedTrips=remember {mutableStateMapOf<String,Trip>()}
    val pageIdentities=remember {mutableStateMapOf<String,String>()}
    val dismissOwner by rememberUpdatedState(onDismiss)
    val tutorialState = LocalTutorialState.current
    val tutorialPath = if (!isListViewVisible && onPinRequested != null && pinTransition != null)
        TutorialPaths.timeline else null
    val guideBlocking = tutorialPath?.let { tutorialState?.isBlocking(it.id) } == true
    var scrollTarget by remember {mutableStateOf<JourneySheetScrollTarget?>(null)}
    var headerHeight by remember {mutableStateOf(64.dp)}
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val density=LocalDensity.current
    DirectModalSheet({dismissOwner()},scrollTarget,"journey-details-sheet",gesturesEnabled=!guideBlocking && waypointTarget==null,headerHeight=headerHeight) {motion,requestDismiss->
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        FirstUseTutorialHost(tutorialPath,
            Modifier.fillMaxSize().testTag("timeline-tutorial-host"),modal=true) {
        AnimatedContent(targetState=isListViewVisible, modifier=Modifier.fillMaxSize(), label="timeline-view-transition",
            transitionSpec={if(quiet) (EnterTransition.None togetherWith ExitTransition.None) else fadeIn(tween(JOURNEY_TRANSITION_MILLIS)) togetherWith fadeOut(tween(JOURNEY_TRANSITION_MILLIS))}) { listVisible ->
        if (listVisible) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(title = { FittedText(tr("Verbindungen")) },windowInsets=WindowInsets(0,0,0,0),
                    modifier=Modifier.onSizeChanged {headerHeight=with(density) {it.height.toDp()}})
                TransitSourceAttribution(Modifier.padding(horizontal=16.dp),compact=true)
                val listScroll=rememberLazyListState()
                val listTarget=rememberJourneyScrollTarget(listScroll)
                SideEffect {if(isListViewVisible) scrollTarget=listTarget}
                LazyColumn(
                    state=listScroll,userScrollEnabled=false,
                    modifier=Modifier.weight(1f).testTag("connection-list").semantics {scrollBy {_,y->
                        motion.cancelContentFling();listScroll.dispatchRawDelta(y);true
                    }},
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val listKeys=uniqueRenderKeys(trips.map {it.id})
                    itemsIndexed(trips, key = { index, _ -> listKeys[index] }) { index, trip ->
                        Column(Modifier.testTag("connection-list-${trip.id}")) {
                            TripCard(trip, onToggle = { onSelected(trip.id) })
                        }
                    }
                }
            }
        } else {
            val detailPager =
                rememberPagerState(
                    initialPage = trips.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
                    pageCount = { trips.size },
                )
            LaunchedEffect(detailPager, trips.map { it.id }) {
                snapshotFlow { detailPager.settledPage }
                    .collect { page -> if (!motion.closing) trips.getOrNull(page)?.let { onSelected(it.id) } }
            }
            val pageKeys=uniqueRenderKeys(trips.map {pageIdentities[it.id] ?: it.id})
            HorizontalPager(
                state = detailPager,
                modifier =
                    Modifier.fillMaxSize().testTag("journey-details-pager"),
                key = { pageKeys.getOrElse(it) {trips[it].id} },
            ) { page ->
                val originalTrip=trips[page]
                val trip = editedTrips[originalTrip.id] ?: originalTrip
                // Read during composition: SideEffect alone does not subscribe to pager state.
                val settled=page==detailPager.settledPage
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val offset =
                            kotlin.math.abs(
                                (detailPager.currentPage - page) +
                                    detailPager.currentPageOffsetFraction
                            )
                        alpha = 1f - .25f * offset.coerceIn(0f, 1f)
                        scaleY = 1f - .015f * offset.coerceIn(0f, 1f)
                    }
                ) {
                    Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().onSizeChanged {headerHeight=with(density) {it.height.toDp()}}
                        .heightIn(min=64.dp).padding(start=16.dp,end=4.dp,top=4.dp,bottom=4.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        FittedText(journeyDetailsTitle(detailsContext,page,trips.size),
                            Modifier.weight(1f).testTag("journey-details-title"),style=MaterialTheme.typography.titleLarge)
                        WaypointPlusButton(waypointTarget==originalTrip.id,{waypointTarget=originalTrip.id},
                            enabled=settled && waypointGateway!=null && pinTransition?.busy!=true,
                            modifier=if(settled) Modifier.tutorialTarget("timeline-waypoint") else Modifier)
                        AnimatedIconButton({handOff.run(trip)},handOff.icon,handOff.label,IconTapMotion.POP,
                            enabled=!handOff.busy && pinTransition?.busy!=true,
                            modifier=if(settled) Modifier.tutorialTarget("timeline-share") else Modifier)
                        IconButton(requestDismiss,enabled=pinTransition?.busy!=true) {
                            AppIcon(R.drawable.ic_close,tr("Details schließen"))
                        }
                    }
                    if (timetableSource==null) TransitSourceAttribution(Modifier.padding(horizontal=16.dp),compact=true)
                    else Text(timetableSource,Modifier.padding(horizontal=16.dp),
                        style=MaterialTheme.typography.bodySmall)
                    Box(Modifier.weight(1f)) {
                    val detailsScroll=rememberScrollState()
                    LaunchedEffect(trip.id) {detailsScroll.scrollTo(0)}
                    val detailTarget=rememberJourneyScrollTarget(detailsScroll)
                    SideEffect {if(!isListViewVisible && settled) scrollTarget=detailTarget}
                    Column(
                        Modifier.fillMaxSize()
                            .testTag("journey-details-scroll-${trip.id}")
                            .verticalScroll(detailsScroll,enabled=false,overscrollEffect=null)
                            .semantics {
                                verticalScrollAxisRange=ScrollAxisRange({detailsScroll.value.toFloat()},{detailsScroll.maxValue.toFloat()})
                                scrollBy {_,y->motion.cancelContentFling();detailsScroll.dispatchRawDelta(y);true}
                            }
                            .padding(horizontal = 16.dp)
                            .padding(bottom = (if(onPinRequested!=null) 160.dp else 24.dp)+LocalNavigationClearance.current)
                    ) {
                        // 2.2: "Diese Fahrt ist nicht mehr verfügbar" with the reason and a new search.
                        unavailable[originalTrip.id]?.let {reason->
                            Surface(Modifier.fillMaxWidth().padding(bottom=12.dp).testTag("journey-unavailable")
                                .semantics(mergeDescendants=true) {liveRegion=LiveRegionMode.Polite},
                                shape=dev.ambivalenton.openv.ui.theme.appCorner(18.dp),color=MaterialTheme.colorScheme.errorContainer,
                                contentColor=MaterialTheme.colorScheme.onErrorContainer) {
                                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(tr("Diese Fahrt ist nicht mehr verfügbar"),style=MaterialTheme.typography.titleSmall)
                                    Text(reason.title(),style=MaterialTheme.typography.bodyMedium)
                                    onSearchOther?.let {TextButton(it,Modifier.heightIn(min=48.dp).testTag("journey-search-other")) {Text(tr("Andere Fahrt suchen"))}}
                                }
                            }
                        }
                        AnimatedContent(targetState=trip,label="waypoint-route-update",
                            transitionSpec={if(quiet) (EnterTransition.None togetherWith ExitTransition.None) else fadeIn(tween(220)) togetherWith fadeOut(tween(140))}) {visibleTrip->
                            // A single connection has no list to return to and nothing to swipe to.
                            TripCard(visibleTrip, initiallyExpanded = true, onToggle = onShowList, alwaysOpen = trips.size <= 1)
                        }
                    }
                    pinTransition?.let {PinMatrixOverlay(it)}
                    if(settled && onPinRequested!=null && pinTransition!=null) {
                        Box(Modifier.align(Alignment.BottomEnd).padding(end=8.dp,bottom=LocalNavigationClearance.current).tutorialTarget("timeline-pin")) {
                            PinFloatingActionButton(trip.id,pinTransition,onLongClick=onBookmark?.let {save->{save(trip)}}) {onPinRequested(trip)}
                        }
                    }
                    }
                    }
                    JourneySwipeHints(page>0,page<trips.lastIndex,Modifier.align(Alignment.Center).padding(horizontal=3.dp))
                }
            }
        }
        }
    }
    }
    }
    waypointTarget?.let {id->
        val base=trips.firstOrNull {it.id==id}
        val trip=editedTrips[id] ?: base
        if(trip!=null && waypointGateway!=null) {
            val query=waypointQueryForTrip(journeyQuery,trip)
            WaypointPlannerDialog(query,trip,waypointGateway,{waypointTarget=null}) {updatedQuery,result->
                editedTrips[id]=result
                if(onWaypointRouted!=null) pageIdentities[result.id]=pageIdentities[id] ?: id
                waypointTarget=null
                onWaypointRouted?.invoke(updatedQuery,result)
            }
        }
    }

}

