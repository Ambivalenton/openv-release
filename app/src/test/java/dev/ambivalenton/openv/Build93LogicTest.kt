// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.network.networkHoldHint
import dev.ambivalenton.openv.ui.network.networkRouteRows
import dev.ambivalenton.openv.ui.screens.searchSettings
import dev.ambivalenton.openv.ui.screens.settingsPath
import dev.ambivalenton.openv.ui.screens.SettingsPage
import dev.ambivalenton.openv.ui.screens.productRestrictionLine
import dev.ambivalenton.openv.ui.theme.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

/**
 * The new domain rules of the UX report. Robolectric only supplies Android's colour maths;
 * the texts are checked in German as the source language.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class Build93LogicTest {
    private val old = java.util.Locale.getDefault()
    @org.junit.Before fun german() { java.util.Locale.setDefault(java.util.Locale.GERMANY) }
    @org.junit.After fun restore() { java.util.Locale.setDefault(old) }
    private val now = 1_800_000_000_000L
    private fun stop(name: String, id: String, lat: Double = 51.45, lon: Double = 7.0) = Location(name, id, lat, lon, "VRR", LocationKind.STOP)
    private val essen = stop("Essen Hbf", "e"); private val bochum = stop("Bochum Hbf", "b", lon = 7.22); private val dortmund = stop("Dortmund Hbf", "d", lon = 7.46)
    private fun ride(from: Location, to: Location, start: Long = now + 600_000, end: Long = start + 900_000, notices: List<String> = emptyList(),
        line: String = "RE 1", cancelled: Boolean = false, stops: List<Stopover> = emptyList()) =
        TripSegment("$line-${from.id}-${to.id}", from, to, start, end, LineType.REGIONAL, line, notices = notices,
            delay = DelayInfo(cancelled = cancelled), stops = stops)
    private fun trip(vararg legs: TripSegment, notices: List<String> = emptyList()) = Trip("t-${legs.size}", legs.toList(), "VRR", now, notices = notices)

    // 2.5 · deterministic notices at the right boarding and alighting stop
    @Test fun noticesHitTheBoardingAlightingAndTransferStopOnly() {
        val boarding = scanJourneyNotices(trip(ride(essen, bochum, notices = listOf("Essen Hbf: Haltestelle vorübergehend verlegt."))))
        assertEquals(NoticeCall.BOARDING, boarding.single().targets.single().call)
        assertEquals("Einstieg verlegt", boarding.single().label())
        assertTrue("trigger words are marked", boarding.single().triggers.isNotEmpty())
        val alighting = scanJourneyNotices(trip(ride(essen, bochum, notices = listOf("Bochum Hbf: Halt entfällt wegen Bauarbeiten."))))
        assertEquals(NoticeCall.ALIGHTING, alighting.single().targets.single().call)
        // A transfer stop is the alighting of one ride and the boarding of the next.
        val transfer = scanJourneyNotices(trip(ride(essen, bochum), ride(bochum, dortmund, start = now + 1_800_000, line = "S1"),
            notices = listOf("Ersatzhaltestelle in Bochum Hbf: bitte Aushänge beachten.")))
        assertEquals(setOf(0 to NoticeCall.ALIGHTING, 1 to NoticeCall.BOARDING), transfer.single().targets.map { it.segmentIndex to it.call }.toSet())
    }

    @Test fun anotherStopANegationAndAHypothesisKeepTheNormalNotice() {
        val herne = stop("Herne, Rathaus", "hr", lon = 7.2)
        assertEquals("the right town hall is named", 1, scanJourneyNotices(trip(ride(herne, bochum,
            notices = listOf("Herne, Rathaus: Haltestelle verlegt.")))).size)
        assertTrue("the town hall of another city", scanJourneyNotices(trip(ride(herne, bochum,
            notices = listOf("Bochum, Rathaus: Haltestelle verlegt.")))).isEmpty())
        assertTrue("a stop of the journey that is no boarding or alighting", scanJourneyNotices(trip(ride(essen, dortmund,
            notices = listOf("Gelsenkirchen Hbf: Halt entfällt.")))).isEmpty())
        assertTrue("negation", scanJourneyNotices(trip(ride(essen, bochum,
            notices = listOf("Essen Hbf: Halt entfällt nicht.")))).isEmpty())
        assertTrue("hypothetical", scanJourneyNotices(trip(ride(essen, bochum,
            notices = listOf("Bochum Hbf: Halt entfällt eventuell.")))).isEmpty())
    }

    @Test fun aHiddenNoticeStaysHiddenUntilItsTextOrStopChanges() {
        val first = trip(ride(essen, bochum, notices = listOf("Essen Hbf: Haltestelle verlegt.")))
        val hit = scanJourneyNotices(first).single()
        assertTrue(scanJourneyNotices(first, hit.keys.toSet()).isEmpty())
        val rephrased = trip(ride(essen, bochum, notices = listOf("Essen Hbf: Ersatzhaltestelle an der Freiheit.")))
        assertEquals("a changed text is judged again", 1, scanJourneyNotices(rephrased, hit.keys.toSet()).size)
        // A notice mark never makes a cancelled journey usable.
        val cancelled = trip(ride(essen, bochum, cancelled = true, notices = listOf("Essen Hbf: Haltestelle verlegt.")))
        assertEquals(TripImpossibility.CANCELLED, tripImpossibility(cancelled, now, currentSearch = true))
        assertEquals(mapOf(0 to "Einstieg verlegt"), noticeMarks(scanJourneyNotices(first)))
    }

    // 2.2 · confirmed impossibility, current search versus a chosen earlier time
    @Test fun onlyConfirmedStatesRemoveAJourney() {
        val gone = trip(ride(essen, bochum, start = now - 10 * 60_000))
        assertEquals(TripImpossibility.MISSED_BOARDING, tripImpossibility(gone, now, currentSearch = true))
        assertNull("a deliberately earlier search keeps it findable", tripImpossibility(gone, now, currentSearch = false))
        val boardingOff = trip(ride(essen, bochum, stops = listOf(Stopover(essen, departureTime = now + 600_000, cancelled = true))))
        assertEquals(TripImpossibility.BOARDING_CANCELLED, tripImpossibility(boardingOff, now, true))
        assertNull(tripImpossibility(trip(ride(essen, bochum)), now, true))
        assertEquals(TripImpossibility.ENDED, pinRejection(trip(ride(essen, bochum, start = now - 3_600_000, end = now - 1_800_000)), now))
    }

    // 2.3 · swap start and destination
    @Test fun swappingNeedsTwoPlacesAndFreezesAGpsStart() {
        assertEquals(SwapPlan.NeedsDestination, swapPlan(null, essen, now))
        assertEquals(SwapPlan.NeedsStart, swapPlan(bochum, null, now))
        val manual = swapPlan(bochum, essen, now) as SwapPlan.Swap
        assertEquals(bochum, manual.newStart); assertEquals(essen, manual.newDestination)
        val gps = Location("Mein Standort", latitude = 51.5, longitude = 7.1, isDevicePosition = true)
        val frozen = swapPlan(bochum, gps, now) as SwapPlan.Swap
        assertFalse("the new destination keeps the place of the swap", frozen.newDestination.isDevicePosition)
        assertEquals(51.5, frozen.newDestination.latitude!!, 1e-9)
        assertEquals(essen, unambiguousPlace("Essen Hbf", listOf(essen, stop("Essen-Steele", "s"))))
        assertNull("free text with several candidates is not guessed", unambiguousPlace("Essen", listOf(essen, stop("Essen-Steele", "s"))))
    }

    // Build 94 · the search button names what the chosen time does (no places), across midnight and the clock change
    @Test fun theSearchButtonNamesTheChosenTime() {
        val zone = ZoneId.of("Europe/Berlin"); val de = java.util.Locale.GERMAN
        val base = ZonedDateTime.of(2026, 11, 23, 23, 50, 0, 0, zone) // a Monday shortly before midnight
        val now = base.toInstant().toEpochMilli()
        fun at(t: ZonedDateTime) = t.toInstant().toEpochMilli()
        assertEquals("Nächste Verbindung suchen", searchButtonLabel(null, TimeMode.DEPARTURE, now, zone, de))
        assertEquals("Verbindung ab 23:55 suchen", searchButtonLabel(at(base.plusMinutes(5)), TimeMode.DEPARTURE, now, zone, de))
        assertEquals("Verbindung suchen, damit du morgen um 08:15 ankommst",
            searchButtonLabel(at(base.plusDays(1).withHour(8).withMinute(15)), TimeMode.ARRIVAL, now, zone, de))
        assertEquals("Verbindung am Donnerstag ab 07:30 suchen",
            searchButtonLabel(at(base.plusDays(3).withHour(7).withMinute(30)), TimeMode.DEPARTURE, now, zone, de))
        assertEquals("Verbindung suchen, damit du am 2.12. um 09:00 ankommst",
            searchButtonLabel(at(base.plusDays(9).withHour(9).withMinute(0)), TimeMode.ARRIVAL, now, zone, de))
        // 25 October 2026, 02:30 exists twice in Berlin: the zone tells them apart.
        val twice = ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, zone)
        assertEquals("Verbindung morgen ab 02:30 MESZ suchen",
            searchButtonLabel(at(twice), TimeMode.DEPARTURE, at(twice.minusHours(3)), zone, de))
        assertEquals("Verbindung morgen ab 02:30 MEZ suchen",
            searchButtonLabel(at(twice.withLaterOffsetAtOverlap()), TimeMode.DEPARTURE, at(twice.minusHours(3)), zone, de))
    }

    // H04/2.4 · the information line and the long-distance default
    @Test fun theTransportLineSaysWhatIsRestricted() {
        assertNull(productRestrictionLine(localProducts))
        assertEquals("Verkehrsmittel · Ohne Bus", productRestrictionLine(localProducts - LineType.BUS))
        assertEquals("Verkehrsmittel · mit Fernverkehr", productRestrictionLine(selectableProducts))
        assertFalse("long-distance trains are off by default", LineType.ICE in AppSettings().products)
    }

    // H11/M09 · comparison groups per destination
    @Test fun boardGroupsFollowTheTappedRowsDestination() {
        fun departure(id: String, to: Location, minute: Int) = Departure(id, essen, "RE $id", LineType.REGIONAL, to.name, now + minute * 60_000L,
            now + minute * 60_000L, DelayInfo(), "3", journey = trip(ride(essen, to, start = now + minute * 60_000L)),
            journeyQuery = JourneyQuery(essen, to, now))
        val rows = listOf(departure("1", bochum, 2), departure("2", dortmund, 4), departure("3", bochum, 9), departure("4", dortmund, 12))
        val groups = boardComparisonGroups(rows)
        assertEquals(listOf("Bochum Hbf", "Dortmund Hbf"), groups.map { it.destination })
        assertEquals("Weitere Fahrten nach Dortmund Hbf", groups[1].label)
        assertTrue(groups[1].matches(rows[3])); assertFalse(groups[1].matches(rows[0]))
        val spoken = boardRowDescription(rows[0].copy(delay = DelayInfo(departureSeconds = 300)))
        assertTrue(spoken, spoken.contains("RE 1 nach Bochum Hbf") && spoken.contains("Gleis 3") && spoken.contains("5 Minuten später"))
    }

    // N02 · shared formatting
    @Test fun countsAndPlatformsUseTheSharedWording() {
        assertEquals("0 Umstiege", transferCount(0)); assertEquals("1 Umstieg", transferCount(1)); assertEquals("2 Umstiege", transferCount(2))
        assertEquals("1 Minute", minutesText(1)); assertEquals("5 Minuten", minutesText(5))
        val bus = Departure("x", essen, "SB 16", LineType.BUS, "Bottrop", now, now, DelayInfo(), "B")
        assertEquals("Bussteig B", departurePlatformText(bus))
        assertEquals("Gleis 7", departurePlatformText(bus.copy(lineType = LineType.REGIONAL, platform = "7")))
    }

    // M03 · the empty overview names its reason
    @Test fun theEmptyOverviewExplainsWhyNothingIsShown() {
        assertEquals(OverviewEmptyKind.NO_FAVORITES, StoredState().overviewEmptyState(now).kind)
    }

    // 2.10 · sun times, schedules and the button
    @Test fun sunTimesAndSchedulesDecideDayAndNight() {
        val sun = sunTimes(LocalDate.of(2026, 6, 21), 51.45, 7.01)!!
        val berlin = ZoneId.of("Europe/Berlin")
        val rise = sun.sunrise.atZone(berlin); val set = sun.sunset.atZone(berlin)
        assertTrue("$rise", rise.hour == 5 && rise.minute in 0..30)
        assertTrue("$set", set.hour == 21 && set.minute in 30..59)
        assertNull("polar night", sunTimes(LocalDate.of(2026, 12, 21), 78.2, 15.6))
        val schedule = DayNightSettings(schedule = DayNightSchedule.TIMES, dayStartMinute = 7 * 60, nightStartMinute = 20 * 60)
        assertEquals(true, schedule.scheduledNight(ZonedDateTime.of(2026, 6, 21, 22, 0, 0, 0, berlin), null, null))
        assertEquals(false, schedule.scheduledNight(ZonedDateTime.of(2026, 6, 21, 12, 0, 0, 0, berlin), null, null))
        assertNull(DayNightSettings().scheduledNight(ZonedDateTime.now(berlin), null, null))
        assertEquals("sun schedule without a place uses the times", true,
            schedule.copy(schedule = DayNightSchedule.SUN).scheduledNight(ZonedDateTime.of(2026, 6, 21, 23, 0, 0, 0, berlin), null, null))
    }

    @Test fun theButtonSwitchesBetweenTwoRealThemesAndKeepsBothHalves() {
        val light = AppSettings(appTheme = AppTheme.CLASSIC_LIGHT)
        assertTrue(light.nextDayNightIsNight(false))
        val night = light.withDayNight(true)
        assertTrue(night.appTheme!!.dark); assertTrue(night.dayNight.night)
        assertEquals(AppTheme.CLASSIC_LIGHT, night.dayNight.other!!.appTheme)
        assertFalse(night.nextDayNightIsNight(false))
        val back = night.withDayNight(false)
        assertEquals(AppTheme.CLASSIC_LIGHT, back.appTheme); assertEquals(night.appTheme, back.dayNight.other!!.appTheme)
        // From a dark theme the first change leads to the day.
        assertFalse(AppSettings(appTheme = AppTheme.CLASSIC_DARK).nextDayNightIsNight(false))
        // A scheduled change applies once per boundary; a manual change in between holds.
        val auto = light.withScheduledDayNight(true, false)
        assertTrue(auto.dayNight.night)
        val manual = auto.withDayNight(false)
        assertEquals(manual, manual.withScheduledDayNight(true, false).let { if (it.dayNight.lastAutoNight == true && !it.dayNight.night) manual else it })
        // A saved personal theme takes both halves along.
        val design = night.personalDesign(AppLogo.ORIGINAL)
        assertEquals(night.dayNight.other, design.dayNight!!.other)
        assertTrue(design.matches(night.personalDesign(AppLogo.ORIGINAL)))
        assertTrue("older themes compare without day and night", design.copy(dayNight = null).matches(night.personalDesign(AppLogo.ORIGINAL)))
    }

    // 2.8 · random start theme and rainbow accents
    @Test fun theRandomThemeNeverRepeatsAndOnlyCoversTheSavedChoice() {
        val saved = AppSettings(appTheme = AppTheme.CLASSIC_LIGHT, randomThemeOnStart = true, lastRandomTheme = AppTheme.CLASSIC_DARK)
        repeat(40) { seed ->
            val drawn = drawRandomTheme(saved, Random(seed))!!
            assertNotEquals(AppTheme.CLASSIC_DARK, drawn); assertNotEquals(AppTheme.CLASSIC_LIGHT, drawn)
        }
        val atNight = saved.copy(dayNight = DayNightSettings(night = true))
        repeat(20) { seed -> assertTrue(drawRandomTheme(atNight, Random(seed))!!.dark) }
        val session = SessionTheme(AppTheme.CLASSIC_LIGHT, AppTheme.CLASSIC_DARK)
        assertEquals(AppTheme.CLASSIC_DARK, saved.withSessionTheme(session).appTheme)
        assertEquals("a newly chosen theme wins", AppTheme.AMOLED_BLACK, saved.copy(appTheme = AppTheme.AMOLED_BLACK).withSessionTheme(session).appTheme)
        assertEquals("switched off", AppTheme.CLASSIC_LIGHT, saved.copy(randomThemeOnStart = false).withSessionTheme(session).appTheme)
    }

    @Test fun rainbowAccentsStayReadableAndAvoidTheBackgroundHue() {
        val blue = lightColorScheme(background = Color(0xFFDDE8FF), surface = Color(0xFFEFF4FF))
        val dark = darkColorScheme()
        val seen = mutableSetOf<Int>()
        for (index in 0 until 14) for ((scheme, high) in listOf(blue to false, dark to true)) {
            val r = rainbowScheme(scheme, index, high)
            val minimum = if (high) 7.0 else 4.5
            fun contrast(a: Color, b: Color) = ColorUtils.calculateContrast(a.toArgb() or 0xff000000.toInt(), b.toArgb() or 0xff000000.toInt())
            assertTrue(contrast(r.onPrimary, r.primary) >= minimum - .05)
            assertTrue(contrast(r.onPrimaryContainer, r.primaryContainer) >= minimum - .05)
            assertTrue(contrast(r.primary, scheme.surface) >= minimum - .05)
            assertEquals("surfaces stay calm", scheme.surface, r.surface); assertEquals(scheme.error, r.error)
            if (scheme === blue) {
                val hue = Hct.fromInt(r.primaryContainer.toArgb()).hue
                assertTrue("no blue on blue ($hue)", abs(hue - Hct.fromInt(blue.background.toArgb()).hue).let { minOf(it, 360 - it) } > 30)
                seen += r.primary.toArgb()
            }
        }
        assertTrue("the accents change with the page", seen.size >= 5)
    }

    // 2.7 · three capsule colours for every theme
    @Test fun capsuleTonesDifferAndStayReadableIncludingGreys() {
        val base = Color(0xFFF2F2F2)
        val same = listOf(Color(0xFF3355AA), Color(0xFF3456AB), Color(0xFF3355A9))
        val tones = destinationTones(3, base, DesignLanguage.MATERIAL, false, same, same)
        assertEquals(3, tones.map { it.fill }.distinct().size)
        tones.forEach { assertTrue(ColorUtils.calculateContrast(it.ink.toArgb(), it.fill.toArgb() or 0xff000000.toInt()) >= 4.4) }
        val greys = destinationTones(3, base, DesignLanguage.MATERIAL, true, same, same)
        greys.forEach { assertTrue("grey ${it.fill}", Hct.fromInt(it.fill.toArgb()).chroma < 4) }
        assertEquals(3, greys.map { it.fill }.distinct().size)
    }

    // M11 · settings search
    @Test fun theSettingsSearchFindsTheUsualWordsWithTheirPath() {
        assertEquals(SettingsPage.TYPOGRAPHY, searchSettings("Schrift").first().page)
        assertEquals(SettingsPage.MODES, searchSettings("Backup").first().page)
        assertEquals("backup", searchSettings("Backup").first().anchor)
        assertEquals(SettingsPage.SERVICES, searchSettings("Cache").first().page)
        assertEquals(SettingsPage.SERVICES, searchSettings("WLAN").first().page)
        assertNotNull(searchSettings("WLAN").first().noteKey)
        assertEquals(SettingsPage.WALKING, searchSettings("Gehtempo").first().page)
        assertEquals(SettingsPage.TYPOGRAPHY, searchSettings("grosse schrift").first().page)
        assertTrue(searchSettings("x").isEmpty())
        assertEquals("Einstellungen › Fahrt & Navigation › Fußwege & Navigation", settingsPath(SettingsPage.WALKING))
    }

    // M10 · widget actions by mode
    @Test fun theWidgetRefreshIsNamedAfterItsMode() {
        val list = StoredState(boardStops = listOf(essen, bochum))
        assertTrue(boardRefreshUsesLocation(list))
        assertEquals("Standort und Abfahrten aktualisieren", boardRefreshLabel(list))
        val fixed = list.copy(settings = list.settings.copy(board = list.settings.board.copy(selectedStop = essen)))
        assertFalse(boardRefreshUsesLocation(fixed)); assertEquals("Abfahrten aktualisieren", boardRefreshLabel(fixed))
        val manual = list.copy(settings = list.settings.copy(originMode = OriginMode.MANUAL, manualOrigin = bochum))
        assertFalse(boardRefreshUsesLocation(manual))
    }

    // M13 · manual waypoint order
    @Test fun theManualOrderIsKeptAndDistinguishedFromTheOptimisedOne() {
        val a = stop("A", "a", lon = 7.05); val b = stop("B", "b2", lon = 7.15)
        val origin = stop("Start", "s0", lon = 7.0); val destination = stop("Ziel", "z0", lon = 7.3)
        assertEquals(listOf(a, b), plannedWaypointOrder(origin, destination, listOf(b, a), WaypointOrder.OPTIMIZED))
        assertEquals(listOf(b, a), plannedWaypointOrder(origin, destination, listOf(b, a), WaypointOrder.MANUAL))
        val query = JourneyQuery(origin, destination, now, waypoints = listOf(b, a))
        val json = StateSerializer.json.encodeToString(JourneyQuery.serializer(), query)
        assertFalse("the default order is not written", json.contains("waypointOrder"))
        val manual = query.copy(waypointOrder = WaypointOrder.MANUAL)
        assertEquals(manual, StateSerializer.json.decodeFromString(JourneyQuery.serializer(), StateSerializer.json.encodeToString(JourneyQuery.serializer(), manual)))
        assertTrue(waypointOrderPreview(origin, destination, listOf(b, a), mapOf(waypointIdentity(b) to 10f)).startsWith("Reihenfolge: Start → B (10 Min) → A → Ziel"))
    }

    // M17/M01/M02 · list of the plan, hold hint, direct favourite
    @Test fun thePlanListNamesEveryDirectionAndTheHoldHintFollowsTheSetting() {
        val home = Favorite("h", "Zuhause", essen); val work = Favorite("w", "Arbeit", bochum)
        val state = StoredState(favorites = listOf(home, work), network = TransitNetwork(
            listOf(NetworkNode("h", GridPoint(0, 0)), NetworkNode("w", GridPoint(3, 0))),
            listOf(NetworkEdge("l", "h", "w", DirectionMode.BOTH))))
        assertEquals(listOf("Zuhause→Arbeit", "Arbeit→Zuhause"), networkRouteRows(state).map { "${it.from.label}→${it.to.label}" })
        val oneWay = state.copy(network = state.network.copy(edges = listOf(NetworkEdge("l", "h", "w", DirectionMode.B_TO_A))))
        assertEquals(listOf("Arbeit→Zuhause"), networkRouteRows(oneWay).map { "${it.from.label}→${it.to.label}" })
        val query = state.routeQuery(work, home, now, TimeMode.DEPARTURE)
        assertEquals(bochum, query.origin); assertEquals(essen, query.destination)
        assertEquals("Halte einen Favoriten gedrückt, um ihn zu verschieben.", networkHoldHint(AppSettings().networkHoldAction))
        assertTrue(networkHoldHint(NetworkHoldAction.LOCATION).contains("Standort"))
        assertEquals("Essen Hbf", suggestedFavoriteName(essen))
    }

    // H05 · typed failures
    @Test fun failuresAreTypedAndRespectAWaitingTime() {
        assertEquals(FailureKind.RATE_LIMITED, httpFailureKind(429))
        assertEquals(FailureKind.UNAVAILABLE, httpFailureKind(503))
        assertEquals(FailureKind.OFFLINE, transportFailureKind(java.net.UnknownHostException("x"), online = false))
        assertEquals(FailureKind.TIMEOUT, transportFailureKind(java.net.SocketTimeoutException("x"), online = true))
        val limited = TransitFailure(FailureKind.RATE_LIMITED, retryAt = now + 30_000)
        assertEquals(30L, limited.waitSeconds(now))
    }
}
