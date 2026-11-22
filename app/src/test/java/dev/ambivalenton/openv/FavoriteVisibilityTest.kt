// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.preferences.*
import dev.ambivalenton.openv.data.repository.StateSerializer
import dev.ambivalenton.openv.ui.theme.createTimelinePalette
import java.io.ByteArrayInputStream
import java.time.ZonedDateTime
import kotlin.math.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FavoriteVisibilityTest {
    private val now = ZonedDateTime.parse("2026-10-26T08:00:00+01:00[Europe/Berlin]")
    private val home = Location("Zuhause", latitude = 51.5, longitude = 7.2)
    private val work = Location("Arbeit", latitude = 51.51, longitude = 7.2)

    private fun fix(age: Long = 0, accuracy: Float = 5f) =
        DevicePresence(home, now.toInstant().toEpochMilli() - age, accuracy)

    @Test
    fun distanceGateUsesMetresAndRejectsMissingCoordinates() {
        fun north(metres: Double) =
            home.copy(latitude = home.latitude!! + Math.toDegrees(metres / 6_371_008.8))
        assertTrue(sameLocation(home, north(49.99)))
        assertFalse(sameLocation(home, north(50.01)))
        assertNull(distanceMeters(home, Location("Zuhause")))
        assertFalse(sameLocation(home, Location("Zuhause")))
        assertTrue(sameLocation(home, home))
    }

    @Test
    fun stopIdentityIsProviderScoped() {
        val a = Location("A", id = "1", provider = "VRR")
        assertTrue(sameLocation(a, a.copy(name = "A neuer Name")))
        assertFalse(sameLocation(a, a.copy(provider = "VBB")))
        assertFalse(sameLocation(a, a.copy(id = "2")))
    }

    @Test
    fun currentPlaceIsHiddenAndOtherTargetsRemainAvailable() {
        val a = Favorite("a", "Zuhause", home)
        val b = Favorite("b", "Arbeit", work)
        assertEquals(listOf(b), visibleFavorites(listOf(a, b), now, fix()))
        assertEquals(FavoriteHiddenReason.AT_CURRENT_LOCATION, a.hiddenReason(now, fix()))
    }

    @Test
    fun staleFutureAndCoarseFixesNeverHideTargets() {
        val f = Favorite(label = "Zuhause", location = home)
        for (presence in
            listOf(
                fix(PRESENCE_MAX_AGE_MILLIS),
                fix(-1000),
                fix(accuracy = 51f),
                fix(accuracy = Float.NaN),
            )) {
            assertEquals(listOf(f), visibleFavorites(listOf(f), now, presence))
        }
        assertEquals(listOf(f), visibleFavorites(listOf(f), now, null))
        assertEquals(
            listOf(f),
            visibleFavorites(listOf(f), now, fix(), hideAtCurrentLocation = false),
        )
    }

    @Test
    fun manualRoutingOriginDoesNotPretendToBePhysicalPresence() {
        val f = Favorite(label = "Zuhause", location = home)
        val state =
            StoredState(
                favorites = listOf(f),
                settings = AppSettings(originMode = OriginMode.MANUAL, manualOrigin = home),
            )
        assertEquals(listOf(f), visibleFavorites(state.favorites, now, state.presence))
    }

    @Test
    fun midnightWindowsBelongToTheirStartingWeekdayAndEndExclusively() {
        val fridayNight = HighlightRule(setOf(5), 22 * 60, 2 * 60)
        assertTrue(
            fridayNight.matches(ZonedDateTime.parse("2026-10-24T01:59:00+02:00[Europe/Berlin]"))
        )
        assertFalse(
            fridayNight.matches(ZonedDateTime.parse("2026-10-24T02:00:00+02:00[Europe/Berlin]"))
        )
        assertFalse(
            fridayNight.matches(ZonedDateTime.parse("2026-10-23T01:00:00+02:00[Europe/Berlin]"))
        )
    }

    @Test
    fun weekendAndEveningRulesAreCombinedWithOrAndBeatHighlighting() {
        val f =
            Favorite(
                label = "Arbeit",
                location = work,
                rule = HighlightRule((1..7).toSet(), 0, 0),
                hiddenRules =
                    listOf(
                        HighlightRule(setOf(6, 7), 0, 0),
                        HighlightRule((1..7).toSet(), 18 * 60, 0),
                    ),
            )
        // The windows are a time-dependent location. They no longer hide on their own;
        // they say where the person is while no GPS fix and no chosen start exist.
        assertNull(f.hiddenReason(now.minusDays(1), null))
        assertNull(f.hiddenReason(now, null))
        assertEquals(listOf(f), visibleFavorites(listOf(f), now.minusDays(1)))
        val state = StoredState(favorites = listOf(f))
        assertEquals(f, state.scheduledFavorite(now.minusDays(1)))
        assertEquals(f, state.scheduledFavorite(now.withHour(18)))
        assertNull(state.scheduledFavorite(now))
    }

    @Test
    fun highlightingIsStableAndNeverReordersUnmatchedFavourites() {
        val a = Favorite("a", "A", home)
        val b = Favorite("b", "B", work, rule = HighlightRule())
        val c = Favorite("c", "C", work)
        assertEquals(listOf(b, a, c), visibleFavorites(listOf(a, b, c), now))
    }

    @Test
    fun schedulerUsesTheNextRuleEdgeOrPresenceExpiry() {
        val f = Favorite(label = "Arbeit", location = work, rule = HighlightRule())
        assertEquals(
            now.withHour(10).toInstant(),
            nextFavoriteChange(StoredState(favorites = listOf(f)), now),
        )
        assertEquals(
            now.plusMinutes(5).toInstant(),
            nextFavoriteChange(StoredState(favorites = listOf(f), presence = fix()), now),
        )
        assertNull(nextFavoriteChange(StoredState(), now))
    }

    @Test
    fun schedulerPreservesRepeatedHoursAndNonexistentSpringEdges() {
        val f = Favorite(label = "Test", location = home, rule = HighlightRule(setOf(7), 150, 165))
        val autumn = ZonedDateTime.parse("2026-10-25T02:25:00+02:00[Europe/Berlin]")
        assertEquals(
            autumn.plusMinutes(5).toInstant(),
            nextFavoriteChange(StoredState(favorites = listOf(f)), autumn),
        )
        val repeated = ZonedDateTime.parse("2026-10-25T02:25:00+01:00[Europe/Berlin]")
        assertEquals(
            repeated.plusMinutes(5).toInstant(),
            nextFavoriteChange(StoredState(favorites = listOf(f)), repeated),
        )
        val spring = ZonedDateTime.parse("2027-03-28T01:59:00+01:00[Europe/Berlin]")
        assertEquals(
            spring.plusMinutes(1).toInstant(),
            nextFavoriteChange(StoredState(favorites = listOf(f)), spring),
        )
    }

    @Test
    fun schedulerIncludesTheEndOfAnOvernightWindowStartedYesterday() {
        val saturday = ZonedDateTime.parse("2026-10-24T01:00:00+02:00[Europe/Berlin]")
        val f =
            Favorite(
                label = "Nachtschicht",
                location = work,
                hiddenRules = listOf(HighlightRule(setOf(5), 22 * 60, 2 * 60)),
            )
        assertEquals(
            saturday.plusHours(1).toInstant(),
            nextFavoriteChange(StoredState(favorites = listOf(f)), saturday),
        )
    }

    @Test
    fun radiusUsesIntegerStepsAndGermanFixedDecimalKilometres() {
        val locale=java.util.Locale.getDefault()
        try {
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        assertEquals("500 m", formatRadius(500))
        assertEquals("900 m", formatRadius(949))
        assertEquals("1,0 km", formatRadius(999))
        assertEquals("1,0 km", formatRadius(1000))
        assertEquals("1,5 km", formatRadius(1500))
        assertEquals("2,0 km", formatRadius(2000))
        assertEquals("3,0 km", formatRadius(3000))
        assertEquals(1000, normalizeRadius(999.99994f.roundToInt()))
        for (m in 500..3000 step 100) assertEquals(m, normalizeRadius(m))
        } finally {java.util.Locale.setDefault(locale)}
    }

    @Test
    fun everyPaletteHasReadableLabelsInLightAndDarkModes() {
        for (scheme in listOf(lightColorScheme(), darkColorScheme())) for (theme in
            TimelineTheme.entries) {
            val palette = createTimelinePalette(theme, scheme)
            for (type in LineType.entries) {
                val ink = palette[type]
                val a = ink.container.luminance()
                val b = ink.content.luminance()
                assertTrue("$theme / $type contrast", (max(a, b) + .05) / (min(a, b) + .05) >= 4.5)
            }
        }
    }

    @Test
    fun schemaTwoDefaultsAndNewPersonalizationRoundTrip() = runTest {
        val old =
            StateSerializer.readFrom(
                ByteArrayInputStream(
                    """{"schemaVersion":2,"favorites":[{"id":"keep","label":"Daheim","icon":"HEART","location":{"name":"H","latitude":51.5,"longitude":7.2}}]}"""
                        .toByteArray()
                )
            )
        assertEquals(10, old.schemaVersion) // build 66: network plan schema
        assertEquals("keep", old.favorites.single().id)
        assertTrue(old.favorites.single().hiddenRules.isEmpty())
        assertEquals(TimelineTheme.ORIGINAL, old.settings.timelineTheme)
        val state =
            old.copy(
                settings = old.settings.copy(timelineTheme = TimelineTheme.VIVID),
                presence = fix(),
                favorites =
                    old.favorites.map {
                        it.copy(
                            icon = FavoriteIcon.COFFEE,
                            hiddenRules = listOf(HighlightRule(setOf(6, 7), 0, 0)),
                        )
                    },
            )
        assertEquals(
            state,
            StateSerializer.readFrom(
                ByteArrayInputStream(StateSerializer.json.encodeToString(state).toByteArray())
            ),
        )
    }
}
