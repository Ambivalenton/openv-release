// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv
import dev.ambivalenton.openv.data.model.*

/** Shared journey fixture of several tests: places, rides and walks around one fixed moment. */
internal object Fixture45 {
    val now=1_800_000_000_000L
    fun point(x:Double,name:String="Ort")=Location(name=name,latitude=50.0,longitude=8.0+x)
    val a=point(0.0,"Start");val b=point(.01,"Mitte");val c=point(.02,"Ziel")
    fun ride(type:LineType=LineType.BUS,start:Long=now-600_000)=TripSegment("ride",a,c,start,start+600_000,type,"L1",
        stops=listOf(Stopover(b,start+300_000,start+320_000)),realtimeDeparture=true)
    fun trip(leg:TripSegment=ride())=Trip("journey",listOf(leg),"TEST",now)
    fun session(trip:Trip=trip())=PinnedJourneySession(favorite=Favorite(label="Ziel",location=c),query=JourneyQuery(a,c,now),initialTrip=trip,routing=AppSettings())
    fun fix(x:Double,at:Long=now,speed:Float=8f)=DevicePresence(point(x),at,10f,speed)
    fun track(session:PinnedJourneySession,fix:DevicePresence,observations:List<DevicePresence> = listOf(fix))=advanceGpsJourney(session,fix,observations,fix.capturedAt)
}
