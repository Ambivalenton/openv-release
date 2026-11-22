// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
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

/** The merged search button on the map and calm chart times while the chart moves. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class Build91UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun bounds(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot

    @Test fun theMergedButtonStandsTenDpAboveTheMapEdgeWithTheMagnifierBeforeTheLabel() {
        val address=Location("Rottbruchstraße 41, 44625 Dortmund",null,51.4964,7.4655,"OSM",LocationKind.ADDRESS)
        val gateway=PlaceSearchGateway({listOf(address)},{flowOf(listOf(address))},true)
        val vm=(compose.activity.application as OpenVApp).koin.get<AppViewModel>()
        var searched=0;var expected=0f
        // H02: a search needs a start; without location access a manual start stands in.
        val settings=AppSettings(originMode=OriginMode.MANUAL,manualOrigin=Location("Dortmund Hbf","d",51.517,7.459,"VRR",LocationKind.STOP))
        compose.setContent {OpenVTheme(settings) {CompositionLocalProvider(LocalPlaceSearchGateway provides gateway) {
            expected=searchButtonHeight().value
            SearchScreen(AppUiState(store=StoredState(settings=settings),ready=true),vm,{_,_,_->searched++},{_,_,_->},{})
        }}}
        compose.onNode(hasSetTextAction() and hasText("Ziel, Adresse oder Haltestelle")).performTextInput("Rott")
        compose.onNodeWithText("Rottbruchstraße 41, 44625 Dortmund").performClick()
        compose.waitForIdle()
        val map=bounds("destination-map");val touch=bounds("search-submit");val label=bounds("search-submit-label")
        val margin=SearchButtonTouchMargin.value
        val visible=androidx.compose.ui.geometry.Rect(touch.left,touch.top+margin,touch.right,touch.bottom-margin)
        val facts="map $map button $visible label $label"
        // 10 dp taller than the former label box, wide, 10 dp above the map's lower edge, centred.
        assertEquals(facts,expected,visible.height,1f)
        assertEquals(facts,10f,map.bottom-visible.bottom,1.5f)
        assertTrue(facts,visible.width>=SearchButtonMinWidth.value-1f && visible.width>3*visible.height)
        assertEquals(facts,map.center.x,visible.center.x,1.5f)
        // The label is part of the button, to the right of the magnifier (24 dp + 10 dp gap).
        assertTrue(facts,label.left>=visible.left+20f+24f+10f-1f && label.right<=visible.right+.5f)
        assertTrue(facts,label.center.y in visible.top..visible.bottom)
        compose.onNodeWithTag("search-submit").assert(hasText("Nächste Verbindung suchen")).assertHasClickAction()
        // The touch target is at least 48 dp tall.
        assertTrue(touch.height>=48f-.5f)
        // Held down it only tilts in; the search starts with the tap.
        compose.onNodeWithTag("search-submit").performTouchInput {down(Offset(width*.15f,height/2f))}
        compose.waitForIdle();assertEquals(0,searched)
        compose.onNodeWithTag("search-submit").performTouchInput {up()}
        compose.waitForIdle();assertEquals(1,searched)
    }

    /** H03 (build 93): the surge runs around a button that can be used from the first frame. */
    @Test fun theButtonIsUsableWhileTheSurgeStillRuns() {
        compose.mainClock.autoAdvance=false
        var clicks=0
        compose.setContent {OpenVTheme {Column {LiquidSearchAction(null,1,true,true,onRevealed={},onClick={clicks++})}}}
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("search-submit").assertHasClickAction()
        compose.onNodeWithText("Verbindung suchen",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("search-submit").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.runOnIdle {assertEquals("a tap within the surge counts at once",1,clicks)}
        compose.mainClock.advanceTimeBy((LIQUID_SURGE_SECONDS*1000).toLong()+64)
        compose.onNodeWithTag("search-submit").assertHasClickAction()
        compose.onNodeWithTag("search-submit-falling").assertDoesNotExist()
    }

    @Test fun timesKeepTheirFormWhileTheChartIsDragged() {
        val now=System.currentTimeMillis()/60_000*60_000
        val a=Location("Bochum Hbf","a",51.47,7.22,"VRR");val m=Location("Essen Hbf","m",51.45,7.01,"VRR");val b=Location("Dortmund Hbf","b",51.51,7.45,"VRR")
        fun seg(id:String,from:Location,to:Location,s:Long,e:Long,t:LineType,n:String)=TripSegment(id,from,to,s,e,t,n)
        val trips=(0 until 12).map {i->
            val t0=now-20*60_000+i*11*60_000L
            val ride1=listOf(8,12,6,15,9,20)[i%6]*60_000L;val ride2=listOf(22,18,35,25,40,16)[i%6]*60_000L
            Trip("t$i",listOf(seg("w$i",a,a,t0,t0+4*60_000,LineType.WALK,"Fußweg"),
                seg("u$i",a,m,t0+4*60_000,t0+4*60_000+ride1,LineType.SUBWAY,"U49"),
                seg("r$i",m,b,t0+9*60_000+ride1,t0+9*60_000+ride1+ride2,LineType.REGIONAL,"RE3"),
                seg("e$i",b,b,t0+9*60_000+ride1+ride2,t0+15*60_000+ride1+ride2,LineType.WALK,"Fußweg")),"VRR",now)
        }
        val settings=AppSettings(minimalistMode=true)
        val ui=AppUiState(ready=true,store=StoredState(settings=settings),query=JourneyQuery(a,b,now),trips=LoadState(trips),
            journeyWindow=JourneyWindow(trips,now,now+3*3_600_000))
        compose.setContent {OpenVTheme(settings) {CompositionLocalProvider(LocalNavigationClearance provides 112.dp,LocalScreenTitleGap provides 14.dp) {
            ResultsScreen(ui,{},{},{},{},{},{},{})
        }}}
        compose.waitForIdle()
        fun tagged(prefix:String)=compose.onAllNodes(SemanticsMatcher(prefix) {node->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) ?: false
        },useUnmergedTree=true).fetchSemanticsNodes().associate {
            // Unclipped (columns slide out of view while dragging).
            it.config[SemanticsProperties.TestTag] to androidx.compose.ui.geometry.Rect(it.positionInRoot,
                androidx.compose.ui.geometry.Size(it.size.width.toFloat(),it.size.height.toFloat()))
        }
        /** Each time with its form (inside its bar, a chip above or below, at a walk) and its size. */
        fun clocks():Map<String,Pair<TimelineClockPlacement,androidx.compose.ui.geometry.Size>> =
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(TimelineClockForm),useUnmergedTree=true).fetchSemanticsNodes().associate {
                it.config[SemanticsProperties.TestTag] to (it.config[TimelineClockForm] to
                    androidx.compose.ui.geometry.Size(it.size.width.toFloat(),it.size.height.toFloat()))
            }
        val before=clocks();assertTrue(before.isNotEmpty());assertTrue("some times inside",before.values.any {it.first==TimelineClockPlacement.INSIDE})
        val chart=compose.onNodeWithTag("connection-chart")
        chart.performTouchInput {down(center)}
        repeat(8) {
            chart.performTouchInput {moveBy(Offset(-14f,0f));advanceEventTime(16)}
            compose.waitForIdle()
            val now=clocks()
            // While the finger moves, no time changes its size or form (no flicker, no shrinking).
            for((tag,form) in now) before[tag]?.let {
                assertEquals("$tag form while dragging",it.first,form.first)
                assertEquals("$tag width",it.second.width,form.second.width,.5f);assertEquals("$tag height",it.second.height,form.second.height,.5f)
            }
        }
        chart.performTouchInput {up()}
        compose.waitForIdle()
    }
}
