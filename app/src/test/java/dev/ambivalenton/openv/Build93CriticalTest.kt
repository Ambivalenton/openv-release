// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.location.CurrentPositionSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * K01 (stand of saved journeys), K02 (recovery from a
 * damaged store) and H09 (pinning is saved with the tap, known cancellations are refused).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Build93CriticalTest {
    // 10:30 local test clock (epoch values only matter relative to each other).
    private val standAt = 1_800_000_000_000L
    private val a = Location("Start", "a", 51.0, 7.0, "TEST")
    private val b = Location("Ziel", "b", 51.1, 7.1, "TEST")
    private fun trip(start: Long, fetched: Long = standAt, token: String? = "t-$start", cancelled: Boolean = false) =
        Trip("trip-$start", listOf(TripSegment("leg-$start", a, b, start, start + 600_000, LineType.BUS, "123",
            delay = DelayInfo(cancelled = cancelled))), "TEST", fetched, refreshToken = token)

    /** Every request fails (radio gap, timeout or server error); counts the attempts. */
    private class FailingProvider(private val error: () -> Exception = { IOException("offline") }) : TransitRepository {
        override val name = "TEST"
        var attempts = 0
        override suspend fun journeyPage(query: JourneyQuery, cursor: String?): JourneyPage { attempts++; throw error() }
        override suspend fun refresh(trip: Trip): Trip { attempts++; throw error() }
        override suspend fun locations(query: String) = emptyList<Location>()
        override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int) = emptyList<Location>()
        override suspend fun departures(stop: Location) = emptyList<Departure>()
    }

    /** Confirms only the bars in [confirm] with a new stand; the others fail. */
    private class PartialProvider(private val confirm: Set<String>, private val at: Long) : TransitRepository {
        override val name = "TEST"
        override suspend fun journeyPage(query: JourneyQuery, cursor: String?): JourneyPage = throw IOException("timeout")
        override suspend fun refresh(trip: Trip): Trip =
            if (trip.id in confirm) trip.copy(fetchedAt = at) else throw java.net.SocketTimeoutException("timeout")
        override suspend fun locations(query: String) = emptyList<Location>()
        override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int) = emptyList<Location>()
        override suspend fun departures(stop: Location) = emptyList<Departure>()
    }

    // K01 ------------------------------------------------------------------------------------------

    @Test fun twoFailedRefreshesKeepTheStandAndTheOriginalExpiry() = runTest {
        val seed = (1..3).map { trip(standAt + it * 900_000L) }
        var clock = standAt + 8 * 60_000L // 10:38
        val cache = JourneySearchCache(now = { clock })
        val query = JourneyQuery(a, b, standAt)
        val settings = AppSettings()
        // The snapshot was confirmed at 10:30.
        val saved = JourneySearchCache(now = { standAt }).put(query, settings, false, true,
            JourneyWindow(seed, standAt, standAt + JOURNEY_WINDOW_MILLIS, 1f, 1, true, true))!!
        assertEquals(standAt, saved.savedAt)
        cache.put(query, settings, false, true, saved.window(), retainSavedAt = saved.savedAt)
        for (attemptAt in listOf(standAt + 8 * 60_000L, standAt + 15 * 60_000L)) { // 10:38 and 10:45
            clock = attemptAt
            val provider = FailingProvider()
            val pager = JourneyBatchPager(provider, query.copy(time = attemptAt), cache.entries.value.single().trips, clock = { clock })
            val window = pager.initial()
            val refresh = pager.refreshSeed(attemptAt) { _, _ -> }
            assertNotNull(window.problem)
            assertEquals(3, refresh.failures)
            assertEquals(0, refresh.confirmed)
            assertNotNull(pager.lastFailure)
            assertNull("no answer arrived, so nothing was confirmed", pager.confirmedAt)
            assertEquals(seed.map { it.id }.toSet(), pager.unconfirmedIds(attemptAt))
            // The ViewModel keeps the stand of unconfirmed bars (persistWindow).
            val entry = cache.put(query, settings, false, true, pager.snapshot(), cache.entries.value.single().id,
                retainSavedAt = standAt, attemptFailed = true)!!
            assertEquals("Stand bleibt 10:30", standAt, entry.savedAt)
            assertEquals("ursprüngliche Ablaufzeit", standAt + JOURNEY_CACHE_TTL, entry.expiresAt)
            assertTrue(entry.lastAttemptFailed)
            assertEquals(attemptAt, entry.lastAttemptAt)
            assertTrue("bars keep their confirmed stand", entry.trips.all { it.fetchedAt == standAt })
        }
        // The snapshot ends at its original expiry, not later.
        clock = standAt + JOURNEY_CACHE_TTL - 1
        assertNotNull(cache.find(query, settings, false, true))
        clock = standAt + JOURNEY_CACHE_TTL
        assertNull(cache.find(query, settings, false, true))
    }

    @Test fun aPartialAnswerConfirmsOnlyTheBarsItContains() = runTest {
        val seed = (1..3).map { trip(standAt + it * 900_000L) }
        val refreshAt = standAt + 8 * 60_000L
        val confirmed = setOf(seed[0].id, seed[2].id)
        val pager = JourneyBatchPager(PartialProvider(confirmed, refreshAt), JourneyQuery(a, b, refreshAt), seed, clock = { refreshAt })
        pager.initial()
        val result = pager.refreshSeed(refreshAt) { _, _ -> }
        assertEquals(2, result.confirmed)
        assertEquals(1, result.failures)
        assertEquals(setOf(seed[1].id), pager.unconfirmedIds(refreshAt))
        val rows = pager.snapshot().trips.associateBy { it.id }
        assertEquals(refreshAt, rows.getValue(seed[0].id).fetchedAt)
        assertEquals(standAt, rows.getValue(seed[1].id).fetchedAt)
        assertEquals(refreshAt, rows.getValue(seed[2].id).fetchedAt)
        // A retained stand never moves forward: the entry keeps 10:30 until every bar is confirmed.
        val clock = refreshAt
        val cache = JourneySearchCache(now = { clock })
        val entry = cache.put(JourneyQuery(a, b, standAt), AppSettings(), false, true, pager.snapshot(), retainSavedAt = standAt)!!
        assertEquals(standAt, entry.savedAt)
    }

    @Test fun aRetainedStandNeverExtendsAnOlderSnapshot() {
        var clock = standAt
        val cache = JourneySearchCache(now = { clock })
        val query = JourneyQuery(a, b, standAt)
        val window = JourneyWindow(listOf(trip(standAt + 900_000)), standAt, standAt + 3_600_000, 1f, 1, true, true)
        val first = cache.put(query, AppSettings(), false, true, window)!!
        clock = standAt + 600_000
        val later = cache.put(query, AppSettings(), false, true, window, first.id, retainSavedAt = standAt + 300_000)!!
        assertEquals(standAt, later.savedAt)
        clock = standAt + 900_000
        val confirmed = cache.put(query, AppSettings(), false, true, window, first.id)!!
        assertEquals("a confirmed answer moves the stand", standAt + 900_000, confirmed.savedAt)
    }

    @Test fun failuresAreClassifiedForTheMatchingStep() {
        assertEquals(FailureKind.OFFLINE, transitFailure(java.net.UnknownHostException("host")).kind)
        assertEquals(FailureKind.TIMEOUT, transitFailure(java.net.SocketTimeoutException("timeout")).kind)
        assertEquals(FailureKind.RATE_LIMITED, transitFailure(TransitException("429", FailureKind.RATE_LIMITED, standAt + 30_000)).kind)
        assertEquals(FailureKind.UNAVAILABLE, httpFailureKind(503))
        assertEquals(FailureKind.UNAVAILABLE, httpFailureKind(500))
        assertEquals(FailureKind.RATE_LIMITED, httpFailureKind(429))
        assertEquals(FailureKind.OFFLINE, transportFailureKind(IOException("reset"), online = false))
        assertEquals(30L, TransitFailure(FailureKind.RATE_LIMITED, retryAt = standAt + 30_000).waitSeconds(standAt))
        assertNull(TransitFailure(FailureKind.RATE_LIMITED, retryAt = standAt).waitSeconds(standAt + 1))
        assertEquals(FailureKind.TIMEOUT, transitFailure(IOException("wrapped", java.net.SocketTimeoutException())).kind)
    }

    // K02 ------------------------------------------------------------------------------------------

    private fun damagedStore(dir: File, gate: StoreRecoveryGate, scope: CoroutineScope): Pair<File, LocationRepository> {
        val file = File(dir, "openv-v1.json")
        file.writeText("""{"favorites":[{"id":"x","label":"Zuhause","location":{"name":"Bahnhof""") // cut off
        val store = DataStoreFactory.create(StateSerializer, ReplaceFileCorruptionHandler { gate.replacementFor(it) },
            scope = scope) { file }
        return file to LocationRepository(store)
    }

    @Test fun aDamagedStoreIsKeptUntilAConfirmedImportReplacesIt() = runBlocking {
        val dir = kotlin.io.path.createTempDirectory("k02").toFile()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val gate = StoreRecoveryGate()
            val (file, repo) = damagedStore(dir, gate, scope)
            val original = file.readBytes()
            // Collectors wait instead of ending; the problem is published for the recovery screen.
            val collected = async { withTimeout(10_000) { repo.state.first() } }
            withTimeout(5_000) { while (repo.storageProblem.value == null) delay(10) }
            assertTrue(repo.storageProblem.value is androidx.datastore.core.CorruptionException)
            val recovery = LocalDataRecovery(file, File(dir, "recovery"), gate, repo)
            val copy = recovery.preserveDamaged()!!
            assertArrayEquals(original, copy.readBytes())
            assertEquals("one copy per distinct content", copy, recovery.preserveDamaged())
            // "Erneut laden" reads again and changes nothing; a cancelled picker changes nothing either.
            recovery.retry()
            delay(200)
            assertNotNull(repo.storageProblem.value)
            assertArrayEquals(original, file.readBytes())
            // A confirmed import: the copy exists, the store is replaced and the screens read it.
            val imported = StoredState(favorites = listOf(Favorite("f", "Arbeit", b)), onboardingVersion = 1)
            assertTrue(recovery.prepareReplacement(imported))
            repo.replaceFromBackup(imported, "token")
            repo.reload()
            val state = collected.await()
            assertEquals(listOf("Arbeit"), state.favorites.map { it.label })
            assertNull(repo.storageProblem.value)
            assertArrayEquals("the damaged data stay preserved", original, recovery.copies().single().readBytes())
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun startingEmptyNeedsThePreservedCopy() = runBlocking {
        val dir = kotlin.io.path.createTempDirectory("k02e").toFile()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val gate = StoreRecoveryGate()
            val (file, repo) = damagedStore(dir, gate, scope)
            val waiting = async { withTimeout(10_000) { repo.state.first() } }
            withTimeout(5_000) { while (repo.storageProblem.value == null) delay(10) }
            // Without a writable copy location the empty start is refused and the file stays.
            val blocked = LocalDataRecovery(file, File(file, "not-a-dir"), gate, repo)
            assertFalse(blocked.startEmpty())
            assertNotNull(repo.storageProblem.value)
            val recovery = LocalDataRecovery(file, File(dir, "recovery"), gate, repo)
            assertTrue(recovery.startEmpty())
            val state = waiting.await()
            assertTrue(state.favorites.isEmpty())
            assertEquals("a fresh store starts the setup again", 0, state.onboardingVersion)
            assertEquals(1, recovery.copies().size)
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun withoutADecisionTheGateRefusesAndConsumesADecisionOnce() {
        val gate = StoreRecoveryGate()
        val error = androidx.datastore.core.CorruptionException("broken")
        assertSame(error, runCatching { gate.replacementFor(error) }.exceptionOrNull())
        gate.offer(StoredState(onboardingVersion = 1))
        assertEquals(1, gate.replacementFor(error).onboardingVersion)
        assertSame(error, runCatching { gate.replacementFor(error) }.exceptionOrNull())
    }

    // H09 ------------------------------------------------------------------------------------------

    private val positions = object : CurrentPositionSource {
        override fun hasPermission() = false
        override val lastPresence: DevicePresence? = null
        override suspend fun current(): Location = error("Location was not granted")
    }

    @Test fun knownCancellationsAndEndedJourneysAreNotPinnedButRunningBoardingsAre() {
        val now = standAt
        assertEquals(TripImpossibility.CANCELLED, pinRejection(trip(now + 600_000, cancelled = true), now))
        assertEquals(TripImpossibility.ENDED, pinRejection(trip(now - 3_600_000), now))
        // Departed five minutes ago, still riding: a running boarding may be pinned.
        assertNull(pinRejection(trip(now - 300_000), now))
        val call = Stopover(a, departureTime = now + 600_000, cancelled = true)
        val boarding = trip(now + 600_000).let { t -> t.copy(segments = t.segments.map { it.copy(stops = listOf(call)) }) }
        assertEquals(TripImpossibility.BOARDING_CANCELLED, pinRejection(boarding, now))
    }

    @Test fun aCancelledTripNeverReplacesTheActiveCompanion() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val active = trip(standAt + 600_000)
        val engine = FailingProvider()
        val controller = PinnedJourneyController(context, { engine }, positions, now = { standAt })
        controller.close()
        assertTrue(controller.pin(Favorite("f", "Ziel", b), JourneyQuery(a, b, standAt), active, AppSettings()))
        val id = controller.state.value.session!!.id
        val cancelled = trip(standAt + 1_800_000, cancelled = true)
        assertFalse(controller.pin(Favorite("f", "Ziel", b), JourneyQuery(a, b, standAt), cancelled, AppSettings(),
            expectedCurrentId = id, enforceExpected = true))
        assertEquals(id, controller.state.value.session!!.id)
        assertEquals(active.id, controller.state.value.session!!.trip.id)
        // The same pin requested twice is one companion (idempotent).
        assertTrue(controller.pin(Favorite("f", "Ziel", b), JourneyQuery(a, b, standAt), active, AppSettings(),
            expectedCurrentId = id, enforceExpected = true))
        assertEquals(id, controller.state.value.session!!.id)
        controller.close()
    }

    @Test fun aPinIsDurableAsSoonAsTheSaveReturns() = runTest {
        // MainActivity starts this save with the tap; the morph only decorates it. Whatever happens
        // to the animation afterwards (app switch after 100, 400 or 800 ms), the pin is on disk.
        val context = RuntimeEnvironment.getApplication()
        val engine = FailingProvider()
        val controller = PinnedJourneyController(context, { engine }, positions, now = { standAt })
        controller.close()
        assertTrue(controller.pin(Favorite("f", "Ziel", b), JourneyQuery(a, b, standAt), trip(standAt + 600_000), AppSettings()))
        val afterProcessDeath = PinnedJourneyController(context, { engine }, positions, now = { standAt })
        afterProcessDeath.restore()
        assertEquals(controller.state.value.session!!.id, afterProcessDeath.state.value.session?.id)
        afterProcessDeath.close()
    }
}
