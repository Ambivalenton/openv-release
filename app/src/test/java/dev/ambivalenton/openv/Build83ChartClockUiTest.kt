// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.LocalFootprintSeed
import dev.ambivalenton.openv.ui.components.OeffiTimelineGraph
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Long bars carry their departure and arrival time inside, at the top and the bottom,
 * without a frame (contrast contour and light shadow instead); only short bars keep the chips.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build83ChartClockUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val home = Location("Wohnung", "home", 51.51, 7.46)
    private val dortmund = Location("Dortmund Hbf", "d", 51.517, 7.459, "VRR")
    private val herne = Location("Herne Bf", "h", 51.54, 7.22, "VRR")
    private val t = 1_800_000_000_000L
    private val minute = 60_000L

    /** Walk, ride of [ride] minutes, walk. */
    private fun connection(i: Int, ride: Int): Trip {
        val start = t + i * 10 * minute
        val board = start + 3 * minute
        val line = if (i % 2 == 0) "RE3" else "S2"
        val type = if (i % 2 == 0) LineType.REGIONAL else LineType.SUBURBAN
        return Trip("c$i", listOf(
            TripSegment("w$i", home, dortmund, start, board, LineType.WALK, "Fußweg", distanceMeters = 240),
            TripSegment("r$i", dortmund, herne, board, board + ride * minute, type, line),
            TripSegment("e$i", herne, home, board + ride * minute, board + (ride + 3) * minute, LineType.WALK, "Fußweg", distanceMeters = 240),
        ), "TEST", start)
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        val file = File("../audit/theme-catalog/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    private var settings by mutableStateOf(AppSettings(themeMode = ThemeMode.LIGHT))

    @Test fun longBarsShowTheirTimesInsideAndShortBarsKeepChips() {
        val trips = listOf(connection(0, 34), connection(1, 7), connection(2, 26), connection(3, 9))
        compose.setContent {
            OpenVTheme(settings) {
                // The chart fits its height; at this one the 34-minute ride is long and the 7-minute one short.
                CompositionLocalProvider(LocalFootprintSeed provides 83L) { Surface { OeffiTimelineGraph(trips, {}, minimumHeight = 280.dp) } }
            }
        }
        val variants = listOf(
            "system-light" to AppSettings(themeMode = ThemeMode.LIGHT),
            "system-dark" to AppSettings(themeMode = ThemeMode.DARK),
            "brutal-poster" to AppSettings(themeMode = ThemeMode.LIGHT, appTheme = AppTheme.BRUTAL_POSTER, timelineTheme = TimelineTheme.APP_THEME),
            "glow-violet" to AppSettings(themeMode = ThemeMode.DARK, appTheme = AppTheme.GLOW_VIOLET, timelineTheme = TimelineTheme.APP_THEME),
        )
        for ((name, next) in variants) {
            compose.runOnIdle { settings = next }
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
            // Long ride: both times lie inside the bar, the departure in its upper and the arrival in its lower half.
            val long = bounds("timeline-segment-c0-1")
            val start = bounds("timeline-ride-start-1-c0")
            val end = bounds("timeline-ride-end-1-c0")
            assertTrue("$name: departure inside ($start in $long)", start.top >= long.top - .5f && start.bottom <= long.center.y)
            assertTrue("$name: arrival inside ($end in $long)", end.bottom <= long.bottom + .5f && end.top >= long.center.y)
            // Short ride: the chips stay outside, flush on the bar edges.
            val short = bounds("timeline-segment-c1-1")
            assertEquals("$name: departure chip on the bar top", short.top, bounds("timeline-ride-start-1-c1").bottom, 1.5f)
            assertEquals("$name: arrival chip on the bar bottom", short.bottom, bounds("timeline-ride-end-1-c1").top, 1.5f)
            capture("chart-times-$name")
        }
    }
}
