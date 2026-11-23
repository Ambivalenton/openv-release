// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class Sprint61PagingTest {
    private val now=1_800_000_000_000L
    private val a=Location("Start","a",51.0,7.0,"TEST")
    private val b=Location("Ziel","b",51.1,7.1,"TEST")
    private fun trip(t:Long)=Trip("trip-$t",listOf(TripSegment("leg-$t",a,b,t,t+600_000,LineType.BUS,"123")),"TEST",now,refreshToken="token-$t")
    private inner class Provider:TransitRepository {
        override val name="TEST"
        val requested=mutableListOf<JourneyQuery>();val refreshed=mutableListOf<String>()
        override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage {
            requested+=query
            val start=Math.floorDiv(query.time+119_999,120_000)*120_000
            return JourneyPage((0 until query.pageSize).map {trip(start+it*120_000)})
        }
        override suspend fun refresh(trip:Trip):Trip {refreshed+=trip.id;return trip.copy(fetchedAt=now+1000)}
        override suspend fun locations(query:String)=emptyList<Location>()
        override suspend fun nearby(latitude:Double,longitude:Double,radiusMeters:Int)=emptyList<Location>()
        override suspend fun departures(stop:Location)=emptyList<Departure>()
    }
    // Build 103: 3 future first, then the target of 5 future/3 past, then three per edge.
    @Test fun threeFutureThenThreeMoreAndThreePastThenThreePerEdge()=runTest {
        val p=Provider();val pager=JourneyBatchPager(p,JourneyQuery(a,b,now))
        val initial=pager.initial();assertEquals(3,initial.trips.size);assertEquals(1,p.requested.size)
        assertEquals(3,p.requested.first().pageSize);assertTrue(initial.trips.all {it.startTime>=now})
        assertEquals(2,pager.missing(CONNECTION_WAVES[1],ConnectionDirection.LATER))
        assertEquals(3,pager.missing(CONNECTION_WAVES[1],ConnectionDirection.EARLIER))
        pager.future(3);pager.past(3)
        assertEquals(6,pager.primaryCount());assertEquals(3,pager.secondaryCount())
        pager.future(3);pager.past(3)
        assertEquals(9,pager.primaryCount());assertEquals(6,pager.secondaryCount())
        assertTrue(pager.missing(CONNECTION_WAVES.last(),ConnectionDirection.LATER)<=0)
        assertTrue(pager.missing(CONNECTION_WAVES.last(),ConnectionDirection.EARLIER)<=0)
        val beforeRequests=p.requested.size
        val future=pager.future(3);assertEquals(18,future.trips.size)
        assertEquals(beforeRequests+1,p.requested.size)
        assertTrue(p.requested.last().pageSize in 3..6)
        val older=pager.past(3);assertEquals(21,older.trips.size)
        assertEquals(older.trips.minOf {it.startTime},older.from);assertEquals(older.trips.maxOf {it.endTime},older.until)
        assertEquals(21,older.trips.map(::historicalIdentity).distinct().size)
    }
    @Test fun reopenedCacheRevalidatesEveryStillBoardableBarWithStableIds()=runTest {
        val seed=(0 until 12).map {trip(now+it*120_000)}
        val p=Provider();val pager=JourneyBatchPager(p,JourneyQuery(a,b,now),seed)
        val updates=mutableListOf<Set<String>>()
        pager.refreshSeed(now) {_,stale->updates+=stale}
        assertEquals(seed.map {it.id}.toSet(),p.refreshed.toSet());assertTrue(updates.last().isEmpty())
        assertEquals(seed.map {it.id},pager.snapshot().trips.map {it.id})
        assertTrue(pager.snapshot().trips.all {it.fetchedAt==now+1000})
    }
    @Test fun historicalSnapshotsAreRetainedWithoutRefreshingMissedDepartures()=runTest {
        val seed=listOf(trip(now-120_000),trip(now),trip(now+120_000))
        val p=Provider();val pager=JourneyBatchPager(p,JourneyQuery(a,b,now),seed)
        pager.refreshSeed(now){_,_->}
        assertEquals(seed.drop(1).map {it.id},p.refreshed);assertEquals(3,pager.snapshot().trips.size)
    }
    @Test fun aProviderReturningExtraTripsDoesNotExposeOrRequestThemBeforeDemand()=runTest {
        val base=Provider();var calls=0
        val provider=object:TransitRepository by base {
            override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage {
                calls++;return base.journeyPage(query.copy(pageSize=12),cursor)
            }
        }
        val pager=JourneyBatchPager(provider,JourneyQuery(a,b,now))
        assertEquals(3,pager.initial().trips.size);assertEquals(1,calls)
        assertEquals(6,pager.future(3).trips.size);assertEquals(1,calls)
        assertEquals(9,pager.future(3).trips.size);assertEquals(1,calls)
        assertEquals(12,pager.future(3).trips.size);assertEquals(1,calls)
        assertEquals(15,pager.future(3).trips.size);assertEquals(2,calls)
    }
    @Test fun tokenlessEfaStyleCacheIsRevalidatedInSmallSearchPages()=runTest {
        val seed=(0 until 9).map {trip(now+it*120_000).copy(refreshToken=null)}
        val p=Provider();val pager=JourneyBatchPager(p,JourneyQuery(a,b,now),seed)
        var stale=setOf<String>()
        pager.refreshSeed(now){_,remaining->stale=remaining}
        assertTrue(p.refreshed.isEmpty());assertTrue(stale.isEmpty())
        assertEquals(3,p.requested.size);assertTrue(p.requested.all {it.pageSize==3})
        assertEquals(9,pager.snapshot().trips.size)
    }

    @Test fun reopeningAPagedCacheDoesNotAutomaticallyFetchAnotherSixOlderBars()=runTest {
        val p=Provider();val seed=(-6 until 6).map {trip(now+it*120_000)}
        val reopened=JourneyBatchPager(p,JourneyQuery(a,b,now),seed)
        assertTrue(reopened.missing(CONNECTION_WAVES.last(),ConnectionDirection.EARLIER)<=0)
        assertTrue(reopened.missing(CONNECTION_WAVES.last(),ConnectionDirection.LATER)<=0)
        assertEquals(3,JourneyBatchPager(p,JourneyQuery(a,b,now)).missing(CONNECTION_WAVES.last(),ConnectionDirection.EARLIER))
        assertEquals(12,reopened.initial().trips.size);assertEquals(1,p.requested.size)
    }
    @Test fun liveUpdatesKeepStableBarIdsAndDoNotRevealExtraFutureConnections()=runTest {
        val p=Provider();val pager=JourneyBatchPager(p,JourneyQuery(a,b,now))
        val before=pager.initial()
        val refreshed=before.trips.first().copy(id="new-provider-id",fetchedAt=now+1000)
        val after=pager.applyLoadedUpdates(listOf(refreshed,trip(now+3_600_000)))
        assertEquals(3,after.trips.size);assertEquals(before.trips.first().id,after.trips.first().id)
        assertEquals(now+1000,after.trips.first().fetchedAt)
    }

}
