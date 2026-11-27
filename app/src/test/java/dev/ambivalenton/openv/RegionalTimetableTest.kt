// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RegionalTimetableTest {
    private val a=Location("Herne Bf","de:05916:7501")
    private val b=Location("Wanne-Eickel Hbf","de:05916:7590")
    private fun time(date:String)=ZonedDateTime.parse("${date}T08:00:00+01:00[Europe/Berlin]").toInstant().toEpochMilli()

    @Test fun publishedDatesFiltersAndMissingCoverage() = runBlocking {
        val timetable=RegionalTimetable(RuntimeEnvironment.getApplication())
        // Build 129: the device keeps only the person's own stations.
        timetable.prepare(listOf(a.id!!,b.id!!))
        val weekday=JourneyQuery(a,b,time("2026-11-27"))
        val friday=timetable.direct(weekday)
        val holiday=timetable.direct(weekday.copy(time=time("2026-12-25")))
        assertTrue(friday.size>=3)
        assertTrue(holiday.isNotEmpty())
        assertNotEquals(friday.map { (it.startTime-weekday.time)/60_000 },holiday.map { (it.startTime-time("2026-12-25"))/60_000 })
        assertTrue(friday.all { it.startTime>=weekday.time && it.segments.single().departureSource==TimeSource.PLANNED })
        assertTrue(timetable.direct(weekday.copy(products=setOf(LineType.FERRY))).isEmpty())
        assertTrue(timetable.direct(weekday.copy(time=time("2027-03-02"))).isEmpty())
        assertTrue(timetable.direct(weekday.copy(origin=a.copy(isDevicePosition=true))).isEmpty())
        val arrival=timetable.direct(weekday.copy(timeMode=TimeMode.ARRIVAL))
        assertTrue(arrival.isNotEmpty())
        assertTrue(arrival.all { it.endTime<=weekday.time })
    }

    @Test fun repeatRequiresAnotherDayAndCanBeCleared() = runBlocking {
        val memory=RegularRouteMemory(RuntimeEnvironment.getApplication())
        val settings=AppSettings()
        val query=JourneyQuery(a,b,time("2026-11-26"))
        fun plan(q:JourneyQuery)=(1..3).map { i ->
            val t=q.time+i*300_000
            Trip("t$i",listOf(TripSegment("ride",a,b,t,t+600_000,LineType.REGIONAL,"RB43")),"VRR GTFS",1L)
        }
        fun live(rows:List<Trip>)=rows.map { trip -> trip.copy(segments=trip.segments.map {
            it.copy(origin=a.copy(id="20007501",provider="VRR",dhid=a.id)) }) }
        memory.clear()
        memory.observe(query,settings,plan(query),live(plan(query)),query.time)
        memory.observe(query,settings,plan(query),live(plan(query)),query.time)
        assertFalse(memory.candidate(query,settings,query.time))
        val next=query.copy(time=time("2026-11-27"))
        val scheduled=plan(next)
        memory.observe(next,settings,scheduled,live(scheduled),next.time)
        assertEquals("20007501",memory.boardingStop(next,settings,scheduled,next.time)?.id)
        val modified=scheduled.toMutableList().apply { this[0]=first().copy(fetchedAt=2L) }
        assertNull(memory.boardingStop(next,settings,modified,next.time))
        memory.clear()
        assertFalse(memory.candidate(next,settings,next.time))
    }
}
