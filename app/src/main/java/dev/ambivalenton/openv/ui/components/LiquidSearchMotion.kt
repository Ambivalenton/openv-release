// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import kotlin.math.*

/**
 * The drop that becomes the search button, in one continuous motion (750 ms). It buds out of
 * the destination field's lower edge, falls with gravity (stretched by its speed), and on contact a
 * damped spring squashes and rebounds it while it turns into the button outline. Every quantity is
 * continuous in value and speed, so there are no keyframe stops any more.
 */
internal const val LIQUID_SEARCH_DURATION_MS=750
private const val TOTAL_S=.75f
private const val BUD_S=.15f
private const val FALL_S=.30f
private const val IMPACT_S=BUD_S+FALL_S
/** Spring of the impact: 3.4 Hz, damping ratio 0.4, entering stretched by 22 % and fast. */
private const val SPRING_HZ=3.4f
private const val SPRING_DAMPING=.40f
private const val SPRING_STRETCH=.22f
private const val SPRING_SPEED=-22f

internal data class LiquidSearchFrame(val fall:Float,val scaleX:Float,val scaleY:Float,val mass:Float,val tear:Float,val wobble:Float,val shape:Float,val labelAlpha:Float,val lift:Float=0f)

private fun smooth(a:Float,b:Float,x:Float):Float {val t=((x-a)/(b-a)).coerceIn(0f,1f);return t*t*(3-2*t)}

/** Damped spring x(τ) with start value [x0] and start speed [v0]. */
private fun spring(tau:Float,x0:Float,v0:Float):Float {
    val w0=2*PI.toFloat()*SPRING_HZ;val z=SPRING_DAMPING;val wd=w0*sqrt(1-z*z)
    return exp(-z*w0*tau)*(x0*cos(wd*tau)+(v0+z*w0*x0)/wd*sin(wd*tau))
}

internal fun liquidSearchFrame(progress:Float):LiquidSearchFrame {
    val t=progress.coerceIn(0f,1f)*TOTAL_S
    // Volume grows out of the field edge (ease-out), then stays.
    val bud=(t/BUD_S).coerceIn(0f,1f);val mass=1-(1-bud).pow(3)
    if(t<IMPACT_S) {
        val u=((t-BUD_S)/FALL_S).coerceIn(0f,1f)
        val fall=u*u
        // Stretched by its speed (proportional to u under gravity), narrower for the same volume.
        val sy=1+SPRING_STRETCH*u;val sx=sy.pow(-.7f)
        // Hanging by a neck while it buds, then a slightly pointed falling drop.
        val hang=1-smooth(BUD_S*.6f,BUD_S+.06f,t)
        val tear=.55f*hang+(.18f+.1f*u)*(1-hang)
        // Below the edge while hanging; at contact its lower side touches the button's lower edge.
        val lift=-.5f*mass*(1-fall)+(sy-1)/2*fall
        val wobble=sin(t*2*PI.toFloat()*5)*.02f*(1-u)*mass
        return LiquidSearchFrame(fall,sx,sy,mass,tear,wobble,0f,0f,lift)
    }
    val tau=t-IMPACT_S
    val settle=1-smooth(.20f,.30f,tau)
    val sy=1+spring(tau,SPRING_STRETCH,SPRING_SPEED)*settle;val sx=sy.pow(-.7f)
    val wobble=.05f*exp(-tau/.09f)*sin(2*PI.toFloat()*9*tau)*settle
    return LiquidSearchFrame(1f,sx,sy,1f,.28f*(1-smooth(0f,.05f,tau)),wobble,smooth(.10f,.28f,tau),smooth(.16f,.30f,tau),(sy-1)/2)
}
