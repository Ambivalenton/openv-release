// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import dev.ambivalenton.openv.ui.components.systemCancelled
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.drawscope.translate
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.NetworkCountdown
import dev.ambivalenton.openv.ui.components.AppIcon
import dev.ambivalenton.openv.ui.components.shapeShadow
import dev.ambivalenton.openv.ui.components.resource
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.favoriteColors
import dev.ambivalenton.openv.ui.theme.systemSurfacePalette
import dev.ambivalenton.openv.ui.theme.drawDecorativeThemeTexture
import dev.ambivalenton.openv.ui.theme.buildFullThemeBackdrop
import dev.ambivalenton.openv.rendering.drawSystemPane
import dev.ambivalenton.openv.rendering.isOperatingSystem
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import dev.ambivalenton.openv.widget.GlanceThemeHelper
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the plan can ask its screen to do. Live and edit mode use different subsets. */
data class NetworkPlanActions(
    val onNodeTap: (String) -> Unit = {},
    val onNodeHold: (String) -> Unit = {},
    val onEdgeTap: (String) -> Unit = {},
    val onEdgeMenu: (String) -> Unit = {},
    val onMove: (String, GridPoint) -> Unit = { _, _ -> },
    /** Returns the new line's ID, or null when the line is not allowed. */
    val onConnect: (String, String) -> String? = { _, _ -> null },
    val onFreePoint: (GridPoint) -> Unit = {},
    val onToTray: (String) -> Unit = {},
    val onConnectRequest: (String) -> Unit = {},
    /** Hold in live mode with "Favoriten-Symbol frei bewegen" switches to edit mode. */
    val onEditRequest: () -> Unit = {},
    /** M17: a tap that hits several close targets about equally; the caller lets the person choose. */
    val onAmbiguousTap: ((List<String>, List<String>) -> Unit)? = null,
    /** Build 104: a moved favourite was let go above the delete zone of the edit bar. */
    val onDelete: (String) -> Unit = {},
)

private enum class Press { TAP, DRAG, CANCEL }

/** Sizes of a node: 1.0 regular, 1.35 while its time rule highlights it. */
internal fun nodeScale(favorite: Favorite, now: ZonedDateTime): Float =
    if (favorite.rule?.matches(now) == true) 1.35f else 1f

/** A node fills most of its grid field (build 68: no ring around it any more, so the shape itself
 * takes that room); lines run straight into the shape. */
internal const val NODE_FIELD_FRACTION = .84f

private class Squish(val axis: Float) {
    val amount = Animatable(0f)
    val pulse = Animatable(1f)
    val ring = Animatable(0f)
}

/** How far lines, vehicles and departure times fade while the names are held. */
private const val PLAN_DIM = .7f

/** One departure chip ready to draw: its line from the node outwards, text and measured size. */
private class PlanChip(val chip: dev.ambivalenton.openv.ui.NetworkCountdownChip, val from: List<Offset>, val text: String, val time: String,
                       val late: Int?, val planned: Boolean, val color: Color, val size: androidx.compose.ui.geometry.Size)

private class RippleRun(val edgeId: String, val reversed: Boolean) { val progress = Animatable(0f) }

/**
 * Settings preview of a design (build 70): no gestures and no TalkBack actions on the sample plan.
 * [vehicles] shows the trains and buses, [animateVehicles] lets them move (otherwise they stand still).
 */
data class NetworkPlanPreview(val design: NetworkPlanDesign, val vehicles: Boolean, val animateVehicles: Boolean)
private class Settling(val edgeId: String, val from: List<Offset>) { val progress = Animatable(0f) }

/**
 * The favourite network plan on the grid chosen in the favourite settings (3 × 3 … 12 × 12). Live mode: tap a node (route from the start), hold
 * a node (location override), tap a line (route between its nodes). Edit mode: the grid appears,
 * nodes wiggle; tap edits, hold + drag moves, swipe from a node draws an elastic rope that snaps
 * into an octilinear line, tap on a line opens its direction menu. Pure layout lives in
 * NetworkLayoutEngine; this composable only renders and resolves gestures.
 */
@Composable
fun NetworkPlan(
    store: StoredState,
    now: ZonedDateTime,
    editing: Boolean,
    anchorId: String?,
    countdown: NetworkCountdown,
    trayPick: String?,
    actions: NetworkPlanActions,
    modifier: Modifier = Modifier,
    preview: NetworkPlanPreview? = null,
    pinnedState: PinnedJourneyState? = null,
    /** Build 104: the edit bar's delete zone, shared while a favourite is moved. */
    deleteZone: NetworkDeleteZone? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // Where the plan lies on screen: the finger's position is compared with the delete zone above it.
    var planOrigin by remember { mutableStateOf(Offset.Zero) }
    val latestZone by rememberUpdatedState(deleteZone)
    val settings = LocalAppSettings.current
    val visuals = dev.ambivalenton.openv.ui.theme.LocalAppVisuals.current
    val look = (preview?.design ?: settings.networkPlanDesign).lookFor(visuals.theme)
    // Motifs step back for "Bewegung reduzieren" and "Hoher Textkontrast", as on the app's own panels.
    val sheetMotif = look.motif && !settings.monochrome && visuals.readability.contrast != AppContrast.HIGH
    val systemPalette = scheme.systemSurfacePalette()
    val quiet = settings.reduceMotion
    val dark = scheme.background.luminance() < .3f
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val latestActions by rememberUpdatedState(actions)
    val favorites = remember(store.favorites) { store.favorites.associateBy { it.id } }
    val placed = store.network.placed.filterKeys { it in favorites }
    // The nodes present when the plan appeared; later ones are animated in.
    val initialNodes = remember { placed.keys.toSet() }
    val birthHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val drawn = store.network.edges.filter { it.aId in placed && it.bId in placed }
    val pinnedJourney = if (editing || preview != null) null else remember(store.favorites, store.network, pinnedState, now) {
        pinnedNetworkJourney(store, pinnedState, maxOf(now.toInstant().toEpochMilli(), System.currentTimeMillis()))
    }
    val columns = store.network.columns; val rows = store.network.rows
    val routes = remember(placed, drawn.map { Triple(it.id, it.aId, it.bId) }, columns, rows) { routeNetwork(placed, drawn, columns, rows) }
    val active = store.network.activeEdges(now).map { it.id }.toSet()
    val scales = placed.keys.associateWith { id -> nodeScale(favorites.getValue(id), now) }
    // Only a virtual location that still applies carries the pin (switching to GPS ends it).
    val overrideId = store.locationOverride?.takeIf { store.originSource() == OriginSource.OVERRIDE }?.nodeId
    // What a hold does in live mode, and the favourite pinned to the overview (only shown
    // while that hold action is chosen, because only then it changes the overview).
    val holdAction = store.settings.holdAction
    val overviewPins = if (holdAction == NetworkHoldAction.OVERVIEW) store.overviewPins.toSet() else emptySet()
    // The design's sheet: printed-map designs use their paper in light mode and a dark sheet with a
    // hint of it in dark mode (AMOLED included), so the plan never glares in a dark app.
    val paper = look.paper?.let { if (dark && !look.night) lerp(scheme.surfaceContainerLow, Color(it), .05f) else Color(it) } ?: scheme.surfaceContainerLow
    val ink = if (look.night) Color(look.ink) else if (dark || look.paper == null) scheme.onSurface else Color(look.ink)
    // A night sheet (Dubai) reads like dark mode for line colours.
    val sheetDark = dark || look.night
    /** Glass themes have a translucent sheet; contrast is judged on what the eye actually sees. */
    val paperSolid = paper.compositeOver(scheme.background)
    val opponentColors = drawn.map { e ->
        val a = favorites[e.aId]?.let { favoriteColors(it.accentSeed, dark, scheme).accent.toArgb() }
        val b = favorites[e.bId]?.let { favoriteColors(it.accentSeed, dark, scheme).accent.toArgb() }
        edgeLineColor(a, b, paper.toArgb(), scheme.tertiary.toArgb(), dark)
    }
    val paletteColors = look.palette?.let { assignPaletteColors(opponentColors, it) }
    val lineColors = drawn.withIndex().associate { (i, e) ->
        e.id to Color(when {
            settings.monochrome -> lerp(scheme.onSurface, scheme.surface, .25f).toArgb()
            paletteColors == null -> opponentColors[i]
            // Dark sheet: the operator colour is lightened until it reads (≥ 3:1).
            sheetDark -> readableLine(paletteColors[i], paperSolid.toArgb())
            else -> paletteColors[i].toInt()
        })
    }
    // Light sheet: a pale operator colour (yellow, silver) keeps its tone and gets a fine dark edge instead.
    val lineEdges = if (sheetDark || paletteColors == null || settings.monochrome) emptyMap() else drawn.withIndex().mapNotNull { (i, e) ->
        val c = paletteColors[i].toInt()
        if (androidx.core.graphics.ColorUtils.calculateContrast(c, paperSolid.toArgb()) >= 3.0) null
        else e.id to Color(readableLine(paletteColors[i], paperSolid.toArgb()))
    }.toMap()
    // Build 110: the material of the App-Theme plan (sheet and stations), monochrome stays plain.
    val finish = if (settings.monochrome) PlanFinish.PLAIN else look.finish
    val planInk = PlanInk(paper, ink, scheme.background, scheme.primary, scheme.secondary, scheme.tertiary, scheme.outlineVariant, dark,
        quiet || visuals.readability.contrast == AppContrast.HIGH)
    /** Mexiko-Stadt: a station takes the colour of its first line. */
    val stationLineColor = placed.keys.associateWith { id -> drawn.firstOrNull { it.touches(id) }?.let { lineColors[it.id] } }

    var moving by remember { mutableStateOf<String?>(null) }
    var movePointer by remember { mutableStateOf(Offset.Zero) }
    var moveTarget by remember { mutableStateOf<GridPoint?>(null) }
    var ropeFrom by remember { mutableStateOf<String?>(null) }
    var ropeTip by remember { mutableStateOf(Offset.Zero) }
    var ropeSnap by remember { mutableStateOf<String?>(null) }
    var ropeFrame by remember { mutableIntStateOf(0) }
    val rope = remember { RopeSimulation() }
    var settlingRun by remember { mutableStateOf<Settling?>(null) }
    var rippleRun by remember { mutableStateOf<RippleRun?>(null) }
    val squishes = remember { mutableStateMapOf<String, Squish>() }
    var undoGesture by remember { mutableIntStateOf(0) }
    /** Live mode: names show only while a finger rests on a free spot of the plan (build 68). */
    var labelsHeld by remember { mutableStateOf(false) }
    /** A hold in live mode switched to edit mode and still carries its favourite. The
     * mode change must not end that gesture, so the reset below leaves the move alone. */
    var holdToEdit by remember { mutableStateOf(false) }
    val editingNow by rememberUpdatedState(editing)
    val holdNow by rememberUpdatedState(holdAction)
    LaunchedEffect(editing) {
        ropeFrom = null; ropeSnap = null; labelsHeld = false
        if (!holdToEdit) { moving = null; moveTarget = null; undoGesture++ }
    }
    /** The grid fades in with edit mode (also when a hold switches to it) instead of popping up. */
    // Long enough to see the dots before they become plus signs.
    val gridIn by animateFloatAsState(if (editing) 1f else 0f, if (quiet) snap() else tween(if (editing) 460 else 300), label = "network-grid")
    val labelMeasurer = rememberTextMeasurer()
    // Words are hyphenated by the platform only where even the widest plate cannot hold them.
    val labelStyle = MaterialTheme.typography.labelSmall.let { it.copy(fontSize = it.fontSize * .9f, lineHeight = it.lineHeight * .9f,
        hyphens = androidx.compose.ui.text.style.Hyphens.Auto) }
    val heldLabelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, hyphens = androidx.compose.ui.text.style.Hyphens.Auto)
    val labelAlpha by animateFloatAsState(if (editing || labelsHeld) 1f else 0f, if (quiet) snap() else tween(if (labelsHeld || editing) 140 else 220),
        label = "network-label-alpha")
    /** While names are held, lines, vehicles and times step back so the names stand out (build 69). */
    val dim by animateFloatAsState(if (labelsHeld && !editing) 1f else 0f, if (quiet) snap() else tween(if (labelsHeld) 140 else 220),
        label = "network-label-dim")

    BoxWithConstraints(modifier.fillMaxWidth().testTag("network-plan"), contentAlignment = Alignment.TopCenter) {
        val side = minOf(maxWidth, 420.dp)
        val sidePx = with(density) { side.toPx() }
        val geometry = remember(sidePx, columns, rows) { NetworkGeometry(sidePx, columns, rows) }
        val pitch = geometry.pitch
        val widthPx = geometry.width; val heightPx = geometry.height
        // Most of a field; dense grids (12 × 12 on a phone) keep about 34 dp as long as it fits.
        val nodePx = max(pitch * NODE_FIELD_FRACTION, kotlin.math.min(pitch * .95f, with(density) { 34.dp.toPx() }))
            .coerceAtMost(with(density) { 68.dp.toPx() })
        val touch = with(density) { 24.dp.toPx() }
        val baseLineWidth = max(with(density) { 3.dp.toPx() }, pitch * .16f).coerceAtMost(with(density) { 8.dp.toPx() })
        val lineWidth = baseLineWidth * look.weight
        /** Vehicles keep about the Öpen-V size, also on very thin or very wide lines. */
        val vehicleWidth = baseLineWidth * look.weight.coerceIn(.85f, 1.25f)
        /** One path for line, ripple-free drawing and vehicles: octilinear bends or free curves. */
        fun linePath(points: List<Offset>): Path = if (look.smooth) smoothPath(points) else roundedPath(points, pitch * look.corner)
        val routesPx = routes.mapValues { (_, path) -> path.map(geometry::center) }
        fun radiusOf(id: String) = nodePx * (scales[id] ?: 1f) / 2f
        fun centerOf(id: String) = if (moving == id) movePointer else placed[id]?.let(geometry::center)
        fun hitNode(o: Offset): String? = placed.keys.mapNotNull { id -> centerOf(id)?.let { id to (it - o).getDistance() } }
            .filter { (id, d) -> d <= max(touch, radiusOf(id)) }.minByOrNull { it.second }?.first
        fun hitEdge(o: Offset): String? = routesPx.map { (id, pts) -> id to distanceToPolyline(o, pts) }
            .filter { it.second <= touch * .75f }.minByOrNull { it.second }?.first
        /**
         * M17: on a dense plan a tap can hit two nodes or two lines about equally well. Then no
         * target is guessed: all of them are offered by name (nodes first, then lines).
         */
        fun closeTargets(o: Offset): Pair<List<String>, List<String>>? {
            val nodes = placed.keys.mapNotNull { id -> centerOf(id)?.let { id to (it - o).getDistance() } }
                .filter { (id, d) -> d <= max(touch, radiusOf(id)) }.sortedBy { it.second }
            val lines = if (nodes.isNotEmpty()) emptyList() else routesPx.map { (id, pts) -> id to distanceToPolyline(o, pts) }
                .filter { it.second <= touch * .75f }.sortedBy { it.second }
            fun close(list: List<Pair<String, Float>>) = list.size > 1 && list[1].second - list[0].second < touch * .2f
            return if (close(nodes) || close(lines))
                nodes.takeIf { close(it) }.orEmpty().take(4).map { it.first } to lines.takeIf { close(it) }.orEmpty().take(4).map { it.first }
            else null
        }
        fun freePointAt(o: Offset, except: String? = null): GridPoint? = geometry.pointNear(o, .6f)?.takeIf { p ->
            p.free(placed.filterKeys { it != except }.values, columns, rows)
        }

        // Rope frames: only while a rope exists; the loop ends with the gesture.
        LaunchedEffect(ropeFrom) {
            val from = ropeFrom ?: return@LaunchedEffect
            val start = placed[from]?.let(geometry::center) ?: return@LaunchedEffect
            rope.reset(start)
            var last = withFrameNanos { it }
            while (ropeFrom == from) {
                val frame = withFrameNanos { it }
                val dt = (frame - last) / 1_000_000_000f; last = frame
                val anchor = placed[from]?.let(geometry::center) ?: break
                val tip = ropeSnap?.let { placed[it]?.let(geometry::center) }?.let { target -> ropeTip + (target - ropeTip) * .55f } ?: ropeTip
                rope.step(dt, anchor, tip, with(density) { 2400.dp.toPx() }, pitch * .6f)
                ropeFrame++
            }
        }

        val wiggle = if (editing && !quiet) rememberInfiniteTransition(label = "network-wiggle")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(300, easing = LinearEasing)), label = "network-wiggle-phase") else null

        // Vehicles: small trains and buses seen from above travel along the lines in the allowed
        // directions. The frame loop runs only while the plan is shown in live mode, the app is in
        // the foreground and neither "Bewegung reduzieren" nor the power saving mode is on.
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        // "Animierte Verkehrsmittel anzeigen" (Design & Darstellung → Netzplan-Design) turns them off.
        val vehiclesMove = pinnedJourney == null && !editing && !quiet && !settings.powerSaving && drawn.isNotEmpty() && moving == null && ropeFrom == null &&
            android.animation.ValueAnimator.areAnimatorsEnabled() && (preview?.let { it.vehicles && it.animateVehicles } ?: settings.networkVehicles)
        // A preview without motion shows the vehicles standing on their lines.
        val vehiclesStill = !vehiclesMove && preview?.vehicles == true && !editing && drawn.isNotEmpty()
        val vehiclesOn = vehiclesMove
        var vehicleClock by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(vehiclesOn, lifecycle) {
            if (!vehiclesOn) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                // The infinite-animation frame clock ends the loop wherever endless motion is not
                // wanted (UI tests, accessibility services that ask for it).
                var last = androidx.compose.animation.core.withInfiniteAnimationFrameNanos { it }
                var skip = false
                while (true) {
                    val frame = androidx.compose.animation.core.withInfiniteAnimationFrameNanos { it }
                    // Every second frame is enough for the slow vehicles and halves the drawing work.
                    skip = !skip
                    if (skip) continue
                    vehicleClock += ((frame - last) / 1_000_000_000f).coerceAtMost(.25f); last = frame
                }
            }
        }
        val vehicleKinds = drawn.withIndex().associate { (i, e) -> e.id to vehicleKindFor(e, favorites, countdown, i) }
        val tracks = remember(routesPx, pitch, look) {
            routesPx.mapValues { (_, pts) -> androidx.compose.ui.graphics.PathMeasure().apply { setPath(linePath(pts), false) } }
        }
        val panel = dev.ambivalenton.openv.ui.theme.appCorner(28.dp)
        val panelPad = 6.dp

        Box(Modifier.size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
            // A quiet map sheet behind the plan: it reads as one object, like a printed network map.
            .drawBehind {
                val pad = panelPad.toPx()
                val outline = panel.createOutline(androidx.compose.ui.geometry.Size(size.width + pad * 2, size.height + pad * 2), layoutDirection, this)
                translate(-pad, -pad) {
                    drawSheetUnder(finish, outline, planInk)
                    sheetFill(finish, paper, scheme.background)?.let { drawOutline(outline, it) }
                    // The sheet carries the theme's motif or system pane.
                    val theme = visuals.theme
                    if (sheetMotif && theme != null) {
                        val sheet = Path().apply { addOutline(outline) }
                        clipPath(sheet) {
                            if (theme.isOperatingSystem) drawIntoCanvas { drawSystemPane(it.nativeCanvas, theme, size.width + pad * 2,
                                size.height + pad * 2, systemPalette, density.density) }
                            else if (theme.texture != ThemeTexture.NONE && !settings.reduceMotion) {
                                val area = androidx.compose.ui.geometry.Size(size.width + pad * 2, size.height + pad * 2)
                                drawDecorativeThemeTexture(theme.texture, scheme.secondary, buildFullThemeBackdrop(theme.texture, area), paper)
                            }
                        }
                    }
                    drawSheetOver(finish, outline, planInk)
                }
            }
            .then(if (preview != null) Modifier.clearAndSetSemantics {} else Modifier.semantics { isTraversalGroup = true })
            .then(if (preview != null) Modifier else Modifier.onGloballyPositioned { planOrigin = it.positionInRoot() })
            .then(if (preview != null) Modifier else Modifier.pointerInput(routesPx.keys, placed, sidePx, columns, rows, trayPick, undoGesture) {
                val slop = viewConfiguration.touchSlop
                val hold = viewConfiguration.longPressTimeoutMillis
                awaitEachGesture {
                    // Read per gesture: a hold may switch to edit mode without restarting this handler.
                    val editing = editingNow
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val node = hitNode(down.position)
                    val edge = if (node == null) hitEdge(down.position) else null
                    val free = if (node == null && edge == null && editing) freePointAt(down.position) else null
                    // Live mode also watches free spots: holding one shows the names.
                    if (node == null && edge == null && free == null && editing) return@awaitEachGesture
                    // null = long-press timeout without movement
                    val press: Press? = withTimeoutOrNull(hold) {
                        var result: Press? = null
                        while (result == null) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            result = when {
                                change == null || event.changes.count { it.pressed } > 1 -> Press.CANCEL
                                // A touch the system takes away (swipe up to the recent apps,
                                // which starts where the plan lies behind the navigation) ends with an
                                // already consumed release. That is not a tap and must not search.
                                change.systemCancelled -> Press.CANCEL
                                !change.pressed -> Press.TAP
                                (change.position - down.position).getDistance() > slop ->
                                    // Edit mode: a swipe from a node draws a line; it must not scroll the page.
                                    if (editing && node != null) { change.consume(); Press.DRAG } else Press.CANCEL
                                else -> null
                            }
                        }
                        result
                    }
                    when {
                        press == Press.CANCEL -> return@awaitEachGesture
                        !editing && press == Press.TAP && latestActions.onAmbiguousTap != null && closeTargets(down.position) != null -> {
                            val (nodes, lines) = closeTargets(down.position)!!
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestActions.onAmbiguousTap?.invoke(nodes, lines)
                        }
                        !editing && node != null && press == Press.TAP -> {
                            val c = centerOf(node)
                            val axis = c?.let { atan2(down.position.y - it.y, down.position.x - it.x) } ?: 0f
                            if (!quiet) scope.launch {
                                val s = Squish(axis).also { squishes[node] = it }
                                launch { s.amount.animateTo(-.10f, tween(90)); s.amount.animateTo(0f, spring(dampingRatio = .35f, stiffness = 520f)) }
                                s.pulse.animateTo(1.06f, tween(90)); s.pulse.animateTo(1f, spring(dampingRatio = .5f, stiffness = 600f))
                            }
                            scope.launch { if (!quiet) delay(200); latestActions.onNodeTap(node) }
                        }
                        node != null && press == null && (editing || holdNow == NetworkHoldAction.MOVE) -> {
                            // Hold, then drag: the node lifts, follows the finger and snaps to a free point.
                            // In live mode the same hold first switches to edit mode (the pencil
                            // unfolds, the grid fades in) and the favourite keeps following the finger.
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!editing) { holdToEdit = true; latestActions.onEditRequest() }
                            try {
                                // The start comes from the stored point: centerOf() already follows the finger once moving.
                                val startCenter = placed[node]?.let(geometry::center) ?: down.position
                                moving = node; movePointer = startCenter; moveTarget = null
                                val zone = latestZone
                                zone?.moving = node
                                val grab = startCenter - down.position
                                var outside = false
                                var cancelled = false
                                var overZone = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.systemCancelled) { cancelled = true; break }
                                    change.consume()
                                    movePointer = change.position + grab
                                    outside = change.position.x < 0 || change.position.y < 0 || change.position.x > widthPx || change.position.y > heightPx
                                    // Build 104: above the edit bar's delete zone the favourite is deleted when let go.
                                    val above = zone?.bounds?.contains(planOrigin + change.position) == true
                                    if (above != overZone) {
                                        overZone = above; zone?.over = above
                                        if (above) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    }
                                    val target = if (outside) null else freePointAt(movePointer, node)
                                    if (target != moveTarget && target != null && !quiet) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    moveTarget = target
                                    if (!change.pressed) break
                                }
                                val target = moveTarget
                                moving = null; moveTarget = null
                                zone?.over = false; zone?.moving = null
                                when {
                                    cancelled -> Unit
                                    overZone -> { haptics.performHapticFeedback(HapticFeedbackType.Confirm); latestActions.onDelete(node) }
                                    outside -> latestActions.onToTray(node)
                                    // Back on its own field (a hold without moving): nothing to store.
                                    target != null && target != placed[node] -> latestActions.onMove(node, target)
                                }
                            } finally { holdToEdit = false; moving = null; moveTarget = null; latestZone?.let { it.moving = null; it.over = false } }
                        }
                        !editing && node != null && press == null -> {
                            if (holdNow != NetworkHoldAction.NOTHING) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!quiet) scope.launch {
                                    val s = Squish(0f).also { squishes[node] = it }
                                    launch { s.ring.snapTo(0f); s.ring.animateTo(1f, tween(450)) }
                                    s.pulse.animateTo(1.12f, tween(120)); s.pulse.animateTo(1f, spring(dampingRatio = .45f, stiffness = 500f))
                                }
                                latestActions.onNodeHold(node)
                            }
                            // "Nichts tun": the hold is swallowed, it neither taps nor scrolls.
                            do { val event = awaitPointerEvent(); event.changes.forEach { it.consume() } } while (event.changes.any { it.pressed })
                        }
                        !editing && node == null && press == null -> {
                            // Hold on a free spot (or a line): the names appear and go again on release.
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            labelsHeld = true
                            try {
                                do { val event = awaitPointerEvent(); event.changes.forEach { it.consume() } } while (event.changes.any { it.pressed })
                            } finally { labelsHeld = false }
                        }
                        !editing && edge != null && press == Press.TAP -> {
                            val members = store.network.edges.firstOrNull { it.id == edge }?.let { store.edgeMembers(it, store.routingPosition()) }
                            if (!quiet) scope.launch {
                                val run = RippleRun(edge, members?.near?.id == store.network.edges.first { it.id == edge }.bId).also { rippleRun = it }
                                run.progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                                if (rippleRun === run) rippleRun = null
                            }
                            scope.launch { if (!quiet) delay(220); latestActions.onEdgeTap(edge) }
                        }
                        editing && node != null && press == Press.TAP -> latestActions.onNodeTap(node)
                        editing && edge != null && press == Press.TAP -> latestActions.onEdgeMenu(edge)
                        editing && free != null && press == Press.TAP -> {
                            if (trayPick != null) latestActions.onMove(trayPick, free) else latestActions.onFreePoint(free)
                        }
                        editing && node != null && press == Press.DRAG -> {
                            // Elastic rope from the node; it snaps to a valid target node.
                            ropeFrom = node; ropeTip = down.position; ropeSnap = null
                            var snapped: String? = null
                            var cancelled = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.systemCancelled) { cancelled = true; break }
                                change.consume()
                                ropeTip = change.position
                                val candidate = hitNode(change.position)?.takeIf { it != node && store.edgeRejection(node, it) == null }
                                val keep = snapped?.let { s -> centerOf(s)?.let { (it - change.position).getDistance() <= pitch * 1.4f } } == true
                                val next = if (keep) snapped else candidate?.takeIf { c -> centerOf(c)?.let { (it - change.position).getDistance() <= pitch * .9f } == true }
                                if (next != snapped && next != null && !quiet) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                snapped = next; ropeSnap = next
                                if (!change.pressed) break
                            }
                            val target = snapped
                            val from = rope.points.toList()
                            ropeFrom = null; ropeSnap = null
                            if (cancelled) Unit
                            else if (target != null) {
                                val id = latestActions.onConnect(node, target)
                                if (id != null) {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    if (!quiet) scope.launch {
                                        val run = Settling(id, from).also { settlingRun = it }
                                        run.progress.animateTo(1f, spring(dampingRatio = .5f, stiffness = 380f))
                                        if (settlingRun === run) settlingRun = null
                                    }
                                } else haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            } else {
                                val nearby = hitNode(ropeTip)
                                if (nearby != null && nearby != node) {
                                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                                    latestActions.onConnect(node, nearby)
                                }
                            }
                        }
                    }
                }
            })) {
            Canvas(Modifier.matchParentSize().graphicsLayer { alpha = 1f - PLAN_DIM * dim }) {
                @Suppress("UNUSED_VARIABLE") val frame = ropeFrame // redraw on every rope frame
                // The grid is faintly there on the Öpen-V sheet; in edit mode it is always clearly visible
                // (build 74: it fades in, also when a hold on a favourite switches to edit mode).
                if (gridIn > 0f) {
                    // Build 101/102: in edit mode every grid point is a small grey "+" (a tap on a free one adds a
                    // favourite there). Outside it the grid is gone. Opening, small dots appear first and then
                    // grow their arms into the plus; closing runs the other way.
                    val dots = (gridIn / .4f).coerceIn(0f, 1f)
                    val arms = ((gridIn - .4f) / .6f).coerceIn(0f, 1f)
                    val arm = minOf(4.5.dp.toPx(), pitch * .13f) * arms
                    val stroke = (1.6f + .2f * arms).dp.toPx()
                    val ink = scheme.onSurfaceVariant.copy(alpha = .46f * dots)
                    for (x in 0 until columns) for (y in 0 until rows) {
                        val p = GridPoint(x, y)
                        val c = geometry.center(p)
                        if (moving != null && p == moveTarget) { drawCircle(scheme.primary, pitch * .16f, c); continue }
                        if (arm < .75f) drawCircle(ink, (1f + .6f * dots).dp.toPx(), c)
                        else {
                            drawLine(ink, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), stroke, StrokeCap.Round)
                            drawLine(ink, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), stroke, StrokeCap.Round)
                        }
                    }
                }
                // Lines run to the centre of their nodes; the opaque shapes drawn above end them.
                run {
                    drawn.forEachIndexed { index, edge ->
                        if (settlingRun?.edgeId == edge.id) return@forEachIndexed
                        val pts = routes[edge.id]?.map { geometry.center(it) } ?: return@forEachIndexed
                        val moved = moving?.let { m -> if (edge.touches(m)) true else null } == true
                        val points = if (moved) listOfNotNull(centerOf(edge.aId), centerOf(edge.bId)) else pts
                        val color = lineColors[edge.id] ?: scheme.primary
                        val alpha = if (edge.id in active) 1f else .35f
                        val dash = if (settings.monochrome) monochromeDash(index)?.map { it * density.density }?.toFloatArray()?.let { PathEffect.dashPathEffect(it) } else null
                        val run = rippleRun?.takeIf { it.edgeId == edge.id }
                        if (run != null) {
                            val samples = resample(if (run.reversed) points.reversed() else points, 96)
                            val deformed = rippleOffsets(samples, run.progress.value, pitch * .22f, pitch * .8f, pitch * 1.6f)
                            val path = Path().apply { deformed.forEachIndexed { i, (o, _) -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                            drawPath(path, paper, style = Stroke(lineWidth + 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(path, color.copy(alpha = alpha), style = Stroke(lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            deformed.zipWithNext().forEach { (a, b) -> if (a.second > .05f)
                                drawLine(lerp(color, scheme.onSurface, .35f * a.second), a.first, b.first, lineWidth * .55f, StrokeCap.Round) }
                        } else {
                            val path = linePath(points)
                            val cap = if (look.butt) StrokeCap.Butt else StrokeCap.Round
                            // Metro-map line: a light casing separates crossing lines (Öpen-V, Berlin,
                            // Hongkong), a fine centre highlight gives the Öpen-V band some depth.
                            if (look.casing == LineCasing.PAPER)
                                drawPath(path, paper, style = Stroke(lineWidth + 3.dp.toPx(), cap = cap, join = StrokeJoin.Round))
                            if (look.casing == LineCasing.INK)
                                drawPath(path, ink.copy(alpha = alpha), style = Stroke(lineWidth + 2.5.dp.toPx(), cap = cap, join = StrokeJoin.Round))
                            lineEdges[edge.id]?.let { edgeColor ->
                                drawPath(path, edgeColor.copy(alpha = alpha), style = Stroke(lineWidth + 2.dp.toPx(), cap = cap, join = StrokeJoin.Round, pathEffect = dash))
                            }
                            val style = if (dash != null) LineStyle.SOLID else look.line
                            if (dash == null && look.stroke != PlanStroke.SOLID)
                                // App-Theme: the theme's own drawing technique (pixel, brush, ink, neon …).
                                drawPlanStroke(look.stroke, path, points, color, alpha, lineWidth, paper, index)
                            else {
                                if (style == LineStyle.GLOW)
                                    drawPath(path, color.copy(alpha = .22f * alpha), style = Stroke(lineWidth * 2.6f, cap = cap, join = StrokeJoin.Round))
                                drawPath(path, color.copy(alpha = alpha), style = Stroke(if (style == LineStyle.GLOW) lineWidth * .62f else lineWidth, cap = cap, join = StrokeJoin.Round, pathEffect = dash))
                                // Line languages: tube, double strand, railway sleepers, neon core.
                                when (style) {
                                    LineStyle.HOLLOW -> drawPath(path, paper, style = Stroke(lineWidth * .52f, cap = cap, join = StrokeJoin.Round))
                                    LineStyle.DOUBLE -> drawPath(path, paper, style = Stroke(lineWidth * .2f, cap = cap, join = StrokeJoin.Round))
                                    LineStyle.RAIL -> drawPath(path, paper.copy(alpha = .92f), style = Stroke(lineWidth * .4f, cap = StrokeCap.Butt, join = StrokeJoin.Round,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(lineWidth * 1.1f, lineWidth * 1.1f))))
                                    LineStyle.GLOW -> drawPath(path, Color.White.copy(alpha = .75f * alpha), style = Stroke(lineWidth * .2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    LineStyle.STITCH -> drawPath(path, lerp(color, if (color.luminance() > .5f) Color.Black else Color.White, .6f).copy(alpha = .8f * alpha),
                                        style = Stroke(lineWidth * .14f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(lineWidth * .55f, lineWidth * .4f))))
                                    LineStyle.SOLID -> Unit
                                }
                            }
                            if (dash == null && look.sheen) drawPath(path, Color.White.copy(alpha = .16f * alpha), style = Stroke(lineWidth * .28f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        // One-way lines carry cut-out chevrons; the direction is not shown by colour alone.
                        if (edge.direction != DirectionMode.BOTH && !moved) {
                            val forward = if (edge.direction == DirectionMode.A_TO_B) pts else pts.reversed()
                            val length = forward.zipWithNext().sumOf { (a, b) -> (b - a).getDistance().toDouble() }.toFloat()
                            val count = max(1, (length / (pitch * 2.5f)).toInt())
                            val samples = resample(forward, count * 2 + 1)
                            for (k in 0 until count) {
                                val at = samples[k * 2 + 1]; val ahead = samples[k * 2 + 2]
                                val dir = (ahead - at).let { val l = it.getDistance(); if (l > 0f) it / l else Offset(1f, 0f) }
                                val normal = Offset(-dir.y, dir.x)
                                val tip = at + dir * (lineWidth * .9f)
                                val back = at - dir * (lineWidth * .6f)
                                drawLine(paper, back + normal * (lineWidth * .9f), tip, lineWidth * .35f, StrokeCap.Round)
                                drawLine(paper, back - normal * (lineWidth * .9f), tip, lineWidth * .35f, StrokeCap.Round)
                            }
                        }
                    }
                    // Settling: the rope straightens into its octilinear line with a damped overshoot.
                    settlingRun?.let { run ->
                        val target = routes[run.edgeId]?.map { geometry.center(it) }
                        val edge = drawn.firstOrNull { it.id == run.edgeId }
                        if (target != null && edge != null) {
                            val f = run.progress.value
                            val a = resample(run.from, 32); val b = resample(target, 32)
                            val pts = a.indices.map { i -> a[i] + (b[i] - a[i]) * f }
                            drawPath(smoothPath(pts), lineColors[edge.id] ?: scheme.primary, style = Stroke(lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                // The rope itself is drawn over the plan, hanging under gravity.
                ropeFrom?.let { from ->
                    val color = ropeSnap?.let { target ->
                        val a = favorites[from]?.let { favoriteColors(it.accentSeed, dark, scheme).accent.toArgb() }
                        val b = favorites[target]?.let { favoriteColors(it.accentSeed, dark, scheme).accent.toArgb() }
                        Color(edgeLineColor(a, b, scheme.background.toArgb(), scheme.tertiary.toArgb(), dark))
                    } ?: favorites[from]?.let { favoriteColors(it.accentSeed, dark, scheme).accent } ?: scheme.primary
                    val pts = if (quiet) listOfNotNull(placed[from]?.let(geometry::center), ropeTip) else rope.points.toList()
                    drawPath(smoothPath(pts), color, style = Stroke(lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = if (quiet) PathEffect.dashPathEffect(floatArrayOf(lineWidth * 2, lineWidth * 1.5f)) else null))
                }
            }
            // Vehicles on their own layer: only this canvas redraws while they move.
            // Vehicles end exactly at the favourites' shapes (stars, petals, hearts included):
            // the layer is clipped with every node outline, so they enter and leave piece by piece.
            val stationMark = if (settings.monochrome) StationMark.FAVORITE else look.station
            val nodeOutlines = remember(placed, scales, nodePx, stationMark, geometry, store.favorites) {
                Path().apply {
                    placed.forEach { (id, point) ->
                        val favorite = favorites[id] ?: return@forEach
                        val d = nodePx * (scales[id] ?: 1f)
                        val shape = stationShapeFor(stationMark, look.stationShape.takeUnless { settings.monochrome }, favorite)
                        val outline = Path().apply { addOutline(shape.createOutline(androidx.compose.ui.geometry.Size(d, d), androidx.compose.ui.unit.LayoutDirection.Ltr, density)) }
                        val c = geometry.center(point)
                        addPath(outline, Offset(c.x - d / 2, c.y - d / 2))
                    }
                }
            }
            if (vehiclesOn || vehiclesStill) Canvas(Modifier.matchParentSize().graphicsLayer { alpha = 1f - PLAN_DIM * dim }.testTag("network-vehicles")) {
                val clock = if (vehiclesOn) vehicleClock else 0f
                clipPath(nodeOutlines, androidx.compose.ui.graphics.ClipOp.Difference) {
                drawn.forEachIndexed { index, edge ->
                    if (edge.id !in active || settlingRun?.edgeId == edge.id || rippleRun?.edgeId == edge.id) return@forEachIndexed
                    val track = tracks[edge.id] ?: return@forEachIndexed
                    val color = lineColors[edge.id] ?: scheme.primary
                    val kind = vehicleKinds[edge.id] ?: VehicleKind.TRAIN
                    val inset = nodePx * .5f + lineWidth
                    val length = track.length
                    val fields = (length - 2 * inset) / pitch
                    vehicleRuns(edge.direction, index).forEach { run ->
                        val place = (if (vehiclesOn) vehiclePassage(length, maxConsistLength(kind, fields) * vehicleWidth, pitch, clock, run)
                                .takeIf { fields > .2f }
                            else stillVehiclePlacement(length, inset, pitch, run)) ?: return@forEach
                        // Each trip draws its own length; every body follows the line on its own,
                        // so a long train bends around corners. Bodies still inside a node stay hidden below it.
                        val consist = vehicleConsist(kind, fields, vehicleSeed(edge.id, run.forward, place.cycle))
                        val back = if (run.forward) -1f else 1f
                        var offset = 0f
                        consist.forEachIndexed { i, car ->
                            if (i > 0) offset += (consist[i - 1].length / 2 + vehicleGap(kind) + car.length / 2) * vehicleWidth
                            val d = place.distance + back * offset
                            if (d < 0f || d > length) return@forEachIndexed
                            val at = track.getPosition(d); val tangent = track.getTangent(d)
                            if (at.x.isNaN() || tangent.x.isNaN()) return@forEachIndexed
                            val heading = if (run.forward) tangent else Offset(-tangent.x, -tangent.y)
                            // Drive on the right: both directions of a line pass each other.
                            val lane = if (edge.direction == DirectionMode.BOTH) Offset(-heading.y, heading.x) * (lineWidth * .32f) else Offset.Zero
                            drawVehicleCar(kind, car, i, at + lane, heading, vehicleWidth, place.alpha, scheme.surface, scheme.onSurface, color)
                        }
                    }
                }
                }
            }
            pinnedJourney?.let { journey ->
                tracks[journey.edgeId]?.let { track ->
                    PinnedNetworkVehicle(journey, track, nodePx / 2, vehicleWidth,
                        lineColors[journey.edgeId] ?: scheme.primary,
                        Modifier.matchParentSize().zIndex(1f))
                }
            }
            // Nodes: the favourite's shape with contour and shadow; lines end inside it.
            val order = placed.keys.sortedWith(compareByDescending<String> { it == anchorId }.thenByDescending { (scales[it] ?: 1f) > 1f }
                .thenBy { id -> placed.getValue(id).y * NETWORK_GRID_SIZE + placed.getValue(id).x })
            order.forEach { id ->
                val favorite = favorites.getValue(id)
                val center = centerOf(id) ?: return@forEach
                key(id) {
                    // A favourite that joins the plan while it is shown (a new one, or one taken
                    // from the tray) pops in with a small overshoot and a ring in its colour.
                    val newcomer = remember { id !in initialNodes && preview == null && !quiet }
                    val birth = remember { Animatable(if (newcomer) 0f else 1f) }
                    val ring = remember { Animatable(if (newcomer) 0f else 1f) }
                    if (newcomer) LaunchedEffect(Unit) {
                        birthHaptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        launch { ring.animateTo(1f, androidx.compose.animation.core.tween(950, delayMillis = 120)) }
                        birth.animateTo(1f, spring(dampingRatio = .38f, stiffness = 260f))
                    }
                    if (ring.value < 1f) {
                        val accent = favoriteColors(favorite.accentSeed, dark, scheme).accent
                        Canvas(Modifier.matchParentSize().testTag("network-node-birth-$id")) {
                            val r = nodePx / 2f * (1f + 1.7f * ring.value)
                            drawCircle(accent.copy(alpha = (1f - ring.value) * .75f), r, center, style = Stroke((1f - ring.value) * 5.dp.toPx() + 1.dp.toPx()))
                        }
                    }
                    NetworkNodeView(
                        favorite = favorite, center = center, pitch = nodePx, scale = (scales[id] ?: 1f) * birth.value.coerceAtLeast(.02f),
                        lifted = moving == id, highlighted = (scales[id] ?: 1f) > 1f, anchor = id == anchorId, override = id == overrideId,
                        overview = id in overviewPins,
                        wiggle = wiggle?.value?.let { phase -> 1.2f * sin(2 * PI * (phase + (id.hashCode() and 0xff) / 255f)).toFloat() }?.takeIf { moving != id },
                        squish = squishes[id], editing = editing, quiet = quiet,
                        semantics = nodeSemantics(store, favorite, editing, anchorId, overrideId, overviewPins, latestActions, placed, geometry, now),
                        mark = if (settings.monochrome) StationMark.FAVORITE else look.station, paper = paper, ink = ink,
                        tile = stationLineColor[id], stationShape = look.stationShape.takeUnless { settings.monochrome },
                        finish = finish, planInk = planInk,
                    )
                }
            }
            // Departure chips at the nearest node, each on its own outgoing line (build 69): sized to
            // their text (large system font included) and placed so that no chip covers another chip
            // or a node; lines leaving side by side used to stack their times. A dashed rim marks a
            // time without a forecast (timetable only), as the line below the plan used to say.
            val chipNode = countdown.nodeId?.takeIf { it in placed && !editing }
            if (chipNode != null) {
                val chipStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                val chipPad = with(density) { Offset(7.dp.toPx(), 2.5.dp.toPx()) }
                val shown = countdown.chips.mapNotNull { chip ->
                    val path = routesPx[chip.edgeId] ?: return@mapNotNull null
                    val edge = drawn.firstOrNull { it.id == chip.edgeId } ?: return@mapNotNull null
                    val from = if (edge.aId == chipNode) path else path.reversed()
                    if (from.size < 2) return@mapNotNull null
                    val first = chip.trip?.segments?.firstOrNull { !it.isWalk } ?: chip.trip?.segments?.firstOrNull()
                    val time = when { first != null -> clockTime(first.startTime); chip.loading -> "…"; else -> "–" }
                    val late = first?.delay?.minutes?.takeIf { it > 0 }
                    val planned = first != null && !first.isWalk && !first.realtimeDeparture
                    val text = time + (late?.let { " +$it" } ?: "")
                    val measured = labelMeasurer.measure(text, chipStyle, softWrap = false, maxLines = 1).size
                    PlanChip(chip, from, text, time, late, planned, lineColors[edge.id] ?: scheme.outline,
                        androidx.compose.ui.geometry.Size(measured.width + 2 * chipPad.x, measured.height + 2 * chipPad.y))
                }
                val requests = shown.map { NetworkChipRequest(it.chip.edgeId, it.from, it.size) }
                val liveVehicle = pinnedJourney?.let { journey -> tracks[journey.edgeId]?.let { track ->
                    pinnedVehiclePlacement(journey,track,nodePx/2,vehicleWidth)?.point?.let {Rect(it,vehicleWidth*1.5f)}
                } }
                val nodeRects = placed.keys.mapNotNull { id -> centerOf(id)?.let { Rect(it, radiusOf(id)) } } + listOfNotNull(liveVehicle)
                val chipGap = with(density) { 2.dp.toPx() }
                val centers = remember(requests, nodeRects, widthPx, heightPx, pitch) {
                    placeNetworkChips(requests, nodeRects, androidx.compose.ui.geometry.Size(widthPx, heightPx),
                        step = pitch * .25f, gap = chipGap, reach = pitch * 4f)
                }
                shown.forEach { view ->
                    val center = centers[view.chip.edgeId] ?: return@forEach
                    val shape = RoundedCornerShape(50)
                    val rim = with(density) { 1.5.dp.toPx() }
                    Box(Modifier.offset { IntOffset((center.x - view.size.width / 2).roundToInt(), (center.y - view.size.height / 2).roundToInt()) }
                        .size(with(density) { view.size.width.toDp() }, with(density) { view.size.height.toDp() })
                        .graphicsLayer { alpha = 1f - PLAN_DIM * dim }
                        .background(scheme.surfaceContainerHighest, shape)
                        .drawWithContent {
                            drawContent()
                            drawRoundRect(view.color, Offset(rim / 2, rim / 2), androidx.compose.ui.geometry.Size(size.width - rim, size.height - rim),
                                androidx.compose.ui.geometry.CornerRadius(size.height / 2), style = Stroke(rim,
                                    pathEffect = if (view.planned) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null))
                        }
                        .testTag("network-chip-${view.chip.edgeId}")
                        .clearAndSetSemantics {
                            contentDescription = tr("Nächste Abfahrt nach %1\$s um %2\$s", view.chip.targetLabel, view.time) +
                                (view.late?.let { ", " + tr("plus %1\$s Minuten", it) } ?: "") +
                                (if (view.planned) tr(", keine Echtzeitprognose") else "")
                        },
                        contentAlignment = Alignment.Center) {
                        Text(view.text, style = chipStyle, color = if (view.late != null) scheme.error else scheme.onSurface,
                            maxLines = 1, softWrap = false)
                    }
                }
            }
            // Names: hidden in live mode, shown while a free spot is held and always while editing.
            // Complete, each in a plate placed below, above, right or left of its node where it covers
            // the least. Held (build 69): larger, bold, dark plates like a tooltip over a dimmed plan;
            // words are never split in the middle (the plate grows to the longest word first).
            if (labelAlpha > 0f) {
                val emphasized = labelsHeld || dim > 0f
                val style = if (emphasized) heldLabelStyle else labelStyle
                val pad = with(density) { if (emphasized) Offset(6.dp.toPx(), 3.dp.toPx()) else Offset(4.dp.toPx(), 1.5.dp.toPx()) }
                val base = (pitch * 2.4f).coerceIn(with(density) { 72.dp.toPx() }, with(density) { 132.dp.toPx() }) - 2 * pad.x
                val widest = (widthPx * .45f - 2 * pad.x).coerceAtLeast(1f)
                val texts = remember(store.favorites, style, base, widest, density) {
                    store.favorites.associate { f ->
                        val maxText = nameTextWidth(f.label, base, widest) { w -> labelMeasurer.measure(w, style, softWrap = false).size.width }
                        f.id to labelMeasurer.measure(f.label, style, constraints = Constraints(maxWidth = maxText)).size
                    }
                }
                val boxes = order.filter { it != moving }.mapNotNull { id ->
                    val size = texts[id] ?: return@mapNotNull null
                    NetworkLabelBox(id, centerOf(id) ?: return@mapNotNull null, radiusOf(id),
                        androidx.compose.ui.geometry.Size(size.width + 2 * pad.x + 1f, size.height + 2 * pad.y))
                }
                val gap = with(density) { 2.dp.toPx() }
                val plate = if (emphasized) scheme.inverseSurface else scheme.surfaceContainerHighest
                val ink = if (emphasized) scheme.inverseOnSurface else scheme.onSurface
                placeNetworkLabels(boxes, androidx.compose.ui.geometry.Size(widthPx, heightPx), gap).forEach { (id, topLeft) ->
                    val box = boxes.first { it.id == id }
                    Text(favorites[id]?.label.orEmpty(), Modifier.offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                        .graphicsLayer { alpha = labelAlpha }
                        .size(with(density) { box.size.width.toDp() }, with(density) { box.size.height.toDp() })
                        .then(if (emphasized) Modifier.shapeShadow(RoundedCornerShape(8.dp), 3.dp) else Modifier)
                        .background(plate, RoundedCornerShape(if (emphasized) 8.dp else 6.dp))
                        .then(if (emphasized) Modifier else Modifier.border(1.dp, scheme.outlineVariant, RoundedCornerShape(6.dp)))
                        .padding(horizontal = with(density) { pad.x.toDp() }, vertical = with(density) { pad.y.toDp() })
                        .testTag("network-label-$id").clearAndSetSemantics {},
                        style = style, textAlign = TextAlign.Center, color = ink)
                }
            }
        }
    }
}

@Composable
private fun NetworkNodeView(
    favorite: Favorite, center: Offset, pitch: Float, scale: Float, lifted: Boolean, highlighted: Boolean,
    anchor: Boolean, override: Boolean, wiggle: Float?, squish: Squish?, editing: Boolean, quiet: Boolean,
    semantics: SemanticsPropertyReceiver.() -> Unit, overview: Boolean = false,
    mark: StationMark = StationMark.FAVORITE, paper: Color = Color.White, ink: Color = Color.Black, tile: Color? = null,
    stationShape: StationShape? = null,
    finish: PlanFinish = PlanFinish.PLAIN, planInk: PlanInk? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val dark = scheme.background.luminance() < .3f
    // A node pops in once with a soft overshoot (new favourite, new grid, first view of the plan).
    val appear = remember { Animatable(if (quiet) 1f else .4f) }
    LaunchedEffect(Unit) { if (!quiet) appear.animateTo(1f, spring(dampingRatio = .45f, stiffness = 380f)) }
    val animated by animateFloatAsState(scale * if (lifted) 1.12f else 1f, if (quiet) snap() else spring(dampingRatio = .55f, stiffness = 320f), label = "network-node-scale")
    val colors = favoriteColors(favorite.accentSeed, dark, scheme)
    // Mexiko-Stadt draws every station as a tile; all other designs keep the favourite's shape.
    // Build 110: favourites on the App-Theme plan are built in the design's material.
    val material = finish != PlanFinish.PLAIN && mark == StationMark.FAVORITE && planInk != null
    val shape = stationShapeFor(mark, stationShape, favorite).let { if (material) stationShapeIn(finish, it) else it }
    // The favourite's colour, readable on the design's sheet (rings and symbols of printed-map designs).
    val accentOnPaper = Color(dev.ambivalenton.openv.ui.theme.readableForeground(colors.accent.toArgb().toLong() and 0xFFFFFFFFL,
        paper.toArgb().toLong() and 0xFFFFFFFFL, 3.0).toInt())
    // A favourite pinned to the overview is highlighted in its own shape, no extra badge.
    // White or black marks of printed-map designs take the favourite's colour (tinted face, coloured
    // ring); coloured marks get a second contour that follows the shape. The change eases in.
    val pinGlow by animateFloatAsState(if (overview) 1f else 0f, if (quiet) snap() else spring(dampingRatio = .7f, stiffness = 260f), label = "network-node-overview")
    val fill = when (mark) {
        StationMark.FAVORITE -> colors.accent
        StationMark.RING, StationMark.ACCENT_RING -> lerp(paper, accentOnPaper, .18f * pinGlow)
        StationMark.INK -> lerp(ink, accentOnPaper, pinGlow)
        StationMark.LINE_TILE -> tile ?: colors.accent
    }
    val symbol = when (mark) {
        StationMark.FAVORITE -> colors.onAccent
        StationMark.RING, StationMark.ACCENT_RING -> accentOnPaper
        StationMark.INK -> if (pinGlow > .5f) dev.ambivalenton.openv.ui.theme.contrastContentColor(accentOnPaper) else paper
        StationMark.LINE_TILE -> if (fill.luminance() > .45f) Color(0xFF111111) else Color.White
    }
    val ring = when (mark) {
        StationMark.RING -> lerp(ink, accentOnPaper, pinGlow)
        StationMark.ACCENT_RING -> accentOnPaper
        else -> null
    }
    val box = pitch * 1.5f + with(density) { 12.dp.toPx() }
    val boxDp = with(density) { box.toDp() }
    val diameter = with(density) { (pitch * animated).toDp() }
    Box(Modifier.offset { IntOffset((center.x - box / 2).roundToInt(), (center.y - box / 2).roundToInt()) }.size(boxDp)
        .testTag("network-node-${favorite.id}").semantics(mergeDescendants = true, properties = semantics),
        contentAlignment = Alignment.Center) {
        if (highlighted) Box(Modifier.size(with(density) { (pitch * 2.1f).toDp() })
            .background(Brush.radialGradient(listOf(colors.accent.copy(alpha = .35f), Color.Transparent)), CircleShape))
        // Feedback and the "nearby" mark follow the favourite's own shape; no circle around it.
        squish?.ring?.value?.takeIf { it in .001f..0.999f }?.let { ring ->
            Box(Modifier.size(diameter * (1f + ring)).shapeContour(shape, 2.dp, colors.accent.copy(alpha = .4f * (1f - ring))))
        }
        if (anchor) Box(Modifier.size(diameter + 8.dp).shapeContour(shape, 2.dp, scheme.primary))
        // Coloured marks: a soft face and a second contour in the favourite's colour, hugging the shape.
        if (pinGlow > .01f && mark in setOf(StationMark.FAVORITE, StationMark.LINE_TILE)) Box(Modifier
            .size(diameter + (if (anchor) 16.dp else 10.dp) * pinGlow)
            .testTag("network-overview-mark-${favorite.id}")
            .background(colors.accent.copy(alpha = .16f * pinGlow), shape)
            .shapeContour(shape, 2.5.dp, colors.accent.copy(alpha = .95f * pinGlow)))
        val axis = (squish?.axis ?: 0f) * 180f / PI.toFloat()
        val amount = squish?.amount?.value ?: 0f
        val pulse = squish?.pulse?.value ?: 1f
        val volume = 1f / kotlin.math.sqrt(1f + amount * amount / 2f)
        Box(Modifier.size(diameter).graphicsLayer { rotationZ = (wiggle ?: 0f) + axis }
            .graphicsLayer { scaleX = (1f + amount) * pulse * volume; scaleY = (1f - amount) * pulse * volume }
            .graphicsLayer { rotationZ = -axis; scaleX = appear.value; scaleY = appear.value }
            // Own shadow, never the platform one: concave shapes (cookie, clover) crashed it, see shapeShadow.
            // Printed-map designs lie flat on their sheet; only a lifted favourite casts a shadow there.
            .then(if (material) Modifier.stationBody(finish, shape, fill, planInk!!, lifted)
            else Modifier.then(if (mark == StationMark.FAVORITE || lifted) Modifier.shapeShadow(shape, if (lifted) 8.dp else 3.dp, lerp(Color.Black, scheme.primary, .1f)) else Modifier)
            .background(fill, shape)
            .then(if (ring != null) Modifier.shapeContour(shape, (diameter * (.11f + .04f * pinGlow)).coerceAtLeast(2.dp), ring)
                else Modifier.shapeContour(shape, 1.5.dp, (if (mark == StationMark.FAVORITE) scheme.surface else paper).copy(alpha = .9f)))),
            contentAlignment = Alignment.Center) {
            AppIcon(favorite.icon.resource(), null, Modifier.size(diameter * if (ring != null) .5f else .55f),
                tint = if (material) stationSymbol(finish, fill, symbol, accentOnPaper, planInk!!) else symbol)
        }
        if (override) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            AppIcon(R.drawable.ic_pin, null, Modifier.size(12.dp), tint = scheme.onPrimary)
        }
        // Printed-map marks: the coloured face and ring above carry the overview pin (tag for tests).
        if (overview && mark !in setOf(StationMark.FAVORITE, StationMark.LINE_TILE))
            Box(Modifier.size(diameter).testTag("network-overview-mark-${favorite.id}"))
    }
}

/** Contour drawn as the shape's outline (no offscreen bitmap, unlike a generic-shape border). */
fun Modifier.shapeContour(shape: androidx.compose.ui.graphics.Shape, width: androidx.compose.ui.unit.Dp, color: Color): Modifier =
    drawWithContent {
        drawContent()
        val stroke = width.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color, style = Stroke(stroke))
    }

/** TalkBack: name, state and every action without gestures (live: search, location; edit: move, connect, tray). */
private fun nodeSemantics(
    store: StoredState, favorite: Favorite, editing: Boolean, anchorId: String?, overrideId: String?, overviewPins: Set<String>,
    actions: NetworkPlanActions, placed: Map<String, GridPoint>, geometry: NetworkGeometry, now: ZonedDateTime,
): SemanticsPropertyReceiver.() -> Unit = {
    val point = placed[favorite.id]
    role = Role.Button
    contentDescription = favorite.label
    traversalIndex = point?.let { (it.y * NETWORK_GRID_SIZE + it.x).toFloat() } ?: 999f
    val lines = store.network.edges.filter { it.touches(favorite.id) }
    stateDescription = listOfNotNull(
        tr("Hervorgehoben").takeIf { favorite.rule?.matches(now) == true },
        tr("Zeitabhängiger Standort aktiv").takeIf { favorite.hiddenRules.any { it.matches(now) } },
        tr("In der Nähe").takeIf { favorite.id == anchorId },
        tr("Virtueller Standort").takeIf { favorite.id == overrideId },
        tr("In der Übersicht angeheftet").takeIf { favorite.id in overviewPins },
        if (lines.size == 1) tr("1 Verbindung") else tr("%1\$s Verbindungen", lines.size),
    ).joinToString(", ")
    if (!editing) {
        onClick(tr("Verbindung hierher suchen")) { actions.onNodeTap(favorite.id); true }
        // The long-click action follows "Favorit im Netzplan gedrückt halten".
        when (store.settings.holdAction) {
            NetworkHoldAction.NOTHING -> {}
            NetworkHoldAction.MOVE -> onLongClick(tr("Bearbeiten und verschieben")) { actions.onEditRequest(); true }
            NetworkHoldAction.LOCATION -> onLongClick(if (store.settings.plannerMode) tr("Als Startpunkt setzen")
                else if (favorite.id == overrideId) tr("Standortfestlegung aufheben") else tr("Als Standort festlegen")) { actions.onNodeHold(favorite.id); true }
            NetworkHoldAction.PIN -> onLongClick(tr("Verbindung anpinnen")) { actions.onNodeHold(favorite.id); true }
            NetworkHoldAction.OVERVIEW -> onLongClick(if (favorite.id in overviewPins) tr("Nicht mehr in der Übersicht anzeigen") else tr("In Übersicht anzeigen")) { actions.onNodeHold(favorite.id); true }
        }
        customActions = lines.mapNotNull { e ->
            val other = store.favorites.firstOrNull { it.id == e.other(favorite.id) } ?: return@mapNotNull null
            CustomAccessibilityAction(tr("Verbindung %1\$s – %2\$s suchen", favorite.label, other.label)) { actions.onEdgeTap(e.id); true }
        }
    } else {
        onClick(tr("Bearbeiten")) { actions.onNodeTap(favorite.id); true }
        val moves = listOf(tr("Nach links verschieben") to (-1 to 0), tr("Nach rechts verschieben") to (1 to 0),
            tr("Nach oben verschieben") to (0 to -1), tr("Nach unten verschieben") to (0 to 1))
        customActions = (point?.let { p ->
            moves.mapNotNull { (title, step) ->
                val target = (1..NETWORK_GRID_SIZE).map { GridPoint(p.x + step.first * it, p.y + step.second * it) }
                    .firstOrNull { it.free(placed.filterKeys { k -> k != favorite.id }.values, geometry.columns, geometry.rows) || !it.within(geometry.columns, geometry.rows) }
                    ?.takeIf { it.within(geometry.columns, geometry.rows) }
                target?.let { t -> CustomAccessibilityAction(title) { actions.onMove(favorite.id, t); true } }
            }
        }.orEmpty()) + listOf(
            CustomAccessibilityAction(tr("Verbinden mit …")) { actions.onConnectRequest(favorite.id); true },
            CustomAccessibilityAction(tr("In Ablage legen")) { actions.onToTray(favorite.id); true },
        ) + lines.map { e ->
            val other = store.favorites.firstOrNull { it.id == e.other(favorite.id) }?.label.orEmpty()
            CustomAccessibilityAction(tr("Verbindung zu %1\$s bearbeiten", other)) { actions.onEdgeMenu(e.id); true }
        }
    }
}


/** Station outline of a design (circle, square, rhombus) or the favourite's own shape. */
private val DiamondShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f); lineTo(size.width, size.height / 2f); lineTo(size.width / 2f, size.height); lineTo(0f, size.height / 2f); close()
}
internal fun stationShapeFor(mark: StationMark, stationShape: StationShape?, favorite: Favorite): androidx.compose.ui.graphics.Shape = when {
    mark == StationMark.FAVORITE -> GlanceThemeHelper.composeShape(favorite.shape)
    stationShape == StationShape.CIRCLE -> CircleShape
    stationShape == StationShape.SQUARE -> RoundedCornerShape(6)
    stationShape == StationShape.TILE -> RoundedCornerShape(34)
    stationShape == StationShape.DIAMOND -> DiamondShape
    mark == StationMark.LINE_TILE -> RoundedCornerShape(24)
    else -> GlanceThemeHelper.composeShape(favorite.shape)
}
