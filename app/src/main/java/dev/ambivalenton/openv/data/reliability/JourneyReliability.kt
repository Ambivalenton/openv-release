// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.reliability

import dev.ambivalenton.openv.data.model.LineType
import dev.ambivalenton.openv.data.model.TimeSource
import dev.ambivalenton.openv.data.model.Trip
import dev.ambivalenton.openv.data.model.TripSegment
import java.time.Instant
import java.time.ZoneId

/** The model's mode of a ride: line names separate express and regional trains and fast regional buses. */
fun reliabilityMode(segment: TripSegment): ReliabilityMode {
    val name = segment.lineName.trim().uppercase()
    return when (segment.lineType) {
        LineType.ICE -> ReliabilityMode.LONG_DISTANCE
        LineType.REGIONAL -> when {
            name.startsWith("RE") -> ReliabilityMode.REGIONAL_EXPRESS
            name.startsWith("S") && name.drop(1).trim().firstOrNull()?.isDigit() == true -> ReliabilityMode.SUBURBAN
            else -> ReliabilityMode.REGIONAL_TRAIN
        }
        LineType.SUBURBAN -> ReliabilityMode.SUBURBAN
        LineType.SUBWAY -> ReliabilityMode.SUBWAY
        LineType.TRAM -> ReliabilityMode.TRAM
        LineType.BUS -> if (name.startsWith("SB") || name.startsWith("X") || name.startsWith("R")) ReliabilityMode.REGIONAL_BUS else ReliabilityMode.CITY_BUS
        LineType.FERRY -> ReliabilityMode.FERRY
        LineType.WALK, LineType.OTHER -> ReliabilityMode.OTHER
    }
}

/** The line as the personal memory knows it: type and name, independent of the trip and the day. */
fun reliabilityLineKey(segment: TripSegment) = "${segment.lineType.name}:${segment.lineName.trim().uppercase()}"

/** A realtime minute value only where an operator forecast (or a carried or local estimate) exists; none means unknown, not on time. */
private fun knownMinutes(seconds: Int?, source: TimeSource): Int? = when (source) {
    TimeSource.PLANNED -> null
    TimeSource.UNKNOWN -> seconds
    else -> seconds ?: 0
}?.let { Math.floorDiv(it + 30, 60) }

/**
 * Build 117 · the reliability of [trip] from now on: cancelled rides make it very unsafe, completed
 * rides ([skipRide]) no longer count, realtime forecasts replace the priors, [memory] adds the
 * person's own reports.
 */
fun rateTrip(trip: Trip, memory: PersonalPunctuality? = null, zone: ZoneId = ZoneId.systemDefault(),
    skipRide: (TripSegment) -> Boolean = { false }): ReliabilityRating {
    val segments = trip.segments.filterNot(skipRide)
    if (segments.any { !it.isWalk && it.delay.cancelled }) return ReliabilityRating(emptyList(), 0.0, 0.0, 0, ReliabilityLevel.VERY_UNSAFE, true, false)
    val rides = mutableListOf<RideInput>(); val walks = mutableListOf<Int>(); var walk = 0
    for (s in segments) {
        if (s.isWalk) { walk += ((s.plannedEndTime - s.plannedStartTime) / 60_000L).toInt().coerceAtLeast(0); continue }
        if (rides.isNotEmpty()) walks += walk
        walk = 0
        // The run before boarding is not in the response; minutesIntoRun stays unknown.
        rides += RideInput(reliabilityMode(s), reliabilityLineKey(s), s.plannedStartTime, s.plannedEndTime,
            RideContext(Instant.ofEpochMilli(s.plannedStartTime).atZone(zone)),
            knownMinutes(s.delay.departureSeconds, s.departureSource), knownMinutes(s.delay.arrivalSeconds, s.arrivalSource))
    }
    return rateJourney(rides, walks, memory)
}
