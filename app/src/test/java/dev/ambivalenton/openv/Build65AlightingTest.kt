// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import org.junit.Assert.*
import org.junit.Test

/** Acceptance group E (progress and alighting) of build 65. */
class Build65AlightingTest {
    private val now=1_800_000_000_000L
    private val a=Location("Essen Hbf","a",51.0,7.0,"TEST")
    private val b=Location("Essen-Steele","b",51.0,7.1,"TEST")
    private val c=Location("Bochum Hbf","c",51.0,7.2,"TEST")
    private val home=Location("Ziel",latitude=51.003,longitude=7.203,kind=LocationKind.ADDRESS)
    private val ride=TripSegment("ride",a,c,now-600_000,now+600_000,LineType.REGIONAL,"RE 1",
        stops=listOf(Stopover(b,now,now+60_000)))
    private val walk=TripSegment("walk",c,home,now+600_000,now+900_000,LineType.WALK,"Zu Fuß")
    private val trip=Trip("t",listOf(ride,walk),"TEST",now-900_000)
    private fun session()=PinnedJourneySession(favorite=Favorite("f","Ziel",home),query=JourneyQuery(a,home,now-900_000),
        initialTrip=trip,routing=AppSettings())
    private fun fix(lon:Double,at:Long,speed:Float?)=DevicePresence(Location("GPS",latitude=51.0,longitude=lon),at,12f,speed)
    private val riding=session().copy(gpsCursor=GpsRouteCursor(journeyLegKey(0,ride),0,5_000.0,true,now))
    private val alighted=riding.copy(completedRideKeys=setOf(stationRideKey(ride)),
        gpsCursor=GpsRouteCursor(journeyLegKey(1,walk),1,0.0,true,now))

    @Test fun anAutomaticAlightingStaysProvisionalAndIsWithdrawnByVehicleMotionOnTheSameRoute() {
        val provisional=reviseAlighting(riding,alighted,null,emptyList(),null,now)
        assertEquals(stationRideKey(ride),provisional.provisionalAlighting?.rideKey)
        assertEquals(TravelPhase.POSSIBLE_ALIGHTING,travelPhase(provisional,now).phase)
        // The train moves on along the same ride, well before its alighting stop (signal halt).
        val moving=(0..3).map {fix(7.04+it*.002,now+20_000+it*4_000L,14f)}
        val reverted=reviseAlighting(provisional,provisional,moving.last(),moving,null,now+32_000)
        assertFalse(stationRideKey(ride) in reverted.completedRideKeys)
        assertEquals(0,reverted.gpsCursor?.legIndex);assertTrue(reverted.gpsCursor!!.departed)
        assertNull(reverted.provisionalAlighting)
    }

    @Test fun withoutContradictionTheAlightingIsKeptAndTheWindowCloses() {
        val provisional=reviseAlighting(riding,alighted,null,emptyList(),null,now)
        val walking=(0..3).map {fix(7.2005+it*.0001,now+20_000+it*5_000L,1.2f)}
        val kept=reviseAlighting(provisional,provisional,walking.last(),walking,null,now+35_000)
        assertTrue(stationRideKey(ride) in kept.completedRideKeys)
        val later=reviseAlighting(kept,kept,null,emptyList(),null,now+AlightingPolicy.REVERT_WINDOW_MILLIS+1_000)
        assertNull(later.provisionalAlighting);assertTrue(stationRideKey(ride) in later.completedRideKeys)
    }

    @Test fun stillOnBoardWithdrawsAndHoldsTheRideAgainstImmediateReCompletion() {
        val provisional=reviseAlighting(riding,alighted,null,emptyList(),null,now)
        val corrected=stayAboardManually(provisional,now+5_000)
        assertFalse(stationRideKey(ride) in corrected.completedRideKeys)
        assertEquals(stationRideKey(ride),corrected.stayAboard?.rideKey)
        // Another automatic completion of the same ride while the hold is active is undone.
        val again=reviseAlighting(corrected,corrected.copy(completedRideKeys=setOf(stationRideKey(ride))),null,emptyList(),null,now+10_000)
        assertFalse(stationRideKey(ride) in again.completedRideKeys)
    }

    @Test fun iGotOffCompletesTheCurrentRideWithoutSteps() {
        val done=alightManually(riding,now+700_000)
        assertTrue(stationRideKey(ride) in done.completedRideKeys)
        assertEquals(1,done.gpsCursor?.legIndex)
        assertNull(done.provisionalAlighting)
    }

    @Test fun correctionsAreOfferedSparingly() {
        assertFalse("nothing to correct while riding on time",alightingCorrection(riding,now).let {it.offerAlighted || it.offerStillAboard})
        val provisional=reviseAlighting(riding,alighted,null,emptyList(),null,now)
        assertTrue(alightingCorrection(provisional,now+10_000).offerStillAboard)
        // Overdue ride with lost GPS (e.g. wheelchair user, no steps): "I got off" is offered once due.
        assertTrue(alightingCorrection(riding,now+600_000+AlightingPolicy.ASK_AFTER_ARRIVAL_MILLIS+1_000).offerAlighted)
        assertFalse(alightingCorrection(stayAboardManually(riding,now+700_000),now+800_000).offerAlighted)
    }

    @Test fun eventsOfAnotherSessionOrRouteRevisionChangeNothing() {
        val other=alighted.copy(id="other")
        assertEquals(other,reviseAlighting(riding,other,null,emptyList(),null,now))
        val revised=alighted.copy(routeRevision=alighted.routeRevision+1)
        assertEquals(revised,reviseAlighting(riding,revised,null,emptyList(),null,now))
    }

    @Test fun theTolerantStationAreaNeverMakesTransferTimeZero() {
        val station=Location("Bochum Hbf","st",51.48,7.22,"TEST")
        fun train(minutes:Int)=Trip("alt$minutes",listOf(TripSegment("r$minutes",station,c,now+minutes*60_000L,now+(minutes+20)*60_000L,
            LineType.REGIONAL,"RE 2")),"TEST",now)
        // 150 m from the station: within the 200 m tolerance, but the platform is not proven.
        val presence=DevicePresence(Location("GPS",latitude=51.4813,longitude=7.22),now,10f)
        val query=JourneyQuery(presence.location,c,now)
        assertNull("30 s are not enough to change platforms",reachableAlternative(train(0).copy(segments=train(0).segments.map {
            it.copy(startTime=now+30_000,plannedStartTime=now+30_000)}),query,now,presence,WalkingSpeed.AVERAGE))
        assertNotNull(reachableAlternative(train(5),query,now,presence,WalkingSpeed.AVERAGE))
    }
}
