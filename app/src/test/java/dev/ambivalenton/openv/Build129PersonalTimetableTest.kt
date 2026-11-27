// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import java.io.File
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.time.Duration
import dev.ambivalenton.openv.ui.AppViewModel

/**
 * Build 129 · the local timetable covers only the person's own stations: departures there and the
 * direct services between them work without a connection; the whole regional table is not kept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class)
class Build129PersonalTimetableTest {
    private val herne = Location("Herne Bf", "de:05916:7501", 51.5432, 7.2193, "VRR", LocationKind.STOP)
    private val wanne = Location("Wanne-Eickel Hbf", "de:05916:7590", 51.5317, 7.1659, "VRR", LocationKind.STOP)
    private val dortmund = Location("Dortmund Hbf", "de:05913:131", 51.5177, 7.4590, "VRR", LocationKind.STOP)
    private val krefeld = Location("Krefeld Hbf", "de:05114:21002", 51.3268, 6.5675, "VRR", LocationKind.STOP)
    private fun time(date: String, clock: String = "08:00") =
        ZonedDateTime.parse("${date}T$clock:00+01:00[Europe/Berlin]").toInstant().toEpochMilli()
    private val app get() = RuntimeEnvironment.getApplication()

    @Before @After fun reset() = runBlocking {
        val locations = (app as OpenVApp).locations
        val old = locations.state.first()
        old.favorites.forEach { locations.deleteFavorite(it.id) }
        old.boardStops.forEach { locations.removeBoardStop(it) }
        locations.selectBoardStop(null)
        locations.saveDepartureBoard(DepartureBoardState())
        RegionalTimetable(app).prepare(emptyList())
    }

    @Test fun theDeviceKeepsOnlyTheOwnStations() = runBlocking {
        // An installation of 1.2.0.8 kept the whole regional table unpacked (about 29 MB).
        val old = File(app.noBackupFilesDir, "regional-timetable").apply { mkdirs() }
        File(old, "stops-old.sqlite").writeText("x")
        val timetable = RegionalTimetable(app)
        timetable.prepare(listOf(herne.id!!, wanne.id!!))
        assertFalse(old.exists())
        val extract = File(app.noBackupFilesDir, "personal-timetable/timetable.sqlite")
        assertTrue("${extract.length()} bytes", extract.length() in 1..2L * 1024 * 1024)
        assertTrue("the unpacked full table is gone", File(app.cacheDir, "timetable-source").listFiles().isNullOrEmpty())
        assertEquals(listOf(herne.id, wanne.id), timetable.places())

        val query = JourneyQuery(herne, wanne, time("2026-11-27"))
        val direct = timetable.direct(query)
        assertTrue(direct.size >= 3)
        assertTrue("intermediate stops stay for a direct service", direct.all { it.segments.single().stops.size >= 2 })
        // A service through two own stations keeps all its stops (Dortmund lies on such lines), but only
        // the own stations are complete: nothing is offered for another one.
        assertTrue(timetable.direct(query.copy(destination = dortmund)).isEmpty())
        assertTrue(timetable.direct(query.copy(destination = krefeld)).isEmpty())

        val board = timetable.departures(herne, query.time, 10)
        assertEquals(10, board.size)
        assertEquals(board.sortedBy { it.time }, board)
        assertTrue(board.all { it.time >= query.time && it.delay.departureSeconds == null && !it.delay.cancelled })
        assertTrue(board.all { it.stop == herne && it.destination.isNotBlank() })
        assertTrue("departures only at own stations", timetable.departures(dortmund, query.time, 10).isEmpty())
        // After midnight the board continues with the services of the next day.
        val late = timetable.departures(herne, time("2026-11-27", "23:50"), 6)
        assertTrue(late.isNotEmpty() && late.all { it.time >= time("2026-11-27", "23:50") })

        // The same stations in another order (every search changes it) keep the extract.
        val stamp = extract.lastModified()
        timetable.prepare(listOf(wanne.id!!, herne.id!!))
        assertEquals(stamp, extract.lastModified())
        // One station less: its services leave the device.
        timetable.prepare(listOf(herne.id!!))
        assertTrue(timetable.direct(query).isEmpty())
        assertTrue(timetable.departures(herne, query.time, 4).isNotEmpty())
        timetable.prepare(emptyList())
        assertFalse(extract.exists())
        assertTrue(timetable.departures(herne, query.time, 4).isEmpty())
    }

    @Test fun ownStationsComeFromSavedPlacesTheBoardAndRecentSearches() {
        val now = time("2026-11-27")
        val address = Location("Zuhause", null, 51.5, 7.2, kind = LocationKind.ADDRESS)
        val stale = SearchHistory(JourneyQuery(dortmund, Location("Bochum Hbf", "de:05911:5194"), now), now - 40L * 24 * 3600_000, 9)
        val frequent = SearchHistory(JourneyQuery(wanne, Location("Gelsenkirchen Hbf", "de:05513:5613"), now), now - 3600_000, 5)
        val once = SearchHistory(JourneyQuery(wanne, Location("Essen Hbf", "de:05113:9289"), now), now - 60_000, 1)
        val state = StoredState(favorites = listOf(Favorite(label = "Bahnhof", location = herne), Favorite(label = "Zuhause", location = address)),
            history = listOf(once, stale, frequent))
        assertEquals(listOf("de:05916:7501", "de:05916:7590", "de:05513:5613", "de:05113:9289"), personalTimetablePlaces(state, now))
        val many = StoredState(history = (1..40).map { i ->
            SearchHistory(JourneyQuery(herne, Location("Halt $i", "de:05913:${1000 + i}"), now), now - i * 60_000L) })
        assertEquals(PERSONAL_TIMETABLE_PLACES, personalTimetablePlaces(many, now).size)
    }

    @Test fun withoutALiveAnswerTheLastLiveRowsContinueWithTheTimetable() {
        val now = time("2026-11-27")
        fun row(id: String, line: String, minutes: Int, delay: Int? = null) = Departure(id, herne, line, LineType.SUBURBAN, "Dortmund Hbf",
            now + minutes * 60_000L, now + minutes * 60_000L + (delay ?: 0) * 60_000L, DelayInfo(delay?.times(60)))
        val live = listOf(row("old", "S2", -5, 0), row("l1", "S2", 5, 2), row("l2", "RE3", 10, 0))
        val planned = listOf(row("gtfs-1", "S2", 5), row("gtfs-2", "S 2", 7), row("gtfs-3", "RE 3", 10), row("gtfs-4", "S2", 15), row("gtfs-5", "RE3", 20))
        val merged = mergeScheduledDepartures(live, planned, now)
        assertEquals(listOf("l1", "l2", "gtfs-4", "gtfs-5"), merged.map { it.id })
        assertEquals(listOf("gtfs-1", "gtfs-2", "gtfs-3", "gtfs-4", "gtfs-5"), mergeScheduledDepartures(emptyList(), planned, now).map { it.id })
    }

    @Test fun aFailedBoardRequestShowsTheOwnTimetable() = runBlocking {
        val now = time("2026-11-27")
        val locations = (app as OpenVApp).locations
        locations.addBoardStop(herne)
        locations.selectBoardStop(herne)
        val timetable = RegionalTimetable(app)
        timetable.prepare(listOf(herne.id!!))
        val offline = object : TransitRepository {
            override val name = "VRR / EFA · NRW"
            override suspend fun locations(query: String) = listOf(herne)
            override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int) = listOf(herne)
            override suspend fun departures(stop: Location): List<Departure> = throw TransitException("Keine Verbindung")
            override suspend fun journeyPage(query: JourneyQuery, cursor: String?) = error("Unused")
            override suspend fun refresh(trip: Trip) = trip
        }
        DepartureBoardRepository(locations, { offline }, { false }, timetable).refresh(force = true, now = now)
        val board = locations.state.first().departureBoard
        assertNotNull(board.error)
        assertTrue(board.scheduled)
        assertEquals(herne, board.stop)
        assertTrue(board.departures.size >= 8)
        assertTrue(board.departures.all { it.id.startsWith("gtfs-") && it.time >= now - 60_000 && it.delay.departureSeconds == null })
    }

    /** The Abfahrten page without a connection: the app builds the extract for the saved station
     * itself, shows its planned departures at once and keeps them when the live request fails. The
     * timetable service is unreachable here on purpose (CI machines do have internet). */
    @Test fun theDeparturesPageShowsTheOwnTimetableOffline() {
        val offline = okhttp3.OkHttpClient.Builder().addInterceptor { throw java.net.UnknownHostException("offline") }.build()
        val locations = (app as OpenVApp).locations
        runBlocking { locations.addBoardStop(herne) }
        val holder = androidx.lifecycle.ViewModelStore()
        val vm = AppViewModel(locations, (app as OpenVApp).deviceLocation, TransitProviderFactory(NetworkClient(offline) { false }),
            (app as OpenVApp).koin.get(), (app as OpenVApp).koin.get(), dev.ambivalenton.openv.data.preferences.UserPreferencesRepository(locations),
            timetable = RegionalTimetable(app))
        holder.put("vm", vm)
        try {
            val extract = File(app.noBackupFilesDir, "personal-timetable/timetable.sqlite")
            val deadline = System.currentTimeMillis() + 20_000
            while (!extract.isFile) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
                assertTrue("the extract for the saved station", System.currentTimeMillis() < deadline)
                Thread.sleep(20)
            }
            vm.loadBoard("herne", "Herne Bf", herne)
            while (vm.state.value.boards["herne"]?.rows?.let { it.loading || it.value == null } != false) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                assertTrue("board answered", System.currentTimeMillis() < deadline)
                Thread.sleep(20)
            }
            val board = vm.state.value.boards.getValue("herne")
            assertNotNull(board.rows.error)
            assertTrue(board.scheduled)
            assertTrue(board.rows.value.orEmpty().size >= 8)
            assertTrue(board.rows.value.orEmpty().all { it.id.startsWith("gtfs-") && it.delay.departureSeconds == null })
        } finally { holder.clear() }
    }
}
