// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import dev.ambivalenton.openv.data.model.*
import java.time.ZonedDateTime
import kotlin.math.ceil
import kotlin.math.floor

data class WidgetGrid(
    val columns: Int,
    val tileWidth: Float,
    val tileHeight: Float,
    val width: Float,
    val height: Float,
    val padding: Float,
    val scroll: Boolean,
)

fun widgetGrid(
    width: Float,
    height: Float,
    count: Int,
    style: WidgetStyle,
    fontScale: Float,
    wideCount: Int = 0,
): WidgetGrid {
    val w = width.coerceAtLeast(40f)
    val h = height.coerceAtLeast(40f)
    val padding = if (minOf(w, h) <= 72) 0f else if (minOf(w, h) < 128) 4f else 8f
    val aw = (w - 2 * padding).coerceAtLeast(24f)
    val ah = (h - 2 * padding).coerceAtLeast(24f)
    // Prefer columns that can actually carry two readable text lines. When none fit,
    // pack icons at their natural density instead of creating empty scrolling text cells.
    val wide = wideCount.coerceIn(0, count.coerceAtLeast(0))
    val normalCount = (count - wide).coerceAtLeast(0)
    fun rowsFor(columns: Int) = wide + ceil(normalCount.toDouble() / columns).toInt()
    val textColumns =
        (1..4)
            .filter { columns ->
                val rows = rowsFor(columns).coerceAtLeast(1)
                aw / columns >= 84 * fontScale && minOf(aw / columns, ah / rows) >= 90 * fontScale
            }
            .maxOrNull()
    val columns =
        (if (!style.iconsOnly && textColumns != null) textColumns
            else floor(aw / 64f).toInt().coerceIn(1, 4))
            .coerceAtMost(normalCount.coerceAtLeast(1))
    val rows = rowsFor(columns).coerceAtLeast(1)
    val tileWidth = aw / columns
    // Reserve complete rows. A highlighted tile must not force a 96dp minimum into a
    // 2-row widget; its content adapts to the resulting height instead.
    val visibleRows = minOf(rows, floor(ah / 48f).toInt().coerceAtLeast(1))
    val tileHeight =
        minOf(ah / visibleRows, (if (style.iconsOnly) 80f else 112f) * fontScale.coerceIn(1f, 2f))
    val contentHeight = minOf(ah, rows * tileHeight)
    return WidgetGrid(
        columns,
        tileWidth,
        tileHeight,
        aw,
        if (style.compactBackground) contentHeight else ah,
        padding,
        rows > visibleRows || rows > 10,
    )
}

fun orderedFavorites(favorites: List<Favorite>, now: ZonedDateTime) =
    favorites.sortedByDescending { it.rule?.matches(now) == true }
