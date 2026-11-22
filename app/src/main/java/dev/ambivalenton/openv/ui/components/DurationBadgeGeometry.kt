// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

/** Actual visible glyph bounds, independent of font ascent, advance width or trailing spaces. */
internal data class DurationInk(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}
internal data class DurationPlacement(val numberX: Float, val unitX: Float, val baseline: Float)

internal fun durationPlacement(width: Float, height: Float, number: DurationInk, unit: DurationInk, gap: Float): DurationPlacement {
    val left = (width - number.width - gap - unit.width) / 2f
    return DurationPlacement(
        numberX = left - number.left,
        unitX = left + number.width + gap - unit.left,
        baseline = (height - number.height) / 2f - number.top,
    )
}
