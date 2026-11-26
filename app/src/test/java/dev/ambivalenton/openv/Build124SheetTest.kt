// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
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
 * Build 124 · screenshot: "Favoriten-Einstellungen" opened from the plan editor ended high above a wide dark
 * band. The pill is aside while the plan is edited; the page keeps its room, the sheet no longer does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build124SheetTest {
    private val home = Favorite("home", "Zuhause", Location("Herne Bf", "h", 51.54, 7.22, "VRR", LocationKind.STOP), icon = FavoriteIcon.HOME, accentSeed = 0xFF245EA8)
    private val state = MutableStateFlow(StoredState(onboardingVersion = 1, completedTutorials = TutorialPaths.all.map { it.id }.toSet(),
        favorites = listOf(home), network = TransitNetwork(listOf(NetworkNode("home", GridPoint(1, 3))), emptyList(), columns = 6, rows = 6),
        settings = AppSettings(originMode = OriginMode.MANUAL, manualOrigin = Location("Essen Hbf", "e", 51.45, 7.01, "VRR", LocationKind.STOP),
            autoRefresh = false, manualOriginChangedAt = System.currentTimeMillis())))
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

    @Test fun theFavouriteSettingsReachDownWhileThePlanIsEdited() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Suchen") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("network-edit").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("network-edit").performClick(); compose.waitForIdle()
        compose.onNodeWithTag("network-settings", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val sheet = compose.onNodeWithTag("favorite-settings-sheet").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        // Above the system bar with a small margin, not above the empty room of the pill (88 dp).
        assertTrue("gap below the sheet: ${(root.bottom - sheet.bottom) / density} dp", (root.bottom - sheet.bottom) / density < 40f)
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            val file = File("../audit/build124/favorite-sheet.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        }
    }
}
