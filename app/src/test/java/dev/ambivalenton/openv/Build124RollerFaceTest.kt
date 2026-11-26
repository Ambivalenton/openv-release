// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Build 124 · screenshot at a large system font: the day roller's two lines ran into their neighbours and its
 * lens stood taller than the others; "Jetzt abfahren" had lost the clock with the current time and pushed
 * "Abfahrt | Ankunft" together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build124RollerFaceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.of("Europe/Berlin")

    @Test fun theDayFaceIsOneLine() {
        val today = LocalDate.of(2026, 11, 28) // a Saturday
        assertEquals(RollerLabel("28.11.", "Heute"), dayRollerLabel(0, today, "Heute", "Morgen"))
        assertEquals(RollerLabel("29.11.", "Morgen", sunday = true), dayRollerLabel(1, today, "Heute", "Morgen"))
        assertEquals(RollerLabel("01.12.", "Di."), dayRollerLabel(3, today, "Heute", "Morgen"))
        assertFalse(dayRollerLabel(3, today, "Heute", "Morgen").text.contains('\n'))
    }

    @Test fun abfahrtAnkunftComesFirst() {
        // Room enough: the area takes what it would like.
        assertEquals(150, nowAreaWidth(400, 200, 150, 8))
        // The pill at its natural size leaves less: the area takes that.
        assertEquals(152, nowAreaWidth(368, 208, 169, 8))
        // Even less: the area keeps its minimum (30 %), the pill makes room.
        assertEquals(90, nowAreaWidth(300, 260, 120, 8))
    }

    @Test fun atALargeFontThePillKeepsItsSizeAndTheClockShowsNow() {
        var time by mutableStateOf<Long?>(null)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.3f)) {
                OpenVTheme(AppSettings(appTheme = AppTheme.SYNTHWAVE)) { Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal = 21.dp)) {
                        Box(Modifier.width(368.dp).testTag("narrow")) { ScheduleControls(time, {}, TimeMode.DEPARTURE, {}, {}) }
                        Box(Modifier.width(600.dp).testTag("wide")) { ScheduleControls(null, {}, TimeMode.DEPARTURE, {}, {}) }
                    }
                } }
            }
        }
        compose.waitForIdle()
        fun pill(box: String) = compose.onAllNodes(hasTestTag("time-mode-pill") and hasAnyAncestor(hasTestTag(box)), useUnmergedTree = true)
            .fetchSemanticsNodes().single().size.width
        compose.runOnIdle { time = System.currentTimeMillis() + 26 * 3_600_000L }
        compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
        compose.onNodeWithTag("search-now").assertIsDisplayed()
        assertEquals("open: \"Abfahrt | Ankunft\" keeps its natural size", pill("wide"), pill("narrow"))
        // The clock in "Jetzt abfahren" shows the current time again.
        val now = java.time.ZonedDateTime.now(transitZone)
        val clocks = compose.onAllNodes(hasTestTagPrefix("analog-clock-") and hasAnyAncestor(hasTestTag("search-now")), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(1, clocks.size)
        val tag = clocks.single().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]
        assertTrue(tag, tag == "analog-clock-${now.hour}-${now.minute}" || tag == "analog-clock-${now.minusMinutes(1).hour}-${now.minusMinutes(1).minute}")
        save("large-font-schedule")
    }

    @Test fun theRollersAtNormalAndLargeFonts() {
        var scale by mutableFloatStateOf(1f)
        var theme by mutableStateOf<AppTheme?>(null)
        val time = LocalDate.now(zone).plusDays(3).atTime(6, 14).atZone(zone).toInstant().toEpochMilli()
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                OpenVTheme(AppSettings(appTheme = theme)) { Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp)) {
                        ScheduleControls(time, {}, TimeMode.DEPARTURE, {}, {})
                        Spacer(Modifier.height(24.dp))
                        ScheduleControls(LocalDate.now(zone).plusDays(1).atTime(23, 59).atZone(zone).toInstant().toEpochMilli(), {}, TimeMode.ARRIVAL, {}, {})
                    }
                } }
            }
        }
        for ((s, t, name) in listOf(Triple(1f, null, "modern-1.0"), Triple(1.3f, AppTheme.SYNTHWAVE, "synthwave-1.3"), Triple(2f, null, "modern-2.0"),
            Triple(1.3f, AppTheme.VINTAGE_TRANSIT, "mechanical-1.3"), Triple(1.3f, AppTheme.SPACE_TERMINAL, "digital-1.3"), Triple(1f, AppTheme.CLASSIC_DARK, "classic-dark-1.0"))) {
            compose.runOnIdle { scale = s; theme = t }
            compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
            compose.onAllNodesWithTag("roller-day").assertCountEquals(2)
            save("rollers-$name")
        }
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
    }

    private fun save(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        val file = File("../audit/build124/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
