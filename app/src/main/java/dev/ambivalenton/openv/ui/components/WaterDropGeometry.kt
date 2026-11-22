// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.tanh

/** Signed head/tail separation drives asymmetry continuously; zero strain is an exact capsule. */
internal data class WaterDropGeometry(val rect: Rect,val leftRadius: Float,val rightRadius: Float) {
    fun path(): Path {
        val l=rect.left;val r=rect.right;val cy=rect.center.y
        val a=leftRadius;val b=rightRadius;val bridge=(rect.width-a-b).coerceAtLeast(0f)
        val k=.55228475f
        return Path().apply {
            moveTo(l+a,cy-a)
            cubicTo(l+a+bridge*.35f,cy-a,r-b-bridge*.35f,cy-b,r-b,cy-b)
            cubicTo(r-b+k*b,cy-b,r,cy-k*b,r,cy)
            cubicTo(r,cy+k*b,r-b+k*b,cy+b,r-b,cy+b)
            cubicTo(r-b-bridge*.35f,cy+b,l+a+bridge*.35f,cy+a,l+a,cy+a)
            cubicTo(l+a-k*a,cy+a,l,cy+k*a,l,cy)
            cubicTo(l,cy-k*a,l+a-k*a,cy-a,l+a,cy-a)
            close()
        }
    }
}
internal fun waterDropGeometry(rect: Rect,head: Float,tail: Float): WaterDropGeometry {
    val strain=tanh((head-tail)*1.8f)
    val amount=abs(strain)
    val round=rect.height/2
    val lead=(round*(1+.10f*amount)).coerceAtMost(rect.width*.42f)
    val trail=(round*(1-.26f*amount)).coerceAtMost(rect.width*.42f)
    return if(strain>=0) WaterDropGeometry(rect,trail,lead) else WaterDropGeometry(rect,lead,trail)
}
