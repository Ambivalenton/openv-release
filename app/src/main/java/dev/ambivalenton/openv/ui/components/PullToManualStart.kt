// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.exp

/**
 * Pulling the search down at its top feels like pulling at something that holds: the page
 * follows with growing resistance, and once [threshold] of finger travel is reached it gives way
 * ("es ist raus"): [onRelease] runs once, a short haptic tick confirms it and the page springs back.
 */
@Stable class PullToManualStart internal constructor(private val scope: CoroutineScope, private val threshold: Float,
    private val onRelease: () -> Unit, private val tick: () -> Unit, private val canPull: () -> Boolean) {
    /** Finger travel since the pull began (px). */
    var travel by mutableFloatStateOf(0f)
        private set
    /** True right after the release, until the page is back. */
    var released by mutableStateOf(false)
        private set
    /** 0…1: how close the pull is to giving way. */
    val progress: Float get() = (travel / threshold).coerceIn(0f, 1f)
    /** Shown offset: the resistance grows the farther it is pulled (at most about 40 % of the threshold). */
    val offset: Float get() = threshold * .42f * (1f - exp(-travel / (threshold * .55f)))

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f && travel > 0f) {
                val used = maxOf(available.y, -travel)
                travel += used
                return Offset(0f, used)
            }
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || released || available.y <= 0f || !canPull()) return Offset.Zero
            travel += available.y
            if (travel >= threshold) { released = true; tick(); onRelease(); settle() }
            return Offset(0f, available.y)
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (travel <= 0f) return Velocity.Zero
            settle()
            return available
        }
    }

    private fun settle() {
        val from = travel
        scope.launch {
            // It gave way: a quick, lively spring back; an unfinished pull sinks back calmly.
            animate(from, 0f, animationSpec = spring(dampingRatio = if (released) .48f else Spring.DampingRatioNoBouncy,
                stiffness = if (released) 520f else 380f)) { value, _ -> travel = value }
            travel = 0f; released = false
        }
    }
}

@Composable fun rememberPullToManualStart(canPull: () -> Boolean, onRelease: () -> Unit): PullToManualStart {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 140.dp.toPx() }
    val latestRelease by rememberUpdatedState(onRelease)
    val latestCanPull by rememberUpdatedState(canPull)
    return remember(scope, threshold) {
        PullToManualStart(scope, threshold, { latestRelease() }, { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }, { latestCanPull() })
    }
}
