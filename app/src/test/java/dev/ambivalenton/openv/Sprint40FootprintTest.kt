// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.graphics.Color
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class Sprint40FootprintTest {
    private val a=Location("A","a",51.0,7.0)
    private val b=a.copy(name="B",id="b",longitude=7.01)
    private val t=1_800_000_000_000L
    private fun route(walk:Long):Trip {
        fun leg(id:String,start:Long,duration:Long,type:LineType)=TripSegment(id,a,b,start,start+duration,type,if(type==LineType.WALK) "Fußweg" else "RE1")
        val rideStart=t+walk;val rideEnd=rideStart+2_400_000
        return Trip("clock",listOf(leg("lead",t,walk,LineType.WALK),leg("ride",rideStart,2_400_000,LineType.REGIONAL),leg("tail",rideEnd,walk,LineType.WALK)),"test",t)
    }
    @Test fun shortWalkingTimesYieldToBothVehicleClocksAtEveryFontScale() {
        for(scale in listOf(.85f,1f,1.6f,2f)) {
            val labels=timelineClockLabels(route(20_000),3.4f,scale)
            assertFalse(labels.any {it.walking})
            assertEquals(setOf("ride-start-1","ride-end-1"),labels.map {it.key}.toSet())
        }
    }
    @Test fun longWalkingTimesRemainVisibleAndDoNotCollideWithVehicleClocks() {
        val labels=timelineClockLabels(route(1_200_000),3.4f,1f)
        assertEquals(2,labels.count {it.walking})
        for(walk in labels.filter {it.walking}) for(ride in labels.filter {!it.walking})
            assertTrue(abs(walk.topDp-ride.topDp)>=22f)
    }
    @Test fun hundredsOfSeededPathsAlwaysAdvanceDownwardAndStayInsideTheBar() {
        for(seed in 0L..200L) for(size in listOf(21f to 15f,21f to 83f,38f to 240f,114f to 600f)) {
            val (w,h)=size;var previous=-1f
            for(i in 0 until footstepCount(w,h,seed)) {
                val step=footstepGeometry(w,h,i,seed)!!
                assertTrue("No backwards steps: $seed",step.y>previous);previous=step.y
                assertTrue("Bounded realistic heading",abs(step.angle)<=.75f)
                val halfW=FOOTPRINT_WIDTH_PX*step.scale/2+FOOTPRINT_LENGTH_PX*step.scale/2*abs(kotlin.math.sin(step.angle))
                assertTrue(step.x-halfW>=-.1f);assertTrue(step.x+halfW<=w+.1f)
                assertTrue(step.y-FOOTPRINT_LENGTH_PX*step.scale/2>=-.01f)
                assertTrue(step.y+FOOTPRINT_LENGTH_PX*step.scale/2<=h+.01f)
            }
        }
    }
    @Test fun differentColumnsAndCyclesHaveDifferentButReproducibleTrajectories() {
        fun trace(seed:Long)=(0 until footstepCount(141f,600f,seed)).map {footstepGeometry(141f,600f,it,seed)}
        assertEquals(trace(149),trace(149));assertNotEquals(trace(149),trace(150));assertNotEquals(trace(149),trace(149+0x9e3779bL))
        val steps=trace(149).filterNotNull();assertTrue(steps.zipWithNext().all {it.first.left!=it.second.left})
    }
    @Test fun fineOutlineAlwaysOpposesClockInk() {
        assertEquals(Color.White,footprintOutlineColor(Color.Black))
        assertEquals(Color.Black,footprintOutlineColor(Color.White))
    }
}

