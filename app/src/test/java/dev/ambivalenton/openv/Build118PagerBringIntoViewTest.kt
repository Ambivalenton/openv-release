// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Build 118 · the screen video: "Suchen" slid on to "Abfahrten" by itself. A request to bring something
 * into view from inside a page (here: an element reaching past the page's right edge, as a focused field or
 * a revealed entry can) made the pager settle a whole page on. The home pages end such requests themselves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
@OptIn(ExperimentalFoundationApi::class)
class Build118PagerBringIntoViewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var pager: PagerState
    private lateinit var scope: CoroutineScope
    private val wide = BringIntoViewRequester()
    private val deep = BringIntoViewRequester()
    private var inner: androidx.compose.foundation.ScrollState? = null

    private fun show(guarded: Boolean) {
        compose.setContent {
            pager = rememberPagerState(initialPage = 1) { 3 }
            scope = rememberCoroutineScope()
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { page ->
                Box(Modifier.fillMaxSize().then(if (guarded) Modifier.bringIntoViewResponder(HomePageBringIntoView) else Modifier)) {
                    if (page == 1) {
                        val scroll = rememberScrollState()
                        inner = scroll
                        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                            Text("Suchen")
                            // Something laid out past the page's right edge (it reaches into the next page).
                            Box(Modifier.fillMaxWidth().height(40.dp).wrapContentWidth(unbounded = true, align = androidx.compose.ui.Alignment.Start)
                                .offset(x = 300.dp).requiredWidth(300.dp).bringIntoViewRequester(wide))
                            repeat(40) { Text("Zeile $it", Modifier.height(60.dp)) }
                            Text("Ganz unten", Modifier.height(60.dp).bringIntoViewRequester(deep))
                        }
                    } else Text(if (page == 0) "Übersicht" else "Abfahrten")
                }
            }
        }
        compose.waitForIdle()
        assertEquals(1, pager.settledPage)
    }

    private fun request(requester: BringIntoViewRequester) {
        compose.runOnIdle { scope.launch { requester.bringIntoView() } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test fun withoutTheGateTheFoundationPagerMovesOnByItself() {
        show(guarded = false)
        request(wide)
        // Documents the cause: the page changes although nobody swiped or tapped.
        assertNotEquals(1, pager.currentPage)
    }

    @Test fun theHomePagesStayWhereTheyAre() {
        show(guarded = true)
        request(wide)
        assertEquals(1, pager.currentPage)
        assertEquals(0f, pager.currentPageOffsetFraction, .001f)
    }

    @Test fun listsInsideAPageStillScrollToTheirEntry() {
        show(guarded = true)
        request(deep)
        assertEquals(1, pager.currentPage)
        val scrolled = compose.runOnIdle { inner!!.value }
        assertTrue("the page scrolled down to its last entry: $scrolled", scrolled > 1_000)
    }
}
