// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

/** Build 116 · the journey is under way: its first step (a walk or a ride) has begun. */
fun PinnedJourneySession.underWay(now: Long): Boolean = trip.segments.firstOrNull()?.let { now >= it.startTime } == true

/** What the person does at the step in focus. */
enum class BriefStepKind { BOARD, ALIGHT, WALK, ARRIVE }

/**
 * Build 116 · the essentials of a running journey, each exactly once (the card used to repeat the
 * transfer stop, the platform and the destination up to four times). [place] and [time] of the step in
 * focus lead; [platform] is the arrival platform when alighting (shown quietly) or the boarding
 * platform when boarding (highlighted); [next] is the onward ride after a change.
 */
data class PinnedJourneyBrief(
    val kind: BriefStepKind,
    val place: String,
    val time: Long,
    val approximate: Boolean,
    val line: String,
    val platform: String?,
    val delayMinutes: Int,
    val stopsLeft: Int,
    val next: BriefBoarding?,
    val arrival: Long,
    val arrivalApproximate: Boolean,
    val arrivalChange: Int,
    /** A short headline when something needs attention now ("Gleich aussteigen", "Fällt aus"), never the details again. */
    val alert: String?,
)

/** The next ride after a change: line, direction, platform (the important one) and departure. */
data class BriefBoarding(val line: String, val lineType: LineType, val direction: String?, val platform: String?, val time: Long,
    val status: TransferStatus?, val waitMinutes: Int?)

/** The brief for [focus] at [now]. */
fun pinnedJourneyBrief(session: PinnedJourneySession, focus: JourneyFocus, now: Long, alert: JourneyAlert? = null): PinnedJourneyBrief {
    val segments = session.trip.segments
    val projection = projectSessionTimes(session, now)
    val index = focus.legIndex
    val leg = index?.let { segments.getOrNull(it) }
    fun nextRideAfter(i: Int) = segments.withIndex().firstOrNull { (j, s) -> j > i && !s.isWalk && !s.delay.cancelled &&
        stationRideKey(s) !in session.completedRideKeys }
    fun boarding(entry: IndexedValue<TripSegment>?, fromIndex: Int?): BriefBoarding? = entry?.let { (j, s) ->
        val transfer = projection.transfers.firstOrNull { it.toIndex == j && (fromIndex == null || it.fromIndex == fromIndex) }
        BriefBoarding(s.lineName, s.lineType, s.direction?.takeIf { it.isNotBlank() }, platformName(s.departurePlatform, s.lineType),
            projection.leg(j)?.departure?.time ?: s.effectiveDeparture(), transfer?.status,
            transfer?.let { ((it.departure - it.arrival) / 60_000L).toInt() })
    }
    val final = projection.finalArrival
    // The alert's own short title ("Umstieg steht bevor"); its long message repeats what the brief already shows.
    val shortAlert = alert?.title?.takeIf { it.isNotBlank() }
    val cancelled = segments.firstOrNull { !it.isWalk && it.delay.cancelled && stationRideKey(it) !in session.completedRideKeys }
    val headline = shortAlert ?: cancelled?.let { tr("%1\$s fällt aus", it.lineName) }
    fun base(kind: BriefStepKind, place: String, event: ProjectedEvent?, fallback: Long, line: String, platform: String?, next: BriefBoarding?) =
        PinnedJourneyBrief(kind, place, event?.time ?: fallback, event?.approximate == true, line, platform,
            ((event?.delaySeconds ?: 0) / 60).coerceAtLeast(0), remainingRideStops(focus), next,
            final.time, final.approximate, arrivalChangeMinutes(session, now), headline)
    // Waiting to board (or before the first ride): the boarding is the step, its platform leads.
    if (leg == null || focus.boarding || leg.isWalk) {
        val ride = if (leg != null && !leg.isWalk && focus.boarding) IndexedValue(index, leg)
            else nextRideAfter(index ?: -1)
        if (ride == null) return base(BriefStepKind.ARRIVE, session.query.destination.name, final, final.time, "", null, null)
        val event = projection.leg(ride.index)?.departure
        val kind = if (leg?.isWalk == true && !focus.boarding) BriefStepKind.WALK else BriefStepKind.BOARD
        return base(kind, displayStopName(ride.value.origin, ride.value.lineType), event, ride.value.effectiveDeparture(), ride.value.lineName,
            platformName(ride.value.departurePlatform, ride.value.lineType), null).copy(stopsLeft = 0,
            next = boarding(ride, null)?.takeIf { kind == BriefStepKind.WALK })
    }
    // On a ride: alighting leads, the onward ride (if any) follows with its platform.
    val onward = nextRideAfter(index)
    val event = projection.leg(index)?.arrival
    val place = if (onward == null) displayStopName(leg.destination, leg.lineType) else displayStopName(leg.destination, leg.lineType)
    return base(if (onward == null && segments.drop(index + 1).none { !it.isWalk }) BriefStepKind.ARRIVE else BriefStepKind.ALIGHT,
        place, event, leg.endTime, leg.lineName, platformName(leg.arrivalPlatform, leg.lineType), boarding(onward, index))
}
