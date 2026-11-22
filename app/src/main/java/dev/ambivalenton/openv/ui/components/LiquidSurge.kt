// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.graphics.PathMeasure
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.*

/*
 * The wide search button forms out of a surge of liquid instead of a drop. A flat mass oozes
 * out of the destination field's lower edge (hanging from it by a neck that thins and tears), falls
 * under gravity, hits the lower edge of the map and spreads flat, throws a few droplets aside, then
 * springs up into the button's place and sets into the button's outline (with the design's corners)
 * while symbol and label appear. Every quantity is continuous; the model is pure and unit-tested.
 */

internal const val LIQUID_SURGE_SECONDS = .88f
internal const val SURGE_BUD = .14f
internal const val SURGE_IMPACT = .40f
private const val SURGE_SQUASH = .06f
internal const val SURGE_RISE = SURGE_IMPACT + SURGE_SQUASH
/** Half height the mass keeps when it lies flat on the map's edge, as a share of the button's. */
private const val FLAT = .5f
/** How much wider than the button it spreads on impact. */
private const val SPREAD = 1.14f
/** Share of the fall after which the neck has torn. */
private const val TEAR = .38f

/** Where the surge comes from, where it lands and where the button stands (px, one coordinate system). */
internal data class SurgeGeometry(val source: Offset, val floor: Float, val target: Offset, val width: Float, val height: Float)

/**
 * One moment: centre and half axes of the mass, how far it has set into the button's outline, the
 * neck to the field (1 = attached, 0 = torn), ripples, and how far symbol, label and shadow are back.
 */
internal data class SurgeFrame(val center: Offset, val halfWidth: Float, val halfHeight: Float, val morph: Float,
    val neck: Float, val wobble: Float, val phase: Float, val content: Float, val shadow: Float)

private fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

/** Damped oscillation from 1 with zero initial speed; frequency [hz], damping ratio [z]. */
private fun damped(tau: Float, hz: Float, z: Float): Float {
    val w0 = 2 * PI.toFloat() * hz; val wd = w0 * sqrt(1 - z * z)
    return exp(-z * w0 * tau) * (cos(wd * tau) + z * w0 / wd * sin(wd * tau))
}

private const val BUD_HEIGHT = .80f
private const val BUD_WIDTH = .78f

internal fun liquidSurgeFrame(seconds: Float, g: SurgeGeometry): SurgeFrame {
    val t = seconds.coerceIn(0f, LIQUID_SURGE_SECONDS)
    val hw = g.width / 2; val hh = g.height / 2
    val phase = t * 30f
    if (t < SURGE_BUD) {
        // Swells out of the field edge and hangs a little below it.
        val m = 1 - (1 - t / SURGE_BUD).pow(3)
        val h = hh * BUD_HEIGHT * (.08f + .92f * m); val w = hw * BUD_WIDTH * (.3f + .7f * m)
        val top = g.source.y + hh * .3f * m
        return SurgeFrame(Offset(g.source.x, top + h), w, h, 0f, 1f, .03f * m, phase, 0f, 0f)
    }
    val budTop = g.source.y + hh * .3f
    val h0 = hh * BUD_HEIGHT; val w0 = hw * BUD_WIDTH
    if (t < SURGE_IMPACT) {
        // Gravity: the way grows with the square of the time; the mass narrows and stretches as it speeds up.
        val u = (t - SURGE_BUD) / (SURGE_IMPACT - SURGE_BUD)
        val h = h0 * (1 + .30f * u); val w = w0 * (1 - .12f * u)
        val y0 = budTop + h0
        val y = y0 + (g.floor - h - y0) * u * u
        val x = g.source.x + (g.target.x - g.source.x) * smooth(0f, 1f, u)
        return SurgeFrame(Offset(x, y), w, h, 0f, 1 - smooth(0f, TEAR, u), .035f * (1 - u) + .02f, phase, 0f, 0f)
    }
    val hImpact = h0 * 1.3f; val wImpact = w0 * .88f
    if (t < SURGE_RISE) {
        // Impact: it spreads flat on the edge, its lower side stays on it.
        val k = smooth(0f, 1f, (t - SURGE_IMPACT) / SURGE_SQUASH)
        val h = hImpact + (hh * FLAT - hImpact) * k; val w = wImpact + (hw * SPREAD - wImpact) * k
        return SurgeFrame(Offset(g.target.x, g.floor - h), w, h, 0f, 0f, .02f + .05f * k, phase, 0f, 0f)
    }
    // It springs up into the button's place (with a small overshoot) and sets like a jelly; the last
    // stretch is pulled exactly onto the button so the hand-over to the real one is seamless.
    val tau = t - SURGE_RISE
    val land = 1 - smooth(.28f, LIQUID_SURGE_SECONDS - SURGE_RISE, tau)
    val start = g.floor - hh * FLAT
    val y = g.target.y + (start - g.target.y) * damped(tau, 3.1f, .44f) * land
    val size = 1 - damped(tau, 4.2f, .36f) * land
    val w = hw * (SPREAD + (1 - SPREAD) * size); val h = hh * (FLAT + (1 - FLAT) * size)
    return SurgeFrame(Offset(g.target.x, y), w, h, smooth(.01f, .30f, tau), 0f,
        .07f * exp(-tau / .12f) * land, phase, smooth(.15f, LIQUID_SURGE_SECONDS - SURGE_RISE, tau), smooth(.05f, .34f, tau))
}

/**
 * A rounded rectangle with fully round ends ([w] × [h] half axes), at [fraction] of its outline from
 * the middle of the right side, going down first (clockwise on screen): the point and its outward normal.
 */
internal fun roundRectPoint(fraction: Float, w: Float, h: Float): Pair<Offset, Offset> {
    val r = min(w, h).coerceAtLeast(.001f)
    val fx = (w - r).coerceAtLeast(0f); val fy = (h - r).coerceAtLeast(0f)
    val quarter = PI.toFloat() * r / 2
    val total = 4 * fx + 4 * fy + 4 * quarter
    var s = (fraction - floor(fraction)) * total
    fun arc(base: Float, cx: Float, cy: Float): Pair<Offset, Offset> {
        val a = base + s / r; val c = cos(a); val n = sin(a)
        return Offset(cx + r * c, cy + r * n) to Offset(c, n)
    }
    if (s < fy) return Offset(w, s) to Offset(1f, 0f)
    s -= fy
    if (s < quarter) return arc(0f, fx, fy)
    s -= quarter
    if (s < 2 * fx) return Offset(fx - s, h) to Offset(0f, 1f)
    s -= 2 * fx
    if (s < quarter) return arc(PI.toFloat() / 2, -fx, fy)
    s -= quarter
    if (s < 2 * fy) return Offset(-w, fy - s) to Offset(-1f, 0f)
    s -= 2 * fy
    if (s < quarter) return arc(PI.toFloat(), -fx, -fy)
    s -= quarter
    if (s < 2 * fx) return Offset(-fx + s, -h) to Offset(0f, -1f)
    s -= 2 * fx
    if (s < quarter) return arc(PI.toFloat() * 1.5f, fx, -fy)
    s -= quarter
    return Offset(w, -fy + s) to Offset(1f, 0f)
}

/**
 * [count] points along a closed outline at equal arc lengths, relative to [center], starting next to
 * the middle of the right side and running clockwise on screen — the same order as [roundRectPoint],
 * so one can be morphed into the other point by point.
 */
internal fun evenOutline(path: android.graphics.Path, center: Offset, count: Int = SURGE_POINTS): List<Offset> {
    val measure = PathMeasure(path, true)
    val length = measure.length
    if (length <= 0f) return emptyList()
    val pos = FloatArray(2)
    var points = List(count) { i -> measure.getPosTan(length * i / count, pos, null); Offset(pos[0], pos[1]) - center }
    val signed = points.indices.sumOf { i -> val a = points[i]; val b = points[(i + 1) % count]; (a.x * b.y - b.x * a.y).toDouble() }
    if (signed < 0) points = points.asReversed()
    val start = points.indices.filter { points[it].x > 0f }.minByOrNull { abs(atan2(points[it].y, points[it].x)) } ?: 0
    return List(count) { points[(start + it) % count] }
}
internal const val SURGE_POINTS = 160

/** The mass at [frame] (without the neck), relative to its centre: liquid at first, then the [button] outline. */
internal fun surgePoints(frame: SurgeFrame, button: List<Offset>, buttonHalfWidth: Float, buttonHalfHeight: Float): List<Offset> {
    val n = if (button.isEmpty()) SURGE_POINTS else button.size
    val sx = frame.halfWidth / buttonHalfWidth; val sy = frame.halfHeight / buttonHalfHeight
    // Gentle swells along the long sides, scaled by the thickness of the mass (not its width).
    val amp = frame.wobble * min(frame.halfWidth, frame.halfHeight) * 2f
    return List(n) { i ->
        val f = i.toFloat() / n
        val (p, normal) = roundRectPoint(f, frame.halfWidth, frame.halfHeight)
        val ripple = amp * (sin(2 * PI.toFloat() * 3 * f + frame.phase) + .5f * sin(2 * PI.toFloat() * 5 * f - frame.phase * 1.3f))
        val liquid = p + normal * ripple
        if (button.isEmpty() || frame.morph <= 0f) liquid
        else { val b = Offset(button[i].x * sx, button[i].y * sy); liquid + (b - liquid) * frame.morph }
    }
}

/**
 * The whole outline at [frame]: the mass plus, while it still hangs from the field, a neck that thins,
 * waists and tears. Both run in the same direction, so one fill draws their union without seams.
 */
internal fun surgeOutline(frame: SurgeFrame, source: Offset, button: List<Offset>, buttonHalfWidth: Float, buttonHalfHeight: Float): Path {
    val path = Path()
    surgePoints(frame, button, buttonHalfWidth, buttonHalfHeight).forEachIndexed { i, p ->
        if (i == 0) path.moveTo(frame.center.x + p.x, frame.center.y + p.y) else path.lineTo(frame.center.x + p.x, frame.center.y + p.y)
    }
    path.close()
    val k = frame.neck
    if (k > .02f) {
        val w = frame.halfWidth
        val top = source.y; val bottom = frame.center.y - frame.halfHeight * .35f
        val length = (bottom - top).coerceAtLeast(1f)
        val topHalf = w * .5f * k.pow(.7f); val bottomHalf = w * .34f * k.pow(.5f); val waist = w * .16f * k.pow(1.4f)
        val cx = frame.center.x
        path.moveTo(source.x - topHalf, top)
        path.lineTo(source.x + topHalf, top)
        path.cubicTo(source.x + waist, top + length * .45f, cx + waist, bottom - length * .5f, cx + bottomHalf, bottom)
        path.lineTo(cx - bottomHalf, bottom)
        path.cubicTo(cx - waist, bottom - length * .5f, source.x - waist, top + length * .45f, source.x - topHalf, top)
        path.close()
    }
    return path
}

/** A droplet thrown aside at the impact: centre and radius, or null outside its short flight. */
internal fun surgeDroplet(i: Int, seconds: Float, g: SurgeGeometry): Pair<Offset, Float>? {
    val tau = seconds - SURGE_IMPACT - .01f
    if (tau < 0f || tau > .34f) return null
    val side = if (i % 2 == 0) -1f else 1f
    val k = i / 2
    val unit = g.height
    val speed = unit * (7.5f + 2.5f * k)
    val angle = (.30f + .13f * k) * PI.toFloat()
    val gravity = unit * 52f
    val x = g.target.x + side * (g.width * (.42f - .06f * k) + cos(angle) * speed * tau)
    val y = g.floor - unit * .12f - sin(angle) * speed * tau + .5f * gravity * tau * tau
    if (y > g.floor && tau > .05f) return null
    return Offset(x, y) to unit * (.10f - .02f * k) * (1 - tau / .34f * .6f)
}
