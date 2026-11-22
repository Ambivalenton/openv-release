// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.toArgb
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

/** The real chart draws the time chips flush on the bar (build 74: the footprints cross them). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build73ChartClockUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val home = Location("Wohnung", "home", 51.51, 7.46)
    private val dortmund = Location("Dortmund Hbf", "d", 51.517, 7.459, "VRR")
    private val herne = Location("Herne Bf", "h", 51.54, 7.22, "VRR")
    private val t = 1_800_000_000_000L
    private val minute = 60_000L

    /** Like the reported screenshot: a short walk to the platform, a ride, a short walk away. */
    private fun connection(i: Int, walk: Int): Trip {
        val start = t + i * 15 * minute
        val ride = start + walk * minute
        val line = if (i % 2 == 0) "RE3" else "S2"
        val type = if (i % 2 == 0) LineType.REGIONAL else LineType.SUBURBAN
        return Trip("c$i", listOf(
            TripSegment("w$i", home, dortmund, start, ride, LineType.WALK, "Fußweg", distanceMeters = walk * 80),
            TripSegment("r$i", dortmund, herne, ride, ride + 16 * minute, type, line),
            TripSegment("e$i", herne, home, ride + 16 * minute, ride + (16 + walk) * minute, LineType.WALK, "Fußweg", distanceMeters = walk * 80),
        ), "TEST", start)
    }

    private fun pixels(): Bitmap {
        val view = compose.activity.window.decorView
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
    private fun capture(name: String) = compose.runOnIdle {
        val bitmap = pixels(); val file = File("../audit/build73/images/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private var primary = androidx.compose.ui.graphics.Color.Unspecified
    private fun show(trips: List<Trip>, settings: AppSettings = AppSettings(), dark: Boolean = false) {
        compose.setContent {
            OpenVTheme(settings.copy(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
                primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
                // The chart fits its height; this one keeps the 16-minute ride a short bar with chips.
                CompositionLocalProvider(LocalFootprintSeed provides 73L) { Surface { OeffiTimelineGraph(trips, {}, minimumHeight = 200.dp) } }
            }
        }
    }

    @Test fun departureAndArrivalChipsSitFlushOnTheBar() {
        show((0..3).map { connection(it, if (it == 1) 9 else 2) })
        compose.mainClock.advanceTimeBy(64)
        val bar = bounds("timeline-segment-c0-1")
        val departure = bounds("timeline-ride-start-1-c0")
        val arrival = bounds("timeline-ride-end-1-c0")
        assertEquals("departure label box ends on the bar top", bar.top, departure.bottom, 1.5f)
        assertEquals("arrival label box starts on the bar bottom", bar.bottom, arrival.top, 1.5f)
        capture("chart-clock-chips-light")
    }

    /** Build 74 follow-up: the steps cross the chip up to the bar, in the accent colour while they are
     * on the chip; covered digits are repainted in the contrast colour, never in the accent. */
    @Test fun footprintsCrossTheChipInTheAccentColourAndKeepItsDigitsReadable() {
        compose.mainClock.autoAdvance = false
        show(listOf(connection(0, 2), connection(1, 9)))
        compose.mainClock.advanceTimeBy(64)
        val chip = bounds("timeline-ride-start-1-c0")
        val accent = primary.toArgb()
        val baseline = compose.runOnIdle { pixels() }
        val digits = mutableListOf<Pair<Int, Int>>()
        for (y in (chip.top + chip.height * .3f).toInt() until (chip.bottom - chip.height * .2f).toInt())
            for (x in chip.left.toInt() until chip.right.toInt())
                if (android.graphics.Color.red(baseline.getPixel(x, y)) < 90) digits += x to y
        assertTrue("the chip's time must be rendered", digits.size > 40)
        val digitSet = digits.toSet()
        fun near(a: Int, b: Int) = kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) < 40 &&
            kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) < 40 &&
            kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) < 40
        var accentOnChip = 0; var accentDigits = 0
        for (frame in 0..40) {
            compose.mainClock.advanceTimeBy(150)
            compose.runOnIdle {
                val bitmap = pixels()
                var count = 0; var onDigits = 0
                for (y in chip.top.toInt() until chip.bottom.toInt()) for (x in chip.left.toInt() until chip.right.toInt()) {
                    val px = bitmap.getPixel(x, y)
                    if (px != baseline.getPixel(x, y) && near(px, accent)) { if ((x to y) in digitSet) onDigits++ else count++ }
                }
                accentOnChip = maxOf(accentOnChip, count); accentDigits = maxOf(accentDigits, onDigits)
                bitmap.recycle()
            }
        }
        capture("chart-clock-chips-walking")
        assertTrue("steps cross the chip in the accent colour ($accentOnChip)", accentOnChip > 20)
        // Covered digits are repainted in the contrast colour; at most anti-aliased glyph edges show the accent.
        assertTrue("digits stay readable ($accentDigits of ${digits.size})", accentDigits <= digits.size * .05f)
        baseline.recycle()
        compose.mainClock.autoAdvance = true
    }

    @Test fun darkModeKeepsReadableChips() {
        compose.mainClock.autoAdvance = false
        show((0..3).map { connection(it, if (it % 2 == 0) 2 else 12) }, dark = true)
        compose.mainClock.advanceTimeBy(2_400)
        capture("chart-clock-chips-dark")
        compose.mainClock.autoAdvance = true
    }

    @Test fun quietModeKeepsTheChipsOnTheBar() {
        show((0..3).map { connection(it, 12) }, AppSettings(quietMode = true))
        compose.mainClock.advanceTimeBy(64)
        assertEquals(bounds("timeline-segment-c0-1").top, bounds("timeline-ride-start-1-c0").bottom, 1.5f)
        capture("chart-clock-chips-quiet")
    }
}
