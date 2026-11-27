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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build124WaveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
        compose.mainClock.advanceTimeBy(6_000)
        val node = compose.onNodeWithTag("journey-wave-riding").fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        val share = wavingColumns(bitmap, bounds.top.toInt(), bounds.height.toInt(), bounds.left.toInt() + 40, bounds.right.toInt() - 40)
        assertTrue("the wave waves across the width at 0 %: $share", share > .5f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun theRemainingTrackStaysDimAcrossTheOldRevealCycle() {
        compose.setContent {
            OpenVTheme(AppSettings(appTheme = AppTheme.CLASSIC_DARK)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) { JourneyWave(.35f, WaveMood.BOARD_NOW) }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        for (frame in listOf(500L, 1600L, 5000L, 5000L)) {
            compose.mainClock.advanceTimeBy(frame)
            val wave=compose.onNodeWithTag("journey-wave-board_now")
            val bounds=wave.fetchSemanticsNode().boundsInRoot
            val bitmap=compose.runOnIdle {
                val view=compose.activity.window.decorView
                Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
            }
            fun redAt(from:Float,to:Float) = (bounds.left.toInt()+(bounds.width*from).toInt() until
                bounds.left.toInt()+(bounds.width*to).toInt()).maxOf { x ->
                (bounds.top.toInt() until bounds.bottom.toInt()).maxOf { y -> android.graphics.Color.red(bitmap.getPixel(x,y)) }
            }
            assertTrue("travelled and remaining portions stay distinct", redAt(.1f,.25f)>redAt(.55f,.8f)+35)
            bitmap.recycle()
        }
        compose.mainClock.autoAdvance = true
    }
}
