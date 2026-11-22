// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.color.utilities.MathUtils
import dev.ambivalenton.openv.data.model.*

/** Same HCT roles as Material Views, using the color utilities already bundled by Öpen-V.
 * Keeping this small calculation here avoids packaging a second, unused view component library. */
internal data class LocalColorRoles(val accent:Int,val onAccent:Int,val accentContainer:Int,val onAccentContainer:Int)
internal fun localColorRoles(color:Int,isLight:Boolean):LocalColorRoles {
    fun tone(value:Double)=Hct.fromInt(color).apply { setTone(value) }.toInt()
    return if(isLight)LocalColorRoles(tone(40.0),tone(100.0),tone(90.0),tone(10.0))
        else LocalColorRoles(tone(80.0),tone(20.0),tone(30.0),tone(90.0))
}
internal fun harmonizeColor(design:Int,source:Int):Int {
    val from=Hct.fromInt(design);val to=Hct.fromInt(source)
    val rotation=minOf(MathUtils.differenceDegrees(from.hue,to.hue)*.5,15.0)
    return Hct.from(MathUtils.sanitizeDegreesDouble(from.hue+rotation*MathUtils.rotationDirection(from.hue,to.hue)),from.chroma,from.tone).toInt()
}

val accentPresets = favoriteColorPresets.map { it.seed }

fun isDarkTheme(mode: ThemeMode, systemDark: Boolean) =
    when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK,
        ThemeMode.AMOLED -> true
    }

private val paletteCache =
    object : LinkedHashMap<Long, ColorScheme>(24, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ColorScheme>?) =
            size > 24
    }

/** Public Material Color Roles API generates HCT-based, contrast-paired container/content tones. */
@Synchronized
fun seedColorScheme(seed: Long, dark: Boolean): ColorScheme {
    val argb = seed.toInt() or 0xFF000000.toInt()
    val key = (argb.toLong() and 0xffffffffL) or if (dark) 1L.shl(32) else 0L
    return paletteCache.getOrPut(key) {
        val primary = localColorRoles(argb, !dark)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hsl[0] = (hsl[0] + 60) % 360
        val tertiary = localColorRoles(ColorUtils.HSLToColor(hsl), !dark)
        val secondary =
            localColorRoles(harmonizeColor(0xFF607D8B.toInt(), argb), !dark)
        fun neutral(light: Int, night: Int, amount: Float = .025f) =
            Color(ColorUtils.blendARGB(if (dark) night else light, argb, amount))
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val readablePrimary =
            Color(
                readableForeground(
                    seed,
                    neutral(0xFFFAFAFAL.toInt(), 0xFF111114.toInt()).toArgb().toLong(),
                )
            )
        base.copy(
            primary = readablePrimary,
            onPrimary = contrastContentColor(readablePrimary),
            primaryContainer =
                Color(
                    toneColor(
                        seed,
                        (dev.ambivalenton.openv.color.utilities.Hct.fromInt(argb).tone +
                                if (dark) -20.0 else 35.0)
                            .coerceIn(10.0, 90.0),
                    )
                ),
            onPrimaryContainer =
                contrastContentColor(
                    Color(
                        toneColor(
                            seed,
                            (dev.ambivalenton.openv.color.utilities.Hct.fromInt(argb).tone +
                                    if (dark) -20.0 else 35.0)
                                .coerceIn(10.0, 90.0),
                        )
                    )
                ),
            inversePrimary = Color(localColorRoles(argb, dark).accent),
            secondary = Color(secondary.accent),
            onSecondary = Color(secondary.onAccent),
            secondaryContainer = Color(secondary.accentContainer),
            onSecondaryContainer = Color(secondary.onAccentContainer),
            tertiary = Color(tertiary.accent),
            onTertiary = Color(tertiary.onAccent),
            tertiaryContainer = Color(tertiary.accentContainer),
            onTertiaryContainer = Color(tertiary.onAccentContainer),
            background = neutral(0xFFFAFAFAL.toInt(), 0xFF111114.toInt()),
            onBackground = if (dark) Color(0xFFE7E7EB) else Color(0xFF19191D),
            surface = neutral(0xFFFAFAFAL.toInt(), 0xFF111114.toInt()),
            onSurface = if (dark) Color(0xFFE7E7EB) else Color(0xFF19191D),
            surfaceContainerLowest = neutral(0xFFFFFFFF.toInt(), 0xFF0B0B0E.toInt()),
            surfaceContainerLow = neutral(0xFFF5F5F7.toInt(), 0xFF19191C.toInt()),
            surfaceContainer = neutral(0xFFF0F0F3.toInt(), 0xFF202023.toInt()),
            surfaceContainerHigh = neutral(0xFFEAEAEF.toInt(), 0xFF29292D.toInt()),
            surfaceContainerHighest = neutral(0xFFE3E3E9.toInt(), 0xFF343438.toInt()),
            surfaceDim = neutral(0xFFDDDEE2.toInt(), 0xFF111114.toInt()),
            surfaceBright = neutral(0xFFFAFAFAL.toInt(), 0xFF3C3C40.toInt()),
            surfaceVariant = neutral(0xFFE1E2E8.toInt(), 0xFF44454C.toInt()),
            onSurfaceVariant = if (dark) Color(0xFFC7C8D0) else Color(0xFF45464C),
            outline = if (dark) Color(0xFF91929A) else Color(0xFF74757D),
            outlineVariant = if (dark) Color(0xFF44454C) else Color(0xFFC4C5CD),
            surfaceTint = Color(primary.accent),
        )
    }
}

fun resolveColorScheme(context: Context, settings: AppSettings, systemDark: Boolean): ColorScheme {
    settings.appTheme?.let { return appThemeScheme(it,settings.appThemeColors[it] ?: it.defaultColors()) }
    val dark = isDarkTheme(settings.themeMode, systemDark)
    val scheme =
        settings.accentSeed?.let { seedColorScheme(it, dark) }
            ?: if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else seedColorScheme(accentPresets.first(), dark)
    return if (settings.themeMode == ThemeMode.AMOLED)
        scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF08080A),
            surfaceContainer = Color(0xFF101012),
            surfaceContainerHigh = Color(0xFF19191C),
            surfaceContainerHighest = Color(0xFF242428),
            surfaceBright = Color(0xFF19191C),
            surfaceTint = Color.Transparent,
        )
    else scheme
}

/** The colours a running design blend is heading for (equal to the shown ones at rest).
 * Colours derived from the scheme by thresholds (the capsules of the navigation) are computed from
 * this target and blended with [themeBlendSpec], so they move with the rest instead of jumping. */
val LocalThemeTargetScheme=staticCompositionLocalOf<ColorScheme?> {null}

/** The one blend of a design change, shared by the scheme and colours derived from it. */
fun themeBlendSpec(enabled:Boolean)=androidx.compose.animation.core.tween<Color>(if(enabled) 460 else 0,
    easing=androidx.compose.animation.core.FastOutSlowInEasing)

@OptIn(ExperimentalMaterial3ExpressiveApi::class,ExperimentalMaterial3Api::class)
@Composable
fun OpenVTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    MonochromeWindowEffect(settings.monochrome)
    val visuals=AppVisuals(settings.appTheme,settings.readability,appFontFamily(settings.fontId),
        themeFontFamily(settings.appTheme?.headFace),themeFontFamily(settings.appTheme?.bodyFace))
    val baseScheme=resolveColorScheme(LocalContext.current,settings,isSystemInDarkTheme())
    val targetScheme=if(settings.readability.contrast==AppContrast.HIGH) highContrastScheme(baseScheme) else baseScheme
    // A change of design (e.g. "Tag & Nacht") blends the colours instead of jumping.
    val scheme=animatedColorScheme(targetScheme,!settings.reduceMotion)
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = if(settings.reduceMotion) dev.ambivalenton.openv.ui.components.QuietMaterialMotion else MotionScheme.expressive(),
        shapes =
            Shapes(
                small = visuals.corner(12.dp),
                medium = visuals.corner(20.dp),
                large = visuals.corner(28.dp),
                extraLarge = visuals.corner(32.dp),
            ),
        typography = appTypography(visuals),
        content = {
            CompositionLocalProvider(
                LocalThemeTargetScheme provides targetScheme,
                LocalAppSettings provides settings,
                LocalRippleConfiguration provides if(settings.reduceMotion) null else LocalRippleConfiguration.current,
                androidx.compose.material3.LocalContentColor provides scheme.onBackground,
                LocalAppVisuals provides visuals,
                LocalTimelineTheme provides settings.timelineTheme,
                LocalTimelineLabelControl provides normalizedTimelineLabelControl(settings.timelineLabelControl),
                LocalTimelineSeed provides settings.timelinePalette().seed,
                LocalTimelineLineColors provides settings.timelinePalette().lineColors,
                content = content,
            )
        },
    )
}

/**
 * Every colour role follows its target with a short ease, so a new design blends in. The
 * same number of animations runs in every composition; without motion the target applies at once.
 */
@Composable private fun animatedColorScheme(target:androidx.compose.material3.ColorScheme,enabled:Boolean):androidx.compose.material3.ColorScheme {
    val spec=themeBlendSpec(enabled)
    @Composable fun blend(color:androidx.compose.ui.graphics.Color)=androidx.compose.animation.animateColorAsState(color,spec,label="scheme").value
    return target.copy(
        primary=blend(target.primary),
        onPrimary=blend(target.onPrimary),
        primaryContainer=blend(target.primaryContainer),
        onPrimaryContainer=blend(target.onPrimaryContainer),
        inversePrimary=blend(target.inversePrimary),
        secondary=blend(target.secondary),
        onSecondary=blend(target.onSecondary),
        secondaryContainer=blend(target.secondaryContainer),
        onSecondaryContainer=blend(target.onSecondaryContainer),
        tertiary=blend(target.tertiary),
        onTertiary=blend(target.onTertiary),
        tertiaryContainer=blend(target.tertiaryContainer),
        onTertiaryContainer=blend(target.onTertiaryContainer),
        background=blend(target.background),
        onBackground=blend(target.onBackground),
        surface=blend(target.surface),
        onSurface=blend(target.onSurface),
        surfaceVariant=blend(target.surfaceVariant),
        onSurfaceVariant=blend(target.onSurfaceVariant),
        surfaceTint=blend(target.surfaceTint),
        inverseSurface=blend(target.inverseSurface),
        inverseOnSurface=blend(target.inverseOnSurface),
        error=blend(target.error),
        onError=blend(target.onError),
        errorContainer=blend(target.errorContainer),
        onErrorContainer=blend(target.onErrorContainer),
        outline=blend(target.outline),
        outlineVariant=blend(target.outlineVariant),
        scrim=blend(target.scrim),
        surfaceBright=blend(target.surfaceBright),
        surfaceContainer=blend(target.surfaceContainer),
        surfaceContainerHigh=blend(target.surfaceContainerHigh),
        surfaceContainerHighest=blend(target.surfaceContainerHighest),
        surfaceContainerLow=blend(target.surfaceContainerLow),
        surfaceContainerLowest=blend(target.surfaceContainerLowest),
        surfaceDim=blend(target.surfaceDim),
    )
}
