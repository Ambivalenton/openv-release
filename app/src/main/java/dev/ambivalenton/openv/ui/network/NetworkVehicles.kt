// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.ambivalenton.openv.data.model.DirectionMode
import dev.ambivalenton.openv.data.model.Favorite
import dev.ambivalenton.openv.data.model.FavoriteIcon
import dev.ambivalenton.openv.data.model.LineType
import dev.ambivalenton.openv.data.model.NetworkEdge
import dev.ambivalenton.openv.data.model.Trip
import dev.ambivalenton.openv.ui.NetworkCountdown
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.min
import kotlin.random.Random

/*
 * Small trains, trams and buses, seen from above, that travel along the lines of the network plan
 * (build 67, consists in build 68). They are decoration: they follow the direction the person chose
 * for a line (both directions: one vehicle each way; one-way: only that way) and never stand for a
 * real vehicle position or a real departure. The kind comes from real journeys of the line where
 * the app knows one; each trip draws its own length (wagons, articulated bus) at random, longer
 * lines allow longer vehicles. Pure functions here; the frame loop lives in NetworkPlan.
 */

/** One vehicle of a line. [forward] = from the line's first node (a) to its second (b). */
internal data class VehicleRun(val forward: Boolean, val phase: Float)

/** [cycle] counts the trips of a run; each trip draws its own consist from it. */
internal data class VehiclePlacement(val distance: Float, val alpha: Float, val cycle: Int = 0)

/** Both directions get a vehicle each (half a cycle apart); a one-way line only its direction. */
internal fun vehicleRuns(direction: DirectionMode, index: Int): List<VehicleRun> {
    val offset = (index * .37f) % 1f
    return when (direction) {
        DirectionMode.BOTH -> listOf(VehicleRun(true, offset), VehicleRun(false, (offset + .5f) % 1f))
        DirectionMode.A_TO_B -> listOf(VehicleRun(true, offset))
        DirectionMode.B_TO_A -> listOf(VehicleRun(false, offset))
    }
}

/** Seconds a vehicle needs for [usable] pixels: about 0.75 grid fields per second, 2.5…12 s. */
internal fun vehicleTravelSeconds(usable: Float, pitch: Float) = (usable / (pitch * .75f)).coerceIn(2.5f, 12f)
internal const val VEHICLE_DWELL_SECONDS = 2.2f

/**
 * Position at [clock] seconds: the vehicle leaves its node, accelerates, brakes into the other node
 * and waits there out of sight. Null while it waits or when the line is too short for a vehicle.
 * [distance] is measured along the path from node a; [inset] keeps the vehicle's front out of the nodes.
 */
internal fun vehiclePlacement(length: Float, inset: Float, pitch: Float, clock: Float, run: VehicleRun): VehiclePlacement? {
    val usable = length - 2 * inset
    if (!usable.isFinite() || usable <= pitch * .2f || pitch <= 0f) return null
    val travel = vehicleTravelSeconds(usable, pitch)
    val cycle = travel + VEHICLE_DWELL_SECONDS
    val total = clock + run.phase * cycle
    val t = total % cycle
    if (t >= travel) return null
    val f = t / travel
    val eased = f * f * (3 - 2 * f)
    val along = inset + usable * eased
    return VehiclePlacement(if (run.forward) along else length - along, (min(f, 1 - f) / .08f).coerceIn(0f, 1f), (total / cycle).toInt())
}

/**
 * The vehicle drives *into* the nodes instead of fading out before them. Its
 * front runs from the centre of its start node to [tail] beyond the centre of its end node, so at
 * both ends every body is inside a node. The nodes are drawn above the vehicles and the vehicle layer
 * is clipped at their shapes, so a vehicle appears and disappears body part by body part at the
 * shape's edge. [tail] is the longest possible consist in px. Always fully opaque.
 */
internal fun vehiclePassage(length: Float, tail: Float, pitch: Float, clock: Float, run: VehicleRun): VehiclePlacement? {
    val usable = length + tail
    if (!usable.isFinite() || length <= 0f || pitch <= 0f) return null
    val travel = vehicleTravelSeconds(usable, pitch)
    val cycle = travel + VEHICLE_DWELL_SECONDS
    val total = clock + run.phase * cycle
    val t = total % cycle
    if (t >= travel) return null
    val f = t / travel
    val along = usable * f * f * (3 - 2 * f)
    return VehiclePlacement(if (run.forward) along else length - along, 1f, (total / cycle).toInt())
}

/** Longest consist [vehicleConsist] can draw for [kind] on [fields], in line widths (gaps included). */
internal fun maxConsistLength(kind: VehicleKind, fields: Float): Float = when (kind) {
    VehicleKind.TRAIN -> { val wagons = when { fields < 2.2f -> 0; fields < 3.4f -> 1; fields < 4.8f -> 2; else -> 3 }
        2.3f + wagons * (1.8f + vehicleGap(kind)) }
    VehicleKind.BUS -> if (fields >= 2f) 1.55f + 1.25f + vehicleGap(kind) else 1.9f
    VehicleKind.TRAM -> { val sections = if (fields >= 3.2f) 3 else 2; sections * 1.35f + (sections - 1) * vehicleGap(kind) }
}

/** Settings preview without motion (build 70): each vehicle stands on the open part of its line. */
internal fun stillVehiclePlacement(length: Float, inset: Float, pitch: Float, run: VehicleRun): VehiclePlacement? {
    val usable = length - 2 * inset
    if (!usable.isFinite() || usable <= pitch * .2f || pitch <= 0f) return null
    val along = inset + usable * (.3f + .4f * run.phase)
    return VehiclePlacement(if (run.forward) along else length - along, 1f, 0)
}

internal enum class VehicleKind { TRAIN, TRAM, BUS }

/** Vehicle family of a real leg; null when it says nothing (walk, ferry, other). */
internal fun vehicleKind(type: LineType): VehicleKind? = when (type) {
    LineType.BUS -> VehicleKind.BUS
    LineType.TRAM, LineType.SUBWAY -> VehicleKind.TRAM
    LineType.SUBURBAN, LineType.REGIONAL, LineType.ICE -> VehicleKind.TRAIN
    else -> null
}

/** Main vehicle of a journey: the ride the person sits in longest. */
internal fun mainVehicleKind(trip: Trip?): VehicleKind? = trip?.segments
    ?.filter { !it.isWalk && vehicleKind(it.lineType) != null }
    ?.maxByOrNull { it.endTime - it.startTime }?.let { vehicleKind(it.lineType) }

/**
 * What the app has really seen on a line in this process: the overview stores the main vehicle of
 * every journey it found over a direct line. Process-local and small (one entry per line); it is
 * never saved and never leaves the device.
 */
object NetworkVehicleMemory {
    private val kinds = ConcurrentHashMap<String, LineType>()
    fun record(edgeId: String, trip: Trip?) {
        val leg = trip?.segments?.filter { !it.isWalk && vehicleKind(it.lineType) != null }?.maxByOrNull { it.endTime - it.startTime } ?: return
        kinds[edgeId] = leg.lineType
    }
    internal fun kind(edgeId: String): VehicleKind? = kinds[edgeId]?.let(::vehicleKind)
    fun clear() = kinds.clear()
}

/**
 * Kind of vehicle on a line, as exact as the app can know it: the next real departure of the line
 * (departure chips), a journey the overview found over it, the transport filter of its places,
 * their symbols, and only then alternating trains and buses so that a plan shows both.
 */
internal fun vehicleKindFor(edge: NetworkEdge, favorites: Map<String, Favorite>, countdown: NetworkCountdown, index: Int): VehicleKind {
    countdown.chips.firstOrNull { it.edgeId == edge.id }?.trip?.segments
        ?.firstOrNull { !it.isWalk && vehicleKind(it.lineType) != null }?.let { return vehicleKind(it.lineType)!! }
    NetworkVehicleMemory.kind(edge.id)?.let { return it }
    val ends = listOfNotNull(favorites[edge.aId], favorites[edge.bId])
    val filtered = ends.mapNotNull { f -> f.transportFilter?.mapNotNull { vehicleKind(it.lineType) }?.toSet()?.singleOrNull() }.toSet()
    filtered.singleOrNull()?.let { return it }
    val icons = ends.map { it.icon }
    return when {
        FavoriteIcon.DIRECTIONS_BUS in icons || FavoriteIcon.STOP in icons -> VehicleKind.BUS
        FavoriteIcon.TRAIN in icons -> VehicleKind.TRAIN
        index % 2 == 1 -> VehicleKind.BUS
        else -> VehicleKind.TRAIN
    }
}

/** One body of a vehicle; lengths in line widths. [joint] = bellows to the body in front (bus, tram). */
internal data class VehicleCar(val length: Float, val head: Boolean, val tail: Boolean, val joint: Boolean)

/**
 * The bodies of one trip, front first. [fields] is the free line length in grid fields; [seed] makes
 * every trip of every line draw its own length, but the same trip never changes while it runs.
 *  - train: a powered head car and 0–3 wagons (more possible on longer lines),
 *  - bus: now and then an articulated bus on lines of 2 fields and more,
 *  - tram: two sections, sometimes three on longer lines.
 */
internal fun vehicleConsist(kind: VehicleKind, fields: Float, seed: Int): List<VehicleCar> {
    val random = Random(seed)
    return when (kind) {
        VehicleKind.TRAIN -> {
            val maxWagons = when { fields < 2.2f -> 0; fields < 3.4f -> 1; fields < 4.8f -> 2; else -> 3 }
            val wagons = random.nextInt(maxWagons + 1)
            listOf(VehicleCar(2.3f, head = true, tail = wagons == 0, joint = false)) +
                List(wagons) { i -> VehicleCar(1.8f, head = false, tail = i == wagons - 1, joint = false) }
        }
        VehicleKind.BUS -> if (fields >= 2f && random.nextFloat() < .35f)
            listOf(VehicleCar(1.55f, true, false, false), VehicleCar(1.25f, false, true, true))
        else listOf(VehicleCar(1.9f, true, true, false))
        VehicleKind.TRAM -> {
            val sections = if (fields >= 3.2f && random.nextFloat() < .45f) 3 else 2
            List(sections) { i -> VehicleCar(1.35f, head = i == 0, tail = i == sections - 1, joint = i > 0) }
        }
    }
}

/** Gap between two bodies, in line widths: couplings for trains, bellows for buses and trams. */
internal fun vehicleGap(kind: VehicleKind) = if (kind == VehicleKind.TRAIN) .22f else .12f

/** Stable seed of one trip of one run on one line. */
internal fun vehicleSeed(edgeId: String, forward: Boolean, cycle: Int): Int = (edgeId.hashCode() * 31 + if (forward) 17 else 5) * 31 + cycle

/**
 * Top view of one body, centred at [at] and heading along [heading] (unit vector). Trains have a
 * rounded nose on the head car and square-ish wagons with a roof stripe in the line's colour; buses
 * a windscreen; trams rounded ends at both sides and a pantograph on the middle section.
 */
internal fun DrawScope.drawVehicleCar(kind: VehicleKind, car: VehicleCar, index: Int, at: Offset, heading: Offset, lineWidth: Float,
                                     alpha: Float, body: Color, outline: Color, accent: Color) {
    if (alpha <= 0f) return
    val width = lineWidth * if (kind == VehicleKind.TRAM) .98f else 1.05f
    val length = lineWidth * car.length
    val topLeft = Offset(at.x - length / 2, at.y - width / 2)
    val corner = when {
        kind == VehicleKind.TRAM -> width * .45f
        kind == VehicleKind.BUS -> width * .26f
        car.head || car.tail -> width * .42f
        else -> width * .18f
    }
    rotate(Math.toDegrees(atan2(heading.y, heading.x).toDouble()).toFloat(), at) {
        drawRoundRect(body.copy(alpha = alpha), topLeft, Size(length, width), CornerRadius(corner))
        drawRoundRect(outline.copy(alpha = .85f * alpha), topLeft, Size(length, width), CornerRadius(corner), style = Stroke(lineWidth * .12f))
        // Roof stripe in the line's colour.
        val stripeStart = if (car.head) .16f else .12f
        val stripeLength = if (car.head) .56f else .76f
        drawRoundRect(accent.copy(alpha = alpha), Offset(topLeft.x + length * stripeStart, at.y - width * .16f),
            Size(length * stripeLength, width * .32f), CornerRadius(width * .16f))
        // The bright end is the windscreen at the front; a tram has one at both ends.
        if (car.head) drawRoundRect(outline.copy(alpha = .55f * alpha), Offset(topLeft.x + length * .82f, at.y - width * .3f),
            Size(length * .09f, width * .6f), CornerRadius(width * .08f))
        if (kind == VehicleKind.TRAM && car.tail) drawRoundRect(outline.copy(alpha = .4f * alpha), Offset(topLeft.x + length * .09f, at.y - width * .3f),
            Size(length * .09f, width * .6f), CornerRadius(width * .08f))
        // Tram: pantograph on the middle (or first) section.
        if (kind == VehicleKind.TRAM && index == 1) {
            drawLine(outline.copy(alpha = .8f * alpha), Offset(at.x - length * .12f, at.y - width * .34f), Offset(at.x + length * .12f, at.y + width * .34f), lineWidth * .08f)
            drawLine(outline.copy(alpha = .8f * alpha), Offset(at.x + length * .12f, at.y - width * .34f), Offset(at.x - length * .12f, at.y + width * .34f), lineWidth * .08f)
        }
        // Bellows of an articulated bus or tram, at the front end of this body.
        if (car.joint) drawRoundRect(outline.copy(alpha = .5f * alpha), Offset(topLeft.x + length - lineWidth * .06f, at.y - width * .36f),
            Size(lineWidth * .18f, width * .72f), CornerRadius(lineWidth * .04f))
    }
}
