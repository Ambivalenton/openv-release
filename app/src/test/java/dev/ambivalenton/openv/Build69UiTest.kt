// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.AppUiState
import dev.ambivalenton.openv.ui.AppViewModel
import dev.ambivalenton.openv.ui.NetworkCountdown
import dev.ambivalenton.openv.ui.NetworkCountdownChip
import dev.ambivalenton.openv.ui.components.AppOverlayHost
import dev.ambivalenton.openv.ui.components.AppOverlayState
import dev.ambivalenton.openv.ui.components.LocalAppOverlay
import dev.ambivalenton.openv.ui.network.NetworkPlan
import dev.ambivalenton.openv.ui.network.NetworkPlanActions
import dev.ambivalenton.openv.ui.screens.SearchScreen
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Build 69 on real composables: the nearby mark follows the position on the search tab, a virtual
 * location that no longer applies loses its pin, departure times never overlap, held names stand out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build69UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val now=java.time.ZonedDateTime.of(2027,1,15,9,0,0,0,ZoneId.of("Europe/Berlin"))
    private fun place(name:String,id:String,lat:Double,lon:Double)=Location(name,id,lat,lon,"VRR")
    private val westStation=place("Westbahnhof","h",51.54,9.22)
    private val homePlace=place("Zuhause","z",51.55,9.20)
    private val parkPlace=place("Park","x",51.56,9.25)
    private val eastStation=place("Ostbahnhof","d",51.51,9.46)
    private val station=Favorite("h","Westbahnhof",westStation,icon=FavoriteIcon.STOP,accentSeed=0xFF4C7A5A)
    private val home=Favorite("z","Zuhause",homePlace,icon=FavoriteIcon.HOME,accentSeed=0xFF2E7D7A)
    private val park=Favorite("x","Park",parkPlace,accentSeed=0xFFB0465E)
    private val work=Favorite("d","Bibliothek Mitte",eastStation,icon=FavoriteIcon.WORK,accentSeed=0xFF7A5C9E)
    // Layout of the build 68 screenshot: three lines leave Westbahnhof upwards, two of them side by side.
    private val network=TransitNetwork(listOf(NetworkNode("h",GridPoint(5,10)),NetworkNode("z",GridPoint(2,1)),
        NetworkNode("x",GridPoint(6,1)),NetworkNode("d",GridPoint(10,3))),
        listOf(NetworkEdge("red","h","z"),NetworkEdge("yellow","h","x"),NetworkEdge("purple","h","d")),columns=12,rows=12)
    private val store=StoredState(favorites=listOf(station,home,park,work),network=network,settings=AppSettings(quietMode=true))
    private val side=360.dp

    @Before fun clean()=ProximityMemory.reset()
    @After fun restore() {ProximityMemory.reset();RuntimeEnvironment.setFontScale(1f)}

    private fun state(tag:String)=compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
    private fun north(of:Location,meters:Double)=Location("Hier",null,of.latitude!!+meters/111_195.0,of.longitude,null)
    private fun chip(edge:String,target:String,minute:Int,realtime:Boolean=true):NetworkCountdownChip {
        val start=now.toInstant().toEpochMilli()+minute*60_000L
        val ride=TripSegment("r$edge",westStation,eastStation,start,start+900_000,LineType.BUS,"390",realtimeDeparture=realtime)
        return NetworkCountdownChip(edge,target,Trip("t$edge",listOf(ride),"TEST",start))
    }
    private val chips=NetworkCountdown("h",true,listOf(chip("red","Zuhause",36),chip("yellow","Park",33),chip("purple","Bibliothek Mitte",33,realtime=false)))

    /** Build 68 drew the mark from the overview, which recalculates only while its tab is open:
     * driving away from home left "Zuhause" marked on the search tab. */
    @Test fun theNearbyMarkFollowsThePositionOnTheSearchTab() {
        val vm=(compose.activity.application as OpenVApp).koin.get<AppViewModel>()
        val gps=store.copy(settings=store.settings.copy(originMode=OriginMode.AUTOMATIC,favoriteRadiusMeters=500))
        val current=mutableStateOf(gps.copy(presence=DevicePresence(north(homePlace,80.0),System.currentTimeMillis(),8f)))
        compose.setContent {
            val overlays=remember {AppOverlayState()}
            CompositionLocalProvider(LocalAppOverlay provides overlays) {
                OpenVTheme(current.value.settings) {AppOverlayHost(overlays,Modifier.fillMaxSize()) {
                    SearchScreen(AppUiState(store=current.value),vm,{_,_,_->},{_,_,_->},{},countdown=chips)
                }}
            }
        }
        compose.onNodeWithTag("network-node-z").performScrollTo()
        assertTrue(state("network-node-z"),state("network-node-z").contains("In der Nähe"))
        // The summary line below the plan is gone; the times are on the lines.
        compose.onNodeWithTag("network-countdown").assertDoesNotExist()
        compose.onNodeWithTag("network-chip-red",useUnmergedTree=true).assertExists()
        // 3 km away with a fresh, accurate fix: the mark goes without opening the overview.
        compose.runOnIdle {current.value=gps.copy(presence=DevicePresence(north(homePlace,3_000.0),System.currentTimeMillis(),8f))}
        compose.waitForIdle()
        assertFalse(state("network-node-z"),state("network-node-z").contains("In der Nähe"))
        // Back near Westbahnhof with a fix taken after the minute clock last ticked (build 68 judged it
        // "not fresh" until the next minute): the mark moves there at once.
        compose.runOnIdle {current.value=gps.copy(presence=DevicePresence(north(westStation,50.0),System.currentTimeMillis(),8f))}
        compose.waitForIdle()
        assertTrue(state("network-node-h"),state("network-node-h").contains("In der Nähe"))
        assertFalse(state("network-node-z").contains("In der Nähe"))
    }

    @Test fun aVirtualLocationThatNoLongerAppliesLosesItsPin() {
        val override=UserLocationOverride("z",homePlace,0L,OriginMode.AUTOMATIC)
        val current=mutableStateOf(store.copy(locationOverride=override,settings=store.settings.copy(originMode=OriginMode.MANUAL,manualOrigin=homePlace)))
        compose.setContent {OpenVTheme(current.value.settings) {Surface {
            NetworkPlan(current.value,now,false,null,NetworkCountdown(),null,NetworkPlanActions(),Modifier.width(side))
        }}}
        assertTrue(state("network-node-z").contains("Virtueller Standort"))
        // Switched to GPS while the old override is still stored: no pin any more.
        compose.runOnIdle {current.value=current.value.copy(settings=current.value.settings.copy(originMode=OriginMode.AUTOMATIC))}
        compose.waitForIdle()
        assertFalse(state("network-node-z").contains("Virtueller Standort"))
    }

    private fun chipsDoNotOverlap(scale:Float) {
        RuntimeEnvironment.setFontScale(scale)
        compose.setContent {OpenVTheme(store.settings) {Surface {
            NetworkPlan(store,now,false,"h",chips,null,NetworkPlanActions(),Modifier.width(side))
        }}}
        compose.waitForIdle()
        val rects=listOf("red","yellow","purple").associateWith {compose.onNodeWithTag("network-chip-$it",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot}
        val plan=compose.onNodeWithTag("network-plan").fetchSemanticsNode().boundsInRoot
        val list=rects.entries.toList()
        for (i in list.indices) for (j in i+1 until list.size)
            assertFalse("$scale: ${list[i].key} ${list[i].value} / ${list[j].key} ${list[j].value}",list[i].value.overlaps(list[j].value))
        rects.forEach {(id,r)->
            assertTrue("$scale: $id outside",r.left>=plan.left-1f && r.right<=plan.right+1f)
            // The chip grows with its text: "16:36" is never cut at large system font.
            assertTrue("$scale: $id too small for its text",r.width>r.height*1.6f)
        }
        // A time without a forecast says so to TalkBack.
        val purple=compose.onNodeWithTag("network-chip-purple",useUnmergedTree=true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
        assertTrue(purple,purple.contains("keine Echtzeitprognose"))
    }
    @Test fun departureTimesNeverOverlapAtNormalText()=chipsDoNotOverlap(1f)
    @Test fun departureTimesNeverOverlapAtLargeText()=chipsDoNotOverlap(1.3f)

    @Test fun heldNamesAreLargerThanTheCalmEditNames() {
        val editing=mutableStateOf(true)
        compose.setContent {OpenVTheme(store.settings) {Surface {
            NetworkPlan(store,now,editing.value,null,NetworkCountdown(),null,NetworkPlanActions(),Modifier.width(side))
        }}}
        val calm=compose.onNodeWithTag("network-label-x",useUnmergedTree=true).fetchSemanticsNode().size.height
        compose.runOnIdle {editing.value=false}
        compose.waitForIdle()
        val pitch=with(compose.density) {side.toPx()}/12f
        compose.onNodeWithTag("network-plan").performTouchInput {down(Offset(11.5f*pitch,11.5f*pitch));advanceEventTime(900);moveTo(Offset(11.5f*pitch,11.5f*pitch))}
        compose.waitForIdle()
        val held=compose.onNodeWithTag("network-label-x",useUnmergedTree=true).fetchSemanticsNode().size.height
        assertTrue("held $held vs calm $calm",held>calm)
        compose.onNodeWithTag("network-plan").performTouchInput {up()}
    }
}
