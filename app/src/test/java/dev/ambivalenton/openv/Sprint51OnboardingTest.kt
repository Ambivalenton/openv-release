// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.onboarding.*
import dev.ambivalenton.openv.ui.theme.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sprint51OnboardingTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val place=Location("Wohnung am Park",latitude=51.5,longitude=7.2)
    private fun capture(name:String)=compose.runOnIdle {
        val view=WindowInspector.getGlobalWindowViews().lastOrNull {it.isShown} ?: compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val file=File("../audit/build64/images/onboarding/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    private fun choosePlace() {
        compose.onNode(hasSetTextAction() and hasText("Adresse oder Haltestelle")).performScrollTo().performTextInput("Wohn")
        compose.mainClock.advanceTimeBy(350);compose.waitForIdle()
        compose.onNodeWithText(place.name).performScrollTo().performClick()
    }
    @Test fun nextAloneSavesASelectedAddressAndAdvancesWithoutAnExtraSaveButton() {
        var store by mutableStateOf(StoredState(onboardingDraft=OnboardingDraft(step=2,settings=AppSettings(quietMode=true))))
        compose.setContent {OnboardingWizard(store,SetupPermissions(),{change->store=store.copy(onboardingDraft=change(store.onboardingDraft!!));true},{true},{listOf(place)})}
        compose.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        compose.onNodeWithTag("onboarding-add-favorite").assertDoesNotExist()
        choosePlace();compose.onNodeWithTag("onboarding-next").assertIsEnabled()
        compose.runOnIdle {assertTrue(store.onboardingDraft!!.favorites.isEmpty())}
        capture("onboarding-favorite-next")
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithText("Deine Übersicht").assertExists()
        compose.runOnIdle {
            assertEquals(3,store.onboardingDraft!!.step)
            assertEquals(place,store.onboardingDraft!!.favorites.single().location)
            assertEquals(place.name,store.onboardingDraft!!.favorites.single().label)
            assertFalse(store.onboardingDraft!!.offerMoreFavorites)
        }
        compose.onNodeWithText("Favorit speichern").assertDoesNotExist()
    }
    @Test fun nextPreservesTheChosenIconAndEditedNameAndAFailedWriteDoesNotAdvance() {
        var accept by mutableStateOf(false)
        var store by mutableStateOf(StoredState(onboardingDraft=OnboardingDraft(step=2,settings=AppSettings(quietMode=true))))
        compose.setContent {OnboardingWizard(store,SetupPermissions(),{change->if(accept){store=store.copy(onboardingDraft=change(store.onboardingDraft!!));true}else false},{true},{listOf(place)})}
        choosePlace()
        compose.onNodeWithTag("onboarding-icon-HOME").performScrollTo().performClick()
        compose.onNodeWithTag("onboarding-favorite-name").performScrollTo().performTextReplacement("Mein Ruhepol")
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithText("Die Auswahl konnte nicht gespeichert werden. Bitte erneut versuchen.").assertExists()
        compose.runOnIdle {assertEquals(2,store.onboardingDraft!!.step);assertTrue(store.onboardingDraft!!.favorites.isEmpty());accept=true}
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.runOnIdle {assertEquals("Mein Ruhepol",store.onboardingDraft!!.favorites.single().label);assertEquals(FavoriteIcon.HOME,store.onboardingDraft!!.favorites.single().icon)}
    }
    @Test fun skipAdvancesPastTheFavoriteWithoutFinishingOrOverwritingOtherSettings() {
        var finishes=0;val settings=AppSettings(appTheme=AppTheme.IRIS_PAPER,quietMode=true)
        var store by mutableStateOf(StoredState(onboardingDraft=OnboardingDraft(step=2,settings=settings)))
        compose.setContent {OnboardingWizard(store,SetupPermissions(),{change->store=store.copy(onboardingDraft=change(store.onboardingDraft!!));true},{finishes++;true},{emptyList()})}
        compose.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        compose.onNodeWithTag("onboarding-skip").performClick()
        compose.runOnIdle {assertEquals(3,store.onboardingDraft!!.step);assertTrue(store.onboardingDraft!!.favorites.isEmpty());assertEquals(settings,store.onboardingDraft!!.settings);assertEquals(0,finishes)}
    }
    @Test fun eachPresetFitsItsWholeExampleInsideTheSameFrameWithoutScrolling() {
        var preset by mutableStateOf(SentencePreset.COMPACT);var calls=0
        compose.setContent {val settings=AppSettings(quietMode=true,overview=OverviewSettings().withSentencePreset(preset))
            OpenVTheme(settings){Box(Modifier.width(320.dp)){OnboardingOverviewPreview(null,settings,SetupPermissions(),{_,_->calls++;null})}}}
        val texts=mutableListOf<String>();val sizes=mutableListOf<Float>();val heights=mutableListOf<Float>()
        for(choice in SentencePreset.entries) {
            compose.runOnIdle {preset=choice}
            val layouts=mutableListOf<TextLayoutResult>()
            val node=compose.onNodeWithTag("onboarding-overview-text")
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            val layout=layouts.single();assertFalse(choice.name,layout.hasVisualOverflow)
            assertTrue(layout.layoutInput.text.text.contains("Mordor"));texts+=layout.layoutInput.text.text
            sizes+=layout.layoutInput.style.fontSize.value;heights+=node.fetchSemanticsNode().boundsInRoot.height
            capture("onboarding-preview-${choice.name.lowercase()}")
        }
        assertEquals(0,calls);assertEquals(3,texts.distinct().size);assertEquals(1,heights.distinct().size)
        assertTrue(sizes.first()>=sizes.last())
    }
    @Test fun permissionAndFavoriteLoadOneRealJourneyAndWordingChangesReuseIt() {
        val favorite=Favorite(label="Mein Ziel",location=place)
        val (_,fake)=mordorOverviewExample()
        val real=fake.copy(id="real",segments=fake.segments.map {it.copy(destination=place)})
        var calls=0;var preset by mutableStateOf(SentencePreset.COMPACT)
        compose.setContent {val settings=AppSettings(quietMode=true,overview=OverviewSettings().withSentencePreset(preset));OpenVTheme(settings) {
            OnboardingOverviewPreview(favorite,settings,SetupPermissions(location=SetupLocationAccess.APPROXIMATE),{f,_->assertEquals(favorite,f);calls++;real})
        }}
        compose.onNodeWithText("Vorschau · Verbindung zu deinem Favoriten").assertExists()
        compose.onNodeWithTag("onboarding-overview-text").assertTextContains("Mein Ziel",substring=true)
        compose.runOnIdle {preset=SentencePreset.DETAILED}
        compose.onNodeWithTag("onboarding-overview-text").assertTextContains("Mein Ziel",substring=true)
        assertEquals(1,calls)
    }
    @Test fun permissionWithoutAFavoriteOrDisabledLocationNeverSendsARequest() {
        var favorite by mutableStateOf<Favorite?>(null);var calls=0
        compose.setContent {OpenVTheme(AppSettings(quietMode=true)){OnboardingOverviewPreview(favorite,AppSettings(),
            SetupPermissions(location=SetupLocationAccess.PRECISE,locationEnabled=false),{_,_->calls++;error("No request allowed")})}}
        compose.runOnIdle {favorite=Favorite(label="Test",location=place)}
        compose.onNodeWithText("Fiktives Beispiel · Reise nach Mordor").assertExists();assertEquals(0,calls)
    }
    @Test fun wizardKeepsEveryPresetAndItsEntireExampleVisibleBelowTheButtons() {
        var store by mutableStateOf(StoredState(onboardingDraft=OnboardingDraft(step=3,settings=AppSettings(quietMode=true))))
        compose.setContent {OnboardingWizard(store,SetupPermissions(),{change->store=store.copy(onboardingDraft=change(store.onboardingDraft!!));true},{true},{emptyList()})}
        for((preset,title) in listOf(SentencePreset.COMPACT to "Kompakt",SentencePreset.EVERYDAY to "Alltag",SentencePreset.DETAILED to "Alle Details")) {
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithText("Kompakt").assertIsDisplayed();compose.onNodeWithText("Alltag").assertIsDisplayed();compose.onNodeWithText("Alle Details").assertIsDisplayed()
            val text=compose.onNodeWithTag("onboarding-overview-text");text.assertIsDisplayed()
            val layouts=mutableListOf<TextLayoutResult>();text.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertFalse(preset.name,layouts.single().hasVisualOverflow)
            assertEquals(preset.name,layouts.single().size.height.toFloat(),text.fetchSemanticsNode().boundsInRoot.height,.5f)
            capture("onboarding-wizard-${preset.name.lowercase()}")
        }
    }
    @Test fun unavailableLiveJourneyFallsBackExplicitlyWithoutSendingFictionToTheService() {
        var calls=0;val favorite=Favorite(label="Mein Ziel",location=place)
        compose.setContent {OpenVTheme(AppSettings(quietMode=true)){OnboardingOverviewPreview(favorite,AppSettings(),
            SetupPermissions(location=SetupLocationAccess.PRECISE),{f,_->calls++;assertEquals(place,f.location);throw java.io.IOException("offline")})}}
        compose.onNodeWithText("Live-Beispiel derzeit nicht verfügbar · fiktive Reise nach Mordor").assertExists()
        compose.onNodeWithTag("onboarding-overview-text").assertTextContains("Mordor",substring=true)
        assertEquals(1,calls)
    }
}
