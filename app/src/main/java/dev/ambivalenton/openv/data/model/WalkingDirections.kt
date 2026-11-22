// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

enum class WalkingTurn { FORWARD, LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, UTURN, ROUNDABOUT, STAIRS, ELEVATOR, ARRIVE }
data class WalkingManeuver(val instruction:String,val turn:WalkingTurn,val meters:Double,val seconds:Double,
    val beginShapeIndex:Int,val endShapeIndex:Int,val streetNames:List<String> = emptyList(),val beginStreetNames:List<String> = emptyList(),
    /** Number of traversable exits excluding the incoming edge; null for legacy native JSON. */
    val junctionExits:Int? = null)
data class WalkingDirections(val maneuvers:List<WalkingManeuver>,val shape:List<Location>,val meters:Double,val seconds:Double)

/** A physically active walk wins; otherwise offer the next walk, including the final egress. */
fun walkingNavigationSegment(trip:Trip,now:Long,activeLegId:String?):TripSegment? {
    trip.segments.withIndex().firstOrNull {"${it.index}:${it.value.id}"==activeLegId && it.value.isWalk}
        ?.value?.let {return it}
    return trip.segments.firstOrNull {it.isWalk && it.endTime>=now}
}
