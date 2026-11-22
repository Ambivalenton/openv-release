// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow

/** WCAG 2 relative luminance of an sRGB colour (alpha ignored). */
fun relativeLuminance(color:Color):Double {
    fun channel(value:Float):Double=if(value<=.04045f)value/12.92 else ((value+.055)/1.055).pow(2.4)
    return .2126*channel(color.red)+.7152*channel(color.green)+.0722*channel(color.blue)
}

/** WCAG 2 contrast ratio between two opaque colours (1..21). */
fun contrastRatio(a:Color,b:Color):Double {
    val la=relativeLuminance(a)+.05;val lb=relativeLuminance(b)+.05
    return maxOf(la,lb)/minOf(la,lb)
}

/**
 * Black or white ink with the better worst-case contrast. A translucent background is evaluated
 * over both extremes of whatever lies beneath it (for example a busy map), so the choice stays
 * stable without analysing map pixels.
 */
fun contrastInk(background:Color):Color {
    val composites=if(background.alpha>=.999f)listOf(background)
        else listOf(background.compositeOver(Color.Black),background.compositeOver(Color.White))
    val black=composites.minOf {contrastRatio(Color.Black,it)}
    val white=composites.minOf {contrastRatio(Color.White,it)}
    return if(white>black)Color.White else Color.Black
}

/** Monochrome outline opposite to the ink, so the glyph edge separates from any background. */
fun contrastOutline(ink:Color):Color=(if(relativeLuminance(ink)>.18)Color.Black else Color.White).copy(alpha=.82f)
