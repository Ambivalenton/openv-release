// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import org.junit.Assert.*
import org.junit.Test

class PinnedNetworkJourneyTest {
    private val now = 1_800_000_000_000L
    private val a = Location("A", "a", 51.0, 7.0, "test")
    private val b = a.copy(name = "B", id = "b", longitude = 7.01)
    private val c = a.copy(name = "C", id = "c", longitude = 7.02)
    private val home = Favorite("home", "Zuhause", a)
    private val work = Favorite("work", "Arbeit", c)
    private val edge = NetworkEdge("home-work", home.id, work.id)
    private val store = StoredState(favorites = listOf(home, work), network = TransitNetwork(
        nodes = listOf(NetworkNode(home.id, GridPoint(0, 1)), NetworkNode(work.id, GridPoint(5, 1))), edges = listOf(edge)))
    private val ride = TripSegment("ride", a, c, now - 600_000, now + 600_000, LineType.REGIONAL, "RE 1")
    private fun session(legs: List<TripSegment> = listOf(ride)) = Trip("trip", legs, "test", now).let {
        PinnedJourneySession(favorite = work, query = JourneyQuery(a, c), initialTrip = it, routing = AppSettings())
    }
    private fun gps(fraction: Float, s: PinnedJourneySession = session(), leg: String = "0:ride",
                    source: JourneyPositionSource = JourneyPositionSource.GPS) = PinnedJourneyState(session = s,
        visualProgress = VisualJourneyProgress(1, true, now, fraction = fraction, stableLegId = leg, positionSource = source))

    @Test fun authoritativeAssistantPositionWinsOverTheTimetable() {
        val marker = pinnedNetworkJourney(store, gps(.23f), now)!!
        assertEquals("home-work", marker.edgeId)
        assertEquals(.23f, marker.fraction!!, .0001f)
        assertEquals(LineType.REGIONAL, marker.lineType)
        assertEquals(JourneyPositionSource.GPS, marker.source)
    }

    @Test fun reversingTheStoredLinePreservesTravelProgressAndReversesTheVehicle() {
        val reverse = store.copy(network = store.network.copy(edges = listOf(edge.copy(aId = work.id, bId = home.id))))
        val marker = pinnedNetworkJourney(reverse, gps(.3f), now)!!
        assertFalse(marker.forward)
        assertEquals(.3f, marker.fraction!!, .0001f)
    }

    @Test fun shortGpsGapHoldsTheAssistantPositionWithoutDrivingOnByTime() {
        val s = session()
        val state = gps(.19f, s, source = JourneyPositionSource.GPS_LAST_KNOWN)
        val marker = pinnedNetworkJourney(store, state, now + 15_000)!!
        assertEquals(.19f, marker.fraction!!, .0001f)
        assertEquals(JourneyPositionSource.GPS_LAST_KNOWN, marker.source)
    }

    /** 2.6: an unmatched fix no longer hides the train; the marker follows the timetable and says so. */
    @Test fun unmatchedGpsFallsBackToTheTimetableAndSaysSo() {
        val state = PinnedJourneyState(session = session(), visualProgress = VisualJourneyProgress(0, true, now,
            positionSource = JourneyPositionSource.GPS_UNMATCHED))
        val marker = pinnedNetworkJourney(store, state, now)!!
        assertEquals(JourneyPositionSource.TIMETABLE, marker.source)
        assertTrue(marker.gpsPending)
        assertEquals(.5f, marker.fraction!!, .001f)
    }

    /** 2.6: trip legs carry platform coordinates and other IDs; the station favourite still matches. */
    @Test fun stationFavouritesMatchPlatformCoordinatesDhidsAndOnlyTheSameStation() {
        val station = Location("Herne Bf", "20000001", 51.5400, 7.2200, "VRR", LocationKind.STOP)
        val target = Location("Dortmund Hbf", "20000002", 51.5180, 7.4590, "VRR", LocationKind.STOP)
        val platform = station.copy(id = "de:05916:1:2:5", latitude = 51.5409, longitude = 7.2205)
        assertTrue("same name within the station area", planPlaceMatches(station, platform))
        assertFalse("a stop of the same name far away", planPlaceMatches(station, platform.copy(latitude = 51.60)))
        assertTrue("DHID of the station without the platform part",
            planPlaceMatches(station.copy(dhid = "de:05916:1"), platform.copy(name = "Herne", dhid = "de:05916:1:2:5", latitude = 51.55)))
        assertFalse("different proven DHIDs stay apart",
            planPlaceMatches(station.copy(dhid = "de:05916:1"), platform.copy(dhid = "de:05916:9:1:1")))
        val homeStation = Favorite("hs", "Herne", station); val workStation = Favorite("ws", "Dortmund", target)
        val plan = StoredState(favorites = listOf(homeStation, workStation), network = TransitNetwork(
            nodes = listOf(NetworkNode("hs", GridPoint(0, 1)), NetworkNode("ws", GridPoint(5, 1))), edges = listOf(NetworkEdge("line", "hs", "ws"))))
        val gpsStart = Location("Mein Standort", null, 51.5452, 7.2101, "device", LocationKind.ADDRESS)
        val legs = listOf(TripSegment("walk", gpsStart, platform, now - 1_200_000, now - 600_000, LineType.WALK, "Fußweg"),
            TripSegment("ride", platform, target.copy(id = "de:05913:2:1:1", latitude = 51.5175), now - 600_000, now + 600_000, LineType.REGIONAL, "RE 3"))
        val s = PinnedJourneySession(favorite = workStation, query = JourneyQuery(gpsStart, target),
            initialTrip = Trip("t", legs, "test", now), routing = AppSettings())
        val marker = pinnedNetworkJourney(plan, PinnedJourneyState(session = s), now)!!
        assertEquals("line", marker.edgeId)
        assertTrue(marker.forward)
        assertEquals(.5f, marker.fraction!!, .02f)
        assertEquals("RE 3", marker.lineName)
    }

    /** 2.6: a replaced pinned journey moves the marker to the new journey's section at once. */
    @Test fun aReplacedJourneyFollowsTheNewTripOnly() {
        val first = session()
        val replaced = first.copy(trip = Trip("other", listOf(ride.copy(id = "later", startTime = now + 300_000, endTime = now + 900_000)), "test", now))
        assertEquals(.5f, pinnedNetworkJourney(store, PinnedJourneyState(session = first), now)!!.fraction!!, .001f)
        assertEquals(0f, pinnedNetworkJourney(store, PinnedJourneyState(session = replaced), now)!!.fraction!!, .001f)
    }

    @Test fun timetableUsesCurrentArrivalAndRecoveredDelaysImmediately() {
        val delayed = session(listOf(ride.copy(endTime = now + 1_800_000)))
        assertEquals(.25f, pinnedNetworkJourney(store, PinnedJourneyState(session = delayed), now)!!.fraction!!, .001f)
        val recovered = session()
        assertEquals(.5f, pinnedNetworkJourney(store, PinnedJourneyState(session = recovered), now)!!.fraction!!, .001f)
    }

    @Test fun dwellAtAnIntermediateStopKeepsTheMarkerStill() {
        val s = session(listOf(ride.copy(stops = listOf(Stopover(b, now - 60_000, now + 60_000)))))
        val state = PinnedJourneyState(session = s)
        val first = pinnedNetworkJourney(store, state, now)!!.fraction!!
        val later = pinnedNetworkJourney(store, state, now + 30_000)!!.fraction!!
        assertEquals(.5f, first, .001f)
        assertEquals(first, later, .0001f)
    }

    @Test fun serverFallbackOverridesAPreviousGpsFrameWhenLocationIsUnavailable() {
        val unavailable = session().copy(locationUnavailable = true)
        val marker = pinnedNetworkJourney(store, gps(.15f, unavailable), now)!!
        assertEquals(JourneyPositionSource.TIMETABLE, marker.source)
        assertEquals(.5f, marker.fraction!!, .001f)
    }

    @Test fun aFutureFavouriteSectionDoesNotInventAnArrivalAtItsStart() {
        val middle = Favorite("middle", "Umstieg", b)
        val network = store.network.copy(nodes = store.network.nodes + NetworkNode("middle", GridPoint(2, 1)),
            edges = listOf(NetworkEdge("later", "middle", "work")))
        val state = store.copy(favorites = store.favorites + middle, network = network)
        val s = session(listOf(ride.copy(stops = listOf(Stopover(b, now)))))
        val marker = pinnedNetworkJourney(state, gps(.1f, s), now)!!
        assertEquals("later", marker.edgeId)
        assertNull(marker.fraction)
    }

    @Test fun currentAndBoardingLegsSelectTheActualVehicleIncludingWalking() {
        val legs = listOf(ride.copy(destination = b, lineType = LineType.BUS),
            ride.copy(id = "walk", origin = b, lineType = LineType.WALK, lineName = "Fußweg"),
            ride.copy(id = "tram", origin = b, lineType = LineType.TRAM, lineName = "302"))
        val s = session(legs)
        assertEquals(LineType.WALK, pinnedNetworkJourney(store, gps(.6f, s, "1:walk"), now)!!.lineType)
        val boarding = gps(.7f, s).copy(visualProgress = VisualJourneyProgress(2, true, now, fraction = .7f,
            boardingLegId = "2:tram", positionSource = JourneyPositionSource.GPS))
        assertEquals(LineType.TRAM, pinnedNetworkJourney(store, boarding, now)!!.lineType)
    }

    @Test fun individualFavouriteSectionsFollowTheCurrentPartOfALongerJourney() {
        val middle = Favorite("middle", "Umstieg", b)
        val network = store.network.copy(nodes = store.network.nodes + NetworkNode("middle", GridPoint(2, 1)),
            edges = listOf(NetworkEdge("first", "home", "middle"), NetworkEdge("second", "middle", "work")))
        val s = session(listOf(ride.copy(stops = listOf(Stopover(b, now)))))
        val state = store.copy(favorites = store.favorites + middle, network = network)
        val marker = pinnedNetworkJourney(state, gps(.75f, s), now)!!
        assertEquals("second", marker.edgeId)
        assertEquals(.5f, marker.fraction!!, .001f)
    }

    @Test fun renamedFavoritesStillMatchAndAnUnplacedOrUnrelatedRouteDoesNotMatch() {
        val renamed = store.copy(favorites = listOf(home.copy(label = "Anderer Name"), work))
        assertNotNull(pinnedNetworkJourney(renamed, gps(.2f), now))
        assertNull(pinnedNetworkJourney(store.copy(network = store.network.copy(nodes = emptyList())), gps(.2f), now))
        val elsewhere = a.copy(id = "elsewhere", latitude = 52.0)
        val s = session().copy(query = JourneyQuery(elsewhere, elsewhere),
            trip = Trip("other", listOf(ride.copy(origin = elsewhere, destination = elsewhere)), "test", now))
        assertNull(pinnedNetworkJourney(store, gps(.5f, s), now))
        assertNull(pinnedNetworkJourney(store, null, now))
    }
}
