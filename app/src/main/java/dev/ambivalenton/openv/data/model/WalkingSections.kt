// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import java.util.Locale

/** Human-scale sections retain every metre and every geometry point of the provider route. */
data class WalkingSection(val streetNames:List<String>,val turn:WalkingTurn,val meters:Double,val seconds:Double,
    val beginShapeIndex:Int,val endShapeIndex:Int,val instructions:List<String> = emptyList(),val facilities:Set<WalkingTurn> = emptySet())
private fun WalkingManeuver.namedStreets():List<String> = (streetNames.ifEmpty {beginStreetNames})
    .map {it.trim()}.filter {it.isNotBlank() && it.lowercase(Locale.ROOT) !in setOf("fußweg","fussweg","footway","footpath","path","walkway")}.distinct()
private fun WalkingTurn.isFacility()=this in setOf(WalkingTurn.STAIRS,WalkingTurn.ELEVATOR,WalkingTurn.ROUNDABOUT)
private fun WalkingTurn.isTurn()=this in setOf(WalkingTurn.LEFT,WalkingTurn.RIGHT,WalkingTurn.SLIGHT_LEFT,WalkingTurn.SLIGHT_RIGHT,WalkingTurn.UTURN)
internal fun WalkingDirections.roadSections():List<WalkingSection> {
    val raw=maneuvers.filterNot {it.turn==WalkingTurn.ARRIVE}
    val result=mutableListOf<WalkingSection>()
    fun append(m:WalkingManeuver) {
        val before=result.last()
        result[result.lastIndex]=before.copy(meters=before.meters+m.meters,seconds=before.seconds+m.seconds,
            endShapeIndex=maxOf(before.endShapeIndex,m.endShapeIndex))
    }
    var i=0
    while(i<raw.size) {
        val m=raw[i];val names=m.namedStreets();val previous=result.lastOrNull()
        if(names.isEmpty() && !m.turn.isFacility() && !(m.turn.isTurn() && (m.junctionExits?:0)>=2)) {
            var end=i;var runMeters=0.0
            while(end<raw.size && raw[end].namedStreets().isEmpty() && !raw[end].turn.isFacility() && !(raw[end].turn.isTurn() && (raw[end].junctionExits?:0)>=2)) {runMeters+=raw[end].meters;end++}
            // Short unnamed connectors belong to their preceding road; long unnamed paths are one
            // honest "Footpath" section, rather than a dozen two-metre left/right commands.
            if(previous!=null && !previous.turn.isFacility() && (previous.streetNames.isEmpty() || runMeters<30.0)) {
                while(i<end) {append(raw[i]);i++};continue
            }
            result+=WalkingSection(emptyList(),WalkingTurn.FORWARD,0.0,0.0,m.beginShapeIndex,m.beginShapeIndex)
            while(i<end) {append(raw[i]);i++};continue
        }
        val sameRoad=previous?.streetNames?.firstOrNull()?.lowercase(Locale.ROOT)==names.firstOrNull()?.lowercase(Locale.ROOT)
        if(previous!=null && sameRoad && names.isNotEmpty() && !m.turn.isFacility() && !previous.turn.isFacility()) append(m)
        else {
            val turn=when {
                m.turn.isFacility()->m.turn
                previous!=null && m.turn.isTurn() && (m.junctionExits==null || m.junctionExits>=2)->m.turn
                else->WalkingTurn.FORWARD
            }
            result+=WalkingSection(names,turn,m.meters,m.seconds,m.beginShapeIndex,m.endShapeIndex)
        }
        i++
    }
    return result
}

/** At least 250 m per map when the complete walk permits it. Road runs remain intact;
 * short junction mazes and the final short remainder join the preceding/next block. */
fun WalkingDirections.sections():List<WalkingSection> {
    val roads=roadSections()
    val blocks=mutableListOf<WalkingSection>()
    fun combine(a:WalkingSection,b:WalkingSection)=a.copy(streetNames=(a.streetNames+b.streetNames).distinct(),
        meters=a.meters+b.meters,seconds=a.seconds+b.seconds,endShapeIndex=maxOf(a.endShapeIndex,b.endShapeIndex),
        instructions=a.instructions+b.instructions,facilities=a.facilities+b.facilities)
    for((index,road)in roads.withIndex()) {
        var approach=0.0
        for(incoming in maneuvers.filter {it.turn!=WalkingTurn.ARRIVE && it.endShapeIndex<=road.beginShapeIndex}.asReversed()) {
            approach+=incoming.meters
            if(incoming.turn.isTurn() || incoming.turn.isFacility())break
        }
        val turn=if(approach>=100 && road.turn.isTurn())road.turn else WalkingTurn.FORWARD
        val current=road.copy(turn=turn,facilities=if(road.turn.isFacility())setOf(road.turn)else emptySet(),instructions=maneuvers.filter {it.beginShapeIndex>=road.beginShapeIndex && it.endShapeIndex<=road.endShapeIndex}.map {it.instruction}.filter {it.isNotBlank()})
        if(blocks.isNotEmpty() && blocks.last().meters<250)blocks[blocks.lastIndex]=combine(blocks.last(),current)
        else blocks+=current
    }
    if(blocks.size>1 && blocks.last().meters<250) {
        val tail=blocks.removeAt(blocks.lastIndex)
        blocks[blocks.lastIndex]=combine(blocks.last(),tail)
    }
    if(blocks.isNotEmpty()) {
        blocks[0]=blocks.first().copy(beginShapeIndex=0)
        val extra=(meters-blocks.sumOf {it.meters}).coerceAtLeast(0.0)
        blocks[blocks.lastIndex]=blocks.last().copy(endShapeIndex=shape.lastIndex.coerceAtLeast(0),meters=blocks.last().meters+extra)
    }
    return blocks
}

data class WalkingMapPart(val points:List<Location>,val incoming:Boolean=false)
/** Section removal uses measured route distance; no speed/time forecast can pass a street. */
fun passedWalkingSections(route:WalkingDirections,presence:DevicePresence?,now:Long):Set<Int> {
    val fix=currentJourneyGps(presence,now) ?: return emptySet()
    val points=route.shape.mapIndexed {i,p->JourneyFocusStop("$i",p,0,0,"",null)}
    val projection=projectOntoStops(points,fix.location) ?: return emptySet()
    if(projection.cross>maxOf(70.0,fix.accuracyMeters*2.0)) return emptySet()
    var distance=0.0
    val distances=route.shape.mapIndexed {i,p->if(i>0) distance+=distanceMeters(route.shape[i-1],p) ?: 0.0;distance}
    return route.sections().mapIndexedNotNull {i,s->if(s.endShapeIndex in distances.indices &&
        projection.along>distances[s.endShapeIndex]+maxOf(25.0,fix.accuracyMeters*1.5)) i else null}.toSet()
}
fun WalkingDirections.mapParts(sections:List<WalkingSection>,index:Int):List<WalkingMapPart> {
    if(shape.size<2 || index !in sections.indices) return emptyList()
    fun points(s:WalkingSection):List<Location> {
        val begin=s.beginShapeIndex.coerceIn(0,shape.lastIndex)
        val end=s.endShapeIndex.coerceIn(begin,shape.lastIndex)
        return shape.subList(begin,end+1)
    }
    return buildList {
        val current=sections[index]
        if(current.turn!=WalkingTurn.FORWARD && index>0) {
            val previous=points(sections[index-1]);var meters=0.0;var first=previous.lastIndex
            while(first>0 && meters<100) {meters+=distanceMeters(previous[first-1],previous[first]) ?: 0.0;first--}
            add(WalkingMapPart(previous.drop(first),true))
        }
        add(WalkingMapPart(points(current)))
    }
}
