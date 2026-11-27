// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.screens

import androidx.compose.animation.togetherWith

import androidx.compose.ui.input.nestedscroll.nestedScroll

import dev.ambivalenton.openv.ui.components.ThemedButton
import dev.ambivalenton.openv.ui.components.ThemedTonalButton

import dev.ambivalenton.openv.ui.components.AppModalBottomSheet as ModalBottomSheet

import dev.ambivalenton.openv.localization.tr

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import dev.ambivalenton.openv.ui.network.ConnectPickerDialog
import dev.ambivalenton.openv.ui.network.EdgeDirectionDialog
import dev.ambivalenton.openv.ui.network.NetworkEditBarGeometry
import dev.ambivalenton.openv.ui.network.NetworkPlanFooter
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.searchRouting
import dev.ambivalenton.openv.data.preferences.historyRouteKey
import dev.ambivalenton.openv.data.preferences.mergeSearchHistory
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    ui: AppUiState,
    vm: AppViewModel,
    onSearch: (Location, Long, TimeMode) -> Unit,
    onFavorite: (String, Long, TimeMode) -> Unit,
    onPermission: () -> Unit,
    onHistory: (SearchHistory) -> Unit = vm::searchHistory,
    recoverablePins: List<RecoverablePin> = emptyList(),
    activePin: PinnedJourneySession?=null,
    onRecoverPreview: (RecoverablePin)->Unit={},
    onRecoverPin: (RecoverablePin)->Unit={},
    pinTransition: PinTransitionController?=null,
    onClearRecoverable: ()->Unit={},
    onCachedSearch:(String)->Unit=vm::openCachedSearch,
    onSavedPreview:(SavedJourney)->Unit={},
    onSavedPin:(SavedJourney)->Unit={},
    countdown:dev.ambivalenton.openv.ui.NetworkCountdown=dev.ambivalenton.openv.ui.NetworkCountdown(),
    /** Search the next connection and pin it. */
    onPinFavorite:(String)->Unit={},
    pinnedState:PinnedJourneyState?=null,
    /** H12: a confirmed deletion; the caller offers "Rückgängig". */
    onFavoriteRemoved:(FavoriteRemoval)->Unit={},
    /** M01/M17: a search with an explicit start (one-off start, a line in a named direction). */
    onSearchFrom:(Location,Location,Long,TimeMode,JourneyQuery?)->Unit={origin,target,time,mode,query->vm.search(target,time,mode,origin,query)},
    /** The search page is the visible page (it may be composed beside the others). */
    active:Boolean=true,
    /** H01/M12/N01: a new value moves the focus into the destination field ("Ziel ändern", first search). */
    focusDestination:Int=0,
    /** H02: a new value opens the start field and asks for a start ("Wähle einen Start für diese Fahrt."). */
    askForStart:Int=0,
    /** H02: Android no longer asks for the location permission; the system settings are offered instead. */
    locationDeniedForGood:Boolean=false,
    /** M03: a new value opens the editor for a new favourite ("Häufiges Ziel speichern"). */
    addFavorite:Int=0,
    /** A favourite or line searched from the typed start (null: the app's start). */
    onFavoriteFrom:(String,Long,TimeMode,Location?)->Unit={id,time,mode,_->onFavorite(id,time,mode)},
    /** Build 106: the plan is being edited (the navigation pill steps aside meanwhile). */
    onPlanEditing:(Boolean)->Unit={},
) {
    var destination by
        rememberSaveable(stateSaver = locationSaver) { mutableStateOf<Location?>(null) }
    var selectedTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var mode by rememberSaveable { mutableStateOf(TimeMode.DEPARTURE) }
    // The start grows out of its own chip instead of opening a sheet.
    var originOpen by rememberSaveable { mutableStateOf(false) }
    var originBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var destinationBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // 2.3: the typed text of the destination field (the arrow appears once it holds text).
    var destinationText by rememberSaveable { mutableStateOf(destination?.name.orEmpty()) }
    // H02/2.3/N01: one short hint below the fields, and which field gets the focus.
    var startHint by rememberSaveable { mutableStateOf<String?>(null) }
    var destinationHint by rememberSaveable { mutableStateOf<String?>(null) }
    var sameHint by rememberSaveable { mutableStateOf(false) }
    var startFocus by remember { mutableIntStateOf(0) }
    var startClear by remember { mutableIntStateOf(0) }
    // Only the pull gesture empties the manual input; a later opening by tap keeps the place.
    LaunchedEffect(originOpen) { if (!originOpen) startClear = 0 }
    val destinationFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // The start is a field of its own above the destination.
    val planner = ui.store.settings.plannerMode
    var plannerStart by rememberSaveable(stateSaver = locationSaver) {
        mutableStateOf(ui.store.settings.manualOrigin?.takeIf { it.routable && !it.isDevicePosition }) }
    var plannerFields by rememberSaveable { mutableIntStateOf(0) }
    // Build 106: a favourite held on the plan becomes the start; the start field is renewed to show it.
    var plannerStartPicked by rememberSaveable { mutableIntStateOf(0) }
    val plannerStartFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val plannerMotion = dev.ambivalenton.openv.ui.components.rememberPlannerSwapMotion()
    val plannerCalm = dev.ambivalenton.openv.ui.components.plannerMotionCalm()
    var swapGhosts by remember { mutableStateOf<dev.ambivalenton.openv.ui.components.SwapGhosts?>(null) }
    var swapProgress by remember { mutableFloatStateOf(0f) }
    var swapPendingStart by remember { mutableStateOf<Location?>(null) }
    val swapScope = rememberCoroutineScope()
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    var controlsHeight by remember { mutableIntStateOf(0) }
    androidx.activity.compose.BackHandler(originOpen) { originOpen = false }
    var edit by remember { mutableStateOf<Favorite?>(null) }
    var editing by rememberSaveable {mutableStateOf(false)}
    val reportEditing by rememberUpdatedState(onPlanEditing)
    DisposableEffect(editing) { reportEditing(editing); onDispose { reportEditing(false) } }
    var edgeMenu by rememberSaveable {mutableStateOf<String?>(null)}
    var connectFrom by rememberSaveable {mutableStateOf<String?>(null)}
    var trayPick by rememberSaveable {mutableStateOf<String?>(null)}
    var addAt by remember {mutableStateOf<GridPoint?>(null)}
    var removedEdge by remember {mutableStateOf<Pair<NetworkEdge,Int>?>(null)}
    // Build 104: the delete zone of the edit bar, shared with the plan while a favourite is moved.
    val deleteZone = remember { dev.ambivalenton.openv.ui.network.NetworkDeleteZone() }
    var deletingFavorite by remember {mutableStateOf<Favorite?>(null)}
    // M17: a tap between close targets on the plan asks which one is meant.
    var targetChoice by remember {mutableStateOf<Pair<List<String>,List<String>>?>(null)}
    androidx.activity.compose.BackHandler(editing) {editing=false;trayPick=null}
    // Build 122: as long as Android recommends for a control (accessibility "Zeit zum Reagieren"), at least 6 s.
    val undoContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(removedEdge) {if(removedEdge!=null){kotlinx.coroutines.delay(dev.ambivalenton.openv.ui.network.recommendedUndoMillis(undoContext));removedEdge=null}}
    var add by rememberSaveable { mutableStateOf(false) }
    var addLocation by rememberSaveable(stateSaver=locationSaver) {mutableStateOf<Location?>(null)}
    LaunchedEffect(addFavorite) { if (addFavorite > 0) { addAt = null; edit = null; addLocation = null; add = true } }
    var revealSerial by rememberSaveable {mutableIntStateOf(0)}
    var revealedSerial by rememberSaveable {mutableIntStateOf(0)}
    var actionMounted by remember {mutableStateOf(false)}
    LaunchedEffect(destination?.routable) {if(destination?.routable==true)actionMounted=true}
    // The place chosen in the field. The map stays centred on it while its pin moves the destination;
    // it is kept while the map folds away.
    var mapAnchor by rememberSaveable(stateSaver = locationSaver) { mutableStateOf(destination?.takeIf { it.routable }) }
    var mapPin by remember { mutableStateOf(destination?.takeIf { it.routable }) }
    LaunchedEffect(destination) { if (destination?.routable == true) mapPin = destination }
    // M04: a tap on the map is a draft; only "Kartenpunkt verwenden" makes it the destination.
    // Height of the search area (button and map) as a share of its full height; the favourites follow it.
    val areaWanted = destination?.routable == true
    val motionOff = ui.store.settings.reduceMotion || !android.animation.ValueAnimator.areAnimatorsEnabled()
    val area = remember { androidx.compose.animation.core.Animatable(if (areaWanted) 1f else 0f) }
    val unfold = remember { androidx.compose.animation.core.Animatable(if (areaWanted) 1f else 0f) }
    LaunchedEffect(areaWanted, motionOff) {
        val target = if (areaWanted) 1f else 0f
        if (motionOff) area.snapTo(target) else area.animateTo(target, if (areaWanted) searchAreaSpring else searchListRiseSpring)
    }
    LaunchedEffect(revealSerial, areaWanted, motionOff) {
        if (!areaWanted) return@LaunchedEffect
        // The map unfolds when a place was chosen (not again after returning to the screen).
        if (motionOff || revealedSerial == revealSerial) unfold.snapTo(1f)
        else { unfold.snapTo(0f); kotlinx.coroutines.delay(90); unfold.animateTo(1f, searchMapUnfoldSpring) }
    }
    val areaOpen by remember { derivedStateOf { area.value > .001f } }
    var destinationEdge by remember {mutableStateOf<Offset?>(null)}
    var manage by rememberSaveable { mutableStateOf(false) }
    // M07: which list a delete action is about; the dialog states its scope.
    var clearing by rememberSaveable { mutableStateOf<ClearScope?>(null) }
    val grid=rememberLazyListState()
    val now = rememberWallClock(tickMillis=if(pinnedState?.session!=null || activePin!=null) 1_000 else 60_000)
    val recoveries=eligibleRecoverablePins(recoverablePins,now.toInstant().toEpochMilli(),activePin,ui.store.presence)
    val settings = ui.store.settings
    // As soon as a routable destination is chosen (the search button forms as a drop), the
    // next connection and the one before it are searched in the background, so that the chart opens
    // with two bars. Only while this page is visible; the view model skips repeats and odd cases.
    val routing = settings.searchRouting()
    LaunchedEffect(destination, selectedTime, mode, routing, settings.powerSaving, active, swapPendingStart, planner) {
        // 2.3: while a swap is being written, the old start must not be pre-searched with the new destination.
        // Planermodus: the pre-search would start from the app's start, not from the typed one.
        if (active && swapPendingStart == null && !planner) vm.prefetchSearch(destination?.takeIf { it.routable }, selectedTime, mode)
        // M15: a pre-search belongs to the visible search form; leaving it ends the request.
        else if (!active || planner) vm.cancelPrefetch()
    }
    val saved=ui.store.savedJourneys.filter {it.available(now.toInstant().toEpochMilli())}
    LaunchedEffect(ui.store.savedJourneys,now.toInstant().epochSecond/60) {
        if(saved.size!=ui.store.savedJourneys.size)vm.expireSavedJourneys(now.toInstant().toEpochMilli())
    }
    val history = remember(ui.store.history) { mergeSearchHistory(ui.store.history) }
    val cached=ui.cachedSearches.filter {it.valid(now.toInstant().toEpochMilli()) &&
        it.provider==dev.ambivalenton.openv.data.repository.journeyCacheProvider(settings) && it.query.products==settings.products}
    var exactTick by remember { mutableIntStateOf(0) }
    // The favourite the app calculates from right now ("in der Nähe", virtual location, time window).
    // Start chip and the plan's mark come from this one evaluation of the current store (build 69);
    // the overview's own anchor is refreshed only while the overview tab is open and went stale here.
    // Judged at the real time of this recomposition (a new fix or the minute tick causes it): the
    // minute clock lags behind a fix that arrives mid-minute, and such a fix counted as not fresh.
    val context = androidx.compose.ui.platform.LocalContext.current
    val gpsOn = settings.originMode == OriginMode.AUTOMATIC && locationPermissionGranted(context)
    val anchorNodeId = exactTick.let {
        val clock = maxOf(now.toInstant().toEpochMilli(), System.currentTimeMillis())
        // Build 121: with the location in use only a real fix snaps to a favourite (not its time window).
        val position = if (gpsOn) ui.store.presence?.takeIf { it.isFresh(clock) }?.location else ui.store.routingPosition(clock)
        ui.store.currentAnchor(clock, position)?.nodeId
            ?.takeIf { it != ProximityMemory.suppressedNodeId }
    }
    val snapped = anchorNodeId?.takeIf { ui.store.originSource() == OriginSource.DEVICE }
        ?.let { id -> ui.store.favorites.firstOrNull { it.id == id } }
    val overrideNode = ui.store.locationOverride?.takeIf { ui.store.originSource() == OriginSource.OVERRIDE }
        ?.let { o -> ui.store.favorites.firstOrNull { it.id == o.nodeId } }
    // Time-dependent location: a favourite's time window supplies the start (fallback behind GPS).
    val scheduledNode = ui.store.scheduledStart(now.toInstant().toEpochMilli())
    // Build 121: with the location in use, the search measures the position itself (AppViewModel.origin); a
    // favourite's time window only stands in when the location is off. Before, a time window took over while
    // the last fix was a few minutes old – swapping then made that favourite the destination, and choosing
    // "my location" afterwards ended in "Start und Ziel sind gleich" although the person was elsewhere.
    val windowStart = scheduledNode?.takeIf { !gpsOn }
    val start =
        swapPendingStart?.name ?: if (overrideNode != null) tr("Standort: %1\$s", overrideNode.label)
        else if (windowStart != null) tr("%1\$s (Zeitfenster)", windowStart.label)
        else if (settings.originMode == OriginMode.MANUAL) settings.manualOrigin?.name ?: tr("Start wählen")
        else if (snapped != null) tr("%1\$s (in der Nähe)", snapped.label)
        else tr("Dein Standort")
    val navigation = dev.ambivalenton.openv.ui.components.LocalNavigationClearance.current
    val nowMillis = now.toInstant().toEpochMilli()
    // 2.3/N01/H02: the start in effect for the next search (a GPS start as the device position).
    val effectiveStart: Location? = when {
        overrideNode != null -> overrideNode.location
        windowStart != null -> windowStart.location
        settings.originMode == OriginMode.MANUAL -> settings.manualOrigin?.takeIf { it.routable && !it.isDevicePosition }
        snapped != null -> snapped.location
        // The device's own fix only (the view model's position may be a favourite standing in for it).
        else -> ui.store.presence?.takeIf { it.isFresh(nowMillis) }?.location
            ?.takeIf { it.hasCoordinates }?.copy(isDevicePosition = true)
    }
    val latestPresence by rememberUpdatedState(ui.store.presence)
    val startMissing = effectiveStart == null && (settings.originMode == OriginMode.MANUAL || !locationPermissionGranted(context))
    // Build 128: the handlers below are kept by Compose (the search button holds "::startSearch" from an
    // earlier composition). They read the start through these states, so a tap always uses the start on
    // screen – before, a swap compared the new destination with the old start ("Start und Ziel sind gleich")
    // and a start chosen afterwards was not used.
    val currentStart by rememberUpdatedState(effectiveStart)
    val currentStartMissing by rememberUpdatedState(startMissing)
    val currentStartLabel by rememberUpdatedState(start)
    val currentGpsOn by rememberUpdatedState(gpsOn)
    val currentOverride by rememberUpdatedState(overrideNode)
    val currentPlanner by rememberUpdatedState(planner)
    val currentMotionOff by rememberUpdatedState(motionOff)
    val currentFavorites by rememberUpdatedState(ui.store.favorites)
    val currentNetwork by rememberUpdatedState(ui.store.network)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val currentOnSearchFrom by rememberUpdatedState(onSearchFrom)
    val currentOnFavoriteFrom by rememberUpdatedState(onFavoriteFrom)
    // Build 118: each request is carried out once. The page is rebuilt whenever the pager returns to it;
    // a request repeated then took the focus again and could pull the pages along.
    var handledDestinationFocus by rememberSaveable { mutableIntStateOf(0) }
    var handledStartRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(focusDestination) {
        if (focusDestination > handledDestinationFocus) {
            handledDestinationFocus = focusDestination
            kotlinx.coroutines.delay(120); runCatching { destinationFocus.requestFocus() }
        }
    }
    LaunchedEffect(askForStart) {
        if (askForStart <= handledStartRequest) return@LaunchedEffect
        handledStartRequest = askForStart
        if (askForStart > 0 && planner) {
            // Planermodus: the start field above the destination takes the focus.
            startHint = tr("Wähle einen Start für diese Fahrt."); kotlinx.coroutines.delay(120); runCatching { plannerStartFocus.requestFocus() }
        } else if (askForStart > 0) { originOpen = true; startHint = tr("Wähle einen Start für diese Fahrt."); startFocus++ }
    }
    LaunchedEffect(effectiveStart?.name, effectiveStart?.id) { if (effectiveStart != null) startHint = null }
    LaunchedEffect(destination, effectiveStart?.name) { sameHint = false }
    // 2.3: the swap is written in one settings update; until it lands, a second tap waits.
    LaunchedEffect(settings.manualOrigin, settings.originMode, swapPendingStart) {
        val pending = swapPendingStart ?: return@LaunchedEffect
        if (settings.originMode == OriginMode.MANUAL && settings.manualOrigin == pending) swapPendingStart = null
        else { kotlinx.coroutines.delay(1_500); swapPendingStart = null }
    }
    var swapResolving by remember { mutableStateOf(false) }
    fun focusDestinationField() { swapScope.launch { kotlinx.coroutines.delay(60); runCatching { destinationFocus.requestFocus() } } }
    fun askStart(text: String) { originOpen = true; startHint = text; startFocus++ }
    /** 2.3: both changes at once; the next search uses them, "Verbindung suchen" starts it. */
    fun performSwap(target: Location, from: Location? = currentStart) {
        when (val plan = swapPlan(target, from, System.currentTimeMillis())) {
            SwapPlan.NeedsStart -> askStart(tr("Wähle zuerst einen Startort."))
            SwapPlan.NeedsDestination -> { destinationHint = tr("Wähle zuerst einen Ort aus den Vorschlägen."); focusDestinationField() }
            is SwapPlan.Swap -> {
                val ghosts = originBounds?.let { o -> destinationBounds?.let { d -> SwapGhosts(d, o, target.name, currentStartLabel, originGps = currentGpsOn) } }
                startHint = null; destinationHint = null; sameHint = false
                destination = plan.newDestination; destinationText = plan.newDestination.name
                mapAnchor = plan.newDestination
                swapPendingStart = plan.newStart
                vm.cancelPrefetch()
                // A start held on the plan would win over the new manual start.
                currentOverride?.let { vm.toggleLocationOverride(it.id) }
                vm.settings { it.copy(originMode = OriginMode.MANUAL, manualOrigin = plan.newStart) }
                if (ghosts != null && !currentMotionOff) {
                    swapProgress = 0f
                    swapGhosts = ghosts
                    swapScope.launch {
                        try {
                            androidx.compose.animation.core.animate(0f, 1f, animationSpec = SwapMotionSpring) { value, _ ->
                                swapProgress = value
                            }
                        } finally { swapGhosts = null }
                    }
                }
            }
        }
    }
    fun swapPlaces() {
        // Quick repeated taps: one swap at a time, each on the state the previous one left.
        if (swapPendingStart != null || swapGhosts != null || swapResolving) return
        val chosen = destination?.takeIf { it.routable }
        // Build 121: the location is in use but no fresh fix is there yet: measure it first, then swap.
        if (chosen != null && currentGpsOn && currentStart == null && currentOverride == null) {
            swapResolving = true
            vm.acquirePosition()
            swapScope.launch {
                try {
                    val fix = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                        snapshotFlow { latestPresence?.takeIf { it.isFresh(System.currentTimeMillis()) && it.location.hasCoordinates } }
                            .filterNotNull().first()
                    }
                    if (fix != null) performSwap(chosen, fix.location.copy(isDevicePosition = true))
                    else askStart(tr("Dein Standort ist gerade nicht verfügbar. Wähle einen Start."))
                } finally { swapResolving = false }
            }
            return
        }
        if (chosen != null) { performSwap(chosen); return }
        val typed = destinationText.trim()
        if (typed.isEmpty()) return
        swapResolving = true
        swapScope.launch {
            try {
                val resolved = runCatching { vm.findLocations(typed) }.getOrNull()?.let { unambiguousPlace(typed, it) }
                if (resolved != null) performSwap(resolved)
                else { destinationHint = tr("Wähle zuerst einen Ort aus den Vorschlägen."); focusDestinationField() }
            } finally { swapResolving = false }
        }
    }
    fun focusPlannerStart() { swapScope.launch { kotlinx.coroutines.delay(60); runCatching { plannerStartFocus.requestFocus() } } }
    /** Planermodus: both places change sides at once; the fields then turn around the button. */
    fun plannerSwap() {
        if (plannerMotion.running) return
        val oldStart = plannerStart; val oldDestination = destination
        if (oldStart == null && oldDestination == null) return
        startHint = null; destinationHint = null; sameHint = false
        plannerStart = oldDestination; destination = oldStart; destinationText = oldStart?.name.orEmpty()
        if (oldStart?.routable == true) mapAnchor = oldStart
        plannerFields++
        swapScope.launch { plannerMotion.play(plannerCalm) }
    }
    /** Planermodus: a favourite or a line, searched from the typed start. */
    fun plannerFavorite(id: String, time: Long, mode: TimeMode) {
        val line = currentNetwork.edges.any { it.id == id }
        val origin = plannerStart?.takeIf { it.routable }
        if (origin == null && !line) { startHint = tr("Wähle einen Start für diese Fahrt."); focusPlannerStart(); return }
        currentOnFavoriteFrom(id, time, mode, origin)
    }
    val favoriteSearch: (String, Long, TimeMode) -> Unit = if (planner) ::plannerFavorite else onFavorite
    /** Planermodus: holding a favourite on the plan enters it as the start in the field above. */
    fun plannerStartFrom(id: String) {
        val favorite = currentFavorites.firstOrNull { it.id == id } ?: return
        plannerStart = favorite.location; startHint = null; sameHint = false
        plannerStartPicked++
    }
    fun startSearch() {
        val target = destination?.takeIf { it.routable }
        if (currentPlanner) {
            val origin = plannerStart?.takeIf { it.routable }
            when {
                target == null -> { destinationHint = tr("Wähle einen Ort aus den Vorschlägen."); focusDestinationField() }
                origin == null -> { startHint = tr("Wähle einen Start für diese Fahrt."); focusPlannerStart() }
                routingSameLocation(origin, target, null) -> { sameHint = true; focusDestinationField() }
                else -> currentOnSearchFrom(origin, target, selectedTime ?: System.currentTimeMillis(), mode, null)
            }
            return
        }
        when {
            target == null -> { destinationHint = tr("Wähle einen Ort aus den Vorschlägen."); focusDestinationField() }
            // H02: no start: the start field opens with its own hint instead of an error page.
            currentStartMissing -> askStart(tr("Wähle einen Start für diese Fahrt."))
            // N01: no timetable request for the same place; the destination field is the place to fix it.
            currentStart?.let { routingSameLocation(it, target, latestPresence) } == true -> { sameHint = true; focusDestinationField() }
            else -> currentOnSearch(target, selectedTime ?: System.currentTimeMillis(), mode)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()
        .onGloballyPositioned { screenOrigin = it.positionInRoot() }
        // A touch anywhere outside the open start field folds it back; the touch itself still counts.
        .pointerInput(originOpen) {
            if (!originOpen) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (originBounds?.contains(down.position) != true) originOpen = false
            }
        }) {
    val viewport = maxHeight
    // Pulled down at the top with resistance until it gives way: the start field opens for
    // a manual place, GPS is switched off and the keyboard comes up in the input.
    val pullStart = dev.ambivalenton.openv.ui.components.rememberPullToManualStart(
        canPull = { !grid.canScrollBackward && !editing && !settings.plannerMode },
        onRelease = {
            vm.settings { it.copy(originMode = OriginMode.MANUAL) }
            originOpen = true; startHint = null; startFocus++; startClear++
        })
    if (pullStart.travel > 0f) Box(Modifier.fillMaxWidth().padding(top = 8.dp).zIndex(2f)
        .graphicsLayer { alpha = (pullStart.progress * 1.4f).coerceIn(0f, 1f); translationY = pullStart.offset * .35f },
        contentAlignment = Alignment.TopCenter) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(R.drawable.ic_place, null, Modifier.size(18.dp).graphicsLayer {
                val p = pullStart.progress; scaleX = .8f + .4f * p; scaleY = scaleX }, tint = MaterialTheme.colorScheme.primary)
            Text(if (pullStart.progress >= 1f) tr("Start von Hand eingeben") else tr("Zum Eingeben des Starts ziehen"),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().nestedScroll(pullStart.connection).graphicsLayer { translationY = pullStart.offset }
            .testTag("search-list"),
        state=grid,
        contentPadding = PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=8.dp+navigation),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "search-controls") {
            // Below the controls lies the search area (map and button). Its height grows and shrinks
            // with a spring and the favourites follow it directly; the map folds with them.
            val density = LocalDensity.current
            val placement = searchActionPlacement(viewport, navigation, with(density) { controlsHeight.toDp() })
            val areaFull = with(density) { (16.dp + placement.height).toPx() }
            val showArea = areaWanted || actionMounted || areaOpen
            val anchor = mapAnchor?.takeIf { it.hasCoordinates }
            val showMap = showArea && anchor != null && !settings.powerSaving && placement.mapHeight > 0.dp
            val editorMotion = rememberSearchEditorMotion(editing, revealSerial.takeIf { showMap }, showMap && areaWanted, motionOff) {
                unfold.value == 1f && !unfold.isRunning && area.value == 1f && !area.isRunning
            }
            val creaseDepth = minOf(32.dp, placement.mapHeight * .13f)
            // Borrow just enough of the vacated mode row to clear the toolbar. The sheet keeps its
            // measured size, so editing never zooms the map or moves the network plan below.
            val lift = (placement.mapHeight + 8.dp + NetworkEditBarGeometry.INFO_HEIGHT - 16.dp - placement.height)
                .coerceIn(0.dp, 48.dp)
            val intoModeRow = if (showMap) Modifier.offset {
                IntOffset(0, -(lift.toPx() * editorMotion.paper.value * area.value.coerceIn(0f, 1f)).roundToInt())
            } else Modifier
            Column(Modifier.layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                val missing = if (showArea) areaFull * (1f - area.value) else 0f
                layout(p.width, (p.height - missing).roundToInt().coerceAtLeast(0)) { p.place(0, 0) }
            }) {
            Column(Modifier.onSizeChanged { controlsHeight = it.height },verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val providerKey = "${settings.engine}:${settings.geocodingEndpoint}"
                val destinationField: @Composable (Modifier) -> Unit = { fieldModifier ->
                PlaceSearchField(
                    tr("Ziel, Adresse oder Haltestelle"),
                    destination,
                    providerKey,
                    vm::findLocations,
                    {destination=it;destinationHint=null
                        if(it?.routable==true){mapAnchor=it;revealSerial++}},
                    modifier=fieldModifier.graphicsLayer { alpha = if (swapGhosts != null) swapFieldReveal(swapProgress) else 1f }
                        .onGloballyPositioned {val b=it.boundsInRoot();destinationEdge=Offset(b.center.x,b.bottom);destinationBounds=b.translate(-screenOrigin)},
                    onSuggestionLongClick={addLocation=it;edit=null;add=true},
                    expressive = true,
                    onTextChange = { destinationText = it },
                    focusRequester = destinationFocus,
                    onImeSearch = ::startSearch,
                )
                }
                // Start above the destination, the swap button on the seam.
                if (planner) {
                    dev.ambivalenton.openv.ui.components.PlannerFieldPair(plannerMotion, ::plannerSwap,
                        swapEnabled = plannerStart != null || destination != null,
                        first = { m -> key(plannerFields, plannerStartPicked) {
                            PlaceSearchField(tr("Start, Adresse oder Haltestelle"), plannerStart, providerKey, vm::findLocations,
                                { plannerStart = it; startHint = null },
                                modifier = m.testTag("planner-start-field"), expressive = true,
                                focusRequester = plannerStartFocus,
                                onImeSearch = { if (destination?.routable == true) startSearch() else focusDestinationField() })
                        } },
                        second = { m -> key(plannerFields) { destinationField(m) } })
                    startHint?.let { SearchHint(it, "search-start-hint") }
                } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // 2.3: the swap arrow takes the former filter button's place. It appears with a spring
                    // once the destination field holds text and pushes the start box to the left; without
                    // it the box may use the whole width (and stays as narrow as its text otherwise).
                    // Build 122: the swap button is always there (faint without a destination), so it is found.
                    val arrowShown = !originOpen
                    val swapReady = destinationText.isNotBlank()
                    val arrowRoom by androidx.compose.animation.core.animateDpAsState(if (arrowShown) 56.dp else 0.dp,
                        if (motionOff) androidx.compose.animation.core.snap() else spring(dampingRatio = .7f, stiffness = 380f), label = "swap-arrow-room")
                    SwapPlacesArrow(arrowShown, {
                        if (swapReady) swapPlaces()
                        else { destinationHint = tr("Wähle zuerst ein Ziel – dann tauscht dieser Knopf Start und Ziel."); focusDestinationField() }
                    }, Modifier.align(Alignment.TopEnd).offset(y = (-4).dp), ready = swapReady)
                    dev.ambivalenton.openv.ui.components.OriginMorphField(
                        label = start,
                        open = originOpen,
                        onOpen = { originOpen = true },
                        gpsOn = gpsOn,
                        onGps = { on ->
                            vm.settings { it.copy(originMode = if (on) OriginMode.AUTOMATIC else OriginMode.MANUAL) }
                            if (on) onPermission()
                        },
                        manual = settings.manualOrigin,
                        providerKey = "${settings.engine}:${settings.geocodingEndpoint}",
                        search = vm::findLocations,
                        onManual = { picked ->
                            vm.settings { it.copy(originMode = OriginMode.MANUAL, manualOrigin = picked) }
                            if (picked != null) { originOpen = false; startHint = null }
                        },
                        collapsedMaxWidth = maxWidth - arrowRoom,
                        focusRequest = startFocus,
                        clearRequest = startClear,
                        favorites = ui.store.favorites,
                        missing = startMissing,
                        modifier = Modifier.zIndex(1f).graphicsLayer { alpha = if (swapGhosts != null) swapFieldReveal(swapProgress) else 1f }
                            .onGloballyPositioned { originBounds = it.boundsInRoot().translate(-screenOrigin) },
                    )
                }
                startHint?.let { SearchHint(it, "search-start-hint") }
                destinationField(Modifier)
                }
                destinationHint?.let { SearchHint(it, "search-destination-hint") }
                if (sameHint) SameStartHint(onChange = {
                    destination = null; destinationText = ""; sameHint = false; focusDestinationField()
                })
                // The departure/arrival pill and the "Ab jetzt" area that springs out of the frame.
                ScheduleControls(selectedTime, { selectedTime = it }, mode, { mode = it },
                    onNow = { selectedTime = null; mode = TimeMode.DEPARTURE },
                    // Build 104: the open plan editor reaches up into this row; "Abfahrt/Ankunft" steps aside.
                    modeAway = editing, modeSlide = editorMotion.row)
                if (!planner && settings.originMode == OriginMode.AUTOMATIC && (ui.position.error != null || !locationPermissionGranted(context)))
                    androidx.compose.foundation.layout.FlowRow(intoModeRow.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                        Text(
                            if (locationPermissionGranted(context)) tr("Standort fehlt") else tr("Standort nicht freigegeben"),
                            Modifier.align(Alignment.CenterVertically).padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        // H02: "Meinen Standort nutzen" asks for the permission; after a final refusal Android
                        // no longer asks, so the system settings are offered as well.
                        if (!locationDeniedForGood) TextButton(onPermission, Modifier.heightIn(min = 48.dp)) { Text(tr("Meinen Standort nutzen")) }
                        else TextButton({ openAppDetailsSettings(context) }, Modifier.heightIn(min = 48.dp).testTag("open-android-settings")) { Text(tr("Android-Einstellungen öffnen")) }
                        TextButton({ askStart(tr("Wähle einen Start für diese Fahrt.")) }, Modifier.heightIn(min = 48.dp)) { Text(tr("Start wählen")) }
                    }
            }
            if (showArea) Box(intoModeRow.fillMaxWidth().height(16.dp + placement.height)) {
                // The destination map fills the space down to the navigation (not in power saving).
                val gap = with(density) { 16.dp.toPx() }
                val pinned = destination?.takeIf { it.routable } ?: mapPin
                val centre = anchor?.takeIf { showMap }
                val buttonHeight = dev.ambivalenton.openv.ui.components.searchButtonHeight()
                // The merged button stands 10 dp above the map's lower edge; without the map its lower
                // edge stays where the round button's was (the pill's height above the pill).
                val buttonOnMap = placement.mapHeight - dev.ambivalenton.openv.ui.components.SearchActionMapGap - buttonHeight
                if (centre != null && pinned != null) key(revealSerial, centre) {
                    DestinationMap(centre, pinned,
                        onPick = { picked -> if (destination?.routable == true) destination = picked },
                        unfold = { unfold.value },
                        visibleHeight = { areaFull * area.value - gap },
                        closing = !areaWanted,
                        editorCreasePx = { with(density) { creaseDepth.toPx() } * editorFold(editorMotion.paper.value) },
                        // The pin stands halfway between the map's upper edge and the button's.
                        pinCenterY = buttonOnMap / 2,
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(placement.mapHeight))
                }
                // The button falls far down and the favourites move behind the navigation pill;
                // its lower edge keeps the pill's own height above the pill. On small screens it stays below the controls.
                if (areaWanted || actionMounted) LiquidSearchAction(destinationEdge,revealSerial,
                    enabled=destination?.routable==true&&settings.products.isNotEmpty()&&ui.ready,animate=revealedSerial!=revealSerial,
                    visible=destination?.routable==true,onHidden={actionMounted=false},
                    onRevealed={revealedSerial=revealSerial},onClick=::startSearch,
                    label=searchButtonLabel(selectedTime,mode,nowMillis),
                    modifier=Modifier.padding(top = 16.dp).offset {
                        val travel = if (showMap) creaseDepth.toPx() * editorButtonTravel(editorMotion.paper.value) else 0f
                        IntOffset(0, -travel.roundToInt())
                    },height=placement.height,
                    buttonTop=if (showMap) buttonOnMap else placement.buttonTop + SEARCH_BUTTON_SIZE_DP.dp - buttonHeight)
            }
            }
        }
        item(key = "favorites-heading") {
            // The open bar spans the whole width and covers the heading.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                FittedText(tr("Favoriten"), Modifier.padding(end = 56.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                // The pencil morphs into a small toolbar (gear, plus, check) and back.
                // The gear opens the favourite settings, the plus "Favorit hinzufügen" directly.
                if (editing || ui.store.favorites.isNotEmpty())
                    dev.ambivalenton.openv.ui.network.NetworkEditBar(editing,
                        onEdit = { editing = true },
                        onSettings = { manage = true },
                        onAdd = { addAt = null; edit = null; addLocation = null; add = true },
                        onDone = { editing = false; trayPick = null },
                        modifier = Modifier.fillMaxWidth(),
                        deleteZone = deleteZone)
            }
        }
        if (ui.store.favorites.isEmpty())
            item(key = "first-favorite") {
                ThemedTonalButton(
                    { add = true },
                    Modifier.fillMaxWidth().heightIn(min = 72.dp)
                        ,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(tr("Ersten Favoriten hinzufügen"))
                }
            }
        else item(key = "network-plan") {
            // With "Strecken als Liste zeigen" the list stands where the plan is; the pencil
            // still edits the plan, and leaving the editing turns it back into the list.
            val asList = settings.favoriteListView && !editing
            androidx.compose.animation.AnimatedContent(asList, label = "plan-or-list",
                transitionSpec = {
                    if (settings.reduceMotion) androidx.compose.animation.fadeIn(androidx.compose.animation.core.snap()) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.snap())
                    else (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 90)) +
                        androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = .78f, stiffness = 380f), initialScale = .94f)) togetherWith
                        (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) +
                            androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(180), targetScale = 1.03f)) using
                        androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.spring(dampingRatio = .86f, stiffness = 340f) }
                }) { list ->
            if (list) dev.ambivalenton.openv.ui.network.NetworkRouteList(ui.store,
                onRoute = { route ->
                    val time = selectedTime ?: System.currentTimeMillis()
                    val query = ui.store.routeQuery(route.from, route.to, time, mode)
                    onSearchFrom(query.origin, query.destination, time, mode, query) },
                onFavorite = { favorite -> favoriteSearch(favorite.id, selectedTime ?: System.currentTimeMillis(), mode) })
            else Column(Modifier,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                dev.ambivalenton.openv.ui.network.NetworkPlan(ui.store, now, editing, anchorNodeId, countdown, trayPick,
                    dev.ambivalenton.openv.ui.network.NetworkPlanActions(
                        onNodeTap = { id -> if (editing) edit = ui.store.favorites.firstOrNull { it.id == id } else favoriteSearch(id, selectedTime ?: System.currentTimeMillis(), mode) },
                        // Einstellungen → Favoriten → "Favorit im Netzplan gedrückt halten".
                        onNodeHold = { id ->
                            when (settings.holdAction) {
                                NetworkHoldAction.LOCATION -> if (planner) plannerStartFrom(id) else vm.toggleLocationOverride(id)
                                NetworkHoldAction.PIN -> onPinFavorite(id)
                                NetworkHoldAction.OVERVIEW -> vm.toggleOverviewFavorite(id)
                                // MOVE is handled by the plan itself (onEditRequest); NOTHING does nothing.
                                NetworkHoldAction.MOVE, NetworkHoldAction.NOTHING -> {}
                            }
                        },
                        onEditRequest = { editing = true; trayPick = null },
                        onEdgeTap = { id -> favoriteSearch(id, selectedTime ?: System.currentTimeMillis(), mode) },
                        onEdgeMenu = { id -> edgeMenu = id },
                        onMove = { id, point -> vm.moveNode(id, point); if (trayPick == id) trayPick = null },
                        onConnect = { a, b -> vm.connectNodes(a, b) },
                        onFreePoint = { point -> addAt = point; edit = null; addLocation = null; add = true },
                        onToTray = { id -> vm.nodeToTray(id) },
                        onConnectRequest = { id -> connectFrom = id },
                        onAmbiguousTap = { nodes, lines -> targetChoice = nodes to lines },
                        // Build 104: let go above the delete zone – deleted at once, "Rückgängig" brings it back.
                        onDelete = { id -> vm.deleteFavoriteWithUndo(id)?.let(onFavoriteRemoved) },
                        // Build 119: a held line bursts and is removed; "Rückgängig" below the plan brings it back.
                        onEdgeDelete = { id -> removedEdge = vm.removeEdge(id); edgeMenu = null },
                    ), pinnedState=pinnedState ?: activePin?.let {PinnedJourneyState(session=it)}, deleteZone = deleteZone)
                NetworkPlanFooter(ui.store, editing, trayPick, { id -> trayPick = if (trayPick == id) null else id },
                    removedEdge?.let { removed -> { vm.restoreEdge(removed.first, removed.second); removedEdge = null } },
                    removedEdge?.first?.let { edge ->
                        val a = ui.store.favorites.firstOrNull { it.id == edge.aId }?.label
                        val b = ui.store.favorites.firstOrNull { it.id == edge.bId }?.label
                        if (a != null && b != null) tr("Linie %1\$s – %2\$s entfernt", a, b) else null
                    })
            }
            }
        }
        // An information line, so the minimalist mode leaves it out.
        if ((overrideNode != null || snapped != null) && !settings.minimalistMode && !planner)
            item(key = "start-hint") {
                Row(Modifier.fillMaxWidth()
                    .testTag("network-start-hint"), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (overrideNode != null) tr("Die ganze App rechnet ab %1\$s.", overrideNode.label)
                        else tr("Du bist in der Nähe von %1\$s. Die App rechnet ab dort.", snapped!!.label),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (overrideNode != null) TextButton({ vm.toggleLocationOverride(overrideNode.id) }, Modifier.testTag("override-clear")) { Text(tr("Aufheben")) }
                    else TextButton({ vm.useExactLocation(); exactTick++ }, Modifier.testTag("exact-location")) { Text(tr("Genauer Standort")) }
                }
            }
        if(saved.isNotEmpty()) {
            item(key="saved-journeys-heading"){FittedText(tr("Vorgemerkt"),Modifier,style=MaterialTheme.typography.titleLarge)}
            items(saved,key={"saved-${it.id}"}) {entry->Box{SavedJourneyCard(entry,{onSavedPreview(entry)},{vm.removeSavedJourney(entry.id)},onPin={onSavedPin(entry)})}}
        }
        if(!settings.minimalistMode && cached.isNotEmpty()) {
            item(key="recent-searches-heading") {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    FittedText(tr("Zuletzt gesucht"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    // M07: deleting names its scope first.
                    AnimatedIconButton({clearing=ClearScope.CACHE},R.drawable.ic_delete,tr("Zwischengespeicherte Fahrten löschen"),IconTapMotion.WIGGLE)
                }
            }
            items(cached,key={"cached-${it.id}"}) {entry->
                ListItem(headlineContent={Text(entry.query.destination.name,maxLines=2,overflow=TextOverflow.Ellipsis)},
                    supportingContent={Column {
                        Text(tr("Ab %1\$s",entry.query.origin.name),maxLines=2,overflow=TextOverflow.Ellipsis)
                        // K01: the confirmed stand, and a failed latest attempt.
                        Text(tr("%1\$s Verbindungen · Stand %2\$s",entry.trips.size,clockTime(entry.savedAt))+
                            if(entry.lastAttemptFailed) " · "+tr("Aktualisierung fehlgeschlagen") else "")
                        // M07: what a tap does.
                        Text(tr("Gespeicherte Fahrten ansehen"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    }},leadingContent={AppIcon(R.drawable.ic_clock,null)},
                    modifier=Modifier.fillMaxWidth().testTag("cached-search-${entry.id}")
                        .clickable(role=Role.Button,onClickLabel=tr("Gespeicherte Fahrten ansehen")) {onCachedSearch(entry.id)},
                    colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.background))
            }
        }
        if (!settings.minimalistMode && (history.isNotEmpty() || recoveries.isNotEmpty())) {
            item(key = "frequent-heading") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FittedText(tr("Häufig gesucht"), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(top = 4.dp))
                    AnimatedIconButton({clearing=ClearScope.HISTORY}, R.drawable.ic_delete, tr("Suchverlauf löschen"),
                        IconTapMotion.WIGGLE, tint = MaterialTheme.colorScheme.error)
                }
            }
            items(history.take(5), key = { historyRouteKey(it.query) }) { recent ->
                ListItem(
                    headlineContent = {
                        Text(
                            recent.query.destination.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                tr("Ab %1\$s", recent.query.origin.name),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // M07: a frequent route is searched again from now, from its saved start.
                            Text(tr("Route ab jetzt suchen"), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    leadingContent = { AppIcon(R.drawable.ic_clock, null) },
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                "${recent.count}×",
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth().clickable(
                            role = Role.Button,
                            onClickLabel = tr("Route ab jetzt suchen"),
                        ) {
                            onHistory(recent)
                        },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                )
            }
            items(recoveries,key={"recoverable-${it.session.id}"}) {candidate ->
                Box{RecoverablePinCard(candidate,pinTransition,{onRecoverPreview(candidate)},{onRecoverPin(candidate)})}
            }
        }
    }
    // 2.3: both fields as they swap, drawn over the hidden real fields.
    swapGhosts?.let { ghosts ->
        SwapGhostOverlay(ghosts, { swapProgress }, reveal = { swapFieldReveal(swapProgress) },
            landedOrigin = { originBounds ?: ghosts.originRect },
            landedDestination = { destinationBounds ?: ghosts.destinationRect })
    }
    }
    if (add || edit != null)
        FavoriteEditor(
            edit,
            "${settings.engine}:${settings.geocodingEndpoint}",
            vm::findLocations,
            {
                if (edit == null) {
                    // Build 101: a new favourite is stored at once; the sheet stays for the saved moment
                    // ("Noch einen Favoriten" places the next one freely, not on the same grid point).
                    if (addAt != null) vm.favoriteAt(it, addAt) else vm.favorite(it)
                    addAt = null
                } else {
                    vm.favorite(it)
                    add = false
                    edit = null
                    addLocation = null
                    addAt = null
                }
            },
            {
                add = false
                edit = null
                addLocation = null
                addAt = null
            },
            initialLocation=addLocation,
            // H12: deleting names what goes with it and is confirmed first.
            onDelete=edit?.let {favorite -> ({deletingFavorite=favorite})},
            networkRole=edit?.id?.let {id->ui.store.network.node(id)?.role},
            onNetworkRole=edit?.id?.let {id->{role:NodeRole->vm.setNodeRole(id,role)}},
            scheduleConflict={windows->ui.store.scheduleConflict(edit?.id,windows)},
            suggestions=remember(ui.store.history,ui.store.favorites) {ui.store.favoritePlaceSuggestions()},
            otherFavorites=ui.store.favorites.filter {it.id!=edit?.id},
            partnerFor={id->ui.store.connectionPartnerFor(id)},
            onConnect={a,b->vm.connectNodes(a,b)!=null},
        )

    targetChoice?.let { (nodes, lines) ->
        dev.ambivalenton.openv.ui.network.NetworkTargetChoiceDialog(ui.store, nodes, lines,
            onFavorite = { id -> targetChoice = null; favoriteSearch(id, selectedTime ?: System.currentTimeMillis(), mode) },
            onLine = { id -> targetChoice = null; favoriteSearch(id, selectedTime ?: System.currentTimeMillis(), mode) },
            onDismiss = { targetChoice = null })
    }
    deletingFavorite?.let { favorite ->
        val lines = ui.store.network.edges.count { it.touches(favorite.id) }
        AppAlertDialog(onDismissRequest = { deletingFavorite = null },
            title = { Text(when (lines) {
                0 -> tr("„%1\$s“ entfernen?", favorite.label)
                1 -> tr("„%1\$s“ und 1 Netzplanlinie entfernen?", favorite.label)
                else -> tr("„%1\$s“ und %2\$s Netzplanlinien entfernen?", favorite.label, lines)
            }) },
            text = { Text(tr("Direkt danach kannst du das Entfernen mit allen Verbindungen rückgängig machen.")) },
            confirmButton = { TextButton({
                val removal = vm.deleteFavoriteWithUndo(favorite.id)
                deletingFavorite = null; edit = null
                removal?.let(onFavoriteRemoved)
            }, Modifier.heightIn(min = 48.dp).testTag("favorite-delete-confirm")) { Text(tr("Entfernen"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ deletingFavorite = null }, Modifier.heightIn(min = 48.dp).testTag("favorite-delete-keep")) { Text(tr("Behalten")) } })
    }
    edgeMenu?.let { id ->
        EdgeDirectionDialog(ui.store, id, { mode -> vm.setEdgeDirection(id, mode); edgeMenu = null },
            { removedEdge = vm.removeEdge(id); edgeMenu = null }, { edgeMenu = null })
    }
    connectFrom?.let { from ->
        ConnectPickerDialog(ui.store, from, { to -> vm.connectNodes(from, to); connectFrom = null }, { connectFrom = null })
    }
    clearing?.let { scope ->
        ClearHistoryDialog(scope, history.size, cached.size, recoveries.size,
            onConfirm = { alsoCache, alsoPins ->
                if (scope == ClearScope.CACHE) vm.clearJourneyCache()
                else { vm.clearHistory(alsoCache); if (alsoPins) onClearRecoverable() }
                clearing = null
            }, onDismiss = { clearing = null })
    }
    // The same menu as Einstellungen → Favoriten, as a sheet that closes by pulling it down.
    if (manage) FavoriteSettingsSheet(ui.store,
        onAdd = { manage = false; addAt = null; edit = null; addLocation = null; add = true },
        onEdit = { manage = false; edit = it },
        onSettings = vm::settings, onGridSize = vm::setGridSize, onDismiss = { manage = false })
}



/** Spring of the favourites while the search button appears: they make room below it. It
 * moves the height of the search area (button and map), which the favourites follow directly. */
internal val searchAreaSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = .72f, stiffness = 380f)
/** Rising back after the destination was removed, with the same small bounce at the top but
 * about 0.1 s slower (stiffness 200 instead of 380: time scales with 1/√stiffness, ≈ 0.28 s → 0.38 s). */
internal val searchListRiseSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = .72f, stiffness = 200f)
/** The opening unfold of the destination map, with a small overshoot that springs back. */
internal val searchMapUnfoldSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = .5f, stiffness = 165f)

/** Where the search button sits in its area, how tall the area is and how tall the destination map
 * may be (pure, unit-tested). */
internal data class SearchActionPlacement(val buttonTop: androidx.compose.ui.unit.Dp, val height: androidx.compose.ui.unit.Dp,
    val mapHeight: androidx.compose.ui.unit.Dp = 0.dp)

/** Free space between the button's lower edge and the navigation pill's upper edge: the pill's own height. */
internal val SearchButtonAbovePill = dev.ambivalenton.openv.ui.components.PillNavigationHeight
/** Gap between the destination map and the navigation pill. */
internal val SearchMapAbovePill = 12.dp
/** Below this the map would be a strip; the search area then stays as it was. */
internal val SearchMapMinimumHeight = 180.dp
internal const val SEARCH_BUTTON_SIZE_DP = 64

internal fun searchActionPlacement(viewport: androidx.compose.ui.unit.Dp, navigation: androidx.compose.ui.unit.Dp,
    controls: androidx.compose.ui.unit.Dp): SearchActionPlacement {
    val top = 8.dp + controls + 16.dp
    // Upper edge of the visible pill, in list coordinates.
    val pill = viewport - navigation + dev.ambivalenton.openv.ui.components.PillNavigationTop
    val buttonBottom = pill - SearchButtonAbovePill
    val buttonTop = (buttonBottom - SEARCH_BUTTON_SIZE_DP.dp - top).coerceAtLeast(0.dp)
    // The favourites start behind the navigation pill, low enough that its rounded ends hide them too.
    val favourites = viewport - navigation + 32.dp
    val height = maxOf(buttonTop + LiquidSearchActionHeight + 12.dp, favourites - 16.dp - top)
    val map = (pill - SearchMapAbovePill - top).takeIf { it >= SearchMapMinimumHeight && it >= buttonTop + SEARCH_BUTTON_SIZE_DP.dp + 24.dp }
    return SearchActionPlacement(buttonTop, height, map ?: 0.dp)
}



/** H02/2.3: a short hint below a field, read once by TalkBack. */
@Composable internal fun SearchHint(text: String, tag: String) {
    Text(text, Modifier.fillMaxWidth().testTag(tag).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

/**
 * N01: start and destination are the same place. No timetable request is made; "Ziel ändern" leads
 * into the destination field. The arrival joke is only an extra line below the hint.
 */
@Composable internal fun SameStartHint(onChange: () -> Unit) {
    val joke = remember { sameLocationMessages.randomOrNull() }
    Surface(Modifier.fillMaxWidth().testTag("same-start-hint").semantics(mergeDescendants = true) {
            liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        shape = dev.ambivalenton.openv.ui.theme.appCorner(20.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Start und Ziel sind gleich."), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onChange, Modifier.heightIn(min = 48.dp).testTag("same-start-change")) { Text(tr("Ziel ändern")) }
            }
            joke?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** M07: the list a delete action belongs to. */
enum class ClearScope { HISTORY, CACHE }

/**
 * M07: deleting names its scope before anything is removed: "Nur Suchverlauf löschen" or "Auch
 * zwischengespeicherte Fahrten löschen"; ended companions are an extra, unticked choice.
 */
@Composable internal fun ClearHistoryDialog(scope: ClearScope, historyCount: Int, cacheCount: Int, pinCount: Int,
    onConfirm: (alsoCache: Boolean, alsoPins: Boolean) -> Unit, onDismiss: () -> Unit) {
    var alsoPins by rememberSaveable { mutableStateOf(false) }
    AppAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("clear-history-dialog"),
        title = { Text(if (scope == ClearScope.CACHE) tr("Zwischengespeicherte Fahrten löschen?") else tr("Suchverlauf löschen?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (scope == ClearScope.CACHE) Text(tr("%1\$s gespeicherte Suchen mit ihren Fahrten werden entfernt. Suchverlauf, Vormerkungen und Reisebegleitungen bleiben.", cacheCount))
                else {
                    Text(tr("%1\$s Einträge unter „Häufig gesucht“. Vormerkungen bleiben erhalten.", historyCount))
                    if (pinCount > 0) Row(Modifier.fillMaxWidth().toggleableRow(alsoPins) { alsoPins = it }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(alsoPins, null)
                        Text(tr("Auch %1\$s beendete Reisebegleitungen entfernen", pinCount))
                    }
                }
            }
        },
        confirmButton = {
            if (scope == ClearScope.CACHE) TextButton({ onConfirm(true, false) }, Modifier.testTag("clear-cache-confirm")) { Text(tr("Löschen")) }
            else Column(horizontalAlignment = Alignment.End) {
                TextButton({ onConfirm(false, alsoPins) }, Modifier.testTag("clear-history-only")) { Text(tr("Nur Suchverlauf löschen")) }
                if (cacheCount > 0) TextButton({ onConfirm(true, alsoPins) }, Modifier.testTag("clear-history-and-cache")) { Text(tr("Auch zwischengespeicherte Fahrten löschen")) }
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("Behalten")) } })
}

private fun Modifier.toggleableRow(value: Boolean, onChange: (Boolean) -> Unit): Modifier =
    this.then(Modifier.toggleable(value = value, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = onChange).heightIn(min = 48.dp))
