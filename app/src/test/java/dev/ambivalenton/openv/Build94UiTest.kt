// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.network.NetworkRouteList
import dev.ambivalenton.openv.ui.screens.SettingsSearch
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The planner mode, the route list, the slim settings search and the font picker at their composables. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w360dp-h780dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class Build94UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun content(settings: AppSettings = AppSettings(quietMode = true), body: @Composable () -> Unit) =
        compose.setContent { OpenVTheme(settings) { Surface(Modifier.fillMaxSize()) { body() } } }
    private fun stop(name: String, id: String, lon: Double) = Location(name, id, 51.45, lon, "VRR", LocationKind.STOP)

    /** The new frame under "Modi & Verhalten" switches the planner mode on and off. */
    @Test fun thePlannerModeHasItsOwnFrame() {
        var settings by mutableStateOf(AppSettings(quietMode = true))
        content { Column(Modifier.verticalScroll(rememberScrollState())) { AppModesSettings(settings, { settings = it(settings) }) } }
        compose.onNodeWithTag("mode-planner").performScrollTo().assertIsOff().performClick()
        compose.runOnIdle { assertTrue(settings.plannerMode) }
        compose.onNodeWithTag("mode-planner").assertIsOn().performClick()
        compose.runOnIdle { assertFalse(settings.plannerMode) }
    }

    /** The swap button sits between the two fields, swaps once per tap and the fields come back to rest. */
    @Test fun theSwapButtonBetweenTheFieldsSwapsAndSettles() {
        var start by mutableStateOf<String?>("Essen Hbf"); var destination by mutableStateOf<String?>("Bochum Hbf")
        val motion = PlannerSwapMotion()
        content(AppSettings(quietMode = false)) {
            val scope = rememberCoroutineScope()
            Column(Modifier.padding(20.dp)) {
                PlannerFieldPair(motion, {
                    val old = start; start = destination; destination = old
                    scope.launch { motion.play(false) }
                }, swapEnabled = start != null || destination != null,
                    first = { m -> Text(start.orEmpty(), m.fillMaxWidth().height(56.dp).testTag("first")) },
                    second = { m -> Text(destination.orEmpty(), m.fillMaxWidth().height(56.dp).testTag("second")) })
            }
        }
        val first = compose.onNodeWithTag("first").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithContentDescription("Start und Ziel tauschen").assertHasClickAction().fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("second").fetchSemanticsNode().boundsInRoot
        assertTrue("on the seam: $first $button $second", button.top < first.bottom + 1f && button.bottom > second.top - 1f)
        compose.onNodeWithContentDescription("Start und Ziel tauschen").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Bochum Hbf", start); assertEquals("Essen Hbf", destination); assertFalse(motion.running) }
        assertEquals(first, compose.onNodeWithTag("first").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Bochum Hbf").assertIsDisplayed()
    }

    /** Both directions of a line share one frame; a tap searches that direction. */
    @Test fun theRouteListShowsBothDirectionsInOneFrame() {
        val home = Favorite("h", "Zuhause", stop("Essen Hbf", "e", 7.0)); val work = Favorite("w", "Arbeit", stop("Bochum Hbf", "b", 7.22))
        val lonely = Favorite("x", "Oma", stop("Mülheim Hbf", "m", 6.88))
        val store = StoredState(favorites = listOf(home, work, lonely), network = TransitNetwork(
            listOf(NetworkNode("h", GridPoint(0, 0)), NetworkNode("w", GridPoint(3, 0)), NetworkNode("x", GridPoint(0, 3))),
            listOf(NetworkEdge("l", "h", "w", DirectionMode.BOTH))))
        var searched: String? = null; var single: String? = null
        content { NetworkRouteList(store, { searched = "${it.from.label}→${it.to.label}" }, { single = it.label }) }
        compose.onNodeWithTag("network-route-group-l").onChildren().assertCountEquals(2)
        compose.onNodeWithTag("network-route-l:h", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("network-route-l:w", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("Arbeit→Zuhause", searched) }
        compose.onNodeWithTag("network-list-favorite-x", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals("Oma", single) }
    }

    /** The settings search is one slim line without the former shortcut buttons. */
    @Test fun theSettingsSearchIsOneSlimLine() {
        content { Column { SettingsSearch {} } }
        compose.onNodeWithTag("settings-shortcut-TYPOGRAPHY").assertDoesNotExist()
        compose.onNodeWithText("Einstellungen durchsuchen").assertIsDisplayed()
        val field = compose.onNodeWithTag("settings-search-field").fetchSemanticsNode().boundsInRoot
        assertTrue("slim: ${field.height}", field.height <= 56f)
        compose.onNodeWithTag("settings-search-field").performTextInput("Planer")
        compose.onNodeWithText("Planermodus").assertIsDisplayed()
    }

    /** With "Designstandard" no font group opens by itself; the group of a chosen font does. */
    @Test fun theFontPickerOpensOnlyTheGroupOfTheChosenFont() {
        val overlay = AppOverlayState()
        var selected by mutableStateOf<String?>(null)
        content { CompositionLocalProvider(LocalAppOverlay provides overlay) {
            AppOverlayHost(overlay, Modifier.fillMaxSize()) { key(selected) { FontPickerSheet(selected, { selected = it }, {}) } } } }
        compose.onNodeWithTag("font-default").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onAllNodes(SemanticsMatcher("font group") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("font-group-") == true })
            .fetchSemanticsNodes().forEach { node ->
                assertNotEquals(node.config.getOrNull(SemanticsProperties.TestTag), "Ausgeklappt", node.config.getOrNull(SemanticsProperties.StateDescription))
            }
        compose.onNodeWithTag("font-inter").assertDoesNotExist()
        compose.runOnIdle { selected = "inter" }
        compose.onNodeWithTag("font-group-SANS").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ausgeklappt"))
    }
}
