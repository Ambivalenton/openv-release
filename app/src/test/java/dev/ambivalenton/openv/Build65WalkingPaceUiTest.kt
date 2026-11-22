// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.AppSettings
import dev.ambivalenton.openv.data.model.WalkingSpeed
import dev.ambivalenton.openv.ui.components.AppModesSettings
import dev.ambivalenton.openv.ui.components.TimelineWalkingSettings
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The walking-pace learning is gone; the walking card keeps the pace and stays readable with large fonts. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-420dpi",fontScale=2.0f)
class Build65WalkingPaceUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun theWalkingCardHasThePaceAndNoLearning() {
        var settings by mutableStateOf(AppSettings(walkingSpeed=WalkingSpeed.RELAXED))
        compose.setContent {OpenVTheme {Surface {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                TimelineWalkingSettings(settings) {change->settings=change(settings)}
            }
        }}}
        compose.onNodeWithTag("walking-pace-slider").assertExists()
        compose.onAllNodesWithText("Gehtempo lokal lernen").assertCountEquals(0)
        compose.onAllNodesWithTag("walking-pace-apply").assertCountEquals(0)
        compose.onNodeWithText("Gehzeiten bei der Suche auslassen").performScrollTo().assertIsDisplayed()
    }

    @Test fun modesAndExtrasNoLongerContainTheWalkingPace() {
        compose.setContent {OpenVTheme {Surface {AppModesSettings(AppSettings(),{})}}}
        compose.onNodeWithText("Stromsparmodus").assertExists()
        compose.onAllNodesWithTag("walking-pace-learning",useUnmergedTree=true).assertCountEquals(0)
    }
}
