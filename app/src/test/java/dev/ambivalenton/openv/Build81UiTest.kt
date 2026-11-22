// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import dev.ambivalenton.openv.ui.theme.appPanelDecoration
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build81UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file = File("../audit/build81/ui-$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }

    @Test fun departureArrivalPillSwitchesAndNowAreaReturnsToNow() {
        var mode by mutableStateOf(TimeMode.DEPARTURE)
        var time by mutableStateOf<Long?>(null)
        compose.setContent { OpenVTheme(AppSettings(appTheme = AppTheme.CONFETTI_STUDIO)) { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(20.dp)) { ScheduleControls(time, { time = it }, mode, { mode = it }, { time = null; mode = TimeMode.DEPARTURE }) }
        } } }
        compose.onNodeWithTag("search-now").assertDoesNotExist()
        compose.onNodeWithText("Ankunft").performClick()
        compose.runOnIdle { assertEquals(TimeMode.ARRIVAL, mode) }
        compose.onNodeWithTag("time-mode-ARRIVAL").assertIsSelected()
        compose.onNodeWithTag("time-mode-DEPARTURE").assertIsNotSelected()
        compose.runOnIdle { time = 1_800_000_000_000L }
        compose.onNodeWithTag("search-now").assertIsDisplayed()
        val chosen = java.time.Instant.ofEpochMilli(1_800_000_000_000L).atZone(java.time.ZoneId.systemDefault())
        compose.onNodeWithTag("analog-clock-${chosen.hour}-${chosen.minute}", useUnmergedTree = true).assertExists()
        capture("schedule-open")
        compose.onNodeWithTag("search-now").performClick()
        compose.runOnIdle { assertNull(time); assertEquals(TimeMode.DEPARTURE, mode) }
        compose.waitForIdle()
        compose.onNodeWithTag("search-now").assertDoesNotExist()
    }

    @Test fun scheduleControlsAcrossThemesAndAnimationFrames() {
        val themes = listOf(AppTheme.CLASSIC_LIGHT, AppTheme.OS_MAC_LEOPARD, AppTheme.CONFETTI_STUDIO, AppTheme.ART_DECO,
            AppTheme.TERRACOTTA, AppTheme.BRUTALIST, AppTheme.SYNTHWAVE, AppTheme.OS_WIN_95)
        var open by mutableStateOf(true)
        compose.setContent { Column(Modifier.fillMaxSize()) {
            themes.forEachIndexed { i, theme -> OpenVTheme(AppSettings(appTheme = theme)) { Surface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    ScheduleControls(if (open) 1_800_000_000_000L else null, {}, if (i % 2 == 0) TimeMode.DEPARTURE else TimeMode.ARRIVAL, {}, {})
                }
            } } }
        } }
        capture("schedule-themes")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { open = false }
        compose.mainClock.advanceTimeBy(64); capture("schedule-closing-064")
        compose.mainClock.advanceTimeBy(96); capture("schedule-closing-160")
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { open = true }
        compose.mainClock.advanceTimeBy(48); capture("schedule-opening-048")
        compose.mainClock.advanceTimeBy(80); capture("schedule-opening-128")
        compose.mainClock.advanceTimeBy(120); capture("schedule-opening-248")
        compose.mainClock.autoAdvance = true
    }

    @Test fun colourfulThemesTintTheCapsulePerDestination() {
        val themes = listOf(AppTheme.OS_MAC_LEOPARD, AppTheme.CONFETTI_STUDIO, AppTheme.RAINBOW_CLASSIC, AppTheme.DECADE_80, AppTheme.CLASSIC_LIGHT)
        val tabs = listOf(PillDestination("Übersicht", dev.ambivalenton.openv.R.drawable.ic_list),
            PillDestination("Suchen", dev.ambivalenton.openv.R.drawable.ic_search), PillDestination("Abfahrten", dev.ambivalenton.openv.R.drawable.ic_train))
        compose.setContent { Column(Modifier.fillMaxSize()) {
            themes.forEach { theme -> OpenVTheme(AppSettings(appTheme = theme)) { Surface(Modifier.fillMaxWidth()) { Row {
                for (selected in 0..2) Box(Modifier.weight(1f)) {
                    val pager = androidx.compose.foundation.pager.rememberPagerState(selected) { 3 }
                    ExpressivePillNavigation(pager, tabs, selected) {}
                }
            } } } }
        } }
        capture("pill-capsules")
        assertTrue(colorfulCapsuleTheme(AppTheme.CONFETTI_STUDIO)); assertFalse(colorfulCapsuleTheme(AppTheme.CLASSIC_LIGHT))
        assertFalse(colorfulCapsuleTheme(AppTheme.OS_MAC_LEOPARD)); assertFalse(colorfulCapsuleTheme(AppTheme.MONOCHROME))
        val hues = capsuleHues(AppTheme.RAINBOW_CLASSIC, androidx.compose.ui.graphics.Color(AppTheme.RAINBOW_CLASSIC.accent), 3)
        assertEquals(3, hues.distinct().size)
    }

    @Test fun appThemeNetworkPlanFollowsEachTheme() {
        val themes = listOf(AppTheme.POCKET_CONSOLE, AppTheme.IMPRESSION_GARDEN, AppTheme.REN_LUMEN, AppTheme.INK_WASH,
            AppTheme.SYNTHWAVE, AppTheme.COPPER_CIRCUIT, AppTheme.FROSTED_BERRY, AppTheme.KRAFT_STUDIO, AppTheme.CLASSIC_LIGHT, AppTheme.OS_WIN_95)
        compose.setContent { Column(Modifier.fillMaxSize()) {
            themes.chunked(2).forEach { pair -> Row {
                pair.forEach { theme -> Box(Modifier.weight(1f)) {
                    OpenVTheme(AppSettings(appTheme = theme, networkPlanDesign = NetworkPlanDesign.APP_THEME)) { Surface {
                        dev.ambivalenton.openv.ui.network.NetworkPlanDesignPreview(NetworkPlanDesign.APP_THEME, vehicles = false, animate = false)
                    } }
                } }
            } }
        } }
        capture("network-app-theme")
        assertEquals(dev.ambivalenton.openv.ui.network.PlanStroke.PIXEL, dev.ambivalenton.openv.ui.network.appThemePlanLook(AppTheme.POCKET_CONSOLE).stroke)
        assertEquals(dev.ambivalenton.openv.ui.network.PlanStroke.DABS, dev.ambivalenton.openv.ui.network.appThemePlanLook(AppTheme.IMPRESSION_GARDEN).stroke)
        assertEquals(dev.ambivalenton.openv.ui.network.PlanStroke.BRUSH, dev.ambivalenton.openv.ui.network.appThemePlanLook(AppTheme.REN_LUMEN).stroke)
    }

    @Test fun nearbyCardsKeepSystemRimsOnTheThemeCorners() {
        val themes = listOf(AppTheme.OS_MAC_LEOPARD, AppTheme.OS_WIN_95, AppTheme.OS_IOS_GLASS, AppTheme.OS_UBUNTU, AppTheme.OS_WIN_XP, AppTheme.ART_DECO)
        compose.setContent { Column(Modifier.fillMaxSize()) {
            themes.forEach { theme -> OpenVTheme(AppSettings(appTheme = theme)) { Surface(Modifier.fillMaxWidth()) {
                val shape = dev.ambivalenton.openv.ui.theme.appCorner(28.dp)
                androidx.compose.material3.Card(shape = shape, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)) {
                    Box(Modifier.fillMaxWidth().height(96.dp).appPanelDecoration(shape)) {
                        androidx.compose.material3.Text("Dortmund Hacheney", Modifier.padding(16.dp))
                    }
                }
            } } }
        } }
        capture("nearby-cards")
    }
}
