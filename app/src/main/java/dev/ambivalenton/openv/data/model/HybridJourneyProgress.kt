// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlin.math.*

/** Display-only result: no trip, time, routing preference or server response is modified. */
enum class JourneyPositionSource { GPS, GPS_UNMATCHED, TIMETABLE, GPS_LAST_KNOWN }
data class VisualJourneyProgress(val nextIndex: Int,val corrected: Boolean=false,val computedAt: Long=0,
    val physicalEtaMinutes: Int?=null,val fraction: Float?=null,val estimated:Boolean=false,val boardingLegId:String?=null,val stableLegId:String?=null,val completedRideKeys:Set<String> = emptySet(),
    val positionSource:JourneyPositionSource=if(corrected) JourneyPositionSource.GPS else JourneyPositionSource.TIMETABLE,
    val passedStopKeys:Set<String> = emptySet())
internal data class RouteProjection(val edge: Int,val along: Double,val cross: Double,val total: Double)

internal fun projectOntoStops(stops: List<JourneyFocusStop>, location: Location, alongHint:Double?=null,maxAdvance:Double=Double.POSITIVE_INFINITY): RouteProjection? {
    if (!location.hasCoordinates) return null
    var distance=0.0
    val candidates=mutableListOf<RouteProjection>()
    val lat=location.latitude!!*PI/180
    fun xy(p: Location): Pair<Double,Double>? = if (!p.hasCoordinates) null else
        ((p.longitude!!-location.longitude!!)*PI/180*cos(lat)*6_371_000) to ((p.latitude!!-location.latitude!!)*PI/180*6_371_000)
    stops.zipWithNext().forEachIndexed { i,(a,b) ->
        val aa=xy(a.location);val bb=xy(b.location)
        if (aa!=null && bb!=null) {
            val dx=bb.first-aa.first;val dy=bb.second-aa.second;val length=hypot(dx,dy)
            val meters=distanceMeters(a.location,b.location) ?: length
            if (length>1) {
                val t=(-(aa.first*dx+aa.second*dy)/(length*length)).coerceIn(0.0,1.0)
                candidates+=RouteProjection(i,distance+t*meters,hypot(aa.first+t*dx,aa.second+t*dy),0.0)
            }
            distance+=meters
        }
    }
    val plausible=if(alongHint==null)candidates else candidates.filter {it.along in (alongHint-100)..(alongHint+maxAdvance)}
    val nearest=plausible.minByOrNull {it.cross} ?: return null
    // A route can cross itself or return to its first stop. Prefer continuity within the
    // accuracy corridor, never a geometrically adjacent but kilometres-away route edge.
    val best=if(alongHint==null)nearest else plausible.filter {it.cross<=nearest.cross+20}.minByOrNull {abs(it.along-alongHint)} ?: nearest
    if(alongHint==null && candidates.any {it.cross<=best.cross+20 && abs(it.along-best.along)>1_500}) return null
    return best.copy(total=distance)
}

fun crediblePresence(p: DevicePresence?,now: Long,maxAge: Long=30_000): DevicePresence? = p?.takeIf {
    now-it.capturedAt in 0..maxAge && it.accuracyMeters.isFinite() && it.accuracyMeters in 0f..100f && it.location.hasCoordinates
}

/** Two credible observations and a >=4-minute discrepancy are required for ETA-based correction. */
fun hybridJourneyProgress(trip: Trip,now: Long,observations: List<DevicePresence>): VisualJourneyProgress {
    val stops=focusStops(trip)
    val scheduled=stops.indexOfFirst { it.departure>=now }.let { if(it<0) stops.size else it }
    val fallback=VisualJourneyProgress(scheduled,computedAt=now)
    val fixes=observations.distinctBy { it.capturedAt }.sortedBy { it.capturedAt }.filter {
        crediblePresence(it,now,120_000)!=null
    }
    val fix=crediblePresence(fixes.lastOrNull(),now) ?: return fallback
    val previous=fixes.dropLast(1).lastOrNull { fix.capturedAt-it.capturedAt in 2_000..120_000 } ?: return fallback
    val currentProjection=projectOntoStops(stops,fix.location) ?: return fallback
    val beforeProjection=projectOntoStops(stops,previous.location) ?: return fallback
    val corridor=maxOf(150.0,fix.accuracyMeters*2.0)
    if (currentProjection.cross>corridor || beforeProjection.cross>corridor) return fallback
    val dt=(fix.capturedAt-previous.capturedAt)/1000.0
    val displacement=distanceMeters(previous.location,fix.location) ?: return fallback
    if (displacement/dt>110 || abs(currentProjection.along-beforeProjection.along)>110*dt+200) return fallback
    val radius=maxOf(65.0,fix.accuracyMeters*1.5)
    val nearby=stops.indices.filter { i ->
        (distanceMeters(stops[i].location,fix.location) ?: Double.MAX_VALUE)<=radius &&
            (distanceMeters(stops[i].location,previous.location) ?: Double.MAX_VALUE)<=maxOf(65.0,previous.accuracyMeters*1.5)
    }.minByOrNull { abs(it-currentProjection.edge) }
    val physical=(nearby ?: (currentProjection.edge+1)).coerceIn(0,stops.lastIndex.coerceAtLeast(0))
    val target=stops.getOrNull(physical) ?: return fallback
    val type=target.lineType
    val baseline=when(type) { LineType.WALK->1.3;LineType.BUS,LineType.TRAM->8.0;LineType.ICE->28.0;else->14.0 }
    val speedSamples=fixes.mapNotNull { it.speedMetersPerSecond?.toDouble()?.takeIf { s->s.isFinite() && s in 2.0..100.0 } }
    val measured=if(speedSamples.size>=3) speedSamples.sorted()[speedSamples.size/2] else (displacement/dt).takeIf { it>=2.0 }
    val speed=(measured ?: baseline).coerceIn(if(type==LineType.WALK) .8 else 4.0,
        when(type) {LineType.WALK->2.5;LineType.BUS,LineType.TRAM->22.0;else->90.0})
    val eta=(distanceMeters(fix.location,target.location) ?: return fallback)/speed
    val serverSeconds=((target.time-now)/1000.0).coerceAtLeast(0.0)
    val stillAtStop=nearby!=null && physical<scheduled
    val disagreement=eta-serverSeconds>=240.0
    if (!stillAtStop && !disagreement) return fallback
    return VisualJourneyProgress(minOf(scheduled,physical),true,now,ceil(eta/60).toInt(),
        (currentProjection.along/currentProjection.total.coerceAtLeast(1.0)).toFloat().coerceIn(0f,1f))
}
