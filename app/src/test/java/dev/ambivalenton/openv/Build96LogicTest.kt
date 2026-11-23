// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.components.destinationTones
import dev.ambivalenton.openv.ui.components.rideMeetsWalk
import dev.ambivalenton.openv.ui.components.walkMeetsRide
import dev.ambivalenton.openv.ui.network.twoLines
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

/** The chosen stop as a strict rule with a two-hour fallback, the new preferences and the timeline joins. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class Build96LogicTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L
    private fun stop(name: String, id: String, lon: Double, city: String = "Herne") = Location(name, id, 51.5, lon, "VRR", LocationKind.STOP, city = city)
    private val julia = stop("Herne Zeche Julia", "de:05916:5751", 7.15).copy(dhid = "de:05916:5751")
    private val market = stop("Herne Juliastr./Großmarkt", "m", 7.156)
    private val herne = stop("Herne Bf", "h", 7.217)
    private val dortmund = stop("Dortmund Hbf", "d", 7.459, "Dortmund")
    private fun walk(from: Location, to: Location, start: Long, minutes: Int = 5) = TripSegment("w-${from.id}-$start", from, to, start, start + minutes * min, LineType.WALK, "Fußweg")
    private fun ride(from: Location, to: Location, start: Long, line: String = "390", minutes: Int = 10) =
        TripSegment("$line-${from.id}-$start", from, to, start, start + minutes * min, LineType.BUS, line)
    private fun trip(id: String, vararg legs: TripSegment) = Trip(id, legs.toList(), "VRR", now)

    /** A fake service: from the chosen stop itself only when asked strictly, otherwise also via the next stop. */
    private class Service(val strict: (JourneyQuery) -> List<Trip>, val relaxed: (JourneyQuery) -> List<Trip>) : TransitRepository {
        override val name = "TEST"
        val asked = mutableListOf<JourneyQuery>()
        override suspend fun journeyPage(query: JourneyQuery, cursor: String?): JourneyPage {
            asked += query
            return JourneyPage(if (query.stopOnly) strict(query) else relaxed(query))
        }
        override suspend fun refresh(trip: Trip) = trip
        override suspend fun locations(query: String) = emptyList<Location>()
        override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int) = emptyList<Location>()
        override suspend fun departures(stop: Location) = emptyList<Departure>()
    }

    @Test fun aChosenStopIsSearchedStrictlyAndOnlyFallsBackWithoutAJourneyWithinTwoHours() = runBlocking {
        val direct = trip("direct", ride(julia, herne, now + 30 * min), ride(herne, dortmund, now + 45 * min, "RE3", 20))
        val viaMarket = trip("market", walk(julia, market, now + 5 * min), ride(market, herne, now + 12 * min, "312"))
        val service = Service({ listOf(direct) }, { listOf(viaMarket, direct) })
        val page = RoutingPreferencesRepository(service, AppSettings()).journeyPage(JourneyQuery(julia, dortmund, now), null)
        assertEquals(listOf("direct"), page.trips.map { it.id })
        assertTrue("asked strictly: no walk to another stop", service.asked.single().stopOnly)
        // Nothing from the stop within two hours (a moved stop): alternatives such as a walk are allowed.
        val late = trip("late", ride(julia, herne, now + 150 * min))
        val moved = Service({ listOf(late) }, { listOf(viaMarket) })
        val fallback = RoutingPreferencesRepository(moved, AppSettings()).journeyPage(JourneyQuery(julia, dortmund, now), null)
        assertEquals(listOf("market"), fallback.trips.map { it.id })
        assertEquals(listOf(true, false), moved.asked.map { it.stopOnly })
        // Addresses at both ends keep the ordinary search.
        val address = Location("Herne, Bahnhofstr. 3", latitude = 51.5, longitude = 7.2, kind = LocationKind.ADDRESS)
        val target = Location("Dortmund, Königswall 1", latitude = 51.51, longitude = 7.45, kind = LocationKind.ADDRESS)
        val plain = Service({ emptyList() }, { listOf(viaMarket) })
        RoutingPreferencesRepository(plain, AppSettings()).journeyPage(JourneyQuery(address, target, now), null)
        assertEquals(listOf(false), plain.asked.map { it.stopOnly })
    }

    @Test fun theStrictRuleKeepsOnlyJourneysByVehicleFromTheStop() {
        val query = JourneyQuery(julia, dortmund, now)
        assertFalse("walk to another stop", strictFits(trip("m", walk(julia, market, now), ride(market, herne, now + 7 * min)), query))
        assertFalse("walking only", strictFits(trip("w", walk(julia, dortmund, now, 30)), query))
        // No transfer limit any more.
        assertTrue(strictFits(trip("2", ride(julia, herne, now), ride(herne, dortmund, now + 15 * min, "RE3")), query))
        assertTrue(strictTripWithinTwoHours(trip("s", ride(julia, herne, now + 119 * min)), query))
        assertFalse(strictTripWithinTwoHours(trip("l", ride(julia, herne, now + 121 * min)), query))
        // Arrival searches look at the two hours before.
        val arrival = query.copy(timeMode = TimeMode.ARRIVAL, time = now + 180 * min)
        assertTrue(strictTripWithinTwoHours(trip("a", ride(julia, herne, now + 100 * min)), arrival))
    }

    @Test fun theNewPreferencesChooseAsDescribed() {
        val query = JourneyQuery(julia, dortmund, now)
        val walkFirst = trip("walk", walk(julia, market, now + 2 * min, 6), ride(market, dortmund, now + 9 * min, "312", 30))
        val laterNoWalk = trip("calm", ride(julia, dortmund, now + 40 * min, "390", 30))
        // "So wenig wie möglich laufen": the journey without a walk, although it departs much later.
        val minimal = OverviewRoutingPreferences().with(walking = WalkingPreference.MINIMAL)
        assertEquals("calm", SmartRouteSelector.select(listOf(walkFirst, laterNoWalk), query, minimal)?.id)
        // Without a preference the next journey.
        assertEquals("walk", SmartRouteSelector.select(listOf(walkFirst, laterNoWalk), query)?.id)
        // Former switches keep their meaning (As transfer weight instead of a priority).
        assertEquals(TransferPreference.FEWEST, OverviewRoutingPreferences(fewerTransfers = true).effectiveTransfers)
        assertEquals(TransferPreference.FEWEST, OverviewRoutingPreferences(maxTransfers = 0).effectiveTransfers)
        assertEquals(TransferPreference.FEWER, OverviewRoutingPreferences(maxTransfers = 2).effectiveTransfers)
        assertEquals(WalkingPreference.LESS, OverviewRoutingPreferences(lessWalking = true).effectiveWalking)
        assertEquals(RoutePriority.ARRIVAL, OverviewRoutingPreferences(faster = true).effectivePriority)
        assertFalse(OverviewRoutingPreferences().enabled)
    }

    @Test fun theLeadTimeMayBeNegative() {
        assertEquals(now - 15 * min, overviewDepartureTime(now, -20f))
        assertEquals(now - 5 * min, overviewDepartureTime(now, -5f))
        assertEquals(now + 30 * min, overviewDepartureTime(now, 45f))
    }

    @Test fun aWalkToTheBoardingStopJoinsItsDepartureWithinTwoMinutes() {
        val arrive = walk(market, herne, now, 4)
        assertTrue(walkMeetsRide(arrive, ride(herne, dortmund, now + 6 * min)))
        assertFalse("three minutes", walkMeetsRide(arrive, ride(herne, dortmund, now + 7 * min)))
        assertFalse("another stop", walkMeetsRide(arrive, ride(julia, dortmund, now + 5 * min)))
        val bus = ride(julia, herne, now)
        assertTrue(rideMeetsWalk(bus, walk(herne, market, now + 11 * min)))
        assertFalse(rideMeetsWalk(bus, walk(herne, market, now + 13 * min)))
    }

    // Build 101: the accent stays on Übersicht, Suchen and Abfahrten change colour clearly.
    @Test fun capsuleColoursChangeClearlyAndStartFromTheThemesOwnColour() {
        val base = Color(0xFFF2F2F2)
        val blues = listOf(Color(0xFF3355AA), Color(0xFF3456AB), Color(0xFF3355A9))
        val tones = destinationTones(3, base, DesignLanguage.MATERIAL, false, blues, blues)
        assertEquals(3, tones.map { it.fill }.distinct().size)
        val hues = tones.map { Hct.fromInt(it.fill.toArgb()).hue }
        val source = Hct.fromInt(blues[0].toArgb()).hue
        fun distance(a: Double, b: Double) = min(abs(a - b), 360 - abs(a - b))
        assertTrue("Übersicht keeps the theme's blue: ${hues[0]}", distance(hues[0], source) <= 12.0)
        hues.zipWithNext().forEach { (a, b) -> assertTrue("a big change from page to page: $a -> $b", distance(a, b) >= 60.0) }
        tones.forEach { tone ->
            // Clearly visible on the bar.
            assertTrue(abs(Hct.fromInt(tone.fill.toArgb()).tone - Hct.fromInt(base.toArgb()).tone) >= 9.0)
        }
    }

    @Test fun editBarLabelsBreakIntoTwoLines() {
        assertEquals("Favoriten-\nEinstellungen", twoLines("Favoriten-Einstellungen"))
        assertEquals("Favorit\nhinzufügen", twoLines("Favorit hinzufügen"))
        assertEquals("Add\nfavourite", twoLines("Add favourite"))
    }
}
