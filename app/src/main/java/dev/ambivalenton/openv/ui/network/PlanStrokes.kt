// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.floor
import kotlin.math.max

/** Deterministic 0..1 noise: the same plan looks the same on every frame and every refresh. */
private fun grain(i: Int, salt: Int): Float = (((i * 1103515245L + salt * 12345L + 4711L) and 0x7fffffffL) % 1000L) / 1000f

/**
 * Line techniques of the App-Theme network plan. All loops are bounded by the line length;
 * nothing allocates per frame beyond the path effects of the drawn line.
 */
internal fun DrawScope.drawPlanStroke(stroke: PlanStroke, path: Path, points: List<Offset>, color: Color, alpha: Float,
    width: Float, paper: Color, seed: Int) {
    fun line(c: Color, w: Float, effect: PathEffect? = null, cap: StrokeCap = StrokeCap.Round) =
        drawPath(path, c, style = Stroke(w, cap = cap, join = StrokeJoin.Round, pathEffect = effect))
    when (stroke) {
        PlanStroke.SOLID -> line(color.copy(alpha = alpha), width)
        PlanStroke.PIXEL -> {
            // A sprite line: one block per grid cell the line passes, snapped to a coarse pixel grid.
            val cell = max(width, 3f)
            val measure = PathMeasure().apply { setPath(path, false) }
            val cells = LinkedHashSet<Long>()
            var d = 0f
            while (d <= measure.length && cells.size < 4000) {
                val p = measure.getPosition(d)
                if (!p.x.isNaN()) cells += (floor(p.x / cell).toLong() shl 32) or (floor(p.y / cell).toLong() and 0xffffffffL)
                d += cell * .45f
            }
            val shade = lerp(color, Color.Black, .28f).copy(alpha = alpha)
            cells.forEach { key ->
                val x = (key shr 32).toInt() * cell; val y = key.toInt() * cell
                drawRect(color.copy(alpha = alpha), Offset(x, y), androidx.compose.ui.geometry.Size(cell, cell))
                drawRect(shade, Offset(x, y + cell * .7f), androidx.compose.ui.geometry.Size(cell, cell * .3f))
            }
        }
        PlanStroke.BRUSH -> {
            // A loaded brush: wavering body, a lighter and a darker pass, then dry bristles at the edge.
            line(color.copy(alpha = .9f * alpha), width, android.graphics.DiscretePathEffect(width * 1.6f, width * .1f).toComposePathEffect())
            translate(width * .12f, -width * .1f) { line(lerp(color, Color.White, .3f).copy(alpha = .45f * alpha), width * .45f,
                android.graphics.DiscretePathEffect(width * 2.2f, width * .14f).toComposePathEffect()) }
            translate(-width * .15f, width * .14f) { line(lerp(color, Color.Black, .3f).copy(alpha = .4f * alpha), width * .32f,
                PathEffect.dashPathEffect(floatArrayOf(width * 3f, width * .8f, width * 1.2f, width * .6f), seed * 7f)) }
        }
        PlanStroke.DABS -> {
            line(color.copy(alpha = .3f * alpha), width * .9f)
            val measure = PathMeasure().apply { setPath(path, false) }
            var d = 0f; var i = 0
            while (d <= measure.length && i < 600) {
                val p = measure.getPosition(d); val t = measure.getTangent(d)
                if (!p.x.isNaN() && !t.x.isNaN()) {
                    val n = Offset(-t.y, t.x) * ((grain(i, seed) - .5f) * width * .5f)
                    val tint = if (i % 3 == 0) lerp(color, Color.White, .35f) else if (i % 3 == 1) color else lerp(color, Color.Black, .22f)
                    val a = p + n - t * (width * .45f); val b = p + n + t * (width * .45f)
                    drawLine(tint.copy(alpha = (.75f + grain(i, seed + 3) * .25f) * alpha), a, b, width * (.55f + grain(i, seed + 5) * .3f), StrokeCap.Round)
                }
                d += width * .6f; i++
            }
        }
        PlanStroke.INK -> {
            line(color.copy(alpha = .16f * alpha), width * 2.1f)
            line(color.copy(alpha = .92f * alpha), width, android.graphics.DiscretePathEffect(width * 2.5f, width * .07f).toComposePathEffect())
            line(lerp(color, Color.Black, .35f).copy(alpha = .5f * alpha), width * .35f)
        }
        PlanStroke.GLOW -> {
            line(color.copy(alpha = .12f * alpha), width * 3.2f)
            line(color.copy(alpha = .3f * alpha), width * 1.8f)
            line(color.copy(alpha = alpha), width)
            line(lerp(color, Color.White, .7f).copy(alpha = .75f * alpha), width * .32f)
        }
        PlanStroke.SKETCH -> {
            line(color.copy(alpha = .85f * alpha), width * .75f, android.graphics.DiscretePathEffect(width * 2.4f, width * .16f).toComposePathEffect())
            translate(width * .1f, width * .08f) { line(color.copy(alpha = .45f * alpha), width * .4f,
                android.graphics.DiscretePathEffect(width * 3.4f, width * .22f).toComposePathEffect()) }
        }
        PlanStroke.GLASS -> {
            line(color.copy(alpha = .62f * alpha), width)
            translate(0f, -width * .16f) { line(Color.White.copy(alpha = .42f * alpha), width * .26f) }
            line(lerp(color, Color.Black, .2f).copy(alpha = .35f * alpha), width, PathEffect.dashPathEffect(floatArrayOf(1f, 0f)), StrokeCap.Butt)
        }
        PlanStroke.TRACE -> {
            line(color.copy(alpha = alpha), width)
            // Vias at every bend, as on a circuit board.
            points.drop(1).dropLast(1).forEach { p ->
                drawCircle(paper, width * .75f, p)
                drawCircle(color.copy(alpha = alpha), width * .75f, p, style = Stroke(width * .38f))
            }
        }
    }
}
