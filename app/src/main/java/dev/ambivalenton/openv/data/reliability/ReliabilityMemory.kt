// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.reliability

import kotlinx.serialization.Serializable

/** Own reports for one key: how often the ride was on time or late, and when the last report came. */
@Serializable
data class PunctualityCount(val onTime: Int = 0, val late: Int = 0, val lastAt: Long = 0) {
    val total: Int get() = onTime + late
}

/**
 * Build 117 · the person's own punctuality reports ("pünktlich", "verspätet") on pinned rides. Each
 * report counts for the line and for the traffic mode, each in its time window, so a line learns from
 * its own rides and a new line from its mode. Only what the person reported is stored – the model's
 * own estimates never come back as observations. Bounded to [MAX_KEYS] keys (the oldest go first),
 * kept on the device (excluded from backups) and deletable in the settings.
 */
@Serializable
data class ReliabilityMemory(
    val counts: Map<String, PunctualityCount> = emptyMap(),
    /** Ride keys already reported, so one ride counts once (newest last, bounded). */
    val reported: List<String> = emptyList(),
) : PersonalPunctuality {
    val reports: Int get() = counts.filterKeys { it.startsWith("mode:") }.values.sumOf { it.total }
    val isEmpty: Boolean get() = counts.isEmpty()

    fun hasReported(rideKey: String) = rideKey in reported

    /** Adds one report for a ride of [lineKey] / [mode] in [window]; a second report for [rideKey] replaces nothing and is ignored. */
    fun record(rideKey: String, lineKey: String, mode: ReliabilityMode, window: TimeWindow, onTime: Boolean, now: Long): ReliabilityMemory {
        if (rideKey in reported) return this
        val next = counts.toMutableMap()
        for (key in listOf(lineKey(lineKey, window), modeKey(mode, window))) {
            val old = next[key] ?: PunctualityCount()
            next[key] = if (onTime) old.copy(onTime = (old.onTime + 1).coerceAtMost(MAX_COUNT), lastAt = now)
                else old.copy(late = (old.late + 1).coerceAtMost(MAX_COUNT), lastAt = now)
        }
        val bounded = if (next.size <= MAX_KEYS) next else next.entries.sortedByDescending { it.value.lastAt }.take(MAX_KEYS).associate { it.toPair() }
        return ReliabilityMemory(bounded, (reported + rideKey).takeLast(MAX_REPORTED))
    }

    /** A line uses its own reports from [MIN_REPORTS] on, otherwise those of its mode, otherwise none. */
    override fun counts(lineKey: String, mode: ReliabilityMode, window: TimeWindow): PunctualityCount? =
        counts[lineKey(lineKey, window)]?.takeIf { it.total >= MIN_REPORTS }
            ?: counts[modeKey(mode, window)]?.takeIf { it.total >= MIN_REPORTS }

    companion object {
        const val MAX_KEYS = 300
        const val MAX_REPORTED = 64
        const val MAX_COUNT = 500
        const val MIN_REPORTS = 3
        fun lineKey(line: String, window: TimeWindow) = "line:${line.take(40)}|${window.name}"
        fun modeKey(mode: ReliabilityMode, window: TimeWindow) = "mode:${mode.name}|${window.name}"
    }
}
