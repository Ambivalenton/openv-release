// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.JourneyWindow
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.ResultsScreen
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Build 114 · the bar chart: duration badges by their place among the connections in view, the now
 * line (shown or not, clock and stroke by design) and its subpage of settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build114ChartTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // ---- duration badges ----

    @Test fun shorterConnectionsGrowLongerOnesShrinkAndTheExtremesAreMarked() {
        val minutes = mapOf("a" to 30L, "b" to 33L, "c" to 45L, "d" to 70L)
        val looks = durationLooks(minutes, listOf("a", "b", "c", "d"))
        val scales = listOf("a", "b", "c", "d").map { looks.getValue(it).scale }
        assertEquals("ordered by duration", scales.sortedDescending(), scales)
        assertEquals(DurationMark.SHORTEST, looks.getValue("a").mark)
        assertEquals(DurationMark.LONGEST, looks.getValue("d").mark)
        assertEquals(DurationMark.NONE, looks.getValue("b").mark)
        // Shorter lean to the accent (negative), longer to grey (positive).
        assertTrue(looks.getValue("a").tint < 0f && looks.getValue("d").tint > 0f)
        // Never more than the range, whatever the deviation.
        looks.values.forEach { assertTrue(it.scale in (1f - DURATION_SCALE_RANGE)..(1f + DURATION_SCALE_RANGE)) }
    }

    @Test fun smallDifferencesStaySubtleLargeOnesShow() {
        val close = durationLooks(mapOf("a" to 40L, "b" to 44L), listOf("a", "b"))
        assertTrue("a few minutes: under 4 %", kotlin.math.abs(close.getValue("a").scale - close.getValue("b").scale) < .04f)
        val far = durationLooks(mapOf("a" to 20L, "b" to 80L), listOf("a", "b"))
        assertTrue("four times as long: over 15 %", far.getValue("a").scale - far.getValue("b").scale > .15f)
    }

    @Test fun marksNeedTwoDifferentConnectionsInViewAndNeverGoOffScreen() {
        assertEquals(DurationMark.NONE, durationLooks(mapOf("a" to 30L), listOf("a")).getValue("a").mark)
        val equal = durationLooks(mapOf("a" to 30L, "b" to 30L), listOf("a", "b"))
        assertTrue(equal.values.all { it.mark == DurationMark.NONE && it.scale == 1f })
        // "c" is shorter but not in view: the shortest in view is "a".
        val partly = durationLooks(mapOf("a" to 30L, "b" to 50L, "c" to 10L), listOf("a", "b"))
        assertEquals(DurationMark.SHORTEST, partly.getValue("a").mark)
        assertEquals(DurationMark.NONE, partly.getValue("c").mark)
        // Ties share the mark.
        val ties = durationLooks(mapOf("a" to 30L, "b" to 30L, "c" to 50L), listOf("a", "b", "c"))
        assertEquals(listOf(DurationMark.SHORTEST, DurationMark.SHORTEST), listOf(ties.getValue("a").mark, ties.getValue("b").mark))
    }

    // ---- now line ----

    @Test fun theNowLineFollowsTheDesignUnlessChosen() {
        assertEquals(ResolvedNowLine(NowClockStyle.DIGITAL, NowStroke.DASHED), NowLinePreferences().resolve(TimelineBarStyle.CLASSIC, null))
        assertEquals(NowStroke.RAIL, nowLineFor(TimelineBarStyle.METRO_RAILS, null).stroke)
        assertEquals(NowClockStyle.ANALOG, nowLineFor(TimelineBarStyle.APP_THEME, DesignLanguage.SWISS).clock)
        assertEquals(NowStroke.PIXEL, nowLineFor(TimelineBarStyle.APP_THEME, DesignLanguage.PIXEL).stroke)
        assertEquals(NowStroke.GLOW, nowLineFor(TimelineBarStyle.NEON, null).stroke)
        assertEquals(NowStroke.STITCH, nowLineFor(TimelineBarStyle.APP_THEME, DesignLanguage.STITCH).stroke)
        // Every design resolves to a concrete line and clock.
        TimelineBarStyle.entries.forEach { s -> DesignLanguage.entries.forEach { l ->
            val r = nowLineFor(s, l); assertNotEquals(NowStroke.AUTO, r.stroke); assertNotEquals(NowClockStyle.AUTO, r.clock) } }
        val chosen = NowLinePreferences(clock = NowClockStyle.BOTH, stroke = NowStroke.WAVE).resolve(TimelineBarStyle.NEON, null)
        assertEquals(ResolvedNowLine(NowClockStyle.BOTH, NowStroke.WAVE), chosen)
    }

    // ---- in the chart ----

    private val now = System.currentTimeMillis() / 60_000 * 60_000
    private val a = Location("Bochum Hbf", "a", 51.47, 7.22, "VRR"); private val m = Location("Essen Hbf", "m", 51.45, 7.01, "VRR")
    private val b = Location("Dortmund Hbf", "b", 51.51, 7.45, "VRR")
    private fun seg(id: String, from: Location, to: Location, s: Long, e: Long, t: LineType, n: String) = TripSegment(id, from, to, s, e, t, n)
    private val trips = (0 until 8).map { i ->
        val t0 = now - 20 * 60_000 + i * 11 * 60_000L
        val ride1 = listOf(8, 12, 6, 15, 9, 20)[i % 6] * 60_000L; val ride2 = listOf(22, 18, 35, 25, 40, 16)[i % 6] * 60_000L
        Trip("t$i", listOf(seg("w$i", a, a, t0, t0 + 4 * 60_000, LineType.WALK, "Fußweg"),
            seg("u$i", a, m, t0 + 4 * 60_000, t0 + 4 * 60_000 + ride1, LineType.SUBWAY, "U49"),
            seg("r$i", m, b, t0 + 9 * 60_000 + ride1, t0 + 9 * 60_000 + ride1 + ride2, LineType.REGIONAL, "RE3"),
            seg("e$i", b, b, t0 + 9 * 60_000 + ride1 + ride2, t0 + 15 * 60_000 + ride1 + ride2, LineType.WALK, "Fußweg")), "VRR", now)
    }

    private fun results(settings: AppSettings) {
        val ui = AppUiState(ready = true, store = StoredState(settings = settings), query = JourneyQuery(a, b, now), trips = LoadState(trips),
            journeyWindow = JourneyWindow(trips, now, now + 3 * 3_600_000))
        compose.setContent { OpenVTheme(settings) { CompositionLocalProvider(LocalNavigationClearance provides 112.dp, LocalScreenTitleGap provides 14.dp) {
            ResultsScreen(ui, {}, {}, {}, {}, {}, {}, {})
        } } }
        compose.waitForIdle()
    }

    private fun description(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsProperties.ContentDescription].joinToString()

    @Test fun theBadgesNameTheShortestAndTheLongestInView() {
        results(AppSettings(quietMode = true))
        val shown = trips.filter { compose.onAllNodesWithTag("duration-${it.id}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(shown.size >= 2)
        val all = shown.map { description("duration-${it.id}") }
        assertEquals(all.toString(), 1, all.count { "kürzeste im Blick" in it }.coerceAtMost(1))
        assertTrue(all.toString(), all.any { "längste im Blick" in it })
        save("chart-badges")
    }

    @Test fun theNowLineCanBeHiddenAndShowsTheChosenClock() {
        var settings by mutableStateOf(AppSettings(quietMode = true, nowLine = NowLinePreferences(clock = NowClockStyle.ANALOG, stroke = NowStroke.RAIL)))
        val ui = AppUiState(ready = true, store = StoredState(settings = settings), query = JourneyQuery(a, b, now), trips = LoadState(trips),
            journeyWindow = JourneyWindow(trips, now, now + 3 * 3_600_000))
        compose.setContent { OpenVTheme(settings) { CompositionLocalProvider(LocalNavigationClearance provides 112.dp, LocalScreenTitleGap provides 14.dp) {
            ResultsScreen(ui.copy(store = StoredState(settings = settings)), {}, {}, {}, {}, {}, {}, {})
        } } }
        compose.waitForIdle()
        compose.onAllNodesWithTag("timeline-now-analog", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag("timeline-now-clock", useUnmergedTree = true).assertCountEquals(0)
        save("chart-now-analog-rail")
        compose.runOnIdle { settings = settings.copy(nowLine = settings.nowLine.copy(visible = false)) }
        compose.waitForIdle()
        compose.onAllNodesWithTag("timeline-now-line", useUnmergedTree = true).assertCountEquals(0)
    }

    // ---- the subpage ----

    @Test fun theDisplaySettingsAreASubpageWithThreeFrames() {
        var settings by mutableStateOf(AppSettings(quietMode = true))
        val overlay = AppOverlayState()
        compose.setContent {
            OpenVTheme(settings) {
                CompositionLocalProvider(LocalAppOverlay provides overlay) {
                    AppOverlayHost(overlay, Modifier.fillMaxSize()) {
                        Surface(Modifier.fillMaxSize()) {}
                        TimelineDesignPicker(settings, {}, {}) { change -> settings = change(settings) }
                    }
                }
            }
        }
        compose.waitForIdle(); save("picker-entry")
        compose.onNodeWithTag("timeline-display-options").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("timeline-display-sheet").assertExists()
        compose.onNodeWithTag("now-line-controls").assertExists()
        save("subpage-now-line")
        // Hide the line with the frame's switch, then show it again with an analog clock.
        compose.onNodeWithTag("now-line-switch").performClick()
        compose.runOnIdle { assertFalse(settings.nowLine.visible) }
        compose.onNodeWithTag("now-line-switch").performClick()
        compose.onNodeWithTag("now-line-clock-ANALOG").performScrollTo().performClick()
        compose.onNodeWithTag("now-line-stroke-WAVE").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(NowLinePreferences(clock = NowClockStyle.ANALOG, stroke = NowStroke.WAVE), settings.nowLine) }
        // Text in the bars: the badge tile, then off and on again (back to the badge).
        compose.onNodeWithTag("timeline-label-detent-2").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(TIMELINE_LABEL_BADGE, settings.timelineLabelControl, .001f) }
        compose.onNodeWithTag("timeline-label-switch").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0f, settings.timelineLabelControl, .001f) }
        compose.onNodeWithTag("timeline-label-switch").performClick()
        compose.runOnIdle { assertEquals(TIMELINE_LABEL_BADGE, settings.timelineLabelControl, .001f) }
        compose.onNodeWithTag("timeline-label-detent-0").performClick()
        compose.onNodeWithTag("timeline-label-slider").assertExists()
        save("subpage-label")
    }

    // ---- the plan's edit bar ----

    /** Opening the bar uncovers the gesture band; its words keep one size in every frame (before, they jittered). */
    @Test fun theGestureBandKeepsItsSizeWhileTheBarOpens() {
        var editing by mutableStateOf(false)
        compose.setContent { OpenVTheme(AppSettings()) { Surface(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                dev.ambivalenton.openv.ui.network.NetworkEditBar(editing, { editing = true }, {}, {}, { editing = false }, Modifier.fillMaxSize())
            }
        } } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { editing = true }
        val sizes = mutableSetOf<Float>(); val widths = mutableMapOf<String, MutableSet<Int>>()
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes().filter { node -> node.config.getOrElseNullable(SemanticsProperties.Text) { null }
                    ?.joinToString()?.let { it == "Halten und schieben" || it == "Ziehen" || it == "Tippen" } == true }
                .forEach { node ->
                    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                    results.firstOrNull()?.let {
                        sizes += it.layoutInput.style.fontSize.value
                        widths.getOrPut(it.layoutInput.text.text) { mutableSetOf() } += it.layoutInput.constraints.maxWidth
                    }
                }
        }
        compose.mainClock.autoAdvance = true
        assertTrue("words measured while opening", sizes.isNotEmpty())
        assertEquals("one size per frame for all three words $sizes", 1, sizes.size)
        assertTrue("one width per cell (laid out at the open width only) $widths", widths.values.all { it.size == 1 })
    }

    private fun save(name: String) = compose.runOnIdle {
        val v = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val file = File("../audit/build114/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
