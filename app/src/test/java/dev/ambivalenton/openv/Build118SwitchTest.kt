// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.AppSettings
import dev.ambivalenton.openv.data.model.TimeMode
import dev.ambivalenton.openv.ui.components.DetentDrag
import dev.ambivalenton.openv.ui.components.ExpressivePillNavigationTo
import dev.ambivalenton.openv.ui.components.PillDestination
import dev.ambivalenton.openv.ui.components.TimeModeSwitch
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Build 118 · "Abfahrt | Ankunft" and the navigation capsule as mechanical switches: lean, click over, rest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
class Build118SwitchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun theLeverLeansFirstAndClicksOverPastTheMiddle() {
        val d = DetentDrag(2, 0)
        assertNull(d.drag(.3f))
        assertTrue("leans towards the finger", d.lean > 0f && d.lean < .3f)
        assertNull(d.drag(.2f)) // .5: right on the middle, no flutter
        assertEquals(1, d.drag(.1f)) // past the middle plus hysteresis: clicks over
        assertEquals(1, d.committed)
        assertTrue("leans back a little from the new rest", d.lean < 0f)
        // Back by a hair does not undo the click.
        assertNull(d.drag(-.1f))
        assertEquals(0, d.drag(-1f))
        // The ends give way softly but never beyond one position.
        assertNull(d.drag(-5f))
        assertEquals(0, d.committed)
        assertTrue(kotlin.math.abs(d.lean) <= DetentDrag.LEAN_REACH)
    }

    @Test fun aFastDragOverThreeTabsClicksIntoEachAndAFlickCountsOnce() {
        val d = DetentDrag(3, 0)
        assertEquals(2, d.drag(2.0f))
        assertEquals(2, d.committed)
        val e = DetentDrag(3, 1)
        e.drag(.3f)
        assertEquals(2, e.release(6f))
        val f = DetentDrag(3, 1)
        f.drag(.1f)
        assertNull("a slow release only springs back", f.release(1f))
        assertEquals(1, f.committed)
    }

    @Test fun draggingTheModeSwitchChangesTheModeTappingStillWorks() {
        var mode by mutableStateOf(TimeMode.DEPARTURE)
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
            Box(Modifier.padding(24.dp).width(320.dp)) { TimeModeSwitch(mode, { mode = it }) }
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("time-mode-pill").performTouchInput { swipeRight(startX = width * .25f, endX = width * .85f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(TimeMode.ARRIVAL, mode)
        compose.onNodeWithTag("time-mode-pill").performTouchInput { swipeLeft(startX = width * .8f, endX = width * .15f, durationMillis = 300) }
        compose.waitForIdle()
        assertEquals(TimeMode.DEPARTURE, mode)
        // A short drag below the middle springs back.
        compose.onNodeWithTag("time-mode-pill").performTouchInput { swipeRight(startX = width * .25f, endX = width * .4f, durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(TimeMode.DEPARTURE, mode)
        compose.onNodeWithTag("time-mode-ARRIVAL").performClick()
        compose.waitForIdle()
        assertEquals(TimeMode.ARRIVAL, mode)
    }

    @Test fun draggingAlongThePillWalksThroughTheTabs() {
        val chosen = mutableListOf<Int>()
        lateinit var pager: PagerState
        compose.setContent { OpenVTheme(AppSettings(quietMode = true)) { Surface(Modifier.fillMaxSize()) {
            pager = rememberPagerState { 3 }
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
                Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).testTag("pill-host")) {
                    ExpressivePillNavigationTo(pager, listOf(PillDestination("Übersicht", R.drawable.ic_overview),
                        PillDestination("Suchen", R.drawable.ic_search), PillDestination("Abfahrten", R.drawable.ic_train)), null, null) { page ->
                        chosen += page
                        // The navigation owner applies the selection, as in the app.
                        pager.requestScrollToPage(page)
                    }
                }
            }
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("pill-host").performTouchInput { swipeRight(startX = width * .15f, endX = width * .85f, durationMillis = 500) }
        compose.waitForIdle()
        assertEquals("clicks into Suchen, then Abfahrten", listOf(1, 2), chosen)
        assertEquals(2, pager.settledPage)
        compose.onNodeWithTag("pill-host").performTouchInput { swipeLeft(startX = width * .85f, endX = width * .5f, durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(listOf(1, 2, 1), chosen)
        assertEquals(1, pager.settledPage)
    }
}
