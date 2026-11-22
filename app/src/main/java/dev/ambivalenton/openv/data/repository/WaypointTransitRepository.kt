// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Uses real, directed, time-dependent transit edges. Never fabricates a ride between two points. */
class WaypointTransitRepository(private val delegate:TransitRepository):TransitRepository by delegate {
    @kotlinx.serialization.Serializable
    private data class Cursor(val anchor:Long,val native:String?=null,val key:String)
    private class MissingConnection(message:String):Exception(message)
    private fun cursorKey(q:JourneyQuery)=MessageDigest.getInstance("SHA-256").digest(
        (listOf(waypointIdentity(q.origin),waypointIdentity(q.destination),q.timeMode.name)+
            q.waypoints.map(::waypointIdentity)+listOf(q.walkingSpeed.name,q.ignoreWalking.toString(),q.transportFilter?.sortedBy {it.name}.toString())+q.products.map {it.name}.sorted()+
            q.waypointDwellMinutes.toSortedMap().map {"${it.key}:${normalizedWaypointStay(it.value)}"}+
            // M13: only the manual order adds to the key, so earlier cursors stay valid.
            (if(q.waypointOrder==WaypointOrder.MANUAL) listOf("order:manual") else emptyList())).joinToString("|").toByteArray())
        .take(12).joinToString("") {"%02x".format(it)}
    private fun encode(cursor:Cursor)="waypoint-v1:"+java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(StateSerializer.json.encodeToString(cursor).toByteArray())
    private fun decode(value:String,key:String):Cursor {
        try {
            require(value.length<=65_536 && value.startsWith("waypoint-v1:"))
            return StateSerializer.json.decodeFromString<Cursor>(String(java.util.Base64.getUrlDecoder().decode(value.removePrefix("waypoint-v1:"))))
                .also {require(it.key==key)}
        } catch(e:IllegalArgumentException) {throw TransitException(tr("Die gespeicherte Routenseite passt nicht mehr zur Suche. Bitte neu laden."))}
    }
    private suspend fun request(query:JourneyQuery,cursor:String?=null):JourneyPage = try {
        withTimeout(20_000) {delegate.journeyPage(query,cursor)}
    } catch(e:TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw TransitException(tr("Die Berechnung eines Abschnitts dauert zu lange. Bitte erneut versuchen."))
    }

    override suspend fun firstJourney(query:JourneyQuery):Trip? =
        if(query.waypoints.isEmpty()) delegate.firstJourney(query) else plannedPage(query,null,true).trips.firstOrNull()

    override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage =
        if(query.waypoints.isEmpty()) delegate.journeyPage(query,cursor) else plannedPage(query,cursor,false)

    /** Page the first/last transit edge natively, then resolve its feasible onward chains. */
    private suspend fun plannedPage(query:JourneyQuery,cursor:String?,firstOnly:Boolean):JourneyPage {
        val key=cursorKey(query)
        val saved=cursor?.let {decode(it,key)}
        val coordinates=mutableMapOf<String,Location>()
        // One stop already has an unambiguous order. Provider-native stop IDs can be
        // routed without coordinates; geometry is required only to order several stops.
        // M13: "Meine Reihenfolge verwenden" keeps the entered order, including a deliberate detour.
        val manualOrder=query.waypointOrder==WaypointOrder.MANUAL
        val needsOrdering=!manualOrder && distinctWaypoints(query.waypoints).size>1
        suspend fun located(place:Location):Location {
            if(place.hasCoordinates) return place
            if(!needsOrdering&&place.routable)return place
            coordinates[waypointIdentity(place)]?.let {return it}
            val exact=withTimeoutOrNull(20_000) {delegate.locations(place.name)}?.firstOrNull {
                it.hasCoordinates && !place.id.isNullOrBlank() && it.id==place.id && it.provider==place.provider && it.kind==place.kind
            } ?: throw TransitException(tr("Für die Stopp-Reihenfolge werden Orte mit Koordinaten benötigt."))
            return place.copy(latitude=exact.latitude,longitude=exact.longitude).also {coordinates[waypointIdentity(place)]=it}
        }
        val origin=located(query.origin);val destination=located(query.destination)
        val requested=query.waypoints.map {located(it)}
        val dwell=requested.mapIndexed {i,p->waypointIdentity(p) to normalizedWaypointStay(query.waypointDwellMinutes[waypointIdentity(query.waypoints[i])] ?: 0f)}
            .toMap().filterValues {it>0f}
        val order=if(manualOrder) distinctWaypoints(requested) else withContext(Dispatchers.Default) {
            val context=currentCoroutineContext()
            if(needsOrdering||(listOf(origin,destination)+requested).all{it.hasCoordinates})WaypointOrderOptimizer().order(origin,destination,requested) {context.ensureActive()}else distinctWaypoints(requested)
        }
        val points=listOf(origin)+order+destination
        val forward=query.timeMode==TimeMode.DEPARTURE
        val indices=if(forward) (0 until points.lastIndex).toList() else (0 until points.lastIndex).reversed().toList()
        val first=indices.first()
        val rootQuery=query.copy(origin=points[first],destination=points[first+1],time=saved?.anchor ?: query.time,
            waypoints=emptyList(),waypointDwellMinutes=emptyMap())
        val page=request(rootQuery,saved?.native)
        if(page.trips.isNotEmpty() && page.trips.none {validWaypointLeg(it,rootQuery) && it.matchesTransportFilter(query.transportFilter)})
            throw TransitException(tr("Keine passende Verbindung zwischen %1\$s und %2\$s gefunden.",rootQuery.origin.name,rootQuery.destination.name))
        val candidates=page.trips.filter {validWaypointLeg(it,rootQuery) && it.matchesTransportFilter(query.transportFilter) && if(forward) it.startTime>=rootQuery.time else it.endTime<=rootQuery.time}
            .distinctBy {it.id}.let {if(forward) it.sortedBy {t->t.endTime} else it.sortedByDescending {t->t.startTime}}
        val routes=mutableListOf<Trip>();var missing:String?=null
        for(seed in candidates) {
            val parts=mutableListOf(rootQuery to seed)
            var anchor=if(forward) seed.endTime+waypointStayMillis(points[first+1],dwell)
                else seed.startTime-waypointStayMillis(points[first],dwell)
            try {
                for(index in indices.drop(1)) {
                    currentCoroutineContext().ensureActive();delay(250)
                    val legQuery=query.copy(origin=points[index],destination=points[index+1],time=anchor,
                        waypoints=emptyList(),waypointDwellMinutes=emptyMap())
                    val options=request(legQuery).shown.filter {validWaypointLeg(it,legQuery) && it.matchesTransportFilter(query.transportFilter) && if(forward) it.startTime>=anchor else it.endTime<=anchor}
                    val chosen=if(forward) options.minByOrNull {it.endTime} else options.maxByOrNull {it.startTime}
                    if(chosen==null) throw MissingConnection(tr("Keine passende Verbindung zwischen %1\$s und %2\$s gefunden.",points[index].name,points[index+1].name))
                    parts+=legQuery to chosen
                    anchor=if(forward) chosen.endTime+waypointStayMillis(points[index+1],dwell)
                        else chosen.startTime-waypointStayMillis(points[index],dwell)
                }
                routes+=combineWaypointLegs(if(forward) parts else parts.reversed(),order,dwellMinutes=dwell)
                if(firstOnly) return JourneyPage(routes)
            } catch(e:MissingConnection) {missing=e.message}
        }
        if(firstOnly && routes.isEmpty()) throw TransitException(missing ?: tr("Die Verbindung konnte nicht neu berechnet werden. Deine bisherige Fahrt bleibt erhalten."))
        val next=if(!page.nextCursor.isNullOrBlank()) encode(Cursor(rootQuery.time,page.nextCursor,key)) else {
            val edge=if(forward) page.trips.maxOfOrNull {it.startTime} else page.trips.minOfOrNull {it.endTime}
            edge?.let {
                val after=if(forward) Math.floorDiv(it,60_000)*60_000+60_000 else Math.floorDiv(it,60_000)*60_000-1
                if(if(forward) after>rootQuery.time else after<rootQuery.time) encode(Cursor(after,key=key)) else null
            }
        }
        return JourneyPage(routes.sortedBy {it.startTime},next)
    }

    override suspend fun refresh(trip:Trip):Trip {
        if(trip.waypointLegs.isEmpty()) return delegate.refresh(trip)
        val parts=mutableListOf<Pair<JourneyQuery,Trip>>();var offset=0
        for((legIndex,ref) in trip.waypointLegs.withIndex()) {
            currentCoroutineContext().ensureActive()
            require(ref.segmentCount>0 && offset+ref.segmentCount<=trip.segments.size)
            val oldSegments=trip.segments.subList(offset,offset+ref.segmentCount).map {it.copy(id=it.id.removePrefix("via-$legIndex-"))}
            val previous=Trip(ref.tripId,oldSegments,trip.source,
                trip.fetchedAt,ref.refreshToken)
            offset+=ref.segmentCount
            val next=if(!ref.refreshToken.isNullOrBlank()) delegate.refresh(previous)
                else delegate.journeyPage(ref.query.copy(time=if(ref.query.timeMode==TimeMode.DEPARTURE)
                    previous.segments.minOf {it.plannedStartTime} else previous.segments.maxOf {it.plannedEndTime}))
                    .trips.firstOrNull {samePinnedJourney(previous,it)}
                    ?: throw TransitException(tr("Ein Abschnitt der Verbindung lässt sich gerade nicht eindeutig aktualisieren."))
            require(samePinnedJourney(previous,next))
            parts+=ref.query to next
        }
        require(offset==trip.segments.size)
        // Keep a delayed selected service chain, even when its transfer becomes unreachable.
        // The existing pin controller detects that disruption and offers a separate alternative.
        return combineWaypointLegs(parts,trip.waypoints,allowMissedTransfer=true,dwellMinutes=trip.waypointDwellMinutes)
    }

    override fun journeyWindow(query:JourneyQuery,policy:PagingPolicy):Flow<JourneyWindow> =
        if(query.waypoints.isEmpty()) delegate.journeyWindow(query,policy) else fetchJourneyWindow(this,query,policy)

    override suspend fun journeys(query:JourneyQuery):List<Trip> = journeyWindow(query).last().let {
        if(!it.complete) throw TransitException(it.problem ?: tr("Der Sechs-Stunden-Zeitraum ist noch nicht vollständig. Bitte erneut laden."))
        it.trips
    }

}

internal fun validWaypointLeg(trip:Trip,query:JourneyQuery):Boolean {
    if(trip.segments.isEmpty() || trip.cancelled) return false
    if(trip.segments.any {it.endTime<it.startTime || (!it.isWalk &&
        (it.lineType !in query.products || (LineType.ICE !in query.products && it.isLongDistance())))}) return false
    if(trip.segments.zipWithNext().any {(a,b)->a.endTime>b.startTime}) return false
    fun reaches(actual:Location,wanted:Location):Boolean {
        if(sameLocation(actual,wanted)) return true
        val tolerance=if(wanted.kind==LocationKind.STOP) 250.0 else 80.0
        return dev.ambivalenton.openv.data.model.distanceMeters(actual,wanted)?.let {it<=tolerance}==true
    }
    return reaches(trip.segments.first().origin,query.origin) && reaches(trip.segments.last().destination,query.destination)
}

internal fun combineWaypointLegs(parts:List<Pair<JourneyQuery,Trip>>,waypoints:List<Location>,
    allowMissedTransfer:Boolean=false,dwellMinutes:Map<String,Float> = emptyMap()):Trip {
    require(parts.isNotEmpty() && parts.all {it.second.segments.isNotEmpty()})
    require(parts.size==waypoints.size+1)
    if(!allowMissedTransfer) require(parts.zipWithNext().withIndex().all {(index,pair)->
        pair.first.second.endTime+waypointStayMillis(waypoints[index],dwellMinutes)<=pair.second.second.startTime}) {
        tr("Die Abschnitte passen zeitlich nicht zusammen. Die bisherige Verbindung bleibt erhalten.")
    }
    val segments=parts.flatMapIndexed {index,(_,trip)->trip.segments.map {it.copy(id="via-$index-${it.id}")}}
    val identity=parts.joinToString("|") {it.second.id}+"|"+waypoints.joinToString("|") {waypointIdentity(it)}
    val hash=MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).take(12).joinToString("") {"%02x".format(it)}
    val visits=waypoints.mapIndexed {index,place->WaypointVisit(place,parts[index].second.endTime,
        parts[index+1].second.startTime,waypointStayMillis(place,dwellMinutes))}
    return Trip("via-$hash",segments,parts.map {it.second.source}.distinct().joinToString(" / "),
        parts.minOf {it.second.fetchedAt},notices=parts.flatMap {it.second.notices}.distinct(),waypoints=waypoints,
        waypointVisits=visits,waypointLegs=parts.map {(query,trip)->WaypointLegReference(query.copy(waypoints=emptyList()),
            trip.id,trip.refreshToken,trip.segments.size)},waypointDwellMinutes=dwellMinutes)
}
