// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.FootprintStyle
import kotlin.math.*

/** Identity persists across strides: hind and fore paws never become alternating human feet. */
enum class WalkingLimb { LEFT_HIND,LEFT_FORE,RIGHT_HIND,RIGHT_FORE,LEFT_FOOT,RIGHT_FOOT,LEFT_CRUTCH,RIGHT_CRUTCH }
data class TimedFootstep(val geometry:FootstepGeometry,val bornMillis:Float,val limb:WalkingLimb)
data class AnimalGait(val sequence:List<WalkingLimb>,val phases:List<Float>,val spread:Float,
    val bodyLength:Float,val stride:Float,val hindScale:Float=1f,val foreScale:Float=1f,val toeOut:Float=.04f)
private val lateralWalk=listOf(WalkingLimb.LEFT_HIND,WalkingLimb.LEFT_FORE,WalkingLimb.RIGHT_HIND,WalkingLimb.RIGHT_FORE)
fun animalGait(style:FootprintStyle):AnimalGait? = when(style) {
    FootprintStyle.LION->AnimalGait(lateralWalk,listOf(0f,.22f,.5f,.72f),1.05f,3.2f,2.5f,.96f,1.1f)
    FootprintStyle.BOAR->AnimalGait(lateralWalk,listOf(0f,.25f,.5f,.75f),.95f,2.9f,2.3f,.96f,1.07f,.04f)
    FootprintStyle.CROCODILE->AnimalGait(listOf(WalkingLimb.LEFT_HIND,WalkingLimb.RIGHT_FORE,WalkingLimb.RIGHT_HIND,WalkingLimb.LEFT_FORE),
        listOf(0f,.24f,.5f,.74f),1.5f,3.6f,2.2f,1.05f,.9f,.2f)
    FootprintStyle.SQUIRREL,FootprintStyle.MOUSE->AnimalGait(listOf(WalkingLimb.LEFT_FORE,WalkingLimb.RIGHT_FORE,WalkingLimb.LEFT_HIND,WalkingLimb.RIGHT_HIND),
        listOf(0f,.1f,.5f,.54f),.95f,2.3f,3f,1.12f,.78f,.06f)
    FootprintStyle.CAT->AnimalGait(lateralWalk,listOf(0f,.22f,.5f,.72f),.72f,2.7f,2.1f,.92f,1.02f)
    FootprintStyle.DOG->AnimalGait(lateralWalk,listOf(0f,.24f,.5f,.74f),1f,2.9f,2.3f,.92f,1.08f)
    FootprintStyle.HORSE->AnimalGait(lateralWalk,listOf(0f,.25f,.5f,.75f),.85f,4f,2.8f,.94f,1.04f,.025f)
    FootprintStyle.FOX->AnimalGait(lateralWalk,listOf(0f,.21f,.5f,.71f),.68f,2.85f,2.35f,.91f,1.03f)
    FootprintStyle.BEAR->AnimalGait(lateralWalk,listOf(0f,.27f,.5f,.77f),1.25f,2.7f,2.15f,1.06f,.98f,.09f)
    FootprintStyle.ELEPHANT->AnimalGait(lateralWalk,listOf(0f,.25f,.5f,.75f),1.3f,3.9f,2.25f,.92f,1.1f,.025f)
    FootprintStyle.DEER->AnimalGait(lateralWalk,listOf(0f,.23f,.5f,.73f),.72f,3.4f,2.8f,.92f,1.04f,.025f)
    FootprintStyle.GOAT->AnimalGait(lateralWalk,listOf(0f,.26f,.5f,.76f),.85f,2.6f,2.1f,.9f,1.08f,.05f)
    FootprintStyle.WOLF->AnimalGait(listOf(WalkingLimb.LEFT_HIND,WalkingLimb.RIGHT_FORE,WalkingLimb.RIGHT_HIND,WalkingLimb.LEFT_FORE),
        listOf(0f,.045f,.5f,.545f),.76f,3.4f,3.15f,.94f,1.08f,.02f)
    FootprintStyle.OTTER->AnimalGait(listOf(WalkingLimb.LEFT_FORE,WalkingLimb.RIGHT_FORE,WalkingLimb.LEFT_HIND,WalkingLimb.RIGHT_HIND),
        listOf(0f,.1f,.5f,.58f),1.1f,3.1f,2.7f,1.12f,.86f,.08f)
    FootprintStyle.RACCOON->AnimalGait(lateralWalk,listOf(0f,.2f,.5f,.7f),1.1f,2.7f,1.9f,1.15f,.82f,.09f)
    FootprintStyle.HEDGEHOG->AnimalGait(lateralWalk,listOf(0f,.29f,.5f,.79f),1.3f,2.2f,1.7f,.91f,1.04f,.12f)
    FootprintStyle.FROG->AnimalGait(listOf(WalkingLimb.LEFT_FORE,WalkingLimb.RIGHT_FORE,WalkingLimb.LEFT_HIND,WalkingLimb.RIGHT_HIND),
        listOf(0f,.035f,.5f,.535f),1.4f,2f,3.3f,1.28f,.66f,.2f)
    FootprintStyle.BADGER->AnimalGait(lateralWalk,listOf(0f,.24f,.5f,.74f),1.35f,2.7f,2.1f,.92f,1.15f,.06f)
    FootprintStyle.RABBIT->AnimalGait(listOf(WalkingLimb.LEFT_FORE,WalkingLimb.RIGHT_FORE,WalkingLimb.LEFT_HIND,WalkingLimb.RIGHT_HIND),
        listOf(0f,.12f,.5f,.52f),1f,2.3f,3f,1.15f,.72f,.06f)
    FootprintStyle.LIZARD,FootprintStyle.TURTLE->AnimalGait(
        listOf(WalkingLimb.LEFT_HIND,WalkingLimb.RIGHT_FORE,WalkingLimb.RIGHT_HIND,WalkingLimb.LEFT_FORE),
        listOf(0f,.24f,.5f,.74f),1.4f,2.2f,1.9f,.95f,1f,.18f)
    else->null
}

/** Body motion follows the same monotone smooth route. Each of four limbs advances independently;
 * a hind contact may lie behind a fore contact, as in an actual animal's tracks. */
fun walkingGaitSteps(width:Float,height:Float,seed:Long,sizeScale:Float,straight:Boolean,style:FootprintStyle):List<TimedFootstep> {
    if(style.continuous)return emptyList()
    if(style==FootprintStyle.CRUTCHES)return crutchSteps(width,height,seed,sizeScale,straight)
    if(style==FootprintStyle.DUCK || style==FootprintStyle.PENGUIN)return duckWaddleSteps(width,height,seed,sizeScale,straight)
    val gait=animalGait(style) ?: run {
        val stride=when(style) {FootprintStyle.BIRD,FootprintStyle.CHICKEN->.8f;FootprintStyle.OSTRICH,FootprintStyle.DINOSAUR->1.25f;FootprintStyle.HIGH_HEEL->.85f;else->1f}
        val interval=when(style) {FootprintStyle.BIRD->800f;FootprintStyle.DINOSAUR->1_150f;else->FOOTSTEP_INTERVAL_MS.toFloat()}
        return walkingFootsteps(width,height,seed,sizeScale,straight,stride).mapIndexed {index,g->
            val toe=0f
            TimedFootstep(g.copy(angle=g.angle+toe),index*interval,if(g.left) WalkingLimb.LEFT_FOOT else WalkingLimb.RIGHT_FOOT)
        }
    }
    // Reserve the widest fore/hind paw and leg spread before constructing the centre path.
    val extra=max(gait.foreScale,gait.hindScale).coerceAtLeast(1f)*gait.spread.coerceAtLeast(1f)
    val route=walkingTrajectory(width,height,seed,sizeScale*extra,straight) ?: return emptyList()
    val scale=route.scale/extra
    val body=gait.bodyLength*FOOTPRINT_LENGTH_PX*scale*.5f
    val stride=FOOTPRINT_STRIDE_PX*scale*gait.stride
    val usable=(route.length-2*body).coerceAtLeast(0f)
    val finalPhase=gait.phases.max()
    val count=(floor(usable/stride.coerceAtLeast(1f)-finalPhase).toInt()+1).coerceIn(1,256)
    return buildList {
        repeat(count) {cycle->
            gait.sequence.forEachIndexed {beat,limb->
                val left=limb==WalkingLimb.LEFT_HIND || limb==WalkingLimb.LEFT_FORE
                val hind=limb==WalkingLimb.LEFT_HIND || limb==WalkingLimb.RIGHT_HIND
                val center=if(usable<=0) route.length/2 else body+usable*(cycle+gait.phases[beat])/(count-1+finalPhase).coerceAtLeast(.01f)
                val point=route.at((center+if(hind) -body else body).coerceIn(0f,route.length))
                val side=if(left) -1f else 1f
                val spread=FOOTPRINT_FOOT_OFFSET_PX*scale*gait.spread
                val pawScale=scale*if(hind) gait.hindScale else gait.foreScale
                // Clamp the complete rotated sole to the finite track, including narrow rails.
                val radius=hypot(FOOTPRINT_LENGTH_PX/2,FOOTPRINT_WIDTH_PX/2)*pawScale
                val x=(point.x+cos(point.heading)*spread*side).coerceIn(min(radius,width/2),max(width-radius,width/2))
                val y=(point.y-sin(point.heading)*spread*side).coerceIn(min(radius,height/2),max(height-radius,height/2))
                add(TimedFootstep(FootstepGeometry(x,y,point.heading+side*gait.toeOut,pawScale,left,fore=!hind),
                    (cycle+gait.phases[beat])*2_000f,limb))
            }
        }
    }.sortedBy {it.bornMillis}
}
fun walkingGaitDuration(steps:List<TimedFootstep>)=(steps.lastOrNull()?.bornMillis ?: 0f)+FOOTSTEP_LIFETIME_MS+FOOTSTEP_REST_MS
fun walkingGaitAlpha(step:TimedFootstep,timeMillis:Float):Float {
    val age=timeMillis-step.bornMillis
    fun smooth(t:Float):Float {val x=t.coerceIn(0f,1f);return x*x*(3-2*x)}
    return when {age<=0 || age>=FOOTSTEP_LIFETIME_MS->0f;age<350->smooth(age/350);age<900->1f
        else->1f-smooth((age-900)/(FOOTSTEP_LIFETIME_MS-900))}
}

/** Short, broad, alternating outward contacts; planted feet never slide. */
private fun duckWaddleSteps(width:Float,height:Float,seed:Long,sizeScale:Float,straight:Boolean):List<TimedFootstep> =
    walkingFootsteps(width,height,seed,sizeScale,straight,.64f).mapIndexed {index,g->
        val side=if(g.left)-1f else 1f;val scale=g.scale*1.12f
        val radius=hypot(FOOTPRINT_WIDTH_PX/2,FOOTPRINT_LENGTH_PX*.62f/2)*scale
        val x=(g.x+side*FOOTPRINT_FOOT_OFFSET_PX*g.scale*.18f).coerceIn(min(radius,width/2),max(width-radius,width/2))
        val y=g.y.coerceIn(min(radius,height/2),max(height-radius,height/2))
        TimedFootstep(g.copy(x=x,y=y,scale=scale,angle=g.angle+side*(.29f+.035f*sin(index*.8f))),
            index*910f-(if(index%2==1)65f else 0f),if(g.left)WalkingLimb.LEFT_FOOT else WalkingLimb.RIGHT_FOOT)
    }

/** Three-point support: both rubber tips plant, followed by the advancing supported foot. */
private fun crutchSteps(width:Float,height:Float,seed:Long,sizeScale:Float,straight:Boolean):List<TimedFootstep> =
    walkingFootsteps(width,height,seed,sizeScale,straight,.8f).flatMapIndexed {i,g->
        val spread=FOOTPRINT_FOOT_OFFSET_PX*g.scale*1.4f
        val dx=cos(g.angle)*spread;val dy=-sin(g.angle)*spread
        val lead=FOOTPRINT_LENGTH_PX*g.scale*.28f
        val centerX=g.x-(if(g.left)-1 else 1)*cos(g.angle)*FOOTPRINT_FOOT_OFFSET_PX*g.scale+sin(g.angle)*lead
        val centerY=g.y+(if(g.left)-1 else 1)*sin(g.angle)*FOOTPRINT_FOOT_OFFSET_PX*g.scale+cos(g.angle)*lead
        val tip=.28f*g.scale
        val left=g.copy(x=centerX-dx,y=centerY-dy,scale=tip,left=true,fore=true)
        val right=g.copy(x=centerX+dx,y=centerY+dy,scale=tip,left=false,fore=true)
        listOf(TimedFootstep(left,i*2_250f,WalkingLimb.LEFT_CRUTCH),TimedFootstep(right,i*2_250f+100f,WalkingLimb.RIGHT_CRUTCH),
            TimedFootstep(g.copy(fore=false),i*2_250f+920f,if(g.left)WalkingLimb.LEFT_FOOT else WalkingLimb.RIGHT_FOOT))
    }
