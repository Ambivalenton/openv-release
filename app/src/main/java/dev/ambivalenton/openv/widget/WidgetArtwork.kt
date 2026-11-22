// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import android.graphics.*
import android.util.LruCache
import androidx.core.graphics.createBitmap
import dev.ambivalenton.openv.data.model.IconStyle
import kotlin.math.*

/** Small bounded bitmaps simulate elevation supported by RemoteViews; no live backdrop capture. */
object WidgetArtwork {
    private val cache =
        object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }

    @Synchronized
    fun surface(
        width: Float,
        height: Float,
        color: Int,
        radius: Float,
        elevation: Float,
        opacity: Int = 100,
        shape: IconStyle? = null,
        drawShadow: Boolean = true,
        shadowOnly: Boolean = false,
    ): Bitmap {
        val w = (width * 1.5f).roundToInt().coerceIn(32, 320)
        val h = (height * 1.5f).roundToInt().coerceIn(32, 420)
        val key = "$w:$h:$color:$radius:$elevation:$opacity:$shape:$drawShadow:$shadowOnly"
        cache.get(key)?.let {
            return it
        }
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val inset = (elevation * 1.1f + 1).coerceAtMost(min(w, h) * .14f)
        val rect = RectF(inset, inset, w - inset, h - inset)
        val path = GlanceThemeHelper.shapePath(rect, shape, radius * 1.5f)
        // Render shadow alone first, then clear its interior so opacity is applied exactly once.
        if (drawShadow && elevation > 0 && opacity > 0) {
            paint.color = Color.BLACK
            paint.setShadowLayer(
                elevation * 1.2f,
                0f,
                elevation * .45f,
                Color.argb((opacity * .35f).toInt(), 0, 0, 0),
            )
            canvas.drawPath(path, paint)
            paint.clearShadowLayer()
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawPath(path, paint)
            paint.xfermode = null
        }
        paint.color = color
        paint.alpha = (opacity.coerceIn(0, 100) * 2.55f).roundToInt()
        if (!shadowOnly) canvas.drawPath(path, paint)
        cache.put(key, bitmap)
        return bitmap
    }

    fun surface(
        width: Float,
        height: Float,
        color: Int,
        radius: Float,
        elevation: Int,
        opacity: Int = 100,
        shape: IconStyle? = null,
        drawShadow: Boolean = true,
        shadowOnly: Boolean = false,
    ): Bitmap =
        surface(
            width,
            height,
            color,
            radius,
            elevation.toFloat(),
            opacity,
            shape,
            drawShadow,
            shadowOnly,
        )

    @Synchronized
    fun border(
        width: Float,
        height: Float,
        color: Int,
        radius: Float,
        shape: IconStyle? = null,
    ): Bitmap {
        val w = (width * 1.5f).roundToInt().coerceIn(16, 640)
        val h = (height * 1.5f).roundToInt().coerceIn(16, 840)
        val key = "border:$w:$h:$color:$radius:$shape"
        cache.get(key)?.let {
            return it
        }
        return createBitmap(w, h).also {
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
            Canvas(it)
                .drawPath(
                    GlanceThemeHelper.shapePath(
                        RectF(2f, 2f, w - 2f, h - 2f),
                        shape,
                        radius * 1.5f,
                    ),
                    paint,
                )
            cache.put(key, it)
        }
    }

    /** Translucent light, fine grain and an inset rim; alpha zero also removes every highlight. */
    @Synchronized
    fun glass(width: Float, height: Float, radius: Float, depth: Int, opacity: Int): Bitmap {
        val w = (width * 1.5f).roundToInt().coerceIn(16, 640)
        val h = (height * 1.5f).roundToInt().coerceIn(16, 840)
        val key = "glass:$w:$h:$radius:$depth:$opacity"
        cache.get(key)?.let {
            return it
        }
        val bitmap = createBitmap(w, h)
        val amount = depth.coerceIn(0, 100) / 100f * opacity.coerceIn(0, 100) / 100f
        if (amount > 0) {
            val canvas = Canvas(bitmap)
            val rect = RectF(1f, 1f, w - 1f, h - 1f)
            val path = GlanceThemeHelper.shapePath(rect, null, radius * 1.5f)
            canvas.save()
            canvas.clipPath(path)
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader =
                        LinearGradient(
                            0f,
                            0f,
                            w.toFloat(),
                            h.toFloat(),
                            intArrayOf(
                                Color.argb((48 * amount).toInt(), 255, 255, 255),
                                Color.TRANSPARENT,
                                Color.argb((12 * amount).toInt(), 255, 255, 255),
                            ),
                            floatArrayOf(0f, .55f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                }
            canvas.drawPath(path, paint)
            paint.shader = null
            paint.color = Color.argb((13 * amount).toInt(), 255, 255, 255)
            for (x in 3 until w step 5) for (y in 3 until h step 5) {
                if ((x * 17 + y * 31) % 7 < 3) canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
            }
            canvas.restore()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.shader =
                LinearGradient(
                    0f,
                    0f,
                    0f,
                    h.toFloat(),
                    Color.argb((110 * amount).toInt(), 255, 255, 255),
                    Color.argb((12 * amount).toInt(), 255, 255, 255),
                    Shader.TileMode.CLAMP,
                )
            canvas.drawPath(path, paint)
        }
        cache.put(key, bitmap)
        return bitmap
    }
}
