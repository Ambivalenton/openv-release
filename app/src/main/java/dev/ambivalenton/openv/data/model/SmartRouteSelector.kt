// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/**
 * The time side of the preferences, a slider from "Bald losfahren" to "Früh ankommen".
 * [TRANSFERS] is only read from older settings (it now means "Umstiege: Möglichst wenig").
 */
@Serializable enum class RoutePriority(val titleKey: String) {
    NEXT("Bald losfahren"), ARRIVAL("Früh ankommen"), TRANSFERS("Wenig umsteigen"), BALANCED("Ausgewogen");
    val title: String get() = dev.ambivalenton.openv.localization.tr(titleKey)
    companion object { val slider = listOf(NEXT, BALANCED, ARRIVAL) }
}

/** How much walking is acceptable, in slider order. [MINIMAL] wins over every other preference. */
@Serializable enum class WalkingPreference(val titleKey: String) {
    MINIMAL("So wenig wie möglich"), LESS("Weniger"), NORMAL("Normal"), RELAXED("Kein Problem");
    val title: String get() = dev.ambivalenton.openv.localization.tr(titleKey)
}

/** How much a transfer weighs, in slider order. Never a hard limit. */
@Serializable enum class TransferPreference(val titleKey: String) {
    FEWEST("Möglichst wenig"), FEWER("Wenig"), NORMAL("Normal"), ANY("Beliebig oft");
    val title: String get() = dev.ambivalenton.openv.localization.tr(titleKey)
}

/** How much a tight transfer worries, in slider order (see [JourneyRanking.transferRiskMinutes]). */
@Serializable enum class TransferBuffer(val titleKey: String) {
    TIGHT("Knapp reicht"), NORMAL("Normal"), SAFE("Lieber mit Puffer");
    val title: String get() = dev.ambivalenton.openv.localization.tr(titleKey)
}

/**
 * The preferences for choosing a connection. Four sliders (time, transfers, walking,
 * time to change) and the leeway a better fitting connection may cost. They set the weights of
 * [JourneyWeights]; nothing is a hard limit except "So wenig wie möglich laufen". Fields of former
 * builds (three switches, the former priority and transfer limit) are still read, so saved
 * settings keep their meaning until a slider is moved.
 */
@Serializable
data class OverviewRoutingPreferences(
    val fewerTransfers: Boolean = false,
    val lessWalking: Boolean = false,
    val faster: Boolean = false,
    val priority: RoutePriority? = null,
    val walking: WalkingPreference? = null,
    /** Minutes a preferred connection may depart and arrive later than the next one (0–30). */
    val toleranceMinutes: Int = SmartRouteSelector.MAX_EXTRA_MINUTES,
    /** Older settings only: a hard transfer limit, now read as a transfer weight. */
    val maxTransfers: Int? = null,
    val transfers: TransferPreference? = null,
    val buffer: TransferBuffer = TransferBuffer.NORMAL,
) {
    val effectivePriority: RoutePriority get() = when (priority) {
        null -> if (faster) RoutePriority.ARRIVAL else RoutePriority.NEXT
        RoutePriority.TRANSFERS -> RoutePriority.NEXT
        else -> priority
    }
    val effectiveWalking: WalkingPreference get() = walking ?: if (lessWalking) WalkingPreference.LESS else WalkingPreference.NORMAL
    val effectiveTransfers: TransferPreference get() = transfers ?: when {
        maxTransfers != null && maxTransfers <= 1 -> TransferPreference.FEWEST
        maxTransfers != null -> TransferPreference.FEWER
        priority == RoutePriority.TRANSFERS || (priority == null && fewerTransfers) -> TransferPreference.FEWEST
        else -> TransferPreference.NORMAL
    }
    val tolerance: Int get() = toleranceMinutes.coerceIn(0, 30)
    val enabled: Boolean get() = effectivePriority != RoutePriority.NEXT || effectiveWalking != WalkingPreference.NORMAL ||
        effectiveTransfers != TransferPreference.NORMAL || buffer != TransferBuffer.NORMAL
    /** What changes requests and shown connections (the leeway only changes the overview's choice). */
    fun routingIdentity(): String = "$effectivePriority/$effectiveTransfers/$effectiveWalking/$buffer"
    /** Writes the slider fields and retires the former ones. */
    fun with(priority: RoutePriority = effectivePriority, walking: WalkingPreference = effectiveWalking,
        transfers: TransferPreference = effectiveTransfers, buffer: TransferBuffer = this.buffer, tolerance: Int = this.tolerance) =
        OverviewRoutingPreferences(priority = priority, walking = walking, transfers = transfers, buffer = buffer,
            toleranceMinutes = tolerance.coerceIn(0, 30))
}

/** Pure, deterministic selection from a bounded provider result, never a new journey search. */
object SmartRouteSelector {
    const val MAX_EXTRA_MINUTES = 15
    private const val MIN_GAIN = 2.0

    private val chronological = compareBy<Trip> { it.startTime }
        .thenBy { it.endTime }.thenBy { it.transfers }.thenBy { it.id }

    /**
     * The next eligible connection, unless one within the leeway is clearly better by the
     * perceived travel time of [JourneyRanking] (by at least [MIN_GAIN] minutes). "So wenig wie
     * möglich laufen" takes the least walking first; the perceived time decides between equals.
     */
    fun select(
        trips: List<Trip>,
        query: JourneyQuery,
        preferences: OverviewRoutingPreferences = OverviewRoutingPreferences(),
        now: Long = query.time,
    ): Trip? {
        val eligible = trips.filter { it.isEligible(query) }.sortedWith(chronological)
        val baseline = eligible.firstOrNull() ?: return null
        if (!preferences.enabled) return baseline
        val weights = JourneyWeights.of(preferences)
        val profiles = eligible.associateWith { JourneyRanking.profile(it, query, now) }
        fun cost(trip: Trip) = profiles[trip]?.let { JourneyRanking.cost(it, query, weights) } ?: Double.MAX_VALUE
        if (!query.ignoreWalking && preferences.effectiveWalking == WalkingPreference.MINIMAL) {
            val known = eligible.mapNotNull { trip -> profiles[trip]?.walkMinutes?.let { trip to it } }
            val least = known.minOfOrNull { it.second }
            if (least != null) return known.filter { it.second <= least + .5 }.map { it.first }
                .minWith(compareBy<Trip> { cost(it) }.then(chronological))
        }
        val window = preferences.tolerance * 60_000L
        val walkingMatters = !query.ignoreWalking && preferences.effectiveWalking == WalkingPreference.LESS
        val baselineCost = cost(baseline)
        val best = eligible.drop(1).filter { candidate ->
            // Both limits are anchored to the ORIGINAL next trip. No rolling/chained skips.
            candidate.startTime - baseline.startTime in 0..window && candidate.endTime - baseline.endTime <= window &&
                // Unknown walking data must not masquerade as zero metres/minutes.
                (!walkingMatters || (profiles[baseline]?.walkMinutes != null && profiles[candidate]?.walkMinutes != null))
        }.minWithOrNull(compareBy<Trip> { cost(it) }.then(chronological)) ?: return baseline
        return if (cost(best) < baselineCost - MIN_GAIN) best else baseline
    }

    private fun Trip.isEligible(query: JourneyQuery): Boolean =
        matchesTransportFilter(query.transportFilter) && hasUsableTransfers(query) && segments.isNotEmpty() && !cancelled && queryStart(query.ignoreWalking) >= query.time && endTime >= startTime &&
            segments.all { segment ->
                segment.endTime >= segment.startTime &&
                    (segment.isWalk || (segment.lineType in query.products &&
                        (LineType.ICE in query.products || !segment.isLongDistance())))
            }
}
