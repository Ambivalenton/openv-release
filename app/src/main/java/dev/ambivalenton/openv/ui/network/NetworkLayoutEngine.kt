// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.ui.geometry.Offset
import dev.ambivalenton.openv.data.model.GridPoint
import dev.ambivalenton.openv.data.model.NETWORK_GRID_SIZE
import dev.ambivalenton.openv.data.model.NetworkEdge
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Octilinear line layout of the network plan: every segment runs at 0°, 45° or 90°. Lines never
 * pass through other nodes. Most lines need one bend and take a fast path; otherwise an A* search
 * on the 12 × 12 lattice minimises length, bends, overlaps and crossings. Lines are routed in list
 * order and reserve their segments, so the same network always gives the same picture.
 */

private val DIRS = arrayOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
private const val SQRT2 = 1.41421356
private const val N = NETWORK_GRID_SIZE

internal fun stepKey(a: GridPoint, b: GridPoint): Long {
    val i = a.x * N + a.y; val j = b.x * N + b.y
    return if (i < j) i * 1000L + j else j * 1000L + i
}

/** Segments, diagonal cells and points already used by earlier lines. */
internal class Occupancy {
    val segments = HashSet<Long>()
    val diagonals = HashMap<Int, Int>()
    val points = HashSet<Int>()
    fun mark(path: List<GridPoint>) {
        val full = rasterize(path)
        full.zipWithNext().forEach { (a, b) ->
            segments += stepKey(a, b)
            if (a.x != b.x && a.y != b.y) diagonals[min(a.x, b.x) * N + min(a.y, b.y)] = if ((b.x - a.x) * (b.y - a.y) > 0) 0 else 1
        }
        full.drop(1).dropLast(1).forEach { points += it.x * N + it.y }
    }
}

/** All lattice points along an octilinear corner path. */
internal fun rasterize(path: List<GridPoint>): List<GridPoint> {
    if (path.size < 2) return path
    val out = mutableListOf(path.first())
    path.zipWithNext().forEach { (a, b) ->
        val dx = Integer.signum(b.x - a.x); val dy = Integer.signum(b.y - a.y)
        var p = a
        while (p != b) { p = GridPoint(p.x + dx, p.y + dy); out += p }
    }
    return out
}

/** Keeps only the corner points of a lattice path. */
internal fun corners(full: List<GridPoint>): List<GridPoint> {
    if (full.size <= 2) return full
    val out = mutableListOf(full.first())
    for (i in 1 until full.lastIndex) {
        val a = full[i - 1]; val p = full[i]; val b = full[i + 1]
        if (p.x - a.x != b.x - p.x || p.y - a.y != b.y - p.y) out += p
    }
    out += full.last()
    return out
}

/** The two one-bend octilinear paths (straight lines have one). */
fun octilinearCandidates(a: GridPoint, b: GridPoint): List<List<GridPoint>> {
    val dx = b.x - a.x; val dy = b.y - a.y
    if (dx == 0 || dy == 0 || abs(dx) == abs(dy)) return listOf(listOf(a, b))
    val d = min(abs(dx), abs(dy)); val sx = Integer.signum(dx); val sy = Integer.signum(dy)
    val diagonalFirst = GridPoint(a.x + sx * d, a.y + sy * d)
    val straightFirst = if (abs(dx) > abs(dy)) GridPoint(b.x - sx * d, a.y) else GridPoint(a.x, b.y - sy * d)
    return listOf(listOf(a, diagonalFirst, b), listOf(a, straightFirst, b))
}

private fun turnCost(previous: Int, next: Int): Double? {
    if (previous < 0) return 0.0
    val diff = abs(previous - next).let { min(it, 8 - it) }
    return when (diff) { 0 -> 0.0; 1 -> .5; 2 -> 1.5; else -> null }
}

/** Cost of one lattice step; null = forbidden (through a foreign node or a sharp turn). */
private fun stepCost(p: GridPoint, dir: Int, previousDir: Int, target: GridPoint, nodes: Set<Int>, used: Occupancy, columns: Int, rows: Int): Double? {
    val (dx, dy) = DIRS[dir]
    val q = GridPoint(p.x + dx, p.y + dy)
    if (!q.within(columns, rows)) return null
    if (q != target && (q.x * N + q.y) in nodes) return null
    val turn = turnCost(previousDir, dir) ?: return null
    var cost = (if (dx != 0 && dy != 0) SQRT2 else 1.0) + turn
    if (dx != 0 && dy != 0) {
        // A diagonal passes 0.7 grid units from the cell's other corners: avoid passing nodes there.
        if ((p.x + dx) * N + p.y in nodes || p.x * N + (p.y + dy) in nodes) cost += 4.0
        val cell = min(p.x, q.x) * N + min(p.y, q.y)
        val type = if (dx * dy > 0) 0 else 1
        used.diagonals[cell]?.let { if (it != type) cost += 1.0 }
    }
    if (stepKey(p, q) in used.segments) cost += 3.0
    if (q != target && (q.x * N + q.y) in used.points) cost += .5
    return cost
}

private fun pathCost(path: List<GridPoint>, target: GridPoint, nodes: Set<Int>, used: Occupancy, columns: Int, rows: Int): Double? {
    val full = rasterize(path)
    var previous = -1; var total = 0.0
    for ((a, b) in full.zipWithNext()) {
        val dir = DIRS.indexOfFirst { it.first == Integer.signum(b.x - a.x) && it.second == Integer.signum(b.y - a.y) }
        total += stepCost(a, dir, previous, target, nodes, used, columns, rows) ?: return null
        previous = dir
    }
    return total
}

private fun idealCost(a: GridPoint, b: GridPoint): Double {
    val dx = abs(a.x - b.x); val dy = abs(a.y - b.y)
    return max(dx, dy) - min(dx, dy) + SQRT2 * min(dx, dy)
}

/** A* on (point, incoming direction); deterministic tie-breaking. */
internal fun searchRoute(a: GridPoint, b: GridPoint, nodes: Set<Int>, used: Occupancy,
                         columns: Int = NETWORK_GRID_SIZE, rows: Int = NETWORK_GRID_SIZE): List<GridPoint>? {
    val states = N * N * 9
    val best = DoubleArray(states) { Double.MAX_VALUE }
    val from = IntArray(states) { -1 }
    fun index(p: GridPoint, dir: Int) = (p.x * N + p.y) * 9 + (dir + 1)
    data class Item(val f: Double, val g: Double, val state: Int)
    val open = PriorityQueue<Item>(compareBy<Item>({ it.f }, { it.g }, { it.state }))
    val start = index(a, -1); best[start] = 0.0; open += Item(idealCost(a, b), 0.0, start)
    while (open.isNotEmpty()) {
        val item = open.poll()
        if (item.g > best[item.state]) continue
        val cell = item.state / 9; val dir = item.state % 9 - 1
        val p = GridPoint(cell / N, cell % N)
        if (p == b) {
            val out = mutableListOf<GridPoint>()
            var s = item.state
            while (s >= 0) { val c = s / 9; out += GridPoint(c / N, c % N); s = from[s] }
            return corners(out.reversed())
        }
        for (d in DIRS.indices) {
            val cost = stepCost(p, d, dir, b, nodes, used, columns, rows) ?: continue
            val q = GridPoint(p.x + DIRS[d].first, p.y + DIRS[d].second)
            val next = index(q, d); val g = item.g + cost
            if (g < best[next] - 1e-9) { best[next] = g; from[next] = item.state; open += Item(g + idealCost(q, b), g, next) }
        }
    }
    return null
}

/** Corner paths of all lines whose two nodes are placed, in list order. */
fun routeNetwork(placed: Map<String, GridPoint>, edges: List<NetworkEdge>,
                 columns: Int = NETWORK_GRID_SIZE, rows: Int = NETWORK_GRID_SIZE): Map<String, List<GridPoint>> {
    val nodePoints = placed.values.map { it.x * N + it.y }.toSet()
    val used = Occupancy()
    val result = LinkedHashMap<String, List<GridPoint>>()
    edges.forEach { edge ->
        val a = placed[edge.aId] ?: return@forEach
        val b = placed[edge.bId] ?: return@forEach
        val others = nodePoints - (a.x * N + a.y) - (b.x * N + b.y)
        val fast = octilinearCandidates(a, b).mapNotNull { c -> pathCost(c, b, others, used, columns, rows)?.let { c to it } }
            .minByOrNull { it.second }?.takeIf { it.second <= idealCost(a, b) + 1.5 + 1e-9 }?.first
        val path = fast ?: searchRoute(a, b, others, used, columns, rows) ?: listOf(a, b)
        used.mark(path)
        result[edge.id] = path
    }
    return result
}

/** Screen geometry of the plan: grid point centres and the inverse lookup. The plan fills
 * [side] in width; a tall grid (3 × 12) is limited to 1.3 × [side] in height and centred. */
class NetworkGeometry(val side: Float, val columns: Int = N, val rows: Int = N) {
    val pitch = min(side / columns, side * 1.3f / rows)
    val width = pitch * columns
    val height = pitch * rows
    fun center(p: GridPoint) = Offset((p.x + .5f) * pitch, (p.y + .5f) * pitch)
    /** Nearest grid point to [o] if it lies within [tolerance] grid units of it. */
    fun pointNear(o: Offset, tolerance: Float = .6f): GridPoint? {
        val x = (o.x / pitch - .5f).let { kotlin.math.round(it).toInt() }
        val y = (o.y / pitch - .5f).let { kotlin.math.round(it).toInt() }
        val p = GridPoint(x, y)
        if (!p.within(columns, rows)) return null
        return p.takeIf { (center(p) - o).getDistance() <= tolerance * pitch }
    }
}

fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val length = ab.x * ab.x + ab.y * ab.y
    if (length <= 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / length).coerceIn(0f, 1f)
    return hypot(p.x - (a.x + ab.x * t), p.y - (a.y + ab.y * t))
}

fun distanceToPolyline(p: Offset, points: List<Offset>): Float =
    if (points.size < 2) points.firstOrNull()?.let { (p - it).getDistance() } ?: Float.MAX_VALUE
    else points.zipWithNext().minOf { (a, b) -> distanceToSegment(p, a, b) }

/** Resamples a polyline to [count] points spaced evenly along its length. */
fun resample(points: List<Offset>, count: Int): List<Offset> {
    if (points.size < 2 || count < 2) return List(count.coerceAtLeast(1)) { points.firstOrNull() ?: Offset.Zero }
    val lengths = points.zipWithNext().map { (a, b) -> (b - a).getDistance() }
    val total = lengths.sum().takeIf { it > 0f } ?: return List(count) { points.first() }
    return List(count) { i ->
        var target = total * i / (count - 1)
        var k = 0
        while (k < lengths.lastIndex && target > lengths[k]) { target -= lengths[k]; k++ }
        val f = if (lengths[k] > 0f) (target / lengths[k]).coerceIn(0f, 1f) else 0f
        points[k] + (points[k + 1] - points[k]) * f
    }
}

/** Octile length of a route in grid units; used by tests and the ripple duration. */
fun routeLength(path: List<GridPoint>): Double = path.zipWithNext().sumOf { (a, b) ->
    val dx = abs(a.x - b.x); val dy = abs(a.y - b.y); if (dx == dy) dx * SQRT2 else max(dx, dy).toDouble()
}

internal fun sqrtSafe(v: Float) = sqrt(v.coerceAtLeast(0f))

/** Text width of a name plate (build 69): the usual [base], widened to the longest word so that no
 * word is split in the middle ("Entsorgun|g"), but never wider than [widest]. */
internal fun nameTextWidth(label: String, base: Float, widest: Float, wordWidth: (String) -> Int): Int {
    val longest = label.split(' ', '-', '/').filter { it.isNotEmpty() }.maxOfOrNull(wordWidth) ?: 0
    return max(base, longest + 1f).coerceAtMost(widest).toInt().coerceAtLeast(1)
}

/** A name plate of [size] for the node at [center] with the visible [radius]. */
internal data class NetworkLabelBox(val id: String, val center: Offset, val radius: Float, val size: androidx.compose.ui.geometry.Size)

/**
 * Places every name plate (top-left corners): below, above, right or left of its node, inside the
 * plan. The first place that covers no other plate and no other node wins; otherwise the place that
 * covers least. Every name is shown in full; in the order given (anchor and highlighted nodes first).
 */
internal fun placeNetworkLabels(boxes: List<NetworkLabelBox>, bounds: androidx.compose.ui.geometry.Size, gap: Float): Map<String, Offset> {
    val taken = mutableListOf<androidx.compose.ui.geometry.Rect>()
    val out = LinkedHashMap<String, Offset>()
    fun overlap(a: androidx.compose.ui.geometry.Rect, b: androidx.compose.ui.geometry.Rect): Float {
        val w = min(a.right, b.right) - max(a.left, b.left); val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        return if (w > 0f && h > 0f) w * h else 0f
    }
    boxes.forEach { box ->
        val (w, h) = box.size.width to box.size.height
        val c = box.center; val r = box.radius + gap
        val candidates = listOf(Offset(c.x - w / 2, c.y + r), Offset(c.x - w / 2, c.y - r - h),
            Offset(c.x + r, c.y - h / 2), Offset(c.x - r - w, c.y - h / 2)).map { o ->
            Offset(o.x.coerceIn(0f, (bounds.width - w).coerceAtLeast(0f)), o.y.coerceIn(0f, (bounds.height - h).coerceAtLeast(0f)))
        }
        val others = boxes.filter { it.id != box.id }.map { androidx.compose.ui.geometry.Rect(it.center, it.radius) }
        val own = androidx.compose.ui.geometry.Rect(c, box.radius)
        val best = candidates.minBy { o ->
            val rect = androidx.compose.ui.geometry.Rect(o, box.size)
            // A plate must never hide its own node; other plates count fully, other nodes a little less.
            overlap(rect, own) * 4f + taken.sumOf { overlap(rect, it).toDouble() }.toFloat() + others.sumOf { overlap(rect, it).toDouble() }.toFloat() * .8f
        }
        taken += androidx.compose.ui.geometry.Rect(best, box.size)
        out[box.id] = best
    }
    return out
}

/** A departure chip of [size] for the line whose polyline leaves its node along [path] (node first). */
internal data class NetworkChipRequest(val id: String, val path: List<Offset>, val size: androidx.compose.ui.geometry.Size)

/** Point and unit direction at [distance] along [points] (clamped to the polyline). */
internal fun pointAlong(points: List<Offset>, distance: Float): Pair<Offset, Offset> {
    if (points.size < 2) return (points.firstOrNull() ?: Offset.Zero) to Offset(1f, 0f)
    var left = distance.coerceAtLeast(0f)
    for (i in 0 until points.lastIndex) {
        val a = points[i]; val b = points[i + 1]
        val length = (b - a).getDistance()
        if (length <= 0f) continue
        val dir = (b - a) / length
        if (left <= length || i == points.lastIndex - 1) return (a + dir * left.coerceAtMost(length)) to dir
        left -= length
    }
    return points.last() to Offset(1f, 0f)
}

/**
 * Centres of the departure chips (build 69). Each chip sits on its own line, as close to its node as
 * possible (at most [reach] and 60 % of the line away) without covering another chip or any node. Lines that leave a node side by side used to
 * stack their chips at the same distance; now a chip slides further along its line, then sideways
 * off it, and only where nothing is free takes the place that covers least. Chips stay inside the plan.
 */
internal fun placeNetworkChips(chips: List<NetworkChipRequest>, nodes: List<androidx.compose.ui.geometry.Rect>,
                               bounds: androidx.compose.ui.geometry.Size, step: Float, gap: Float, reach: Float): Map<String, Offset> {
    val taken = mutableListOf<androidx.compose.ui.geometry.Rect>()
    val out = LinkedHashMap<String, Offset>()
    fun overlap(a: androidx.compose.ui.geometry.Rect, b: androidx.compose.ui.geometry.Rect): Float {
        val w = min(a.right, b.right) - max(a.left, b.left); val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        return if (w > 0f && h > 0f) w * h else 0f
    }
    chips.forEach { chip ->
        val (w, h) = chip.size.width to chip.size.height
        val length = chip.path.zipWithNext().sumOf { (a, b) -> (b - a).getDistance().toDouble() }.toFloat()
        fun rectAt(center: Offset): androidx.compose.ui.geometry.Rect {
            val x = (center.x - w / 2).coerceIn(0f, (bounds.width - w).coerceAtLeast(0f))
            val y = (center.y - h / 2).coerceIn(0f, (bounds.height - h).coerceAtLeast(0f))
            return androidx.compose.ui.geometry.Rect(Offset(x, y), chip.size)
        }
        // Grown by the gap, so neighbouring chips never touch and stay readable.
        fun cost(rect: androidx.compose.ui.geometry.Rect): Float {
            val padded = rect.inflate(gap)
            return taken.sumOf { overlap(padded, it).toDouble() }.toFloat() * 4f + nodes.sumOf { overlap(padded, it).toDouble() }.toFloat()
        }
        // Only the first part of the line: a chip must read as leaving this node, not as arriving at the other.
        val limit = min(length * .6f, reach)
        val distances = generateSequence(0f) { it + step }.takeWhile { it <= limit }.toList().ifEmpty { listOf(0f) }
        val onLine = distances.map { d -> rectAt(pointAlong(chip.path, d).first) }
        val sideways = distances.flatMap { d ->
            val (at, dir) = pointAlong(chip.path, d)
            val normal = Offset(-dir.y, dir.x)
            // Far enough to the side that the chip no longer sits on its line, whatever the angle.
            val shift = (abs(normal.x) * w + abs(normal.y) * h) / 2 + gap
            listOf(rectAt(at + normal * shift), rectAt(at - normal * shift))
        }
        val best = onLine.firstOrNull { cost(it) == 0f } ?: sideways.firstOrNull { cost(it) == 0f }
            ?: (onLine + sideways).withIndex().minBy { (i, r) -> cost(r) + i * .001f }.value
        taken += best
        out[chip.id] = best.center
    }
    return out
}
