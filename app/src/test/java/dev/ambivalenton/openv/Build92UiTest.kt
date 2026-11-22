// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.AppUiState
import dev.ambivalenton.openv.ui.AppViewModel
import dev.ambivalenton.openv.ui.components.AppModesSettings
import dev.ambivalenton.openv.ui.dialogs.FavoriteEditDialog
import dev.ambivalenton.openv.ui.screens.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The grid needs a brief hold, more symbols behind "Weitere", the page "Modi & Verhalten". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=OpenVApp::class,qualifiers="de-rDE-w411dp-h891dp-mdpi")
class Build92UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun bounds(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true).getUnclippedBoundsInRoot()

    @Test fun scrollingOverTheGridChangesNothingABriefHoldThenDragDoes() {
        val changes=mutableListOf<Pair<Int,Int>>()
        compose.setContent {OpenVTheme(AppSettings(quietMode=true)) {Surface {
            var size by remember {mutableStateOf(6 to 6)}
            Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) {
                NetworkGridSizeSetting(size.first,size.second,{_,_->emptyList()}) {c,r->changes+=c to r;size=c to r}
            }
        }}}
        val picker=compose.onNodeWithTag("network-grid-picker")
        // A swipe that starts on the grid (like scrolling the list) does not resize it.
        picker.performTouchInput {down(center);repeat(6) {moveBy(Offset(-20f,-30f));advanceEventTime(16)};up()}
        compose.waitForIdle();assertTrue(changes.isEmpty())
        // A tap does not either.
        picker.performTouchInput {click(Offset(width*.2f,height*.2f))}
        compose.waitForIdle();assertTrue(changes.isEmpty())
        // Holding at a different cell without dragging must also preserve the current grid.
        picker.performTouchInput {down(Offset(width*.2f,height*.2f));advanceEventTime(NETWORK_GRID_HOLD_MS+20);up()}
        compose.waitForIdle();assertTrue(changes.isEmpty())
        // A brief hold (well below a long press) arms it; then dragging sets the size.
        picker.performTouchInput {
            down(Offset(width*.5f,height*.5f));advanceEventTime(NETWORK_GRID_HOLD_MS+20)
            repeat(5) {i->moveTo(Offset(width*(.5f-.06f*(i+1)),height*(.5f+.048f*(i+1))));advanceEventTime(16)};up()
        }
        compose.waitForIdle()
        assertEquals(listOf(3 to 9),changes)
        assertTrue(NETWORK_GRID_HOLD_MS<android.view.ViewConfiguration.getLongPressTimeout())
    }

    @Test fun theGridComesLastInTheFavouriteSettings() {
        compose.setContent {OpenVTheme(AppSettings(quietMode=true)) {Surface {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            FavoriteSettingsContent(StoredState(),{},{},{},{_,_->})
        }}}}
        val grid=bounds("network-grid-setting")
        listOf("favorite-settings-list","favorite-radius-setting","network-hold-setting").forEach {assertTrue(it,bounds(it).bottom<=grid.top)}
    }

    @Test fun moreSymbolsOpenFromTheLowerRightWithASmallWord() {
        val place=Location("Bahnhof am Park",latitude=50.1,longitude=10.2)
        compose.setContent {OpenVTheme {FavoriteEditDialog(null,"test",{emptyList()},{},{},initialLocation=place)}}
        // M02: the place alone can be saved. The kinds of place stand in one row, all symbols behind "Mehr".
        compose.onNodeWithTag("favorite-save").assertIsEnabled().assert(hasText("Favorit speichern"))
        compose.onNodeWithTag("favorite-intent-more").assert(hasText("Alle Symbole"))
        compose.onNodeWithTag("favorite-icon-BAKERY").assertDoesNotExist()
        compose.onNodeWithTag("favorite-intent-more").performTouchInput {click()}
        compose.waitForIdle()
        listOf("MOTHER","FATHER","PARENTS","GRANDPARENTS","BAKERY","RESTAURANT","DOG").forEach {
            compose.onNodeWithTag("favorite-icon-$it").assertExists()
        }
        compose.onNodeWithTag("favorite-icon-CHILDREN").assertDoesNotExist()
        compose.onNodeWithTag("favorite-icon-BAKERY").performTouchInput {click()}
        compose.waitForIdle()
        // A kind of place keeps the place's name; the chosen symbol leads the row.
        compose.onNodeWithTag("favorite-name").assertTextContains("Bahnhof am Park")
        compose.onNodeWithTag("favorite-intent-BAKERY").assertIsSelected()
    }

    @Test fun everyModeHasItsOwnFrameAndTheBackupComesLast() {
        var settings by mutableStateOf(AppSettings(quietMode=true))
        compose.setContent {OpenVTheme(settings) {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AppModesSettings(settings,{settings=it(settings)}) {androidx.compose.material3.Text("Sichern",Modifier.testTag("mode-backup"))}
        }}}
        val frames=listOf("mode-monochrome","mode-quiet","mode-power","mode-minimal","mode-bubble","mode-long-press","mode-backup")
        val tops=frames.map {bounds(it).top}
        assertEquals(tops.sorted(),tops)
        // The whole frame switches; each mode stays independent of the others.
        compose.onNodeWithTag("mode-monochrome").performScrollTo().performClick()
        compose.onNodeWithTag("mode-power").performScrollTo().performClick()
        compose.runOnIdle {assertTrue(settings.monochrome);assertTrue(settings.powerSaving);assertTrue(settings.quietMode);assertFalse(settings.minimalistMode)}
        compose.onNodeWithTag("mode-minimal").performScrollTo().performClick()
        compose.runOnIdle {assertTrue(settings.minimalistMode);assertTrue(settings.monochrome)}
    }

    @Test fun settingsPagesMovedAsAsked() {
        val vm=(RuntimeEnvironment.getApplication() as OpenVApp).koin.get<AppViewModel>()
        val state=AppUiState(store=StoredState(settings=AppSettings(quietMode=true)),ready=true)
        compose.setContent {OpenVTheme(state.store.settings) {Surface {SettingsScreen(state,vm)}}}
        // The root lists the new page instead of the fold-out, and no longer "Dienste & Daten".
        compose.onNodeWithTag("app-modes").assertDoesNotExist()
        compose.onNodeWithTag("settings-SERVICES").assertDoesNotExist()
        compose.onNodeWithTag("settings-MODES").performScrollTo().performClick()
        compose.onNodeWithTag("mode-backup").performScrollTo().assertExists()
        compose.onNodeWithTag("backup-export").assertExists()
        compose.onNodeWithTag("settings-back").performClick()
        // Connection search without "Aktuelle Daten laden".
        compose.onNodeWithTag("settings-TRAVEL").performScrollTo().performClick()
        compose.onNodeWithTag("settings-SEARCH").performClick()
        compose.onNodeWithText("Aktuelle Daten laden").assertDoesNotExist()
        compose.onNodeWithTag("settings-back").performClick();compose.onNodeWithTag("settings-back").performClick()
        // Services & data under info & help, without export and import.
        compose.onNodeWithTag("settings-INFO").performScrollTo().performClick()
        compose.onNodeWithTag("settings-SERVICES").performScrollTo().performClick()
        compose.onNodeWithText("Fahrplandienst wählen").assertExists()
        compose.onNodeWithTag("backup-export").assertDoesNotExist()
        compose.onNodeWithTag("settings-back").performClick()
        compose.onNodeWithTag("settings-SERVICES").assertExists()
    }
}
