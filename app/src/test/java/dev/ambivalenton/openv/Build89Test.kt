// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.SearchMapAbovePill
import dev.ambivalenton.openv.ui.screens.searchActionPlacement
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.*

/** Search button position, destination map view and the folding of the map. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class Build89Test {
    private val place=Location("Rottbruchstraße 41, 44625 Dortmund",null,51.4964,7.4655,"OSM",LocationKind.ADDRESS)

    @Test fun theButtonKeepsThePillsHeightAboveThePillAndTheMapEndsJustAboveIt() {
        for(viewport in listOf(780,900,1100)) {
            val p=searchActionPlacement(viewport.dp,112.dp,260.dp)
            val top=8.dp+260.dp+16.dp
            val pill=viewport.dp-112.dp+PillNavigationTop
            assertEquals(PillNavigationHeight.value,(pill-(top+p.buttonTop+64.dp)).value,.5f)
            assertEquals(SearchMapAbovePill.value,(pill-(top+p.mapHeight)).value,.5f)
            // The button floats over the map, with room for its label below.
            assertTrue(p.buttonTop+64.dp<p.mapHeight)
        }
        // On a small screen there is no strip of a map; the button stays below the controls.
        val small=searchActionPlacement(560.dp,112.dp,300.dp)
        assertEquals(0f,small.mapHeight.value,.01f)
    }

    @Test fun theMapShowsThreeHundredMetresAroundTheChosenPlace() {
        val density=2.625f;val width=371*density;val height=520*density
        val camera=destinationMapCamera(place,width,height,density)!!
        val edge=camera.toMap(Offset(0f,height/2),width,height)
        val metres=distanceMeters(place,Location("edge",latitude=edge.latitude(),longitude=edge.longitude()))!!
        assertEquals(DESTINATION_MAP_RADIUS_METERS,metres,DESTINATION_MAP_RADIUS_METERS*.01)
        // Tiles of the nearest zoom: drawn at 0.71× to 1.41× of their own size, so labels stay readable.
        val size=camera.tilePixels/(256*density)
        assertTrue("tile scale $size",size in .7..1.42)
        assertTrue(camera.tiles(width,height).size in 2..48)
    }

    @Test fun aPointOnTheMapBecomesADestinationNamedAfterThePlace() {
        val camera=destinationMapCamera(place,800f,1000f,2f)!!
        val tap=Offset(610f,240f)
        val point=camera.toMap(tap,800f,1000f)
        val back=camera.toPx(point,800f,1000f)
        assertEquals(tap.x,back.x,.01f);assertEquals(tap.y,back.y,.01f)
        val destination=pinnedDestination(point,place)
        assertEquals("Kartenpunkt bei Rottbruchstraße 41, 44625 Dortmund",destination.name)
        assertTrue(destination.routable);assertEquals(LocationKind.COORDINATE,destination.kind)
        assertEquals(point.latitude(),destination.latitude!!,1e-9)
        // The centre of the map is the chosen place itself.
        val centre=camera.toMap(Offset(400f,500f),800f,1000f)
        assertEquals(place.latitude!!,centre.latitude(),1e-7);assertEquals(place.longitude!!,centre.longitude(),1e-7)
    }

    @Test fun theFoldedPanelsMeetAtTheirFoldsAndLieFlatWhenOpen() {
        val width=900f;val height=1200f;val panels=6;val distance=width*.5f
        // Flat: every panel where it belongs.
        accordionCenters(panels,width,1f,0f,1f,distance,centred=true).forEachIndexed {i,(src,dst)->
            assertEquals(width/panels*(i+.5f),src,.01f);assertEquals(src,dst,.01f)
        }
        // Half folded: neighbouring edges meet (same x and the same height), and the stack stays centred.
        val angle=55*PI.toFloat()/180;val c=cos(angle);val s=sin(angle)
        val centres=accordionCenters(panels,width,c,s,1f,distance,centred=true)
        val half=width/panels/2
        val edges=centres.mapIndexed {i,(src,dst)->
            val m=foldPanelMatrix(src,dst,height/2,c,(if(i%2==0)1f else -1f)*s,1f,distance,alongX=true)
            listOf(m.project(src-half,0f),m.project(src+half,0f),m.project(src-half,height),m.project(src+half,height))
        }
        for(i in 0 until panels-1) {
            assertEquals(edges[i][1].x,edges[i+1][0].x,.5f)
            assertEquals(edges[i][1].y,edges[i+1][0].y,.5f);assertEquals(edges[i][3].y,edges[i+1][2].y,.5f)
        }
        assertEquals(width/2,(edges.first()[0].x+edges.last()[1].x)/2,.5f)
        assertTrue(edges.last()[1].x-edges.first()[0].x<width*.8f)
        // Folding up from the bottom: the stack hangs from the top edge.
        val rows=accordionCenters(5,height,.4f,sqrt(1-.16f),1f,height*1.3f,centred=false)
        val first=foldPanelMatrix(rows[0].first,rows[0].second,width/2,.4f,sqrt(1-.16f),1f,height*1.3f,alongX=false)
        assertEquals(0f,first.project(width/2,0f).y,.5f)
    }
}
