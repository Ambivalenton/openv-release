// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.NetworkCountdown
import dev.ambivalenton.openv.ui.network.EdgeDirectionDialog
import dev.ambivalenton.openv.ui.network.NetworkPlan
import dev.ambivalenton.openv.ui.network.NetworkPlanActions
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Build 66 network plan: live and edit gestures resolve to exactly one action each. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="de-rDE-w411dp-h891dp-420dpi")
class Build66NetworkPlanUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val now=java.time.ZonedDateTime.of(2027,1,15,9,0,0,0,ZoneId.of("Europe/Berlin"))
    private val home=Favorite("z","Zuhause",Location("Herne","z",51.54,7.22,"T"),icon=FavoriteIcon.HOME,accentSeed=0xFF245EA8)
    private val work=Favorite("a","Arbeit",Location("Dortmund","a",51.51,7.46,"T"),icon=FavoriteIcon.WORK,accentSeed=0xFF404B92)
    private val gym=Favorite("f","Fitness",Location("Bochum","f",51.48,7.21,"T"),icon=FavoriteIcon.FITNESS_CENTER)
    private val store=StoredState(favorites=listOf(home,work,gym),settings=AppSettings(quietMode=true,
        // Build 74 made "frei bewegen" the default hold; these checks are about the location hold.
        networkHoldAction=NetworkHoldAction.LOCATION),
        network=TransitNetwork(listOf(NetworkNode("z",GridPoint(2,5)),NetworkNode("a",GridPoint(8,5)),NetworkNode("f",GridPoint(2,9))),
            listOf(NetworkEdge("line","z","a")),columns=12,rows=12))
    private val side=360.dp
    private val calls=mutableListOf<String>()
    private val actions=NetworkPlanActions(
        onNodeTap={calls+="tap:$it"},onNodeHold={calls+="hold:$it"},onEdgeTap={calls+="line:$it"},onEdgeMenu={calls+="menu:$it"},
        onMove={id,p->calls+="move:$id:${p.x},${p.y}"},onConnect={a,b->calls+="connect:$a-$b";"new"},
        onFreePoint={p->calls+="free:${p.x},${p.y}"},onToTray={calls+="tray:$it"},onConnectRequest={calls+="pick:$it"})
    private fun show(editing:Boolean) = compose.setContent {OpenVTheme(store.settings) {Surface {
        NetworkPlan(store,now,editing,null,NetworkCountdown(),null,actions,Modifier.width(side))
    }}}
    private fun center(p:GridPoint):Offset {
        val pitch=with(compose.density) {side.toPx()}/12f
        return Offset((p.x+.5f)*pitch,(p.y+.5f)*pitch)
    }

    @Test fun liveTapSearchesHoldSetsTheLocationAndALineTapSearchesBetweenItsNodes() {
        show(editing=false)
        compose.onNodeWithTag("network-plan").performTouchInput {click(center(GridPoint(2,5)))}
        compose.runOnIdle {assertEquals(listOf("tap:z"),calls);calls.clear()}
        compose.onNodeWithTag("network-plan").performTouchInput {longClick(center(GridPoint(8,5)))}
        compose.runOnIdle {assertEquals(listOf("hold:a"),calls);calls.clear()}
        compose.onNodeWithTag("network-plan").performTouchInput {click(center(GridPoint(5,5)))}
        compose.runOnIdle {assertEquals(listOf("line:line"),calls);calls.clear()}
        // An empty spot does nothing in live mode.
        compose.onNodeWithTag("network-plan").performTouchInput {click(center(GridPoint(10,1)))}
        compose.runOnIdle {assertTrue(calls.isEmpty())}
    }

    @Test fun editTapEditsSwipeDrawsALineAndLineTapOpensTheMenu() {
        show(editing=true)
        compose.onNodeWithTag("network-plan").performTouchInput {click(center(GridPoint(2,9)))}
        compose.runOnIdle {assertEquals(listOf("tap:f"),calls);calls.clear()}
        // Swipe without holding from Fitness to Arbeit: a new line.
        compose.onNodeWithTag("network-plan").performTouchInput {
            down(center(GridPoint(2,9)))
            repeat(12) {i->moveTo(center(GridPoint(2,9))+(center(GridPoint(8,5))-center(GridPoint(2,9)))*((i+1)/12f));advanceEventTime(16)}
            up()
        }
        compose.runOnIdle {assertEquals(listOf("connect:f-a"),calls);calls.clear()}
        compose.onNodeWithTag("network-plan").performTouchInput {click(center(GridPoint(5,5)))}
        compose.runOnIdle {assertEquals(listOf("menu:line"),calls);calls.clear()}
        compose.onNodeWithTag("network-plan").performTouchInput {click(center(GridPoint(10,1)))}
        compose.runOnIdle {assertEquals(listOf("free:10,1"),calls);calls.clear()}
    }

    @Test fun holdingAndDraggingMovesTheNodeToAFreeGridPoint() {
        show(editing=true)
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("network-plan").performTouchInput {down(center(GridPoint(2,9)))}
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("network-plan").performTouchInput {
            repeat(10) {i->moveTo(center(GridPoint(2,9))+(center(GridPoint(6,9))-center(GridPoint(2,9)))*((i+1)/10f));advanceEventTime(16)}
            up()
        }
        compose.mainClock.autoAdvance=true
        compose.runOnIdle {assertEquals(listOf("move:f:6,9"),calls)}
    }

    @Test fun talkBackReachesEveryActionWithoutGestures() {
        show(editing=false)
        val node=compose.onNodeWithTag("network-node-z").fetchSemanticsNode()
        assertEquals(listOf("Zuhause"),node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals("Verbindung hierher suchen",node.config.getOrNull(SemanticsActions.OnClick)?.label)
        assertEquals("Als Standort festlegen",node.config.getOrNull(SemanticsActions.OnLongClick)?.label)
        val custom=node.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf("Verbindung Zuhause – Arbeit suchen"),custom.map {it.label})
        custom.single().action()
        compose.runOnIdle {assertEquals(listOf("line:line"),calls)}
    }

    @Test fun theDirectionMenuOffersBothOneWayChoicesAndRemoval() {
        var chosen:DirectionMode?=null;var removed=false
        compose.setContent {OpenVTheme(store.settings) {EdgeDirectionDialog(store,"line",{chosen=it},{removed=true},{})}}
        compose.onNodeWithText("Beide Richtungen (Zuhause ↔ Arbeit)").assertExists()
        compose.onNodeWithText("Von Arbeit nach Zuhause (Arbeit → Zuhause)").performClick()
        compose.runOnIdle {assertEquals(DirectionMode.B_TO_A,chosen)}
        compose.onNodeWithTag("edge-remove").performClick()
        compose.runOnIdle {assertTrue(removed)}
    }
}
