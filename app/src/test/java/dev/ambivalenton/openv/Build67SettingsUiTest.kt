// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.screens.FavoriteRadiusSetting
import dev.ambivalenton.openv.ui.screens.NetworkGridSizeSetting
import dev.ambivalenton.openv.ui.screens.NETWORK_GRID_HOLD_MS
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The two new favourite settings (grid rectangle and radius speech bubble). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="de-rDE-w411dp-h891dp-420dpi")
class Build67SettingsUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val changes=mutableListOf<Pair<Int,Int>>()
    /** H12: [outside] names the favourites a smaller grid would move into the tray. */
    private fun grid(outside:List<String> = emptyList(),columns:Int=6,rows:Int=6) = compose.setContent {OpenVTheme(AppSettings(quietMode=true)) {Surface {
        var size by remember {mutableStateOf(columns to rows)}
        Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) {
            NetworkGridSizeSetting(size.first,size.second,{c,r->if(c<size.first||r<size.second) outside else emptyList()}) {c,r->changes+=c to r;size=c to r}
        }
    }}}
    private fun drag(columns:Float,rows:Float) = compose.onNodeWithTag("network-grid-picker").performTouchInput {
        // A brief hold arms the picker before dragging resizes it.
        down(center);advanceEventTime(NETWORK_GRID_HOLD_MS+30)
        val target=Offset(width*columns/12f,height*rows/12f)
        repeat(6) {i->moveTo(center+(target-center)*((i+1)/6f));advanceEventTime(16)}
        up()
    }

    @Test fun theRectangleIsDraggedFromTheTopLeftAndAppliesDirectlyWithoutFavourites() {
        grid()
        compose.onNodeWithTag("network-grid-value").assertTextEquals("6 × 6")
        drag(2.5f,11.5f)
        compose.runOnIdle {assertEquals(listOf(3 to 12),changes)}
        compose.onNodeWithTag("network-grid-value").assertTextEquals("3 × 12")
        // Below 3 and beyond 12 the size stays inside its limits.
        drag(.2f,.2f)
        compose.runOnIdle {assertEquals(3 to 3,changes.last())}
    }

    /** H12: shrinking names the favourites that move into the tray; growing applies at once. */
    @Test fun shrinkingNamesTheFavouritesForTheTrayFirstAndCancellingKeepsTheGrid() {
        grid(outside=listOf("Arbeit"))
        drag(4.5f,3.5f)
        compose.runOnIdle {assertTrue("nothing changes before the confirmation",changes.isEmpty())}
        compose.onNodeWithText("Raster auf 5 × 4 verkleinern?").assertExists()
        compose.onNodeWithTag("network-grid-outside").assertTextEquals("Arbeit")
        compose.onNodeWithText("Abbrechen").performClick()
        compose.onNodeWithTag("network-grid-value").assertTextEquals("6 × 6")
        drag(4.5f,3.5f)
        compose.onNodeWithText("In Ablage verschieben").assertExists()
        compose.onNodeWithTag("network-grid-confirm").performClick()
        compose.runOnIdle {assertEquals(listOf(5 to 4),changes)}
        drag(7.5f,8.5f)
        compose.runOnIdle {assertEquals("growing needs no confirmation",listOf(5 to 4,8 to 9),changes)}
    }

    @Test fun talkBackCanChangeTheGridWithoutDragging() {
        grid()
        val actions=compose.onNodeWithTag("network-grid-picker").fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf("Spalte hinzufügen","Spalte entfernen","Zeile hinzufügen","Zeile entfernen"),actions.map {it.label})
        actions[0].action();compose.waitForIdle();compose.runOnIdle {assertEquals(listOf(7 to 6),changes)}
    }

    @Test fun theRectangleCanBeDraggedInsideTheFavouriteSettingsSheet() {
        val overlays=dev.ambivalenton.openv.ui.components.AppOverlayState()
        var size by mutableStateOf(6 to 6)
        val home=Favorite("z","Zuhause",Location("Herne","z",51.54,7.22,"T"))
        compose.setContent {OpenVTheme(AppSettings(quietMode=true)) {Surface {
            CompositionLocalProvider(dev.ambivalenton.openv.ui.components.LocalAppOverlay provides overlays) {
                dev.ambivalenton.openv.ui.components.AppOverlayHost(overlays,Modifier.fillMaxSize()) {
                    dev.ambivalenton.openv.ui.screens.FavoriteSettingsSheet(StoredState(favorites=listOf(home),
                        network=TransitNetwork(listOf(NetworkNode("z",GridPoint(5,5))),columns=size.first,rows=size.second)),
                        {},{},{},{c,r->size=c to r},{})
                }
            }
        }}}
        compose.onNodeWithTag("network-grid-picker").performScrollTo()
        drag(4.5f,3.5f)
        // H12: the favourite at 6/6 lies outside 5 × 4, so the tray move is confirmed first.
        compose.runOnIdle {assertEquals(6 to 6,size)}
        compose.onNodeWithTag("network-grid-confirm").performClick()
        compose.runOnIdle {assertEquals(5 to 4,size)}
        compose.onNodeWithTag("network-grid-value").assertTextEquals("5 × 4")
    }

    @Test fun theRadiusBubbleNamesBothSnapPointsAndTheFreeRange() {
        var committed:Int?=null
        compose.setContent {OpenVTheme(AppSettings(quietMode=true)) {Surface {
            var meters by remember {mutableIntStateOf(500)}
            Column(Modifier.width(360.dp)) {FavoriteRadiusSetting(meters) {committed=it;meters=it}}
        }}}
        compose.onNodeWithTag("favorite-radius-value").assertTextEquals("Bis 500 m Entfernung startet die App am Favoriten")
        compose.onNodeWithTag("favorite-radius-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(0f)}
        compose.onNodeWithTag("favorite-radius-value").assertTextEquals("Aus: immer dein tatsächlicher Standort")
        compose.onNodeWithTag("favorite-radius-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(1f)}
        compose.onNodeWithTag("favorite-radius-value").assertTextEquals("Immer der nächstgelegene Favorit, egal wie weit er entfernt ist")
        compose.runOnIdle {assertEquals(FAVORITE_RADIUS_ALWAYS,committed)}
        compose.onNodeWithTag("favorite-radius-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(.5f)}
        compose.runOnIdle {assertEquals(1000f,committed!!.toFloat(),15f)}
        assertEquals("Umkreis um Favoriten",compose.onNodeWithTag("favorite-radius-slider").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.single())
    }
}
