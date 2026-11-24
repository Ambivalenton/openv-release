// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import dev.ambivalenton.openv.ui.theme.favoriteColorPresets
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Build 112 · the colour choice: scrolling never changes a colour, one sheet for choosing and
 * mixing, and a name for every colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w392dp-h760dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build112ColorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val blue = 0xFF245EA8L

    @Test fun everyColourHasAName() {
        assertEquals("Weiß", colorName(0xFFFFFFFFL))
        assertEquals("Schwarz", colorName(0xFF000000L))
        assertEquals("Grau", colorName(0xFF808080L))
        assertEquals("Kräftiges Rot", colorName(0xFFFF0000L))
        assertEquals("Kräftiges Blau", colorName(0xFF0000FFL))
        assertEquals("Blau", colorName(blue))
        assertEquals("Braun", colorName(0xFF8B4513L))
        assertEquals("Rosa", colorName(0xFFFFC0CBL))
        assertTrue(colorName(0xFF0B3D1FL).startsWith("Dunkles"))
        assertTrue(colorName(0xFFB9E6C9L).startsWith("Helles"))
        // Every colour of the wheel gets a name of its own family.
        (0 until 360 step 15).forEach { assertTrue(colorName(studioColor(it.toFloat(), .8f, .8f)).isNotBlank()) }
    }

    private fun studio(onConfirm: (Long) -> Unit = {}) {
        val overlay = AppOverlayState()
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true)) {
                CompositionLocalProvider(LocalAppOverlay provides overlay) {
                    AppOverlayHost(overlay, Modifier.fillMaxSize()) {
                        Surface(Modifier.fillMaxSize()) {}
                        AdvancedColorDialog(blue, true, onConfirm, {}, themeAccentColor = 0xFF6750A4L,
                            recentColors = listOf(0xFFC2185BL, 0xFF2E7D32L, 0xFFEF6C00L))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The mixing tools never sit inside something that scrolls – the reason scrolling changed colours before. */
    @Test fun mixingToolsStandStill() {
        studio()
        for (tag in listOf("color-field", "hue-strip", "alpha-strip"))
            compose.onNodeWithTag(tag).assert(!hasAnyAncestor(hasScrollAction()))
        compose.onNodeWithTag("color-hex").assert(hasAnyAncestor(hasScrollAction()))
    }

    /** A swipe over the suggestions scrolls them and leaves the colour exactly as it was. */
    @Test fun scrollingNeverChangesTheColour() {
        var confirmed: Long? = null
        studio(onConfirm = { confirmed = it })
        compose.onNodeWithTag("color-extras").performTouchInput { swipeUp(startY = bottom - 4f, endY = top + 4f, durationMillis = 300) }
        compose.onNodeWithTag("color-extras").performTouchInput { swipeDown(startY = top + 4f, endY = bottom - 4f, durationMillis = 300) }
        compose.onNodeWithTag("advanced-color-apply").performClick()
        compose.runOnIdle { assertEquals(blue, confirmed) }
    }

    /** As in the app: the sheet is hosted in the activity's own overlay, not in a window of its own. */
    private fun sheet(seed: Long?, onSave: (Long?) -> Unit) {
        val overlay = AppOverlayState()
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true)) {
                CompositionLocalProvider(LocalAppOverlay provides overlay) {
                    AppOverlayHost(overlay, Modifier.fillMaxSize()) {
                        Surface(Modifier.fillMaxSize()) {}
                        ColorPickerSheet("Akzentfarbe", seed, "Designstandard", onSave, {},
                            previewContent = { Text("Vorschau") })
                    }
                }
            }
        }
    }

    /** Choose, switch to mixing, mix, go back to "Vorher", apply: one sheet, no dialog on top. */
    @Test fun oneSheetForChoosingAndMixing() {
        var saved: Long? = -1L
        sheet(blue) { saved = it }
        compose.waitForIdle()
        compose.onNodeWithTag("color-hero").assertExists()
        compose.onNodeWithTag("color-name").assertTextEquals(colorName(blue))
        compose.onNodeWithTag("color-mode-choose").assertIsSelected()
        // A preset changes the hero at once.
        val preset = favoriteColorPresets[3]
        compose.onNodeWithContentDescription(preset.name).performScrollTo().performClick()
        compose.onNodeWithTag("color-name").assertTextEquals(colorName(preset.seed))
        // Mixing continues from the chosen colour; the field is not inside a scroll container.
        compose.onNodeWithTag("color-mode-mix").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("color-field").assert(!hasAnyAncestor(hasScrollAction()))
        compose.onNodeWithTag("color-field").performTouchInput { down(center); moveTo(topRight); up() }
        compose.onAllNodesWithTag("advanced-color-dialog").assertCountEquals(0)
        compose.onNodeWithTag("color-picker-apply").performClick()
        compose.runOnIdle {
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(saved!!.toInt(), it) }
            val presetHue = FloatArray(3).also { android.graphics.Color.colorToHSV(preset.seed.toInt(), it) }[0]
            assertEquals("hue of the chosen preset", presetHue, hsv[0], 1.5f)
            assertEquals(1f, hsv[1], .02f); assertEquals(1f, hsv[2], .02f)
        }
    }

    /** "Vorher" brings the starting colour back; a tap right into a corner reaches black; the design's own colour is a choice of its own. */
    @Test fun beforeAndTheDesignsOwnColour() {
        var saved: Long? = -1L
        sheet(blue) { saved = it }
        compose.onNodeWithTag("color-mode-mix").performClick()
        compose.onNodeWithTag("color-field").performTouchInput { down(Offset(2f, height - 2f)); up() }
        compose.onNodeWithTag("color-name").assertTextEquals("Schwarz")
        compose.onNodeWithTag("color-before").performClick()
        compose.onNodeWithTag("color-name").assertTextEquals(colorName(blue))
        compose.onNodeWithTag("color-mode-choose").performClick()
        compose.onNodeWithText("Designstandard").performScrollTo().performClick()
        compose.onNodeWithTag("color-picker-apply").performClick()
        compose.runOnIdle { assertNull(saved) }
    }

    /** Audit: the sheet choosing and mixing, the dialog. */
    @Test fun captureTheColourChoice() {
        fun save(name: String) = compose.runOnIdle {
            val v = compose.activity.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
            val f = File("../audit/build112/$name.png"); f.parentFile!!.mkdirs()
            f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
        }
        sheet(blue) {}
        compose.waitForIdle(); save("sheet-choose")
        compose.onNodeWithTag("color-mode-mix").performClick(); compose.waitForIdle(); save("sheet-mix")
        compose.onNodeWithTag("color-field").performTouchInput { down(Offset(width * .7f, height * .45f)) }
        compose.mainClock.advanceTimeBy(400); save("sheet-mix-loupe")
        compose.onNodeWithTag("color-field").performTouchInput { up() }
    }
}
