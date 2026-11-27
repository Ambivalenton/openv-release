// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/*
 * Build 130 · the lenses of the modern rollers, one per design family (see RollerSkin). The body is drawn below
 * the values, the overlay above them; both get the lens outline at the origin and draw it at [lens].
 */

private fun Outline.path(at: Offset) = Path().apply { addOutline(this@path); translate(at) }

internal fun DrawScope.drawLensBody(skin: RollerSkin, lens: Rect, o: Outline, c: RollerColors, position: Float, speed: Float, lean: Float,
    pop: Float, flat: Boolean, row: Float, shape: Shape) {
    fun body(block: DrawScope.() -> Unit) = translate(lens.left, lens.top, block)
    fun drop(color: Color, layers: Int, step: Dp, start: Dp = 0.dp) = body {
        for (k in 1..layers) translate(0f, (start + step * k).toPx()) { drawOutline(o, color.copy(alpha = color.alpha / k)) }
    }
    when (skin) {
        // Material 3 Expressive: a tonal lens floating on a soft shadow, lighter at the top, with a fine rim.
        RollerSkin.MATERIAL -> {
            if (!flat) drop(Color.Black.copy(alpha = .06f), 3, 1.2.dp)
            body {
                if (flat) drawOutline(o, c.lens)
                else {
                    drawOutline(o, Brush.verticalGradient(listOf(lerp(c.lens, Color.White, .2f), c.lens, lerp(c.lens, Color.Black, .05f)), 0f, lens.height))
                    drawOutline(o, Brush.verticalGradient(0f to Color.White.copy(alpha = .5f), .22f to Color.Transparent, .8f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .12f), startY = 0f, endY = lens.height), style = Stroke(1.2.dp.toPx()))
                    drawOutline(o, c.accent.copy(alpha = .24f), style = Stroke(.8.dp.toPx()))
                }
            }
        }
        // Glass: the accent glows behind a frosted pane (the glow drifts with the turn); bright rim, lighter at the top.
        RollerSkin.GLASS -> {
            val glowAt = Offset(lens.center.x + lens.width * .2f * lean, lens.center.y)
            val r = lens.width * .58f
            drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = .36f + .12f * speed), c.accent.copy(alpha = .1f), Color.Transparent), glowAt, r), r, glowAt)
            drop(Color.Black.copy(alpha = .05f), 3, 1.4.dp)
            body {
                drawOutline(o, c.lens.copy(alpha = if (c.dark) .13f else .52f))
                drawOutline(o, Brush.verticalGradient(0f to Color.White.copy(alpha = if (c.dark) .16f else .5f), .52f to Color.White.copy(alpha = .04f),
                    1f to Color.Transparent, startY = 0f, endY = lens.height))
                drawOutline(o, Brush.verticalGradient(0f to Color.White.copy(alpha = .95f), .5f to Color.White.copy(alpha = .22f),
                    1f to Color.White.copy(alpha = .5f), startY = 0f, endY = lens.height), style = Stroke(1.2.dp.toPx()))
            }
        }
        // Aurora / glow edge: a dark lens in a soft halo, its gradient edge turning with the drum.
        RollerSkin.AURORA -> body {
            for (k in 3 downTo 1) drawOutline(o, c.accent.copy(alpha = .06f + .03f * pop), style = Stroke((k * 4).dp.toPx()))
            drawOutline(o, c.lens)
            drawOutline(o, Brush.verticalGradient(0f to Color.White.copy(alpha = .1f), .5f to Color.Transparent, startY = 0f, endY = lens.height))
            val a = position * 35f * PI.toFloat() / 180f
            val center = Offset(lens.width / 2f, lens.height / 2f)
            val reach = Offset(cos(a), sin(a)) * (lens.width / 2f)
            drawOutline(o, Brush.linearGradient(listOf(c.accent, c.edge, lerp(c.accent, c.edge, .5f).copy(alpha = .4f), c.accent),
                center - reach, center + reach), style = Stroke(1.6.dp.toPx()))
        }
        // Soft UI: a groove pressed into the surface – shadow inside at the top left, light at the bottom right.
        RollerSkin.SOFT -> body {
            drawOutline(o, c.lens)
            clipPath(o.path(Offset.Zero)) {
                // Each layer reaches exactly to its own offset, so only the near side shows (no ring all round).
                for (k in 1..5) {
                    val d = k * 1.dp.toPx()
                    translate(d, d) { drawOutline(o, Color.Black.copy(alpha = if (c.dark) .085f else .04f), style = Stroke(2 * d)) }
                    translate(-d, -d) { drawOutline(o, Color.White.copy(alpha = if (c.dark) .025f else .16f), style = Stroke(2 * d)) }
                }
            }
        }
        // Clay: a puffy, saturated pill on a coloured soft shadow, an inner light top left and a shade at the bottom.
        RollerSkin.CLAY -> {
            drop(lerp(c.lens, Color.Black, .35f).copy(alpha = .3f), 4, 1.3.dp, 1.dp)
            body {
                drawOutline(o, Brush.verticalGradient(listOf(lerp(c.lens, Color.White, .2f), c.lens, lerp(c.lens, Color.Black, .1f)), 0f, lens.height))
                clipPath(o.path(Offset.Zero)) {
                    // A soft inner light along the upper edge, faint enough to leave the values clear.
                    drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = if (c.dark) .2f else .42f), Color.Transparent),
                        Offset(lens.width * .32f, lens.height * .16f), lens.width * .3f),
                        Offset(lens.width * .06f, lens.height * .03f), Size(lens.width * .52f, lens.height * .3f))
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, .6f to Color.Transparent, 1f to Color.Black.copy(alpha = .14f),
                        startY = 0f, endY = lens.height), size = lens.size)
                }
            }
        }
        // Neo-brutalism: a flat colour block, a thick ink outline and a hard shadow; a landing value stamps it down.
        RollerSkin.BRUTAL -> {
            val full = 4.dp.toPx(); val sink = full * .7f * pop
            translate(lens.left + full, lens.top + full) { drawOutline(o, c.edge) }
            translate(lens.left + sink, lens.top + sink) {
                drawOutline(o, c.lens)
                drawOutline(o, c.edge, style = Stroke(2.5.dp.toPx()))
            }
        }
        // Drafting: only lines (drawn over the values).
        RollerSkin.OUTLINE, RollerSkin.SWISS, RollerSkin.EDITORIAL, RollerSkin.JAPANDI -> Unit
        // Tiles and bento: one crisp plane; a landing value lights it for a moment.
        RollerSkin.TILE -> body {
            drawOutline(o, c.lens)
            if (c.edge != c.accent) drawOutline(o, c.edge, style = Stroke(1.dp.toPx()))
            drawOutline(o, Color.White.copy(alpha = .16f * pop))
        }
        // Tonal: the lens and, fading away from it, tonal steps of the same hue behind the neighbouring values.
        RollerSkin.TONAL -> {
            for (k in 1..2) for (side in listOf(-1f, 1f)) {
                val top = lens.top + side * k * row
                drawRect(c.lens.copy(alpha = .32f / k), Offset(lens.left + k * 6.dp.toPx(), top + 2.dp.toPx()),
                    Size(lens.width - k * 12.dp.toPx(), lens.height - 4.dp.toPx()))
            }
            body { drawOutline(o, c.lens) }
        }
        // Organic: a pebble that breathes with the turn, on a soft coloured shadow.
        RollerSkin.ORGANIC -> {
            drop(lerp(c.lens, Color.Black, .3f).copy(alpha = .2f), 3, 1.4.dp)
            body { drawOutline(o, Brush.verticalGradient(listOf(lerp(c.lens, Color.White, .16f), c.lens), 0f, lens.height)) }
        }
        // E-ink: inverted, no shadow, no gradient – sharp black.
        RollerSkin.INK -> body { drawOutline(o, c.lens) }
        // Chrome: polished metal with a horizon line and a bevelled edge.
        RollerSkin.CHROME -> {
            drop(Color.Black.copy(alpha = .1f), 2, 1.dp)
            body {
                drawOutline(o, Brush.verticalGradient(0f to Color(0xFFF6F8FB), .44f to Color(0xFFCBD2DB), .5f to Color(0xFF98A1AD),
                    .56f to Color(0xFFD6DCE3), 1f to Color(0xFFEEF1F5), startY = 0f, endY = lens.height))
                drawOutline(o, Brush.verticalGradient(listOf(Color.White, Color(0xFF59616C)), 0f, lens.height), style = Stroke(1.2.dp.toPx()))
            }
        }
        // Layered paper: three cut sheets below, each a little further down and darker.
        RollerSkin.PAPER -> {
            for (k in 3 downTo 1) translate(lens.left + (k * 1.1f).dp.toPx(), lens.top + (k * 1.5f).dp.toPx()) {
                drawOutline(o, lerp(c.lens, Color.Black, (if (c.dark) .25f else .06f) + .05f * k))
            }
            body { drawOutline(o, c.lens); drawOutline(o, c.edge.copy(alpha = .6f), style = Stroke(.6.dp.toPx())) }
        }
        // Cloth: a fabric patch with a stitched seam just inside its edge.
        RollerSkin.STITCH -> {
            drop(Color.Black.copy(alpha = .07f), 2, 1.2.dp)
            body {
                drawOutline(o, c.lens)
                val inset = 3.dp.toPx()
                translate(inset, inset) {
                    drawOutline(shape.createOutline(Size(lens.width - 2 * inset, lens.height - 2 * inset), layoutDirection, this),
                        c.edge.copy(alpha = .75f), style = Stroke(1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                }
            }
        }
        // 8-bit: stepped pixel corners, a solid outline and a stepped shadow one pixel step down and right.
        RollerSkin.PIXEL -> {
            val step = 3.dp.toPx()
            translate(lens.left + step, lens.top + step) { drawOutline(o, c.edge) }
            body { drawOutline(o, c.lens); drawOutline(o, c.edge, style = Stroke(2.dp.toPx())) }
        }
    }
}

internal fun DrawScope.drawLensOverlay(skin: RollerSkin, lens: Rect, o: Outline, c: RollerColors, position: Float, speed: Float, pop: Float,
    row: Float, radius: Float, chosen: RollerFace, fit: Float) {
    val w = size.width; val cy = size.height / 2f
    when (skin) {
        // Glass: the pane's reflection lies over the digits; a sheen glides across while the drum turns.
        RollerSkin.GLASS -> clipPath(o.path(lens.topLeft)) {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (c.dark) .1f else .26f), .42f to Color.Transparent,
                startY = lens.top, endY = lens.bottom), lens.topLeft, lens.size)
            if (speed > .02f) {
                val frac = position - floor(position)
                val x = lens.left - lens.width * .3f + lens.width * 1.6f * frac
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .22f * speed), Color.Transparent),
                    Offset(x - 24.dp.toPx(), lens.top), Offset(x + 24.dp.toPx(), lens.bottom)), lens.topLeft, lens.size)
            }
        }
        // Swiss: a strong accent rule on top, a hairline below – a typographic grid instead of a box.
        RollerSkin.SWISS -> {
            val rule = (3f + 1.5f * pop).dp.toPx()
            drawRect(c.accent, Offset(lens.left, lens.top - rule / 2f), Size(lens.width, rule))
            drawRect(c.ink.copy(alpha = .35f), Offset(lens.left, lens.bottom), Size(lens.width, 1.dp.toPx()))
        }
        // Editorial: the newspaper's double rule above, a fine rule below.
        RollerSkin.EDITORIAL -> {
            val ink = c.ink.copy(alpha = .72f)
            drawRect(ink, Offset(lens.left, lens.top - 4.dp.toPx()), Size(lens.width, .8.dp.toPx()))
            drawRect(ink, Offset(lens.left, lens.top - 1.5.dp.toPx()), Size(lens.width, 1.6.dp.toPx()))
            drawRect(ink, Offset(lens.left, lens.bottom + .5.dp.toPx()), Size(lens.width, .8.dp.toPx()))
        }
        // Drafting: an outlined lens and a scale on both edges that turns with the drum.
        RollerSkin.OUTLINE -> {
            translate(lens.left, lens.top) { drawOutline(o, c.accent, style = Stroke(1.5.dp.toPx())) }
            val first = floor(position * 4f).toInt() - 14
            for (t in first..first + 28) {
                val angle = (t / 4f - position) * 34f
                if (abs(angle) >= 88f) continue
                val rad = angle * PI.toFloat() / 180f
                val y = cy + radius * sin(rad)
                val depth = cos(rad)
                val length = (if (Math.floorMod(t, 4) == 0) 6f else 3f).dp.toPx() * depth
                val color = c.ink.copy(alpha = .45f * depth * depth)
                drawLine(color, Offset(0f, y), Offset(length, y), .8.dp.toPx())
                drawLine(color, Offset(w - length, y), Offset(w, y), .8.dp.toPx())
            }
        }
        // Japandi: one calm brush stroke under the chosen value, tapering at both ends; it breathes when a value lands.
        RollerSkin.JAPANDI -> {
            val length = (chosen.width * fit * .5f * (1f + .25f * pop)).coerceAtMost(lens.width * .8f)
            val y = lens.bottom - 3.dp.toPx()
            val left = w / 2f - length / 2f
            val stroke = Path().apply {
                moveTo(left, y)
                cubicTo(left + length * .3f, y - 1.6.dp.toPx(), left + length * .7f, y - 1.6.dp.toPx(), left + length, y - .4.dp.toPx())
                cubicTo(left + length * .7f, y + .9.dp.toPx(), left + length * .3f, y + 1.1.dp.toPx(), left, y)
                close()
            }
            drawPath(stroke, c.accent.copy(alpha = .9f))
        }
        // Chrome: a hard highlight across the upper half.
        RollerSkin.CHROME -> clipPath(o.path(lens.topLeft)) {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = .45f), .38f to Color.White.copy(alpha = .08f), .4f to Color.Transparent,
                startY = lens.top, endY = lens.bottom), lens.topLeft, lens.size)
        }
        else -> Unit
    }
}

/** 8-bit lens: rectangular, its corners cut in two pixel steps. */
internal class SteppedShape(private val step: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = with(density) { step.toPx() }.coerceAtMost(minOf(size.width, size.height) / 4f)
        val w = size.width; val h = size.height
        return Outline.Generic(Path().apply {
            moveTo(2 * p, 0f); lineTo(w - 2 * p, 0f); lineTo(w - 2 * p, p); lineTo(w - p, p); lineTo(w - p, 2 * p); lineTo(w, 2 * p)
            lineTo(w, h - 2 * p); lineTo(w - p, h - 2 * p); lineTo(w - p, h - p); lineTo(w - 2 * p, h - p); lineTo(w - 2 * p, h)
            lineTo(2 * p, h); lineTo(2 * p, h - p); lineTo(p, h - p); lineTo(p, h - 2 * p); lineTo(0f, h - 2 * p)
            lineTo(0f, 2 * p); lineTo(p, 2 * p); lineTo(p, p); lineTo(2 * p, p); close()
        })
    }
}

/**
 * Organic lens: a rounded superellipse whose edge breathes – a slow ripple tied to the drum's position, livelier
 * while it turns and when a value lands.
 */
internal class BlobShape(private val position: Float, private val speed: Float, private val pop: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f; val cy = size.height / 2f
        val amp = .03f + .06f * speed + .05f * pop
        val phase = position * 1.7f
        val n = 4.2f
        return Outline.Generic(Path().apply {
            for (k in 0..72) {
                val t = k / 72f * 2f * PI.toFloat()
                val ct = cos(t); val st = sin(t)
                val sx = Math.signum(ct) * abs(ct).pow(2f / n); val sy = Math.signum(st) * abs(st).pow(2f / n)
                val ripple = 1f + amp * sin(2f * t + phase) + amp * .5f * sin(3f * t - phase * 1.3f)
                val x = cx + (cx * sx) * ripple.coerceAtMost(1f + amp)
                val y = cy + (cy * sy) * (2f - ripple).coerceIn(1f - amp * 1.5f, 1f)
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        })
    }
}
