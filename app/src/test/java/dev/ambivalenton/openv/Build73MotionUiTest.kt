// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.AppSettings
import dev.ambivalenton.openv.ui.components.AnimatedIconButton
import dev.ambivalenton.openv.ui.components.IconTapMotion
import dev.ambivalenton.openv.ui.network.NetworkEditBar
import dev.ambivalenton.openv.ui.network.NetworkEditBarGeometry
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The pencil morphs into a toolbar and icons answer taps, without changing the actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build73MotionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        val file = File("../audit/build73/images/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    @Composable private fun Header(settings: AppSettings, log: MutableList<String>) {
        var editing by remember { mutableStateOf(false) }
        OpenVTheme(settings) {
            Surface {
                // As on the search page, the bar lies over the heading and may span the width.
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Favoriten", style = MaterialTheme.typography.titleLarge)
                    NetworkEditBar(editing, onEdit = { editing = true; log += "edit" }, onSettings = { log += "settings" },
                        onAdd = { log += "add" }, onDone = { editing = false; log += "done" }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    @Test fun thePencilGrowsToTheLeftIntoTheToolbarAndBack() {
        val log = mutableListOf<String>()
        compose.mainClock.autoAdvance = false
        compose.setContent { Header(AppSettings(), log) }
        compose.mainClock.advanceTimeByFrame()
        val closed = compose.onNodeWithTag("network-edit").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Bearbeiten").assertHasClickAction()
        compose.onNodeWithTag("network-add").assertDoesNotExist()
        capture("network-bar-0-closed")
        compose.onNodeWithTag("network-edit").performClick()
        compose.mainClock.advanceTimeBy(64); capture("network-bar-1-morphing")
        compose.mainClock.advanceTimeBy(96); capture("network-bar-2-morphing")
        compose.mainClock.advanceTimeBy(1_500); capture("network-bar-3-open")
        compose.onNodeWithTag("network-edit").assertDoesNotExist()
        val add = compose.onNodeWithTag("network-add").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithTag("network-settings").fetchSemanticsNode().boundsInRoot
        val done = compose.onNodeWithTag("network-done").fetchSemanticsNode().boundsInRoot
        // The check takes the pencil's place; plus and gear extend to the left.
        assertEquals(closed.center.x, done.center.x, 2f)
        assertTrue(add.right <= done.left + 1f && settings.right <= add.left + 1f)
        // The boxes of gear and plus fill the bar; "Fertig" keeps its own room on the right.
        assertTrue("check apart: $add $done", done.left - add.right >= done.width / 5f)
        assertTrue("boxes side by side: $settings $add", add.left - settings.right in 0f..(done.width / 2f))
        assertTrue("the bar grew to the left", settings.left < closed.left)
        compose.onNodeWithContentDescription("Favorit hinzufügen").performClick()
        compose.onNodeWithContentDescription("Favoriten-Einstellungen").performClick()
        compose.mainClock.advanceTimeBy(120); capture("network-bar-4-tapped")
        compose.onNodeWithContentDescription("Fertig").performClick()
        compose.mainClock.advanceTimeBy(80); capture("network-bar-5-closing")
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("network-edit").assertExists()
        compose.onNodeWithTag("network-done").assertDoesNotExist()
        assertEquals(listOf("edit", "add", "settings", "done"), log)
        compose.mainClock.autoAdvance = true
    }

    @Test fun theCheckIsHighlightedWithTheAccentColour() {
        var primary = 0
        compose.setContent {
            OpenVTheme(AppSettings()) {
                primary = MaterialTheme.colorScheme.primary.toArgb()
                Surface { Box(Modifier.padding(20.dp)) { NetworkEditBar(true, {}, {}, {}, {}) } }
            }
        }
        compose.waitForIdle()
        val done = compose.onNodeWithTag("network-done").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            // A point inside the button, off the check glyph.
            val pixel = bitmap.getPixel((done.left + done.width * .2f).toInt(), (done.top + done.height * .5f).toInt())
            bitmap.recycle()
            assertEquals(primary, pixel)
        }
        assertEquals(NetworkEditBarGeometry.OPEN_HEIGHT, 60.dp)
    }

    @Test fun theOpenBarFollowsDarkModeAndCutCornerThemes() {
        var settings by mutableStateOf(AppSettings(themeMode = dev.ambivalenton.openv.data.model.ThemeMode.DARK))
        compose.setContent {
            OpenVTheme(settings) {
                Surface { Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.End) { NetworkEditBar(true, {}, {}, {}, {}) } }
            }
        }
        compose.waitForIdle(); capture("network-bar-dark")
        compose.runOnIdle { settings = AppSettings(appTheme = dev.ambivalenton.openv.data.model.AppTheme.ART_DECO) }
        compose.waitForIdle(); capture("network-bar-art-deco")
        compose.onNodeWithTag("network-done").assertHasClickAction()
    }

    @Test fun quietModeOpensWithoutMotionAndKeepsEveryAction() {
        val log = mutableListOf<String>()
        compose.mainClock.autoAdvance = false
        compose.setContent { Header(AppSettings(quietMode = true), log) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("network-edit").performClick()
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("network-done").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("network-edit").assertExists()
        assertEquals(listOf("edit", "done"), log)
        compose.mainClock.autoAdvance = true
    }

    @Test fun animatedIconButtonsKeepTheirActionAndLabel() {
        var refreshed = 0
        compose.setContent {
            OpenVTheme(AppSettings()) {
                Surface { AnimatedIconButton({ refreshed++ }, R.drawable.ic_refresh, "Aktuelle Daten laden", IconTapMotion.SPIN) }
            }
        }
        compose.onNodeWithContentDescription("Aktuelle Daten laden").performClick().performClick()
        compose.runOnIdle { assertEquals(2, refreshed) }
    }
}
