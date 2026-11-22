// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appCorner
import dev.ambivalenton.openv.ui.tutorial.LocalTutorialSurfaceActive
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * The destination map of the search. A fixed sheet of OpenStreetMap around the chosen place
 * (no zoom, no panning) with a push pin on the destination. A tap moves the pin there, a drag on the
 * pin carries it; the new point becomes the destination. The map unfolds like a folded paper map and
 * folds away again; the pin is pulled out, pressed in, knocked in, and falls off the sheet.
 */

/** Radius around the chosen place that the shorter side of the map shows. */
const val DESTINATION_MAP_RADIUS_METERS = 500.0
/** Half the height of the resting pin (from its tip to the top of its head). */
internal val DESTINATION_PIN_HALF_HEIGHT = 12.dp
private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686

/**
 * Fixed view: mercator centre, pixels per world unit and the tile zoom drawn at that scale. The centre
 * shows at [focusX]/[focusY] (px); NaN means the middle of the view (The destination stands
 * halfway between the map's upper edge and the search button).
 */
internal data class DestinationMapCamera(val center: MapPoint, val scale: Double, val zoom: Int,
    val focusX: Float = Float.NaN, val focusY: Float = Float.NaN) {
    val tilePixels: Double get() = scale / (1 shl zoom)
    private fun fx(width: Float) = if (focusX.isNaN()) width / 2 else focusX
    private fun fy(height: Float) = if (focusY.isNaN()) height / 2 else focusY
    fun toPx(p: MapPoint, width: Float, height: Float) =
        Offset(((p.x - center.x) * scale + fx(width)).toFloat(), ((p.y - center.y) * scale + fy(height)).toFloat())
    fun toMap(o: Offset, width: Float, height: Float) =
        MapPoint(center.x + (o.x - fx(width)) / scale, center.y + (o.y - fy(height)) / scale)
    /** Tiles covering a [width] × [height] px view; at most 48. */
    fun tiles(width: Float, height: Float): List<MapTileId> {
        val count = 1 shl zoom; val t = tilePixels
        val left = center.x * scale - fx(width); val top = center.y * scale - fy(height)
        return buildList {
            for (ty in floor(top / t).toInt()..floor((top + height) / t).toInt()) {
                if (ty !in 0 until count) continue
                for (tx in floor(left / t).toInt()..floor((left + width) / t).toInt()) {
                    add(MapTileId(zoom, ((tx % count) + count) % count, ty))
                    if (size >= 48) return@buildList
                }
            }
        }.distinct()
    }
}

/** The view in which [radiusMeters] around [anchor] fit into the shorter side of a [width] × [height] px map. */
internal fun destinationMapCamera(anchor: Location, width: Float, height: Float, density: Float,
    radiusMeters: Double = DESTINATION_MAP_RADIUS_METERS, focusY: Float = Float.NaN): DestinationMapCamera? {
    val center = mercatorPoint(anchor) ?: return null
    if (width <= 0f || height <= 0f || radiusMeters <= 0.0) return null
    val metersPerPixel = radiusMeters / (min(width, height) / 2.0)
    val scale = EARTH_CIRCUMFERENCE_METERS * cos(Math.toRadians(anchor.latitude!!)) / metersPerPixel
    // Tiles of the nearest zoom, drawn between 0.71× and 1.41× their size: labels stay readable.
    val zoom = (ln(scale / (256.0 * density)) / ln(2.0)).roundToInt().coerceIn(3, 19)
    return DestinationMapCamera(center, scale, zoom, focusY = focusY)
}

/** Geographic position of a mercator point. */
internal fun MapPoint.latitude() = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))
internal fun MapPoint.longitude() = x * 360.0 - 180.0

/** The point the pin marks as a destination; named after the place the map was opened for. */
internal fun pinnedDestination(point: MapPoint, anchor: Location) =
    Location(tr("Kartenpunkt bei %1\$s", anchor.name), latitude = point.latitude(), longitude = point.longitude(),
        kind = LocationKind.COORDINATE)

/**
 * One panel of a folded map as a 3 × 3 projective matrix (android.graphics.Matrix order). The panel
 * spans [srcCenter] ± half its length along the fold axis in the flat map and is drawn around
 * [dstCenter], turned by an angle with [cos]/[sin] about its own centre line (perspective with
 * viewing [distance]) and stretched by [stretch]. [cross] is the panel's centre across the axis.
 */
internal fun foldPanelMatrix(srcCenter: Float, dstCenter: Float, cross: Float, cos: Float, sin: Float, stretch: Float,
    distance: Float, alongX: Boolean): FloatArray {
    val p = sin / distance
    val along = stretch * cos + dstCenter * p
    val offset = dstCenter - along * srcCenter
    return if (alongX) floatArrayOf(along, 0f, offset, cross * p, 1f, -cross * p * srcCenter, p, 0f, 1f - p * srcCenter)
    else floatArrayOf(1f, cross * p, -cross * p * srcCenter, 0f, along, offset, 0f, p, 1f - p * srcCenter)
}

/** Where a 3 × 3 projective matrix maps the point (x, y). */
internal fun FloatArray.project(x: Float, y: Float): Offset {
    val w = this[6] * x + this[7] * y + this[8]
    return Offset((this[0] * x + this[1] * y + this[2]) / w, (this[3] * x + this[4] * y + this[5]) / w)
}

/**
 * Panels of an accordion fold: [count] strips of a [length] long map, folded to [cos] (1 = flat),
 * alternately towards and away from the viewer so that neighbouring edges meet. Centred on the map
 * ([centred]) or hanging from its start. Returns each panel's source centre and drawn centre.
 */
internal fun accordionCenters(count: Int, length: Float, cos: Float, sin: Float, stretch: Float, distance: Float,
    centred: Boolean): List<Pair<Float, Float>> {
    val panel = length / count
    val projected = panel * cos * stretch
    // Edges at ±panel/2 meet with the same depth; k is how much nearer/farther they are.
    val k = (sin * panel / 2 / distance).coerceIn(-.9f, .9f)
    val width = projected / (1 - k * k)
    val start = if (centred) length / 2 - width * count / 2 else 0f
    return List(count) { i ->
        val towards = i % 2 == 0
        val left = start + i * width
        // The panel's centre line lies nearer the edge that is farther away.
        val near = if (towards) 1 - k else 1 + k
        (panel * (i + .5f)) to (left + projected / 2 / near)
    }
}

private data class FallingPin(val start: Offset, val startNanos: Long, val drift: Float, val spin: Float, val tilt: Float)

private const val MAP_PANELS = 6
private const val MAP_ROWS = 5
private const val PIN_TILT = -.17f
/** Needle above the paper at rest and pulled out, dp; height of a carried pin and where a knocked-in pin starts. */
private const val PIN_REST_OUT = 5f
private const val PIN_PULLED_OUT = 12f
private const val PIN_CARRIED = 11f
private const val PIN_STRIKE_HEIGHT = 46f

/**
 * [unfold] is the opening fold (0 folded in the middle, 1 flat; above 1 the sheet stretches and
 * springs back) and [visibleHeight] how much of the map's height is free (opening: revealed; while
 * [closing]: the sheet folds from the bottom up into that height).
 */
@Composable internal fun DestinationMap(
    anchor: Location,
    pin: Location,
    onPick: (Location) -> Unit,
    unfold: () -> Float,
    visibleHeight: () -> Float,
    closing: Boolean,
    modifier: Modifier = Modifier,
    /** Where (from the map's top) the middle of the pin stands; the place itself is a little lower. */
    pinCenterY: Dp? = null,
) {
    val density = LocalDensity.current; val d = density.density
    val settings = LocalAppSettings.current
    val source = LocalWalkingMapTiles.current; val template = settings.mapTileTemplate
    val quiet = settings.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val surfaceActive = LocalTutorialSurfaceActive.current
    val haptics = LocalHapticFeedback.current
    val window = LocalWindowInfo.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val shape = appCorner(28.dp)
    val dark = scheme.background.luminance() < .3f
    val pinColor = scheme.primary
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var topInWindow by remember { mutableFloatStateOf(0f) }
    val w = mapSize.width.toFloat(); val h = mapSize.height.toFloat()
    val focusPx = pinCenterY?.let { (it + DESTINATION_PIN_HALF_HEIGHT).value * d } ?: Float.NaN
    val camera = remember(anchor, mapSize, d, focusPx) { destinationMapCamera(anchor, w, h, d, focusY = focusPx) }
    val tiles = remember(template, anchor) { mutableStateMapOf<MapTileId, ImageBitmap>() }
    val requested = remember(camera, mapSize) { camera?.tiles(w, h).orEmpty() }
    var failed by remember(template, anchor) { mutableStateOf(false) }
    LaunchedEffect(source, template, requested, surfaceActive) {
        if (!surfaceActive || source == null || requested.isEmpty()) return@LaunchedEffect
        // Only while the app is in front; the tile source allows two downloads at a time.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            failed = false
            coroutineScope {
                requested.filterNot { it in tiles }.forEach { id -> launch {
                    try { tiles[id] = source.tile(id, template) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
                } }
            }
            awaitCancellation()
        }
    }
    val home = remember(camera, pin) { camera?.let { c -> mercatorPoint(pin)?.let { c.toPx(it, w, h) } } }
    val latestHome by rememberUpdatedState(home)
    val latestPick by rememberUpdatedState(onPick)
    val latestAnchor by rememberUpdatedState(anchor)
    val tip = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    // How far the needle stands out of the paper (dp; 5 at rest, less while it is driven in) and how
    // high the whole pin floats above the sheet (dp; it then looks nearer and casts a farther shadow).
    val out = remember { Animatable(PIN_REST_OUT) }
    val elevation = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }
    val ripple = remember { Animatable(1f) }      // 0..1 ring after an impact
    var rippleAt by remember { mutableStateOf(Offset.Zero) }
    val shake = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) } // the pin is being carried or knocked in
    var carried by remember { mutableStateOf<Offset?>(null) }
    val fallers = remember { mutableStateListOf<FallingPin>() }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(home) { if (!busy && home != null) tip.snapTo(home) }
    LaunchedEffect(fallers.size) {
        while (fallers.isNotEmpty()) {
            withFrameNanos { now ->
                frame = now
                // A new falling pin starts on the frame clock, the time base its motion is drawn with.
                for (i in fallers.indices) if (fallers[i].startNanos < 0) fallers[i] = fallers[i].copy(startNanos = now)
            }
            val limit = window.containerSize.height - topInWindow + 140 * d
            fallers.removeAll { fallerAt(it, frame, d).first.y > limit }
        }
    }
    fun clampToSheet(o: Offset): Offset {
        val m = 14 * d
        return Offset(o.x.coerceIn(m, max(m, w - m)), o.y.coerceIn(m + 22 * d, max(m + 22 * d, h - m)))
    }
    suspend fun pressIn(at: Offset) {
        rippleAt = at
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        coroutineScope {
            launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(520, easing = LinearOutSlowInEasing)) }
            launch { shake.snapTo(1f); shake.animateTo(0f, tween(300, easing = LinearEasing)) }
            launch { squash.snapTo(.74f); squash.animateTo(1f, spring(dampingRatio = .32f, stiffness = 720f)) }
            elevation.snapTo(0f)
            // Driven into the paper a little too far, then the cork springs it back to its rest.
            out.animateTo(PIN_REST_OUT - 2.2f, tween(60, easing = LinearEasing))
            out.animateTo(PIN_REST_OUT, spring(dampingRatio = .38f, stiffness = 900f))
        }
    }
    val settledNow = { !closing && abs(unfold() - 1f) < .004f && visibleHeight() >= h - .5f }
    val input = Modifier.pointerInput(camera, closing) {
        val c = camera ?: return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!settledNow() || busy) return@awaitEachGesture
            val head = tip.value + Offset(sin(PIN_TILT), -cos(PIN_TILT)) * (15 * d)
            val onPin = (down.position - head).getDistance() <= 26 * d || (down.position - tip.value).getDistance() <= 18 * d
            if (onPin) {
                // Pulled out of the sheet: it lifts, follows the finger and is pressed in where it is let go.
                down.consume(); busy = true
                val before = tip.value; val grab = tip.value - down.position
                carried = before
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                val lifting = scope.launch {
                    if (quiet) { out.snapTo(PIN_PULLED_OUT); elevation.snapTo(PIN_CARRIED) }
                    else coroutineScope {
                        launch { out.animateTo(PIN_PULLED_OUT, spring(dampingRatio = .7f, stiffness = 900f)) }
                        elevation.animateTo(PIN_CARRIED, spring(dampingRatio = .55f, stiffness = 620f))
                    }
                }
                var cancelled = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || change.systemCancelled) { cancelled = true; break }
                    change.consume()
                    carried = clampToSheet(change.position + grab)
                    if (!change.pressed) break
                }
                val target = carried ?: before
                lifting.cancel()
                scope.launch {
                    try {
                        tip.snapTo(target); carried = null
                        if (cancelled) {
                            // The system took the touch: the pin goes back into its old hole.
                            tip.animateTo(before, spring(dampingRatio = .8f, stiffness = 420f))
                            elevation.animateTo(0f, spring(dampingRatio = .6f, stiffness = 500f))
                            out.animateTo(PIN_REST_OUT, spring(dampingRatio = .6f, stiffness = 500f))
                        } else {
                            latestPick(pinnedDestination(c.toMap(target, w, h), latestAnchor))
                            if (quiet) { elevation.snapTo(0f); out.snapTo(PIN_REST_OUT) }
                            else {
                                // Set down onto the sheet, then pressed in.
                                elevation.animateTo(0f, tween(110, easing = FastOutLinearInEasing))
                                pressIn(target)
                            }
                        }
                    } finally { busy = false }
                }
                return@awaitEachGesture
            }
            // A tap elsewhere: the old pin pops out and falls off the sheet, a new one is knocked in.
            var tap = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.systemCancelled || event.changes.count { it.pressed } > 1) break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                if (!change.pressed) { change.consume(); tap = true; break }
            }
            if (!tap) return@awaitEachGesture
            val target = clampToSheet(down.position)
            val old = tip.value
            busy = true
            if (!quiet) fallers += FallingPin(old, -1L, if (target.x > old.x) -1f else 1f,
                if (target.x > old.x) -1f else 1f, PIN_TILT)
            latestPick(pinnedDestination(c.toMap(target, w, h), latestAnchor))
            scope.launch {
                try {
                    tip.snapTo(target)
                    if (quiet) { elevation.snapTo(0f); out.snapTo(PIN_REST_OUT); return@launch }
                    // Knocked in: the whole pin comes down fast from above the sheet and drives into the paper.
                    elevation.snapTo(PIN_STRIKE_HEIGHT); out.snapTo(PIN_PULLED_OUT); squash.snapTo(1f)
                    elevation.animateTo(0f, tween(150, easing = CubicBezierEasing(.55f, 0f, 1f, .45f)))
                    pressIn(target)
                } finally { busy = false }
            }
        }
    }
    val description = tr("Karte um %1\$s. Tippe auf eine Stelle, um den Zielpunkt dorthin zu setzen, oder ziehe die Nadel.", anchor.name)
    val reset = tr("Zielpunkt zurücksetzen")
    Box(modifier
        .onSizeChanged { mapSize = it }
        .onGloballyPositioned { topInWindow = it.positionInWindow().y }
        .testTag("destination-map")
        .semantics {
            contentDescription = description
            if (mercatorPoint(pin) != mercatorPoint(anchor)) customActions = listOf(CustomAccessibilityAction(reset) { onPick(anchor); true })
        }
        .then(input)) {
        val paper = scheme.surfaceContainer; val grid = scheme.outlineVariant.copy(alpha = .35f)
        val rim = scheme.outlineVariant.copy(alpha = .6f)
        Canvas(Modifier.matchParentSize()) {
            val cam = camera ?: return@Canvas
            val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@Canvas)) }
            val jitter = shake.value * sin(shake.value * 34f) * 2.4f * d
            fun DrawScope.content(withPin: Boolean) {
                translate(0f, jitter) { drawSheet(cam, tiles, paper, grid, dark) }
                if (withPin) home?.let { drawPushPin(it, PIN_REST_OUT, 0f, 1f, pinColor, PIN_TILT, d) }
            }
            val visible = visibleHeight().coerceIn(0f, size.height)
            val u = unfold()
            val flat = !closing && abs(u - 1f) < .004f
            if (visible <= .5f) return@Canvas
            when {
                closing -> {
                    // Folds from the bottom up into the height the rising favourites leave free.
                    val share = (visible / size.height).coerceIn(0f, 1f)
                    if (share <= .015f) return@Canvas
                    // Perspective makes the folded stack a little taller than cos·height; one correction step keeps it inside.
                    val distance = size.height * 1.3f
                    val k = sqrt(1 - share * share) * size.height / MAP_ROWS / 2 / distance
                    val cosA = (share * (1 - k * k)).coerceIn(0f, 1f)
                    drawFolded(MAP_ROWS, size.height, size.width / 2, cosA, sqrt(1 - cosA * cosA), 1f, distance,
                        alongX = false, centred = false, outline = outline) { content(true) }
                }
                flat -> clipRect(bottom = visible) {
                    clipPath(outline) { content(false) }
                    drawPath(outline, rim, style = Stroke(1.dp.toPx()))
                }
                else -> clipRect(bottom = visible) {
                    // Unfolds from the middle to both sides; above 1 the sheet stretches a little and springs back.
                    val angle = (1f - u).coerceIn(0f, 1f) * 84f * PI.toFloat() / 180f
                    val stretch = 1f + (u - 1f).coerceAtLeast(0f) * .55f
                    drawFolded(MAP_PANELS, size.width, size.height / 2, cos(angle), sin(angle), stretch, size.width * .5f,
                        alongX = true, centred = true, outline = outline) { content(true) }
                }
            }
        }
        // The pin above the sheet; nothing clips it, so a falling pin leaves the map downwards and passes
        // behind the search button (drawn after) and the navigation pill (drawn above the screen).
        Canvas(Modifier.matchParentSize()) {
            @Suppress("UNUSED_VARIABLE") val tick = frame
            if (settledNow()) {
                val r = ripple.value
                if (r < 1f) drawCircle(pinColor.copy(alpha = .38f * (1 - r)), (4 + 22 * r) * d, rippleAt, style = Stroke((2.2f * (1 - r) + .6f) * d))
                drawPushPin(carried ?: tip.value, out.value, elevation.value, squash.value, pinColor, PIN_TILT, d)
            }
            for (f in fallers) {
                val (at, motion) = fallerAt(f, frame, d)
                drawPushPin(at, motion.out, motion.elevation, 1f, pinColor, motion.tilt, d, scale = motion.scale, shadow = motion.shadow)
            }
        }
        if (failed && tiles.isEmpty()) Text(tr("Kartenhintergrund nicht verfügbar"), Modifier.align(Alignment.Center).graphicsLayer { alpha = if (settledNow()) 1f else 0f },
            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        // Small but legible in the upper right corner; a tap opens the copyright page.
        OutlinedMapAttribution(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 12.dp)
            .graphicsLayer { alpha = if (settledNow()) 1f else 0f }
            .background(scheme.surface.copy(alpha = .9f), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .clickable { uri.openUri("https://www.openstreetmap.org/copyright") }.padding(horizontal = 5.dp, vertical = 1.dp)
            .testTag("destination-map-attribution"),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp))
    }
}

private data class FallerMotion(val out: Float, val elevation: Float, val tilt: Float, val scale: Float, val shadow: Float)

/** Where a falling pin is: first it pops out of the sheet (0.13 s), then it falls, drifts and spins. */
private fun fallerAt(f: FallingPin, now: Long, d: Float): Pair<Offset, FallerMotion> {
    val t = if (f.startNanos < 0) 0f else ((now - f.startNanos) / 1e9f).coerceAtLeast(0f)
    val pop = .13f
    if (t < pop) {
        // Pulled out of its hole by the knock next to it: the needle comes out and the pin rises a little.
        val p = 1 - (1 - t / pop).pow(3)
        return f.start to FallerMotion(PIN_REST_OUT + (PIN_PULLED_OUT - PIN_REST_OUT) * p, 8f * p, f.tilt + f.spin * .35f * p, 1f, 1f - p * .7f)
    }
    val s = t - pop
    // dp/s and dp/s²: a small hop, then gravity; it tumbles and comes towards the viewer while it falls.
    val x = f.start.x + f.drift * 90f * d * s
    val y = f.start.y + (-140f * s + .5f * 4600f * s * s) * d
    return Offset(x, y) to FallerMotion(PIN_PULLED_OUT, 8f, f.tilt + f.spin * (.35f + 6f * s), 1f + min(.45f, s * 1.1f), (.3f - s * 3f).coerceAtLeast(0f))
}

private fun DrawScope.drawSheet(cam: DestinationMapCamera, tiles: Map<MapTileId, ImageBitmap>, paper: Color, grid: Color, dark: Boolean) {
    drawRect(paper)
    // Faint squared paper until the tiles have arrived.
    val step = 32.dp.toPx(); var x = step
    while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
    var y = step
    while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }
    val t = cam.tilePixels; val count = 1 shl cam.zoom
    val origin = cam.toPx(MapPoint(0.0, 0.0), size.width, size.height)
    val left = -origin.x.toDouble(); val top = -origin.y.toDouble()
    for ((id, bitmap) in tiles) {
        if (id.z != cam.zoom) continue
        var tx = id.x * t - left
        val world = t * count
        while (tx < -world / 2) tx += world
        while (tx > world / 2) tx -= world
        val ty = id.y * t - top
        if (tx + t < 0 || ty + t < 0 || tx > size.width || ty > size.height) continue
        drawImage(bitmap, srcSize = IntSize(bitmap.width, bitmap.height), dstOffset = IntOffset(floor(tx).toInt(), floor(ty).toInt()),
            dstSize = IntSize(ceil(t).toInt() + 1, ceil(t).toInt() + 1), filterQuality = FilterQuality.Medium)
    }
    // A bright map would glare in a dark theme.
    if (dark) drawRect(Color.Black.copy(alpha = .2f))
}

private fun DrawScope.drawFolded(count: Int, length: Float, cross: Float, cos: Float, sin: Float, stretch: Float, distance: Float,
    alongX: Boolean, centred: Boolean, outline: Path, content: DrawScope.() -> Unit) {
    val panel = length / count
    val matrix = Matrix(); val platform = android.graphics.Matrix()
    accordionCenters(count, length, cos, sin, stretch, distance, centred).forEachIndexed { i, (src, dst) ->
        val sign = if (i % 2 == 0) 1f else -1f
        platform.setValues(foldPanelMatrix(src, dst, cross, cos, sign * sin, stretch, distance, alongX))
        matrix.setFrom(platform)
        withTransform({ transform(matrix) }) {
            val slice = if (alongX) Rect(src - panel / 2, 0f, src + panel / 2 + .5f, size.height)
                else Rect(0f, src - panel / 2, size.width, src + panel / 2 + .5f)
            clipRect(slice.left, slice.top, slice.right, slice.bottom) {
                clipPath(outline) {
                    content()
                    // Panels facing away are in shade, those facing the light are a little brighter.
                    drawRect(if (sign > 0) Color.Black.copy(alpha = .28f * sin) else Color.White.copy(alpha = .1f * sin))
                }
            }
        }
    }
}

/**
 * A push pin seen from above at a slight angle. The needle enters the sheet at [tip] and stands [out]
 * dp out of the paper; the whole pin may float [elevation] dp above the sheet: it then looks nearer
 * (larger, a little higher) and its shadow falls farther away and softer. [squash] flattens the head
 * for a moment when it is knocked in.
 */
internal fun DrawScope.drawPushPin(tip: Offset, out: Float, elevation: Float, squash: Float, color: Color, tilt: Float, d: Float,
    scale: Float = 1f, shadow: Float = 1f) {
    val height = elevation.coerceAtLeast(0f)
    val grow = scale * (1f + height / 80f)
    val r = 10.5f * d * grow
    val needle = out.coerceAtLeast(1.2f) * d * grow
    val axis = Offset(sin(tilt), -cos(tilt))
    val point = tip - Offset(0f, height * .55f * d)
    val neck = point + axis * needle
    val head = neck + axis * (r * .78f)
    if (shadow > 0f) {
        // Light from the upper left: the shadow falls to the lower right and goes farther and softer with height.
        val slant = Offset(.62f, .3f)
        val soft = 1f + height / 16f
        val ground = tip + slant * (height * .9f * d)
        val top = ground + slant * (needle + r * .78f)
        drawLine(Color.Black.copy(alpha = .22f * shadow / soft), ground, ground + (top - ground) * .82f, 1.6f * d, StrokeCap.Round)
        val rs = r * 1.1f * soft
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = .3f * shadow / soft), Color.Transparent), top, rs),
            Offset(top.x - rs, top.y - rs * .68f), Size(rs * 2, rs * 1.36f))
        // The small hole where the needle goes in.
        if (height < 1f && out < PIN_REST_OUT + 1f)
            drawOval(Color.Black.copy(alpha = .45f * shadow), Offset(tip.x - 1.6f * d, tip.y - .9f * d), Size(3.2f * d, 1.8f * d))
    }
    // Steel needle with a light edge.
    drawLine(Color(0xFF5E656C), point, neck, 1.7f * d * grow, StrokeCap.Round)
    drawLine(Color(0xFFDDE2E6), point + Offset(-.45f * d, 0f), neck + Offset(-.45f * d, 0f), .6f * d * grow, StrokeCap.Round)
    // Collar where the needle meets the head.
    drawOval(lerp(color, Color.Black, .45f), Offset(neck.x - r * .42f, neck.y - r * .22f), Size(r * .84f, r * .44f))
    // Glossy head, flattened for a moment when it is knocked in.
    val sx = r * (2f - squash).coerceIn(.9f, 1.3f); val sy = r * squash
    val center = head + Offset(0f, (r - sy) * .6f)
    drawOval(Brush.radialGradient(listOf(lerp(color, Color.White, .55f), color, lerp(color, Color.Black, .45f)),
        center + Offset(-sx * .36f, -sy * .4f), max(sx, sy) * 1.35f), Offset(center.x - sx, center.y - sy), Size(sx * 2, sy * 2))
    drawOval(lerp(color, Color.Black, .5f).copy(alpha = .4f), Offset(center.x - sx, center.y - sy), Size(sx * 2, sy * 2), style = Stroke(.8f * d))
    drawOval(Color.White.copy(alpha = .78f), Offset(center.x - sx * .62f, center.y - sy * .66f), Size(sx * .55f, sy * .36f))
    drawCircle(Color.White.copy(alpha = .5f), r * .09f, center + Offset(sx * .36f, sy * .3f))
}
