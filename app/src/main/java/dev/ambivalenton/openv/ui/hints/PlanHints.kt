// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.hints

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** What a hint on the plan shows: hold and drag, draw a line, tap, or hold a free spot for the names. */
enum class PlanHintKind { MOVE, CONNECT, TAP, NAMES }

/** One hint on the plan: [from] a favourite (or a free spot for [PlanHintKind.NAMES]), [to] the free field or the partner. */
data class PlanHintStep(val kind: PlanHintKind, val from: Offset, val to: Offset?, val radius: Float)

/**
 * Build 115 · hints on the plan itself during the first hours of use, one after another with rests
 * between (no frames while resting). A finger shows the gesture on a real favourite: it holds it and
 * carries a ghost along a dashed arrow to a free field, draws a line to a neighbour, or taps it and a
 * small card pops up. Outside editing it holds a free spot and name plates appear.
 */
@Composable fun PlanGestureHints(steps: List<PlanHintStep>, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val plate = MaterialTheme.colorScheme.inverseSurface
    val film = remember { Animatable(0f) }
    var index by remember { mutableIntStateOf(0) }
    val current by rememberUpdatedState(steps)
    LaunchedEffect(Unit) {
        delay(1400L)
        while (true) {
            film.snapTo(0f)
            film.animateTo(1f, tween(2600, easing = LinearEasing))
            film.snapTo(0f)
            delay(if (index == current.lastIndex) 6000L else 2200L)
            index = (index + 1) % current.size.coerceAtLeast(1)
        }
    }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    Canvas(modifier.testTag("plan-hint-${step.kind.name.lowercase()}")) {
        val t = film.value
        if (t > 0f) drawPlanHint(step, t, accent, ink, plate)
    }
}

/** One frame of [step] at time [t] (0..1). */
internal fun DrawScope.drawPlanHint(step: PlanHintStep, t: Float, accent: Color, ink: Color, plate: Color) {
    val fade = (minOf(t / .08f, (1f - t) / .08f)).coerceIn(0f, 1f)
    val fingerR = 15.dp.toPx()
    fun finger(at: Offset, press: Float, hold: Float = 0f, alpha: Float = 1f) {
        val a = alpha * fade
        if (a <= 0f) return
        val r = fingerR * (1f - .14f * press)
        drawCircle(ink.copy(alpha = .14f * a), r, at)
        drawCircle(ink.copy(alpha = .5f * a), r, at, style = Stroke(1.8.dp.toPx()))
        if (hold > 0f) drawArc(accent.copy(alpha = .9f * a), -90f, 360f * hold, false, Offset(at.x - r - 4.dp.toPx(), at.y - r - 4.dp.toPx()),
            Size((r + 4.dp.toPx()) * 2, (r + 4.dp.toPx()) * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
    fun arrowHead(tip: Offset, from: Offset, color: Color) {
        val angle = atan2(tip.y - from.y, tip.x - from.x); val len = 9.dp.toPx()
        val path = Path().apply {
            moveTo(tip.x - len * cos(angle - .5f), tip.y - len * sin(angle - .5f)); lineTo(tip.x, tip.y)
            lineTo(tip.x - len * cos(angle + .5f), tip.y - len * sin(angle + .5f))
        }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    val from = step.from
    when (step.kind) {
        PlanHintKind.MOVE -> {
            val to = step.to ?: return
            val hold = phase(t, .1f, .36f); val carry = smooth(phase(t, .38f, .78f)); val land = phase(t, .78f, .9f)
            // A dashed arc from the favourite to the free field, drawn on as the ghost travels.
            val lift = -((to - from).getDistance() * .25f).coerceAtMost(60.dp.toPx())
            fun at(s: Float) = Offset(from.x + (to.x - from.x) * s, from.y + (to.y - from.y) * s + lift * sin(s * PI.toFloat()))
            val slot = phase(t, .28f, .38f)
            if (slot > 0f) drawRoundRect(accent.copy(alpha = .55f * slot * fade), Offset(to.x - step.radius, to.y - step.radius),
                Size(step.radius * 2, step.radius * 2), CornerRadius(step.radius * .4f),
                style = Stroke(1.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
            if (carry > 0f) {
                val trail = Path().apply { moveTo(from.x, from.y); var s = 0f; while (s <= carry) { val p = at(s); lineTo(p.x, p.y); s += .03f } }
                drawPath(trail, accent.copy(alpha = .6f * fade), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))))
                arrowHead(at(carry), at((carry - .04f).coerceAtLeast(0f)), accent.copy(alpha = .8f * fade))
                drawCircle(accent.copy(alpha = .35f * fade * (1f - land * .4f)), step.radius * (1f + .08f * sin(land * PI.toFloat())), at(carry))
            }
            finger(if (carry > 0f) at(carry) else from, if (hold > 0f) 1f else 0f, if (carry == 0f) hold else 0f, 1f - land)
        }
        PlanHintKind.CONNECT -> {
            val to = step.to ?: return
            val pull = smooth(phase(t, .14f, .7f)); val snap = phase(t, .7f, .84f)
            val tip = Offset(from.x + (to.x - from.x) * pull, from.y + (to.y - from.y) * pull)
            if (pull > 0f) {
                drawLine(accent.copy(alpha = .85f * fade), from, if (snap > 0f) to else tip, 4.dp.toPx(), StrokeCap.Round)
                if (snap == 0f) arrowHead(tip, from, accent.copy(alpha = .85f * fade))
            }
            if (snap > 0f) drawCircle(accent.copy(alpha = .55f * (1f - snap) * fade), step.radius * (1f + .9f * snap), to, style = Stroke(2.5.dp.toPx()))
            finger(if (snap > 0f) to else tip, if (pull > 0f) 1f else 0f, alpha = 1f - snap)
        }
        PlanHintKind.TAP -> {
            val press = phase(t, .14f, .24f); val release = phase(t, .24f, .32f)
            val ripple = phase(t, .26f, .6f); val card = overshoot(phase(t, .34f, .6f))
            if (ripple in .001f..0.999f) drawCircle(accent.copy(alpha = .5f * (1f - ripple) * fade), step.radius * (1f + 1.4f * ripple), from, style = Stroke(2.5.dp.toPx()))
            if (card > 0f) {
                val w = 74.dp.toPx(); val h = 40.dp.toPx()
                val top = from.y - step.radius - h - 10.dp.toPx() + (1f - card) * 18.dp.toPx()
                val left = (from.x - w / 2).coerceIn(0f, size.width - w)
                val a = card.coerceIn(0f, 1f) * fade
                drawRoundRect(plate.copy(alpha = .9f * a), Offset(left, top), Size(w, h), CornerRadius(9.dp.toPx()))
                val line = Color.White.copy(alpha = .8f * a)
                drawLine(line, Offset(left + 10.dp.toPx(), top + 14.dp.toPx()), Offset(left + w * .62f, top + 14.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
                drawLine(line.copy(alpha = .5f * a), Offset(left + 10.dp.toPx(), top + 26.dp.toPx()), Offset(left + w * .45f, top + 26.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
                drawCircle(accent.copy(alpha = a), 5.dp.toPx(), Offset(left + w - 14.dp.toPx(), top + 26.dp.toPx()))
            }
            finger(from, press * (1f - release), alpha = 1f - phase(t, .34f, .5f))
        }
        PlanHintKind.NAMES -> {
            val hold = phase(t, .1f, .5f); val plates = overshoot(phase(t, .5f, .72f))
            finger(from, if (hold > 0f) 1f else 0f, hold, 1f - phase(t, .8f, .95f))
            if (plates > 0f) for ((k, dx) in listOf(-1f, 1f).withIndex()) {
                val w = 54.dp.toPx(); val h = 18.dp.toPx()
                val c = Offset(from.x + dx * 46.dp.toPx(), from.y - 34.dp.toPx() - k * 10.dp.toPx())
                val a = plates.coerceIn(0f, 1f) * fade
                drawRoundRect(plate.copy(alpha = .85f * a), Offset(c.x - w / 2, c.y - h / 2 + (1f - plates) * 8.dp.toPx()), Size(w, h), CornerRadius(6.dp.toPx()))
            }
        }
    }
}
