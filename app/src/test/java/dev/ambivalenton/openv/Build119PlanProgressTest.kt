// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import org.junit.Assert.*
import org.junit.Test

/** Build 119 · the plan's single progress train for a pinned journey between two favourites. */
class Build119PlanProgressTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L
    private fun stop(name: String, id: String, lat: Double, lon: Double, dhid: String? = null) =
        Location(name, id, lat, lon, "VRR", LocationKind.STOP, dhid = dhid)
    // Saved favourites: the station (an older favourite without DHID) and the home address.
    private val hbfFavorite = Favorite("hbf", "Dortmund Hbf", stop("Dortmund Hbf", "de:05913:2410", 51.5176, 7.4590))
    private val home = Favorite("home", "Zuhause", Location("Zuhause, Bahnhofstr. 12, Herne", null, 51.5392, 7.2210, kind = LocationKind.ADDRESS))
    private val network = TransitNetwork(nodes = listOf(NetworkNode("hbf", GridPoint(0, 0)), NetworkNode("home", GridPoint(2, 1))),
        edges = listOf(NetworkEdge(id = "e", aId = "hbf", bId = "home")))
    private val store = StoredState(favorites = listOf(hbfFavorite, home), network = network)

    /** From where the person stands (in the station hall), on foot to the platform, RE to Herne, bus, walk home. */
    private fun session(start: Long): PinnedJourneySession {
        val here = Location("Mein Standort", null, 51.5180, 7.4600, kind = LocationKind.COORDINATE, isDevicePosition = true)
        // The platform of the trip: its own ID and coordinates, 90 m from the saved station point.
        val platform = stop("Dortmund Hbf", "de:05913:2410:12:3", 51.5168, 7.4592, dhid = "de:05913:2410:12:3")
        val herne = stop("Herne Bf", "de:05916:5001", 51.5440, 7.2190)
        val wanne = stop("Herne Wanner Str.", "de:05916:5120", 51.5395, 7.2205)
        val segments = listOf(
            TripSegment("w1", here, platform, start, start + 4 * min, LineType.WALK, ""),
            TripSegment("re", platform, herne, start + 6 * min, start + 26 * min, LineType.REGIONAL, "RE3",
                stops = listOf(Stopover(platform, start + 6 * min, start + 6 * min), Stopover(herne, start + 26 * min, start + 26 * min))),
            TripSegment("bus", herne, wanne, start + 30 * min, start + 36 * min, LineType.BUS, "311",
                stops = listOf(Stopover(herne, start + 30 * min, start + 30 * min), Stopover(wanne, start + 36 * min, start + 36 * min))),
            TripSegment("w2", wanne, home.location, start + 36 * min, start + 39 * min, LineType.WALK, ""))
        return PinnedJourneySession(favorite = home, query = JourneyQuery(here, home.location, start),
            initialTrip = Trip("t", segments, "VRR", start), routing = AppSettings())
    }

    @Test fun aJourneyFromHereToAFavouriteThroughTheStationShowsTheProgressTrain() {
        val s = session(now - 10 * min)
        val journey = pinnedNetworkJourney(store, PinnedJourneyState(s, PinnedTrackingStatus.RUNNING), now)
        assertNotNull("the station favourite and home share a line on the plan", journey)
        assertEquals("e", journey!!.edgeId)
        assertTrue(journey.forward)
        val f = journey.fraction
        assertNotNull(f)
        assertTrue("on the RE, well under way: $f", f!! in .05f..0.8f)
    }

    /** From home (GPS 120 m from the saved address) to a station saved without its town. */
    @Test fun homeAndAStationWithoutItsTownAreFoundOnTheJourney() {
        val station = Favorite("hbf", "Hauptbahnhof", stop("Hauptbahnhof", "x:station", 51.5180, 7.4600))
        val homeFav = Favorite("home", "Zuhause", Location("Zuhause", null, 51.5392, 7.2210, kind = LocationKind.ADDRESS))
        val net = TransitNetwork(nodes = listOf(NetworkNode("hbf", GridPoint(0, 0)), NetworkNode("home", GridPoint(2, 1))),
            edges = listOf(NetworkEdge(id = "e", aId = "home", bId = "hbf")))
        val here = Location("Mein Standort", null, 51.5402, 7.2223, kind = LocationKind.COORDINATE, isDevicePosition = true)
        val herne = stop("Herne Bf", "de:05916:5001", 51.5440, 7.2190)
        val hbf = stop("Dortmund Hbf", "de:05913:2410:12:3", 51.5168, 7.4592)
        val start = now - 10 * min
        val trip = Trip("t", listOf(
            TripSegment("w", here, herne, start, start + 8 * min, LineType.WALK, ""),
            TripSegment("re", herne, hbf, start + 9 * min, start + 30 * min, LineType.REGIONAL, "RE3",
                stops = listOf(Stopover(herne, start + 9 * min, start + 9 * min), Stopover(hbf, start + 30 * min, start + 30 * min)))), "VRR", start)
        val session = PinnedJourneySession(favorite = station, query = JourneyQuery(here, hbf, start), initialTrip = trip, routing = AppSettings())
        val journey = pinnedNetworkJourney(StoredState(favorites = listOf(station, homeFav), network = net),
            PinnedJourneyState(session, PinnedTrackingStatus.RUNNING), now)
        assertNotNull(journey)
        assertEquals("e", journey!!.edgeId)
        assertTrue("from home towards the station, along the line from a to b", journey.forward)
        // The stricter routing match stays as it was.
        assertFalse(planPlaceMatches(homeFav.location, here))
        assertTrue(planFavoriteOnJourney(homeFav.location, here))
        assertTrue(planFavoriteOnJourney(station.location, hbf))
    }
}
