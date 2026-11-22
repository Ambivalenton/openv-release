// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.rendering

import android.graphics.*
import android.util.LruCache
import androidx.core.graphics.createBitmap
import dev.ambivalenton.openv.data.model.IconStyle
import dev.ambivalenton.openv.widget.GlanceThemeHelper

/** The exact same masks are used by Compose and RemoteViews, with host-aware tint per layer. */
object FavoriteBadgeArtwork {
    fun iconFraction(shape: IconStyle): Float = if (shape == IconStyle.PILL) .32f else .42f

    private val cache = object : LruCache<String, Bitmap>(512 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    @Synchronized
    fun mask(shape: IconStyle, accent: Boolean): Bitmap {
        val key = "$shape:$accent"
        cache.get(key)?.let { return it }
        val bitmap = createBitmap(144, 144)
        val rect = if (accent) RectF(14f, 14f, 130f, 130f) else RectF(0f, 0f, 144f, 144f)
        Canvas(bitmap).drawPath(GlanceThemeHelper.shapePath(rect, shape, 32f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        cache.put(key, bitmap)
        return bitmap
    }
}
