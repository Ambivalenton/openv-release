// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv

import dev.ambivalenton.openv.ui.hints.gestureHint

import kotlinx.coroutines.flow.distinctUntilChanged

import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

import dev.ambivalenton.openv.ui.theme.withSessionTheme

import androidx.lifecycle.lifecycleScope

import dev.ambivalenton.openv.ui.components.AppAlertDialog as AlertDialog
import dev.ambivalenton.openv.localization.tr

import android.Manifest
import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.ambivalenton.openv.ui.components.navigationPillAside
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.ambivalenton.openv.data.repository.PinnedJourneyController
import dev.ambivalenton.openv.tracking.PinnedJourneyService
import dev.ambivalenton.openv.widget.WidgetRefreshScheduler
import androidx.core.view.WindowCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.*
import dev.ambivalenton.openv.ui.tutorial.*
import dev.ambivalenton.openv.ui.onboarding.FirstLaunchOnboarding
import dev.ambivalenton.openv.ui.theme.appThemeBackground
import dev.ambivalenton.openv.ui.theme.appSystemHeader
import dev.ambivalenton.openv.ui.theme.appTopBarColors
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** A tapped tab rests once its page is within a pixel of its place. The default threshold
 * (1/100 px) kept the pages "moving" for about a quarter second after they visibly arrived, which
 * delayed the snap feedback of the pill and everything that waits for the settled page. */
internal val HOME_TAB_SPRING=androidx.compose.animation.core.spring(dampingRatio=.86f,stiffness=380f,visibilityThreshold=1f)

/** The tab a tap counts as already open. A running page change counts as its target, a
 * moving pager as none: [settledPage][PagerState.settledPage] only follows once the pages have come to
 * rest, so tapping back to the page being left was ignored until then. */
internal fun activeHomeTab(route:String,settledPage:Int,scrolling:Boolean,target:Int?):Int=when(route) {
    "results"->1
    "search"->target ?: if(scrolling) -1 else settledPage
    else->-1
}

private data class PendingPinRequest(val favorite: Favorite,val query: JourneyQuery,val trip: Trip,
    val routing: AppSettings,val expectedId: String?,val recoveryId: String?=null)

/** H09: a pin that was refused before anything changed (known cancellation, journey over). */
private data class PinBlocked(val query: JourneyQuery,val reason: TripImpossibility,val keepsActive: Boolean)

internal data class LinkCommand(
    val nonce: Long,
    val favoriteId: String? = null,
    val settings: Boolean = false,
    val search: Boolean = false,
    val departures: Boolean = false,
    val overview: Boolean = false,
    val pinnedId: String? = null,
    val boardJourneyToken:String? = null,
    /** M10: open the departures' stop sheet to choose a fixed stop. */
    val chooseFixedStop: Boolean = false,
)

internal fun parseLink(intent: Intent, nonce: Long): LinkCommand? {
    if(intent.hasExtra(BOARD_JOURNEY_EXTRA))return intent.getStringExtra(BOARD_JOURNEY_EXTRA)
        ?.takeIf {it.matches(Regex("[a-f0-9]{64}"))}?.let {LinkCommand(nonce,boardJourneyToken=it)}
    intent.getStringExtra(PinnedJourneyService.EXTRA_PINNED_ID)
        ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) }
        ?.let { return LinkCommand(nonce, overview = true, pinnedId = it) }
    val uri =
        intent.data
            ?: return intent
                .getStringExtra("favorite_id")
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) }
                ?.let { LinkCommand(nonce, it) }
    if (uri.scheme != "openv") return null
    if (uri.host == "departures" && uri.pathSegments.isEmpty())
        return LinkCommand(nonce, departures = true)
    // M10: the widget's way to a fixed stop when the location is missing.
    if (uri.host == "departures" && uri.pathSegments == listOf("fixed"))
        return LinkCommand(nonce, departures = true, chooseFixedStop = true)
    if (uri.host == "overview" && uri.pathSegments.isEmpty())
        return LinkCommand(nonce, overview = true)
    if (uri.host == "search" && uri.pathSegments.isEmpty()) return LinkCommand(nonce, search = true)
    if (uri.host == "settings" && uri.pathSegments.isEmpty())
        return LinkCommand(nonce, settings = true)
    if (uri.host != "route" || uri.pathSegments.size != 1) return null
    return uri.pathSegments
        .first()
        .takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) }
        ?.let { LinkCommand(nonce, it) }
}

class MainActivity : ComponentActivity() {
    private val commands = MutableStateFlow<LinkCommand?>(null)
    private var nonce = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.postDelayed({
            if(!isFinishing)lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO){
                runCatching {
                    val app=application as OpenVApp
                    app.koin.get<dev.ambivalenton.openv.data.repository.AppBackupService>().recoverIfNeeded()
                    app.koin.get<dev.ambivalenton.openv.data.repository.PersonalDesignRepository>().repairLauncher()
                }
            }
        },250)
        nonce = savedInstanceState?.getLong("link_nonce") ?: SystemClock.elapsedRealtimeNanos()
        commands.value = parseLink(intent, nonce)
        setContent {
            val app = application as OpenVApp
            val vm: AppViewModel =
                viewModel(
                    factory =
                        object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                app.koin.get<AppViewModel>() as T
                        }
                )
            val ui by vm.state.collectAsStateWithLifecycle()
            val overviewVm: OverviewViewModel =
                viewModel(
                    factory =
                        object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                app.koin.get<OverviewViewModel>() as T
                        }
                )
            val overview by overviewVm.state.collectAsStateWithLifecycle()
            val command by commands.collectAsStateWithLifecycle()
            // 2.8: a random start theme lives only in this session; the rainbow accents only on screen.
            val sessionTheme by dev.ambivalenton.openv.ui.theme.SessionAppearance.random.collectAsStateWithLifecycle()
            val shownSettings = ui.store.settings.withSessionTheme(sessionTheme)
            OpenVTheme(shownSettings) {
            dev.ambivalenton.openv.ui.theme.RainbowAccents(shownSettings.rainbowMode && !shownSettings.monochrome) {
                val dark = MaterialTheme.colorScheme.background.luminance() < .3f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = if (ui.store.settings.appTheme?.name?.startsWith("OS_") == true) !dev.ambivalenton.openv.rendering.systemHeaderIsDark(ui.store.settings.appTheme!!) else !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 29)
                        window.isNavigationBarContrastEnforced = false
                }
                // K02: an unreadable store gets its own screen, outside the navigation and the tabs.
                if (ui.storageError != null) StorageRecoveryScreen(ui.storageError)
                else CompositionLocalProvider(
                    LocalWalkingMapTiles provides remember(app) {app.koin.get<dev.ambivalenton.openv.data.repository.WalkingMapTileSource>()},
                    dev.ambivalenton.openv.ui.components.LocalStopCatalog provides remember(app) {app.koin.getOrNull<dev.ambivalenton.openv.data.repository.StaticStopCatalog>()},
                    LocalWalkingNavigationMode provides ui.store.settings.walkingNavigation,
                    LocalWalkingDirectionsSource provides remember(app) {app.koin.get<dev.ambivalenton.openv.data.repository.WalkingDirectionsSource>()},
                    LocalTutorialState provides rememberTutorialState(ui.store.completedTutorials,ui.ready && ui.storageError==null && ui.store.onboardingVersion>=1,vm::completeTutorial),
                    // Build 115: wordless hints in the first hours of use (not with reduced motion).
                    dev.ambivalenton.openv.ui.hints.LocalGestureHints provides dev.ambivalenton.openv.ui.hints.rememberGestureHints(ui.store.gestureHints,
                        ui.ready && ui.storageError==null && ui.store.onboardingVersion>=1 && ui.store.settings.gestureHintsEnabled &&
                            !ui.store.settings.reduceMotion,vm::learnGestureHint,vm::addGestureHintUse),
                    LocalPlaceSearchGateway provides remember(vm) {PlaceSearchGateway(vm::localLocationMatches,vm::locationSuggestions,startsImmediately=true)},
                    LocalFavoritePlaces provides ui.store.favorites,
                    LocalWaypointRoutingGateway provides remember(vm) {WaypointRoutingGateway(vm::routeWaypoints)},
                    LocalWalkingNavigationOrigin provides ui.store.routingPosition()
                ) {OpenVNavigation(ui, vm, overview, overviewVm, command, app)}
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        nonce = SystemClock.elapsedRealtimeNanos()
        commands.value = parseLink(intent, nonce)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("link_nonce", nonce)
        super.onSaveInstanceState(outState)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OpenVNavigation(
    ui: AppUiState,
    vm: AppViewModel,
    overview: OverviewUiState,
    overviewVm: OverviewViewModel,
    command: LinkCommand?,
    app: OpenVApp,
) {
    val overlays=remember {AppOverlayState()}
    var navigationHiddenPin by remember {mutableStateOf<String?>(null)}
    // Build 106: the plan is being edited on the search page; the navigation pill steps aside meanwhile.
    var planEditing by remember {mutableStateOf(false)}
    val tracker = remember(app) { app.koin.get<PinnedJourneyController>() }
    val pinned by tracker.state.collectAsStateWithLifecycle()
    val recoverable by tracker.recoverable.collectAsStateWithLifecycle()
    val pinAnimation=rememberPinTransitionController()
    var pendingPin by remember {mutableStateOf<PendingPinRequest?>(null)}
    var skipFocusEntry by remember {mutableStateOf(false)}
    var overviewPreview by remember { mutableStateOf<JourneyPreviewRequest?>(null) }
    // H09: the identity of the trip whose pin is being saved (at most one save per trip), the session
    // whose card waits for the decorative morph to end, and a pin refused before anything changed.
    var pinSaving by remember {mutableStateOf<String?>(null)}
    var revealPinId by remember {mutableStateOf<String?>(null)}
    var pinBlocked by remember {mutableStateOf<PinBlocked?>(null)}
    val hasPinnedSession = pinned.session != null
    val revealHeld = revealPinId!=null && revealPinId==pinned.session?.id && pinAnimation.busy
    val isPinned = hasPinnedSession && pinned.session?.minimized != true && pinned.session?.id!=navigationHiddenPin && !revealHeld
    var removingPinnedId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(tracker) { tracker.restore() }
    var alternativeComparison by rememberSaveable(pinned.session?.id){mutableStateOf(false)}
    var automaticComparison by rememberSaveable(pinned.session?.id){mutableStateOf(false)}
    var dismissedAlternative by rememberSaveable(pinned.session?.id){mutableStateOf<String?>(null)}
    val comparisonNow=rememberJourneyNow()
    val suggestionEnabled=ui.store.settings.alternativeSuggestions && pinned.arrival==null && pinned.session?.let {alternativeSuggestionWindow(it,comparisonNow)}==true
    val offer=pinned.session?.let {availablePinnedAlternative(it,comparisonNow,pinned.presence)}
    val offerKey=offer?.let(::alternativeJourneyKey)
    // H10: an offer interrupts nothing. With the companion open it is compared there; elsewhere a compact
    // message "Alternative verfügbar · Ansehen" leaves the current screen as it is.
    var compactAlternative by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(offerKey,pinned.session?.id,suggestionEnabled) {
        if(suggestionEnabled && offer!=null && offerKey!=null && offerKey!=dismissedAlternative && pinned.session?.let {shouldPromptAssistantAlternative(it,offer,comparisonNow)}==true){
            tracker.noteAlternativePrompt(pinned.session!!.id)
            if(pinned.session?.minimized==false) {automaticComparison=true;alternativeComparison=true}
            else compactAlternative=offerKey
        }
        if(!suggestionEnabled && automaticComparison){alternativeComparison=false;automaticComparison=false}
    }
    val nav = rememberNavController()
    // H01/H02/M03/M12/N01: requests the search page acts on (focus the destination, ask for a start,
    // open the favourite editor); the start picker (M18) and the coverage text (H06) on demand.
    var focusDestinationRequest by rememberSaveable { mutableIntStateOf(0) }
    var askForStartRequest by rememberSaveable { mutableIntStateOf(0) }
    var addFavoriteRequest by rememberSaveable { mutableIntStateOf(0) }
    var startPicker by rememberSaveable { mutableStateOf(false) }
    var coverageDocument by rememberSaveable { mutableStateOf(false) }
    var locationDeniedForGood by rememberSaveable { mutableStateOf(false) }
    /** "Meinen Standort nutzen" waits for access or the location switch, then sets the automatic start. */
    var deviceAfterGrant by rememberSaveable { mutableStateOf(false) }
    val settingsNavigation=rememberSettingsNavigationState()
    val personalThemeUi=rememberPersonalThemeUiState()
    val entry by nav.currentBackStackEntryAsState()
    val destinationRoute = entry?.destination?.route ?: "search"
    val homePager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    // 2.8: one draw per cold start (process), never on rotation, tab change or return from the background.
    LaunchedEffect(ui.ready) {
        if (ui.ready && !dev.ambivalenton.openv.ui.theme.SessionAppearance.decided) {
            dev.ambivalenton.openv.ui.theme.SessionAppearance.decided = true
            val saved = ui.store.settings
            if (saved.randomThemeOnStart) dev.ambivalenton.openv.ui.theme.drawRandomTheme(saved)?.let { drawn ->
                dev.ambivalenton.openv.ui.theme.SessionAppearance.random.value = dev.ambivalenton.openv.ui.theme.SessionTheme(saved.appTheme, drawn)
                vm.settings { it.copy(lastRandomTheme = drawn) }
            }
        }
    }
    // 2.8: the rainbow accents change once per completed change between the three pages; a tap on
    // the active tab or a swipe that springs back does not settle on another page.
    val rainbowOn by rememberUpdatedState(ui.store.settings.rainbowMode)
    LaunchedEffect(homePager) {
        snapshotFlow { homePager.settledPage }.distinctUntilChanged().drop(1).collect {
            if (rainbowOn) dev.ambivalenton.openv.ui.theme.SessionAppearance.rainbow.value++
        }
    }
    // 2.10: the scheduled change between day and night, checked once a minute while the app is shown.
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val latestStore by rememberUpdatedState(ui.store)
    val dayNightLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(ui.ready, ui.store.settings.dayNight.schedule, systemDark) {
        if (!ui.ready || ui.store.settings.dayNight.schedule == DayNightSchedule.MANUAL) return@LaunchedEffect
        dayNightLifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                val saved = latestStore.settings
                val place = latestStore.presence?.location?.takeIf { it.hasCoordinates } ?: saved.manualOrigin?.takeIf { it.hasCoordinates }
                val night = saved.dayNight.scheduledNight(java.time.ZonedDateTime.now(), place?.latitude, place?.longitude)
                if (night != null && saved.dayNight.lastAutoNight != night) vm.settings { it.withScheduledDayNight(night, systemDark) }
                delay(60_000L - System.currentTimeMillis() % 60_000L + 500L)
            }
        }
    }
    val quiet=ui.store.settings.reduceMotion
    // Only the search page, no navigation pill and no position requests.
    val planner=ui.store.settings.plannerMode
    val route =
        if (destinationRoute == "search")
            if (planner) "search" else listOf("overview", "search", "departures")[homePager.currentPage]
        else destinationRoute
    // One owner for every page change. A tap names its page; the capsule heads there and the
    // pager reaches it even when another scroll (a layout change, a focus request) cuts the animation
    // short. A finger on the pager always wins: the change then stops instead of fighting the swipe.
    var tabTarget by remember { mutableStateOf<Int?>(null) }
    val tabJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val pagerDragged = homePager.interactionSource.collectIsDraggedAsState()
    fun goToPage(page: Int, animate: Boolean = !quiet) {
        tabJob[0]?.cancel()
        tabTarget = page
        // Build 118: the target is cleared however the change ends – also when it is cancelled before it
        // ever ran (then its finally block never runs). A left-over target made the tab count as open.
        lateinit var change: kotlinx.coroutines.Job
        change = scope.launch {
            try {
                var attempt = 0
                while (homePager.settledPage != page || homePager.currentPage != page || homePager.currentPageOffsetFraction != 0f) {
                    // A page that is within 2 % of its place (the spring ends a pixel short when its
                    // rounded distance misses) rests at once instead of starting another spring.
                    val near = homePager.currentPage == page && kotlin.math.abs(homePager.currentPageOffsetFraction) < .02f
                    try {
                        if (!animate || near || attempt >= 2) homePager.scrollToPage(page)
                        else homePager.animateScrollToPage(page, animationSpec = HOME_TAB_SPRING)
                    } catch (e: CancellationException) {
                        if (!isActive || pagerDragged.value) throw e
                    }
                    if (++attempt > 3 || pagerDragged.value) break
                    withFrameNanos {}
                }
            } finally { if (tabTarget == page) tabTarget = null }
        }
        change.invokeOnCompletion { if (tabTarget == page && tabJob[0] === change) tabTarget = null }
        tabJob[0] = change
    }
    LaunchedEffect(pagerDragged.value) { if (pagerDragged.value) { tabJob[0]?.cancel(); tabTarget = null } }
    LaunchedEffect(planner) { if (planner && homePager.currentPage != 1) goToPage(1, animate = false) }
    fun openHome(requested: Int) {
        val page = if (planner) 1 else requested
        goToPage(page)
        if (destinationRoute != "search")
            nav.navigate("search") {
                popUpTo("search")
                launchSingleTop = true
            }
    }
    val tutorialState=LocalTutorialState.current
    val focusManager=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun selectHomeTab(page:Int) {
        // M12: the active tab keeps the current task visible (details, results, settings page).
        val active=activeHomeTab(destinationRoute,homePager.settledPage,homePager.isScrollInProgress,
            tabTarget?.takeIf {tabJob[0]?.isActive==true})
        // Build 118: on the pages a tab only counts as open when its page visibly rests there; a tap is
        // never swallowed by a change that is no longer running.
        val resting=destinationRoute!="search" || (homePager.settledPage==page && homePager.currentPage==page &&
            kotlin.math.abs(homePager.currentPageOffsetFraction)<.01f && !homePager.isScrollInProgress)
        if(page==active && !isPinned && resting) return
        pinAnimation.cancel()
        overlays.dismissForTab()
        pendingPin=null;removingPinnedId=null;overviewPreview=null
        vm.closeConnectionDetails();settingsNavigation.reset()
        tutorialState?.dismissForNavigation()
        focusManager.clearFocus(force=true);keyboard?.hide()
        if(pinned.session?.minimized==false) {
            navigationHiddenPin=pinned.session?.id
            scope.launch {tracker.setMinimized(true)}
        }
        if(nav.currentDestination?.route!="search" && !nav.popBackStack("search",false))
            nav.navigate("search") {popUpTo(nav.graph.id) {inclusive=true};launchSingleTop=true}
        goToPage(page)
    }
    val snackbar = remember { SnackbarHostState() }
    val editorialHistory=remember(app) {app.koin.get<dev.ambivalenton.openv.data.repository.EditorialHistoryRepository>()}
    val readTexts by editorialHistory.history.collectAsStateWithLifecycle()
    LaunchedEffect(editorialHistory) {
        try {editorialHistory.load()}
        catch(e:CancellationException) {throw e}
        catch(e:Exception) {snackbar.showSnackbar(tr("Die Textchronik konnte nicht geladen werden."))}
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // Bubble button on the pinned card: an explicit request also switches the bubble mode on. The
    // bubble then stands in for the card, inside the app as well; closing it brings the card back.
    val bubbles=remember(app) {app.koin.get<dev.ambivalenton.openv.tracking.PinnedBubbleCoordinator>()}
    val bubbleShown by bubbles.shown.collectAsStateWithLifecycle()
    // Build 116: once a pinned journey is under way it takes the overview page; title, tab name and symbol follow.
    val journeyNow=dev.ambivalenton.openv.ui.components.rememberJourneyNow()
    val underWay=pinned.session?.underWay(journeyNow)==true && !bubbleShown
    val planCountdown by overviewVm.countdown.collectAsStateWithLifecycle()
    var bubbleRequest by rememberSaveable { mutableStateOf<String?>(null) }
    suspend fun startBubble(id:String) {
        app.locations.updateSettings {it.copy(pinnedBubble=true)}
        bubbles.reopen(id)
    }
    val overlayAccess = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val id=bubbleRequest;bubbleRequest=null
        if(id!=null) scope.launch {
            if(dev.ambivalenton.openv.tracking.overlayAllowed(context)) startBubble(id)
            else snackbar.showSnackbar(tr("Ohne die Erlaubnis „Über anderen Apps einblenden“ kann keine schwebende Reisekarte angezeigt werden."))
        }
    }
    fun requestBubble(id:String) {
        if(dev.ambivalenton.openv.tracking.overlayAllowed(context)) {scope.launch {startBubble(id)};return}
        bubbleRequest=id
        try {overlayAccess.launch(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri()))}
        catch(_:android.content.ActivityNotFoundException) {
            bubbleRequest=null
            if(!dev.ambivalenton.openv.ui.components.openOverlaySettings(context))
                scope.launch {snackbar.showSnackbar(tr("Die Einstellung für Overlays konnte nicht geöffnet werden."))}
        }
    }
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* A denied notification grant still permits the user-started foreground service. */ }
    fun startTracking() {
        if (!PinnedJourneyService.start(context))
            tracker.pause(tr("Android konnte das Hintergrund-Tracking nicht starten. Tippe auf Fortsetzen."))
    }
    /**
     * H09: the pin is saved with the tap, exactly once per trip, and survives an immediate app switch
     * (the save is not part of the animation any more). The morph runs alongside; the pinned card is
     * revealed when it ends or is cut short. "Fahrt wird angeheftet …" → "Reisebegleitung aktiv".
     */
    fun animatePin(request: PendingPinRequest) {
        val key=connectionIdentity(request.trip)
        if(pinSaving==key) return
        pinSaving=key
        skipFocusEntry=true
        pinAnimation.begin(request.trip.id) {}
        scope.launch {
            // The morph is the feedback; the hint appears only when saving takes unusually long,
            // so no message covers the animated button.
            val hint=launch {kotlinx.coroutines.delay(1500);snackbar.currentSnackbarData?.dismiss();snackbar.showSnackbar(tr("Fahrt wird angeheftet …"))}
            val accepted=withContext(NonCancellable) {
                tracker.pin(request.favorite,request.query,request.trip,request.routing,
                    expectedCurrentId=request.expectedId,enforceExpected=true,recoveryId=request.recoveryId)
            }
            hint.cancel()
            pinSaving=null
            if(accepted) {
                revealPinId=tracker.state.value.session?.id
                if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startTracking()
                else withContext(NonCancellable) {tracker.setMinimized(true);tracker.pause()}
                snackbar.currentSnackbarData?.dismiss()
                // Confirmed only once the morph has ended (or was cut short).
                launch {
                    androidx.compose.runtime.snapshotFlow {pinAnimation.busy}.first {!it}
                    snackbar.showSnackbar(tr("Reisebegleitung aktiv"))
                }
            } else {
                skipFocusEntry=false
                pinAnimation.cancel()
                // An active companion stays as it was when the change could not be completed.
                snackbar.showSnackbar(tr("Die angepinnte Verbindung wurde inzwischen geändert oder konnte nicht gespeichert werden."))
            }
        }
    }
    // H09: the decorative morph has ended (or was cut short by an app switch): show the pinned card.
    LaunchedEffect(revealPinId,pinAnimation.busy) {
        val id=revealPinId ?: return@LaunchedEffect
        if(pinAnimation.busy) return@LaunchedEffect
        revealPinId=null
        if(tracker.state.value.session?.id==id) {
            overviewPreview=null
            vm.closeConnectionDetails()
            // H01: notifications are asked for when the journey companion starts, not at the first launch.
            if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && Build.VERSION.SDK_INT>=33 &&
                ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun requestPin(trip: Trip,query: JourneyQuery,routing: AppSettings=ui.store.settings,recoveryId: String?=null) {
        if(pinAnimation.busy || pinSaving!=null) return
        val current=tracker.state.value.session
        // H09: a known cancellation (or a journey that is over) is refused before anything changes.
        val reopening=current!=null && samePinnedJourney(current.trip,trip)
        if(!reopening) pinRejection(trip,System.currentTimeMillis())?.let {reason->
            pinBlocked=PinBlocked(query,reason,keepsActive=current!=null);return
        }
        val favorite=ui.store.favorites.firstOrNull {sameLocation(it.location,query.destination)}
            ?: Favorite(label=query.destination.name.take(80),location=query.destination)
        val request=PendingPinRequest(favorite,query.copy(waypoints=trip.waypoints,waypointDwellMinutes=trip.waypointDwellMinutes),trip,routing,current?.id,recoveryId)
        if(current!=null && !samePinnedJourney(current.trip,trip)) pendingPin=request else animatePin(request)
    }
    // 2.5: hiding a notice is local and can be undone right away.
    val noticeActions=remember(ui.store.dismissedNotices,vm) {
        JourneyNoticeActions(ui.store.dismissedNotices) {hit->
            vm.dismissNotice(hit)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                if(snackbar.showSnackbar(tr("Hinweis nicht mehr hervorgehoben"),actionLabel=tr("Rückgängig"),duration=SnackbarDuration.Short)==SnackbarResult.ActionPerformed)
                    vm.restoreNotice(hit)
            }
        }
    }
    // Find the next connection and pin it directly.
    // A quiet hint names the search; one request at a time, the hint ends with the result.
    var favoritePinJob by remember {mutableStateOf<Job?>(null)}
    fun pinNextToFavorite(id: String) {
        if(favoritePinJob?.isActive==true || pinAnimation.busy) return
        val label=ui.store.favorites.firstOrNull {it.id==id}?.label ?: return
        favoritePinJob=scope.launch {
            val hint=launch {snackbar.showSnackbar(tr("Nächste Verbindung nach %1\$s wird gesucht …",label))}
            try {
                val (query,trip)=vm.nextFavoriteJourney(id)
                hint.cancel()
                requestPin(trip,query)
            } catch(e: CancellationException) {hint.cancel();throw e}
            catch(e: Exception) {hint.cancel();snackbar.showSnackbar(errorText(e))}
        }
    }
    fun previewJourney(query: JourneyQuery,trip: Trip,openDetails: Boolean=false,
        detailsContext: JourneyDetailsContext=JourneyDetailsContext.Comparison) {
        if (openDetails) {
            overviewPreview=JourneyPreviewRequest(query,trip,detailsContext)
            tracker.state.value.arrival?.let { tracker.dismissArrival(it.sessionId) }
        } else {
            vm.openKnownJourney(query,trip)
            nav.navigate("results") {launchSingleTop=true}
        }
    }
    // Present the completion in the overview; the next deliberate screen change scrambles it away.
    // H10: an arrival no longer moves the navigation to the overview; a compact message offers it.
    var arrivalShownId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pinned.arrival?.sessionId) {
        val arrival=pinned.arrival ?: return@LaunchedEffect
        if (route=="overview" && homePager.settledPage==0) return@LaunchedEffect
        pendingPin=null;pinAnimation.cancel()
        snackbar.currentSnackbarData?.dismiss()
        // Planermodus: there is no overview to show the arrival on; the message is all.
        if(planner) {snackbar.showSnackbar(tr("Ziel erreicht"));tracker.dismissArrival(arrival.sessionId);return@LaunchedEffect}
        if(snackbar.showSnackbar(tr("Ziel erreicht"),actionLabel=tr("Ansehen"),duration=SnackbarDuration.Long)==SnackbarResult.ActionPerformed) {
            overviewPreview=null;vm.closeConnectionDetails();openHome(0)
        }
    }
    LaunchedEffect(compactAlternative) {
        val key=compactAlternative ?: return@LaunchedEffect
        val result=snackbar.showSnackbar(tr("Alternative verfügbar"),actionLabel=tr("Ansehen"),duration=SnackbarDuration.Long)
        compactAlternative=null
        if(result==SnackbarResult.ActionPerformed && key==offerKey) {automaticComparison=false;alternativeComparison=true}
    }
    /** H10: the outcome of a takeover is always told, with the journey that is active now. */
    fun adopt(expected:String?,key:String?) {
        scope.launch {
            when(val outcome=tracker.adoptAlternative(expected,key)) {
                is dev.ambivalenton.openv.data.repository.AlternativeAdoption.Adopted -> snackbar.showSnackbar(tr("Neue Fahrt aktiv: %1\$s ab %2\$s",
                    outcome.trip.segments.firstOrNull {!it.isWalk}?.lineName ?: tr("Fußweg"),clockTime(outcome.trip.segments.firstOrNull {!it.isWalk}?.effectiveDeparture() ?: outcome.trip.startTime)))
                dev.ambivalenton.openv.data.repository.AlternativeAdoption.Expired -> snackbar.showSnackbar(tr("Diese Alternative ist gerade entfallen. Bisherige Fahrt bleibt aktiv."))
                dev.ambivalenton.openv.data.repository.AlternativeAdoption.Failed -> snackbar.showSnackbar(tr("Die Alternative konnte nicht übernommen werden. Bisherige Fahrt bleibt aktiv."))
            }
        }
    }
    /** Build 117: a connection chosen at a station replaces the running journey; the outcome is told like any takeover. */
    fun adoptChoice(expected:String,key:String) {
        scope.launch {
            when(val outcome=tracker.adoptChoice(expected,key)) {
                is dev.ambivalenton.openv.data.repository.AlternativeAdoption.Adopted -> snackbar.showSnackbar(tr("Neue Fahrt aktiv: %1\$s ab %2\$s",
                    outcome.trip.segments.firstOrNull {!it.isWalk}?.lineName ?: tr("Fußweg"),clockTime(outcome.trip.segments.firstOrNull {!it.isWalk}?.effectiveDeparture() ?: outcome.trip.startTime)))
                dev.ambivalenton.openv.data.repository.AlternativeAdoption.Expired -> snackbar.showSnackbar(tr("Diese Verbindung ist gerade entfallen. Bisherige Fahrt bleibt aktiv."))
                dev.ambivalenton.openv.data.repository.AlternativeAdoption.Failed -> snackbar.showSnackbar(tr("Die Alternative konnte nicht übernommen werden. Bisherige Fahrt bleibt aktiv."))
            }
        }
    }
    val choiceActions=remember(ui.store.reliability,vm) {
        dev.ambivalenton.openv.ui.components.JourneyChoiceActions(ui.store.reliability,onChoose=::adoptChoice,onReport=vm::reportPunctuality)
    }
    LaunchedEffect(route,homePager.settledPage,pinned.arrival?.sessionId) {
        pinned.arrival?.let { announcement ->
            if (route=="overview" && homePager.settledPage==0) arrivalShownId=announcement.sessionId
            else if (arrivalShownId==announcement.sessionId) tracker.dismissArrival(announcement.sessionId)
        }
    }
    DisposableEffect(lifecycle,pinAnimation) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_PAUSE) pinAnimation.cancel()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);pinAnimation.cancel()}
    }
    LaunchedEffect(destinationRoute,homePager.settledPage) {pinAnimation.cancel();if(destinationRoute=="results" && homePager.settledPage!=1 && tabTarget==null) goToPage(1,animate=false)}
    LaunchedEffect(pinned.session?.id,isPinned) {if(isPinned) skipFocusEntry=false}
    LaunchedEffect(isPinned,hasPinnedSession) {
        vm.setLiveFocus(isPinned)
        if (hasPinnedSession) {
            if (isPinned) WidgetRefreshScheduler.pauseForPinnedJourney(context)
            else WidgetRefreshScheduler.refresh(context)
        }
    }
    val needsIntroduction = ui.ready && ui.storageError == null && needsFirstLaunchSetup(ui.store)
    LaunchedEffect(ui.ready,needsIntroduction) {
        if(ui.ready && ui.storageError==null && !needsIntroduction)vm.startUsageTipWeek()
    }
    val locationSettings =
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (isPinned) startTracking()
            // Back from the location switch after "Meinen Standort nutzen": now the automatic start.
            if (deviceAfterGrant && app.deviceLocation.hasPermission() && app.deviceLocation.locationEnabled()) {
                deviceAfterGrant = false
                vm.settings { it.copy(originMode = OriginMode.AUTOMATIC) }
            }
            vm.locationPermissionGranted()
            overviewVm.refresh()
            vm.nearby(force = true)
        }
    val permissions =
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.values.any { it }) {
                locationDeniedForGood = false
                if (isPinned) startTracking()
                // "Meinen Standort nutzen" switches to the automatic start once access exists.
                if (deviceAfterGrant) {
                    deviceAfterGrant = false
                    if (app.deviceLocation.locationEnabled()) vm.settings { it.copy(originMode = OriginMode.AUTOMATIC) }
                    else { deviceAfterGrant = true; locationSettings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                }
                vm.locationPermissionGranted()
                overviewVm.refresh()
                if (route == "departures") vm.nearby(force = true)
            } else {
                // H02: after a final refusal Android shows no dialog any more; the settings are offered instead.
                val activity = context as? android.app.Activity
                locationDeniedForGood = activity != null && result.keys.none {
                    androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                }
                vm.acquirePosition()
            }
        }
    val requestPermission: () -> Unit = {
        // Planermodus: no permission dialog and no settings page; the start is typed in.
        if (planner) Unit
        else if (!app.deviceLocation.hasPermission()) {
            permissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        } else if (
            !LocationManagerCompat.isLocationEnabled(
                context.getSystemService(LocationManager::class.java)
            )
        ) {
            locationSettings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else {
            if (isPinned) startTracking()
            vm.locationPermissionGranted()
            overviewVm.refresh()
            if (route == "departures") vm.nearby(force = true)
        }
    }
    /**
     * "Meinen Standort nutzen" really uses it: with access and location on, the start becomes
     * automatic at once; otherwise access is asked for (after a final refusal the app's Android
     * settings open) or the location switch, and the start follows once that succeeded.
     */
    val useMyLocation: () -> Unit = {
        if (planner) Unit
        else if (!app.deviceLocation.hasPermission()) {
            if (locationDeniedForGood) openAppDetailsSettings(context)
            else { deviceAfterGrant = true; permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
        } else if (!app.deviceLocation.locationEnabled()) {
            deviceAfterGrant = true
            locationSettings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else {
            vm.settings { it.copy(originMode = OriginMode.AUTOMATIC) }
            vm.locationPermissionGranted()
            overviewVm.refresh()
        }
    }
    LaunchedEffect(ui.ready, needsIntroduction, isPinned, ui.store.settings.originMode, ui.store.settings.manualOrigin, planner) {
        if (ui.ready && ui.storageError == null && !needsIntroduction && !isPinned && !planner) {
            if (
                ui.store.settings.originMode == OriginMode.MANUAL ||
                    app.deviceLocation.hasPermission()
            )
                vm.acquirePosition()

        }
    }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }
    // Once after the update, name the favourites whose overlapping time windows were shortened.
    LaunchedEffect(ui.ready, ui.store.scheduleNotice) {
        val names = ui.store.scheduleNotice
        if (!ui.ready || names.isEmpty()) return@LaunchedEffect
        snackbar.showSnackbar(tr("Zeitabhängiger Standort: Bei %1\$s wurden überlappende Zeiten entfernt, weil dort schon ein anderer Favorit den Standort vorgibt.", names.joinToString(", ")),
            withDismissAction = true, duration = androidx.compose.material3.SnackbarDuration.Long)
        vm.clearScheduleNotice()
    }
    LaunchedEffect(lifecycle, needsIntroduction, isPinned, route, settingsNavigation.page) {
        val designPreview=route=="settings" && settingsNavigation.page==dev.ambivalenton.openv.ui.screens.SettingsPage.SENTENCES
        if (needsIntroduction || (isPinned && !designPreview) || (route != "overview" && !designPreview)) {
            overviewVm.setForeground(false)
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            overviewVm.setForeground(true)
            try {
                awaitCancellation()
            } finally {
                overviewVm.setForeground(false)
            }
        }
    }
    // Departure chips of the network plan are requested only while the search tab is visible.
    LaunchedEffect(lifecycle, route, needsIntroduction) {
        if (needsIntroduction || route != "search") { overviewVm.setPlanVisible(false); return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            overviewVm.setPlanVisible(true)
            try { awaitCancellation() } finally { overviewVm.setPlanVisible(false) }
        }
    }
    LaunchedEffect(ui.ready,isPinned,ui.store.settings.walkingSpeed,ui.store.settings.ignoreWalking) {
        if(ui.ready)tracker.setWalkingPreferences(ui.store.settings.walkingSpeed,ui.store.settings.ignoreWalking)
    }
    // Foreground-only fixes keep presence-based favorites current without background tracking.
    LaunchedEffect(ui.ready, needsIntroduction, isPinned, ui.store.settings.originMode, ui.store.settings.powerSaving, lifecycle) {
        if (ui.ready && !needsIntroduction && !isPinned && ui.store.settings.originMode == OriginMode.AUTOMATIC)
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    if (app.deviceLocation.hasPermission()) vm.acquirePosition()
                    delay(locationCacheMillis(ui.store.settings.powerSaving))
                }
            }
    }
    LaunchedEffect(command) {
        command?.let {
            if (vm.consumeCommand(it.nonce)) {
                if(it.boardJourneyToken!=null) {
                    val request=vm.boardJourney(it.boardJourneyToken)
                    if(request==null)snackbar.showSnackbar(tr("Diese Verbindung ist nicht mehr auf der Anzeigetafel. Bitte aktualisieren."))
                    else {tracker.restore();tracker.setMinimized(true);navigationHiddenPin=tracker.state.value.session?.id;overviewPreview=request}
                } else if (it.pinnedId != null) {
                    tracker.restore()
                    openHome(0)
                    if (tracker.state.value.session?.id != it.pinnedId)
                        snackbar.showSnackbar(tr("Diese angepinnte Verbindung wurde bereits geschlossen."))
                    // An earlier tab switch may have hidden the timeline for this pin; an explicit
                    // request from the bubble or the notification always shows it again.
                    else { navigationHiddenPin = null; tracker.setMinimized(false); startTracking(); tracker.requestRefresh() }
                } else if (it.overview) openHome(0)
                else if (it.departures) { openHome(2); if (it.chooseFixedStop) vm.requestFixedBoardStop() }
                else if (it.search) openHome(1)
                else if (it.settings) nav.navigate("settings") { launchSingleTop = true }
                else
                    it.favoriteId?.let { id ->
                        // M10: a widget target that was removed in the meantime explains itself and
                        // leads to the search instead of an empty result.
                        if (!vm.routeTargetExists(id)) {
                            openHome(1)
                            snackbar.showSnackbar(tr("Dieses Ziel gibt es nicht mehr. Wähle in der Suche ein neues Ziel oder richte das Widget neu ein."),
                                withDismissAction = true)
                            return@let
                        }
                        tracker.restore();tracker.setMinimized(true);navigationHiddenPin=tracker.state.value.session?.id
                        vm.setLiveFocus(false)
                        vm.searchFavorite(id)
                        nav.navigate("results") { launchSingleTop = true }
                    }
            }
        }
    }
    // Current data are always loaded (the switch "Aktuelle Daten laden" is gone; an old "off" is ignored).
    LaunchedEffect(route, ui.store.settings.powerSaving, isPinned) {
        if (!isPinned && route in setOf("overview", "results", "departures"))
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(if(ui.store.settings.powerSaving)POWER_SERVER_INTERVAL_MS else if (route == "departures")30_000 else 60_000)
                    when (route) {
                        "overview" -> overviewVm.refresh(force = false)
                        "results" -> vm.refreshLiveResults()
                        else -> vm.refreshVisibleBoards()
                    }
                }
            }
    }
    CompositionLocalProvider(LocalAppOverlay provides overlays, LocalJourneyNotices provides noticeActions) {
    // Build 101: after the setup is finished it stays a moment longer and fades out over the app.
    var introductionShown by remember { mutableStateOf(false) }
    LaunchedEffect(needsIntroduction) { if (needsIntroduction) introductionShown = true }
    if (needsIntroduction || introductionShown) FirstLaunchOnboarding(
        visible = needsIntroduction,
        onExited = { introductionShown = false },
        store = ui.store,
        onChange = vm::updateOnboarding,
        onFinish = vm::finishOnboarding,
        search = vm::findLocations,
        onLocationGranted = { vm.locationPermissionGranted(); overviewVm.refresh() },
        // H01: one tap to the search form with start and destination, without permission or design choice.
        onSearchNow = {
            scope.launch {
                if (!needsFirstLaunchSetup(ui.store) || vm.finishOnboarding()) {
                    vm.completeTutorial(TutorialPaths.search.id)
                    goToPage(1, animate = false)
                    focusDestinationRequest++
                }
            }
        },
    )
    overviewPreview?.takeIf { !isPinned }?.let { request ->
        JourneyDetailsSheet(request.comparisons.ifEmpty {listOf(request.trip)},request.trip.id,onDismiss={overviewPreview=null},
            onPinRequested={requestPin(it,request.query)},onBookmark={trip->vm.saveJourney(request.query,trip){scope.launch {snackbar.showSnackbar(tr("Verbindung vorgemerkt"))}}},pinTransition=pinAnimation,detailsContext=request.detailsContext,
            journeyQuery=request.query,onWaypointRouted={query,trip->overviewPreview=request.copy(query=query,trip=trip,comparisons=emptyList())})
    }
    val comparedSession=pinned.session
    if(alternativeComparison && offer==null) LaunchedEffect(Unit) {
        // H10: the offer ended while its comparison was open.
        alternativeComparison=false;automaticComparison=false
        snackbar.showSnackbar(tr("Diese Alternative ist gerade entfallen. Bisherige Fahrt bleibt aktiv."))
    }
    if(alternativeComparison && offer!=null && comparedSession!=null)AlternativeJourneyDialog(comparedSession,offer,
        onAccept={val expected=comparedSession.id;val key=alternativeJourneyKey(offer);alternativeComparison=false;dismissedAlternative=key;adopt(expected,key)},
        onKeep={dismissedAlternative=offerKey
            scope.launch {tracker.dismissAlternative(comparedSession.id,alternativeJourneyKey(offer));alternativeComparison=false}})
    // H10: the changed arrival is told inside the open companion; it does not cover another task.
    val arrivalChange=pinned.session?.takeIf {needsArrivalChangeNotice(it,comparisonNow) && alternativeSuggestionWindow(it,comparisonNow)}
    if(arrivalChange!=null && !alternativeComparison && pinned.status==PinnedTrackingStatus.RUNNING && isPinned) {
        fun acknowledge(){scope.launch {tracker.acknowledgeArrivalChange(arrivalChange.id)}}
        AlertDialog(onDismissRequest=::acknowledge,title={Text(tr("Ankunft geändert"))},
            text={Text(tr("Du erreichst dein Ziel voraussichtlich um %1\$s · %2\$s Minuten später. Dein Reiseplan wird laufend angepasst.",
                clockTime(assistantFinalArrival(arrivalChange,comparisonNow)),arrivalChangeMinutes(arrivalChange,comparisonNow)))},
            confirmButton={TextButton(::acknowledge){Text(tr("Verstanden"))}})
    }
    if (isPinned) PinnedJourneyDialog(
        state = pinned,
        onClose = { scope.launch {
            openHome(0)
            tracker.setMinimized(true)
        } },
        onResume = { startTracking(); tracker.requestRefresh() },
        onAlternative = { automaticComparison=false;alternativeComparison=true },
        autoAlternativePrompt=false,
        onAlternativeSelected={id,key->adopt(id,key)},
        onLocationPermission = requestPermission,
        animateEntry=!skipFocusEntry,
        pinTransition=pinAnimation,
    )
    pinBlocked?.let { blocked ->
        AlertDialog(onDismissRequest={pinBlocked=null},modifier=Modifier.testTag("pin-blocked-dialog"),
            title={Text(blocked.reason.title())},
            text={Text(if(blocked.keepsActive) tr("Sie wird nicht angeheftet. Deine bisherige Reisebegleitung bleibt aktiv.")
                else tr("Sie wird nicht angeheftet. Suche eine andere Fahrt zum selben Ziel."))},
            confirmButton={TextButton({
                pinBlocked=null;overviewPreview=null;vm.closeConnectionDetails()
                val query=blocked.query
                vm.search(query.destination,System.currentTimeMillis(),TimeMode.DEPARTURE,originOverride=query.origin)
                nav.navigate("results") {launchSingleTop=true}
            },Modifier.testTag("pin-blocked-search")) {Text(tr("Andere Fahrt suchen"))}},
            dismissButton={TextButton({pinBlocked=null}) {Text(tr("Schließen"))}})
    }
    pendingPin?.let { request ->
        AlertDialog(onDismissRequest={pendingPin=null},
            title={FittedText(tr("Verbindung im Reisebegleiter ersetzen?"))},
            text={Text(tr("Die laufende Fahrt bleibt bis zur Bestätigung im Reisebegleiter. Danach findest du sie im Suchverlauf, solange sie noch nutzbar ist."))},
            confirmButton={TextButton({pendingPin=null;animatePin(request)}) {Text(tr("Ersetzen"))}},
            dismissButton={TextButton({pendingPin=null}) {Text(tr("Behalten"))}})
    }
    removingPinnedId?.let { expected ->
        AlertDialog(onDismissRequest={removingPinnedId=null},
            text={Text(tr("Möchtest du die angepinnte Verbindung wirklich entfernen?"))},
            confirmButton={TextButton({
                removingPinnedId=null
                scope.launch {
                    if (tracker.close(expected)) {
                        PinnedJourneyService.stop(context)
                        WidgetRefreshScheduler.refresh(context)
                    }
                }
            }) { Text(tr("Entfernen")) }},
            dismissButton={TextButton({removingPinnedId=null}) { Text(tr("Behalten")) }})
    }
    ManualLocationFallback(ui,vm,app.deviceLocation,needsIntroduction)
    // M18/H02: the start picker opens only on an explicit "Start wählen".
    if (startPicker && !needsIntroduction) ManualLocationPicker(ui.store,vm::findLocations,
        { place -> vm.setManualLocation(place); startPicker = false; overviewVm.refresh() }, { startPicker = false })
    if (coverageDocument) LegalDocumentSheet("coverage") { coverageDocument = false }
    val tutorialPath=when {
        // The first-use hints are hints as well; the minimalist mode shows none.
        needsIntroduction || isPinned || overviewPreview!=null || ui.selectedTripId!=null || ui.store.settings.minimalistMode->null
        destinationRoute=="search" && homePager.settledPage==0->TutorialPaths.overview
        destinationRoute=="search" && homePager.settledPage==1->TutorialPaths.search
        else->null
    }
    val navVisible=!needsIntroduction && !WindowInsets.isImeVisible && !planner
    // Build 106: the pill steps aside where a view needs the whole height and has its own way back –
    // an opened connection (results, overview, board) and the plan while it is edited. It stays on the
    // overview, the board, the results, a running journey and in the settings (a tab tap leaves them).
    val planEditingHere=planEditing && destinationRoute=="search" && homePager.currentPage==1
    val pillAside=navigationPillAside(route,ui.selectedTripId!=null,overviewPreview!=null,isPinned,planEditingHere,
        // "App-Design" is chosen on "Design & Darstellung" (and on its own page from the settings search).
        designPage=settingsNavigation.page in setOf(dev.ambivalenton.openv.ui.screens.SettingsPage.DESIGN,dev.ambivalenton.openv.ui.screens.SettingsPage.APP_THEME)) ||
        // Build 116: e.g. the compact view of a connection asks for the whole height.
        dev.ambivalenton.openv.ui.components.PillAsideRequests.active
    val pillShown=navVisible && !pillAside
    // Planermodus: the pinned journey floats where the pill would be.
    val hoverCard=planner && !needsIntroduction && !WindowInsets.isImeVisible && hasPinnedSession && !isPinned
    // Build 107: while the plan is edited its page keeps the pill's room, so the plan does not move.
    val bottomClear=pillShown || hoverCard || (navVisible && planEditingHere)
    val navDensity=LocalDensity.current
    var navHeight by remember {mutableStateOf(88.dp)}
    // Without the pill, content still keeps clear of the system's gesture bar.
    val systemBottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val clearance=if(bottomClear) navHeight else if(navVisible) systemBottom+8.dp else 0.dp
    // 0 = the pill in place, 1 = below the screen edge. Out with a short lift and a drop, back in with a spring.
    val pillOffset=remember {androidx.compose.animation.core.Animatable(if(pillShown) 0f else 1f)}
    LaunchedEffect(pillShown,navVisible,quiet) {
        when {
            // Keyboard, planner, introduction: gone at once as before, so it comes back with a spring.
            !navVisible || quiet -> pillOffset.snapTo(if(pillShown) 0f else 1f)
            pillShown -> pillOffset.animateTo(0f,androidx.compose.animation.core.spring(dampingRatio=PILL_RETURN_DAMPING,stiffness=PILL_RETURN_STIFFNESS))
            else -> pillOffset.animateTo(1f,androidx.compose.animation.core.tween(PILL_LEAVE_MILLIS,easing=PillLeaveEasing))
        }
    }
    val pillGone by remember {derivedStateOf {pillOffset.value>=1f}}
    // Lower edge of the screen title (px, in root), for the equal gap below the bar's rule.
    var titleBottom by remember {mutableFloatStateOf(-1f)}
    // The drop of the search button falls onto the navigation pill in this layer.
    val liquidStage=remember {dev.ambivalenton.openv.ui.components.LiquidStage()}
    CompositionLocalProvider(LocalNavigationClearance provides clearance,
        dev.ambivalenton.openv.ui.components.LocalLiquidStage provides liquidStage) {
    Box(Modifier.fillMaxSize().appThemeBackground().imePadding().testTag("floating-navigation-root")) {
    AppOverlayHost(overlays,Modifier.fillMaxSize(),modalBottomPadding=clearance,
        foreground={
            SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(bottom=clearance+8.dp)) {data->
                Snackbar(data,Modifier.testTag("app-snackbar-message"))
            }
        }) {
    FirstUseTutorialHost(tutorialPath,Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets=WindowInsets(0,0,0,0),
        modifier=Modifier.appThemeBackground(),
        containerColor=androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            if(route=="settings") SettingsAppBar(settingsNavigation,{nav.popBackStack()},{enabled->vm.settings {it.withQuietMode(enabled)}},personalThemeUi,
                settings=ui.store.settings.withSessionTheme(dev.ambivalenton.openv.ui.theme.SessionAppearance.random.collectAsStateWithLifecycle().value),
                onDayNight={vm.toggleDayNight(systemDark)}) else TopAppBar(
                modifier=Modifier.appSystemHeader(),
                colors=appTopBarColors(),
                title = {
                    Column(Modifier.onGloballyPositioned {titleBottom=it.boundsInRoot().bottom}) {
                    FittedText(
                        when (route) {
                            "results" -> connectionScreenTitle(ui)
                            "overview" -> if(underWay) tr("Unterwegs") else tr("Übersicht")
                            "departures" -> if(ui.store.settings.minimalistMode)tr("Anzeigetafel") else tr("Abfahrten")
                            "settings" -> tr("Einstellungen")
                            "search" -> tr("Suchen")
                            else -> tr("Öpen-V")
                        },modifier=Modifier.testTag("main-screen-title")
                    )
                    dev.ambivalenton.openv.ui.components.TransitSourceAttribution(compact=true)
                    }
                },
                navigationIcon = {
                    if (route == "results" || route == "settings")
                        dev.ambivalenton.openv.ui.components.AnimatedIconButton({
                            if(route!="settings" || !settingsNavigation.back()) nav.popBackStack()
                        }, R.drawable.ic_back, tr("Zurück"), dev.ambivalenton.openv.ui.components.IconTapMotion.BACK)
                },
                actions = {
                    // The symbols answer a tap (refresh turns once, the sliders adjust).
                    if (route in setOf("overview", "results", "departures"))
                        dev.ambivalenton.openv.ui.components.AnimatedIconButton({
                            if (route == "overview") overviewVm.refresh()
                            else if (route == "results") vm.retrySearch()
                            else {
                                vm.nearby(force = true)
                                vm.refreshVisibleBoards()
                            }
                        }, R.drawable.ic_refresh, tr("Aktuelle Daten laden"), dev.ambivalenton.openv.ui.components.IconTapMotion.SPIN)
                    if (route != "settings")
                        dev.ambivalenton.openv.ui.components.AnimatedIconButton({ nav.navigate("settings") { launchSingleTop = true } },
                            R.drawable.ic_settings, tr("Einstellungen öffnen"), dev.ambivalenton.openv.ui.components.IconTapMotion.NUDGE,
                            // Build 115: the gear nods now and then during the first hours of use.
                            Modifier.gestureHint(dev.ambivalenton.openv.data.model.GestureHintKeys.SETTINGS, dev.ambivalenton.openv.ui.hints.HintKind.PULSE, 2600L))
                },
            )
        },
    ) { padding ->
        val titleGap=with(LocalDensity.current) {
            if(titleBottom<0f) 16.dp else ((padding.calculateTopPadding().toPx()-titleBottom)/density).coerceIn(4f,40f).dp
        }
        CompositionLocalProvider(dev.ambivalenton.openv.ui.components.LocalScreenTitleGap provides titleGap) {
        Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                NavHost(
                    nav,
                    startDestination = "search",
                    enterTransition = {
                        if(quiet) EnterTransition.None else fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 8 }
                    },
                    exitTransition = { if(quiet) ExitTransition.None else fadeOut(tween(140)) },
                    popEnterTransition = { if(quiet) EnterTransition.None else fadeIn(tween(180)) },
                    popExitTransition = {
                        if(quiet) ExitTransition.None else fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 8 }
                    },
                ) {
                    composable("search") {
                        HorizontalPager(
                            state = homePager,
                            userScrollEnabled = !planner,
                            modifier = Modifier.fillMaxSize().testTag("main-tabs-pager"),
                            key = { it },
                        ) { page ->
                            Box(
                                // Build 118: requests to bring something into view end at the page.
                                Modifier.fillMaxSize().bringIntoViewResponder(HomePageBringIntoView).graphicsLayer {
                                    val offset =
                                        kotlin.math.abs(
                                            (homePager.currentPage - page) +
                                                homePager.currentPageOffsetFraction
                                        )
                                    alpha = 1f - .12f * offset.coerceIn(0f, 1f)
                                }
                            ) {
                                if (page == 0) CompositionLocalProvider(dev.ambivalenton.openv.ui.components.LocalJourneyChoiceActions provides choiceActions) {
                                    OverviewScreen(
                                        overview,
                                        ui.store.settings.overview,
                                        { overviewVm.refresh() },
                                        useMyLocation,
                                        { openHome(1) },
                                        isActive = homePager.settledPage == 0 && !isPinned && !needsIntroduction && destinationRoute=="search",
                                        textHistory=readTexts,
                                        nextHeader=vm::nextOverviewHeader,
                                        tipWeekEndsAt=ui.store.usageTips.startedAt?.plus(7*USAGE_TIP_DAY),
                                        onTextShown={content,text->scope.launch {
                                            try {vm.startUsageTipWeek();editorialHistory.recordDisplayed(content,text)}
                                            catch(e:CancellationException) {throw e}
                                            catch(e:Exception) {snackbar.showSnackbar(tr("Der Text konnte nicht in der Textchronik gespeichert werden."))}
                                        }},
                                        pinnedState = pinned,
                                        pinnedAsBubble = bubbleShown,
                                        takeover = underWay,
                                        onHeaderArrivalDismiss = {pinned.arrival?.let {tracker.dismissArrival(it.sessionId)}},
                                        onPinnedOpen = { navigationHiddenPin=null;scope.launch { tracker.setMinimized(false);startTracking();tracker.requestRefresh() } },
                                        onPinnedRemove = { removingPinnedId=pinned.session?.id },
                                        onCorrectAlighting = {alighted->pinned.session?.id?.let {id->scope.launch {tracker.correctAlighting(id,alighted)}}},
                                        // Explicitly reopens the bubble for this pin; the app moves to the
                                        // background so the bubble becomes visible. Without access: settings.
                                        onPinnedBubble = {pinned.session?.id?.let(::requestBubble)},
                                        onOpenConnection = { entry ->
                                            entry.journeyPreviewRequest()?.let { request ->
                                                previewJourney(request.query,request.trip,openDetails=true,detailsContext=request.detailsContext)
                                            }
                                        },
                                        onSearchConnections = { entry ->
                                            entry.query?.let { query ->
                                                vm.search(query.destination,query.time,query.timeMode,
                                                    originOverride=query.origin,cacheQuery=query)
                                                nav.navigate("results") {launchSingleTop=true}
                                            }
                                        },
                                        emptyState = remember(ui.store, planCountdown) { ui.store.overviewEmptyState(System.currentTimeMillis()) },
                                        onAddFavorite = { openHome(1); addFavoriteRequest++ },
                                        onShowFavorites = { openHome(1) },
                                        onChooseStart = { startPicker = true },
                                    )
                                }
                                else if (page == 2) DeparturesScreen(ui, vm, requestPermission,active=route=="departures"&&!isPinned,onJourney={query,trip->
                                    overviewPreview=JourneyPreviewRequest(query,trip,comparisons=boardJourneyGallery(ui.store.departureBoard.departures,query))
                                },onComparison={query,trips->vm.openKnownJourneys(query,trips);nav.navigate("results"){launchSingleTop=true}})
                                else
                                    SearchScreen(
                                        ui,
                                        vm,
                                        { target, time, mode ->
                                            vm.search(target, time, mode)
                                            nav.navigate("results") { launchSingleTop = true }
                                        },
                                        { id, time, mode ->
                                            vm.searchFavorite(id, time, mode)
                                            nav.navigate("results") { launchSingleTop = true }
                                        },
                                        requestPermission,
                                        onCachedSearch={id->vm.openCachedSearch(id);nav.navigate("results") {launchSingleTop=true}},
                                        recoverablePins=recoverable,activePin=pinned.session,pinnedState=pinned,onSavedPreview={saved->previewJourney(saved.query,saved.trip,openDetails=true)},
                                        onSavedPin={saved->requestPin(saved.trip,saved.query)},
                                        onRecoverPreview={candidate ->previewJourney(candidate.session.query,candidate.session.trip)},
                                        onRecoverPin={candidate ->requestPin(candidate.session.trip,candidate.session.query,candidate.session.routing,candidate.session.id)},
                                        pinTransition=pinAnimation,
                                        onClearRecoverable={scope.launch {if(!tracker.clearRecoveries()) snackbar.showSnackbar(tr("Verlauf konnte nicht gespeichert werden."))}},
                                        onHistory = { history ->
                                            vm.searchHistory(history)
                                            nav.navigate("results") { launchSingleTop = true }
                                        },
                                        countdown = planCountdown,
                                        onPinFavorite = ::pinNextToFavorite,
                                        onPlanEditing = { planEditing = it },
                                        onSearchFrom = { origin, target, time, mode, query ->
                                            vm.search(target, time, mode, origin, query)
                                            nav.navigate("results") { launchSingleTop = true }
                                        },
                                        onFavoriteRemoved = { removal ->
                                            scope.launch {
                                                snackbar.currentSnackbarData?.dismiss()
                                                val text = if (removal.lines == 0) tr("„%1\$s“ entfernt", removal.favorite.label)
                                                    else tr("„%1\$s“ und %2\$s Netzplanlinien entfernt", removal.favorite.label, removal.lines)
                                                if (snackbar.showSnackbar(text, actionLabel = tr("Rückgängig"), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed)
                                                    vm.restoreFavorite(removal)
                                            }
                                        },
                                        active = route == "search",
                                        focusDestination = focusDestinationRequest,
                                        askForStart = askForStartRequest,
                                        locationDeniedForGood = locationDeniedForGood,
                                        addFavorite = addFavoriteRequest,
                                        onFavoriteFrom = { id, time, mode, start ->
                                            vm.searchFavorite(id, time, mode, start)
                                            nav.navigate("results") { launchSingleTop = true }
                                        },
                                    )
                            }
                        }
                    }
                    composable("results") {
                        ResultsScreen(
                            ui,
                            vm::retrySearch,
                            requestPermission,
                            // H02: back to the form with the start field open and the hint.
                            {
                                openHome(1)
                                askForStartRequest++
                            },
                            { vm.favorite(Favorite(label = it.name.take(80), location = it)) },
                            vm::selectConnection,
                            vm::showConnectionsList,
                            vm::closeConnectionDetails,
                            onRefresh = vm::pullToRefreshResults,
                            onHistoricalConnections=vm::loadHistoricalConnections,
                            onFutureConnections=vm::loadFutureConnections,
                            onCachedViewport=vm::saveCachedViewport,
                            onPinRequested={trip ->ui.query?.let {requestPin(trip,it)}},onBookmark={trip->ui.query?.let {vm.saveJourney(it,trip){scope.launch {snackbar.showSnackbar(tr("Verbindung vorgemerkt"))}}}},pinTransition=pinAnimation,
                            onWaypointRouted={query,trip->vm.openKnownJourney(query,trip,openDetails=false)},
                            // H05: every cause leads to its own step.
                            onFailureStep={step->when(step) {
                                FailureStep.RETRY,FailureStep.RELOAD->vm.retrySearch()
                                FailureStep.CHOOSE_START->{openHome(1);askForStartRequest++}
                                FailureStep.CHANGE_TIME->openHome(1)
                                FailureStep.CHECK_PRODUCTS->{settingsNavigation.open(SettingsPage.SEARCH);nav.navigate("settings") {launchSingleTop=true}}
                                FailureStep.COVERAGE->coverageDocument=true
                                FailureStep.SWITCH_PROVIDER->{settingsNavigation.open(SettingsPage.SERVICES);nav.navigate("settings") {launchSingleTop=true}}
                            }},
                            onChangeDestination={openHome(1);focusDestinationRequest++},
                            onRestore={r->vm.restoreResults(r.destination,r.origin,r.time,r.mode,r.current,r.cacheId,r.selectedTripId)},
                            onSearchOther={ui.query?.let {q->vm.search(q.destination,System.currentTimeMillis(),TimeMode.DEPARTURE,originOverride=q.origin.takeUnless {it.isDevicePosition})}},
                        )
                    }
                    // Preserve the old destination ID for restored Build-8/9 navigation state.
                    composable("departures") { LaunchedEffect(Unit) { openHome(2) } }
                    composable("settings") { SettingsScreen(ui, vm,settingsNavigation,renderHeader=false,overview=overview,onOverviewRefresh={overviewVm.refresh()},personalThemeUi=personalThemeUi) }
                }
            if(destinationRoute=="search") PinMatrixOverlay(pinAnimation)
        }
        }
    }
    }
    }
    if(navVisible && !(pillGone && !pillShown)) Box(Modifier.align(Alignment.BottomCenter).onSizeChanged {navHeight=with(navDensity) {it.height.toDp()}}
        .graphicsLayer {
            val out=pillOffset.value
            translationY=out*(size.height+16.dp.toPx())
            // A little jelly: slimmer and taller while it flies, wider and flatter where the spring dips below its place.
            val swing=(-out).coerceIn(-.12f,.12f)
            transformOrigin=androidx.compose.ui.graphics.TransformOrigin(.5f,1f)
            scaleY=1f+swing*.55f;scaleX=1f-swing*.3f-(out.coerceIn(0f,1f))*.06f
            alpha=(1f-(out-.7f).coerceAtLeast(0f)/.3f).coerceIn(0f,1f)
        }
        .then(if(!pillShown) Modifier.clearAndSetSemantics {} else Modifier)) {
        ExpressivePillNavigationTo(homePager,listOf(
            if(underWay) PillDestination(tr("Unterwegs"),R.drawable.ic_navigation) else PillDestination(tr("Übersicht"),R.drawable.ic_overview),
            PillDestination(tr("Suchen"),if(route=="results" && !ui.trips.value.isNullOrEmpty() && !ui.isListViewVisible && ui.selectedTripId==null) R.drawable.ic_dashboard else R.drawable.ic_search),
            PillDestination(tr("Abfahrten"),R.drawable.ic_train)).mapIndexed {index,tab->
                tab.copy(timeline=(isPinned || overviewPreview!=null || ui.selectedTripId!=null) &&
                    index==(if(route=="results") 1 else homePager.currentPage))
            },if(route=="results") 1 else null,tabTarget,::selectHomeTab)
    }
    androidx.compose.animation.AnimatedVisibility(hoverCard,Modifier.align(Alignment.BottomCenter),
        enter=if(quiet) androidx.compose.animation.EnterTransition.None else androidx.compose.animation.slideInVertically(
            androidx.compose.animation.core.spring(dampingRatio=.72f,stiffness=420f)) {it}+androidx.compose.animation.fadeIn(),
        exit=if(quiet) androidx.compose.animation.ExitTransition.None else androidx.compose.animation.slideOutVertically {it}+androidx.compose.animation.fadeOut()) {
        Box(Modifier.onSizeChanged {navHeight=with(navDensity) {it.height.toDp()}}) {
            dev.ambivalenton.openv.ui.components.PinnedHoverCard(pinned,
                onOpen={navigationHiddenPin=null;scope.launch {tracker.setMinimized(false);startTracking();tracker.requestRefresh()}},
                onRemove={removingPinnedId=pinned.session?.id})
        }
    }
    dev.ambivalenton.openv.ui.components.LiquidStageLayer(liquidStage)
    }
    }
    }
}

/**
 * Build 118 · a request to bring something into view (focus, a text field, a revealed list entry) ends at
 * its home page: the pager only sees the page itself, which is in view. The pager of the current Compose
 * foundation otherwise answered such a request by settling a whole page on – "Suchen" slid on to
 * "Abfahrten", and from "Abfahrten" the tab "Suchen" seemed not to answer. Scrolling inside a page is
 * unaffected (its lists handle the request before it arrives here); the pages change only by a swipe or a tab.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal object HomePageBringIntoView : androidx.compose.foundation.relocation.BringIntoViewResponder {
    override fun calculateRectForParent(localRect: androidx.compose.ui.geometry.Rect) = androidx.compose.ui.geometry.Rect.Zero
    override suspend fun bringChildIntoView(localRect: () -> androidx.compose.ui.geometry.Rect?) = Unit
}

/** Build 106: the navigation pill coming back – a lively spring with one small swing above its place. */
private const val PILL_RETURN_DAMPING=.58f
private const val PILL_RETURN_STIFFNESS=380f
/** Leaving: a short lift, then down and out (anticipation), quick enough not to hold anything up. */
private const val PILL_LEAVE_MILLIS=300
private val PillLeaveEasing=androidx.compose.animation.core.CubicBezierEasing(.45f,-.35f,.75f,.45f)
