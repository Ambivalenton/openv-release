// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.*

/** Capillary fronts spread independently of the disappearing volume; the pool never shrinks back to a dot. */
internal fun liquidPoolPoints(width:Float,edge:Float,progress:Float):List<Offset> {
    val p=progress.coerceIn(0f,1f)
    fun smooth(a:Float,b:Float):Float {val t=((p-a)/(b-a)).coerceIn(0f,1f);return t*t*(3-2*t)}
    val left=edge*.48f+(width*.475f-edge*.48f)*smooth(.01f,.74f)
    val right=edge*.48f+(width*.475f-edge*.48f)*smooth(.05f,.82f)
    val thickness=edge*(.34f*(1-smooth(0f,.65f))+.065f*(1-smooth(.65f,1f)))
    val mound=edge*.58f*(1-smooth(0f,.38f))
    val top=(0..48).map {i->
        val u=i/24f-1;val x=width/2+if(u<0)u*left else u*right
        val envelope=(1-abs(u).pow(3)).coerceAtLeast(0f).pow(.7f)
        val wave=1+.20f*sin(u*9-p*11)+.08f*cos(u*17+p*8)
        val ridge=exp(-((abs(u)-(.15f+p*.75f))/.18f).pow(2))*edge*.06f*(1-p)
        Offset(x,edge-(thickness*wave+mound*exp(-u*u*5)+ridge)*envelope)
    }
    val bottom=(48 downTo 0).map {i->val u=i/24f-1
        Offset(width/2+if(u<0)u*left else u*right,edge-.25f*(1-p)*(1-u*u)*sin(u*13+p*4))}
    return top+bottom
}
internal fun fluidContour(points:List<Offset>):Path=Path().apply {
    if(points.isEmpty())return@apply
    moveTo(points[0].x,points[0].y)
    for(i in points.indices) {
        val a=points[(i-1+points.size)%points.size];val b=points[i];val c=points[(i+1)%points.size];val d=points[(i+2)%points.size]
        cubicTo(b.x+(c.x-a.x)/6,b.y+(c.y-a.y)/6,c.x-(d.x-b.x)/6,c.y-(d.y-b.y)/6,c.x,c.y)
    };close()
}
