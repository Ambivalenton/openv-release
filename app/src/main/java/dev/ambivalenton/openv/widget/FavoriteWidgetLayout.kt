// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.widget

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min

/**
 * Build 107 · how a favourite looks in its tile: only the symbol (small tiles), symbol above its name
 * (square tiles), or symbol beside name and stop (wide tiles).
 */
internal enum class FavoriteTile { MARK, STACKED, WIDE }

/** The favourites widget's grid at its real size. [visibleRows] of [rows] fit; the rest scrolls. */
internal data class FavoritePlan(
    val padding: Float,
    val gap: Float,
    val header: Float,
    val columns: Int,
    val rows: Int,
    val visibleRows: Int,
    val tileWidth: Float,
    val tileHeight: Float,
    val tile: FavoriteTile,
) {
    val scroll: Boolean get() = rows > visibleRows
}

private const val MIN_TILE = 44f
private const val MIN_LABELLED = 52f

/**
 * Every launcher size gets the arrangement that shows the most favourites in readable, tappable tiles:
 * a single symbol at 1×1, a row of named tiles in a flat widget, a list of wide tiles in a tall narrow
 * one, a grid of large tiles in a big one. A short header with the search button appears only where
 * there is room to spare. Pure, so every size is checked in a unit test.
 */
internal fun favoritePlan(width: Float, height: Float, count: Int, fontScale: Float = 1f): FavoritePlan {
    val w = if (width.isFinite()) width.coerceAtLeast(40f) else 64f
    val h = if (height.isFinite()) height.coerceAtLeast(40f) else 64f
    val font = if (fontScale.isFinite()) fontScale.coerceIn(.85f, 1.6f) else 1f
    val small = min(w, h) < 100f
    val padding = if (small) 4f else 8f
    val gap = if (small) 4f else 6f
    val header = if (w >= 180f && h >= 200f) 36f * min(font, 1.3f) else 0f
    val aw = w - 2 * padding
    val ah = (h - 2 * padding - if (header > 0f) header + gap else 0f).coerceAtLeast(MIN_TILE)
    val n = count.coerceAtLeast(1)
    var best: Pair<Float, FavoritePlan>? = null
    for (columns in 1..min(n, 6)) {
        val tw = (aw - gap * (columns - 1)) / columns
        if (tw < 40f && columns > 1) continue
        val rows = ceil(n / columns.toFloat()).toInt()
        val visible = floor((ah + gap) / (MIN_TILE + gap)).toInt().coerceIn(1, rows)
        val th = (ah - gap * (visible - 1)) / visible
        val aspect = tw / th
        val tile = when {
            aspect >= 1.6f && tw >= 120f && th >= 40f -> FavoriteTile.WIDE
            tw >= MIN_LABELLED && th >= MIN_LABELLED -> FavoriteTile.STACKED
            else -> FavoriteTile.MARK
        }
        val hidden = n - min(n, visible * columns)
        val shape = when (tile) {
            FavoriteTile.WIDE -> if (aspect > 4f) (aspect - 4f) * 2f else 0f
            FavoriteTile.STACKED -> abs(ln(aspect)) * 10f
            FavoriteTile.MARK -> abs(ln(aspect)) * 6f + 10f
        }
        val score = hidden * 100f + shape + (min(rows, visible) * columns - min(n, visible * columns)) * 6f
        // A widget only one tile row high does not scroll: it shows the first favourites (highlighted first).
        val plan = FavoritePlan(padding, gap, header, columns, if (visible == 1) 1 else rows, visible, tw, th, tile)
        if (best == null || score < best.first) best = score to plan
    }
    return best!!.second
}
