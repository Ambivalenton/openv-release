// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.graphics.PathMeasure
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.*

/** Sample the actual theme outline, then deform it continuously without replacing its corners. */
internal fun themedPillPath(shape:Shape,rect:Rect,density:Density,direction:LayoutDirection,
    center:Float=rect.center.x,slot:Float=rect.width,inflation:Float=0f,strain:Float=0f,roundness:Float=1f):Path {
    val outline=shape.createOutline(Size(rect.width,rect.height),direction,density)
    val base=Path().apply {when(outline){is Outline.Generic->addPath(outline.path);is Outline.Rounded->addRoundRect(outline.roundRect);is Outline.Rectangle->addRect(outline.rect)}}
    // At rest use the exact theme path, avoiding resampling artefacts and needless native work.
    if(inflation==0f && strain==0f)return base.apply {translate(Offset(rect.left,rect.top))}
    val measure=PathMeasure(base.asAndroidPath(),true);val xy=FloatArray(2)
    return Path().apply {
        repeat(192) {i->
            measure.getPosTan(measure.length*i/192,xy,null)
            val x=rect.left+xy[0];val y=rect.top+xy[1]
            val nx=(xy[0]/rect.width*2-1).coerceIn(-1f,1f)
            val ny=(xy[1]/rect.height*2-1).coerceIn(-1f,1f)
            val exponent=2f+(1-roundness.coerceIn(0f,1f))*3f
            val distance=abs(x-center)/(slot*.65f).coerceAtLeast(1f)
            val locality=exp(-distance.pow(exponent))
            val bulge=inflation*locality
            val sideProfile=(1-abs(ny).pow(exponent)).coerceAtLeast(0f).pow(1f/exponent)
            val edgeWeight=abs(nx).pow(5)
            val horizontal=(inflation*.9f*locality+abs(tanh(strain))*rect.height*.09f)*sideProfile*edgeWeight
            val skew=tanh(strain*1.8f)*.12f*nx
            val px=x+sign(nx)*horizontal+bulge*nx*.18f
            val py=rect.center.y+(y-rect.center.y)*(1+skew)+bulge*ny
            if(i==0)moveTo(px,py) else lineTo(px,py)
        };close()
    }
}
