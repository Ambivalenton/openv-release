// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.efaGlobalStopId
import dev.ambivalenton.openv.data.repository.efaLocationArgs
import dev.ambivalenton.openv.data.repository.efaPointResolved
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * A stop from the local directory reaches the EFA as a stop, a walk from the provider's
 * point near the entered place to that place is dropped, and no stop rule can empty a result.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class Build95LogicTest {
    private val now = 1_800_000_000_000L
    /** "Herne, Bf" as the local DELFI directory names it, with its DHID. */
    private val herneDirectory = Location("Herne, Bf", "de:05916:7501", 51.5442, 7.2170, "DELFI_STATIC", LocationKind.STOP, city = "Herne", dhid = "de:05916:7501")
    /** The same station as the EFA's trip legs name it (other ID, slightly other coordinates). */
    private val herneLeg = Location("Herne Bf", "20007501", 51.544363, 7.216813, "VRR", LocationKind.STOP, city = "Herne")
    private val dortmund = Location("Dortmund Hbf", "20000131", 51.517198, 7.459475, "VRR", LocationKind.STOP, city = "Dortmund")
    private val nearPoint = Location("Baukau-Ost (Herne), Konrad-Adenauer-Platz 5", "coord:1", 51.5449, 7.2180, "VRR", LocationKind.STOP)
    private val bismarck = Location("Herne Bismarckstr.", "20007536", 51.547340, 7.216248, "VRR", LocationKind.STOP, city = "Herne")
    private fun walk(from: Location, to: Location, start: Long) = TripSegment("w-${from.id}-${to.id}", from, to, start, start + 300_000, LineType.WALK, "Fußweg")
    private fun ride(from: Location, to: Location, start: Long, line: String = "RE 3") =
        TripSegment("$line-${from.id}", from, to, start, start + 1_200_000, LineType.REGIONAL, line)

    /** Measured on the VRR EFA (21.11.2026): sent as coordinates the journey began with a walk from "Konrad-Adenauer-Platz 5". */
    @Test fun aDirectoryStopIsSentAsAStopAndFallsBackToItsCoordinates() {
        assertEquals("de:05916:7501", efaGlobalStopId(herneDirectory))
        assertEquals(mapOf("type_origin" to "stop", "name_origin" to "de:05916:7501"), efaLocationArgs("origin", herneDirectory))
        // A platform ID is reduced to its stop; a VRR stop keeps its own ID.
        assertEquals("de:05916:7501", efaGlobalStopId(herneDirectory.copy(dhid = "de:05916:7501:1:2")))
        assertEquals(mapOf("type_destination" to "stop", "name_destination" to "20000131"), efaLocationArgs("destination", dortmund))
        // Not a DHID, a device position or an address: coordinates as before.
        assertNull(efaGlobalStopId(herneDirectory.copy(id = "8000150", dhid = null)))
        assertNull(efaGlobalStopId(herneDirectory.copy(isDevicePosition = true)))
        assertNull(efaGlobalStopId(herneDirectory.copy(kind = LocationKind.ADDRESS)))
        // The retry with coordinates when the EFA does not know the stop ("stop invalid", no point).
        assertEquals("coord", efaLocationArgs("origin", herneDirectory, globalIds = false)["type_origin"])
        val unknown = Json.parseToJsonElement("""{"origin":{"input":{"input":"de:99999:1"},"points":null},"destination":{"points":{"point":{"name":"Dortmund, Hbf"}}}}""")
        assertFalse(efaPointResolved(unknown, "origin")); assertTrue(efaPointResolved(unknown, "destination"))
    }

    /** The walk to the very stop the user entered is dropped; the journey begins at the stop. */
    @Test fun aWalkToTheEnteredPlaceIsDroppedAndOneToAnotherStopStays() {
        val start = now + 600_000
        val trip = Trip("t", listOf(walk(nearPoint, herneLeg, start), ride(herneLeg, dortmund, start + 300_000)), "VRR", now)
        val clean = trip.withoutWalksAtEnteredPlaces(herneDirectory, dortmund)
        assertEquals(listOf("RE 3"), clean.segments.map { it.lineName })
        assertEquals(start + 300_000, clean.startTime)
        // A walk to another stop (Bismarckstr. instead of the station) is a real walk.
        val other = Trip("o", listOf(walk(herneLeg, bismarck, start), ride(bismarck, dortmund, start + 300_000)), "VRR", now)
        assertEquals(2, other.withoutWalksAtEnteredPlaces(herneDirectory, dortmund).segments.size)
        // A device position keeps its walk; so does a journey with stopovers.
        assertEquals(2, trip.withoutWalksAtEnteredPlaces(herneDirectory.copy(isDevicePosition = true), dortmund).segments.size)
        assertEquals(2, trip.copy(waypoints = listOf(dortmund)).withoutWalksAtEnteredPlaces(herneDirectory, dortmund).segments.size)
        // At the end as well: "Dortmund Hbf" entered, a final walk to a point in the station building.
        val inside = Location("Mitte (Dortmund), Weg im Gebäude", "coord:2", 51.5184, 7.4588, "VRR", LocationKind.STOP)
        val ending = Trip("e", listOf(ride(herneLeg, dortmund, start), walk(dortmund, inside, start + 1_200_000)), "VRR", now)
        assertEquals(1, ending.withoutWalksAtEnteredPlaces(herneLeg, dortmund.copy(id = "de:05913:131", provider = "DELFI_STATIC", name = "Dortmund, Hbf")).segments.size)
    }

    /** Same name, but another city or far away: not the entered place. */
    @Test fun theEnteredPlaceNeedsTheSameNameCityAndNeighbourhood() {
        assertTrue(isEnteredPlace(herneDirectory, herneLeg))
        assertFalse(isEnteredPlace(herneDirectory, dortmund))
        assertFalse(isEnteredPlace(herneDirectory, herneLeg.copy(name = "Bochum Bf", city = "Bochum", latitude = 51.4783, longitude = 7.2229)))
        assertFalse("1 km away", isEnteredPlace(herneDirectory, herneLeg.copy(latitude = 51.5532)))
        assertTrue("a POI named like the station", isEnteredPlace(Location("Herne, Herne Bahnhof", latitude = 51.5441, longitude = 7.2163,
            kind = LocationKind.POI, city = "Herne"), herneLeg))
    }

    /** A moved stop sends every journey via its replacement: none of them may be filtered away. */
    @Test fun aMovedStopNeverEmptiesTheResult() {
        val replacement = Location("Herne Bf (Ersatzhaltestelle Westring)", "20009999", 51.5470, 7.2120, "VRR", LocationKind.STOP, city = "Herne")
        val query = JourneyQuery(herneLeg, dortmund, now)
        val viaReplacement = (0 until 3).map { i -> Trip("r$i", listOf(walk(herneLeg, replacement, now + i * 600_000),
            ride(replacement, dortmund, now + i * 600_000 + 400_000, "S 2")), "VRR", now) }
        assertEquals(3, viaReplacement.preferExplicitStops(query).size)
        // With journeys from the stop itself, walks to another stop give way to them.
        val direct = Trip("d", listOf(ride(herneLeg, dortmund, now + 900_000)), "VRR", now)
        assertEquals(listOf("d"), (viaReplacement + direct).preferExplicitStops(query).map { it.id })
    }

    /** A refresh does not bring a dropped walk back. */
    @Test fun aRefreshKeepsTheShapeOfTheShownJourney() {
        val start = now + 600_000
        val shown = Trip("t", listOf(ride(herneLeg, dortmund, start + 300_000)), "VRR", now)
        val refreshed = Trip("t", listOf(walk(nearPoint, herneLeg, start), ride(herneLeg, dortmund, start + 360_000)), "VRR", now + 60_000)
        assertEquals(listOf("RE 3"), refreshed.withWalksOf(shown).segments.map { it.lineName })
        // A journey that was shown with its walk keeps it.
        assertEquals(2, refreshed.withWalksOf(refreshed).segments.size)
    }
}
