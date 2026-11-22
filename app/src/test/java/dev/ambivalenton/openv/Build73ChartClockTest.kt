// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Vehicle times in the comparison chart are chips welded to their bar. Footprints run
 * underneath them all the way to the bar, so no track appears to start at the time.
 */
class Build73ChartClockTest {
    private val a = Location("A", "a", 51.0, 7.0)
    private val b = a.copy(name = "B", id = "b", longitude = 7.01)
    private val t = 1_800_000_000_000L
    private val minute = 60_000L
    private val scale = 3.4f
    private fun leg(id: String, start: Long, minutes: Int, type: LineType) =
        TripSegment(id, a, b, start, start + minutes * minute, type, if (type == LineType.WALK) "Fußweg" else "RE3")
    private fun route(walkBefore: Int, ride: Int = 16, walkAfter: Int = walkBefore): Trip {
        val rideStart = t + walkBefore * minute
        return Trip("r", listOfNotNull(
            leg("lead", t, walkBefore, LineType.WALK).takeIf { walkBefore > 0 },
            leg("ride", rideStart, ride, LineType.REGIONAL),
            leg("tail", rideStart + ride * minute, walkAfter, LineType.WALK).takeIf { walkAfter > 0 },
        ), "test", t)
    }
    private fun offset(trip: Trip, time: Long) = ((time - trip.startTime) / 60_000.0 * scale).toFloat()

    @Test fun vehicleChipsTouchTheBarEdgesAtEveryFontScale() {
        for (font in listOf(.85f, 1f, 1.6f, 2f)) {
            // Chips belong to short bars only (12 min = 40.8 dp, below three clock rows).
            val trip = route(20, ride = 12)
            val ride = trip.segments.first { !it.isWalk }
            val labels = timelineClockLabels(trip, scale, font)
            val height = timelineClockHeight(font)
            val departure = labels.single { it.key == "ride-start-1" }
            val arrival = labels.single { it.key == "ride-end-1" }
            assertEquals(TimelineClockPlacement.ABOVE_BAR, departure.placement)
            assertEquals(TimelineClockPlacement.BELOW_BAR, arrival.placement)
            assertEquals("departure chip ends at the bar top", offset(trip, ride.startTime), departure.topDp + height, .001f)
            assertEquals("arrival chip starts at the bar bottom", offset(trip, ride.endTime), arrival.topDp, .001f)
            assertEquals(TimelineClockPlacement.WALK_START, labels.single { it.key == "walk-start" }.placement)
            assertEquals(TimelineClockPlacement.WALK_END, labels.single { it.key == "walk-end" }.placement)
        }
    }

    @Test fun longBarsCarryBothTimesInsideWithoutChips() {
        // Room for both times plus the line name → times at the top and bottom inside the bar,
        // even when there is free space above and below; no frames.
        for (font in listOf(.85f, 1f, 1.6f)) {
            val trip = route(20, ride = 30)
            val ride = trip.segments.first { !it.isWalk }
            val height = timelineClockHeight(font)
            val labels = timelineClockLabels(trip, scale, font)
            val departure = labels.single { it.key == "ride-start-1" }
            val arrival = labels.single { it.key == "ride-end-1" }
            assertEquals(TimelineClockPlacement.INSIDE, departure.placement)
            assertEquals(TimelineClockPlacement.INSIDE, arrival.placement)
            assertTrue(departure.insideVehicle && arrival.insideVehicle)
            assertEquals("top row inside the bar", offset(trip, ride.startTime) + 2f, departure.topDp, .001f)
            assertEquals("bottom row inside the bar", offset(trip, ride.endTime) - height, arrival.topDp, .001f)
            assertTrue(timelineBarHoldsClocks(offset(trip, ride.endTime) - offset(trip, ride.startTime), font))
            assertTrue(labels.none { it.placement == TimelineClockPlacement.ABOVE_BAR || it.placement == TimelineClockPlacement.BELOW_BAR })
        }
        // At 200 % type the same bar is too short for two rows and a name: chips again.
        val big = timelineClockLabels(route(20, ride = 30), scale, 2f)
        assertEquals(TimelineClockPlacement.ABOVE_BAR, big.single { it.key == "ride-start-1" }.placement)
        // A short walk before a long bar is exactly as long as its time, ending at the bar.
        val short = route(2, ride = 30)
        val ride = short.segments.first { !it.isWalk }
        val tracks = timelineWalkTracks(short, scale, 1f, timelineClockLabels(short, scale, 1f), 4f, 16f)
        assertEquals(-4f, tracks.single { it.segmentIndex == 0 }.topDp, .001f)
        assertEquals(offset(short, ride.startTime), tracks.single { it.segmentIndex == 0 }.bottomDp, .001f)
    }

    @Test fun footprintsRunUnderTheVehicleChipExactlyToTheBar() {
        val trip = route(20, ride = 12)
        val ride = trip.segments.first { !it.isWalk }
        val labels = timelineClockLabels(trip, scale, 1f)
        val tracks = timelineWalkTracks(trip, scale, 1f, labels, 4f, 16f)
        val lead = tracks.single { it.segmentIndex == 0 }
        val tail = tracks.single { it.segmentIndex == 2 }
        assertEquals("no overhang into the bar", offset(trip, ride.startTime), lead.bottomDp, .001f)
        assertEquals("no overhang into the bar", offset(trip, ride.endTime), tail.topDp, .001f)
        // A long walk keeps its own time: the steps emerge from underneath that chip, not above it.
        val walkStart = labels.single { it.key == "walk-start" }
        assertTrue(lead.topDp > walkStart.topDp)
        assertTrue(lead.topDp < walkStart.topDp + timelineClockHeight(1f))
    }

    @Test fun walksAreAsLongAsTheirTimeWhateverTheChips() {
        // Neither chips nor a fixed approach stretch a walk; 2 and 15 minutes stay 2 : 15.
        for (s in listOf(1.2f, 2.6f, scale, 6f)) {
            val lengths = listOf(2, 15).map { minutes ->
                val trip = route(minutes, ride = 12)
                val track = timelineWalkTracks(trip, s, 1f, timelineClockLabels(trip, s, 1f), 4f, 16f).single { it.segmentIndex == 0 }
                track.bottomDp - track.topDp - 4f
            }
            assertEquals("scale $s", 2f / 15f, lengths[0] / lengths[1], .001f)
        }
        // The column's lead is set by its highest chip or the walk's half sole, not by stretched steps.
        val trip = route(2)
        val labels = timelineClockLabels(trip, scale, 1f)
        assertEquals(maxOf(4f, -labels.minOf { it.topDp }), timelineColumnLead(trip, scale, 1f, 4f, 16f), .001f)
    }

    @Test fun aTransferWalkCoversItsWalkNotTheWaitingTimeAndTimesInsideBarsStayPlain() {
        val first = leg("one", t, 20, LineType.REGIONAL)
        val walk = leg("walk", first.endTime, 2, LineType.WALK)
        val second = leg("two", walk.endTime + 18 * minute, 20, LineType.SUBURBAN)
        val trip = Trip("transfer", listOf(first, walk, second), "test", t)
        val labels = timelineClockLabels(trip, scale, 1f)
        val track = timelineWalkTracks(trip, scale, 1f, labels, 4f, 16f).single()
        // Two minutes of steps from the first bar; the 18 minutes of waiting stay empty.
        assertEquals(offset(trip, first.endTime), track.topDp, .001f)
        assertEquals(offset(trip, walk.endTime) + 4f, track.bottomDp, .001f)
        // Long bars and tight transfers put the times inside the bars, without chips.
        val tight = Trip("tight", listOf(first, walk, second.copy(startTime = walk.endTime + minute, endTime = walk.endTime + 21 * minute)), "test", t)
        val inside = timelineClockLabels(tight, scale, 1f).filter { it.insideVehicle }
        assertTrue(inside.isNotEmpty())
        assertTrue(inside.all { it.placement == TimelineClockPlacement.INSIDE })
    }

    @Test fun walkingTimesStillAppearOnlyWithEnoughRoomAndNeverCollide() {
        // Unchanged rule from build 40: long walks keep their own time, short ones attach to the vehicle.
        for (walk in 1..30) {
            val labels = timelineClockLabels(route(walk), scale, 1f)
            for (w in labels.filter { it.walking }) for (r in labels.filter { !it.walking })
                assertTrue(kotlin.math.abs(w.topDp - r.topDp) >= timelineClockHeight(1f) + 2f)
        }
        assertEquals(2, timelineClockLabels(route(20), scale, 1f).count { it.walking })
        assertEquals(0, timelineClockLabels(route(3), scale, 1f).count { it.walking })
    }
}
