// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.ambivalenton.openv.data.model.Location
import dev.ambivalenton.openv.data.model.LocationKind
import dev.ambivalenton.openv.data.model.clockTime
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 2.3: what one tap on "Start und Ziel tauschen" does, decided before any animation (pure, tested).
 * The destination becomes the manual start; the start that was in effect becomes the destination.
 * A GPS start is frozen at the swap: the new destination keeps this place while the device moves on.
 */
sealed interface SwapPlan {
    data class Swap(val newStart: Location, val newDestination: Location) : SwapPlan
    /** The destination field holds text that is not (yet) one place. */
    data object NeedsDestination : SwapPlan
    /** No start is known (manual mode without a place, GPS without a fix). */
    data object NeedsStart : SwapPlan
}

fun swapPlan(destination: Location?, start: Location?, now: Long): SwapPlan {
    val target = destination?.takeIf { it.routable && !it.isDevicePosition } ?: return SwapPlan.NeedsDestination
    val from = start?.takeIf { it.routable || it.hasCoordinates } ?: return SwapPlan.NeedsStart
    val fixed = if (!from.isDevicePosition) from
        else if (from.hasCoordinates) Location(tr("Standort um %1\$s Uhr", clockTime(now)), latitude = from.latitude, longitude = from.longitude,
            kind = LocationKind.COORDINATE)
        else return SwapPlan.NeedsStart
    return SwapPlan.Swap(newStart = target, newDestination = fixed)
}

/**
 * 2.3: free text is used for a swap only when the place search resolves it unambiguously: a single
 * suggestion, or a first suggestion whose name equals the text.
 */
fun unambiguousPlace(text: String, suggestions: List<Location>): Location? {
    val wanted = text.trim()
    if (wanted.isEmpty()) return null
    val routable = suggestions.filter { it.routable && !it.isDevicePosition }
    if (routable.size == 1) return routable.single()
    return routable.firstOrNull()?.takeIf { it.name.trim().equals(wanted, ignoreCase = true) }
}

/** The arrow's spring: a lively but short bounce. */
private val ArrowSpring = spring<Float>(dampingRatio = .52f, stiffness = 420f)

/**
 * The swap arrow after the publisher's drawing: one bent arrow with a head to the left and
 * a head downwards, raised by light and shadow in the colours of the theme. A tap turns it in space by
 * half a turn about its 45° axis (the diagonal through its bend), so the two heads trade places, with
 * a springy finish. The light stays where it is while the arrow turns (see [SpaceArrow]).
 */
@Composable fun SwapPlacesArrow(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val quiet = LocalAppSettings.current.reduceMotion
    val presence = remember { Animatable(if (visible) 1f else 0f) }
    val bounce = remember { Animatable(1f) }
    // Accumulated half turns: every tap adds 180° about the diagonal (the shape looks the same after it).
    val flip = remember { Animatable(0f) }
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible, quiet) {
        val target = if (visible) 1f else 0f
        if (quiet) presence.snapTo(target) else presence.animateTo(target, if (visible) ArrowSpring else spring(dampingRatio = .9f, stiffness = 520f))
    }
    LaunchedEffect(taps) {
        if (taps == 0 || quiet) return@LaunchedEffect
        kotlinx.coroutines.coroutineScope {
            launch { bounce.animateTo(.86f, tween(80, easing = FastOutLinearInEasing)); bounce.animateTo(1f, spring(dampingRatio = .42f, stiffness = 420f)) }
            launch { flip.animateTo(taps * 180f, spring(dampingRatio = .58f, stiffness = 170f)) }
        }
    }
    val scheme = MaterialTheme.colorScheme
    val body = scheme.primary
    val dark = scheme.background.luminance() < .4f
    val shadowInk = lerp(Color.Black, scheme.primary, .18f)
    val label = tr("Start und Ziel tauschen")
    Box(modifier.size(48.dp)
        .graphicsLayer {
            val p = presence.value
            alpha = p.coerceIn(0f, 1f)
            scaleX = (.55f + .45f * p) * bounce.value; scaleY = scaleX
            translationX = (1f - p.coerceIn(0f, 1f)) * 14.dp.toPx()
        }
        .then(if (visible) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onClickLabel = label) { taps++; onClick() }
            .semantics { contentDescription = label } else Modifier.clearAndSetSemantics {})
        .testTag("swap-places")) {
        // The turn is computed as geometry (the outline projected in space), the light is
        // not: it keeps coming from the upper left, so shadow, sheen and edges never turn with it.
        Spacer(Modifier.fillMaxSize().drawWithCache {
            val arrow = SpaceArrow(size)
            onDrawBehind { arrow.draw(this, flip.value, body, shadowInk, dark) }
        })
    }
}

/**
 * The swap arrow as a thin slab in space. [draw] turns its outline about the diagonal
 * through the bend (half turns map the shape onto itself) and projects it with a mild perspective;
 * the light stays fixed in screen space: diffuse and specular terms follow the turned surface normal,
 * the shadow falls to the lower right, the bright rim sits upper left and the slab's side shows only
 * while it is tilted. Paths and paints are made once per size; a frame only transforms the outline.
 */
internal class SpaceArrow(size: Size) {
    private val unit = size.minDimension / 100f * .86f
    private val box = 100f * unit
    private val center = Offset(size.width / 2f, size.height / 2f)
    private val base = swapArrowPath(unit, center - Offset(box / 2f, box / 2f)).asAndroidPath()
    private val face = android.graphics.Path()
    private val matrix = android.graphics.Matrix()
    private val src = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f).also { c ->
        for (i in c.indices) c[i] = (if (i % 2 == 0) center.x else center.y) + c[i] * box / 2f }
    private val dst = FloatArray(8)
    private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL_AND_STROKE; strokeWidth = 4.5f * unit
        strokeJoin = android.graphics.Paint.Join.ROUND
        maskFilter = BlurMaskFilter(5f * unit, BlurMaskFilter.Blur.NORMAL)
    }
    private val round = Stroke(4.5f * unit, join = StrokeJoin.Round)
    private val perspective = size.minDimension * 4f

    // Light from the upper left and in front (screen space, y down, z towards the viewer).
    private val light = floatArrayOf(-.45f, -.6f, .66f).normalized()
    private val half = floatArrayOf(light[0], light[1], light[2] + 1f).normalized()

    fun draw(scope: DrawScope, degrees: Float, body: Color, shadowInk: Color, dark: Boolean) = with(scope) {
        val theta = Math.toRadians(degrees.toDouble())
        val cos = kotlin.math.cos(theta).toFloat(); val sin = kotlin.math.sin(theta).toFloat()
        // Rotation about u = (1, -1, 0)/√2 (the diagonal through the bend), Rodrigues' formula, then perspective.
        val geomCos = if (kotlin.math.abs(cos) < .03f) (if (cos < 0f) -.03f else .03f) else cos
        val r = 1f / kotlin.math.sqrt(2f)
        for (i in 0 until 4) {
            val x = src[2 * i] - center.x; val y = src[2 * i + 1] - center.y
            val along = (x - y) * r                   // u·p
            val crossX = -y * 0f; val crossZ = (-x - y) * r // u × p = (0, 0, (-x - y)/√2)·… (z only, p in the plane)
            val px = x * geomCos + along * r * (1f - geomCos) + crossX
            val py = y * geomCos - along * r * (1f - geomCos)
            val pz = crossZ * sin
            val f = perspective / (perspective - pz)
            dst[2 * i] = center.x + px * f; dst[2 * i + 1] = center.y + py * f
        }
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        face.set(base); face.transform(matrix)
        val shape = face.asComposePath()
        // The surface normal after the turn; the visible side always faces the viewer.
        val sign = if (cos >= 0f) 1f else -1f
        val nx = -sin * r * sign; val ny = -sin * r * sign; val nz = kotlin.math.abs(cos)
        val diffuse = (nx * light[0] + ny * light[1] + nz * light[2]).coerceIn(0f, 1f)
        val rest = light[2]
        val specular = Math.pow((nx * half[0] + ny * half[1] + nz * half[2]).coerceIn(0f, 1f).toDouble(), 8.0).toFloat()
        // Drop shadow, always to the lower right.
        drawIntoCanvas { canvas ->
            shadowPaint.color = shadowInk.copy(alpha = if (dark) .55f else .30f).toArgb()
            canvas.nativeCanvas.save(); canvas.nativeCanvas.translate(2.2f * unit, 4.4f * unit)
            canvas.nativeCanvas.drawPath(face, shadowPaint); canvas.nativeCanvas.restore()
        }
        translate(.8f * unit, 1.8f * unit) {
            val contact = shadowInk.copy(alpha = if (dark) .35f else .16f)
            drawPath(shape, contact); drawPath(shape, contact, style = round)
        }
        // The slab's side: visible only while tilted, on the side turned away from the viewer.
        val depth = 3.2f * unit
        val side = lerp(body, Color.Black, if (dark) .45f else .38f)
        if (nz < .995f) for (step in 3 downTo 1) {
            val k = step / 3f
            translate(-nx * depth * k, -ny * depth * k) { drawPath(shape, side); drawPath(shape, side, style = round) }
        }
        // The face: brighter when it turns to the light, darker when it turns away.
        val tone = diffuse - rest
        val lit = if (tone >= 0f) lerp(body, Color.White, (tone * .9f).coerceAtMost(.5f)) else lerp(body, Color.Black, (-tone * .45f).coerceAtMost(.32f))
        val brush = Brush.linearGradient(listOf(lerp(lit, Color.White, if (dark) .24f else .32f), lit, lerp(lit, Color.Black, if (dark) .30f else .22f)),
            center - Offset(box / 2f, box / 2f), center + Offset(box / 2f, box / 2f))
        drawPath(shape, brush); drawPath(shape, brush, style = round)
        clipPath(shape) {
            // Edges: dark at the lower right, a bright rim at the upper left (offsets in screen space).
            translate(1.6f * unit, 2.0f * unit) { drawPath(shape, Color.Black.copy(alpha = if (dark) .16f else .10f), style = Stroke(3f * unit)) }
            translate(-1.2f * unit, -1.5f * unit) { drawPath(shape, Color.White.copy(alpha = (if (dark) .22f else .34f) * (.4f + .6f * diffuse / rest).coerceAtMost(1.3f)), style = Stroke(2.6f * unit)) }
            // Sheen: a soft reflection of the light, fixed on the screen; it flares as the face turns towards it.
            val glow = ((if (dark) .30f else .45f) * specular / .48f).coerceIn(0f, .8f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = glow), Color.Transparent),
                center + Offset(-.14f * box, -.2f * box), .42f * box), .42f * box, center + Offset(-.14f * box, -.2f * box))
        }
    }

    private fun FloatArray.normalized(): FloatArray {
        val l = kotlin.math.sqrt(this[0] * this[0] + this[1] * this[1] + this[2] * this[2]); return floatArrayOf(this[0] / l, this[1] / l, this[2] / l)
    }
}

/** The bent double arrow in a 100-unit box (after the publisher's drawing): head left, bend, head down. */
internal fun swapArrowPath(unit: Float, origin: Offset): Path = Path().apply {
    fun p(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)
    val start = p(12.8f, 30.3f); moveTo(start.x, start.y)
    p(32.3f, 14.8f).let { lineTo(it.x, it.y) }
    p(32.3f, 23.7f).let { lineTo(it.x, it.y) }
    p(56.6f, 23.7f).let { lineTo(it.x, it.y) }
    // Outer bend (radius 21.9 around 56.6/45.6).
    arcTo(androidx.compose.ui.geometry.Rect(p(56.6f - 21.9f, 45.6f - 21.9f), p(56.6f + 21.9f, 45.6f + 21.9f)), -90f, 90f, false)
    p(78.5f, 65.6f).let { lineTo(it.x, it.y) }
    p(88.9f, 65.6f).let { lineTo(it.x, it.y) }
    p(72.2f, 86.5f).let { lineTo(it.x, it.y) }
    p(55.0f, 65.6f).let { lineTo(it.x, it.y) }
    p(65.8f, 65.6f).let { lineTo(it.x, it.y) }
    p(65.8f, 45.6f).let { lineTo(it.x, it.y) }
    // Inner bend (radius 9.2 around the same centre), back to the horizontal arm.
    arcTo(androidx.compose.ui.geometry.Rect(p(56.6f - 9.2f, 45.6f - 9.2f), p(56.6f + 9.2f, 45.6f + 9.2f)), 0f, -90f, false)
    p(32.3f, 36.4f).let { lineTo(it.x, it.y) }
    p(32.3f, 46.2f).let { lineTo(it.x, it.y) }
    close()
}

/** 2.3: the two fields as they move: from their measured places to each other's. */
data class SwapGhosts(val destinationRect: Rect, val originRect: Rect, val destinationText: String, val originText: String)

/**
 * 2.3: draws both fields while they swap with a spring: the large destination field shrinks into the
 * start box and the start box grows into the destination field, aligned with their real positions.
 * The real fields are hidden meanwhile; the new state is valid from the first frame.
 */
@Composable fun SwapGhostOverlay(ghosts: SwapGhosts, progress: () -> Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val big = MaterialTheme.typography.titleLarge
    val small = MaterialTheme.typography.labelLarge
    val fill = scheme.surfaceContainerHigh
    val ink = scheme.onSurface
    val chipOutline = scheme.outline
    Canvas(modifier.fillMaxSize().clearAndSetSemantics {}.testTag("swap-ghosts")) {
        val t = progress()
        fun ghost(from: Rect, to: Rect, text: String, fromBig: Boolean) {
            val k = t.coerceIn(-.15f, 1.15f)
            val rect = Rect(from.left + (to.left - from.left) * k, from.top + (to.top - from.top) * k,
                from.right + (to.right - from.right) * k, from.bottom + (to.bottom - from.bottom) * k)
            val bigness = if (fromBig) 1f - k.coerceIn(0f, 1f) else k.coerceIn(0f, 1f)
            val corner = lerp(12.dp, 28.dp, bigness).toPx()
            val w = max(rect.width, 1f); val h = max(rect.height, 1f)
            drawRoundRect(fill.copy(alpha = .35f + .65f * bigness), rect.topLeft, Size(w, h), CornerRadius(corner))
            if (bigness < 1f) drawRoundRect(chipOutline.copy(alpha = 1f - bigness), rect.topLeft, Size(w, h), CornerRadius(corner), style = Stroke(1.dp.toPx()))
            val style = TextStyle(fontSize = androidx.compose.ui.unit.lerp(small.fontSize, big.fontSize, bigness), color = ink,
                fontWeight = small.fontWeight)
            val pad = lerp(12.dp, 52.dp, bigness).toPx()
            val layout = measurer.measure(text, style, maxLines = 1, overflow = TextOverflow.Ellipsis,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = (w - pad - 12.dp.toPx()).toInt().coerceAtLeast(1)))
            clipRect(rect.left, rect.top, rect.left + w, rect.top + h) {
                drawText(layout, topLeft = Offset(rect.left + pad, rect.top + (h - layout.size.height) / 2f))
            }
        }
        // The destination text travels into the start box, the start text into the destination field.
        ghost(ghosts.destinationRect, ghosts.originRect, ghosts.destinationText, fromBig = true)
        ghost(ghosts.originRect, ghosts.destinationRect, ghosts.originText, fromBig = false)
    }
}

/** Spring of the swap itself: fluid, with a small overshoot. */
internal val SwapMotionSpring = spring<Float>(dampingRatio = .66f, stiffness = 230f)
