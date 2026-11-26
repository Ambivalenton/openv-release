// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import java.io.File

/**
 * Build 124 · screenshot: at the start of a ride the wave was only a dot on a flat track – it only waved up to
 * the progress. Now it waves across the whole width, breathes with the situation and shows the progress
 * every few seconds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build124WaveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun theProgressShowsNowAndThen() {
        assertEquals(0f, progressReveal(0f), 1e-4f)
        assertTrue(progressReveal(.4f) > .5f)
        assertEquals(1f, progressReveal(2f), 1e-4f)
        assertTrue(progressReveal(3.7f) in .01f..0.99f)
        assertEquals(0f, progressReveal(5f), 1e-4f)
        assertEquals(0f, progressReveal(WAVE_REVEAL_EVERY - .1f), 1e-4f)
    }

    /** The share of the slot's columns in which the wave leaves the middle line (a flat line or a dot: almost none). */
    private fun wavingColumns(bitmap: Bitmap, top: Int, height: Int, left: Int, right: Int): Float {
        val background = bitmap.getPixel(left + 2, top + 1)
        var waving = 0
        for (x in left until right) {
            var ys = mutableListOf<Int>()
            for (y in top until top + height) if (bitmap.getPixel(x, y) != background) ys += y
            if (ys.isNotEmpty() && kotlin.math.abs(ys.average() - (top + height / 2.0)) > 2.0) waving++
        }
        return waving / (right - left).toFloat()
    }

    @Test fun atTheStartOfARideTheWaveSpansTheWholeWidth() {
        val moods = listOf(WaveMood.RIDING to 0f, WaveMood.WAITING to 0f, WaveMood.BOARD_NOW to .05f, WaveMood.RIDING_LATE to .4f,
            WaveMood.WALKING to .55f, WaveMood.HURRY to .6f, WaveMood.ARRIVING to .96f, WaveMood.DISRUPTED to .5f, WaveMood.PAUSED to .3f)
        compose.setContent { OpenVTheme(AppSettings(appTheme = AppTheme.CLASSIC_DARK)) { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                moods.forEach { (mood, p) -> Text("${mood.name} ${(p * 100).toInt()} %"); JourneyWave(p, mood) }
            }
        } } }
        compose.mainClock.autoAdvance = false
        // Between two showings of the progress: just the wave.
        compose.mainClock.advanceTimeBy(6_000)
        val node = compose.onNodeWithTag("journey-wave-riding").fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        val share = wavingColumns(bitmap, bounds.top.toInt(), bounds.height.toInt(), bounds.left.toInt() + 40, bounds.right.toInt() - 40)
        assertTrue("the wave waves across the width at 0 %: $share", share > .5f)
        save(bitmap, "between")
        compose.mainClock.advanceTimeBy(3_000) // the next showing of the progress begins at 10 s
        compose.mainClock.advanceTimeBy(1_600)
        save(compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }, "reveal")
        compose.mainClock.autoAdvance = true
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("../audit/build124/wave-$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
