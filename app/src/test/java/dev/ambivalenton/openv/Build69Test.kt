// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.datastore.core.DataStore
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.LocationRepository
import dev.ambivalenton.openv.ui.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** The plan follows the current position, departure times never overlap, names stay whole. */
class Build69Test {
    private class Store(s: StoredState) : DataStore<StoredState> {
        override val data = MutableStateFlow(s); private val lock = Mutex()
        override suspend fun updateData(transform: suspend (StoredState) -> StoredState) = lock.withLock { transform(data.value).also { data.value = it } }
    }
    private val now = 1_800_000_000_000L
    private val homePlace = Location("Zuhause", "z", 51.5, 9.2, "VRR")
    private val workPlace = Location("Ostbahnhof", "d", 51.51, 9.46, "VRR")
    private val home = Favorite("z", "Zuhause", homePlace, icon = FavoriteIcon.HOME)
    private val work = Favorite("d", "Stadtpark", workPlace)
    /** A place [meters] north of home. */
    private fun north(meters: Double) = Location("Hier", null, 51.5 + meters / 111_195.0, 9.2, null)

    @Before fun clean() = ProximityMemory.reset()
    @After fun cleanUp() = ProximityMemory.reset()

    private fun overlap(a: Rect, b: Rect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    /** Screenshot of build 68: two lines leave Westbahnhof upwards side by side and "16:36" sat on "16:33". */
    @Test fun departureChipsOnLinesLeavingSideBySideNeverOverlap() {
        val node = Offset(200f, 330f)
        val chips = listOf(
            NetworkChipRequest("red", listOf(node, Offset(200f, 0f)), Size(70f, 26f)),
            NetworkChipRequest("yellow", listOf(node, Offset(230f, 300f), Offset(230f, 0f)), Size(70f, 26f)),
            NetworkChipRequest("purple", listOf(node, Offset(230f, 300f), Offset(260f, 270f), Offset(260f, 100f)), Size(70f, 26f)))
        val nodes = listOf(Rect(node, 14f), Rect(Offset(200f, 20f), 14f), Rect(Offset(320f, 330f), 14f))
        val bounds = Size(360f, 360f)
        val centers = placeNetworkChips(chips, nodes, bounds, step = 7.5f, gap = 2f, reach = 120f)
        assertEquals(setOf("red", "yellow", "purple"), centers.keys)
        val rects = chips.associate { it.id to Rect(centers.getValue(it.id) - Offset(it.size.width / 2, it.size.height / 2), it.size) }
        rects.entries.toList().let { list ->
            for (i in list.indices) for (j in i + 1 until list.size)
                assertFalse("${list[i].key} / ${list[j].key}", overlap(list[i].value, list[j].value))
        }
        rects.forEach { (id, r) ->
            assertTrue(id, r.left >= 0f && r.top >= 0f && r.right <= bounds.width && r.bottom <= bounds.height)
            nodes.forEach { n -> assertFalse("$id covers a node", overlap(r, n)) }
        }
    }

    @Test fun aSingleChipSitsRightNextToItsNodeOnItsOwnLine() {
        val node = Offset(50f, 100f)
        val chip = NetworkChipRequest("e", listOf(node, Offset(350f, 100f)), Size(60f, 24f))
        val center = placeNetworkChips(listOf(chip), listOf(Rect(node, 15f), Rect(Offset(350f, 100f), 15f)), Size(400f, 200f), 7.5f, 2f, 120f).getValue("e")
        assertEquals("on its line", 100f, center.y, .01f)
        // Just clear of the node (radius 15 + half a chip 30 + gap 2), within one search step.
        assertTrue("close to its node: ${center.x}", center.x - node.x in 47f..55f)
    }

    @Test fun aCrowdedChipNeverWandersToTheOtherEndOfItsLine() {
        val node = Offset(20f, 100f)
        val crowd = (0 until 6).map { NetworkChipRequest("c$it", listOf(node, Offset(220f, 100f)), Size(60f, 24f)) }
        val centers = placeNetworkChips(crowd, listOf(Rect(node, 10f), Rect(Offset(220f, 100f), 10f)), Size(240f, 200f), 5f, 2f, 400f)
        // 60 % of the 200 px line plus the sideways shift at most.
        centers.values.forEach { c -> assertTrue("$c", c.x <= 20f + 120f + 1f) }
        // A plan narrower than a chip still yields a place, inside the plan where possible.
        val tiny = placeNetworkChips(listOf(NetworkChipRequest("t", listOf(Offset(10f, 10f), Offset(10f, 40f)), Size(60f, 24f))), emptyList(), Size(40f, 50f), 5f, 2f, 100f)
        assertTrue(tiny.containsKey("t"))
    }

    /** Screenshot of build 68: "Bibliothe|k Mitte" and "Innenstad|t" were split in the middle of a word. */
    @Test fun namePlatesGrowToTheirLongestWordButStayBounded() {
        val width: (String) -> Int = { it.length * 8 }
        assertEquals("Bibliothek (80 px) fits: plate grows", 81, nameTextWidth("Bibliothek Mitte", 64f, 200f, width))
        assertEquals("short names keep the usual width", 64, nameTextWidth("Herz", 64f, 200f, width))
        assertEquals("never wider than allowed", 120, nameTextWidth("Stadtteilbibliothek", 64f, 120f, width))
        assertEquals(81, nameTextWidth("Innenstadt", 64f, 200f, width))
        assertEquals(64, nameTextWidth("", 64f, 200f, width))
    }

    @Test fun gpsHysteresisStaysButManualStartsAreJudgedAsTheyAre() {
        val radius = AppSettings(favoriteRadiusMeters = 500)
        // GPS: entering at 400 m, keeping the node up to 600 m.
        val gps = StoredState(favorites = listOf(home, work), settings = radius.copy(originMode = OriginMode.AUTOMATIC))
        assertEquals("z", gps.copy(presence = DevicePresence(north(400.0), now, 10f)).currentAnchor(now)?.nodeId)
        assertEquals("z", gps.copy(presence = DevicePresence(north(550.0), now, 10f)).currentAnchor(now)?.nodeId)
        assertNull("well away from home", gps.copy(presence = DevicePresence(north(2_000.0), now, 10f)).currentAnchor(now))
        // Manual start at home, then GPS 550 m away: build 68 kept "Zuhause" through the hysteresis.
        ProximityMemory.reset()
        val manual = StoredState(favorites = listOf(home, work), settings = radius.copy(originMode = OriginMode.MANUAL, manualOrigin = homePlace))
        assertEquals("z", manual.currentAnchor(now)?.nodeId)
        assertNull(gps.copy(presence = DevicePresence(north(550.0), now, 10f)).currentAnchor(now))
        // A manual start is deliberate, not noise: 550 m from home is outside the 500 m radius.
        ProximityMemory.reset()
        assertEquals("z", manual.currentAnchor(now)?.nodeId)
        assertNull(manual.copy(settings = manual.settings.copy(manualOrigin = north(550.0))).currentAnchor(now))
        // A stale fix (older than 5 minutes) never anchors, wherever it was taken.
        assertNull(gps.copy(presence = DevicePresence(north(10.0), now - 6 * 60_000L, 10f)).currentAnchor(now))
    }

    @Test fun switchingToGpsEndsTheVirtualLocationAtOnce() = runTest {
        val start = StoredState(favorites = listOf(home, work),
            network = TransitNetwork(listOf(NetworkNode("z", GridPoint(1, 1)), NetworkNode("d", GridPoint(4, 1))), listOf(NetworkEdge("e", "z", "d"))),
            settings = AppSettings(originMode = OriginMode.AUTOMATIC))
        val repo = LocationRepository(Store(start))
        repo.toggleLocationOverride("z", now)
        repo.state.first().let { s ->
            assertEquals(OriginSource.OVERRIDE, s.originSource())
            assertEquals("z", s.locationOverride?.nodeId)
        }
        // The person switches the start to GPS: the pin must go now, not only after the next app start.
        repo.updateSettings { it.copy(originMode = OriginMode.AUTOMATIC) }
        repo.state.first().let { s ->
            assertNull(s.locationOverride)
            assertEquals(OriginSource.DEVICE, s.originSource())
        }
        // Holding the node again sets a new virtual location instead of "restoring" an outdated one.
        repo.toggleLocationOverride("z", now + 1)
        repo.state.first().let { s ->
            assertEquals("z", s.locationOverride?.nodeId)
            assertEquals(OriginMode.AUTOMATIC, s.locationOverride?.previousOriginMode)
        }
        // Choosing another manual start also ends it.
        repo.updateSettings { it.copy(originMode = OriginMode.MANUAL, manualOrigin = workPlace) }
        repo.state.first().let { s ->
            assertNull(s.locationOverride)
            assertEquals(workPlace, s.settings.manualOrigin)
        }
    }
}
