// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Activity
import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.PinnedBubbleSetting
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** Acceptance group D (bubble): overlay access granted, denied, revoked and the switch state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="de-rDE-w411dp-h891dp-420dpi")
class Build65BubbleSettingTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val title="Schwebende Reisekarte"
    private var settings by mutableStateOf(AppSettings())

    private fun show() = compose.setContent {OpenVTheme {Surface {PinnedBubbleSetting(settings) {change->settings=change(settings)}}}}

    @Test fun withAccessTheSwitchTurnsOnAndOffDirectly() {
        ShadowSettings.setCanDrawOverlays(true);show()
        compose.onNodeWithText(title).assertIsOff().performClick()
        compose.runOnIdle {assertTrue(settings.pinnedBubble)}
        compose.onNodeWithText(title).assertIsOn().performClick()
        compose.runOnIdle {assertFalse(settings.pinnedBubble)}
        assertNull("the system screen was not needed",shadowOf(compose.activity).peekNextStartedActivityForResult())
    }

    @Test fun withoutAccessTheSystemScreenOpensAndADenialLeavesTheSwitchOffWithAReason() {
        ShadowSettings.setCanDrawOverlays(false);show()
        compose.onNodeWithText(title).performClick()
        val request=shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,request.intent.action)
        assertEquals("package:${compose.activity.packageName}",request.intent.dataString)
        compose.runOnIdle {assertFalse("nothing is switched on before access exists",settings.pinnedBubble)}
        shadowOf(compose.activity).receiveResult(request.intent,Activity.RESULT_CANCELED,null)
        compose.waitForIdle()
        compose.runOnIdle {assertFalse(settings.pinnedBubble)}
        compose.onNodeWithText(title).assertIsOff()
        compose.onNodeWithTag("pinned-bubble-hint",useUnmergedTree=true).assertExists()
    }

    @Test fun grantingAccessInTheSystemScreenSwitchesTheBubbleOn() {
        ShadowSettings.setCanDrawOverlays(false);show()
        compose.onNodeWithText(title).performClick()
        val request=shadowOf(compose.activity).nextStartedActivityForResult
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(compose.activity).receiveResult(request.intent,Activity.RESULT_OK,null)
        compose.waitForIdle()
        compose.runOnIdle {assertTrue(settings.pinnedBubble)}
        compose.onNodeWithText(title).assertIsOn()
        compose.onNodeWithTag("pinned-bubble-hint",useUnmergedTree=true).assertDoesNotExist()
    }

    @Test fun aMissingSystemScreenIsExplainedInsteadOfLeavingAMisleadingSwitch() {
        ShadowSettings.setCanDrawOverlays(false)
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true) // no settings app resolves
        show()
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("Die Einstellung für Overlays konnte nicht geöffnet werden.").assertExists()
        compose.onNodeWithText(title).assertIsOff()
        compose.runOnIdle {assertFalse(settings.pinnedBubble)}
    }

    @Test fun revokedAccessShowsTheSwitchOffAndExplainsIt() {
        settings=AppSettings(pinnedBubble=true)
        ShadowSettings.setCanDrawOverlays(false);show()
        compose.onNodeWithText(title).assertIsOff()
        compose.onNodeWithText("Die Erlaubnis für die schwebende Reisekarte wurde entzogen. Schalte sie erneut ein, um sie zu erteilen.").assertExists()
    }

    @Test fun anActiveBubbleStandsInForThePinnedCardInTheOverview() {
        val a=Location("Herne Bahnhof","a",51.54,7.23,"Test");val b=Location("Bochum Hbf","b",51.48,7.22,"Test")
        val start=System.currentTimeMillis()+300_000
        val trip=Trip("t",listOf(TripSegment("leg",a,b,start,start+1_200_000,LineType.BUS,"312")),"Test",start-60_000)
        val session=PinnedJourneySession(favorite=Favorite(label="Arbeit",location=b),query=JourneyQuery(a,b,start),initialTrip=trip,
            routing=AppSettings(),minimized=true)
        var asBubble by mutableStateOf(true)
        compose.setContent {OpenVTheme {Surface {dev.ambivalenton.openv.ui.screens.OverviewScreen(
            dev.ambivalenton.openv.ui.OverviewUiState(ready=true),OverviewSettings(),{},{},{},
            pinnedState=PinnedJourneyState(session=session),pinnedAsBubble=asBubble)}}}
        compose.onNodeWithTag("pinned-journey-card").assertDoesNotExist()
        compose.runOnIdle {asBubble=false} // the bubble was closed via the drop zone
        compose.onNodeWithTag("pinned-journey-card").assertExists()
    }
}
