// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.network.NetworkEditBar
import dev.ambivalenton.openv.ui.screens.ConnectionSearchSettings
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The timeline's notices and joins, the preferences, the lead time and the edit bar at their composables. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w380dp-h1600dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class Build96UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = System.currentTimeMillis() + 3_600_000
    private fun stop(name: String, id: String, lon: Double) = Location(name, id, 51.5, lon, "VRR", LocationKind.STOP, city = "Herne")
    private fun content(settings: AppSettings = AppSettings(quietMode = true), body: @Composable () -> Unit) =
        compose.setContent { OpenVTheme(settings) { Surface(Modifier.fillMaxSize()) { Column(Modifier.verticalScroll(rememberScrollState())) { body() } } } }

    /** The notices of a ride stand before it; a walk to the boarding stop shows that stop once. */
    @Test fun noticesComeBeforeTheirRideAndAWalkJoinsTheDeparture() {
        val julia = stop("Herne Zeche Julia", "j", 7.15); val herne = stop("Herne Bf", "h", 7.217); val street = stop("Herne, Bahnhofstr. 3", "c", 7.214)
        val trip = Trip("t", listOf(
            TripSegment("bus", julia, street, now, now + 600_000, LineType.BUS, "390", notices = listOf("Herne Zeche Julia: Haltestelle vorübergehend verlegt.")),
            TripSegment("walk", street, herne, now + 600_000, now + 840_000, LineType.WALK, "Fußweg"),
            TripSegment("s2", herne, stop("Dortmund Hbf", "d", 7.46), now + 900_000, now + 2_400_000, LineType.SUBURBAN, "S2")), "VRR", now)
        content { ModernRouteTimeline(trip) }
        val mark = compose.onNodeWithTag("notice-mark-bus").fetchSemanticsNode().boundsInRoot
        val boarding = compose.onNodeWithText("Herne Zeche Julia").fetchSemanticsNode().boundsInRoot
        assertTrue("the notice comes before the ride: $mark / $boarding", mark.bottom <= boarding.top + 1f)
        compose.onAllNodesWithText("Herne Bf").assertCountEquals(1)
    }

    /** The preferences write the new fields; the lead time rests on zero and goes below it. */
    @Test fun thePreferencesAndTheLeadTime() {
        var settings by mutableStateOf(AppSettings(quietMode = true))
        content(settings) { ConnectionSearchSettings(settings, { settings = it(settings) }) }
        // Sliders with speech bubbles instead of chips.
        compose.onNodeWithTag("pref-walking-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.runOnIdle { assertEquals(WalkingPreference.MINIMAL, settings.overview.routingPreferences.walking) }
        compose.onNodeWithTag("pref-transfers-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        compose.runOnIdle { assertEquals(TransferPreference.ANY, settings.overview.routingPreferences.transfers) }
        compose.onNodeWithTag("pref-priority-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        compose.runOnIdle { assertEquals(RoutePriority.ARRIVAL, settings.overview.routingPreferences.priority) }
        compose.onNodeWithContentDescription("Zeit bis zur Abfahrt").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(-6f) }
        compose.runOnIdle { assertEquals(-6f, settings.overview.bufferMinutes, .01f) }
        compose.onNodeWithContentDescription("Zeit bis zur Abfahrt").performSemanticsAction(SemanticsActions.SetProgress) { it(.4f) }
        compose.runOnIdle { assertEquals("rests on zero", 0f, settings.overview.bufferMinutes, .001f) }
    }

    /** The open bar covers the whole width; its three actions keep their labels. */
    @Test fun theOpenEditBarSpansTheWidth() {
        val log = mutableListOf<String>()
        content { Box(Modifier.fillMaxWidth()) { NetworkEditBar(true, {}, { log += "settings" }, { log += "add" }, { log += "done" }, Modifier.fillMaxWidth()) } }
        val settingsBox = compose.onNodeWithTag("network-settings").fetchSemanticsNode().boundsInRoot
        val done = compose.onNodeWithTag("network-done").fetchSemanticsNode().boundsInRoot
        assertTrue("the settings box starts at the left: $settingsBox", settingsBox.left < 20f)
        assertTrue("the check stays at the right: $done", done.right > 340f)
        compose.onNodeWithContentDescription("Favoriten-Einstellungen").performClick()
        compose.onNodeWithContentDescription("Favorit hinzufügen").performClick()
        compose.onNodeWithContentDescription("Fertig").performClick()
        compose.runOnIdle { assertEquals(listOf("settings", "add", "done"), log) }
    }
}
