// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Build 130 · screenshot (Weltraumgelee, Fredoka): the digits sat below the middle of the lens, and every modern
 * design had the same pill. The digits are now centred on their real glyph outlines, and each design family has
 * its own roller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build130RollerSkinTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.of("Europe/Berlin")

    @Test fun everyDesignFamilyHasItsOwnRoller() {
        assertEquals(RollerSkin.MATERIAL, rollerSkin(null))
        assertEquals(RollerSkin.GLASS, rollerSkin(AppTheme.LILAC_GLASS))
        assertEquals(RollerSkin.BRUTAL, rollerSkin(AppTheme.BRUTAL_POSTER))
        assertEquals(RollerSkin.SOFT, rollerSkin(AppTheme.SOFT_PEARL))
        assertEquals(RollerSkin.CLAY, rollerSkin(AppTheme.JELLY_SPACE))
        assertEquals(RollerSkin.SWISS, rollerSkin(AppTheme.SWISS_METRO))
        assertEquals(RollerSkin.EDITORIAL, rollerSkin(AppTheme.NEWSPRINT))
        assertEquals(RollerSkin.OUTLINE, rollerSkin(AppTheme.LINE_PENCIL))
        assertEquals(RollerSkin.TILE, rollerSkin(AppTheme.TILE_COBALT))
        assertEquals(RollerSkin.TILE, rollerSkin(AppTheme.BENTO_PORCELAIN))
        assertEquals(RollerSkin.JAPANDI, rollerSkin(AppTheme.JAPANDI_OAK))
        assertEquals(RollerSkin.ORGANIC, rollerSkin(AppTheme.ORGANIC_PEBBLE))
        assertEquals(RollerSkin.AURORA, rollerSkin(AppTheme.GLOW_VIOLET))
        assertEquals(RollerSkin.INK, rollerSkin(AppTheme.MONOCHROME_INK))
        // 8-bit designs get stepped pixels on their own colours instead of a black display.
        val pixel = AppTheme.entries.filter { it.language == DesignLanguage.PIXEL && appThemeGroup(it) != AppThemeGroup.RETRO }
        assertTrue(pixel.isNotEmpty())
        pixel.forEach { assertEquals(it.name, WheelLook.MODERN, wheelLook(it)); assertEquals(it.name, RollerSkin.PIXEL, rollerSkin(it)) }
        // Every design language of the catalogue reaches a skin of its own kind, not the generic one.
        val generic = AppTheme.entries.filter { wheelLook(it) == WheelLook.MODERN && rollerSkin(it) == RollerSkin.MATERIAL }
            .map { it.language }.toSet()
        assertEquals(setOf(DesignLanguage.MATERIAL), generic)
        assertTrue(RollerSkin.entries.all { skin -> AppTheme.entries.any { wheelLook(it) == WheelLook.MODERN && rollerSkin(it) == skin } })
    }

    /** The chosen minute's ink is centred on the lens in typefaces with very different proportions. */
    @Test fun theDigitsSitInTheMiddleOfTheLens() {
        var theme by mutableStateOf<AppTheme?>(null)
        val time = LocalDate.now(zone).plusDays(3).atTime(6, 14).atZone(zone).toInstant().toEpochMilli()
        compose.setContent {
            OpenVTheme(AppSettings(appTheme = theme)) { Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(16.dp)) { TimeRollers(time, {}, {}, {}) }
            } }
        }
        for (t in listOf(null, AppTheme.JELLY_SPACE, AppTheme.LAVENDER_FIELDS, AppTheme.TILE_COBALT, AppTheme.BRUTAL_POSTER, AppTheme.CLASSIC_DARK)) {
            compose.runOnIdle { theme = t }
            compose.mainClock.advanceTimeBy(2_000); compose.waitForIdle()
            val bounds = compose.onNodeWithTag("roller-minute").fetchSemanticsNode().boundsInRoot
            val offset = compose.onNodeWithTag("roller-minute").fetchSemanticsNode().let { it.positionInWindow - it.positionInRoot }
            val view = compose.activity.window.decorView
            val bitmap = compose.runOnIdle { Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) } }
            val left = (bounds.left + offset.x).toInt(); val width = bounds.width.toInt()
            val top = (bounds.top + offset.y).toInt(); val height = bounds.height.toInt()
            val middle = top + height / 2f
            // Inside the lens only the chosen value is drawn: rows where the middle differs strongly from the lens body at
            // its side (digits have full contrast; a soft highlight on the lens does not).
            // Only the lens itself (one row of the drum high); the neighbours above and below lie outside it.
            val band = (rollerRowPx(height.toFloat()) * .44f).toInt()
            val inked = (middle.toInt() - band..middle.toInt() + band).filter { y ->
                val reference = bitmap.getPixel(left + (width * .12f).toInt(), y)
                (left + (width * .3f).toInt()..left + (width * .7f).toInt()).count { x -> distance(bitmap.getPixel(x, y), reference) > 220 } > 1
            }
            assertTrue("$t: digits found", inked.size > 8)
            val inkMiddle = (inked.first() + inked.last()) / 2f
            assertTrue("$t: ink ${inked.first()}..${inked.last()} around $inkMiddle, lens middle $middle", abs(inkMiddle - middle) <= 1.6f)
            bitmap.recycle()
        }
    }

    private fun distance(a: Int, b: Int): Int =
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) + abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
            abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
}
