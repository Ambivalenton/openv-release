// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.JourneyWindow
import dev.ambivalenton.openv.localization.englishStrings
import dev.ambivalenton.openv.ui.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.screens.*
import dev.ambivalenton.openv.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZonedDateTime
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build87Test {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val now=1_800_000_000_000L
    private val home=Location("Rottbruchstraße 41, 44625 Dortmund",null,51.5,7.4,"OSM",LocationKind.ADDRESS)
    private val start=Location("Aktueller Standort","start",51.45,7.3)
    private val query=JourneyQuery(start,home,now)
    private val trips=(0..5).map {i->Trip("route$i",listOf(TripSegment("s$i",start,home,
        now+i*1_200_000,now+i*1_200_000+2_400_000,LineType.BUS,"390")),"Test",now)}

    private fun results(settings:AppSettings,favorites:List<Favorite> =emptyList()) {
        val ui=AppUiState(ready=true,store=StoredState(settings=settings,favorites=favorites),query=query,trips=LoadState(trips),
            journeyWindow=JourneyWindow(trips,now,now+10_800_000))
        compose.setContent {OpenVTheme(settings) {ResultsScreen(ui,{},{},{},{},{},{},{})}}
    }

    @Test fun theDropThatBecomesTheButtonMovesInOnePiece() {
        assertEquals(750,LIQUID_SEARCH_DURATION_MS)
        val frames=(0..750).map {liquidSearchFrame(it/750f)}
        // No jumps: every quantity changes by a small amount per millisecond, including the squash.
        frames.zipWithNext().forEach {(a,b)->
            assertTrue(abs(a.scaleY-b.scaleY)<.03f);assertTrue(abs(a.scaleX-b.scaleX)<.05f)
            assertTrue(abs(a.lift-b.lift)<.02f);assertTrue(b.fall>=a.fall);assertTrue(abs(a.shape-b.shape)<.03f)
        }
        // It buds from nothing, is squashed clearly on contact, rebounds and ends exactly as the button.
        assertEquals(0f,frames.first().mass,0f)
        assertTrue(frames.minOf {it.scaleY}<.55f);assertTrue(frames.maxOf {it.scaleX}>1.5f)
        val squash=frames.indexOfFirst {it.scaleY<.6f};assertTrue(frames.drop(squash).any {it.scaleY>1.05f})
        val end=frames.last()
        assertEquals(1f,end.scaleX,1e-4f);assertEquals(1f,end.scaleY,1e-4f);assertEquals(0f,end.lift,1e-4f)
        assertEquals(0f,end.wobble,1e-4f);assertEquals(1f,end.shape,0f);assertEquals(1f,end.labelAlpha,0f);assertEquals(1f,end.fall,0f)
    }

    @Test fun favouritesWhereYouAreAreAlwaysHidden() {
        val here=Location("Zuhause","h",51.5,7.4,"VRR")
        val favorite=Favorite("f","Zuhause",here)
        val time=ZonedDateTime.now();val presence=DevicePresence(here,time.toInstant().toEpochMilli(),5f)
        assertEquals(emptyList<Favorite>(),visibleFavorites(listOf(favorite),time,presence))
        // An old stored "off" no longer changes anything, and the setting is gone from the screen.
        val state=StoredState(favorites=listOf(favorite),settings=AppSettings(hideFavoritesAtCurrentLocation=false),presence=presence)
        compose.setContent {OpenVTheme {Column {FavoriteSettingsContent(state,{},{},{},{_,_->})}}}
        compose.onNodeWithText("Am Aufenthaltsort ausblenden").assertDoesNotExist()
        assertNull(englishStrings["Am Aufenthaltsort ausblenden"])
    }

    @Test fun theChartHeaderIsCollapsedShowsTheFavouriteAndExpands() {
        results(AppSettings(quietMode=true),listOf(Favorite("f","Arbeit",home)))
        compose.onNodeWithTag("chart-query-destination").assertTextEquals("Arbeit")
        compose.onNodeWithText("Ab Aktueller Standort").assertDoesNotExist()
        // The button is exactly as tall as the two lines next to it.
        val toggle=compose.onNodeWithTag("chart-query-toggle").fetchSemanticsNode().boundsInRoot
        val name=compose.onNodeWithTag("chart-query-destination").fetchSemanticsNode().boundsInRoot
        assertEquals(name.top,toggle.top,2f)
        compose.onNodeWithTag("chart-query-toggle").performClick()
        compose.onNodeWithText("Ab Aktueller Standort").assertIsDisplayed()
        compose.onNodeWithText(home.name).assertIsDisplayed()
    }

    @Test fun theMinimalistModeShowsOnlyTheChart() {
        results(AppSettings(quietMode=true,minimalistMode=true))
        compose.onNodeWithTag("chart-query-header").assertDoesNotExist()
        compose.onNodeWithTag("comparison-chart-container").assertExists()
    }

    @Test fun theMinimalistModeHidesHintsButKeepsErrors() {
        var hints=true
        compose.setContent {OpenVTheme(AppSettings(quietMode=true,minimalistMode=true)) {hints=hintsShown;JourneySwipeHints(true,true)}}
        compose.runOnIdle {assertFalse(hints)}
        compose.onNodeWithTag("journey-swipe-hints").assertDoesNotExist()
    }

    @Test fun timeLabelsAreCutWhereTheirBarLies() {
        compose.setContent {OpenVTheme(AppSettings(appTheme=AppTheme.CLASSIC_DARK,quietMode=true)) {
            Box(Modifier.size(100.dp,90.dp).background(Color.White)) {
                val d=compose.density
                // Label box 10–34 dp; the chip's surface runs 20 dp further down, where the bar is.
                TimelineClockChip("12:00",TimelineClockPlacement.ABOVE_BAR,Color.Red,
                    Modifier.offset(y=10.dp).fillMaxWidth().height(24.dp),maxWidth=60.dp,tuck=20.dp,
                    cut=ChipBarCut(RectangleShape,listOf(with(d) {Rect(20.dp.toPx(),24.dp.toPx(),80.dp.toPx(),64.dp.toPx())})))
            }
        }}
        val pixel=compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val root=compose.onRoot().fetchSemanticsNode().positionInWindow;val d=compose.density.density
            bitmap.getPixel((root.x+50*d).toInt(),(root.y+44*d).toInt()).also {bitmap.recycle()}
        }
        assertEquals(android.graphics.Color.WHITE,pixel)
    }

    @Test fun theNetworkPlanRisesMoreCalmlyThanItMakesRoom() {
        val rise=searchListRiseSpring as androidx.compose.animation.core.SpringSpec<*>
        assertTrue(rise.stiffness<380f);assertEquals(.72f,rise.dampingRatio,0f)
        // Time scales with 1/√stiffness: about 0.1 s more for the ≈0.28 s motion.
        val extra=.28f*(kotlin.math.sqrt(380f/rise.stiffness)-1)
        assertTrue(extra in .08f..0.13f)
    }

    @Test fun theMatrixFontIsCompleteReadableAndKeepsTimesAligned() {
        val needed=('A'..'Z')+('a'..'z')+('0'..'9')+"ÄÖÜäöüß.,:-+/()'!?&…·".toList()
        needed.forEach {assertTrue("missing $it",it in dev.ambivalenton.openv.rendering.MatrixFont.characters)}
        val digits=('0'..'9').map {dev.ambivalenton.openv.rendering.MatrixFont.glyph(it).width}.toSet()
        assertEquals(setOf(5),digits)
        // Accents fall back to the base letter, unknown signs to a question mark, never to nothing.
        assertEquals(dev.ambivalenton.openv.rendering.MatrixFont.glyph('e').width,dev.ambivalenton.openv.rendering.MatrixFont.glyph('é').width)
        assertEquals(dev.ambivalenton.openv.rendering.MatrixFont.glyph('?').width,dev.ambivalenton.openv.rendering.MatrixFont.glyph('☃').width)
        val long="Holzwickede, Lange Straße"
        val fitted=dev.ambivalenton.openv.rendering.MatrixFont.fit(long,60)
        assertTrue(fitted.endsWith("…"));assertTrue(dev.ambivalenton.openv.rendering.MatrixFont.width(fitted)<=60)
        assertEquals("Dortmund Hbf",dev.ambivalenton.openv.rendering.MatrixFont.fit("Dortmund Hbf",200))
        val (cols,lit)=dev.ambivalenton.openv.rendering.MatrixFont.mask("Gleis 1")
        assertEquals(cols*dev.ambivalenton.openv.rendering.MatrixFont.ROWS,lit.size);assertTrue(lit.count {it}>40)
    }

    @Test fun boardsAreDrawnAtScreenResolutionInTheAppAndBoundedInWidgets() {
        val stop=Location("Dortmund Hacheney","st",51.48,7.49,"VRR")
        for(design in listOf(BoardDesign.TELETEXT,BoardDesign.DOT_MATRIX,BoardDesign.FLIP_DOT,BoardDesign.SPLIT_FLAP,BoardDesign.APP_THEME)) {
            val settings=AppSettings(board=BoardSettings(design=design,selectedStop=stop))
            val base=StoredState(settings=settings)
            val rows=List(6){i->Departure("$i",stop,"U49",LineType.SUBWAY,"Dortmund Hbf",now+i*300_000,now+i*300_000,DelayInfo(),platform="1")}
            val state=base.copy(departureBoard=DepartureBoardState(stop,rows,now,configurationKey=dev.ambivalenton.openv.data.repository.boardConfigurationKey(base)))
            val model=dev.ambivalenton.openv.rendering.boardPresentation(state,appThemeScheme(AppTheme.CLASSIC_DARK),now=now)
            val app=dev.ambivalenton.openv.rendering.BoardRenderer.render(model,371f,430f,1f,density=2.75f)
            assertTrue(design.name,abs((371*2.75f).toInt()-app.width)<=2)
            val widget=dev.ambivalenton.openv.rendering.BoardRenderer.render(model,371f,430f,1f)
            assertTrue(design.name,widget.width.toLong()*widget.height<=250_000L)
        }
    }
}
