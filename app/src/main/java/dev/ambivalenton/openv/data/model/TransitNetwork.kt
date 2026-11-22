// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/*
 * Favourite network plan (build 66). Favourites are the nodes; a node only adds its grid position
 * and its role. Name, place, colour, symbol, time rules and filters stay authoritative in Favorite.
 * Lines are stored undirected with an explicit direction mode. Former favourite pairs become lines
 * with the same ID (deep links and widgets keep working).
 */

/** Largest grid; the person chooses the used part (columns × rows) in the favourite settings. */
const val NETWORK_GRID_SIZE = 12
const val NETWORK_GRID_MIN = 3
const val NETWORK_GRID_DEFAULT = 6
/** One favourite per grid field (build 67); nodes are drawn smaller than a field so lines fit between. */
const val NETWORK_MIN_NODE_SPACING = 1
const val NETWORK_MAX_EDGES = 64
const val NETWORK_MAX_HOPS = 4
const val NETWORK_MAX_PATHS_PER_TARGET = 3

/** Grid point. No init-require: damaged data is repaired on read, never rejected. */
@Serializable data class GridPoint(val x: Int, val y: Int) {
    fun within(columns: Int, rows: Int) = x in 0 until columns && y in 0 until rows
    fun chebyshev(o: GridPoint) = max(abs(x - o.x), abs(y - o.y))
}

/** Destinations end a route in the overview; transfer points are passed through. */
@Serializable enum class NodeRole { AUTO, DESTINATION, TRANSFER }

@Serializable data class NetworkNode(
    val favoriteId: String,
    /** null = tray: not placed on the grid yet. */
    val point: GridPoint? = null,
    val role: NodeRole = NodeRole.AUTO,
)

@Serializable enum class DirectionMode { BOTH, A_TO_B, B_TO_A }

/** Undirected storage; [aId] is the node the line was drawn from. */
@Serializable data class NetworkEdge(
    val id: String = newId(),
    val aId: String,
    val bId: String,
    val direction: DirectionMode = DirectionMode.BOTH,
    /** Taken over from a former favourite pair; never discarded. */
    val rule: HighlightRule? = null,
    val hiddenRules: List<HighlightRule> = emptyList(),
) {
    fun touches(id: String) = aId == id || bId == id
    fun other(id: String) = if (aId == id) bId else aId
    fun connects(a: String, b: String) = (aId == a && bId == b) || (aId == b && bId == a)
}

@Serializable data class TransitNetwork(
    val nodes: List<NetworkNode> = emptyList(),
    /** List order is the stable layout priority. */
    val edges: List<NetworkEdge> = emptyList(),
    /** Monotonic; the UI keeps a pending move until the store reaches its revision. */
    val revision: Long = 0,
    /** Used part of the grid, 3…12 each (also 3 × 12). A change re-arranges all nodes, see [withGridSize]. */
    val columns: Int = NETWORK_GRID_DEFAULT,
    val rows: Int = NETWORK_GRID_DEFAULT,
) {
    fun node(id: String) = nodes.firstOrNull { it.favoriteId == id }
    val placed: Map<String, GridPoint> get() = nodes.mapNotNull { n -> n.point?.let { n.favoriteId to it } }.toMap()
}

/** Virtual location set by holding a node. Keeps what applied before, so a second hold restores it. */
@Serializable data class UserLocationOverride(
    val nodeId: String,
    val location: Location,
    val activatedAt: Long,
    val previousOriginMode: OriginMode,
    val previousManualOrigin: Location? = null,
    val previousManualOriginChangedAt: Long = 0,
)

data class DirectedArc(val from: String, val to: String, val edgeId: String)

fun NetworkEdge.arcs(): List<DirectedArc> = when (direction) {
    DirectionMode.BOTH -> listOf(DirectedArc(aId, bId, id), DirectedArc(bId, aId, id))
    DirectionMode.A_TO_B -> listOf(DirectedArc(aId, bId, id))
    DirectionMode.B_TO_A -> listOf(DirectedArc(bId, aId, id))
}

fun GridPoint.free(occupied: Collection<GridPoint>, columns: Int, rows: Int) =
    within(columns, rows) && occupied.none { it.chebyshev(this) < NETWORK_MIN_NODE_SPACING }

/** Reading order: from the top left to the right, then the next row. */
internal fun rowMajorPoints(columns: Int, rows: Int): List<GridPoint> =
    (0 until rows).flatMap { y -> (0 until columns).map { x -> GridPoint(x, y) } }

/** Grid points ordered by distance to [from] (ring search), deterministic for equal distances. */
internal fun gridPointsNear(from: GridPoint, columns: Int, rows: Int): List<GridPoint> =
    rowMajorPoints(columns, rows)
        .sortedWith(compareBy({ (it.x - from.x) * (it.x - from.x) + (it.y - from.y) * (it.y - from.y) }, { it.y }, { it.x }))

fun normalizedGridSize(value: Int) = value.coerceIn(NETWORK_GRID_MIN, NETWORK_GRID_SIZE)

/** Exactly one node per favourite, unique free grid points, valid unique lines (repair, not reject). */
fun TransitNetwork.normalized(favorites: List<Favorite>): TransitNetwork {
    val byId = favorites.associateBy { it.id }
    val known = nodes.filter { it.favoriteId in byId }.distinctBy { it.favoriteId }.associateBy { it.favoriteId }
    val occupied = mutableListOf<GridPoint>()
    val c = normalizedGridSize(columns); val r = normalizedGridSize(rows)
    val fixedNodes = favorites.map { f ->
        val node = known[f.id] ?: NetworkNode(f.id)
        val p = node.point?.takeIf { it.free(occupied, c, r) }
        if (p != null) occupied += p
        node.copy(point = p)
    }
    val pairs = mutableSetOf<Set<String>>()
    val ids = mutableSetOf<String>()
    val fixedEdges = edges.filter { e ->
        val a = byId[e.aId]; val b = byId[e.bId]
        a != null && b != null && e.aId != e.bId && e.id.isNotBlank() && !sameLocation(a.location, b.location) &&
            e.hiddenRules.size <= 8 && ids.add(e.id) && pairs.add(setOf(e.aId, e.bId))
    }.take(NETWORK_MAX_EDGES)
    val result = TransitNetwork(fixedNodes, fixedEdges, revision, c, r)
    return if (result == this) this else result
}

/** Lines between two placed nodes that are not hidden by their own time rules right now. */
fun TransitNetwork.activeEdges(now: ZonedDateTime): List<NetworkEdge> {
    val placed = placed.keys
    return edges.filter { it.aId in placed && it.bId in placed && it.hiddenRules.none { r -> r.matches(now) } }
}

private fun StoredState.withNetwork(change: (TransitNetwork) -> TransitNetwork): StoredState {
    val next = change(network)
    return if (next == network) this else copy(network = next.copy(revision = network.revision + 1))
}

/** New favourites get a node at once: at [at] if free, else the next free field in reading order
 * (from the top left), else the tray. */
fun StoredState.withNetworkNodeFor(id: String, at: GridPoint? = null): StoredState {
    if (favorites.none { it.id == id }) return this
    val existing = network.node(id)
    if (existing != null && at == null) return this
    return withNetwork { net ->
        val occupied = net.nodes.filter { it.favoriteId != id }.mapNotNull { it.point }
        val point = at?.takeIf { it.free(occupied, net.columns, net.rows) } ?: existing?.point
            ?: rowMajorPoints(net.columns, net.rows).firstOrNull { it.free(occupied, net.columns, net.rows) }
        if (existing == null) net.copy(nodes = net.nodes + NetworkNode(id, point))
        else net.copy(nodes = net.nodes.map { if (it.favoriteId == id) it.copy(point = point) else it })
    }
}

/** Removes a node, all its lines and a location override pointing at it (the previous origin returns). */
fun StoredState.withoutNetworkNode(id: String): StoredState {
    val restored = (locationOverride?.takeIf { it.nodeId == id }?.let { restoreFromOverride(it) } ?: this)
        .let { if (id in it.overviewPins) it.copy(overviewFavoriteId = null, overviewFavoriteIds = it.overviewPins - id) else it }
    return restored.withNetwork { net -> net.copy(nodes = net.nodes.filterNot { it.favoriteId == id }, edges = net.edges.filterNot { it.touches(id) }) }
}

/** Moves a node to a free grid point; an occupied or invalid point leaves everything unchanged. */
fun StoredState.withNodeMoved(id: String, point: GridPoint): StoredState {
    val node = network.node(id) ?: return this
    val occupied = network.nodes.filter { it.favoriteId != id }.mapNotNull { it.point }
    if (!point.free(occupied, network.columns, network.rows) || node.point == point) return this
    return withNetwork { net -> net.copy(nodes = net.nodes.map { if (it.favoriteId == id) it.copy(point = point) else it }) }
}

/**
 * New grid size: every line is removed and the favourites are placed again in reading order from
 * the top left (in the order of the favourites list); what does not fit goes to the tray. The plan
 * has to be arranged again, but no node can end up outside the grid or on a line.
 */
/**
 * H12: a new grid size keeps what still fits. Growing keeps every position, line and direction;
 * shrinking moves only the favourites outside the new size into the tray ("In Ablage verschieben").
 * Their lines stay stored and appear again as soon as the favourite is placed on the plan again.
 */
fun StoredState.withGridSize(columns: Int, rows: Int): StoredState {
    val c = normalizedGridSize(columns); val r = normalizedGridSize(rows)
    if (c == network.columns && r == network.rows) return this
    val nodes = network.nodes.map { node -> if (node.point?.within(c, r) == false) node.copy(point = null) else node }
    return copy(network = network.copy(nodes = nodes, revision = network.revision + 1, columns = c, rows = r))
}

/** H12: the favourites a smaller grid would move into the tray, in reading order. */
fun StoredState.outsideGrid(columns: Int, rows: Int): List<Favorite> {
    val c = normalizedGridSize(columns); val r = normalizedGridSize(rows)
    val byId = favorites.associateBy { it.id }
    return network.nodes.filter { it.point?.within(c, r) == false }
        .sortedWith(compareBy({ it.point!!.y }, { it.point!!.x })).mapNotNull { byId[it.favoriteId] }
}

/** H12: everything deleting a favourite takes along, so "Rückgängig" can bring all of it back. */
data class FavoriteRemoval(val favorite: Favorite, val index: Int, val node: NetworkNode?,
    val edges: List<Pair<Int, NetworkEdge>>, val overviewPinned: Boolean) {
    val lines: Int get() = edges.size
}

fun StoredState.favoriteRemoval(id: String): FavoriteRemoval? {
    val index = favorites.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
    return FavoriteRemoval(favorites[index], index, network.node(id),
        network.edges.withIndex().filter { it.value.touches(id) }.map { it.index to it.value }, id in overviewPins)
}

/**
 * H12: undoes a deletion with the favourite's place in the list, its field (or the next free one,
 * else the tray), role, lines at their former positions and its overview pin. Lines to favourites
 * deleted in the meantime stay away; a restored favourite that exists again changes nothing.
 */
fun StoredState.withRestoredFavorite(removal: FavoriteRemoval): StoredState {
    val id = removal.favorite.id
    if (favorites.any { it.id == id }) return this
    val list = favorites.toMutableList().apply { add(removal.index.coerceIn(0, size), removal.favorite) }
    val occupied = network.nodes.filter { it.favoriteId != id }.mapNotNull { it.point }
    val point = removal.node?.point?.takeIf { it.free(occupied, network.columns, network.rows) }
        ?: removal.node?.point?.let { rowMajorPoints(network.columns, network.rows).firstOrNull { p -> p.free(occupied, network.columns, network.rows) } }
    val node = (removal.node ?: NetworkNode(id)).copy(point = point)
    val ids = list.map { it.id }.toSet()
    val edges = network.edges.toMutableList()
    removal.edges.sortedBy { it.first }.forEach { (index, edge) ->
        val other = if (edge.aId == id) edge.bId else edge.aId
        if (other in ids && edges.none { it.id == edge.id || it.connects(edge.aId, edge.bId) } && edges.size < NETWORK_MAX_EDGES)
            edges.add(index.coerceIn(0, edges.size), edge)
    }
    val restored = copy(favorites = list, network = network.copy(nodes = network.nodes.filterNot { it.favoriteId == id } + node,
        edges = edges, revision = network.revision + 1))
    return if (removal.overviewPinned && id !in restored.overviewPins && !restored.overviewPinsFull)
        restored.copy(overviewFavoriteId = null, overviewFavoriteIds = restored.overviewPins + id) else restored
}

fun StoredState.withNodeToTray(id: String): StoredState =
    withNetwork { net -> net.copy(nodes = net.nodes.map { if (it.favoriteId == id) it.copy(point = null) else it }) }

fun StoredState.withNodeRole(id: String, role: NodeRole): StoredState =
    withNetwork { net -> net.copy(nodes = net.nodes.map { if (it.favoriteId == id) it.copy(role = role) else it }) }

/** Why a line between two nodes cannot be drawn (null = it can). */
enum class EdgeRejection { SAME_NODE, ALREADY_CONNECTED, SAME_LOCATION, NOT_PLACED, LIMIT }

fun StoredState.edgeRejection(aId: String, bId: String): EdgeRejection? {
    if (aId == bId) return EdgeRejection.SAME_NODE
    val a = favorites.firstOrNull { it.id == aId } ?: return EdgeRejection.NOT_PLACED
    val b = favorites.firstOrNull { it.id == bId } ?: return EdgeRejection.NOT_PLACED
    if (network.node(aId)?.point == null || network.node(bId)?.point == null) return EdgeRejection.NOT_PLACED
    if (network.edges.any { it.connects(aId, bId) }) return EdgeRejection.ALREADY_CONNECTED
    if (sameLocation(a.location, b.location)) return EdgeRejection.SAME_LOCATION
    if (network.edges.size >= NETWORK_MAX_EDGES) return EdgeRejection.LIMIT
    return null
}

/** A drawn line starts in both directions. [id] is chosen by the caller so UI and store share it. */
fun StoredState.withEdge(aId: String, bId: String, id: String = newId()): StoredState =
    if (edgeRejection(aId, bId) != null || network.edges.any { it.id == id }) this
    else withNetwork { net -> net.copy(edges = net.edges + NetworkEdge(id = id, aId = aId, bId = bId)) }

fun StoredState.withEdgeDirection(id: String, mode: DirectionMode): StoredState =
    withNetwork { net -> net.copy(edges = net.edges.map { if (it.id == id) it.copy(direction = mode) else it }) }

fun StoredState.withoutEdge(id: String): StoredState =
    withNetwork { net -> net.copy(edges = net.edges.filterNot { it.id == id }) }

/** Undo of a removal: the same line returns at its former layout position. */
fun StoredState.withRestoredEdge(edge: NetworkEdge, index: Int): StoredState {
    if (network.edges.any { it.id == edge.id || it.connects(edge.aId, edge.bId) }) return this
    if (favorites.none { it.id == edge.aId } || favorites.none { it.id == edge.bId }) return this
    return withNetwork { net -> net.copy(edges = net.edges.toMutableList().apply { add(index.coerceIn(0, size), edge) }) }
}

/**
 * First layout of an existing install: geographic projection (north up, aspect kept) onto points
 * 1…10, each favourite on the nearest free point. Members of former pairs follow each other.
 * Favourites without coordinates and anything beyond the capacity go to the tray. Deterministic.
 */
fun TransitNetwork.autoLayout(favorites: List<Favorite>): TransitNetwork {
    val byId = favorites.associateBy { it.id }
    val order = mutableListOf<String>()
    favorites.forEach { f ->
        if (f.id !in order) order += f.id
        edges.filter { it.touches(f.id) }.map { it.other(f.id) }.filter { it !in order }.forEach { order += it }
    }
    val located = order.mapNotNull { id -> byId[id]?.location?.takeIf { it.hasCoordinates }?.let { id to it } }
    val targets = mutableMapOf<String, GridPoint>()
    if (located.isNotEmpty()) {
        val meanLat = Math.toRadians(located.map { it.second.latitude!! }.average())
        val xs = located.map { it.second.longitude!! * cos(meanLat) }
        val ys = located.map { -it.second.latitude!! }
        val minX = xs.min(); val minY = ys.min()
        val spanX = xs.max() - minX; val spanY = ys.max() - minY
        val span = max(spanX, spanY).takeIf { it > 1e-9 }
        located.forEachIndexed { i, (id, _) ->
            val fx = span?.let { (xs[i] - minX) / it } ?: .5
            val fy = span?.let { (ys[i] - minY) / it } ?: .5
            // Centre the shorter side inside the 1…10 frame.
            val ox = span?.let { (1 - spanX / it) / 2 } ?: 0.0
            val oy = span?.let { (1 - spanY / it) / 2 } ?: 0.0
            targets[id] = GridPoint((1 + (fx + ox) * 9).roundToInt(), (1 + (fy + oy) * 9).roundToInt())
        }
    }
    val occupied = mutableListOf<GridPoint>()
    val points = mutableMapOf<String, GridPoint?>()
    order.forEach { id ->
        val near = targets[id] ?: GridPoint(0, 0)
        val p = gridPointsNear(near, columns, rows).firstOrNull { it.free(occupied, columns, rows) }
        if (p != null) occupied += p
        points[id] = p
    }
    return copy(nodes = nodes.map { it.copy(point = points[it.favoriteId]) }, revision = revision + 1)
}

/**
 * Former favourite pairs → lines (build 66). Runs once and is idempotent: pair IDs become line
 * IDs, pairs worked in both directions (nearer → farther), their time rules move to the line.
 */
fun StoredState.migrateToNetwork(): StoredState {
    val base = migrateFavoritePairs()
    if (base.schemaVersion >= 10 && base.favoritePairs.isEmpty()) {
        val normalized = base.network.normalized(base.favorites)
        return if (normalized == base.network) base else base.copy(network = normalized)
    }
    val known = base.network.edges.map { it.id }.toSet()
    val migrated = base.favoritePairs.filter { it.validIn(base.favorites) && it.id !in known }.map { p ->
        NetworkEdge(id = p.id, aId = p.firstId, bId = p.secondId, direction = DirectionMode.BOTH, rule = p.rule, hiddenRules = p.hiddenRules)
    }
    val network = base.network.copy(edges = base.network.edges + migrated, revision = base.network.revision + 1).normalized(base.favorites)
    val placed = if (network.nodes.any { it.point != null }) network else network.autoLayout(base.favorites)
    return base.copy(schemaVersion = 10, network = placed, favoritePairs = emptyList())
}

private val TRANSFER_ICONS = setOf(FavoriteIcon.TRAIN, FavoriteIcon.DIRECTIONS_BUS, FavoriteIcon.STOP)

/**
 * AUTO: the symbol is the hint; the node editor can always override it.
 *  - train, bus or stop symbol with ≥ 2 lines → transfer point
 *  - neutral place symbol with exactly 2 lines → transfer point (a link in a row)
 *  - every named destination (home, work, school …) → destination, whatever its number of lines
 */
fun TransitNetwork.effectiveRoles(favorites: Map<String, Favorite>): Map<String, NodeRole> {
    val degree = edges.flatMap { listOf(it.aId, it.bId) }.groupingBy { it }.eachCount()
    return nodes.associate { n ->
        val icon = favorites[n.favoriteId]?.icon
        val d = degree[n.favoriteId] ?: 0
        n.favoriteId to when (n.role) {
            NodeRole.AUTO -> when {
                icon in TRANSFER_ICONS && d >= 2 -> NodeRole.TRANSFER
                icon == FavoriteIcon.PLACE && d == 2 -> NodeRole.TRANSFER
                else -> NodeRole.DESTINATION
            }
            else -> n.role
        }
    }
}

data class NetworkPath(val nodeIds: List<String>, val edgeIds: List<String>) {
    val via: List<String> get() = nodeIds.drop(1).dropLast(1)
}

/** From [anchorId] along allowed directions: destinations end a route, transfer points are passed. */
fun reachableTargets(anchorId: String, edges: List<NetworkEdge>, roles: Map<String, NodeRole>): Map<String, List<NetworkPath>> {
    val out = edges.flatMap { it.arcs() }.groupBy { it.from }.mapValues { (_, a) -> a.sortedBy { it.to } }
    val result = sortedMapOf<String, MutableList<NetworkPath>>()
    fun walk(nodes: List<String>, edgeIds: List<String>) {
        for (arc in out[nodes.last()].orEmpty()) {
            if (arc.to in nodes) continue
            val n = nodes + arc.to; val e = edgeIds + arc.edgeId
            if (roles[arc.to] == NodeRole.TRANSFER) { if (e.size < NETWORK_MAX_HOPS) walk(n, e) }
            else result.getOrPut(arc.to) { mutableListOf() } += NetworkPath(n, e)
        }
    }
    walk(listOf(anchorId), emptyList())
    return result.mapValues { (_, p) ->
        p.sortedWith(compareBy({ it.edgeIds.size }, { it.nodeIds.joinToString() })).take(NETWORK_MAX_PATHS_PER_TARGET)
    }
}

/**
 * Orientation of a line tap (nearer → farther) with the hysteresis of build 65: a member must be
 * clearly nearer before the direction swaps; close or unknown distances keep the last direction.
 */
object EdgeOrientation {
    const val HYSTERESIS_METERS = 150.0
    const val HYSTERESIS_FRACTION = .15
    private val remembered = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    fun reset() = remembered.clear()
    fun firstIsNear(edgeId: String, a: Favorite, b: Favorite, position: Location?, accuracyMeters: Float?): Boolean {
        val previous = remembered[edgeId]
        val da = position?.let { distanceMeters(it, a.location) }
        val db = position?.let { distanceMeters(it, b.location) }
        val decided = if (da == null || db == null) null else {
            val margin = maxOf(HYSTERESIS_METERS, (accuracyMeters?.toDouble() ?: 0.0) * 2.0, minOf(da, db) * HYSTERESIS_FRACTION)
            when { db + margin < da -> false; da + margin < db -> true; else -> null }
        }
        return (decided ?: previous ?: true).also { remembered[edgeId] = it }
    }
}

/** A line as routing target: the farther end is the destination, the nearer end the fixed origin. */
data class EdgeMembers(val edge: NetworkEdge, val near: Favorite, val far: Favorite) {
    /** Presentation/routing adapter, never saved. The destination owns its filter. */
    fun routingFavorite() = far.copy(id = edge.id, counterpole = near.location, originAccentSeed = near.accentSeed,
        originLabel = near.label, rule = edge.rule, hiddenRules = edge.hiddenRules)
}

/** Direction of a line tap. A one-way line always wins over the position. */
fun StoredState.edgeMembers(edge: NetworkEdge, position: Location?, accuracyMeters: Float? = null): EdgeMembers? {
    val a = favorites.firstOrNull { it.id == edge.aId } ?: return null
    val b = favorites.firstOrNull { it.id == edge.bId } ?: return null
    return when (edge.direction) {
        DirectionMode.A_TO_B -> EdgeMembers(edge, a, b)
        DirectionMode.B_TO_A -> EdgeMembers(edge, b, a)
        DirectionMode.BOTH -> if (EdgeOrientation.firstIsNear(edge.id, a, b, position, accuracyMeters)) EdgeMembers(edge, a, b) else EdgeMembers(edge, b, a)
    }
}

/** The position decided against a one-way line (the person stands nearer its destination end). */
fun StoredState.edgeAgainstPosition(edge: NetworkEdge, position: Location?): Boolean {
    if (edge.direction == DirectionMode.BOTH || position == null) return false
    val members = edgeMembers(edge, position) ?: return false
    val dNear = distanceMeters(position, members.near.location) ?: return false
    val dFar = distanceMeters(position, members.far.location) ?: return false
    return dFar + EdgeOrientation.HYSTERESIS_METERS < dNear
}

/** First hold on a node: the whole app uses its place as location. Second hold on it: back to before. */
fun StoredState.toggleLocationOverride(nodeId: String, now: Long): StoredState {
    val current = locationOverride
    if (current != null && current.nodeId == nodeId) return restoreFromOverride(current)
    val favorite = favorites.firstOrNull { it.id == nodeId }?.takeIf { it.location.routable } ?: return this
    return copy(
        locationOverride = UserLocationOverride(nodeId, favorite.location, now,
            previousOriginMode = current?.previousOriginMode ?: settings.originMode,
            previousManualOrigin = current?.previousManualOrigin ?: settings.manualOrigin,
            previousManualOriginChangedAt = current?.previousManualOriginChangedAt ?: settings.manualOriginChangedAt),
        settings = settings.copy(originMode = OriginMode.MANUAL, manualOrigin = favorite.location, manualOriginChangedAt = now))
}

/** At most this many favourites are pinned to the overview at the same time. */
const val MAX_OVERVIEW_FAVORITES = 5

/** Pinned favourites in pinning order: the build 81 list, or the single build 74 pin of older data. */
val StoredState.overviewPins: List<String>
    get() = overviewFavoriteIds.ifEmpty { listOfNotNull(overviewFavoriteId) }
        .filter { id -> favorites.any { it.id == id } }.distinct().take(MAX_OVERVIEW_FAVORITES)

/** True while one more favourite may be pinned to the overview. */
val StoredState.overviewPinsFull: Boolean get() = overviewPins.size >= MAX_OVERVIEW_FAVORITES

/** Holding a favourite adds it to the overview, holding it again
 * releases it. Up to [MAX_OVERVIEW_FAVORITES] favourites; with the list full a new one is not added. */
fun StoredState.toggleOverviewFavorite(id: String): StoredState {
    val pins = overviewPins
    return withOverviewFavorites(when {
        id in pins -> pins - id
        pins.size >= MAX_OVERVIEW_FAVORITES -> pins
        else -> pins + id
    })
}

/** Idempotent form of [toggleOverviewFavorite] (optimistic UI overlay and its persisted write). */
fun StoredState.withOverviewFavorites(ids: List<String>): StoredState {
    val clean = ids.filter { id -> favorites.any { it.id == id } }.distinct().take(MAX_OVERVIEW_FAVORITES)
    return if (clean == overviewFavoriteIds && overviewFavoriteId == null) this
    else copy(overviewFavoriteId = null, overviewFavoriteIds = clean)
}

/** Single-pin form, kept for callers that set exactly one pin (or none). */
fun StoredState.withOverviewFavorite(id: String?): StoredState = withOverviewFavorites(listOfNotNull(id))

fun StoredState.restoreFromOverride(o: UserLocationOverride): StoredState = copy(locationOverride = null,
    settings = settings.copy(originMode = o.previousOriginMode, manualOrigin = o.previousManualOrigin,
        manualOriginChangedAt = o.previousManualOriginChangedAt))

/** On read: a newer manual choice of the person wins over the override; a removed node restores. */
fun StoredState.normalizedOverride(): StoredState {
    val o = locationOverride ?: return this
    val stillApplied = settings.originMode == OriginMode.MANUAL && settings.manualOrigin == o.location
    return when {
        !stillApplied -> copy(locationOverride = null)
        favorites.none { it.id == o.nodeId } -> restoreFromOverride(o)
        else -> this
    }
}


/** M17: a line searched in an explicitly named direction; the destination's transport filter applies, as for a tapped line. */
fun StoredState.routeQuery(from: Favorite, to: Favorite, time: Long, mode: TimeMode): JourneyQuery =
    JourneyQuery(from.location, to.location, time, mode, to.transportFilter?.map { it.lineType }?.toSet() ?: settings.products,
        walkingSpeed = settings.walkingSpeed, ignoreWalking = settings.ignoreWalking, transportFilter = to.transportFilter)
