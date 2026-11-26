// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.PinnedJourneyCard
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
 * Build 125 · test drive in the S2: the card flipped between "07:13" and "ca. 07:16 +3 Min." (own GPS estimate
 * against the provider, decided afresh with every fix), and it said too much – "S2 · Ankunft Gleis 6" while
 * sitting in the S2, the onward ride in a big box twenty minutes before the change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build125CardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = System.currentTimeMillis() / 60_000 * 60_000
    private val min = 60_000L

    @Test fun theOwnEstimateIsHeldAndDoesNotFlip() {
        val provider = now + 20 * min
        val threshold = 120_000L
        fun step(held: LocalArrivalHold?, local: Long) = decideLocalArrivalHold(held, "k", local, provider, threshold, false, now)
        // 2.5 min later than the forecast: switch to the own estimate.
        var h = step(null, provider + 150_000)
        assertEquals(provider + 150_000, h.time)
        // Wavering around the limit (1.7 min, 2.8 min, 1.5 min): the shown time stays where it is.
        for (local in listOf(provider + 100_000, provider + 170_000, provider + 90_000)) {
            h = step(h, local); assertEquals("held at $local", provider + 150_000, h.time)
        }
        // A real change of a minute and more is followed.
        h = step(h, provider + 240_000); assertEquals(provider + 240_000, h.time)
        // Clear agreement again (within 40 s): back to the provider – and it stays there below the threshold.
        h = step(h, provider + 30_000); assertNull(h.time)
        h = step(h, provider + 100_000); assertNull(h.time)
        // An own estimate never makes a provider forecast earlier.
        assertNull(step(null, provider - 5 * min).time)
    }

    private fun place(name: String, id: String, lat: Double, lon: Double) = Location(name, id, lat, lon, "VRR")
    private val start = place("Recklinghausen Süd", "s", 51.57, 7.20)
    private val hbf = place("Dortmund Hbf", "h", 51.517, 7.459)
    private val hacheney = place("Hacheney", "z", 51.48, 7.48)

    private fun trip(arriveIn: Long, stops: Boolean = true): Trip {
        val sArrival = now + arriveIn
        val calls = if (!stops) emptyList() else (0..9).map { k -> val at = now - 20 * min + k * (sArrival - (now - 20 * min)) / 9
            Stopover(if (k == 0) start else if (k == 9) hbf else place("Halt $k", "x$k", 51.55 - k / 100.0, 7.25 + k / 50.0), at, at) }
        val s2 = TripSegment("s2", start, hbf, now - 20 * min, sArrival, LineType.SUBURBAN, "S2", arrivalPlatform = "6", direction = "Dortmund Hbf",
            stops = calls, delay = DelayInfo(0, 0), departureSource = TimeSource.PROGNOSIS, arrivalSource = TimeSource.PROGNOSIS)
        val u = TripSegment("u49", hbf, hacheney, sArrival + 5 * min, sArrival + 16 * min, LineType.SUBWAY, "U49",
            departurePlatform = "2", direction = "Dortmund Hacheney")
        return Trip("trip", listOf(s2, u), "VRR", now)
    }

    @Test fun projectionUsesTheHeldDecision() {
        val t = trip(20 * min)
        val provider = t.segments[0].effectiveArrival()
        val held = projectJourneyTimes(t, now, held = HeldArrival(0, provider + 3 * min)).leg(0)!!.arrival
        assertEquals(provider + 3 * min, held.time)
        assertTrue(held.approximate)
        assertEquals(provider, projectJourneyTimes(t, now, held = HeldArrival(0, null)).leg(0)!!.arrival.time)
    }

    @Test fun theStageFollowsStopsAndTime() {
        assertEquals(BriefStage.CALM, briefStage(BriefStepKind.ALIGHT, 9, now + 25 * min, now))
        assertEquals(BriefStage.SOON, briefStage(BriefStepKind.ALIGHT, 2, now + 6 * min, now))
        assertEquals(BriefStage.SOON, briefStage(BriefStepKind.ALIGHT, 9, now + 3 * min, now))
        // Without the ride's stops, "one stop" is the destination itself: only the time decides.
        assertEquals(BriefStage.CALM, briefStage(BriefStepKind.ALIGHT, 1, now + 25 * min, now, stopsKnown = false))
    }

    private fun texts(): List<String> = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        .fetchSemanticsNodes().flatMap { n -> n.config[SemanticsProperties.Text].map { it.text } }

    private fun show(trip: Trip) {
        val session = PinnedJourneySession(favorite = Favorite("bfw", "BFW", hacheney), query = JourneyQuery(start, hacheney, now),
            initialTrip = trip, routing = AppSettings())
        val state = stabilizedAssistantState(PinnedJourneyState(), PinnedJourneyState(session, PinnedTrackingStatus.RUNNING), System.currentTimeMillis())
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp)) { PinnedJourneyCard(state, {}, {}) }
        } } }
        compose.waitForIdle()
    }

    @Test fun onTheRideOnlyWhereAndWhenItEnds() {
        show(trip(22 * min))
        val all = texts()
        assertTrue(all.toString(), all.any { it == "Ankunft" })
        assertTrue(all.toString(), all.any { it == "Dortmund Hbf" })
        // Not repeated while sitting in it: the line and the arrival platform.
        assertFalse(all.toString(), all.any { "S2 ·" in it || "Ankunft Gleis" in it })
        // The onward ride waits in one quiet line, without its platform.
        assertTrue(all.toString(), all.any { it.startsWith("Weiter mit U49 um") })
        compose.onAllNodesWithTag("card-platform", useUnmergedTree = true).assertCountEquals(0)
        assertTrue(all.toString(), all.any { it.startsWith("Am Ziel") })
        save("ride")
    }

    @Test fun shortlyBeforeTheChangeTheOnwardRideComesToTheFront() {
        show(trip(3 * min))
        val all = texts()
        assertTrue(all.toString(), all.any { it == "Aussteigen in Dortmund Hbf" })
        assertTrue(all.toString(), all.any { it == "Weiter mit U49" })
        compose.onNodeWithTag("card-platform", useUnmergedTree = true).assertExists()
        assertEquals(all.toString(), 1, all.count { "Dortmund Hbf" in it && "Hacheney" !in it })
        save("change-soon")
    }

    private fun save(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        val file = File("../audit/build125/card-$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
