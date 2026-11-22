// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * 2.8: appearance that lives only as long as this process: the theme drawn at a cold start and
 * the rainbow accents. Nothing here is written into the chosen or a personal theme.
 */
object SessionAppearance {
    /** The theme drawn at this cold start, together with the saved theme it was drawn over. */
    val random = MutableStateFlow<SessionTheme?>(null)
    /** Whether this process has already decided about a random theme (one draw per cold start). */
    @Volatile var decided = false
    /** Counts completed changes between Übersicht, Suchen and Abfahrten for the rainbow accents. */
    val rainbow = MutableStateFlow(0)
    fun clearRandom() { random.value = null }
}

data class SessionTheme(val base: AppTheme?, val drawn: AppTheme)

/**
 * 2.8: the drawn theme applies while the saved theme is still the one it was drawn over: choosing a
 * theme, the "Tag & Nacht" button or switching the option off shows the saved choice again.
 */
fun AppSettings.withSessionTheme(session: SessionTheme?): AppSettings =
    if (session != null && randomThemeOnStart && appTheme == session.base) copy(appTheme = session.drawn) else this

/**
 * 2.8: one draw from the available themes, never the one drawn last time (when there is a choice).
 * At night only dark themes take part, as in the theme picker.
 */
fun drawRandomTheme(settings: AppSettings, random: Random = Random.Default): AppTheme? {
    val pool = groupedAppThemes(settings.quietMode).flatMap { it.second }.distinct()
        .filter { !settings.dayNight.night || it.dark }
    val choices = pool.filter { it != settings.lastRandomTheme && it != settings.appTheme }.ifEmpty { pool }
    return choices.randomOrNull(random)
}

/** 2.8: hues of the rainbow accents (HCT): red, orange, yellow, green, teal, blue, violet. */
private val RAINBOW_HUES = listOf(25.0, 55.0, 95.0, 145.0, 185.0, 255.0, 305.0)

private fun hueDistance(a: Double, b: Double) = min(abs(a - b), 360 - abs(a - b))

/**
 * 2.8: temporary accents for the rainbow mode: clear accent roles over the theme's own calm
 * surfaces. A hue close to the background's own colour is skipped (no blue on blue), every text
 * colour is checked against its own fill (7:1 with high text contrast), and error colours stay.
 */
fun rainbowScheme(base: ColorScheme, index: Int, highContrast: Boolean): ColorScheme {
    val dark = base.background.luminance() < .4f
    val background = Hct.fromInt(base.background.toArgb())
    val surfaceHue = background.hue.takeIf { background.chroma > 10 }
    val hues = RAINBOW_HUES.filter { h -> surfaceHue == null || hueDistance(h, surfaceHue) > 40 }.ifEmpty { RAINBOW_HUES }
    val minimum = if (highContrast) 7.0 else 4.5
    fun tone(hue: Double, tone: Double, chroma: Double) = Color(Hct.from(hue, chroma, tone).toInt())
    fun ink(on: Color, fill: Color) = Color(readableForeground(on.toArgb().toLong() and 0xffffffffL, fill.toArgb().toLong() and 0xffffffffL, minimum).toInt())
    fun role(hue: Double): Pair<Pair<Color, Color>, Pair<Color, Color>> {
        val accent = tone(hue, if (dark) 80.0 else 40.0, 56.0)
        val container = tone(hue, if (dark) 30.0 else 90.0, 32.0)
        return (accent to ink(if (dark) Color.Black else Color.White, accent)) to (container to ink(tone(hue, if (dark) 92.0 else 12.0, 20.0), container))
    }
    val first = hues[Math.floorMod(index, hues.size)]
    val second = hues[Math.floorMod(index + 2, hues.size)]
    val third = hues[Math.floorMod(index + 4, hues.size)]
    val (p, pc) = role(first); val (s, sc) = role(second); val (t, tc) = role(third)
    // Accent text on the surfaces stays readable as well.
    val primary = ink(p.first, base.surface)
    return base.copy(primary = primary, onPrimary = ink(p.second, primary), primaryContainer = pc.first, onPrimaryContainer = pc.second,
        secondary = ink(s.first, base.surface), onSecondary = s.second, secondaryContainer = sc.first, onSecondaryContainer = sc.second,
        tertiary = ink(t.first, base.surface), onTertiary = t.second, tertiaryContainer = tc.first, onTertiaryContainer = tc.second,
        inversePrimary = tone(first, if (dark) 40.0 else 80.0, 48.0), surfaceTint = primary)
}

/**
 * 2.8: wraps the app in the rainbow accents while the mode is on. Black-and-white mode keeps its
 * greys; line colours and status colours are not accent roles and stay as they are.
 */
@Composable fun RainbowAccents(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val index by SessionAppearance.rainbow.collectAsState()
    val base = MaterialTheme.colorScheme
    val high = LocalAppVisuals.current.readability.contrast == AppContrast.HIGH
    val scheme = remember(base, index, high) { rainbowScheme(base, index, high) }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}
