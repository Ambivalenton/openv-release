// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in integration probe, kept separate from deterministic offline/fixture tests. */
class LiveTransitSmokeTest {
    @Test
    fun realVrrSearchNearbyTripsAndDepartures() = runBlocking {
        assumeTrue(
            "Set OPENV_LIVE_TESTS=1 for actual network traffic",
            System.getenv("OPENV_LIVE_TESTS") == "1",
        )
        val provider = EfaTransitRepository(NetworkClient(OkHttpClient()))
        val stops = provider.nearby(51.5439, 7.2187, 500)
        assertTrue("Real nearby stations", stops.isNotEmpty())
        val places = provider.locations("Dortmund Hbf")
        assertTrue("Real stop finder", places.isNotEmpty())
        val rows = provider.departures(stops.first())
        assertTrue("Real departures", rows.isNotEmpty())
        val page = provider.journeyPage(JourneyQuery(
            Location("Herne Bf Umgebung", latitude = 51.5439, longitude = 7.2187),
            Location("Dortmund Hbf Umgebung", latitude = 51.5179, longitude = 7.4593),
        ), null)
        val trips = page.trips
        assertTrue("One user-requested page", trips.isNotEmpty())
        assertFalse(trips.any { it.segments.any { leg -> leg.lineType == LineType.ICE } })
        println(
            "LIVE VRR: ${stops.size} stops in 500m; ${places.size} stop suggestions; ${rows.size} departures; ${trips.size} journeys from one page"
        )
    }
}

