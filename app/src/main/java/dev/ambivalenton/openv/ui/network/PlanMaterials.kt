// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.theme.drawSeam
import dev.ambivalenton.openv.ui.theme.drawWeave
import kotlin.math.max
import kotlin.math.min

/**
 * Build 110 · the material of the "App-Theme" network plan. The sheet and every station are built
 * like the app design's own surfaces: frosted glass, glowing tubes, 8-bit sprites, ink-and-offset
 * posters, puffy clay, polished chrome, embossed relief, cut paper, stitched cloth, fine line work,
 * letterpress rings, a terminal frame or a Swiss grid. Everything is static vector drawing; quiet mode
 * keeps outlines and fills, drops glows, sheens and textures.
 */
internal enum class PlanFinish { PLAIN, GLASS, AURORA, NEON, PIXEL, BRUTAL, CLAY, CHROME, SOFT, PAPER, STITCH, OUTLINE, PRINT, TERMINAL, SWISS }

/** The finish of the active design: its design language first, else its design-language collection. */
internal fun planFinishFor(theme: AppTheme?): PlanFinish {
    if (theme == null) return PlanFinish.PLAIN
    return when (theme.language) {
        DesignLanguage.GLASS -> PlanFinish.GLASS
        DesignLanguage.AURORA -> PlanFinish.AURORA
        DesignLanguage.NEON -> PlanFinish.NEON
        DesignLanguage.PIXEL -> PlanFinish.PIXEL
        DesignLanguage.BRUTAL -> PlanFinish.BRUTAL
        DesignLanguage.CLAY -> PlanFinish.CLAY
        DesignLanguage.CHROME -> PlanFinish.CHROME
        DesignLanguage.SOFT -> PlanFinish.SOFT
        DesignLanguage.PAPER -> PlanFinish.PAPER
        DesignLanguage.STITCH -> PlanFinish.STITCH
        DesignLanguage.OUTLINE -> PlanFinish.OUTLINE
        DesignLanguage.EDITORIAL, DesignLanguage.INK -> PlanFinish.PRINT
        DesignLanguage.TERMINAL -> PlanFinish.TERMINAL
        DesignLanguage.SWISS -> PlanFinish.SWISS
        DesignLanguage.FLAT, DesignLanguage.BENTO -> PlanFinish.PLAIN
        DesignLanguage.MATERIAL -> when (theme.themeLanguage) {
            ThemeLanguage.NEO_BRUTAL -> PlanFinish.BRUTAL
            ThemeLanguage.SOFT_RELIEF -> PlanFinish.SOFT
            ThemeLanguage.CLAY, ThemeLanguage.ORGANIC -> PlanFinish.CLAY
            ThemeLanguage.OUTLINE -> PlanFinish.OUTLINE
            ThemeLanguage.JAPANDI -> PlanFinish.PRINT
            ThemeLanguage.GLOW_EDGE -> PlanFinish.NEON
            else -> PlanFinish.PLAIN
        }
    }
}

/** Colours a finish needs, taken from the active scheme once per plan. */
internal data class PlanInk(val paper: Color, val ink: Color, val background: Color, val primary: Color, val secondary: Color,
    val tertiary: Color, val outline: Color, val dark: Boolean, val quiet: Boolean)

/** A blur-free soft shadow: the outline in [steps] widening, fading layers. */
private fun DrawScope.softShadow(outline: Outline, color: Color, dx: Float, dy: Float, spread: Float, steps: Int = 5) {
    val path = Path().apply { addOutline(outline) }
    val smallest = size.minDimension.coerceAtLeast(1f)
    for (step in steps downTo 1) {
        val grow = 1f + 2f * spread * step / steps / smallest
        withTransform({ translate(dx, dy); scale(grow, grow, center) }) { drawPath(path, color.copy(alpha = color.alpha / steps)) }
    }
}

/** Under the sheet: shadows, glows and paper layers. Drawn before the sheet's fill. */
internal fun DrawScope.drawSheetUnder(finish: PlanFinish, outline: Outline, c: PlanInk) {
    val u = 1.dp.toPx()
    when (finish) {
        PlanFinish.GLASS -> if (!c.quiet) softShadow(outline, Color.Black.copy(alpha = if (c.dark) .45f else .16f), 0f, 6 * u, 14 * u)
        PlanFinish.AURORA, PlanFinish.NEON -> if (!c.quiet) softShadow(outline, c.primary.copy(alpha = if (c.dark) .5f else .3f), 0f, 0f, 12 * u)
        PlanFinish.BRUTAL, PlanFinish.PIXEL -> translate(5 * u, 5 * u) { drawOutline(outline, if (c.dark) c.primary.copy(alpha = .9f) else c.ink) }
        PlanFinish.CLAY -> if (!c.quiet) softShadow(outline, lerp(c.primary, Color.Black, .3f).copy(alpha = if (c.dark) .55f else .32f), 0f, 10 * u, 16 * u)
        PlanFinish.CHROME -> if (!c.quiet) softShadow(outline, Color.Black.copy(alpha = if (c.dark) .5f else .22f), 0f, 5 * u, 8 * u)
        PlanFinish.SOFT -> if (!c.quiet) {
            softShadow(outline, Color.Black.copy(alpha = if (c.dark) .6f else .17f), 6 * u, 6 * u, 10 * u)
            softShadow(outline, Color.White.copy(alpha = if (c.dark) .07f else .95f), -6 * u, -6 * u, 10 * u)
        }
        PlanFinish.PAPER -> {
            translate(7 * u, 8 * u) { drawOutline(outline, lerp(c.background, c.secondary, if (c.dark) .34f else .26f)) }
            translate(3.5f * u, 4 * u) { drawOutline(outline, lerp(c.background, c.primary, if (c.dark) .28f else .2f)) }
        }
        PlanFinish.STITCH -> if (!c.quiet) softShadow(outline, Color.Black.copy(alpha = if (c.dark) .4f else .14f), 0f, 4 * u, 8 * u)
        else -> Unit
    }
}

/** Over the sheet's fill and motif: sheens, rims, seams, rules and frames. Replaces the plain outline. */
internal fun DrawScope.drawSheetOver(finish: PlanFinish, outline: Outline, c: PlanInk) {
    val u = 1.dp.toPx(); val w = size.width; val h = size.height
    val path = Path().apply { addOutline(outline) }
    when (finish) {
        PlanFinish.PLAIN -> drawOutline(outline, c.outline.copy(alpha = .6f), style = Stroke(u))
        PlanFinish.GLASS -> {
            if (!c.quiet) clipPath(path) {
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = if (c.dark) .13f else .42f), Color.White.copy(alpha = 0f)),
                    Offset.Zero, Offset(w * .55f, h * .7f)))
                // A soft caustic arc: the light that bends through a thick pane.
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (c.dark) .06f else .22f), Color.Transparent),
                    Offset(w * .92f, h * 1.05f), max(w, h) * .5f), max(w, h) * .5f, Offset(w * .92f, h * 1.05f))
            }
            drawOutline(outline, Brush.linearGradient(listOf(Color.White.copy(alpha = if (c.dark) .42f else .95f),
                Color.White.copy(alpha = if (c.dark) .08f else .3f), c.primary.copy(alpha = .35f)), Offset.Zero, Offset(w, h)), style = Stroke(1.2f * u))
        }
        PlanFinish.AURORA -> {
            drawOutline(outline, Brush.linearGradient(listOf(c.primary, c.secondary, c.tertiary), Offset.Zero, Offset(w, h)), style = Stroke(1.6f * u))
        }
        PlanFinish.NEON -> {
            if (!c.quiet) {
                val grid = c.primary.copy(alpha = if (c.dark) .07f else .06f); val step = 22 * u
                clipPath(path) {
                    var x = step; while (x < w) { drawLine(grid, Offset(x, 0f), Offset(x, h), u); x += step }
                    var y = step; while (y < h) { drawLine(grid, Offset(0f, y), Offset(w, y), u); y += step }
                }
                drawOutline(outline, c.primary.copy(alpha = .16f), style = Stroke(7 * u))
                drawOutline(outline, c.primary.copy(alpha = .3f), style = Stroke(3.5f * u))
            }
            drawOutline(outline, c.primary, style = Stroke(1.4f * u))
            drawOutline(outline, Color.White.copy(alpha = .5f), style = Stroke(.5f * u))
        }
        PlanFinish.PIXEL -> {
            if (!c.quiet) clipPath(path) {
                val cell = 8 * u; val dot = lerp(c.paper, c.ink, .1f)
                var y = cell; while (y < h) { var x = cell; while (x < w) { drawRect(dot, Offset(x - u, y - u), Size(2 * u, 2 * u)); x += cell }; y += cell }
            }
            drawOutline(outline, c.ink, style = Stroke(2.5f * u))
        }
        PlanFinish.BRUTAL -> drawOutline(outline, c.ink, style = Stroke(2.5f * u))
        PlanFinish.CLAY -> if (!c.quiet) clipPath(path) {
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.dark) .1f else .5f), Color.Transparent), 0f, min(h * .22f, 40 * u)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (c.dark) .25f else .07f)), h - min(h * .2f, 36 * u), h))
        }
        PlanFinish.CHROME -> {
            if (!c.quiet) clipPath(path) {
                var y = 0f; var n = 0
                while (y < h && n < 600) { drawLine((if (n % 2 == 0) Color.White else Color.Black).copy(alpha = .03f), Offset(0f, y), Offset(w, y), u); y += 2.5f * u; n++ }
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (c.dark) .14f else .5f), .18f to Color.White.copy(alpha = 0f), 1f to Color.Transparent))
            }
            drawOutline(outline, Brush.verticalGradient(listOf(Color.White.copy(alpha = .95f), lerp(c.ink, c.paper, .55f), Color.White.copy(alpha = .8f))), style = Stroke(2.4f * u))
            drawOutline(outline, c.ink.copy(alpha = .25f), style = Stroke(.7f * u))
        }
        PlanFinish.SOFT -> drawOutline(outline, Brush.linearGradient(listOf(Color.White.copy(alpha = if (c.dark) .08f else .8f),
            Color.Black.copy(alpha = if (c.dark) .3f else .06f)), Offset.Zero, Offset(w, h)), style = Stroke(u))
        PlanFinish.PAPER -> drawOutline(outline, Color.White.copy(alpha = if (c.dark) .08f else .6f), style = Stroke(u))
        PlanFinish.STITCH -> {
            if (!c.quiet) clipPath(path) { drawWeave(c.ink.copy(alpha = if (c.dark) .05f else .045f), 5 * u) }
            drawSeam(outline, lerp(c.secondary, c.ink, .2f).copy(alpha = .8f), 7 * u, 1.5f * u)
        }
        PlanFinish.OUTLINE -> drawOutline(outline, c.ink.copy(alpha = .8f), style = Stroke(1.2f * u))
        PlanFinish.PRINT -> {
            drawOutline(outline, c.ink.copy(alpha = .85f), style = Stroke(.9f * u))
            clipPath(path) {
                drawRect(c.ink, Offset(14 * u, 7 * u), Size(w - 28 * u, 1.6f * u))
                drawRect(c.ink.copy(alpha = .6f), Offset(14 * u, 10.5f * u), Size(w - 28 * u, .7f * u))
            }
        }
        PlanFinish.TERMINAL -> {
            if (!c.quiet) clipPath(path) {
                var y = 0f; var n = 0
                while (y < h && n < 500) { drawLine(c.ink.copy(alpha = .045f), Offset(0f, y), Offset(w, y), u); y += 3 * u; n++ }
            }
            drawOutline(outline, c.primary.copy(alpha = .85f), style = Stroke(1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * u, 4 * u))))
        }
        PlanFinish.SWISS -> clipPath(path) {
            val grid = c.ink.copy(alpha = .06f); val step = 24 * u
            var x = step; while (x < w) { drawLine(grid, Offset(x, 0f), Offset(x, h), u); x += step }
            drawRect(c.primary, Offset.Zero, Size(min(6 * u, w * .03f), h))
        }
    }
}

/** Fill of the sheet: an outline-only plan shows the app's ground; relief takes the background colour. */
internal fun sheetFill(finish: PlanFinish, paper: Color, background: Color): Color? = when (finish) {
    PlanFinish.OUTLINE -> null
    PlanFinish.SOFT -> background
    else -> paper
}

/** Shape of a station in a finish: the 8-bit plan steps every favourite's outline into a sprite tile. */
internal fun stationShapeIn(finish: PlanFinish, own: Shape): Shape =
    if (finish == PlanFinish.PIXEL) dev.ambivalenton.openv.ui.theme.PixelCornerShape(
        androidx.compose.foundation.shape.CornerSize(28), androidx.compose.foundation.shape.CornerSize(28),
        androidx.compose.foundation.shape.CornerSize(28), androidx.compose.foundation.shape.CornerSize(28)) else own

/** Symbol colour on a station of [finish]: [onFill] on filled bodies, the favourite's colour on open ones. */
internal fun stationSymbol(finish: PlanFinish, fill: Color, onFill: Color, accentOnPaper: Color, c: PlanInk): Color = when (finish) {
    PlanFinish.NEON, PlanFinish.AURORA -> lerp(fill, Color.White, if (c.dark) .3f else 0f)
    PlanFinish.SOFT, PlanFinish.OUTLINE, PlanFinish.PRINT, PlanFinish.TERMINAL -> accentOnPaper
    PlanFinish.SWISS -> c.paper.copy(alpha = 1f)
    else -> onFill
}

/**
 * The body of a station in [finish]: shadow or glow, fill and the finish's details, drawn behind the
 * symbol. [fill] is the favourite's colour; [lifted] while it is moved.
 */
internal fun Modifier.stationBody(finish: PlanFinish, shape: Shape, fill: Color, c: PlanInk, lifted: Boolean): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val u = 1.dp.toPx(); val w = size.width; val h = size.height
    val lift = if (lifted) 2.2f else 1f
    onDrawBehind {
        fun drop(color: Color = Color.Black.copy(alpha = if (c.dark) .45f else .22f)) = softShadow(outline, color, 0f, 2.5f * u * lift, 4 * u * lift, 4)
        when (finish) {
            PlanFinish.PLAIN -> { drop(); drawOutline(outline, fill); drawOutline(outline, c.background.copy(alpha = .9f), style = Stroke(1.5f * u)) }
            PlanFinish.GLASS -> {
                if (!c.quiet) drawCircle(Brush.radialGradient(listOf(fill.copy(alpha = .45f), Color.Transparent), center, max(w, h) * .85f), max(w, h) * .85f)
                drop(Color.Black.copy(alpha = if (c.dark) .35f else .14f))
                drawOutline(outline, lerp(fill, c.paper.copy(alpha = 1f), .12f).copy(alpha = .72f))
                if (!c.quiet) clipPath(path) {
                    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .55f), Color.White.copy(alpha = 0f)), Offset.Zero, Offset(w * .6f, h * .6f)))
                    drawOval(Color.White.copy(alpha = .35f), Offset(w * .16f, h * .08f), Size(w * .42f, h * .2f))
                }
                drawOutline(outline, Brush.linearGradient(listOf(Color.White.copy(alpha = .95f), Color.White.copy(alpha = .2f), fill.copy(alpha = .6f)),
                    Offset.Zero, Offset(w, h)), style = Stroke(1.3f * u))
            }
            PlanFinish.AURORA -> {
                if (!c.quiet) drawCircle(Brush.radialGradient(listOf(fill.copy(alpha = .35f), Color.Transparent), center, max(w, h) * .8f), max(w, h) * .8f)
                drawOutline(outline, c.paper.copy(alpha = 1f))
                drawOutline(outline, Brush.sweepGradient(listOf(c.primary, c.secondary, c.tertiary, c.primary), center), style = Stroke(2.4f * u))
            }
            PlanFinish.NEON -> {
                if (!c.quiet) {
                    drawOutline(outline, fill.copy(alpha = .14f), style = Stroke(10 * u))
                    drawOutline(outline, fill.copy(alpha = .28f), style = Stroke(5 * u))
                }
                drawOutline(outline, lerp(c.paper.copy(alpha = 1f), fill, .1f))
                drawOutline(outline, fill, style = Stroke(2 * u))
                drawOutline(outline, lerp(fill, Color.White, .65f), style = Stroke(.7f * u))
            }
            PlanFinish.PIXEL -> {
                translate(3 * u * lift, 3 * u * lift) { drawOutline(outline, c.ink) }
                drawOutline(outline, fill)
                clipPath(path) {
                    val px = max(2 * u, w / 14f)
                    drawRect(Color.White.copy(alpha = .55f), Offset(px * 2, px * 2), Size(px * 2, px))
                    drawRect(Color.White.copy(alpha = .55f), Offset(px * 2, px * 3), Size(px, px))
                    drawRect(Color.Black.copy(alpha = .18f), Offset(0f, h - px * 2), Size(w, px * 2))
                }
                drawOutline(outline, c.ink, style = Stroke(2 * u))
            }
            PlanFinish.BRUTAL -> {
                translate(3.5f * u * lift, 3.5f * u * lift) { drawOutline(outline, if (c.dark) c.primary.copy(alpha = .9f) else c.ink) }
                drawOutline(outline, fill)
                drawOutline(outline, c.ink, style = Stroke(2.5f * u))
            }
            PlanFinish.CLAY -> {
                softShadow(outline, lerp(fill, Color.Black, .35f).copy(alpha = if (c.dark) .6f else .45f), 0f, 4 * u * lift, 5 * u * lift, 5)
                drawOutline(outline, Brush.verticalGradient(listOf(lerp(fill, Color.White, .28f), fill, lerp(fill, Color.Black, .14f))))
                if (!c.quiet) clipPath(path) {
                    drawOval(Color.White.copy(alpha = .42f), Offset(w * .2f, h * .07f), Size(w * .6f, h * .24f))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .14f)), h * .7f, h))
                }
            }
            PlanFinish.CHROME -> {
                drop()
                drawOutline(outline, Brush.verticalGradient(0f to lerp(fill, Color.White, .5f), .47f to lerp(fill, Color.White, .14f),
                    .5f to fill, 1f to lerp(fill, Color.Black, .24f)))
                drawOutline(outline, Brush.verticalGradient(listOf(Color.White, lerp(c.ink, c.paper.copy(alpha = 1f), .5f), Color.White.copy(alpha = .85f))), style = Stroke(2.2f * u))
                drawOutline(outline, Color.Black.copy(alpha = .3f), style = Stroke(.6f * u))
            }
            PlanFinish.SOFT -> {
                softShadow(outline, Color.Black.copy(alpha = if (c.dark) .55f else .18f), 3 * u * lift, 3 * u * lift, 4 * u, 4)
                softShadow(outline, Color.White.copy(alpha = if (c.dark) .07f else .95f), -3 * u, -3 * u, 4 * u, 4)
                drawOutline(outline, c.background.copy(alpha = 1f))
                drawOutline(outline, lerp(c.background, fill, .1f).copy(alpha = 1f), style = Stroke(2 * u))
            }
            PlanFinish.PAPER -> {
                translate(3.5f * u, 4 * u) { drawOutline(outline, lerp(fill, Color.Black, .32f)) }
                translate(1.75f * u, 2 * u) { drawOutline(outline, lerp(fill, c.paper.copy(alpha = 1f), .5f)) }
                drawOutline(outline, fill)
                drawOutline(outline, Color.White.copy(alpha = .35f), style = Stroke(.8f * u))
            }
            PlanFinish.STITCH -> {
                drop()
                drawOutline(outline, fill)
                if (!c.quiet) clipPath(path) { drawWeave(Color.Black.copy(alpha = .06f), max(2 * u, w / 16f)) }
                drawSeam(outline, lerp(fill, if (fill.luminance() > .45f) Color.Black else Color.White, .7f).copy(alpha = .85f), max(3 * u, w * .1f), 1.2f * u)
            }
            PlanFinish.OUTLINE -> {
                drawOutline(outline, c.paper.copy(alpha = 1f))
                drawOutline(outline, fill, style = Stroke(1.8f * u))
            }
            PlanFinish.PRINT -> {
                drawOutline(outline, c.paper.copy(alpha = 1f))
                drawOutline(outline, c.ink, style = Stroke(1.6f * u))
                withTransform({ scale((w - 7 * u) / w, (h - 7 * u) / h, center) }) { drawOutline(outline, fill, style = Stroke(.9f * u)) }
            }
            PlanFinish.TERMINAL -> {
                drawOutline(outline, lerp(c.paper.copy(alpha = 1f), fill, .08f))
                drawOutline(outline, fill, style = Stroke(1.4f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * u, 2.5f * u))))
            }
            PlanFinish.SWISS -> {
                drawOutline(outline, c.ink)
                drawRect(fill, Offset(w * .72f, -h * .06f), Size(w * .34f, h * .34f))
            }
        }
    }
}
