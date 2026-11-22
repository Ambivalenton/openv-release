// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion

import androidx.compose.foundation.gestures.*
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class ChartDragAxis {
    HORIZONTAL,
    VERTICAL,
}

/** Lock once using both axes, so a small horizontal tremor cannot steal a vertical scroll. */
internal fun chartDragAxis(delta: Offset, slop: Float): ChartDragAxis? =
    when {
        delta.getDistance() < slop -> null
        abs(delta.x) > abs(delta.y) * 1.35f -> ChartDragAxis.HORIZONTAL
        else -> ChartDragAxis.VERTICAL
    }

/** Axis-locked drag + inertial X. The injected snap owns X and Y on one animation clock. */
@Composable
internal fun Modifier.axisLockedChartScroll(
    state: LazyListState,
    onMotion: (Boolean) -> Unit = {},
    onSettled: (Int) -> Unit = {},
    onSnap: (suspend (Int) -> Unit)? = null,
    onPullToPast:(()->Unit)?=null,
    onPullToFuture:(()->Unit)?=null,
    /** The last column a position may rest on (the last one that still fills the view). */
    lastIndex:(()->Int)?=null,
): Modifier {
    val scope = rememberCoroutineScope()
    val quiet by rememberUpdatedState(dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion)
    val haptics=rememberChartHaptics()
    val decay = rememberSplineBasedDecay<Float>()
    val motion by rememberUpdatedState(onMotion)
    val settled by rememberUpdatedState(onSettled)
    val coupledSnap by rememberUpdatedState(onSnap)
    val past by rememberUpdatedState(onPullToPast)
    val future by rememberUpdatedState(onPullToFuture)
    val pastThreshold=with(LocalDensity.current) {72.dp.toPx()}
    var generation by remember { mutableIntStateOf(0) }
    val last by rememberUpdatedState(lastIndex)
    fun maxIndex()=(last?.invoke() ?: (state.layoutInfo.totalItemsCount-1)).coerceAtLeast(0)
    /**
     * One motion from the release to the resting column. Where the flick would coast to
     * decides the column (a deliberate flick moves on at least one); a spring then carries the
     * release velocity into place, with a soft overshoot instead of a stop and a second animation.
     */
    suspend fun settle(velocity: Float) {
        val width = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: return
        val x = state.firstVisibleItemIndex*width+state.firstVisibleItemScrollOffset
        val here = (x/width).roundToInt()
        var index = (decay.calculateTargetValue(x,velocity)/width).roundToInt()
        if (abs(velocity) > CHART_FLICK_VELOCITY && index == here) index += if (velocity > 0f) 1 else -1
        index = index.coerceIn(0, maxIndex())
        when {
            coupledSnap != null -> coupledSnap?.invoke(index)
            quiet -> state.scrollToItem(index)
            else -> state.scroll {
                var at = x
                AnimationState(initialValue = x, initialVelocity = velocity).animateTo(index*width, chartSettleSpring) {
                    val consumed = scrollBy(value-at)
                    at += consumed
                    // At the very first or last column the list cannot follow an overshoot: stop there.
                    if (abs(value-at) > 1f && abs(consumed) < .01f) cancelAnimation()
                }
            }
        }
        settled(index)
    }
    var fling by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(state) { onDispose { fling?.cancel() } }
    return this.pointerInput(state, decay) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                generation++
                val token = generation
                fling?.cancel()
                motion(false)
                val velocityTracker =
                    VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                var total = Offset.Zero
                var edgePull=0f
                var futurePull=0f
                var released=false
                var axis: ChartDragAxis? = null
                while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        // A touch taken away by the system is not a release (no paging pull).
                        if(!change.pressed && !change.systemCancelled) released=true
                        if (
                            !change.pressed ||
                                (change.isConsumed && axis != ChartDragAxis.HORIZONTAL)
                        )
                            break
                        val delta = change.positionChange()
                        total += delta
                        val starting = axis == null
                        if (starting) axis = chartDragAxis(total, viewConfiguration.touchSlop)
                        if (axis == ChartDragAxis.VERTICAL) break
                        if (axis == ChartDragAxis.HORIZONTAL) {
                            if (starting) motion(true)
                            change.consume()
                            val x =
                                if (starting)
                                    sign(total.x) *
                                        (abs(total.x) - viewConfiguration.touchSlop).coerceAtLeast(
                                            0f
                                        )
                                else delta.x
                            val used=state.dispatchRawDelta(-x)
                            val unused=x+used
                            futurePull=if(x>0f)0f else if(!state.canScrollForward && unused<0f)(futurePull-unused).coerceAtMost(pastThreshold*2)else futurePull
                            edgePull=if(x<0f) 0f else if(!state.canScrollBackward && unused>0f) (edgePull+unused).coerceAtMost(pastThreshold*2) else edgePull
                        }
                }
                if(axis==ChartDragAxis.HORIZONTAL && released && edgePull>=pastThreshold && past!=null) {
                    motion(false);haptics.locked();past?.invoke();return@awaitEachGesture
                }
                if(axis==ChartDragAxis.HORIZONTAL && released && futurePull>=pastThreshold && future!=null) {
                    motion(false);haptics.locked();future?.invoke();return@awaitEachGesture
                }
                if (axis == ChartDragAxis.HORIZONTAL) {
                    // A touch taken away by the system settles without its (unreliable) velocity.
                    val speed = if (released) -velocityTracker.calculateVelocity().x.coerceIn(-9_000f, 9_000f) else 0f
                    fling =
                        scope.launch {
                            try { settle(speed) } finally { if (generation == token) motion(false) }
                        }
                }
            }
        }
        // LazyRow's built-in touch scrolling is disabled below. Keep explicit accessibility
        // actions and the scroll range so TalkBack can still browse it.
        .semantics {
            horizontalScrollAxisRange =
                ScrollAxisRange(
                    value = {
                        state.firstVisibleItemIndex +
                            state.firstVisibleItemScrollOffset.toFloat() /
                                (state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1)
                                    .coerceAtLeast(1)
                    },
                    maxValue = {
                        if (state.canScrollForward) state.layoutInfo.totalItemsCount.toFloat()
                        else
                            state.firstVisibleItemIndex +
                                state.firstVisibleItemScrollOffset.toFloat() /
                                    (state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1)
                                        .coerceAtLeast(1)
                    },
                    reverseScrolling = false,
                )
            scrollBy { x, _ ->
                if (
                    x == 0f ||
                        (x > 0 && !state.canScrollForward) ||
                        (x < 0 && !state.canScrollBackward)
                )
                    false
                else {
                    fling?.cancel()
                    scope.launch {
                        val width=state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: return@launch
                        val index=((state.firstVisibleItemIndex*width+state.firstVisibleItemScrollOffset+x)/width)
                            .roundToInt().coerceIn(0,maxIndex())
                        coupledSnap?.invoke(index) ?: if(quiet) state.scrollToItem(index) else state.animateScrollToItem(index)
                        settled(index)
                    }
                    true
                }
            }
            scrollToIndex { requested ->
                val index = requested.coerceAtMost(maxIndex())
                if (requested !in 0 until state.layoutInfo.totalItemsCount) false
                else {
                    fling?.cancel()
                    scope.launch { coupledSnap?.invoke(index) ?: if(quiet) state.scrollToItem(index) else state.animateScrollToItem(index); settled(index) }
                    true
                }
            }
        }
}

/** Release speed (px/s) above which a flick always moves on by at least one column. */
internal const val CHART_FLICK_VELOCITY = 650f

/** The spring that carries a released chart into its column: quick, with a soft overshoot. */
internal val chartSettleSpring = spring<Float>(dampingRatio = .9f, stiffness = 260f)
