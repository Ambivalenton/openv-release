// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelStore
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.location.CurrentPositionSource
import dev.ambivalenton.openv.rendering.ExpressiveShapeOutline
import dev.ambivalenton.openv.ui.OverviewViewModel
import dev.ambivalenton.openv.ui.theme.appThemeScheme
import dev.ambivalenton.openv.ui.theme.contrastRatio
import dev.ambivalenton.openv.ui.theme.highContrastScheme
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Hold actions of the network plan, the overview pin, ten new shapes, high text contrast. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OpenVApp::class)
@OptIn(ExperimentalCoroutinesApi::class)
class Build74Test {
    private val now = 1_800_000_000_000L
    private val here = Location("Aktueller Standort", latitude = 51.0, longitude = 7.0)
    private fun place(name: String, lat: Double) = Location(name, name, lat, 7.3, "T")
    private val home = Favorite("home", "Zuhause", place("Herne", 51.54), icon = FavoriteIcon.HOME)
    private val work = Favorite("work", "Arbeit", place("Dortmund", 51.51), icon = FavoriteIcon.WORK)
    private val hub = Favorite("hub", "Hauptbahnhof", place("Bochum Hbf", 51.48), icon = FavoriteIcon.TRAIN)
    private val gym = Favorite("gym", "Fitness", place("Witten", 51.44), icon = FavoriteIcon.FITNESS_CENTER)

    private val locations get() = (RuntimeEnvironment.getApplication() as OpenVApp).locations

    @Before
    fun clearPersistentFixture() = runBlocking {
        locations.state.first().favorites.forEach { locations.deleteFavorite(it.id) }
        locations.updateSettings { AppSettings() }
        locations.clearPresence()
        Unit
    }

    // --- Hold action setting -------------------------------------------------------------------

    @Test fun moveIsTheDefaultHoldAndOldStoresReadIt() {
        assertEquals(NetworkHoldAction.MOVE, AppSettings().networkHoldAction)
        assertEquals(listOf("Nichts tun", "Favoriten-Symbol frei bewegen", "Als aktuellen Standort festlegen",
            "Verbindung anpinnen", "In Übersicht anzeigen"), NetworkHoldAction.entries.map { it.titleKey })
        // A store written before build 74 has neither field; an unknown value falls back to the default.
        val old = StateSerializer.json.decodeFromString(StoredState.serializer(), """{"settings":{"quietMode":true}}""")
        assertEquals(NetworkHoldAction.MOVE, old.settings.networkHoldAction)
        assertNull(old.overviewFavoriteId)
        val unknown = StateSerializer.json.decodeFromString(StoredState.serializer(), """{"settings":{"networkHoldAction":"LATER"}}""")
        assertEquals(NetworkHoldAction.MOVE, unknown.settings.networkHoldAction)
        val written = StateSerializer.json.encodeToString(StoredState.serializer(),
            StoredState(settings = AppSettings(networkHoldAction = NetworkHoldAction.OVERVIEW), overviewFavoriteId = "gym"))
        val back = StateSerializer.json.decodeFromString(StoredState.serializer(), written)
        assertEquals(NetworkHoldAction.OVERVIEW, back.settings.networkHoldAction)
        assertEquals("gym", back.overviewFavoriteId)
        NetworkHoldAction.entries.forEach { assertTrue(it.description.isNotBlank()) }
    }

    @Test fun holdingPinsReleasesAndAddsUpToFiveOverviewPins() {
        val state = StoredState(favorites = listOf(home, work, gym)).withNetworkNodeFor("home").withNetworkNodeFor("work").withNetworkNodeFor("gym")
        val pinned = state.toggleOverviewFavorite("gym")
        assertEquals(listOf("gym"), pinned.overviewPins)
        // A second hold adds a pin instead of moving it.
        assertEquals(listOf("gym", "work"), pinned.toggleOverviewFavorite("work").overviewPins)
        assertEquals(emptyList<String>(), pinned.toggleOverviewFavorite("gym").overviewPins)
        // Unknown favourites are ignored, the idempotent form changes nothing twice.
        assertSame(state, state.toggleOverviewFavorite("nobody"))
        assertSame(pinned, pinned.withOverviewFavorite("gym"))
        // Removing the favourite (or its node) ends the pin.
        assertEquals(emptyList<String>(), pinned.withoutNetworkNode("gym").overviewPins)
        assertEquals(listOf("gym"), pinned.withoutNetworkNode("work").overviewPins)
        // A build 74 single pin is read as the first entry of the list.
        assertEquals(listOf("work"), state.copy(overviewFavoriteId = "work").overviewPins)
    }

    @Test fun overviewFocusShowsHighlightedAndPinnedOtherwiseDestinationsOnly() {
        data class Row(val id: String, val highlighted: Boolean = false, val transfer: Boolean = false)
        val rows = listOf(Row("home"), Row("work", highlighted = true), Row("hub", transfer = true), Row("gym"))
        fun pick(pinned: String?) = overviewFocusRows(rows, { it.highlighted }, { it.id == pinned }, { !it.transfer }).map { it.id }
        assertEquals(listOf("work", "gym"), pick("gym"))
        assertEquals(listOf("work"), pick(null))
        // A pinned transfer point is shown: the person chose it explicitly.
        assertEquals(listOf("work", "hub"), pick("hub"))
        val quiet = rows.map { it.copy(highlighted = false) }
        assertEquals(listOf("gym"), overviewFocusRows(quiet, { it.highlighted }, { it.id == "gym" }, { !it.transfer }).map { it.id })
        assertEquals(listOf("home", "work", "gym"), overviewFocusRows(quiet, { it.highlighted }, { false }, { !it.transfer }).map { it.id })
    }

    // --- Overview in "In Übersicht anzeigen" mode ----------------------------------------------

    private class Fixed(private val point: Location, now: Long) : CurrentPositionSource {
        override fun hasPermission() = true
        override val lastPresence = DevicePresence(point, now, 10f)
        override suspend fun current() = point
    }

    @Test fun theOverviewFollowsThePinAndLeavesOutTransferPoints() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val holder = ViewModelStore()
        try {
            locations.updateSettings { it.copy(originMode = OriginMode.MANUAL, manualOrigin = here, networkHoldAction = NetworkHoldAction.OVERVIEW) }
            listOf(home, work, hub, gym).forEach { locations.saveFavorite(it) }
            locations.setNodeRole("hub", NodeRole.TRANSFER)
            val vm = OverviewViewModel(locations, OverviewRepository({ OverviewTestProvider() }) { now }, Fixed(here, now), { now })
            holder.put("overview", vm)
            vm.setForeground(true)
            fun ids(state: dev.ambivalenton.openv.ui.OverviewUiState) = state.entries.map { it.favorite.id }.toSet()
            // Nothing pinned, nothing highlighted: every destination, no transfer point.
            assertEquals(setOf("home", "work", "gym"), ids(vm.state.first { it.ready && !it.loading && it.entries.isNotEmpty() }))
            // Pinned: only that favourite.
            locations.setOverviewFavorite("gym")
            assertEquals(setOf("gym"), ids(vm.state.first { s -> s.ready && !s.loading && s.entries.map { it.favorite.id } == listOf("gym") }))
            // Another hold action: the pin rests and the overview is as before (transfer points included).
            locations.updateSettings { it.copy(networkHoldAction = NetworkHoldAction.LOCATION) }
            assertEquals(setOf("home", "work", "hub", "gym"), ids(vm.state.first { s -> s.ready && !s.loading && s.entries.size == 4 }))
            assertEquals(listOf("gym"), locations.state.first().overviewPins)
        } finally {
            holder.clear()
            Dispatchers.resetMain()
        }
    }

    // --- Network vehicles (follow-up) -------------------------------------------------------------

    @Test fun vehiclesDriveFromInsideOneNodeIntoTheOtherWithoutFading() {
        val run = dev.ambivalenton.openv.ui.network.VehicleRun(true, 0f)
        val tail = 40f
        val start = dev.ambivalenton.openv.ui.network.vehiclePassage(300f, tail, 50f, .001f, run)!!
        assertEquals("starts in the centre of its node", 0f, start.distance, 1f)
        val travel = dev.ambivalenton.openv.ui.network.vehicleTravelSeconds(340f, 50f)
        val end = dev.ambivalenton.openv.ui.network.vehiclePassage(300f, tail, 50f, travel - .001f, run)!!
        assertEquals("the whole consist ends inside the far node", 340f, end.distance, 1f)
        (0..200).forEach { i -> dev.ambivalenton.openv.ui.network.vehiclePassage(300f, tail, 50f, i * .07f, run)?.let { assertEquals(1f, it.alpha, 0f) } }
        val back = dev.ambivalenton.openv.ui.network.vehiclePassage(300f, tail, 50f, .001f, run.copy(forward = false))!!
        assertEquals(300f, back.distance, 1f)
        // The tail allowance covers every consist the plan can draw.
        dev.ambivalenton.openv.ui.network.VehicleKind.entries.forEach { kind ->
            listOf(1f, 2.5f, 4f, 6f).forEach { fields ->
                (0..200).forEach { seed ->
                    val cars = dev.ambivalenton.openv.ui.network.vehicleConsist(kind, fields, seed)
                    val length = cars.sumOf { it.length.toDouble() } + (cars.size - 1) * dev.ambivalenton.openv.ui.network.vehicleGap(kind)
                    assertTrue("$kind $fields", length <= dev.ambivalenton.openv.ui.network.maxConsistLength(kind, fields) + 1e-4)
                }
            }
        }
    }

    // --- Shapes ----------------------------------------------------------------------------------

    @Test fun tenNewShapesFillTheUnitSquareAndAreDistinct() {
        assertEquals(21, IconStyle.entries.size)
        assertEquals(10, ExpressiveShapeOutline.shapes.size)
        val outlines = ExpressiveShapeOutline.shapes.associateWith { requireNotNull(ExpressiveShapeOutline.points(it)) }
        outlines.forEach { (shape, points) ->
            assertTrue("$shape has a real outline", points.size >= 12)
            val xs = points.map { it.first }; val ys = points.map { it.second }
            assertTrue("$shape stays inside", xs.all { it in -1e-9..1 + 1e-9 } && ys.all { it in -1e-9..1 + 1e-9 })
            assertEquals("$shape fills the longer side", 1.0, maxOf(xs.max() - xs.min(), ys.max() - ys.min()), 1e-9)
            assertEquals("$shape is centred", .5, (xs.max() + xs.min()) / 2, 1e-9)
            assertEquals("$shape is centred", .5, (ys.max() + ys.min()) / 2, 1e-9)
            // Shoelace area: a solid silhouette, not a sliver (the icon sits in its middle).
            val area = points.indices.sumOf { i -> val a = points[i]; val b = points[(i + 1) % points.size]; a.first * b.second - b.first * a.second } / 2
            assertTrue("$shape area ${kotlin.math.abs(area)}", kotlin.math.abs(area) in .45..0.95)
            assertTrue("$shape covers its centre", contains(points, .5, .5))
        }
        // Older shapes keep their own geometry.
        assertNull(ExpressiveShapeOutline.points(IconStyle.CIRCLE))
        assertEquals(outlines.size, outlines.values.map { it.map { p -> "%.3f,%.3f".format(p.first, p.second) } }.distinct().size)
    }

    @Test fun widgetDrawablesMatchTheAppOutline() {
        val res = listOf(File("src/main/res/drawable"), File("app/src/main/res/drawable")).first { it.isDirectory }
        ExpressiveShapeOutline.shapes.forEach { shape ->
            val name = shape.name.lowercase()
            val xml = File(res, "widget_favorite_$name.xml").readText()
            val data = Regex("pathData=\"([^\"]+)\"").find(xml)!!.groupValues[1]
            val drawn = Regex("[ML]([-0-9.]+),([-0-9.]+)").findAll(data).map { it.groupValues[1].toDouble() / 144 to it.groupValues[2].toDouble() / 144 }.toList()
            val expected = ExpressiveShapeOutline.points(shape)!!
            assertEquals("$shape point count (run tools/generate_favorite_shapes.py)", expected.size, drawn.size)
            expected.zip(drawn).forEach { (a, b) ->
                assertEquals("$shape x", a.first, b.first, .001); assertEquals("$shape y", a.second, b.second, .001)
            }
            listOf("fill", "contour", "shadow").forEach { assertTrue("$shape $it layer", File(res, "widget_symbol_${name}_$it.xml").readText().contains(data)) }
        }
    }

    private fun contains(points: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        var inside = false
        for (i in points.indices) {
            val a = points[i]; val b = points[(i + 1) % points.size]
            if ((a.second > y) != (b.second > y) && x < (b.first - a.first) * (y - a.second) / (b.second - a.second) + a.first) inside = !inside
        }
        return inside
    }

    // --- High text contrast ----------------------------------------------------------------------

    @Test fun highContrastReachesSevenToOneInEveryDesign() {
        val schemes = listOf(lightColorScheme()) + AppTheme.entries.map { appThemeScheme(it) }
        schemes.forEachIndexed { index, base ->
            val s = highContrastScheme(base)
            val label = if (index == 0) "Material" else AppTheme.entries[index - 1].name
            listOf(s.background, s.surface, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh,
                s.surfaceContainerHighest, s.primaryContainer, s.secondaryContainer).forEach {
                assertEquals("$label surfaces are opaque", 1f, it.alpha, 0f)
            }
            fun ratio(a: Color, b: Color) = contrastRatio(a, b)
            assertTrue("$label onSurface", ratio(s.onSurface, s.surface) >= 7.0)
            assertTrue("$label onBackground", ratio(s.onBackground, s.background) >= 7.0)
            assertTrue("$label onSurfaceVariant", ratio(s.onSurfaceVariant, s.surfaceVariant) >= 7.0)
            assertTrue("$label onPrimaryContainer", ratio(s.onPrimaryContainer, s.primaryContainer) >= 4.5)
            assertTrue("$label primary as text ${ratio(s.primary, s.background)}", ratio(s.primary, s.background) >= 7.0)
            assertTrue("$label secondary as text", ratio(s.secondary, s.background) >= 7.0)
            assertTrue("$label error as text", ratio(s.error, s.background) >= 7.0)
            listOf(s.onSurface, s.onBackground, s.onPrimary, s.onPrimaryContainer).forEach {
                assertTrue("$label ink is black or white", it == Color.Black || it == Color.White)
            }
        }
    }

    @Test fun highContrastIsVisiblyStrongerThanTheDesignStandard() {
        // The complaint of build 73: nothing noticeable. At least the accent text must change in the
        // pastel and glass designs, and their glass surfaces must turn solid.
        val changed = AppTheme.entries.count { theme ->
            val base = appThemeScheme(theme); val strong = highContrastScheme(base)
            base.primary != strong.primary || base.surfaceContainer.alpha < 1f
        }
        assertTrue("high contrast changes most designs ($changed of ${AppTheme.entries.size})", changed > AppTheme.entries.size / 2)
    }

}
