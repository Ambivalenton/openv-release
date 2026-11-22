// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb

/** WCAG AAA contrast for normal text; accents used as text colour reach at least this. */
const val HIGH_CONTRAST_TEXT_RATIO = 7.0

/**
 * "Hoher Textkontrast" (Schrift & Lesbarkeit). Until build 73 only the on-colours turned black or
 * white, which most designs already almost were, so the setting was barely visible. Since build 74
 * it applies to every design:
 *  - every surface is opaque (glass designs included), so text is judged against what is really there;
 *    a mid-tone surface (where neither black nor white reaches 7:1, e.g. NeXTSTEP grey) is made a
 *    little lighter or darker in its own hue until one of them does;
 *  - every on-colour is pure black or white, whichever contrasts more with its own surface;
 *  - accents that are also used as text (primary, secondary, tertiary, error) keep their hue but are
 *    darkened or lightened to at least 7:1 against the background;
 *  - outlines take the ink colour.
 * Typography adds one weight step and decorative textures step back behind text (see AppThemeEngine).
 */
fun highContrastScheme(s: ColorScheme): ColorScheme {
    val bg = legibleSurface(s.background.copy(alpha = 1f))
    fun solid(c: Color): Color = legibleSurface(c.compositeOver(bg))
    fun ink(background: Color): Color = contrastInk(solid(background))
    fun strong(accent: Color): Color = Color(readableForeground(accent.toArgb().toLong() and 0xffffffffL,
        bg.toArgb().toLong() and 0xffffffffL, HIGH_CONTRAST_TEXT_RATIO).toInt())
    val surface = solid(s.surface)
    val primary = strong(s.primary); val secondary = strong(s.secondary)
    val tertiary = strong(s.tertiary); val error = strong(s.error)
    val inverse = legibleSurface(s.inverseSurface.compositeOver(bg))
    val outline = ink(surface)
    return s.copy(
        primary = primary, onPrimary = contrastInk(primary),
        primaryContainer = solid(s.primaryContainer), onPrimaryContainer = ink(s.primaryContainer),
        secondary = secondary, onSecondary = contrastInk(secondary),
        secondaryContainer = solid(s.secondaryContainer), onSecondaryContainer = ink(s.secondaryContainer),
        tertiary = tertiary, onTertiary = contrastInk(tertiary),
        tertiaryContainer = solid(s.tertiaryContainer), onTertiaryContainer = ink(s.tertiaryContainer),
        error = error, onError = contrastInk(error),
        errorContainer = solid(s.errorContainer), onErrorContainer = ink(s.errorContainer),
        background = bg, onBackground = ink(bg),
        surface = surface, onSurface = ink(surface),
        surfaceVariant = solid(s.surfaceVariant), onSurfaceVariant = ink(s.surfaceVariant),
        surfaceContainerLowest = solid(s.surfaceContainerLowest), surfaceContainerLow = solid(s.surfaceContainerLow),
        surfaceContainer = solid(s.surfaceContainer), surfaceContainerHigh = solid(s.surfaceContainerHigh),
        surfaceContainerHighest = solid(s.surfaceContainerHighest),
        surfaceBright = solid(s.surfaceBright), surfaceDim = solid(s.surfaceDim),
        inverseSurface = inverse, inverseOnSurface = ink(inverse),
        inversePrimary = Color(readableForeground(s.inversePrimary.toArgb().toLong() and 0xffffffffL,
            inverse.toArgb().toLong() and 0xffffffffL, HIGH_CONTRAST_TEXT_RATIO).toInt()),
        outline = outline, outlineVariant = outline.copy(alpha = .75f),
    )
}

/**
 * A surface on which black or white text reaches [HIGH_CONTRAST_TEXT_RATIO]. Mid tones move towards
 * the nearer end in their own hue (HCT tone), so the design keeps its colour.
 */
internal fun legibleSurface(surface: Color): Color {
    fun best(c: Color) = maxOf(contrastRatio(Color.Black, c), contrastRatio(Color.White, c))
    if (best(surface) >= HIGH_CONTRAST_TEXT_RATIO) return surface
    val hct = dev.ambivalenton.openv.color.utilities.Hct.fromInt(surface.toArgb())
    val lighter = hct.tone >= 50.0
    var tone = hct.tone
    while (tone in 0.0..100.0) {
        tone += if (lighter) 1.0 else -1.0
        val candidate = Color(dev.ambivalenton.openv.color.utilities.Hct.from(hct.hue, hct.chroma, tone.coerceIn(0.0, 100.0)).toInt())
        if (best(candidate) >= HIGH_CONTRAST_TEXT_RATIO) return candidate
    }
    return if (lighter) Color.White else Color.Black
}
