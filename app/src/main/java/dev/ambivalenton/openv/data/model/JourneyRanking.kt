// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * How Öpen-V weighs connections. Three building blocks, all local and deterministic:
 *
 * 1. **Wahrgenommene Reisezeit** (generalised cost, as used in transport planning): every minute
 *    counts, walking and waiting at a transfer count extra, each transfer costs fixed minutes, and
 *    very many transfers or very long walks count more than proportionally. The preferences set
 *    the weights ([JourneyWeights]); [JourneyWeights.realism] keeps a floor even for "Beliebig oft".
 * 2. **Anschlussrisiko**: the estimated chance of missing a transfer (time buffer against the usual
 *    spread of the arriving service, larger without real-time data) times the minutes lost until
 *    the next service. A heuristic without historical delay data, see [transferRiskMinutes].
 * 3. **Unscharfe Dominanz** (after Delling et al., "Computing Multimodal Journeys in Practice",
 *    2013): a connection is hidden when a comparable one – departing and arriving at about the same
 *    time, the tolerances relative to the journey's duration – is clearly less effort and nowhere
 *    noticeably worse ([hiddenConnections]).
 */
data class JourneyWeights(
    /** Extra cost per minute of leaving later than requested (arrival search: of arriving earlier). */
    val waitAtStart: Double,
    /** Minutes per transfer. */
    val transfer: Double,
    /** Extra minutes per minute of walking (on top of the minute itself). */
    val walkExtra: Double,
    /** Extra minutes per minute of waiting at a transfer. */
    val waitExtra: Double,
    /** Factor on the expected minutes lost through missed transfers. */
    val risk: Double,
) {
    /** The floor of plausibility: even "Beliebig oft" or "Kein Problem" never makes detours free. */
    fun realism() = copy(transfer = max(transfer, 5.0), walkExtra = max(walkExtra, .5), risk = max(risk, 1.0))

    companion object {
        fun of(p: OverviewRoutingPreferences) = JourneyWeights(
            waitAtStart = when (p.effectivePriority) { RoutePriority.ARRIVAL -> 0.0; RoutePriority.BALANCED -> .05; else -> .15 },
            transfer = when (p.effectiveTransfers) {
                TransferPreference.FEWEST -> 20.0; TransferPreference.FEWER -> 10.0
                TransferPreference.NORMAL -> 5.0; TransferPreference.ANY -> 2.0
            },
            walkExtra = when (p.effectiveWalking) {
                WalkingPreference.MINIMAL -> 2.5; WalkingPreference.LESS -> 1.5
                WalkingPreference.NORMAL -> .5; WalkingPreference.RELAXED -> .1
            },
            waitExtra = .5,
            risk = when (p.buffer) { TransferBuffer.TIGHT -> .5; TransferBuffer.NORMAL -> 1.0; TransferBuffer.SAFE -> 2.5 },
        )
    }
}

/** The measured facts of one connection that the weighing uses (minutes, epoch millis). */
data class JourneyProfile(
    val start: Long, val end: Long, val rides: Int, val transfers: Int,
    /** Null when a walk has neither a duration nor a distance: unknown is not zero. */
    val walkMinutes: Double?, val waitMinutes: Double, val riskMinutes: Double,
) {
    val durationMinutes: Double get() = ((end - start) / MINUTE).coerceAtLeast(1.0)
}

private const val MINUTE = 60_000.0
private val LN_HALF = ln(.5)

/** 1 when [x] ≤ 0, one half at [x] = [tolerance], falling softly (Gaussian) beyond. */
internal fun fuzzyNotWorse(x: Double, tolerance: Double): Double = if (x <= 0) 1.0 else exp(LN_HALF * (x / tolerance).pow(2))
/** 0 when [x] ≤ 0, one half at [x] = [scale], approaching 1 for a clear advantage. */
internal fun fuzzyClearly(x: Double, scale: Double): Double = if (x <= 0) 0.0 else 1 - exp(LN_HALF * (x / scale).pow(2))

object JourneyRanking {
    /** A connection is hidden from a dominance degree of this size on. */
    const val HIDE_AT = .6

    fun profile(trip: Trip, query: JourneyQuery, now: Long): JourneyProfile? {
        if (trip.segments.isEmpty()) return null
        var walk: Double? = 0.0
        if (!query.ignoreWalking) for (leg in trip.segments) if (leg.isWalk) {
            // Prefer supplied durations and distances; a walk with neither is unknown, not zero.
            walk = if (leg.endTime > leg.startTime || (leg.distanceMeters ?: -1) >= 0)
                walk?.plus(walkingDurationMillis(leg, query.walkingSpeed) / MINUTE) else null
        }
        val rides = trip.segments.withIndex().filterNot { it.value.isWalk }
        val wait = rides.zipWithNext().sumOf { (a, b) ->
            val between = if (query.ignoreWalking) 0L else trip.segments.subList(a.index + 1, b.index)
                .filter { it.isWalk }.sumOf { walkingDurationMillis(it, query.walkingSpeed) }
            ((b.value.effectiveDeparture() - a.value.effectiveArrival() - between) / MINUTE).coerceAtLeast(0.0)
        }
        return JourneyProfile(trip.queryStart(query.ignoreWalking), trip.queryEnd(query.ignoreWalking), rides.size, trip.transfers,
            walk, wait, transferRiskMinutes(trip, query, now))
    }

    /**
     * Expected minutes lost through missed transfers. Per transfer: the buffer (gap minus walking)
     * against the usual spread of the arriving service – smaller with a current forecast close to the
     * arrival, larger without one (no real-time is not punctuality) – gives a logistic miss
     * probability, times the usual wait for the next service of the departing kind.
     */
    fun transferRiskMinutes(trip: Trip, query: JourneyQuery, now: Long): Double {
        val rides = trip.segments.withIndex().filterNot { it.value.isWalk }
        return rides.zipWithNext().sumOf { (before, after) ->
            val walk = if (query.ignoreWalking) 0L else trip.segments.subList(before.index + 1, after.index)
                .filter { it.isWalk }.sumOf { walkingDurationMillis(it, query.walkingSpeed) }
            val arriving = before.value
            val slack = (after.value.effectiveDeparture() - arriving.effectiveArrival() - walk) / MINUTE
            missProbability(slack, arrivalSpread(arriving, now)) * nextServiceMinutes(after.value.lineType)
        }
    }

    /** Logistic approximation of a normal distribution; half a minute is needed to change at all. */
    internal fun missProbability(slackMinutes: Double, spreadMinutes: Double): Double =
        1.0 / (1.0 + exp(1.7 * (slackMinutes - .5) / spreadMinutes.coerceAtLeast(.2)))

    /** Typical spread (minutes) of an arrival, by kind, horizon and whether a forecast exists. */
    internal fun arrivalSpread(leg: TripSegment, now: Long): Double {
        val base = when (leg.lineType) {
            LineType.SUBWAY, LineType.TRAM -> 1.2
            LineType.ICE -> 3.5
            else -> 2.0
        }
        val ahead = (leg.effectiveArrival() - now) / MINUTE
        if (ahead <= 0) return .3
        val forecast = leg.arrivalSource == TimeSource.PROGNOSIS || leg.delay.arrivalSeconds != null || leg.realtimeDeparture
        return if (forecast) base * (.35 + ahead / 90.0).coerceIn(.35, 1.0) else base * 1.2
    }

    /** Usual minutes until the next service of a kind, the price of a missed transfer. */
    internal fun nextServiceMinutes(type: LineType): Double = when (type) {
        LineType.SUBWAY -> 10.0; LineType.TRAM -> 12.0
        LineType.REGIONAL, LineType.FERRY -> 30.0; LineType.ICE -> 60.0
        else -> 20.0
    }

    /** Perceived minutes of [p] for [query]; lower is better. Unknown walking counts as [walkFallback]. */
    fun cost(p: JourneyProfile, query: JourneyQuery, w: JourneyWeights, walkFallback: Double? = null): Double {
        val time = if (query.timeMode == TimeMode.ARRIVAL)
            (query.time - p.start) / MINUTE + w.waitAtStart * ((query.time - p.end) / MINUTE).coerceAtLeast(0.0)
        else (p.end - query.time) / MINUTE + w.waitAtStart * ((p.start - query.time) / MINUTE).coerceAtLeast(0.0)
        val walk = p.walkMinutes ?: walkFallback ?: 0.0
        val many = (p.transfers - 3).coerceAtLeast(0).toDouble()
        val long = (walk - 10.0).coerceAtLeast(0.0)
        return time + w.transfer * p.transfers + 2.0 * many * many + w.walkExtra * walk + .02 * long * long +
            w.waitExtra * p.waitMinutes + w.risk * p.riskMinutes
    }

    /**
     * Degree (0–1) to which [a] makes [b] superfluous: comparable in time (tolerances relative to
     * [b]'s duration, narrower for "Bald losfahren" and "Früh ankommen" in their own dimension),
     * nowhere noticeably worse, and clearly less effort.
     */
    fun dominance(a: JourneyProfile, b: JourneyProfile, query: JourneyQuery, priority: RoutePriority, w: JourneyWeights): Double {
        val d = b.durationMinutes
        val (departTolerance, arriveTolerance) = when (priority) {
            RoutePriority.ARRIVAL -> (.8 * d).coerceIn(10.0, 40.0) to (.1 * d).coerceIn(2.0, 6.0)
            RoutePriority.BALANCED -> (.5 * d).coerceIn(8.0, 25.0) to (.15 * d).coerceIn(3.0, 9.0)
            else -> (.3 * d).coerceIn(5.0, 15.0) to (.2 * d).coerceIn(3.0, 12.0)
        }
        val forward = query.timeMode != TimeMode.ARRIVAL
        // Departure search: a may leave a little later (and hardly earlier) and must not arrive much later.
        // Arrival search, mirrored: a may arrive a little earlier (hardly later) and must not leave much earlier.
        val shifted = if (forward) (a.start - b.start) / MINUTE else (b.end - a.end) / MINUTE
        val against = if (forward) (a.end - b.end) / MINUTE else (b.start - a.start) / MINUTE
        val time = minOf(fuzzyNotWorse(shifted, departTolerance), fuzzyNotWorse(-shifted, 3.0), fuzzyNotWorse(against, arriveTolerance))
        val transfers = fuzzyNotWorse((a.transfers - b.transfers).toDouble(), .5)
        val walking = if (a.walkMinutes != null && b.walkMinutes != null) fuzzyNotWorse(a.walkMinutes - b.walkMinutes - 2.0, 3.0) else 1.0
        val risk = fuzzyNotWorse(a.riskMinutes - b.riskMinutes - 1.0, 3.0)
        val gain = cost(b, query, w, a.walkMinutes) - cost(a, query, w, b.walkMinutes)
        val clearly = fuzzyClearly(gain, max(8.0, .25 * d))
        return minOf(time, transfers, walking, risk, clearly)
    }

    /**
     * IDs of connections a comparable, clearly better one makes superfluous. Greedy by perceived
     * time with the realism floor, so every hidden connection has a shown one that beats it. Walks
     * without a vehicle, journeys with waypoints and unknown profiles always stay.
     */
    fun hiddenConnections(trips: List<Trip>, query: JourneyQuery, preferences: OverviewRoutingPreferences, now: Long): Set<String> {
        if (trips.size < 2 || query.waypoints.isNotEmpty()) return emptySet()
        val w = JourneyWeights.of(preferences).realism()
        val profiled = trips.mapNotNull { trip -> profile(trip, query, now)?.takeIf { it.rides > 0 && !trip.cancelled }?.let { trip to it } }
        val ordered = profiled.sortedWith(compareBy<Pair<Trip, JourneyProfile>> { cost(it.second, query, w) }.thenBy { it.second.start }.thenBy { it.first.id })
        val shown = mutableListOf<JourneyProfile>()
        val hidden = linkedSetOf<String>()
        for ((trip, p) in ordered) {
            if (shown.any { dominance(it, p, query, preferences.effectivePriority, w) >= HIDE_AT }) hidden += trip.id
            else shown += p
        }
        return hidden
    }
}
