// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.SwapPlacesArrow
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
/**
 * Build 126 · screenshot: after a swap the arrow sat outside its circle (lower right). Inside the drawing "center"
 * meant the whole drawing area, not the inset arrow: half a turn mirrored it about the wrong point. The round body
 * is now a little volume of liquid that the turning tips push.
 */
class Build126SwapTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun theArrowStaysInItsCircleAndTheBodyIsLiquid() {
        compose.setContent { OpenVTheme(AppSettings()) { Surface(Modifier.fillMaxSize()) {
            Box(Modifier.padding(40.dp)) { SwapPlacesArrow(true, {}, Modifier.size(48.dp)) }
        } } }
        compose.waitForIdle(); save("0")
        compose.onNodeWithTag("swap-places").performClick()
        compose.mainClock.autoAdvance = false
        for (t in listOf(100, 200, 300, 500)) { compose.mainClock.advanceTimeBy(100); save("t$t") }
        compose.mainClock.advanceTimeBy(3000); save("1")
        compose.mainClock.autoAdvance = true
        val before = bounds("0"); val after = bounds("1")
        // Same place before and after the half turn (the shape maps onto itself), within a few pixels.
        org.junit.Assert.assertTrue("before $before after $after", kotlin.math.abs(before.first - after.first) <= 8 && kotlin.math.abs(before.second - after.second) <= 8)
    }
    /** Centre of the arrow's dark body in a saved frame. */
    private fun bounds(name: String): Pair<Int, Int> {
        val b = android.graphics.BitmapFactory.decodeFile("../audit/build126/swap-$name.png")
        var minX = 9999; var maxX = -1; var minY = 9999; var maxY = -1
        for (y in 0 until b.height) for (x in 0 until b.width) {
            val c = b.getPixel(x, y); val r = (c shr 16) and 255; val bl = c and 255
            if (bl > r + 40 && r < 90) { minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y) }
        }
        return (minX + maxX) / 2 to (minY + maxY) / 2
    }

    @Test fun theLiquidKeepsItsVolumeAndSettles() {
        val ring = dev.ambivalenton.openv.ui.components.LiquidRing()
        // A tip sweeping past the upper left pushes out there; across, the rim draws in; the mean stays.
        repeat(30) { ring.step(1f / 60f, listOf(dev.ambivalenton.openv.ui.components.LiquidRing.Source((-3 * Math.PI / 4).toFloat(), 25f))) }
        val pushed = ring.offset[(ring.n * 5) / 8]; val across = ring.offset[ring.n / 8]
        org.junit.Assert.assertTrue("bulges where pushed: $pushed", pushed > .03f)
        org.junit.Assert.assertTrue("draws in elsewhere: ${ring.offset.toList()}", ring.offset.minOrNull()!! < -.01f)
        org.junit.Assert.assertEquals(0f, ring.offset.average().toFloat(), 1e-4f)
        // Let go: it wobbles back and comes to rest.
        var crossed = false; var last = pushed
        repeat(240) { ring.step(1f / 60f, emptyList()); val v = ring.offset[(ring.n * 5) / 8]; if (last > 0 && v < 0) crossed = true; last = v }
        org.junit.Assert.assertTrue("springs past rest once (liquid, not a damper)", crossed)
        org.junit.Assert.assertTrue(ring.resting)
        org.junit.Assert.assertTrue(across.isFinite())
    }

    private fun save(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        val small = Bitmap.createBitmap(b, 0, 0, 600, 600)
        val f = File("../audit/build126/swap-$name.png"); f.parentFile!!.mkdirs(); f.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
