// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.onboarding.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Build 121: importing a backup is offered on the first view only; inside the setup "Zurück" and "Weiter"
 * stay in reach – also with a large font, where "Weiter" was pushed off the row before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build82OnboardingImportUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun theFirstViewOpensTheImportSheetTheStepsDoNot() {
        compose.setContent {FirstLaunchIntro({},{})}
        compose.onNodeWithTag("intro-import").assertIsDisplayed().performClick();compose.waitForIdle()
        compose.onNodeWithTag("onboarding-import-sheet").assertExists()
        compose.onNodeWithTag("onboarding-import-pick").assertIsDisplayed()
        compose.onNode(hasText("Sicherung importieren") and hasAnyAncestor(hasTestTag("onboarding-import-sheet"))).assertIsDisplayed()
        compose.onNodeWithTag("onboarding-import-close").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("onboarding-import-sheet").assertDoesNotExist()
    }

    @Test fun nextStaysInReachOnEveryStepWithALargeFont() {
        var shown by mutableStateOf(OnboardingStage.PERMISSIONS)
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1.6f)) {
            OnboardingWizard(StoredState(onboardingDraft=OnboardingDraft(stage=shown)),SetupPermissions(),{true},{true},{emptyList()},onBackToIntro={})
        }}
        for(stage in OnboardingStage.entries) {
            compose.runOnIdle {shown=stage}
            compose.waitForIdle()
            compose.onNodeWithTag("onboarding-import").assertDoesNotExist()
            val next=compose.onNodeWithTag("onboarding-next").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val width=compose.activity.resources.displayMetrics.widthPixels
            assert(next.right<=width+1 && next.left>=0f) {"$stage: Weiter lies inside the screen"}
            val back=compose.onNodeWithTag("onboarding-back").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assert(back.right<=next.left) {"$stage: Zurück and Weiter do not overlap"}
            compose.onAllNodesWithTag("onboarding-skip").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.let {skip->
                assert(skip.right<=next.left && skip.left>=back.right) {"$stage: Überspringen sits between them"}
            }
        }
    }
}
