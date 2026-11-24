// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.screens

import dev.ambivalenton.openv.ui.components.ThemedTonalButton

import dev.ambivalenton.openv.ui.components.CalmVisibility
import dev.ambivalenton.openv.localization.tr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.OverviewUiState
import dev.ambivalenton.openv.ui.OverviewEntry
import dev.ambivalenton.openv.ui.overviewRowKeys
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.ui.tutorial.tutorialTarget

/** Narrative paragraphs rather than another set of cards. Every success opens its exact trip. */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun OverviewScreen(
    ui: OverviewUiState,
    settings: OverviewSettings,
    onRefresh: () -> Unit,
    onPermission: () -> Unit,
    onSearch: () -> Unit,
    isActive: Boolean = true,
    onOpenConnection: (OverviewEntry) -> Unit = {},
    pinnedState: PinnedJourneyState? = null,
    /** The active bubble stands in for the pinned card until it is closed. */
    pinnedAsBubble: Boolean = false,
    onPinnedOpen: () -> Unit = {},
    onPinnedRemove: () -> Unit = {},
    onPinnedBubble: (() -> Unit)? = null,
    onCorrectAlighting: ((Boolean) -> Unit)? = null,
    onHeaderArrivalDismiss: () -> Unit = {},
    textHistory:List<SeenEditorialText> = emptyList(),
    onTextShown:(JourneyContent,String)->Unit={_,_->},
    nextHeader:suspend ()->String={TransitHeaderRotation.next()},
    tipWeekEndsAt:Long?=null,
    onSearchConnections: (OverviewEntry) -> Unit = onOpenConnection,
    /** M03: why there is nothing to show (null: unknown). */
    emptyState: OverviewEmptyState? = null,
    /** M03: "Häufiges Ziel speichern" and "Favoriten anzeigen". */
    onAddFavorite: () -> Unit = onSearch,
    onShowFavorites: () -> Unit = onSearch,
    /** H02: "Start wählen" opens the start picker. */
    onChooseStart: () -> Unit = onSearch,
    /** Build 116: a pinned journey is under way – its card takes the whole page. */
    takeover: Boolean = false,
) {
    val walkingSettings=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current
    var header by rememberSaveable { mutableStateOf(TransitHeaderRotation.next()) }
    var chronicle by rememberSaveable {mutableStateOf(false)}
    var headerVisible by remember {mutableStateOf(false)}
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed=lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var headerRecorded by remember(header) {mutableStateOf(false)}
    val surfaceVisible=dev.ambivalenton.openv.ui.tutorial.LocalTutorialSurfaceActive.current
    val tutorial=dev.ambivalenton.openv.ui.tutorial.LocalTutorialState.current
    var hasResumed by rememberSaveable { mutableStateOf(false) }
    var ignoredArrival by rememberSaveable { mutableStateOf<String?>(null) }
    val announcement = pinnedState?.arrival?.takeIf {it.sessionId!=ignoredArrival}
    var lastAnnouncement by rememberSaveable { mutableStateOf<String?>(null) }
    var rotationRequested by remember {mutableIntStateOf(0)}
    var rotationProcessed by remember {mutableIntStateOf(0)}
    val latestNextHeader by rememberUpdatedState(nextHeader)
    LaunchedEffect(announcement?.sessionId) {
        if (announcement != null) lastAnnouncement=announcement.sessionId
        else if (lastAnnouncement != null) { rotationRequested++;lastAnnouncement=null }
    }
    LifecycleResumeEffect(isActive) {
        if (isActive) {
            if (hasResumed) rotationRequested++ else hasResumed = true
        }
        onPauseOrDispose {}
    }
    val textScale=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.readability.normalized().textScale
    val overviewBodySize=OVERVIEW_BODY_SIZE*textScale
    val overviewLineHeight=OVERVIEW_LINE_HEIGHT*textScale
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.quietMode
    val showHeader = !dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.minimalistMode && ((settings.showQuotes && !quiet) || announcement != null)
    val canRecord=showHeader && !quiet && isActive && resumed && surfaceVisible && headerVisible && settings.showQuotes && pinnedState?.session==null && announcement==null && tutorial?.session==null
    LaunchedEffect(header,tipWeekEndsAt) {
        if(editorialContentForText(header)?.kind==JourneyContentKind.TIP) {
            val remaining=(tipWeekEndsAt ?: 0)-System.currentTimeMillis()
            if(remaining>0)kotlinx.coroutines.delay(remaining)
            header=TransitHeaderRotation.next()
        }
    }
    LaunchedEffect(rotationRequested,canRecord) {
        if(canRecord && rotationRequested>rotationProcessed) {
            rotationProcessed=rotationRequested
            header=try {latestNextHeader()} catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(_:Exception){TransitHeaderRotation.next()}
        }
    }
    LaunchedEffect(header,canRecord) {
        if(canRecord && !headerRecorded) editorialContentForText(header)?.let {entry->headerRecorded=true;onTextShown(entry,header)}
    }
    if(chronicle) EditorialHistorySheet(textHistory,editorialContentForText(header)?.kind) {chronicle=false}
    val introVisible = remember { MutableTransitionState(showHeader || ui.loading) }
    introVisible.targetState = showHeader || ui.loading
    val uri = LocalUriHandler.current
    val rowKeys=overviewRowKeys(ui.entries)
    val firstReadyIndex=ui.entries.indexOfFirst {it.trip!=null}
    // Build 116: a running pinned journey takes the whole page. The card grows out of its place
    // (shared bounds, a soft spring), everything else gives way; ending the companion brings the
    // overview back the same way. In quiet mode the change is immediate.
    val calm = dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    androidx.compose.animation.SharedTransitionLayout(Modifier.fillMaxSize()) {
    val shared = this
    androidx.compose.animation.AnimatedContent(takeover && pinnedState?.session != null && !pinnedAsBubble, label = "overview-takeover",
        transitionSpec = {
            if (calm) fadeIn(androidx.compose.animation.core.snap()) togetherWith fadeOut(androidx.compose.animation.core.snap())
            else (fadeIn(androidx.compose.animation.core.spring(dampingRatio = .9f, stiffness = 260f)) togetherWith
                fadeOut(androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 520f))) using
                androidx.compose.animation.SizeTransform(clip = false)
        }) { full ->
    val bounds = with(shared) { rememberSharedContentState("pinned-journey") }
    val journeyBounds = with(shared) {
        Modifier.sharedBounds(bounds, this@AnimatedContent,
            boundsTransform = { _, _ -> androidx.compose.animation.core.spring(dampingRatio = .8f, stiffness = 280f) },
            resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.RemeasureToBounds)
    }
    if (full) {
        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp,
            bottom = 12.dp + dev.ambivalenton.openv.ui.components.LocalNavigationClearance.current).testTag("pinned-journey-page")) {
            PinnedJourneyCard(pinnedState!!, onPinnedOpen, onPinnedRemove, onPinnedBubble, onCorrectAlighting,
                modifier = Modifier.fillMaxSize().then(journeyBounds), showSource = false, expanded = true)
        }
    } else
    JourneyPullRefresh(ui.loading,onRefresh,enabled=isActive) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("overview-content"),
        contentPadding = PaddingValues(start=24.dp,end=24.dp,top=20.dp,bottom=20.dp+dev.ambivalenton.openv.ui.components.LocalNavigationClearance.current),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // No rectangular backdrop behind the rounded card: the card draws its own shape and shadow.
        if (pinnedState?.session != null && !pinnedAsBubble) stickyHeader(key="pinned-card") {
            PinnedJourneyCard(pinnedState,onPinnedOpen,onPinnedRemove,onPinnedBubble,onCorrectAlighting,
                modifier=Modifier.fillMaxWidth().then(journeyBounds),showSource=false)
        }
        // M03: an empty overview starts with the question and the main action, above the info text.
        if (ui.ready && !ui.loading && ui.error == null && ui.entries.isEmpty())
            item(key = "overview-empty") { OverviewEmptyView(emptyState ?: OverviewEmptyState(OverviewEmptyKind.NO_FAVORITES), onSearch, onAddFavorite, onShowFavorites) }
        if (pinnedState?.session == null && (introVisible.currentState || introVisible.targetState))
            item(key = "transit-header") {
                CalmVisibility(
                    visibleState = introVisible,
                    enter = fadeIn(tween(200)),
                    exit = shrinkVertically(tween(500)) + fadeOut(tween(500)),
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (showHeader)
                            Column(Modifier.testTag("transit-header")) {
                                DisposableEffect(Unit) {onDispose {headerVisible=false}}
                                QuoteFactHeader(
                                    announcement?.text ?: header,
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontSize = OVERVIEW_HEADER_SIZE*textScale,
                                            lineHeight = 23.sp*textScale,
                                            fontFamily = with(dev.ambivalenton.openv.ui.theme.LocalAppVisuals.current) {if(customFont==null && theme==null) FontFamily.Serif else fontFamily},
                                            textAlign = TextAlign.Center,
                                        ),
                                    onSwap = {
                                        ignoredArrival=announcement?.sessionId
                                        lastAnnouncement=null
                                        rotationRequested++
                                        onHeaderArrivalDismiss()
                                    },
                                    modifier = Modifier.fillMaxWidth().onGloballyPositioned {headerVisible=it.boundsInWindow().let {r->r.width>0f && r.height>0f}},
                                    onOpenHistory={chronicle=true},
                                )
                            }
                        OverviewDottedDivider(isLoading = ui.loading)
                    }
                }
            }
        ui.error?.let { error ->
            item {
                // Without a start the card offers the two ways to one, each over the full width;
                // other problems keep the retry.
                if (ui.missingStart) OverviewStartCard(error, onPermission, onChooseStart)
                else ErrorPanel(error, onRefresh)
            }
        }
        if (pinnedState?.session != null && ui.loading) item(key="overview-loading") {
            OverviewDottedDivider(Modifier.testTag("overview-loading"),isLoading=true)
        }
        itemsIndexed(ui.entries, key = { index, _ -> rowKeys[index] }) { index, entry ->
            val trip = remember(entry.trip,walkingSettings.ignoreWalking,walkingSettings.walkingSpeed) {entry.trip?.let {if(walkingSettings.ignoreWalking)assembleTimeline(it,walkingSettings).trip else it}}
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if(settings.cards)OverviewConnectionCard(entry,settings,{onOpenConnection(entry)},Modifier.pressScale(.98f).then(
                    if(index==firstReadyIndex)Modifier.tutorialTarget("overview-connection") else Modifier),
                    onSearchConnections={onSearchConnections(entry)})
                else if (trip != null) {
                    val parts =
                        remember(entry.favorite, trip, settings, entry.variant, entry.weather, entry.network?.anchorIcon) {
                            // Text is decoration of a valid journey: a wording fault must never take
                            // the overview (and the app) down; the plain sentence keeps all facts.
                            runCatching {
                                OverviewSentenceGenerator.sentence(
                                    entry.favorite.forDestination(entry.query?.destination),
                                    trip,
                                    settings,
                                    entry.variant,
                                    entry.weather,
                                    entry.network?.anchorIcon,
                                )
                            }.getOrElse {
                                OverviewSentenceGenerator.sentence(entry.favorite.copy(icon = FavoriteIcon.PLACE, counterpole = null), trip, settings, entry.variant)
                            }
                        }
                    val highlights = overviewFavoriteHighlights(entry.favorite.accentSeed)
                    val originHighlights=overviewFavoriteHighlights(if(entry.favorite.counterpole!=null)entry.favorite.originAccentSeed else entry.favorite.accentSeed)
                    val accent = highlights.original
                    val muted = highlights.tonal
                    val text =
                        remember(parts, accent, muted,originHighlights,overviewBodySize) {
                            overviewAnnotatedString(parts, accent, baseSize=overviewBodySize,mutedAccent = muted,originAccent=originHighlights.original,originMutedAccent=originHighlights.tonal)
                        }

                    val hasWeather = settings.showWeather && entry.weather?.covers(trip.endTime) == true
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text,
                            Modifier.fillMaxWidth().pressScale(.985f)
                                .testTag("overview-${entry.favorite.id}")
                                .then(if(index==firstReadyIndex) Modifier.tutorialTarget("overview-connection") else Modifier)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClickLabel =
                                        tr("Verbindungen zu %1\$s öffnen", entry.favorite.label),
                                    onLongClickLabel=tr("Weitere Verbindungen suchen"),
                                    onLongClick={onSearchConnections(entry)},
                                    onClick={onOpenConnection(entry)},
                                )
                                .padding(top = 8.dp, bottom = if (hasWeather) 0.dp else 8.dp),
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = overviewBodySize,
                                    lineHeight = overviewLineHeight,
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (hasWeather)
                            Text(
                                tr("Wetter: Open-Meteo"),
                                Modifier.fillMaxWidth().clickable(
                                    role = Role.Button,
                                    onClickLabel = tr("Wetterquelle Open-Meteo öffnen"),
                                ) { uri.openUri("https://open-meteo.com/") },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp*textScale, lineHeight = 12.sp*textScale),
                                color = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color.White
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                    }
                } else {
                    Text(
                        overviewAnnotatedString(
                            listOf(
                                OverviewTextPart(
                                    if (entry.loading) tr("Die nächste Verbindung zu ")
                                    else tr("Für dein Ziel ")
                                ),
                                OverviewTextPart(entry.favorite.label, OverviewEmphasis.PLACE),
                                OverviewTextPart(
                                    if (entry.loading) tr(" wird gesucht …")
                                    else tr(" ist noch keine Verbindung verfügbar.")
                                ),
                            ),
                            overviewFavoriteHighlights(entry.favorite.accentSeed).original,
                            baseSize=overviewBodySize,
                            mutedAccent =
                                overviewFavoriteHighlights(entry.favorite.accentSeed).tonal,
                        ),
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontSize = overviewBodySize,
                                lineHeight = overviewLineHeight,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                entry.error?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onRefresh) { Text(tr("Erneut versuchen")) }
                }
            }
        }
    }
    }
    }
    }
}

/**
 * M03: the empty overview names its cause and leads to the matching step: "Wo möchtest du hin?" with
 * "Verbindung suchen" (and "Häufiges Ziel speichern"), "Hier bist du gerade" or "Heute sind keine
 * Ziele eingeblendet" with "Favoriten anzeigen".
 */
@Composable internal fun OverviewEmptyView(state: OverviewEmptyState, onSearch: () -> Unit, onAddFavorite: () -> Unit,
    onShowFavorites: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("overview-empty-${state.kind.name.lowercase()}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val (title, body) = when (state.kind) {
            OverviewEmptyKind.NO_FAVORITES -> tr("Wo möchtest du hin?") to
                tr("Suche eine Verbindung oder speichere ein häufiges Ziel. Hier erscheinen danach automatisch die nächsten Fahrten.")
            OverviewEmptyKind.HERE -> (state.place?.let { tr("Hier bist du gerade: %1\$s", it) } ?: tr("Hier bist du gerade")) to
                tr("Von hier aus zeigt die Übersicht Fahrten zu deinen anderen Zielen.")
            OverviewEmptyKind.HIDDEN_BY_TIME -> tr("Heute sind keine Ziele eingeblendet") to
                (state.place?.let { tr("Zu dieser Zeit gilt „%1\$s“ als dein Standort. Deine Zeitregeln blenden die übrigen Ziele gerade aus.", it) }
                    ?: tr("Deine Zeitregeln blenden gerade alle Ziele aus."))
        }
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ThemedButton(onSearch, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("overview-empty-search")) { Text(tr("Verbindung suchen")) }
        if (state.kind == OverviewEmptyKind.NO_FAVORITES)
            ThemedTonalButton(onAddFavorite, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("overview-empty-add")) { Text(tr("Häufiges Ziel speichern")) }
        else ThemedTonalButton(onShowFavorites, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("overview-empty-favorites")) { Text(tr("Favoriten anzeigen")) }
    }
}

/**
 * No start yet. A calm card (not an error) with the two ways to a start: the own location
 * (asks for access, or opens the Android settings after a final refusal, and switches to the
 * automatic start) and a chosen start. Both buttons span the width, so their labels never wrap.
 */
@Composable
internal fun OverviewStartCard(message: String, onUseLocation: () -> Unit, onChooseStart: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().testTag("overview-start-card"), shape = dev.ambivalenton.openv.ui.theme.appCorner(24.dp),
        color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                dev.ambivalenton.openv.ui.components.AppIcon(R.drawable.ic_place, null, Modifier.size(24.dp))
                FittedText(tr("Wo startest du?"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            dev.ambivalenton.openv.ui.components.ThemedButton(onUseLocation,
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("overview-use-location")) {
                dev.ambivalenton.openv.ui.components.AppIcon(R.drawable.ic_gps, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                FittedText(tr("Meinen Standort nutzen"))
            }
            // On the tinted card the outline and text take the card's ink, so the button never looks disabled.
            dev.ambivalenton.openv.ui.components.ThemedOutlinedButton(onChooseStart,
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("overview-choose-start"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSecondaryContainer),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, scheme.onSecondaryContainer.copy(alpha = .6f))) {
                FittedText(tr("Start wählen"))
            }
        }
    }
}

