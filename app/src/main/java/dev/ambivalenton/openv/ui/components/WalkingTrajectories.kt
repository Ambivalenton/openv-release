// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import kotlin.math.*
import kotlin.random.Random

/** Avalanche the key so similar column IDs do not share predictable first-foot parity. */
fun walkingRouteSeed(base:Long,key:String):Long {
    var value=base-7046029254386353131L
    key.forEach {value=(value xor it.code.toLong())*1099511628211L}
    value=(value xor (value ushr 30))*-4658895280553007687L
    value=(value xor (value ushr 27))*-7723592293110705685L
    return value xor (value ushr 31)
}

/** The route is sampled by distance, so curves retain a believable walking stride. */
data class WalkingRoutePoint(val x:Float,val y:Float,val heading:Float,val distance:Float)
data class WalkingTrajectory(val points:List<WalkingRoutePoint>,val kind:Int,val scale:Float) {
    val length get()=points.lastOrNull()?.distance ?: 0f
    fun at(distance:Float):WalkingRoutePoint {
        if(points.size==1) return points.first()
        val target=distance.coerceIn(0f,length)
        var low=0;var high=points.lastIndex
        while(low<high) {val middle=(low+high)/2;if(points[middle].distance<target) low=middle+1 else high=middle}
        if(low==0) return points.first()
        val a=points[low-1];val b=points[low];val t=(target-a.distance)/(b.distance-a.distance).coerceAtLeast(.0001f)
        return WalkingRoutePoint(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.heading+(b.heading-a.heading)*t,target)
    }
}

/** Independent straight, drift, slalom, wandering and rounded-zigzag families; never a fixed S. */
fun walkingTrajectory(width:Float,height:Float,seed:Long=0L,sizeScale:Float=1f,straight:Boolean=false):WalkingTrajectory? {
    if(!width.isFinite() || !height.isFinite() || width<=0f || height<=0f || !sizeScale.isFinite() || sizeScale<=0f) return null
    val radius=hypot(FOOTPRINT_LENGTH_PX/2,FOOTPRINT_WIDTH_PX/2)
    val inset=radius+FOOTPRINT_FOOT_OFFSET_PX
    val scale=min(sizeScale,min(width/(inset*2),height/(inset*2)))
    val safe=min(inset*scale,min(width,height)/2)
    val top=safe;val bottom=(height-safe).coerceAtLeast(top)
    val available=(width-2*safe).coerceAtLeast(0f)
    val random=Random(seed);val kind=if(straight) 0 else random.nextInt(5)
    val maxSections=floor((bottom-top)/(FOOTPRINT_STRIDE_PX*scale*2.8f)).toInt().coerceIn(1,7)
    val sections=if(kind<=1) 1 else random.nextInt(1,maxSections+1)
    val xs=FloatArray(sections+1);val ys=FloatArray(sections+1)
    val direction=if(random.nextBoolean()) 1 else -1
    val narrowCenter=safe+available*(.15f+.7f*random.nextFloat())
    for(i in 0..sections) {
        val fraction=i.toFloat()/sections
        ys[i]=top+(bottom-top)*(if(i==0 || i==sections) fraction else fraction+(random.nextFloat()-.5f)*.18f/sections)
        val target=when {
            straight->width/2
            kind==0->narrowCenter+(random.nextFloat()-.5f)*available*.18f
            kind==1->safe+available*(if((i==0)==(direction==1)) random.nextFloat()*.12f else .88f+random.nextFloat()*.12f)
            kind==2->safe+available*(if((i+direction)%2==0) .04f+random.nextFloat()*.25f else .71f+random.nextFloat()*.25f)
            kind==3->safe+available*random.nextFloat()
            else->safe+available*(if((i+direction)%2==0) random.nextFloat()*.1f else .9f+random.nextFloat()*.1f)
        }
        // Quintic X has maximum derivative 1.875; this bounds the turn to ~38 degrees.
        val reach=if(i==0) width else (ys[i]-ys[i-1])*.42f
        xs[i]=if(i==0) target.coerceIn(safe,width-safe) else target.coerceIn(max(safe,xs[i-1]-reach),min(width-safe,xs[i-1]+reach))
    }
    val count=ceil((bottom-top)/3f).toInt().coerceIn(24,2048)
    val points=ArrayList<WalkingRoutePoint>(count+1);var section=0;var distance=0f
    for(i in 0..count) {
        val y=top+(bottom-top)*i/count
        while(section<sections-1 && y>ys[section+1]) section++
        val span=(ys[section+1]-ys[section]).coerceAtLeast(.001f)
        val t=((y-ys[section])/span).coerceIn(0f,1f)
        val ease=t*t*t*(t*(t*6-15)+10)
        val dx=xs[section+1]-xs[section]
        val x=xs[section]+dx*ease
        val slope=dx/span*30*t*t*(1-t)*(1-t)
        points.lastOrNull()?.let {distance+=hypot(x-it.x,y-it.y)}
        points+=WalkingRoutePoint(x,y,atan(slope),distance)
    }
    return WalkingTrajectory(points,kind,scale)
}

fun walkingFootsteps(width:Float,height:Float,seed:Long=0L,sizeScale:Float=1f,straight:Boolean=false,strideFactor:Float=1f):List<FootstepGeometry> {
    val route=walkingTrajectory(width,height,seed,sizeScale,straight) ?: return emptyList()
    val count=(floor(route.length/(FOOTPRINT_STRIDE_PX*route.scale*strideFactor.coerceIn(.6f,1.4f))).toInt()+1).coerceIn(1,1024)
    return List(count) {index ->
        val point=route.at(if(count==1) route.length/2 else route.length*index/(count-1))
        val left=(index+if((seed and 1L)==0L) 0 else 1)%2==0
        val side=if(left) -1f else 1f
        val offset=FOOTPRINT_FOOT_OFFSET_PX*route.scale*side
        val toeOut=if(straight) side*.035f else side*(.025f+Random(seed+index).nextFloat()*.035f)
        FootstepGeometry(point.x+cos(point.heading)*offset,point.y-sin(point.heading)*offset,
            point.heading+toeOut,route.scale,left)
    }
}
