// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model
import kotlin.math.*

fun walkingBearing(a:Location,b:Location):Double? {
    if(!a.hasCoordinates||!b.hasCoordinates||(distanceMeters(a,b)?:0.0)<.5)return null
    val p=a.latitude!!*PI/180;val q=b.latitude!!*PI/180;val d=(b.longitude!!-a.longitude!!)*PI/180
    return (atan2(sin(d)*cos(q),cos(p)*sin(q)-sin(p)*cos(q)*cos(d))*180/PI+360)%360
}
/** Correct contradictory handedness only when a sufficiently long route tangent confirms it. */
fun WalkingDirections.correctedTurns():WalkingDirections=copy(maneuvers=maneuvers.map{m->
    if(m.turn !in setOf(WalkingTurn.LEFT,WalkingTurn.RIGHT,WalkingTurn.SLIGHT_LEFT,WalkingTurn.SLIGHT_RIGHT))return@map m
    val pivot=shape.getOrNull(m.beginShapeIndex)?:return@map m
    val before=(m.beginShapeIndex-1 downTo 0).firstOrNull{(distanceMeters(shape[it],pivot)?:0.0)>=5.0}?.let{shape[it]}
    val after=(m.beginShapeIndex+1..m.endShapeIndex.coerceAtMost(shape.lastIndex)).firstOrNull{(distanceMeters(pivot,shape[it])?:0.0)>=5.0}?.let{shape[it]}
    val incoming=before?.let{walkingBearing(it,pivot)}?:return@map m
    val outgoing=after?.let{walkingBearing(pivot,it)}?:return@map m
    val delta=(outgoing-incoming+540)%360-180
    if(abs(delta)<30||abs(delta)>155)return@map m
    val left=delta<0;val statedLeft=m.turn in setOf(WalkingTurn.LEFT,WalkingTurn.SLIGHT_LEFT)
    if(left==statedLeft)m else m.copy(turn=if(left)WalkingTurn.LEFT else WalkingTurn.RIGHT)
})

fun walkingIncomingBearing(parts:List<WalkingMapPart>):Float? {
    val p=parts.firstOrNull{it.incoming}?.points?:return null
    val end=p.lastOrNull()?:return null
    val before=p.dropLast(1).lastOrNull{(distanceMeters(it,end)?:0.0)>=2.0}?:return null
    return walkingBearing(before,end)?.toFloat()
}
