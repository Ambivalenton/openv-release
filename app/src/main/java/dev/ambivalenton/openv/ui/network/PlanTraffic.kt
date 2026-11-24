// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.exp

/**
 * Build 119 · where two lines of the plan cross. Lines are drawn in list order, so the later one lies on
 * top ([upper]); its vehicles cross over, the vehicles of the [lower] line pass underneath. Distances are
 * measured along each line's own track (from its node a), [heading] is the upper line's direction there.
 */
internal data class LineCrossing(val lower: String, val upper: String, val lowerAt: Float, val upperAt: Float,
    val point: Offset, val heading: Offset)

/** A line sampled along its track: points and the distance of each point from the start. */
internal class SampledTrack(val points: List<Offset>, val distances: List<Float>)

/** Samples a track every [step] px (at least two points); [at] gives the point at a distance. */
internal fun sampleTrack(length: Float, step: Float, at: (Float) -> Offset): SampledTrack {
    val n = (length / step.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
    val distances = List(n + 1) { i -> length * i / n }
    return SampledTrack(distances.map(at), distances)
}

/**
 * All crossings between the tracks in [order] (draw order: later = on top). Shared end points do not
 * count – lines meet in their nodes, which are drawn above them. [skip] px at both ends of every track
 * are left out for that reason.
 */
internal fun lineCrossings(order: List<String>, tracks: Map<String, SampledTrack>, skip: Float): List<LineCrossing> {
    val result = mutableListOf<LineCrossing>()
    for (i in order.indices) for (j in i + 1 until order.size) {
        val a = tracks[order[i]] ?: continue
        val b = tracks[order[j]] ?: continue
        if (!boxesMeet(a.points, b.points)) continue
        val lengthA = a.distances.last(); val lengthB = b.distances.last()
        for (p in 0 until a.points.size - 1) {
            val p0 = a.points[p]; val p1 = a.points[p + 1]
            for (q in 0 until b.points.size - 1) {
                val q0 = b.points[q]; val q1 = b.points[q + 1]
                val (s, t) = segmentHit(p0, p1, q0, q1) ?: continue
                val da = a.distances[p] + (a.distances[p + 1] - a.distances[p]) * s
                val db = b.distances[q] + (b.distances[q + 1] - b.distances[q]) * t
                if (da < skip || da > lengthA - skip || db < skip || db > lengthB - skip) continue
                // A crossing found twice (on a shared sample point) counts once.
                if (result.any { it.lower == order[i] && it.upper == order[j] && abs(it.lowerAt - da) < skip }) continue
                val d = q1 - q0; val len = d.getDistance().coerceAtLeast(1e-3f)
                result += LineCrossing(order[i], order[j], da, db, p0 + (p1 - p0) * s, Offset(d.x / len, d.y / len))
            }
        }
    }
    return result
}

private fun boxesMeet(a: List<Offset>, b: List<Offset>): Boolean =
    a.minOf { it.x } <= b.maxOf { it.x } && b.minOf { it.x } <= a.maxOf { it.x } &&
        a.minOf { it.y } <= b.maxOf { it.y } && b.minOf { it.y } <= a.maxOf { it.y }

/** Parameters (s, t) in 0..1 where two segments cross, or null (parallel or apart). */
private fun segmentHit(p0: Offset, p1: Offset, q0: Offset, q1: Offset): Pair<Float, Float>? {
    val r = p1 - p0; val d = q1 - q0
    val denominator = r.x * d.y - r.y * d.x
    if (abs(denominator) < 1e-6f) return null
    val w = q0 - p0
    val s = (w.x * d.y - w.y * d.x) / denominator
    val t = (w.x * r.y - w.y * r.x) / denominator
    return if (s in 0f..1f && t in 0f..1f) s to t else null
}

/**
 * One vehicle run for the traffic control: its line, the length of its body (px) and where its front
 * is at a given clock of its own (null while it waits in a node). [forward]: it drives from node a.
 */
internal class TrafficRun(val key: String, val edgeId: String, val forward: Boolean, val body: Float, val frontAt: (Float) -> Float?)

/**
 * Build 119 · vehicles do not meet at a crossing. Every run has a clock of its own; when two runs would
 * occupy the same crossing at once, the one that would get there later (on a tie: the one on the lower
 * line) brakes softly before it and speeds up again once the other has passed. Plain state, advanced
 * from the drawing; nothing here starts a frame of its own.
 */
internal class PlanTraffic {
    private val clocks = HashMap<String, Float>()
    private val rates = HashMap<String, Float>()
    private var last: Float? = null

    /** The run's own clock (the shared clock until it ever had to wait). */
    fun clock(key: String, shared: Float): Float = clocks[key] ?: shared

    fun rate(key: String): Float = rates[key] ?: 1f

    /**
     * Advances every run to the shared clock [shared]. [zone] px around a crossing count as occupied
     * (half the upper line plus a little room), [horizon] s are looked ahead.
     */
    fun advance(shared: Float, runs: List<TrafficRun>, crossings: List<LineCrossing>, zone: Float, horizon: Float = 1.2f) {
        val previous = last
        last = shared
        if (previous == null || shared < previous) { runs.forEach { clocks[it.key] = shared; rates[it.key] = 1f }; return }
        val dt = (shared - previous).coerceAtMost(.25f)
        if (dt <= 0f) return
        runs.forEach { if (it.key !in clocks) clocks[it.key] = shared }
        val targets = runs.associate { it.key to 1f }.toMutableMap()
        val byEdge = runs.groupBy { it.edgeId }
        for (crossing in crossings) {
            val lower = byEdge[crossing.lower].orEmpty(); val upper = byEdge[crossing.upper].orEmpty()
            for (a in lower) for (b in upper) {
                val wa = window(a, crossing.lowerAt, zone, horizon) ?: continue
                val wb = window(b, crossing.upperAt, zone, horizon) ?: continue
                if (wa.first > wb.second || wb.first > wa.second) continue
                // The later one yields; already inside, nobody stops in the middle of a crossing.
                val yielder = when {
                    wa.first <= 0f -> b
                    wb.first <= 0f -> a
                    wb.first < wa.first -> a
                    else -> if (wa.first < wb.first) b else a
                }
                val entry = if (yielder === a) wa.first else wb.first
                // Close to the crossing it nearly stops; further away it only eases off.
                val target = if (entry < .35f) .08f else (.25f + .5f * ((entry - .35f) / (horizon - .35f)).coerceIn(0f, 1f))
                targets[yielder.key] = minOf(targets.getValue(yielder.key), target)
            }
        }
        // Soft brakes and soft starts (a quarter second), never a jump.
        val blend = 1f - exp(-dt / .22f)
        runs.forEach { run ->
            val rate = rates[run.key] ?: 1f
            val next = rate + (targets.getValue(run.key) - rate) * blend
            rates[run.key] = next
            clocks[run.key] = clocks.getValue(run.key) + dt * next
        }
    }

    /** Seconds from now until [run]'s body enters and leaves the zone around [at] (at full speed), or null. */
    private fun window(run: TrafficRun, at: Float, zone: Float, horizon: Float): Pair<Float, Float>? {
        val now = clocks[run.key] ?: return null
        var enter: Float? = null
        var leave: Float? = null
        val step = .04f
        var s = 0f
        while (s <= horizon + 1.2f) {
            val inside = occupies(run, run.frontAt(now + s), at, zone)
            if (inside && enter == null) { if (s > horizon) return null; enter = s }
            if (!inside && enter != null) { leave = s; break }
            s += step
        }
        val from = enter ?: return null
        return from to (leave ?: (horizon + 1.2f))
    }

    private fun occupies(run: TrafficRun, front: Float?, at: Float, zone: Float): Boolean {
        front ?: return false
        val tail = if (run.forward) front - run.body else front + run.body
        val lo = minOf(front, tail) - zone; val hi = maxOf(front, tail) + zone
        return at in lo..hi
    }
}

/** The band of [crossing]'s upper line where a lower vehicle is hidden: [along] the upper line, [across] it (half widths). */
internal fun androidx.compose.ui.graphics.Path.addUnderpass(crossing: LineCrossing, along: Float, across: Float) {
    val h = crossing.heading; val n = Offset(-h.y, h.x)
    val p = crossing.point
    val a = h * along; val b = n * across
    moveTo((p - a - b).x, (p - a - b).y)
    lineTo((p + a - b).x, (p + a - b).y)
    lineTo((p + a + b).x, (p + a + b).y)
    lineTo((p - a + b).x, (p - a + b).y)
    close()
}
