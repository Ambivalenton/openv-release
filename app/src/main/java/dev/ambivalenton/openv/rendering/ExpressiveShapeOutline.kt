// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import dev.ambivalenton.openv.data.model.IconStyle
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Ten favourite shapes in the spirit of Material 3 Expressive – soft polygons, rays,
 * petals and playful silhouettes. Own geometry (no copied shape data): each outline is a closed
 * polygon in the unit square (0…1, y downwards), centred and scaled so its longer side fills it.
 *
 * The same numbers drive the app's masks ([dev.ambivalenton.openv.widget.GlanceThemeHelper.shapePath])
 * and, through tools/generate_favorite_shapes.py, the widget drawables; Build74Test compares both.
 */
object ExpressiveShapeOutline {
    val shapes: List<IconStyle> = listOf(
        IconStyle.PENTAGON, IconStyle.GEM, IconStyle.ARCH, IconStyle.SLANTED, IconStyle.SUNNY,
        IconStyle.FLOWER, IconStyle.BURST, IconStyle.HEART, IconStyle.GHOST, IconStyle.PIXEL,
    )

    private class P(val x: Double, val y: Double)

    private val cache = java.util.concurrent.ConcurrentHashMap<IconStyle, List<Pair<Double, Double>>>()

    /** Outline of one of [shapes] (computed once); null for the older shapes, which keep their own geometry. */
    fun points(shape: IconStyle): List<Pair<Double, Double>>? =
        cache[shape] ?: compute(shape)?.also { cache[shape] = it }

    private fun compute(shape: IconStyle): List<Pair<Double, Double>>? = when (shape) {
        IconStyle.PENTAGON -> fit(rounded(regular(5, 0.0), List(5) { .26 }))
        IconStyle.GEM -> fit(rounded(listOf(P(-.6, -.8), P(.6, -.8), P(1.0, -.25), P(0.0, 1.0), P(-1.0, -.25)),
            listOf(.18, .18, .2, .26, .2)))
        IconStyle.ARCH -> {
            val dome = (0..24).map { k -> val a = PI + PI * k / 24; P(cos(a), sin(a)) }
            fit(rounded(dome + listOf(P(1.0, 1.0), P(-1.0, 1.0)), List(dome.size) { 0.0 } + listOf(.3, .3)))
        }
        IconStyle.SLANTED -> fit(rounded(listOf(P(-.85, -1.0), P(1.15, -1.0), P(.85, 1.0), P(-1.15, 1.0)), List(4) { .5 }))
        IconStyle.SUNNY -> fit(rounded(star(8, 1.0, .8), List(16) { if (it % 2 == 0) .14 else .1 }, steps = 8))
        IconStyle.FLOWER -> fit(petals(8, .62, .38))
        IconStyle.BURST -> fit(rounded(star(12, 1.0, .78), List(24) { if (it % 2 == 0) .05 else .04 }, steps = 6))
        IconStyle.HEART -> fit(heart())
        IconStyle.GHOST -> fit(ghost())
        IconStyle.PIXEL -> fit(pixelCircle())
        else -> null
    }

    private fun regular(corners: Int, offset: Double) = (0 until corners).map { i ->
        val a = -PI / 2 + offset + 2 * PI * i / corners; P(cos(a), sin(a))
    }

    private fun star(points: Int, outer: Double, inner: Double) = (0 until points * 2).map { i ->
        val a = -PI / 2 + PI * i / points; val r = if (i % 2 == 0) outer else inner; P(r * cos(a), r * sin(a))
    }

    /** Polygon with circular fillets of [radii] (0 keeps the corner); also rounds concave corners. */
    private fun rounded(vertices: List<P>, radii: List<Double>, steps: Int = 10): List<P> {
        val n = vertices.size
        val out = ArrayList<P>()
        for (i in 0 until n) {
            val v = vertices[i]; val prev = vertices[(i - 1 + n) % n]; val next = vertices[(i + 1) % n]
            val r = radii[i]
            if (r <= 0.0) { out += v; continue }
            val ax = prev.x - v.x; val ay = prev.y - v.y; val la = hypot(ax, ay)
            val bx = next.x - v.x; val by = next.y - v.y; val lb = hypot(bx, by)
            val ux = ax / la; val uy = ay / la; val wx = bx / lb; val wy = by / lb
            val theta = acos((ux * wx + uy * wy).coerceIn(-1.0, 1.0))
            if (theta < 1e-6 || theta > PI - 1e-6) { out += v; continue }
            var t = r / tan(theta / 2)
            val limit = min(la, lb) / 2
            val radius = if (t > limit) { t = limit; limit * tan(theta / 2) } else r
            val sx = ux + wx; val sy = uy + wy; val sl = hypot(sx, sy)
            val d = radius / sin(theta / 2)
            val cx = v.x + sx / sl * d; val cy = v.y + sy / sl * d
            val a1 = atan2(v.y + uy * t - cy, v.x + ux * t - cx)
            val a2 = atan2(v.y + wy * t - cy, v.x + wx * t - cx)
            var delta = a2 - a1
            while (delta > PI) delta -= 2 * PI
            while (delta < -PI) delta += 2 * PI
            for (k in 0..steps) { val a = a1 + delta * k / steps; out += P(cx + radius * cos(a), cy + radius * sin(a)) }
        }
        return out
    }

    /** Round petals: the union of [count] circles, the notches between them softened (log-sum-exp). */
    private fun petals(count: Int, distance: Double, radius: Double): List<P> = (0 until 180).map { j ->
        val a = -PI / 2 + 2 * PI * j / 180
        var sum = 0.0
        for (i in 0 until count) {
            val along = distance * cos(a - (-PI / 2 + 2 * PI * i / count))
            val disc = radius * radius - (distance * distance - along * along)
            if (disc >= 0) sum += exp(40 * (along + sqrt(disc)))
        }
        val r = ln(sum) / 40
        P(r * cos(a), r * sin(a))
    }

    /** Two lobes and a softly rounded tip; the notch between the lobes stays crisp. */
    private fun heart(): List<P> {
        val cx = .5; val cy = -.3; val r = .52; val tipY = .95
        val notchY = cy - sqrt(r * r - cx * cx)
        val start = atan2(notchY - cy, -cx)
        val d = hypot(cx, tipY - cy)
        val end = atan2(tipY - cy, -cx) - acos(r / d)
        val right = (0..40).map { k -> val a = start + (end - start) * k / 40; P(cx + r * cos(a), cy + r * sin(a)) }
        val left = right.reversed().dropLast(1).map { P(-it.x, it.y) }
        val outline = right + P(0.0, tipY) + left
        return rounded(outline, outline.map { if (it.x == 0.0 && it.y == tipY) .2 else 0.0 })
    }

    /** A dome over three round lobes. */
    private fun ghost(): List<P> {
        val dome = (0..24).map { k -> val a = PI + PI * k / 24; P(cos(a), sin(a)) }
        val hem = .62; val lobe = 1.0 / 3
        val lobes = (0 until 3).flatMap { i ->
            val center = 1 - (2 * i + 1) * lobe
            (0..12).drop(if (i == 0) 0 else 1).map { k -> val a = PI * k / 12; P(center + lobe * cos(a), hem + lobe * sin(a)) }
        }
        return dome + lobes
    }

    /** A circle on an 8 × 8 pixel grid. */
    private fun pixelCircle(): List<P> {
        val widths = listOf(.5, .75, 1.0, 1.0, 1.0, 1.0, .75, .5)
        val raw = ArrayList<P>()
        widths.forEachIndexed { j, w -> raw += P(w, -1 + .25 * j); raw += P(w, -1 + .25 * (j + 1)) }
        widths.indices.reversed().forEach { j -> val w = widths[j]; raw += P(-w, -1 + .25 * (j + 1)); raw += P(-w, -1 + .25 * j) }
        return raw.filterIndexed { i, p -> val q = raw[(i - 1 + raw.size) % raw.size]; q.x != p.x || q.y != p.y }
    }

    private fun fit(points: List<P>): List<Pair<Double, Double>> {
        val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
        val scale = 1.0 / max(maxX - minX, maxY - minY)
        val ox = (1 - (maxX - minX) * scale) / 2 - minX * scale
        val oy = (1 - (maxY - minY) * scale) / 2 - minY * scale
        return points.map { (it.x * scale + ox) to (it.y * scale + oy) }
    }
}
