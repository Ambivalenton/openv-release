// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.AppUiState
import dev.ambivalenton.openv.ui.LoadState
import dev.ambivalenton.openv.ui.components.chartEdgeRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Acceptance group A (identities) and C (loading waves) of build 65. */
class Build65IdentityPagingTest {
    private val now=1_800_000_000_000L
    private val home=Location("Zuhause",latitude=51.45,longitude=7.01,kind=LocationKind.COORDINATE,isDevicePosition=true)
    private val x=Location("Essen Hbf","x",51.451,7.014,"TEST")
    private val y=Location("Bochum Hbf","y",51.478,7.223,"TEST")
    private val z=Location("Bochum West","z",51.474,7.20,"TEST")
    private fun ride(id:String,from:Location=x,to:Location=y,start:Long=now+600_000,end:Long=start+900_000,line:String="RE 1")=
        TripSegment(id,from,to,start,end,LineType.REGIONAL,line)
    private fun walk(from:Location,to:Location,start:Long,minutes:Int)=TripSegment("w-$start",from,to,start,start+minutes*60_000L,LineType.WALK,"Zu Fuß")
    private fun trip(id:String,vararg legs:TripSegment,fetched:Long=now)=Trip(id,legs.toList(),"TEST",fetched)

    @Test fun sameRideWithDifferentAlightingStopIsADifferentConnectionWithItsOwnId() {
        val toY=trip("dup",ride("r1",to=y));val toZ=trip("dup",ride("r2",to=z))
        assertNotEquals(connectionIdentity(toY),connectionIdentity(toZ))
        val merged=mergeDuplicateConnections(listOf(toY,toZ))
        assertEquals(2,merged.size);assertEquals(2,merged.map {it.id}.toSet().size)
        assertEquals("dup",merged.first().id)
    }

    @Test fun trueDuplicatesMergeAndTheFresherRealtimeCopyWinsWithTheStableId() {
        val old=trip("first",ride("r"),fetched=now)
        val fresh=trip("provider-new",ride("r").copy(startTime=now+780_000,delay=DelayInfo(180),realtimeDeparture=true),fetched=now+5_000)
        val merged=mergeDuplicateConnections(listOf(old,fresh))
        assertEquals(1,merged.size);assertEquals("first",merged.single().id)
        assertEquals(180,merged.single().segments.single().delay.departureSeconds)
    }

    @Test fun anAdditionalWalkIsAnotherConnectionButAChangedWalkDurationIsNot() {
        val direct=trip("direct",ride("r"))
        val walking=trip("walking",walk(home,x,now+300_000,5),ride("r"))
        val walkingLater=trip("walking2",walk(home.copy(latitude=51.4502),x,now+420_000,3),ride("r"))
        assertNotEquals(connectionIdentity(direct),connectionIdentity(walking))
        assertEquals(connectionIdentity(walking),connectionIdentity(walkingLater))
        // The older walk-insensitive comparison treats them as the same pinned journey.
        assertTrue(samePinnedJourney(direct,walking))
    }

    @Test fun liveUpdatesNeverHandOneBarsIdToAnotherConnectionOnTheSameRoute()=runTest {
        val direct=trip("b",ride("r"));val walking=trip("a",walk(home,x,now+300_000,5),ride("r"))
        val provider=FixedProvider(emptyList())
        val pager=JourneyBatchPager(provider,JourneyQuery(home,y,now),listOf(walking,direct))
        val update=direct.copy(id="provider-changed",fetchedAt=now+10_000,segments=direct.segments.map {it.copy(startTime=it.startTime+120_000,delay=DelayInfo(120))})
        val window=pager.applyLoadedUpdates(listOf(update))
        assertEquals(setOf("a","b"),window.trips.map {it.id}.toSet())
        assertEquals(120,window.trips.single {it.id=="b"}.segments.single().delay.departureSeconds)
        assertNull(window.trips.single {it.id=="a"}.segments.last().delay.departureSeconds)
    }

    @Test fun restoredSnapshotsWithRepeatedIdsAndDuplicatesYieldUniqueStableIds()=runTest {
        val one=trip("dup",ride("r1",to=y));val two=trip("dup",ride("r2",to=z));val copy=trip("other",ride("r1",to=y))
        val pager=JourneyBatchPager(FixedProvider(emptyList()),JourneyQuery(x,y,now),listOf(one,two,copy))
        val ids=pager.snapshot().trips.map {it.id}
        assertEquals(2,ids.size);assertEquals(2,ids.toSet().size)
        // Deterministic repair: the same restored state produces the same IDs again.
        assertEquals(ids,JourneyBatchPager(FixedProvider(emptyList()),JourneyQuery(x,y,now),listOf(one,two,copy)).snapshot().trips.map {it.id})
    }

    @Test fun overlappingPagesProduceUniqueIdsInTheAuthoritativeList() {
        val pageOne=listOf(trip("dup",ride("r1",to=y)),trip("t2",ride("r3",start=now+1_200_000)))
        val pageTwo=listOf(trip("dup",ride("r2",to=z)),trip("t2",ride("r3",start=now+1_200_000)))
        val state=AppUiState(query=JourneyQuery(x,y,now),trips=LoadState(pageOne+pageTwo))
        val ids=state.allTrips.map {it.id}
        assertEquals(3,ids.size);assertEquals(ids.toSet().size,ids.size)
    }

    @Test fun renderKeysAreUniqueEvenWhenSuffixesCollide() {
        val keys=uniqueRenderKeys(listOf("a","a#2","a","b","a"))
        assertEquals(keys.toSet().size,keys.size);assertEquals("a",keys.first());assertEquals("a#2",keys[1])
    }

    @Test fun efaIdsContainTheAlightingStop() {
        fun leg(destination:String,id:String)="""{"legs":[{"points":[
            {"name":"Essen Hbf","ref":{"id":"1","coords":"7.014,51.451"},"dateTime":{"date":"25.10.2026","time":"13:04"}},
            {"name":"$destination","ref":{"id":"$id","coords":"7.2,51.47"},"dateTime":{"date":"25.10.2026","time":"13:20"}}],
            "mode":{"name":"RE 1","symbol":"RE1","type":"0"}}]}"""
        val first=parseEfaTrip(Json.parseToJsonElement(leg("Bochum Hbf","2")))!!
        val second=parseEfaTrip(Json.parseToJsonElement(leg("Bochum West","3")))!!
        assertNotEquals(first.id,second.id)
        assertNotEquals(connectionIdentity(first),connectionIdentity(second))
    }

    @Test fun arrivalModeTreatsArrivalsBeforeTheReferenceAsPrimary()=runTest {
        val pager=JourneyBatchPager(FixedProvider(emptyList()),JourneyQuery(x,y,now,TimeMode.ARRIVAL),
            listOf(trip("early",ride("e",start=now-1_800_000)),trip("late",ride("l",start=now)),trip("x",ride("x2",start=now-3_600_000))))
        assertEquals(ConnectionDirection.EARLIER,pager.primaryDirection)
        assertEquals(2,pager.primaryCount());assertEquals(1,pager.secondaryCount())
        assertEquals(1,pager.missing(CONNECTION_WAVES[0],ConnectionDirection.EARLIER))
    }

    @Test fun identicalProviderPagesEndTheDirectionInsteadOfRetryingOnEveryScroll()=runTest {
        val same=listOf(trip("t1",ride("r1")),trip("t2",ride("r2",start=now+900_000)),trip("t3",ride("r3",start=now+1_200_000)))
        val provider=FixedProvider(same)
        val pager=JourneyBatchPager(provider,JourneyQuery(x,y,now))
        assertEquals(3,pager.initial().trips.size)
        val requests=provider.calls
        pager.future(3)
        assertTrue(pager.futureExhausted)
        assertTrue(provider.calls-requests<=6)
        assertEquals(3,pager.snapshot().trips.size)
    }

    @Test fun edgePrefetchFollowsScrollDirectionAndTheEightyPercentMark() {
        assertEquals(true,chartEdgeRequest(8,11,15,+1))
        assertNull(chartEdgeRequest(6,10,15,+1))
        assertEquals(false,chartEdgeRequest(3,6,15,-1))
        assertNull(chartEdgeRequest(4,7,15,-1))
        assertNull(chartEdgeRequest(0,3,15,+1))
        assertNull(chartEdgeRequest(0,0,0,+1))
    }

    @Test fun theLoadingPlanIsCentralAndCountsConnections() {
        assertEquals(listOf(ConnectionWave(3,0),ConnectionWave(6,3),ConnectionWave(9,6)),CONNECTION_WAVES)
        assertEquals(3,CONNECTION_EDGE_BATCH);assertEquals(.8f,CONNECTION_EDGE_PREFETCH_FRACTION)
    }

    @Test fun shortWalksAttachTheirTimeToTheVehicleAndLongWalksKeepTheirOwnLabels() {
        fun route(walkMinutes:Int):Trip {
            val rideStart=now+walkMinutes*60_000L;val rideEnd=rideStart+40*60_000L
            return trip("w$walkMinutes",walk(home,x,now,walkMinutes),ride("r",start=rideStart,end=rideEnd),walk(y,home.copy(name="Ziel"),rideEnd,walkMinutes))
        }
        val short=dev.ambivalenton.openv.ui.components.timelineClockLabels(route(3),3.4f,1f)
        assertFalse(short.any {it.walking})
        assertEquals(0,short.single {it.key=="ride-start-1"}.attachedWalkIndex)
        assertEquals(2,short.single {it.key=="ride-end-1"}.attachedWalkIndex)
        val justLonger=dev.ambivalenton.openv.ui.components.timelineClockLabels(route(4),3.4f,1f)
        assertNull(justLonger.single {it.key=="ride-start-1"}.attachedWalkIndex)
        val long=dev.ambivalenton.openv.ui.components.timelineClockLabels(route(20),3.4f,1f)
        assertEquals(2,long.count {it.walking})
        // Display only: the full walk stays in the arrival and in every transfer calculation.
        assertEquals(now+3*60_000L+40*60_000L+3*60_000L,route(3).effectiveArrival())
    }

    private class FixedProvider(private val rows:List<Trip>):TransitRepository {
        override val name="TEST"
        var calls=0
        override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage {calls++;return JourneyPage(rows)}
        override suspend fun locations(query:String)=emptyList<Location>()
        override suspend fun nearby(latitude:Double,longitude:Double,radiusMeters:Int)=emptyList<Location>()
        override suspend fun departures(stop:Location)=emptyList<Departure>()
        override suspend fun refresh(trip:Trip)=trip
    }
}
