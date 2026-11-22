// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import kotlin.math.*
internal const val LIQUID_SEARCH_EXIT_DURATION_MS=920
internal data class LiquidSearchExit(val width:Float,val height:Float,val mass:Float,val alpha:Float,val shape:Float,val iconAlpha:Float,val labelAlpha:Float,val ripple:Float)
internal fun liquidSearchExit(progress:Float):LiquidSearchExit {
    val p=progress.coerceIn(0f,1f)
    fun smooth(a:Float,b:Float):Float {val t=((p-a)/(b-a)).coerceIn(0f,1f);return t*t*(3-2*t)}
    fun mix(a:Float,b:Float,t:Float)=a+(b-a)*t
    val width=if(p<.60f)mix(1f,4.6f,smooth(.03f,.60f)) else mix(4.6f,6.5f,smooth(.60f,.94f))
    val height=if(p<.60f)mix(1f,.12f,smooth(0f,.60f)) else mix(.12f,0f,smooth(.60f,1f))
    return LiquidSearchExit(width,height,1-smooth(.28f,1f),1-smooth(.80f,1f),1-smooth(.10f,.56f),
        1-smooth(.02f,.24f),1-smooth(0f,.20f),sin(p*PI.toFloat()*9)*.05f*(1-p))
}
