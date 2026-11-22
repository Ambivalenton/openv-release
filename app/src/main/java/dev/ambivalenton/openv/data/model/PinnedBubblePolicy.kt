// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlin.math.abs
import kotlin.math.hypot

/*
 * Pure rules of the floating bubble (build 65). The overlay host applies them; it never starts
 * its own location, sensor, polling or replanning loop and only shows the shared journey state.
 *
 * Four separate states decide visibility:
 *  - the global bubble mode (setting) and the granted overlay access,
 *  - an existing pinned connection,
 *  - the per-session decision "bubble closed" (survives refreshes and restarts, reset by a new pin
 *    or the bubble button),
 *  - the screen context: hidden only while the device is locked.
 * A shown bubble replaces the pinned card: it also floats above the app itself. The card is hidden
 * only while a bubble window is really attached (never "neither card nor bubble"), so it returns as
 * soon as the bubble is closed, hidden on the lock screen or could not be added. Journey tracking keeps running
 * independently of all of them.
 */
data class PinnedBubbleInputs(val modeEnabled:Boolean,val overlayAllowed:Boolean,val sessionId:String?,
    val dismissedForSession:Boolean,val deviceLocked:Boolean)

/** The bubble stands in for the pinned card (mode, access, pin and not closed for this pin). */
fun pinnedBubbleActive(inputs:PinnedBubbleInputs):Boolean = inputs.modeEnabled && inputs.overlayAllowed &&
    inputs.sessionId!=null && !inputs.dismissedForSession

fun pinnedBubbleVisible(inputs:PinnedBubbleInputs):Boolean = pinnedBubbleActive(inputs) && !inputs.deviceLocked

/** Starting values (configurable), expressed in dp and converted by the host with the density. */
object PinnedBubbleMetrics {
    const val BUBBLE_DP=56
    const val EDGE_MARGIN_DP=8
    /** Transparent window margin around head and card, wide enough for the whole shadow. */
    const val SHADOW_INSET_DP=16
    const val CARD_DP=300
    /** Close zone height above the bottom system inset; ~150 px reference at 2× density. */
    const val CLOSE_ZONE_DP=76
    /** Remaining momentum after release; the bubble travels at most this long at release speed. */
    const val MOMENTUM_SECONDS=.12f
    const val MAX_FLING_DP_PER_SECOND=2400f
    const val TAP_MAX_MILLIS=350L
}

/** A touch is a tap only without movement beyond the platform touch slop and within a short time. */
fun bubbleIsTap(dx:Float,dy:Float,touchSlop:Float,durationMillis:Long):Boolean =
    hypot(dx,dy)<=touchSlop && durationMillis<=PinnedBubbleMetrics.TAP_MAX_MILLIS

data class BubbleArea(val left:Int,val top:Int,val right:Int,val bottom:Int) {
    val width get()=right-left
    val height get()=bottom-top
}

data class BubbleRest(val x:Int,val y:Int,val dockedRight:Boolean)

/**
 * Release with bounded momentum, then dock magnetically to the nearer usable edge. Velocity is
 * clamped; the result always lies inside [area] (system bars and display cutouts excluded).
 */
fun bubbleRestingPosition(x:Int,y:Int,size:Int,velocityX:Float,velocityY:Float,area:BubbleArea,
    margin:Int,maxVelocity:Float):BubbleRest {
    val vx=velocityX.coerceIn(-maxVelocity,maxVelocity);val vy=velocityY.coerceIn(-maxVelocity,maxVelocity)
    val projectedX=x+vx*PinnedBubbleMetrics.MOMENTUM_SECONDS
    val projectedY=y+vy*PinnedBubbleMetrics.MOMENTUM_SECONDS
    val right=projectedX+size/2f>area.left+area.width/2f
    val targetX=if(right)area.right-size-margin else area.left+margin
    val targetY=projectedY.toInt().coerceIn(area.top+margin,maxOf(area.top+margin,area.bottom-size-margin))
    return BubbleRest(targetX,targetY,right)
}

/** Restore or re-clamp after rotation and size changes: same side, same relative height. */
fun bubbleRestoredPosition(dockedRight:Boolean,verticalFraction:Float,size:Int,area:BubbleArea,margin:Int):BubbleRest {
    val top=area.top+margin;val bottom=maxOf(top,area.bottom-size-margin)
    val y=(top+(bottom-top)*verticalFraction.coerceIn(0f,1f)).toInt()
    return BubbleRest(if(dockedRight)area.right-size-margin else area.left+margin,y,dockedRight)
}

fun bubbleVerticalFraction(y:Int,size:Int,area:BubbleArea,margin:Int):Float {
    val top=area.top+margin;val bottom=maxOf(top+1,area.bottom-size-margin)
    return ((y-top).toFloat()/(bottom-top)).coerceIn(0f,1f)
}

/** The drop zone spans the usable width at the bottom; the bubble centre decides. */
fun bubbleInCloseZone(centerY:Float,area:BubbleArea,zoneHeight:Int):Boolean = centerY>=area.bottom-zoneHeight

/** Expanded card placement: it grows away from the docked edge and stays fully on screen. */
fun bubbleCardX(dockedRight:Boolean,bubbleX:Int,size:Int,cardWidth:Int,area:BubbleArea,margin:Int):Int =
    (if(dockedRight)bubbleX+size-cardWidth else bubbleX).coerceIn(area.left+margin,maxOf(area.left+margin,area.right-cardWidth-margin))

/** Movement within the slop is not a drag; it keeps the bubble exactly where it was. */
fun bubbleDragged(dx:Float,dy:Float,touchSlop:Float)=abs(dx)>touchSlop || abs(dy)>touchSlop
