// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.NetworkCountdown
import dev.ambivalenton.openv.ui.network.NetworkPlan
import dev.ambivalenton.openv.ui.network.NetworkPlanActions
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Assert.*
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PinnedNetworkJourneyUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = System.currentTimeMillis()
    private val home = Favorite("home", "Zuhause", Location("Herne", "home", 51.54, 7.22, "test"))
    private val work = Favorite("work", "Arbeit", Location("Dortmund", "work", 51.51, 7.46, "test"))
    private val trip = Trip("trip", listOf(TripSegment("ride", home.location, work.location,
        now - 600_000, now + 600_000, LineType.REGIONAL, "RE 1")), "test", now)
    private val session = PinnedJourneySession(favorite = work, query = JourneyQuery(home.location, work.location),
        initialTrip = trip, routing = AppSettings())
    private val store = StoredState(favorites = listOf(home, work), network = TransitNetwork(
        nodes = listOf(NetworkNode("home", GridPoint(0, 1)), NetworkNode("work", GridPoint(5, 1))),
        edges = listOf(NetworkEdge("line", "home", "work"))))

    @Test fun thePinnedVehicleReplacesAllDecorationAndReadsTheAssistantUpdates() {
        val pin = mutableStateOf<PinnedJourneyState?>(PinnedJourneyState(session = session,
            visualProgress = VisualJourneyProgress(1, true, now, fraction = .25f, stableLegId = "0:ride")))
        compose.setContent { OpenVTheme(AppSettings()) { Surface {
            NetworkPlan(store, ZonedDateTime.now(), false, null, NetworkCountdown(), null,
                NetworkPlanActions(), Modifier.width(360.dp), pinnedState = pin.value)
        } } }
        compose.onNodeWithTag("network-vehicles", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("network-pinned-vehicle").assertExists()
            .assertContentDescriptionContains("RE 1 · Gemessener Standort")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
                androidx.compose.ui.semantics.ProgressBarRangeInfo(.25f, 0f..1f)))
        compose.runOnIdle { pin.value = pin.value!!.copy(visualProgress = pin.value!!.visualProgress!!.copy(fraction = .75f)) }
        compose.onNodeWithTag("network-pinned-vehicle").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, androidx.compose.ui.semantics.ProgressBarRangeInfo(.75f, 0f..1f)))
        compose.runOnIdle { pin.value = null }
        compose.onNodeWithTag("network-pinned-vehicle").assertDoesNotExist()
        val expected = if (android.animation.ValueAnimator.areAnimatorsEnabled()) 1 else 0
        compose.onAllNodesWithTag("network-vehicles", useUnmergedTree = true).assertCountEquals(expected)
    }

    @Test fun quietPowerSavingAndTheDecorationSwitchKeepTheUsefulPinnedPosition() {
        compose.setContent { OpenVTheme(AppSettings(quietMode = true, powerSaving = true, networkVehicles = false)) { Surface {
            NetworkPlan(store, ZonedDateTime.now(), false, null, NetworkCountdown(), null, NetworkPlanActions(),
                Modifier.width(360.dp), pinnedState = PinnedJourneyState(session = session))
        } } }
        compose.onNodeWithTag("network-pinned-vehicle").assertExists()
            .assertContentDescriptionContains("RE 1 · Position nach Fahrplan")
        compose.onNodeWithTag("network-vehicles", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun unmatchedGpsHidesDecorationAndShowsTheTimetablePosition() {
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface {
            NetworkPlan(store, ZonedDateTime.now(), false, null, NetworkCountdown(), null, NetworkPlanActions(),
                Modifier.width(360.dp), pinnedState = PinnedJourneyState(session = session,
                    visualProgress = VisualJourneyProgress(0, true, now, positionSource = JourneyPositionSource.GPS_UNMATCHED)))
        } } }
        compose.onNodeWithTag("network-vehicles", useUnmergedTree = true).assertDoesNotExist()
        // 2.6: the train stays visible on its line, placed by the timetable until GPS fits the route.
        compose.onNodeWithTag("network-pinned-unknown").assertDoesNotExist()
        compose.onNodeWithTag("network-pinned-vehicle").assertExists()
            .assertContentDescriptionContains("RE 1 · Position nach Fahrplan, GPS wird zugeordnet")
    }

    @Test fun aDepartureChipCannotPaintOverTheLiveVehicle() {
        val departure=trip.copy(segments=listOf(trip.segments.first().copy(startTime=now+600_000,endTime=now+1_200_000)))
        val countdown=mutableStateOf(NetworkCountdown("home",true,listOf(
            dev.ambivalenton.openv.ui.NetworkCountdownChip("line","Arbeit",departure))))
        val fraction=mutableFloatStateOf(.5f)
        compose.setContent {OpenVTheme(AppSettings(quietMode=true,networkVehicles=false)) {Surface {
            NetworkPlan(store,ZonedDateTime.now(),false,null,countdown.value,null,NetworkPlanActions(),Modifier.width(360.dp),
                pinnedState=PinnedJourneyState(session=session,visualProgress=VisualJourneyProgress(1,true,now,
                    fraction=fraction.floatValue,stableLegId="0:ride")))
        }}}
        val chip=compose.onNodeWithTag("network-chip-line").fetchSemanticsNode().boundsInWindow
        val map=compose.onNodeWithTag("network-plan").fetchSemanticsNode().boundsInWindow
        val origin=compose.onNodeWithTag("network-node-home").fetchSemanticsNode().boundsInWindow.center
        val target=compose.onNodeWithTag("network-node-work").fetchSemanticsNode().boundsInWindow.center
        assertEquals("the test chip is on the vehicle's straight route",origin.y,chip.center.y,1f)
        val dp=compose.density.density
        val pitch=map.width/6
        val node=maxOf(pitch*.84f,minOf(pitch*.95f,34*dp)).coerceAtMost(68*dp)
        val width=maxOf(3*dp,pitch*.16f).coerceAtMost(8*dp)
        val length=target.x-origin.x
        val inset=minOf(length/2,node/2+width*1.5f)
        val at=(chip.center.x-origin.x-inset)/(length-2*inset)
        assertTrue("the caption can be reached by the vehicle",at in 0f..1f)
        compose.runOnIdle {fraction.floatValue=at}
        compose.waitForIdle()
        val movedChip=compose.onNodeWithTag("network-chip-line").fetchSemanticsNode().boundsInWindow
        assertFalse("the time card moves clear of the live vehicle",
            movedChip.overlaps(androidx.compose.ui.geometry.Rect(chip.center,width*1.5f)))
        fun capture(name:String):android.graphics.Bitmap {
            lateinit var image:android.graphics.Bitmap
            compose.runOnIdle {
                val view=compose.activity.window.decorView
                image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(image))
                val file=java.io.File("../audit/theme-catalog/$name.png");file.parentFile!!.mkdirs()
                file.outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }
            return image
        }
        val withClock=capture("network-live-vehicle-over-clock")
        compose.runOnIdle {countdown.value=NetworkCountdown()}
        compose.waitForIdle()
        val withoutClock=capture("network-live-vehicle-no-clock")
        val x=chip.center.x.roundToInt();val y=chip.center.y.roundToInt()
        for(dx in -1..1)for(dy in -1..1)assertEquals("the opaque vehicle stays above the clock",
            withoutClock.getPixel(x+dx,y+dy),withClock.getPixel(x+dx,y+dy))
        withClock.recycle();withoutClock.recycle()
    }
}
