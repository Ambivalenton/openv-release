// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.components.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Sprint41DomainTest {
    private val a=Location("Alpha","a",51.0,7.0)
    private val b=a.copy(name="Beta",id="b",longitude=7.05)
    private val t=1_800_000_000_000L
    private fun trip(start:Long,id:String=start.toString(),type:LineType=LineType.BUS)=Trip(id,
        listOf(TripSegment(id,a,b,start,start+3_600_000,type,"42-$id")),"fixture",t)
    private abstract class Provider:TransitRepository {
        override val name="fixture"
        override suspend fun locations(query:String)=emptyList<Location>()
        override suspend fun nearby(latitude:Double,longitude:Double,radiusMeters:Int)=emptyList<Location>()
        override suspend fun departures(stop:Location)=emptyList<Departure>()
        override suspend fun refresh(trip:Trip)=trip
        override fun journeyWindow(query:JourneyQuery,policy:PagingPolicy)=error("History must not load a full 3-hour window")
    }
    @Test fun aNormalHistoryPageReturnsThreeInOneRequestWithoutWaitingOrDroppingOverlappingArrivals()=runTest {
        val requests=mutableListOf<JourneyQuery>()
        val p=object:Provider() {override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage {
            requests+=query;return JourneyPage((0..11).map {trip(query.time+it*120_000L)})
        }}
        val query=JourneyQuery(a,b,t,waypoints=listOf(a.copy(id="via")),waypointDwellMinutes=mapOf("via" to 7.5f))
        val rows=p.fetchHistoricalConnections(query,t)
        assertEquals(1,requests.size);assertEquals(3,rows.size)
        assertEquals(listOf(t-360_000,t-240_000,t-120_000),rows.map {it.startTime})
        assertTrue(rows.all {it.endTime>t});assertEquals(query.waypoints,requests.single().waypoints)
        assertEquals(query.waypointDwellMinutes,requests.single().waypointDwellMinutes)
        assertEquals(TimeMode.DEPARTURE,requests.single().timeMode)
        assertEquals(0L,testScheduler.currentTime)
    }
    @Test fun sparseServicesExpandOnlyAsFarBackAsNeeded()=runTest {
        var calls=0
        val all=(-4..2).map {trip(t+it*3_600_000L-300_000L)}
        val p=object:Provider() {override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage {
            calls++;return JourneyPage(all.filter {it.startTime>=query.time})
        }}
        val rows=p.fetchHistoricalConnections(JourneyQuery(a,b,t),t)
        assertEquals(4,calls);assertEquals(3,rows.size)
        assertEquals(listOf(t-7_500_000,t-3_900_000,t-300_000),rows.map {it.startTime})
        assertEquals(0L,testScheduler.currentTime)
    }
    @Test fun nativeCursorContinuesToTheEdgeAndKeepsSimultaneousServices()=runTest {
        val refs=mutableListOf<String?>()
        val p=object:Provider() {override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage {
            refs+=cursor
            return if(cursor==null) JourneyPage((-10..-5).map {trip(t+it*60_000L)},"next")
            else JourneyPage(listOf(trip(t-60_000,"a"),trip(t-60_000,"b"),trip(t-120_000,"c"),trip(t,"future")))
        }}
        val rows=p.fetchHistoricalConnections(JourneyQuery(a,b,t),t)
        assertEquals(listOf(null,"next"),refs);assertEquals(setOf("a","b","c"),rows.map {it.id}.toSet())
    }
    @Test fun excludesExistingServicesAndUnselectedProducts()=runTest {
        val excluded=trip(t-60_000,"old");val valid=(2..5).map {trip(t-it*60_000,"valid-$it")}
        val p=object:Provider() {override suspend fun journeyPage(query:JourneyQuery,cursor:String?)=
            JourneyPage(listOf(excluded,trip(t-30_000,"rail",LineType.ICE))+valid+trip(t,"future"))}
        val rows=p.fetchHistoricalConnections(JourneyQuery(a,b,t,products=setOf(LineType.BUS)),t,3,setOf(historicalIdentity(excluded)))
        assertEquals(3,rows.size);assertTrue(rows.all {it.id.startsWith("valid-")})
    }
    @Test fun cancellingHistoryCancelsItsProviderInsteadOfPretendingItWasEmpty()=runTest {
        val p=object:Provider() {override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage=throw CancellationException("cancel")}
        try {p.fetchHistoricalConnections(JourneyQuery(a,b,t),t);fail("Cancellation must propagate")} catch(_:CancellationException) {}
    }
    @Test fun slowHistoryHasAnExplicitDeadline()=runTest {
        val p=object:Provider() {override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage {delay(60_000);return JourneyPage(emptyList())}}
        try {p.fetchHistoricalConnections(JourneyQuery(a,b,t),t);fail("A timed-out request must be retryable")} catch(_:TransitException) {}
        assertEquals(12_000L,testScheduler.currentTime)
    }
    @Test fun generatedRoutesUseSeveralFamiliesAndTheFullAvailableField() {
        val routes=(0L..200).map {walkingTrajectory(141f,620f,it)!!}
        assertEquals((0..4).toSet(),routes.map {it.kind}.toSet())
        assertTrue(routes.any {r->r.points.maxOf {it.x}-r.points.minOf {it.x}>65f})
        assertTrue(routes.any {r->r.points.maxOf {it.x}-r.points.minOf {it.x}<15f})
        assertTrue(routes.map {it.points.map {p->p.x}}.distinct().size>190)
        assertEquals(walkingFootsteps(141f,620f,19L),walkingFootsteps(141f,620f,19L))
        assertNotEquals(walkingFootsteps(141f,620f,19L),walkingFootsteps(141f,620f,19L+0x9e3779bL))
    }
    @Test fun naturalFootstepsNeverReverseOrLeaveTheFieldAtRealDeviceDensities() {
        for(seed in 0L..300) for((w,h) in listOf(21f to 15f,54f to 90f,141f to 620f,160f to 1200f)) {
            val steps=walkingFootsteps(w,h,seed)
            assertTrue(steps.isNotEmpty());assertTrue(steps.zipWithNext().all {(a,b)->b.y>a.y && a.left!=b.left})
            for(step in steps) {
                assertTrue(abs(step.angle)<.75f)
                val radius=hypot(FOOTPRINT_LENGTH_PX/2,FOOTPRINT_WIDTH_PX/2)*step.scale
                assertTrue(step.x-radius>=-.01f);assertTrue(step.x+radius<=w+.01f)
                assertTrue(step.y-radius>=-.01f);assertTrue(step.y+radius<=h+.01f)
            }
        }
    }
    @Test fun routeHeadingsAreSmoothAndAlwaysPointDownward() {
        for(seed in 0L..100) {
            val route=walkingTrajectory(141f,600f,seed)!!
            assertTrue(route.points.zipWithNext().all {(a,b)->b.y>a.y && abs(b.heading-a.heading)<.2f})
            assertTrue(route.points.all {it.heading.isFinite() && abs(it.heading)<.7f})
        }
    }
    @Test fun theRegularTimelineUsesLargeStraightAlternatingStepsAndACompleteRest() {
        val scale=28f/FOOTPRINT_LENGTH_PX
        val steps=walkingFootsteps(52f,320f,123L,scale,true)
        assertEquals(28f,steps.first().scale*FOOTPRINT_LENGTH_PX,.01f)
        assertTrue(steps.filter {it.left}.map {it.x}.distinct().size==1)
        assertTrue(steps.filterNot {it.left}.map {it.x}.distinct().size==1)
        assertTrue(steps.all {abs(it.angle)<.04f})
        for(i in steps.indices) assertEquals(0f,footstepAlpha(i,steps.size,.999f),0f)
        assertEquals(22.5f,FOOTPRINT_LENGTH_PX,0f);assertEquals(10.5f,FOOTPRINT_WIDTH_PX,0f)
    }
    @Test fun glyphContrastAlwaysOpposesTheTextIncludingTheActualGreyWalkColour() {
        assertEquals(Color.White,footprintOutlineColor(Color.Black))
        assertEquals(Color.Black,footprintOutlineColor(Color.White))
    }
    @Test fun theMorphCreatesALargerCardWithRoomOnTheLeftOfThePin() {
        val start=pinFabFrame(0f);val end=pinFabFrame(1f)
        assertEquals(56f,start.width,0f);assertEquals(80f,end.width,.01f);assertEquals(116f,end.height,.01f)
        assertTrue(end.pinX>12f);assertTrue(end.width*end.height>2f*46f*76f)
    }
}

