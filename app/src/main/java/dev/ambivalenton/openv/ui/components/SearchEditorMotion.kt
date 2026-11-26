// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

internal class SearchEditorMotion(
    val row: Animatable<Float, AnimationVector1D>,
    val paper: Animatable<Float, AnimationVector1D>,
)

/**
 * Both directions use the same paper pose for a given progress. A replacement sheet starts at the
 * current editor endpoint. A departing sheet keeps its last pose for the normal destination exit.
 */
@Composable
internal fun rememberSearchEditorMotion(editing: Boolean, sheetKey: Int?, mapWanted: Boolean, quiet: Boolean): SearchEditorMotion {
    val target = if (editing) 1f else 0f
    val row = remember { Animatable(target) }
    val paper = remember(sheetKey) { Animatable(target) }
    val motion = remember(row, paper) { SearchEditorMotion(row, paper) }
    LaunchedEffect(editing, mapWanted, paper, quiet) {
        if (quiet) {
            row.snapTo(target)
            paper.snapTo(target)
            return@LaunchedEffect
        }
        suspend fun slideRow() {
            if (row.value != target) row.animateTo(target, spring(
                dampingRatio = if (editing) .8f else .58f,
                stiffness = if (editing) 340f else 260f,
                visibilityThreshold = .001f,
            ))
        }
        if (editing) slideRow()
        if (mapWanted && paper.value != target) {
            val duration = (460 * abs(target - paper.value)).roundToInt().coerceAtLeast(1)
            paper.animateTo(target, tween(duration, easing = FastOutSlowInEasing))
        }
        if (!editing) slideRow()
    }
    return motion
}

/** Smooth between key poses, with zero speed at a crease's deepest point and at rest. */
private fun easePose(fraction: Float) = ((1.0 - cos(PI * fraction.coerceIn(0f, 1f))) / 2.0).toFloat()

internal fun editorFold(progress: Float): Float = when {
    progress < .42f -> easePose(progress / .42f)
    progress < .74f -> 1f - 1.08f * easePose((progress - .42f) / .32f)
    else -> -.08f * (1f - easePose((progress - .74f) / .26f))
}

/** The button follows the contracting paper, carries on briefly, then rebounds past its seat. */
internal fun editorButtonTravel(progress: Float): Float = when {
    progress < .50f -> 1.04f * easePose(progress / .50f)
    progress < .78f -> 1.04f - 1.22f * easePose((progress - .50f) / .28f)
    else -> -.18f * (1f - easePose((progress - .78f) / .22f))
}
