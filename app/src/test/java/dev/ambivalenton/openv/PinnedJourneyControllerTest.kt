// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.location.CurrentPositionSource
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PinnedJourneyControllerTest {
    private val now = 1_800_000_000_000L
    private val a = Location("Start", "a", 51.5, 7.2, "Test")
    private val b = Location("Ziel", "b", 51.6, 7.3, "Test")
    private val trip = Trip("original", listOf(TripSegment("leg", a, b, now + 600_000,
        now + 1_200_000, LineType.BUS, "312")), "Test", now, refreshToken = "token")
    private val favorite = Favorite("favorite", "Zuhause", b)
    private val query = JourneyQuery(a, b, now)
    private val positions = object : CurrentPositionSource {
        override fun hasPermission() = false
        override val lastPresence: DevicePresence? = null
        override suspend fun current(): Location = error("Location was not granted")
    }
    private open class Engine(private val original: Trip) : TransitRepository {
        override val name = "Test"
        var refreshes = 0
        var pages = 0
        var fail = false
        var response: suspend (JourneyQuery) -> List<Trip> = { listOf(original) }
        override suspend fun locations(query: String) = emptyList<Location>()
        override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int) = emptyList<Location>()
        override suspend fun departures(stop: Location) = emptyList<Departure>()
        override suspend fun journeyPage(query: JourneyQuery, cursor: String?): JourneyPage {
            pages++
            return JourneyPage(response(query))
        }
        override suspend fun refresh(trip: Trip): Trip {
            refreshes++
            if (fail) throw java.io.IOException("offline")
            return original.copy(fetchedAt = original.fetchedAt + 1_000)
        }
    }

    @Test fun savedJourneySurvivesControllerRecreationAndCloseRemovesIt() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val engine = Engine(trip)
        val controller = PinnedJourneyController(context, { engine }, positions, now = { now })
        controller.close()
        controller.pin(favorite, query, trip, AppSettings())
        controller.refreshOnce()
        val restored = PinnedJourneyController(context, { engine }, positions, now = { now })
        restored.restore()
        assertEquals(controller.state.value.session, restored.state.value.session)
        assertEquals(PinnedTrackingStatus.PAUSED, restored.state.value.status)
        restored.close()
        val empty = PinnedJourneyController(context, { engine }, positions)
        empty.restore()
        assertNull(empty.state.value.session)
    }

    @Test fun unavailableLiveDataNeverReplacesThePinnedVehicleWithAnUnrelatedSearchResult() = runTest {
        val engine = Engine(trip).apply { fail = true }
        val controller = PinnedJourneyController(RuntimeEnvironment.getApplication(), { engine }, positions, now = { now })
        controller.close()
        controller.pin(favorite, query, trip, AppSettings())
        controller.refreshOnce()
        assertEquals(trip, controller.state.value.session!!.trip)
        assertEquals(now, controller.state.value.session!!.lastSuccessAt)
        assertNull(controller.state.value.error)
        controller.close()
    }

    @Test fun closingDuringAnInFlightRefreshCannotResurrectTheJourney() = runTest {
        val pending = CompletableDeferred<Trip>()
        val entered = CompletableDeferred<Unit>()
        val engine = object : Engine(trip) {
            override suspend fun refresh(trip: Trip): Trip { entered.complete(Unit); return pending.await() }
        }
        val controller = PinnedJourneyController(RuntimeEnvironment.getApplication(), { engine }, positions, now = { now })
        controller.close()
        controller.pin(favorite, query, trip, AppSettings())
        val refresh = launch { controller.refreshOnce() }
        entered.await()
        controller.close()
        pending.complete(trip)
        refresh.join()
        assertNull(controller.state.value.session)
        assertFalse(controller.hasSession)
    }

    @Test fun efaFallbackMatchesTheOriginalChainAndOffersAnAlternativeWithoutSilentlyReplacingIt() = runTest {
        val efa = trip.copy(refreshToken = null,segments=trip.segments.map {it.copy(startTime=now+299_000,plannedStartTime=now+299_000)})
        val cancelled = efa.copy(id = "changed-provider-id", segments = efa.segments.map {
            it.copy(delay = DelayInfo(cancelled = true))
        })
        val alternative = efa.copy(id = "alternative", segments = efa.segments.map {
            it.copy(id = "alt", lineName = "390", startTime = now + 900_000,
                plannedStartTime = now + 900_000, endTime = now + 1_500_000)
        })
        val engine = Engine(efa).apply {
            response = { if (pages == 1) listOf(alternative, cancelled) else listOf(alternative) }
        }
        val controller = PinnedJourneyController(RuntimeEnvironment.getApplication(), { engine }, positions, now = { now })
        controller.close()
        controller.pin(favorite, query, efa, AppSettings())
        controller.refreshOnce()
        val before = controller.state.value.session!!
        assertTrue(before.trip.cancelled)
        assertEquals("312", before.trip.segments.single().lineName)
        assertEquals(alternative, before.alternative)
        controller.adoptAlternative()
        val after = controller.state.value.session!!
        assertNotEquals(before.id, after.id)
        assertEquals(alternative, after.trip)
        assertNull(after.alternative)
        controller.close()
    }

    @Test fun pollingStartsEveryMinuteEvenIfTheProviderTakesFiveSeconds() = runTest {
        val engine = object : Engine(trip) {
            override suspend fun refresh(trip: Trip): Trip { refreshes++; delay(5_000); return trip }
        }
        val controller = PinnedJourneyController(RuntimeEnvironment.getApplication(), { engine }, positions,
            now = { now + testScheduler.currentTime }, elapsed = { testScheduler.currentTime }, ioDispatcher = StandardTestDispatcher(testScheduler))
        controller.close()
        controller.pin(favorite, query, trip, AppSettings())
        val tracking = launch { controller.track() }
        // Let real atomic file I/O finish before advancing the virtual service clock.
        while (engine.refreshes == 0) { runCurrent(); yield() }
        advanceTimeBy(59_999); runCurrent()
        assertEquals(1, engine.refreshes)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, engine.refreshes)
        tracking.cancelAndJoin()
        controller.close()
    }
    @Test fun minimizedSessionSurvivesRestartAndCanReopenWithoutChangingIdentity() = runTest {
        val context=RuntimeEnvironment.getApplication()
        val engine=Engine(trip)
        val controller=PinnedJourneyController(context,{engine},positions,now={now})
        controller.close();controller.pin(favorite,query,trip,AppSettings())
        val id=controller.state.value.session!!.id
        assertTrue(controller.focusIsOpen)
        controller.setMinimized(true)
        assertFalse(controller.focusIsOpen)
        val restored=PinnedJourneyController(context,{engine},positions,now={now})
        restored.restore()
        assertTrue(restored.state.value.session!!.minimized)
        assertEquals(id,restored.state.value.session!!.id)
        restored.setMinimized(false)
        assertFalse(restored.state.value.session!!.minimized)
        assertTrue(restored.focusIsOpen)
        assertEquals(id,restored.state.value.session!!.id)
        restored.close()
    }

    @Test fun minimizingDuringRefreshCannotBeOverwrittenByTheNetworkResponse() = runTest {
        val entered=CompletableDeferred<Unit>();val pending=CompletableDeferred<Trip>()
        val engine=object : Engine(trip) {
            override suspend fun refresh(trip: Trip): Trip { entered.complete(Unit);return pending.await() }
        }
        val context=RuntimeEnvironment.getApplication()
        val controller=PinnedJourneyController(context,{engine},positions,now={now})
        controller.close();controller.pin(favorite,query,trip,AppSettings())
        val refresh=launch { controller.refreshOnce() };entered.await()
        controller.setMinimized(true);pending.complete(trip);refresh.join()
        assertTrue(controller.state.value.session!!.minimized)
        val restored=PinnedJourneyController(context,{engine},positions,now={now})
        restored.restore();assertTrue(restored.state.value.session!!.minimized);restored.close()
    }

    @Test fun staleRemovalConfirmationCannotDeleteAReplacementSession() = runTest {
        val controller=PinnedJourneyController(RuntimeEnvironment.getApplication(),{Engine(trip)},positions,now={now})
        controller.close();controller.pin(favorite,query,trip,AppSettings())
        val old=controller.state.value.session!!.id
        val replacement=trip.copy(id="replacement",segments=trip.segments.map {it.copy(lineName="391")})
        controller.pin(favorite,query,replacement,AppSettings())
        assertFalse(controller.close(old))
        assertNotNull(controller.state.value.session)
        assertTrue(controller.close(controller.state.value.session!!.id))
        assertNull(controller.state.value.session)
    }

}
