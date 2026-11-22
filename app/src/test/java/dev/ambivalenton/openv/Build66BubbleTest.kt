// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.WindowManager
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.location.CurrentPositionSource
import dev.ambivalenton.openv.tracking.PinnedBubbleCoordinator
import dev.ambivalenton.openv.tracking.PinnedBubbleOverlay
import dev.ambivalenton.openv.tracking.PinnedJourneyService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/** Build 66 bubble fixes: card or visible bubble (never neither), shadow margin, way back, timeline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class Build66BubbleTest {
    private val now=1_800_000_000_000L
    private val a=Location("Start","a",51.5,7.2,"Test")
    private val b=Location("Ziel","b",51.6,7.3,"Test")
    private val trip=Trip("original",listOf(TripSegment("leg",a,b,now+600_000,now+1_200_000,LineType.BUS,"312")),"Test",now,refreshToken="token")
    private val positions=object:CurrentPositionSource {
        override fun hasPermission()=false
        override val lastPresence:DevicePresence?=null
        override suspend fun current():Location=error("no location")
    }
    private val engine=object:TransitRepository {
        override val name="Test"
        override suspend fun locations(query:String)=emptyList<Location>()
        override suspend fun nearby(latitude:Double,longitude:Double,radiusMeters:Int)=emptyList<Location>()
        override suspend fun departures(stop:Location)=emptyList<Departure>()
        override suspend fun journeyPage(query:JourneyQuery,cursor:String?)=JourneyPage(listOf(trip))
        override suspend fun refresh(trip:Trip):Trip=trip
    }
    private val context=RuntimeEnvironment.getApplication()
    private val saved=MutableStateFlow(StoredState())
    private val locations=LocationRepository(object:androidx.datastore.core.DataStore<StoredState> {
        override val data=saved
        override suspend fun updateData(transform:suspend(StoredState)->StoredState)=transform(saved.value).also {saved.value=it}
    })
    private val controller=PinnedJourneyController(context,{engine},positions,now={now})
    private val coordinator=PinnedBubbleCoordinator(context,controller,locations)
    private fun windows()=Shadow.extract<ShadowWindowManagerImpl>(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).views
    private fun waitFor(why:String,condition:()->Boolean) {
        val deadline=System.currentTimeMillis()+10_000
        while(!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if(System.currentTimeMillis()>deadline)fail(why)
            Thread.sleep(10)
        }
    }

    @After fun tearDown() {coordinator.stop();runBlocking {controller.close()}}

    @Test fun theCardHidesOnlyWhileTheBubbleIsReallyAttached() {
        ShadowSettings.setCanDrawOverlays(true)
        runBlocking {controller.close();locations.updateSettings {it.copy(pinnedBubble=true)}}
        coordinator.start()
        runBlocking {controller.pin(Favorite("f","Ziel",b),JourneyQuery(a,b,now),trip,AppSettings())}
        waitFor("bubble shown") {windows().size==1 && coordinator.shown.value}
        // Screen off: the bubble window goes away while the pin still asks for a bubble.
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        waitFor("bubble hidden on the lock screen") {windows().isEmpty()}
        assertTrue("the bubble is still wanted",coordinator.active.value)
        assertFalse("but the card must return: never neither card nor bubble",coordinator.shown.value)
        context.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        waitFor("bubble back after unlocking") {windows().size==1 && coordinator.shown.value}
        // Closing: overlay away, card back, the pin and the companion stay.
        val id=controller.state.value.session!!.id
        runBlocking {controller.setBubbleDismissed(id,true)}
        waitFor("closed") {windows().isEmpty() && !coordinator.shown.value}
        assertEquals(id,controller.state.value.session?.id)
    }

    @Test fun theWindowLeavesRoomForTheWholeShadowAndReportsItsAttachment() {
        val attached=MutableStateFlow(false)
        val overlay=PinnedBubbleOverlay(context,attached) {}
        val session=PinnedJourneySession(favorite=Favorite("f","Ziel",b),query=JourneyQuery(a,b,now),initialTrip=trip,routing=AppSettings())
        overlay.update(PinnedJourneyState(session=session),AppSettings())
        overlay.show()
        assertTrue(attached.value)
        val params=windows().single().layoutParams as WindowManager.LayoutParams
        assertTrue("the transparent margin may extend past the screen edge",params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS!=0)
        val inset=(PinnedBubbleMetrics.SHADOW_INSET_DP*context.resources.displayMetrics.density).toInt()
        val view=windows().single()
        val spec=android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED)
        shadowOf(Looper.getMainLooper()).idle()
        view.measure(spec,spec)
        val bubble=(PinnedBubbleMetrics.BUBBLE_DP*context.resources.displayMetrics.density).toInt()
        assertTrue("window = bubble + shadow margin on both sides",view.measuredWidth>=bubble+2*inset-1 && view.measuredHeight>=bubble+2*inset-1)
        overlay.hide()
        assertFalse(attached.value)
        assertTrue(windows().isEmpty())
    }

    @Test fun theNotificationOffersTheWayBackOnlyForAClosedBubble() {
        runBlocking {controller.pin(Favorite("f","Ziel",b),JourneyQuery(a,b,now),trip,AppSettings())}
        val service=Robolectric.buildService(PinnedJourneyService::class.java).get()
        val state=controller.state.value
        // "Reise beenden" and the source link of the timetable service.
        val expected=listOf("Reise beenden")+if(TimetableProfile.sourceNotice) listOf("Fahrplandaten: VRR") else emptyList()
        assertEquals(expected,service.notification(state,bubbleOffer=false).actions.map {it.title.toString()})
        val offered=service.notification(state,bubbleOffer=true)
        assertEquals(expected.size+1,offered.actions.size)
        assertEquals("Schwebende Reisekarte anzeigen",offered.actions.last().title.toString())
    }
}
