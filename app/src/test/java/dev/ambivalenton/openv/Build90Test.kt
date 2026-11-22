// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** The comparison chart fits whole columns and every column in view. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class Build90Test {
    private val now=1_800_000_000_000L
    private val a=Location("Bochum Hbf","a",51.47,7.22,"VRR");private val m=Location("Essen Hbf","m",51.45,7.01,"VRR")
    private val b=Location("Dortmund Hbf","b",51.51,7.45,"VRR")
    private val fringe=4f;private val approach=12f
    private fun seg(id:String,from:Location,to:Location,s:Long,e:Long,t:LineType,n:String)=TripSegment(id,from,to,s,e,t,n)
    /** Walk – ride – (transfer) ride – walk, as the chart draws them. */
    private fun trip(i:Int,endWalk:Int=6):Trip {
        val t0=now+i*11*60_000L
        val ride1=listOf(8,12,6,15,9,20)[i%6]*60_000L;val ride2=listOf(22,18,35,25,40,16)[i%6]*60_000L
        val segs=buildList {
            add(seg("w$i",a,a,t0,t0+4*60_000,LineType.WALK,"Fußweg"))
            add(seg("u$i",a,m,t0+4*60_000,t0+4*60_000+ride1,LineType.SUBWAY,"U49"))
            add(seg("r$i",m,b,t0+9*60_000+ride1,t0+9*60_000+ride1+ride2,LineType.REGIONAL,"RE3"))
            val end=last().endTime
            if(endWalk>0) add(seg("e$i",b,b,end,end+endWalk*60_000L,LineType.WALK,"Fußweg"))
        }
        return Trip("t$i",segs,"VRR",now)
    }
    private val trips=(0 until 10).map {trip(it)}

    @Test fun atLeastFiveWholeColumnsAndMoreOnWideScreens() {
        val narrow=chartColumns(360*3,85f*3);val phone=chartColumns(393*3,61.6f*3);val tablet=chartColumns(782*2,61.6f*2)
        assertEquals(5,narrow.count);assertEquals(6,phone.count);assertEquals(12,tablet.count)
        for((plot,c) in listOf(360*3 to narrow,393*3 to phone,782*2 to tablet)) {
            // Exactly whole columns: never a cut one at the right edge once snapped.
            assertTrue(c.count*c.widthPx<=plot);assertTrue(plot-c.count*c.widthPx<c.count)
        }
        // A narrow screen squeezes its columns a little instead of showing fewer than five.
        assertTrue(narrow.widthPx<85*3)
    }

    @Test fun everyColumnInViewFitsFromTheFirstDepartureToTheBottomFootstepsIncluded() {
        val extents=ChartExtentTable(trips,1f,fringe,approach)
        val origin=trips.first().startTime
        for(first in 0..4) {
            val frame=chartWindowFrame(trips,extents,first,5,origin,6f,700f)
            var lowest=0f
            for(j in first until first+5) {
                val e=timelineColumnExtent(trips[j],frame.scale,1f,fringe,approach)
                val y=frame.yDp((trips[j].startTime-origin)/60_000f)
                assertTrue("column $j starts below the top",y-e.lead>=6f-.5f)
                assertTrue("column $j ends above the bottom",y+e.depth<=706f+.5f)
                lowest=maxOf(lowest,y+e.depth)
            }
            // The leftmost column's highest element stands at the top, the lowest end just at the bottom.
            val left=timelineColumnExtent(trips[first],frame.scale,1f,fringe,approach)
            assertEquals(6f,frame.yDp((trips[first].startTime-origin)/60_000f)-left.lead,.5f)
            assertEquals("window $first fills the height",706f,lowest,2f)
        }
    }

    @Test fun walksCountNotOnlyTheBars() {
        val long=listOf(trip(0,endWalk=14))
        val frame=chartWindowFrame(long,ChartExtentTable(long,1f,fringe,approach),0,5,long[0].startTime,6f,600f)
        val lastRideEnd=long[0].segments.last {!it.isWalk}.endTime
        val barBottom=frame.yDp((lastRideEnd-long[0].startTime)/60_000f)
        // The 14-minute walk to the destination (and its arrival time) lies below the last bar and still fits.
        assertTrue(606f-barBottom>=14*frame.scale)
        val walkEnd=frame.yDp((long[0].endTime-long[0].startTime)/60_000f)
        assertTrue(walkEnd<=606f)
    }

    @Test fun theFitBlendsContinuouslyBetweenSnappedPositions() {
        val extents=ChartExtentTable(trips,1f,fringe,approach);val origin=trips.first().startTime
        val w1=chartWindowFrame(trips,extents,1,5,origin,6f,700f);val w2=chartWindowFrame(trips,extents,2,5,origin,6f,700f)
        assertEquals(w1,chartFrameAt(1f,trips,extents,5,origin,6f,700f))
        val almost=chartFrameAt(1.999f,trips,extents,5,origin,6f,700f)
        assertEquals(w2.originMinutes,almost.originMinutes,.05f);assertEquals(w2.scale,almost.scale,.01f)
        val half=chartFrameAt(1.5f,trips,extents,5,origin,6f,700f)
        assertTrue(half.originMinutes>w1.originMinutes && half.originMinutes<w2.originMinutes)
        // Never beyond the last position that still fills the view.
        assertEquals(chartWindowFrame(trips,extents,5,5,origin,6f,700f),chartFrameAt(9f,trips,extents,5,origin,6f,700f))
    }

    @Test fun theFrameFollowsWithASpringAndComesToRest() {
        val p=ChartProjection();val from=ChartFrame(0f,20f,4f);val to=ChartFrame(30f,24f,6f)
        p.snapTo(from);assertTrue(p.converged(from));assertFalse(p.moving)
        repeat(6) {p.step(to,1/120f)}
        assertTrue(p.moving);assertTrue(p.originMinutes>0f && p.originMinutes<30f)
        var t=0f
        while(!p.converged(to) && t<2f) {p.step(to,1/120f);t+=1/120f}
        assertTrue("settles within a second",t<1f);assertFalse(p.moving)
        assertEquals(to.scale,p.scale,.001f);assertEquals(to.topDp,p.topDp,.01f)
    }

    @Test fun finerAxisMarksFadeInAsRoomAppears() {
        // 15-minute marks at 3 dp/min are only 45 dp apart: hidden; at 9 dp/min they show fully.
        assertEquals(0f,chartMarkAlpha(15,3f),0f);assertEquals(1f,chartMarkAlpha(15,9f),0f)
        // Full hours stay visible where quarter hours have no room.
        assertEquals(1f,chartMarkAlpha(17*60,3f),0f)
        assertTrue(chartMarkAlpha(15,4.6f) in .01f..0.99f)
        assertEquals(30,chartMarkInterval(4f));assertEquals(15,chartMarkInterval(9f))
        // Each step divides the next, so finer marks always include the coarser ones.
        assertTrue(CHART_MARK_STEPS.toList().zipWithNext().all {(x,y)->y%x==0})
        assertTrue(abs(chartMarkAlpha(30,4f)-1f)<1e-6)
    }
}
