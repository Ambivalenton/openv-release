// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/** Build 108 · "Eigene Farbe" as a colour studio, and the mixing palette springing open. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build108ColorStudioTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val blue = 0xFF245EA8L

    private fun hue(color: Long) = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toInt(), it) }

    /** The pure helpers: HSV in and out, steps of one hue from light to dark, partners around the colour wheel. */
    @Test fun colourHelpers() {
        assertEquals(0xFFFF0000L, studioColor(0f, 1f, 1f))
        assertEquals(0x80FFFFFFL, studioColor(120f, 0f, 1f, .5f))
        assertEquals("out of range is clamped", 0xFF000000L, studioColor(400f, 2f, -1f))
        val steps = colorToneSteps(blue)
        assertEquals(6, steps.size)
        val tones = steps.map { Hct.fromInt(it.toInt()).tone }
        assertTrue("light to dark: $tones", tones.zipWithNext().all { (a, b) -> a > b })
        val source = Hct.fromInt(blue.toInt()).hue
        steps.drop(1).dropLast(1).forEach { assertTrue("same hue", angle(Hct.fromInt(it.toInt()).hue, source) < 12.0) }
        val partners = colorHarmonies(blue)
        assertEquals(4, partners.size)
        assertTrue("opposite", abs(angle(Hct.fromInt(partners[2].toInt()).hue, source) - 180.0) < 15.0)
        assertTrue("neighbour", abs(angle(Hct.fromInt(partners[1].toInt()).hue, source) - 30.0) < 12.0)
        // A grey still gets colourful partners.
        assertTrue(colorHarmonies(0xFF808080L).all { Hct.fromInt(it.toInt()).chroma > 15.0 })
    }

    private fun angle(a: Double, b: Double) = ((a - b + 540.0) % 360.0 - 180.0).let(::abs)

    private fun studio(seed: Long = blue, alpha: Boolean = false, dark: Boolean = false, onConfirm: (Long) -> Unit = {}, onDismiss: () -> Unit = {}) {
        val overlay = AppOverlayState()
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true, themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalAppOverlay provides overlay) {
                    AppOverlayHost(overlay, Modifier.fillMaxSize()) {
                        Surface(Modifier.fillMaxSize()) {}
                        AdvancedColorDialog(seed, alpha, onConfirm, onDismiss, themeAccentColor = 0xFF6750A4L,
                            recentColors = listOf(0xFFC2185BL, 0xFF2E7D32L, 0xFFEF6C00L))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The field follows the finger at once – no long press – and "Übernehmen" returns what it shows. */
    @Test fun theFieldFollowsTheFingerAtOnce() {
        var confirmed: Long? = null
        studio(onConfirm = { confirmed = it })
        val h = hue(blue)[0]
        compose.onNodeWithTag("color-field").performTouchInput { down(center); moveTo(topRight); up() }
        compose.onNodeWithTag("advanced-color-apply").performClick()
        compose.runOnIdle {
            val hsv = hue(confirmed!!)
            assertEquals("hue kept", h, hsv[0], 1.5f)
            assertEquals("full strength", 1f, hsv[1], .02f)
            assertEquals("full brightness", 1f, hsv[2], .02f)
        }
    }

    /** The rainbow strip turns the hue; a tap at its left end is red. */
    @Test fun theHueStripTurnsTheHue() {
        var confirmed: Long? = null
        studio(seed = 0xFF2E7D32L, onConfirm = { confirmed = it })
        compose.onNodeWithTag("hue-strip").performTouchInput { down(Offset(1f, center.y)); up() }
        compose.onNodeWithTag("advanced-color-apply").performClick()
        compose.runOnIdle { val hsv = hue(confirmed!!); assertTrue("red: ${hsv[0]}", hsv[0] < 3f || hsv[0] > 357f) }
    }

    /** A complete hex code takes over at once; "Vorher" brings the old colour back; "Abbrechen" changes nothing. */
    @Test fun hexBeforeAndCancel() {
        var confirmed: Long? = null
        var dismissed = 0
        studio(onConfirm = { confirmed = it }, onDismiss = { dismissed++ })
        compose.onNodeWithTag("color-hex").performTextReplacement("00a86b")
        compose.onNodeWithTag("advanced-color-apply").performClick()
        compose.runOnIdle { assertEquals(0xFF00A86BL, confirmed) }
        compose.onNodeWithTag("color-field").performTouchInput { down(bottomLeft); up() }
        compose.onNodeWithTag("color-before").performClick()
        compose.onNodeWithTag("advanced-color-apply").performClick()
        compose.runOnIdle { assertEquals("back to before", blue, confirmed) }
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    /** Suggestions: steps, partners, the design's accent and the recent colours, each one tap. */
    @Test fun suggestionsAreOneTap() {
        var confirmed: Long? = null
        studio(onConfirm = { confirmed = it })
        compose.onNodeWithText("Heller und dunkler").assertExists()
        compose.onNodeWithText("Passt dazu").assertExists()
        compose.onNodeWithContentDescription("Akzent des App-Designs, ${colorName(0xFF6750A4L)}").performScrollTo().performClick()
        compose.onNodeWithTag("advanced-color-apply").performClick()
        compose.runOnIdle { assertEquals(0xFF6750A4L, confirmed) }
        compose.onNodeWithContentDescription(colorName(0xFFC2185BL)).performScrollTo().performClick()
        compose.onNodeWithTag("advanced-color-apply").performClick()
        compose.runOnIdle { assertEquals(0xFFC2185BL, confirmed) }
    }

    /** With opacity: the strip at its left end is fully clear; a 6-digit code keeps the opacity chosen. */
    @Test fun opacityStrip() {
        var confirmed: Long? = null
        studio(alpha = true, onConfirm = { confirmed = it })
        compose.onNodeWithTag("alpha-strip").performTouchInput { down(Offset(1f, center.y)); moveTo(Offset(width * .5f, center.y)); up() }
        compose.onNodeWithTag("color-hex").performScrollTo().performTextReplacement("00A86B")
        compose.onNodeWithTag("advanced-color-apply").performClick()
        compose.runOnIdle {
            assertEquals("colour from the code", 0x00A86BL, confirmed!! and 0xFFFFFFL)
            assertEquals("about half clear", 128f, (confirmed!! ushr 24).toFloat(), 14f)
        }
    }

    /** TalkBack: the field and strips say what they hold and can be moved without dragging. */
    @Test fun accessible() {
        studio()
        compose.onNodeWithTag("color-field").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.CustomActions))
        compose.onNodeWithTag("hue-strip").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress))
        compose.onNodeWithTag("hue-strip").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.5f) }
        compose.onNodeWithTag("hue-strip").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "180 Grad"))
    }

    private fun spring(damping: Float, stiffness: Float) = TargetBasedAnimation(
        androidx.compose.animation.core.spring(dampingRatio = damping, stiffness = stiffness), Float.VectorConverter, 0f, 1f, AnimationVector1D(0f))

    /** The palette swells past its size a little and settles; width leads height; the wells pop in turn. */
    @Test fun thePaletteSpringsOpen() {
        val width = spring(PALETTE_GROW_DAMPING, PALETTE_GROW_STIFFNESS)
        val height = spring(PALETTE_RISE_DAMPING, PALETTE_RISE_STIFFNESS)
        val w = (0..900 step 8).map { width.getValueFromNanos(it * 1_000_000L) }
        val h = (0..900 step 8).map { height.getValueFromNanos(it * 1_000_000L) }
        assertTrue("overshoot ${w.max()}", w.max() in 1.04f..1.14f)
        assertTrue("height softer ${h.max()}", h.max() in 1.02f..w.max())
        assertTrue("width leads", w[15] > h[15])
        assertTrue("settled within 0.7 s", abs(w[88] - 1f) < .01f && abs(h[88] - 1f) < .01f)
        val well = spring(PALETTE_WELL_DAMPING, PALETTE_WELL_STIFFNESS)
        assertTrue("wells pop", (0..600 step 8).maxOf { well.getValueFromNanos(it * 1_000_000L) } in 1.1f..1.3f)
        assertTrue("all wells in within half a second of the palette", PALETTE_WELL_DELAY + 6 * PALETTE_WELL_STAGGER < 500)
    }

    /** Audit: the studio in light and dark, and the palette opening frame by frame. */
    @Test fun captureTheStudio() {
        fun save(name: String) = compose.runOnIdle {
            val v = compose.activity.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
            val f = File("../audit/build108/$name.png"); f.parentFile!!.mkdirs()
            f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
        }
        var dark by mutableStateOf(false)
        var alpha by mutableStateOf(false)
        val overlay = AppOverlayState()
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true, themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalAppOverlay provides overlay) {
                    AppOverlayHost(overlay, Modifier.fillMaxSize()) {
                        Surface(Modifier.fillMaxSize()) {}
                        key(dark, alpha) {
                            AdvancedColorDialog(if (alpha) 0xA0C2185BL else blue, alpha, {}, {}, themeAccentColor = 0xFF6750A4L,
                                recentColors = listOf(0xFFC2185BL, 0xFF2E7D32L, 0xFFEF6C00L, 0xFF00838FL))
                        }
                    }
                }
            }
        }
        compose.waitForIdle(); save("studio-light")
        dark = true; compose.waitForIdle(); save("studio-dark")
        alpha = true; compose.waitForIdle(); save("studio-alpha-dark")
    }

    /** The entry to the studio says what it offers and shows an own colour once one is chosen. */
    @Test fun theEntryShowsTheOwnColour() {
        var selected by mutableStateOf<Long?>(null)
        var open by mutableStateOf(false)
        compose.setContent {
            OpenVTheme(AppSettings(quietMode = true)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) { FavoriteColorControls(selected, open, { open = true }, { open = false }) { selected = it } }
                }
            }
        }
        compose.onNodeWithText("Eigene Farbe wählen").assertExists()
        compose.onNodeWithText("Frei mischen oder Hex-Code eingeben").assertExists()
        compose.onNodeWithTag("favorite-custom-color").performClick()
        compose.onNodeWithTag("color-hex").performTextReplacement("12AB34")
        compose.onNodeWithTag("advanced-color-apply").performClick()
        compose.runOnIdle { assertEquals(0xFF12AB34L, selected); assertFalse(open) }
        compose.onNodeWithText("Gemischt: ${colorName(0xFF12AB34L)}").assertExists()
        compose.runOnIdle {
            val v = compose.activity.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height / 2, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
            val f = File("../audit/build108/entry.png"); f.parentFile!!.mkdirs()
            f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Audit: the mixing palette opening, 16 frames of 32 ms. */
    @Test fun captureThePaletteOpening() {
        val fields = listOf(PaletteField("bg", "Hintergrund", 0xFF1B2733L, 0xFF1B2733L), PaletteField("fg", "Schrift", 0xFFF4F1E8L, 0xFFF4F1E8L),
            PaletteField("ac", "Akzent", 0xFFE5A100L, 0xFFE5A100L), PaletteField("ln", "Linien", 0xFF5FA8D3L, 0xFF5FA8D3L))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OpenVTheme(AppSettings()) {
                Surface(Modifier.fillMaxSize()) {}
                val overlay = remember { AppOverlayState() }
                CompositionLocalProvider(LocalAppOverlay provides overlay) {
                    AppOverlayHost(overlay, Modifier.fillMaxSize()) {
                        MixingPaletteOverlay("Farben", fields, fields.associate { it.key to it.initial }, null, {}, {}, {})
                    }
                }
            }
        }
        val shots = mutableListOf<Bitmap>()
        repeat(16) {
            compose.mainClock.advanceTimeBy(32)
            shots += compose.runOnIdle { val v = compose.activity.window.decorView
                Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) } }
        }
        compose.mainClock.autoAdvance = true
        val cols = 4; val scale = 3; val w = shots[0].width / scale; val h = shots[0].height / scale
        val sheet = Bitmap.createBitmap(w * cols, h * (shots.size / cols), Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(sheet)
        shots.forEachIndexed { i, b -> c.drawBitmap(Bitmap.createScaledBitmap(b, w, h, true), (i % cols * w).toFloat(), (i / cols * h).toFloat(), null); b.recycle() }
        val f = File("../audit/build108/palette-opening.png"); f.parentFile!!.mkdirs()
        f.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.waitForIdle()
        compose.onNodeWithTag("palette-field-ac").assertExists()
    }
}
