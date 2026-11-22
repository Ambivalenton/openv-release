// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import dev.ambivalenton.openv.data.repository.GeocodingRepository
import dev.ambivalenton.openv.data.repository.NetworkClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import dev.ambivalenton.openv.data.repository.houseNumberMatches
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Address search with house numbers. Cause of "Keine passenden Orte gefunden": OSM (and so
 * Photon) does not know every house number. For such a query Photon returns only the street, and
 * the search dropped it because a house number in the query must match exactly. Now the street is
 * offered instead, marked "ohne Hausnummer"; a known house number still wins.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class Build67GeocodingTest {
    private fun house(number:String,city:String="Herne")="""{"type":"Feature","geometry":{"type":"Point","coordinates":[7.21,51.53]},"properties":{"street":"Bahnhofstraße","housenumber":"$number","postcode":"44623","city":"$city","countrycode":"DE","osm_key":"place","osm_value":"house"}}"""
    private val street="""{"type":"Feature","geometry":{"type":"Point","coordinates":[7.22,51.54]},"properties":{"name":"Bahnhofstraße","postcode":"44623","city":"Herne","countrycode":"DE","osm_key":"highway","osm_value":"residential"}}"""
    private fun shop(name:String,number:String)="""{"type":"Feature","geometry":{"type":"Point","coordinates":[7.215,51.535]},"properties":{"name":"$name","street":"Bahnhofstraße","housenumber":"$number","postcode":"44623","city":"Herne","countrycode":"DE","osm_key":"shop","osm_value":"mobile_phone"}}"""
        private fun body(vararg f:String)="""{"features":[${f.joinToString(",")}]}"""
    /** Photon stand-in: knows Bahnhofstraße 12 and 12a, but not 7. */
    private fun server()=MockWebServer().apply {
        dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val q=request.requestUrl!!.queryParameter("q").orEmpty()
                return MockResponse().setBody(when {
                    q.contains(" 2 ") || q.endsWith(" 2") -> body(shop("O2 Shop Herne","51"),shop("HaarKonzept 2.0","8a"),house("1-3"))
                    q.contains("12a") -> body(house("12a"))
                    q.contains(" 12") -> body(house("12"),house("120"))
                    q.contains(" 7") -> body() // unknown house number
                    else -> body(street)
                })
            }
        }
        start()
    }
    private fun repo(s:MockWebServer)=GeocodingRepository(NetworkClient(OkHttpClient.Builder().addInterceptor {chain->
        chain.proceed(chain.request().newBuilder().url(chain.request().url.newBuilder().scheme("http").host(s.hostName).port(s.port).build()).build())}.build()))

    @Test fun streetHouseNumberLetterAndCityAreFoundAndAnUnknownNumberFallsBackToTheStreet()=runBlocking {
        server().use {s->
            val r=repo(s)
            val plain=r.search("Bahnhofstraße",null,"https://photon.example")
            assertEquals(listOf("Bahnhofstraße, 44623 Herne"),plain.map {it.name})
            val twelve=r.search("Bahnhofstraße 12",null,"https://photon.example")
            assertEquals("12 never finds 120",listOf("Bahnhofstraße 12, 44623 Herne"),twelve.map {it.name})
            assertNull(twelve.single().note)
            assertEquals(listOf("Bahnhofstraße 12a, 44623 Herne"),r.search("Bahnhofstraße 12a",null,"https://photon.example").map {it.name})
            assertEquals(listOf("Bahnhofstraße 12, 44623 Herne"),r.search("Bahnhofstraße 12, Herne",null,"https://photon.example").map {it.name})
            // Not in the map data: the street stays selectable instead of an error.
            val unknown=r.search("Bahnhofstraße 7, Herne",null,"https://photon.example")
            assertEquals(listOf("Bahnhofstraße, 44623 Herne"),unknown.map {it.name})
            assertEquals("Hausnummer 7 nicht im Kartenmaterial – ohne Hausnummer",unknown.single().note)
            assertTrue(unknown.single().routable)
            // A range contains the number; a "2" inside a shop name ("O2", "2.0") is no house number.
            assertEquals(listOf("Bahnhofstraße 1-3, 44623 Herne"),r.search("Bahnhofstraße 2 Herne",null,"https://photon.example").map {it.name})
        }
    }

    @Test fun houseNumbersMatchExactlyWithLettersAndRanges() {
        assertTrue(houseNumberMatches("12","12"));assertTrue(houseNumberMatches("12","12a"));assertTrue(houseNumberMatches("12a","12 A"))
        assertTrue(houseNumberMatches("2","1-3"));assertTrue(houseNumberMatches("104","102-106"));assertTrue(houseNumberMatches("47","46–48"))
        assertFalse(houseNumberMatches("12","120"));assertFalse(houseNumberMatches("12","2"));assertFalse(houseNumberMatches("12a","12"))
        assertFalse(houseNumberMatches("12a","12b"));assertFalse(houseNumberMatches("5","1-3"));assertFalse(houseNumberMatches("2",null))
    }
}
