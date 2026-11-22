// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.StateSerializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Migration (task 14): Build 64 data without the new fields decodes with safe defaults. */
class Build65MigrationTest {
    private val now=1_800_000_000_000L
    private val sessionJson=Json {ignoreUnknownKeys=true;encodeDefaults=true} // as in PinnedJourneyController
    private val a=Location("Essen Hbf","a",51.0,7.0,"TEST")
    private val c=Location("Bochum Hbf","c",51.0,7.2,"TEST")

    /** Removes the given keys at every depth, giving the shape a Build 64 store had. */
    private fun JsonElement.without(vararg keys:String):JsonElement=when(this) {
        is JsonObject->JsonObject(filterKeys {it !in keys}.mapValues {(_,value)->value.without(*keys)})
        is JsonArray->JsonArray(map {it.without(*keys)})
        else->this
    }

    @Test fun build64SettingsAndFavoritesSurviveAndTheBubbleNeedsAFreshOptIn()=runBlocking {
        val home=Favorite("home","Zuhause",c);val twin=Favorite("twin","Zuhause",c) // intentional duplicate place
        val current=StoredState(favorites=listOf(home,twin),settings=AppSettings(pinnedBubble=true,
            walkingSpeed=WalkingSpeed.RELAXED))
        val old=StateSerializer.json.encodeToJsonElement(StoredState.serializer(),current).without("pinnedBubble")
        val restored=StateSerializer.readFrom(old.toString().byteInputStream())
        assertFalse("an overlay is never switched on by an update",restored.settings.pinnedBubble)
        assertEquals(WalkingSpeed.RELAXED,restored.settings.walkingSpeed)
        assertEquals(listOf("home","twin"),restored.favorites.map {it.id})
    }

    @Test fun build64PinnedSessionsGetDerivedProvenanceButNoInventedFreshnessOrAlightingState() {
        val ride=TripSegment("ride",a,c,now+180_000,now+600_000,LineType.REGIONAL,"RE 1",plannedStartTime=now,plannedEndTime=now+600_000,
            delay=DelayInfo(departureSeconds=180),departurePlatform="7")
        val trip=Trip("t",listOf(ride),"TEST",now-60_000)
        val session=PinnedJourneySession(favorite=Favorite("f","Bochum",c),query=JourneyQuery(a,c,now),initialTrip=trip,routing=AppSettings())
            .copy(bubbleDismissed=true)
        val newKeys=arrayOf("bubbleDismissed","provisionalAlighting","stayAboard","departureSource","arrivalSource",
            "realtimeReceivedAt","realtimeGeneratedAt","forecastChangedAt","plannedArrivalTime","plannedDepartureTime","realtime")
        val old=sessionJson.encodeToJsonElement(PinnedJourneySession.serializer(),session).without(*newKeys)
        val restored=sessionJson.decodeFromJsonElement(PinnedJourneySession.serializer(),old)
        assertFalse(restored.bubbleDismissed);assertNull(restored.provisionalAlighting);assertNull(restored.stayAboard)
        val leg=restored.trip.segments.single()
        assertEquals("a known departure delay stays a forecast",TimeSource.PROGNOSIS,leg.departureTimeSource())
        assertEquals("the arrival is carried, not claimed as forecast",TimeSource.CARRIED,leg.arrivalTimeSource())
        assertNull("no receipt time is invented for old data",leg.realtimeReceivedAt)
        assertEquals("7",leg.departurePlatform)
        assertEquals(session.id,restored.id)
    }
}
