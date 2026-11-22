// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.parseEfaTrip
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Acceptance group E (current, consistent travel times) and A (platforms) of build 65. */
class Build65TimesTest {
    private val zone=ZoneId.of("Europe/Berlin")
    private fun at(h:Int,m:Int)=LocalDateTime.of(2026,11,9,h,m).atZone(zone).toInstant().toEpochMilli()
    private val a=Location("Essen Hbf","a",51.451,7.014,"TEST")
    private val b=Location("Bochum Hbf","b",51.478,7.223,"TEST")
    private val c=Location("Dortmund Hbf","c",51.518,7.459,"TEST")
    private val home=Location("Ziel",latitude=51.52,longitude=7.47,kind=LocationKind.ADDRESS)
    private fun ride(id:String,from:Location,to:Location,plannedStart:Long,plannedEnd:Long,depDelay:Int?=null,arrDelay:Int?=null,
        received:Long?=null,platform:String?=null)=TripSegment(id,from,to,plannedStart+(depDelay ?: 0)*60_000L,plannedEnd+(arrDelay ?: 0)*60_000L,
        LineType.REGIONAL,"RE $id",plannedStart,plannedEnd,DelayInfo(depDelay?.times(60),arrDelay?.times(60)),departurePlatform=platform,
        departureSource=if(depDelay!=null)TimeSource.PROGNOSIS else TimeSource.PLANNED,arrivalSource=if(arrDelay!=null)TimeSource.PROGNOSIS else TimeSource.PLANNED,
        realtimeReceivedAt=received.takeIf {depDelay!=null || arrDelay!=null},forecastChangedAt=received.takeIf {depDelay!=null || arrDelay!=null})
    private fun walk(from:Location,to:Location,start:Long,minutes:Int)=TripSegment("walk-$start",from,to,start,start+minutes*60_000L,LineType.WALK,"Zu Fuß")
    private fun session(trip:Trip)=PinnedJourneySession(favorite=Favorite(label="Ziel",location=home),query=JourneyQuery(a,home,trip.startTime),
        initialTrip=trip,routing=AppSettings())

    @Test fun aWalkAfterARecoveredArrivalStartsAtTheNewArrival() {
        // Expected arrival 10:10 is corrected to 10:05; the walk still carries its old 10:10 start.
        val corrected=ride("1",a,b,at(9,45),at(10,5),depDelay=0,arrDelay=0,received=at(9,50))
        val trip=Trip("t",listOf(corrected,walk(b,home,at(10,10),10)),"TEST",at(9,50))
        assertEquals(at(10,15),trip.effectiveArrival())
        assertEquals(at(10,15),projectJourneyTimes(trip,at(9,50)).finalArrival.time)
    }

    @Test fun waitingTimeAbsorbsADelayThatKeepsTheConnection() {
        val first=ride("1",a,b,at(17,50),at(18,10),depDelay=5,arrDelay=5,received=at(18,0))
        val second=ride("2",b,c,at(18,20),at(18,40),depDelay=0,arrDelay=0,received=at(18,0))
        val trip=Trip("t",listOf(first,walk(b,b,at(18,10),3),second),"TEST",at(18,0))
        val projection=projectJourneyTimes(trip,at(18,0))
        assertEquals(at(18,40),projection.finalArrival.time)
        val transfer=projection.transfers.single()
        // 18:20 − 18:15 − 3 min walk − 30 s boarding reserve = 90 s.
        assertEquals(90_000L,transfer.reserveMillis)
        assertEquals(TransferStatus.TIGHT,transfer.status)
        val late=trip.copy(segments=listOf(first.copy(endTime=at(18,19),delay=DelayInfo(300,540)),walk(b,b,at(18,10),3),second))
        val broken=projectJourneyTimes(late,at(18,0)).transfers.single()
        assertEquals(TransferStatus.BROKEN,broken.status)
        assertTrue(projectJourneyTimes(late,at(18,0)).finalArrival.approximate)
    }

    @Test fun onTimeThenPlusEightThenPlusThreeThenOnTimeReachesEveryView() {
        val planned=ride("1",a,c,at(12,0),at(12,30),depDelay=0,arrDelay=0,received=at(11,50))
        var trip=Trip("t",listOf(planned),"TEST",at(11,50))
        var pinned=session(trip)
        val now=at(11,55)
        for((step,delay) in listOf(0,8,3,0).withIndex()) {
            val received=at(11,56)+step*60_000L
            val update=Trip("t-$step",listOf(ride("1",a,c,at(12,0),at(12,30),depDelay=delay,arrDelay=delay,received=received)),"TEST",received)
            trip=continuousAssistantJourney(trip,update)
            pinned=pinned.copy(trip=trip)
            val expected=at(12,30)+delay*60_000L
            assertEquals("final arrival after +$delay",expected,assistantFinalArrival(pinned,now))
            val focus=assistantDisplayFocus(PinnedJourneyState(session=pinned),now)!!
            assertEquals(expected,focus.arrival)
            assertEquals(at(12,0)+delay*60_000L,focus.nextTime)
            assertTrue(assistantTravelSummary(pinned,now,focus).arrival.contains(clockTime(expected)))
            assertTrue(compactPinnedNotification(pinned,now,null,focus).text.contains(clockTime(at(12,0)+delay*60_000L)))
            assertTrue(pinnedHeaderPresentation(PinnedJourneyState(session=pinned),focus,null,now,true).arrival.endsWith(clockTime(expected)))
        }
        assertEquals(0,trip.liveDelaySeconds())
    }

    @Test fun aMissingArrivalForecastIsNotShownAsOnTime() {
        val json="""{"legs":[{"realtimeStatus":"MONITORED","points":[
            {"name":"Essen Hbf","ref":{"id":"1","coords":"7.014,51.451"},"dateTime":{"date":"09.11.2026","time":"12:00","rtDate":"09.11.2026","rtTime":"12:05"}},
            {"name":"Bochum Hbf","ref":{"id":"2","coords":"7.2,51.47"},"dateTime":{"date":"09.11.2026","time":"12:20"}}],
            "mode":{"name":"RE 1","symbol":"RE1","type":"0"},
            "stopSeq":[
              {"name":"Essen Hbf","ref":{"id":"1","coords":"7.014,51.451","depDateTime":"20261109 12:00","depDelay":"5","depValid":"1"}},
              {"name":"Essen-Steele","ref":{"id":"3","coords":"7.08,51.45","arrDateTime":"20261109 12:08","depDateTime":"20261109 12:09","arrDelay":"4","arrValid":"1","depDelay":"4","depValid":"1"}},
              {"name":"Wattenscheid","ref":{"id":"4","coords":"7.13,51.46","arrDateTime":"20261109 12:14","depDateTime":"20261109 12:15","arrValid":"0","depValid":"0"}},
              {"name":"Bochum Hbf","ref":{"id":"2","coords":"7.2,51.47","arrDateTime":"20261109 12:20","arrValid":"0"}}]}]}"""
        val leg=parseEfaTrip(Json.parseToJsonElement(json),receivedAt=at(11,58))!!.segments.single()
        assertEquals(300,leg.delay.departureSeconds)
        assertNull("no arrival forecast is not 0 minutes",leg.delay.arrivalSeconds)
        assertEquals(TimeSource.CARRIED,leg.arrivalSource)
        assertEquals(at(12,25),leg.endTime)
        assertEquals(at(12,12),leg.stops[1].arrivalTime);assertTrue(leg.stops[1].realtime)
        assertEquals(at(12,18),leg.stops[2].arrivalTime);assertFalse(leg.stops[2].realtime)
        assertEquals(at(12,14),leg.stops[2].plannedArrivalTime)
        val projection=projectJourneyTimes(Trip("t",listOf(leg),"TEST",at(11,58)),at(11,58))
        assertTrue(projection.finalArrival.approximate)
        assertEquals("ca. ",approximateMarker(projection.finalArrival).replace("approx.","ca."))
    }

    @Test fun anUnmonitoredLegHasNoInventedDelay() {
        val json="""{"legs":[{"points":[
            {"name":"Essen Hbf","ref":{"id":"1","coords":"7.014,51.451"},"dateTime":{"date":"09.11.2026","time":"12:00"}},
            {"name":"Bochum Hbf","ref":{"id":"2","coords":"7.2,51.47"},"dateTime":{"date":"09.11.2026","time":"12:20"}}],
            "mode":{"name":"RE 1","symbol":"RE1","type":"0"}}]}"""
        val leg=parseEfaTrip(Json.parseToJsonElement(json))!!.segments.single()
        assertNull(leg.delay.departureSeconds);assertNull(leg.delay.arrivalSeconds)
        assertEquals(TimeSource.PLANNED,leg.departureTimeSource());assertNull(leg.realtimeReceivedAt)
    }

    @Test fun aPartialRefreshDoesNotMakeOtherLegsFresh() {
        val t0=at(11,40)
        val old=Trip("t",listOf(ride("1",a,b,at(12,0),at(12,20),depDelay=2,arrDelay=2,received=t0),
            ride("2",b,c,at(12,30),at(12,50),depDelay=1,arrDelay=1,received=t0)),"TEST",t0)
        val received=at(11,55)
        // Only the first ride was received again; the provider sent no realtime for the second.
        val update=Trip("t",listOf(ride("1",a,b,at(12,0),at(12,20),depDelay=4,arrDelay=4,received=received),
            ride("2",b,c,at(12,30),at(12,50))),"TEST",received)
        val merged=continuousAssistantJourney(old,update)
        assertEquals(received,merged.segments[0].realtimeReceivedAt)
        assertEquals(t0,merged.segments[1].realtimeReceivedAt)
        assertEquals("the last known forecast is kept, not replaced by on-time",60,merged.segments[1].delay.arrivalSeconds)
        assertEquals(t0,staleRealtimeSince(session(merged),received))
    }

    @Test fun aRepeatedForecastKeepsItsChangeTimeAndAChangedOneMovesIt() {
        val t0=at(11,40);val t1=at(11,45);val t2=at(11,50)
        val first=Trip("t",listOf(ride("1",a,b,at(12,0),at(12,20),depDelay=3,arrDelay=3,received=t0)),"TEST",t0)
        val same=continuousAssistantJourney(first,first.copy(fetchedAt=t1,segments=listOf(ride("1",a,b,at(12,0),at(12,20),depDelay=3,arrDelay=3,received=t1))))
        assertEquals(t0,same.segments.single().forecastChangedAt);assertEquals(t1,same.segments.single().realtimeReceivedAt)
        val changed=continuousAssistantJourney(same,same.copy(fetchedAt=t2,segments=listOf(ride("1",a,b,at(12,0),at(12,20),depDelay=1,arrDelay=1,received=t2))))
        assertEquals(t2,changed.segments.single().forecastChangedAt)
    }

    private fun evidenceTrip():Trip {
        val leg=ride("1",a,c,at(12,0),at(12,40),depDelay=0,arrDelay=0,received=at(12,0))
        return Trip("t",listOf(leg),"TEST",at(12,0))
    }

    @Test fun localEvidenceOnlyMakesAMonitoredForecastLater() {
        val trip=evidenceTrip()
        // At 12:20 the passenger is still at the first quarter of the route: clearly behind.
        val behind=RideProgressEvidence(0,(distanceMeters(a,c) ?: 0.0)*.25,at(12,20))
        val later=projectJourneyTimes(trip,at(12,20),behind).finalArrival
        assertEquals(TimeSource.LOCAL,later.source);assertEquals(at(12,50),later.time)
        // Ahead of the schedule: a monitored forecast is not shortened by local evidence.
        val ahead=RideProgressEvidence(0,(distanceMeters(a,c) ?: 0.0)*.9,at(12,20))
        assertEquals(at(12,40),projectJourneyTimes(trip,at(12,20),ahead).finalArrival.time)
    }

    @Test fun withoutAnArrivalForecastLocalProgressCanAlsoShowARecoveredDelay() {
        // Only the departure delay (+8) is known; it is carried to the arrival as an estimate.
        val leg=ride("1",a,c,at(12,0),at(12,40),depDelay=8,received=at(12,0)).copy(endTime=at(12,48),arrivalSource=TimeSource.CARRIED)
        val trip=Trip("t",listOf(leg),"TEST",at(12,0))
        assertEquals(at(12,48),projectJourneyTimes(trip,at(12,20)).finalArrival.time)
        val onSchedule=RideProgressEvidence(0,(distanceMeters(a,c) ?: 0.0)*.5,at(12,20))
        val local=projectJourneyTimes(trip,at(12,20),onSchedule).finalArrival
        assertEquals(TimeSource.LOCAL,local.source);assertEquals(at(12,40),local.time)
        // Rail does not arrive more than one minute before its timetable.
        val ahead=RideProgressEvidence(0,(distanceMeters(a,c) ?: 0.0)*.95,at(12,20))
        assertEquals(at(12,39),projectJourneyTimes(trip,at(12,20),ahead).finalArrival.time)
    }

    @Test fun aLongStopWithoutNewStopObservationsStillMovesTheArrival() {
        val trip=evidenceTrip()
        val along=(distanceMeters(a,c) ?: 0.0)*.5
        val early=projectJourneyTimes(trip,at(12,24),RideProgressEvidence(0,along,at(12,24))).finalArrival.time
        val stillStanding=projectJourneyTimes(trip,at(12,30),RideProgressEvidence(0,along,at(12,30))).finalArrival.time
        assertEquals(at(12,44),early);assertEquals(at(12,50),stillStanding)
    }

    @Test fun oldOrRestoredEvidenceIsNotFresh() {
        val trip=evidenceTrip()
        val old=RideProgressEvidence(0,0.0,at(12,0))
        assertEquals(at(12,40),projectJourneyTimes(trip,at(12,10),old).finalArrival.time)
        assertEquals(TimeSource.PROGNOSIS,projectJourneyTimes(trip,at(12,10),old).finalArrival.source)
    }

    @Test fun theBoardingPlatformAppearsForADirectStartBeforeAnyRouteMatch() {
        val trip=Trip("t",listOf(ride("1",a,c,at(12,0),at(12,40),platform="4")),"TEST",at(11,50))
        val pinned=session(trip)
        val focus=assistantDisplayFocus(PinnedJourneyState(session=pinned),at(11,55))!!.copy(legIndex=null,boarding=false)
        assertTrue(nextBoardingInstruction(pinned,focus).endsWith("4"))
        val waiting=pinned.copy(gpsCursor=GpsRouteCursor(journeyLegKey(0,trip.segments[0]),0,0.0,false,at(11,55)))
        assertTrue(nextBoardingInstruction(waiting,focus.copy(legIndex=0,boarding=false)).endsWith("4"))
        val riding=pinned.copy(gpsCursor=GpsRouteCursor(journeyLegKey(0,trip.segments[0]),0,500.0,true,at(12,2)))
        assertEquals("",nextBoardingInstruction(riding,focus.copy(legIndex=0,boarding=false)))
    }

    @Test fun nextStopOnlyAppearsOnceTheRideCanHaveLeftNotOnEnteringTheStation() {
        val trip=Trip("t",listOf(ride("1",a,c,at(12,0),at(12,40),platform="4")),"TEST",at(11,40))
        val key=journeyLegKey(0,trip.segments[0])
        // Entering a large station: an imprecise fix already marks the ride as departed.
        val early=session(trip).copy(gpsCursor=GpsRouteCursor(key,0,260.0,true,at(11,50)))
        val fix=DevicePresence(Location("GPS",latitude=51.4520,longitude=7.0160),at(11,50),20f) // ~180 m from the stop point
        val waiting=resolvedJourneyProgress(early,at(11,50),fix)
        assertEquals("0:${trip.segments[0].id}",waiting.boardingLegId)
        val focus=gpsJourneyFocus(early,at(11,50),fix,waiting)
        assertTrue("departure and platform stay in view",focus.boarding)
        assertEquals(at(12,0),focus.nextTime);assertTrue(focus.platform.orEmpty().endsWith("4"))
        assertNotEquals(dev.ambivalenton.openv.localization.tr("Nächster Halt"),focus.action)
        // After the departure time the same evidence means riding: now the next stop is shown.
        val moved=fix.copy(location=Location("GPS",latitude=51.47,longitude=7.15),capturedAt=at(12,4))
        val riding=early.copy(gpsCursor=GpsRouteCursor(key,0,9_000.0,true,at(12,4)))
        val progress=resolvedJourneyProgress(riding,at(12,4),moved)
        assertNull(progress.boardingLegId)
        assertEquals(dev.ambivalenton.openv.localization.tr("Nächster Halt"),gpsJourneyFocus(riding,at(12,4),moved,progress).action)
    }

    @Test fun anOmittedPlatformIsNotAChangeAndAChangedOneIsReported() {
        val old=Trip("t",listOf(ride("1",a,c,at(12,0),at(12,40),depDelay=0,arrDelay=0,received=at(11,50),platform="4")),"TEST",at(11,50))
        val omitted=continuousAssistantJourney(old,old.copy(segments=listOf(old.segments.single().copy(departurePlatform=null))))
        assertEquals("4",omitted.segments.single().departurePlatform)
        val changed=continuousAssistantJourney(old,old.copy(segments=listOf(old.segments.single().copy(departurePlatform="7"))))
        assertEquals("7",changed.segments.single().departurePlatform)
        val copy=PinnedJourneyNarrator.describe(session(old).copy(trip=changed),at(11,52),null)
        assertTrue(copy.disruption.orEmpty().contains("7"))
    }
}
