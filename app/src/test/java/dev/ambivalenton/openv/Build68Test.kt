// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.NetworkCountdown
import dev.ambivalenton.openv.ui.NetworkCountdownChip
import dev.ambivalenton.openv.ui.network.*
import java.util.Locale
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Overview crash with the "Haltestelle" icon, icon-specific sentences, vehicles, names. */
class Build68Test {
    private val now = 1_800_000_000_000L
    private val westStation = Location("Westbahnhof", "h", 51.54, 9.22, "VRR", city = "Weststadt")
    private val eastStation = Location("Ostbahnhof", "d", 51.51, 9.46, "VRR", city = "Oststadt")
    private val ride = TripSegment("r", westStation, eastStation, now + 300_000, now + 1_500_000, LineType.SUBWAY, "U35", departurePlatform = "U-Bahn Gleis 2")
    private val trip = Trip("t", listOf(ride), "TEST", now)
    private val old = Locale.getDefault()

    @Before fun german() { Locale.setDefault(Locale.GERMANY) }
    @After fun restore() { Locale.setDefault(old); NetworkVehicleMemory.clear() }

    private fun text(parts: List<OverviewTextPart>) = parts.joinToString("") { it.text }

    /** The crash of build 67: the selectable "Haltestelle" icon had no copy and getValue() threw while drawing. */
    @Test fun everyIconHasCopyAndTheStopIconNoLongerCrashesTheOverview() {
        for (icon in FavoriteIcon.entries) {
            assertTrue(icon.name, iconArrivalMessages(icon).size >= 10)
            assertEquals(icon.name, 6, (0 until 6).map { iconJourneyLead(icon, it) }.distinct().size)
        }
        val stop = Favorite("s", "Stadtmitte", eastStation, icon = FavoriteIcon.STOP)
        for (v in 0 until OverviewSentenceGenerator.variantCount) {
            val parts = OverviewSentenceGenerator.sentence(stop, trip, OverviewSettings(), v)
            assertTrue(parts.any { it.text == "Stadtmitte" && it.role == OverviewTextRole.DESTINATION_PLACE })
            // The same favourite with only the lead structure (destination shown, departure hidden).
            assertTrue(text(OverviewSentenceGenerator.sentence(stop, trip, OverviewSettings(showDepartureTime = false), v)).contains("Stadtmitte"))
        }
        assertTrue(IconArrivalPicker().next(FavoriteIcon.STOP).isNotBlank())
    }

    @Test fun everySentenceIsCompleteForEveryIconVariantSettingStartIconAndLanguage() {
        val starts = listOf(null, FavoriteIcon.HOME, FavoriteIcon.WORK, FavoriteIcon.SCHOOL, FavoriteIcon.FITNESS_CENTER, FavoriteIcon.STOP)
        for (locale in listOf(Locale.GERMANY, Locale.US)) {
            Locale.setDefault(locale)
            for (icon in FavoriteIcon.entries) for (label in listOf("Ziel", "Zuhause")) for (network in listOf(false, true))
                for (start in starts) for (mask in 0..15) for (v in 0 until OverviewSentenceGenerator.variantCount) {
                    val settings = OverviewSettings(showDestination = mask and 1 != 0, showOriginStop = mask and 2 != 0,
                        showDepartureTime = mask and 4 != 0, showArrivalTime = mask and 8 != 0, showPlatforms = true)
                    val favorite = Favorite("f", label, eastStation, icon = icon, counterpole = westStation.takeIf { network })
                    val parts = OverviewSentenceGenerator.sentence(favorite, trip, settings, v, originIcon = start)
                    val all = text(parts)
                    assertFalse(all, all.contains("{") || all.contains("}"))
                    assertEquals(all, if (settings.showArrivalTime) 1 else 0, parts.count { it.role == OverviewTextRole.ARRIVAL_TIME })
                    assertEquals(all, if (settings.showDepartureTime) 1 else 0, parts.count { it.role == OverviewTextRole.DEPARTURE_TIME })
                    assertFalse(all, all.contains("Gleis U-Bahn"))
                    assertFalse(all, all.contains("Pendel"))
                }
        }
    }

    @Test fun networkRowsFitTheirPairAndAPlainHomeLabelReadsNachHause() {
        val home = Favorite("z", "Zuhause", westStation, icon = FavoriteIcon.HOME, counterpole = eastStation)
        val fromWork = (0 until 6).map { text(OverviewSentenceGenerator.sentence(home, trip, OverviewSettings(), it, originIcon = FavoriteIcon.WORK)) }
        assertTrue(fromWork.toString(), fromWork.all { it.startsWith("Feierabend") || it.startsWith("Arbeit geschafft") || it.startsWith("Vom Job") })
        assertEquals(3, fromWork.distinct().size)
        for (v in 0 until 6) for (start in listOf(null, FavoriteIcon.WORK, FavoriteIcon.SCHOOL, FavoriteIcon.STAR)) {
            val t = text(OverviewSentenceGenerator.sentence(home, trip, OverviewSettings(), v, originIcon = start))
            assertTrue(t, t.contains("nach Hause")); assertFalse(t, t.contains("Zuhause"))
        }
        val work = Favorite("a", "VHS", eastStation, icon = FavoriteIcon.SCHOOL, counterpole = westStation)
        assertTrue(text(OverviewSentenceGenerator.sentence(work, trip, OverviewSettings(), 0, originIcon = FavoriteIcon.HOME)).contains("VHS"))
        // A plain place keeps neutral wording.
        val place = Favorite("p", "Innenstadt", eastStation, icon = FavoriteIcon.PLACE, counterpole = westStation)
        assertTrue((0 until 6).map { text(OverviewSentenceGenerator.sentence(place, trip, OverviewSettings(), it)) }.distinct().size >= 5)
        Locale.setDefault(Locale.US)
        val english = text(OverviewSentenceGenerator.sentence(home.copy(label = "Home"), trip, OverviewSettings(), 3, originIcon = FavoriteIcon.STAR))
        assertTrue(english, english.startsWith("Back home"))
    }

    @Test fun overviewPlatformUsesTheCompanionWording() {
        val f = Favorite("f", "Ziel", eastStation)
        val t = text(OverviewSentenceGenerator.sentence(f, trip, OverviewSettings(showPlatforms = true), 0))
        assertTrue(t, t.contains("Gleis 2")); assertFalse(t, t.contains("U-Bahn Gleis"))
    }

    @Test fun theCardFallsBackToThePlatformAlone() {
        val session = PinnedJourneySession(favorite = Favorite("z", "Zuhause", eastStation), query = JourneyQuery(westStation, eastStation, now),
            initialTrip = trip, routing = AppSettings())
        val focus = journeyFocus(session, now, null, null)
        assertEquals("Nächster Einstieg: Gleis 2", nextBoardingInstruction(session, focus))
        assertEquals("Gleis 2", nextBoardingPlatform(session, focus))
    }

    @Test fun vehicleKindsComeFromRealJourneysBeforeGuessing() {
        assertEquals(VehicleKind.BUS, vehicleKind(LineType.BUS))
        assertEquals(VehicleKind.TRAM, vehicleKind(LineType.TRAM)); assertEquals(VehicleKind.TRAM, vehicleKind(LineType.SUBWAY))
        listOf(LineType.SUBURBAN, LineType.REGIONAL, LineType.ICE).forEach { assertEquals(VehicleKind.TRAIN, vehicleKind(it)) }
        assertNull(vehicleKind(LineType.WALK))
        val a = Favorite("a", "A", westStation); val b = Favorite("b", "B", eastStation)
        val favorites = mapOf("a" to a, "b" to b)
        val edge = NetworkEdge("e", "a", "b")
        assertEquals("nothing known: alternating, starting with a train", VehicleKind.TRAIN, vehicleKindFor(edge, favorites, NetworkCountdown(), 0))
        assertEquals(VehicleKind.BUS, vehicleKindFor(edge, favorites, NetworkCountdown(), 1))
        assertEquals("transport filter of a place", VehicleKind.TRAM,
            vehicleKindFor(edge, favorites + ("b" to b.copy(transportFilter = setOf(FavoriteTransport.TRAM, FavoriteTransport.SUBWAY))), NetworkCountdown(), 0))
        NetworkVehicleMemory.record("e", trip.copy(segments = listOf(ride.copy(lineType = LineType.BUS))))
        assertEquals("a journey the overview found", VehicleKind.BUS, vehicleKindFor(edge, favorites, NetworkCountdown(), 0))
        val chip = NetworkCountdown("a", true, listOf(NetworkCountdownChip("e", "B", trip)))
        assertEquals("the next real departure wins", VehicleKind.TRAM, vehicleKindFor(edge, favorites, chip, 0))
        // The main vehicle is the longest ride, not a short feeder bus.
        val feeder = ride.copy(id = "f", lineType = LineType.BUS, startTime = now, endTime = now + 120_000)
        assertEquals(VehicleKind.TRAM, mainVehicleKind(trip.copy(segments = listOf(feeder, ride))))
    }

    @Test fun consistsVaryPerTripAndFitTheLine() {
        val long = (0 until 400).map { vehicleConsist(VehicleKind.TRAIN, 6f, vehicleSeed("e", true, it)) }
        assertEquals(setOf(0, 1, 2, 3), long.map { it.size - 1 }.toSet())
        assertTrue(long.all { it.first().head && it.last().tail })
        assertTrue("short lines carry no wagons", (0 until 100).all { vehicleConsist(VehicleKind.TRAIN, 1.5f, it).size == 1 })
        assertTrue((0 until 100).all { vehicleConsist(VehicleKind.TRAIN, 3f, it).size <= 2 })
        val buses = (0 until 400).map { vehicleConsist(VehicleKind.BUS, 4f, it).size }
        assertTrue("now and then an articulated bus", buses.count { it == 2 } in 60..220)
        assertTrue((0 until 100).all { vehicleConsist(VehicleKind.BUS, 1.5f, it).size == 1 })
        val trams = (0 until 400).map { vehicleConsist(VehicleKind.TRAM, 4f, it).size }.toSet()
        assertEquals(setOf(2, 3), trams)
        // The same trip never changes while it runs.
        assertEquals(vehicleConsist(VehicleKind.TRAIN, 6f, vehicleSeed("e", true, 7)), vehicleConsist(VehicleKind.TRAIN, 6f, vehicleSeed("e", true, 7)))
        assertEquals(3, vehiclePlacement(300f, 20f, 50f, 3 * (6.9333f + VEHICLE_DWELL_SECONDS) + 1f, VehicleRun(true, 0f))!!.cycle)
    }

    @Test fun namePlatesStayInsideTheirPlanAndNeverHideTheirNode() {
        val boxes = listOf(NetworkLabelBox("a", Offset(50f, 50f), 20f, Size(80f, 20f)),
            NetworkLabelBox("b", Offset(50f, 110f), 20f, Size(80f, 20f)),
            NetworkLabelBox("c", Offset(290f, 290f), 20f, Size(120f, 34f)))
        val placed = placeNetworkLabels(boxes, Size(300f, 300f), 2f)
        assertEquals(setOf("a", "b", "c"), placed.keys)
        val rects = boxes.associate { it.id to Rect(placed.getValue(it.id), it.size) }
        rects.values.forEach { r -> assertTrue(r.left >= 0f && r.top >= 0f && r.right <= 300f && r.bottom <= 300f) }
        boxes.forEach { b -> assertFalse(b.id, rects.getValue(b.id).overlaps(Rect(b.center, b.radius))) }
        assertFalse(rects.getValue("a").overlaps(rects.getValue("b")))
    }
}
