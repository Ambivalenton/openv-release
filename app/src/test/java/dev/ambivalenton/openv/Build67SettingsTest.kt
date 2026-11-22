// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.StateSerializer
import dev.ambivalenton.openv.data.repository.splitHouseNumber
import dev.ambivalenton.openv.data.repository.placeMatch
import dev.ambivalenton.openv.data.repository.withoutFavorite
import dev.ambivalenton.openv.ui.network.VehicleRun
import dev.ambivalenton.openv.ui.network.vehiclePlacement
import dev.ambivalenton.openv.ui.network.vehicleRuns
import dev.ambivalenton.openv.ui.screens.favoriteRadiusForPosition
import dev.ambivalenton.openv.ui.screens.favoriteRadiusPosition
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Grid size, favourite radius, time-dependent location, highlight filter, vehicles and
 * the house number fallback. Pure domain tests. */
class Build67SettingsTest {
    private val now=1_800_000_000_000L
    private fun place(name:String,lat:Double,lon:Double)=Location(name,name.lowercase(),lat,lon,"TEST")
    private val home=Favorite("z","Zuhause",place("Herne",51.540,7.220),icon=FavoriteIcon.HOME)
    private val work=Favorite("a","Arbeit",place("Dortmund",51.514,7.465),icon=FavoriteIcon.WORK)
    private val gym=Favorite("f","Fitness",place("Bochum",51.481,7.216),icon=FavoriteIcon.FITNESS_CENTER)
    private fun fresh(at:Location)=DevicePresence(at,now,8f)

    @After fun reset() {ProximityMemory.reset();EdgeOrientation.reset()}

    @Test fun newFavouritesFillTheGridFromTheTopLeftRowByRow() {
        var s=StoredState()
        (1..8).forEach {i->s=s.copy(favorites=s.favorites+home.copy(id="n$i")).withNetworkNodeFor("n$i")}
        assertEquals(6,s.network.columns);assertEquals(6,s.network.rows)
        assertEquals(listOf(0 to 0,1 to 0,2 to 0,3 to 0,4 to 0,5 to 0,0 to 1,1 to 1),s.network.nodes.map {it.point!!.x to it.point!!.y})
        // A tapped free field wins; the next favourite takes the first gap again.
        s=s.copy(favorites=s.favorites+home.copy(id="t")).withNetworkNodeFor("t",GridPoint(4,4))
        assertEquals(GridPoint(4,4),s.network.node("t")!!.point)
        s=s.withNodeToTray("n1").copy(favorites=s.favorites+home.copy(id="g")).withNetworkNodeFor("g")
        assertEquals(GridPoint(0,0),s.network.node("g")!!.point)
    }

    /** H12: growing keeps positions, lines and directions; shrinking moves only outside favourites to the tray. */
    @Test fun aNewGridSizeKeepsLinesAndMovesOnlyFavouritesOutsideIntoTheTray() {
        val favorites=listOf(home,work,gym)+(1..9).map {home.copy(id="n$it",label="Ort $it",location=place("Ort $it",51.0+it,7.0))}
        var s=StoredState(favorites=favorites).let {st->favorites.fold(st) {acc,f->acc.withNetworkNodeFor(f.id)}}
            .withNodeMoved("a",GridPoint(5,5)).withNodeRole("f",NodeRole.TRANSFER).withEdge("z","a","l1")
            .let {it.copy(network=it.network.copy(edges=it.network.edges.map {e->e.copy(direction=DirectionMode.A_TO_B)}))}
        assertEquals(1,s.network.edges.size)
        val positions=s.network.nodes.associate {it.favoriteId to it.point}
        val before=s.network.revision
        // 6 × 6 → 7 × 7 and on to 12 × 12: nothing moves, no line or direction is lost.
        val grown=s.withGridSize(7,7)
        assertEquals(7 to 7,grown.network.columns to grown.network.rows)
        assertEquals(s.network.edges,grown.network.edges)
        assertEquals(positions,grown.network.nodes.associate {it.favoriteId to it.point})
        assertTrue(grown.network.revision>before)
        assertEquals(s.network.edges,grown.withGridSize(12,12).network.edges)
        // Shrinking: exactly the favourites outside the new size are named and move into the tray.
        val outside=s.outsideGrid(3,12).map {it.id}
        assertEquals(positions.filterValues {it!=null && !it.within(3,12)}.keys,outside.toSet())
        s=s.withGridSize(3,12)
        assertEquals(3 to 12,s.network.columns to s.network.rows)
        assertTrue(outside.all {s.network.node(it)!!.point==null})
        assertTrue(s.network.nodes.filter {it.favoriteId !in outside}.all {it.point==positions[it.favoriteId]})
        assertEquals("lines stay stored",listOf("l1"),s.network.edges.map {it.id})
        assertEquals("roles survive",NodeRole.TRANSFER,s.network.node("f")!!.role)
        assertEquals(favorites.map {it.id},s.network.nodes.map {it.favoriteId})
        val small=s.withGridSize(3,3)
        assertEquals(12,small.network.nodes.size)
        assertEquals(small,small.withGridSize(3,3))
        assertEquals(12 to 3,small.withGridSize(99,-4).let {it.network.columns to it.network.rows})
        // Normalising keeps the size and never places a node outside it.
        assertEquals(small.network,small.network.normalized(small.favorites))
    }

    /** H12: undoing a deletion brings the favourite back with its field, role, lines and overview pin. */
    @Test fun undoingADeletionRestoresTheFavouriteWithAllItsRelations() {
        val s=StoredState(favorites=listOf(home,work,gym)).let {st->listOf(home,work,gym).fold(st) {acc,f->acc.withNetworkNodeFor(f.id)}}
            .withNodeRole("a",NodeRole.TRANSFER).withEdge("z","a","l1").withEdge("a","f","l2")
            .let {it.copy(overviewFavoriteIds=listOf("a"))}
        val removal=s.favoriteRemoval("a")!!
        assertEquals(2,removal.lines)
        val gone=s.withoutFavorite("a")
        assertTrue(gone.network.edges.isEmpty());assertNull(gone.network.node("a"))
        val back=gone.withRestoredFavorite(removal)
        assertEquals(s.favorites,back.favorites)
        assertEquals(s.network.edges,back.network.edges)
        assertEquals(s.network.node("a"),back.network.node("a"))
        assertEquals(listOf("a"),back.overviewPins)
        assertEquals("a second undo changes nothing",back,back.withRestoredFavorite(removal))
        // A line to a favourite deleted in the meantime stays away.
        val partly=gone.withoutFavorite("f").withRestoredFavorite(removal)
        assertEquals(listOf("l1"),partly.network.edges.map {it.id})
    }

    @Test fun existingUsersKeepTheir12x12GridWithAllLines()=runBlocking {
        val old=StoredState(favorites=listOf(home,work,gym),network=TransitNetwork(
            listOf(NetworkNode("z",GridPoint(5,5)),NetworkNode("a",GridPoint(9,5)),NetworkNode("f",GridPoint(2,10))),listOf(NetworkEdge("l","z","a"))))
        val json=StateSerializer.json.encodeToJsonElement(StoredState.serializer(),old).jsonObject
        val legacy=JsonObject(json+("network" to JsonObject(json.getValue("network").jsonObject.filterKeys {it!="columns" && it!="rows"})))
        val restored=StateSerializer.readFrom(legacy.toString().byteInputStream())
        assertEquals(12 to 12,restored.network.columns to restored.network.rows)
        assertEquals(old.network.nodes,restored.network.nodes);assertEquals(old.network.edges,restored.network.edges)
        // A build 67 store keeps its own size, also when it is small.
        val current=StoredState(favorites=listOf(home),network=TransitNetwork(listOf(NetworkNode("z",GridPoint(1,1))),columns=3,rows=4))
        val again=StateSerializer.readFrom(StateSerializer.json.encodeToString(StoredState.serializer(),current).byteInputStream())
        assertEquals(3 to 4,again.network.columns to again.network.rows)
        // New installs start with 6 × 6.
        assertEquals(6 to 6,StoredState().network.let {it.columns to it.rows})
        // A store from before the network plan (no network at all) is an existing install as well.
        assertEquals(12 to 12,StateSerializer.readFrom("""{"favorites":[]}""".byteInputStream()).network.let {it.columns to it.rows})
    }

    @Test fun theRadiusSettingSnapsAtBothEndsAndIsFreeInBetween() {
        assertEquals(0,favoriteRadiusForPosition(0f));assertEquals(0,favoriteRadiusForPosition(.04f))
        assertEquals(FAVORITE_RADIUS_ALWAYS,favoriteRadiusForPosition(1f));assertEquals(FAVORITE_RADIUS_ALWAYS,favoriteRadiusForPosition(.96f))
        assertEquals(10,favoriteRadiusForPosition(.041f));assertEquals(2000,favoriteRadiusForPosition(.959f))
        listOf(0,10,250,500,1230,2000,FAVORITE_RADIUS_ALWAYS).forEach {assertEquals(it,favoriteRadiusForPosition(favoriteRadiusPosition(it)))}
        assertEquals(FAVORITE_RADIUS_ALWAYS,normalizedFavoriteRadius(2500));assertEquals(0,normalizedFavoriteRadius(-3))
        val favorites=listOf(home,work)
        val near=place("x",51.5425,7.2200) // about 280 m north of Zuhause
        val far=place("y",52.52,13.40)     // Berlin
        fun anchor(p:Location,radius:Int)=proximityAnchor(favorites,p,fresh(p),false,null,now,radius)?.nodeId
        assertEquals("z",anchor(near,500));assertNull(anchor(near,200));assertNull("0 = always the real position",anchor(home.location,0))
        assertNull(anchor(far,2000))
        assertEquals("always: the nearest favourite, however far away","a",anchor(far,FAVORITE_RADIUS_ALWAYS))
        // The hysteresis scales with the radius: 200 m keeps its node up to 240 m.
        val held=ProximityAnchor("z",100.0,now)
        val at230=place("h",51.54207,7.2200)
        assertEquals("z",proximityAnchor(favorites,at230,fresh(at230),false,held,now,200)?.nodeId)
        assertNull(proximityAnchor(favorites,near,fresh(near),false,held,now,200))
    }

    @Test fun onlyOneFavouriteCanOwnAMinuteOfTheWeek() {
        val weekdays=HighlightRule((1..5).toSet(),8*60,16*60)
        assertTrue(weekdays.overlaps(HighlightRule(setOf(3),15*60,17*60)))
        assertFalse(weekdays.overlaps(HighlightRule(setOf(3),16*60,17*60)))
        assertFalse(weekdays.overlaps(HighlightRule(setOf(6,7),0,0)))
        // Overnight: Friday 22:00 to 02:00 reaches into Saturday and wraps from Sunday to Monday.
        assertTrue(HighlightRule(setOf(5),22*60,2*60).overlaps(HighlightRule(setOf(6),60,90)))
        assertTrue(HighlightRule(setOf(7),23*60,60).overlaps(HighlightRule(setOf(1),0,30)))
        assertFalse(HighlightRule(setOf(5),22*60,2*60).overlaps(HighlightRule(setOf(6),2*60,3*60)))
        val s=StoredState(favorites=listOf(work.copy(hiddenRules=listOf(weekdays)),home))
        assertEquals("a",s.scheduleConflict("z",listOf(HighlightRule(setOf(1),9*60,10*60)))?.id)
        assertNull(s.scheduleConflict("z",listOf(HighlightRule(setOf(1),17*60,23*60))))
        assertNull("a favourite never conflicts with itself",s.scheduleConflict("a",listOf(weekdays)))
        // Older stores may overlap: the first favourite in the list keeps the time, the others lose
        // only the overlapping part, and a one-time notice names them.
        val both=StoredState(favorites=listOf(work.copy(hiddenRules=listOf(weekdays)),
            home.copy(hiddenRules=listOf(HighlightRule(setOf(2),6*60,18*60),HighlightRule(setOf(6),0,0)))))
        val repaired=both.withUniqueSchedules(notify=true)
        assertEquals(listOf(weekdays),repaired.favorites[0].hiddenRules)
        assertEquals(listOf(HighlightRule(setOf(2),6*60,8*60),HighlightRule(setOf(2),16*60,18*60),HighlightRule(setOf(6),0,0)),
            repaired.favorites[1].hiddenRules)
        assertEquals(listOf("Zuhause"),repaired.scheduleNotice)
        assertEquals(repaired,repaired.withUniqueSchedules(notify=true))
        assertNull(repaired.scheduleConflict("z",repaired.favorites[1].hiddenRules))
        // A whole day minus a working window keeps the morning and the evening (until midnight).
        val day=HighlightRule(setOf(1),0,0)
        assertEquals(listOf(HighlightRule(setOf(1),0,8*60),HighlightRule(setOf(1),16*60,0)),day.without(weekdays.weekRanges()))
        // An overnight window cut at midnight keeps its first night part.
        val night=HighlightRule(setOf(5),22*60,2*60)
        assertEquals(listOf(HighlightRule(setOf(5),22*60,0)),night.without(HighlightRule(setOf(6),0,0).weekRanges()))
    }

    @Test fun theTimeDependentLocationComesAfterGpsAndAChosenStart() {
        val zone=ZoneId.systemDefault()
        val monday=ZonedDateTime.of(2027,1,18,10,0,0,0,zone).toInstant().toEpochMilli()
        val evening=ZonedDateTime.of(2027,1,18,20,0,0,0,zone).toInstant().toEpochMilli()
        val favorites=listOf(home,work.copy(hiddenRules=listOf(HighlightRule((1..5).toSet(),8*60,16*60))))
        val noGps=StoredState(favorites=favorites)
        assertEquals("a",noGps.scheduledStart(monday)?.id)
        assertEquals(work.location,noGps.routingPosition(monday))
        assertEquals(work.location,noGps.routingPresence(monday)?.location)
        assertEquals("the calculations start at that favourite","a",noGps.currentAnchor(monday)?.nodeId)
        assertNull(noGps.scheduledStart(evening));assertNull(noGps.routingPosition(evening))
        // 1. A fresh GPS fix wins.
        val here=place("hier",48.1,11.5)
        val gps=noGps.copy(presence=DevicePresence(here,monday,8f))
        assertNull(gps.scheduledStart(monday));assertEquals(here,gps.routingPosition(monday))
        assertEquals(here,gps.startPosition(monday,here))
        assertEquals("no measurement right now: the window knows the place",work.location,noGps.startPosition(monday,null))
        // 2. A manually chosen start wins over the window, however old it is.
        val chosen=place("Gewählt",50.0,8.0)
        val manual=noGps.copy(settings=AppSettings(originMode=OriginMode.MANUAL,manualOrigin=chosen,manualOriginChangedAt=monday-48*60*60_000))
        assertNull(manual.scheduledStart(monday));assertEquals(chosen,manual.routingPosition(monday))
        // 3. Without location access and without a chosen start, the window supplies the start.
        val noAccess=noGps.copy(settings=AppSettings().manualLocationFallback())
        assertEquals(work.location,noAccess.routingPosition(monday));assertEquals(work.location,noAccess.startPosition(monday,null))
        assertNull(noAccess.routingPosition(evening))
        // A virtual location (hold on a node) is never replaced.
        val held=noGps.toggleLocationOverride("z",monday-3*60*60_000)
        assertEquals(home.location,held.routingPosition(monday))
        // The window no longer hides the favourite by itself; being there does (as for every place).
        val clock=java.time.Instant.ofEpochMilli(monday).atZone(zone)
        assertEquals(favorites,visibleFavorites(favorites,clock,null,true))
        assertEquals(listOf(home),visibleFavorites(favorites,clock,noGps.routingPresence(monday),true))
    }

    @Test fun whileSomethingIsHighlightedOnlyTheHighlightedRowsAreShown() {
        val rows=listOf("a","b","c")
        assertEquals(listOf("b"),onlyHighlighted(rows) {it=="b"})
        assertEquals("nothing highlighted: everything as before",rows,onlyHighlighted(rows) {false})
        assertEquals(emptyList<String>(),onlyHighlighted(emptyList<String>()) {true})
    }

    @Test fun vehiclesFollowTheDirectionOfTheirLine() {
        assertEquals(listOf(true,false),vehicleRuns(DirectionMode.BOTH,0).map {it.forward})
        assertEquals(listOf(true),vehicleRuns(DirectionMode.A_TO_B,3).map {it.forward})
        assertEquals(listOf(false),vehicleRuns(DirectionMode.B_TO_A,3).map {it.forward})
        val forward=VehicleRun(true,0f);val back=VehicleRun(false,0f)
        // 300 px line, 20 px inset, 50 px fields: 260 px in about 6.9 s, then a pause at the node.
        val start=vehiclePlacement(300f,20f,50f,.01f,forward)!!;val middle=vehiclePlacement(300f,20f,50f,3.4666f,forward)!!
        assertTrue(start.distance in 20f..22f);assertTrue(start.alpha<.1f)
        assertEquals(150f,middle.distance,1f);assertEquals(1f,middle.alpha,0f)
        assertEquals(300f-middle.distance,vehiclePlacement(300f,20f,50f,3.4666f,back)!!.distance,.01f)
        assertNull("waiting at the node",vehiclePlacement(300f,20f,50f,7.5f,forward))
        assertNull("too short for a vehicle",vehiclePlacement(45f,20f,50f,1f,forward))
        // Never outside the line, whatever the clock says.
        (0..400).forEach {i->vehiclePlacement(300f,20f,50f,i*.37f,forward)?.let {assertTrue(it.distance in 20f..280f)}}
    }

    @Test fun aMissingHouseNumberFallsBackToTheStreet() {
        assertEquals("musterstrasse" to "12",splitHouseNumber("Musterstraße 12"))
        assertEquals("musterstrasse herne" to "12a",splitHouseNumber("Musterstr. 12 a Herne"))
        assertEquals("a five-digit postcode is not a house number","musterstrasse 44623 herne" to "7",splitHouseNumber("Musterstraße 7, 44623 Herne"))
        assertNull(splitHouseNumber("Musterstraße"));assertNull(splitHouseNumber("44623 Herne"));assertNull(splitHouseNumber("12"))
        val house=Location("Musterstraße 12a, 44623 Herne",latitude=51.5,longitude=7.2,kind=LocationKind.ADDRESS,city="Herne")
        assertNotNull("12 finds 12a",placeMatch("Musterstraße 12",house))
        assertNotNull("12 a is 12a",placeMatch("Musterstraße 12 a",house))
        assertNull("12 never finds 120 or 2",placeMatch("Musterstraße 12",house.copy(name="Musterstraße 120, 44623 Herne")))
        // The street offered instead carries the number only as a search alias, never in its name.
        val street=Location("Musterstraße, 44623 Herne",latitude=51.5,longitude=7.2,kind=LocationKind.POI,city="Herne")
        assertNull(placeMatch("Musterstraße 12",street))
        assertNotNull(placeMatch("Musterstraße 12",street.copy(searchTerms="Musterstraße, 44623 Herne Herne 12")))
    }

    /** Regression guard for the crash with the shapes "Cookie" and "Kleeblatt": the platform elevation
     * shadow (hardware renderer only, so invisible to these software-rendered tests) must not get a
     * favourite shape again. Those places draw [shapeShadow] instead. */
    @Test fun favouriteShapesNeverUseThePlatformShadow() {
        listOf("ui/network/NetworkPlan.kt","ui/components/FavoriteBadge.kt","tracking/PinnedBubbleOverlay.kt").forEach {name->
            val source=java.io.File("src/main/java/dev/ambivalenton/openv/$name").readText()
            assertFalse("$name uses Modifier.shadow",Regex("""\.shadow\(""").containsMatchIn(source))
            assertTrue("$name draws its own shadow",source.contains("shapeShadow("))
        }
    }
}
