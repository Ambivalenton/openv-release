// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidPath
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.efaLocation
import dev.ambivalenton.openv.data.repository.efaModes
import dev.ambivalenton.openv.data.repository.parsePhotonLocation
import dev.ambivalenton.openv.localization.englishStrings
import dev.ambivalenton.openv.rendering.BoardAppPalette
import dev.ambivalenton.openv.rendering.appBadgeColors
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.searchActionPlacement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build86Test {
    private fun json(text:String):JsonElement=Json.parseToJsonElement(text)

    @Test fun everyPlaceCategoryHasAnEnglishLabelAndASymbol() {
        PlaceCategory.entries.forEach {
            assertNotNull(it.name,englishStrings[it.labelKey])
            assertTrue(placeCategoryIcon(it)!=0)
        }
        assertNotNull(englishStrings["Standort wählen"]);assertNotNull(englishStrings["Aktuellen GPS-Standort verwenden"])
        assertNotNull(englishStrings["Manuellen Standort festlegen"])
    }

    @Test fun stopsAreToldByTheirModesAndOtherwiseByTheirName() {
        assertEquals(PlaceCategory.STATION,efaStopCategory(setOf(0,5),"Bochum, Hbf"))
        assertEquals(PlaceCategory.SUBWAY,efaStopCategory(setOf(2,5),"Dortmund, Stadtgarten"))
        assertEquals(PlaceCategory.TRAM,efaStopCategory(setOf(4),"Essen, Rathaus"))
        assertEquals(PlaceCategory.BUS,efaStopCategory(setOf(5,6),"Herne, Schulstraße"))
        assertEquals(PlaceCategory.FERRY,efaStopCategory(setOf(9),"Duisburg, Anleger"))
        assertEquals(PlaceCategory.AIRPORT,efaStopCategory(setOf(1,5),"Düsseldorf Flughafen"))
        // Without modes (local DELFI directory) the name decides.
        assertEquals(PlaceCategory.STATION,stopCategoryFromName("Dortmund Hbf"))
        assertEquals(PlaceCategory.STATION,stopCategoryFromName("Wanne-Eickel Hauptbahnhof"))
        assertEquals(PlaceCategory.STATION,stopCategoryFromName("Hörde Bf"))
        assertEquals(PlaceCategory.BUS,stopCategoryFromName("Bochum ZOB"))
        assertEquals(PlaceCategory.STOP,stopCategoryFromName("Dortmund, Rottbruchstraße"))
        // "Bf" inside a word is no station.
        assertEquals(PlaceCategory.STOP,stopCategoryFromName("Bfeld Mitte"))
        val stop=Location("Dortmund Hbf","1",51.5,7.4,"DELFI")
        assertEquals(PlaceCategory.STATION,placeCategory(stop))
        assertEquals(PlaceCategory.FAVORITE,placeCategory(stop.copy(favoriteLabel="Arbeit")))
        assertEquals(PlaceCategory.ADDRESS,placeCategory(Location("Rottbruchstraße 41, 44625 Dortmund",null,51.5,7.4,"OSM",LocationKind.ADDRESS)))
        assertEquals(PlaceCategory.STREET,placeCategory(Location("Rottbruchstraße, Dortmund",null,51.5,7.4,"OSM",LocationKind.ADDRESS)))
        assertEquals(PlaceCategory.COORDINATE,placeCategory(Location("Punkt",null,51.5,7.4)))
    }

    @Test fun efaPointsCarryTheirTransportModes() {
        val point=json("""{"name":"Hauptbahnhof","type":"any","anyType":"stop","stateless":"20000131","modes":"0,1,2,3,5",
            "ref":{"id":"20000131","place":"Dortmund","coords":"7.459,51.517"}}""")
        assertEquals(setOf(0,1,2,3,5),efaModes(point))
        assertEquals(PlaceCategory.STATION,efaLocation(point,searchResult=true)!!.category)
        // Legs and boards keep the stored form.
        assertNull(efaLocation(point)!!.category)
        val bus=json("""{"name":"Schulstraße","stateless":"1","productClasses":[5,6],"ref":{"place":"Herne","coords":"7.2,51.5"}}""")
        assertEquals(PlaceCategory.BUS,efaLocation(bus,searchResult=true)!!.category)
        // Nested objects never recurse endlessly.
        assertEquals(emptySet<Int>(),efaModes(json("""{"modes":{"mode":{"mode":{"mode":{"mode":1}}}}}""")))
        // Presentation only: the category is never written into stored data.
        val stored=Json.encodeToString(TargetLocation.serializer(),efaLocation(point,searchResult=true)!!)
        assertFalse(stored,stored.contains("category"))
    }

    @Test fun photonKeysBecomeCategories() {
        fun photon(key:String,value:String,extra:String="")=parsePhotonLocation(json("""{"geometry":{"coordinates":[7.4,51.5]},
            "properties":{"name":"Ort","osm_key":"$key","osm_value":"$value","countrycode":"DE"$extra}}"""))!!.category
        assertEquals(PlaceCategory.STATION,photon("railway","station"))
        assertEquals(PlaceCategory.TRAM,photon("railway","tram_stop"))
        assertEquals(PlaceCategory.BUS,photon("highway","bus_stop"))
        assertEquals(PlaceCategory.CAFE,photon("amenity","cafe"))
        assertEquals(PlaceCategory.FOOD,photon("amenity","restaurant"))
        assertEquals(PlaceCategory.SHOP,photon("shop","bakery"))
        assertEquals(PlaceCategory.HEALTH,photon("amenity","hospital"))
        assertEquals(PlaceCategory.EDUCATION,photon("amenity","university"))
        assertEquals(PlaceCategory.PARK,photon("leisure","park"))
        assertEquals(PlaceCategory.CULTURE,photon("tourism","museum"))
        assertEquals(PlaceCategory.LOCALITY,photon("place","city"))
        assertEquals(PlaceCategory.AIRPORT,photon("aeroway","aerodrome"))
        assertEquals(PlaceCategory.ADDRESS,photon("building","yes",""","street":"Rottbruchstraße","housenumber":"41""""))
        assertEquals(PlaceCategory.STREET,photon("highway","residential",""","street":"Rottbruchstraße""""))
        assertEquals(PlaceCategory.STATION,restStopCategory(mapOf("regional" to true,"bus" to true),"Berlin Hbf"))
        assertEquals(PlaceCategory.SUBWAY,restStopCategory(mapOf("subway" to true,"bus" to true),"U Alexanderplatz"))
        assertEquals(PlaceCategory.ADDRESS,efaObjectCategory("singlehouse"));assertEquals(PlaceCategory.STREET,efaObjectCategory("street"))
    }

    @Test fun departureAndArrivalShareOneFontThatAlwaysFits() {
        // Wide enough: the labels keep their size and padding.
        assertEquals(1f,timeModeLabelFit(600,120,8f,32f,12f).scale)
        // Narrow or large system font: one smaller size for both labels, never below the readable floor.
        val fit=timeModeLabelFit(300,200,8f,32f,12f)
        assertTrue(fit.scale<1f && fit.scale>=.45f)
        val slot=(300-16)/2f
        assertTrue(200*fit.scale+2*fit.padding<=slot+.5f)
    }

    @Test fun theSearchButtonKeepsItsDistanceFromTheBottomOnEveryScreen() {
        for(viewport in listOf(780,900,1100,1300)) {
            val p=searchActionPlacement(viewport.dp,112.dp,260.dp)
            // Between the button's lower edge and the visible pill lies the pill's own height.
            val bottomFromTop=8.dp+260.dp+16.dp+p.buttonTop+64.dp
            val pillTop=viewport.dp-112.dp+dev.ambivalenton.openv.ui.components.PillNavigationTop
            assertEquals("viewport $viewport",dev.ambivalenton.openv.ui.components.PillNavigationHeight.value,(pillTop-bottomFromTop).value,.5f)
            // The favourites (next item) begin behind the navigation pill.
            val favourites=8.dp+260.dp+16.dp+p.height+16.dp
            assertTrue(favourites>=viewport.dp-112.dp)
        }
        // A small screen keeps the button directly below the controls.
        val small=searchActionPlacement(480.dp,112.dp,300.dp)
        assertEquals(0f,small.buttonTop.value,.01f);assertTrue(small.height>=LiquidSearchActionHeight)
    }

    @Test fun theDropFallsOntoThePillSplashesAndRunsDownBothEnds() {
        val pill=Rect(34f,1600f,788f,1740f)
        val path=androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(pill,androidx.compose.ui.geometry.CornerRadius(70f)))
        }
        val samples=sampleOutline(path.asAndroidPath(),3f,pill)!!
        val start=Offset(411f,1050f);val radius=54f
        val impact=samples.topIndex(start.x)
        assertEquals(pill.top,samples.points[impact].y,1f)
        // Melting keeps the button outline at first, falling accelerates, contact squashes it on the pill.
        val melt=liquidDropBody(0f,start,64f,radius,pill.top,2000f)!!
        assertEquals(0f,melt.morph,.001f);assertEquals(64f,melt.rx,.01f)
        val early=liquidDropBody(.40f,start,64f,radius,pill.top,2000f)!!
        val late=liquidDropBody(.70f,start,64f,radius,pill.top,2000f)!!
        assertTrue(late.center.y-early.center.y>early.center.y-start.y)
        val contact=liquidDropBody(LIQUID_IMPACT+.01f,start,64f,radius,pill.top,2000f)!!
        assertEquals(pill.top,contact.center.y+contact.ry,1f)
        assertNull(liquidDropBody(LIQUID_IMPACT+.2f,start,64f,radius,pill.top,2000f))
        // The film spreads to both ends and past the lower part of each end.
        val left=samples.dripDistance(impact,-1);val right=samples.dripDistance(impact,1)
        assertTrue(left>pill.width/2-80f && right>pill.width/2-80f)
        val film=liquidFilm(2.3f,radius,left+radius*.6f,right+radius*.6f)!!
        assertTrue(film.reachLeft>left && film.reachRight>right)
        assertTrue(filmThickness(0f,film,film.reachRight)>0f);assertEquals(0f,filmThickness(film.reachRight+1f,film,film.reachRight),0f)
        assertNotNull(filmArrival(left,radius,left+radius*.6f))
        assertEquals(0f,liquidFilm(2.499f,radius,left,right)!!.alpha,.01f)
        assertNull(liquidFilm(LIQUID_DROP_SECONDS,radius,left,right))
        // Drips hang, then fall.
        val hang=dripDrop(1.5f,1.45f,Offset(40f,1700f),12f)!!;val fall=dripDrop(1.95f,1.45f,Offset(40f,1700f),12f)!!
        assertTrue(fall.first.y>hang.first.y+40f)
        // Splash droplets fly up and come back down onto the pill.
        val hit=Offset(start.x,pill.top)
        assertTrue((0 until 7).any {i->(splashDroplet(i,7,LIQUID_IMPACT+.1f,hit,radius,pill)?.first?.y ?: Float.MAX_VALUE)<pill.top-20f})
        assertTrue((0 until 7).all {i->splashDroplet(i,7,LIQUID_IMPACT+.85f,hit,radius,pill)==null})
        // The whole outline of the film stays a closed, finite path.
        val outline=filmPath(samples,impact,film,2f)
        assertFalse(outline.getBounds().isEmpty);assertTrue(outline.getBounds().left.isFinite())
    }

    @Test fun theAppThemeBoardUsesThemeTonesForLines() {
        val pal=BoardAppPalette(0xff101010.toInt(),0xff202020.toInt(),0xff303030.toInt(),0xff91d4df.toInt(),0xff1c3a40.toInt(),
            0xffd0f0f5.toInt(),0xffbac9da.toInt(),0xffeeeeee.toInt(),0xffbbbbbb.toInt(),0xff444444.toInt(),0xffb3261e.toInt())
        for(type in LineType.entries) {
            val (fill,ink)=appBadgeColors(pal,0xff2a2a2a.toInt(),type)
            assertNotEquals(type.name,type.argb.toInt(),fill)
            assertTrue(type.name,androidx.core.graphics.ColorUtils.calculateContrast(ink,fill)>=3.0)
        }
        assertEquals(BoardBadgeTone.PRIMARY,boardBadgeTone(LineType.SUBURBAN));assertEquals(BoardBadgeTone.SECONDARY,boardBadgeTone(LineType.SUBWAY))
        assertEquals(BoardBadgeTone.TERTIARY,boardBadgeTone(LineType.BUS))
    }
}
