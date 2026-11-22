// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/**
 * LED text as a bitmap (4 px per dot, [MatrixFont.ROWS] rows: 7 cap rows and 2 descender rows).
 * Drawn with the hand-set [dev.ambivalenton.openv.rendering.MatrixFont] – mixed case,
 * umlauts, ß and proportional letters – instead of a capitals-only 5×7 alphabet. Each lit LED has
 * a bright core and a faint halo; the halo stays inside its own cell, so letters keep their shape.
 */
internal object DotMatrixArtwork {
    private val cache =
        object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }
    /** Pixels per dot of the cached bitmaps. */
    const val DOT = 4

    @Synchronized
    fun text(label: String, color: Int, square: Boolean = false): Bitmap {
        val key = "$label:$color:$square"
        cache.get(key)?.let {
            return it
        }
        val (cols, lit) = dev.ambivalenton.openv.rendering.MatrixFont.mask(label)
        val rows = dev.ambivalenton.openv.rendering.MatrixFont.ROWS
        val dot = DOT.toFloat()
        return createBitmap(cols * DOT, rows * DOT, Bitmap.Config.ARGB_8888)
            .also { bitmap ->
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val halo = androidx.core.graphics.ColorUtils.setAlphaComponent(color, 70)
                val core = androidx.core.graphics.ColorUtils.blendARGB(color, android.graphics.Color.WHITE, .35f)
                for (row in 0 until rows) for (col in 0 until cols) {
                    if (!lit[row * cols + col]) continue
                    val cx = (col + .5f) * dot; val cy = (row + .5f) * dot
                    if (square) {
                        paint.color = color
                        canvas.drawRect(col * dot + .3f, row * dot + .3f, (col + 1) * dot - .3f, (row + 1) * dot - .3f, paint)
                    } else {
                        paint.color = halo; canvas.drawCircle(cx, cy, dot * .48f, paint)
                        paint.color = color; canvas.drawCircle(cx, cy, dot * .36f, paint)
                        paint.color = core; canvas.drawCircle(cx - dot * .06f, cy - dot * .06f, dot * .16f, paint)
                    }
                }
                cache.put(key, bitmap)
            }
    }

    @Synchronized
    fun panel(width: Float, height: Float, color: Int, dotColor: Int): Bitmap {
        val w = (width * 2).roundToInt().coerceIn(1, 1200)
        val h = (height * 2).roundToInt().coerceIn(1, 1600)
        val key = "$w:$h:$color:$dotColor"
        cache.get(key)?.let {
            return it
        }
        return createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val path =
                android.graphics.Path().apply {
                    addRoundRect(
                        RectF(0f, 0f, w.toFloat(), h.toFloat()),
                        40f,
                        40f,
                        android.graphics.Path.Direction.CW,
                    )
                }
            canvas.clipPath(path)
            canvas.drawColor(color)
            paint.color = dotColor
            for (x in 6 until w step 12) for (y in 6 until h step 12) canvas.drawCircle(
                x.toFloat(),
                y.toFloat(),
                .8f,
                paint,
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.alpha = 90
            canvas.drawPath(path, paint)
            cache.put(key, bitmap)
        }
    }
}
