// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.DirectModalSheet
import dev.ambivalenton.openv.ui.components.DirectSheetOffsetKey
import dev.ambivalenton.openv.ui.components.SharedDepartureBoard
import dev.ambivalenton.openv.ui.network.NetworkEditBar
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Build 102 · a sheet that slid in stays when the keyboard opens; the plan keeps its place when editing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w400dp-h880dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build102SheetAndPlanTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** The keyboard makes the window shorter. Before, the sheet then slid in a second time. */
    @Test fun aSheetDoesNotSlideInAgainWhenTheKeyboardOpens() {
        var height by mutableStateOf(880.dp)
        compose.setContent {
            OpenVTheme(AppSettings()) {
                Box(Modifier.fillMaxWidth().height(height)) {
                    DirectModalSheet({}, null, "probe-sheet") { _, _ -> Text("Inhalt") }
                }
            }
        }
        compose.waitForIdle()
        fun offset() = compose.onNodeWithTag("probe-sheet").fetchSemanticsNode().config.getOrNull(DirectSheetOffsetKey)!!
        fun top() = compose.onNodeWithTag("probe-sheet").fetchSemanticsNode().boundsInRoot.top
        assertEquals(0f, offset(), .5f)
        val restingTop = top()
        compose.mainClock.autoAdvance = false
        height = 560.dp
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("the sheet stays where it is", 0f, offset(), .5f)
        assertEquals("its upper edge does not move", restingTop, top(), 1f)
        repeat(30) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(0f, offset(), .5f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun theEditBarKeepsItsHeightAndThePlusIsATenthSmaller() {
        var editing by mutableStateOf(false)
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true)) { Surface { Column {
                NetworkEditBar(editing, {}, {}, {}, {}, Modifier.testTag("bar"))
                Box(Modifier.fillMaxWidth().height(10.dp).testTag("below"))
            } } }
        }
        fun below() = compose.onNodeWithTag("below").fetchSemanticsNode().boundsInRoot.top
        val closed = below()
        editing = true; compose.waitForIdle()
        assertEquals("the plan below stays put", closed, below(), .5f)
        val plus = compose.onNodeWithTag("network-add-button").fetchSemanticsNode().size.width
        val gear = compose.onNodeWithTag("network-settings-button").fetchSemanticsNode().size.width
        assertEquals(.9f, plus.toFloat() / gear, .02f)
    }

    @Test fun theBoardDoesNotRepeatTheSourceOfTheScreenTitle() {
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface { SharedDepartureBoard(StoredState(), {}, {}) } } }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("transit-source-vrr", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
