// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One scrap of a burst line: where it starts, how it flies and turns, its size and colour. */
internal data class ConfettiPiece(val start: Offset, val velocity: Offset, val spin: Float, val angle: Float,
    val size: Size, val color: Color, val round: Boolean, val delay: Float)

/** Build 119 · a removed line bursting into confetti; [time] runs from 0 to [CONFETTI_SECONDS]. */
internal class ConfettiBurst(val id: Long, val pieces: List<ConfettiPiece>) { val time = Animatable(0f) }

internal const val CONFETTI_SECONDS = 1.25f

/**
 * Scraps along the whole line in its colour (with lighter and darker tones and a few in [accent]). They
 * burst away from the line on both sides with a little lift, turn as they fly, fall and fade. The line's
 * middle bursts first and the burst runs out to both ends, as if it snapped there. Pure and seeded, so a
 * test can check the scraps without drawing.
 */
internal fun confettiAlong(points: List<Offset>, color: Color, accent: Color, lineWidth: Float, seed: Int,
    count: Int = 90): List<ConfettiPiece> {
    if (points.size < 2) return emptyList()
    val random = Random(seed)
    val lengths = points.zipWithNext { a, b -> (b - a).getDistance() }
    val total = lengths.sum().takeIf { it > 0f } ?: return emptyList()
    fun at(d: Float): Pair<Offset, Offset> {
        var rest = d
        for (i in lengths.indices) {
            val l = lengths[i]
            if (rest <= l || i == lengths.lastIndex) {
                val dir = (points[i + 1] - points[i]).let { it / it.getDistance().coerceAtLeast(1e-3f) }
                return points[i] + dir * rest.coerceIn(0f, l) to dir
            }
            rest -= l
        }
        return points.last() to Offset(1f, 0f)
    }
    val tones = listOf(color, lerp(color, Color.White, .35f), lerp(color, Color.Black, .25f), color, accent)
    return List(count) { k ->
        val d = total * (k + random.nextFloat()) / count
        val (p, dir) = at(d)
        val normal = Offset(-dir.y, dir.x) * (if (random.nextBoolean()) 1f else -1f)
        val speed = lineWidth * (11f + random.nextFloat() * 18f)
        val sideways = dir * (lineWidth * (random.nextFloat() - .5f) * 10f)
        val lift = Offset(0f, -lineWidth * (9f + random.nextFloat() * 12f))
        val w = lineWidth * (.55f + random.nextFloat() * .6f)
        ConfettiPiece(p + normal * (lineWidth * .3f), normal * speed + sideways + lift, (random.nextFloat() - .5f) * 900f,
            random.nextFloat() * 360f, Size(w, w * (.45f + random.nextFloat() * .5f)), tones[random.nextInt(tones.size)],
            round = random.nextInt(4) == 0, delay = abs01(d / total - .5f) * .22f)
    }
}

private fun abs01(x: Float) = if (x < 0f) -x else x

/** Where [piece] is at [t] seconds (gravity [gravity] px/s², a little air drag), or null before its start. */
internal fun confettiPosition(piece: ConfettiPiece, t: Float, gravity: Float): Offset? {
    val s = t - piece.delay
    if (s < 0f) return null
    val drag = (1f - kotlin.math.exp(-2.2f * s)) / 2.2f
    return piece.start + piece.velocity * drag + Offset(0f, .5f * gravity * s * s)
}

/** Draws every scrap of [pieces] at [t] seconds. */
internal fun DrawScope.drawConfetti(pieces: List<ConfettiPiece>, t: Float, lineWidth: Float) {
    // Light paper: it floats a moment before it falls.
    val gravity = lineWidth * 75f
    pieces.forEach { piece ->
        val at = confettiPosition(piece, t, gravity) ?: return@forEach
        val life = ((t - piece.delay) / (CONFETTI_SECONDS - piece.delay)).coerceIn(0f, 1f)
        val alpha = if (life < .6f) 1f else (1f - (life - .6f) / .4f).coerceIn(0f, 1f)
        if (alpha <= 0f) return@forEach
        val turn = piece.angle + piece.spin * (t - piece.delay)
        // A flat scrap seen turning: its width wobbles with the turn, like paper in the air.
        val flutter = .35f + .65f * kotlin.math.abs(cos((turn * PI / 180f).toFloat()))
        if (piece.round) drawCircle(piece.color.copy(alpha = piece.color.alpha * alpha), piece.size.width * .42f * (.6f + .4f * flutter), at)
        else rotate(turn, at) {
            val w = piece.size.width * flutter; val h = piece.size.height
            drawRect(piece.color.copy(alpha = piece.color.alpha * alpha), Offset(at.x - w / 2, at.y - h / 2), Size(w, h))
        }
    }
}

/** The tension of a held line before it bursts: a growing tremble across the line, 0 at rest. */
internal fun lineTremble(points: List<Offset>, tension: Float, lineWidth: Float, phase: Float): List<Offset> {
    if (tension <= 0f || points.size < 2) return points
    val amplitude = lineWidth * .45f * tension * tension
    return points.mapIndexed { i, p ->
        val next = points.getOrElse(i + 1) { p }; val prev = points.getOrElse(i - 1) { p }
        val dir = (next - prev).let { it / it.getDistance().coerceAtLeast(1e-3f) }
        p + Offset(-dir.y, dir.x) * (amplitude * sin(phase * 2f * PI.toFloat() + i * 1.7f))
    }
}
