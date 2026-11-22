// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

/** Provider timestamps already contain delays. Never add DelayInfo a second time. */
data class JourneyTimeWindow(val departure: Long, val arrival: Long) {
    fun progress(now: Long): Float = when {
        arrival <= departure -> if (now >= arrival) 1f else 0f
        else -> ((now.toDouble() - departure.toDouble()) /
            (arrival.toDouble() - departure.toDouble())).coerceIn(0.0, 1.0).toFloat()
    }
}

fun Trip.timeWindow(): JourneyTimeWindow {
    val first = segments.firstOrNull()
    val last = segments.lastOrNull()
    val live = JourneyTimeWindow(first?.startTime ?: startTime, last?.endTime ?: endTime)
    val planned = JourneyTimeWindow(first?.plannedStartTime ?: startTime, last?.plannedEndTime ?: endTime)
    return if (live.arrival > live.departure) live else planned
}

/** An announcement is state, not a lossy SharedFlow event: background arrival survives resume. */
data class JourneyArrival(val sessionId: String, val text: String, val detectedAt: Long)

const val ARRIVAL_DESTINATION_RADIUS_METERS = 250.0

enum class ArrivalPhase { TRACKING, CANDIDATE, CONFIRMED }

/** Two separate, fresh, slow observations prevent a noisy fix or passing train from closing a pin.
 * The 250 m destination fence does not imply that the rider has entered a building.
 */
fun pinnedArrivalPhase(session: PinnedJourneySession, now: Long, fixes: List<DevicePresence>): ArrivalPhase {
    val trip = session.trip
    if (trip.cancelled || trip.segments.isEmpty()) return ArrivalPhase.TRACKING
    val end = trip.timeWindow().arrival
    val lastLeg = trip.segments.last()
    if(session.gpsCursor!=null) {
        if(gpsCursorLeg(session)?.index!=trip.segments.lastIndex) return ArrivalPhase.TRACKING
    } else if (now < lastLeg.startTime || now < end - 120_000) return ArrivalPhase.TRACKING
    val fresh = fixes.distinctBy { it.capturedAt }.sortedBy { it.capturedAt }.filter {
        it.location.hasCoordinates && now - it.capturedAt in 0..30_000 &&
            it.accuracyMeters.isFinite() && it.accuracyMeters in 0f..50f
    }
    val radius=if(lastLeg.isWalk)75.0 else ARRIVAL_DESTINATION_RADIUS_METERS
    fun inside(fix: DevicePresence): Boolean =
        (distanceMeters(session.query.destination, fix.location) ?: Double.MAX_VALUE) + fix.accuracyMeters <= radius &&
            (fix.speedMetersPerSecond == null ||
                (fix.speedMetersPerSecond.isFinite() && fix.speedMetersPerSecond in 0f..3f))
    val current = fresh.lastOrNull()?.takeIf(::inside) ?: return ArrivalPhase.TRACKING
    val previous = fresh.dropLast(1).lastOrNull {
        current.capturedAt - it.capturedAt in 3_000..30_000 && inside(it)
    } ?: return ArrivalPhase.CANDIDATE
    val seconds = (current.capturedAt - previous.capturedAt) / 1_000.0
    if ((distanceMeters(previous.location, current.location) ?: Double.MAX_VALUE) / seconds > 3.0)
        return ArrivalPhase.CANDIDATE
    // Circular routes must have actually left the starting destination fence.
    if ((distanceMeters(session.query.origin, session.query.destination) ?: Double.MAX_VALUE) <= ARRIVAL_DESTINATION_RADIUS_METERS &&
        !session.leftDestinationFence && fixes.none { it.capturedAt >= trip.startTime &&
            (distanceMeters(it.location, session.query.destination) ?: 0.0) > ARRIVAL_DESTINATION_RADIUS_METERS + it.accuracyMeters })
        return ArrivalPhase.CANDIDATE
    return ArrivalPhase.CONFIRMED
}
