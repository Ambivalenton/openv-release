// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.tutorial.TutorialPaths
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Build 104 · the open plan editor is twice as tall (gesture band above the tools), "Abfahrt/Ankunft"
 * steps aside for it, the plan keeps its place, and a favourite moved onto the delete zone is deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build104PlanEditTest {
    private val home = Favorite("home", "Zuhause", Location("Herne Bf", "h", 51.54, 7.22, "VRR", LocationKind.STOP), icon = FavoriteIcon.HOME, accentSeed = 0xFF245EA8)
    private val work = Favorite("work", "Arbeit", Location("Dortmund Hbf", "d", 51.517, 7.459, "VRR", LocationKind.STOP), icon = FavoriteIcon.WORK, accentSeed = 0xFFC2185B)
    private val state = MutableStateFlow(StoredState(onboardingVersion = 1, completedTutorials = TutorialPaths.all.map { it.id }.toSet(),
        favorites = listOf(home, work),
        network = TransitNetwork(listOf(NetworkNode("home", GridPoint(1, 3)), NetworkNode("work", GridPoint(4, 3))),
            listOf(NetworkEdge("line", "home", "work")), columns = 6, rows = 6),
        settings = AppSettings(originMode = OriginMode.MANUAL, manualOrigin = Location("Essen Hbf", "e", 51.45, 7.01, "VRR", LocationKind.STOP),
            autoRefresh = false, manualOriginChangedAt = System.currentTimeMillis(), networkHoldAction = NetworkHoldAction.MOVE)))
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = object : ExternalResource() {
        override fun before() {
            val store = object : androidx.datastore.core.DataStore<StoredState> {
                override val data = state
                override suspend fun updateData(transform: suspend (StoredState) -> StoredState) = transform(state.value).also { state.value = it }
            }
            val client = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request())
                .protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body("{}".toResponseBody()).build() }.build()
            (RuntimeEnvironment.getApplication() as OpenVApp).koin.loadModules(listOf(org.koin.dsl.module {
                single<LocationRepository> { LocationRepository(store) }
                single<NetworkClient> { NetworkClient(client) }
            }))
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val file = File("../audit/build104/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }
    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun openSearch() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Suchen") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
        capture("0-search")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("network-edit").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun theOpenEditorIsTwiceAsTallTheModeRowStepsAsideAndThePlanStays() {
        openSearch()
        compose.onNodeWithTag("time-mode-pill").assertExists()
        val planBefore = bounds("network-plan")
        val closed = bounds("network-edit").height
        capture("1-plan")
        compose.onNodeWithTag("network-edit").performClick()
        compose.waitForIdle()
        capture("2-plan-edit")
        // The plan keeps its place; the bar is twice its open row and reaches up into the mode row.
        assertEquals(planBefore.top, bounds("network-plan").top, 1f)
        val open = bounds("network-edit-bar")
        assertTrue("open ${open.height} vs closed $closed", open.height > closed * 2.2f)
        compose.onNodeWithTag("network-gesture-band", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Halten und schieben", useUnmergedTree = true).assertExists()
        // "Abfahrt/Ankunft" has left to the side (out of the semantics while away).
        compose.onNodeWithTag("time-mode-pill").assertDoesNotExist()
        compose.onNodeWithTag("network-done").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("time-mode-pill").assertExists()
        assertEquals(planBefore.top, bounds("network-plan").top, 1f)
    }

    /** Red deletes – unless the theme's own accent is red; then another signal colour stands out. */
    @Test fun theDeleteSignalStandsApartFromARedTheme() {
        fun hue(c: androidx.compose.ui.graphics.Color) = dev.ambivalenton.openv.color.utilities.Hct.fromInt(c.toArgb()).hue
        fun distance(a: Double, b: Double) = minOf(kotlin.math.abs(a - b), 360 - kotlin.math.abs(a - b))
        val error = androidx.compose.ui.graphics.Color(0xFFBA1A1A)
        val blue = androidx.compose.ui.graphics.Color(0xFF245EA8); val red = androidx.compose.ui.graphics.Color(0xFFC62828)
        assertEquals(error, dev.ambivalenton.openv.ui.network.deleteSignalFor(error, blue).first)
        val onRed = dev.ambivalenton.openv.ui.network.deleteSignalFor(error, red).first
        assertTrue("apart from the red accent: ${hue(onRed)}", distance(hue(onRed), hue(red)) >= 40)
    }

    @Test fun aFavouriteMovedOntoTheDeleteZoneIsDeleted() {
        openSearch()
        compose.onNodeWithTag("network-edit").performClick()
        compose.waitForIdle()
        val plan = bounds("network-plan")
        val pitch = plan.width / 6f
        val work = Offset(4.5f * pitch, 3.5f * pitch)
        compose.onNodeWithTag("network-plan").performTouchInput { down(work); advanceEventTime(900); moveBy(Offset(0f, -20f)) }
        compose.waitForIdle()
        // While moving, the tools give way to the delete zone.
        val zone = bounds("network-delete-zone")
        compose.onNodeWithText("Hierhin verschieben, um den Favoriten zu löschen", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("network-plan").performTouchInput { moveTo(Offset(zone.center.x - plan.left, zone.center.y - plan.top)) }
        compose.waitForIdle()
        compose.onNodeWithText("Loslassen zum Löschen", useUnmergedTree = true).assertExists()
        capture("3-delete-zone")
        compose.onNodeWithTag("network-plan").performTouchInput { up() }
        compose.waitForIdle()
        compose.waitUntil(5_000) { state.value.favorites.none { it.id == "work" } }
        assertEquals(listOf("home"), state.value.favorites.map { it.id })
        // The tools are back; "Rückgängig" is offered.
        compose.onNodeWithTag("network-delete-zone", useUnmergedTree = true).assertDoesNotExist()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Rückgängig").fetchSemanticsNodes().isNotEmpty() }
    }
}
