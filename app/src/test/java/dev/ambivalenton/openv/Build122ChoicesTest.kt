// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.reliability.ReliabilityMemory
import dev.ambivalenton.openv.ui.OverviewUiState
import dev.ambivalenton.openv.ui.components.JourneyChoiceActions
import dev.ambivalenton.openv.ui.components.LocalJourneyChoiceActions
import dev.ambivalenton.openv.ui.components.LocalNavigationClearance
import dev.ambivalenton.openv.ui.components.PinnedJourneyCard
import dev.ambivalenton.openv.ui.screens.OverviewScreen
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
 * Build 122 · test report: three buses arriving at the same time looked alike before the choice. Every card now
 * names line and departure (also for TalkBack), the tightest change concretely and the level in words; with
 * large type the cards stand one below the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build122ChoicesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = System.currentTimeMillis() / 60_000 * 60_000
    private fun place(name: String, id: String, lat: Double, lon: Double) = Location(name, id, lat, lon, "VRR")
    private val start = place("Bochum Ruhrstadion", "s", 51.49, 7.23)
    private val hbf = place("Bochum Hbf", "h", 51.478, 7.223)
    private val herne = place("Herne Schloß Strünkede", "z", 51.55, 7.21)
    private val home = Favorite("home", "Zuhause", herne, icon = FavoriteIcon.HOME)
    private val min = 60_000L

    /** Riding the RE1 towards Bochum Hbf (arriving in 3 min), then the U35. */
    private fun session(): PinnedJourneySession {
        val re = TripSegment("re", start, hbf, now - 4 * min, now + 3 * min, LineType.REGIONAL, "RE1",
            arrivalPlatform = "3", departurePlatform = "2", direction = "Hamm")
        val u = TripSegment("u", hbf, herne, now + 12 * min, now + 38 * min, LineType.SUBWAY, "U35",
            departurePlatform = "3 (U)", direction = "Herne Schloß Strünkede")
        return PinnedJourneySession(favorite = home, query = JourneyQuery(start, herne, now), initialTrip = Trip("trip", listOf(re, u), "VRR", now),
            routing = AppSettings())
    }
    private fun choice(id: String, line: String, type: LineType, leave: Int, arrive: Int, changes: Int = 0): Trip {
        val first = TripSegment("$id-1", hbf, if (changes == 0) herne else start, now + leave * min, now + (if (changes == 0) arrive else leave + 8) * min, type, line)
        val rest = if (changes == 0) emptyList() else listOf(TripSegment("$id-2", start, herne, now + (leave + 12) * min, now + arrive * min, LineType.BUS, "SB20"))
        return Trip(id, listOf(first) + rest, "VRR", now)
    }
    private fun state(): PinnedJourneyState {
        val base = stabilizedAssistantState(PinnedJourneyState(), PinnedJourneyState(session(), PinnedTrackingStatus.RUNNING), System.currentTimeMillis())
        val s = base.session!!
        // Bus 310, 311 and 312 leave at different times and arrive together.
        return base.copy(choices = JourneyChoices(s.id, JourneyQuery(hbf, herne, now + 3 * min), listOf(
            choice("a", "310", LineType.BUS, 6, 40), choice("b", "311", LineType.BUS, 8, 40), choice("c", "312", LineType.BUS, 10, 40))))
    }

    private fun content(fontScale: Float) {
        val pinned = state()
        compose.setContent {
            val base = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale)) {
                OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalJourneyChoiceActions provides JourneyChoiceActions(ReliabilityMemory(), { _, _ -> }, { _, _ -> })) {
                        Column(Modifier.padding(16.dp)) { PinnedJourneyCard(pinned, {}, {}) }
                    }
                } }
            }
        }
        compose.waitForIdle()
    }

    @Test fun threeBusesArrivingTogetherAreToldApart() {
        content(1f)
        val spoken = compose.onAllNodesWithTag("journey-choice", useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString() }
        assertEquals(3, spoken.size)
        assertEquals("every card says something else", 3, spoken.distinct().size)
        assertTrue(spoken[0], spoken[0].startsWith("Bus 310, ab ") && spoken[0].contains("Verlässlichkeit geschätzt"))
        compose.onNodeWithText("Bus 311", useUnmergedTree = true).assertExists()
        save("choices-buses")
    }

    @Test fun withLargeTypeTheCardsStandBelowEachOther() {
        content(2f)
        val cards = compose.onAllNodesWithTag("journey-choice", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(3, cards.size)
        assertTrue("one per row", cards[1].top >= cards[0].bottom && cards[2].top >= cards[1].bottom)
        assertTrue("using the width", cards[0].width > 250f)
        save("choices-large-type")
    }

    private fun save(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        val file = File("../audit/build122/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
