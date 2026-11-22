// SPDX-FileCopyrightText: 2026 Ambivalenton
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
import dev.ambivalenton.openv.ui.network.NetworkEditBar
import dev.ambivalenton.openv.ui.screens.ConnectionSearchSettings
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** The preference sliders with speech bubbles and the floating add button. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w380dp-h1600dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class Build97UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun content(settings: AppSettings = AppSettings(quietMode = true), body: @Composable () -> Unit) =
        compose.setContent { OpenVTheme(settings) { Surface(Modifier.fillMaxSize()) { Column(Modifier.verticalScroll(rememberScrollState())) { body() } } } }

    /** Every preference is a slider whose bubble names the stop; the text below explains it. */
    @Test fun thePreferencesAreSlidersWithSpeechBubbles() {
        var settings by mutableStateOf(AppSettings(quietMode = true))
        content(settings) { ConnectionSearchSettings(settings, { settings = it(settings) }) }
        for (tag in listOf("pref-priority", "pref-transfers", "pref-walking", "pref-buffer", "pref-tolerance"))
            compose.onNodeWithTag("$tag-slider").performScrollTo().assertExists()
        compose.onNodeWithTag("pref-transfers-value").assertTextEquals("Normal")
        compose.onNodeWithTag("pref-transfers-description").assertTextEquals("Ein Umstieg zählt wie etwa 5 Minuten Fahrt.")
        compose.onNodeWithTag("pref-transfers-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.waitForIdle()
        compose.onNodeWithTag("pref-transfers-value").assertTextEquals("Möglichst wenig")
        compose.runOnIdle { assertEquals(TransferPreference.FEWEST, settings.overview.routingPreferences.transfers) }
        compose.onNodeWithTag("pref-buffer-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        compose.runOnIdle { assertEquals(TransferBuffer.SAFE, settings.overview.routingPreferences.buffer) }
        compose.onNodeWithTag("pref-tolerance-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        compose.runOnIdle { assertEquals(25, settings.overview.routingPreferences.tolerance) }
        // No hard limits any more: the former chips are gone.
        compose.onAllNodesWithText("Nur direkt").assertCountEquals(0)
    }

    /** The add button sits centred in the rounded end of its box and is not part of the box's clip. */
    @Test fun theAddButtonFloatsAboveItsBox() {
        content { Box(Modifier.fillMaxWidth()) { NetworkEditBar(true, {}, {}, {}, {}, Modifier.fillMaxWidth()) } }
        for (tool in listOf("network-add", "network-settings")) {
            val box = compose.onNodeWithTag("$tool-box").fetchSemanticsNode().boundsInRoot
            val button = compose.onNodeWithTag("$tool-button", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val end = box.left + box.height / 2
            assertTrue("$tool centred in the round end: $button in $box", abs(button.center.x - end) <= 1.5f)
            assertTrue("$tool vertically centred", abs(button.center.y - box.center.y) <= 1.5f)
        }
    }
}
