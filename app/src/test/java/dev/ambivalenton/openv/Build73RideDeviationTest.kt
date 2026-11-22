// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.JourneyPage
import dev.ambivalenton.openv.data.repository.TransitRepository
import dev.ambivalenton.openv.data.repository.findObservedRide
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * The passenger takes an earlier or a later service than the pinned one. Reported case:
 * an underground train boarded earlier than planned, no GPS in the tunnel, then on foot – the
 * assistant kept asking to board.
 */
class Build73RideDeviationTest {
    // Notices are compared in German; other suites in the same JVM may switch the default locale.
    private var savedLocale: java.util.Locale = java.util.Locale.getDefault()
    @org.junit.Before fun german() { savedLocale = java.util.Locale.getDefault(); java.util.Locale.setDefault(java.util.Locale.GERMANY) }
    @org.junit.After fun restoreLocale() { java.util.Locale.setDefault(savedLocale) }
    private val t0 = 1_800_000_000_000L
    private val minute = 60_000L
    /** Longitude 8.0 + x at latitude 50: 0.01° is about 715 m. */
    private fun point(x: Double, name: String = "Ort") = Location(name = name, latitude = 50.0, longitude = 8.0 + x)
    private val home = point(-.006, "Wohnung")
    private val a = point(0.0, "Hauptbahnhof")
    private val b = point(.015, "Stadtgarten")
    private val c = point(.03, "Museum")
    private val d = point(.045, "Endstation")

    /** U47 a → d, 12 minutes, calls at b (+4 min) and c (+8 min). */
    private fun subway(start: Long, line: String = "U47", realtime: Boolean = true, delay: Long = 0, id: String = "u-$start") =
        TripSegment(id, a, d, start + delay, start + 12 * minute + delay, LineType.SUBWAY, line,
            plannedStartTime = start, plannedEndTime = start + 12 * minute,
            delay = if (realtime) DelayInfo(departureSeconds = (delay / 1000).toInt(), arrivalSeconds = (delay / 1000).toInt()) else DelayInfo(),
            stops = listOf(
                Stopover(b, start + 4 * minute + delay, start + 4 * minute + delay, plannedArrivalTime = start + 4 * minute, plannedDepartureTime = start + 4 * minute),
                Stopover(c, start + 8 * minute + delay, start + 8 * minute + delay, plannedArrivalTime = start + 8 * minute, plannedDepartureTime = start + 8 * minute),
            ),
            realtimeDeparture = realtime, departureSource = if (realtime) TimeSource.PROGNOSIS else TimeSource.PLANNED,
            arrivalSource = if (realtime) TimeSource.PROGNOSIS else TimeSource.PLANNED)

    private val plannedDeparture = t0 + 20 * minute
    private fun pinned(ride: TripSegment = subway(plannedDeparture)): PinnedJourneySession {
        val walk = TripSegment("walk", home, a, ride.plannedStartTime - 6 * minute, ride.plannedStartTime - minute, LineType.WALK, "Fußweg", distanceMeters = 430)
        val trip = Trip("pinned", listOf(walk, ride), "TEST", t0)
        return PinnedJourneySession(favorite = Favorite(label = "Ziel", location = d), query = JourneyQuery(home, d, t0), initialTrip = trip, routing = AppSettings())
    }
    private fun fix(location: Location, at: Long, speed: Float? = null, accuracy: Float = 12f) = DevicePresence(location, at, accuracy, speed)
    private fun step(session: PinnedJourneySession, fix: DevicePresence, observations: List<DevicePresence> = listOf(fix)) =
        advanceAssistantJourney(session, fix, observations, null, fix.capturedAt)

    /** Walk to the underground station, then the tunnel swallows all fixes. */
    private fun atStation(session: PinnedJourneySession = pinned(), at: Long = t0 + 5 * minute): PinnedJourneySession {
        var s = step(session, fix(point(-.003), at - 60_000, 1.3f))
        s = step(s, fix(point(-.0005), at, 1.2f), listOf(fix(point(-.003), at - 60_000, 1.3f), fix(point(-.0005), at, 1.2f)))
        return s
    }

    @Test fun anEarlierUndergroundTrainIsRecognisedAfterTheTunnelEvenOnFoot() {
        var s = atStation()
        assertNotEquals("before boarding the ride is not ridden", true, s.gpsCursor?.departed == true && gpsCursorLeg(s)?.index == 1)
        // Six minutes later at "Museum" (about 2 km along), walking, while the pinned train leaves in 9 minutes.
        val first = fix(c, t0 + 11 * minute, 1.3f)
        s = step(s, first)
        assertEquals(RideDeviationKind.EARLIER, s.rideDeviation?.kind)
        assertEquals("one fix is not enough", 1, s.rideDeviation?.confirmations)
        val second = fix(point(.0302), first.capturedAt + 20_000, 1.2f)
        s = step(s, second, listOf(first, second))
        assertNotNull(confirmedRideDeviation(s, second.capturedAt))
        assertEquals("the ride counts as boarded", 1, gpsCursorLeg(s)?.index)
        assertTrue(s.gpsCursor!!.departed)
        val progress = resolvedJourneyProgress(s, second.capturedAt, second)
        assertNull("no boarding instruction any more", progress.boardingLegId)
        assertEquals("riding the ride instead", "1:u-$plannedDeparture", progress.stableLegId)
        assertEquals("Frühere Fahrt erkannt · Zeiten werden aktualisiert", assistantNotice(s, second.capturedAt))
    }

    @Test fun ridingThePinnedTrainOnTimeIsNoDeviation() {
        var s = atStation(at = plannedDeparture - minute)
        val onTime = fix(c, plannedDeparture + 8 * minute, 9f)
        s = step(s, onTime)
        val again = fix(point(.031), onTime.capturedAt + 20_000, 9f)
        s = step(s, again, listOf(onTime, again))
        assertNull(s.rideDeviation)
    }

    @Test fun walkingAlongTheLineIsNotAVehicle() {
        var s = atStation()
        // Two kilometres in 30 minutes: 1.1 m/s.
        val walked = fix(c, t0 + 35 * minute, 1.3f)
        s = step(s, walked)
        assertNull(s.rideDeviation)
    }

    @Test fun aSingleOutlierIsForgottenWhenTheNextFixDisagrees() {
        var s = atStation()
        val jump = fix(c, t0 + 11 * minute, 1.3f)
        s = step(s, jump)
        assertEquals(1, s.rideDeviation?.confirmations)
        val back = fix(point(-.0004), jump.capturedAt + 20_000, 0f)
        s = step(s, back, listOf(jump, back))
        assertNull(s.rideDeviation)
        assertNull(confirmedRideDeviation(s, back.capturedAt))
    }

    @Test fun aLaterServiceIsRecognisedWithAForecastButNeedsMoreSlackWithoutOne() {
        fun ridingLate(realtime: Boolean, lateMinutes: Int): PinnedJourneySession {
            var s = atStation(pinned(subway(plannedDeparture, realtime = realtime)), plannedDeparture - minute)
            val start = fix(point(.004), plannedDeparture + lateMinutes * minute, 8f)
            val moving = fix(point(.008), start.capturedAt + 30_000, 9f)
            s = step(s, start, listOf(start))
            s = step(s, moving, listOf(start, moving))
            val atC = fix(c, plannedDeparture + (8 + lateMinutes) * minute, 9f)
            s = step(s, atC, listOf(atC))
            val after = fix(point(.031), atC.capturedAt + 20_000, 9f)
            return step(s, after, listOf(atC, after))
        }
        assertEquals(RideDeviationKind.LATER, ridingLate(true, 6).rideDeviation?.kind)
        assertNull("a missing forecast is not punctuality", ridingLate(false, 6).rideDeviation)
        assertEquals(RideDeviationKind.LATER, ridingLate(false, 10).rideDeviation?.kind)
    }

    @Test fun theMatchingServiceReplacesTheRideAndKeepsItBoarded() {
        var s = atStation()
        val first = fix(c, t0 + 11 * minute, 1.3f)
        val second = fix(point(.0302), first.capturedAt + 20_000, 1.2f)
        s = step(step(s, first), second, listOf(first, second))
        val observation = confirmedRideDeviation(s, second.capturedAt)!!
        // Candidates from the boarding stop: the one actually taken left at t0 + 3 min.
        val taken = Trip("taken", listOf(TripSegment("access", a, a, t0 + 2 * minute, t0 + 3 * minute, LineType.WALK, "Fußweg"),
            subway(t0 + 3 * minute, id = "taken-u")), "TEST", second.capturedAt)
        val otherLine = Trip("other", listOf(subway(t0 + 3 * minute + 30_000, line = "U43", id = "u43")), "TEST", second.capturedAt)
        val tooEarly = Trip("early", listOf(subway(t0 - 7 * minute, id = "too-early")), "TEST", second.capturedAt)
        val notYet = Trip("later", listOf(subway(t0 + 13 * minute, id = "not-yet")), "TEST", second.capturedAt)
        val match = matchObservedRide(s, observation, listOf(tooEarly, otherLine, notYet, taken))!!
        assertEquals("same line wins over another line on the corridor", "taken-u", match.trip.segments.first().id)
        assertEquals("access walks of the answer are history", 1, match.trip.segments.size)
        val adopted = adoptObservedRide(s, match, second.capturedAt)
        assertEquals("taken-u", adopted.trip.segments.single().id)
        assertEquals(0, gpsCursorLeg(adopted)?.index)
        assertTrue(adopted.gpsCursor!!.departed)
        assertTrue("stops before the position are passed", "0-0" in adopted.gpsCursor!!.passedStopKeys)
        assertNull(adopted.rideDeviation)
        assertEquals(s.routeRevision + 1, adopted.routeRevision)
        assertEquals("Frühere Fahrt erkannt · U47 ab ${clockTime(t0 + 3 * minute)} · Ankunft ${clockTime(t0 + 15 * minute)}",
            assistantNotice(adopted, second.capturedAt))
        assertEquals("the original plan stays the arrival baseline", s.initialTrip, adopted.initialTrip)
        // Without a fitting service nothing is replaced.
        assertNull(matchObservedRide(s, observation, listOf(tooEarly, notYet)))
    }

    @Test fun theProviderIsAskedFromTheBoardingStopAroundTheEstimatedDeparture() {
        var s = atStation()
        val first = fix(c, t0 + 11 * minute, 1.3f)
        val second = fix(point(.0302), first.capturedAt + 20_000, 1.2f)
        s = step(step(s, first), second, listOf(first, second))
        val observation = confirmedRideDeviation(s, second.capturedAt)!!
        val asked = mutableListOf<JourneyQuery>()
        val provider = object : TransitRepository {
            override val name = "TEST"
            override suspend fun journeyPage(query: JourneyQuery, cursor: String?): JourneyPage {
                asked += query
                return JourneyPage(listOf(Trip("taken", listOf(subway(t0 + 3 * minute, id = "taken-u")), "TEST", second.capturedAt)))
            }
            override suspend fun locations(query: String) = emptyList<Location>()
            override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int) = emptyList<Location>()
            override suspend fun departures(stop: Location) = emptyList<Departure>()
            override suspend fun refresh(trip: Trip) = trip
        }
        val match = runBlocking { provider.findObservedRide(s, observation) }
        assertEquals("taken-u", match?.trip?.segments?.first()?.id)
        val query = asked.single()
        assertEquals(a, query.origin)
        assertEquals(d, query.destination)
        // Observed 2 km along, 8 planned minutes after the departure: estimated departure t0 + 3:20.
        assertTrue(query.time in t0 - 6 * minute..t0 + 3 * minute)
    }

    @Test fun stayingAtTheStationAfterTheDepartureIsAMissedBoarding() {
        val train = subway(plannedDeparture)
        val s = atStation(pinned(train), plannedDeparture - 2 * minute)
        val platform = point(.0004)
        fun still(after: Long, accuracy: Float = 15f) = fix(platform, plannedDeparture + after, 0f, accuracy)
        assertNull("the train may still be at the platform", missedBoardingRide(s, still(minute).capturedAt, still(minute)))
        assertEquals(1, missedBoardingRide(s, still(3 * minute).capturedAt, still(3 * minute))?.index)
        assertNull("coarse fixes are not evidence", missedBoardingRide(s, still(3 * minute).capturedAt, still(3 * minute, 80f)))
        assertNull("no fix in the tunnel, no conclusion", missedBoardingRide(s, plannedDeparture + 3 * minute, null))
        val plannedOnly = atStation(pinned(subway(plannedDeparture, realtime = false)), plannedDeparture - 2 * minute)
        assertNull("without a forecast the train may be late", missedBoardingRide(plannedOnly, still(3 * minute).capturedAt, still(3 * minute)))
        assertEquals(1, missedBoardingRide(plannedOnly, still(7 * minute).capturedAt, still(7 * minute))?.index)
        // Two fresh observations at least 20 s apart confirm it.
        var observed = observeMissedBoarding(s, still(3 * minute).capturedAt, still(3 * minute), true)
        observed = observeMissedBoarding(observed, still(3 * minute + 30_000).capturedAt, still(3 * minute + 30_000), true)
        assertNotNull(confirmedMissedBoarding(observed, still(3 * minute + 30_000).capturedAt, still(3 * minute + 30_000)))
    }

    @Test fun savedSessionsWithoutTheNewFieldStillLoad() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val encoded = json.encodeToString(PinnedJourneySession.serializer(), pinned())
            .replace(",\"rideDeviation\":null", "")
        assertFalse(encoded.contains("rideDeviation"))
        assertNull(json.decodeFromString(PinnedJourneySession.serializer(), encoded).rideDeviation)
    }
}
