// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.reliability.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Build 121 · test report: a connection that, by the forecast, leaves before the feeder arrives got the
 * same best rating as a punctual one. The feeder's delay was only known at its departure and carried to
 * its arrival (shown arrival later, seconds field empty) – the model read that as "on time".
 */
class Build121ReliabilityTest {
    private val zone = ZoneId.of("Europe/Berlin")
    /** Tuesday, 24 November 2026, 11:00 (no rush hour). */
    private val start = ZonedDateTime.of(2026, 11, 24, 11, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val a = Location("Bochum Hbf", "a", 51.478, 7.223, "VRR")
    private val b = Location("Herne", "b", 51.54, 7.22, "VRR")
    private val c = Location("Recklinghausen", "c", 51.61, 7.20, "VRR")
    private fun min(m: Int) = m * 60_000L

    /** The feeder RE (10 min ride) exactly as the EFA parser builds it for a delay known only at departure. */
    private fun carriedFeeder(delay: Int) = TripSegment("f", a, b, start + min(delay), start + min(10 + delay), LineType.REGIONAL, "RE3",
        plannedStartTime = start, plannedEndTime = start + min(10),
        delay = DelayInfo(delay * 60, null), departureSource = TimeSource.PROGNOSIS, arrivalSource = TimeSource.CARRIED)

    /** The connecting S-Bahn 5 min after the planned arrival, its departure forecast [delay]. */
    private fun connection(delay: Int?) = TripSegment("s", b, c, start + min(15 + (delay ?: 0)), start + min(30 + (delay ?: 0)), LineType.SUBURBAN, "S2",
        plannedStartTime = start + min(15), plannedEndTime = start + min(30),
        delay = DelayInfo(delay?.times(60), delay?.times(60)),
        departureSource = if (delay != null) TimeSource.PROGNOSIS else TimeSource.PLANNED,
        arrivalSource = if (delay != null) TimeSource.PROGNOSIS else TimeSource.PLANNED)

    private fun rate(vararg s: TripSegment) = rateTrip(Trip("t", s.toList(), "VRR", start - min(30)), zone = zone)

    @Test fun aCarriedDelayCountsAtTheArrival() {
        assertEquals(8, knownMinutes(null, TimeSource.CARRIED, start + min(18), start + min(10)))
        // Without any forecast nothing is claimed, not even "on time".
        assertNull(knownMinutes(null, TimeSource.PLANNED, start, start))
        assertEquals(0, knownMinutes(null, TimeSource.PROGNOSIS, start, start))
    }

    @Test fun aConnectionThatLeavesBeforeTheFeederArrivesIsVeryUnsafe() {
        val punctual = rate(carriedFeeder(0), connection(0))
        val missed = rate(carriedFeeder(8), connection(0))
        assertTrue("punctual: ${punctual.percent}", punctual.level == ReliabilityLevel.VERY_SAFE || punctual.level == ReliabilityLevel.SAFE)
        assertEquals(ReliabilityLevel.VERY_UNSAFE, missed.level)
        assertTrue("${missed.transfers}", missed.transfers.single() < .2)
        assertNotEquals(punctual.level, missed.level)
    }

    @Test fun aTightButReachableTransferIsNotRuledOut() {
        // 3 min late into a 5 min gap (one minute to change): still reachable, worse than punctual, not "missed".
        val tight = rate(carriedFeeder(3), connection(0))
        val punctual = rate(carriedFeeder(0), connection(0))
        assertTrue("${tight.percent} vs ${punctual.percent}", tight.percent < punctual.percent)
        assertTrue(tight.percent > FORECAST_MISSED_PERCENT)
        // The connection waits for it (late as well): fine again.
        assertTrue(rate(carriedFeeder(8), connection(6)).percent > FORECAST_MISSED_PERCENT)
    }

    @Test fun anEarlyDepartureTakesTimeAway() {
        val ride = listOf(RideInput(ReliabilityMode.REGIONAL_EXPRESS, "R", start, start + min(10), RideContext(ZonedDateTime.now(zone)), 0, 0),
            RideInput(ReliabilityMode.SUBURBAN, "S", start + min(13), start + min(30), RideContext(ZonedDateTime.now(zone)), -3, -3))
        // 3 min gap, 1 min to change, the S-Bahn leaves 3 min early: gone by the forecast.
        assertEquals(ReliabilityLevel.VERY_UNSAFE, rateJourney(ride, listOf(0)).level)
    }
}
