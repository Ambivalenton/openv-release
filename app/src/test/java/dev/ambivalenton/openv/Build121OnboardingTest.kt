// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.onboarding.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Build 121 · the setup: start place without location access, the modes with their pictures, setup first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build121OnboardingTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val essen=Location("Essen Hbf","e",51.451,7.014,"VRR",LocationKind.STOP)
    private fun capture(name:String)=compose.runOnIdle {
        val view=android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull {it.isShown && it.width>0} ?: compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val file=File("../audit/build121/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }

    @Test fun theStagesFollowTheLocationAccess() {
        assertEquals(OnboardingStage.entries,onboardingStages(locationGranted=false))
        assertFalse(OnboardingStage.LOCATION in onboardingStages(locationGranted=true))
        // A draft from before build 121 keeps its place.
        assertEquals(OnboardingStage.FAVORITE,OnboardingDraft(step=1).currentStage)
        assertEquals(OnboardingStage.DESIGN,OnboardingDraft(step=2).currentStage)
        // Granted while on the start place: the next stage takes over.
        val granted=onboardingStages(true)
        assertEquals(OnboardingStage.FAVORITE,OnboardingDraft(stage=OnboardingStage.LOCATION).shownStage(granted))
        assertEquals(OnboardingStage.FAVORITE,OnboardingDraft(stage=OnboardingStage.PERMISSIONS).moved(granted,1).stage)
        assertEquals(OnboardingStage.LOCATION,OnboardingDraft(stage=OnboardingStage.PERMISSIONS).moved(onboardingStages(false),1).stage)
        assertEquals(OnboardingStage.MODES,OnboardingDraft(stage=OnboardingStage.DESIGN).moved(granted,-1).stage)
    }

    @Test fun theProgressIsAJourneyWithATrainBetweenColouredStops() {
        val stops=onboardingStopColors(androidx.compose.ui.graphics.Color(0xFF6750A4),5)
        assertEquals(5,stops.size);assertEquals(5,stops.distinct().size)
        var index by mutableStateOf(0)
        compose.setContent {dev.ambivalenton.openv.ui.theme.OpenVTheme(AppSettings()) {
            androidx.compose.material3.Surface {OnboardingProgress(5,index,Modifier.fillMaxWidth().padding(16.dp))}
        }}
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.runOnIdle {index=2}
        compose.mainClock.advanceTimeBy(450)
        capture("progress-riding")
        compose.mainClock.advanceTimeBy(4_000)
        capture("progress-arrived")
        compose.mainClock.autoAdvance=true
    }

    @Test fun theFirstViewPutsTheSetupFirst() {
        compose.setContent {FirstLaunchIntro({},{})}
        val setup=compose.onNodeWithTag("intro-setup").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val search=compose.onNodeWithTag("intro-search").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("setup above the way out",setup.bottom<=search.top)
        assertTrue("the setup is the larger button",setup.height>=search.height)
        compose.onNodeWithTag("intro-journey").assertExists()
        capture("intro")
    }

    @Test fun withoutLocationAStartPlaceIsChosenAndCanBeSkipped() {
        var store by mutableStateOf(StoredState(onboardingDraft=OnboardingDraft(stage=OnboardingStage.PERMISSIONS,settings=AppSettings(quietMode=true))))
        compose.setContent {OnboardingWizard(store,SetupPermissions(),{change->store=store.copy(onboardingDraft=change(store.onboardingDraft!!));true},{true},{listOf(essen)})}
        compose.onNodeWithText("Ohne Standort wählst du im nächsten Schritt",substring=true).assertExists()
        capture("permissions")
        // "Weiter" works on the permissions: both are optional.
        compose.onNodeWithTag("onboarding-next").assertIsEnabled().performClick()
        compose.onNodeWithText("Dein Startpunkt").assertExists()
        compose.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        compose.onNodeWithTag("onboarding-skip").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("onboarding-start-search"))).performTextInput("Essen")
        compose.mainClock.advanceTimeBy(400);compose.waitForIdle()
        compose.onNodeWithText("Essen Hbf").performClick();compose.waitForIdle()
        compose.onNodeWithTag("onboarding-start-chosen").assertExists()
        compose.runOnIdle {
            val settings=store.onboardingDraft!!.settings
            assertEquals(OriginMode.MANUAL,settings.originMode);assertEquals(essen,settings.manualOrigin)
        }
        compose.onNodeWithTag("onboarding-skip").assertDoesNotExist()
        capture("start-place")
        compose.onNodeWithTag("onboarding-next").assertIsEnabled().performClick()
        compose.onNodeWithText("Dein erster Favorit").assertExists()
        // The finished setup keeps the start place.
        compose.runOnIdle {assertEquals(essen,store.finishFirstLaunchSetup().settings.manualOrigin)}
    }

    @Test fun withLocationTheStartPlaceIsLeftOut() {
        var store by mutableStateOf(StoredState(onboardingDraft=OnboardingDraft(stage=OnboardingStage.PERMISSIONS,settings=AppSettings(quietMode=true))))
        compose.setContent {OnboardingWizard(store,SetupPermissions(location=SetupLocationAccess.PRECISE),
            {change->store=store.copy(onboardingDraft=change(store.onboardingDraft!!));true},{true},{emptyList()})}
        compose.onNodeWithText("Erlaubt").assertExists()
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithText("Dein erster Favorit").assertExists()
    }

    @Test fun setupOffersGlassAndRestoresSystemBrightnessInBothDirections() {
        val draft = OnboardingDraft(stage = OnboardingStage.DESIGN, settings = AppSettings(themeMode = ThemeMode.DARK))
        var store by mutableStateOf(StoredState(onboardingDraft = draft))
        compose.setContent { OnboardingWizard(store, SetupPermissions(), { change ->
            store = store.copy(onboardingDraft = change(store.onboardingDraft!!))
            true
        }, { true }, { emptyList() }) }
        compose.onNodeWithTag("onboarding-design-CLASSIC_DARK").assertDoesNotExist()
        compose.onNodeWithTag("onboarding-design-SEA_GLASS").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AppTheme.SEA_GLASS, store.onboardingDraft!!.settings.appTheme) }
        compose.onNodeWithTag("onboarding-design-STANDARD").performScrollTo().performClick()
        compose.runOnIdle {
            val settings = store.finishFirstLaunchSetup().settings
            assertNull(settings.appTheme)
            assertEquals(ThemeMode.SYSTEM, settings.themeMode)
            assertFalse(settings.appearanceIsDark(systemDark = false))
            assertTrue(settings.appearanceIsDark(systemDark = true))
            assertNull(settings.copy(quietMode = true).normalizedQuietAppearance().appTheme)
        }
    }

    @Test fun theModesSwitchAndShowTheirEffectAtOnce() {
        var store by mutableStateOf(StoredState(onboardingDraft=OnboardingDraft(stage=OnboardingStage.MODES)))
        compose.setContent {OnboardingWizard(store,SetupPermissions(),{change->store=store.copy(onboardingDraft=change(store.onboardingDraft!!));true},{true},{emptyList()})}
        compose.onNodeWithText("Deine Modi").assertExists()
        listOf("mode-monochrome","onboarding-mode-minimal","onboarding-mode-quiet","onboarding-mode-planner").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
        }
        capture("modes")
        compose.onNodeWithTag("onboarding-mode-minimal").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-mode-planner").performScrollTo().performClick()
        compose.onNodeWithTag("mode-monochrome").performScrollTo().performClick()
        compose.runOnIdle {
            val s=store.onboardingDraft!!.settings
            assertTrue(s.minimalistMode);assertTrue(s.plannerMode);assertTrue(s.monochrome);assertFalse(s.quietMode)
        }
        capture("modes-on")
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithText("Dein Design").assertExists()
        compose.onNodeWithTag("onboarding-next").assertTextEquals("Los geht’s")
    }
}
