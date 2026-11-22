// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/*
 * Motion helpers of the network plan. Pure geometry; Compose only animates the parameters.
 *  - RopeSimulation: a Verlet rope with gravity. In equilibrium it takes the catenary shape of a
 *    hanging rope, so no per-frame cosh solution is needed.
 *  - rippleOffsets: a Gaussian wave packet travelling along a line (CPU, every API level; no shader).
 */

class RopeSimulation(private val segments: Int = 14) {
    val points = Array(segments + 1) { Offset.Zero }
    private val previous = Array(segments + 1) { Offset.Zero }
    fun reset(from: Offset) { points.fill(from); previous.fill(from) }

    /** dt ≤ 1/30 s, lengths in px. A rope, not a rod: constraints only limit stretching. */
    fun step(dt: Float, anchor: Offset, tip: Offset, gravity: Float, minSag: Float, damping: Float = .985f) {
        val t = dt.coerceIn(0f, 1f / 30f)
        val chord = (tip - anchor).getDistance()
        val rest = maxOf(chord * 1.12f, chord + minSag) / segments
        for (i in 1 until segments) {
            val v = (points[i] - previous[i]) * damping
            previous[i] = points[i]
            points[i] = points[i] + v + Offset(0f, gravity * t * t)
        }
        points[0] = anchor; points[segments] = tip
        repeat(6) {
            for (i in 0 until segments) {
                val d = points[i + 1] - points[i]; val len = d.getDistance()
                if (len <= rest || len == 0f) continue
                val c = d * ((len - rest) / len)
                val firstFixed = i == 0; val secondFixed = i + 1 == segments
                when {
                    firstFixed && !secondFixed -> points[i + 1] -= c
                    !firstFixed && secondFixed -> points[i] += c
                    !firstFixed -> { points[i] += c / 2f; points[i + 1] -= c / 2f }
                }
            }
        }
    }
}

/** Smooth path through points (Catmull-Rom as cubic Bézier). */
fun smoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    for (i in 0 until points.lastIndex) {
        val p0 = points[(i - 1).coerceAtLeast(0)]; val p1 = points[i]; val p2 = points[i + 1]; val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]
        val c1 = p1 + (p2 - p0) / 6f; val c2 = p2 - (p3 - p1) / 6f
        cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
}

/** Octilinear corner path with rounded bends, like a metro map. */
fun roundedPath(points: List<Offset>, radius: Float): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.lastIndex) {
        val p = points[i]; val a = points[i - 1]; val b = points[i + 1]
        val da = (a - p).getDistance(); val db = (b - p).getDistance()
        val r = min(radius, min(da, db) / 2f)
        if (r <= 0f || da == 0f || db == 0f) { lineTo(p.x, p.y); continue }
        val s = p + (a - p) * (r / da); val e = p + (b - p) * (r / db)
        lineTo(s.x, s.y); quadraticTo(p.x, p.y, e.x, e.y)
    }
    lineTo(points.last().x, points.last().y)
}

/**
 * Ripple: δ(s,t) = A·exp(−((s−c)/σ)²)·sin(2π(s−c)/λ)·√(1−t), c(t) = −σ + (L+2σ)·ease(t).
 * Returns the deformed samples and the brightness of the band (0…1) per sample.
 */
fun rippleOffsets(samples: List<Offset>, progress: Float, amplitude: Float, sigma: Float, wavelength: Float): List<Pair<Offset, Float>> {
    if (samples.size < 2) return samples.map { it to 0f }
    val lengths = FloatArray(samples.size)
    for (i in 1 until samples.size) lengths[i] = lengths[i - 1] + (samples[i] - samples[i - 1]).getDistance()
    val total = lengths.last()
    val t = progress.coerceIn(0f, 1f)
    val eased = 1f - (1f - t) * (1f - t) * (1f - t)
    val center = -sigma + (total + 2 * sigma) * eased
    val fade = kotlin.math.sqrt(1f - t)
    return samples.indices.map { i ->
        val prev = samples[(i - 1).coerceAtLeast(0)]; val next = samples[(i + 1).coerceAtMost(samples.lastIndex)]
        val tangent = (next - prev).let { val l = it.getDistance(); if (l > 0f) it / l else Offset(1f, 0f) }
        val normal = Offset(-tangent.y, tangent.x)
        val u = (lengths[i] - center) / sigma
        val envelope = exp(-u * u)
        val wave = sin(2 * PI * (lengths[i] - center) / wavelength).toFloat()
        (samples[i] + normal * (amplitude * envelope * wave * fade)) to envelope * fade
    }
}
