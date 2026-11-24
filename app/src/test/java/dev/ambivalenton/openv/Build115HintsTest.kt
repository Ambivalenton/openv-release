// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.tracking.PinnedBubbleOverlay
import dev.ambivalenton.openv.ui.hints.*
import dev.ambivalenton.openv.ui.network.GESTURE_FILM_FIRST_MS
import dev.ambivalenton.openv.ui.network.GESTURE_FILM_MS
import dev.ambivalenton.openv.ui.network.NetworkEditBar
import dev.ambivalenton.openv.ui.theme.OpenVTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.io.File

/** Build 115 · wordless hints: gesture films in the plan's edit bar, first-use hints, the bubble on the right edge. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build115HintsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun hintsLastTheFirstHoursOfUseAndAtMostTwoWeeks() {
        val start = 1_800_000_000_000L
        val fresh = GestureHintProgress().used(10 * 60_000L, start)
        assertEquals(start, fresh.startedAt)
        assertTrue(fresh.active(start + 60_000L))
        assertFalse("three hours of use", fresh.copy(usedMillis = GESTURE_HINT_USE_MS).active(start))
        assertFalse("the current visit counts", fresh.active(start, GESTURE_HINT_USE_MS))
        assertFalse("two weeks at most", fresh.active(start + GESTURE_HINT_DAYS_MS + 1))
        // A wrong clock never ends the hints at once.
        assertEquals(6 * 60 * 60_000L, GestureHintProgress().used(Long.MAX_VALUE / 2, start).usedMillis)
    }

    @Test fun aHintEndsTheFirstTimeItsGestureIsUsed() {
        val learned = mutableListOf<String>()
        val state = GestureHintState().apply { configure(GestureHintProgress(), true) { learned += it } }
        compose.setContent {
            OpenVTheme(AppSettings()) {
                CompositionLocalProvider(LocalGestureHints provides state) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(48.dp).gestureHint("demo", HintKind.PULSE).testTag("target"))
                    }
                }
            }
        }
        assertTrue(state.shows("demo"))
        compose.onNodeWithTag("target").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("demo"), learned); assertFalse(state.shows("demo")) }
    }

    /** One cell after another: the words give way to a film, then the next cell plays. */
    @Test fun theGestureFilmsPlayOneAfterAnother() {
        compose.setContent { OpenVTheme(AppSettings()) { Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NetworkEditBar(true, {}, {}, {}, {}, Modifier.fillMaxWidth()) }
        } } }
        compose.mainClock.autoAdvance = false
        fun films() = (0..2).filter { compose.onAllNodesWithTag("network-gesture-film-$it", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(GESTURE_FILM_FIRST_MS - 300); assertEquals(emptyList<Int>(), films())
        compose.mainClock.advanceTimeBy(900); assertEquals(listOf(0), films())
        save("edit-bar-film-0")
        compose.mainClock.advanceTimeBy(GESTURE_FILM_MS + 2200L); assertEquals(listOf(1), films())
        compose.mainClock.advanceTimeBy(GESTURE_FILM_MS + 1600L); assertEquals(listOf(2), films())
        compose.mainClock.autoAdvance = true
    }

    @Test fun captureTheGestureFilms() {
        val w = 360; val h = 140
        val sheet = Bitmap.createBitmap(w * 6, h * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(android.graphics.Canvas(sheet))
        val scope = CanvasDrawScope()
        GestureDemo.entries.forEachIndexed { row, demo ->
            listOf(.08f, .25f, .45f, .6f, .78f, .95f).forEachIndexed { col, t ->
                canvas.save(); canvas.translate((col * w).toFloat(), (row * h).toFloat())
                scope.draw(Density(2.625f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                    drawRect(Color(0xFFE6ECF5)); drawGestureDemo(demo, t, Color(0xFF2D5DA8), Color(0xFF1B2430))
                }
                canvas.restore()
            }
        }
        val file = File("../audit/build115/films.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Docked right, the window is anchored at its right edge: opening the card never moves it. */
    @Test fun theBubbleOnTheRightStaysWhereItIsWhileTheCardOpens() {
        val context = RuntimeEnvironment.getApplication()
        val now = 1_800_000_000_000L
        val a = Location("Start", "a", 51.5, 7.2, "Test"); val b = Location("Ziel", "b", 51.6, 7.3, "Test")
        val trip = Trip("t", listOf(TripSegment("leg", a, b, now + 600_000, now + 1_200_000, LineType.BUS, "312")), "Test", now)
        val overlay = PinnedBubbleOverlay(context, kotlinx.coroutines.flow.MutableStateFlow(false)) {}
        overlay.update(PinnedJourneyState(session = PinnedJourneySession(favorite = Favorite("f", "Ziel", b), query = JourneyQuery(a, b, now),
            initialTrip = trip, routing = AppSettings())), AppSettings())
        overlay.show()
        fun params() = Shadow.extract<ShadowWindowManagerImpl>(context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager)
            .views.map { it.layoutParams as WindowManager.LayoutParams }.single { it.title == "Öpen-V bubble" }
        shadowOf(Looper.getMainLooper()).idle()
        val closed = params()
        assertEquals(Gravity.TOP or Gravity.END, closed.gravity)
        val x = closed.x
        val toggle = PinnedBubbleOverlay::class.java.getDeclaredMethod("setExpanded", Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        toggle.invoke(overlay, true); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("open: same right edge", x, params().x)
        toggle.invoke(overlay, false); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("closed again: same right edge", x, params().x)
        overlay.hide()
    }

    private fun save(name: String) = compose.runOnIdle {
        val v = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        val file = File("../audit/build115/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
