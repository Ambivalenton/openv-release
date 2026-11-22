// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.ambivalenton.openv.data.model.Trip
import kotlin.math.*

/*
 * The comparison chart fits itself. The columns are as wide as the screen allows whole
 * columns (at least five), and the time axis stretches or compresses so that every column in view
 * shows completely: the leftmost departure stands at the top edge, the latest arrival (with its time
 * chip and footsteps) ends just above the navigation. Between two snapped positions the fit blends
 * continuously with the horizontal position, and a damped spring lets it follow, so new data or a
 * collapsing header never make it jump.
 */

/** How many whole columns fit and how wide each is (px). */
data class ChartColumns(val count: Int, val widthPx: Int)

/** At least [minimum] columns; wider screens show more, never a cut column once snapped. */
internal fun chartColumns(plotWidthPx: Int, preferredWidthPx: Float, minimum: Int = 5): ChartColumns {
    val width = plotWidthPx.coerceAtLeast(1)
    val count = max(minimum, floor(width / preferredWidthPx.coerceAtLeast(1f)).toInt()).coerceAtLeast(1)
    return ChartColumns(count, (width / count).coerceAtLeast(1))
}

/** The vertical projection: [originMinutes] (since the chart origin) lies at [topDp]; [scale] dp per minute. */
internal data class ChartFrame(val originMinutes: Float, val topDp: Float, val scale: Float) {
    fun yDp(minutes: Float) = topDp + (minutes - originMinutes) * scale
}

internal fun lerpFrame(a: ChartFrame, b: ChartFrame, t: Float): ChartFrame {
    val f = t.coerceIn(0f, 1f)
    return ChartFrame(a.originMinutes + (b.originMinutes - a.originMinutes) * f, a.topDp + (b.topDp - a.topDp) * f,
        exp(ln(a.scale) + (ln(b.scale) - ln(a.scale)) * f))
}

/** Allowed scale range in dp per minute; beyond the maximum the chart rather leaves room below. */
internal const val CHART_MIN_SCALE = .25f
internal const val CHART_MAX_SCALE = 24f

/** Vertical extent of one column around its departure, in dp: [lead] above it, [depth] below it. */
data class ColumnExtent(val lead: Float, val depth: Float)

/** Everything a column draws (bars, time chips, footsteps) relative to its departure. */
fun timelineColumnExtent(trip: Trip, minuteDp: Float, fontScale: Float, fringeDp: Float, approachDp: Float): ColumnExtent {
    val labels = timelineClockLabels(trip, minuteDp, fontScale)
    val tracks = timelineWalkTracks(trip, minuteDp, fontScale, labels, fringeDp, approachDp)
    val height = timelineClockHeight(fontScale)
    val highest = minOf(labels.minOfOrNull { it.topDp } ?: 0f, tracks.minOfOrNull { it.topDp } ?: 0f)
    val duration = (trip.endTime - trip.startTime) / 60_000f * minuteDp
    val lowest = maxOf(duration, labels.maxOfOrNull { it.topDp + height } ?: 0f, tracks.maxOfOrNull { it.bottomDp } ?: 0f)
    return ColumnExtent((-highest).coerceAtLeast(0f), lowest.coerceAtLeast(0f))
}

/**
 * Column extents tabulated over the scale range. Chips move inside or outside a bar only at a few
 * thresholds, so between grid points the extent is the duration times the scale plus a constant;
 * near a threshold the larger neighbour counts, which keeps the fit on the safe side.
 */
internal class ChartExtentTable(private val trips: List<Trip>, private val fontScale: Float,
    private val fringeDp: Float, private val approachDp: Float) {
    private val steps = 48
    private val grid = FloatArray(steps + 1) { CHART_MIN_SCALE * (CHART_MAX_SCALE / CHART_MIN_SCALE).pow(it / steps.toFloat()) }
    private val leads = arrayOfNulls<FloatArray>(trips.size)
    private val extras = arrayOfNulls<FloatArray>(trips.size)

    private fun table(index: Int) {
        if (leads[index] != null) return
        val trip = trips[index]
        val minutes = (trip.endTime - trip.startTime) / 60_000f
        val lead = FloatArray(grid.size); val extra = FloatArray(grid.size)
        grid.forEachIndexed { k, s ->
            val e = timelineColumnExtent(trip, s, fontScale, fringeDp, approachDp)
            lead[k] = e.lead; extra[k] = (e.depth - minutes * s).coerceAtLeast(0f)
        }
        leads[index] = lead; extras[index] = extra
    }
    private fun cell(s: Float) =
        (ln(s / CHART_MIN_SCALE) / ln(CHART_MAX_SCALE / CHART_MIN_SCALE) * steps).toInt().coerceIn(0, steps - 1)

    fun lead(index: Int, s: Float): Float { table(index); val k = cell(s); return max(leads[index]!![k], leads[index]!![k + 1]) }
    /** Exact extent from the real column layout (labels, bars, footsteps of every walk). */
    fun exact(index: Int, s: Float) = timelineColumnExtent(trips[index], s, fontScale, fringeDp, approachDp)
    private val frames = HashMap<String, ChartFrame>()
    /** Window frames are kept: while the chart moves only the two neighbouring windows are looked up. */
    fun memo(key: String, compute: () -> ChartFrame): ChartFrame {
        frames[key]?.let { return it }
        if (frames.size > 256) frames.clear()
        return compute().also { frames[key] = it }
    }
    fun depth(index: Int, s: Float): Float {
        table(index); val k = cell(s)
        return (trips[index].endTime - trips[index].startTime) / 60_000f * s + max(extras[index]!![k], extras[index]!![k + 1])
    }
}

/**
 * The frame in which columns [first] until [first] + [count] show completely between [topDp] and
 * [topDp] + [availableDp]: the first departure (with its lead) at the top, the lowest end at the bottom.
 * [origin] is the chart's time origin.
 */
internal fun chartWindowFrame(trips: List<Trip>, extents: ChartExtentTable, first: Int, count: Int, origin: Long,
    topDp: Float, availableDp: Float): ChartFrame {
    val a = first.coerceIn(0, trips.lastIndex)
    val last = (a + count - 1).coerceAtMost(trips.lastIndex)
    val available = availableDp.coerceAtLeast(1f)
    val key = "$a:$last:$origin:${(topDp * 4).roundToInt()}:${(available * 4).roundToInt()}"
    return extents.memo(key) {
        val startA = trips[a].startTime
        fun offset(j: Int, s: Float) = (trips[j].startTime - startA) / 60_000f * s
        fun estimate(s: Float): Float {
            var bottom = 0f
            for (j in a..last) bottom = max(bottom, offset(j, s) + extents.depth(j, s))
            return extents.lead(a, s) + bottom
        }
        fun exact(s: Float): Float {
            var bottom = 0f
            for (j in a..last) bottom = max(bottom, offset(j, s) + extents.exact(j, s).depth)
            return extents.exact(a, s).lead + bottom
        }
        // A cheap search on the tabulated extents, then a few exact steps with the real layout, so the
        // lowest arrival (time chip and footsteps included) ends right at the bottom, not a chip early.
        var lo = ln(CHART_MIN_SCALE); var hi = ln(CHART_MAX_SCALE)
        if (estimate(CHART_MAX_SCALE) <= available) lo = hi
        else repeat(30) { val mid = (lo + hi) / 2; if (estimate(exp(mid)) <= available) lo = mid else hi = mid }
        var good = exp(lo)
        if (exact(good) > available) {
            var low = ln(CHART_MIN_SCALE); var high = lo
            repeat(16) { val mid = (low + high) / 2; if (exact(exp(mid)) <= available) low = mid else high = mid }
            good = exp(low)
        } else if (good < CHART_MAX_SCALE) {
            var low = lo; var high = min(ln(CHART_MAX_SCALE), lo + ln(1.4f))
            if (exact(exp(high)) <= available) low = high
            else repeat(14) { val mid = (low + high) / 2; if (exact(exp(mid)) <= available) low = mid else high = mid }
            good = exp(low)
        }
        ChartFrame((startA - origin) / 60_000f, topDp + extents.exact(a, good).lead, good)
    }
}

/** The fit at a horizontal [position] in columns (fractional while moving), blended between neighbours. */
internal fun chartFrameAt(position: Float, trips: List<Trip>, extents: ChartExtentTable, count: Int, origin: Long,
    topDp: Float, availableDp: Float, lastIndex: Int = (trips.size - count).coerceAtLeast(0)): ChartFrame {
    val p = position.coerceIn(0f, lastIndex.toFloat())
    val a = floor(p).toInt()
    val here = chartWindowFrame(trips, extents, a, count, origin, topDp, availableDp)
    val f = p - a
    if (f <= 1e-4f || a + 1 > lastIndex) return here
    return lerpFrame(here, chartWindowFrame(trips, extents, a + 1, count, origin, topDp, availableDp), f)
}

/**
 * The frame on screen. It follows the target with a critically damped spring (3.8 Hz, no overshoot):
 * horizontal moves feel liquid, and jumps of the target (new data, the header folding) glide instead.
 */
@Stable internal class ChartProjection {
    var originMinutes by mutableFloatStateOf(0f); private set
    var topDp by mutableFloatStateOf(0f); private set
    var scale by mutableFloatStateOf(3.4f); private set
    /** True while the frame is still on its way to the target. */
    var moving by mutableStateOf(false)
    /** False until the first real frame is known; the chart stays invisible until then. */
    var placed by mutableStateOf(false); private set
    private var vOrigin = 0f; private var vTop = 0f; private var vLogScale = 0f
    val frame get() = ChartFrame(originMinutes, topDp, scale)
    fun yDp(minutes: Float) = topDp + (minutes - originMinutes) * scale

    fun snapTo(target: ChartFrame) {
        originMinutes = target.originMinutes; topDp = target.topDp; scale = target.scale
        vOrigin = 0f; vTop = 0f; vLogScale = 0f; placed = true; moving = false
    }

    /** Whether the frame is where the target is (in screen terms: below half a dp everywhere that matters). */
    fun converged(target: ChartFrame): Boolean = placed &&
        abs(topDp - target.topDp) < .25f && abs((originMinutes - target.originMinutes) * scale) < .25f &&
        abs(ln(scale / target.scale)) < .0015f && abs(vTop) < 2f && abs(vOrigin * scale) < 2f && abs(vLogScale) < .01f

    /** One step of [seconds] towards [target]. */
    fun step(target: ChartFrame, seconds: Float) {
        if (!placed) { snapTo(target); return }
        // Critically damped: it glides into place without swinging past it.
        val omega = 2f * PI.toFloat() * 3.8f; val damping = 1f
        var left = seconds.coerceIn(0f, 1 / 20f)
        while (left > 0f) {
            val dt = min(left, 1 / 240f); left -= dt
            fun accel(x: Float, v: Float, goal: Float) = -omega * omega * (x - goal) - 2 * damping * omega * v
            vOrigin += accel(originMinutes, vOrigin, target.originMinutes) * dt; originMinutes += vOrigin * dt
            vTop += accel(topDp, vTop, target.topDp) * dt; topDp += vTop * dt
            val logScale = ln(scale)
            vLogScale += accel(logScale, vLogScale, ln(target.scale)) * dt
            scale = exp(logScale + vLogScale * dt).coerceIn(CHART_MIN_SCALE * .5f, CHART_MAX_SCALE * 2f)
        }
        if (converged(target)) snapTo(target) else moving = true
    }
}

/** Minutes between time-axis marks: as dense as possible while marks stay at least [minimumDp] apart. */
internal fun chartMarkInterval(scale: Float, minimumDp: Float = 88f): Int =
    CHART_MARK_STEPS.firstOrNull { it * scale >= minimumDp } ?: CHART_MARK_STEPS.last()

/** Each step divides the next, so the marks of a finer step always include the coarser ones. */
internal val CHART_MARK_STEPS = intArrayOf(15, 30, 60, 120, 360, 720)

/**
 * How strongly a mark at [minuteOfDay] shows at [scale]: marks of the current interval fully, those of
 * the next finer interval fade in as room appears, so lines never pop while the chart breathes.
 */
internal fun chartMarkAlpha(minuteOfDay: Int, scale: Float, minimumDp: Float = 88f): Float {
    val level = CHART_MARK_STEPS.lastOrNull { minuteOfDay % it == 0 } ?: return 0f
    val spacing = level * scale
    return ((spacing - minimumDp * .6f) / (minimumDp * .4f)).coerceIn(0f, 1f)
}
