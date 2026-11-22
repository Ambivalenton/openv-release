// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import dev.ambivalenton.openv.data.model.ThemeTexture
import kotlin.math.*

val ThemeTexture.isCatalogTexture: Boolean get() = name.startsWith("CATALOG_")

/** Original static vectors shared by app backgrounds, panels and adaptive departure boards.
 * Every loop has a fixed upper bound. No image assets, network, randomness or animation clock.
 * The caller owns the background and clip, so custom colours and silhouettes remain intact. */
fun drawCatalogThemePattern(
    canvas: Canvas, texture: ThemeTexture, width: Float, height: Float,
    color: Int, opacity: Float = .10f,
) {
    if (!texture.isCatalogTexture || width <= 0f || height <= 0f) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val unit = min(width, height)
    val hair = max(unit / 300f, .6f)
    fun ink(strength: Float = 1f, stroke: Float = 0f) {
        paint.color = color
        paint.alpha = (Color.alpha(color) * opacity * strength).roundToInt().coerceIn(0, 255)
        paint.style = if (stroke > 0f) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }
    fun line(x: Float, y: Float, xx: Float, yy: Float, strength: Float = 1f, stroke: Float = hair) {
        ink(strength, stroke); canvas.drawLine(x, y, xx, yy, paint)
    }
    fun path(strength: Float = 1f, stroke: Float = 0f, body: Path.() -> Unit) {
        ink(strength, stroke); canvas.drawPath(Path().apply(body), paint)
    }
    fun circle(x: Float, y: Float, r: Float, strength: Float = 1f, stroke: Float = 0f) {
        ink(strength, stroke); canvas.drawCircle(x, y, r, paint)
    }
    fun rect(x: Float, y: Float, w: Float, h: Float, strength: Float = 1f, stroke: Float = 0f) {
        ink(strength, stroke); canvas.drawRect(x, y, x + w, y + h, paint)
    }
    /** Draws [body] turned by [degrees] around ([cx], [cy]). */
    fun turned(cx: Float, cy: Float, degrees: Float, body: () -> Unit) {
        val saved = canvas.save(); canvas.rotate(degrees, cx, cy)
        try { body() } finally { canvas.restoreToCount(saved) }
    }
    /** A five-pointed star. */
    fun star(cx: Float, cy: Float, outer: Float, inner: Float, strength: Float) {
        val shape = Path()
        repeat(10) { k ->
            val a = Math.toRadians(-90.0 + k * 36.0)
            val r = if (k % 2 == 0) outer else inner
            val px = cx + r * cos(a).toFloat()
            val py = cy + r * sin(a).toFloat()
            if (k == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
        }
        shape.close()
        ink(strength); canvas.drawPath(shape, paint)
    }
    fun tiles(divisions: Int = 5, block: (Float, Float, Float, Int) -> Unit) {
        val step = unit / divisions
        val columns = ceil(width / step).toInt().coerceIn(1, 24)
        val rows = ceil(height / step).toInt().coerceIn(1, 36)
        repeat(min(columns * rows, 256)) { i -> block(i % columns * step, i / columns * step, step, i) }
    }
    val token = canvas.save()
    try {
        canvas.clipRect(0f, 0f, width, height)
        when (texture) {
            ThemeTexture.CATALOG_STARCHART -> {
                repeat(7) { n ->
                    val cx = width * ((n * 31 % 89 + 5) / 100f)
                    val cy = height * ((n * 43 % 83 + 7) / 100f)
                    val r = unit * (.07f + n % 3 * .025f)
                    repeat(4) { i ->
                        val a = (i * 1.3 + n).toFloat()
                        val b = a + 1.3f
                        val x = cx + cos(a) * r; val y = cy + sin(a) * r
                        line(x, y, cx + cos(b) * r, cy + sin(b) * r, .9f)
                        circle(x, y, hair * (1.2f + i % 2), 1.7f)
                    }
                }
                repeat(3) { i -> circle(width * .62f, height * .42f, unit * (.28f + i * .14f), .4f, hair) }
            }
            ThemeTexture.CATALOG_RADIOGRAM -> repeat(9) { row ->
                val y = height * (row + .5f) / 9
                line(0f, y, width, y, .25f)
                path(.85f, hair) {
                    moveTo(0f, y)
                    repeat(65) { i ->
                        val x = width * i / 64
                        val pulse = exp(-((i % 16 - 8) / 2.4f).pow(2))
                        lineTo(x, y + sin(i * .75f + row) * pulse * unit * .055f)
                    }
                }
            }
            ThemeTexture.CATALOG_ECLIPSE -> {
                val x = width * .78f; val y = height * .28f; val r = unit * .24f
                circle(x, y, r, .7f)
                repeat(8) { i -> circle(x, y, r + unit * (.018f + i * .025f), 1.4f / (i + 1), hair * (1 + i % 2)) }
                repeat(32) { i ->
                    val a = i * PI.toFloat() / 16
                    line(x + cos(a) * r * 1.12f, y + sin(a) * r * 1.12f,
                        x + cos(a) * r * 1.33f, y + sin(a) * r * 1.33f, .7f)
                }
            }
            ThemeTexture.CATALOG_SPECTRUM -> {
                repeat(28) { i ->
                    val x = width * ((i * 37 % 103) / 103f)
                    rect(x, 0f, hair * (1 + i % 3), height, .15f + i % 4 * .2f)
                    line(x, height * .84f, x, height * (.84f - .04f * (i % 3)), 1.5f)
                }
                line(0f, height * .84f, width, height * .84f, .8f)
            }
            ThemeTexture.CATALOG_CRATERS -> tiles(4) { x, y, s, i ->
                val r = s * (.15f + i % 3 * .06f)
                circle(x + s * .52f, y + s * .5f, r, .55f)
                circle(x + s * .5f, y + s * .48f, r * 1.3f, .9f, hair)
                circle(x + s * .5f, y + s * .48f, r * 1.65f, .35f, hair)
                circle(x + s * .14f, y + s * .82f, s * .06f, .65f, hair)
            }
            ThemeTexture.CATALOG_FACETS -> tiles(3) { x, y, s, i ->
                val cx = x + s * (.35f + i % 2 * .22f); val cy = y + s * .43f
                listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f).forEachIndexed { j, (a, b) ->
                    val (c, d) = listOf(1f to 0f, 1f to 1f, 0f to 1f, 0f to 0f)[j]
                    path(.25f + j * .22f) { moveTo(cx, cy); lineTo(x + s * a, y + s * b); lineTo(x + s * c, y + s * d); close() }
                    line(cx, cy, x + s * a, y + s * b, .65f)
                }
            }
            ThemeTexture.CATALOG_AGATE -> repeat(15) { i ->
                val inset = unit * i * .055f
                path(if (i % 3 == 0) 1.25f else .55f, unit * (.008f + i % 3 * .004f)) {
                    moveTo(-unit * .1f, height - inset)
                    cubicTo(width * .08f, height * .5f - inset, width * .68f, height * 1.13f - inset, width + unit * .1f, height * .33f - inset)
                }
            }
            ThemeTexture.CATALOG_MARBLE -> repeat(7) { i ->
                val x = width * (i - 1) / 5
                path(.9f, hair * (1 + i % 2)) {
                    moveTo(x, 0f); cubicTo(x + unit * .23f, height * .27f, x - unit * .15f, height * .51f, x + unit * .18f, height)
                }
                path(.35f, hair) { moveTo(x + unit * .08f, height * .25f); lineTo(x + unit * .32f, height * .38f); lineTo(x + unit * .47f, height * .73f) }
            }
            ThemeTexture.CATALOG_BRUSHED -> {
                repeat(100) { i -> line(0f, height * i / 100, width, height * i / 100 - unit * .02f, if (i % 7 == 0) .75f else .23f) }
                path(.7f) { moveTo(width * .05f, 0f); lineTo(width * .3f, 0f); lineTo(width * .9f, height); lineTo(width * .65f, height); close() }
            }
            ThemeTexture.CATALOG_TERRAZZO -> tiles(6) { x, y, s, i ->
                val cx = x + s * .5f; val cy = y + s * .5f
                path(.5f + i % 4 * .35f) {
                    repeat(5) { n ->
                        val a = n * 2 * PI / 5 + i
                        val r = s * (.12f + (i + n) % 3 * .055f)
                        val xx = cx + cos(a).toFloat() * r; val yy = cy + sin(a).toFloat() * r
                        if (n == 0) moveTo(xx, yy) else lineTo(xx, yy)
                    }; close()
                }
            }
            ThemeTexture.CATALOG_TARTAN -> tiles(3) { x, y, s, _ ->
                rect(x + s * .2f, y, s * .22f, s, .8f); rect(x, y + s * .2f, s, s * .22f, .8f)
                line(x + s * .65f, y, x + s * .65f, y + s, 1.35f)
                line(x, y + s * .65f, x + s, y + s * .65f, 1.35f)
                line(x + s * .76f, y, x + s * .76f, y + s, .5f)
                line(x, y + s * .76f, x + s, y + s * .76f, .5f)
            }
            ThemeTexture.CATALOG_HERRINGBONE -> tiles(8) { x, y, s, i ->
                val direction = if (i % 2 == 0) 1 else -1
                repeat(4) { n ->
                    val yy = y + s * n / 4
                    line(x, yy, x + s * .5f, yy + s * .27f * direction, .7f)
                    line(x + s * .5f, yy + s * .27f * direction, x + s, yy, .7f)
                }
            }
            ThemeTexture.CATALOG_SASHIKO -> tiles(5) { x, y, s, _ ->
                repeat(4) { side ->
                    val cx = x + if (side % 2 == 0) 0f else s
                    val cy = y + if (side < 2) 0f else s
                    repeat(5) { n ->
                        ink(1f, hair)
                        val start = when (side) { 0 -> 0f; 1 -> 90f; 2 -> 270f; else -> 180f } + n * 18f
                        canvas.drawArc(RectF(cx - s * .5f, cy - s * .5f, cx + s * .5f, cy + s * .5f), start, 10f, false, paint)
                    }
                }
            }
            ThemeTexture.CATALOG_WEAVE -> tiles(6) { x, y, s, i ->
                repeat(3) { n ->
                    val a = s * (.12f + n * .11f)
                    rect(x + a, y, s * .065f, s, .65f)
                    rect(x, y + s * .55f + n * s * .11f, s, s * .065f, .65f)
                }
                if (i % 2 == 0) rect(x, y + s * .52f, s * .47f, s * .4f, .3f)
            }
            ThemeTexture.CATALOG_IKAT -> tiles(4) { x, y, s, _ ->
                repeat(5) { n ->
                    val yy = y + s * (.1f + n * .16f); val length = s * (.12f + (2 - abs(n - 2)) * .15f)
                    rect(x + s * .5f - length / 2, yy, length, s * .1f, .8f)
                    line(x + s * .5f - length / 2 - s * .05f, yy, x + s * .5f + length / 2 + s * .05f, yy, .5f)
                }
            }
            ThemeTexture.CATALOG_ARCADES -> tiles(3) { x, y, s, _ ->
                path(1f, hair * 2) { moveTo(x + s * .16f, y + s); lineTo(x + s * .16f, y + s * .4f); cubicTo(x + s * .16f, y - s * .04f, x + s * .84f, y - s * .04f, x + s * .84f, y + s * .4f); lineTo(x + s * .84f, y + s) }
                line(x + s * .24f, y + s * .43f, x + s * .24f, y + s, .4f, s * .08f)
            }
            ThemeTexture.CATALOG_ISOMETRIC -> tiles(4) { x, y, s, _ ->
                val cx = x + s * .5f; val cy = y + s * .52f
                path(.9f) { moveTo(cx, cy - s * .42f); lineTo(cx + s * .36f, cy - s * .21f); lineTo(cx, cy); lineTo(cx - s * .36f, cy - s * .21f); close() }
                path(.4f) { moveTo(cx, cy); lineTo(cx + s * .36f, cy - s * .21f); lineTo(cx + s * .36f, cy + s * .21f); lineTo(cx, cy + s * .42f); close() }
                path(.65f) { moveTo(cx, cy); lineTo(cx - s * .36f, cy - s * .21f); lineTo(cx - s * .36f, cy + s * .21f); lineTo(cx, cy + s * .42f); close() }
            }
            ThemeTexture.CATALOG_COLONNADE -> repeat(8) { i ->
                val x = width * i / 7; val column = width * .035f
                rect(x, 0f, column, height, .45f)
                rect(x + column, 0f, column * .8f, height, .2f)
                rect(x - column, height * .12f, column * 3, unit * .025f, .8f)
                rect(x - column, height * .87f, column * 3, unit * .025f, .8f)
                line(x + column * 3, height * .16f, x + unit * .38f, height * .82f, .25f, column)
            }
            ThemeTexture.CATALOG_STAIRCASE -> repeat(8) { i ->
                val y = height * i / 7
                path(.7f, hair * 1.5f) {
                    moveTo(-unit * .1f, y)
                    repeat(12) { n -> rLineTo(width / 10, 0f); rLineTo(0f, -unit * .045f) }
                }
                line(0f, y + unit * .04f, width, y - unit * .5f, .35f)
            }
            ThemeTexture.CATALOG_LEADED_GLASS -> tiles(3) { x, y, s, i ->
                rect(x + s * .05f, y + s * .05f, s * .9f, s * .9f, .5f, hair * 1.5f)
                path(.4f + i % 3 * .25f) { moveTo(x + s * .5f, y + s * .05f); lineTo(x + s * .95f, y + s * .5f); lineTo(x + s * .5f, y + s * .95f); lineTo(x + s * .05f, y + s * .5f); close() }
                circle(x + s * .5f, y + s * .5f, s * .18f, .9f, hair)
            }
            ThemeTexture.CATALOG_COFFEE_RINGS -> tiles(3) { x, y, s, i ->
                repeat(4) { n ->
                    ink(.55f + n * .12f, hair * (1 + n % 2))
                    val r = s * (.19f + n * .035f)
                    canvas.drawArc(RectF(x + s * .5f - r, y + s * .5f - r, x + s * .5f + r, y + s * .5f + r), (i * 37).toFloat(), 285f + n * 12, false, paint)
                }
                circle(x + s * .85f, y + s * .25f, s * .025f, 1.3f)
            }
            ThemeTexture.CATALOG_TEA_SPRIGS -> tiles(4) { x, y, s, i ->
                val sign = if (i % 2 == 0) 1 else -1
                line(x + s * .35f, y + s * .8f, x + s * .65f, y + s * .15f, .9f)
                repeat(3) { n ->
                    val xx = x + s * (.4f + n * .08f); val yy = y + s * (.67f - n * .18f)
                    path(.7f) { moveTo(xx, yy); quadTo(xx + sign * s * .36f, yy - s * .35f, xx + sign * s * .32f, yy - s * .03f); quadTo(xx + sign * s * .15f, yy + s * .04f, xx, yy) }
                }
            }
            ThemeTexture.CATALOG_PASTRY -> tiles(3) { x, y, s, i ->
                repeat(5) { n ->
                    ink(.65f, hair * 1.5f)
                    val r = s * (.12f + n * .055f)
                    canvas.drawArc(RectF(x + s * .5f - r, y + s * .7f - r, x + s * .5f + r, y + s * .7f + r), 195f + i % 2 * 10, 150f, false, paint)
                }
                line(x + s * .15f, y + s * .75f, x + s * .85f, y + s * .75f, .55f)
            }
            ThemeTexture.CATALOG_NOODLES -> repeat(9) { row ->
                val y = height * row / 8
                path(.7f, unit * .018f) {
                    moveTo(-unit * .1f, y)
                    cubicTo(width * .35f, y - unit * .24f, width * .12f, y + unit * .24f, width * .5f, y)
                    cubicTo(width * .88f, y - unit * .24f, width * .65f, y + unit * .24f, width * 1.1f, y)
                }
            }
            ThemeTexture.CATALOG_CHOCOLATE -> tiles(4) { x, y, s, _ ->
                rect(x + s * .08f, y + s * .08f, s * .84f, s * .84f, .45f)
                rect(x + s * .21f, y + s * .21f, s * .58f, s * .58f, .8f, hair)
                line(x + s * .08f, y + s * .08f, x + s * .21f, y + s * .21f, 1.1f)
                line(x + s * .79f, y + s * .79f, x + s * .92f, y + s * .92f, .65f)
            }
            ThemeTexture.CATALOG_REGISTRATION -> tiles(3) { x, y, s, _ ->
                val r = s * .09f
                circle(x + s * .5f, y + s * .5f, r, 1f, hair)
                line(x + s * .3f, y + s * .5f, x + s * .7f, y + s * .5f, 1.1f)
                line(x + s * .5f, y + s * .3f, x + s * .5f, y + s * .7f, 1.1f)
                line(x + s * .08f, y + s * .06f, x + s * .22f, y + s * .06f, .7f)
                line(x + s * .06f, y + s * .08f, x + s * .06f, y + s * .22f, .7f)
            }
            ThemeTexture.CATALOG_LINOCUT -> tiles(3) { x, y, s, i ->
                repeat(6) { n ->
                    ink(.85f, s * .022f)
                    val r = s * (.13f + n * .065f)
                    canvas.drawArc(RectF(x - r, y - r, x + r, y + r), 5f + i % 2 * 12, 78f, false, paint)
                }
            }
            ThemeTexture.CATALOG_MOIRE -> repeat(2) { layer ->
                repeat(32) { i ->
                    val x = width * (i - 3) / 25
                    path(.55f, hair) {
                        moveTo(x, 0f)
                        cubicTo(x + unit * (.17f + layer * .1f), height * .3f, x - unit * (.12f + layer * .15f), height * .7f, x + unit * layer * .17f, height)
                    }
                }
            }
            ThemeTexture.CATALOG_RISO -> tiles(3) { x, y, s, i ->
                circle(x + s * .46f, y + s * .47f, s * .31f, .75f)
                circle(x + s * .54f, y + s * .53f, s * .31f, .45f)
                repeat(9) { n -> circle(x + s * (.24f + n % 3 * .17f), y + s * (.25f + n / 3 * .17f), s * .012f, 1.2f) }
                if (i % 2 == 0) line(x + s * .2f, y + s * .85f, x + s * .8f, y + s * .85f, .5f)
            }
            ThemeTexture.CATALOG_POSTAGE -> tiles(3) { x, y, s, _ ->
                rect(x + s * .16f, y + s * .12f, s * .68f, s * .76f, .8f, hair)
                repeat(8) { n ->
                    val a = s * (.19f + n * .088f)
                    circle(x + a, y + s * .06f, s * .018f, 1.1f)
                    circle(x + a, y + s * .94f, s * .018f, 1.1f)
                    circle(x + s * .1f, y + a, s * .018f, 1.1f)
                    circle(x + s * .9f, y + a, s * .018f, 1.1f)
                }
                line(x + s * .28f, y + s * .68f, x + s * .72f, y + s * .32f, .6f, s * .06f)
            }
            // Gaming – controller cross and buttons, pixel hearts, hex maps and loot.
            ThemeTexture.CATALOG_DPAD -> tiles(3) { x, y, s, i ->
                val cx = x + s * .5f; val cy = y + s * .5f
                if (i % 2 == 0) {
                    val a = s * .075f; val l = s * .2f
                    path(.85f) { addRect(cx - a, cy - l, cx + a, cy + l, Path.Direction.CW); addRect(cx - l, cy - a, cx - a, cy + a, Path.Direction.CW); addRect(cx + a, cy - a, cx + l, cy + a, Path.Direction.CW) }
                    circle(cx, cy, s * .03f, .4f)
                } else {
                    val d = s * .13f
                    circle(cx, cy - d, s * .06f, .9f); circle(cx + d, cy, s * .06f, .6f, hair * 1.5f)
                    circle(cx, cy + d, s * .06f, .6f, hair * 1.5f); circle(cx - d, cy, s * .06f, .9f)
                }
            }
            ThemeTexture.CATALOG_PIXEL_HEARTS -> tiles(4) { x, y, s, i ->
                val rows = listOf("0110110", "1111111", "1111111", "0111110", "0011100", "0001000")
                val p = s * .075f; val ox = x + s * .24f; val oy = y + s * .28f
                rows.forEachIndexed { r, row -> row.forEachIndexed { c, bit ->
                    // Every third heart is half empty, like a health bar.
                    if (bit == '1') rect(ox + c * p, oy + r * p, p * .92f, p * .92f, if (i % 3 == 2 && c > 3) .35f else .9f)
                } }
            }
            ThemeTexture.CATALOG_HEXGRID -> {
                val r = unit / 9f; val w = sqrt(3f) * r; val h = 1.5f * r
                val columns = ceil(width / w).toInt().coerceIn(1, 40) + 1
                val rows = ceil(height / h).toInt().coerceIn(1, 40) + 1
                repeat(min(columns * rows, 600)) { n ->
                    val col = n % columns; val row = n / columns
                    val cx = col * w + if (row % 2 == 1) w / 2 else 0f; val cy = row * h
                    path(if ((col * 7 + row * 3) % 11 == 0) .9f else .55f, if ((col * 7 + row * 3) % 11 == 0) 0f else hair * 1.2f) {
                        repeat(6) { k ->
                            val a = Math.toRadians((60.0 * k) - 30.0)
                            val px = cx + r * cos(a).toFloat(); val py = cy + r * sin(a).toFloat()
                            if (k == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                    }
                }
            }
            ThemeTexture.CATALOG_COINS -> tiles(3) { x, y, s, i ->
                if (i % 3 == 1) star(x + s * .5f, y + s * .5f, s * .17f, s * .07f, .8f)
                else {
                    circle(x + s * .5f, y + s * .5f, s * .17f, .7f, hair * 2f)
                    circle(x + s * .5f, y + s * .5f, s * .11f, .45f, hair)
                    line(x + s * .5f, y + s * .43f, x + s * .5f, y + s * .57f, .9f, hair * 2f)
                }
            }
            // Haustiere – paws, bones, yarn, fish, feathers and horseshoes.
            ThemeTexture.CATALOG_PAWS -> tiles(3) { x, y, s, i ->
                val cx = x + s * .5f; val cy = y + s * .55f
                turned(cx, cy, if (i % 2 == 0) -18f else 16f) {
                    path(.85f) { addOval(RectF(cx - s * .13f, cy - s * .04f, cx + s * .13f, cy + s * .16f), Path.Direction.CW) }
                    listOf(-.15f to -.1f, -.055f to -.19f, .055f to -.19f, .15f to -.1f).forEach { (dx, dy) ->
                        circle(cx + s * dx, cy + s * dy, s * .045f, .85f)
                    }
                }
            }
            ThemeTexture.CATALOG_BONES -> tiles(3) { x, y, s, i ->
                val cx = x + s * .5f; val cy = y + s * .5f
                turned(cx, cy, if (i % 2 == 0) 32f else -28f) {
                    rect(cx - s * .2f, cy - s * .035f, s * .4f, s * .07f, .8f)
                    listOf(-1f, 1f).forEach { side ->
                        circle(cx + side * s * .21f, cy - s * .05f, s * .055f, .8f)
                        circle(cx + side * s * .21f, cy + s * .05f, s * .055f, .8f)
                    }
                }
            }
            ThemeTexture.CATALOG_YARN -> tiles(3) { x, y, s, i ->
                val cx = x + s * .5f; val cy = y + s * .48f; val r = s * .2f
                circle(cx, cy, r, .75f, hair * 1.6f)
                repeat(3) { n ->
                    ink(.6f, hair * 1.3f)
                    canvas.drawArc(RectF(cx - r * (.9f - n * .2f), cy - r, cx + r * (.9f - n * .2f), cy + r), 200f + i * 13, 140f, false, paint)
                }
                path(.6f, hair * 1.3f) { moveTo(cx + r * .7f, cy + r * .7f); cubicTo(cx + r * 1.4f, cy + r * 1.6f, cx + r * .2f, cy + r * 1.9f, cx + r * 1.2f, cy + r * 2.2f) }
            }
            ThemeTexture.CATALOG_FISH -> tiles(3) { x, y, s, i ->
                val cx = x + s * .48f; val cy = y + s * .5f; val dir = if (i % 2 == 0) 1f else -1f
                path(.8f) {
                    moveTo(cx + dir * s * .2f, cy)
                    quadTo(cx, cy - s * .16f, cx - dir * s * .16f, cy)
                    quadTo(cx, cy + s * .16f, cx + dir * s * .2f, cy)
                    moveTo(cx - dir * s * .14f, cy); lineTo(cx - dir * s * .28f, cy - s * .1f); lineTo(cx - dir * s * .28f, cy + s * .1f); close()
                }
                circle(cx + dir * s * .1f, cy - s * .03f, s * .018f, 1.4f)
                circle(x + s * .8f, y + s * .22f, s * .025f, .5f, hair); circle(x + s * .86f, y + s * .12f, s * .015f, .5f, hair)
            }
            ThemeTexture.CATALOG_PLUMES -> tiles(3) { x, y, s, i ->
                val cx = x + s * .5f; val cy = y + s * .5f
                turned(cx, cy, if (i % 2 == 0) 28f else -24f) {
                    path(.55f) { moveTo(cx, cy - s * .3f); quadTo(cx + s * .14f, cy - s * .05f, cx, cy + s * .26f); quadTo(cx - s * .14f, cy - s * .05f, cx, cy - s * .3f) }
                    line(cx, cy - s * .32f, cx, cy + s * .34f, 1f, hair * 1.5f)
                    repeat(3) { n -> line(cx, cy - s * (.12f - n * .1f), cx + s * .09f, cy - s * (.18f - n * .1f), .9f) }
                }
            }
            ThemeTexture.CATALOG_HORSESHOE -> tiles(3) { x, y, s, i ->
                val cx = x + s * .5f; val cy = y + s * .48f; val r = s * .17f
                ink(.8f, s * .06f)
                canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 160f + i % 2 * 180f, 220f, false, paint)
                repeat(4) { n ->
                    val a = Math.toRadians(180.0 + n * 50.0 + (i % 2) * 180.0)
                    circle(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), s * .012f, .2f)
                }
            }
            else -> Unit
        }
    } finally {
        canvas.restoreToCount(token)
    }
}
