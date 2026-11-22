// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.isOperatingSystem
import dev.ambivalenton.openv.ui.theme.LocalAppVisuals
import dev.ambivalenton.openv.ui.theme.readableForeground
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** Fill and ink of the selection capsule for one destination. */
@Immutable data class CapsuleTone(val fill: Color, val ink: Color)

/**
 * Playful, colourful themes give every destination of a pill its own capsule colour from
 * the theme's curated colour family. Calm, monochrome and system themes keep their single container.
 */
fun colorfulCapsuleTheme(theme: AppTheme?): Boolean {
    if (theme == null || theme.isOperatingSystem || appColorFamily(theme) == DesignColorFamily.INK) return false
    return appThemeGroup(theme) in setOf(AppThemeGroup.PLAY, AppThemeGroup.RAINBOWS, AppThemeGroup.FESTIVALS,
        AppThemeGroup.ART, AppThemeGroup.RETRO,
        // The colourful collections of the ten design languages (poster, clay and colour blocks).
        AppThemeGroup.NEO_BRUTAL, AppThemeGroup.CLAY, AppThemeGroup.COLOR_BLOCK,
        // The playful collections of gaming worlds and pets.
        AppThemeGroup.GAMING, AppThemeGroup.PETS) ||
        theme in setOf(AppTheme.SYNTHWAVE, AppTheme.CYBERPUNK_NEON, AppTheme.PRINT_RISO, AppTheme.MINERAL_TERRAZZO,
            AppTheme.TEXTILE_PICNIC, AppTheme.TEXTILE_IKAT, AppTheme.CAFE_PATISSERIE, AppTheme.VIVALDI_PRIMAVERA_III)
}

/**
 * Picks [count] colours of the family that belong to the theme: the swatch nearest the
 * accent first, then the swatches nearest to the theme's own colours ([anchors]) that are still
 * clearly distinguishable. When the family has too few such swatches, harmonious neighbours of the
 * accent (a little hue, lighter or darker) fill up – never a far-off rainbow colour.
 */
internal fun capsuleHues(theme: AppTheme, accent: Color, count: Int, anchors: List<Color> = emptyList()): List<Color> {
    val swatches = appColorFamily(theme).colors.map { Color(it.color) }
        .filter { Hct.fromInt(it.toArgb()).chroma > 18 }.ifEmpty { listOf(accent) }
    val themeColors = (listOf(accent, Color(theme.accent), Color(theme.secondary)) + anchors)
        .filter { Hct.fromInt(it.toArgb()).chroma > 12 }.ifEmpty { listOf(accent) }
    fun hue(c: Color) = Hct.fromInt(c.toArgb()).hue
    fun distance(a: Double, b: Double) = min(abs(a - b), 360 - abs(a - b))
    fun nearTheme(c: Color) = themeColors.minOf { distance(hue(it), hue(c)) }
    val ranked = swatches.sortedBy { nearTheme(it) }
    val chosen = mutableListOf(swatches.minBy { distance(hue(it), hue(accent)) })
    for (s in ranked) {
        if (chosen.size >= count) break
        if (s !in chosen && nearTheme(s) <= 75.0 && chosen.all { separated(it, s) }) chosen += s
    }
    return harmonious(chosen, count)
}

/** Two capsule colours read apart by hue or by brightness. */
internal fun separated(a: Color, b: Color): Boolean {
    val x = Hct.fromInt(a.toArgb()); val y = Hct.fromInt(b.toArgb())
    val dh = min(abs(x.hue - y.hue), 360 - abs(x.hue - y.hue))
    return dh >= 22 || abs(x.tone - y.tone) >= 12
}

/**
 * Fills [colors] up to [count] and parts colours that lie too close together with
 * harmonious neighbours of the same colour: a small hue step together with a lighter or darker tone.
 */
internal fun harmonious(colors: List<Color>, count: Int): List<Color> {
    val steps = listOf(18.0 to 12.0, -18.0 to -12.0, 30.0 to -8.0, -30.0 to 8.0, 0.0 to 22.0, 0.0 to -22.0, 45.0 to 0.0, -45.0 to 0.0)
    val result = mutableListOf<Color>()
    val wanted = List(count) { colors[it % colors.size] }
    wanted.forEach { color ->
        if (result.all { separated(it, color) }) { result += color; return@forEach }
        val source = Hct.fromInt(color.toArgb())
        val variant = steps.asSequence().map { (dh, dt) ->
            Color(Hct.from((source.hue + dh + 360.0) % 360.0, source.chroma.coerceAtLeast(20.0), (source.tone + dt).coerceIn(22.0, 86.0)).toInt())
        }.firstOrNull { candidate -> result.all { separated(it, candidate) } } ?: color
        result += variant
    }
    return result
}

/** Per-destination capsule tones, or null when the theme keeps one capsule colour. [scheme]: the
 * palette to derive them from (The navigation passes the target of a running design blend). */
@Composable fun rememberCapsuleTones(count: Int, shell: Color, scheme: ColorScheme = MaterialTheme.colorScheme): List<CapsuleTone>? {
    val theme = LocalAppVisuals.current.theme
    return remember(theme, count, shell, scheme.primary, scheme.background) {
        if (!colorfulCapsuleTheme(theme) || count < 2) null
        else {
            val base = shell.compositeOver(scheme.background)
            val dark = base.luminanceValue() < .45f
            capsuleHues(theme!!, scheme.primary, count, listOf(scheme.secondary, scheme.tertiary)).map { hue ->
                val fill = lerp(base, hue, if (dark) .52f else .40f)
                val inkSeed = lerp(hue, if (dark) Color.White else Color.Black, .55f)
                CapsuleTone(fill, Color(readableForeground(inkSeed.toArgb().toLong() and 0xffffffffL, fill.toArgb().toLong() and 0xffffffffL).toInt()))
            }
        }
    }
}

/** Continuous blend between neighbouring destinations, following the capsule's actual position. */
fun List<CapsuleTone>.at(position: Float): CapsuleTone {
    val p = position.coerceIn(0f, lastIndex.toFloat())
    val i = floor(p).toInt().coerceIn(0, lastIndex); val j = (i + 1).coerceAtMost(lastIndex); val t = p - i
    return CapsuleTone(lerp(this[i].fill, this[j].fill, t), lerp(this[i].ink, this[j].ink, t))
}

private fun Color.luminanceValue(): Float = androidx.core.graphics.ColorUtils.calculateLuminance(toArgb()).toFloat()

/**
 * 2.7: every theme gives Übersicht, Suchen and Abfahrten their own capsule colour. The tones come
 * from the active palette (primary, tertiary and secondary roles, so personal themes follow their
 * own colours). Roles that lie too close together are parted by harmonious neighbours of
 * the same colour (a little hue, lighter or darker), so the capsules always belong to the theme. Monochrome
 * themes and the black-and-white mode use three grey steps. The design language keeps its kind of
 * capsule: solid, faintly tinted or softly blended. Each ink is checked against its own fill.
 */
@Composable fun rememberDestinationTones(count: Int, shell: Color, language: DesignLanguage, grey: Boolean,
    scheme: ColorScheme = MaterialTheme.colorScheme): List<CapsuleTone>? {
    return remember(count, shell, language, grey, scheme.primary, scheme.secondary, scheme.tertiary, scheme.background,
        scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer) {
        if (count < 2) null else destinationTones(count, shell.compositeOver(scheme.background), language, grey,
            listOf(scheme.primary, scheme.tertiary, scheme.secondary),
            listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer))
    }
}

internal fun destinationTones(count: Int, base: Color, language: DesignLanguage, grey: Boolean,
    accents: List<Color>, containers: List<Color>): List<CapsuleTone> {
    val dark = base.luminanceValue() < .45f
    val greyscale = grey || accents.all { Hct.fromInt(it.toArgb()).chroma < 10 }
    fun readable(seed: Color, fill: Color) =
        Color(readableForeground(seed.toArgb().toLong() and 0xffffffffL, fill.compositeOver(base).toArgb().toLong() and 0xffffffffL).toInt())
    if (greyscale) return List(count) { i ->
        // Three clearly separated grey steps on the bar's own brightness.
        val fill = lerp(base, if (dark) Color.White else Color.Black, .14f + .13f * i)
        CapsuleTone(fill, readable(if (dark) Color.White else Color.Black, fill))
    }
    val hues = distinctHues(List(count) { accents[it % accents.size] })
    return hues.mapIndexed { i, hue ->
        val fill = when (language) {
            DesignLanguage.BRUTAL, DesignLanguage.PIXEL, DesignLanguage.SWISS -> hue
            DesignLanguage.NEON, DesignLanguage.OUTLINE, DesignLanguage.TERMINAL, DesignLanguage.EDITORIAL -> lerp(base, hue, .16f)
            DesignLanguage.SOFT, DesignLanguage.GLASS, DesignLanguage.INK -> lerp(base, hue, if (dark) .26f else .18f)
            // The theme's own hue at a fixed, clearly visible step from the bar (containers often
            // had nearly the bar's brightness); the three capsules differ by hue and a small tone step.
            else -> tonalCapsule(base, hue, dark, i)
        }
        val seed = when (language) {
            DesignLanguage.BRUTAL, DesignLanguage.PIXEL, DesignLanguage.SWISS -> if (fill.luminanceValue() > .45f) Color.Black else Color.White
            else -> lerp(hue, if (dark) Color.White else Color.Black, .45f)
        }
        CapsuleTone(fill, readable(seed, fill))
    }
}

/** Keeps neighbouring capsule colours apart. With harmonious neighbours of the theme colour, never 40° turns. */
internal fun distinctHues(colors: List<Color>): List<Color> = harmonious(colors, colors.size)

/** A capsule in [hue]'s colour, 15 tone steps away from the bar, moderately saturated. */
internal fun tonalCapsule(base: Color, hue: Color, dark: Boolean, index: Int): Color {
    val bar = Hct.fromInt(base.toArgb()).tone
    val source = Hct.fromInt(hue.toArgb())
    val step = listOf(0.0, 5.0, -5.0)[index % 3]
    val tone = if (dark) (bar + 16.0 + step).coerceIn(24.0, 60.0) else (bar - 15.0 + step).coerceIn(45.0, 86.0)
    return Color(Hct.from(source.hue, source.chroma.coerceIn(14.0, 44.0), tone).toInt())
}

/**
 * Capsule tones that follow a design change like every other colour. The tones are worked
 * out once for the new design and blended with the design's own ease ([dev.ambivalenton.openv.ui.theme.themeBlendSpec]).
 * Worked out from the half-blended palette, the thresholds above (separation, grey fallback) made the
 * capsules jump at the start and once more in the middle of the blend.
 */
@Composable fun blendedCapsuleTones(target: List<CapsuleTone>?, animate: Boolean): List<CapsuleTone>? {
    if (target == null) return null
    val spec = dev.ambivalenton.openv.ui.theme.themeBlendSpec(animate)
    return target.mapIndexed { i, tone ->
        CapsuleTone(animateColorAsState(tone.fill, spec, label = "capsule-fill-$i").value,
            animateColorAsState(tone.ink, spec, label = "capsule-ink-$i").value)
    }
}
