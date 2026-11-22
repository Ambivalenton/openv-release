// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.SettingsPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Proportional walks, new favourite symbols with their own texts, the new settings order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class)
class Build92Test {
    private val a = Location("A", "a", 51.0, 7.0)
    private val b = a.copy(name = "B", id = "b", longitude = 7.01)
    private val t = 1_800_000_000_000L
    private val minute = 60_000L
    private fun leg(id: String, start: Long, minutes: Int, type: LineType) =
        TripSegment(id, a, b, start, start + minutes * minute, type, if (type == LineType.WALK) "Fußweg" else "RE3")
    private val newIcons = listOf(FavoriteIcon.MOTHER, FavoriteIcon.FATHER, FavoriteIcon.GRANDPARENTS, FavoriteIcon.PARENTS,
        FavoriteIcon.PARTNER, FavoriteIcon.SIBLINGS, FavoriteIcon.FRIENDS, FavoriteIcon.BAKERY, FavoriteIcon.RESTAURANT,
        FavoriteIcon.SUPERMARKET, FavoriteIcon.KINDERGARTEN, FavoriteIcon.PHARMACY, FavoriteIcon.DOCTOR, FavoriteIcon.TOWN_HALL,
        FavoriteIcon.LIBRARY, FavoriteIcon.CINEMA, FavoriteIcon.THEATER, FavoriteIcon.STADIUM, FavoriteIcon.SWIMMING,
        FavoriteIcon.CLUB, FavoriteIcon.WORSHIP, FavoriteIcon.HOTEL, FavoriteIcon.LAKE, FavoriteIcon.DOG)

    @Test fun walksOnTheChartAreAsLongAsTheirTime() {
        // A 2- and a 15-minute walk before and after rides, at the small scales of the fitted chart.
        for (scale in listOf(1f, 1.8f, 2.6f, 4f)) {
            val lengths = listOf(2, 15).associateWith { minutes ->
                val walk = leg("w", t, minutes, LineType.WALK)
                val ride = leg("r", walk.endTime, 9, LineType.REGIONAL)
                val trip = Trip("x", listOf(walk, ride, leg("e", ride.endTime, minutes, LineType.WALK)), "test", t)
                val tracks = timelineWalkTracks(trip, scale, 1f, timelineClockLabels(trip, scale, 1f), 4f, 12f)
                // Each track minus its single free end (half a sole) is exactly its time on the axis.
                tracks.map { it.bottomDp - it.topDp - 4f }
            }
            for (i in 0..1) assertEquals("scale $scale", 15f / 2f, lengths.getValue(15)[i] / lengths.getValue(2)[i], .001f)
        }
    }

    @Test fun atLeastTwentyNewSymbolsEachWithItsOwnOverviewTexts() {
        assertTrue(newIcons.size >= 20)
        assertEquals(newIcons.size, newIcons.toSet().size)
        newIcons.forEach { icon ->
            val arrivals = iconArrivalMessages(icon)
            assertTrue("$icon arrivals", arrivals.size >= 10)
            // Not the neutral place texts: each symbol has its own.
            assertTrue("$icon own arrivals", arrivals.toSet() != iconArrivalMessages(FavoriteIcon.PLACE).toSet())
            assertTrue("$icon lead", iconJourneyLead(icon, 0).endsWith(" ") || iconJourneyLead(icon, 0).endsWith(": "))
            for (variant in 0 until 5) {
                val template = iconRouteTemplate(icon, null, false, variant)!!
                listOf("{origin}", "{target}", "{dep}", "{arr}").forEach { assertEquals(template, 1, template.split(it).size - 1) }
            }
        }
        // "Kinder" was replaced by "Eltern"; heart and stop are the old ones, not new symbols.
        assertFalse(FavoriteIcon.entries.any { it.name == "CHILDREN" })
        assertEquals(FavoriteIcon.HEART.ordinal + 1, FavoriteIcon.STOP.ordinal)
        assertTrue(newIcons.none { it in primaryFavoriteIcons })
    }

    @Test fun theSettingsAreReordered() {
        // The former fold-out is a page of its own; services & data live under info & help.
        assertEquals(SettingsPage.ROOT, SettingsPage.MODES.parent)
        assertEquals(SettingsPage.INFO, SettingsPage.SERVICES.parent)
        assertTrue(SettingsPage.MODES in SettingsPage.ROOT.children)
        assertFalse(SettingsPage.SERVICES in SettingsPage.ROOT.children)
        assertTrue(SettingsPage.SERVICES in SettingsPage.INFO.children)
    }
}
