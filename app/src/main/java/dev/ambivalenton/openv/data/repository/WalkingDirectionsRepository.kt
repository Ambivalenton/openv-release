// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

fun interface WalkingDirectionsSource {
    suspend fun directions(segment:TripSegment,speed:WalkingSpeed,endpoint:String,language:String):WalkingDirections
}

/** One application-scoped pedestrian client. Cache keys exclude live timestamps and device fixes.
 * Uncached requests are serialized, bounded, identified and separated by at least one second.
 * Coordinates remain in memory; only the two walking endpoints go to the configured provider. */
class WalkingDirectionsRepository(private val network:NetworkClient,
    private val now:()->Long=System::currentTimeMillis,
    private val minimumIntervalMillis:Long=1_000,
    private val monotonicNow:()->Long={System.nanoTime()/1_000_000},
) : WalkingDirectionsSource {
    private data class Entry(val stored:Long,val route:WalkingDirections)
    private val cache=object:LinkedHashMap<String,Entry>(32,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Entry>)=size>32
    }
    private val mutex=Mutex()
    private var lastStart:Long?=null
    override suspend fun directions(segment:TripSegment,speed:WalkingSpeed,endpoint:String,language:String):WalkingDirections {
        require(segment.isWalk && segment.origin.hasCoordinates && segment.destination.hasCoordinates) {
            tr("Für diesen Fußweg fehlen genaue Koordinaten.")
        }
        val base=httpsBase(endpoint)
        val payload=walkingRouteRequest(segment,speed,language).toString()
        val key=base.toString()+payload
        return mutex.withLock {
            val instant=now()
            cache[key]?.takeIf {instant-it.stored in 0..86_400_000L}?.let {return@withLock it.route}
            lastStart?.let {delay((minimumIntervalMillis-(monotonicNow()-it)).coerceAtLeast(0))}
            lastStart=monotonicNow()
            val response=withTimeout(12_000) {
                network.get(base.newBuilder().addPathSegment("route").addQueryParameter("json",payload).build(),
                    mapOf("X-Client-Id" to "dev.ambivalenton.openv","Cache-Control" to "no-store"))
            }
            val route=parseWalkingDirections(response).correctedTurns()
            cache[key]=Entry(now(),route)
            route
        }
    }
}

internal fun walkingRouteRequest(segment:TripSegment,speed:WalkingSpeed,language:String)=buildJsonObject {
    putJsonArray("locations") {
        listOf(segment.origin,segment.destination).forEach {place->add(buildJsonObject {
            put("lat",place.latitude);put("lon",place.longitude);put("type","break")
            if(place.kind==LocationKind.STOP){put("minimum_reachability",1);put("radius",40)}
        })}
    }
    put("costing","pedestrian");put("units","kilometers");put("language",language)
    put("directions_type","instructions");put("format","osrm")
    putJsonObject("costing_options") {putJsonObject("pedestrian") {
        put("type","foot");put("walking_speed",speed.metersPerSecond*3.6)
        // Do not trade a station stairway for a long detour around the building.
        put("step_penalty",0);put("elevator_penalty",0);put("walkway_factor",.8)
        if(segment.origin.kind==LocationKind.STOP&&segment.destination.kind==LocationKind.STOP&&
            (distanceMeters(segment.origin,segment.destination)?:Double.MAX_VALUE)<1000)put("shortest",true)
    }}
}

internal fun walkingTurn(type:Int)=when(type) {
    4,5,6->WalkingTurn.ARRIVE
    9,18,23,37->WalkingTurn.SLIGHT_RIGHT
    10,11,20->WalkingTurn.RIGHT
    12,13->WalkingTurn.UTURN
    14,15,21->WalkingTurn.LEFT
    16,19,24,38->WalkingTurn.SLIGHT_LEFT
    26,27->WalkingTurn.ROUNDABOUT
    39->WalkingTurn.ELEVATOR
    40,41->WalkingTurn.STAIRS
    else->WalkingTurn.FORWARD
}

/** Reject corrupt geometry/indices instead of inventing directions from timetable stop names. */
internal fun parseWalkingDirections(json:JsonElement):WalkingDirections {
    fun fail():Nothing=throw TransitException(tr("Für diesen Fußweg sind keine verlässlichen Geh-Anweisungen verfügbar."))
    if(json.field("routes")!=null) return parseOsrmWalkingDirections(json)
    val trip=json.field("trip") ?: fail()
    if(trip.number("status")!=0 || trip.text("units")!="kilometers") fail()
    val leg=trip.field("legs").elements().singleOrNull() ?: fail()
    val shape=decodeWalkingPolyline(leg.text("shape") ?: fail())
    fun JsonElement.amount(name:String,max:Double):Double = text(name)?.toDoubleOrNull()
        ?.takeIf {it.isFinite() && it in 0.0..max} ?: fail()
    val raw=leg.field("maneuvers").elements()
    if(raw.isEmpty() || raw.size>256) fail()
    var previous=0
    val steps=raw.map {m->
        if(m.text("travel_mode")?.lowercase()?.let{it !in setOf("pedestrian","walking","foot")}==true)fail()
        val begin=m.number("begin_shape_index") ?: fail();val end=m.number("end_shape_index") ?: fail()
        if(begin<previous || begin !in shape.indices || end !in begin..shape.lastIndex) fail()
        previous=begin
        val instruction=m.text("instruction")?.takeIf {it.length<=2_000} ?: fail()
        WalkingManeuver(instruction,walkingTurn(m.number("type") ?: 0),m.amount("length",1000.0)*1000,
            m.amount("time",604_800.0),begin,end,
            m.field("street_names").elements().mapNotNull {it.text()?.takeIf {name->name.isNotBlank() && name.length<=256}}.take(8),
            m.field("begin_street_names").elements().mapNotNull {it.text()?.takeIf {name->name.isNotBlank() && name.length<=256}}.take(8))
    }
    val summary=leg.field("summary") ?: fail()
    return WalkingDirections(steps,shape,summary.amount("length",1000.0)*1000,summary.amount("time",604_800.0))
}

/** Valhalla OSRM output includes the actual intersection exits. One request provides both
 * named street sections and graph junctions; no extra geocoding or intersection lookup. */
internal fun parseOsrmWalkingDirections(json:JsonElement):WalkingDirections {
    fun fail():Nothing=throw TransitException(tr("Für diesen Fußweg sind keine verlässlichen Geh-Anweisungen verfügbar."))
    fun JsonElement.amount(key:String,max:Double):Double=text(key)?.toDoubleOrNull()
        ?.takeIf {it.isFinite() && it in 0.0..max} ?: fail()
    if(json.text("code")!="Ok") fail()
    val route=json.field("routes").elements().firstOrNull() ?: fail()
    val leg=route.field("legs").elements().singleOrNull() ?: fail()
    val shape=decodeWalkingPolyline(route.text("geometry") ?: fail())
    val steps=leg.field("steps").elements()
    if(steps.isEmpty() || steps.size>256) fail()
    val intersections=steps.map {it.field("intersections").elements().firstOrNull() ?: fail()}
    val starts=intersections.map {it.number("geometry_index")?.takeIf {i->i in shape.indices} ?: fail()}
    if(starts.first()!=0 || starts.zipWithNext().any {(a,b)->b<a}) fail()
    val maneuvers=steps.mapIndexed {index,step->
        if(step.text("mode")?.lowercase()?.let{it !in setOf("pedestrian","walking","foot")}==true)fail()
        val maneuver=step.field("maneuver") ?: fail()
        val instruction=maneuver.text("instruction")?.takeIf {it.isNotBlank() && it.length<=2_000} ?: fail()
        val type=maneuver.text("type") ?: fail()
        val modifier=maneuver.text("modifier")
        val junction=intersections[index]
        val bearings=junction.field("bearings").elements()
        val entries=junction.field("entry").elements().map {it.jsonPrimitive.booleanOrNull ?: fail()}
        if(bearings.size!=entries.size || entries.isEmpty() || entries.size>32) fail()
        val incoming=junction.number("in")
        if(incoming!=null && incoming !in entries.indices) fail()
        val outgoing=junction.number("out")
        if(type!="arrive" && (outgoing==null || outgoing !in entries.indices || !entries[outgoing])) fail()
        val exits=entries.indices.count {it!=incoming && entries[it]}
        // The OSRM dialect has no dedicated stairs/elevator type in older Valhalla releases.
        // Keep their explicit provider narration in the two supported app languages.
        val lower=instruction.lowercase(java.util.Locale.ROOT)
        val turn=when {
            type=="arrive"->WalkingTurn.ARRIVE
            type in setOf("roundabout","rotary","exit roundabout","exit rotary")->WalkingTurn.ROUNDABOUT
            type=="stairs" || Regex("\\b(treppe[n]?|stairs|steps)\\b").containsMatchIn(lower)->WalkingTurn.STAIRS
            type=="elevator" || Regex("\\b(aufzug|fahrstuhl|elevator|lift)\\b").containsMatchIn(lower)->WalkingTurn.ELEVATOR
            type in setOf("depart","new name")->WalkingTurn.FORWARD
            else->when(modifier) {"right","sharp right"->WalkingTurn.RIGHT;"left","sharp left"->WalkingTurn.LEFT
                "slight right"->WalkingTurn.SLIGHT_RIGHT;"slight left"->WalkingTurn.SLIGHT_LEFT
                "uturn"->WalkingTurn.UTURN;else->WalkingTurn.FORWARD}
        }
        val names=step.text("name")?.takeIf {it.length<=2_000}?.split(';')
            ?.map {it.trim()}?.filter {it.isNotEmpty() && it.length<=256}?.take(8) ?: emptyList()
        WalkingManeuver(instruction,turn,step.amount("distance",1_000_000.0),step.amount("duration",604_800.0),
            starts[index],starts.getOrElse(index+1) {shape.lastIndex},names,junctionExits=exits)
    }
    return WalkingDirections(maneuvers,shape,leg.amount("distance",1_000_000.0),leg.amount("duration",604_800.0))
}

/** Valhalla's native JSON uses polyline6, not Google's common polyline5. */
internal fun decodeWalkingPolyline(encoded:String):List<Location> {
    require(encoded.length<=200_000) {"Route geometry too large"}
    var offset=0;var latitude=0L;var longitude=0L
    fun delta():Long {
        var value=0L;var shift=0
        do {
            require(offset<encoded.length && shift<=35) {"Invalid route geometry"}
            val next=encoded[offset++].code-63
            require(next in 0..63) {"Invalid route geometry"}
            value=value or ((next and 31).toLong() shl shift);shift+=5
        } while(next>=32)
        return if(value and 1L!=0L) (value ushr 1).inv() else value ushr 1
    }
    return buildList {
        while(offset<encoded.length) {
            latitude+=delta();longitude+=delta()
            val point=Location("",latitude=latitude/1e6,longitude=longitude/1e6)
            require(point.hasCoordinates && size<20_000) {"Invalid route coordinates"}
            add(point)
        }
        require(size>=2) {"Missing route geometry"}
    }
}
