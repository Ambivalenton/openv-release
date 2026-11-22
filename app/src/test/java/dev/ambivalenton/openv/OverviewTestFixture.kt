// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*

internal open class OverviewTestProvider : TransitRepository {
    override val name = "Test"
    val queries = mutableListOf<JourneyQuery>()
    var response: suspend (JourneyQuery, String?) -> JourneyPage = { query, _ ->
        JourneyPage(listOf(overviewTestTrip(query)))
    }

    override suspend fun journeyPage(query: JourneyQuery, cursor: String?): JourneyPage {
        queries += query
        return response(query, cursor)
    }

    override suspend fun locations(query: String) = emptyList<Location>()

    override suspend fun nearby(latitude: Double, longitude: Double, radiusMeters: Int) =
        emptyList<Location>()

    override suspend fun departures(stop: Location) = emptyList<Departure>()

    override suspend fun refresh(trip: Trip) = trip

    override fun journeyWindow(
        query: JourneyQuery,
        policy: PagingPolicy,
    ): kotlinx.coroutines.flow.Flow<JourneyWindow> =
        error("Overview must not load a three-hour window")
}

internal fun overviewTestTrip(query: JourneyQuery, suffix: String = ""): Trip =
    Trip(
        "trip-${query.destination.name}$suffix",
        listOf(
            TripSegment(
                "leg$suffix",
                query.origin,
                query.destination,
                query.time + 120_000,
                query.time + 1_920_000,
                LineType.BUS,
                "SB20",
                departurePlatform = "A",
            )
        ),
        "Test",
        query.time,
    )
