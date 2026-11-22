// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.content.Context
import android.view.WindowManager
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.location.CurrentPositionSource
import dev.ambivalenton.openv.tracking.PinnedBubbleOverlay
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.annotation.Config

/** Acceptance group D (bubble) of build 65. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class Build65BubbleTest {
    private val inputs=PinnedBubbleInputs(modeEnabled=true,overlayAllowed=true,sessionId="s",dismissedForSession=false,deviceLocked=false)
    private val area=BubbleArea(0,80,1080,2200)

    @Test fun visibilityNeedsModeAccessPinAndAnUnlockedScreenAlsoInsideTheApp() {
        assertTrue(pinnedBubbleVisible(inputs))
        assertFalse(pinnedBubbleVisible(inputs.copy(modeEnabled=false)))
        assertFalse("denied or revoked access",pinnedBubbleVisible(inputs.copy(overlayAllowed=false)))
        assertFalse("pin removed or journey ended",pinnedBubbleVisible(inputs.copy(sessionId=null)))
        assertFalse("closed via the drop zone",pinnedBubbleVisible(inputs.copy(dismissedForSession=true)))
        assertFalse("lock screen",pinnedBubbleVisible(inputs.copy(deviceLocked=true)))
        assertTrue("on the lock screen it still stands in for the card",pinnedBubbleActive(inputs.copy(deviceLocked=true)))
        assertFalse("closed: the card returns",pinnedBubbleActive(inputs.copy(dismissedForSession=true)))
    }

    @Test fun tapAndDragAreSeparatedByTheTouchSlop() {
        assertTrue(bubbleIsTap(3f,4f,8f,120))
        assertFalse("moved beyond the slop",bubbleIsTap(9f,0f,8f,120))
        assertFalse("held too long",bubbleIsTap(0f,0f,8f,900))
        assertFalse(bubbleDragged(5f,5f,8f));assertTrue(bubbleDragged(0f,9f,8f))
    }

    @Test fun releaseHasBoundedMomentumAndDocksToTheNearerUsableEdge() {
        val calm=bubbleRestingPosition(700,900,140,0f,0f,area,20,6000f)
        assertTrue(calm.dockedRight);assertEquals(1080-140-20,calm.x);assertEquals(900,calm.y)
        val flungLeft=bubbleRestingPosition(700,900,140,-5000f,0f,area,20,6000f)
        assertFalse("momentum carries it to the left edge",flungLeft.dockedRight);assertEquals(20,flungLeft.x)
        val absurd=bubbleRestingPosition(500,900,140,0f,1e9f,area,20,6000f)
        assertTrue("clamped velocity and position stay on screen",absurd.y<=area.bottom-140-20)
        val top=bubbleRestingPosition(500,-400,140,0f,0f,area,20,6000f)
        assertEquals("never under the status bar or cutout",area.top+20,top.y)
    }

    @Test fun rotationKeepsTheSideAndRelativeHeightInsideTheNewArea() {
        val fraction=bubbleVerticalFraction(1000,140,area,20)
        val landscape=BubbleArea(120,0,2200,1080)
        val rotated=bubbleRestoredPosition(true,fraction,140,landscape,20)
        assertTrue(rotated.dockedRight);assertEquals(2200-140-20,rotated.x)
        assertTrue(rotated.y in 20..(1080-140-20))
        assertEquals(fraction,bubbleVerticalFraction(rotated.y,140,landscape,20),.01f)
    }

    @Test fun theCloseZoneSpansTheBottomAndTheCardStaysOnScreen() {
        assertTrue(bubbleInCloseZone(2150f,area,152));assertFalse(bubbleInCloseZone(1900f,area,152))
        assertEquals(20,bubbleCardX(false,20,140,560,area,20))
        val right=bubbleCardX(true,1080-140-20,140,560,area,20)
        assertTrue(right+560<=area.right-20+1)
    }

    private val now=1_800_000_000_000L
    private val a=Location("Start","a",51.5,7.2,"Test")
    private val b=Location("Ziel","b",51.6,7.3,"Test")
    private val trip=Trip("original",listOf(TripSegment("leg",a,b,now+600_000,now+1_200_000,LineType.BUS,"312")),"Test",now,refreshToken="token")
    private val positions=object:CurrentPositionSource {
        override fun hasPermission()=false
        override val lastPresence:DevicePresence?=null
        override suspend fun current():Location=error("no location")
    }
    private inner class Engine:TransitRepository {
        override val name="Test"
        override suspend fun locations(query:String)=emptyList<Location>()
        override suspend fun nearby(latitude:Double,longitude:Double,radiusMeters:Int)=emptyList<Location>()
        override suspend fun departures(stop:Location)=emptyList<Departure>()
        override suspend fun journeyPage(query:JourneyQuery,cursor:String?)=JourneyPage(listOf(trip))
        override suspend fun refresh(trip:Trip)=trip.copy(fetchedAt=trip.fetchedAt+1_000)
    }

    @Test fun closingTheBubbleKeepsThePinSurvivesRefreshAndRestartAndANewPinResetsIt()=runTest {
        val context=RuntimeEnvironment.getApplication()
        val controller=PinnedJourneyController(context,{Engine()},positions,now={now})
        controller.close()
        controller.pin(Favorite("f","Ziel",b),JourneyQuery(a,b,now),trip,AppSettings())
        val id=controller.state.value.session!!.id
        assertTrue(controller.setBubbleDismissed(id,true))
        controller.refreshOnce()
        val session=controller.state.value.session!!
        assertEquals("the pin stays",id,session.id);assertTrue("a refresh does not reopen it",session.bubbleDismissed)
        val restarted=PinnedJourneyController(context,{Engine()},positions,now={now});restarted.restore()
        assertTrue(restarted.state.value.session!!.bubbleDismissed)
        assertFalse("an old session id changes nothing",restarted.setBubbleDismissed("other",false))
        assertTrue(restarted.setBubbleDismissed(id,false));assertFalse(restarted.state.value.session!!.bubbleDismissed)
        restarted.setBubbleDismissed(id,true)
        restarted.pin(Favorite("f","Ziel",b),JourneyQuery(a,b,now),trip,AppSettings())
        assertFalse("pinning again shows the bubble again",restarted.state.value.session!!.bubbleDismissed)
        restarted.close()
    }

    @Test fun addingAndRemovingTheOverlayIsIdempotentAndLeavesNoWindow() {
        val context=RuntimeEnvironment.getApplication()
        val windows=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlay=PinnedBubbleOverlay(context) {}
        val session=PinnedJourneySession(favorite=Favorite("f","Ziel",b),query=JourneyQuery(a,b,now),initialTrip=trip,routing=AppSettings())
        overlay.update(PinnedJourneyState(session=session),AppSettings())
        overlay.show();overlay.show()
        assertEquals(1,Shadow.extract<ShadowWindowManagerImpl>(windows).views.size);assertTrue(overlay.showing)
        val params=Shadow.extract<ShadowWindowManagerImpl>(windows).views.single().layoutParams as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,params.type)
        assertEquals("only the bubble's own size, never full screen",WindowManager.LayoutParams.WRAP_CONTENT,params.width)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE!=0)
        overlay.hide();overlay.hide()
        assertEquals(0,Shadow.extract<ShadowWindowManagerImpl>(windows).views.size);assertFalse(overlay.showing)
        overlay.show();assertEquals(1,Shadow.extract<ShadowWindowManagerImpl>(windows).views.size);overlay.hide()
        assertEquals(0,Shadow.extract<ShadowWindowManagerImpl>(windows).views.size)
    }
}
