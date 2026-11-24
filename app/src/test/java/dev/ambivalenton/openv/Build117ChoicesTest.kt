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

/** Build 117 · other connections at a station, the confirmation, own reports and the reliability line. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build117ChoicesTest {
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
        return base.copy(choices = JourneyChoices(s.id, JourneyQuery(hbf, herne, now + 3 * min), listOf(
            choice("a", "U35", LineType.SUBWAY, 7, 33), choice("b", "302", LineType.TRAM, 9, 41), choice("c", "RE2", LineType.REGIONAL, 10, 44, changes = 1))))
    }

    @Test fun threeChoicesAtTheChangeAskOnceBeforeReplacing() {
        val chosen = mutableListOf<String>()
        val pinned = state()
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalJourneyChoiceActions provides JourneyChoiceActions(ReliabilityMemory(), { _, key -> chosen += key }, { _, _ -> })) {
                Column(Modifier.padding(16.dp)) { PinnedJourneyCard(pinned, {}, {}) }
            }
        } } }
        compose.waitForIdle()
        compose.onAllNodesWithTag("journey-choice", useUnmergedTree = true).assertCountEquals(3)
        compose.onAllNodesWithContentDescription("Ankunft", substring = true, useUnmergedTree = true)[0]
            .assertContentDescriptionContains("Verlässlichkeit geschätzt", substring = true)
        save("card-choices")
        compose.onAllNodesWithTag("journey-choice", useUnmergedTree = true)[2].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journey-choice-dialog").assertExists()
        compose.onNodeWithText("Verbindung wechseln?").assertExists()
        compose.onNodeWithText("statt", substring = true).assertExists()
        save("choice-dialog")
        assertTrue(chosen.isEmpty())
        compose.onNodeWithTag("journey-choice-accept").performClick()
        compose.waitForIdle()
        assertEquals(listOf(alternativeJourneyKey(pinned.choices!!.trips[2])), chosen)
        compose.onAllNodesWithTag("journey-choice-dialog").assertCountEquals(0)
    }

    @Test fun withoutActionsOrFreshChoicesNothingIsOffered() {
        val stale = state().let { s -> s.copy(choices = s.choices!!.copy(trips = s.choices!!.trips.map { it.copy(fetchedAt = now - 10 * min) })) }
        var withActions by mutableStateOf(false)
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
            val actions = if (withActions) JourneyChoiceActions(ReliabilityMemory(), { _, _ -> }, { _, _ -> }) else null
            CompositionLocalProvider(LocalJourneyChoiceActions provides actions) {
                Column(Modifier.padding(16.dp)) { PinnedJourneyCard(if (withActions) stale else state(), {}, {}) }
            }
        } } }
        compose.waitForIdle()
        // The bubble and previews provide no actions: no buttons.
        compose.onAllNodesWithTag("journey-choice", useUnmergedTree = true).assertCountEquals(0)
        compose.runOnIdle { withActions = true }
        compose.waitForIdle()
        // Choices older than three minutes may be gone: not offered.
        compose.onAllNodesWithTag("journey-choice", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun theJourneyPageTakesOwnReportsAndShowsTheReliability() {
        val reports = mutableListOf<Pair<String, Boolean>>()
        var memory by mutableStateOf(ReliabilityMemory())
        val pinned = state()
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalNavigationClearance provides 96.dp,
                LocalJourneyChoiceActions provides JourneyChoiceActions(memory, { _, _ -> }, { segment, onTime -> reports += segment.lineName to onTime
                    memory = memory.record(stationRideKey(segment), segment.lineName, dev.ambivalenton.openv.data.reliability.ReliabilityMode.REGIONAL_EXPRESS,
                        dev.ambivalenton.openv.data.reliability.TimeWindow.WEEKDAY, onTime, now) })) {
                OverviewScreen(OverviewUiState(ready = true), OverviewSettings(showQuotes = false), {}, {}, {}, pinnedState = pinned, takeover = true)
            }
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("pinned-journey-page").assertExists()
        compose.onNodeWithTag("journey-reliability", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Wie fährt die RE1?").assertExists()
        compose.onAllNodesWithTag("journey-choice", useUnmergedTree = true).assertCountEquals(3)
        save("page-choices")
        compose.onNodeWithTag("ride-late").performClick()
        compose.waitForIdle()
        assertEquals(listOf("RE1" to false), reports)
        compose.onNodeWithText("Danke, gemerkt.").assertExists()
        compose.mainClock.advanceTimeBy(3_000); compose.waitForIdle()
        // Reported once: the question does not come back for this ride.
        compose.onAllNodesWithTag("ride-feedback").assertCountEquals(0)
    }

    private fun save(name: String) = compose.runOnIdle {
        val v = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val file = File("../audit/build117/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
