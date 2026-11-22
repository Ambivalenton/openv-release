// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.NetworkCountdown
import dev.ambivalenton.openv.ui.components.OeffiTimelineGraph
import dev.ambivalenton.openv.ui.network.NetworkPlan
import dev.ambivalenton.openv.ui.network.NetworkPlanActions
import dev.ambivalenton.openv.ui.screens.FavoriteSettingsContent
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

/** Build 74 on screen: hold actions of the plan, their setting, and the chart's chip/bar layering. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class, qualifiers = "de-rDE-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Build74UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = java.time.ZonedDateTime.of(2027, 1, 15, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))
    private val home = Favorite("z", "Zuhause", Location("Herne", "z", 51.54, 7.22, "T"), icon = FavoriteIcon.HOME, accentSeed = 0xFF245EA8, shape = IconStyle.HEART)
    private val work = Favorite("a", "Arbeit", Location("Dortmund", "a", 51.51, 7.46, "T"), icon = FavoriteIcon.WORK, shape = IconStyle.FLOWER)
    private val gym = Favorite("f", "Fitness", Location("Bochum", "f", 51.48, 7.21, "T"), icon = FavoriteIcon.FITNESS_CENTER, shape = IconStyle.GHOST)
    private fun store(action: NetworkHoldAction, pinned: String? = null) = StoredState(favorites = listOf(home, work, gym),
        settings = AppSettings(quietMode = true, networkHoldAction = action), overviewFavoriteId = pinned,
        network = TransitNetwork(listOf(NetworkNode("z", GridPoint(2, 5)), NetworkNode("a", GridPoint(8, 5)), NetworkNode("f", GridPoint(2, 9))),
            listOf(NetworkEdge("line", "z", "a")), columns = 12, rows = 12))
    private val side = 360.dp
    private val calls = mutableListOf<String>()
    private var editing by mutableStateOf(false)
    /** One setContent per test; later screens replace the hosted content. */
    private var content by mutableStateOf<@Composable () -> Unit>({})
    private var started = false
    private fun host(block: @Composable () -> Unit) {
        content = block
        if (!started) { started = true; compose.setContent { content() } } else compose.waitForIdle()
    }
    private fun show(state: StoredState) {
        editing = false
        host {
            OpenVTheme(state.settings) {
                Surface {
                    NetworkPlan(state, now, editing, null, NetworkCountdown(), null, NetworkPlanActions(
                        onNodeTap = { calls += "tap:$it" }, onNodeHold = { calls += "hold:$it" },
                        onMove = { id, p -> calls += "move:$id:${p.x},${p.y}" }, onToTray = { calls += "tray:$it" },
                        onEditRequest = { calls += "edit"; editing = true }), Modifier.width(side))
                }
            }
        }
    }
    private fun center(p: GridPoint): Offset { val pitch = with(compose.density) { side.toPx() } / 12f; return Offset((p.x + .5f) * pitch, (p.y + .5f) * pitch) }
    private fun capture(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        val file = File("../audit/build74/images/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    @Test fun holdingAFavouriteSwitchesToEditModeAndTheFavouriteFollowsTheFinger() {
        show(store(NetworkHoldAction.MOVE))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("network-plan").performTouchInput { down(center(GridPoint(2, 9))) }
        compose.mainClock.advanceTimeBy(1_000)
        // Still the same gesture: edit mode is on, the grid is there, nothing was dropped yet.
        compose.runOnIdle { assertEquals(listOf("edit"), calls); assertTrue(editing) }
        // From here frames run normally (a paused clock does not lay out the moving node).
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("network-plan").performTouchInput {
            repeat(10) { i -> moveTo(center(GridPoint(2, 9)) + (center(GridPoint(6, 9)) - center(GridPoint(2, 9))) * ((i + 1) / 10f)); advanceEventTime(16) }
        }
        compose.waitForIdle()
        // The lifted favourite is under the finger, not at its old field.
        val plan = compose.onNodeWithTag("network-plan").fetchSemanticsNode().boundsInRoot
        val lifted = compose.onNodeWithTag("network-node-f").fetchSemanticsNode().boundsInRoot.center - plan.topLeft
        assertEquals(center(GridPoint(6, 9)).x, lifted.x, 4f)
        assertEquals(center(GridPoint(6, 9)).y, lifted.y, 4f)
        capture("hold-move-dragging")
        compose.onNodeWithTag("network-plan").performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf("edit", "move:f:6,9"), calls); assertTrue(editing) }
        // In edit mode a tap edits as before.
        calls.clear()
        compose.onNodeWithTag("network-plan").performTouchInput { click(center(GridPoint(2, 5))) }
        compose.runOnIdle { assertEquals(listOf("tap:z"), calls) }
    }

    @Test fun aHoldWithoutMovingOnlyOpensEditMode() {
        show(store(NetworkHoldAction.MOVE))
        compose.onNodeWithTag("network-plan").performTouchInput { longClick(center(GridPoint(8, 5))) }
        compose.runOnIdle { assertEquals(listOf("edit"), calls); assertTrue(editing) }
    }

    @Test fun nothingSwallowsTheHoldAndATapStillSearches() {
        show(store(NetworkHoldAction.NOTHING))
        compose.onNodeWithTag("network-plan").performTouchInput { longClick(center(GridPoint(8, 5))) }
        compose.runOnIdle { assertTrue(calls.isEmpty()); assertFalse(editing) }
        compose.onNodeWithTag("network-plan").performTouchInput { click(center(GridPoint(8, 5))) }
        compose.runOnIdle { assertEquals(listOf("tap:a"), calls) }
        val node = compose.onNodeWithTag("network-node-a").fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsActions.OnLongClick))
    }

    @Test fun pinAndOverviewHoldsReachTheScreenAndTalkBackNamesThem() {
        show(store(NetworkHoldAction.PIN))
        compose.onNodeWithTag("network-plan").performTouchInput { longClick(center(GridPoint(8, 5))) }
        compose.runOnIdle { assertEquals(listOf("hold:a"), calls); assertFalse(editing) }
        assertEquals("Verbindung anpinnen", compose.onNodeWithTag("network-node-a").fetchSemanticsNode().config.getOrNull(SemanticsActions.OnLongClick)?.label)
        calls.clear()
        show(store(NetworkHoldAction.OVERVIEW, pinned = "f"))
        compose.onNodeWithTag("network-plan").performTouchInput { longClick(center(GridPoint(8, 5))) }
        compose.runOnIdle { assertEquals(listOf("hold:a"), calls) }
        compose.onNodeWithTag("network-overview-mark-f", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("network-overview-mark-a", useUnmergedTree = true).assertDoesNotExist()
        val pinned = compose.onNodeWithTag("network-node-f").fetchSemanticsNode().config
        assertEquals("Nicht mehr in der Übersicht anzeigen", pinned.getOrNull(SemanticsActions.OnLongClick)?.label)
        assertTrue(pinned.getOrNull(SemanticsProperties.StateDescription).orEmpty().contains("In der Übersicht angeheftet"))
        assertEquals("In Übersicht anzeigen", compose.onNodeWithTag("network-node-a").fetchSemanticsNode().config.getOrNull(SemanticsActions.OnLongClick)?.label)
        capture("overview-pin-mark")
        // The mark only counts while the overview action is chosen.
        show(store(NetworkHoldAction.LOCATION, pinned = "f"))
        compose.onNodeWithTag("network-overview-mark-f", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun theHoldSettingSitsAboveTheGridWithFixedStops() {
        var state by mutableStateOf(store(NetworkHoldAction.MOVE))
        host {
            OpenVTheme(state.settings) {
                Surface { Column { FavoriteSettingsContent(state, {}, {}, { change -> state = state.copy(settings = change(state.settings)) }, { _, _ -> }) } }
            }
        }
        val grid = compose.onNodeWithTag("network-grid-setting").fetchSemanticsNode().boundsInRoot
        val hold = compose.onNodeWithTag("network-hold-setting").fetchSemanticsNode().boundsInRoot
        // The grid comes last, so scrolling through the list cannot resize it by accident.
        assertTrue("above the grid", grid.top >= hold.bottom)
        compose.onNodeWithTag("network-hold-value").assertTextEquals("Favoriten-Symbol frei bewegen")
        NetworkHoldAction.entries.forEach { action ->
            compose.onNodeWithTag("network-hold-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(action.ordinal.toFloat()) }
            compose.waitForIdle()
            compose.onNodeWithTag("network-hold-value").assertTextEquals(action.title)
            compose.onNodeWithTag("network-hold-description").assertTextEquals(action.description)
            compose.runOnIdle { assertEquals(action, state.settings.networkHoldAction) }
        }
        capture("hold-setting")
    }

    // --- Chart -------------------------------------------------------------------------------------

    private val t = 1_800_000_000_000L
    private val minute = 60_000L
    private fun ride(i: Int): Trip {
        val a = Location("Herne Bf", "h", 51.54, 7.22, "VRR"); val b = Location("Dortmund Hbf", "d", 51.517, 7.459, "VRR")
        val start = t + i * 20 * minute
        // Chips belong to short bars; a 9-minute ride stays below three clock rows.
        return Trip("r$i", listOf(TripSegment("s$i", a, b, start, start + 9 * minute, if (i % 2 == 0) LineType.SUBWAY else LineType.BUS, "U49")), "TEST", start)
    }
    private fun chart(settings: AppSettings) = host {
        OpenVTheme(settings.copy(themeMode = ThemeMode.LIGHT)) { Surface { OeffiTimelineGraph((0..3).map(::ride), {}, minimumHeight = 360.dp) } }
    }
    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test fun barsCastTheShadowAndTheChipsTuckUnderThem() {
        chart(AppSettings(quietMode = true))
        compose.waitForIdle()
        // The second column: the first one starts at the very top of the viewport.
        val bar = bounds("timeline-segment-r1-0")
        val chip = bounds("timeline-ride-start-0-r1")
        assertEquals("the chip box still ends on the bar top", bar.top, chip.bottom, 1.5f)
        val px = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val dp = compose.density.density
        fun luminance(x: Float, y: Float) = px.getPixel(x.toInt(), y.toInt()).let { android.graphics.Color.luminance(it) }
        val y = bar.center.y
        val nearBar = luminance(bar.right + 1.2f * dp, y)
        val gap = luminance(bar.right + 4f * dp, y)
        assertTrue("the bar casts a shadow beside it ($nearBar vs $gap)", nearBar < gap - .005f)
        // Above the chip there is no shadow any more: the same as the free background next to it.
        val aboveChip = luminance(chip.center.x, chip.top - 1.2f * dp)
        val free = luminance(bar.right + 4f * dp, chip.top - 1.2f * dp)
        assertEquals("no chip shadow", free, aboveChip, .004f)
        px.recycle()
        capture("chart-chips-under-bars")
    }

    /** Follow-up: a short bar (a few minutes) gets the same soft rim as a long one, not a wide block. */
    @Test fun shortBarsKeepANarrowShadow() {
        val a = Location("Herne Bf", "h", 51.54, 7.22, "VRR"); val b = Location("Dortmund Hbf", "d", 51.517, 7.459, "VRR")
        val trips = (0..3).map { i -> val start = t + i * 20 * minute
            Trip("s$i", listOf(TripSegment("q$i", a, b, start, start + 7 * minute, LineType.BUS, "312")), "TEST", start) }
        host { OpenVTheme(AppSettings(quietMode = true, themeMode = ThemeMode.LIGHT, appTheme = AppTheme.BUBBLEGUM, timelineTheme = TimelineTheme.APP_THEME)) {
            Surface { OeffiTimelineGraph(trips, {}, minimumHeight = 360.dp) } } }
        compose.waitForIdle()
        val bar = bounds("timeline-segment-s1-0")
        val px = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val dp = compose.density.density
        fun luminance(x: Float, y: Float) = android.graphics.Color.luminance(px.getPixel(x.toInt(), y.toInt()))
        val y = bar.center.y
        assertTrue("a rim right beside the bar", luminance(bar.right + .8f * dp, y) < luminance(bar.right + 3.6f * dp, y) - .002f)
        assertEquals("no block beyond the rim", luminance(bar.right + 3.6f * dp, y), luminance(bar.right + 3.6f * dp, bar.top - 30f * dp), .004f)
        px.recycle()
        capture("chart-short-bars")
    }

    @Test fun appThemeBarsKeepTheirChipsInTheThemeLook() {
        chart(AppSettings(quietMode = true, appTheme = AppTheme.BUBBLEGUM, timelineTheme = TimelineTheme.APP_THEME))
        compose.waitForIdle()
        assertEquals(bounds("timeline-segment-r1-0").bottom, bounds("timeline-ride-end-0-r1").top, 1.5f)
        capture("chart-chips-bubblegum")
        chart(AppSettings(quietMode = true, appTheme = AppTheme.COMIC_POP, timelineTheme = TimelineTheme.APP_THEME))
        compose.waitForIdle()
        capture("chart-chips-comic")
        chart(AppSettings(quietMode = true, timelineTheme = TimelineTheme.OUTLINE))
        compose.waitForIdle()
        // Open styles: no tuck (the bar would not hide it), the chip still sits flush.
        assertEquals(bounds("timeline-segment-r0-0").top, bounds("timeline-ride-start-0-r0").bottom, 1.5f)
        capture("chart-chips-outline")
    }

    @Test fun highContrastIsVisibleInTheSettings() {
        val base = store(NetworkHoldAction.MOVE)
        listOf(AppContrast.STANDARD, AppContrast.HIGH).forEach { contrast ->
            val state = base.copy(settings = base.settings.copy(appTheme = AppTheme.BUBBLEGUM, readability = AppReadability(contrast = contrast)))
            host { OpenVTheme(state.settings) { Surface { Column { FavoriteSettingsContent(state, {}, {}, {}, { _, _ -> }) } } } }
            compose.waitForIdle()
            capture("contrast-${contrast.name.lowercase()}")
        }
    }
}
