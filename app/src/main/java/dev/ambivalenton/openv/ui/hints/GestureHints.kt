// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.hints

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.GestureHintProgress
import dev.ambivalenton.openv.data.model.active
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The wordless hints: a pulse (tap here), a filling ring (hold), a gliding dot (swipe). */
enum class HintKind { PULSE, HOLD, SWIPE }

/**
 * Build 115 · which hints still show. [enabled] is set from the stored progress (first hours of use,
 * the switch in the settings, no reduced motion, no text tutorial on screen). A hint ends for good the
 * first time its gesture is used; [learn] persists that.
 */
@Stable class GestureHintState {
    var enabled by mutableStateOf(false)
    var learned by mutableStateOf<Set<String>>(emptySet())
    private val now = mutableStateMapOf<String, Unit>()
    var persist: (String) -> Unit = {}
    fun shows(key: String) = enabled && key !in learned && key !in now
    fun learn(key: String) { if (key in learned || key in now) return; now[key] = Unit; persist(key) }
    fun configure(progress: GestureHintProgress, active: Boolean, persist: (String) -> Unit) {
        learned = progress.learned; enabled = active; this.persist = persist
    }
}
val LocalGestureHints = staticCompositionLocalOf<GestureHintState?> { null }

/**
 * The app's hint state: active while [allowed] and within the first hours of use. Foreground time of
 * each visit is handed to [onUse] when the app goes to the background.
 */
@Composable fun rememberGestureHints(progress: GestureHintProgress, allowed: Boolean, persist: (String) -> Unit, onUse: (Long) -> Unit): GestureHintState {
    val state = remember { GestureHintState() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val use by rememberUpdatedState(onUse)
    var visitStart by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> visitStart = System.currentTimeMillis()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> use(System.currentTimeMillis() - visitStart)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val active = allowed && progress.active(System.currentTimeMillis(), System.currentTimeMillis() - visitStart)
    SideEffect { state.configure(progress, active, persist) }
    return state
}

/** Hold long enough to count as "held" (the plan and the chart react at about this time). */
private const val HOLD_MS = 420L

/**
 * A wordless first-use hint on this element: it plays for about a second, rests for several seconds
 * (no frames while resting) and stops for good once the element is used that way.
 */
fun Modifier.gestureHint(key: String, kind: HintKind, delayMillis: Long = 900L): Modifier = composed {
    val state = LocalGestureHints.current
    // Never next to a text tutorial on screen.
    if (state == null || !state.shows(key) || dev.ambivalenton.openv.ui.tutorial.LocalTutorialState.current?.session != null) return@composed this
    val accent = MaterialTheme.colorScheme.primary
    val film = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMillis)
        while (true) {
            film.snapTo(0f)
            film.animateTo(1f, tween(if (kind == HintKind.HOLD) 1500 else 1100, easing = LinearEasing))
            film.snapTo(0f)
            delay(if (kind == HintKind.PULSE) 4200L else 5200L)
        }
    }
    this.pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val start = down.uptimeMillis
            var moved = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                moved = maxOf(moved, abs(change.position.x - down.position.x) + abs(change.position.y - down.position.y))
                if (!change.pressed) {
                    val held = change.uptimeMillis - start
                    when (kind) {
                        HintKind.PULSE -> state.learn(key)
                        HintKind.HOLD -> if (held >= HOLD_MS) state.learn(key)
                        HintKind.SWIPE -> if (moved > viewConfiguration.touchSlop * 2) state.learn(key)
                    }
                    break
                }
                if (kind == HintKind.HOLD && change.uptimeMillis - start >= HOLD_MS && moved < viewConfiguration.touchSlop) { state.learn(key) }
            }
        }
    }.drawWithContent {
        val t = film.value
        if (kind == HintKind.PULSE && t > 0f) {
            // The element nods once (up to 7 % larger) while two soft rings leave it.
            val nod = 1f + .07f * sin((t / .35f).coerceIn(0f, 1f) * PI.toFloat())
            scale(nod) { this@drawWithContent.drawContent() }
        } else drawContent()
        if (t > 0f) drawHint(kind, t, accent)
    }
}

/** The hint itself, over the element: [t] runs once from 0 to 1. */
internal fun DrawScope.drawHint(kind: HintKind, t: Float, accent: Color) {
    val c = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension / 2
    when (kind) {
        HintKind.PULSE -> for (k in 0..1) {
            val p = ((t - k * .22f) / .7f).coerceIn(0f, 1f)
            if (p > 0f && p < 1f) drawCircle(accent.copy(alpha = .42f * (1f - p)), r * (1f + .55f * p), c, style = Stroke(2.dp.toPx() * (1f - p * .5f)))
        }
        HintKind.HOLD -> {
            // A finger lands, a ring fills around it, then it lets go with a small pop.
            val land = (t / .12f).coerceIn(0f, 1f); val fill = ((t - .12f) / .62f).coerceIn(0f, 1f); val pop = ((t - .78f) / .22f).coerceIn(0f, 1f)
            val fr = 12.dp.toPx() * (1.3f - .3f * land)
            val alpha = land * (1f - pop)
            drawCircle(accent.copy(alpha = .16f * alpha), fr, c)
            drawArc(accent.copy(alpha = .85f * alpha), -90f, 360f * fill, false, Offset(c.x - fr - 4.dp.toPx(), c.y - fr - 4.dp.toPx()),
                Size((fr + 4.dp.toPx()) * 2, (fr + 4.dp.toPx()) * 2), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            if (pop > 0f) drawCircle(accent.copy(alpha = .5f * (1f - pop)), (fr + 4.dp.toPx()) * (1f + .8f * pop), c, style = Stroke(2.dp.toPx()))
        }
        HintKind.SWIPE -> {
            // A dot glides across with a fading tail, as a finger would.
            val x = size.width * (.25f + .5f * (t * t * (3f - 2f * t)))
            val alpha = sin(t * PI.toFloat()).coerceAtLeast(0f)
            for (i in 0..6) {
                val back = i * 5.dp.toPx()
                drawCircle(accent.copy(alpha = .5f * alpha * (1f - i / 7f)), 5.dp.toPx() * (1f - i * .08f), Offset(x - back, c.y))
            }
        }
    }
}
