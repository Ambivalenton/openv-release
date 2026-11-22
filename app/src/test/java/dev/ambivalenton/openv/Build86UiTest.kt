// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.boardConfigurationKey
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.*
import dev.ambivalenton.openv.ui.theme.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build86UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val station=Location("Dortmund Hbf","s1",51.5,7.4,"VRR",category=PlaceCategory.STATION)
    private val address=Location("Rottbruchstraße 41, 44625 Dortmund",null,51.5,7.4,"OSM",LocationKind.ADDRESS,category=PlaceCategory.ADDRESS)
    private val cafe=Location("Café am Markt, Dortmund",null,51.5,7.4,"OSM",LocationKind.POI,category=PlaceCategory.CAFE)
    private val gateway=PlaceSearchGateway({listOf(station,address,cafe)},{flowOf(listOf(station,address,cafe))},true)

    private fun searchScreen(settings:AppSettings=AppSettings(quietMode=true)) {
        val vm=(compose.activity.application as OpenVApp).koin.get<AppViewModel>()
        compose.setContent {OpenVTheme(settings){CompositionLocalProvider(LocalPlaceSearchGateway provides gateway) {
            SearchScreen(AppUiState(store=StoredState(settings=settings),ready=true),vm,{_,_,_->},{_,_,_->},{})
        }}}
    }

    @Test fun theStartChipBecomesAnInlineFieldInsteadOfASheet() {
        searchScreen()
        compose.onNodeWithText("Dein Standort").assertIsDisplayed()
        compose.onNodeWithTag("origin-morph").performClick()
        compose.onNodeWithText("Dein Startpunkt").assertDoesNotExist()
        compose.onNodeWithText("Aktuellen GPS-Standort verwenden").assertIsDisplayed()
        // Without location access the field opens directly with the manual part below a divider.
        compose.onNodeWithTag("origin-gps-row").assertIsOff()
        compose.onNodeWithTag("origin-divider").assertExists()
        compose.onNodeWithText("Manuellen Standort festlegen").assertIsDisplayed()
        // The destination field moved down instead of being covered.
        val panel=compose.onNodeWithTag("origin-morph").fetchSemanticsNode().boundsInRoot
        val destination=compose.onAllNodes(hasSetTextAction()).filter(hasText("Ziel, Adresse oder Haltestelle")).onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue(destination.top>=panel.bottom)
        // Back folds it into the chip again.
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.waitForIdle()
        compose.onNodeWithTag("origin-panel").assertDoesNotExist()
        compose.onNodeWithText("Dein Standort").assertIsDisplayed()
    }

    @Test fun choosingAManualLocationFoldsTheFieldBack() {
        searchScreen()
        compose.onNodeWithTag("origin-morph").performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("origin-manual"))).performTextInput("Rott")
        compose.onNodeWithTag("place-category-address",useUnmergedTree=true).assertExists()
        compose.onNodeWithText("Rottbruchstraße 41, 44625 Dortmund").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("origin-panel").assertDoesNotExist()
    }

    @Test fun aTapOutsideFoldsTheStartFieldBack() {
        searchScreen()
        compose.onNodeWithTag("origin-morph").performClick()
        compose.onNodeWithTag("origin-panel").assertExists()
        compose.onNodeWithTag("time-mode-ARRIVAL").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("origin-panel").assertDoesNotExist()
        compose.onNodeWithTag("time-mode-ARRIVAL").assertIsSelected()
    }

    @Test fun suggestionsShowWhatKindOfPlaceTheyAre() {
        compose.setContent {OpenVTheme(AppSettings(quietMode=true)){
            PlaceSearchField("Ziel",null,"test",{emptyList()},{},fastSearch=gateway)
        }}
        compose.onNode(hasSetTextAction()).performTextInput("Dor")
        compose.onNodeWithTag("place-category-station",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("place-category-address",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("place-category-cafe",useUnmergedTree=true).assertExists()
        compose.onNodeWithContentDescription("Bahnhof: Dortmund Hbf").assertExists()
    }

    @Test fun arrivalIsNeverCutOffAtLargeFonts() {
        compose.setContent {OpenVTheme(AppSettings(quietMode=true)){
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
                Box(Modifier.width(320.dp)) {ScheduleControls(null,{},TimeMode.DEPARTURE,{},{})}
            }
        }}
        for(label in listOf("Abfahrt","Ankunft")) {
            val results=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label,useUnmergedTree=true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
            val r=results.single()
            assertFalse(label+" "+r.size+" "+r.layoutInput.constraints+" w="+r.multiParagraph.width+" h="+r.multiParagraph.height+" font="+r.layoutInput.style.fontSize+" lh="+r.layoutInput.style.lineHeight+" overW="+r.didOverflowWidth+" overH="+r.didOverflowHeight,r.hasVisualOverflow)
        }
    }

    @Test fun sizeAnimationNoLongerCutsShadows() {
        compose.setContent {
            Box(Modifier.size(200.dp).background(Color.White)) {
                Box(Modifier.padding(60.dp).calmContentSize().size(60.dp).drawBehind {
                    drawRect(Color.Red,Offset(-40.dp.toPx(),0f),Size(20.dp.toPx(),20.dp.toPx()))
                }.testTag("animated"))
            }
        }
        val pixel=compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val root=compose.onRoot().fetchSemanticsNode().positionInWindow
            val d=compose.density.density
            bitmap.getPixel((root.x+30*d).toInt(),(root.y+70*d).toInt()).also {bitmap.recycle()}
        }
        assertEquals(android.graphics.Color.RED,pixel)
    }

    @Test fun theAppThemeBoardIsARealCardWithThemeColouredLines() {
        val now=System.currentTimeMillis();val stop=Location("Dortmund Hacheney","st",51.48,7.49,"VRR")
        val target=Location("Dortmund Hbf","hbf",51.5,7.45,"VRR");val q=JourneyQuery(stop,target,now)
        val rows=List(8){i->val leg=TripSegment("$i",stop,target,now+(i+1)*300_000,now+(i+1)*300_000+600_000,LineType.SUBWAY,"U49")
            Departure("$i",stop,"U49",LineType.SUBWAY,"Dortmund Hbf",leg.startTime,leg.startTime,DelayInfo(),platform="1",journey=Trip("$i",listOf(leg),"TEST",now),journeyQuery=q)}
        val settings=AppSettings(quietMode=true,board=BoardSettings(design=BoardDesign.APP_INTEGRATED))
        val base=StoredState(settings=settings)
        val state=base.copy(departureBoard=DepartureBoardState(stop,rows,now,configurationKey=boardConfigurationKey(base)))
        var opened=0;var compared=0
        compose.setContent {OpenVTheme(settings){Surface(Modifier.fillMaxSize()){Column(Modifier.padding(20.dp)){
            SharedDepartureBoard(state,{},{},onJourney={_,_->opened++},onComparison={_,trips->compared=trips.size})
        }}}}
        compose.onNodeWithTag("shared-departure-board-image").assertDoesNotExist()
        compose.onNodeWithText("Dortmund Hacheney").assertIsDisplayed()
        compose.onNodeWithTag("board-journey-5").assertExists()
        compose.onNodeWithTag("board-journey-6").assertDoesNotExist()
        compose.onNodeWithTag("board-journey-0").performClick()
        compose.onNodeWithTag("board-journey-0").performTouchInput {longClick()}
        compose.runOnIdle {assertEquals(1,opened);assertEquals(8,compared)}
        compose.onNodeWithTag("footer-design").assertIsDisplayed()
    }

    @Test fun theRemovedSearchButtonBecomesADropOnTheNavigationPill() {
        val stage=LiquidStage().apply {pill=Rect(20f,800f,391f,870f)}
        var visible by mutableStateOf(true);var hidden=0
        compose.setContent {OpenVTheme {CompositionLocalProvider(LocalLiquidStage provides stage) {
            Box(Modifier.fillMaxSize()) {
                Column {LiquidSearchAction(null,1,true,false,{},{},visible=visible,onHidden={hidden++})}
                LiquidStageLayer(stage)
            }
        }}}
        compose.runOnIdle {visible=false}
        compose.waitForIdle()
        // Handed over to the layer (no dissolve in place), fell onto the pill once and is gone after 2.5 s.
        assertEquals(1,hidden);assertEquals(1,stage.impacts)
        compose.onNodeWithTag("search-submit-dissolving").assertDoesNotExist()
        assertTrue(stage.runs.isEmpty())
    }
}
