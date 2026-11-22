// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

/** The weighing of connections – perceived time, transfer risk, fuzzy dominance, extra goals. */
class Build97LogicTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L
    private val home = Location("Herne, Bahnhofstr. 3", latitude = 51.54, longitude = 7.22, kind = LocationKind.ADDRESS)
    private val work = Location("Bochum, Universitätsstr. 150", latitude = 51.44, longitude = 7.26, kind = LocationKind.ADDRESS)
    private val query = JourneyQuery(home, work, now, products = LineType.entries.toSet() - LineType.WALK)
    private fun stop(n: Int) = Location("Halt $n", "s$n", 51.5 + n / 1000.0, 7.2, "VRR", LocationKind.STOP)
    private fun prefs(transfers: TransferPreference = TransferPreference.NORMAL, walking: WalkingPreference = WalkingPreference.NORMAL,
        buffer: TransferBuffer = TransferBuffer.NORMAL, priority: RoutePriority = RoutePriority.NEXT) =
        OverviewRoutingPreferences().with(priority = priority, walking = walking, transfers = transfers, buffer = buffer)

    /** [rides] consecutive rides of [rideMinutes], [gap] minutes apart, after an optional walk. */
    private fun chain(id: String, start: Long, rides: Int, rideMinutes: Int, gap: Int, walk: Int = 0,
        type: LineType = LineType.BUS): Trip {
        val legs = mutableListOf<TripSegment>()
        var t = start
        if (walk > 0) { legs += TripSegment("$id-walk", home, stop(0), t, t + walk * min, LineType.WALK, "Fußweg"); t += walk * min }
        repeat(rides) { i ->
            legs += TripSegment("$id-$i", stop(i), stop(i + 1), t, t + rideMinutes * min, type, "${100 + i}")
            t += rideMinutes * min + gap * min
        }
        return Trip(id, legs, "VRR", now)
    }

    @Test fun tenTransfersGiveWayToAComparableJourneyWithTwoEvenForAnyNumberOfTransfers() {
        val many = chain("many", now, rides = 11, rideMinutes = 5, gap = 1)          // 10 transfers, 0:00–1:05
        val few = chain("few", now + 5 * min, rides = 3, rideMinutes = 18, gap = 4)   // 2 transfers, 0:05–1:07
        assertEquals(setOf("many"), JourneyRanking.hiddenConnections(listOf(many, few), query, prefs(TransferPreference.ANY), now))
        // A clearly faster journey with more transfers stays: arriving 18 minutes earlier is a real choice.
        val fast = chain("fast", now, rides = 3, rideMinutes = 8, gap = 5)            // 2 transfers, 0:00–0:34
        val direct = chain("direct", now + 2 * min, rides = 1, rideMinutes = 50, gap = 0)
        assertEquals(emptySet<String>(), JourneyRanking.hiddenConnections(listOf(fast, direct), query, prefs(), now))
    }

    @Test fun aFortyMinuteWalkGivesWayToAComparableRideEvenWhenWalkingIsNoProblem() {
        val walker = chain("walker", now, rides = 1, rideMinutes = 10, gap = 0, walk = 40)   // 0:00–0:50
        val rider = chain("rider", now + 3 * min, rides = 1, rideMinutes = 50, gap = 0, walk = 2) // 0:03–0:55
        assertEquals(setOf("walker"), JourneyRanking.hiddenConnections(listOf(walker, rider), query, prefs(walking = WalkingPreference.RELAXED), now))
        // If the walk saves a quarter of an hour, it stays.
        val slow = chain("slow", now + 3 * min, rides = 1, rideMinutes = 62, gap = 0, walk = 2)  // arrives 0:67
        assertEquals(emptySet<String>(), JourneyRanking.hiddenConnections(listOf(walker, slow), query, prefs(walking = WalkingPreference.RELAXED), now))
    }

    @Test fun thePreferencesDecideAboutCloseCases() {
        val change = chain("change", now, rides = 2, rideMinutes = 12, gap = 6)      // 1 transfer, 0:00–0:30
        val direct = chain("direct", now + 5 * min, rides = 1, rideMinutes = 29, gap = 0) // 0:05–0:34
        assertEquals("Möglichst wenig umsteigen", setOf("change"),
            JourneyRanking.hiddenConnections(listOf(change, direct), query, prefs(TransferPreference.FEWEST), now))
        assertEquals("Normal keeps both", emptySet<String>(), JourneyRanking.hiddenConnections(listOf(change, direct), query, prefs(), now))
        // Walks without a vehicle, cancelled journeys and journeys with waypoints are never touched.
        val walkOnly = Trip("walk", listOf(TripSegment("w", home, work, now, now + 90 * min, LineType.WALK, "Fußweg", distanceMeters = 7000)), "VRR", now)
        assertEquals(emptySet<String>(), JourneyRanking.hiddenConnections(listOf(walkOnly, direct), query, prefs(), now))
        assertEquals(emptySet<String>(), JourneyRanking.hiddenConnections(listOf(change, direct), query.copy(waypoints = listOf(stop(5))), prefs(TransferPreference.FEWEST), now))
    }

    @Test fun theTransferRiskFollowsBufferKindAndForecast() {
        val tight = chain("tight", now + 60 * min, rides = 2, rideMinutes = 15, gap = 1, type = LineType.REGIONAL)
        val loose = chain("loose", now + 60 * min, rides = 2, rideMinutes = 15, gap = 8, type = LineType.REGIONAL)
        val tightRisk = JourneyRanking.transferRiskMinutes(tight, query, now)
        assertTrue("one minute at a train is risky: $tightRisk", tightRisk in 5.0..20.0)
        assertTrue("eight minutes are safe", JourneyRanking.transferRiskMinutes(loose, query, now) < 1.0)
        // Missing real-time data is not punctuality; a current forecast just before the arrival narrows the spread.
        val leg = tight.segments.first()
        val unknown = JourneyRanking.arrivalSpread(leg, leg.endTime - 5 * min)
        val forecast = JourneyRanking.arrivalSpread(leg.copy(arrivalSource = TimeSource.PROGNOSIS, delay = DelayInfo(arrivalSeconds = 60)), leg.endTime - 5 * min)
        assertTrue("$forecast < $unknown", forecast < unknown)
        assertTrue(JourneyRanking.missProbability(0.0, 2.0) > JourneyRanking.missProbability(5.0, 2.0))
    }

    @Test fun theOverviewWeighsLikeTheSearch() {
        val tight = chain("tight", now, rides = 2, rideMinutes = 14, gap = 1, type = LineType.REGIONAL)   // 0:00–0:29
        val safe = chain("safe", now + 5 * min, rides = 2, rideMinutes = 12, gap = 7, type = LineType.REGIONAL) // 0:05–0:36
        assertEquals("tight", SmartRouteSelector.select(listOf(tight, safe), query, prefs(buffer = TransferBuffer.TIGHT))?.id)
        assertEquals("safe", SmartRouteSelector.select(listOf(tight, safe), query, prefs(buffer = TransferBuffer.SAFE))?.id)
        // "Früh ankommen": a later, faster journey wins; "Bald losfahren" keeps the next one for a small gain.
        val next = chain("next", now, rides = 1, rideMinutes = 40, gap = 0)
        val express = chain("express", now + 6 * min, rides = 1, rideMinutes = 30, gap = 0)
        assertEquals("express", SmartRouteSelector.select(listOf(next, express), query, prefs(priority = RoutePriority.ARRIVAL))?.id)
        assertEquals("next", SmartRouteSelector.select(listOf(next, express), query)?.id)
        // The leeway stays a hard boundary.
        val late = chain("late", now + 20 * min, rides = 1, rideMinutes = 10, gap = 0)
        assertEquals("next", SmartRouteSelector.select(listOf(next, late), query, prefs(priority = RoutePriority.ARRIVAL).copy(toleranceMinutes = 15))?.id)
    }

    /** A fake provider that knows route goals and remembers what it was asked. */
    private class Focused(val answer: (JourneyQuery) -> List<Trip>, val fails: Set<RouteFocus> = emptySet()) : TransitRepository {
        override val name = "TEST"
        override val supportsRouteFocus = true
        val asked: MutableList<RouteFocus> = Collections.synchronizedList(mutableListOf())
        override suspend fun journeyPage(query: JourneyQuery, cursor: String?): JourneyPage {
            asked += query.routeFocus
            if (query.routeFocus in fails) throw TransitException("down")
            return JourneyPage(answer(query))
        }
        override suspend fun refresh(trip: Trip) = trip
        override suspend fun locations(query: String) = emptyList<Location>()
        override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int) = emptyList<Location>()
        override suspend fun departures(stop: Location) = emptyList<Departure>()
    }

    private fun settings(p: OverviewRoutingPreferences) = AppSettings(overview = OverviewSettings(routingPreferences = p))

    @Test fun leaningPreferencesAskTheProviderAgainWithTheirOwnGoal() = runBlocking {
        val usual = chain("usual", now + 2 * min, rides = 3, rideMinutes = 10, gap = 3)
        val calm = chain("calm", now + 4 * min, rides = 1, rideMinutes = 38, gap = 0)
        val service = Focused({ q -> if (q.routeFocus == RouteFocus.FEWER_CHANGES) listOf(calm, usual) else listOf(usual) })
        val page = RoutingPreferencesRepository(service, settings(prefs(TransferPreference.FEWER)), { now }).journeyPage(query, null)
        assertEquals(setOf(RouteFocus.TIME, RouteFocus.FEWER_CHANGES), service.asked.toSet())
        assertEquals("merged without duplicates", listOf("usual", "calm"), page.trips.map { it.id })
        // Ordinary preferences: one request. A failing extra request never costs the ordinary answer.
        val plain = Focused({ listOf(usual) })
        RoutingPreferencesRepository(plain, settings(prefs()), { now }).journeyPage(query, null)
        assertEquals(listOf(RouteFocus.TIME), plain.asked)
        val broken = Focused({ listOf(usual) }, fails = setOf(RouteFocus.LESS_WALKING))
        val kept = RoutingPreferencesRepository(broken, settings(prefs(walking = WalkingPreference.LESS)), { now }).journeyPage(query, null)
        assertEquals(listOf("usual"), kept.trips.map { it.id })
        assertEquals(setOf(RouteFocus.TIME, RouteFocus.LESS_WALKING), broken.asked.toSet())
        // Native pagination (a cursor) asks once.
        val paged = Focused({ listOf(usual) })
        RoutingPreferencesRepository(paged, settings(prefs(TransferPreference.FEWEST)), { now }).journeyPage(query, "next")
        assertEquals(listOf(RouteFocus.TIME), paged.asked)
    }

    @Test fun hiddenConnectionsMoveThePagesButNeverBecomeBars() = runBlocking {
        val many = chain("many", now + 1 * min, rides = 11, rideMinutes = 5, gap = 1)
        val few = chain("few", now + 5 * min, rides = 3, rideMinutes = 18, gap = 4)
        val later = chain("later", now + 20 * min, rides = 1, rideMinutes = 50, gap = 0)
        val service = Focused({ q -> listOf(many, few, later).filter { it.startTime >= q.time - min } })
        val repository = RoutingPreferencesRepository(service, settings(prefs(TransferPreference.ANY)), { now })
        val page = repository.journeyPage(query, null)
        assertEquals(setOf("many"), page.hidden)
        assertEquals(listOf("many", "few", "later"), page.trips.map { it.id })
        val pager = JourneyBatchPager(repository, query, initialCount = 2, clock = { now })
        assertEquals(listOf("few", "later"), pager.initial().trips.map { it.id })
    }

    @Test fun preferencesBelongToTheCacheAndTheVrrRequest() = runBlocking {
        val plain = AppSettings()
        val leaning = settings(prefs(TransferPreference.FEWEST))
        assertNotEquals(journeyCacheProvider(plain), journeyCacheProvider(leaning))
        // The leeway only changes the overview's choice, not the request.
        assertEquals(journeyCacheProvider(plain), journeyCacheProvider(settings(OverviewRoutingPreferences(toleranceMinutes = 25))))
        assertEquals(listOf(RouteFocus.FEWER_CHANGES, RouteFocus.LESS_WALKING),
            routeFocuses(prefs(TransferPreference.FEWER, WalkingPreference.MINIMAL), query))
        assertEquals(emptyList<RouteFocus>(), routeFocuses(prefs(walking = WalkingPreference.LESS), query.copy(ignoreWalking = true)))
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody("""{"trips":[]}""")) }
            val efa = EfaTransitRepository(NetworkClient(OkHttpClient()), server.url("/"))
            assertTrue(efa.supportsRouteFocus)
            efa.journeyPage(query, null)
            assertEquals("LEASTTIME", server.takeRequest().requestUrl!!.queryParameter("routeType"))
            efa.journeyPage(query.copy(routeFocus = RouteFocus.LESS_WALKING), null)
            assertEquals("LEASTWALKING", server.takeRequest().requestUrl!!.queryParameter("routeType"))
        }
    }

    @Test fun formerSettingsKeepTheirMeaning() {
        assertEquals(TransferPreference.FEWEST, OverviewRoutingPreferences(priority = RoutePriority.TRANSFERS).effectiveTransfers)
        assertEquals(RoutePriority.NEXT, OverviewRoutingPreferences(priority = RoutePriority.TRANSFERS).effectivePriority)
        assertEquals(TransferPreference.FEWER, OverviewRoutingPreferences(maxTransfers = 2).effectiveTransfers)
        assertFalse(OverviewRoutingPreferences().enabled)
        assertTrue(prefs(buffer = TransferBuffer.SAFE).enabled)
        // A slider writes the new fields and retires the former limit.
        val moved = OverviewRoutingPreferences(maxTransfers = 0).with(walking = WalkingPreference.RELAXED)
        assertNull(moved.maxTransfers); assertEquals(TransferPreference.FEWEST, moved.transfers)
        // "Beliebig oft" still has a floor of plausibility.
        assertEquals(5.0, JourneyWeights.of(prefs(TransferPreference.ANY)).realism().transfer, 0.0)
    }
}
