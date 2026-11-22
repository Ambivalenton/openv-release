// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.JourneyWindow
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.ResultsScreen
import dev.ambivalenton.openv.ui.screens.SearchScreen
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The fitted comparison chart and the search map's label, button and attribution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class Build90UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val now=System.currentTimeMillis()/60_000*60_000
    private val a=Location("Bochum Hbf","a",51.47,7.22,"VRR");private val m=Location("Essen Hbf","m",51.45,7.01,"VRR")
    private val b=Location("Dortmund Hbf","b",51.51,7.45,"VRR")
    private fun seg(id:String,from:Location,to:Location,s:Long,e:Long,t:LineType,n:String)=TripSegment(id,from,to,s,e,t,n)
    private val trips=(0 until 12).map {i->
        val t0=now-20*60_000+i*11*60_000L
        val ride1=listOf(8,12,6,15,9,20)[i%6]*60_000L;val ride2=listOf(22,18,35,25,40,16)[i%6]*60_000L
        Trip("t$i",listOf(seg("w$i",a,a,t0,t0+4*60_000,LineType.WALK,"Fußweg"),
            seg("u$i",a,m,t0+4*60_000,t0+4*60_000+ride1,LineType.SUBWAY,"U49"),
            seg("r$i",m,b,t0+9*60_000+ride1,t0+9*60_000+ride1+ride2,LineType.REGIONAL,"RE3"),
            seg("e$i",b,b,t0+9*60_000+ride1+ride2,t0+15*60_000+ride1+ride2,LineType.WALK,"Fußweg")),"VRR",now)
    }
    private val navigation=112.dp;private val gap=14.dp

    private fun results(minimal:Boolean=true) {
        val settings=AppSettings(minimalistMode=minimal,quietMode=true)
        val ui=AppUiState(ready=true,store=StoredState(settings=settings),query=JourneyQuery(a,b,now),trips=LoadState(trips),
            journeyWindow=JourneyWindow(trips,now,now+3*3_600_000))
        compose.setContent {OpenVTheme(settings) {CompositionLocalProvider(LocalNavigationClearance provides navigation,LocalScreenTitleGap provides gap) {
            ResultsScreen(ui,{},{},{},{},{},{},{})
        }}}
        compose.waitForIdle()
    }
    private fun bounds(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
    private fun durationBadges():List<Rect> = trips.mapNotNull {t->
        compose.onAllNodesWithTag("duration-${t.id}",useUnmergedTree=true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot}

    @Test fun theMinimalistChartKeepsAFixedMarginAndCannotBeMovedUpOrDown() {
        results()
        val top=bounds("results-content").top
        val header=bounds("trip-duration-header")
        // The duration row stands exactly the title's distance below the rule.
        assertEquals(gap.value,header.top-top,1f)
        compose.onNodeWithTag("connection-chart").performTouchInput {swipeUp(startY=height*.8f,endY=height*.2f)}
        compose.onNodeWithTag("connection-chart").performTouchInput {swipeDown(startY=height*.2f,endY=height*.8f)}
        compose.waitForIdle()
        assertEquals(header.top,bounds("trip-duration-header").top,.5f)
    }

    @Test fun aSwipeAlwaysRestsOnWholeColumnsThatShowCompletely() {
        results()
        val plot=bounds("connection-columns")
        val pillTop=bounds("results-content").bottom-navigation.value+PillNavigationTop.value
        repeat(3) {round->
            compose.onNodeWithTag("connection-chart").performTouchInput {
                down(center);repeat(8) {moveBy(androidx.compose.ui.geometry.Offset(-(23f+round*9),0f));advanceEventTime(30)};up()
            }
            compose.waitForIdle()
            val visible=durationBadges().filter {it.right>plot.left+1 && it.left<plot.right-1}
            assertTrue("at least five columns",visible.size>=5)
            // No column is cut at either edge once it rests.
            visible.forEach {assertTrue("whole column $it in $plot",it.left>=plot.left-1 && it.right<=plot.right+1)}
            // Every bar, time and footstep of the columns in view lies between the duration row and the pill.
            val header=bounds("trip-duration-header")
            val shown=trips.filter {t->compose.onAllNodesWithTag("duration-${t.id}",useUnmergedTree=true).fetchSemanticsNodes()
                .firstOrNull()?.boundsInRoot?.let {it.right>plot.left+1 && it.left<plot.right-1}==true}
            shown.forEach {t->
                val parts=compose.onAllNodes(SemanticsMatcher("parts of ${t.id}") {node->
                    node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.let {tag->
                        tag.startsWith("timeline-") && (tag.endsWith("-${t.id}") || tag.startsWith("timeline-segment-${t.id}-") ||
                            tag.startsWith("timeline-footprints-${t.id}-"))} ?: false
                },useUnmergedTree=true).fetchSemanticsNodes()
                assertTrue(parts.isNotEmpty())
                parts.forEach {node->
                    val r=node.boundsInRoot
                    assertTrue("${t.id} top ${r.top} below ${header.bottom}",r.top>=header.bottom-1)
                    assertTrue("${t.id} bottom ${r.bottom} above $pillTop",r.bottom<=pillTop+1)
                }
            }
        }
    }

    @Test fun theMapAttributionIsSmallInTheUpperRightAndTheButtonSearches() {
        val address=Location("Rottbruchstraße 41, 44625 Dortmund",null,51.4964,7.4655,"OSM",LocationKind.ADDRESS)
        val gateway=PlaceSearchGateway({listOf(address)},{flowOf(listOf(address))},true)
        val vm=(compose.activity.application as OpenVApp).koin.get<AppViewModel>()
        var searched=0
        // H02: a search needs a start; without location access a manual start stands in.
        val settings=AppSettings(quietMode=true,originMode=OriginMode.MANUAL,manualOrigin=Location("Dortmund Hbf","d",51.517,7.459,"VRR",LocationKind.STOP))
        compose.setContent {OpenVTheme(settings) {CompositionLocalProvider(LocalPlaceSearchGateway provides gateway) {
            SearchScreen(AppUiState(store=StoredState(settings=settings),ready=true),vm,{_,_,_->searched++},{_,_,_->},{})
        }}}
        compose.onNode(hasSetTextAction() and hasText("Ziel, Adresse oder Haltestelle")).performTextInput("Rott")
        compose.onNodeWithText("Rottbruchstraße 41, 44625 Dortmund").performClick()
        compose.waitForIdle()
        val map=bounds("destination-map");val credit=bounds("destination-map-attribution")
        // The attribution: small, in the upper right corner (The button position is in Build91UiTest).
        val facts="map $map credit $credit"
        assertTrue(facts,credit.top-map.top<12f && map.right-credit.right<16f && credit.height<16f)
        compose.onNodeWithTag("search-submit").performClick();compose.waitForIdle()
        assertEquals(1,searched)
    }
}
