// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.location.CurrentPositionSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Build 117 · the controller keeps the other reachable connections of its own search and takes a chosen one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class Build117ControllerTest {
    private val now = 1_800_000_000_000L
    private val a = Location("Start", "a", 51.5, 7.2, "Test")
    private val b = Location("Ziel", "b", 51.6, 7.3, "Test")
    private val trip = Trip("original", listOf(TripSegment("leg", a, b, now + 600_000, now + 1_200_000, LineType.BUS, "312")), "Test", now, refreshToken = "token")
    private val favorite = Favorite("favorite", "Zuhause", b)
    private val query = JourneyQuery(a, b, now)
    private val positions = object : CurrentPositionSource {
        override fun hasPermission() = false
        override val lastPresence: DevicePresence? = null
        override suspend fun current(): Location = error("Location was not granted")
    }
    private fun other(id: String, line: String, leave: Long, arrive: Long) =
        Trip(id, listOf(TripSegment("$id-leg", a, b, now + leave, now + arrive, LineType.BUS, line)), "Test", now)
    private val choices = listOf(other("x", "390", 900_000, 1_500_000), other("y", "391", 1_000_000, 1_600_000),
        other("z", "392", 1_100_000, 1_700_000), other("w", "393", 1_200_000, 1_800_000))
    private inner class Engine : TransitRepository {
        override val name = "Test"
        var pages = 0
        override suspend fun locations(query: String) = emptyList<Location>()
        override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int) = emptyList<Location>()
        override suspend fun departures(stop: Location) = emptyList<Departure>()
        override suspend fun journeyPage(query: JourneyQuery, cursor: String?): JourneyPage { pages++; return JourneyPage(listOf(trip) + choices) }
        override suspend fun refresh(trip: Trip): Trip = this@Build117ControllerTest.trip.copy(fetchedAt = now + 1_000)
    }

    @Test fun theSameSearchOffersUpToThreeChoicesAndAChosenOneReplacesTheJourney() = runTest {
        val engine = Engine()
        val controller = PinnedJourneyController(RuntimeEnvironment.getApplication(), { engine }, positions, now = { now })
        controller.close()
        controller.pin(favorite, query, trip, AppSettings())
        controller.refreshOnce()
        val pagesAfterSearch = engine.pages
        val state = controller.state.value
        val offered = state.choices!!
        assertEquals(state.session!!.id, offered.sessionId)
        // The pinned journey itself is never offered; at most three others, earliest arrival first.
        assertTrue(offered.trips.none { samePinnedJourney(it, trip) })
        assertEquals(listOf("390", "391"), offered.trips.map { it.segments.single().lineName }.take(2))
        assertTrue(offered.trips.size in 1..3)
        val before = state.session!!
        val outcome = controller.adoptChoice(before.id, alternativeJourneyKey(offered.trips[1]))
        assertTrue(outcome is AlternativeAdoption.Adopted)
        val after = controller.state.value
        assertEquals("391", after.session!!.trip.segments.single { !it.isWalk }.lineName)
        assertNotEquals(before.id, after.session!!.id)
        assertNull(after.choices)
        // Choosing needs no further request.
        assertEquals(pagesAfterSearch, engine.pages)
        // A stale key or an old session is not taken.
        assertEquals(AlternativeAdoption.Expired, controller.adoptChoice(before.id, alternativeJourneyKey(offered.trips[0])))
        assertEquals(AlternativeAdoption.Expired, controller.adoptChoice(after.session!!.id, "unknown"))
        controller.close()
    }
}
