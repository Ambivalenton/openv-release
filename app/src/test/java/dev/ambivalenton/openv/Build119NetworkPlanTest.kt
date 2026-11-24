// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.NetworkCountdown
import dev.ambivalenton.openv.ui.network.*
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import java.io.File
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Build 119 · holding a line in edit mode makes it burst into confetti and removes it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build119NetworkPlanTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = java.time.ZonedDateTime.of(2027, 1, 15, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))
    private fun fav(id: String, label: String, lat: Double, icon: FavoriteIcon) = Favorite(id, label, Location(label, id, lat, 7.2, "T"), icon = icon)
    private val favorites = listOf(fav("z", "Zuhause", 51.54, FavoriteIcon.HOME), fav("a", "Arbeit", 51.51, FavoriteIcon.WORK),
        fav("b", "Schule", 51.50, FavoriteIcon.SCHOOL), fav("c", "Sport", 51.48, FavoriteIcon.STAR))
    // A horizontal line z–a and a vertical line b–c crossing it.
    private val network = TransitNetwork(listOf(NetworkNode("z", GridPoint(0, 3)), NetworkNode("a", GridPoint(5, 3)),
        NetworkNode("b", GridPoint(2, 0)), NetworkNode("c", GridPoint(2, 5))),
        listOf(NetworkEdge("h", "z", "a"), NetworkEdge("v", "b", "c")), columns = 6, rows = 6)

    @Test fun aHeldLineBurstsIntoConfettiAndIsRemoved() {
        val removed = mutableListOf<String>()
        val store = StoredState(favorites = favorites, settings = AppSettings(), network = network)
        compose.setContent { OpenVTheme(store.settings) { Surface {
            NetworkPlan(store, now, true, null, NetworkCountdown(), null, NetworkPlanActions(onEdgeDelete = { removed += it }), Modifier.width(360.dp))
        } } }
        compose.waitForIdle()
        val z = compose.onNodeWithTag("network-node-z").fetchSemanticsNode().boundsInRoot.center
        val a = compose.onNodeWithTag("network-node-a").fetchSemanticsNode().boundsInRoot.center
        val plan = compose.onNodeWithTag("network-plan").fetchSemanticsNode().boundsInRoot.topLeft
        // A point on the line z–a, away from the crossing in its middle.
        val on = Offset(z.x + (a.x - z.x) * .8f, z.y) - plan
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("network-plan").performTouchInput { down(on) }
        compose.mainClock.advanceTimeBy(250)
        assertTrue("nothing removed while the line only trembles", removed.isEmpty())
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("network-plan").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(listOf("h"), removed)
        compose.onNodeWithTag("network-confetti").assertExists()
        save("confetti")
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onAllNodesWithTag("network-confetti").assertCountEquals(0)
    }

    @Test fun confettiCoversTheWholeLineInItsColours() {
        val pieces = confettiAlong(listOf(Offset(0f, 0f), Offset(200f, 0f), Offset(200f, 100f)), Color.Red, Color.Blue, 8f, seed = 7)
        assertEquals(90, pieces.size)
        assertTrue(pieces.any { it.start.x < 30f } && pieces.any { it.start.y > 70f })
        assertTrue(pieces.count { it.color == Color.Red } > 15)
        // The burst starts in the middle and runs to both ends.
        val middle = pieces.minBy { it.delay }
        assertTrue(middle.start.x > 150f || middle.start.y < 30f)
        // They fall: later lower than at the start (gravity wins over the lift).
        val p = pieces.first()
        assertTrue(confettiPosition(p, 1.2f, 960f)!!.y > confettiPosition(p, .05f + p.delay, 960f)!!.y)
        assertNull(confettiPosition(pieces.maxBy { it.delay }, 0f, 960f)?.takeIf { pieces.maxOf { it.delay } > 0f })
    }

    @Test fun theLinesCrossOnThePlanAndTheVerticalLiesOnTop() {
        val store = StoredState(favorites = favorites, settings = AppSettings(), network = network)
        compose.setContent { OpenVTheme(store.settings) { Surface {
            NetworkPlan(store, now, false, null, NetworkCountdown(), null, NetworkPlanActions(), Modifier.width(360.dp))
        } } }
        compose.waitForIdle()
        save("crossing")
        // The geometry of the plan's own tracks: one crossing, h below v (drawn first).
        val h = sampleTrack(300f, 4f) { Offset(it, 150f) }
        val v = sampleTrack(300f, 4f) { Offset(130f, it) }
        val c = lineCrossings(listOf("h", "v"), mapOf("h" to h, "v" to v), 20f).single()
        assertEquals("h", c.lower)
    }

    private fun save(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        val file = File("../audit/build119/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
