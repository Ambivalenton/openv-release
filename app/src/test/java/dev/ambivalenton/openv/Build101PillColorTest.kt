// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.isOperatingSystem
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.appThemeScheme
import kotlin.math.abs
import kotlin.math.min
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Build 101 · the navigation capsule changes colour clearly from page to page (Übersicht → Suchen →
 * Abfahrten), with colours of the theme or its harmony – checked for every theme of the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Build101PillColorTest {
    private fun distance(a: Double, b: Double) = min(abs(a - b), 360 - abs(a - b))
    private fun hct(c: Color) = Hct.fromInt(c.toArgb())

    /** The three capsule colours as the navigation works them out for [theme]. */
    private fun capsuleColors(theme: AppTheme): List<Color> {
        val scheme = appThemeScheme(theme)
        val base = scheme.surfaceContainerHigh.compositeOver(scheme.background)
        return if (colorfulCapsuleTheme(theme)) capsuleHues(theme, scheme.primary, 3, listOf(scheme.secondary, scheme.tertiary))
        else vividCapsuleHues(scheme.primary, listOf(scheme.tertiary, scheme.secondary) + themeSwatches(theme), 3).also {
            assertEquals(3, destinationTones(3, base, theme.language, false, listOf(scheme.primary, scheme.tertiary, scheme.secondary),
                listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer), themeSwatches(theme)).size)
        }
    }

    @Test fun everyColourfulThemeChangesColourClearlyFromPageToPage() {
        val weak = mutableListOf<String>()
        var checked = 0
        for (theme in AppTheme.entries) {
            val scheme = appThemeScheme(theme)
            // Monochrome themes keep their grey steps.
            if (appColorFamily(theme) == DesignColorFamily.INK && !theme.isOperatingSystem) continue
            if (hct(scheme.primary).chroma < 10) continue
            checked++
            val hues = capsuleColors(theme).map { hct(it).hue }
            hues.zipWithNext().forEachIndexed { i, (a, b) ->
                if (distance(a, b) < CAPSULE_HUE_STEP - 1) weak += "${theme.name} ${i}→${i + 1}: ${a.toInt()}° → ${b.toInt()}°"
            }
            // Übersicht keeps the theme's accent.
            if (distance(hues[0], hct(scheme.primary).hue) > 1) weak += "${theme.name}: accent moved"
        }
        assertTrue("themes checked: $checked", checked > 40)
        assertTrue(weak.joinToString("\n"), weak.isEmpty())
    }

    @Test fun aRedThemeNoLongerGoesFromRedToRed() {
        // Roles that are the same colour (secondary = tertiary), as in most app themes.
        val red = Color(0xFFC62828)
        val tones = destinationTones(3, Color(0xFFF6F1EE), DesignLanguage.MATERIAL, false, listOf(red, red, red), listOf(red, red, red))
        val hues = tones.map { hct(it.fill).hue }
        assertTrue("Suchen → Abfahrten: ${hues[1]} → ${hues[2]}", distance(hues[1], hues[2]) >= 60.0)
        assertTrue("Übersicht → Suchen: ${hues[0]} → ${hues[1]}", distance(hues[0], hues[1]) >= 60.0)
    }

    @Test fun theTravellingCapsuleStaysColourfulHalfway() {
        val red = Color(0xFFD32F2F); val teal = Color(0xFF00897B)
        val middle = hueLerp(red, teal, .5f)
        val straight = lerp(red, teal, .5f)
        assertTrue("around the wheel ${hct(middle).chroma} vs straight ${hct(straight).chroma}", hct(middle).chroma > hct(straight).chroma + 10)
        assertEquals(red.toArgb(), hueLerp(red, teal, 0f).toArgb())
        assertEquals(teal.toArgb(), hueLerp(red, teal, 1f).toArgb())
        // Grey ends do not spin through unrelated colours.
        val grey = hueLerp(Color(0xFF808080), teal, .5f)
        assertTrue(distance(hct(grey).hue, hct(teal).hue) < 30)
    }
}
