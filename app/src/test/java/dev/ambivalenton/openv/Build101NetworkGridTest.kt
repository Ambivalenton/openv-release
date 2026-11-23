// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.NetworkCountdown
import dev.ambivalenton.openv.ui.network.NetworkPlan
import dev.ambivalenton.openv.ui.network.NetworkPlanActions
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

/** Build 101 · in the plan's edit mode the free grid points are small grey "+" instead of dots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build101NetworkGridTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = java.time.ZonedDateTime.of(2027, 1, 15, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))
    private val home = Favorite("z", "Zuhause", Location("Herne", "z", 51.54, 7.22, "T"), icon = FavoriteIcon.HOME, accentSeed = 0xFF245EA8)
    private val work = Favorite("a", "Arbeit", Location("Dortmund", "a", 51.51, 7.46, "T"), icon = FavoriteIcon.WORK, accentSeed = 0xFF404B92)
    private val side = 360.dp
    private val actions = NetworkPlanActions(onNodeTap = {}, onNodeHold = {}, onEdgeTap = {}, onEdgeMenu = {}, onMove = { _, _ -> },
        onConnect = { _, _ -> null }, onFreePoint = {}, onToTray = {}, onConnectRequest = {})

    private var theme by mutableStateOf<AppTheme?>(null)
    private var editing by mutableStateOf(true)

    private fun show() = compose.setContent {
        val store = StoredState(favorites = listOf(home, work), settings = AppSettings(quietMode = true, appTheme = theme),
            network = TransitNetwork(listOf(NetworkNode("z", GridPoint(1, 4)), NetworkNode("a", GridPoint(4, 4))),
                listOf(NetworkEdge("line", "z", "a")), columns = 6, rows = 6))
        OpenVTheme(store.settings) { Surface { NetworkPlan(store, now, editing, null, NetworkCountdown(), null, actions, Modifier.width(side)) } }
    }

    private fun plan(theme: AppTheme?, editing: Boolean): Bitmap {
        this.theme = theme; this.editing = editing
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("network-plan").fetchSemanticsNode().boundsInWindow
        return compose.runOnIdle {
            val view = compose.activity.window.decorView
            val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(full))
            Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        }
    }

    private fun contrast(a: Int, b: Int) = ColorUtils.calculateContrast(a or 0xff000000.toInt(), b or 0xff000000.toInt())

    @Test fun freePointsShowAPlusInEditModeAndNothingCrossShapedOutsideIt() {
        show()
        for (theme in listOf(null, AppTheme.CONFETTI_STUDIO, AppTheme.AMOLED_BLACK)) {
            val edit = plan(theme, editing = true)
            val pitch = edit.width / 6f
            val cx = (2.5f * pitch).toInt(); val cy = (1.5f * pitch).toInt()
            val arm = with(compose.density) { 3.dp.toPx() }.toInt()
            val paper = edit.getPixel(cx + (pitch * .35f).toInt(), cy + (pitch * .35f).toInt())
            val name = theme?.name ?: "standard"
            // Both arms stand out from the paper, the space between them does not: a "+", not a dot or a square.
            for ((dx, dy) in listOf(arm to 0, -arm to 0, 0 to arm, 0 to -arm))
                assertTrue("$name arm $dx,$dy", contrast(edit.getPixel(cx + dx, cy + dy), paper) > 1.25)
            assertTrue("$name between the arms", contrast(edit.getPixel(cx + arm, cy + arm), paper) < 1.1)
            val file = File("../audit/build101/plan-edit-${name.lowercase()}.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { edit.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Outside edit mode the plus is gone (at most the faint resting dot of the theme's sheet).
            val live = plan(theme, editing = false)
            val livePaper = live.getPixel(cx + (pitch * .35f).toInt(), cy + (pitch * .35f).toInt())
            assertTrue("$name live arm", contrast(live.getPixel(cx + arm, cy), livePaper) < 1.1)
            // Build 102: no resting dot either – the live plan shows no grid at all.
            assertTrue("$name live dot", contrast(live.getPixel(cx, cy), livePaper) < 1.05)
        }
    }
}
