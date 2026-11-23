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
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

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
 * Picks [count] colours for a colourful theme: the theme's accent first, then colours of the theme's own
 * curated family and palette that lie clearly apart from it (build 101: at least [CAPSULE_HUE_STEP] degrees of
 * hue, so every page change is a big colour change). Without such a colour the accent's harmony fills in.
 */
internal fun capsuleHues(theme: AppTheme, accent: Color, count: Int, anchors: List<Color> = emptyList()): List<Color> {
    val swatches = appColorFamily(theme).colors.map { Color(it.color) }
    return vividCapsuleHues(accent, listOf(Color(theme.accent), Color(theme.secondary)) + anchors + swatches, count)
}

/** Build 101: neighbouring capsules differ by at least this many degrees of hue. */
internal const val CAPSULE_HUE_STEP = 70.0

private fun hueDistance(a: Double, b: Double): Double = min(abs(a - b), 360 - abs(a - b))

/**
 * Build 101: [count] capsule colours whose hues lie far apart, all belonging to the theme. The first is
 * [accent]. Each further one is the theme colour among [candidates] (secondary, tertiary, the family's
 * swatches …) that lies farthest from the colours chosen so far – if it lies at least [minStep] degrees
 * away from each. Otherwise colour harmony with the accent fills in (triad ±120°, split complement ±150°,
 * complement), with the accent's own strength and brightness. Before, roles that are the same colour in
 * most themes (secondary = tertiary) only turned by 18°: red to a slightly different red.
 */
internal fun vividCapsuleHues(accent: Color, candidates: List<Color>, count: Int, minStep: Double = CAPSULE_HUE_STEP): List<Color> {
    fun hct(c: Color) = Hct.fromInt(c.toArgb())
    val source = hct(accent)
    val pool = candidates.filter { hct(it).chroma > 16 }.distinct()
    val chosen = mutableListOf(accent)
    while (chosen.size < count) {
        val hues = chosen.map { hct(it).hue }
        fun gap(hue: Double) = hues.minOf { hueDistance(hue, it) }
        val fromTheme = pool.filter { it !in chosen && gap(hct(it).hue) >= minStep }.maxByOrNull { gap(hct(it).hue) }
        if (fromTheme != null) { chosen += fromTheme; continue }
        val harmony = listOf(120.0, -120.0, 150.0, -150.0, 180.0, 90.0, -90.0).map { (source.hue + it + 360.0) % 360.0 }
        val hue = harmony.firstOrNull { gap(it) >= minStep } ?: harmony.maxBy { gap(it) }
        chosen += Color(Hct.from(hue, source.chroma.coerceIn(24.0, 72.0), source.tone.coerceIn(30.0, 80.0)).toInt())
    }
    return chosen
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
    return CapsuleTone(hueLerp(this[i].fill, this[j].fill, t), lerp(this[i].ink, this[j].ink, t))
}

/**
 * Build 101: blends two colours around the colour wheel (OkLCh, the shorter way). With far-apart capsule
 * colours a straight blend passed through a dull grey halfway; this one stays colourful all the way.
 */
internal fun hueLerp(a: Color, b: Color, t: Float): Color {
    if (t <= 0f) return a
    if (t >= 1f) return b
    return runCatching {
        val x = a.convert(ColorSpaces.Oklab); val y = b.convert(ColorSpaces.Oklab)
        val cx = hypot(x.green, x.blue); val cy = hypot(y.green, y.blue)
        // A nearly grey end keeps the other end's hue (no spin through unrelated colours).
        val hx = if (cx < .02f) atan2(y.blue, y.green) else atan2(x.blue, x.green)
        val hy = if (cy < .02f) hx else atan2(y.blue, y.green)
        var d = hy - hx
        if (d > PI.toFloat()) d -= 2 * PI.toFloat()
        if (d < -PI.toFloat()) d += 2 * PI.toFloat()
        val h = hx + d * t; val c = cx + (cy - cx) * t; val l = x.red + (y.red - x.red) * t
        Color((l).coerceIn(0f, 1f), (c * cos(h)).coerceIn(-.5f, .5f), (c * sin(h)).coerceIn(-.5f, .5f),
            a.alpha + (b.alpha - a.alpha) * t, ColorSpaces.Oklab).convert(ColorSpaces.Srgb)
    }.getOrElse { lerp(a, b, t) }
}

private fun Color.luminanceValue(): Float = androidx.core.graphics.ColorUtils.calculateLuminance(toArgb()).toFloat()

/**
 * 2.7: every theme gives Übersicht, Suchen and Abfahrten their own capsule colour. Build 101: the
 * accent stays on Übersicht; Suchen and Abfahrten take the theme's own colours that lie far enough apart
 * (palette roles and the theme's curated swatches, so personal themes follow their own colours), otherwise
 * the accent's colour harmony – every change of page is a clearly visible change of colour. Monochrome
 * themes and the black-and-white mode use three grey steps. The design language keeps its kind of
 * capsule: solid, faintly tinted or softly blended. Each ink is checked against its own fill.
 */
@Composable fun rememberDestinationTones(count: Int, shell: Color, language: DesignLanguage, grey: Boolean,
    scheme: ColorScheme = MaterialTheme.colorScheme): List<CapsuleTone>? {
    val theme = LocalAppVisuals.current.theme
    return remember(count, shell, language, grey, theme, scheme.primary, scheme.secondary, scheme.tertiary, scheme.background,
        scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer) {
        if (count < 2) null else destinationTones(count, shell.compositeOver(scheme.background), language, grey,
            listOf(scheme.primary, scheme.tertiary, scheme.secondary),
            listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer),
            themeSwatches(theme))
    }
}

/** The theme's own curated colours (none for personal and system themes). */
internal fun themeSwatches(theme: AppTheme?): List<Color> =
    if (theme == null || theme.isOperatingSystem) emptyList()
    else listOf(Color(theme.accent), Color(theme.secondary)) + appColorFamily(theme).colors.map { Color(it.color) }

internal fun destinationTones(count: Int, base: Color, language: DesignLanguage, grey: Boolean,
    accents: List<Color>, containers: List<Color>, swatches: List<Color> = emptyList()): List<CapsuleTone> {
    val dark = base.luminanceValue() < .45f
    val greyscale = grey || accents.all { Hct.fromInt(it.toArgb()).chroma < 10 }
    fun readable(seed: Color, fill: Color) =
        Color(readableForeground(seed.toArgb().toLong() and 0xffffffffL, fill.compositeOver(base).toArgb().toLong() and 0xffffffffL).toInt())
    if (greyscale) return List(count) { i ->
        // Three clearly separated grey steps on the bar's own brightness.
        val fill = lerp(base, if (dark) Color.White else Color.Black, .14f + .13f * i)
        CapsuleTone(fill, readable(if (dark) Color.White else Color.Black, fill))
    }
    // Build 101: Übersicht keeps the accent; Suchen and Abfahrten get clearly different hues of the theme.
    val hues = vividCapsuleHues(accents.first(), accents.drop(1) + swatches, count)
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


/** A capsule in [hue]'s colour, 15 tone steps away from the bar, moderately saturated. */
internal fun tonalCapsule(base: Color, hue: Color, dark: Boolean, index: Int): Color {
    val bar = Hct.fromInt(base.toArgb()).tone
    val source = Hct.fromInt(hue.toArgb())
    val step = listOf(0.0, 5.0, -5.0)[index % 3]
    val tone = if (dark) (bar + 16.0 + step).coerceIn(24.0, 60.0) else (bar - 15.0 + step).coerceIn(45.0, 86.0)
    return Color(Hct.from(source.hue, source.chroma.coerceIn(24.0, 48.0), tone).toInt())
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
