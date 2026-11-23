// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
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
