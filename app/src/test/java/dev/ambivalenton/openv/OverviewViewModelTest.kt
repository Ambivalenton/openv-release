// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv

import androidx.lifecycle.ViewModelStore
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.location.CurrentPositionSource
import dev.ambivalenton.openv.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {
    private val now = 1_800_000_000_000L
    private val here = Location("Aktueller Standort", latitude = 51.5, longitude = 7.2)
    private val far = Location("Zielort", latitude = 51.51, longitude = 7.2)

    private class Position(
        private val point: Location,
        now: Long,
        private val allowed: Boolean = true,
    ) : CurrentPositionSource {
        var calls = 0

        override fun hasPermission() = allowed

        override val lastPresence = DevicePresence(point, now, 10f)

        override suspend fun current(): Location {
            calls++
            check(allowed)
            return point
        }
    }

    private val locations
        get() = (RuntimeEnvironment.getApplication() as OpenVApp).locations

    @Before
    fun clearPersistentFixture() = runBlocking {
        locations.state.first().favorites.forEach { locations.deleteFavorite(it.id) }
        locations.updateSettings { AppSettings() }
        locations.clearPresence()
        Unit
    }

    @Test
    fun manualStartupHidesOnlyDestinationsNearTheSelectedOriginAndIsolatesErrors() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val holder = ViewModelStore()
        try {
            val manual = far.copy(name = "Manueller Start", latitude = 51.53)
            locations.updateSettings {
                it.copy(
                    originMode = OriginMode.MANUAL,
                    manualOrigin = manual,
                    overview = OverviewSettings(bufferMinutes = 10f),
                )
            }
            locations.saveFavorite(Favorite("near", "Schon da", here.copy(latitude = 51.5005)))
            locations.saveFavorite(Favorite("far", "Zuhause", far))
            locations.saveFavorite(Favorite("alias", "Familie", far))
            locations.saveFavorite(
                Favorite("bad", "Nicht verfügbar", far.copy(name = "Fehler", latitude = 51.54))
            )
            val provider =
                OverviewTestProvider().apply {
                    response = { q, _ ->
                        if (q.destination.name == "Fehler") throw TransitException("Testfehler")
                        JourneyPage(listOf(overviewTestTrip(q)))
                    }
                }
            val source = Position(here, now)
            val vm =
                OverviewViewModel(
                    locations,
                    OverviewRepository({ provider }) { now },
                    source,
                    { now },
                )
            holder.put("overview", vm)
            vm.setForeground(true)
            val state = vm.state.first { it.ready && !it.loading && it.entries.size == 4 }
            assertEquals(0, source.calls)
            assertEquals(0, state.hiddenNearby)
            assertEquals(3, provider.queries.size)
            assertTrue(provider.queries.any { it.destination.latitude == 51.5005 })
            assertTrue(
                provider.queries.all {
                    it.origin == manual &&
                        it.time == now + 600_000 &&
                        it.timeMode == TimeMode.DEPARTURE
                }
            )
            assertNotNull(state.entries.first { it.favorite.id == "far" }.trip)
            assertEquals(
                state.entries.first { it.favorite.id == "far" }.trip,
                state.entries.first { it.favorite.id == "alias" }.trip,
            )
            assertNotNull(state.entries.first { it.favorite.id == "bad" }.error)
            assertNull(state.error)
        } finally {
            holder.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun textModulesDoNotRefetchButBufferAndFavoriteChangesDo() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val holder = ViewModelStore()
        try {
            val favorite = Favorite("far", "Zuhause", far)
            locations.saveFavorite(favorite)
            val provider = OverviewTestProvider()
            val source = Position(here, now)
            val vm =
                OverviewViewModel(
                    locations,
                    OverviewRepository({ provider }) { now },
                    source,
                    { now },
                )
            holder.put("overview", vm)
            vm.setForeground(true)
            val first =
                vm.state.first { it.ready && !it.loading && it.entries.firstOrNull()?.trip != null }
            val variant = first.entries.single().variant
            locations.updateSettings {
                it.copy(overview = it.overview.copy(showDuration = true, showPlatforms = true))
            }
            locations.state.first { it.settings.overview.showDuration }
            runCurrent()
            assertEquals(1, provider.queries.size)
            locations.updateSettings { it.copy(overview = it.overview.copy(bufferMinutes = 5f)) }
            val buffered =
                vm.state.first {
                    !it.loading && it.entries.firstOrNull()?.query?.time == now + 300_000
                }
            assertEquals(2, provider.queries.size)
            assertEquals(variant, buffered.entries.single().variant)
            locations.saveFavorite(favorite.copy(location = far.copy(latitude = 51.52)))
            vm.state.first {
                !it.loading &&
                    it.entries.firstOrNull()?.trip?.segments?.last()?.destination?.latitude == 51.52
            }
            assertEquals(3, provider.queries.size)
            locations.deleteFavorite(favorite.id)
            vm.state.first { it.ready && !it.loading && it.entries.isEmpty() }
            assertEquals(3, source.calls)
        } finally {
            holder.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun backgroundingCancelsRequestsAndDoesNotStartNewOnesUntilForeground() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val holder = ViewModelStore()
        try {
            locations.saveFavorite(Favorite("far", "Zuhause", far))
            val started = CompletableDeferred<Unit>()
            val resumed = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val provider =
                OverviewTestProvider().apply {
                    response = { _, _ ->
                        if (!started.complete(Unit)) resumed.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled.complete(Unit)
                        }
                    }
                }
            val vm =
                OverviewViewModel(
                    locations,
                    OverviewRepository({ provider }) { now },
                    Position(here, now),
                    { now },
                )
            holder.put("overview", vm)
            vm.setForeground(true)
            started.await()
            vm.setForeground(false)
            cancelled.await()
            vm.state.first { !it.loading }
            vm.refresh()
            runCurrent()
            assertEquals(1, provider.queries.size)
            vm.setForeground(true)
            resumed.await()
            assertEquals(2, provider.queries.size)
        } finally {
            holder.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun manualStartWorksWithoutGpsButIsNeverUsedAsDevicePresence() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val holder = ViewModelStore()
        try {
            locations.saveFavorite(Favorite("far", "Zuhause", far))
            // A favourite at the manual start is always hidden, so the start lies elsewhere.
            val manualStart = Location("Manueller Start", latitude = 51.53, longitude = 7.2)
            locations.updateSettings { it.copy(originMode = OriginMode.MANUAL, manualOrigin = manualStart) }
            val provider = OverviewTestProvider()
            val source = Position(here, now, allowed = false)
            val vm =
                OverviewViewModel(
                    locations,
                    OverviewRepository({ provider }) { now },
                    source,
                    { now },
                )
            holder.put("overview", vm)
            vm.setForeground(true)
            val manual =
                vm.state.first { it.ready && !it.loading && it.entries.firstOrNull()?.trip != null }
            assertEquals(0, source.calls)
            assertEquals(0, manual.hiddenNearby)
            locations.updateSettings { it.copy(originMode = OriginMode.AUTOMATIC) }
            locations.state.first {it.settings.originMode==OriginMode.MANUAL}
            val fallback = vm.state.first {it.ready && !it.loading && it.entries.firstOrNull()?.trip!=null}
            assertNull(fallback.error)
            assertEquals(0,source.calls)
            assertNull(locations.state.first().presence)
            assertEquals(1, provider.queries.size)
        } finally {
            holder.clear()
            Dispatchers.resetMain()
        }
    }
}
