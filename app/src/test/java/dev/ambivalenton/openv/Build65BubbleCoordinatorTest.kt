// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.WindowManager
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.location.CurrentPositionSource
import dev.ambivalenton.openv.tracking.PinnedBubbleCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/** Acceptance group D (bubble): the process-wide coordinator wired to the shared journey state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class Build65BubbleCoordinatorTest {
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
        var refreshes=0
        override val name="Test"
        override suspend fun locations(query:String)=emptyList<Location>()
        override suspend fun nearby(latitude:Double,longitude:Double,radiusMeters:Int)=emptyList<Location>()
        override suspend fun departures(stop:Location)=emptyList<Departure>()
        override suspend fun journeyPage(query:JourneyQuery,cursor:String?)=JourneyPage(listOf(trip))
        override suspend fun refresh(trip:Trip):Trip {refreshes++;return trip.copy(fetchedAt=trip.fetchedAt+1_000)}
    }
    private val context=RuntimeEnvironment.getApplication()
    private val saved=MutableStateFlow(StoredState())
    private val locations=LocationRepository(object:androidx.datastore.core.DataStore<StoredState> {
        override val data=saved
        override suspend fun updateData(transform:suspend(StoredState)->StoredState)=transform(saved.value).also {saved.value=it}
    })
    private val engine=Engine()
    private val controller=PinnedJourneyController(context,{engine},positions,now={now})
    private val coordinator=PinnedBubbleCoordinator(context,controller,locations)

    private fun windows()=Shadow.extract<ShadowWindowManagerImpl>(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).views.size
    /** The coordinator observes off the main thread and only touches windows on it. */
    private fun settle(millis:Long=300) {
        val end=System.currentTimeMillis()+millis
        while(System.currentTimeMillis()<end) {shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)}
    }
    private fun awaitWindows(expected:Int,why:String) {
        val deadline=System.currentTimeMillis()+10_000
        while(true) {
            shadowOf(Looper.getMainLooper()).idle()
            if(windows()==expected && coordinator.active.value==(expected==1))return
            if(System.currentTimeMillis()>deadline)fail("$why: expected $expected bubble windows, found ${windows()}, active=${coordinator.active.value}")
            Thread.sleep(10)
        }
    }
    /** The overlay content really composes and draws outside an Activity (no missing owners or locals). */
    private fun assertTheBubbleComposesAndDraws() {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
        val view=Shadow.extract<ShadowWindowManagerImpl>(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).views.single()
        val spec=android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED)
        view.measure(spec,spec);view.layout(0,0,view.measuredWidth,view.measuredHeight)
        assertTrue("the bubble has a visible size",view.measuredWidth>0 && view.measuredHeight>0)
        val bitmap=android.graphics.Bitmap.createBitmap(view.measuredWidth,view.measuredHeight,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap));bitmap.recycle()
    }
    private fun bubble(on:Boolean)=runBlocking {locations.updateSettings {it.copy(pinnedBubble=on)}}

    @After fun tearDown() {coordinator.stop();runBlocking {controller.close()}}

    @Test fun theBubbleFollowsModePinCloseReopenRevocationAndUnpinWithoutOwnRequests() {
        ShadowSettings.setCanDrawOverlays(true)
        runBlocking {controller.close()}
        coordinator.start();coordinator.start() // idempotent
        runBlocking {controller.pin(Favorite("f","Ziel",b),JourneyQuery(a,b,now),trip,AppSettings())}
        settle();assertEquals("mode off: no bubble despite pin and access",0,windows());assertFalse(coordinator.active.value)

        val requests=engine.refreshes
        bubble(true);awaitWindows(1,"mode on");assertTheBubbleComposesAndDraws()
        settle();assertEquals("showing the bubble asks the provider nothing",requests,engine.refreshes)
        val id=controller.state.value.session!!.id
        runBlocking {controller.setBubbleDismissed(id,true)};awaitWindows(0,"closed via the drop zone")
        runBlocking {controller.refreshOnce()};settle()
        assertEquals("a normal refresh does not bring it back",0,windows())
        assertEquals("the pin stays",id,controller.state.value.session?.id)

        val beforeReopen=engine.refreshes
        runBlocking {assertTrue(coordinator.reopen(id))};awaitWindows(1,"deliberately reopened")
        assertEquals("reopening asks the provider nothing",beforeReopen,engine.refreshes)
        ShadowSettings.setCanDrawOverlays(false);runBlocking {controller.refreshOnce()};awaitWindows(0,"access revoked")
        ShadowSettings.setCanDrawOverlays(true);runBlocking {controller.refreshOnce()};awaitWindows(1,"access granted again")

        bubble(false);awaitWindows(0,"mode switched off")
        bubble(true);awaitWindows(1,"mode on again")
        runBlocking {controller.close()};awaitWindows(0,"unpinned")
    }
}
