// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.graphics.PathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import kotlinx.coroutines.flow.first
import kotlin.math.*

/*
 * When the destination is removed, the "Verbindung suchen" button melts into a drop, falls
 * onto the floating navigation pill, splashes and runs over the pill and down its left and right ends.
 * The drop lives in its own layer above the navigation (it never takes touches), so the app stays
 * usable for the whole 2.5 s. The geometry below is pure and deterministic (unit-tested).
 */

internal const val LIQUID_DROP_SECONDS = 2.5f
internal const val LIQUID_MELT_END = .26f
internal const val LIQUID_IMPACT = .78f
private const val LIQUID_SQUASH = .09f
private const val LIQUID_FADE_START = 2.0f

private fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

/**
 * One released button: where it was, how big, its outline (relative to its centre) and colours.
 * A wide button has its own [halfWidth], symbol position ([iconOffset], [iconSize]), drop [radius] (0: the
 * standard one) and the share of its shadow that fades out while it melts.
 */
@Immutable internal class LiquidDropRun(val id: Long, val center: Offset, val half: Float, val contour: List<Offset>,
    val color: Color, val ink: Color, val label: String, val labelCenter: Offset, val labelStyle: TextStyle, val labelColor: Color,
    val halfWidth: Float = half, val iconOffset: Offset = Offset.Zero, val iconSize: Float = 0f, val radius: Float = 0f,
    val shadow: Float = 0f, val content: Float = 1f)

/** Shared by the search screen (releases drops) and the navigation pill (reports where it lies, bounces). */
@Stable class LiquidStage {
    /** The visible outline rectangle of the navigation pill in root coordinates; null while hidden. */
    var pill by mutableStateOf<Rect?>(null)
    /** Counts impacts so the pill can answer with a small squash. */
    var impacts by mutableIntStateOf(0)
        private set
    internal val runs = mutableStateListOf<LiquidDropRun>()
    private var serial = 0L
    internal fun release(center: Offset, half: Float, contour: List<Offset>, color: Color, ink: Color, label: String,
        labelCenter: Offset, labelStyle: TextStyle, labelColor: Color, halfWidth: Float = half, iconOffset: Offset = Offset.Zero,
        iconSize: Float = 0f, radius: Float = 0f, shadow: Float = 0f, content: Float = 1f) {
        // Bounded: at most three drops at once, the oldest goes first.
        while (runs.size >= 3) runs.removeAt(0)
        runs += LiquidDropRun(++serial, center, half, contour, color, ink, label, labelCenter, labelStyle, labelColor,
            halfWidth, iconOffset, iconSize, radius, shadow, content)
    }
    internal fun impact() { impacts++ }
}
val LocalLiquidStage = staticCompositionLocalOf<LiquidStage?> { null }

/** The drop's body before and during contact. Null once it has become the film on the pill. */
internal data class DropBody(val center: Offset, val rx: Float, val ry: Float, val tear: Float, val morph: Float)

internal fun liquidDropBody(t: Float, start: Offset, half: Float, radius: Float, landing: Float?, floor: Float,
    halfWidth: Float = half): DropBody? {
    val sag = half * .15f
    if (t < LIQUID_MELT_END) {
        // A wide button gathers sideways into the drop while it sags.
        val m = smooth(0f, LIQUID_MELT_END, t)
        return DropBody(Offset(start.x, start.y + sag * m), halfWidth + (radius * 1.04f - halfWidth) * m, half + (radius * 1.06f - half) * m, .25f * m, m)
    }
    val rxEnd = radius * .92f; val ryEnd = radius * 1.32f
    val y0 = start.y + sag
    val ground = landing ?: (floor + radius * 3f)
    val target = maxOf(ground - ryEnd, y0)
    if (t < LIQUID_IMPACT || landing == null) {
        val u = ((t - LIQUID_MELT_END) / (LIQUID_IMPACT - LIQUID_MELT_END)).coerceAtLeast(0f)
        if (landing == null && u > 1.6f) return null
        val y = y0 + (target - y0) * u * u
        val k = u.coerceAtMost(1f)
        return DropBody(Offset(start.x, y), radius * (1.04f - .12f * k), radius * (1.06f + .26f * k), .25f + .5f * k, 1f)
    }
    val k = ((t - LIQUID_IMPACT) / LIQUID_SQUASH)
    if (k >= 1f) return null
    val ry = ryEnd * (1 - k).pow(1.4f)
    return DropBody(Offset(start.x, ground - ry), rxEnd * (1 + .7f * k), ry, .75f * (1 - k), 1f)
}

/** The liquid on the pill: a mound where the drop landed plus a film that spreads to both ends. */
internal data class FilmState(val mound: Float, val moundWidth: Float, val thickness: Float, val reachLeft: Float,
    val reachRight: Float, val alpha: Float)

internal fun liquidFilm(t: Float, radius: Float, maxLeft: Float, maxRight: Float): FilmState? {
    val s = t - LIQUID_IMPACT
    if (s < 0f || t >= LIQUID_DROP_SECONDS) return null
    val rise = smooth(0f, .07f, s)
    val after = (s - .07f).coerceAtLeast(0f)
    val wobble = 1f + .22f * sin(2 * PI.toFloat() * after / .2f) * exp(-after / .25f)
    val mound = radius * 1.05f * rise * exp(-after / .3f) * wobble
    val width = radius * (.9f + 1.5f * (1 - exp(-s / .16f))) * (1 - .12f * sin(2 * PI.toFloat() * s / .2f) * exp(-s / .25f))
    val thickness = radius * .30f * smooth(.02f, .18f, s) * (1 - .5f * smooth(.4f, 1.6f, s))
    fun reach(max: Float): Float { val a = minOf(radius * 1.2f, max); return a + (max - a) * (1 - exp(-s / .45f)) }
    val alpha = 1f - smooth(LIQUID_FADE_START, LIQUID_DROP_SECONDS, t)
    return FilmState(mound, width, thickness, reach(maxLeft), reach(maxRight), alpha)
}

/** Thickness of the liquid at arc distance [d] from the impact point; zero beyond the front. */
internal fun filmThickness(d: Float, film: FilmState, reach: Float): Float {
    if (d < 0f || reach <= 0f || d > reach) return 0f
    val x = d / reach
    val envelope = sqrt((1 - x.pow(10)).coerceAtLeast(0f))
    val mound = film.mound * exp(-(d / film.moundWidth.coerceAtLeast(1f)).pow(2))
    val head = film.thickness * .55f * exp(-((d - reach * .93f) / (reach * .07f + 2f)).pow(2))
    return (mound + film.thickness + head) * envelope
}

/** A droplet thrown up by the impact; null once it fell back onto the pill or out of time. */
internal fun splashDroplet(i: Int, count: Int, t: Float, impact: Offset, radius: Float, pill: Rect?): Pair<Offset, Float>? {
    val tau = t - LIQUID_IMPACT - .02f
    if (tau < 0f || tau > .8f) return null
    fun hash(n: Int) = ((sin(n * 12.9898f + 78.233f) * 43758.547f) % 1f + 1f) % 1f
    val angle = PI.toFloat() * (.12f + .76f * i / (count - 1).coerceAtLeast(1)) + (hash(i) - .5f) * .22f
    val speed = radius * (11f + 9f * hash(i + 7))
    val g = radius * 70f
    val x = impact.x + cos(angle) * speed * tau
    val y = impact.y - radius * .3f - sin(angle) * speed * tau + .5f * g * tau * tau
    val falling = -sin(angle) * speed + g * tau > 0f
    if (falling && pill != null && x in pill.left..pill.right && y >= pill.top) return null
    return Offset(x, y) to radius * (.10f + .08f * hash(i + 3))
}

/** Points and outward normals along the pill outline, every [step] pixels. */
internal class OutlineSamples(val points: List<Offset>, val normals: List<Offset>, val step: Float, val bounds: Rect) {
    val size get() = points.size
    fun at(i: Int) = ((i % size) + size) % size
    /** The sample on the upper half closest to [x]. */
    fun topIndex(x: Float): Int = points.indices.filter { points[it].y < bounds.center.y }.minByOrNull { abs(points[it].x - x) } ?: 0
    /** Arc distance from [from] (walking in [direction]) to the first point on the lower part of an end. */
    fun dripDistance(from: Int, direction: Int): Float {
        val limit = bounds.center.y + bounds.height * .22f
        for (k in 1 until size / 2) {
            val i = at(from + direction * k)
            if (points[i].y >= limit && abs(normals[i].x) > .3f) return k * step
        }
        return size / 2 * step
    }
}

internal fun sampleOutline(path: android.graphics.Path, step: Float, bounds: Rect): OutlineSamples? {
    val measure = PathMeasure(path, true)
    val length = measure.length
    if (length <= step * 8) return null
    val count = (length / step).toInt()
    val pos = FloatArray(2); val tan = FloatArray(2)
    val points = ArrayList<Offset>(count); val normals = ArrayList<Offset>(count)
    repeat(count) { i ->
        measure.getPosTan(length * i / count, pos, tan)
        points += Offset(pos[0], pos[1]); normals += Offset(tan[1], -tan[0])
    }
    val top = points.indices.minByOrNull { points[it].y } ?: return null
    val flip = normals[top].y > 0f
    return OutlineSamples(points, if (flip) normals.map { -it } else normals, length / count, bounds)
}

/** The film as one closed outline along the pill, from the left front over the impact to the right front. */
internal fun filmPath(samples: OutlineSamples, impact: Int, film: FilmState, inset: Float): Path {
    val maxSteps = samples.size / 2 - 1
    val left = (0..minOf((film.reachLeft / samples.step).toInt(), maxSteps)).map { samples.at(impact - it) to it * samples.step }
    val right = (0..minOf((film.reachRight / samples.step).toInt(), maxSteps)).map { samples.at(impact + it) to it * samples.step }
    val outer = left.asReversed().map { (i, d) -> samples.points[i] + samples.normals[i] * filmThickness(d, film, film.reachLeft) } +
        right.drop(1).map { (i, d) -> samples.points[i] + samples.normals[i] * filmThickness(d, film, film.reachRight) }
    val inner = (right.asReversed() + left.drop(1)).map { (i, _) -> samples.points[i] - samples.normals[i] * inset }
    return Path().apply {
        outer.forEachIndexed { k, p -> if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        inner.forEach { lineTo(it.x, it.y) }
        close()
    }
}

/** A drip hanging from, then falling off an end of the pill: centre and half axes. */
internal fun dripDrop(t: Float, born: Float, origin: Offset, radius: Float): Pair<Offset, Size>? {
    val s = t - born
    if (s < 0f) return null
    // Hangs and swells for a moment, stretching under its own weight.
    if (s < DRIP_HANG) {
        val grow = smooth(0f, DRIP_HANG, s)
        return origin + Offset(0f, radius * (.4f + .9f * grow)) to Size(radius * .9f * grow, radius * (.8f + .5f * grow) * grow)
    }
    val fall = s - DRIP_HANG
    val g = radius * 160f
    val v = g * fall
    val stretch = 1f + .7f * (v / (radius * 30f)).coerceAtMost(1f)
    return origin + Offset(0f, radius * 1.3f + .5f * g * fall * fall) to Size(radius * .9f / sqrt(stretch), radius * 1.3f * stretch)
}
internal const val DRIP_HANG = .24f

/** When the film front passes [distance] along one side (seconds since start), or null if never. */
internal fun filmArrival(distance: Float, radius: Float, max: Float): Float? {
    val a = minOf(radius * 1.2f, max)
    if (distance <= a) return LIQUID_IMPACT
    if (distance >= max) return null
    return LIQUID_IMPACT - .45f * ln(1 - (distance - a) / (max - a))
}

/** The layer above the navigation pill. Draws only; every touch passes through. */
@Composable fun LiquidStageLayer(stage: LiquidStage, modifier: Modifier = Modifier) {
    if (stage.runs.isEmpty()) return
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val shape = dev.ambivalenton.openv.ui.theme.appCorner(42.dp)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val pill = stage.pill
    val samples = remember(pill, shape, density, direction) {
        pill?.let { r ->
            val outline = shape.createOutline(r.size, direction, density)
            val path = Path().apply { addOutline(outline); translate(r.topLeft) }
            sampleOutline(path.asAndroidPath(), with(density) { 1.5.dp.toPx() }, r)
        }
    }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.testTag("liquid-stage")) {
        stage.runs.toList().forEach { run -> key(run.id) { LiquidRunCanvas(stage, run, samples, origin) } }
    }
}

@Composable private fun LiquidRunCanvas(stage: LiquidStage, run: LiquidDropRun, samples: OutlineSamples?, origin: Offset) {
    val clock = remember(run.id) { Animatable(0f) }
    val measurer = rememberTextMeasurer()
    val label = remember(run.id) { measurer.measure(run.label, run.labelStyle) }
    val icon = rememberVectorPainter(ImageVector.vectorResource(R.drawable.ic_search))
    val density = LocalDensity.current
    LaunchedEffect(run.id) {
        clock.animateTo(LIQUID_DROP_SECONDS, tween((LIQUID_DROP_SECONDS * 1000).toInt(), easing = LinearEasing))
        stage.runs.remove(run)
    }
    LaunchedEffect(run.id, samples != null) {
        if (samples == null) return@LaunchedEffect
        snapshotFlow { clock.value >= LIQUID_IMPACT }.first { it }
        stage.impact()
    }
    Canvas(Modifier.fillMaxSize()) {
        translate(-origin.x, -origin.y) {
            drawLiquidRun(run, clock.value, samples, label, icon,
                if (run.radius > 0f) run.radius else with(density) { 27.dp.toPx() },
                if (run.iconSize > 0f) run.iconSize else with(density) { 28.dp.toPx() }, size.height + origin.y, density.density)
        }
    }
}

private fun DrawScope.drawLiquidRun(run: LiquidDropRun, t: Float, samples: OutlineSamples?, label: androidx.compose.ui.text.TextLayoutResult,
    icon: androidx.compose.ui.graphics.painter.Painter, radius: Float, iconSize: Float, floor: Float, d: Float) {
    val fade = 1f - smooth(LIQUID_FADE_START, LIQUID_DROP_SECONDS, t)
    val color = run.color
    val landing = samples?.let { it.points[it.topIndex(run.center.x)].y }
    val body = liquidDropBody(t, run.center, run.half, radius, landing, floor, run.halfWidth)
    // How far a wide button has gathered towards its middle; label and symbol go along with it.
    val gather = body?.let { it.rx / run.halfWidth } ?: 0f
    // The button's shadow fades while it melts.
    if (run.shadow > 0f && t < .3f && body != null) {
        val a = .34f * run.shadow * (1f - t / .3f)
        val c = body.center + Offset(0f, 8f * d)
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = a), Color.Black.copy(alpha = a * .45f), Color.Transparent), c,
            max(body.rx, body.ry) * 1.25f), Offset(c.x - body.rx * 1.25f, c.y - body.ry * 1.6f), Size(body.rx * 2.5f, body.ry * 3.2f))
    }
    if (samples != null) {
        val impact = samples.topIndex(run.center.x)
        val leftDrip = samples.dripDistance(impact, -1); val rightDrip = samples.dripDistance(impact, 1)
        // The film runs around both ends down to their lowest part, where it drips off.
        val maxLeft = leftDrip + radius * 1.5f; val maxRight = rightDrip + radius * 1.5f
        liquidFilm(t, radius, maxLeft, maxRight)?.let { film ->
            val ink = color.copy(alpha = color.alpha * film.alpha)
            drawPath(filmPath(samples, impact, film, 1.dp.toPx()), ink)
            for ((side, distance, max) in listOf(Triple(-1, leftDrip, maxLeft), Triple(1, rightDrip, maxRight))) {
                val at = distance + radius * 1.1f
                val arrival = filmArrival(at, radius, max) ?: continue
                val point = samples.points[samples.at(impact + side * (at / samples.step).toInt())]
                for (k in 0 until 3) {
                    val born = arrival + k * .28f
                    if (born > LIQUID_DROP_SECONDS - .5f) break
                    val r = radius * .24f * (1f - .2f * k)
                    dripDrop(t, born, point, r)?.let { (center, size) ->
                        // A thin neck still connects the drip to the film for a moment after it lets go.
                        val since = t - born - DRIP_HANG
                        if (since < .1f) drawLine(ink, point, center - Offset(0f, size.height * .6f),
                            strokeWidth = r * .7f * (1f - (since / .1f).coerceIn(0f, 1f)), cap = StrokeCap.Round)
                        drawOval(ink, center - Offset(size.width, size.height), Size(size.width * 2, size.height * 2))
                    }
                }
            }
        }
        val hit = Offset(run.center.x, landing ?: 0f)
        for (i in 0 until 9) splashDroplet(i, 9, t, hit, radius, samples.bounds)?.let { (center, r) ->
            drawCircle(color.copy(alpha = color.alpha * fade), r, center)
        }
    }
    body?.let { body ->
        drawPath(dropOutline(run.contour, run.half, body, run.halfWidth), color.copy(alpha = color.alpha * fade))
        if (body.morph > .5f) drawOval(Color.White.copy(alpha = .22f * fade),
            Offset(body.center.x - body.rx * .55f, body.center.y - body.ry * .55f), Size(body.rx * .38f, body.ry * .26f))
        val iconAt = body.center + run.iconOffset * gather
        // Label and symbol of the button leave first (The label lies on the button, so after it).
        if (t < .16f && run.content > 0f) {
            val at = body.center + (run.labelCenter - run.center) * gather
            // It narrows with the gathering liquid instead of sticking out of it.
            withTransform({ scale(gather.coerceIn(.2f, 1f), 1f, at) }) {
                drawText(label, run.labelColor, at - Offset(label.size.width / 2f, label.size.height / 2f), alpha = (1f - t / .16f).coerceIn(0f, 1f) * run.content)
            }
        }
        if (t < .16f) translate(iconAt.x - iconSize / 2, iconAt.y - iconSize / 2) {
            with(icon) { draw(Size(iconSize, iconSize), alpha = (1f - t / .16f) * run.content, colorFilter = ColorFilter.tint(run.ink)) }
        }
    }
}

/** Keeps the button's own outline at first and turns it continuously into a (falling) drop. */
internal fun dropOutline(contour: List<Offset>, half: Float, body: DropBody, halfWidth: Float = half): Path {
    val path = Path()
    if (contour.isEmpty()) { path.addOval(Rect(body.center, maxOf(body.rx, body.ry))); return path }
    val first = contour.first(); val start = atan2(first.y, first.x)
    val signed = contour.indices.sumOf { i -> val a = contour[i]; val b = contour[(i + 1) % contour.size]; (a.x * b.y - b.x * a.y).toDouble() }
    val winding = if (signed >= 0) 1 else -1
    contour.forEachIndexed { i, point ->
        val angle = start + winding * 2 * PI.toFloat() * i / contour.size
        val c = cos(angle); val s = sin(angle); val top = max(0f, -s)
        val dropX = c * (1 - body.tear * top * .36f) * body.rx
        val dropY = (s - body.tear * .5f * top.pow(4)) * body.ry
        val button = Offset(point.x / halfWidth * body.rx, point.y / half * body.ry)
        val x = button.x * (1 - body.morph) + dropX * body.morph
        val y = button.y * (1 - body.morph) + dropY * body.morph
        if (i == 0) path.moveTo(body.center.x + x, body.center.y + y) else path.lineTo(body.center.x + x, body.center.y + y)
    }
    path.close()
    return path
}
