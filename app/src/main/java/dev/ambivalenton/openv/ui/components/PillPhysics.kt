// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.*

internal const val PILL_FLASH_MILLIS=180
internal const val PILL_SHELL_DAMPING=.30f
internal const val PILL_HEAD_DAMPING=.57f
internal const val PILL_TAIL_DAMPING=.47f

internal data class PillVisualGeometry(val capsule: Rect,val inflationCenterX: Float,val inflationWidth: Float)

/** One frame supplies both shapes: the outer bulge cannot jump ahead to a target tab. */
internal fun pillVisualGeometry(width: Float,height: Float,slots: Int,inset: Float,
    head: Float,tail: Float,squash: Float): PillVisualGeometry {
    val capsule=elasticCapsuleRect(width,height,slots,inset,head,tail,squash)
    val slot=(width-2*inset)/slots.coerceAtLeast(1)
    return PillVisualGeometry(capsule,capsule.center.x,maxOf(slot,capsule.width*1.2f))
}

/** Fast leading edge and slower trailing edge form a travelling elastic body. */
internal fun elasticCapsuleRect(width: Float,height: Float,slots: Int,inset: Float,
    head: Float,tail: Float,squash: Float): Rect {
    val slot=(width-2*inset)/slots.coerceAtLeast(1)
    val stretch=(abs(head-tail)*.72f).coerceIn(0f,.78f)
    val w=(slot*.82f*(1+stretch+squash.coerceIn(-.22f,.14f))).coerceIn(slot*.6f,minOf(width-2*inset,slot*1.5f))
    val h=((height-2*inset)*.70f/(1+stretch*.18f+squash*.28f)).coerceAtMost(height-2*inset-8f)
    val cx=(inset+slot*((head+tail)/2+.5f)).coerceIn(inset+w/2,width-inset-w/2)
    return Rect(cx-w/2,(height-h)/2,cx+w/2,(height+h)/2)
}

/** Signed return amplitude is intentional: a damped spring crosses zero into local compression. */
@Stable internal class PillShellMotion {
    val amount=Animatable(0f)
    val landing=Animatable(0f)
    suspend fun inflate()=amount.animateTo(1f,spring(dampingRatio=.65f,stiffness=620f))
    suspend fun settle()=amount.animateTo(0f,spring(dampingRatio=PILL_SHELL_DAMPING,stiffness=330f))
    /** Build 106: an impulse instead of a jump to -16 %: the squash grows out of rest and swings back,
     * with no frame where the capsule changes size at once. */
    suspend fun land(strength:Float=1f) {
        landing.animateTo(0f,spring(dampingRatio=.38f,stiffness=430f),initialVelocity=-PILL_LANDING_IMPULSE*strength)
    }
}

/** Gives a landing squash of about 16 % (spring .38/430: peak ≈ 0.03 × the impulse). */
internal const val PILL_LANDING_IMPULSE=5.4f
/** A symbol counts as hit while the capsule's middle is this close (in slots). */
internal const val PILL_HIT_RADIUS=.3f
/** A symbol's hop (spring .42/520): this impulse lifts it by one full hop height. */
internal const val PILL_HOP_VELOCITY=38f
/** A symbol the capsule only passes on its way hops lower. */
internal const val PILL_PASS_HOP=.45f

/** Draw-phase pose of a symbol that hops: up and slim while in the air, flat and wide when it lands. */
internal data class HopPose(val lift:Float,val scaleX:Float,val scaleY:Float)
internal fun hopPose(hop:Float,press:Float=1f):HopPose =
    HopPose(lift=hop.coerceAtLeast(0f),scaleX=press*(1f-.08f*hop),scaleY=press*(1f+.14f*hop))

/**
 * Build 113 · how far the outline of [shape] (a capsule of [width] × [height] px) lies inside its box
 * at [edgeDistance] px from its upper or lower edge – on the side where it lies furthest inside. A
 * label that reaches that low must stay this far from both sides to stay inside a round, cut or
 * hexagonal capsule. Cut corners recede in a straight line, all others like a circle (the most
 * receding of the convex corners the designs use).
 */
internal fun capsuleSideInset(shape: CornerBasedShape, width: Float, height: Float, density: Density, edgeDistance: Float): Float {
    val box = Size(width, height)
    val cut = shape is CutCornerShape
    return listOf(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart).maxOf { corner ->
        val r = corner.toPx(box, density).coerceIn(0f, min(width, height) / 2f)
        val dy = edgeDistance.coerceAtLeast(0f)
        when {
            dy >= r -> 0f
            cut -> r - dy
            else -> r - sqrt(r * r - (r - dy) * (r - dy))
        }
    }
}

/**
 * Build 113 · one size for all labels of the pill: the largest (at most the style's own size) at which
 * every label fits on one line – measured in the selected (wider) weight, so a tap never changes the
 * size. [widthFor] gives the room a label has at a given line height (a lower label sits nearer the
 * capsule's receding lower corners); [maxHeight] is the room below the symbol. The longest word
 * decides, so "Suchen" never stands larger than "Übersicht" beside it.
 */
internal fun sharedLabelSize(measurer: TextMeasurer, labels: List<String>, style: TextStyle, maxHeight: Int,
    widthFor: (lineHeight: Int) -> Int): TextUnit {
    fun fits(label: String, size: Float): Boolean {
        val layout = measurer.measure(label, style.copy(fontSize = size.sp), maxLines = 1, softWrap = false)
        return layout.size.height <= maxHeight && layout.size.width <= widthFor(layout.size.height)
    }
    var size = style.fontSize.value
    for (label in labels) {
        if (fits(label, size)) continue
        // Largest fitting size below the current one, in steps of 0.25 sp.
        var low = 0; var high = ((size - .5f) / .25f).toInt() - 1
        if (high < 0 || !fits(label, .5f)) { size = .5f; continue }
        while (low < high) { val mid = (low + high + 1) / 2; if (fits(label, .5f + mid * .25f)) low = mid else high = mid - 1 }
        size = .5f + low * .25f
    }
    return size.sp
}
