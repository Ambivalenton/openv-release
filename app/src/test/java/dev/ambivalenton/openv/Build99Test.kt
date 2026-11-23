// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The chart loads the last few missed connections before it looks further ahead. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class Build99Test {
    private val now=1_800_000_000_000L
    private val a=Location("Start","a",51.0,7.0,"TEST")
    private val b=Location("Ziel","b",51.1,7.1,"TEST")
    private fun trip(t:Long)=Trip("trip-$t",listOf(TripSegment("leg-$t",a,b,t,t+600_000,LineType.BUS,"123")),"TEST",now)
    /** Every page starts two minutes after the requested time, one connection every ten minutes. */
    private inner class Provider:TransitRepository {
        override val name="TEST"
        override suspend fun journeyPage(query:JourneyQuery,cursor:String?)=
            JourneyPage((0 until query.pageSize).map {trip(query.time+120_000+it*600_000)})
        override suspend fun refresh(trip:Trip)=trip
        override suspend fun locations(query:String)=emptyList<Location>()
        override suspend fun nearby(latitude:Double,longitude:Double,radiusMeters:Int)=emptyList<Location>()
        override suspend fun departures(stop:Location)=emptyList<Departure>()
    }
    private data class Step(val direction:ConnectionDirection,val earlierBefore:Int,val laterBefore:Int)

    private suspend fun steps(mode:TimeMode):Pair<JourneyBatchPager,List<Step>> {
        val pager=JourneyBatchPager(Provider(),JourneyQuery(a,b,now,mode))
        pager.initial()
        val steps=mutableListOf<Step>()
        fun earlier()=if(mode==TimeMode.DEPARTURE) pager.secondaryCount() else pager.primaryCount()
        fun later()=if(mode==TimeMode.DEPARTURE) pager.primaryCount() else pager.secondaryCount()
        pager.completeWaves({true}) {direction,count->steps+=Step(direction,earlier(),later());pager.load(direction,count)}
        return pager to steps
    }

    // Build 103 (publisher's plan): after the first bars three more on the searched side, then three on the other.
    @Test fun aDepartureSearchLoadsThreeLaterThenThreeEarlier()=runTest {
        val (pager,steps)=steps(TimeMode.DEPARTURE)
        assertEquals(listOf(ConnectionDirection.LATER,ConnectionDirection.EARLIER),steps.map {it.direction})
        assertEquals(3,steps.first().laterBefore);assertEquals(0,steps.first().earlierBefore)
        assertEquals(6,pager.primaryCount());assertEquals(3,pager.secondaryCount())
        assertEquals(listOf(ConnectionWave(3,0),ConnectionWave(5,3)),CONNECTION_WAVES)
    }

    @Test fun anArrivalSearchKeepsItsSearchedSideFirst()=runTest {
        val (pager,steps)=steps(TimeMode.ARRIVAL)
        // Arriving by a time: the searched side lies earlier and comes first.
        assertEquals(ConnectionDirection.EARLIER,pager.primaryDirection)
        assertEquals(ConnectionDirection.EARLIER,steps.first().direction)
    }

    @Test fun aSeededPreSearchGetsThreeMoreOnEachSide()=runTest {
        // Pre-search: one bar before and two after the time are already there.
        val pager=JourneyBatchPager(Provider(),JourneyQuery(a,b,now),listOf(trip(now-480_000),trip(now+120_000),trip(now+720_000)))
        val steps=mutableListOf<Pair<ConnectionDirection,Int>>()
        pager.completeWaves({true}) {direction,count->steps+=direction to count;pager.load(direction,count)}
        assertEquals(listOf(ConnectionDirection.LATER to 3,ConnectionDirection.EARLIER to 3),steps)
    }

    @Test fun aReplacedSearchStopsTheWaves()=runTest {
        val pager=JourneyBatchPager(Provider(),JourneyQuery(a,b,now))
        pager.initial()
        var calls=0
        pager.completeWaves({false}) {_,_->calls++}
        assertEquals(0,calls)
    }
}
