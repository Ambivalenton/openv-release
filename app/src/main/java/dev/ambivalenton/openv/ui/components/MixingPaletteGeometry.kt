// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.*
import kotlin.random.Random

internal data class PaletteSpot(val x:Float,val y:Float,val radius:Float)
internal data class MixingPalettePlacement(val bounds:Rect,val anchor:Offset,val wide:Boolean)
internal fun mixingPaletteAspect(count:Int,wide:Boolean=false)=if(wide){if(count<=4).70f else .60f}else when{count>=7->1.45f;count>=6->1.30f;count>=4->1.25f;else->1.15f}
internal fun mixingPaletteHole(count:Int,wide:Boolean=false)=Offset(if(wide).93f else .90f,if(wide).86f else .91f)

/** Bounded, stable scatter. The last place is the reset colour well, kept near the grip. */
internal fun mixingPaletteSpots(count:Int,seed:Int,wide:Boolean=false):List<PaletteSpot>{
    require(count in 1..10)
    val points=if(wide&&count>=7){
        listOf(PaletteSpot(.15f,.30f,.073f),PaletteSpot(.33f,.21f,.073f),PaletteSpot(.51f,.30f,.073f),
            PaletteSpot(.69f,.20f,.073f),PaletteSpot(.87f,.37f,.073f),PaletteSpot(.12f,.67f,.073f),
            PaletteSpot(.30f,.77f,.073f),PaletteSpot(.48f,.61f,.073f),PaletteSpot(.66f,.76f,.073f),PaletteSpot(.84f,.65f,.073f)).take(count)
    }else if(wide){
        when(count){
            1->listOf(PaletteSpot(.45f,.44f,.22f))
            2->listOf(PaletteSpot(.29f,.40f,.19f),PaletteSpot(.69f,.45f,.19f))
            3->listOf(PaletteSpot(.20f,.35f,.14f),PaletteSpot(.49f,.55f,.14f),PaletteSpot(.78f,.34f,.14f))
            4->listOf(PaletteSpot(.18f,.35f,.12f),PaletteSpot(.43f,.29f,.12f),PaletteSpot(.69f,.33f,.12f),PaletteSpot(.55f,.73f,.12f))
            else->listOf(PaletteSpot(.16f,.30f,.095f),PaletteSpot(.39f,.25f,.095f),PaletteSpot(.63f,.31f,.095f),PaletteSpot(.84f,.30f,.095f),PaletteSpot(.25f,.73f,.095f),PaletteSpot(.59f,.73f,.095f)).take(count)
        }
    }else when(count){
        1->listOf(PaletteSpot(.46f,.45f,.32f))
        2->listOf(PaletteSpot(.35f,.28f,.25f),PaletteSpot(.58f,.70f,.25f))
        3->listOf(PaletteSpot(.34f,.23f,.22f),PaletteSpot(.73f,.46f,.205f),PaletteSpot(.38f,.76f,.23f))
        4->listOf(PaletteSpot(.32f,.23f,.20f),PaletteSpot(.73f,.39f,.19f),PaletteSpot(.26f,.65f,.195f),PaletteSpot(.66f,.80f,.18f))
        5->listOf(PaletteSpot(.32f,.19f,.175f),PaletteSpot(.72f,.32f,.17f),PaletteSpot(.26f,.52f,.18f),PaletteSpot(.70f,.65f,.18f),PaletteSpot(.34f,.82f,.165f))
        6->listOf(PaletteSpot(.30f,.18f,.145f),PaletteSpot(.68f,.23f,.155f),PaletteSpot(.22f,.48f,.15f),PaletteSpot(.62f,.52f,.155f),PaletteSpot(.30f,.79f,.155f),PaletteSpot(.69f,.78f,.15f))
        else->listOf(PaletteSpot(.30f,.14f,.106f),PaletteSpot(.63f,.16f,.11f),PaletteSpot(.19f,.37f,.104f),PaletteSpot(.49f,.37f,.107f),PaletteSpot(.80f,.40f,.104f),PaletteSpot(.28f,.59f,.108f),PaletteSpot(.62f,.61f,.109f),PaletteSpot(.20f,.81f,.102f),PaletteSpot(.50f,.84f,.108f),PaletteSpot(.78f,.78f,.101f)).take(count)
    }
    val random=Random(seed)
    val jitter=if(wide).003f else .004f
    val scattered=points.map{it.copy(x=it.x+(random.nextFloat()*2-1)*jitter,y=it.y+(random.nextFloat()*2-1)*jitter)}
    return scattered.dropLast(1).shuffled(random)+scattered.last()
}

/** The grip, rather than the centre of the overlay, is anchored to the actual FAB. */
internal fun mixingPalettePlacement(width:Float,height:Float,fab:Rect,count:Int,clearance:Float,density:Float):MixingPalettePlacement{
    val wide=width>height;val hole=mixingPaletteHole(count,wide);val aspect=mixingPaletteAspect(count,wide)
    val anchor=fab.center
    val safe=Rect(12*density,54*density,width-12*density,height-clearance-8*density)
    val maxWidth=minOf((anchor.x-safe.left)/hole.x,(safe.right-anchor.x)/(1-hole.x),
        (anchor.y-safe.top)/hole.y/aspect,(safe.bottom-anchor.y)/(1-hole.y)/aspect,width-24*density)
    val w=maxWidth.coerceAtLeast(1f);val h=w*aspect
    val left=anchor.x-hole.x*w;val top=anchor.y-hole.y*h
    return MixingPalettePlacement(Rect(left,top,left+w,top+h),anchor,wide)
}
internal fun mixingPaletteHoleRadius(w:Float,h:Float,density:Float)=minOf(22*density,w*.07f,h*.10f)

/** Smooth cubic silhouette and an actual transparent grip; both animate from the FAB. */
internal fun mixingPalettePath(width:Float,height:Float,progress:Float,wide:Boolean,count:Int,holeRadius:Float=mixingPaletteHoleRadius(width,height,1f)):Path{
    val end=listOf(Offset(.58f,.005f),Offset(.85f,.035f),Offset(.98f,.20f),Offset(1f,.45f),Offset(.998f,.72f),Offset(.985f,.90f),Offset(.90f,.985f),Offset(.64f,1f),Offset(.32f,.97f),Offset(.10f,.86f),Offset(.005f,.65f),Offset(.005f,.39f),Offset(.09f,.15f),Offset(.31f,.025f))
    val points=end.map{e->
        val angle=atan2(e.y-.5f,e.x-.5f)
        val sx=cos(angle).let{sign(it)*abs(it).pow(.63f)}*.5f+.5f
        val sy=sin(angle).let{sign(it)*abs(it).pow(.63f)}*.5f+.5f
        Offset((sx+(e.x-sx)*progress)*width,(sy+(e.y-sy)*progress)*height)
    }
    val path=Path()
    points.forEachIndexed{i,p->val before=points[(i+points.size-1)%points.size];val next=points[(i+1)%points.size];val after=points[(i+2)%points.size]
        if(i==0)path.moveTo(p.x,p.y)
        path.cubicTo(p.x+(next.x-before.x)/6,p.y+(next.y-before.y)/6,next.x-(after.x-p.x)/6,next.y-(after.y-p.y)/6,next.x,next.y)
    };path.close()
    val q=mixingPaletteHole(count,wide);val hole=Offset(.5f+(q.x-.5f)*progress,.5f+(q.y-.5f)*progress)
    path.fillType=Path.FillType.EVEN_ODD
    path.addCircle(hole.x*width,hole.y*height,holeRadius*((progress-.5f)/.5f).coerceIn(0f,1f),Path.Direction.CW)
    return path
}
