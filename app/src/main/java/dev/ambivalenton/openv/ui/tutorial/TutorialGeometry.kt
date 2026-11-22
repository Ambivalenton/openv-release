// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.tutorial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Share a baseline only for controls in one toolbar, never across a bottom FAB and header. */
fun tutorialTargetGroup(target:Rect,targets:List<Rect>,tolerance:Float):Rect {
    val aligned=targets.filter {abs(it.center.y-target.center.y)<=tolerance}
    return aligned.reduceOrNull {a,b->Rect(min(a.left,b.left),min(a.top,b.top),max(a.right,b.right),max(a.bottom,b.bottom))} ?: target
}

/** All distances are dp until converted at the geometry boundary. */
object TutorialDimensions {
    const val margin = 16f
    const val spotlightPadding = 8f
    const val cornerRadius = 20f
    const val cardWidth = 360f
    const val corridor = 64f
    const val compactCorridor = 32f
    const val minimumCardHeight = 144f
    const val arrowClearance = 7f
    const val arrowStroke = 2.25f
    const val arrowHead = 10f
}

enum class TutorialSide { ABOVE, BELOW, LEFT, RIGHT }

data class TutorialPlacement(
    val hole: Rect,
    val bubbleTop: Float,
    val bubbleLeft: Float,
    val bubbleWidth: Float,
    val above: Boolean,
    val bubbleHeight: Float,
    val bubbleMaxHeight: Float,
    val compact: Boolean,
    val side:TutorialSide=if(above) TutorialSide.ABOVE else TutorialSide.BELOW,
) {
    val bubbleBounds get() = Rect(bubbleLeft, bubbleTop, bubbleLeft + bubbleWidth, bubbleTop + bubbleHeight)
}

fun tutorialSpotlight(target: Rect, width: Float, height: Float, density: Float): Rect? {
    if (!width.isFinite() || !height.isFinite() || !density.isFinite() || density <= 0f ||
        width < 48f * density || height < 48f * density || target.isEmpty ||
        listOf(target.left, target.top, target.right, target.bottom).any { !it.isFinite() }) return null
    val padding = TutorialDimensions.spotlightPadding * density
    val hole = Rect(max(padding, target.left - padding), max(padding, target.top - padding),
        min(width - padding, target.right + padding), min(height - padding, target.bottom + padding))
    return hole.takeUnless { it.isEmpty }
}

/** The measured card must fit COMPLETELY. Never clip or silently turn it into a scroll
 * area. The layout engine can retry with more width or its compact copy presentation. */
fun tutorialPlacement(
    target:Rect,width:Float,height:Float,bubbleHeight:Float,density:Float,
    groupBounds:Rect=target,safeBounds:Rect=Rect(0f,0f,width,height),bubbleWidth:Float?=null,
):TutorialPlacement? {
    val hole=tutorialSpotlight(target,width,height,density) ?: return null
    val group=tutorialSpotlight(groupBounds,width,height,density) ?: hole
    if(!bubbleHeight.isFinite() || bubbleHeight<0f) return null
    val margin=TutorialDimensions.margin*density
    val safe=Rect(max(0f,safeBounds.left)+margin,max(0f,safeBounds.top)+margin,
        min(width,safeBounds.right)-margin,min(height,safeBounds.bottom)-margin)
    if(safe.isEmpty) return null
    val cardWidth=bubbleWidth ?: min(TutorialDimensions.cardWidth*density,safe.width)
    if(!cardWidth.isFinite() || cardWidth<=0f || cardWidth>safe.width+.1f || bubbleHeight>safe.height) return null
    val order=if(group.center.y>safe.center.y) listOf(TutorialSide.ABOVE,TutorialSide.BELOW)
        else listOf(TutorialSide.BELOW,TutorialSide.ABOVE)
    for(compact in listOf(false,true)) {
        val gap=(if(compact) TutorialDimensions.compactCorridor else TutorialDimensions.corridor)*density
        for(side in order+listOf(TutorialSide.RIGHT,TutorialSide.LEFT)) {
            val maximumHeight=when(side) {
                TutorialSide.ABOVE->group.top-gap-safe.top
                TutorialSide.BELOW->safe.bottom-group.bottom-gap
                else->safe.height
            }
            val lateralFits=when(side) {
                TutorialSide.RIGHT->safe.right-group.right-gap>=cardWidth
                TutorialSide.LEFT->group.left-gap-safe.left>=cardWidth
                else->true
            }
            if(bubbleHeight>maximumHeight+.1f || !lateralFits) continue
            val left=when(side) {
                TutorialSide.RIGHT->group.right+gap
                TutorialSide.LEFT->group.left-gap-cardWidth
                else->safe.center.x-cardWidth/2f
            }
            val top=when(side) {
                TutorialSide.ABOVE->group.top-gap-bubbleHeight
                TutorialSide.BELOW->group.bottom+gap
                else->(group.center.y-bubbleHeight/2f).coerceIn(safe.top,safe.bottom-bubbleHeight)
            }
            return TutorialPlacement(hole,top,left,cardWidth,side==TutorialSide.ABOVE,bubbleHeight,
                maximumHeight,compact,side)
        }
    }
    return null
}

data class TutorialArrow(
    val start: Offset, val control1: Offset, val control2: Offset, val tip: Offset,
    val headLeft: Offset, val headRight: Offset,
) {
    fun point(t: Float): Offset {
        val u = 1f - t
        return start * (u * u * u) + control1 * (3f * u * u * t) +
            control2 * (3f * u * t * t) + tip * (t * t * t)
    }
}

/** Edge anchors and actual end tangent; complete stroke stays in the reserved corridor. */
fun tutorialArrow(placement:TutorialPlacement,card:Rect,density:Float):TutorialArrow? {
    if(card.isEmpty || density<=0f || !density.isFinite()) return null
    val hole=placement.hole
    val clearance=TutorialDimensions.arrowClearance*density
    val vertical=placement.side==TutorialSide.ABOVE || placement.side==TutorialSide.BELOW
    val forward=when(placement.side) {
        TutorialSide.ABOVE->Offset(0f,1f)
        TutorialSide.BELOW->Offset(0f,-1f)
        TutorialSide.LEFT->Offset(1f,0f)
        TutorialSide.RIGHT->Offset(-1f,0f)
    }
    val tip=when(placement.side) {
        TutorialSide.ABOVE->Offset(hole.center.x,hole.top-clearance)
        TutorialSide.BELOW->Offset(hole.center.x,hole.bottom+clearance)
        TutorialSide.LEFT->Offset(hole.left-clearance,hole.center.y)
        TutorialSide.RIGHT->Offset(hole.right+clearance,hole.center.y)
    }
    val edge=when(placement.side) {
        TutorialSide.ABOVE->card.bottom+clearance
        TutorialSide.BELOW->card.top-clearance
        TutorialSide.LEFT->card.right+clearance
        TutorialSide.RIGHT->card.left-clearance
    }
    val gap=if(vertical) (tip.y-edge)*forward.y else (tip.x-edge)*forward.x
    if(gap<12f*density) return null
    val bend=if(placement.compact) 0f else min(48f*density,gap*.65f)
    val halfEdge=if(vertical) card.width else card.height
    val inset=min(32f*density,halfEdge/3f)
    val target=if(vertical) tip.x else tip.y
    val middle=if(vertical) card.center.x else card.center.y
    val cross=(target+if(target<middle) bend else -bend).coerceIn(
        (if(vertical) card.left else card.top)+inset,(if(vertical) card.right else card.bottom)-inset)
    val start=if(vertical) Offset(cross,edge) else Offset(edge,cross)
    val sideways=if(vertical) Offset(tip.x-start.x,0f) else Offset(0f,tip.y-start.y)
    val p1=start+forward*(gap*.38f)+sideways*.15f
    val p2=tip-forward*(gap*.34f)-sideways*.10f
    val tangent=tip-p2
    val unit=if(tangent.getDistance()>.001f) tangent/tangent.getDistance() else forward
    val side=Offset(-unit.y,unit.x)
    val head=min(TutorialDimensions.arrowHead*density,gap*.28f)
    return TutorialArrow(start,p1,p2,tip,tip-unit*head+side*head*.55f,tip-unit*head-side*head*.55f)
}
