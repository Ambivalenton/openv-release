// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.breaksOnlyBetweenWords
import dev.ambivalenton.openv.ui.screens.PinnedTimelineStopRow
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Build 124 · screenshot: "Westfalenpar|k", "DO-Wischling|en S", "Markgrafenstr|aße" in the pinned timeline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build124StopNameTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun longNamesShrinkFirstAndNeverBreakInsideAWord() {
        val names = listOf("Westfalenpark", "DO-Wischlingen S", "Markgrafenstraße", "DO-Nette/Oestrich S", "Recklinghausen-Hochlarmark Kirche")
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.3f)) {
                OpenVTheme(AppSettings()) { Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.width(340.dp)) {
                        names.forEachIndexed { i, name ->
                            PinnedTimelineStopRow(JourneyFocusStop("s$i", Location(name, "id$i", 51.5, 7.4, "VRR", LocationKind.STOP), 0L, 0L, "U49", null,
                                lineType = LineType.SUBWAY), Color.Magenta, false, { 0f }, i == 0, i == names.lastIndex, false, false)
                        }
                    }
                } }
            }
        }
        compose.waitForIdle()
        names.indices.forEach { i ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("focus-label-s$i").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            val layout = layouts.single()
            assertTrue("${names[i]}: ${layout.lineCount} lines", layout.lineCount <= 2)
            assertTrue("${names[i]} broken inside a word", breaksOnlyBetweenWords(layout))
            assertFalse("${names[i]} cut", layout.hasVisualOverflow)
        }
        // A single long word stays on one line (a little smaller).
        val single = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("focus-label-s0").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(single)
        assertEquals(1, single.single().lineCount)
    }
}
