// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.reliability

import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Build 117 · Verlässlichkeit (reliability) of a connection – the core of the model.
 *
 * Each ride is modelled by a delay distribution: with probability `punctual` the vehicle is on time
 * (0 minutes), otherwise late by an exponentially distributed amount with mean `lateMean` minutes;
 * with probability `cancel` it does not run (short-notice cancellation; planned cancellations are
 * already absent from the timetable). The priors come from published punctuality figures
 * (docs/reliability.md), context factors scale them (rush hour, weekend, night, season, distance into
 * the run), realtime forecasts replace them where present, and the person's own reports adjust them
 * locally (Beta posterior with the prior as pseudo-counts, see ReliabilityMemory).
 *
 * A transfer holds when feeder delay − connecting delay ≤ slack (scheduled gap minus walking time);
 * both distributions are convolved on a one-minute grid. A journey's rating combines the chance that
 * every ride runs and every transfer holds with the chance that the arrival stays within a few
 * minutes of the current forecast – a percentage internally, five plain levels outside.
 */

/**
 * Delay prior of one ride: chance to be on time, mean of the late part (min), chance of a short-notice
 * cancellation, and the punctuality [threshold] (min) of the published figure it was fitted to.
 */
data class DelayPrior(val punctual: Double, val lateMean: Double, val cancel: Double, val threshold: Double) {
    /** P(delay < [threshold]) under this prior – the share an operator would publish as "punctual". */
    fun share(): Double = punctual + (1 - punctual) * (1 - exp(-threshold / lateMean))
}

/** The traffic modes the priors distinguish (finer than LineType where the evidence differs). */
enum class ReliabilityMode { LONG_DISTANCE, REGIONAL_EXPRESS, REGIONAL_TRAIN, SUBURBAN, SUBWAY, TRAM, CITY_BUS, REGIONAL_BUS, FERRY, OTHER }

/**
 * Starting database (Build 117), derived in docs/reliability.md: DB long distance 60.1 % < 6 min
 * (2025); NRW S-Bahn 77.3 %, RE 63.9 %, RB 74.5 % < 4 min (2025); Rheinbahn Stadtbahn ≈ 78–83 %, bus
 * ≈ 77–86 % ≤ 3 min (2023–2025); Bremen city bus 78 %, regional bus 61 % ≤ 5 min (2025); metros on
 * their own track (Hamburg U-Bahn) ≈ 98 %. Each prior is fitted so that P(delay < threshold) matches
 * the published share with a late mean typical for the mode.
 */
object ReliabilityPriors {
    val base: Map<ReliabilityMode, DelayPrior> = mapOf(
        ReliabilityMode.LONG_DISTANCE to fit(.601, 6.0, 14.0, cancel = .03),
        ReliabilityMode.REGIONAL_EXPRESS to fit(.639, 4.0, 7.0, cancel = .04),
        ReliabilityMode.REGIONAL_TRAIN to fit(.745, 4.0, 5.5, cancel = .04),
        ReliabilityMode.SUBURBAN to fit(.773, 4.0, 4.5, cancel = .03),
        // Stadtbahn/U-Bahn in the Ruhr area partly runs on the street: between Rheinbahn and Hamburg.
        ReliabilityMode.SUBWAY to fit(.85, 3.0, 3.0, cancel = .01),
        ReliabilityMode.TRAM to fit(.80, 3.0, 3.0, cancel = .01),
        ReliabilityMode.CITY_BUS to fit(.78, 5.0, 5.0, cancel = .015),
        ReliabilityMode.REGIONAL_BUS to fit(.61, 5.0, 8.0, cancel = .02),
        ReliabilityMode.FERRY to fit(.95, 3.0, 3.0, cancel = .02),
        ReliabilityMode.OTHER to fit(.75, 4.0, 4.0, cancel = .02),
    )

    fun of(mode: ReliabilityMode): DelayPrior = base.getValue(mode)

    /**
     * The on-time share p0 so that P(delay < [threshold]) = [share] with an exponential late part of
     * mean [lateMean]: share = p0 + (1 − p0)(1 − e^(−t/μ)).
     */
    fun fit(share: Double, threshold: Double, lateMean: Double, cancel: Double): DelayPrior {
        val tail = exp(-threshold / lateMean)
        val p0 = ((share - (1 - tail)) / tail).coerceIn(.02, .98)
        return DelayPrior(p0, lateMean, cancel, threshold)
    }
}

/** When a ride happens, for the context factors; [knownDelay] minutes from a realtime forecast. */
data class RideContext(val time: ZonedDateTime, val minutesIntoRun: Int? = null, val knownDelay: Int? = null)

/** The time windows the context factors and the personal memory distinguish. */
enum class TimeWindow { WEEKDAY_PEAK, WEEKDAY, WEEKEND, NIGHT }

fun timeWindow(time: ZonedDateTime): TimeWindow {
    val minute = time.hour * 60 + time.minute
    val weekend = time.dayOfWeek == DayOfWeek.SATURDAY || time.dayOfWeek == DayOfWeek.SUNDAY
    return when {
        minute >= 22 * 60 || minute < 5 * 60 -> TimeWindow.NIGHT
        weekend -> TimeWindow.WEEKEND
        minute in 6 * 60 + 30..9 * 60 || minute in 15 * 60 + 30..18 * 60 + 30 -> TimeWindow.WEEKDAY_PEAK
        else -> TimeWindow.WEEKDAY
    }
}

/** Context factors (Build 117), documented in docs/reliability.md. */
object ReliabilityContext {
    private fun road(m: ReliabilityMode) = m == ReliabilityMode.CITY_BUS || m == ReliabilityMode.REGIONAL_BUS || m == ReliabilityMode.TRAM
    private fun rail(m: ReliabilityMode) = m == ReliabilityMode.LONG_DISTANCE || m == ReliabilityMode.REGIONAL_EXPRESS ||
        m == ReliabilityMode.REGIONAL_TRAIN || m == ReliabilityMode.SUBURBAN

    /** Factor on the late share and the late mean for [mode] at [c]; 1 = no change. */
    fun factor(mode: ReliabilityMode, c: RideContext): Double {
        val t = c.time; val minute = t.hour * 60 + t.minute
        val weekday = t.dayOfWeek != DayOfWeek.SATURDAY && t.dayOfWeek != DayOfWeek.SUNDAY
        val morning = weekday && minute in 6 * 60 + 30..9 * 60
        val evening = weekday && minute in 15 * 60 + 30..18 * 60 + 30
        val night = minute >= 22 * 60 || minute < 5 * 60
        var f = 1.0
        if (road(mode)) {
            if (morning) f *= 1.25
            if (evening) f *= 1.40   // the evening peak weighs more on the road (AVL studies)
            if (!weekday) f *= .85
            if (night) f *= .80
        }
        if (rail(mode)) {
            if (morning || evening) f *= 1.15   // dense timetable, delays propagate
            if (night) f *= 1.15                // night-time works
            if (mode == ReliabilityMode.LONG_DISTANCE && (t.dayOfWeek == DayOfWeek.FRIDAY && minute >= 12 * 60 ||
                    t.dayOfWeek == DayOfWeek.SUNDAY && minute >= 14 * 60)) f *= 1.15
            f *= when (t.monthValue) { 12, 1, 2 -> 1.15; 6, 7, 8 -> 1.08; 10, 11 -> 1.05; else -> 1.0 }
        }
        if (mode == ReliabilityMode.SUBWAY && (morning || evening)) f *= 1.05
        // Delays build along a run; after an hour the effect levels off.
        c.minutesIntoRun?.let { f *= 1.0 + .2 * min(1.0, it.coerceAtLeast(0) / 60.0) }
        return f
    }

    /** The prior at [c]: more often late and later by [factor]. */
    fun apply(prior: DelayPrior, mode: ReliabilityMode, c: RideContext): DelayPrior {
        val f = factor(mode, c)
        val late = ((1 - prior.punctual) * f).coerceIn(.01, .97)
        return prior.copy(punctual = 1 - late, lateMean = prior.lateMean * (1 + (f - 1) * .6))
    }
}

/** A delay distribution on a one-minute grid 0..[MAX]; index = minutes late. */
class DelayGrid(val p: DoubleArray, val cancel: Double) {
    companion object {
        const val MAX = 90

        fun of(prior: DelayPrior): DelayGrid {
            val p = DoubleArray(MAX + 1)
            p[0] = prior.punctual
            val lambda = 1.0 / prior.lateMean
            for (m in 1..MAX) p[m] = (1 - prior.punctual) * (exp(-lambda * (m - 1)) - exp(-lambda * m))
            p[MAX] += (1 - prior.punctual) * exp(-lambda * MAX)
            return DelayGrid(p, prior.cancel)
        }

        /** Realtime: the forecast [delay] with a narrow spread (forecasts are good, not exact) and a small tail. */
        fun known(delay: Int, prior: DelayPrior): DelayGrid {
            val p = DoubleArray(MAX + 1); val d = delay.coerceIn(0, MAX)
            p[d] += .7
            for ((offset, w) in listOf(-1 to .06, 1 to .1, 2 to .06, 3 to .04, 5 to .03)) p[(d + offset).coerceIn(0, MAX)] += w
            // Forecasts can still grow: the rest follows the mode's late tail beyond the forecast.
            val rest = 1 - p.sum(); val lambda = 1.0 / prior.lateMean
            if (d < MAX) for (m in d + 1..MAX) p[m] += rest * (exp(-lambda * (m - d - 1)) - exp(-lambda * (m - d)))
            val sum = p.sum(); for (i in p.indices) p[i] /= sum
            return DelayGrid(p, prior.cancel * .5)
        }
    }

    /** P(delay ≤ [minutes]). */
    fun atMost(minutes: Int): Double {
        if (minutes < 0) return 0.0
        var s = 0.0; for (i in 0..minutes.coerceAtMost(MAX)) s += p[i]; return s.coerceIn(0.0, 1.0)
    }
}

/** P(feeder delay − connecting delay ≤ [slack]): the transfer holds. */
fun transferHolds(feeder: DelayGrid, next: DelayGrid, slack: Int): Double {
    var ok = 0.0
    for (a in feeder.p.indices) {
        val pa = feeder.p[a]; if (pa < 1e-9) continue
        // The connecting vehicle leaves d minutes late; the person is there a − slack minutes after its plan.
        var reachable = 0.0
        for (d in next.p.indices) if (a - d <= slack) reachable += next.p[d]
        ok += pa * reachable
    }
    return ok.coerceIn(0.0, 1.0)
}

/** Five plain levels of a rating. */
enum class ReliabilityLevel { VERY_SAFE, SAFE, MEDIUM, UNSAFE, VERY_UNSAFE }

fun reliabilityLevel(percent: Int) = when {
    percent >= 90 -> ReliabilityLevel.VERY_SAFE
    percent >= 75 -> ReliabilityLevel.SAFE
    percent >= 55 -> ReliabilityLevel.MEDIUM
    percent >= 35 -> ReliabilityLevel.UNSAFE
    else -> ReliabilityLevel.VERY_UNSAFE
}

/**
 * The result for one journey: per-transfer chances, the chance that everything runs and connects, the
 * chance to arrive within the tolerance of the current forecast, overall percentage and level.
 * [informed]: realtime data informed at least one ride; [personal]: own reports adjusted at least one.
 */
data class ReliabilityRating(val transfers: List<Double>, val connections: Double, val arrival: Double, val percent: Int,
    val level: ReliabilityLevel, val informed: Boolean, val personal: Boolean)

/** One ride as the model sees it; [departureDelay]/[arrivalDelay] are realtime minutes, null without forecast. */
data class RideInput(val mode: ReliabilityMode, val lineKey: String, val plannedDeparture: Long, val plannedArrival: Long,
    val context: RideContext, val departureDelay: Int? = null, val arrivalDelay: Int? = null)

/** Personal adjustment: the person's own punctuality counts for a line or mode in a time window. */
fun interface PersonalPunctuality { fun counts(lineKey: String, mode: ReliabilityMode, window: TimeWindow): PunctualityCount? }

/** Pseudo-observations the published prior counts as against the person's own reports. */
const val PRIOR_WEIGHT = 12.0

/** The prior moved towards the person's own share: Beta posterior with [PRIOR_WEIGHT] pseudo-counts. */
fun personalPrior(prior: DelayPrior, own: PunctualityCount): DelayPrior {
    val share = (prior.share() * PRIOR_WEIGHT + own.onTime) / (PRIOR_WEIGHT + own.total)
    return ReliabilityPriors.fit(share, prior.threshold, prior.lateMean, prior.cancel)
}

/**
 * Rates a journey: [rides] in order, [walks] the walking minutes between consecutive rides, [tolerance]
 * minutes beyond the current forecast that still count as "as forecast". Overall: 65 % the chance that
 * every ride runs and every transfer holds, 35 % the chance to arrive within the tolerance.
 */
fun rateJourney(rides: List<RideInput>, walks: List<Int>, personal: PersonalPunctuality? = null, tolerance: Int = 5): ReliabilityRating {
    if (rides.isEmpty()) return ReliabilityRating(emptyList(), 1.0, 1.0, 100, ReliabilityLevel.VERY_SAFE, false, false)
    var personalUsed = false
    val priors = rides.map { r ->
        val contextual = ReliabilityContext.apply(ReliabilityPriors.of(r.mode), r.mode, r.context)
        val own = personal?.counts(r.lineKey, r.mode, timeWindow(r.context.time))
        if (own != null) { personalUsed = true; personalPrior(contextual, own) } else contextual
    }
    fun departureGrid(i: Int) = rides[i].departureDelay?.let { DelayGrid.known(it.coerceAtLeast(0), priors[i]) } ?: DelayGrid.of(priors[i])
    fun arrivalGrid(i: Int) = rides[i].arrivalDelay?.let { DelayGrid.known(it.coerceAtLeast(0), priors[i]) } ?: DelayGrid.of(priors[i])
    val transfers = (0 until rides.size - 1).map { i ->
        val gap = ((rides[i + 1].plannedDeparture - rides[i].plannedArrival) / 60_000L).toInt()
        val walk = walks.getOrNull(i) ?: 0
        // Changing without a walk still takes a minute (stairs, other side of the platform).
        transferHolds(arrivalGrid(i), departureGrid(i + 1), gap - walk - if (walk == 0) 1 else 0)
    }
    val running = rides.indices.fold(1.0) { acc, i -> acc * (1 - (if (rides[i].arrivalDelay != null) priors[i].cancel * .5 else priors[i].cancel)) }
    val connections = transfers.fold(running) { acc, p -> acc * p }
    val last = rides.lastIndex
    val arrival = arrivalGrid(last).atMost((rides[last].arrivalDelay ?: 0).coerceAtLeast(0) + tolerance)
    val percent = (100 * (.65 * connections + .35 * arrival)).roundToInt().coerceIn(0, 100)
    return ReliabilityRating(transfers, connections, arrival, percent, reliabilityLevel(percent),
        rides.any { it.departureDelay != null || it.arrivalDelay != null }, personalUsed)
}
