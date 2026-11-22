// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.widget.DotMatrixArtwork
import kotlin.math.*

/**
 * "Teletext / Videotext" as a real page of the broadcast service. Everything is set in
 * block pixels of one size (the matrix font with square cells), the station name stands double
 * height on a blue band, line numbers are cyan, destinations white, times in the board's accent
 * and delays red. Rows are separated by a mosaic rule of 2 × 3 block graphics, and the bottom of
 * the page carries the four coloured Fastext keys. No page number.
 */
internal fun drawTeletextBoard(c: Canvas, m: BoardPresentation, w: Float, h: Float, l: BoardLayout, fontScale: Float, headerAction: Boolean): Boolean {
    if (m.design != BoardDesign.TELETEXT) return false
    val bg = m.colors.background.toInt() or Color.BLACK
    val ink = m.colors.foreground.toInt() or Color.BLACK
    val accent = m.colors.accent.toInt() or Color.BLACK
    val cyan = 0xff3fe6f0.toInt(); val green = 0xff52e05a.toInt(); val red = 0xffff5a4c.toInt(); val blue = 0xff1d36c8.toInt()
    val yellow = 0xfffff04a.toInt(); val magenta = 0xffe85ce8.toInt()
    val pad = l.padding
    val scale = if (l.singleLine) min(1.1f, l.rowHeight / 20) else fontScale.coerceIn(1f, 2f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    c.drawColor(bg)

    /** Block-pixel text with its cap height [cap] from [top]; [tall] doubles the height only, like the real thing. */
    fun text(value: String, x: Float, top: Float, cap: Float, color: Int, width: Float, alignEnd: Boolean = false, tall: Boolean = false): Float {
        val dot = cap / MatrixFont.CAP
        val fitted = MatrixFont.fit(value, floor(width / dot).toInt().coerceAtLeast(1))
        if (fitted.isEmpty()) return 0f
        val bitmap = DotMatrixArtwork.text(fitted, color, square = true)
        val drawn = dot * bitmap.width / DotMatrixArtwork.DOT
        val left = if (alignEnd) x + width - drawn else x
        val height = dot * MatrixFont.ROWS * if (tall) 2f else 1f
        c.drawBitmap(bitmap, null, RectF(left, top, left + drawn, top + height), bitmapPaint)
        return drawn
    }

    /** A rule of 2 × 3 mosaic cells, alternating, as teletext pages draw their separators. */
    fun mosaic(y: Float, color: Int, cell: Float) {
        p.color = color
        var x = pad; var i = 0
        while (x + cell <= w - pad + .5f) {
            val sub = cell / 2f; val subH = cell / 3f
            // Upper-left and lower-right sixels in turn: a woven band.
            if (i % 2 == 0) { c.drawRect(x, y, x + sub, y + subH, p); c.drawRect(x + sub, y + 2 * subH, x + cell, y + 3 * subH, p) }
            else { c.drawRect(x + sub, y, x + cell, y + subH, p); c.drawRect(x, y + 2 * subH, x + sub, y + 3 * subH, p) }
            c.drawRect(x, y + subH, x + cell, y + 2 * subH, p.apply { alpha = 90 }); p.alpha = 255
            x += cell; i++
        }
    }

    // Faint scan lines of the television picture.
    p.color = ColorUtils.setAlphaComponent(Color.BLACK, 46)
    var sy = 0f
    while (sy < h) { c.drawRect(0f, sy, w, sy + .5f, p); sy += 2f }

    var top = pad
    if (!m.isDesignPreview) {
        // Title band: double-height station name on the blue background.
        val band = l.header - 2f
        p.color = blue; c.drawRect(0f, top - pad * .35f, w, top + band, p)
        val cap = min(band * .9f / (MatrixFont.ROWS * 2f / MatrixFont.CAP), 11f * scale)
        text(m.title, pad + 2f, top + (band - cap * MatrixFont.ROWS * 2 / MatrixFont.CAP) / 2f + 1f, cap, accent,
            w - 2 * pad - 4f - if (headerAction) 28f else 0f, tall = true)
        top += l.header
    }
    val rows = m.rows.take(l.rows)
    if (rows.isEmpty() && !m.isDesignPreview) text(m.emptyMessage, pad, top + 6f, 8f * scale, ink, w - 2 * pad)
    rows.forEachIndexed { index, row ->
        val rh = l.rowHeight - l.journeyLinkHeight
        val left = pad + 2f; val right = w - pad - 2f; val cw = right - left
        if (m.isDesignPreview) {
            val cap = min(rh * .5f, 12f * fontScale)
            text(row.destination, left, top + (rh - cap * MatrixFont.ROWS / MatrixFont.CAP) / 2f, cap, ink, cw)
            top += l.rowHeight; return@forEachIndexed
        }
        val cancelled = row.delay.cancelled
        val late = row.delay.minutes?.takeIf { it > 0 && !cancelled }?.let { "+$it" }
        val clock = if (cancelled) tr("Fällt aus") else clockTime(row.time)
        val detail = listOfNotNull(platformName(row.platform, row.lineType) ?: row.lineType.label).joinToString()
        val cap = min(9f * scale, rh * (if (l.singleLine) .62f else .30f))
        val small = cap * .78f
        val lineW = min(40f * scale, cw * .2f)
        val timeW = if (cancelled) min(MatrixFont.width(clock) * cap / MatrixFont.CAP + 2f, cw * .4f) else min(52f * scale, cw * .3f)
        if (l.singleLine || l.narrow) {
            val y0 = top + (rh - cap * MatrixFont.ROWS / MatrixFont.CAP) / 2f
            text(row.lineName, left, y0, cap, cyan, lineW - 3f)
            text(row.destination, left + lineW, y0, cap, ink, cw - lineW - timeW - 3f)
            text(if (late != null) "$clock $late" else clock, right - timeW, y0, cap, if (cancelled || late != null) red else accent, timeW, alignEnd = true)
        } else {
            val y0 = top + rh * .14f
            text(row.lineName, left, y0, cap, cyan, lineW - 3f)
            text(row.destination, left + lineW, y0, cap, ink, cw - lineW - timeW - 3f)
            text(clock, right - timeW, y0, cap, if (cancelled) red else accent, timeW, alignEnd = true)
            val y1 = y0 + cap * MatrixFont.ROWS / MatrixFont.CAP + 1.5f
            text(detail, left + lineW, y1, small, green, cw - lineW - timeW - 3f)
            if (late != null) text(late, right - timeW, y1, small, red, timeW, alignEnd = true)
        }
        // Mosaic separator between the rows, in the page's own blue.
        if (index < rows.lastIndex) mosaic(top + rh - 2.2f, ColorUtils.setAlphaComponent(blue, 210), 3f)
        top += l.rowHeight
    }
    if (!m.isDesignPreview) {
        // Fastext keys along the bottom edge, then the "as of" line above them.
        val keys = intArrayOf(red, green, yellow, cyan)
        val keyH = 2.4f; val seg = (w - 2 * pad) / keys.size
        keys.forEachIndexed { i, k -> p.color = k; c.drawRect(pad + i * seg + .8f, h - pad * .55f - keyH, pad + (i + 1) * seg - .8f, h - pad * .55f, p) }
        if (l.footer > 0) {
            val cap = min(7f * scale, l.footer * .55f)
            text(m.caption, pad + 2f, h - pad * .55f - keyH - 2f - cap * MatrixFont.ROWS / MatrixFont.CAP, cap, cyan, w - 2 * pad - 4f)
        }
        // The board's own menu: three magenta block dots stacked, as a teletext graphic.
        if (headerAction) { p.color = magenta; repeat(3) { i -> c.drawRect(w - 16f, pad + 3f + i * 5f, w - 13f, pad + 6f + i * 5f, p) } }
    }
    return true
}
