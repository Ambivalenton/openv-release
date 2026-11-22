// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import kotlin.math.*

/** A continuous angular colour field gently twists towards the centre; never discrete wedges. */
internal fun paletteSwirl(colors:List<Int>,edge:Int=256):Bitmap {
    require(colors.isNotEmpty()&&edge in 32..512)
    val shades=colors.map{it or 0xff000000.toInt()}.distinct()
    val middle=shades.reduce{a,b->ColorUtils.blendARGB(a,b,.5f)}
    val pixels=IntArray(edge*edge)
    for(y in 0 until edge)for(x in 0 until edge){
        val dx=(x+.5f-edge/2f)/(edge/2f);val dy=(y+.5f-edge/2f)/(edge/2f)
        val radius=hypot(dx,dy).coerceIn(0f,1f)
        val angle=atan2(dy,dx)+2.5f*(1-radius).pow(1.4f)
        val phase=((angle/(2*PI)).toFloat()%1f+1f)%1f*shades.size
        val i=floor(phase).toInt();val blend=(1-cos((phase-i)*PI)).toFloat()/2
        val color=ColorUtils.blendARGB(shades[i%shades.size],shades[(i+1)%shades.size],blend)
        val centre=(radius/.16f).coerceIn(0f,1f).let{it*it*(3-2*it)}
        pixels[y*edge+x]=ColorUtils.blendARGB(middle,color,centre) or 0xff000000.toInt()
    }
    return Bitmap.createBitmap(pixels,edge,edge,Bitmap.Config.ARGB_8888)
}
