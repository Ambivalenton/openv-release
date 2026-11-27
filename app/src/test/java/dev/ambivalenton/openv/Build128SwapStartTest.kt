// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.SearchScreen
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * Build 128 · after swapping start and destination, "Verbindung suchen" compared the new destination with
 * the start from before the swap ("Start und Ziel sind gleich"), and a start chosen afterwards was not used:
 * the search button kept the handler of an earlier composition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build128SwapStartTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val reck = Location("Recklinghausen Hbf", "de:05562:4800", 51.6144, 7.1979, "VRR", LocationKind.STOP)
    private val herne = Location("Herne Bf", "de:05916:1", 51.5432, 7.2193, "VRR", LocationKind.STOP)
    private val dortmund = Location("Dortmund Hbf", "de:05913:10", 51.5177, 7.4590, "VRR", LocationKind.STOP)
    private fun places(q: String) = listOf(reck, herne, dortmund).filter { it.name.startsWith(q.trim(), ignoreCase = true) }
    private val manualInput get() = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("origin-manual")))

    private fun start(): Pair<AppViewModel, MutableList<Location>> {
        val gateway = PlaceSearchGateway({ q -> places(q) }, { q -> flowOf(places(q)) }, true)
        val vm = (compose.activity.application as OpenVApp).koin.get<AppViewModel>()
        vm.settings { it.copy(quietMode = false, originMode = OriginMode.MANUAL, manualOrigin = reck) }
        val searched = mutableListOf<Location>()
        compose.setContent {
            val ui by vm.state.collectAsState()
            OpenVTheme(ui.store.settings) { CompositionLocalProvider(LocalPlaceSearchGateway provides gateway) {
                SearchScreen(ui, vm, { target, _, _ -> searched += target }, { _, _, _ -> }, {})
            } }
        }
        awaitOrigin(vm, reck)
        return vm to searched
    }

    /** The store writes on its own thread; the main looper must run for the view model to see it. */
    private fun awaitOrigin(vm: AppViewModel, expected: Location) {
        val deadline = System.currentTimeMillis() + 10_000
        while (vm.state.value.store.settings.manualOrigin != expected) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(16)
            assertTrue("start is ${vm.state.value.store.settings.manualOrigin?.name}, not ${expected.name}", System.currentTimeMillis() < deadline)
            Thread.sleep(10)
        }
        compose.waitForIdle()
    }

    private fun chooseDestination(name: String) {
        compose.onNode(hasSetTextAction() and hasText("Ziel, Adresse oder Haltestelle")).performTextInput(name.take(5))
        compose.waitUntil(5_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(name).onFirst().performClick(); compose.waitForIdle()
    }

    private fun swap(vm: AppViewModel, expected: Location) {
        compose.onNodeWithTag("swap-places").performClick()
        awaitOrigin(vm, expected)
        compose.mainClock.advanceTimeBy(3_000); compose.waitForIdle()
    }

    @Test fun theSearchUsesTheStartOnScreenAfterASwap() {
        val (vm, searched) = start()
        chooseDestination("Herne Bf")
        swap(vm, herne)
        compose.onNodeWithTag("search-submit").performClick(); compose.waitForIdle()
        compose.onAllNodesWithTag("same-start-hint").assertCountEquals(0)
        assertEquals("Herne → Recklinghausen, not the old start", listOf(reck), searched)
    }

    @Test fun aStartChosenAfterTheSwapIsTakenAndUsed() {
        val (vm, searched) = start()
        chooseDestination("Herne Bf")
        swap(vm, herne)
        compose.onNodeWithText("Herne Bf").assertExists()
        compose.onNodeWithTag("origin-morph").performClick(); compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle()
        manualInput.performTextClearance()
        manualInput.performTextInput("Dort")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Dortmund Hbf").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Dortmund Hbf").onFirst().performClick()
        awaitOrigin(vm, dortmund)
        compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
        compose.onNodeWithText("Dortmund Hbf").assertExists()
        compose.onNodeWithTag("search-submit").performClick(); compose.waitForIdle()
        compose.onAllNodesWithTag("same-start-hint").assertCountEquals(0)
        assertEquals(listOf(reck), searched)
    }
}
