// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.localization.englishStrings
import dev.ambivalenton.openv.ui.network.*
import dev.ambivalenton.openv.ui.screens.SettingsPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Address search uses the typed form before retrying a normalized spelling. The mocked street and
 * house results use sample data. A second source (the EFA address directory)
 * only starts when the fast sources found nothing usable. Network plan: ten-plus designs and a switch
 * for the moving vehicles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Build70Test {
    private val query = "Hölzer Weg  14 Beispielstadt"
    private val photonHouse = Location("Hölzer Weg 14, 12345 Beispielstadt", latitude = 51.6195, longitude = 9.2005,
        provider = "OSM", kind = LocationKind.ADDRESS, city = "Beispielstadt")
    private val efaHouse = Location("Hölzer Weg 14, 12345 Beispielstadt", latitude = 51.619492, longitude = 9.200545,
        provider = "VRR", kind = LocationKind.ADDRESS, city = "Beispielstadt")
    private val streetOnly = Location("Hölzer Weg, 12345 Beispielstadt", latitude = 51.6263, longitude = 9.2037, provider = "OSM",
        kind = LocationKind.POI, city = "Beispielstadt", searchTerms = "Hölzer Weg, 12345 Beispielstadt Beispielstadt 14",
        note = "Hausnummer 14 nicht im Kartenmaterial – ohne Hausnummer")

    // ---- Photon spellings ----

    @Test fun photonGetsTheTypedTextFirstAndTheJoinedFormOnlyAsRetry() {
        assertEquals(listOf("Hölzer Weg 14 Beispielstadt", "holzerweg 14 beispielstadt"), photonSpellings(query))
        // Written together although the street is written apart: the retry splits the place adjective.
        assertEquals(listOf("Hölzerweg 14 Beispielstadt", "Hölzer Weg 14 Beispielstadt"), photonSpellings("Hölzerweg 14 Beispielstadt"))
        assertEquals("Bremer Straße 5", photonSpellings("Bremerstraße 5")[1])
        // Joined compounds stay joined; a typed form that equals the matching form needs no retry.
        assertEquals(listOf("Hauptstraße 5"), photonSpellings("Hauptstraße 5"))
        assertEquals(listOf("Rottbruch Str. 100", "rottbruchstrasse 100"), photonSpellings("Rottbruch Str. 100"))
        assertEquals(listOf("Neust", "neustrasse"), photonSpellings("Neust"))
    }

    @Test fun theStreetQueryKeepsTheTypedSpellingWithoutTheHouseNumber() {
        assertEquals("Hölzer Weg Beispielstadt", typedWithoutHouseNumber("Hölzer Weg 14, Beispielstadt"))
        assertEquals("Kortumstraße Bochum", typedWithoutHouseNumber("Kortumstraße 12 a Bochum"))
        assertEquals("Kortumstraße Bochum", typedWithoutHouseNumber("Kortumstraße 12a Bochum"))
        // A postcode is no house number; a leading number has no street before it.
        assertEquals("Hauptstraße 12347 Musterstadt", typedWithoutHouseNumber("Hauptstraße 5, 12347 Musterstadt"))
        assertNull(typedWithoutHouseNumber("12 Monkeys"))
        assertNull(typedWithoutHouseNumber("Hauptstraße"))
    }

    private fun photonServer(requests: MutableList<String>) = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val q = request.requestUrl!!.queryParameter("q").orEmpty(); requests += q
                // Only the spelling with separate words returns the matching house.
                val body = if (q.contains("Hölzer Weg")) """{"features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[9.2005,51.6195]},
                    "properties":{"street":"Hölzer Weg","housenumber":"14","postcode":"12345","city":"Beispielstadt","countrycode":"DE","osm_key":"building","osm_value":"yes"}}]}"""
                else """{"features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[8.95,51.57]},
                    "properties":{"name":"Gartenweg","postcode":"12346","city":"Nebenstadt","countrycode":"DE","osm_key":"highway","osm_value":"residential"}}]}"""
                return MockResponse().setBody(body)
            }
        }
        start()
    }
    private fun photon(s: MockWebServer) = GeocodingRepository(NetworkClient(OkHttpClient.Builder().addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().url(chain.request().url.newBuilder().scheme("http").host(s.hostName).port(s.port).build()).build())
    }.build()))

    @Test fun theTypedAddressIsFoundWithOneRequest() = runBlocking {
        val requests = mutableListOf<String>()
        photonServer(requests).use { s ->
            val rows = photon(s).search(query, null, "https://photon.example")
            assertEquals(listOf("Hölzer Weg 14, 12345 Beispielstadt"), rows.map { it.name })
            assertNull(rows.single().note)
            assertEquals(listOf("Hölzer Weg 14 Beispielstadt"), requests)
        }
    }

    @Test fun aJoinedSpellingFindsTheSeparateStreetWithTheSingleRetry() = runBlocking {
        val requests = mutableListOf<String>()
        photonServer(requests).use { s ->
            val rows = photon(s).search("Hölzerweg 14 Beispielstadt", null, "https://photon.example")
            assertEquals(listOf("Hölzer Weg 14, 12345 Beispielstadt"), rows.map { it.name })
            assertEquals(listOf("Hölzerweg 14 Beispielstadt", "Hölzer Weg 14 Beispielstadt"), requests)
        }
    }

    // ---- EFA address directory ----

    private val efaBody = """{"stopFinder":{"points":[
        {"usage":"sf","type":"any","name":"Beispielstadt, Hölzer Weg 14","anyType":"singlehouse","object":"Hölzer Weg","postcode":"12345",
         "street":"Hölzer Weg","buildingNumber":"14","ref":{"id":"1500000017","place":"Beispielstadt","coords":"9.200545,51.619492"}},
        {"usage":"sf","type":"any","name":"Beispielstadt, Hölzer Weg 140","anyType":"singlehouse","street":"Hölzer Weg","buildingNumber":"140",
         "postcode":"12345","ref":{"place":"Beispielstadt","coords":"9.21,51.63"}},
        {"usage":"sf","type":"any","name":"Herdecke, Hauptstraße 14","anyType":"singlehouse","street":"Hauptstraße","buildingNumber":"14",
         "postcode":"58313","ref":{"place":"Herdecke","coords":"7.43,51.40"}},
        {"usage":"sf","type":"any","name":"Beispielstadt, Hölzer Weg","anyType":"street","street":"Hölzer Weg","postcode":"12345",
         "ref":{"place":"Beispielstadt","coords":"9.203725,51.626268"}},
        {"usage":"sf","type":"any","name":"Beispielstadt, Hbf","anyType":"stop","ref":{"id":"20003582","place":"Beispielstadt","coords":"9.19,51.61"}}
        ]}}"""
    private fun efaRows() = Json.parseToJsonElement(efaBody).field("stopFinder").field("points").elements().mapNotNull(::efaAddressRow)

    @Test fun efaHousesAreAddressesWithCoordinatesAndExactHouseNumbers() {
        val rows = efaRows()
        assertEquals("stops are not part of the address directory", 4, rows.size)
        val house = acceptedAddressRows(query, rows)
        assertEquals(listOf("Hölzer Weg 14, 12345 Beispielstadt"), house.map { it.name })
        val first = house.single()
        assertEquals(LocationKind.ADDRESS, first.kind); assertNull(first.id)
        assertEquals(51.619492, first.latitude!!, 1e-9); assertEquals(9.200545, first.longitude!!, 1e-9)
        // Routed by its coordinates: EFA's ID of a house is not unique.
        assertEquals(mapOf("type_origin" to "coord", "name_origin" to coordinate(51.619492, 9.200545)), efaLocationArgs("origin", first))
        // An unknown number keeps the street selectable, clearly marked.
        val unknown = acceptedAddressRows("Hölzer Weg 999 Beispielstadt", rows)
        assertEquals(listOf("Hölzer Weg, 12345 Beispielstadt"), unknown.map { it.name })
        assertEquals("Hausnummer 999 nicht im Kartenmaterial – ohne Hausnummer", unknown.single().note)
    }

    @Test fun theEfaRequestAsksForStreetsAndHousesWithTheTypedText() = runBlocking {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody(efaBody)); s.start()
            val efa = EfaTransitRepository(NetworkClient(OkHttpClient()), s.url("/vrr/"))
            assertEquals(listOf("Hölzer Weg 14, 12345 Beispielstadt"), efa.addresses(query).map { it.name })
            val url = s.takeRequest().requestUrl!!
            assertEquals("XML_STOPFINDER_REQUEST", url.pathSegments.last())
            assertEquals("any", url.queryParameter("type_sf")); assertEquals("12", url.queryParameter("anyObjFilter_sf"))
            assertEquals("Hölzer Weg  14 Beispielstadt", url.queryParameter("name_sf"))
            // The ordinary stop search is unchanged: stops only.
            assertEquals(emptyList<Location>(), efa.addresses("ab"))
        }
    }

    // ---- staged search ----

    @Test fun theFallbackStaysSilentWhileTheFastSourcesFindTheAddress() = runTest {
        var calls = 0
        val search = FastPlaceSearch({ _, _ -> emptyList() }, { _, _, _ -> listOf(photonHouse) }, fallback = { _, _ -> calls++; listOf(efaHouse) })
        assertEquals(listOf(photonHouse), search.suggestions(query, StoredState(), null).last())
        assertEquals(0, calls)
    }

    @Test fun theFallbackAnswersWhenTheFastSourcesFoundNothing() = runTest {
        val asked = mutableListOf<String>()
        val search = FastPlaceSearch({ _, _ -> emptyList() }, { _, _, _ -> emptyList() }, fallback = { q, _ -> asked += q; listOf(efaHouse) })
        assertEquals(listOf(efaHouse), search.suggestions(query, StoredState(), null).last())
        assertEquals(listOf(query.trim()), asked)
        // The result is cached together with the fast sources: the next identical query asks nobody.
        assertEquals(listOf(efaHouse), search.suggestions(query, StoredState(), null).last()); assertEquals(1, asked.size)
    }

    @Test fun aSlowSearchStartsTheFallbackAfterTheGracePeriod() = runTest {
        val photon = CompletableDeferred<List<Location>>()
        var startedAt = -1L
        val search = FastPlaceSearch({ _, _ -> emptyList() }, { _, _, _ -> photon.await() }, networkDebounceMillis = 150,
            fallback = { _, _ -> startedAt = currentTime; listOf(efaHouse) })
        val values = mutableListOf<List<Location>>()
        val job = launch { search.suggestions(query, StoredState(), null).toList(values) }
        advanceTimeBy(1_300); runCurrent(); assertEquals("the fast sources keep their time", -1L, startedAt)
        advanceTimeBy(100); runCurrent(); assertEquals(1_350L, startedAt)
        assertEquals(listOf(efaHouse), values.last())
        photon.complete(emptyList()); job.join()
        assertEquals(listOf(efaHouse), values.last())
    }

    @Test fun aStreetStandInGivesWayToTheRealAddressAndStopsNeverAskTheFallback() = runTest {
        var calls = 0
        val search = FastPlaceSearch({ _, _ -> emptyList() }, { _, _, _ -> listOf(streetOnly) }, fallback = { _, _ -> calls++; listOf(efaHouse) })
        assertEquals(listOf(efaHouse), search.suggestions(query, StoredState(), null).last()); assertEquals(1, calls)
        // Without a real address the stand-in stays.
        assertEquals(listOf(streetOnly), withoutStreetFallbacks(query, listOf(streetOnly)))
        search.stopSuggestions("Hölzer Weg Beispielstadt", StoredState()).last(); assertEquals(1, calls)
        // Short or coordinate input never asks the extra source.
        assertFalse(wantsAddressFallback("Bo"))
        assertFalse(wantsAddressFallback("51.5, 7.2"))
    }

    @Test fun onlyGuessesStillLetTheFallbackLook() {
        val typo = photonHouse.copy(name = "Borstelweg 14, 23562 Lübeck", city = "Lübeck")
        assertFalse(hasUsablePlace("Hölzerweg 14", listOf(typo)))
        assertTrue(hasUsablePlace(query, listOf(photonHouse)))
        assertFalse(hasUsablePlace(query, listOf(streetOnly)))
    }

    // ---- network plan designs ----

    @Test fun moreThanTenIconicDesignsBesideOpenVAllTranslated() {
        val designs = NetworkPlanDesign.entries
        assertEquals(NetworkPlanDesign.OPENV, AppSettings().networkPlanDesign)
        assertTrue(designs.size >= 11)
        assertTrue(listOf("LONDON", "NEW_YORK", "TOKYO").all { name -> designs.any { it.name == name } })
        designs.forEach { d ->
            assertNotNull("${d.name} title", englishStrings[d.titleKey]); assertNotNull("${d.name} description", englishStrings[d.descriptionKey])
            if (d != NetworkPlanDesign.OPENV) assertTrue(d.name, d.look.palette!!.size >= 6)
        }
        assertEquals("Öpen-V keeps the plan as it was", StationMark.FAVORITE, NetworkPlanDesign.OPENV.look.station)
        assertNull(NetworkPlanDesign.OPENV.look.palette)
        assertEquals(designs.size, designs.map { it.look }.distinct().size)
    }

    @Test fun paletteColoursFollowTheFavouritesAndStayDistinct() {
        val london = NetworkPlanDesign.LONDON.look.palette!!
        val yellow = 0xFFE0C000.toInt(); val blue = 0xFF1030C0.toInt()
        assertEquals(0xFFFFD329, assignPaletteColors(listOf(yellow), london).single())
        assertEquals(0xFF0019A8, assignPaletteColors(listOf(blue), london).single())
        val many = assignPaletteColors(List(london.size) { yellow }, london)
        assertEquals("every line its own colour while the palette lasts", london.size, many.distinct().size)
        assertEquals(london.size + 1, assignPaletteColors(List(london.size + 1) { null }, london).size)
    }

    @Test fun darkSheetsLightenOperatorColoursToThreeToOne() {
        val dark = 0xFF14161A.toInt()
        NetworkPlanDesign.entries.mapNotNull { it.look.palette }.flatten().forEach { c ->
            assertTrue(ColorUtils.calculateContrast(readableLine(c, dark) or (0xFF shl 24), dark) >= 3.0)
        }
    }

    @Test fun settingsThemesAndOldDataKeepTheirDesign() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(AppSettings.serializer(), """{"quietMode":false}""")
        assertEquals(NetworkPlanDesign.OPENV, old.networkPlanDesign); assertTrue(old.networkVehicles)
        val tokyo = AppSettings(networkPlanDesign = NetworkPlanDesign.TOKYO)
        val theme = tokyo.personalDesign(AppLogo.ORIGINAL)
        assertEquals(NetworkPlanDesign.TOKYO, theme.networkPlan)
        assertEquals(NetworkPlanDesign.TOKYO, AppSettings().withPersonalDesign(theme).networkPlanDesign)
        // A theme saved before build 70 keeps whatever design is chosen now.
        assertEquals(NetworkPlanDesign.PARIS, AppSettings(networkPlanDesign = NetworkPlanDesign.PARIS).withPersonalDesign(theme.copy(networkPlan = null)).networkPlanDesign)
        assertEquals(SettingsPage.DESIGN, SettingsPage.NETWORK_PLAN.parent)
        assertTrue(SettingsPage.NETWORK_PLAN in SettingsPage.DESIGN.children)
    }

    @Test fun stillVehiclesStandOnTheOpenLine() {
        val runs = vehicleRuns(DirectionMode.BOTH, 0)
        val places = runs.map { stillVehiclePlacement(300f, 30f, 60f, it)!! }
        assertTrue(places.all { it.distance in 30f..270f && it.alpha == 1f })
        assertNotEquals(places[0].distance, places[1].distance)
        assertNull(stillVehiclePlacement(70f, 30f, 60f, runs[0]))
    }
}
