// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model
import kotlin.math.*

data class MapPoint(val x:Double,val y:Double)
data class MapTileId(val z:Int,val x:Int,val y:Int)
data class WalkingMapViewport(val x:Double,val y:Double,val zoom:Int)
fun mercatorPoint(location:Location):MapPoint? {
    if(!location.hasCoordinates) return null
    val latitude=location.latitude!!.coerceIn(-85.05112878,85.05112878)*PI/180
    return MapPoint((location.longitude!!+180)/360,(1-ln(tan(latitude)+1/cos(latitude))/PI)/2)
}
fun fitWalkingMap(parts:List<WalkingMapPart>,width:Int,height:Int):WalkingMapViewport {
    val points=parts.flatMap {it.points}.mapNotNull(::mercatorPoint)
    if(points.isEmpty()) return WalkingMapViewport(.5,.5,3)
    val minX=points.minOf {it.x};val maxX=points.maxOf {it.x};val minY=points.minOf {it.y};val maxY=points.maxOf {it.y}
    val zx=ln((width.coerceAtLeast(1)*.65)/((maxX-minX).coerceAtLeast(1e-9)*256))/ln(2.0)
    val zy=ln((height.coerceAtLeast(1)*.65)/((maxY-minY).coerceAtLeast(1e-9)*256))/ln(2.0)
    return WalkingMapViewport((minX+maxX)/2,(minY+maxY)/2,floor(min(zx,zy)).toInt().coerceIn(3,18))
}
fun WalkingMapViewport.visibleTiles(width:Int,height:Int):List<MapTileId> {
    val count=1 shl zoom.coerceIn(0,19);val left=x*count-width/512.0;val top=y*count-height/512.0
    val right=x*count+width/512.0;val bottom=y*count+height/512.0
    return buildList {
        for(tx in floor(left).toInt()..floor(right).toInt()) for(ty in floor(top).toInt()..floor(bottom).toInt()) {
            if(ty in 0 until count) add(MapTileId(zoom,((tx%count)+count)%count,ty))
            if(size>=36) return@buildList
        }
    }.distinct()
}
