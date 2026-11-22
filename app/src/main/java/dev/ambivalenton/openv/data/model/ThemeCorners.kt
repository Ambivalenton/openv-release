// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** These themes intentionally use right angles, including at enlarged corner settings.
 * Since build 81 every design with [ThemeShape.SHARP] belongs to them. */
val AppTheme.hasSquareCorners:Boolean get()=shapeStyle==ThemeShape.SHARP || this in setOf(
    AppTheme.INDUSTRIAL,AppTheme.MONOCHROME,AppTheme.MONOCHROME_INK,
    AppTheme.BRUTALIST,AppTheme.SPACE_TERMINAL,
)
