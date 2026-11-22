// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.components.timelineWindow
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class EfaProviderTest {
    private fun fixture(name: String) =
        Json.parseToJsonElement(javaClass.getResource("/$name")!!.readText())

    private fun trip() = fixture("efa-trips.json").jsonObject["trips"]!!.jsonArray.first()

    @Test
    fun realVrrTripsKeepEveryLegAndPlatform() {
        val data = fixture("efa-trips.json").jsonObject["trips"]!!.jsonArray
        assertTrue(data.isNotEmpty())
        data.forEach { raw ->
            val parsed = parseEfaTrip(raw)!!
            assertEquals(raw.jsonObject["legs"]!!.jsonArray.size, parsed.segments.size)
            assertTrue(
                parsed.segments.all { it.endTime >= it.startTime && it.origin.hasCoordinates }
            )
            assertEquals(dev.ambivalenton.openv.localization.tr(dev.ambivalenton.openv.data.model.TimetableProfile.name), parsed.source)
        }
        val parsed = parseEfaTrip(trip())!!
        assertTrue(parsed.segments.first().stops.size > 2)
        assertEquals("2", parsed.segments.first().departurePlatform)
    }

    @Test
    fun malformedLegRejectsTheWholeConnection() {
        val raw = trip().jsonObject.toMutableMap()
        raw["legs"] =
            JsonArray(raw["legs"]!!.jsonArray + buildJsonObject { putJsonArray("points") {} })
        assertNull(parseEfaTrip(JsonObject(raw)))
    }

    @Test
    fun unmonitoredConnectionHasNoInventedZeroDelay() {
        val raw = trip().jsonObject.toMutableMap()
        raw["legs"] =
            JsonArray(
                raw["legs"]!!.jsonArray.map { leg ->
                    JsonObject(
                        leg.jsonObject.toMutableMap().apply {
                            put("realtimeStatus", JsonPrimitive("SCHEDULED"))
                        }
                    )
                }
            )
        val result = parseEfaTrip(JsonObject(raw))!!
        assertTrue(
            result.segments.all { it.delay.minutes == null && it.startTime == it.plannedStartTime }
        )
    }

    @Test
    fun realtimeDoesNotChangeTripIdentity() {
        val raw = trip().jsonObject.toMutableMap()
        raw["legs"] =
            JsonArray(
                raw["legs"]!!.jsonArray.map { leg ->
                    JsonObject(
                        leg.jsonObject.toMutableMap().apply {
                            put("realtimeStatus", JsonPrimitive("SCHEDULED"))
                        }
                    )
                }
            )
        assertEquals(parseEfaTrip(trip())!!.id, parseEfaTrip(JsonObject(raw))!!.id)
    }

    @Test
    fun realCoordinateJourneysRetainFootpathsAndVehicle() {
        val parsed =
            fixture("efa-coordinate-trips.json")
                .jsonObject["trips"]!!
                .jsonArray
                .mapNotNull(::parseEfaTrip)
        assertTrue(parsed.isNotEmpty())
        assertTrue(parsed.first().segments.any { it.isWalk })
        assertTrue(parsed.first().segments.any { !it.isWalk })
    }

    @Test
    fun realDeparturesMapNullableRealtimeAndPlatforms() {
        val stop = Location("Wanne-Eickel", "20007590", 51.532, 7.165, "VRR")
        val parsed =
            fixture("efa-departures.json").jsonObject["departureList"]!!.jsonArray.mapNotNull {
                parseEfaDeparture(it, stop)
            }
        assertTrue(parsed.size >= 3)
        assertEquals(LineType.ICE, parsed.first().lineType)
        assertEquals(15, parsed.first().delay.minutes)
        assertNotNull(parsed.first().platform)
        assertTrue(parsed.any { it.delay.minutes == 0 })
    }

    @Test
    fun cancellationSentinelIsNotARealDelay() {
        val source =
            fixture("efa-departures.json")
                .jsonObject["departureList"]!!
                .jsonArray
                .first()
                .jsonObject
        val changed =
            JsonObject(
                source.toMutableMap().apply {
                    put(
                        "servingLine",
                        JsonObject(
                            source["servingLine"]!!.jsonObject.toMutableMap().apply {
                                put("delay", JsonPrimitive("-9999"))
                            }
                        ),
                    )
                }
            )
        assertTrue(parseEfaDeparture(changed, Location("Stop", "1"))!!.delay.cancelled)
    }

    @Test
    fun serviceDayTimesAndBerlinZoneAreExplicit() {
        assertEquals(
            Instant.parse("2026-10-26T00:10:00Z").toEpochMilli(),
            efaDate("20261025", "25:10:00"),
        )
        assertNull(efaDate("20261025", "49:00"))
    }

    @Test
    fun foreignProviderIdsBecomeCoordinatesNotVrrStopIds() {
        val args = efaLocationArgs("origin", initialFavorites().first().location)
        assertEquals("coord", args["type_origin"])
        assertTrue(args["name_origin"]!!.startsWith("7.4593000:51.5179000"))
    }

    @Test
    fun commonTimelineAlignsAlternativesAndKeepsWaitingGaps() {
        val parsed =
            fixture("efa-trips.json").jsonObject["trips"]!!.jsonArray.mapNotNull(::parseEfaTrip)
        val window = timelineWindow(parsed)
        assertTrue(window.start <= parsed.minOf { it.startTime })
        assertTrue(window.end >= parsed.maxOf { it.endTime })
        assertEquals(.5f, window.fraction((window.start + window.end) / 2), .0001f)
        assertEquals(0, window.start % (15 * 60_000L))
    }

    @Test
    fun nearbyRequestAndClientFilterEnforce500Meters() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(javaClass.getResource("/efa-nearby.json")!!.readText())
            )
            val provider = EfaTransitRepository(NetworkClient(OkHttpClient()), server.url("/"))
            val stops = provider.nearby(51.5439, 7.2187, 500)
            assertTrue(stops.isNotEmpty())
            assertTrue(
                stops.all { distanceMeters(51.5439, 7.2187, it.latitude!!, it.longitude!!) <= 500 }
            )
            val request = server.takeRequest().requestUrl!!
            assertEquals("500", request.queryParameter("radius_1"))
            assertEquals("STOP", request.queryParameter("type_1"))
        }
    }

    @Test
    fun querySendsArrivalTimeProductsAndNoUnrelatedStopIds() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(javaClass.getResource("/efa-coordinate-trips.json")!!.readText())
            )
            val provider = EfaTransitRepository(NetworkClient(OkHttpClient()), server.url("/"))
            provider.journeyPage(
                JourneyQuery(
                    initialFavorites()[0].location,
                    initialFavorites()[1].location,
                    Instant.parse("2026-10-25T14:00:00Z").toEpochMilli(),
                    TimeMode.ARRIVAL,
                    setOf(LineType.SUBWAY),
                )
            )
            val q = server.takeRequest().requestUrl!!
            assertEquals("arr", q.queryParameter("itdTripDateTimeDepArr"))
            assertEquals("1500", q.queryParameter("itdTime"))
            assertEquals("on", q.queryParameter("inclMOT_2"))
            assertNull(q.queryParameter("inclMOT_5"))
            assertEquals("coord", q.queryParameter("type_origin"))
        }
    }

    @Test
    fun highSpeedSelectionExplicitlyRemovesRegionalOnlyRestriction() {
        assertEquals("400", efaProductArgs(setOf(LineType.ICE))["lineRestriction"])
        assertEquals("403", efaProductArgs(setOf(LineType.REGIONAL))["lineRestriction"])
    }

    @Test
    fun malformedProviderPageIsAnErrorAlsoWithDefaultLocalFilters() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"trips":[{"legs":[]}]}"""))
            val provider = EfaTransitRepository(NetworkClient(OkHttpClient()), server.url("/"))
            val result = runCatching {
                provider.journeyPage(
                    JourneyQuery(initialFavorites()[0].location, initialFavorites()[1].location)
                )
            }
            assertTrue(result.exceptionOrNull() is TransitException)
        }
    }

    @Test
    fun unselectedJourneysRemainAvailableAsPaginationAnchors() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(javaClass.getResource("/efa-coordinate-trips.json")!!.readText())
            )
            val provider = EfaTransitRepository(NetworkClient(OkHttpClient()), server.url("/"))
            val page =
                provider.journeyPage(
                    JourneyQuery(
                        initialFavorites()[0].location,
                        initialFavorites()[1].location,
                        products = setOf(LineType.FERRY),
                    )
                )
            assertTrue(page.trips.isNotEmpty())
        }
    }
}
