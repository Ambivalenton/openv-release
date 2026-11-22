// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model
import kotlinx.serialization.Serializable
import dev.ambivalenton.openv.localization.tr

/** An offline plan, never a running tracker. Days in the future are deliberately supported. */
@Serializable data class SavedJourney(val id:String=newId(),val favorite:Favorite,val query:JourneyQuery,val trip:Trip,
    val savedAt:Long=System.currentTimeMillis()) {
    val expiresAt:Long get()=trip.firstBoardingTime()
    fun available(now:Long)=trip.segments.isNotEmpty()&&!trip.cancelled&&now<expiresAt
    val identity:String get()=alternativeJourneyKey(trip)
}
fun StoredState.withSavedJourney(candidate:SavedJourney,now:Long):StoredState {
    require(candidate.query.destination.routable&&candidate.available(now)){tr("Diese Verbindung kann nicht mehr vorgemerkt werden.")}
    val current=savedJourneys.filter {it.available(now)}
    val duplicate=current.firstOrNull {it.identity==candidate.identity}
    require(duplicate!=null||current.size<32){tr("Bis zu 32 Vormerkungen sind möglich. Entferne zuerst eine alte Vormerkung.")}
    val replacement=if(duplicate==null)candidate else candidate.copy(id=duplicate.id)
    return copy(savedJourneys=(current.filterNot {it.id==replacement.id}+replacement).sortedBy {it.trip.firstBoardingTime()})
}
