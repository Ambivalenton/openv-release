// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable
import java.util.Collections
import java.util.WeakHashMap

@Serializable data class RoutePoint(val lat:Double,val lon:Double)

/** Optional provider geometry is strictly bounded, validated and kept in travel order. */
fun boundedRouteShape(points:List<RoutePoint>):List<RoutePoint> {
    if(points.size<2 || points.any {!it.lat.isFinite() || it.lat !in -90.0..90.0 || !it.lon.isFinite() || it.lon !in -180.0..180.0})return emptyList()
    val selected=if(points.size<=512)points else List(512){i->points[(i.toLong()*(points.size-1)/511).toInt()]}
    if(selected.zipWithNext().any {(a,b)->(distanceMeters(Location("",latitude=a.lat,longitude=a.lon),Location("",latitude=b.lat,longitude=b.lon)) ?: Double.MAX_VALUE)>100_000})return emptyList()
    return selected.distinctUntilConsecutive().takeIf {it.size>=2} ?: emptyList()
}
private fun List<RoutePoint>.distinctUntilConsecutive()=filterIndexed {i,p->i==0 || p!=this[i-1]}

private data class GeometryLayout(val points:List<JourneyFocusStop>,val offsets:List<Double>)
// Values contain coordinates/calls, not the TripSegment key; obsolete responses can be collected.
private val layouts=Collections.synchronizedMap(WeakHashMap<TripSegment,GeometryLayout>())
private fun geometryLayout(leg:TripSegment):GeometryLayout? {
    layouts[leg]?.let {return it}
    if(leg.routeShape.size !in 2..512)return null
    val points=leg.routeShape.mapIndexed {i,p->JourneyFocusStop("shape-$i",Location("",latitude=p.lat,longitude=p.lon),0,0,"",null)}
    if((distanceMeters(leg.origin,points.first().location) ?: Double.MAX_VALUE)>400 ||
        (distanceMeters(leg.destination,points.last().location) ?: Double.MAX_VALUE)>400)return null
    val calls=focusStops(Trip("geometry",listOf(leg),"",0))
    val offsets=calls.map {stop->projectOntoStops(points,stop.location)?.takeIf {it.cross<=400}?.along ?: return null}
    if(offsets.zipWithNext().any {(a,b)->b+35<a})return null
    return GeometryLayout(points,offsets).also {layouts[leg]=it}
}

internal fun projectOntoJourneyLeg(leg:TripSegment,stops:List<JourneyFocusStop>,location:Location,alongHint:Double?=null,maxAdvance:Double=Double.POSITIVE_INFINITY):RouteProjection? {
    val geometry=geometryLayout(leg) ?: return projectOntoStops(stops,location,alongHint,maxAdvance)
    val projection=projectOntoStops(geometry.points,location,alongHint,maxAdvance) ?: return null
    val edge=geometry.offsets.indexOfLast {it<=projection.along}.coerceIn(0,(stops.size-2).coerceAtLeast(0))
    return projection.copy(edge=edge)
}
internal fun journeyStopDistances(leg:TripSegment,stops:List<JourneyFocusStop>):List<Double> {
    geometryLayout(leg)?.takeIf {it.offsets.size==stops.size}?.let {return it.offsets}
    var sum=0.0
    return stops.mapIndexed {i,s->if(i>0)sum+=distanceMeters(stops[i-1].location,s.location) ?: 0.0;sum}
}
internal fun hasJourneyGeometry(leg:TripSegment)=geometryLayout(leg)!=null
