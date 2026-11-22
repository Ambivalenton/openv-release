// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.onboarding.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Importieren" sits left of "Weiter" on step 1 only and opens a sheet with handle and X. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build82OnboardingImportUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun importButtonOpensAClosableSheetOnTheFirstStepOnly() {
        compose.setContent {OnboardingWizard(StoredState(),SetupPermissions(),{true},{true},{emptyList()})}
        val import=compose.onNodeWithTag("onboarding-import",useUnmergedTree=true).assertIsDisplayed()
        val left=import.fetchSemanticsNode().boundsInRoot
        val next=compose.onNodeWithTag("onboarding-next").fetchSemanticsNode().boundsInRoot
        assert(left.right<=next.left && left.center.y in next.top..next.bottom) {"import sits left of Weiter in the same row"}
        compose.onNodeWithTag("onboarding-skip").assertIsDisplayed()
        import.performClick();compose.waitForIdle()
        compose.onNodeWithTag("onboarding-import-sheet").assertExists()
        compose.onNodeWithTag("onboarding-import-pick").assertIsDisplayed()
        compose.onNode(hasText("Sicherung importieren") and hasAnyAncestor(hasTestTag("onboarding-import-sheet"))).assertIsDisplayed()
        compose.onNodeWithTag("onboarding-import-close").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("onboarding-import-sheet").assertDoesNotExist()
    }

    @Test fun laterStepsKeepOnlyBackAndLaterSetup() {
        compose.setContent {OnboardingWizard(StoredState(onboardingDraft=OnboardingDraft(step=1)),SetupPermissions(),{true},{true},{emptyList()})}
        compose.onNodeWithTag("onboarding-import").assertDoesNotExist()
        compose.onNodeWithText("Zurück").assertExists()
        compose.onNodeWithTag("onboarding-skip").assertExists()
    }
}
