// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.AppUiState
import dev.ambivalenton.openv.ui.LoadState
import dev.ambivalenton.openv.ui.components.OeffiTimelineGraph
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Fußwege vollständig ignorieren" – no duplicate columns, no cut-off departure time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-420dpi")
class Build75Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val t = 1_800_000_000_000L
    private val minute = 60_000L
    private val home = Location("Wohnung", latitude = 51.54, longitude = 7.22)
    private val herne = Location("Herne Bf", "h", 51.54, 7.22, "VRR")
    private val dortmund = Location("Dortmund Hbf", "d", 51.517, 7.459, "VRR")

    /** The same RE3 ride, reached by a 4- or a 9-minute walk (two offers of the provider). */
    private fun offer(id: String, walk: Int, rideStart: Long = t + 20 * minute) = Trip(id, listOf(
        TripSegment("w$id", home, herne, rideStart - walk * minute, rideStart, LineType.WALK, "Fußweg"),
        TripSegment("re3", herne, dortmund, rideStart, rideStart + 18 * minute, LineType.REGIONAL, "RE3", serviceId = "RE3-1"),
    ), "TEST", t)

    /** The same ride, but the provider routes the walk over the bus stop in front of the station. */
    private fun viaForecourt(id: String): Trip {
        val forecourt = Location("Herne Bf (Busbahnhof)", "hb", 51.541, 7.221, "VRR")
        val base = offer(id, 9)
        val ride = base.segments.last()
        return base.copy(segments = listOf(
            TripSegment("w1$id", home, forecourt, ride.startTime - 9 * minute, ride.startTime - 3 * minute, LineType.WALK, "Fußweg"),
            TripSegment("w2$id", forecourt, herne, ride.startTime - 3 * minute, ride.startTime, LineType.WALK, "Fußweg"),
            ride))
    }

    @Test fun offersThatDifferOnlyInTheirWalksAreOneConnectionWhenWalksAreIgnored() {
        val trips = listOf(offer("a", 4), viaForecourt("b"), offer("c", 4, t + 50 * minute))
        assertEquals("walks count by default", 3, mergeDuplicateConnections(trips).size)
        val merged = mergeDuplicateConnections(trips, ignoreWalking = true)
        assertEquals(listOf("a", "c"), merged.map { it.id })
        val ui = AppUiState(store = StoredState(settings = AppSettings(ignoreWalking = true)), trips = LoadState(trips))
        assertEquals(2, ui.allTrips.size)
        assertEquals(3, ui.copy(store = StoredState()).allTrips.size)
    }

    @Test fun theFirstColumnsDepartureTimeStaysVisibleWithoutWalks() {
        val trips = (0..3).map { i -> offer("r$i", 6, t + (20 + i * 15) * minute) }
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true, ignoreWalking = true)) { Surface { OeffiTimelineGraph(trips, {}) } }
        }
        compose.waitForIdle()
        val viewport = compose.onNodeWithTag("timeline-viewport").fetchSemanticsNode().boundsInRoot
        val chip = compose.onNodeWithTag("timeline-ride-start-0-r0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("departure chip inside the viewport (${chip.top} vs ${viewport.top})", chip.top >= viewport.top - .5f)
    }
}
