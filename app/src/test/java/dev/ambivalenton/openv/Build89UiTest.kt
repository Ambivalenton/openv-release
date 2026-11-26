// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.AppUiState
import dev.ambivalenton.openv.ui.AppViewModel
import dev.ambivalenton.openv.ui.components.LocalPlaceSearchGateway
import dev.ambivalenton.openv.ui.components.PlaceSearchGateway
import dev.ambivalenton.openv.ui.screens.SearchScreen
import kotlinx.coroutines.flow.flowOf
import dev.ambivalenton.openv.ui.NetworkCountdown
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

/**
 * A touch that Android takes away (the swipe up to the recent apps starts at the bottom edge,
 * where the network plan lies behind the navigation once a destination is chosen) is not a tap. It used
 * to search the favourite under the finger and open the bar chart without any tap on "Suchen".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-420dpi")
class Build89UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val now=java.time.ZonedDateTime.of(2027,1,15,9,0,0,0,ZoneId.of("Europe/Berlin"))
    private val home=Favorite("z","Zuhause",Location("Herne","z",51.54,7.22,"T"),icon=FavoriteIcon.HOME)
    private val work=Favorite("a","Arbeit",Location("Dortmund","a",51.51,7.46,"T"),icon=FavoriteIcon.WORK)
    private val gym=Favorite("f","Fitness",Location("Bochum","f",51.48,7.21,"T"),icon=FavoriteIcon.FITNESS_CENTER)
    private val store=StoredState(favorites=listOf(home,work,gym),settings=AppSettings(quietMode=true,networkHoldAction=NetworkHoldAction.MOVE),
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
    private val plan get()=compose.onNodeWithTag("network-plan")

    @Test fun aTouchTakenAwayBySystemGestureNeitherSearchesAFavouriteNorALine() {
        show(editing=false)
        plan.performTouchInput {down(center(GridPoint(2,5)));advanceEventTime(40);cancel()}
        plan.performTouchInput {down(center(GridPoint(5,5)));advanceEventTime(40);cancel()}
        compose.runOnIdle {assertEquals(emptyList<String>(),calls)}
        // A real tap still searches.
        plan.performTouchInput {click(center(GridPoint(2,5)))}
        compose.runOnIdle {assertEquals(listOf("tap:z"),calls)}
    }

    @Test fun aCancelledDragOrLineIsNotStored() {
        show(editing=true)
        // Hold Fitness, move it towards a free point, then the system takes the touch: it stays where it was.
        plan.performTouchInput {
            down(center(GridPoint(2,9)));advanceEventTime(800)
            repeat(8) {i->moveTo(center(GridPoint(2,9))+(center(GridPoint(6,10))-center(GridPoint(2,9)))*((i+1)/8f));advanceEventTime(16)}
            cancel()
        }
        // Draw a line from Fitness to Arbeit and lose the touch on the target: no line.
        plan.performTouchInput {
            down(center(GridPoint(2,9)))
            repeat(12) {i->moveTo(center(GridPoint(2,9))+(center(GridPoint(8,5))-center(GridPoint(2,9)))*((i+1)/12f));advanceEventTime(16)}
            cancel()
        }
        compose.runOnIdle {assertEquals(emptyList<String>(),calls)}
    }

    // The destination map of the search.
    private val address=Location("Rottbruchstraße 41, 44625 Dortmund",null,51.4964,7.4655,"OSM",LocationKind.ADDRESS,category=PlaceCategory.ADDRESS)
    private val gateway=PlaceSearchGateway({listOf(address)},{flowOf(listOf(address))},true)
    private fun search(settings:AppSettings=AppSettings(quietMode=true), searchStore: StoredState = StoredState()) {
        val vm=(compose.activity.application as OpenVApp).koin.get<AppViewModel>()
        compose.setContent {OpenVTheme(settings) {CompositionLocalProvider(LocalPlaceSearchGateway provides gateway) {
            SearchScreen(AppUiState(store=searchStore.copy(settings=settings),ready=true),vm,{_,_,_->},{_,_,_->},{})
        }}}
        compose.onNode(hasSetTextAction() and hasText("Ziel, Adresse oder Haltestelle")).performTextInput("Rott")
        compose.onNodeWithText("Rottbruchstraße 41, 44625 Dortmund").performClick()
        compose.waitForIdle()
    }
    private val field get()=compose.onNode(hasSetTextAction() and hasText("Ziel, Adresse oder Haltestelle"))
    private val map get()=compose.onNodeWithTag("destination-map")
    private fun mapCentre()=map.fetchSemanticsNode().size.let {Offset(it.width/2f,it.height/2f)}

    @Test fun choosingAPlaceShowsTheMapAndATapThereMovesTheDestination() {
        search()
        map.assertExists();compose.onNodeWithTag("search-submit").assertExists()
        field.assert(hasText("Rottbruchstraße 41, 44625 Dortmund",substring=true))
        map.performTouchInput {click(Offset(width*.25f,height*.3f))}
        compose.waitForIdle()
        field.assert(hasText("Kartenpunkt bei Rottbruchstraße 41, 44625 Dortmund",substring=true))
        // The map stays on the chosen place; resetting is offered to TalkBack.
        map.assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.CustomActions))
    }

    @Test fun thePinCanBeCarriedAndATakenAwayTouchPutsItBack() {
        search()
        val c=mapCentre()
        map.performTouchInput {down(c);advanceEventTime(40);repeat(10){i->moveTo(c+Offset(12f*(i+1),-8f*(i+1)));advanceEventTime(16)};cancel()}
        compose.waitForIdle()
        field.assert(hasText("Rottbruchstraße 41, 44625 Dortmund",substring=true))
        map.performTouchInput {down(c);advanceEventTime(40);repeat(10){i->moveTo(c+Offset(12f*(i+1),-8f*(i+1)));advanceEventTime(16)};up()}
        compose.waitForIdle()
        field.assert(hasText("Kartenpunkt bei",substring=true))
    }

    @Test fun clearingAndChoosingAgainKeepsThePlanEditorAndTheButtonInPlace() {
        search(searchStore = store)
        val list = compose.onNodeWithTag("search-list")
        val height = map.fetchSemanticsNode().size.height
        list.performScrollToNode(hasTestTag("network-edit"))
        compose.onNodeWithTag("network-edit").performClick()
        compose.waitForIdle()
        val button = compose.onNodeWithTag("search-submit").fetchSemanticsNode()
        val band = compose.onNodeWithTag("network-gesture-band", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("editor covers the search button", button.positionInRoot.y + button.size.height <= band.positionInRoot.y)
        list.performScrollToIndex(0)
        field.performTextClearance()
        compose.waitForIdle()
        map.assertDoesNotExist()
        compose.onNodeWithTag("search-submit").assertDoesNotExist()
        compose.onNodeWithTag("time-mode-pill").assertDoesNotExist()
        field.performTextInput("Rott")
        compose.onNodeWithText(address.name).performClick()
        compose.waitForIdle()
        assertEquals(height, map.fetchSemanticsNode().size.height)
        compose.onNodeWithTag("search-submit").assertHasClickAction()
        list.performScrollToNode(hasTestTag("network-done"))
        compose.onNodeWithTag("network-done").performClick()
        list.performScrollToIndex(0)
        compose.onNodeWithTag("time-mode-pill").assertExists()
        assertEquals(height, map.fetchSemanticsNode().size.height)
    }

    @Test fun powerSavingLeavesTheMapOut() {
        search(AppSettings(quietMode=true,powerSaving=true))
        compose.onNodeWithTag("search-submit").assertExists()
        map.assertDoesNotExist()
    }
}
