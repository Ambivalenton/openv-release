// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.ScheduleControls
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Build 121 · screen video: at a large system font "Abfahrt | Ankunft" sat exactly at its limit beside the
 * always reserved room of the "Jetzt" area and shrank in one jump when a page landed. The room is now only
 * kept while the area is out; closed, the pill has its natural size whatever the width around it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build121ScheduleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun theClosedPillKeepsItsNaturalSizeAtALargeFont() {
        var time by mutableStateOf<Long?>(null)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.4f)) {
                OpenVTheme(AppSettings(appTheme = AppTheme.SYNTHWAVE)) {
                    Column {
                        Box(Modifier.width(352.dp).testTag("narrow")) { ScheduleControls(time, {}, TimeMode.DEPARTURE, {}, {}) }
                        Box(Modifier.width(600.dp).testTag("wide")) { ScheduleControls(null, {}, TimeMode.DEPARTURE, {}, {}) }
                    }
                }
            }
        }
        compose.waitForIdle()
        fun pill(box: String) = compose.onAllNodes(hasTestTag("time-mode-pill") and hasAnyAncestor(hasTestTag(box)), useUnmergedTree = true)
            .fetchSemanticsNodes().single().size.width
        assertEquals("closed: natural size, as with all the room in the world", pill("wide"), pill("narrow"))
        // A chosen time opens the area: the pill makes room, never wider than before.
        compose.runOnIdle { time = System.currentTimeMillis() + 3_600_000 }
        compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
        assertTrue(pill("narrow") <= pill("wide"))
    }
}
