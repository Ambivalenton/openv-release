// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import org.junit.Assert.*
import org.junit.Test

class JourneyBoardUpdatesTest {
    private val stop=Location("Start","25",provider="VRR")
    private val end=Location("Ziel","26",provider="VRR")
    private val leg=TripSegment("ride",stop,end,120_030_000,121_200_000,LineType.REGIONAL,"RE 1 (RRX)",
        direction="Bahnhof Ziel Gleis 1",departureSource=TimeSource.PLANNED,arrivalSource=TimeSource.PLANNED)
    private val trip=Trip("t",listOf(leg),"VRR",119_000_000)
    private val board=Departure("d",stop,"RE1",LineType.REGIONAL,"Bahnhof Ziel",120_000_000,120_120_000,DelayInfo(120),platform="4")

    @Test fun matchUpdatesBoardingAndEstimatedArrivalButRetainsWholeSearchTimestamp() {
        val updated=trip.withBoardDepartures(listOf(board),119_900_000)
        val ride=updated.segments.single()
        assertEquals(leg.plannedStartTime+120_000,ride.startTime)
        assertEquals(leg.plannedEndTime+120_000,ride.endTime)
        assertEquals(TimeSource.CARRIED,ride.arrivalSource)
        assertEquals(trip.fetchedAt,updated.fetchedAt)
        assertEquals("4",ride.departurePlatform)
        val cancelled=trip.withBoardDepartures(listOf(board.copy(delay=DelayInfo(cancelled=true))),119_900_000)
        assertTrue(cancelled.cancelled)
    }

    @Test fun nothingIsInferredFromAConflictingOrMissingBoardRow() {
        assertSame(trip,trip.withBoardDepartures(listOf(board,board.copy(id="ambiguous")),119_900_000))
        assertSame(trip,trip.withBoardDepartures(listOf(board.copy(destination="Andere Richtung")),119_900_000))
        assertSame(trip,trip.withBoardDepartures(listOf(board.copy(platform=null,delay=DelayInfo())),119_900_000))
    }
    @Test fun cacheReuseNeedsRecentCoverageForThreeUpcomingConnections() {
        val now=119_000_000L
        val query=JourneyQuery(stop,end,now)
        val settings=AppSettings()
        val rows=(0..2).map { i ->
            trip.copy(id="t$i",segments=listOf(leg.copy(startTime=leg.startTime+i*60_000L,
                plannedStartTime=leg.plannedStartTime+i*60_000L)))
        }
        val snapshot=JourneySearchCache(now={ now }).put(query,settings,false,true,
            JourneyWindow(rows,now,rows.last().endTime,1f,1,finished=true,complete=true))!!
        assertTrue(snapshot.covers(query,now))
        assertFalse(snapshot.covers(query.copy(time=leg.startTime+60_000),now))
        assertFalse(snapshot.covers(query,now+ROUTE_RECHECK))
        assertFalse(snapshot.copy(lastAttemptFailed=true).covers(query,now))
        assertFalse(snapshot.copy(complete=false).covers(query,now))
    }

}
