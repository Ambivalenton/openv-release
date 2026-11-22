// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import androidx.compose.ui.geometry.Offset
import kotlin.math.*

/** Compact Gaussian: value and first two derivatives reach zero at the tab boundary. */
internal fun localPillWeight(x: Float, center: Float, slot: Float): Float {
    val u=abs(x-center)/(slot*.5f).coerceAtLeast(1f)
    if(u>=1f) return 0f
    val gaussian=exp(-.5f*((x-center)/(slot*.50f).coerceAtLeast(1f)).pow(2))
    val falloff=1-(10*u.pow(3)-15*u.pow(4)+6*u.pow(5))
    return gaussian*falloff
}

internal fun localPillContour(width: Float, height: Float, inset: Float,
    center: Float, slot: Float, inflation: Float): List<Offset> {
    val radius=(height/2-inset).coerceAtLeast(1f)
    val left=inset+radius;val right=width-inset-radius;val cy=height/2
    val points=mutableListOf<Offset>()
    val support=(slot*.5f).coerceAtLeast(1f)
    fun edgeExposure(distance: Float): Float {
        val t=((distance-support)/(support*.6f)).coerceIn(0f,1f)
        return 1f-t*t*(3f-2f*t)
    }
    val leftExposure=edgeExposure(center-inset)
    val rightExposure=edgeExposure(width-inset-center)
    fun add(x: Float,y: Float,nx: Float,ny: Float) {
        // Blend cap expansion continuously as the moving influence area approaches either end.
        // A binary "edge tab" branch would pop when the capsule crosses that boundary.
        val compact=localPillWeight(x,center,slot)
        val gaussian=exp(-.5f*((x-center)/(slot*.50f).coerceAtLeast(1f)).pow(2))
        val exposure=if(x<center) leftExposure else rightExposure
        val weight=compact+(gaussian-compact)*exposure
        val amount=inflation*weight
        points+=Offset(x+nx*amount,y+ny*amount)
    }
    for (i in 0..40) add(left+(right-left)*i/40,inset,0f,-1f)
    for (i in 0..48) {
        val angle=-PI/2+PI*i/48
        add(right+radius*cos(angle).toFloat(),cy+radius*sin(angle).toFloat(),cos(angle).toFloat(),sin(angle).toFloat())
    }
    for (i in 0..40) add(right-(right-left)*i/40,height-inset,0f,1f)
    for (i in 0..48) {
        val angle=PI/2+PI*i/48
        add(left+radius*cos(angle).toFloat(),cy+radius*sin(angle).toFloat(),cos(angle).toFloat(),sin(angle).toFloat())
    }
    return points
}
