// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import kotlin.math.*

/** Forward-precedence constrained path TSP. Geometry is a heuristic, never a timetable guarantee. */
class WaypointOrderOptimizer {
    companion object { const val LOCAL_FORWARD_CELL_METERS=50.0 }
    fun order(origin:Location,destination:Location,requested:List<Location>,checkCancelled:()->Unit={}):List<Location> {
        val stops=distinctWaypoints(requested)
        require((listOf(origin,destination)+stops).all {it.hasCoordinates}) {
            tr("Für die Stopp-Reihenfolge werden Orte mit Koordinaten benötigt.")
        }
        if(stops.isEmpty()) return stops
        val length=dev.ambivalenton.openv.data.model.distanceMeters(origin,destination)!!
        require(length>50.0) {tr("Für Zwischenstopps bitte unterschiedliche Start- und Zielorte wählen.")}
        fun bearing(a:Location,b:Location):Double {
            val lat1=Math.toRadians(a.latitude!!);val lat2=Math.toRadians(b.latitude!!)
            val lon=Math.toRadians(b.longitude!!-a.longitude!!)
            return atan2(sin(lon)*cos(lat2),cos(lat1)*sin(lat2)-sin(lat1)*cos(lat2)*cos(lon))
        }
        val direction=bearing(origin,destination)
        fun progress(stop:Location):Double {
            val distance=dev.ambivalenton.openv.data.model.distanceMeters(origin,stop)!!/6_371_008.8
            return atan2(sin(distance)*cos(bearing(origin,stop)-direction),cos(distance))*6_371_008.8
        }
        val along=stops.map(::progress)
        require(along.all {it>=-LOCAL_FORWARD_CELL_METERS && it<=length+LOCAL_FORWARD_CELL_METERS}) {
            tr("Dieser Zwischenstopp liegt hinter dem Start oder Ziel und würde eine Rückfahrt erfordern.")
        }
        val cells=along.map {floor(it.coerceIn(0.0,length)/LOCAL_FORWARD_CELL_METERS).toLong()}
        // Dense symmetric geographical cost matrix for small sets; on-demand edges for larger sets.
        val points=listOf(origin)+stops+destination
        val matrix=if(points.size<=64) Array(points.size) {a->DoubleArray(points.size) {b->
            dev.ambivalenton.openv.data.model.distanceMeters(points[a],points[b])!!
        }} else null
        fun cost(a:Int,b:Int)=matrix?.get(a)?.get(b)
            ?: dev.ambivalenton.openv.data.model.distanceMeters(points[a],points[b])!!
        val ordered=mutableListOf<Int>();var before=0
        cells.indices.groupBy {cells[it]}.toSortedMap().values.forEach {group->
            checkCancelled()
            val pending=group.map {it+1}.toMutableList()
            // Cheapest forward insertion, then local 2-opt. Cell order can never be reversed.
            val local=mutableListOf<Int>()
            for(point in pending) {
                checkCancelled()
                var best=0;var delta=Double.POSITIVE_INFINITY
                for(slot in 0..local.size) {
                    val a=if(slot==0) before else local[slot-1]
                    val b=local.getOrNull(slot) ?: points.lastIndex
                    val value=cost(a,point)+cost(point,b)-cost(a,b)
                    if(value<delta) {delta=value;best=slot}
                }
                local.add(best,point)
            }
            repeat(2) {
                checkCancelled()
                for(i in 0 until local.lastIndex) for(j in i+1..minOf(local.lastIndex,i+12)) {
                    val a=if(i==0) before else local[i-1];val b=local.getOrNull(j+1) ?: points.lastIndex
                    if(cost(a,local[j])+cost(local[i],b)+.01<cost(a,local[i])+cost(local[j],b))
                        local.subList(i,j+1).reverse()
                }
            }
            ordered+=local;before=local.last()
        }
        return ordered.map {stops[it-1]}
    }
}
