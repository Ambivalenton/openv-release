// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import kotlin.math.*

const val PIN_TRANSITION_MILLIS=1_000
const val PIN_IMPACT_FRACTION=.74f
const val PIN_COMMIT_FRACTION=.88f

data class PinFabFrame(val width:Float,val height:Float,val corner:Float,val pinY:Float,
    val rotationX:Float,val rotationY:Float,val rotationZ:Float,val pinScale:Float,
    val compression:Float,val impact:Float,val insertion:Float,val pinX:Float=0f)
private fun pinSmooth(value:Float):Float {val t=value.coerceIn(0f,1f);return t*t*t*(t*(t*6-15)+10)}
private fun pinLerp(a:Float,b:Float,t:Float)=a+(b-a)*t

/** One authored clock: anticipation, silhouette morph, 3-D lift, decisive insertion, damped settle. */
fun pinFabFrame(progress:Float):PinFabFrame {
    val p=progress.coerceIn(0f,1f)
    val morph=pinSmooth((p-.08f)/.48f)
    val lift=pinSmooth((p-.10f)/.40f)
    val strike=pinSmooth((p-.63f)/.11f)
    val after=((p-PIN_IMPACT_FRACTION)/(1-PIN_IMPACT_FRACTION)).coerceIn(0f,1f)
    val ring=if(p>=PIN_IMPACT_FRACTION) sin(after*PI*3).toFloat()*exp(-5*after) else 0f
    val anticipation=sin(PI*(p/.22f).coerceIn(0f,1f)).toFloat()*(1-morph)
    val impact=if(p>=PIN_IMPACT_FRACTION) exp(-10*after)*(1-after) else 0f
    return PinFabFrame(
        width=pinLerp(56f,80f,morph)+1.2f*anticipation+1.5f*ring,
        height=pinLerp(56f,116f,morph)-2*anticipation-2.6f*ring,
        corner=pinLerp(28f,22f,morph),
        pinY=pinLerp(-17f*lift,10f,strike)+2.2f*ring,
        rotationX=-32f*lift*(1-strike)+5f*ring,
        rotationY=-15f*lift*(1-strike)+2f*ring,
        rotationZ=pinLerp(-18f,7f,lift)*(1-strike)-2f*ring,
        pinScale=1f+.19f*lift*(1-strike)-.06f*strike,
        compression=1f-.025f*anticipation-.025f*impact,
        impact=impact,insertion=strike,pinX=14f*morph)
}
