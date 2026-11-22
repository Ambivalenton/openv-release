// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.NetworkCountdown
import dev.ambivalenton.openv.ui.OverviewEntry
import dev.ambivalenton.openv.ui.OverviewUiState
import dev.ambivalenton.openv.ui.components.AppOverlayHost
import dev.ambivalenton.openv.ui.components.AppOverlayState
import dev.ambivalenton.openv.ui.components.LocalAppOverlay
import dev.ambivalenton.openv.ui.components.PinnedJourneyCard
import dev.ambivalenton.openv.ui.network.NetworkPlan
import dev.ambivalenton.openv.ui.network.NetworkPlanActions
import dev.ambivalenton.openv.ui.screens.FavoriteSettingsContent
import dev.ambivalenton.openv.ui.screens.OverviewScreen
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import dev.ambivalenton.openv.tracking.PinnedBubbleOverlay
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl

/** Build 68 on real composables: names on hold, the favourites pop-up, the platform at large text,
 * the overview with a "Haltestelle" favourite, and the bubble window without system slides. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="de-rDE-w411dp-h891dp-420dpi")
// Real text measurement: wrapping and large-font line heights are what these tests check.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build68UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val now=java.time.ZonedDateTime.of(2027,1,15,9,0,0,0,ZoneId.of("Europe/Berlin"))
    private val westStation=Location("Westbahnhof","h",51.54,9.22,"VRR")
    private val eastStation=Location("Ostbahnhof","d",51.51,9.46,"VRR")
    private val home=Favorite("z","Zuhause",westStation,icon=FavoriteIcon.HOME,accentSeed=0xFF245EA8)
    private val work=Favorite("a","Volkshochschule Stadtmitte",eastStation,icon=FavoriteIcon.SCHOOL,accentSeed=0xFF404B92)
    private val store=StoredState(favorites=listOf(home,work),settings=AppSettings(quietMode=true,
        // Build 74 made "frei bewegen" the default hold; these checks are about the location hold.
        networkHoldAction=NetworkHoldAction.LOCATION),
        network=TransitNetwork(listOf(NetworkNode("z",GridPoint(2,5)),NetworkNode("a",GridPoint(8,5))),listOf(NetworkEdge("line","z","a")),columns=12,rows=12))
    private val side=360.dp
    private fun center(p:GridPoint):Offset {val pitch=with(compose.density) {side.toPx()}/12f;return Offset((p.x+.5f)*pitch,(p.y+.5f)*pitch)}
    private fun plan(editing:Boolean,calls:MutableList<String> = mutableListOf()) = compose.setContent {OpenVTheme(store.settings) {Surface {
        NetworkPlan(store,now,editing,null,NetworkCountdown(),null,NetworkPlanActions(onNodeHold={calls+="hold:$it"},onEdgeTap={calls+="line:$it"}),Modifier.width(side))
    }}}

    @Test fun namesAppearOnlyWhileAFreeSpotIsHeldAndAlwaysWhileEditing() {
        val calls=mutableListOf<String>()
        plan(editing=false,calls)
        compose.onNodeWithTag("network-label-z",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("network-plan").performTouchInput {down(center(GridPoint(5,9)));advanceEventTime(900);moveTo(center(GridPoint(5,9)))}
        compose.waitForIdle()
        // The long name is complete: it wraps onto a second line instead of being cut. (The plates are
        // decoration for TalkBack; the node itself is announced with its name.)
        compose.onNodeWithTag("network-label-z",useUnmergedTree=true).assertExists()
        val short=compose.onNodeWithTag("network-label-z",useUnmergedTree=true).fetchSemanticsNode().size.height
        val long=compose.onNodeWithTag("network-label-a",useUnmergedTree=true).fetchSemanticsNode().size.height
        assertTrue("long name wraps ($long vs $short)",long>short*1.5f)
        compose.onNodeWithTag("network-plan").performTouchInput {up()}
        compose.waitForIdle()
        compose.onNodeWithTag("network-label-a",useUnmergedTree=true).assertDoesNotExist()
        // Holding a favourite still sets the location and shows no names.
        compose.onNodeWithTag("network-plan").performTouchInput {longClick(center(GridPoint(8,5)))}
        compose.runOnIdle {assertEquals(listOf("hold:a"),calls)}
        compose.onNodeWithTag("network-label-a",useUnmergedTree=true).assertDoesNotExist()
    }

    @Test fun editModeShowsEveryName() {
        plan(editing=true)
        compose.onNodeWithTag("network-label-z",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("network-label-a",useUnmergedTree=true).assertExists()
    }

    @Test fun theFavouriteListOpensInAPopUpAndTheSettingsStayOnTop() {
        val favorites=List(14) {i->Favorite("f$i","Ort $i",westStation.copy(id="h$i"))}
        val edited=mutableListOf<String>();var added=0
        val overlays=AppOverlayState()
        compose.setContent {OpenVTheme(AppSettings(quietMode=true)) {Surface {
            CompositionLocalProvider(LocalAppOverlay provides overlays) {AppOverlayHost(overlays,Modifier.fillMaxSize()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    FavoriteSettingsContent(StoredState(favorites=favorites),{added++},{edited+=it.id},{},{_,_->})
                }
            }}
        }}}
        // Without the list the sliders are visible at once, however many favourites exist.
        compose.onNodeWithTag("favorite-radius-setting").assertIsDisplayed()
        compose.onNodeWithTag("favorite-list-dialog").assertDoesNotExist()
        compose.onNodeWithTag("favorite-settings-list").assertTextEquals("Favoriten-Liste").performClick()
        compose.onNodeWithTag("favorite-list-dialog").assertExists()
        compose.onNodeWithTag("favorite-list-f13").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(listOf("f13"),edited)}
        compose.onNodeWithTag("favorite-settings-add").performClick()
        compose.runOnIdle {assertEquals(1,added)}
        compose.onNodeWithTag("favorite-list-close").performClick()
        compose.onNodeWithTag("favorite-list-dialog").assertDoesNotExist()
    }

    /** Build 67 cut "Nächster Einstieg: Gleis 2" after the first line at large system font
     * (Android 14+ scales large text non-linearly; the slot was computed linearly). */
    private fun platformAt(scale:Float) {
        RuntimeEnvironment.setFontScale(scale)
        try {
            val start=System.currentTimeMillis()+600_000
            val ride=TripSegment("ride",westStation,eastStation,start,start+1_800_000,LineType.SUBWAY,"U35",departurePlatform="U-Bahn Gleis 2")
            val trip=Trip("trip",listOf(ride),"TEST",start)
            val session=PinnedJourneySession(favorite=home,query=JourneyQuery(westStation,eastStation,start),initialTrip=trip,routing=AppSettings())
            val state=stabilizedAssistantState(PinnedJourneyState(),PinnedJourneyState(session,PinnedTrackingStatus.RUNNING),System.currentTimeMillis())
            compose.setContent {OpenVTheme(AppSettings(quietMode=true)) {Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(24.dp)) {PinnedJourneyCard(state,{},{})}
            }}}
            val node=compose.onNodeWithTag("card-platform",useUnmergedTree=true).fetchSemanticsNode()
            val layouts=mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            val layout=layouts.single()
            assertTrue("$scale: ${layout.layoutInput.text}",layout.layoutInput.text.text.contains("Gleis 2"))
            assertFalse("$scale: '${layout.layoutInput.text}' is cut",layout.hasVisualOverflow)
        } finally {RuntimeEnvironment.setFontScale(1f)}
    }
    @Test fun thePlatformIsShownAtNormalText()=platformAt(1f)
    @Test fun thePlatformIsShownAtLargeText()=platformAt(1.3f)
    @Test fun thePlatformIsShownAtTheLargestText()=platformAt(2f)

    @Test fun anOverviewWithAStopFavouriteDrawsItsSentence() {
        val stop=Favorite("s","Stadtmitte",eastStation,icon=FavoriteIcon.STOP)
        val trips=(0..5).map {i->Trip("t$i",listOf(TripSegment("r$i",westStation,eastStation,1_800_000_000_000+i*60_000L,1_800_001_200_000+i*60_000L,LineType.BUS,"390")),"TEST",0)}
        val entries=trips.mapIndexed {i,t->OverviewEntry(stop.copy(id="s$i"),i,JourneyQuery(westStation,eastStation),t,loading=false)}
        compose.setContent {OpenVTheme(AppSettings(quietMode=true)) {Surface {
            OverviewScreen(OverviewUiState(entries=entries,ready=true),OverviewSettings(showQuotes=false),{},{},{})
        }}}
        compose.onNodeWithTag("overview-s0").assertExists()
        compose.onAllNodesWithText("Stadtmitte",substring=true).fetchSemanticsNodes().let {assertTrue(it.isNotEmpty())}
    }

    @Test fun theBubbleWindowNeverGetsTheSystemSlide() {
        val context=RuntimeEnvironment.getApplication()
        val overlay=PinnedBubbleOverlay(context) {}
        val start=System.currentTimeMillis()+600_000
        val trip=Trip("trip",listOf(TripSegment("ride",westStation,eastStation,start,start+1_800_000,LineType.BUS,"390")),"TEST",start)
        overlay.update(PinnedJourneyState(PinnedJourneySession(favorite=home,query=JourneyQuery(westStation,eastStation,start),initialTrip=trip,routing=AppSettings())),AppSettings())
        overlay.show()
        shadowOf(Looper.getMainLooper()).idle()
        val params=Shadow.extract<ShadowWindowManagerImpl>(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).views
            .map {it.layoutParams as WindowManager.LayoutParams}.single {it.title=="Öpen-V bubble"}
        assertFalse("the right-hand bubble must not glide in from off screen",params.canPlayMoveAnimation())
        overlay.hide()
    }
}
