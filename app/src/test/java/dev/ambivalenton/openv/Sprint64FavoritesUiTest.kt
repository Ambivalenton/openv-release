// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.*
import dev.ambivalenton.openv.ui.onboarding.*
import dev.ambivalenton.openv.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sprint64FavoritesUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val a=Favorite(id="a",label="Zuhause",location=Location("Herne Bf","a",51.54,7.22,"TEST"),accentSeed=0xff156b56)
    private val b=Favorite(id="b",label="Arbeit",location=Location("Dortmund Hbf","b",51.51,7.46,"TEST"),accentSeed=0xff926126)
    private val now=1_800_000_000_000L
    private val clock=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    private fun state()=StoredState(favorites=listOf(a,b),settings=AppSettings(quietMode=true,
        originMode=OriginMode.MANUAL,manualOrigin=a.location))

    private fun capture(name:String)=compose.runOnIdle {
        val view=android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull {it.isShown && it.width>0} ?: compose.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val file=File("../audit/build64/images/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }





    @Test fun repeatedLocationsAndEvenLegacyRepeatedIdsRenderAndLongPressOnlyStartsTheSearch() {
        val query=JourneyQuery(a.location,b.location,now)
        val trip=overviewTestTrip(query)
        val first=OverviewEntry(b,0,query,trip,loading=false)
        val second=first.copy(favorite=b.copy(label="Arbeit noch einmal"))
        var cards by mutableStateOf(false);var opened:OverviewEntry?=null;var searched:OverviewEntry?=null
        compose.setContent {OpenVTheme(AppSettings(quietMode=true)){Surface(Modifier.fillMaxSize()) {
            OverviewScreen(OverviewUiState(listOf(first,second),ready=true),OverviewSettings(showQuotes=false,cards=cards),{},{},{},
                onOpenConnection={opened=it},onSearchConnections={searched=it})
        }}}
        for(cardMode in listOf(false,true)) {
            compose.runOnIdle {cards=cardMode;opened=null;searched=null}
            val tag=if(cardMode)"overview-card-b" else "overview-b"
            compose.onAllNodesWithTag(tag).assertCountEquals(2)
            compose.onAllNodesWithTag(tag)[1].performTouchInput {longClick()}
            compose.runOnIdle {assertNull(opened);assertEquals(second,searched);searched=null}
            compose.onAllNodesWithTag(tag)[0].performClick()
            compose.runOnIdle {assertEquals(first,opened);assertNull(searched)}
        }
        capture("duplicate-favorites-overview")
    }

    @Test fun setupExplainsDuplicatesManagementAndTheOverviewLongPress() {
        var store by mutableStateOf(StoredState(onboardingDraft=OnboardingDraft(step=2,settings=AppSettings(quietMode=true))))
        compose.setContent {OnboardingWizard(store,SetupPermissions(),{change->store=store.copy(onboardingDraft=change(store.onboardingDraft!!));true},{true},{emptyList()})}
        compose.onNodeWithTag("onboarding-duplicate-favorites").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {store=store.copy(onboardingDraft=store.onboardingDraft!!.copy(step=3))}
        compose.onNodeWithTag("onboarding-overview-actions").assertIsDisplayed()
        compose.runOnIdle {store=store.copy(onboardingDraft=store.onboardingDraft!!.copy(step=4))}
        compose.onNodeWithText("In der Nähe eines Favoriten").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Wischen von einem Favoriten zum anderen",substring=true).performScrollTo().assertIsDisplayed()
        capture("onboarding-management-guide")
        compose.onNodeWithText("Im Drei-Punkte-Menü",substring=true).assertDoesNotExist()
    }
}
