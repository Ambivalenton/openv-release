// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.SwapGhostOverlay
import dev.ambivalenton.openv.ui.components.SwapGhosts
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Build 127 · the swap as one motion: each ghost blends the look of the field it leaves into the one it becomes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build127SwapMorphTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun theGhostsMorphBetweenBothFieldLooks() {
        var k by mutableFloatStateOf(0f)
        var theme by mutableStateOf<AppTheme?>(null)
        // Places as the search screen measures them (px at xhdpi): the start chip above, the destination field below.
        val ghosts = SwapGhosts(Rect(40f, 230f, 782f, 370f), Rect(40f, 120f, 640f, 200f), "Hauptbahnhof, Ausgang Nord", "Stadtpark",
            originGps = true)
        compose.setContent { OpenVTheme(AppSettings(appTheme = theme)) { Surface(Modifier.fillMaxSize()) {
            SwapGhostOverlay(ghosts, { k })
        } } }
        for (t in listOf(null, AppTheme.SPACE_TERMINAL, AppTheme.TILE_COBALT)) {
            compose.runOnIdle { theme = t }
            for (p in listOf(0f, .35f, .65f, 1f)) {
                compose.runOnIdle { k = p }; compose.waitForIdle()
                save("${t?.name?.lowercase() ?: "default"}-${(p * 100).toInt()}")
            }
        }
    }

    private fun save(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        val f = File("../audit/build127/swap-$name.png"); f.parentFile!!.mkdirs()
        f.outputStream().use { Bitmap.createBitmap(b, 0, 0, b.width, 450).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
