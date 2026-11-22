// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.readableForeground
import dev.ambivalenton.openv.widget.DotMatrixArtwork
import kotlin.math.*

/**
 * "Display" board. Static artwork only: every loop is bounded by the board size, nothing
 * animates, nothing is random between refreshes, and no extra bitmaps besides the cached LED glyphs.
 */
internal fun drawThemeDisplay(c: Canvas, m: BoardPresentation, w: Float, h: Float, l: BoardLayout,
    fontScale: Float, headerAction: Boolean): Boolean {
    val theme = m.appStyle ?: return false
    val d = displayProfile(theme)
    val bg = m.colors.background.toInt() or Color.BLACK
    val ink = m.colors.foreground.toInt() or Color.BLACK
    val accent = m.colors.accent.toInt() or Color.BLACK
    // Split-flap boards print details on the dark frame between the flaps.
    val muted = if (d.material == DisplayMaterial.SPLIT_FLAP) ColorUtils.blendARGB(bg, d.tint, .3f) else readableOn(d.muted, bg)
    val pad = l.padding
    val scale = if (l.singleLine) min(1.1f, l.rowHeight / 20) else fontScale.coerceIn(1f, 2f)
    val pen = DisplayPen(c, d, m.typeface)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    displayBackground(c, d, theme, w, h, l, bg, accent, m.appDecoration)
    // Header: title, plus the chrome that makes the screen recognisable (system bar, HUD, rules).
    var titleInk = accent
    if (!m.isDesignPreview) {
        titleInk = displayHeader(c, d, theme, w, pad, l.header, bg, ink, accent, paint)
        val reserve = if (headerAction) 28f else 0f
        pen.text(displayTitle(d, m.title), pad + 2, pad + l.header * .42f, w - 2 * pad - reserve - 4, 14.5f * scale,
            l.header * .74f, titleInk, bold = true, role = DisplayRole.TITLE)
    }
    var top = pad + l.header
    val rows = m.rows.take(l.rows)
    if (rows.isEmpty() && !m.isDesignPreview)
        pen.text(m.emptyMessage, pad + 4, (top + 16).coerceAtMost(h - pad - 8), w - 2 * pad - 8, 12 * scale, 26 * scale,
            if (d.material == DisplayMaterial.SPLIT_FLAP) muted else ink, role = DisplayRole.DETAIL)
    val delayInk = readableOn(if (d.light) 0xFFB3261E.toInt() else 0xFFFF8A70.toInt(), bg)
    rows.forEachIndexed { index, row ->
        val rh = l.rowHeight - l.journeyLinkHeight
        val left = pad + 4; val right = w - pad - 4; val cw = right - left
        c.save(); c.clipRect(pad - 2, top, w - pad + 2, top + rh)
        displayRowBackground(c, d, index, pad, top, w - pad, top + rh, bg, ink, accent, paint)
        if (m.isDesignPreview) {
            pen.text(row.destination, left, top + rh / 2, cw, 17 * fontScale, rh * .74f, ink, bold = true)
            c.restore(); top += l.rowHeight; return@forEachIndexed
        }
        val clockWidth = min(if (l.singleLine) 64 * scale else 74 * scale, cw * .32f)
        val lineWidth = min(46 * scale, cw * .2f)
        val cancelled = row.delay.cancelled
        val clock = if (cancelled) tr("Fällt aus") else clockTime(row.time)
        val late = row.delay.minutes?.takeIf { it > 0 && !cancelled }?.let { "+$it" }.orEmpty()
        val detail = row.platform?.let { platformName(it, row.lineType) } ?: row.lineType.label
        fun time(x: Float, cy: Float, width: Float, height: Float) {
            val timeInk = if (cancelled) delayInk else accent
            val segments = d.material == DisplayMaterial.VFD || d.material == DisplayMaterial.REFLECTIVE_LCD ||
                d.motif == DisplayMotif.HANDHELD
            if (segments && !cancelled) {
                val digitHeight = min(16 * scale, min(height * .8f, (width - if (late.isEmpty()) 0f else 18 * scale) / 2.85f))
                drawDisplayDigits(c, clock, x, cy - digitHeight / 2, digitHeight, timeInk, d.light)
                if (late.isNotEmpty()) pen.text(late, x + digitHeight * 2.9f, cy, width - digitHeight * 2.9f, 9.5f * scale, height, delayInk, bold = true)
            } else if (late.isNotEmpty()) {
                val lateWidth = min(width * .36f, 24 * scale)
                pen.text(clock, x, cy, width - lateWidth, 12.5f * scale, height, timeInk, bold = true, alignEnd = true)
                pen.text(late, x + width - lateWidth + 2, cy, lateWidth - 2, 10 * scale, height, delayInk, bold = true)
            } else pen.text(clock, x, cy, width, 12.5f * scale, height, timeInk, bold = true, alignEnd = true)
        }
        when {
            l.singleLine -> {
                displayBadge(c, pen, d, row, left, top + rh * .14f, lineWidth - 5, rh * .72f, 11 * scale, bg, ink, accent, paint)
                pen.text(row.destination, left + lineWidth, top + rh / 2, cw - lineWidth - clockWidth - 4, 11.5f * scale, rh * .8f, ink)
                time(right - clockWidth, top + rh / 2, clockWidth, rh * .8f)
            }
            l.narrow -> {
                displayBadge(c, pen, d, row, left, top + rh * .05f, lineWidth, rh * .26f, 10 * scale, bg, ink, accent, paint)
                pen.text(detail, left + lineWidth + 6, top + rh * .18f, cw - lineWidth - 6, 10 * scale, rh * .26f, muted, role = DisplayRole.DETAIL)
                pen.text(row.destination, left, top + rh * .48f, cw, 12 * scale, rh * .3f, ink, bold = true)
                time(left, top + rh * .8f, min(cw, 110 * scale), rh * .28f)
            }
            else -> {
                displayBadge(c, pen, d, row, left, top + rh * .18f, lineWidth - 6, rh * .64f, 11 * scale, bg, ink, accent, paint)
                val textX = left + lineWidth + 2
                val textW = cw - lineWidth - clockWidth - 8
                pen.text(row.destination, textX, top + rh * .33f, textW, 12.5f * scale, rh * .48f, ink, bold = true)
                pen.text(detail, textX, top + rh * .73f, textW, 10 * scale, rh * .36f, muted, role = DisplayRole.DETAIL)
                time(right - clockWidth, top + rh * .5f, clockWidth, rh * .7f)
            }
        }
        c.restore(); top += l.rowHeight
    }
    if (!m.isDesignPreview && l.footer > 0)
        pen.text(m.caption, pad + 2, h - pad - l.footer / 2, w - 2 * pad - 4 - if (headerAction) 28f else 0f, 10 * scale, l.footer * .85f, muted, role = DisplayRole.DETAIL)
    displayOverlay(c, d, w, h, bg, ink)
    if (headerAction) pen.text("⋮", w - 26, 18f, 20f, 18f, 24f, titleInk, bold = true, center = true, role = DisplayRole.SYMBOL)
    return true
}

internal enum class DisplayRole { TEXT, DETAIL, TITLE, BADGE, SYMBOL }

private fun readableOn(color: Int, background: Int, minimum: Double = 4.5) =
    readableForeground(color.toLong() and 0xffffffffL, (background or Color.BLACK).toLong() and 0xffffffffL, minimum).toInt() or Color.BLACK

/** Terminals show the stop as a command prompt; every other screen shows the plain name. */
private fun displayTitle(d: DisplayProfile, title: String) =
    if (d.material == DisplayMaterial.OS_WINDOW && d.face == DisplayFace.MONO) "> $title" else title

/** One measured paint for the whole board; LED and pixel faces draw the bundled 5×7 alphabet. */
internal class DisplayPen(private val canvas: Canvas, private val d: DisplayProfile, custom: Typeface?) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val face: Typeface = custom ?: when (d.face) {
        DisplayFace.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        DisplayFace.SERIF -> Typeface.SERIF
        DisplayFace.MONO, DisplayFace.DOTS, DisplayFace.PIXELS -> Typeface.MONOSPACE
        DisplayFace.ROUNDED -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
        DisplayFace.SANS -> Typeface.SANS_SERIF
    }
    private val glow = when (d.material) {
        DisplayMaterial.VFD -> 2.0f
        DisplayMaterial.CRT -> 1.6f
        DisplayMaterial.OLED -> .7f
        DisplayMaterial.STATION_TFT -> .35f
        else -> 0f
    }

    fun text(value: String, x: Float, cy: Float, width: Float, size: Float, height: Float, color: Int,
        bold: Boolean = false, center: Boolean = false, alignEnd: Boolean = false, role: DisplayRole = DisplayRole.TEXT) {
        if (width <= 1f || height <= 1f || value.isEmpty()) return
        if ((d.face == DisplayFace.DOTS || d.face == DisplayFace.PIXELS) && role != DisplayRole.SYMBOL) {
            dots(value, x, cy, width, min(size * .92f, height * .9f), color, center, alignEnd); return
        }
        paint.reset(); paint.isAntiAlias = true; paint.isSubpixelText = true
        paint.color = color; paint.textSize = size
        paint.typeface = Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL)
        if (d.material == DisplayMaterial.ENAMEL || d.motif == DisplayMotif.DECO_FAN) paint.letterSpacing = .03f
        val fh = paint.fontMetrics.descent - paint.fontMetrics.ascent
        if (fh > height) paint.textSize *= height / fh
        if (d.material == DisplayMaterial.SPLIT_FLAP && (role == DisplayRole.TEXT)) {
            flaps(value, x, cy, width, alignEnd); paint.clearShadowLayer(); return
        }
        val shown = TextUtils.ellipsize(value, paint, width, TextUtils.TruncateAt.END).toString()
        val measured = paint.measureText(shown)
        val xx = when { center -> x + (width - measured) / 2; alignEnd -> x + width - measured; else -> x }
        val baseline = cy - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
        canvas.save(); canvas.clipRect(x - 3, cy - height / 2 - 2, x + width + 3, cy + height / 2 + 2)
        when {
            d.material == DisplayMaterial.CHALKBOARD -> {
                // Chalk: a soft dusty pass beneath a slightly transparent stroke.
                paint.alpha = 70; canvas.drawText(shown, xx + .45f, baseline + .35f, paint)
                paint.alpha = 232; canvas.drawText(shown, xx, baseline, paint)
            }
            d.material == DisplayMaterial.REFLECTIVE_LCD || d.motif == DisplayMotif.HANDHELD || d.motif == DisplayMotif.POCKET -> {
                paint.setShadowLayer(.4f, .8f, 1f, 0x38000000); canvas.drawText(shown, xx, baseline, paint)
            }
            d.material == DisplayMaterial.PAPER && d.motif == DisplayMotif.POSTER && role == DisplayRole.TITLE -> {
                // Slight misregistration of a second ink, as on a riso or screen print.
                val second = paint.color; paint.color = ColorUtils.setAlphaComponent(d.tint, 190)
                canvas.drawText(shown, xx + 1.1f, baseline + .7f, paint); paint.color = second
                canvas.drawText(shown, xx, baseline, paint)
            }
            d.material == DisplayMaterial.PAINTING -> {
                val shade = if (d.light) 0x40FFFFFF else 0x66000000
                paint.setShadowLayer(1.3f, 0f, .6f, shade); canvas.drawText(shown, xx, baseline, paint)
            }
            glow > 0f -> {
                paint.setShadowLayer(glow, 0f, 0f, ColorUtils.setAlphaComponent(color, 150)); canvas.drawText(shown, xx, baseline, paint)
            }
            else -> canvas.drawText(shown, xx, baseline, paint)
        }
        paint.clearShadowLayer(); canvas.restore()
    }

    /** Every character on its own flap: fixed pitch, centred glyph and the split across the middle. */
    private fun flaps(value: String, x: Float, cy: Float, width: Float, alignEnd: Boolean) {
        val pitch = max(paint.measureText("M") * .86f + 1.2f, 2f)
        val count = floor(width / pitch).toInt().coerceIn(1, 60)
        val text = if (value.length > count) value.take((count - 1).coerceAtLeast(1)) + "." else value
        val start = if (alignEnd) x + width - text.length * pitch else x
        val top = cy - (paint.fontMetrics.descent - paint.fontMetrics.ascent) * .5f
        val bottom = cy + (paint.fontMetrics.descent - paint.fontMetrics.ascent) * .5f
        val baseline = cy - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
        val tile = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textAlign = Paint.Align.CENTER
        text.forEachIndexed { i, ch ->
            val l = start + i * pitch + .5f; val r = start + (i + 1) * pitch - .5f
            tile.color = Color.BLACK
            tile.shader = LinearGradient(0f, top, 0f, bottom, intArrayOf(ColorUtils.blendARGB(d.background, Color.WHITE, .25f),
                d.background, ColorUtils.blendARGB(d.background, Color.BLACK, .12f)), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(l, top, r, bottom, 1.2f, 1.2f, tile); tile.shader = null
            if (ch != ' ') canvas.drawText(ch.toString(), (l + r) / 2, baseline, paint)
            tile.color = ColorUtils.setAlphaComponent(Color.BLACK, 95)
            canvas.drawRect(l, cy - .3f, r, cy + .3f, tile)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun dots(raw: String, x: Float, cy: Float, width: Float, height: Float, color: Int, center: Boolean, alignEnd: Boolean) {
        // The hand-set matrix font (mixed case, umlauts); [height] is the cap height.
        val dot = height / MatrixFont.CAP
        val fitted = MatrixFont.fit(raw, floor(width / dot).toInt().coerceAtLeast(1))
        if (fitted.isBlank()) return
        val bitmap = DotMatrixArtwork.text(fitted, color, d.face == DisplayFace.PIXELS)
        val drawnWidth = dot * bitmap.width / DotMatrixArtwork.DOT
        val xx = when { center -> x + (width - drawnWidth) / 2; alignEnd -> x + width - drawnWidth; else -> x }
        val top = cy - height / 2
        val bottom = top + dot * MatrixFont.ROWS
        if (d.material == DisplayMaterial.LED_MATRIX || d.material == DisplayMaterial.GAME && d.motif != DisplayMotif.POCKET) {
            // Light bleeding around lit LEDs or phosphor pixels.
            bitmapPaint.colorFilter = null; bitmapPaint.alpha = 70
            canvas.drawBitmap(bitmap, null, RectF(xx - .6f, top - .6f, xx + drawnWidth + .6f, bottom + .6f), bitmapPaint)
        }
        bitmapPaint.alpha = 255
        canvas.drawBitmap(bitmap, null, RectF(xx, top, xx + drawnWidth, bottom), bitmapPaint)
    }
}

private fun Paint.solid(color: Int, alpha: Int = 255): Paint {
    shader = null; style = Paint.Style.FILL; this.color = ColorUtils.setAlphaComponent(color, alpha.coerceIn(0, 255)); return this
}
private fun Paint.line(color: Int, width: Float, alpha: Int = 255): Paint {
    shader = null; style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND
    this.color = ColorUtils.setAlphaComponent(color, alpha.coerceIn(0, 255)); return this
}
/** Deterministic pseudo-random value in 0..1; the same board looks identical after every refresh. */
private fun noise(i: Int, salt: Int): Float = (((i * 1103515245L + salt * 12345L + 9876L) and 0x7fffffffL) % 10000L) / 10000f

private fun displayBackground(c: Canvas, d: DisplayProfile, theme: AppTheme, w: Float, h: Float, l: BoardLayout,
    bg: Int, accent: Int, decoration: Boolean) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val u = min(w, h)
    fun vertical(top: Int, bottom: Int) { p.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p); p.shader = null }
    fun artwork(alpha: Int) {
        if (!decoration || theme.texture == ThemeTexture.NONE) return
        c.saveLayerAlpha(0f, 0f, w, h, alpha)
        drawAdaptiveBoardBackdrop(c, theme, w, h, d.tint, bg)
        c.restore()
    }
    when (d.material) {
        DisplayMaterial.OLED -> {
            vertical(ColorUtils.blendARGB(bg, Color.WHITE, .035f), ColorUtils.blendARGB(bg, Color.BLACK, .45f))
            if (d.motif == DisplayMotif.ARTWORK) artwork(96)
        }
        DisplayMaterial.LIGHT_LCD -> {
            vertical(ColorUtils.blendARGB(bg, Color.WHITE, .5f), bg)
            if (d.motif == DisplayMotif.ARTWORK) artwork(150)
        }
        DisplayMaterial.E_PAPER -> {
            c.drawColor(bg)
            if (d.motif == DisplayMotif.ARTWORK) artwork(110)
        }
        DisplayMaterial.STATION_TFT -> vertical(ColorUtils.blendARGB(bg, Color.WHITE, .07f), ColorUtils.blendARGB(bg, Color.BLACK, .25f))
        DisplayMaterial.LED_MATRIX -> {
            c.drawColor(bg)
            if (d.motif == DisplayMotif.CONCRETE) {
                // Board cast into raw concrete: large, soft formwork panels instead of speckles.
                repeat(4) { i -> p.solid(d.tint, 20 + i * 4); c.drawRect(i * w / 4 + 1, 0f, (i + 1) * w / 4 - 1, h, p) }
                p.solid(Color.BLACK, 255); c.drawRect(5f, l.padding + l.header - 2, w - 5f, h - 5f, p)
            }
            // Unlit LEDs on a regular pitch – recognisable as a panel, not as stray pixels.
            p.solid(d.ink, 24); val step = 3.2f
            var y = l.padding + l.header + 1f
            while (y < h - 4) { var x = 7f; while (x < w - 6) { c.drawCircle(x, y, .62f, p); x += step }; y += step }
        }
        DisplayMaterial.VFD -> {
            p.shader = RadialGradient(w * .45f, h * .4f, max(w, h) * .8f, intArrayOf(ColorUtils.blendARGB(bg, accent, .07f), bg),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p); p.shader = null
        }
        DisplayMaterial.CRT -> {
            c.drawColor(bg)
            if (d.motif == DisplayMotif.OSCILLOSCOPE) {
                p.line(d.ink, .6f, 34)
                for (i in 1..9) c.drawLine(w * i / 10, 0f, w * i / 10, h, p)
                for (i in 1..7) c.drawLine(0f, h * i / 8, w, h * i / 8, p)
                p.line(d.ink, 1.2f, 70); val path = Path()
                for (i in 0..120) { val x = w * i / 120; val y = h * .86f - sin(i / 120f * PI.toFloat() * 7) * h * .045f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                c.drawPath(path, p)
            }
        }
        DisplayMaterial.CHALKBOARD -> {
            vertical(ColorUtils.blendARGB(bg, Color.WHITE, .04f), ColorUtils.blendARGB(bg, Color.BLACK, .18f))
            // Wiped chalk: a few broad, faint eraser arcs.
            p.line(Color.WHITE, u * .16f, 7)
            repeat(3) { i -> c.drawArc(RectF(-w * .2f + i * w * .35f, h * (.15f + .25f * i), w * (.5f + i * .35f), h * (.95f + .2f * i)), 200f, 80f, false, p) }
        }
        DisplayMaterial.LIGHTBOX -> {
            p.shader = RadialGradient(w * .5f, h * .45f, max(w, h) * .75f, intArrayOf(ColorUtils.blendARGB(bg, Color.WHITE, .7f), bg,
                ColorUtils.blendARGB(bg, Color.BLACK, .06f)), floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
        }
        DisplayMaterial.REFLECTIVE_LCD -> vertical(ColorUtils.blendARGB(bg, Color.WHITE, .12f), ColorUtils.blendARGB(bg, Color.BLACK, .06f))
        DisplayMaterial.ENAMEL -> {
            vertical(ColorUtils.blendARGB(bg, Color.WHITE, .22f), ColorUtils.blendARGB(bg, Color.BLACK, .05f))
            if (d.motif == DisplayMotif.HAZARD) {
                val band = 7f
                c.save(); c.clipRect(0f, h - band, w, h)
                p.solid(d.tint); var x = -h; while (x < w + band * 2) {
                    c.drawPath(Path().apply { moveTo(x, h); lineTo(x + band, h - band); lineTo(x + band * 2, h - band); lineTo(x + band, h); close() }, p); x += band * 2 }
                c.restore()
            }
            // Four enamel screw heads.
            for ((sx, sy) in listOf(7f to 7f, w - 7f to 7f, 7f to h - 14f, w - 7f to h - 14f)) {
                p.shader = RadialGradient(sx - .8f, sy - .8f, 3.4f, Color.WHITE, 0xFF8A8576.toInt(), Shader.TileMode.CLAMP)
                c.drawCircle(sx, sy, 2.6f, p); p.shader = null
                p.line(0xFF5B574C.toInt(), .7f); c.drawLine(sx - 1.6f, sy - 1.6f, sx + 1.6f, sy + 1.6f, p)
            }
        }
        DisplayMaterial.SPLIT_FLAP -> {
            c.drawColor(d.tint)
            p.solid(Color.WHITE, 10); c.drawRect(0f, 0f, w, l.padding + l.header, p)
        }
        DisplayMaterial.PAPER -> paperBackground(c, d, w, h, l, bg, accent, p)
        DisplayMaterial.PAINTING -> paintingBackground(c, d, w, h, l, bg, accent, p)
        DisplayMaterial.GAME -> gameBackground(c, d, w, h, l, bg, accent, p)
        DisplayMaterial.OS_WINDOW -> c.drawColor(bg)
    }
    when (d.motif) {
        DisplayMotif.SPECTRUM -> {
            val colors = intArrayOf(0xFF6A3FB5.toInt(), 0xFF2F6FD6.toInt(), 0xFF22A6A0.toInt(), 0xFF7CC24A.toInt(), 0xFFF2C230.toInt(),
                0xFFF0872F.toInt(), 0xFFD93A3A.toInt())
            p.shader = LinearGradient(0f, 0f, w, 0f, colors, null, Shader.TileMode.CLAMP)
            c.drawRect(0f, h - 3.5f, w, h, p); c.drawRect(l.padding, l.padding + l.header - 2.4f, w - l.padding, l.padding + l.header - 1f, p)
            p.shader = null
        }
        DisplayMotif.FLAT -> {
            val colors = intArrayOf(0xFF1ABC9C.toInt(), 0xFFF39C12.toInt(), 0xFFE74C3C.toInt(), 0xFF3498DB.toInt())
            colors.forEachIndexed { i, color -> p.solid(color, 46); c.drawCircle(w * (.92f - i * .07f), h * (.08f + i * .02f), u * (.20f - i * .035f), p) }
        }
        else -> Unit
    }
}

private fun paperBackground(c: Canvas, d: DisplayProfile, w: Float, h: Float, l: BoardLayout, bg: Int, accent: Int, p: Paint) {
    c.drawColor(bg)
    val ink = d.ink
    when (d.motif) {
        DisplayMotif.GREENBAR -> {
            // Continuous-form printer paper: green bands, tractor holes and perforation.
            var y = l.padding + l.header; var band = 0
            while (y < h) { if (band % 2 == 0) { p.solid(d.tint); c.drawRect(14f, y, w - 14f, y + l.rowHeight, p) }; y += l.rowHeight; band++ }
            p.solid(ColorUtils.blendARGB(bg, Color.BLACK, .2f))
            var hole = 7f; while (hole < h) { c.drawCircle(6.5f, hole, 2.2f, p); c.drawCircle(w - 6.5f, hole, 2.2f, p); hole += 10f }
            p.line(ColorUtils.blendARGB(bg, Color.BLACK, .25f), .6f); p.pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
            c.drawLine(13f, 0f, 13f, h, p); c.drawLine(w - 13f, 0f, w - 13f, h, p); p.pathEffect = null
        }
        DisplayMotif.WHITEPRINT -> {
            p.line(d.tint, .5f, 50); var x = 0f; while (x < w) { c.drawLine(x, 0f, x, h, p); x += 8f }
            var y = 0f; while (y < h) { c.drawLine(0f, y, w, y, p); y += 8f }
            p.line(d.tint, .8f, 110); x = 0f; while (x < w) { c.drawLine(x, 0f, x, h, p); x += 40f }
            y = 0f; while (y < h) { c.drawLine(0f, y, w, y, p); y += 40f }
            p.line(ink, 1f, 170); c.drawRect(3f, 3f, w - 3f, h - 3f, p)
            if (l.footer > 0) c.drawRect(w * .58f, h - l.padding - l.footer - 2, w - 3f, h - 3f, p)
        }
        DisplayMotif.STAR_CHART -> {
            val cx = w * .5f; val cy = h * 1.25f
            p.line(d.tint, .7f, 90)
            for (r in 1..6) c.drawCircle(cx, cy, h * (.35f + r * .22f), p)
            for (a in 0 until 12) { val t = PI / 12 * a + PI; c.drawLine(cx, cy, cx + cos(t).toFloat() * w * 2, cy + sin(t).toFloat() * w * 2, p) }
            // Constellation: proper star glyphs joined by lines.
            val stars = listOf(.80f to .16f, .88f to .30f, .75f to .38f, .93f to .50f)
            p.line(accent, .7f, 120); stars.zipWithNext().forEach { (a, b) -> c.drawLine(w * a.first, h * a.second, w * b.first, h * b.second, p) }
            stars.forEachIndexed { i, (sx, sy) -> star(c, w * sx, h * sy, if (i % 2 == 0) 3.6f else 2.6f, ink, p) }
        }
        DisplayMotif.CHART_RECORDER -> {
            p.line(d.tint, .5f, 120); var x = 0f; while (x < w) { c.drawLine(x, 0f, x, h, p); x += 6f }
            var y = 0f; while (y < h) { c.drawLine(0f, y, w, y, p); y += 6f }
            p.line(d.tint, .9f, 200); x = 0f; while (x < w) { c.drawLine(x, 0f, x, h, p); x += 30f }
            y = 0f; while (y < h) { c.drawLine(0f, y, w, y, p); y += 30f }
            p.line(accent, 1.1f, 150); val trace = Path(); val base = h - l.padding - l.footer - 4
            for (i in 0..160) { val xx = w * i / 160; val pulse = if (i % 23 in 0..1) -h * .06f else 0f
                val yy = base + pulse + sin(i * .9f) * .8f; if (i == 0) trace.moveTo(xx, yy) else trace.lineTo(xx, yy) }
            c.drawPath(trace, p)
        }
        DisplayMotif.CONTACT_SHEET -> {
            // Film strips with sprocket holes along both edges of a contact print.
            for (x in listOf(0f, w - 11f)) {
                p.solid(0xFF161616.toInt()); c.drawRect(x, 0f, x + 11f, h, p)
                p.solid(bg); var y = 3f; while (y < h) { c.drawRoundRect(x + 3f, y, x + 8f, y + 3.6f, .8f, .8f, p); y += 7f }
            }
        }
        DisplayMotif.WASHI -> {
            p.line(ColorUtils.blendARGB(bg, Color.BLACK, .18f), .5f, 90)
            repeat(14) { i -> val y = h * noise(i, 3); c.drawLine(w * noise(i, 5) - 30, y, w * noise(i, 5) + 40, y + 6 * (noise(i, 7) - .5f), p) }
            // Seigaiha waves along the foot in indigo.
            p.line(d.tint, .9f, 120); val r = 9f; var row = 0
            var y = h - 2f; while (y > h - 24f) { var x = if (row % 2 == 0) 0f else r; while (x < w + r) {
                for (k in 1..3) c.drawArc(RectF(x - r * k / 3, y - r * k / 3, x + r * k / 3, y + r * k / 3), 180f, 180f, false, p); x += r * 2 }
                y -= r * .55f; row++ }
            p.solid(accent, 200); val s = 13f; c.drawRect(w - l.padding - s - 2, l.padding + 2, w - l.padding - 2, l.padding + 2 + s, p)
            p.line(bg, 1f); c.drawRect(w - l.padding - s + 1.5f, l.padding + 5.5f, w - l.padding - 5.5f, l.padding - 1.5f + s, p)
        }
        DisplayMotif.AIRMAIL -> {
            val band = 6f
            c.save(); val inner = Path().apply { addRect(band, band, w - band, h - band, Path.Direction.CW) }
            c.clipOutPath(inner)
            var k = 0; var x = -h
            while (x < w + h) { p.solid(if (k % 2 == 0) accent else d.tint)
                c.drawPath(Path().apply { moveTo(x, 0f); lineTo(x + 10f, 0f); lineTo(x + 10f + h, h); lineTo(x + h, h); close() }, p); x += 10f; k++ }
            c.restore()
            val sx = w - l.padding - 30f; val sy = l.padding + 3f
            p.solid(0xFFF2E6CC.toInt()); c.drawRect(sx, sy, sx + 22f, sy + 16f, p)
            p.solid(bg); var px = sx; while (px <= sx + 22f) { c.drawCircle(px, sy, 1.2f, p); c.drawCircle(px, sy + 16f, 1.2f, p); px += 3.7f }
            p.solid(accent, 170); c.drawRect(sx + 3f, sy + 3f, sx + 19f, sy + 13f, p)
        }
        DisplayMotif.STAMP_ALBUM -> {
            // Stock-book page: transparent glassine strips carry the rows.
            var y = l.padding + l.header; while (y + l.rowHeight <= h - l.footer + 1) {
                p.solid(Color.WHITE, 18); c.drawRect(5f, y + 2, w - 5f, y + l.rowHeight - 1, p)
                p.solid(Color.WHITE, 40); c.drawRect(5f, y + 2, w - 5f, y + 3f, p); y += l.rowHeight }
            val sx = w - l.padding - 28f; val sy = l.padding + 1f
            p.solid(d.tint); c.drawRect(sx, sy, sx + 22f, sy + 17f, p)
            p.solid(bg); var px = sx; while (px <= sx + 22f) { c.drawCircle(px, sy, 1.3f, p); c.drawCircle(px, sy + 17f, 1.3f, p); px += 3.6f }
            p.solid(accent, 220); c.drawCircle(sx + 11f, sy + 8.5f, 4.2f, p)
        }
        DisplayMotif.POSTER -> {
            // Print sheet: a big second-colour shape, crop marks, registration targets and a colour bar.
            p.solid(d.tint, 90); c.drawCircle(w * .96f, h * .02f, min(w, h) * .38f, p)
            p.solid(accent, 60); c.drawCircle(w * .06f, h * 1.02f, min(w, h) * .28f, p)
            p.line(d.ink, .7f, 200)
            for ((x, y) in listOf(4f to 4f, w - 4f to 4f, 4f to h - 4f, w - 4f to h - 4f)) {
                val sx = if (x < w / 2) 1f else -1f; val sy = if (y < h / 2) 1f else -1f
                c.drawLine(x, y + sy * 3, x + sx * 9, y + sy * 3, p); c.drawLine(x + sx * 3, y, x + sx * 3, y + sy * 9, p)
            }
            val ty = h - 9f; val tx = w * .5f; c.drawCircle(tx, ty, 3.4f, p); c.drawLine(tx - 6, ty, tx + 6, ty, p); c.drawLine(tx, ty - 6, tx, ty + 6, p)
            listOf(0xFF00AEEF.toInt(), 0xFFEC008C.toInt(), 0xFFFFF200.toInt(), 0xFF111111.toInt()).forEachIndexed { i, color ->
                p.solid(color); c.drawRect(w - 54f + i * 10f, h - 12f, w - 46f + i * 10f, h - 6f, p) }
        }
        DisplayMotif.STAVES -> {
            p.line(d.tint, .55f, 110)
            var y = l.padding + l.header; while (y + l.rowHeight <= h) {
                for (k in 0..4) { val yy = y + l.rowHeight * (.2f + k * .15f); c.drawLine(4f, yy, w - 4f, yy, p) }; y += l.rowHeight }
            p.solid(d.ink, 150); val nx = w - l.padding - 34f; val ny = l.padding + l.header * .55f
            for (k in 0..2) { c.drawOval(nx + k * 10f, ny + 3 - k * 2, nx + k * 10f + 5f, ny + 6.5f - k * 2, p)
                p.line(d.ink, .9f, 150); c.drawLine(nx + k * 10f + 5f, ny + 4.5f - k * 2, nx + k * 10f + 5f, ny - 9 - k * 2, p); p.solid(d.ink, 150) }
        }
        else -> Unit
    }
}

private fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int, p: Paint) {
    p.solid(color)
    c.drawPath(Path().apply {
        moveTo(x, y - r); quadTo(x, y, x + r, y); quadTo(x, y, x, y + r); quadTo(x, y, x - r, y); quadTo(x, y, x, y - r); close()
    }, p)
}

private fun paintingBackground(c: Canvas, d: DisplayProfile, w: Float, h: Float, l: BoardLayout, bg: Int, accent: Int, p: Paint) {
    val u = min(w, h)
    fun gradient(vararg colors: Int) { p.shader = LinearGradient(0f, 0f, 0f, h, colors, null, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p); p.shader = null }
    when (d.motif) {
        DisplayMotif.OIL_PANEL -> {
            p.shader = RadialGradient(w * .32f, h * .35f, max(w, h) * .85f, intArrayOf(ColorUtils.blendARGB(bg, d.tint, .55f), bg, 0xFF0F0B07.toInt()),
                floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p); p.shader = null
            // Arcade in the background, as in an altar panel, then a fine craquelure of the varnish.
            p.line(accent, 1.4f, 46); val ax = w * .78f; val ar = u * .26f
            c.drawArc(RectF(ax - ar, h * .18f, ax + ar, h * .18f + ar * 2), 180f, 180f, false, p)
            c.drawLine(ax - ar, h * .18f + ar, ax - ar, h, p); c.drawLine(ax + ar, h * .18f + ar, ax + ar, h, p)
            p.line(Color.BLACK, .45f, 40)
            repeat(26) { i -> val x = w * noise(i, 11); val y = h * noise(i, 13); val a = noise(i, 17) * 6.28f
                c.drawLine(x, y, x + cos(a) * 14, y + sin(a) * 9, p) }
            p.shader = LinearGradient(0f, 0f, w * .6f, h * .6f, 0x16FFF2D0, 0x00FFF2D0, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p); p.shader = null
        }
        DisplayMotif.FRESCO_SKY -> {
            gradient(0xFFB9D0E3.toInt(), ColorUtils.blendARGB(bg, 0xFFDCE6EC.toInt(), .4f), 0xFFF2E6D0.toInt())
            for ((cx, cy, r) in listOf(Triple(.12f, .9f, .22f), Triple(.3f, 1.02f, .2f), Triple(.88f, .12f, .2f), Triple(.98f, .3f, .16f), Triple(.7f, -.04f, .18f))) {
                repeat(4) { k -> val rr = u * r * (1 - k * .18f); val x = w * cx + (k - 1.5f) * rr * .6f; val y = h * cy - k % 2 * rr * .25f
                    p.shader = RadialGradient(x, y, rr, intArrayOf(0xD0FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                    c.drawCircle(x, y, rr, p); p.shader = null }
            }
            // Gilded stucco frame.
            p.line(d.tint, 1.6f, 200); c.drawRect(3f, 3f, w - 3f, h - 3f, p)
            p.line(d.tint, .7f, 160); c.drawRect(6.5f, 6.5f, w - 6.5f, h - 6.5f, p)
        }
        DisplayMotif.MISTY_DAWN -> {
            gradient(0xFFC3CEDC.toInt(), ColorUtils.blendARGB(bg, 0xFFE4DDD8.toInt(), .5f), 0xFFF2E1CF.toInt())
            p.shader = RadialGradient(w * .8f, h * .3f, u * .3f, intArrayOf(0xB0FFF6E8.toInt(), 0x00FFF6E8), null, Shader.TileMode.CLAMP)
            c.drawCircle(w * .8f, h * .3f, u * .3f, p); p.shader = null
            listOf(.62f to 70, .72f to 100, .84f to 130).forEachIndexed { i, (base, alpha) ->
                p.solid(ColorUtils.blendARGB(d.tint, bg, .25f), alpha)
                c.drawPath(Path().apply { moveTo(0f, h); lineTo(0f, h * base)
                    for (k in 0..8) lineTo(w * k / 8, h * base - (noise(k + i * 9, 21) * .14f + .02f) * h)
                    lineTo(w, h); close() }, p)
            }
            gradient(0x00FFFFFF, 0x00FFFFFF, 0x55F7EFE6)
        }
        DisplayMotif.NOCTURNE -> {
            gradient(ColorUtils.blendARGB(bg, 0xFF213B57.toInt(), .45f), bg, ColorUtils.blendARGB(bg, 0xFF0B1D1C.toInt(), .5f))
            val dabs = intArrayOf(0xFF6FA38B.toInt(), 0xFF3F7A86.toInt(), 0xFF8C7DB8.toInt(), 0xFF9CC38A.toInt(), 0xFF2F5D6B.toInt())
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            repeat(90) { i -> val x = w * noise(i, 31); val y = h * (.45f + .55f * noise(i, 37)); p.strokeWidth = 2.5f + 2 * noise(i, 41)
                p.color = ColorUtils.setAlphaComponent(dabs[i % dabs.size], 55 + (noise(i, 43) * 50).toInt())
                c.drawLine(x, y, x + 6 + 8 * noise(i, 47), y + 1.5f * (noise(i, 53) - .5f), p) }
            repeat(16) { i -> p.strokeWidth = 2.2f; p.color = ColorUtils.setAlphaComponent(accent, 30 + i * 2)
                val y = h * (.35f + i * .04f); c.drawLine(w * .84f - 3 * noise(i, 59), y, w * .84f + 6 + 4 * noise(i, 61), y, p) }
            p.style = Paint.Style.FILL
            for ((x, y) in listOf(.18f to .9f, .42f to .97f, .64f to .88f)) {
                p.solid(0xFF4E8A5C.toInt(), 120); c.drawOval(w * x - 13, h * y - 4, w * x + 13, h * y + 4, p)
                p.solid(0xFFE9A8C4.toInt(), 150); c.drawCircle(w * x + 3, h * y - 2, 2.6f, p) }
            p.shader = RadialGradient(w * .84f, h * .14f, 12f, intArrayOf(0xF0F8EBC0.toInt(), 0x00F8EBC0), null, Shader.TileMode.CLAMP)
            c.drawCircle(w * .84f, h * .14f, 12f, p); p.shader = null
        }
        DisplayMotif.NOUVEAU -> {
            gradient(ColorUtils.blendARGB(bg, Color.WHITE, .05f), bg, ColorUtils.blendARGB(bg, Color.BLACK, .3f))
            p.line(d.tint, 1.3f, 170)
            c.drawPath(Path().apply { moveTo(5f, h - 4f); cubicTo(5f, h * .5f, w * .12f, h * .5f, 8f, h * .22f); cubicTo(4f, h * .08f, w * .2f, 4f, w * .5f, 5f)
                cubicTo(w * .8f, 4f, w - 4f, h * .08f, w - 8f, h * .22f); cubicTo(w * .88f, h * .5f, w - 5f, h * .5f, w - 5f, h - 4f) }, p)
            p.line(d.tint, .6f, 120); c.drawRect(10f, 10f, w - 10f, h - 10f, p)
            // An iris in whiplash lines, bottom right.
            val ix = w * .9f; val iy = h * .84f
            p.solid(d.tint, 70)
            for (k in 0..2) { c.save(); c.rotate(-40f + k * 40f, ix, iy); c.drawOval(ix - 4, iy - 22, ix + 4, iy, p); c.restore() }
            p.line(d.tint, 1.2f, 140); c.drawPath(Path().apply { moveTo(ix, iy); cubicTo(ix - 4, iy + 10, ix + 6, iy + 14, ix - 2, h) }, p)
        }
        DisplayMotif.COLOR_FIELDS -> {
            c.drawColor(bg)
            fun field(color: Int, alpha: Int, vararg pts: Float) { p.solid(color, alpha)
                c.drawPath(Path().apply { moveTo(w * pts[0], h * pts[1]); var i = 2; while (i < pts.size) { lineTo(w * pts[i], h * pts[i + 1]); i += 2 }; close() }, p) }
            field(0xFF1F3FA8.toInt(), 70, .72f, -.05f, 1.05f, -.05f, 1.05f, .38f)
            field(0xFFE8742A.toInt(), 80, .84f, -.05f, 1.05f, .14f, .9f, .26f)
            field(0xFFC9302C.toInt(), 60, -.05f, .78f, .3f, 1.05f, -.05f, 1.05f)
            field(0xFFF2C230.toInt(), 80, -.05f, .9f, .12f, 1.05f, -.05f, 1.05f)
            field(0xFF2F8F5B.toInt(), 50, .55f, 1.05f, .78f, .9f, .95f, 1.05f)
            p.line(0xFF151515.toInt(), 2.2f, 120); c.drawLine(w * .7f, -2f, w * 1.02f, h * .42f, p); c.drawLine(-2f, h * .74f, w * .32f, h * 1.02f, p)
        }
        DisplayMotif.RED_WEDGE -> {
            c.drawColor(bg)
            p.solid(d.tint, 150); c.drawPath(Path().apply { moveTo(-2f, h * .72f); lineTo(w * .36f, h * .9f); lineTo(-2f, h * 1.04f); close() }, p)
            p.solid(0xFFEFE6D2.toInt(), 46); c.drawCircle(w * .9f, h * .12f, u * .22f, p)
            p.line(0xFFEFE6D2.toInt(), .8f, 70); c.drawLine(w * .55f, h, w, h * .55f, p); c.drawLine(w * .62f, h, w, h * .62f, p)
            p.solid(d.tint, 200); c.drawRect(l.padding, l.padding + l.header - 3, l.padding + 26f, l.padding + l.header - 1, p)
        }
        DisplayMotif.SURREAL_DUSK -> {
            gradient(0xFF131A33.toInt(), bg, ColorUtils.blendARGB(bg, d.tint, .75f))
            p.solid(Color.BLACK, 70); c.drawPath(Path().apply { moveTo(0f, h); lineTo(w * .18f, h * .78f); lineTo(w * .7f, h * .78f); lineTo(w, h); close() }, p)
            p.solid(0xFF0D1022.toInt(), 160); val ax = w * .06f; c.drawRect(ax, h * .62f, ax + 18f, h * .8f, p)
            c.drawArc(RectF(ax, h * .56f, ax + 18f, h * .68f), 180f, 180f, true, p)
            val sx = w * .86f; val sy = h * .16f; val r = u * .07f
            p.shader = RadialGradient(sx - r * .35f, sy - r * .35f, r * 1.3f, intArrayOf(0xFFFFF4DE.toInt(), 0xFFC9A6C8.toInt()), null, Shader.TileMode.CLAMP)
            c.drawCircle(sx, sy, r, p); p.shader = null
            p.solid(Color.BLACK, 60); c.drawOval(sx - r * 1.2f, h * .79f, sx + r * 1.8f, h * .79f + r * .5f, p)
        }
        DisplayMotif.BENDAY -> {
            c.drawColor(bg)
            p.solid(d.tint, 150); val step = 7f
            var y = 0f; var row = 0
            while (y < h + step) { var x = if (row % 2 == 0) 0f else step / 2
                while (x < w + step) { val t = ((x / w) + (1 - y / h) - .9f).coerceIn(0f, 1f); if (t > 0f) c.drawCircle(x, y, 2.6f * t, p); x += step }
                y += step * .87f; row++ }
            // A comic burst behind the header's right edge.
            val bx = w - l.padding - 14f; val by = l.padding + l.header * .45f
            p.solid(accent, 235); c.drawPath(Path().apply { for (k in 0 until 16) { val a = PI / 8 * k; val r = if (k % 2 == 0) 13f else 7f
                val x = bx + cos(a).toFloat() * r; val yy = by + sin(a).toFloat() * r * .8f; if (k == 0) moveTo(x, yy) else lineTo(x, yy) }; close() }, p)
        }
        DisplayMotif.DECO_FAN -> {
            gradient(ColorUtils.blendARGB(bg, Color.WHITE, .3f), bg, ColorUtils.blendARGB(bg, d.tint, .12f))
            val fx = w * .5f; val fy = -h * .06f
            p.line(d.tint, .8f, 130); for (k in 0..18) { val a = PI * k / 18; c.drawLine(fx, fy, fx + cos(a).toFloat() * w, fy + sin(a).toFloat() * w, p) }
            p.solid(bg, 235); c.drawRect(0f, l.padding + l.header, w, h, p)
            p.line(d.tint, 1.1f, 190); for (k in 1..3) c.drawArc(RectF(fx - k * 14f, fy - k * 14f, fx + k * 14f, fy + k * 14f), 0f, 180f, false, p)
            p.line(d.tint, 1.4f, 200); c.drawRect(3f, 3f, w - 3f, h - 3f, p)
            p.line(d.tint, .6f, 170); c.drawRect(6.5f, 6.5f, w - 6.5f, h - 6.5f, p)
            for (x in listOf(10f, w - 10f)) { c.drawLine(x, h * .3f, x, h - 10f, p) }
        }
        DisplayMotif.INK_MIST -> {
            gradient(ColorUtils.blendARGB(bg, Color.WHITE, .04f), bg, ColorUtils.blendARGB(bg, Color.BLACK, .25f))
            listOf(Triple(.58f, .55f, 46), Triple(.7f, .78f, 64), Triple(.8f, .3f, 34)).forEachIndexed { i, (top, peak, alpha) ->
                p.shader = LinearGradient(0f, h * top - h * .15f, 0f, h, ColorUtils.setAlphaComponent(d.tint, alpha), 0x00000000, Shader.TileMode.CLAMP)
                c.drawPath(Path().apply { moveTo(w * (peak - .5f), h); quadTo(w * (peak - .12f), h * top - h * .05f, w * peak, h * top - h * .18f)
                    quadTo(w * (peak + .1f), h * top, w * (peak + .45f), h); close() }, p); p.shader = null }
            p.line(d.tint, 1.1f, 100); c.drawPath(Path().apply { moveTo(-2f, h * .1f); cubicTo(w * .1f, h * .14f, w * .16f, h * .06f, w * .26f, h * .1f) }, p)
            val s = 11f; val sx = w - l.padding - s - 2; val sy = h - l.padding - l.footer - s - 2
            p.solid(accent, 220); c.drawRect(sx, sy, sx + s, sy + s, p); p.line(bg, .9f); c.drawRect(sx + 2.5f, sy + 2.5f, sx + s - 2.5f, sy + s - 2.5f, p)
        }
        DisplayMotif.MEMPHIS -> {
            c.drawColor(bg)
            p.line(d.tint, 2f, 220); c.drawPath(Path().apply { moveTo(w * .7f, 10f); for (k in 1..8) lineTo(w * .7f + k * 7f, if (k % 2 == 0) 10f else 17f) }, p)
            p.line(0xFF3EC9D6.toInt(), 1.8f, 210); c.drawPath(Path().apply { moveTo(8f, h * .9f); cubicTo(w * .1f, h * .8f, w * .16f, h, w * .26f, h * .9f) }, p)
            p.solid(accent, 200); c.drawPath(Path().apply { moveTo(w - 18f, h - 8f); lineTo(w - 6f, h - 8f); lineTo(w - 12f, h - 20f); close() }, p)
            p.line(0xFFFFFFFF.toInt(), 1.2f, 160); c.drawArc(RectF(w * .9f, h * .4f, w * .9f + 18f, h * .4f + 18f), 90f, 180f, false, p)
            repeat(10) { i -> p.line(intArrayOf(d.tint, accent, 0xFF3EC9D6.toInt())[i % 3], 1.6f, 200)
                val x = if (i % 2 == 0) w * (.02f + .04f * i) else w * (.97f - .01f * i); val y = h * noise(i, 71)
                if (x < w * .2f || x > w * .9f) c.drawLine(x, y, x + 4f, y - 3f, p) }
        }
        else -> c.drawColor(bg)
    }
}

private fun pixelRect(c: Canvas, p: Paint, x: Float, y: Float, cell: Float, cols: Int, rows: Int) =
    c.drawRect(x, y, x + cell * cols, y + cell * rows, p)

private fun gameBackground(c: Canvas, d: DisplayProfile, w: Float, h: Float, l: BoardLayout, bg: Int, accent: Int, p: Paint) {
    val cell = 2f
    when (d.motif) {
        DisplayMotif.NES -> {
            c.drawColor(bg)
            // Plus-shaped pixel stars, a brick ground and a small pixel locomotive.
            p.solid(Color.WHITE, 200)
            for ((sx, sy) in listOf(.62f to .1f, .78f to .05f, .9f to .14f, .7f to .18f)) {
                val x = (w * sx / cell).roundToInt() * cell; val y = (h * sy / cell).roundToInt() * cell
                c.drawRect(x, y - cell, x + cell, y + cell * 2, p); c.drawRect(x - cell, y, x + cell * 2, y + cell, p) }
            val ground = h - 10f
            p.solid(d.tint); c.drawRect(0f, ground, w, h, p)
            p.solid(Color.BLACK, 170); var x = 0f; var row = 0
            c.drawRect(0f, ground, w, ground + 1f, p)
            for (yy in listOf(ground + 4.5f)) c.drawRect(0f, yy, w, yy + 1f, p)
            while (x < w) { c.drawRect(x, ground + 1f, x + 1f, ground + 4.5f, p); c.drawRect(x + 4f, ground + 5.5f, x + 5f, h, p); x += 8f; row++ }
            val tx = w - 40f; val ty = ground - 10f
            p.solid(accent); pixelRect(c, p, tx, ty + 2, cell, 12, 4); pixelRect(c, p, tx + 2 * cell, ty - 2 * cell, cell, 3, 3)
            p.solid(0xFFD83A2E.toInt()); pixelRect(c, p, tx + 8 * cell, ty - 2, cell, 4, 3)
            p.solid(Color.BLACK); for (k in 0..2) pixelRect(c, p, tx + (1 + k * 4) * cell, ty + 10, cell, 2, 2)
        }
        DisplayMotif.PIXEL_DUSK -> {
            val bands = intArrayOf(bg, 0xFF33205A.toInt(), 0xFF4B2766.toInt(), 0xFF67306D.toInt(), 0xFF82396E.toInt())
            val bandH = h / bands.size
            bands.forEachIndexed { i, color -> p.solid(color); c.drawRect(0f, i * bandH, w, (i + 1) * bandH + 1, p) }
            p.solid(0xFFFFB85C.toInt(), 210); val sx = w * .82f; val sy = h * .74f
            for (k in 0..5) { val half = (sqrt(36f - (k - 5.5f) * (k - 5.5f)) * cell * 1.5f).roundToInt().toFloat()
                c.drawRect(sx - half, sy - (6 - k) * cell * 1.5f, sx + half, sy - (5 - k) * cell * 1.5f, p) }
            p.solid(0xFF1B0F33.toInt())
            var x = 0f; while (x < w) { val peak = ((noise((x / 6).toInt(), 81) * 9 + 3).roundToInt() * cell)
                c.drawRect(x, h - 4 - peak, x + 6f, h, p); x += 6f }
            p.solid(Color.WHITE, 210)
            for ((px, py) in listOf(.15f to .08f, .42f to .05f, .66f to .12f, .92f to .06f)) { val xx = (w * px / cell).roundToInt() * cell; val yy = (h * py / cell).roundToInt() * cell
                c.drawRect(xx, yy - cell, xx + cell, yy + cell * 2, p); c.drawRect(xx - cell, yy, xx + cell * 2, yy + cell, p) }
        }
        DisplayMotif.POCKET -> {
            c.drawColor(bg)
            p.solid(ColorUtils.blendARGB(bg, d.tint, .25f)); c.drawRect(0f, 0f, w, l.padding + l.header - 2, p)
            p.solid(d.tint); val tx = w - 34f; val ty = h - 12f
            pixelRect(c, p, tx, ty, cell, 12, 3); pixelRect(c, p, tx + 2 * cell, ty - 3 * cell, cell, 3, 3); pixelRect(c, p, tx + 8 * cell, ty - 2 * cell, cell, 3, 2)
            p.solid(d.ink); for (k in 0..2) pixelRect(c, p, tx + (1 + k * 4) * cell, ty + 3 * cell, cell, 2, 2)
            c.drawRect(0f, h - 3f, w, h - 2f, p)
        }
        DisplayMotif.OUTRUN -> {
            val horizon = h * .8f
            p.shader = LinearGradient(0f, 0f, 0f, horizon, intArrayOf(0xFFFFF0F5.toInt(), bg, 0xFFFFE2C2.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, horizon, p); p.shader = null
            val sx = w * .86f; val sy = l.padding + l.header * .35f; val r = min(w, h) * .13f
            p.shader = LinearGradient(0f, sy - r, 0f, sy + r, 0xFFFFC04D.toInt(), 0xFFFF3D8B.toInt(), Shader.TileMode.CLAMP)
            c.save(); c.clipRect(sx - r, sy - r, sx + r, sy + r * .2f)
            c.drawCircle(sx, sy, r, p); c.restore(); p.shader = null
            p.solid(0xFFFFF0F5.toInt()); for (k in 0..2) c.drawRect(sx - r, sy - r * .5f + k * r * .26f, sx + r, sy - r * .5f + k * r * .26f + 1.4f + k * .6f, p)
            p.solid(0xFFEDE0FA.toInt()); c.drawRect(0f, horizon, w, h, p)
            p.line(0xFFB27FE0.toInt(), .8f, 220); c.drawLine(0f, horizon, w, horizon, p)
            for (k in -8..8) c.drawLine(w / 2 + k * 6f, horizon, w / 2 + k * w * .12f, h, p)
            var gy = horizon; var step = 2f; while (gy < h) { c.drawLine(0f, gy, w, gy, p); gy += step; step *= 1.45f }
            p.solid(0xFFEDE0FA.toInt(), 150); c.drawRect(0f, horizon + 1, w, h, p)
        }
        DisplayMotif.PONG -> {
            c.drawColor(bg)
            p.solid(d.ink, 230)
            c.drawRect(4f, h * .38f, 7f, h * .38f + 16f, p); c.drawRect(w - 7f, h * .6f, w - 4f, h * .6f + 16f, p)
            c.drawRect(w * .74f, h * .52f, w * .74f + 3.5f, h * .52f + 3.5f, p)
            p.solid(d.tint, 200); var x = l.padding; while (x < w - l.padding) { c.drawRect(x, l.padding + l.header - 2.5f, x + 3f, l.padding + l.header - .5f, p); x += 6f }
        }
        DisplayMotif.HANDHELD -> {
            c.drawColor(bg)
            // The printed backdrop of a pocket LCD game, behind the liquid crystals.
            p.solid(d.tint, 60); c.drawCircle(w * .9f, h * .14f, min(w, h) * .12f, p)
            p.solid(0xFF6FA59A.toInt(), 50); c.drawRect(0f, h - 9f, w, h, p)
            p.solid(0xFF6FA59A.toInt(), 36); c.drawPath(Path().apply { moveTo(0f, h - 9f); lineTo(w * .14f, h * .78f); lineTo(w * .26f, h - 9f); close() }, p)
            p.shader = LinearGradient(0f, 0f, w * .3f, h, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p); p.shader = null
        }
        DisplayMotif.RPG_WINDOW -> {
            p.shader = LinearGradient(0f, 0f, 0f, h, ColorUtils.blendARGB(bg, d.tint, .55f), ColorUtils.blendARGB(bg, Color.BLACK, .35f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
            p.line(Color.WHITE, 2f); c.drawRoundRect(3.5f, 3.5f, w - 3.5f, h - 3.5f, 5f, 5f, p)
            p.line(0xFF9AA4C8.toInt(), .8f); c.drawRoundRect(6f, 6f, w - 6f, h - 6f, 3.5f, 3.5f, p)
            p.solid(accent); val cy = l.padding + l.header + l.rowHeight * .5f - 1
            c.drawPath(Path().apply { moveTo(l.padding - 3f, cy - 3.5f); lineTo(l.padding + .5f, cy); lineTo(l.padding - 3f, cy + 3.5f); close() }, p)
        }
        DisplayMotif.XMB -> {
            p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(ColorUtils.blendARGB(bg, d.tint, .35f), bg, 0xFF02060E.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
            for (k in 0..3) { p.line(if (k % 2 == 0) Color.WHITE else accent, .8f + k * .4f, 26 + k * 10)
                val path = Path(); for (i in 0..80) { val x = w * i / 80; val y = h * (.56f + k * .03f) + sin(i / 80f * 6.3f + k * .7f) * h * (.07f + k * .015f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }; c.drawPath(path, p) }
            p.solid(Color.WHITE, 14); c.drawRect(l.padding - 2, l.padding + l.header, w - l.padding + 2, l.padding + l.header + l.rowHeight - l.journeyLinkHeight, p)
        }
        else -> c.drawColor(bg)
    }
}

/** Returns the colour the title is written in. */
private fun displayHeader(c: Canvas, d: DisplayProfile, theme: AppTheme, w: Float, pad: Float, header: Float,
    bg: Int, ink: Int, accent: Int, p: Paint): Int {
    val y = pad + header - 2
    return when (d.material) {
        DisplayMaterial.OS_WINDOW -> {
            val palette = SystemSurfacePalette(theme.background.toInt(), theme.surface.toInt(), theme.accent.toInt(), theme.secondary.toInt(), Color.BLACK)
            val headerColor = systemHeaderColor(theme, palette)
            val text = readableForeground(0xFF111111, headerColor.toLong() and 0xffffffffL).toInt()
            val chrome = palette.copy(text = text)
            drawSystemHeader(c, theme, w, pad + header - 1, chrome)
            systemHeaderForeground(theme, chrome)
        }
        DisplayMaterial.LIGHT_LCD -> {
            val band = readableOn(accent, Color.WHITE, 4.5)
            p.solid(band); c.drawRect(0f, 0f, w, pad + header - 1, p)
            if (ColorUtils.calculateContrast(Color.WHITE, band or Color.BLACK) >= 4.5) Color.WHITE else Color.BLACK
        }
        DisplayMaterial.STATION_TFT -> {
            p.solid(Color.WHITE, 20); c.drawRect(0f, 0f, w, pad + header - 1, p)
            p.solid(accent, 220); c.drawRect(0f, pad + header - 2, w, pad + header - 1, p); Color.WHITE
        }
        DisplayMaterial.OLED -> {
            p.shader = LinearGradient(pad, 0f, w - pad, 0f, ColorUtils.setAlphaComponent(accent, 200), ColorUtils.setAlphaComponent(accent, 0), Shader.TileMode.CLAMP)
            c.drawRect(pad, y, w - pad, y + .9f, p); p.shader = null; accent
        }
        DisplayMaterial.E_PAPER, DisplayMaterial.LIGHTBOX -> { p.solid(ink); c.drawRect(pad, y - .6f, w - pad, y + 1.2f, p); ink }
        DisplayMaterial.ENAMEL -> { p.solid(ink); c.drawRect(pad, y - 1f, w - pad, y + 1.4f, p); ink }
        DisplayMaterial.CHALKBOARD -> {
            p.line(accent, 1.1f, 190); val path = Path(); path.moveTo(pad, y)
            var x = pad; while (x < w * .55f) { path.quadTo(x + 4, y - 1.4f, x + 8, y); x += 8 }; c.drawPath(path, p); accent
        }
        DisplayMaterial.PAPER -> {
            if (d.motif != DisplayMotif.AIRMAIL && d.motif != DisplayMotif.STAMP_ALBUM && d.motif != DisplayMotif.POSTER) {
                p.solid(ink, 200); c.drawRect(pad, y - 2f, w - pad, y - 1.2f, p); c.drawRect(pad, y, w - pad, y + .5f, p) }
            if (d.motif == DisplayMotif.POSTER) accent else ink
        }
        DisplayMaterial.PAINTING -> {
            if (d.motif !in setOf(DisplayMotif.RED_WEDGE, DisplayMotif.BENDAY, DisplayMotif.MEMPHIS)) {
                p.line(accent, .8f, 170); c.drawLine(pad, y, w / 2 - 6, y, p); c.drawLine(w / 2 + 6, y, w - pad, y, p)
                p.solid(accent, 200); c.drawPath(Path().apply { moveTo(w / 2, y - 3); lineTo(w / 2 + 3.5f, y); lineTo(w / 2, y + 3); lineTo(w / 2 - 3.5f, y); close() }, p) }
            accent
        }
        DisplayMaterial.GAME -> {
            if (d.motif == DisplayMotif.NES || d.motif == DisplayMotif.PIXEL_DUSK) {
                p.solid(accent); var x = pad; while (x < w - pad) { c.drawRect(x, y, x + 2f, y + 2f, p); x += 4f } }
            if (d.motif == DisplayMotif.XMB) { p.solid(Color.WHITE, 90); c.drawRect(pad, y, w - pad, y + .6f, p); ink }
            else if (d.motif == DisplayMotif.POCKET || d.motif == DisplayMotif.HANDHELD) ink else accent
        }
        DisplayMaterial.LED_MATRIX, DisplayMaterial.VFD, DisplayMaterial.CRT -> {
            p.solid(accent, 90); c.drawRect(pad, y, w - pad, y + .7f, p); accent
        }
        DisplayMaterial.SPLIT_FLAP -> bg
        DisplayMaterial.REFLECTIVE_LCD -> { p.solid(ink, 160); c.drawRect(pad, y, w - pad, y + .8f, p); ink }
    }
}

private fun displayRowBackground(c: Canvas, d: DisplayProfile, index: Int, left: Float, top: Float, right: Float, bottom: Float,
    bg: Int, ink: Int, accent: Int, p: Paint) {
    when (d.material) {
        DisplayMaterial.STATION_TFT -> { if (index % 2 == 1) { p.solid(Color.WHITE, 14); c.drawRect(left - 2, top, right + 2, bottom, p) } }
        DisplayMaterial.LIGHT_LCD -> { if (index % 2 == 1) { p.solid(accent, 16); c.drawRect(left - 2, top, right + 2, bottom, p) }
            p.solid(ink, 26); c.drawRect(left, bottom - .6f, right, bottom, p) }
        DisplayMaterial.OS_WINDOW -> {
            if (d.light) { if (index % 2 == 1) { p.solid(0xFFE9F0FB.toInt()); c.drawRect(left - 4, top, right + 4, bottom, p) } }
            else { p.solid(ink, 20); c.drawRect(left, bottom - .6f, right, bottom, p) }
        }
        DisplayMaterial.OLED -> { p.solid(ink, 24); c.drawRect(left, bottom - .6f, right, bottom, p) }
        DisplayMaterial.E_PAPER, DisplayMaterial.LIGHTBOX -> { p.solid(ink, 150); c.drawRect(left, bottom - .7f, right, bottom - .1f, p) }
        DisplayMaterial.ENAMEL -> { p.solid(ink); c.drawRect(left, bottom - 1.2f, right, bottom, p) }
        DisplayMaterial.PAPER -> {
            if (d.motif == DisplayMotif.CONTACT_SHEET) { p.line(ink, .8f, 200); c.drawRect(left + 12, top + 1.5f, right - 12, bottom - 1.5f, p) }
            else if (d.motif != DisplayMotif.GREENBAR && d.motif != DisplayMotif.STAMP_ALBUM && d.motif != DisplayMotif.STAVES) {
                p.line(ink, .6f, 120); p.pathEffect = DashPathEffect(floatArrayOf(1.2f, 2.2f), 0f)
                c.drawLine(left, bottom - .5f, right, bottom - .5f, p); p.pathEffect = null }
        }
        DisplayMaterial.CHALKBOARD -> { p.line(ink, .7f, 46); c.drawLine(left + 4, bottom - 1, right - 10, bottom - 1.6f, p) }
        DisplayMaterial.PAINTING -> if (d.motif !in setOf(DisplayMotif.BENDAY, DisplayMotif.MEMPHIS)) {
            p.solid(accent, 46); c.drawRect(left + 6, bottom - .6f, right - 6, bottom, p) }
        DisplayMaterial.GAME -> if (d.motif == DisplayMotif.POCKET) { p.solid(ink, 40); var x = left; while (x < right) { c.drawRect(x, bottom - 1.5f, x + 1.5f, bottom, p); x += 4f } }
        DisplayMaterial.REFLECTIVE_LCD -> { p.solid(ink, 34); c.drawRect(left, bottom - .7f, right, bottom, p) }
        else -> Unit
    }
}

private fun displayBadge(c: Canvas, pen: DisplayPen, d: DisplayProfile, row: Departure, x: Float, y: Float, w: Float, h: Float,
    size: Float, bg: Int, ink: Int, accent: Int, p: Paint) {
    if (w <= 4f || h <= 4f) return
    val cy = y + h / 2
    when (d.material) {
        DisplayMaterial.STATION_TFT -> {
            p.solid(row.lineType.argb.toInt()); c.drawRoundRect(x, y, x + w, y + h, 2.5f, 2.5f, p)
            pen.text(row.lineName, x + 2, cy, w - 4, size, h * .9f, row.lineType.contentArgb.toInt(), bold = true, center = true, role = DisplayRole.BADGE)
        }
        DisplayMaterial.LIGHT_LCD, DisplayMaterial.OS_WINDOW -> {
            val fill = if (d.material == DisplayMaterial.OS_WINDOW && !d.light) ColorUtils.setAlphaComponent(accent, 70) else accent
            p.solid(fill, Color.alpha(fill)); c.drawRoundRect(x, y, x + w, y + h, h * .3f, h * .3f, p)
            val label = if (ColorUtils.calculateContrast(Color.WHITE, ColorUtils.compositeColors(fill, bg)) >= 4.5) Color.WHITE else Color.BLACK
            pen.text(row.lineName, x + 2, cy, w - 4, size, h * .9f, label, bold = true, center = true, role = DisplayRole.BADGE)
        }
        DisplayMaterial.E_PAPER, DisplayMaterial.LIGHTBOX, DisplayMaterial.ENAMEL -> {
            p.solid(ink); c.drawRoundRect(x, y, x + w, y + h, if (d.material == DisplayMaterial.ENAMEL) 1f else 3f, 3f, p)
            pen.text(row.lineName, x + 2, cy, w - 4, size, h * .9f, bg, bold = true, center = true, role = DisplayRole.BADGE)
        }
        DisplayMaterial.OLED -> {
            p.solid(accent, 46); c.drawRoundRect(x, y, x + w, y + h, h * .35f, h * .35f, p)
            pen.text(row.lineName, x + 2, cy, w - 4, size, h * .9f, accent, bold = true, center = true, role = DisplayRole.BADGE)
        }
        DisplayMaterial.PAPER, DisplayMaterial.PAINTING -> {
            p.line(accent, .9f, 220); c.drawRoundRect(x + .5f, y + .5f, x + w - .5f, y + h - .5f, if (d.material == DisplayMaterial.PAINTING) h * .5f else 1.5f, h * .5f, p)
            pen.text(row.lineName, x + 2, cy, w - 4, size, h * .85f, accent, bold = true, center = true, role = DisplayRole.BADGE)
        }
        DisplayMaterial.CHALKBOARD -> {
            p.line(accent, 1f, 200); c.drawOval(x - 1, y, x + w + 1, y + h, p)
            pen.text(row.lineName, x + 3, cy, w - 6, size, h * .85f, accent, bold = true, center = true, role = DisplayRole.BADGE)
        }
        DisplayMaterial.GAME -> {
            if (d.face == DisplayFace.PIXELS) { p.line(accent, 1.4f); p.strokeCap = Paint.Cap.SQUARE; c.drawRect(x + .7f, y + .7f, x + w - .7f, y + h - .7f, p) }
            pen.text(row.lineName, x + 3, cy, w - 6, size * .9f, h * .62f, accent, bold = true, center = true, role = DisplayRole.BADGE)
        }
        DisplayMaterial.SPLIT_FLAP -> {
            p.solid(accent); c.drawRoundRect(x, y, x + w, y + h, 1.5f, 1.5f, p)
            p.solid(Color.BLACK, 80); c.drawRect(x, cy - .35f, x + w, cy + .35f, p)
            pen.text(row.lineName, x + 2, cy, w - 4, size, h * .9f, bg, bold = true, center = true, role = DisplayRole.TITLE)
        }
        else -> pen.text(row.lineName, x, cy, w, size, h, accent, bold = true, role = DisplayRole.BADGE)
    }
}

private fun displayOverlay(c: Canvas, d: DisplayProfile, w: Float, h: Float, bg: Int, ink: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    when (d.material) {
        DisplayMaterial.CRT -> {
            p.solid(Color.BLACK, 46); var y = 1.5f; while (y < h) { c.drawRect(0f, y, w, y + .7f, p); y += 2.6f }
            p.shader = RadialGradient(w / 2, h / 2, max(w, h) * .75f, intArrayOf(0x00000000, 0x00000000, 0x8C000000.toInt()), floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
        }
        DisplayMaterial.VFD -> {
            // The fine control mesh in front of a vacuum fluorescent display.
            p.line(bg, .45f, 120); var x = 1.5f; while (x < w) { c.drawLine(x, 0f, x, h, p); x += 2.4f }
        }
        DisplayMaterial.REFLECTIVE_LCD -> {
            p.line(Color.BLACK, .3f, 16); var x = 0f; while (x < w) { c.drawLine(x, 0f, x, h, p); x += 2.2f }
            var y = 0f; while (y < h) { c.drawLine(0f, y, w, y, p); y += 2.2f }
        }
        DisplayMaterial.GAME -> when (d.motif) {
            DisplayMotif.POCKET -> { p.line(Color.BLACK, .3f, 18); var x = 0f; while (x < w) { c.drawLine(x, 0f, x, h, p); x += 2f }
                var y = 0f; while (y < h) { c.drawLine(0f, y, w, y, p); y += 2f } }
            DisplayMotif.NES, DisplayMotif.PIXEL_DUSK, DisplayMotif.PONG, DisplayMotif.RPG_WINDOW -> {
                p.solid(Color.BLACK, 30); var y = 1f; while (y < h) { c.drawRect(0f, y, w, y + .6f, p); y += 2f } }
            else -> Unit
        }
        DisplayMaterial.OLED, DisplayMaterial.STATION_TFT, DisplayMaterial.LIGHT_LCD, DisplayMaterial.OS_WINDOW -> {
            // One broad reflection on the cover glass.
            p.shader = LinearGradient(0f, 0f, w, h * .5f, if (d.light) 0x22FFFFFF else 0x10FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * .8f, 0f); lineTo(w * .3f, h * .36f); lineTo(0f, h * .44f); close() }, p)
            p.shader = null
        }
        else -> Unit
    }
}
