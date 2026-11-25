// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.WaypointOrderOptimizer
import dev.ambivalenton.openv.data.repository.waypointDetours
import dev.ambivalenton.openv.ui.components.WaypointPlannerDialog
import dev.ambivalenton.openv.ui.components.WaypointRoutingGateway
import dev.ambivalenton.openv.ui.components.plannedWaypointOrder
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Build 120 · a stop off the direct way is a detour the person chose, never an error. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build120WaypointTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun stop(name: String, lat: Double, lon: Double) = Location(name, name.lowercase(), lat, lon, "VRR", LocationKind.STOP)
    private val bfw = Location("Berufsförderungswerk Dortmund, Hacheneyer Straße 180, 44265 Dortmund", null, 51.4820, 7.4990, kind = LocationKind.ADDRESS)
    private val home = Location("Rottbruchstraße 41, 44625 Herne", null, 51.5300, 7.2050, kind = LocationKind.ADDRESS)
    private val duesseldorf = stop("Düsseldorf Hbf", 51.2200, 6.7940)
    private val juelich = stop("Jülich, Bf", 50.9220, 6.3610)
    private val bochum = stop("Bochum Hbf", 51.4780, 7.2230)
    private val unna = stop("Unna Bf", 51.5370, 7.6890)

    @Test fun aStopBeyondTheDestinationIsVisitedNotRefused() {
        assertEquals(listOf(duesseldorf), WaypointOrderOptimizer().order(bfw, home, listOf(duesseldorf)))
        assertEquals(listOf(duesseldorf), plannedWaypointOrder(bfw, home, listOf(duesseldorf), WaypointOrder.OPTIMIZED))
    }

    @Test fun severalStopsKeepAForwardOrderWithTheDetoursAtTheEnds() {
        // Unna lies behind the start, Bochum on the way, Düsseldorf and Jülich beyond the destination.
        val order = WaypointOrderOptimizer().order(bfw, home, listOf(duesseldorf, bochum, unna, juelich))
        assertEquals(4, order.size)
        assertEquals(unna, order.first())
        assertTrue(order.indexOf(bochum) < order.indexOf(duesseldorf))
        assertEquals(setOf(duesseldorf, juelich), order.takeLast(2).toSet())
    }

    @Test fun detoursAreNamedQuietly() {
        assertEquals(listOf(duesseldorf), waypointDetours(bfw, home, listOf(duesseldorf, bochum)))
        assertTrue(waypointDetours(bfw, home, listOf(bochum)).isEmpty())
        // Without coordinates nothing is claimed.
        assertTrue(waypointDetours(bfw, home, listOf(Location("Irgendwo"))).isEmpty())
    }

    /** The dialog with a detour stop in a dark monospace design: the hint is quiet, no error, the field is visible. */
    @Test fun theDialogNamesTheDetourWithoutAnError() {
        val now = System.currentTimeMillis()
        val trip = Trip("t", listOf(TripSegment("re", bfw, home, now + 3_600_000, now + 5_400_000, LineType.REGIONAL, "RE3")), "VRR", now,
            waypoints = listOf(duesseldorf), waypointDwellMinutes = mapOf(waypointIdentity(duesseldorf) to 30f))
        val query = JourneyQuery(bfw, home, now, waypoints = listOf(duesseldorf))
        val theme = AppTheme.entries.firstOrNull { it.name.contains("TERMINAL") || it.name.contains("MONO") } ?: AppTheme.entries.first()
        val overlay = dev.ambivalenton.openv.ui.components.AppOverlayState()
        compose.setContent { OpenVTheme(AppSettings(quietMode = true, appTheme = theme)) {
            androidx.compose.runtime.CompositionLocalProvider(dev.ambivalenton.openv.ui.components.LocalAppOverlay provides overlay) {
                dev.ambivalenton.openv.ui.components.AppOverlayHost(overlay, Modifier.fillMaxSize()) {
                    Surface(Modifier.fillMaxSize()) {}
                    WaypointPlannerDialog(query, trip, WaypointRoutingGateway { trip }, {}, { _, _ -> })
                }
            }
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("waypoint-detour").assertExists()
        compose.onAllNodesWithText("Rückfahrt", substring = true).assertCountEquals(0)
        compose.onNodeWithTag("waypoint-search").assertExists()
        compose.runOnIdle {
            val v = compose.activity.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
            val f = File("../audit/build120/waypoints.png"); f.parentFile!!.mkdirs()
            f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
        }
    }
}
