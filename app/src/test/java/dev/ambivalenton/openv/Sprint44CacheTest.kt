// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

internal object Fixture44 {
    const val now=1_900_000_000_000L
    val a=Location("Bahnhof A","a",52.5,13.4,"TEST")
    val b=Location("Bahnhof B","b",52.6,13.5,"TEST")
    val c=Location("Ziel","c",52.61,13.51,"TEST")
    val settings=AppSettings(originMode=OriginMode.MANUAL,manualOrigin=a)
    val query=JourneyQuery(a,b,now)
    fun trip(id:String="t",start:Long=now+10*60_000,end:Long=start+20*60_000,type:LineType=LineType.REGIONAL,
        from:Location=a,to:Location=b,line:String="RE1",planned:Long=start,delay:DelayInfo=DelayInfo())=
        Trip(id,listOf(TripSegment(id,from,to,start,end,type,line,plannedStartTime=planned,delay=delay,departurePlatform="4")),"TEST",now)
    fun window(rows:List<Trip>,complete:Boolean=true)=JourneyWindow(rows,now,now+JOURNEY_WINDOW_MILLIS,if(complete) 1f else .2f,2,true,complete)
}
class Sprint44CacheTest {
    private val f=Fixture44
    @Test fun twentyMinuteOldNowSearchMatchesWithinTwoHundredMetresAndShowsTheWholeWindow() {
        var now=f.now;val cache=JourneySearchCache(now={now});val rows=(1..30).map {f.trip("t$it",start=f.now+it*600_000)}
        cache.put(f.query,f.settings,true,true,f.window(rows))
        now+=20*60_000
        val nearby=f.a.copy(latitude=f.a.latitude!!+.0017)
        val hit=cache.find(f.query.copy(origin=nearby,time=now),f.settings,true,true)!!
        assertEquals(rows,hit.trips);assertEquals(JOURNEY_WINDOW_MILLIS,hit.until-hit.from)
        assertNull(cache.find(f.query.copy(origin=f.a.copy(latitude=f.a.latitude!!+.0021),time=now),f.settings,true,true))
    }
    @Test fun expiryClockRollbackAndProviderChangesAreNeverAHit() {
        var now=f.now;val cache=JourneySearchCache(now={now});cache.put(f.query,f.settings,false,true,f.window(listOf(f.trip())))
        assertNull(cache.find(f.query,f.settings.copy(efaEndpoint="https://other.example"),false,true))
        assertNull(cache.find(f.query,f.settings,false,false))
        now+=JOURNEY_CACHE_TTL;assertNull(cache.find(f.query,f.settings,false,true));assertTrue(cache.entries.value.isEmpty())
        now=f.now;cache.put(f.query,f.settings,false,true,f.window(listOf(f.trip())));now--
        assertNull(cache.find(f.query,f.settings,false,true))
    }
    @Test fun exactTimeArrivalFiltersDestinationWaypointsAndDwellAllRemainPartOfTheKey() {
        val q=f.query.copy(timeMode=TimeMode.ARRIVAL,waypoints=listOf(f.c),waypointDwellMinutes=mapOf(waypointIdentity(f.c) to 5f))
        val cache=JourneySearchCache(now={f.now});cache.put(q,f.settings,false,false,f.window(listOf(f.trip())))
        assertNotNull(cache.find(q,f.settings,false,false))
        for(other in listOf(q.copy(time=q.time+60_000),q.copy(products=setOf(LineType.BUS)),q.copy(destination=f.c),
            q.copy(waypoints=emptyList()),q.copy(waypointDwellMinutes=mapOf(waypointIdentity(f.c) to 6f)),q.copy(timeMode=TimeMode.DEPARTURE)))
            assertNull(cache.find(other,f.settings,false,false))
    }
    @Test fun fixedOriginsDoNotReceiveTheGpsTolerance() {
        val cache=JourneySearchCache(now={f.now});cache.put(f.query,f.settings,false,true,f.window(listOf(f.trip())))
        val other=f.a.copy(id="different",latitude=f.a.latitude!!+.001)
        assertNull(cache.find(f.query.copy(origin=other),f.settings,false,true))
        assertNull(cache.find(f.query,f.settings,true,true))
    }
    @Test fun aPartialRefreshKeepsEveryCachedBarAndRestoresColoursIndividually() {
        val a=f.trip("a");val b=f.trip("b",start=f.now+50*60_000,end=f.now+70*60_000)
        val validation=JourneyRevalidation(listOf(a,b))
        val first=validation.accept(f.window(listOf(a.copy(id="provider-new",segments=a.segments.map {it.copy(startTime=it.startTime+180_000,delay=DelayInfo(180))})),false))
        assertEquals(setOf("a","b"),first.trips.map {it.id}.toSet());assertEquals(setOf("b"),validation.staleIds)
        assertEquals(180,first.trips.first().segments.first().delay.departureSeconds)
        val fail=validation.accept(f.window(listOf(first.trips.first()),false).copy(problem="offline"))
        assertEquals(2,fail.trips.size);assertEquals(setOf("b"),validation.staleIds)
        val complete=validation.accept(f.window(listOf(first.trips.first())))
        assertEquals(listOf("a"),complete.trips.map {it.id});assertTrue(validation.staleIds.isEmpty())
    }
    @Test fun emptyInitialEmissionNeverErasesTheCachedDiagram() {
        val before=(0..20).map {f.trip("$it",f.now+it*60_000)}
        val validation=JourneyRevalidation(before)
        assertEquals(before,validation.accept(JourneyWindow(from=f.now,until=f.now+JOURNEY_WINDOW_MILLIS)).trips)
        assertEquals(21,validation.staleIds.size)
    }
    @Test fun privateFileRoundTripRetainsFullTripsAndViewportAndDropsExpiredEntries()=runTest {
        val file=Files.createTempDirectory("openv-cache44").resolve("routes.json").toFile()
        var now=f.now;val a=JourneySearchCache(file,{now});a.initialize()
        val e=a.put(f.query,f.settings,false,true,f.window((0..4).map {f.trip("$it")}))!!
        a.viewport(e.id,3,17);a.flush()
        val b=JourneySearchCache(file,{now});b.initialize()
        assertEquals(a.entries.value,b.entries.value);assertEquals(3,b.get(e.id)!!.viewIndex);assertEquals(17,b.get(e.id)!!.viewOffset)
        now+=JOURNEY_CACHE_TTL
        val c=JourneySearchCache(file,{now});c.initialize();assertTrue(c.entries.value.isEmpty())
    }
    @Test fun corruptOrOversizedCacheDoesNotBlockLiveSearching()=runTest {
        val file=Files.createTempDirectory("openv-corrupt44").resolve("routes.json").toFile();file.writeText("{broken")
        val cache=JourneySearchCache(file,{f.now});cache.initialize();assertTrue(cache.entries.value.isEmpty())
        cache.put(f.query,f.settings,false,true,f.window(listOf(f.trip())));cache.flush()
        val restarted=JourneySearchCache(file,{f.now});restarted.initialize();assertEquals(1,restarted.entries.value.size)
        restarted.clear();restarted.flush();assertEquals("[]",file.readText())
    }
    @Test fun capacityIsBoundedAndIncompleteRefreshDoesNotRenewOldUnverifiedRows() {
        var now=f.now;val cache=JourneySearchCache(now={now},limit=2)
        val old=cache.put(f.query,f.settings,false,true,f.window(listOf(f.trip())))!!
        now+=20*60_000
        cache.put(f.query,f.settings,false,true,old.window().copy(complete=false),old.id,old.savedAt)
        assertEquals(old.savedAt,cache.get(old.id)!!.savedAt)
        cache.put(f.query.copy(destination=f.c),f.settings,false,true,f.window(listOf(f.trip())))
        cache.put(f.query.copy(destination=f.a.copy(id="d")),f.settings,false,true,f.window(listOf(f.trip())))
        assertEquals(2,cache.entries.value.size);assertNull(cache.get(old.id))
    }
    @Test fun onlyNearCurrentDepartureIsTimeFlexibleAndTheProviderKeyContainsNoUrl() {
        assertTrue(isCurrentDeparture(f.now,TimeMode.DEPARTURE,f.now+60_000))
        assertFalse(isCurrentDeparture(f.now,TimeMode.ARRIVAL,f.now));assertFalse(isCurrentDeparture(f.now,TimeMode.DEPARTURE,f.now+120_000))
        val key=journeyCacheProvider(f.settings);assertEquals(64,key.length);assertFalse(key.contains("http"))
    }
}
