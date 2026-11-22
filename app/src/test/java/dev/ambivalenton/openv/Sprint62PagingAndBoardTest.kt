// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class Sprint62PagingAndBoardTest {
    private val now=1_800_000_000_000L
    private val a=Location("Herne Bf","a",51.0,7.0,"TEST")
    private val b=Location("Dortmund Hbf","b",51.1,7.1,"TEST")
    private fun trip(time:Long,query:JourneyQuery)=Trip("trip-$time",listOf(TripSegment("leg-$time",query.origin,query.destination,time,time+600_000,LineType.REGIONAL,"RE 1")),"TEST",now)
    private inner class CappedProvider:TransitRepository {
        override val name="TEST"
        val queries=mutableListOf<JourneyQuery>()
        var failAfter=Int.MAX_VALUE
        override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage {
            if(queries.size>=failAfter)error("Provider unavailable")
            queries+=query
            val start=Math.floorDiv(query.time+119_999,120_000)*120_000
            return JourneyPage((0 until 3).map {trip(start+it*120_000,query)})
        }
        override suspend fun locations(query:String)=emptyList<Location>()
        override suspend fun nearby(latitude:Double,longitude:Double,radiusMeters:Int)=emptyList<Location>()
        override suspend fun departures(stop:Location)=emptyList<Departure>()
        override suspend fun refresh(trip:Trip)=trip
    }
    @Test fun aProviderCappedAtThreeFollowsTheWavesAndThenThreePerSwipe()=runTest {
        val provider=CappedProvider();val pager=JourneyBatchPager(provider,JourneyQuery(a,b,now))
        val initial=pager.initial()
        assertEquals(3,initial.trips.size);assertEquals(1,provider.queries.size)
        assertTrue(initial.trips.all {it.startTime>=now})
        pager.future(3);pager.future(3)
        assertEquals(9,pager.primaryCount())
        val initialCalls=provider.queries.size
        pager.past(3);val both=pager.past(3)
        assertEquals(6,both.trips.count {it.startTime<now});assertEquals(15,both.trips.size)
        assertTrue(provider.queries.drop(initialCalls).all {it.time<=now})
        assertEquals(18,pager.future(3).trips.size)
        assertEquals(21,pager.past(3).trips.size)
        assertEquals(21,pager.snapshot().trips.map(::historicalIdentity).distinct().size)
    }
    @Test fun brokenPaginationIsBoundedAndRetainsTheAvailableTrips()=runTest {
        val provider=CappedProvider();val q=JourneyQuery(a,b,now)
        val stuck=object:TransitRepository by provider {
            override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage {
                provider.queries+=query
                return JourneyPage((0 until 3).map {trip(now+it*120_000,q)},"same-cursor")
            }
        }
        assertEquals(3,JourneyBatchPager(stuck,q).initial().trips.size)
        assertTrue(provider.queries.size<=8)
    }
    @Test fun partialInitialPageSurvivesAFailedFollowupRequest()=runTest {
        val provider=CappedProvider().apply {failAfter=1}
        // A larger explicit first page still needs a follow-up request that may fail.
        val result=JourneyBatchPager(provider,JourneyQuery(a,b,now),initialCount=9).initial()
        assertEquals(3,result.trips.size);assertFalse(result.complete)
    }
    @Test fun rotationSkipsDisabledAndExcludedPlacesWrapsAndPersists() {
        val c=Location("C Markt","c",51.2,7.2,"TEST")
        val d=Location("D Markt","d",51.3,7.3,"TEST")
        val base=StoredState(boardStops=listOf(a,b,c,d),boardDisabledKeys=setOf(boardLocationKey(c)),
            boardExcludedKeys=setOf(boardLocationKey(d)),settings=AppSettings(board=BoardSettings(autoRotate=true,selectedStop=a)))
        assertEquals(b,nextRotatingBoardStop(base))
        assertEquals(a,nextRotatingBoardStop(base.copy(settings=base.settings.copy(board=base.settings.board.copy(selectedStop=b)))))
        val stored=StateSerializer.json.decodeFromString(StoredState.serializer(),StateSerializer.json.encodeToString(StoredState.serializer(),base))
        assertTrue(stored.settings.board.autoRotate)
        assertNull(nextRotatingBoardStop(base.copy(boardDisabledKeys=setOf(a,b,c,d).map(::boardLocationKey).toSet())))
    }
    @Test fun anEmptyOldBoardSettingsObjectKeepsRotationOff() {
        assertFalse(StateSerializer.json.decodeFromString(BoardSettings.serializer(),"{}").autoRotate)
    }
}
