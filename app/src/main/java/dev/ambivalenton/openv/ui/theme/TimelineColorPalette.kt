// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.theme

import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import dev.ambivalenton.openv.data.model.*

val LocalTimelineSeed = staticCompositionLocalOf<Long?> { null }
val LocalTimelineLineColors = staticCompositionLocalOf<Map<LineType, Long>> { emptyMap() }

val LocalTimelineTheme = staticCompositionLocalOf { TimelineTheme.ORIGINAL }

@Immutable
data class TimelineInk(val container: Color, val content: Color, val rail: Color = container)

@Immutable
class TimelineColorPalette(private val colors: Map<LineType, TimelineInk>) {
    operator fun get(type: LineType): TimelineInk = colors.getValue(type)
}

/** Choose the higher WCAG contrast, so every named palette keeps readable line labels. */
private fun contrastingContent(color: Color): Color =
    if ((color.luminance() + .05f) / .05f >= 1.05f / (color.luminance() + .05f)) Color.Black
    else Color.White

fun createTimelinePalette(
    theme: TimelineTheme,
    scheme: ColorScheme,
    systemScheme: ColorScheme = scheme,
    seed: Long? = null,
    overrides: Map<LineType, Long> = emptyMap(),
    /** The active app design; its language shapes the "App-Theme" bar colours. */
    appTheme: AppTheme? = null,
): TimelineColorPalette {
    val dark = scheme.background.luminance() < .3f
    val colorTheme=when(theme) {
        TimelineTheme.APP_THEME->TimelineTheme.APP_THEME
        TimelineTheme.GLASS->TimelineTheme.PASTEL
        TimelineTheme.NEON,TimelineTheme.PIXEL->TimelineTheme.VIVID
        TimelineTheme.GRADIENT,TimelineTheme.BRAID->TimelineTheme.OCEAN
        else->if(theme.style==TimelineBarStyle.CLASSIC) theme else TimelineTheme.ORIGINAL
    }
    val tones =
        when (colorTheme) {
            TimelineTheme.OCEAN ->
                listOf(
                    0xFF007C91,
                    0xFF1255A2,
                    0xFF006B68,
                    0xFF36C5CF,
                    0xFF06466B,
                    0xFF327CB5,
                    0xFF009A9C,
                    0xFF5D7F91,
                    0xFF165B82,
                )
            TimelineTheme.PASTEL ->
                listOf(
                    0xFFBEE4C8,
                    0xFFBEDBFF,
                    0xFFE1CCF4,
                    0xFFFFE5AD,
                    0xFFBADFDB,
                    0xFFF5C3C6,
                    0xFFBFE9EF,
                    0xFFD6DCE5,
                    0xFFE4D4CF,
                )
            TimelineTheme.VIVID ->
                listOf(
                    0xFF00873D,
                    0xFF005AE8,
                    0xFF9C20DD,
                    0xFFFFC400,
                    0xFF007A80,
                    0xFFE12A41,
                    0xFF006999,
                    0xFF565D73,
                    0xFFC83D80,
                )
            else -> emptyList()
        }
    return TimelineColorPalette(
        LineType.entries.associateWith { type ->
            val custom = if(theme==TimelineTheme.APP_THEME)null else
                overrides[type]
                    ?: seed?.let {
                        toneColor(
                            it,
                            listOf(42.0, 58.0, 72.0, 85.0, 34.0, 50.0, 65.0, 78.0, 46.0)[
                                type.ordinal],
                        )
                    }
            if (custom != null) {
                val fill = Color(custom)
                TimelineInk(
                    fill,
                    contrastContentColor(fill),
                    Color(readableForeground(custom, scheme.background.toArgb().toLong())),
                )
            } else
                when (colorTheme) {
                    TimelineTheme.APP_THEME -> {
                        val group=when(type) {
                            LineType.BUS,LineType.SUBURBAN,LineType.REGIONAL->0
                            LineType.TRAM,LineType.SUBWAY,LineType.ICE->1
                            else->2
                        }
                        val accent=when(group) {0->scheme.primary;1->scheme.secondary;else->scheme.tertiary}
                        languageTimelineInk(appTheme,scheme,group,accent,dark) ?: run {
                            val container=androidx.compose.ui.graphics.lerp(scheme.surfaceContainerLow,accent,if(dark).28f else .16f)
                                .compositeOver(scheme.background.copy(alpha=1f))
                            TimelineInk(container,contrastingContent(container),accent)
                        }
                    }
                    TimelineTheme.MONET ->
                        when (type) {
                            LineType.SUBURBAN,
                            LineType.REGIONAL,
                            LineType.WALK ->
                                TimelineInk(
                                    systemScheme.primaryContainer,
                                    systemScheme.onPrimaryContainer,
                                    systemScheme.primary,
                                )
                            LineType.SUBWAY,
                            LineType.ICE,
                            LineType.OTHER ->
                                TimelineInk(
                                    systemScheme.secondaryContainer,
                                    systemScheme.onSecondaryContainer,
                                    systemScheme.secondary,
                                )
                            else ->
                                TimelineInk(
                                    systemScheme.tertiaryContainer,
                                    systemScheme.onTertiaryContainer,
                                    systemScheme.tertiary,
                                )
                        }
                    TimelineTheme.MONOCHROME ->
                        TimelineInk(
                            listOf(Color.White, Color(0xFFD5D5D5), Color(0xFFAAAAAA))[
                                type.ordinal % 3],
                            Color.Black,
                            if (dark) Color.White else Color.Black,
                        )
                    TimelineTheme.ORIGINAL -> {
                        val fill =
                            if (type == LineType.REGIONAL) Color(0xFF616774) else Color(type.argb)
                        TimelineInk(
                            fill,
                            contrastingContent(fill),
                            if (type == LineType.BUS && !dark) Color(0xFF8A6400) else fill,
                        )
                    }
                    else -> {
                        // Each transit family retains a distinct tone while staying in one hue
                        // family.
                        val fill = Color(tones[type.ordinal])
                        TimelineInk(fill, contrastingContent(fill))
                    }
                }
        }
    )
}

@Composable
fun timelineColorPalette(theme: TimelineTheme = LocalTimelineTheme.current,
    seed:Long?=LocalTimelineSeed.current,overrides:Map<LineType,Long> = LocalTimelineLineColors.current): TimelineColorPalette {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dark = scheme.background.luminance() < .3f
    val system =
        remember(context, dark, scheme) {
            if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else scheme
        }
    val appTheme = LocalAppVisuals.current.theme
    return remember(theme, scheme, system, seed, overrides, appTheme) {
        createTimelinePalette(theme, scheme, system, seed, overrides, appTheme)
    }
}

/**
 * Ride colours of the design languages. Poster, clay and colour-block bars are solid fills
 * (rail = their contour ink), outline bars stay open, tonal bars step through one hue. Content is
 * always black or white with the better contrast; null keeps the classic App-Theme formula.
 */
internal fun languageTimelineInk(appTheme:AppTheme?,scheme:ColorScheme,group:Int,accent:Color,dark:Boolean):TimelineInk? {
    val ground=scheme.background.copy(alpha=1f)
    fun solid(c:Color)=c.compositeOver(ground)
    return when(appTheme.themeLanguageOrStandard) {
        ThemeLanguage.NEO_BRUTAL,ThemeLanguage.COLOR_BLOCK,ThemeLanguage.CLAY->{
            val fill=solid(when(group) {0->scheme.primaryContainer;1->scheme.secondaryContainer;else->scheme.tertiaryContainer})
            TimelineInk(fill,contrastingContent(fill),if(appTheme.themeLanguageOrStandard==ThemeLanguage.NEO_BRUTAL) scheme.outline else accent)
        }
        ThemeLanguage.OUTLINE->TimelineInk(ground,contrastingContent(ground),accent)
        ThemeLanguage.TONAL->{
            val fill=solid(androidx.compose.ui.graphics.lerp(ground,scheme.primary,(if(dark) .24f else .18f)+group*.13f))
            TimelineInk(fill,contrastingContent(fill),scheme.primary)
        }
        else->null
    }
}
