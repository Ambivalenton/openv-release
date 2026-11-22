// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import kotlin.math.PI
import kotlin.math.sin

/**
 * The turn of the two fields after a swap. The places are exchanged at once;
 * [progress] then runs from 1 (each field still drawn at the other one's place) to 0 with a soft
 * spring that carries them a little past their places and pulls them back.
 */
@Stable class PlannerSwapMotion {
    internal val progress = Animatable(0f)
    val running: Boolean get() = progress.isRunning
    suspend fun play(calm: Boolean) {
        if (calm) { progress.snapTo(0f); return }
        progress.snapTo(1f)
        progress.animateTo(0f, spring(dampingRatio = .5f, stiffness = 190f))
    }
}

@Composable fun rememberPlannerSwapMotion() = remember { PlannerSwapMotion() }

/**
 * Start above, destination below and the swap button on the seam between
 * them (towards the end, clear of the labels). On a swap both fields circle around the seam (the upper one down on the right, the lower
 * one up on the left, the front one a little larger) and the arrows turn half around with them.
 */
@Composable fun PlannerFieldPair(motion: PlannerSwapMotion, onSwap: () -> Unit, swapEnabled: Boolean,
    first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier) {
    var firstTop by remember { mutableFloatStateOf(0f) }
    var secondTop by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val sideways = with(density) { 22.dp.toPx() }
    val p = motion.progress.value
    val distance = secondTop - firstTop
    // A half ellipse around the button; past the end (p < 0) the fields overshoot along their path.
    val arc = sin(PI * p.coerceIn(0f, 1f)).toFloat()
    Column(modifier.fillMaxWidth().testTag("planner-fields")) {
        first(Modifier.zIndex(if (p > 0f) 2f else 0f).onGloballyPositioned { firstTop = it.positionInParent().y }
            .graphicsLayer {
                translationY = distance * p; translationX = sideways * arc
                scaleX = 1f + .05f * arc; scaleY = scaleX
            })
        // On the seam towards the end, so the button never covers a field's label.
        Box(Modifier.fillMaxWidth().height(14.dp).zIndex(3f).padding(end = 30.dp), contentAlignment = Alignment.CenterEnd) {
            PlannerSwapButton(p, swapEnabled, onSwap, Modifier.requiredSize(width = 58.dp, height = 38.dp))
        }
        second(Modifier.zIndex(if (p > 0f) 1f else 0f).onGloballyPositioned { secondTop = it.positionInParent().y }
            .graphicsLayer {
                translationY = -distance * p; translationX = -sideways * arc
                scaleX = 1f - .07f * arc; scaleY = scaleX
                alpha = 1f - .45f * arc
            })
    }
}

/** The arrows up and down side by side; a half turn looks the same as the start, so it rests seamlessly. */
@Composable private fun PlannerSwapButton(progress: Float, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val label = tr("Start und Ziel tauschen")
    val scheme = MaterialTheme.colorScheme
    Surface(onClick, modifier.semantics { contentDescription = label }.testTag("planner-swap"), enabled = enabled,
        shape = dev.ambivalenton.openv.ui.theme.appCorner(20.dp), color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer,
        border = androidx.compose.foundation.BorderStroke(2.dp, scheme.surface), shadowElevation = 2.dp) {
        val ink = if (enabled) scheme.onPrimaryContainer else scheme.onPrimaryContainer.copy(alpha = .38f)
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = 180f * progress }) {
            val stroke = 2.2.dp.toPx(); val h = size.height * .26f; val head = h * .62f
            fun arrow(x: Float, up: Boolean) {
                val tip = if (up) center.y - h else center.y + h
                val tail = if (up) center.y + h else center.y - h
                val back = if (up) head else -head
                drawLine(ink, Offset(x, tail), Offset(x, tip), stroke, StrokeCap.Round)
                drawLine(ink, Offset(x, tip), Offset(x - head * .78f, tip + back), stroke, StrokeCap.Round)
                drawLine(ink, Offset(x, tip), Offset(x + head * .78f, tip + back), stroke, StrokeCap.Round)
            }
            arrow(center.x - size.width * .13f, up = true)
            arrow(center.x + size.width * .13f, up = false)
        }
    }
}

/** Planermodus without motion: [PlannerSwapMotion.play] snaps; kept here so callers read one setting. */
@Composable fun plannerMotionCalm(): Boolean = LocalAppSettings.current.reduceMotion ||
    !android.animation.ValueAnimator.areAnimatorsEnabled()
