// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.theme

import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.color.utilities.Hct

/** HCT Tone is perceptual luminance. Changing it never rotates the source hue. */
fun toneColor(hue: Double, chroma: Double, tone: Double): Long =
    Hct.from(hue, chroma, tone.coerceIn(10.0, 90.0)).toInt().toLong() and 0xffffffffL

fun toneColor(seed: Long, tone: Double): Long =
    Hct.fromInt(seed.toInt()).let { toneColor(it.hue, it.chroma, tone) }

fun parseColorHex(text: String, allowAlpha: Boolean = false): Long? {
    val value = text.trim().removePrefix("#")
    if (
        !(value.length == 6 || (allowAlpha && value.length == 8)) ||
            !value.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }
    )
        return null
    return value.toLongOrNull(16)?.let { if (value.length == 6) it or 0xff000000L else it }
}

fun colorHex(color: Long, alpha: Boolean = false): String =
    "#" +
        (color and if (alpha) 0xffffffffL else 0xffffffL)
            .toString(16)
            .uppercase()
            .padStart(if (alpha) 8 else 6, '0')

/** The chosen foreground is adjusted only when needed; every opaque pair meets AA. */
fun readableForeground(foreground: Long, background: Long, minimum: Double = 4.5): Long {
    val bg = background.toInt() or 0xff000000.toInt()
    val fg = foreground.toInt() or 0xff000000.toInt()
    if (ColorUtils.calculateContrast(fg, bg) >= minimum) return fg.toLong() and 0xffffffffL
    val source = Hct.fromInt(fg)
    return (10..90)
        .map { Hct.from(source.hue, source.chroma, it.toDouble()).toInt() }
        .filter { ColorUtils.calculateContrast(it, bg) >= minimum }
        .minByOrNull { kotlin.math.abs(Hct.fromInt(it).tone - source.tone) }
        ?.let { it.toLong() and 0xffffffffL }
        ?: if (
            ColorUtils.calculateContrast(-1, bg) >=
                ColorUtils.calculateContrast(0xff000000.toInt(), bg)
        )
            0xffffffffL
        else 0xff000000L
}
