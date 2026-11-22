// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.ui.theme.readableForeground
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * Line colour = the "opposite" of both node colours on the CAM16 opponent-colour wheel (red ↔ green,
 * blue ↔ yellow), the same perceptual model (HCT) the app uses for all colours. Two blue favourites
 * give a yellow line, two red ones a green line. The tone is then adjusted to at least 3:1 contrast
 * against the background (WCAG 1.4.11); on light backgrounds yellow therefore becomes olive.
 */

/** CAM16 unique hues (CIECAM02/CAM16 hue quadrature): red, yellow, green, blue, red again. */
private val UNIQUE = doubleArrayOf(20.14, 90.0, 164.25, 237.53, 380.14)

/** CAM16 hue h → opponent angle q (red 0°, yellow 90°, green 180°, blue 270°), piecewise linear. */
fun opponentAngle(hue: Double): Double {
    val x = if (hue < UNIQUE[0]) hue + 360.0 else hue
    val i = (0 until 4).first { x <= UNIQUE[it + 1] }
    return (i * 90.0 + (x - UNIQUE[i]) / (UNIQUE[i + 1] - UNIQUE[i]) * 90.0) % 360.0
}

fun cam16Hue(opponent: Double): Double {
    val q = ((opponent % 360.0) + 360.0) % 360.0
    val i = (q / 90.0).toInt().coerceIn(0, 3)
    return (UNIQUE[i] + (q - i * 90.0) / 90.0 * (UNIQUE[i + 1] - UNIQUE[i])) % 360.0
}

fun circularMean(a: Double, b: Double): Double? {
    val x = cos(Math.toRadians(a)) + cos(Math.toRadians(b))
    val y = sin(Math.toRadians(a)) + sin(Math.toRadians(b))
    return if (hypot(x, y) < 1e-6) null else (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Hue (CAM16) of the line between two node colours; null when both are achromatic. */
fun opponentLineHue(a: Int?, b: Int?): Double? {
    val sources = listOfNotNull(a, b).map { Hct.fromInt(it) }.filter { it.chroma >= 12.0 }
    if (sources.isEmpty()) return null
    val q = sources.map { opponentAngle(it.hue) }
    // Exactly opposite colours (e.g. blue + yellow) have no mean: take the perpendicular, deterministically.
    val mean = if (q.size == 1) q[0] else circularMean(q[0], q[1]) ?: ((q[0] + 90.0) % 360.0)
    return cam16Hue((mean + 180.0) % 360.0)
}

/** ARGB line colour with ≥ 3:1 contrast to [background]; [fallback] (theme colour) when no hue exists. */
fun edgeLineColor(a: Int?, b: Int?, background: Int, fallback: Int, darkTheme: Boolean): Int {
    val hue = opponentLineHue(a, b)
        ?: return readableForeground(fallback.toLong() and 0xFFFFFFFFL, background.toLong() and 0xFFFFFFFFL, 3.0).toInt()
    val chroma = listOfNotNull(a, b).map { Hct.fromInt(it).chroma }.average().coerceIn(48.0, 90.0)
    val preferred = if (darkTheme) 70.0 else 55.0
    val opaque = background or (0xFF shl 24)
    return (10..90).map { Hct.from(hue, chroma, it.toDouble()).toInt() }
        .filter { ColorUtils.calculateContrast(it or (0xFF shl 24), opaque) >= 3.0 }
        .minByOrNull { kotlin.math.abs(Hct.fromInt(it).tone - preferred) }
        ?: readableForeground(fallback.toLong() and 0xFFFFFFFFL, background.toLong() and 0xFFFFFFFFL, 3.0).toInt()
}

/** Monochrome mode: lines differ by dash pattern instead of hue. */
fun monochromeDash(index: Int): FloatArray? = when (index % 8) {
    0 -> null
    1 -> floatArrayOf(10f, 6f)
    2 -> floatArrayOf(3f, 5f)
    3 -> floatArrayOf(14f, 4f, 3f, 4f)
    4 -> floatArrayOf(6f, 6f)
    5 -> floatArrayOf(18f, 6f)
    6 -> floatArrayOf(2f, 3f)
    else -> floatArrayOf(10f, 4f, 2f, 4f, 2f, 4f)
}
