// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import kotlin.math.abs
import kotlin.math.tanh

/** The shortest and the longest connection in view carry a mark; all others none. */
internal enum class DurationMark { NONE, SHORTEST, LONGEST }

/**
 * Build 114 · how one duration badge stands among the connections in view.
 *
 * [scale] grows shorter connections a little and shrinks longer ones (font and background together),
 * [tint] leans towards the accent (negative, shorter) or towards grey (positive, longer). Both follow
 * the deviation from the average in view through a soft curve: a few minutes make a quiet difference,
 * twice the average a clear one, and nothing ever grows without bound.
 */
internal data class DurationLook(val scale: Float = 1f, val tint: Float = 0f, val mark: DurationMark = DurationMark.NONE)

/** Largest change in size (±16 %) and in colour (45 % of the way to accent or grey). */
internal const val DURATION_SCALE_RANGE = .16f
internal const val DURATION_TINT_RANGE = .45f

/**
 * Looks for every connection of [minutes] (by id) relative to those [inView]. Connections outside the
 * view are measured against the same average (they slide in without a jump) but never carry a mark.
 * Marks need at least two connections in view that differ by a minute or more; equal durations share it.
 */
internal fun durationLooks(minutes: Map<String, Long>, inView: List<String>): Map<String, DurationLook> {
    val seen = inView.mapNotNull { minutes[it] }
    if (seen.isEmpty()) return minutes.mapValues { DurationLook() }
    val average = seen.average().coerceAtLeast(1.0)
    val shortest = seen.min(); val longest = seen.max()
    val marked = seen.size >= 2 && longest - shortest >= 1
    val visible = inView.toSet()
    return minutes.mapValues { (id, value) ->
        val deviation = ((value - average) / average).toFloat()
        val mark = when {
            !marked || id !in visible -> DurationMark.NONE
            value == shortest -> DurationMark.SHORTEST
            value == longest -> DurationMark.LONGEST
            else -> DurationMark.NONE
        }
        DurationLook(
            scale = 1f - DURATION_SCALE_RANGE * tanh(2f * deviation),
            tint = (DURATION_TINT_RANGE * tanh(1.5f * abs(deviation))).let { if (deviation < 0f) -it else it },
            mark = mark,
        )
    }
}
