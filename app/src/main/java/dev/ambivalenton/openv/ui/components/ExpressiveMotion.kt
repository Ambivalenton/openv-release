// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlinx.coroutines.launch
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.ui.theme.LocalAppSettings

/*
 * Expressive motion shared by the whole app (build 67): elements give way under the finger and
 * bounce back, pill buttons morph towards a rounded rectangle while pressed. Everything here is
 * event-driven (no running loop) and switched off by "Bewegung reduzieren".
 */

/** Springy press feedback: shrinks slightly while a finger is down and bounces back on release.
 * It only observes the pointer; clicks, long presses and scrolling of the element stay untouched. */
@Composable
fun Modifier.pressScale(pressedScale: Float = .96f): Modifier {
    if (LocalAppSettings.current.reduceMotion) return this
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (down) pressedScale else 1f,
        if (down) spring(dampingRatio = .7f, stiffness = 1200f) else spring(dampingRatio = .38f, stiffness = 420f), label = "press-scale")
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            down = true
            try { waitForUpOrCancellation() } finally { down = false }
        }
    }.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Corner morph of a pill: fully round at rest, a rounded rectangle while pressed, with overshoot. */
@Composable
fun pressMorphShape(interaction: InteractionSource, restPercent: Int = 50, pressedPercent: Int = 26): Shape {
    if (LocalAppSettings.current.reduceMotion) return RoundedCornerShape(restPercent)
    val pressed by interaction.collectIsPressedAsState()
    val percent by animateIntAsState(if (pressed) pressedPercent else restPercent,
        if (pressed) spring(dampingRatio = .75f, stiffness = 1400f) else spring(dampingRatio = .42f, stiffness = 380f), label = "press-morph")
    return RoundedCornerShape(percent.coerceIn(0, 50))
}

/*
 * Animated icons. A tap plays a short motion that fits the symbol, on top of the normal
 * ripple and without delaying the action: refresh turns once, plus turns a quarter, sliders are
 * adjusted sideways, a bin shakes, share pops, back nudges back and a check draws itself. All are
 * Material 3 Expressive style springs (overshoot and settle), run only after a tap (no loop) and
 * are skipped with "Bewegung reduzieren".
 */
enum class IconTapMotion { SPIN, QUARTER_TURN, NUDGE, WIGGLE, POP, BACK, DRAW }

@Stable
class IconTapState internal constructor(
    val motion: IconTapMotion,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val quiet: Boolean,
) {
    internal val value = androidx.compose.animation.core.Animatable(0f)
    /** Drawn share of a DRAW icon; 1 when idle. */
    internal val drawn = androidx.compose.animation.core.Animatable(1f)

    fun play() {
        if (quiet) return
        scope.launch {
            when (motion) {
                IconTapMotion.SPIN -> value.animateTo(value.value + 360f, spring(dampingRatio = .62f, stiffness = 240f))
                IconTapMotion.QUARTER_TURN -> value.animateTo(value.value + 90f, spring(dampingRatio = .5f, stiffness = 420f))
                IconTapMotion.NUDGE -> value.animateTo(0f, spring(dampingRatio = .28f, stiffness = 700f), initialVelocity = 90f)
                IconTapMotion.WIGGLE -> value.animateTo(0f, spring(dampingRatio = .22f, stiffness = 900f), initialVelocity = 260f)
                IconTapMotion.POP -> value.animateTo(0f, spring(dampingRatio = .35f, stiffness = 700f), initialVelocity = 6f)
                IconTapMotion.BACK -> value.animateTo(0f, spring(dampingRatio = .4f, stiffness = 600f), initialVelocity = -70f)
                IconTapMotion.DRAW -> { drawn.snapTo(0f); drawn.animateTo(1f, androidx.compose.animation.core.tween(260)) }
            }
        }
    }
}

@Composable
fun rememberIconTap(motion: IconTapMotion): IconTapState {
    val scope = rememberCoroutineScope()
    val quiet = LocalAppSettings.current.reduceMotion
    return remember(motion, quiet) { IconTapState(motion, scope, quiet) }
}

/** Draw-phase transform for an icon driven by [state]; no recomposition per frame. */
fun Modifier.iconTap(state: IconTapState): Modifier = graphicsLayer {
    val v = state.value.value
    when (state.motion) {
        IconTapMotion.SPIN, IconTapMotion.QUARTER_TURN -> rotationZ = v
        IconTapMotion.NUDGE -> translationX = v * density / 12f
        IconTapMotion.WIGGLE -> rotationZ = v / 10f
        IconTapMotion.POP -> { val s = 1f + v / 20f; scaleX = s; scaleY = s }
        IconTapMotion.BACK -> translationX = v * density / 12f
        IconTapMotion.DRAW -> {
            val bump = 1f + .18f * kotlin.math.sin(state.drawn.value * Math.PI).toFloat()
            scaleX = bump; scaleY = bump
        }
    }
}.drawWithContent {
    if (state.motion == IconTapMotion.DRAW) clipRect(right = size.width * state.drawn.value) { this@drawWithContent.drawContent() }
    else drawContent()
}

/** A standard icon button whose symbol animates on tap ([IconTapMotion]). */
@Composable
fun AnimatedIconButton(
    onClick: () -> Unit,
    icon: Int,
    description: String?,
    motion: IconTapMotion,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.material3.LocalContentColor.current,
) {
    val tap = rememberIconTap(motion)
    androidx.compose.material3.IconButton({ tap.play(); onClick() }, modifier, enabled) {
        AppIcon(icon, description, Modifier.iconTap(tap), tint = tint)
    }
}
