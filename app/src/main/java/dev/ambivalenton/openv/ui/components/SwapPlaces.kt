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
import dev.ambivalenton.openv.ui.theme.corner
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

/** How strongly a sweeping tip pushes the liquid body (per radius per second). */
private const val PUSH = 7.5f

/** The arrow's spring: a lively but short bounce. */
private val ArrowSpring = spring<Float>(dampingRatio = .52f, stiffness = 420f)

/**
 * The swap arrow after the publisher's drawing: one bent arrow with a head to the left and
 * a head downwards, raised by light and shadow in the colours of the theme. A tap turns it in space by
 * half a turn about its 45° axis (the diagonal through its bend), so the two heads trade places, with
 * a springy finish. The light stays where it is while the arrow turns (see [SpaceArrow]).
 */
@Composable fun SwapPlacesArrow(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    /** Build 122: always shown; without a destination it stays faint and [onClick] explains what is missing. */
    ready: Boolean = true) {
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
    // Build 126: the round body is liquid – the tips sweeping past push it out, the rest draws in, it wobbles on.
    val liquid = remember { LiquidRing() }
    var liquidFrame by remember { mutableIntStateOf(0) }
    val probe = remember { SpaceArrow(Size(78f, 78f)) }
    LaunchedEffect(taps) {
        if (taps == 0 || quiet) return@LaunchedEffect
        kotlinx.coroutines.coroutineScope {
            launch { bounce.animateTo(.9f, tween(80, easing = FastOutLinearInEasing)); bounce.animateTo(1f, spring(dampingRatio = .42f, stiffness = 420f)) }
            launch { flip.animateTo(taps * 180f, spring(dampingRatio = .58f, stiffness = 170f)) }
            launch {
                // The press dips the drop from above: flatter, wider, then it springs back.
                liquid.squeeze((Math.PI / 2).toFloat(), -2.4f)
                var last = withFrameNanos { it }
                var previous: List<Triple<Float, Float, Float>>? = null
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = ((now - last) / 1e9f).coerceIn(0f, .05f); last = now
                    // Tips relative to the circle (radius 50 in the probe's units: the arrow box is 78 % of the button).
                    val tips = probe.tips(flip.value)
                    val sources = tips.mapIndexed { i, (x, y, z) ->
                        val rx = (x - 39f) / 50f; val ry = (y - 39f) / 50f
                        val speed = previous?.get(i)?.let { (px, py, _) -> kotlin.math.hypot((x - px) / 50f, (y - py) / 50f) / dt.coerceAtLeast(1e-3f) } ?: 0f
                        // Faster and nearer to the rim (and nearer to the viewer) pushes harder.
                        LiquidRing.Source(kotlin.math.atan2(ry, rx), PUSH * speed * kotlin.math.hypot(rx, ry) * (1f + (z / 39f).coerceIn(-.5f, .5f)))
                    }
                    previous = tips
                    liquid.step(dt, sources)
                    liquidFrame++
                    if (!flip.isRunning && liquid.resting) { liquid.reset(); liquidFrame++; break }
                }
            }
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
            role = Role.Button, onClickLabel = label) { if (ready) taps++; onClick() }
            .semantics { contentDescription = label } else Modifier.clearAndSetSemantics {})
        .testTag("swap-places")) {
        // The turn is computed as geometry (the outline projected in space), the light is
        // not: it keeps coming from the upper left, so shadow, sheen and edges never turn with it.
        // Build 122: a round tonal button behind the arrow, so it reads as something to tap.
        val ring by androidx.compose.animation.core.animateFloatAsState(if (ready) 1f else .45f,
            if (quiet) androidx.compose.animation.core.snap() else spring(dampingRatio = .8f, stiffness = 380f), label = "swap-ready")
        val container = scheme.secondaryContainer
        Spacer(Modifier.fillMaxSize().drawWithCache {
            val arrow = SpaceArrow(Size(size.width * .78f, size.height * .78f))
            onDrawBehind {
                @Suppress("UNUSED_EXPRESSION") liquidFrame // a new liquid frame redraws (only the drawing, no layout)
                drawPath(liquid.path(center, size.minDimension / 2f), container.copy(alpha = container.alpha * (.55f + .45f * ring)))
                translate(size.width * .11f, size.height * .11f) {
                    arrow.draw(this, flip.value, body.copy(alpha = ring), shadowInk.copy(alpha = shadowInk.alpha * ring), dark)
                }
            }
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
    // Build 126: "pivot", not "center": inside draw (with the DrawScope as receiver) "center" meant the middle of the
    // whole drawing area. The arrow is drawn inset, so after half a turn it was mirrored about the wrong point
    // and slipped out of its circle.
    private val pivot = Offset(size.width / 2f, size.height / 2f)
    private val base = swapArrowPath(unit, pivot - Offset(box / 2f, box / 2f)).asAndroidPath()
    private val face = android.graphics.Path()
    private val matrix = android.graphics.Matrix()
    private val src = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f).also { c ->
        for (i in c.indices) c[i] = (if (i % 2 == 0) pivot.x else pivot.y) + c[i] * box / 2f }
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

    /** Build 126: the two arrow tips at [degrees]: projected position (this arrow's box) and depth towards the viewer. */
    fun tips(degrees: Float): List<Triple<Float, Float, Float>> {
        val theta = Math.toRadians(degrees.toDouble())
        val cos = kotlin.math.cos(theta).toFloat(); val sin = kotlin.math.sin(theta).toFloat()
        val r = 1f / kotlin.math.sqrt(2f)
        val origin = pivot - Offset(box / 2f, box / 2f)
        return listOf(Offset(12.8f, 30.3f), Offset(72.2f, 86.5f)).map { t ->
            val x = origin.x + t.x * unit - pivot.x; val y = origin.y + t.y * unit - pivot.y
            val along = (x - y) * r
            val px = x * cos + along * r * (1f - cos); val py = y * cos - along * r * (1f - cos)
            val pz = (-x - y) * r * sin
            val f = perspective / (perspective - pz)
            Triple(pivot.x + px * f, pivot.y + py * f, pz)
        }
    }

    fun draw(scope: DrawScope, degrees: Float, body: Color, shadowInk: Color, dark: Boolean) = with(scope) {
        val theta = Math.toRadians(degrees.toDouble())
        val cos = kotlin.math.cos(theta).toFloat(); val sin = kotlin.math.sin(theta).toFloat()
        // Rotation about u = (1, -1, 0)/√2 (the diagonal through the bend), Rodrigues' formula, then perspective.
        val geomCos = if (kotlin.math.abs(cos) < .03f) (if (cos < 0f) -.03f else .03f) else cos
        val r = 1f / kotlin.math.sqrt(2f)
        for (i in 0 until 4) {
            val x = src[2 * i] - pivot.x; val y = src[2 * i + 1] - pivot.y
            val along = (x - y) * r                   // u·p
            val crossX = -y * 0f; val crossZ = (-x - y) * r // u × p = (0, 0, (-x - y)/√2)·… (z only, p in the plane)
            val px = x * geomCos + along * r * (1f - geomCos) + crossX
            val py = y * geomCos - along * r * (1f - geomCos)
            val pz = crossZ * sin
            val f = perspective / (perspective - pz)
            dst[2 * i] = pivot.x + px * f; dst[2 * i + 1] = pivot.y + py * f
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
            pivot - Offset(box / 2f, box / 2f), pivot + Offset(box / 2f, box / 2f))
        drawPath(shape, brush); drawPath(shape, brush, style = round)
        clipPath(shape) {
            // Edges: dark at the lower right, a bright rim at the upper left (offsets in screen space).
            translate(1.6f * unit, 2.0f * unit) { drawPath(shape, Color.Black.copy(alpha = if (dark) .16f else .10f), style = Stroke(3f * unit)) }
            translate(-1.2f * unit, -1.5f * unit) { drawPath(shape, Color.White.copy(alpha = (if (dark) .22f else .34f) * (.4f + .6f * diffuse / rest).coerceAtMost(1.3f)), style = Stroke(2.6f * unit)) }
            // Sheen: a soft reflection of the light, fixed on the screen; it flares as the face turns towards it.
            val glow = ((if (dark) .30f else .45f) * specular / .48f).coerceIn(0f, .8f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = glow), Color.Transparent),
                pivot + Offset(-.14f * box, -.2f * box), .42f * box), .42f * box, pivot + Offset(-.14f * box, -.2f * box))
        }
    }

    private fun FloatArray.normalized(): FloatArray {
        val l = kotlin.math.sqrt(this[0] * this[0] + this[1] * this[1] + this[2] * this[2]); return floatArrayOf(this[0] / l, this[1] / l, this[2] / l)
    }
}

/**
 * Build 126 · the button's round body as a little volume of liquid. Its rim is [n] points, each with a radial
 * offset (in radii) that springs back to the circle, is pulled smooth by its neighbours (surface tension), is
 * damped only a little (it wobbles on) and keeps the enclosed volume: what bulges out in one place is drawn in
 * elsewhere. Sources push from inside – the arrow tips sweeping past while the arrow turns, a press from above –
 * nothing is added to a fixed size. Pure and frame-rate independent (fixed sub-steps), so it is unit-tested.
 */
internal class LiquidRing(val n: Int = 40) {
    val offset = FloatArray(n)
    private val velocity = FloatArray(n)
    fun angle(i: Int) = (2.0 * Math.PI * i / n).toFloat()
    /** A push from inside towards [angle] (radians, screen coordinates) with [strength]. */
    data class Source(val angle: Float, val strength: Float)
    /** A sudden push: [amount] (radii per second) along [angle] and its opposite, the same amount in across. */
    fun squeeze(angle: Float, amount: Float) {
        for (i in 0 until n) velocity[i] += amount * kotlin.math.cos(2f * (angle(i) - angle))
        keepVolume()
    }
    fun step(dt: Float, sources: List<Source>) {
        var left = dt.coerceIn(0f, .1f)
        val h = 1f / 480f
        while (left > 0f) {
            val k = minOf(h, left); left -= k
            for (i in 0 until n) {
                val lap = offset[(i + n - 1) % n] + offset[(i + 1) % n] - 2f * offset[i]
                var push = 0f
                for (s in sources) {
                    var d = angle(i) - s.angle
                    while (d > Math.PI) d -= (2 * Math.PI).toFloat()
                    while (d < -Math.PI) d += (2 * Math.PI).toFloat()
                    push += s.strength * kotlin.math.exp(-(d / SPREAD) * (d / SPREAD))
                }
                velocity[i] += (-STIFFNESS * offset[i] + TENSION * lap - DAMPING * velocity[i] + push) * k
            }
            for (i in 0 until n) offset[i] = (offset[i] + velocity[i] * k).coerceIn(-.3f, .3f)
            keepVolume()
        }
    }
    /** Incompressible: the mean radius stays the circle's (first order), velocities likewise. */
    private fun keepVolume() {
        val mean = offset.average().toFloat(); val meanV = velocity.average().toFloat()
        for (i in 0 until n) { offset[i] -= mean; velocity[i] -= meanV }
    }
    val resting: Boolean get() = offset.all { kotlin.math.abs(it) < .002f } && velocity.all { kotlin.math.abs(it) < .02f }
    fun reset() { offset.fill(0f); velocity.fill(0f) }
    /** The rim as a smooth closed curve (Catmull-Rom through the points) around [c] with rest radius [radius]. */
    fun path(c: Offset, radius: Float): Path {
        val pts = List(n) { i -> val a = angle(i); val r = radius * (1f + offset[i]); Offset(c.x + r * kotlin.math.cos(a), c.y + r * kotlin.math.sin(a)) }
        return Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until n) {
                val p0 = pts[(i + n - 1) % n]; val p1 = pts[i]; val p2 = pts[(i + 1) % n]; val p3 = pts[(i + 2) % n]
                cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
            }
            close()
        }
    }
    companion object {
        /** About two wobbles a second, lightly damped; tension smooths the rim; a push spreads over ~±30°. */
        const val STIFFNESS = 170f
        const val DAMPING = 6.5f
        const val TENSION = 700f
        const val SPREAD = .55f
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

/** 2.3: the two fields as they move: from their measured places to each other's. [originGps]: the start chip showed GPS. */
data class SwapGhosts(val destinationRect: Rect, val originRect: Rect, val destinationText: String, val originText: String,
    val originGps: Boolean = false)

/** How one of the two fields looks at rest; the moving ghost blends every value from one to the other. */
private data class FieldLook(val corner: androidx.compose.ui.unit.Dp, val fill: Color, val border: Color, val iconSize: Float, val iconX: Float,
    val iconTint: Color, val textSize: androidx.compose.ui.unit.TextUnit, val weight: androidx.compose.ui.text.font.FontWeight,
    val ink: Color, val chip: Float)

/**
 * Build 127 · both fields as they trade places, in one motion. Each ghost is drawn exactly like the field it
 * leaves and, step by step, like the field it becomes: corner, fill and outline, icon size and colour, type size
 * and weight, the chip's "Ab" and chevron, the field's clear button and its label all blend along the way, while the
 * two fly past each other on gentle arcs – the rising one above, lifted by a soft shadow. At the end they lie
 * exactly on the real fields, which fade in under them ([reveal]); nothing snaps or changes its frame at arrival.
 */
@Composable fun SwapGhostOverlay(ghosts: SwapGhosts, progress: () -> Float, modifier: Modifier = Modifier, reveal: () -> Float = { 0f },
    landedOrigin: () -> Rect = { ghosts.originRect }, landedDestination: () -> Rect = { ghosts.destinationRect }) {
    val scheme = MaterialTheme.colorScheme
    val visuals = dev.ambivalenton.openv.ui.theme.LocalAppVisuals.current
    val measurer = rememberTextMeasurer()
    val t = MaterialTheme.typography
    val place = androidx.compose.ui.res.painterResource(dev.ambivalenton.openv.R.drawable.ic_place)
    val gps = androidx.compose.ui.res.painterResource(dev.ambivalenton.openv.R.drawable.ic_gps)
    val chevron = androidx.compose.ui.res.painterResource(dev.ambivalenton.openv.R.drawable.ic_expand)
    val close = androidx.compose.ui.res.painterResource(dev.ambivalenton.openv.R.drawable.ic_close)
    val abLabel = tr("Ab"); val fieldLabel = tr("Ziel, Adresse oder Haltestelle")
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // The two looks at rest, exactly as the real fields draw them.
    val field = FieldLook(28.dp, scheme.surfaceContainerHigh, Color.Transparent, 24f, 12f, scheme.onSurfaceVariant,
        t.titleLarge.fontSize, t.titleLarge.fontWeight ?: androidx.compose.ui.text.font.FontWeight.Normal, scheme.onSurface, 0f)
    val chip = FieldLook(12.dp, scheme.surfaceContainerHigh.copy(alpha = 0f), scheme.outline, 18f, 12f, scheme.primary,
        t.labelLarge.fontSize, androidx.compose.ui.text.font.FontWeight.SemiBold, scheme.onSurface, 1f)
    val abLayout = remember(abLabel, t) { measurer.measure(abLabel, t.labelMedium) }
    val labelLayout = remember(fieldLabel, t) { measurer.measure(fieldLabel, t.bodySmall) }
    Canvas(modifier.fillMaxSize().clearAndSetSemantics {}.testTag("swap-ghosts")) {
        val k = progress()
        val fade = 1f - reveal().coerceIn(0f, 1f)
        if (fade <= 0f) return@Canvas
        val lift = kotlin.math.sin(Math.PI.toFloat() * k.coerceIn(0f, 1f))
        fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f
        fun ghost(from: Rect, to: Rect, a: FieldLook, b: FieldLook, text: String, startGps: Boolean, bow: Float, above: Boolean) {
            val f = k.coerceIn(0f, 1f)
            // Eased style blend; the frame follows the spring itself (with its small overshoot).
            val s = f * f * (3f - 2f * f)
            val scale = if (above) 1f + .035f * lift else 1f - .02f * lift
            val cx = mix(from.center.x, to.center.x, k) + bow * lift
            val cy = mix(from.center.y, to.center.y, k)
            val w = mix(from.width, to.width, k).coerceAtLeast(1f) * scale
            val h = mix(from.height, to.height, k).coerceAtLeast(1f) * scale
            val rect = Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            val shape = visuals.corner(lerp(a.corner, b.corner, s))
            val outline = shape.createOutline(Size(w, h), direction, density)
            translate(rect.left, rect.top) {
                if (above && lift > 0f) for (layer in 1..3) translate(0f, (layer * 1.6f).dp.toPx() * lift) {
                    drawOutline(outline, Color.Black.copy(alpha = .05f * lift * fade))
                }
                drawOutline(outline, lerp(a.fill, b.fill, s).let { it.copy(alpha = it.alpha * fade) })
                val border = lerp(a.border, b.border, s)
                if (border.alpha > 0f) drawOutline(outline, border.copy(alpha = border.alpha * fade), style = Stroke(1.dp.toPx()))
                val path = Path().apply { addOutline(outline) }
                clipPath(path) {
                    val chipness = mix(a.chip, b.chip, s)
                    // Icon: place or GPS, its size and colour blending.
                    val iconPx = mix(a.iconSize, b.iconSize, s).dp.toPx()
                    val iconLeft = mix(a.iconX, b.iconX, s).dp.toPx()
                    val iconTint = lerp(a.iconTint, b.iconTint, s)
                    fun icon(painter: androidx.compose.ui.graphics.painter.Painter, alpha: Float) = translate(iconLeft, (h - iconPx) / 2f) {
                        with(painter) { draw(Size(iconPx, iconPx), alpha = alpha * fade, colorFilter = ColorFilter.tint(iconTint)) }
                    }
                    val gpsWeight = if (startGps) 1f - s else 0f
                    if (gpsWeight > 0f) icon(gps, gpsWeight)
                    if (gpsWeight < 1f) icon(place, 1f - gpsWeight)
                    // "Ab" only on the chip, the text right after it there; in the field the text starts after the icon slot.
                    val abX = iconLeft + iconPx + 8.dp.toPx()
                    if (chipness > .01f) drawText(abLayout, lerp(a.ink, b.ink, s).copy(alpha = .72f), Offset(abX, (h - abLayout.size.height) / 2f), alpha = chipness * fade)
                    val textX = mix(52.dp.toPx(), abX + abLayout.size.width + 8.dp.toPx(), chipness)
                    val trailing = mix(48.dp.toPx(), 12.dp.toPx() + 16.dp.toPx() + 6.dp.toPx(), chipness)
                    val style = TextStyle(fontSize = androidx.compose.ui.unit.lerp(a.textSize, b.textSize, s),
                        fontWeight = androidx.compose.ui.text.font.FontWeight(mix(a.weight.weight.toFloat(), b.weight.weight.toFloat(), s).toInt().coerceIn(1, 1000)),
                        fontFamily = t.titleLarge.fontFamily, color = lerp(a.ink, b.ink, s))
                    val layout = measurer.measure(text, style, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (w - textX - trailing).toInt().coerceAtLeast(1)))
                    drawText(layout, topLeft = Offset(textX, (h - layout.size.height) / 2f), alpha = fade)
                    // Trailing: the chip's chevron, the field's clear button.
                    val chevronPx = 16.dp.toPx(); val closePx = 24.dp.toPx()
                    if (chipness > .01f) translate(w - 12.dp.toPx() - chevronPx, (h - chevronPx) / 2f) {
                        with(chevron) { draw(Size(chevronPx, chevronPx), alpha = chipness * fade, colorFilter = ColorFilter.tint(lerp(a.ink, b.ink, s))) }
                    }
                    if (chipness < .99f) translate(w - 12.dp.toPx() - closePx, (h - closePx) / 2f) {
                        with(close) { draw(Size(closePx, closePx), alpha = (1f - chipness) * fade, colorFilter = ColorFilter.tint(scheme.onSurfaceVariant)) }
                    }
                }
                // The field's label sits on its upper edge; it fades in as the ghost becomes the field (and out the other way).
                val labelAlpha = ((1f - mix(a.chip, b.chip, s)) - .55f).coerceAtLeast(0f) / .45f
                if (labelAlpha > 0f) drawText(labelLayout, scheme.onSurfaceVariant, Offset(16.dp.toPx(), -labelLayout.size.height / 2f), alpha = labelAlpha * fade)
            }
        }
        // The start text sinks into the destination field (below), the destination rises into the start chip (above).
        // Material reserves half a label line above an outlined field. The chip has no such inset.
        fun fieldBody(bounds: Rect) = bounds.copy(top = (bounds.top + labelLayout.size.height / 2f).coerceAtMost(bounds.bottom))
        ghost(ghosts.originRect, fieldBody(landedDestination()), chip, field, ghosts.originText, ghosts.originGps, -14.dp.toPx(), above = false)
        ghost(fieldBody(ghosts.destinationRect), landedOrigin(), field, chip, ghosts.destinationText, false, 14.dp.toPx(), above = true)
    }
}

/** Hand off while the fields are arriving, on the same timeline as their movement. */
internal fun swapFieldReveal(progress: Float): Float {
    val t = ((progress - .88f) / .12f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Spring of the swap itself: fluid, with a small overshoot. */
internal val SwapMotionSpring = spring<Float>(dampingRatio = .66f, stiffness = 230f)
