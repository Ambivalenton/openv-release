// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.Manifest
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.location.DeviceLocationProvider
import dev.ambivalenton.openv.ui.network.networkRouteGroups
import dev.ambivalenton.openv.ui.screens.searchSettings
import dev.ambivalenton.openv.ui.screens.SettingsPage
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Chosen stops without walks, the planner mode's position switch and the route list. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class Build94LogicTest {
    private val old = java.util.Locale.getDefault()
    @org.junit.Before fun german() { java.util.Locale.setDefault(java.util.Locale.GERMANY) }
    @org.junit.After fun restore() { java.util.Locale.setDefault(old) }
    private val now = 1_800_000_000_000L
    private fun stop(name: String, id: String, lat: Double = 51.45, lon: Double = 7.0) = Location(name, id, lat, lon, "VRR", LocationKind.STOP)
    private val essen = stop("Essen Hbf", "e"); private val bochum = stop("Bochum Hbf", "b", lon = 7.22)
    private fun ride(from: Location, to: Location, start: Long = now + 600_000, end: Long = start + 900_000) =
        TripSegment("RE-${from.id}-${to.id}", from, to, start, end, LineType.REGIONAL, "RE 1")
    private fun walk(from: Location, to: Location, start: Long = now) =
        TripSegment("walk-${from.id}-${to.id}", from, to, start, start + 300_000, LineType.WALK, "Fußweg")

    /** A stop chosen on purpose is where the journey begins and ends; a walk to another stop is not. */
    @Test fun anExplicitStopKeepsOnlyJourneysFromThatStop() {
        val query = JourneyQuery(essen, bochum, now)
        val steele = stop("Essen-Steele", "s", lat = 51.45, lon = 7.08)
        assertTrue(Trip("direct", listOf(ride(essen, bochum)), "VRR", now).keepsExplicitStops(query))
        assertFalse(Trip("walk-away", listOf(walk(essen, steele), ride(steele, bochum)), "VRR", now).keepsExplicitStops(query))
        // Another platform of the same station (same name, a few metres away) still counts as the chosen stop.
        val platform = essen.copy(id = "e:2", latitude = 51.4502)
        assertTrue(Trip("platform", listOf(walk(essen, platform), ride(platform, bochum)), "VRR", now).keepsExplicitStops(query))
        // A device position or an address keeps its walking legs.
        val here = essen.copy(id = "gps", isDevicePosition = true)
        assertTrue(Trip("gps", listOf(walk(here, steele), ride(steele, bochum)), "VRR", now).keepsExplicitStops(JourneyQuery(here, bochum, now)))
        // Uncertain data (no coordinates, same name) never removes a journey.
        val unknown = Location("Essen Hbf", "other-id", kind = LocationKind.STOP)
        assertTrue(Trip("unknown", listOf(ride(unknown, bochum)), "VRR", now).keepsExplicitStops(query))
    }

    /** "Planermodus": with the switch on, no request path sees a usable location access. */
    @Test fun thePlannerModeSwitchesEveryPositionRequestOff() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val device = DeviceLocationProvider(app)
        assertTrue(device.hasPermission())
        device.setLocationOff(true)
        assertFalse(device.hasPermission())
        assertNull(device.lastPresence)
        device.setLocationOff(false)
        assertTrue(device.hasPermission())
        assertFalse("off by default", AppSettings().plannerMode)
    }

    /** The settings search finds the new mode under its usual words. */
    @Test fun theSettingsSearchFindsThePlannerMode() {
        listOf("Planer", "ohne Standort", "kein GPS").forEach { words ->
            val hit = searchSettings(words).firstOrNull()
            assertEquals(words, "Planermodus", hit?.titleKey); assertEquals(SettingsPage.MODES, hit?.page)
        }
    }

    /** The list keeps both directions of a line in one group, a one-way line with its own direction. */
    @Test fun theRouteListGroupsBothDirectionsOfALine() {
        val home = Favorite("h", "Zuhause", essen); val work = Favorite("w", "Arbeit", bochum)
        val gym = Favorite("g", "Sport", stop("Sporthalle", "g", lon = 7.1))
        val state = StoredState(favorites = listOf(home, work, gym), network = TransitNetwork(
            listOf(NetworkNode("h", GridPoint(0, 0)), NetworkNode("w", GridPoint(3, 0)), NetworkNode("g", GridPoint(1, 2))),
            listOf(NetworkEdge("l", "h", "w", DirectionMode.BOTH), NetworkEdge("m", "h", "g", DirectionMode.B_TO_A))))
        val groups = networkRouteGroups(state)
        assertEquals(listOf("l", "m"), groups.map { it.edgeId })
        assertEquals(listOf("Zuhause→Arbeit", "Arbeit→Zuhause"), groups[0].directions.map { "${it.from.label}→${it.to.label}" })
        assertEquals(listOf("Sport→Zuhause"), groups[1].directions.map { "${it.from.label}→${it.to.label}" })
    }
}
