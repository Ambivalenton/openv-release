// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.reliability.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Build 117 · the reliability model: priors, context, transfers, realtime, personal reports. */
class ReliabilityModelTest {
    private val zone = ZoneId.of("Europe/Berlin")
    /** Tuesday, 24 November 2026. */
    private fun at(hour: Int, minute: Int = 0, day: Int = 24) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)
    private fun ms(t: ZonedDateTime) = t.toInstant().toEpochMilli()
    private val a = Location("Bochum Hbf", "a", 51.478, 7.223, "VRR")
    private val b = Location("Herne", "b", 51.54, 7.22, "VRR")
    private val c = Location("Recklinghausen", "c", 51.61, 7.20, "VRR")
    private fun ride(id: String, from: Location, to: Location, start: ZonedDateTime, minutes: Int, type: LineType, line: String,
        departureDelay: Int? = null, arrivalDelay: Int? = null, cancelled: Boolean = false) =
        TripSegment(id, from, to, ms(start) + (departureDelay ?: 0) * 60_000L, ms(start) + (minutes + (arrivalDelay ?: 0)) * 60_000L, type, line,
            plannedStartTime = ms(start), plannedEndTime = ms(start) + minutes * 60_000L,
            delay = DelayInfo(departureDelay?.times(60), arrivalDelay?.times(60), cancelled),
            departureSource = if (departureDelay != null) TimeSource.PROGNOSIS else TimeSource.PLANNED,
            arrivalSource = if (arrivalDelay != null) TimeSource.PROGNOSIS else TimeSource.PLANNED)
    private fun walk(from: Location, to: Location, start: ZonedDateTime, minutes: Int) =
        TripSegment("w", from, to, ms(start), ms(start) + minutes * 60_000L, LineType.WALK, "")
    private fun trip(vararg s: TripSegment) = Trip("t", s.toList(), "VRR", ms(at(7)))

    @Test fun thePriorsReproduceThePublishedShares() {
        assertEquals(.639, ReliabilityPriors.of(ReliabilityMode.REGIONAL_EXPRESS).share(), .002)
        assertEquals(.601, ReliabilityPriors.of(ReliabilityMode.LONG_DISTANCE).share(), .002)
        assertEquals(.773, ReliabilityPriors.of(ReliabilityMode.SUBURBAN).share(), .002)
        assertEquals(.61, ReliabilityPriors.of(ReliabilityMode.REGIONAL_BUS).share(), .002)
        assertEquals(.78, ReliabilityPriors.of(ReliabilityMode.CITY_BUS).share(), .002)
        for (prior in ReliabilityPriors.base.values) {
            // Every published share is reachable without clamping the on-time part.
            assertTrue("$prior", prior.punctual in .05..0.95)
            val grid = DelayGrid.of(prior)
            assertEquals(1.0, grid.p.sum(), 1e-9)
        }
    }

    @Test fun theRoadIsSlowerInTheEveningPeakThanAtTheWeekend() {
        val peak = ReliabilityContext.factor(ReliabilityMode.CITY_BUS, RideContext(at(17)))
        val weekend = ReliabilityContext.factor(ReliabilityMode.CITY_BUS, RideContext(at(17, day = 28)))
        val morning = ReliabilityContext.factor(ReliabilityMode.CITY_BUS, RideContext(at(7, 30)))
        assertTrue(peak > morning && morning > 1.0 && weekend < 1.0)
        assertEquals(TimeWindow.WEEKDAY_PEAK, timeWindow(at(17)))
        assertEquals(TimeWindow.WEEKEND, timeWindow(at(17, day = 28)))
        assertEquals(TimeWindow.NIGHT, timeWindow(at(23, 30)))
    }

    @Test fun aTransferHoldsMoreOftenWithMoreSlack() {
        val re = DelayGrid.of(ReliabilityPriors.of(ReliabilityMode.REGIONAL_EXPRESS))
        val bus = DelayGrid.of(ReliabilityPriors.of(ReliabilityMode.CITY_BUS))
        val chances = (0..15).map { transferHolds(re, bus, it) }
        assertTrue(chances.zipWithNext().all { (x, y) -> y >= x })
        assertTrue("$chances", chances[0] < .7 && chances[15] > .9)
        // A forecast seven minutes late against a two-minute change: only if the bus is late too.
        val late = DelayGrid.known(7, ReliabilityPriors.of(ReliabilityMode.REGIONAL_EXPRESS))
        assertTrue(transferHolds(late, bus, 2) < .35)
        assertTrue(transferHolds(DelayGrid.known(0, ReliabilityPriors.of(ReliabilityMode.REGIONAL_EXPRESS)), bus, 2) > chances[2])
    }

    @Test fun aDirectSubwayIsSaferThanATightChangeFromTheReInTheEveningPeak() {
        val direct = rateTrip(trip(ride("u", a, b, at(11), 20, LineType.SUBWAY, "U35")), zone = zone)
        val tight = rateTrip(trip(ride("re", a, b, at(17), 8, LineType.REGIONAL, "RE1"),
            walk(b, b, at(17, 8), 2), ride("bus", b, c, at(17, 12), 15, LineType.BUS, "SB20")), zone = zone)
        assertTrue("${direct.percent}", direct.level <= ReliabilityLevel.SAFE)
        assertTrue("${tight.percent}", tight.percent < direct.percent - 15)
        assertTrue(tight.level >= ReliabilityLevel.MEDIUM)
        assertEquals(1, tight.transfers.size)
        assertFalse(direct.informed)
    }

    @Test fun realtimeNarrowsTheEstimateBothWays() {
        fun rated(delay: Int?) = rateTrip(trip(ride("re", a, b, at(11), 10, LineType.REGIONAL, "RE1", arrivalDelay = delay),
            ride("u", b, c, at(11, 16), 10, LineType.SUBWAY, "U35")), zone = zone)
        val none = rated(null); val onTime = rated(0); val late = rated(9)
        assertTrue(onTime.informed)
        assertTrue("${onTime.percent} ${none.percent}", onTime.percent > none.percent)
        assertTrue("${late.percent}", late.level >= ReliabilityLevel.UNSAFE)
    }

    @Test fun aCancelledRideIsVeryUnsafe() {
        val rating = rateTrip(trip(ride("re", a, b, at(11), 10, LineType.REGIONAL, "RE1", cancelled = true)), zone = zone)
        assertEquals(ReliabilityLevel.VERY_UNSAFE, rating.level)
    }

    @Test fun ownReportsMoveTheEstimateFromTheThirdOnAndCountOncePerRide() {
        val journey = trip(ride("re", a, b, at(11), 10, LineType.REGIONAL, "RE1"), ride("u", b, c, at(11, 14), 10, LineType.SUBWAY, "U35"))
        val before = rateTrip(journey, ReliabilityMemory(), zone = zone)
        var memory = ReliabilityMemory()
        memory = memory.record("ride-1", "REGIONAL:RE1", ReliabilityMode.REGIONAL_EXPRESS, TimeWindow.WEEKDAY, false, 1)
        memory = memory.record("ride-1", "REGIONAL:RE1", ReliabilityMode.REGIONAL_EXPRESS, TimeWindow.WEEKDAY, false, 2)
        assertEquals(1, memory.reports)
        memory = memory.record("ride-2", "REGIONAL:RE1", ReliabilityMode.REGIONAL_EXPRESS, TimeWindow.WEEKDAY, false, 3)
        assertEquals(before.percent, rateTrip(journey, memory, zone = zone).percent)
        repeat(8) { memory = memory.record("late-$it", "REGIONAL:RE1", ReliabilityMode.REGIONAL_EXPRESS, TimeWindow.WEEKDAY, false, 10L + it) }
        val worse = rateTrip(journey, memory, zone = zone)
        assertTrue(worse.personal)
        assertTrue("${worse.percent} < ${before.percent}", worse.percent < before.percent)
        // Another RE line learns from the mode until it has reports of its own.
        val other = rateTrip(trip(ride("re", a, b, at(11), 10, LineType.REGIONAL, "RE2"), ride("u", b, c, at(11, 14), 10, LineType.SUBWAY, "U35")), memory, zone = zone)
        assertTrue(other.personal)
        // A different time window is untouched.
        val evening = trip(ride("re", a, b, at(23), 10, LineType.REGIONAL, "RE1"))
        assertFalse(rateTrip(evening, memory, zone = zone).personal)
    }

    @Test fun theMemoryStaysBounded() {
        var memory = ReliabilityMemory()
        repeat(400) { i -> memory = memory.record("r$i", "BUS:$i", ReliabilityMode.CITY_BUS, TimeWindow.WEEKDAY, i % 2 == 0, i.toLong()) }
        assertTrue(memory.counts.size <= ReliabilityMemory.MAX_KEYS)
        assertTrue(memory.reported.size <= ReliabilityMemory.MAX_REPORTED)
        // The newest keys survive.
        assertTrue(memory.counts.containsKey(ReliabilityMemory.lineKey("BUS:399", TimeWindow.WEEKDAY)))
    }

    @Test fun linesMapToTheirModes() {
        fun mode(type: LineType, name: String) = reliabilityMode(TripSegment("x", a, b, 0, 1, type, name))
        assertEquals(ReliabilityMode.REGIONAL_EXPRESS, mode(LineType.REGIONAL, "RE 1"))
        assertEquals(ReliabilityMode.REGIONAL_TRAIN, mode(LineType.REGIONAL, "RB43"))
        assertEquals(ReliabilityMode.REGIONAL_BUS, mode(LineType.BUS, "SB20"))
        assertEquals(ReliabilityMode.CITY_BUS, mode(LineType.BUS, "390"))
        assertEquals(ReliabilityMode.LONG_DISTANCE, mode(LineType.ICE, "ICE 945"))
        assertEquals(5, ReliabilityLevel.entries.size)
        assertEquals(ReliabilityLevel.SAFE, reliabilityLevel(80))
        assertEquals(ReliabilityLevel.VERY_UNSAFE, reliabilityLevel(20))
    }
}
