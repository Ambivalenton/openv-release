// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.AppSettings
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A sheet closed by swiping never shows up again, not even for one frame. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build78SheetCloseUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val sheetColor = Color(0xffd01020)
    private val overlay = AppOverlayState()
    private var open by mutableStateOf(true)

    private fun pixel(x: Float, y: Float): Int = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        bitmap.getPixel(x.toInt(), y.toInt()).also { bitmap.recycle() }
    }

    private fun bottomSheet(settings: AppSettings) {
        compose.setContent {
            OpenVTheme(settings) {
                CompositionLocalProvider(LocalAppOverlay provides overlay) {
                    AppOverlayHost(overlay, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.White))
                        if (open) AppModalBottomSheet({ open = false }, containerColor = sheetColor) {
                            Box(Modifier.fillMaxWidth().height(320.dp).testTag("sheet-body"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Drags the handle down by [distance] px and returns a point inside the strip the sheet left. */
    private fun swipeHandle(distance: Float): Offset {
        val handle = compose.onNodeWithTag("app-bottom-sheet-drag-handle").fetchSemanticsNode().boundsInRoot
        val probe = Offset(handle.center.x, handle.top + distance / 2f)
        assertEquals("the sheet covers the probe at rest", sheetColor.toArgb(), pixel(probe.x, probe.y))
        compose.onNodeWithTag("app-bottom-sheet-drag-handle").performTouchInput {
            swipe(center, center + Offset(0f, distance), 400)
        }
        return probe
    }

    private fun assertSwipeCloseNeverReturns(settings: AppSettings) {
        bottomSheet(settings)
        compose.mainClock.autoAdvance = false
        val probe = swipeHandle(220f)
        val frames = mutableListOf<Int>()
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            frames += pixel(probe.x, probe.y)
        }
        compose.mainClock.autoAdvance = true
        assertFalse("the sheet was dismissed", open)
        assertFalse("the sheet reappeared at its resting position: $frames", frames.any { it == sheetColor.toArgb() })
        compose.onNodeWithTag("sheet-body").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, overlay.visibleCount) }
        // Nothing is left over the content: no sheet and no scrim.
        assertEquals(Color.White.toArgb(), pixel(probe.x, probe.y))
    }

    @Test fun aSwipedAwaySheetSlidesOutAndNeverShowsItsRestingPositionAgain() =
        assertSwipeCloseNeverReturns(AppSettings())

    @Test fun withReducedMotionTheSheetDisappearsAtOnceWithoutSnappingBack() =
        assertSwipeCloseNeverReturns(AppSettings(quietMode = true))

    @Test fun aShortDragReturnsTheSheetToRestAndKeepsItOpen() {
        bottomSheet(AppSettings())
        val before = compose.onNodeWithTag("app-bottom-sheet-drag-handle").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("app-bottom-sheet-drag-handle").performTouchInput { swipe(center, center + Offset(0f, 40f), 400) }
        compose.waitForIdle()
        assertTrue(open)
        val after = compose.onNodeWithTag("app-bottom-sheet-drag-handle").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(sheetColor.toArgb(), pixel(before.center.x + 80f, before.top + 20f))
    }

    @Test fun aSheetWhoseCallerDeclinesTheDismissalComesBackInsteadOfBlockingInvisibly() {
        var requests = 0
        compose.setContent {
            OpenVTheme(AppSettings()) {
                CompositionLocalProvider(LocalAppOverlay provides overlay) {
                    AppOverlayHost(overlay, Modifier.fillMaxSize()) {
                        AppModalBottomSheet({ requests++ }, containerColor = sheetColor) {
                            Box(Modifier.fillMaxWidth().height(320.dp).testTag("sheet-body"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("app-bottom-sheet-drag-handle").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("app-bottom-sheet-drag-handle").performTouchInput { swipe(center, center + Offset(0f, 220f), 400) }
        compose.mainClock.advanceTimeBy(HOSTED_SHEET_REOPEN_MILLIS + 1_000)
        compose.waitForIdle()
        assertEquals(1, requests)
        val after = compose.onNodeWithTag("app-bottom-sheet-drag-handle").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(sheetColor.toArgb(), pixel(before.center.x + 80f, before.top + 20f))
    }

    @Test fun aDirectSheetKeepsItsDarknessAndIsFullyClearWhenItReportsTheClose() {
        var closes = 0
        var close: () -> Unit = {}
        compose.setContent {
            OpenVTheme(AppSettings()) {
                CompositionLocalProvider(LocalAppOverlay provides overlay) {
                    AppOverlayHost(overlay, Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.White))
                        if (open) DirectModalSheet({ closes++; open = false }, null, "close-sheet", fraction = .5f, animateEntry = false) { _, dismiss ->
                            SideEffect { close = dismiss }
                            Box(Modifier.fillMaxSize().testTag("close-body"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val sheet = compose.onNodeWithTag("close-sheet").fetchSemanticsNode().boundsInRoot
        val probe = Offset(sheet.center.x, sheet.top / 2f)
        // Same resting darkness as the former two layers (host 38 % under the sheet's own 44 %).
        val resting = android.graphics.Color.red(pixel(probe.x, probe.y))
        assertEquals((255 * (1f - .38f) * (1f - .44f)).toDouble(), resting.toDouble(), 2.0)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { close() }
        var previous = resting
        var reported = -1
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            val now = android.graphics.Color.red(pixel(probe.x, probe.y))
            assertTrue("the scrim must only fade out ($previous → $now)", now >= previous - 1)
            if (closes == 1 && reported < 0) reported = now
            previous = now
        }
        compose.mainClock.autoAdvance = true
        assertEquals(1, closes)
        assertTrue("nothing dimmed is left when the close is reported (red $reported)", reported >= 253)
        compose.runOnIdle { assertEquals(0, overlay.visibleCount) }
    }
}
