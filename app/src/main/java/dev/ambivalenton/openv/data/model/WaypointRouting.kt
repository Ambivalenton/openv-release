// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/** Timed visits and compact refresh references survive pinning without duplicating whole trips. */
@Serializable data class WaypointVisit(val location:Location,val arrivalTime:Long,val departureTime:Long,
    val requestedStayMillis:Long=0)
@Serializable data class WaypointLegReference(val query:JourneyQuery,val tripId:String,
    val refreshToken:String?=null,val segmentCount:Int)

fun waypointIdentity(place:Location):String = if(place.kind==LocationKind.STOP && !place.id.isNullOrBlank())
    "${place.provider}:${place.id}" else "${place.latitude}:${place.longitude}:${place.kind}:${place.name.trim()}"

/** The existing place identity comparison prevents accidental duplicates at the same named stop. */
fun distinctWaypoints(places:List<Location>)=places.distinctBy(::waypointIdentity)

fun normalizedWaypointStay(minutes:Float):Float=if(minutes.isFinite()) minutes.coerceIn(0f,120f) else 0f
fun waypointStayMillis(place:Location,minutes:Map<String,Float>):Long =
    (normalizedWaypointStay(minutes[waypointIdentity(place)] ?: 0f)*60_000.0).toLong()

/** Once a visit lies behind the recovery origin in time, it must never be requested again. */
fun remainingWaypoints(trip:Trip,fromTime:Long):List<Location> =
    if(trip.waypointVisits.isEmpty()) trip.waypoints
    else trip.waypointVisits.filter {maxOf(it.arrivalTime,it.departureTime)>=fromTime}.map {it.location}

/** Bind the editor to the pressed service, borrowing coordinates only from the same stop identity.
 * Preserve addresses, product restrictions, arrival/departure semantics and existing visits. */
fun waypointQueryForTrip(original:JourneyQuery?,trip:Trip):JourneyQuery {
    require(trip.segments.isNotEmpty())
    val known=trip.segments.flatMap{listOf(it.origin,it.destination)+it.stops.map{stop->stop.location}}+trip.waypoints
    fun enriched(place:Location):Location {
        if(place.hasCoordinates)return place
        val exact=known.firstOrNull{candidate->candidate.hasCoordinates&&candidate.kind==place.kind&&
            ((!place.id.isNullOrBlank()&&candidate.id==place.id&&candidate.provider==place.provider)||
                (!place.dhid.isNullOrBlank()&&candidate.dhid==place.dhid))}?:return place
        return place.copy(latitude=exact.latitude,longitude=exact.longitude)
    }
    val query=original?:JourneyQuery(trip.segments.first().origin,trip.segments.last().destination,trip.startTime)
    return query.copy(origin=enriched(query.origin),destination=enriched(query.destination),
        time=if(query.timeMode==TimeMode.DEPARTURE)trip.startTime else trip.endTime,
        waypoints=trip.waypoints.map(::enriched),waypointDwellMinutes=trip.waypointDwellMinutes)
}
