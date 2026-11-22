// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Retained for source compatibility; inertial motion no longer determines journey progress. */
data class DeviceMotion(val capturedAt:Long,val eastAcceleration:Double,val northAcceleration:Double)
val GPS_PRIORITY_RAIL=setOf(LineType.ICE,LineType.REGIONAL,LineType.SUBURBAN)

/** Every transport mode now obeys the same GPS-only position contract. No extrapolation,
 * no discrepancy threshold, and no timetable position while a usable GPS fix exists. */
@Suppress("UNUSED_PARAMETER")
fun gpsPriorityJourneyProgress(trip:Trip,now:Long,observations:List<DevicePresence>,motion:DeviceMotion?=null):VisualJourneyProgress {
    val fix=observations.maxByOrNull {it.capturedAt}?.let {currentJourneyGps(it,now)}
    val origin=trip.segments.firstOrNull()?.origin ?: return VisualJourneyProgress(0,computedAt=now)
    val destination=trip.segments.last().destination
    val session=PinnedJourneySession(favorite=Favorite(label=destination.name,location=destination),
        query=JourneyQuery(origin,destination,trip.startTime),initialTrip=trip,routing=AppSettings())
    return resolvedJourneyProgress(advanceGpsJourney(session,fix,observations,now),now,fix)
}
