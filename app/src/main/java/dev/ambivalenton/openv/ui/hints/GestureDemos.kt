// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.hints

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Build 115 · the three gestures of the plan's edit mode as tiny, wordless films. */
enum class GestureDemo { MOVE, CONNECT, TAP }

/** 0..1 of a phase that runs from [start] to [end] of the whole film. */
internal fun phase(t: Float, start: Float, end: Float) = ((t - start) / (end - start)).coerceIn(0f, 1f)

/** Ease out with a small overshoot (a spring's first swing), 0..1 → 0..1. */
internal fun overshoot(x: Float): Float { val c = 1.70158f; val y = x - 1f; return 1f + (c + 1f) * y * y * y + c * y * y }

/** Smooth start and end. */
internal fun smooth(x: Float) = x * x * (3f - 2f * x)

/**
 * Draws [demo] at time [t] (0..1) into the whole size: a favourite dot in [accent], a finger as a soft
 * ring in [ink]. Everything is vector and depends only on [t], so a test can draw any frame.
 */
fun DrawScope.drawGestureDemo(demo: GestureDemo, t: Float, accent: Color, ink: Color) {
    val h = size.height; val w = size.width
    val cy = h * .5f
    val dot = (h * .16f).coerceAtMost(9.dp.toPx())
    val finger = dot * 1.35f
    fun fingerAt(at: Offset, visible: Float, press: Float, hold: Float = 0f) {
        if (visible <= 0f) return
        val r = finger * (1.25f - .25f * visible) * (1f - .14f * press)
        drawCircle(ink.copy(alpha = .16f * visible), r, at)
        drawCircle(ink.copy(alpha = .55f * visible), r, at, style = Stroke(1.5.dp.toPx()))
        if (hold > 0f) drawArc(ink.copy(alpha = .8f * visible), -90f, 360f * hold, false,
            Offset(at.x - r - 3.dp.toPx(), at.y - r - 3.dp.toPx()), Size((r + 3.dp.toPx()) * 2, (r + 3.dp.toPx()) * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
    when (demo) {
        GestureDemo.MOVE -> {
            val from = Offset(w * .24f, cy); val to = Offset(w * .76f, cy)
            val appear = phase(t, 0f, .14f); val hold = phase(t, .14f, .40f)
            val drag = smooth(phase(t, .40f, .74f)); val land = phase(t, .74f, .86f)
            // The free field it goes to: a dashed rounded square, from the start of the hold.
            val slot = phase(t, .28f, .40f)
            if (slot > 0f) drawRoundRect(ink.copy(alpha = .45f * slot), Offset(to.x - dot * 1.6f, to.y - dot * 1.6f), Size(dot * 3.2f, dot * 3.2f),
                CornerRadius(dot * .8f), style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
            // A light arc of the way, the dot lifted a little while it travels.
            val arc = -h * .16f * sin(drag * PI.toFloat())
            val at = Offset(from.x + (to.x - from.x) * drag, cy + arc)
            if (drag > 0f) {
                val trail = Path().apply { moveTo(from.x, from.y)
                    var s = 0f; while (s <= drag) { lineTo(from.x + (to.x - from.x) * s, cy - h * .16f * sin(s * PI.toFloat())); s += .04f } }
                drawPath(trail, accent.copy(alpha = .35f), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.5.dp.toPx()))))
            }
            val lift = if (drag in .001f..0.999f) 1.12f else 1f
            val settle = if (land > 0f) 1f + .18f * sin(land * PI.toFloat()) * (1f - land) else 1f
            drawCircle(Color.Black.copy(alpha = .12f), dot * lift, at + Offset(0f, 1.5.dp.toPx() * lift))
            drawCircle(accent, dot * lift * settle, at)
            fingerAt(at, appear * (1f - land), if (hold > 0f && land == 0f) 1f else 0f, if (drag == 0f) hold else 0f)
        }
        GestureDemo.CONNECT -> {
            val a = Offset(w * .2f, cy); val b = Offset(w * .8f, cy)
            val appear = phase(t, 0f, .14f); val pull = smooth(phase(t, .16f, .66f)); val snap = phase(t, .66f, .80f)
            val tip = Offset(a.x + (b.x - a.x) * pull, cy + h * .08f * sin(pull * PI.toFloat()))
            if (pull > 0f) drawLine(accent, a, if (snap > 0f) b else tip, 3.dp.toPx(), StrokeCap.Round)
            drawCircle(accent, dot, a)
            drawCircle(accent, dot * (1f + .25f * sin(snap * PI.toFloat())), b)
            if (snap > 0f) drawCircle(accent.copy(alpha = .5f * (1f - snap)), dot * (1f + 1.4f * snap), b, style = Stroke(2.dp.toPx()))
            fingerAt(if (snap > 0f) b else tip, appear * (1f - snap), if (pull > 0f) 1f else 0f)
        }
        GestureDemo.TAP -> {
            val at = Offset(w * .3f, cy)
            val appear = phase(t, 0f, .14f); val press = phase(t, .16f, .26f); val release = phase(t, .26f, .34f)
            val ripple = phase(t, .28f, .62f); val card = overshoot(phase(t, .36f, .62f))
            val pressed = press * (1f - release)
            if (ripple > 0f && ripple < 1f) drawCircle(accent.copy(alpha = .45f * (1f - ripple)), dot * (1f + 2.2f * ripple), at, style = Stroke(2.dp.toPx()))
            drawCircle(accent, dot * (1f - .12f * pressed), at)
            // A small settings card springs up beside the favourite: a frame, two lines and a switch.
            if (card > 0f) {
                val cw = w * .36f; val ch = h * .62f
                val left = w * .52f; val top = cy - ch / 2 + (1f - card) * h * .4f
                val alpha = card.coerceIn(0f, 1f)
                drawRoundRect(ink.copy(alpha = .14f * alpha), Offset(left, top), Size(cw, ch), CornerRadius(5.dp.toPx()))
                drawRoundRect(ink.copy(alpha = .5f * alpha), Offset(left, top), Size(cw, ch), CornerRadius(5.dp.toPx()), style = Stroke(1.2.dp.toPx()))
                drawLine(ink.copy(alpha = .55f * alpha), Offset(left + cw * .14f, top + ch * .34f), Offset(left + cw * .7f, top + ch * .34f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(ink.copy(alpha = .35f * alpha), Offset(left + cw * .14f, top + ch * .66f), Offset(left + cw * .5f, top + ch * .66f), 2.dp.toPx(), StrokeCap.Round)
                drawCircle(accent.copy(alpha = alpha), ch * .13f, Offset(left + cw * .82f, top + ch * .66f))
            }
            fingerAt(at, appear * (1f - phase(t, .34f, .46f)), pressed)
        }
    }
}

