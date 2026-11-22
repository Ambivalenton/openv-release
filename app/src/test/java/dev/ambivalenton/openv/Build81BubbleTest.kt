// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** The bubble names the next change or the destination, never the next intermediate stop. */
class Build81BubbleTest {
    private val zone=ZoneId.of("Europe/Berlin")
    private fun at(h:Int,m:Int)=LocalDateTime.of(2026,11,17,h,m).atZone(zone).toInstant().toEpochMilli()
    private val a=Location("Essen Hbf","a",51.451,7.014,"TEST")
    private val mid=Location("Wattenscheid","m",51.480,7.140,"TEST")
    private val b=Location("Bochum Hbf","b",51.478,7.223,"TEST")
    private val mid2=Location("Langendreer","n",51.490,7.320,"TEST")
    private val c=Location("Dortmund Hbf","c",51.518,7.459,"TEST")
    private val home=Location("Zuhause",latitude=51.52,longitude=7.47,kind=LocationKind.ADDRESS)
    private fun ride(id:String,from:Location,via:Location,to:Location,start:Long,end:Long,platform:String)=TripSegment(id,from,to,start,end,
        LineType.REGIONAL,"RE $id",departurePlatform="1",arrivalPlatform=platform,
        stops=listOf(Stopover(via,start+(end-start)/2,start+(end-start)/2+60_000L)),
        departureSource=TimeSource.PLANNED,arrivalSource=TimeSource.PLANNED)
    private fun walk(from:Location,to:Location,start:Long,minutes:Int)=TripSegment("walk-$start",from,to,start,start+minutes*60_000L,LineType.WALK,"Zu Fuß")
    private val trip=Trip("t",listOf(
        ride("1",a,mid,b,at(12,0),at(12,20),"5"),
        walk(b,b,at(12,20),4),
        ride("2",b,mid2,c,at(12,30),at(12,50),"7"),
        walk(c,home,at(12,50),8)),"TEST",at(11,50))
    private val session=PinnedJourneySession(favorite=Favorite(label="Zuhause",location=home),query=JourneyQuery(a,home,trip.startTime),
        initialTrip=trip,routing=AppSettings())

    private fun bubble(now:Long)=assistantDisplayFocus(PinnedJourneyState(session=session),now)!!.let {bubbleJourneyFocus(it,session,now)}

    @Test fun duringTheFirstRideTheBubbleShowsTheArrivalAtTheChangeNotTheNextStop() {
        val now=at(12,5)
        val card=assistantDisplayFocus(PinnedJourneyState(session=session),now)!!
        assertEquals("the card still names the next call","Wattenscheid",card.nextPlace)
        val focus=bubble(now)
        assertEquals("Bochum Hbf",focus.nextPlace)
        assertEquals(at(12,20),focus.nextTime)
        assertEquals(tr("Umstieg"),focus.action)
        // Remaining-stop count of the same ride is unchanged.
        assertEquals(remainingRideStops(card),remainingRideStops(focus))
    }

    @Test fun duringTheLastRideTheBubbleShowsTheArrivalAtTheDestinationIncludingTheFinalWalk() {
        val focus=bubble(at(12,35))
        assertEquals("Zuhause",focus.nextPlace)
        assertEquals(at(12,58),focus.nextTime)
        assertEquals(tr("Ziel"),focus.action)
        assertNull("a final walk follows: no arrival platform as destination",focus.platform)
    }

    @Test fun waitingToBoardKeepsTheDepartureTime() {
        val focus=bubble(at(11,55))
        assertTrue(focus.boarding)
        assertEquals(at(12,0),focus.nextTime)
    }

    @Test fun aTransferWalkEndsAtTheNextBoardingPlace() {
        val focus=bubble(at(12,22))
        assertEquals(at(12,24),focus.nextTime)
    }

    @Test fun aDelayedArrivalAtTheChangeMovesTheBubbleTime() {
        val late=trip.copy(segments=trip.segments.mapIndexed {i,s->if(i==0)s.copy(endTime=at(12,26),delay=DelayInfo(0,360),
            arrivalSource=TimeSource.PROGNOSIS,realtimeReceivedAt=at(12,4),forecastChangedAt=at(12,4)) else s})
        val pinned=session.copy(initialTrip=late,trip=late)
        val now=at(12,5)
        val focus=assistantDisplayFocus(PinnedJourneyState(session=pinned),now)!!.let {bubbleJourneyFocus(it,pinned,now)}
        assertEquals(at(12,26),focus.nextTime)
    }
}
