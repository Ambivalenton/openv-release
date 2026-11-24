// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.OverviewUiState
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

/** Build 116 · the running journey: every fact once, and the overview becomes its page once under way. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build116JourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = System.currentTimeMillis() / 60_000 * 60_000
    private fun place(name: String, id: String, lat: Double, lon: Double) = Location(name, id, lat, lon, "VRR")
    private val start = place("Bochum Ruhrstadion", "s", 51.49, 7.23)
    private val hbf = place("Bochum Hbf", "h", 51.478, 7.223)
    private val herne = place("Herne Schloß Strünkede", "z", 51.55, 7.21)
    private val home = Favorite("home", "Zuhause", herne, icon = FavoriteIcon.HOME)
    /** Riding the RE1 towards Bochum Hbf, then the U35 from platform 3 (U). */
    private fun session(): PinnedJourneySession {
        val re = TripSegment("re", start, hbf, now - 4 * 60_000, now + 3 * 60_000, LineType.REGIONAL, "RE1",
            arrivalPlatform = "3", departurePlatform = "2", direction = "Hamm")
        val u = TripSegment("u", hbf, herne, now + 12 * 60_000, now + 38 * 60_000, LineType.SUBWAY, "U35",
            departurePlatform = "3 (U)", direction = "Herne Schloß Strünkede")
        val trip = Trip("trip", listOf(re, u), "VRR", now)
        return PinnedJourneySession(favorite = home, query = JourneyQuery(start, herne, now), initialTrip = trip, routing = AppSettings())
    }
    private fun state() = stabilizedAssistantState(PinnedJourneyState(), PinnedJourneyState(session(), PinnedTrackingStatus.RUNNING), System.currentTimeMillis())

    private fun texts(): List<String> = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        .fetchSemanticsNodes().flatMap { n -> n.config[SemanticsProperties.Text].map { it.text } }

    @Test fun aJourneyUnderWayStartedWithItsFirstStep() {
        val s = session()
        assertTrue(s.underWay(now))
        assertFalse(s.underWay(now - 10 * 60_000))
    }

    @Test fun theCardNamesTheChangeAndThePlatformOnce() {
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp)) { PinnedJourneyCard(state(), {}, {}) }
        } } }
        compose.waitForIdle()
        val all = texts()
        assertEquals(all.toString(), 1, all.count { "Bochum Hbf" in it })
        assertEquals(all.toString(), 1, all.count { "3 (U)" in it })
        assertTrue(all.toString(), all.any { it.startsWith("Weiter mit U35") })
        assertTrue(all.toString(), all.any { it.startsWith("Am Ziel") })
        compose.onNodeWithTag("card-platform", useUnmergedTree = true).assertExists()
        save("card")
    }

    @Test fun theOverviewBecomesThePageOfTheJourneyAndComesBack() {
        var takeover by mutableStateOf(false)
        val pinned = state()
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalNavigationClearance provides 96.dp) {
                OverviewScreen(OverviewUiState(ready = true), OverviewSettings(showQuotes = false), {}, {}, {}, pinnedState = pinned, takeover = takeover)
            }
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("overview-content").assertExists()
        compose.onAllNodesWithTag("pinned-journey-page").assertCountEquals(0)
        compose.runOnIdle { takeover = true }
        compose.waitForIdle()
        compose.onNodeWithTag("pinned-journey-page").assertExists()
        compose.onAllNodesWithTag("overview-content").assertCountEquals(0)
        // Fixed: the page does not scroll.
        compose.onNodeWithTag("pinned-journey-page").assert(!hasScrollAction())
        save("page")
        compose.runOnIdle { takeover = false }
        compose.waitForIdle()
        compose.onNodeWithTag("overview-content").assertExists()
    }

    /** Closing the intermediate stops: their points shrink on the spot instead of riding up with the rows. */
    @Test fun captureClosingTheIntermediateStops() {
        val min = 60_000L
        fun stop(k: Int) = place("Halt $k", "x$k", 51.5, 7.3 + k / 100.0)
        val stops = (0..7).map { k -> val at = now + 3_600_000 + k * 90_000L
            Stopover(if (k == 0) start else if (k == 7) hbf else stop(k), at, at) }
        val trip = Trip("t", listOf(TripSegment("tram", start, hbf, now + 3_600_000, now + 3_600_000 + 12 * min, LineType.TRAM, "302",
            stops = stops, direction = "Bochum Laer", notices = listOf("Hinweis: Umleitung."))), "VRR", now)
        compose.setContent { OpenVTheme(AppSettings()) { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp)) { dev.ambivalenton.openv.ui.components.ModernRouteTimeline(trip) }
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("intermediate-stops-tram").performClick()
        compose.mainClock.advanceTimeBy(2000); compose.waitForIdle()
        save("stops-open")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("intermediate-stops-tram").performClick()
        for (ms in listOf(32L, 80L, 160L)) { compose.mainClock.advanceTimeBy(if (ms == 32L) 32 else if (ms == 80L) 48 else 80); save("stops-closing-$ms") }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle(); save("stops-closed")
    }

    /** Transfer maps without a drawn way show the stops around: the catalog finds them by position. */
    @Test fun theCatalogFindsTheStopsAroundATransfer() = kotlinx.coroutines.runBlocking {
        val catalog = dev.ambivalenton.openv.data.repository.StaticStopCatalog(org.robolectric.RuntimeEnvironment.getApplication())
        // Herne, Bahnhof (Herne Mitte): several platforms and bus stops within a few hundred metres.
        val around = catalog.nearby(51.5435, 7.2197, 350)
        assertTrue(around.toString(), around.isNotEmpty())
        assertTrue(around.toString(), around.all { (it.latitude ?: 0.0) in 51.5 .. 51.6 && (it.longitude ?: 0.0) in 7.15..7.3 })
        assertTrue(catalog.nearby(0.0, 0.0, 350).isEmpty())
    }

    private fun save(name: String) = compose.runOnIdle {
        val v = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val file = File("../audit/build116/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
