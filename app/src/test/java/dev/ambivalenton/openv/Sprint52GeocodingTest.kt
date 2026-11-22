// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv
import android.app.Application
import dev.ambivalenton.openv.data.repository.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class Sprint52GeocodingTest {
    private fun feature(street:String="Rottbruchstraße",number:String="100",city:String="Herne",country:String="DE")="""{"type":"Feature","geometry":{"type":"Point","coordinates":[7.21,51.53]},"properties":{"street":"$street","housenumber":"$number","city":"$city","countrycode":"$country"}}"""
    private fun response(vararg f:String)=MockResponse().setBody("""{"features":[${f.joinToString(",")}]}""")
    private fun repo(s:MockWebServer)=GeocodingRepository(NetworkClient(OkHttpClient.Builder().addInterceptor{chain->chain.proceed(chain.request().newBuilder().url(chain.request().url.newBuilder().scheme("http").host(s.hostName).port(s.port).build()).build())}.build()))
    @Test fun germanCountryIsRequestedAndForeignRowsAreRejectedEvenInsideTheBoundingBox()=runBlocking{
        MockWebServer().use{s->s.enqueue(response(feature(country="NL"),feature(),feature(country="US")));s.start();val r=repo(s)
            val rows=r.search("rottbruchst 100",null,"https://photon.example");assertEquals(1,rows.size)
            // Photon gets the text as typed first (it understands "st"/"Str." itself).
            val q=s.takeRequest().requestUrl!!;assertEquals("DE",q.queryParameter("countrycode"));assertEquals("rottbruchst 100",q.queryParameter("q"))
            assertEquals(rows,r.search("  Rottbruchst   100 ",null,"https://photon.example"));assertEquals(1,s.requestCount)
        }
    }
    @Test fun streetRootHouseAndCityMustAllFitInTheScreenshotExample()=runBlocking{
        MockWebServer().use{s->s.enqueue(response(feature("Herner Straße","55","Bochum"),feature("Herner Straße","55","Recklinghausen"),feature("Rottbruchstraße","550"),feature("Rottbruchstraße","55")));s.start()
            val rows=repo(s).search("rottbruchstr 55 Hern",null,"https://photon.example");assertEquals(1,rows.size);assertTrue(rows.single().name.contains("Rottbruchstraße 55"));assertEquals("Herne",rows.single().city)
        }
    }
    @Test fun aPartialStreetUsesAtMostOneCanonicalRetry()=runBlocking{
        MockWebServer().use{s->s.enqueue(response());s.enqueue(response(feature("Neustraße","5")));s.start()
            assertEquals(1,repo(s).search("Neust",null,"https://photon.example").size);assertEquals(2,s.requestCount)
            assertEquals("Neust",s.takeRequest().requestUrl!!.queryParameter("q"));assertEquals("neustrasse",s.takeRequest().requestUrl!!.queryParameter("q"))
        }
    }
    @Test fun unknownCountriesAreRejectedAndDocumentedGermanCountryFallbackIsAccepted(){
        assertTrue(isGermanPhotonFeature(Json.parseToJsonElement(feature())));assertFalse(isGermanPhotonFeature(Json.parseToJsonElement(feature(country=""))))
        assertTrue(isGermanPhotonFeature(Json.parseToJsonElement(feature().replace("\"countrycode\":\"DE\"","\"country\":\"Germany\""))))
    }
}
