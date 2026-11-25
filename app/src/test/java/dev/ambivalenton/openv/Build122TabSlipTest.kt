// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.AppSettings
import dev.ambivalenton.openv.ui.components.ExpressivePillNavigationTo
import dev.ambivalenton.openv.ui.components.PillDestination
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Build 122 · "Suchen" did not answer now and then: a tap whose finger slid a few pixels sideways counted as
 * a tiny drag of the capsule, which sprang back. Such a tap now opens the tab it went down on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
class Build122TabSlipTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun aTapThatSlidesALittleStillOpensTheTab() {
        val chosen = mutableListOf<Int>()
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
            val pager = rememberPagerState { 3 }
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                Box(Modifier.testTag("pill-host")) {
                    ExpressivePillNavigationTo(pager, listOf(PillDestination("Übersicht", R.drawable.ic_overview),
                        PillDestination("Suchen", R.drawable.ic_search), PillDestination("Abfahrten", R.drawable.ic_train)), null, null) { chosen += it }
                }
            }
        } } }
        compose.waitForIdle()
        // Down on "Suchen", a slip of about a tenth of a tab to the right (past the touch slop), up.
        compose.onNodeWithTag("pill-host").performTouchInput {
            val start = Offset(width * .48f, height / 2f)
            down(start); moveTo(start + Offset(width * .02f, 0f)); moveTo(start + Offset(width * .045f, 0f)); up()
        }
        compose.waitForIdle()
        assertEquals("the slipped tap opens Suchen", listOf(1), chosen)
        // A real drag still walks the capsule: from Übersicht's side to Abfahrten's.
        compose.onNodeWithTag("pill-host").performTouchInput { swipeRight(startX = width * .15f, endX = width * .85f, durationMillis = 500) }
        compose.waitForIdle()
        assertEquals(2, chosen.last())
    }
}
