// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.StateSerializer
import dev.ambivalenton.openv.data.repository.boardPlaces
import dev.ambivalenton.openv.data.repository.boardConfigurationKey
import dev.ambivalenton.openv.data.repository.withFavorite
import dev.ambivalenton.openv.data.repository.withoutFavorite
import dev.ambivalenton.openv.data.repository.withoutBoardPlace
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Favourite network plan, migration of the former pairs, proximity routing and the
 * departures view without favourites. Pure domain tests; the tables follow docs/NETZPLAN_SPEZIFIKATION.md. */
class Build66NetworkTest {
    private val now=1_800_000_000_000L
    private val clock:ZonedDateTime=java.time.Instant.ofEpochMilli(now).atZone(ZoneId.of("Europe/Berlin"))
    private fun place(name:String,lat:Double,lon:Double)=Location(name,name.lowercase(),lat,lon,"TEST")
    private val home=Favorite("z","Zuhause",place("Herne",51.540,7.220),icon=FavoriteIcon.HOME)
    private val hub=Favorite("h","Bahnhof",place("Herne Bf",51.545,7.230),icon=FavoriteIcon.TRAIN)
    private val work=Favorite("a","Arbeit",place("Dortmund",51.514,7.465),icon=FavoriteIcon.WORK)
    private val gym=Favorite("f","Fitness",place("Bochum",51.481,7.216),icon=FavoriteIcon.FITNESS_CENTER)
    private val school=Favorite("s","Schule",place("Gelsenkirchen",51.505,7.100),icon=FavoriteIcon.SCHOOL)

    private fun state(vararg f:Favorite,edges:List<NetworkEdge> = emptyList()):StoredState {
        val base=StoredState(favorites=f.toList())
        val points=f.mapIndexed {i,fav->fav.id to GridPoint(2*(i%5),2*(i/5))}.toMap()
        return base.copy(network=TransitNetwork(f.map {NetworkNode(it.id,points.getValue(it.id))},edges,columns=12,rows=12))
    }
    private fun edge(id:String,a:String,b:String,d:DirectionMode=DirectionMode.BOTH)=NetworkEdge(id,a,b,d)

    @After fun reset() {ProximityMemory.reset();EdgeOrientation.reset()}

    @Test fun formerPairsBecomeLinesWithTheSameIdentityRulesAndBothDirections()=runBlocking {
        val rule=HighlightRule(setOf(1,2,3),360,600)
        val legacy=StoredState(schemaVersion=9,favorites=listOf(home,work,gym),
            favoritePairs=listOf(FavoritePair(id="pair-1",firstId="z",secondId="a",rule=rule)))
        val restored=StateSerializer.readFrom(StateSerializer.json.encodeToString(StoredState.serializer(),legacy).byteInputStream())
        assertEquals(10,restored.schemaVersion)
        assertTrue(restored.favoritePairs.isEmpty())
        val line=restored.network.edges.single()
        assertEquals("pair-1",line.id);assertEquals("z" to "a",line.aId to line.bId)
        assertEquals(DirectionMode.BOTH,line.direction);assertEquals(rule,line.rule)
        assertEquals(listOf(home,work,gym),restored.favorites)
        // Every favourite has a node on its own grid point with the minimum spacing.
        val points=restored.network.nodes.map {it.point}
        assertTrue(points.all {it!=null})
        points.filterNotNull().forEach {p->assertTrue(points.filterNotNull().filter {it!=p}.all {it.chebyshev(p)>=NETWORK_MIN_NODE_SPACING})}
        // Idempotent: reading the migrated store again changes nothing.
        val again=StateSerializer.readFrom(StateSerializer.json.encodeToString(StoredState.serializer(),restored).byteInputStream())
        assertEquals(restored,again)
    }

    @Test fun aBuild57CounterpoleTravelsThroughThePairIntoALine()=runBlocking {
        val legacy=StoredState(schemaVersion=9,favorites=listOf(work.copy(counterpole=home.location)))
        val restored=StateSerializer.readFrom(StateSerializer.json.encodeToString(StoredState.serializer(),legacy).byteInputStream())
        assertEquals(2,restored.favorites.size)
        assertTrue(restored.favorites.all {it.counterpole==null})
        assertEquals(1,restored.network.edges.size)
        assertEquals(restored.favorites.map {it.id}.toSet(),restored.network.edges.single().let {setOf(it.aId,it.bId)})
    }

    @Test fun autoLayoutKeepsTheMapOrientationAndSendsOverflowToTheTray() {
        // West → east and north → south stay recognisable.
        val net=TransitNetwork(listOf(school,home,work).map {NetworkNode(it.id)},columns=12,rows=12).autoLayout(listOf(school,home,work))
        val p=net.placed
        assertTrue(p.getValue("s").x<p.getValue("z").x && p.getValue("z").x<p.getValue("a").x)
        // 40 favourites without coordinates on the default 6 × 6 grid: one per field, the rest wait in the tray.
        val many=(1..40).map {Favorite("n$it","Ort $it",Location("Ort $it","n$it",null,null,"TEST",kind=LocationKind.ADDRESS))}
        val crowded=TransitNetwork(many.map {NetworkNode(it.id)}).autoLayout(many)
        val placed=crowded.nodes.mapNotNull {it.point}
        assertEquals(36,placed.size)
        assertEquals(40,crowded.nodes.size)
        assertEquals(placed.size,placed.distinct().size)
        assertTrue(placed.all {it.within(NETWORK_GRID_DEFAULT,NETWORK_GRID_DEFAULT)})
    }

    @Test fun repairDropsBrokenLinesButNeverAFavourite() {
        val broken=StoredState(favorites=listOf(home,work),network=TransitNetwork(
            listOf(NetworkNode("z",GridPoint(1,1)),NetworkNode("a",GridPoint(1,1)),NetworkNode("ghost",GridPoint(5,5))),
            listOf(edge("e1","z","a"),edge("e1","a","z"),edge("e2","z","a"),edge("e3","z","ghost"),edge("e4","z","z"))))
        val fixed=broken.network.normalized(broken.favorites)
        assertEquals(listOf("z","a"),fixed.nodes.map {it.favoriteId})
        assertEquals(GridPoint(1,1),fixed.node("z")!!.point)
        assertNull("two nodes on one field: the later one moves to the tray",fixed.node("a")!!.point)
        assertEquals(listOf("e1"),fixed.edges.map {it.id})
    }

    @Test fun linesAreRejectedForTheSameNodeTwiceTheSamePlaceOrTheTray() {
        var s=state(home,work,gym)
        assertEquals(EdgeRejection.SAME_NODE,s.edgeRejection("z","z"))
        s=s.withEdge("z","a","l1")
        assertEquals(EdgeRejection.ALREADY_CONNECTED,s.edgeRejection("a","z"))
        assertEquals(s,s.withEdge("a","z","l2"))
        val twin=work.copy(id="t",label="Arbeit 2")
        val withTwin=state(home,work,twin)
        assertEquals(EdgeRejection.SAME_LOCATION,withTwin.edgeRejection("a","t"))
        assertEquals(EdgeRejection.NOT_PLACED,s.withNodeToTray("f").edgeRejection("z","f"))
        assertEquals(DirectionMode.BOTH,s.network.edges.single().direction)
    }

    @Test fun movingKeepsTheSpacingAndDeletingAFavouriteRemovesItsLines() {
        val s=state(home,work,gym).withEdge("z","a","l1").withEdge("a","f","l2")
        val occupied=s.network.node("a")!!.point!!
        assertEquals(s,s.withNodeMoved("z",GridPoint(occupied.x,occupied.y)))
        assertEquals("outside the chosen grid nothing moves",s,s.withNodeMoved("z",GridPoint(12,0)))
        val moved=s.withNodeMoved("z",GridPoint(9,9))
        assertEquals(GridPoint(9,9),moved.network.node("z")!!.point)
        assertTrue(moved.network.revision>s.network.revision)
        val removed=moved.withoutFavorite("a")
        assertNull(removed.network.node("a"))
        assertTrue(removed.network.edges.isEmpty())
    }

    @Test fun autoRolesFollowTheSymbolAndTheNumberOfLines() {
        val place=Favorite("o","Oma",place("Wanne",51.53,7.15))
        val s=state(home,hub,work,place,gym,edges=listOf(edge("1","z","h"),edge("2","h","a"),edge("3","z","o"),edge("4","o","f")))
        val roles=s.network.effectiveRoles(s.favorites.associateBy {it.id})
        assertEquals(NodeRole.TRANSFER,roles["h"])   // train symbol, 2 lines
        assertEquals(NodeRole.TRANSFER,roles["o"])   // neutral place in a row
        assertEquals(NodeRole.DESTINATION,roles["z"]) // home is always a destination
        val explicit=s.withNodeRole("h",NodeRole.DESTINATION).network.effectiveRoles(s.favorites.associateBy {it.id})
        assertEquals(NodeRole.DESTINATION,explicit["h"])
    }

    @Test fun chainsHubsRedundancyAndOneWayLinesResolveAsSpecified() {
        val t=mapOf("h" to NodeRole.TRANSFER)
        fun targets(edges:List<NetworkEdge>,from:String,roles:Map<String,NodeRole> = t)=
            reachableTargets(from,edges,roles).mapValues {(_,paths)->paths.map {it.via}}
        val chain=listOf(edge("1","z","h"),edge("2","h","a"))
        assertEquals(mapOf("a" to listOf(listOf("h"))),targets(chain,"z"))
        assertEquals(mapOf("z" to listOf(listOf("h"))),targets(chain,"a"))
        assertEquals(mapOf("h" to listOf(emptyList<String>())),targets(chain,"z",emptyMap()))
        val homeHub=listOf(edge("1","z","a"),edge("2","z","s"),edge("3","z","f"))
        assertEquals(setOf("a","s","f"),targets(homeHub,"z",emptyMap()).keys)
        assertEquals(setOf("z"),targets(homeHub,"a",emptyMap()).keys)
        val redundant=chain+edge("3","z","a")
        assertEquals(mapOf("a" to listOf(emptyList(),listOf("h"))),targets(redundant,"z"))
        val oneWay=listOf(edge("1","z","h",DirectionMode.A_TO_B),edge("2","h","a",DirectionMode.A_TO_B))
        assertEquals(mapOf("a" to listOf(listOf("h"))),targets(oneWay,"z"))
        assertTrue(targets(oneWay,"a").isEmpty())
    }

    @Test fun theOverviewRoutesFromTheAnchorAndComparesTheChainOnlyOnRedundancy() {
        val chainOnly=state(home,hub,work,edges=listOf(edge("1","z","h"),edge("2","h","a")))
        val routes=chainOnly.networkRoutes("z",clock)
        assertEquals(listOf("a"),routes.map {it.target.id})
        assertEquals(1,chainOnly.routeCandidates(routes.single(),now,allowVia=true).size)
        val redundant=state(home,hub,work,edges=listOf(edge("1","z","h"),edge("2","h","a"),edge("3","z","a")))
        val candidates=redundant.routeCandidates(redundant.networkRoutes("z",clock).single(),now,allowVia=true)
        assertEquals(2,candidates.size)
        val via=candidates.last()
        assertEquals(listOf("h"),via.viaNodeIds)
        assertEquals(listOf(hub.location),via.query.waypoints)
        assertEquals(VIA_TRANSFER_BUFFER_MINUTES,via.query.waypointDwellMinutes.values.single())
        assertEquals(home.location,candidates.first().query.origin);assertEquals(work.location,candidates.first().query.destination)
        // The adapter keeps the pair sentences: origin label and colour come from the anchor.
        val adapter=routes.single().routingFavorite()
        assertEquals("Zuhause",adapter.originLabel);assertEquals(home.location,adapter.counterpole)
    }

    @Test fun theEarliestArrivalWinsButTheShownOptionStaysWithinAMinute() {
        val q=JourneyQuery(home.location,work.location,now)
        fun trip(id:String,end:Long)=Trip(id,listOf(TripSegment(id,home.location,work.location,now,end,LineType.BUS,"1")),"T",now)
        val direct=RouteCandidate(emptyList(),q);val via=RouteCandidate(listOf("h"),q)
        assertEquals("via",pickFastest(listOf(direct to trip("d",now+3_000_000),via to trip("via",now+2_000_000)),null)!!.second.id)
        assertEquals("d",pickFastest(listOf(direct to trip("d",now+2_030_000),via to trip("via",now+2_000_000)),"")!!.second.id)
    }

    @Test fun theAnchorUsesHysteresisAndNeedsAFreshFix() {
        val favorites=listOf(home,work)
        fun fix(lat:Double)=Location("p","p",lat,7.220,"TEST")
        fun presence(l:Location,age:Long=0,accuracy:Float=10f)=DevicePresence(l,now-age,accuracy)
        val inside=fix(51.5365) // ≈ 390 m south of home
        val first=proximityAnchor(favorites,inside,presence(inside),false,null,now)
        assertEquals("z",first?.nodeId)
        val edge=fix(51.5348) // ≈ 580 m: outside 500 m but kept up to 600 m
        assertNull(proximityAnchor(favorites,edge,presence(edge),false,null,now))
        assertEquals("z",proximityAnchor(favorites,edge,presence(edge),false,first,now)?.nodeId)
        assertNull("a stale fix never creates an anchor",proximityAnchor(favorites,inside,presence(inside,age=10*60_000),false,null,now))
        assertNull("a coarse fix never creates an anchor",proximityAnchor(favorites,inside,presence(inside,accuracy=200f),false,null,now))
        assertEquals("a manual start counts as a position","z",proximityAnchor(favorites,inside,null,true,null,now)?.nodeId)
    }

    @Test fun holdingANodeSetsTheLocationAndHoldingAgainRestoresTheFormerStart() {
        val manual=place("Manuell",51.0,7.0)
        val s=state(home,work).copy(settings=AppSettings(originMode=OriginMode.MANUAL,manualOrigin=manual,manualOriginChangedAt=5))
        val on=s.toggleLocationOverride("a",now)
        assertEquals(work.location,on.routingPosition(now))
        assertEquals(OriginSource.OVERRIDE,on.originSource())
        val off=on.toggleLocationOverride("a",now+1)
        assertNull(off.locationOverride)
        assertEquals(manual,off.settings.manualOrigin);assertEquals(5L,off.settings.manualOriginChangedAt)
        // A newer manual choice wins over the override; deleting the node restores the start.
        val changed=on.copy(settings=on.settings.copy(manualOrigin=manual)).normalizedOverride()
        assertNull(changed.locationOverride);assertEquals(manual,changed.settings.manualOrigin)
        val deleted=on.withoutFavorite("a")
        assertNull(deleted.locationOverride);assertEquals(manual,deleted.settings.manualOrigin)
    }

    @Test fun aLineTapGoesNearerToFartherButAOneWayLineKeepsItsDirection() {
        val s=state(home,work)
        val both=edge("l","z","a")
        assertEquals("a",s.edgeMembers(both,work.location)!!.near.id)
        assertEquals("z",s.edgeMembers(both,home.location)!!.near.id)
        val oneWay=edge("o","z","a",DirectionMode.A_TO_B)
        assertEquals("z",s.edgeMembers(oneWay,work.location)!!.near.id)
        assertTrue(s.edgeAgainstPosition(oneWay,work.location))
        assertFalse(s.edgeAgainstPosition(oneWay,home.location))
        val adapter=s.edgeMembers(oneWay,work.location)!!.routingFavorite()
        assertEquals("o",adapter.id);assertEquals(home.location,adapter.counterpole);assertEquals("Arbeit",adapter.label)
    }

    @Test fun favouritePlacesBelongToTheStopListAutomatically()=runBlocking {
        val stop=Location("Herne Bf","herne",51.545,7.230,"TEST",kind=LocationKind.STOP)
        val legacyJson=StateSerializer.json.encodeToJsonElement(StoredState.serializer(),
            StoredState(schemaVersion=9,favorites=listOf(home.copy(location=stop))))
            .let {JsonObject(it.jsonObject.filterKeys {k->k!="boardStopsDecoupled"})}
        val restored=StateSerializer.readFrom(legacyJson.toString().byteInputStream())
        assertTrue(restored.boardStopsDecoupled)
        assertEquals(listOf(stop),boardPlaces(restored))
        // The places of favourites are members again; the copied stop stays as well.
        val elsewhere=place("Woanders",50.0,8.0)
        val edited=restored.withFavorite(home.copy(location=elsewhere))
        assertEquals(listOf(elsewhere,stop),boardPlaces(edited))
        assertNotEquals(boardConfigurationKey(restored),boardConfigurationKey(edited))
        val newInstall=StoredState().withFavorite(work)
        assertEquals(listOf(work.location),boardPlaces(newInstall))
        // Removing a favourite's place is remembered; an unrelated edit does not bring it back,
        // a new place of the favourite joins the list again.
        val removed=newInstall.withoutBoardPlace(work.location)
        assertTrue(boardPlaces(removed).isEmpty())
        assertTrue(boardPlaces(removed.withFavorite(work.copy(label="Büro"))).isEmpty())
        assertEquals(listOf(elsewhere),boardPlaces(removed.withFavorite(work.copy(location=elsewhere))))
    }

    @Test fun ruleEdgesOfLinesTakePartInTheNextChange() {
        val rule=HighlightRule((1..7).toSet(),clock.hour*60+clock.minute+5,clock.hour*60+clock.minute+30)
        val s=state(home,work,edges=listOf(edge("l","z","a").copy(rule=rule)))
        val next=nextFavoriteChange(s,clock)
        assertNotNull(next)
        assertTrue(next!!.toEpochMilli()-clock.toInstant().toEpochMilli()<=5*60_000)
    }
}
