// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class)
class DepartureBoardTest {
    private val now = 1_800_000_000_000L
    private val a = Location("Herne A", "a", 51.5, 7.2, "VRR")
    private val b = Location("Herne B", "b", 51.51, 7.2, "VRR")

    @Before
    @After
    fun resetStore() = runBlocking {
        val locations = (RuntimeEnvironment.getApplication() as OpenVApp).locations
        val old = locations.state.first()
        old.favorites.forEach { locations.deleteFavorite(it.id) }
        old.boardStops.forEach { locations.removeBoardStop(it) }
        locations.updateSettings { AppSettings() }
        locations.saveDepartureBoard(DepartureBoardState())
        locations.clearPresence()
        Unit
    }

    private open class Provider(val stops: List<Location>) : TransitRepository {
        override val name = "VRR / EFA · NRW"
        var fail = false
        var rows = emptyList<Departure>()

        override suspend fun locations(query: String) = stops.filter { it.name == query }

        override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int) = stops

        override suspend fun departures(stop: Location): List<Departure> {
            if (fail) throw TransitException("Verbindung unterbrochen")
            return rows
        }

        override suspend fun journeyPage(query: JourneyQuery, cursor: String?) = error("Unused")

        override suspend fun refresh(trip: Trip) = trip
    }

    @Test
    fun hiddenFavoritesStillParticipateAndExtraStopsNeverBecomeFavorites() = runBlocking {
        val locations = (RuntimeEnvironment.getApplication() as OpenVApp).locations
        val favorite =
            Favorite(
                "home",
                "Zuhause",
                a,
                hiddenRules = listOf(HighlightRule((1..7).toSet(), 0, 0)),
            )
        locations.saveFavorite(favorite)
        locations.addBoardStop(b)
        locations.addBoardStop(b)
        val state = locations.state.first()
        assertEquals(listOf(favorite), state.favorites)
        assertEquals(listOf(b), state.boardStops)
        assertEquals(listOf(a, b), boardCandidates(state))
        locations.removeBoardStop(b)
        assertEquals(listOf(a), boardCandidates(locations.state.first()))
    }

    @Test
    fun nearestStationChangesWithTheDeviceAndIgnoresInvalidCoordinates() {
        assertEquals(a, nearestBoardStop(a, listOf(b, a)))
        assertEquals(b, nearestBoardStop(b, listOf(a, b)))
        assertEquals(a, nearestBoardStop(a, listOf(Location("Missing", "missing"), a)))
        assertNull(nearestBoardStop(a, emptyList()))
    }

    @Test
    fun addressAndForeignStationResolveThroughTheSelectedProvider() = runBlocking {
        val foreign = b.copy(provider = "DB", id = "old")
        val address = a.copy(kind = LocationKind.ADDRESS, provider = "OSM", id = null)
        val stops = resolveBoardStops(listOf(address, foreign, a), Provider(listOf(a, b)))
        assertEquals(setOf(a, b), stops.toSet())
        assertEquals(b, nearestBoardStop(b, stops))
    }

    @Test
    fun selectionIsCachedButADeviceMoveRecomputesTheNearestStationAndFiltersIce() = runBlocking {
        val locations = (RuntimeEnvironment.getApplication() as OpenVApp).locations
        locations.saveFavorite(Favorite("home", "Zuhause", a))
        locations.addBoardStop(b)
        locations.recordPresence(DevicePresence(a, now, 10f))
        val provider =
            Provider(listOf(a, b)).apply {
                rows =
                    listOf(
                        Departure(
                            "local",
                            a,
                            "U35",
                            LineType.SUBWAY,
                            "Bochum",
                            now + 60_000,
                            now + 60_000,
                            DelayInfo(),
                            "2",
                        ),
                        Departure(
                            "ice",
                            a,
                            "ICE 100",
                            LineType.ICE,
                            "Berlin",
                            now + 30_000,
                            now + 30_000,
                            DelayInfo(),
                        ),
                    )
            }
        val repo = DepartureBoardRepository(locations) { provider }
        repo.refresh(now = now)
        assertEquals(a, locations.state.first().departureBoard.stop)
        assertEquals(
            listOf("local"),
            locations.state.first().departureBoard.departures.map { it.id },
        )
        locations.recordPresence(DevicePresence(b, now + 1000, 10f))
        repo.refresh(now = now + 1000)
        assertEquals(b, locations.state.first().departureBoard.stop)
    }

    @Test
    fun failedRefreshKeepsStampedDataButAChangedConfigurationCannotShowAnOldStation() =
        runBlocking {
            val locations = (RuntimeEnvironment.getApplication() as OpenVApp).locations
            locations.addBoardStop(a)
            locations.recordPresence(DevicePresence(a, now, 10f))
            val provider =
                Provider(listOf(a)).apply {
                    rows =
                        listOf(
                            Departure(
                                "next",
                                a,
                                "S2",
                                LineType.SUBURBAN,
                                "Dortmund",
                                now + 60_000,
                                now + 60_000,
                                DelayInfo(),
                            )
                        )
                }
            val repo = DepartureBoardRepository(locations) { provider }
            repo.refresh(now = now)
            provider.fail = true
            repo.refresh(force = true, now = now + 1000)
            val stale = locations.state.first().departureBoard
            assertEquals(a, stale.stop)
            assertEquals(now, stale.fetchedAt)
            assertNotNull(stale.error)
            locations.removeBoardStop(a)
            repo.refresh(force = true, now = now + 2000)
            val empty = locations.state.first().departureBoard
            assertNull(empty.stop)
            assertTrue(empty.departures.isEmpty())
        }

    @Test
    fun aProviderChangeDuringTheRequestPreventsPublishingItsResponse() = runBlocking {
        val locations = (RuntimeEnvironment.getApplication() as OpenVApp).locations
        locations.addBoardStop(a)
        locations.recordPresence(DevicePresence(a, now, 10f))
        val provider =
            object : Provider(listOf(a)) {
                override suspend fun departures(stop: Location): List<Departure> {
                    locations.updateSettings { it.copy(includeLongDistance = true) }
                    return emptyList()
                }
            }
        DepartureBoardRepository(locations) { provider }.refresh(now = now)
        assertEquals(0L, locations.state.first().departureBoard.fetchedAt)
    }

    /** "Automatisch die nächstgelegene Haltestelle" with an older or coarse fix (approximate
     * location, indoors). Before, the guard accepted such a fix but the choice then required a fresh
     * fix of at most 50 m, so the board only showed "could not be resolved". */
    @Test
    fun automaticBoardUsesTheNearestListStopAlsoWithAnOlderOrCoarseFix() = runBlocking {
        val locations = (RuntimeEnvironment.getApplication() as OpenVApp).locations
        val stopA = a.copy(kind = LocationKind.STOP); val stopB = b.copy(kind = LocationKind.STOP)
        locations.addBoardStop(stopA); locations.addBoardStop(stopB)
        locations.selectBoardStop(null)
        // 12 minutes old, 900 m accuracy, right next to B.
        locations.recordPresence(DevicePresence(Location("hier", latitude = 51.5101, longitude = 7.2), now - 12 * 60_000, 900f))
        val provider = Provider(listOf(stopA, stopB))
        DepartureBoardRepository(locations) { provider }.refresh(force = true, now = now)
        val board = locations.state.first().departureBoard
        assertNull(board.error)
        assertEquals(stopB, board.stop)
    }
}
