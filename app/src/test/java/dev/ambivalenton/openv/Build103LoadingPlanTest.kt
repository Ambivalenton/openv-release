// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Build 103 · the chart's loading plan, walked through with a simulated service: one departure every
 * 20 minutes, 94 minutes on the way, 800 ms per request (virtual time). Measures requests and waiting
 * time and checks that the bars form one gap-free row in both search modes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class Build103LoadingPlanTest {
    private val day = 1_795_651_200_000L // a Wednesday, 00:00 UTC
    private val minute = 60_000L
    private val a = Location("Start", "a", 51.0, 7.0, "TEST")
    private val b = Location("Ziel", "b", 51.1, 7.1, "TEST")
    private val duration = 94 * minute
    private val cadence = 20 * minute
    private val departures = (0 until 60).map { day + 5 * 60 * minute + 10 * minute + it * cadence }
    private fun trip(departure: Long) = Trip("t$departure", listOf(TripSegment("s$departure", a, b, departure, departure + duration,
        LineType.BUS, "U49")), "TEST", day)

    private inner class Line(private val latency: Long = 800) : TransitRepository {
        var requests = 0
        override val name = "TEST"
        override suspend fun journeyPage(query: JourneyQuery, cursor: String?): JourneyPage {
            requests++; delay(latency)
            val found = if (query.timeMode == TimeMode.DEPARTURE) departures.filter { it >= query.time }.take(query.pageSize)
                else departures.filter { it + duration <= query.time }.takeLast(query.pageSize)
            return JourneyPage(found.map(::trip))
        }
        override suspend fun refresh(trip: Trip) = trip
        override suspend fun locations(query: String) = emptyList<Location>()
        override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int) = emptyList<Location>()
        override suspend fun departures(stop: Location) = emptyList<Departure>()
    }

    data class Run(val prefetchMillis: Long, val firstBarsMillis: Long, val completeMillis: Long, val requestsAfterTap: Int,
        val departures: List<Long>)

    /** As the search runs it: pre-search while the destination is chosen, then "Suchen". */
    private suspend fun TestScope.search(mode: TimeMode, time: Long, preSearch: Boolean = true): Run {
        val line = Line()
        val query = JourneyQuery(a, b, time, mode)
        val start = testScheduler.currentTime
        val seed = if (preSearch) line.prefetchFirstConnections(query) else emptyList()
        val prefetch = testScheduler.currentTime - start
        val before = line.requests
        val tap = testScheduler.currentTime
        val pager = JourneyBatchPager(line, query, seed, seedConfirmedAt = if (seed.isEmpty()) null else tap)
        if (!(seed.isNotEmpty() && pager.primaryCount() > 0)) pager.initial()
        val first = testScheduler.currentTime - tap
        pager.completeWaves({ true }) { direction, count -> pager.load(direction, count) }
        val complete = testScheduler.currentTime - tap
        return Run(prefetch, first, complete, line.requests - before, pager.snapshot().trips.map { it.startTime }.sorted())
    }

    private fun assertNoGap(run: Run) {
        run.departures.zipWithNext { x, y -> assertEquals("one row without gaps: ${run.departures.map { (it - day) / minute }}", cadence, y - x) }
    }

    @Test fun anArrivalSearchFormsOneRowWithoutAGap() = runTest {
        val arrival = day + 15 * 60 * minute + 30 * minute
        val run = search(TimeMode.ARRIVAL, arrival)
        println("Build103 arrival: $run")
        assertNoGap(run)
        // The searched side holds the connections arriving in time, the other side the next ones after it.
        assertTrue(run.departures.count { it + duration <= arrival } >= 5)
        assertTrue(run.departures.count { it + duration > arrival } >= 3)
        // The very first later connection is the one departing right after the last one arriving in time.
        val lastInTime = departures.last { it + duration <= arrival }
        assertTrue(lastInTime + cadence in run.departures)
    }

    @Test fun aDepartureSearchLoadsTwoAndOneAheadThenThreeLaterThenThreeEarlier() = runTest {
        val time = day + 15 * 60 * minute + 30 * minute
        val line = Line()
        val query = JourneyQuery(a, b, time, TimeMode.DEPARTURE)
        val seed = line.prefetchFirstConnections(query)
        // While the destination is entered: the nearest earlier one and the next two.
        assertEquals(1, seed.count { it.startTime < time }); assertEquals(2, seed.count { it.startTime >= time })
        val pager = JourneyBatchPager(line, query, seed, seedConfirmedAt = 1L)
        val steps = mutableListOf<Pair<ConnectionDirection, Int>>()
        pager.completeWaves({ true }) { direction, count -> steps += direction to count; pager.load(direction, count) }
        // After "Suchen": three later, then three earlier – nothing more until the chart is scrolled.
        assertEquals(listOf(ConnectionDirection.LATER to 3, ConnectionDirection.EARLIER to 3), steps)
        assertEquals(5, pager.primaryCount()); assertEquals(4, pager.secondaryCount())
        val run = search(TimeMode.DEPARTURE, time)
        println("Build103 departure: $run")
        assertNoGap(run)
        // With the pre-search done, the first bars are there at once and the rest needs two rounds.
        assertEquals(0L, run.firstBarsMillis)
        assertTrue("background ${run.completeMillis} ms", run.completeMillis <= 2 * 800)
        assertTrue("requests after the tap: ${run.requestsAfterTap}", run.requestsAfterTap <= 2)
        // The pre-search itself takes one round: the look back is sized for one connection.
        assertTrue("pre-search ${run.prefetchMillis} ms", run.prefetchMillis <= 800)
    }

    /** Build 103: one message at a time instead of three stacked ones. */
    @Test fun aFailedSearchShowsOneMessageNotThree() {
        val offline = "Dienst nicht erreichbar. Internetverbindung prüfen und erneut versuchen."
        // The search's own error card is shown: no further lines below it.
        assertTrue(dev.ambivalenton.openv.ui.screens.pagingStatusLines(true, offline, offline, true, false, true, false, true).isEmpty())
        // Both directions failed: one shared line whose retry loads both.
        val both = dev.ambivalenton.openv.ui.screens.pagingStatusLines(false, offline, offline, true, false, true, false, true)
        assertEquals(1, both.size); assertTrue(both.single().past && both.single().future)
        assertEquals("Weitere Verbindungen konnten nicht geladen werden.", both.single().text)
        // One direction failed: that one only, without end notes beside it.
        val one = dev.ambivalenton.openv.ui.screens.pagingStatusLines(false, null, offline, false, false, true, false, true)
        assertEquals(1, one.size); assertTrue(one.single().future && !one.single().past)
    }

    @Test fun withoutAPreSearchTheFirstPageStillComesFirst() = runTest {
        val run = search(TimeMode.DEPARTURE, day + 15 * 60 * minute + 30 * minute, preSearch = false)
        println("Build103 no pre-search: $run")
        assertNoGap(run)
        assertEquals(800L, run.firstBarsMillis)
        assertEquals(9, run.departures.size)
    }
}
