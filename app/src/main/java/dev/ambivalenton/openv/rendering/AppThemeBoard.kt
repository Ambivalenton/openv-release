// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.buildFullThemeBackdrop
import dev.ambivalenton.openv.ui.theme.drawDecorativeThemeTexture
import dev.ambivalenton.openv.ui.theme.readableForeground
import kotlin.math.*

/**
 * "App-Theme" board: no screen material at all. The board is one more card of the app –
 * same container colour as its footer, theme corners on every row tile, the theme's typeface, its
 * panel motif and, for system themes, the system pane and title bar.
 */
internal fun drawAppThemeBoard(c: Canvas, m: BoardPresentation, w: Float, h: Float, l: BoardLayout,
    fontScale: Float, headerAction: Boolean): Boolean {
    val theme = m.appStyle ?: return false
    val pal = m.appPalette ?: BoardAppPalette(m.colors.background.toInt(), m.colors.background.toInt(), m.colors.background.toInt(),
        m.colors.accent.toInt(), m.colors.background.toInt(), m.colors.foreground.toInt(), m.colors.accent.toInt(),
        m.colors.foreground.toInt(), m.colors.foreground.toInt(), m.colors.foreground.toInt(), 0xFFB3261E.toInt())
    val bg = pal.container
    val ink = m.colors.foreground.toInt() or Color.BLACK
    val accent = m.colors.accent.toInt() or Color.BLACK
    // The theme's own bundled typeface first (a chosen app font still wins), so the board reads like the app.
    val face = m.typeface ?: m.themeTypeface ?: when (theme.font) {
        ThemeFont.SERIF -> Typeface.SERIF
        ThemeFont.MONO -> Typeface.MONOSPACE
        ThemeFont.ROUNDED -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
        ThemeFont.SANS -> Typeface.SANS_SERIF
    }
    val radiusScale = if (theme.hasSquareCorners) 0f else theme.radiusScale * m.appCornerScale
    val pad = l.padding
    val scale = if (l.singleLine) min(1.1f, l.rowHeight / 20) else fontScale.coerceIn(1f, 2f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val text = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    fun write(value: String, x: Float, cy: Float, width: Float, size: Float, height: Float, color: Int,
        bold: Boolean = false, center: Boolean = false, end: Boolean = false) {
        if (width <= 1f || height <= 1f || value.isEmpty()) return
        text.reset(); text.isAntiAlias = true; text.color = color; text.textSize = size
        text.typeface = Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL)
        // Build 109: fitted and centred on the letters, not on the font's line box – typefaces with a
        // tall line box (Source Sans 3, Baloo 2, …) otherwise shrank to two thirds and sat too high.
        val fh = min(text.fontMetrics.descent - text.fontMetrics.ascent, text.textSize * LETTER_BOX)
        if (fh > height) text.textSize *= height / fh
        val shown = TextUtils.ellipsize(value, text, width, TextUtils.TruncateAt.END).toString()
        val measured = text.measureText(shown)
        val xx = when { center -> x + (width - measured) / 2; end -> x + width - measured; else -> x }
        c.drawText(shown, xx, letterBaseline(text, cy), text)
    }
    // Card body: container colour plus exactly the motif the app paints on its own panels.
    c.drawColor(bg)
    val systemPalette = SystemSurfacePalette(pal.background, pal.surface, pal.primary, pal.secondary, pal.onSurface)
    // The host card clips with the theme's 24 dp corner; the system rim follows exactly that contour.
    val cardRadius = 24f * radiusScale
    if (theme.isOperatingSystem) drawSystemPane(c, theme, w, h, systemPalette) { inset ->
        themeCornerPath(RectF(inset, inset, w - inset, h - inset), (cardRadius - inset).coerceAtLeast(0f), theme.shapeStyle) }
    else if (m.appDecoration && theme.texture != ThemeTexture.NONE)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(c), Size(w, h)) {
            drawDecorativeThemeTexture(theme.texture, androidx.compose.ui.graphics.Color(pal.secondary),
                buildFullThemeBackdrop(theme.texture, size), androidx.compose.ui.graphics.Color(bg))
        }
    var titleInk = accent
    if (!m.isDesignPreview) {
        if (theme.isOperatingSystem) {
            val headerText = readableForeground(ink.toLong() and 0xffffffffL, systemHeaderColor(theme, systemPalette).toLong() and 0xffffffffL).toInt()
            val chrome = systemPalette.copy(text = headerText)
            drawSystemHeader(c, theme, w, pad + l.header - 1, chrome)
            titleInk = systemHeaderForeground(theme, chrome)
        }
        write(m.title, pad + 4, pad + l.header * .45f, w - 2 * pad - 8 - if (headerAction) 28f else 0f, 15f * scale, l.header * .8f, titleInk, bold = true)
    }
    // Row tiles: theme surface, theme corners, the same gap rhythm as the app's lists.
    val tile = tileColor(pal, bg)
    val tileInk = readableOn(ink, tile)
    val tileMuted = readableOn(pal.onSurfaceVariant, tile)
    val tileAccent = readableOn(accent, tile)
    val delayInk = readableOn(pal.error, tile)
    val radius = 14f * radiusScale
    var top = pad + l.header
    val rows = m.rows.take(l.rows)
    if (rows.isEmpty() && !m.isDesignPreview) write(m.emptyMessage, pad + 6, (top + 16).coerceAtMost(h - pad - 8), w - 2 * pad - 12, 12 * scale, 26 * scale, ink)
    rows.forEachIndexed { index, row ->
        val rh = l.rowHeight - l.journeyLinkHeight
        val rect = RectF(pad, top + 1.5f, w - pad, top + rh - 1.5f)
        p.color = tile; p.style = Paint.Style.FILL
        c.drawPath(themeCornerPath(rect, min(radius, rect.height() / 2), theme.shapeStyle), p)
        if (theme.isOperatingSystem || pal.outlineVariant != tile) {
            p.style = Paint.Style.STROKE; p.strokeWidth = .7f; p.color = ColorUtils.setAlphaComponent(pal.outlineVariant, 110)
            c.drawPath(themeCornerPath(rect, min(radius, rect.height() / 2), theme.shapeStyle), p); p.style = Paint.Style.FILL
        }
        val left = rect.left + 6; val right = rect.right - 6; val cw = right - left
        val cy = rect.centerY()
        if (m.isDesignPreview) {
            write(row.destination, left, cy, cw, 16 * fontScale, rect.height() * .76f, tileInk, bold = true)
            top += l.rowHeight; return@forEachIndexed
        }
        val cancelled = row.delay.cancelled
        val clock = if (cancelled) tr("Fällt aus") else clockTime(row.time)
        val late = row.delay.minutes?.takeIf { it > 0 && !cancelled }?.let { "+$it" }.orEmpty()
        val detail = row.platform?.let { platformName(it, row.lineType) } ?: row.lineType.label
        val clockWidth = min(if (l.singleLine) 66 * scale else 76 * scale, cw * .32f)
        val badgeWidth = min(42 * scale, cw * .2f)
        fun badge(x: Float, y: Float, bw: Float, bh: Float) {
            val r = RectF(x, y, x + bw, y + bh)
            // Tones of the theme instead of the transport colours (same mapping as the in-app card).
            val (fill, label) = appBadgeColors(pal, tile, row.lineType)
            p.color = fill; c.drawPath(themeCornerPath(r, min(8f * radiusScale, bh / 2), theme.shapeStyle), p)
            write(row.lineName, x + 2, y + bh / 2, bw - 4, 11 * scale, bh * .9f, label, bold = true, center = true)
        }
        fun time(x: Float, mid: Float, width: Float, height: Float) {
            if (late.isEmpty()) write(clock, x, mid, width, 12.5f * scale, height, if (cancelled) delayInk else tileAccent, bold = true, end = true)
            else {
                val lateWidth = min(width * .38f, 25 * scale)
                write(clock, x, mid, width - lateWidth - 2, 12.5f * scale, height, tileAccent, bold = true, end = true)
                // The app's delay chip in miniature.
                val chip = RectF(x + width - lateWidth, mid - height * .32f, x + width, mid + height * .32f)
                p.color = ColorUtils.setAlphaComponent(delayInk, 38); c.drawPath(themeCornerPath(chip, min(6f * radiusScale, chip.height() / 2), theme.shapeStyle), p)
                write(late, chip.left + 1, mid, chip.width() - 2, 10 * scale, chip.height(), delayInk, bold = true, center = true)
            }
        }
        when {
            l.singleLine -> {
                badge(left, rect.top + rect.height() * .16f, badgeWidth - 5, rect.height() * .68f)
                write(row.destination, left + badgeWidth, cy, cw - badgeWidth - clockWidth - 4, 11.5f * scale, rect.height() * .8f, tileInk)
                time(right - clockWidth, cy, clockWidth, rect.height() * .8f)
            }
            l.narrow -> {
                badge(left, rect.top + rect.height() * .07f, badgeWidth, rect.height() * .26f)
                write(detail, left + badgeWidth + 6, rect.top + rect.height() * .2f, cw - badgeWidth - 6, 10 * scale, rect.height() * .26f, tileMuted)
                write(row.destination, left, rect.top + rect.height() * .5f, cw, 12 * scale, rect.height() * .3f, tileInk, bold = true)
                time(left, rect.top + rect.height() * .8f, min(cw, 110 * scale), rect.height() * .28f)
            }
            else -> {
                badge(left, rect.top + rect.height() * .2f, badgeWidth - 4, rect.height() * .6f)
                val tx = left + badgeWidth + 4; val tw = cw - badgeWidth - clockWidth - 10
                write(row.destination, tx, rect.top + rect.height() * .34f, tw, 12.5f * scale, rect.height() * .46f, tileInk, bold = true)
                write(detail, tx, rect.top + rect.height() * .72f, tw, 10 * scale, rect.height() * .34f, tileMuted)
                time(right - clockWidth, cy, clockWidth, rect.height() * .7f)
            }
        }
        top += l.rowHeight
    }
    if (!m.isDesignPreview && l.footer > 0)
        write(m.caption, pad + 4, h - pad - l.footer / 2, w - 2 * pad - 8, 10 * scale, l.footer * .85f, readableOn(pal.onSurfaceVariant, bg))
    if (headerAction) write("⋮", w - 26, 18f, 20f, 18f, 24f, titleInk, bold = true, center = true)
    return true
}

/** Badge fill and label of the "App-Theme" board, from the theme's own roles. */
internal fun appBadgeColors(pal: BoardAppPalette, tile: Int, type: LineType): Pair<Int, Int> {
    val fill = when (dev.ambivalenton.openv.ui.components.boardBadgeTone(type)) {
        dev.ambivalenton.openv.ui.components.BoardBadgeTone.PRIMARY -> pal.primaryContainer
        dev.ambivalenton.openv.ui.components.BoardBadgeTone.SECONDARY -> ColorUtils.blendARGB(tile, pal.secondary, .30f)
        dev.ambivalenton.openv.ui.components.BoardBadgeTone.TERTIARY -> ColorUtils.blendARGB(tile, pal.onSurface, .13f)
        dev.ambivalenton.openv.ui.components.BoardBadgeTone.NEUTRAL -> ColorUtils.blendARGB(tile, pal.outlineVariant, .5f)
    } or Color.BLACK
    val ink = if (fill == pal.primaryContainer or Color.BLACK) readableOn(pal.onPrimaryContainer, fill) else readableOn(pal.onSurface, fill)
    return fill to ink
}

/** The row tiles must read as separate surfaces on the card in every theme. */
private fun tileColor(pal: BoardAppPalette, container: Int): Int {
    var tile = pal.surface
    if (ColorUtils.calculateContrast(tile, container) < 1.12) {
        val lighter = ColorUtils.calculateLuminance(container) < .5
        tile = ColorUtils.blendARGB(container, if (lighter) Color.WHITE else Color.BLACK, .08f) or Color.BLACK
    }
    return tile
}

private fun readableOn(color: Int, background: Int) =
    readableForeground(color.toLong() and 0xffffffffL, (background or Color.BLACK).toLong() and 0xffffffffL).toInt() or Color.BLACK

/** Canvas twin of the app's corner shapes ([dev.ambivalenton.openv.ui.theme.corner]). */
internal fun themeCornerPath(r: RectF, radius: Float, style: ThemeShape): Path = Path().apply {
    val rr = radius.coerceIn(0f, min(r.width(), r.height()) / 2)
    when (style) {
        ThemeShape.ROUNDED -> addRoundRect(r, rr, rr, Path.Direction.CW)
        ThemeShape.ASYMMETRIC -> addRoundRect(r, floatArrayOf(rr, rr, rr * .25f, rr * .25f, rr, rr, rr * .25f, rr * .25f), Path.Direction.CW)
        ThemeShape.CUT, ThemeShape.BEVEL -> {
            // top-left, top-right, bottom-right, bottom-left
            val cuts = if (style == ThemeShape.CUT) floatArrayOf(rr * .55f, rr * .55f, rr * .55f, rr * .55f)
                else floatArrayOf(rr * .2f, rr * .7f, rr * .2f, rr * .7f)
            moveTo(r.left + cuts[0], r.top); lineTo(r.right - cuts[1], r.top); lineTo(r.right, r.top + cuts[1])
            lineTo(r.right, r.bottom - cuts[2]); lineTo(r.right - cuts[2], r.bottom); lineTo(r.left + cuts[3], r.bottom)
            lineTo(r.left, r.bottom - cuts[3]); lineTo(r.left, r.top + cuts[0]); close()
        }
        // The corner forms of both design systems (continuous, pebble, leaf, pixel, square).
        ThemeShape.SQUIRCLE -> addPath(dev.ambivalenton.openv.ui.theme.squirclePath(Size(r.width(), r.height()), rr, rr, rr, rr).asAndroidPath(), r.left, r.top)
        ThemeShape.PEBBLE, ThemeShape.LEAF -> {
            val f = if (style == ThemeShape.PEBBLE) floatArrayOf(1.35f, .55f, 1.2f, .7f) else floatArrayOf(1.5f, .22f, 1.5f, .22f)
            val limit = min(r.width(), r.height()) / 2
            val c = f.map { (radius * it).coerceIn(0f, limit) }
            addRoundRect(r, floatArrayOf(c[0], c[0], c[1], c[1], c[2], c[2], c[3], c[3]), Path.Direction.CW)
        }
        ThemeShape.PIXEL -> {
            val cut = rr * .45f
            moveTo(r.left + cut, r.top); lineTo(r.right - cut, r.top); lineTo(r.right, r.top + cut)
            lineTo(r.right, r.bottom - cut); lineTo(r.right - cut, r.bottom); lineTo(r.left + cut, r.bottom)
            lineTo(r.left, r.bottom - cut); lineTo(r.left, r.top + cut); close()
        }
        ThemeShape.SHARP -> addRect(r, Path.Direction.CW)
    }
}

/** Line height that letters with ascenders and descenders really need, in text sizes. */
internal const val LETTER_BOX = 1.24f
private val capBounds = Rect()
/** Baseline that centres the capital letters of [paint] on [cy], whatever line box the typeface declares. */
internal fun letterBaseline(paint: Paint, cy: Float): Float {
    synchronized(capBounds) { paint.getTextBounds("H", 0, 1, capBounds); return cy - capBounds.exactCenterY() }
}
