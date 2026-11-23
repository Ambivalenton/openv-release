// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.tutorial.TutorialPaths
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

/** Build 106 · the navigation pill: where it steps aside, and its capsule and symbols moving without jumps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class Build106PillTest {
    private val home = Favorite("home", "Zuhause", Location("Herne Bf", "h", 51.54, 7.22, "VRR", LocationKind.STOP), icon = FavoriteIcon.HOME, accentSeed = 0xFF245EA8)
    private val work = Favorite("work", "Arbeit", Location("Dortmund Hbf", "d", 51.517, 7.459, "VRR", LocationKind.STOP), icon = FavoriteIcon.WORK, accentSeed = 0xFFC2185B)
    private val state = MutableStateFlow(StoredState(onboardingVersion = 1, completedTutorials = TutorialPaths.all.map { it.id }.toSet(),
        favorites = listOf(home, work),
        network = TransitNetwork(listOf(NetworkNode("home", GridPoint(1, 3)), NetworkNode("work", GridPoint(4, 3))),
            listOf(NetworkEdge("line", "home", "work")), columns = 6, rows = 6),
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

    /** An opened connection and the plan editor take the whole height; pages, results, journey and settings keep the pill. */
    @Test fun wherePillStepsAside() {
        assertTrue(navigationPillAside("results", connectionOpen = true, previewOpen = false, pinnedOpen = false, planEditing = false))
        assertTrue(navigationPillAside("overview", connectionOpen = false, previewOpen = true, pinnedOpen = false, planEditing = false))
        assertTrue(navigationPillAside("search", connectionOpen = false, previewOpen = false, pinnedOpen = false, planEditing = true))
        assertFalse(navigationPillAside("results", connectionOpen = false, previewOpen = false, pinnedOpen = false, planEditing = false))
        assertFalse(navigationPillAside("overview", connectionOpen = false, previewOpen = true, pinnedOpen = true, planEditing = false))
        // Build 107: the settings hide it, the page "App-Design" shows it (its colours are being chosen).
        assertTrue(navigationPillAside("settings", connectionOpen = false, previewOpen = false, pinnedOpen = false, planEditing = false))
        assertFalse(navigationPillAside("settings", connectionOpen = false, previewOpen = false, pinnedOpen = false, planEditing = false, designPage = true))
        assertFalse(navigationPillAside("departures", connectionOpen = false, previewOpen = false, pinnedOpen = false, planEditing = false))
    }

    /** In the app: the pencil sends the pill down and out, "Fertig" brings it back, its tabs work again. */
    @Test fun thePillStepsAsideWhileThePlanIsEdited() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty() }
        val tab = hasText("Suchen") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.onNode(tab).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("expressive-pill-navigation").assertExists()
        val plan = compose.onNodeWithTag("network-plan").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("network-edit").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("expressive-pill-navigation").fetchSemanticsNodes().isEmpty() }
        // Build 107: the plan stays exactly where it was – its page keeps the pill's room while it is edited.
        val editing = compose.onNodeWithTag("network-plan").fetchSemanticsNode().boundsInRoot
        assertEquals("top", plan.top, editing.top, 1f); assertEquals("bottom", plan.bottom, editing.bottom, 1f)
        compose.onNodeWithTag("network-done").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("expressive-pill-navigation").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Abfahrten") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Abfahrten") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertIsSelected()
    }

    private fun spring(damping: Float, stiffness: Float, impulse: Float) = TargetBasedAnimation(
        androidx.compose.animation.core.spring(dampingRatio = damping, stiffness = stiffness), Float.VectorConverter, 0f, 0f, AnimationVector1D(impulse))

    /** The landing squash grows out of rest (no jump to −16 % in the first frame) and swings back. */
    @Test fun theCapsuleLandsWithoutAJump() {
        val land = spring(.38f, 430f, -PILL_LANDING_IMPULSE)
        val frames = (0..800 step 16).map { land.getValueFromNanos(it * 1_000_000L) }
        assertEquals("starts at rest", 0f, frames.first(), .001f)
        assertTrue("first frame only a small step: ${frames[1]}", kotlin.math.abs(frames[1]) < .08f)
        assertTrue("squashes by about 16 %: ${frames.min()}", frames.min() in -.2f..-.12f)
        assertTrue("swings back past rest", frames.max() > .01f)
        assertEquals("settles", 0f, frames.last(), .01f)
    }

    /** A hop: up and slim in the air, flat and wide on landing; pressed, the symbol gives a little. */
    @Test fun aHitSymbolHopsAndLands() {
        val air = hopPose(1f); val landing = hopPose(-.25f); val rest = hopPose(0f); val pressed = hopPose(0f, .86f)
        assertTrue(air.lift > .99f && air.scaleY > 1.1f && air.scaleX < 1f)
        assertTrue(landing.lift == 0f && landing.scaleY < 1f && landing.scaleX > 1f)
        assertEquals(1f, rest.scaleX, 0f); assertEquals(1f, rest.scaleY, 0f)
        assertEquals(.86f, pressed.scaleY, .001f)
        // The impulse lifts a full hop once: spring .42/520, peak near 1.
        val hop = spring(.42f, 520f, PILL_HOP_VELOCITY)
        val peak = (0..600 step 8).maxOf { hop.getValueFromNanos(it * 1_000_000L) }
        assertTrue("hop peak $peak", peak in .85f..1.15f)
        assertTrue("short: at rest within 0.7 s", kotlin.math.abs(hop.getValueFromNanos(700_000_000L)) < .03f)
    }

    /** Audit: a tab change and the pill stepping aside and back, frame by frame. */
    @Test fun captureThePillInMotion() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("main-tabs-pager").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Übersicht") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
        fun strip(name: String, frames: Int, step: Long, action: () -> Unit) {
            compose.mainClock.autoAdvance = false
            action()
            val shots = mutableListOf<android.graphics.Bitmap>()
            repeat(frames) { compose.mainClock.advanceTimeBy(step); shots += compose.runOnIdle {
                val v = compose.activity.window.decorView
                android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) } } }
            compose.mainClock.autoAdvance = true; compose.waitForIdle()
            val h = (130 * compose.density.density).toInt(); val w = shots[0].width
            val out = android.graphics.Bitmap.createBitmap(w, h * shots.size, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            shots.forEachIndexed { i, b -> c.drawBitmap(android.graphics.Bitmap.createBitmap(b, 0, b.height - h, w, h), 0f, (i * h).toFloat(), null); b.recycle() }
            val f = java.io.File("../audit/build106/$name.png"); f.parentFile!!.mkdirs()
            f.outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        strip("pill-tab-change", 16, 32) {
            compose.onNode(hasText("Abfahrten") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick() }
        compose.onNode(hasText("Suchen") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
        strip("pill-aside", 12, 32) { compose.onNodeWithTag("network-edit").performClick() }
        strip("pill-back", 16, 32) { compose.onNodeWithTag("network-done").performClick() }
    }
}
