// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/* Continuous corners (SquircleShape, squirclePath) live in DesignLanguage.kt and serve both design systems. */

/**
 * Stepped 8-bit corners. A corner of radius r becomes a staircase of two or three square
 * pixels, so cards, chips and the navigation look like low-resolution sprites at every size.
 */
class PixelCornerShape(topStart:CornerSize,topEnd:CornerSize,bottomEnd:CornerSize,bottomStart:CornerSize):
    CornerBasedShape(topStart,topEnd,bottomEnd,bottomStart) {
    constructor(radius:Dp):this(CornerSize(radius),CornerSize(radius),CornerSize(radius),CornerSize(radius))
    override fun copy(topStart:CornerSize,topEnd:CornerSize,bottomEnd:CornerSize,bottomStart:CornerSize)=
        PixelCornerShape(topStart,topEnd,bottomEnd,bottomStart)
    override fun createOutline(size:Size,topStart:Float,topEnd:Float,bottomEnd:Float,bottomStart:Float,layoutDirection:LayoutDirection):Outline {
        val r=maxOf(topStart,topEnd,bottomEnd,bottomStart)
        if(r<=0f)return Outline.Rectangle(Rect(0f,0f,size.width,size.height))
        return Outline.Generic(pixelCornerPath(size,r))
    }
    override fun equals(other:Any?)=other is PixelCornerShape && topStart==other.topStart && topEnd==other.topEnd &&
        bottomEnd==other.bottomEnd && bottomStart==other.bottomStart
    override fun hashCode()=((topStart.hashCode()*31+topEnd.hashCode())*31+bottomEnd.hashCode())*31+bottomStart.hashCode()
}

internal fun pixelCornerPath(size:Size,radius:Float):Path {
    val w=size.width;val h=size.height
    val r=radius.coerceAtMost(min(w,h)/2f)
    val steps=if(r>=24f)3 else 2
    val s=r/steps
    return Path().apply {
        // Top-left corner to top edge, clockwise.
        moveTo(0f,r)
        for(i in 1..steps){lineTo(s*(i-1),r-s*(i-1));lineTo(s*(i-1),r-s*i);lineTo(s*i,r-s*i)}
        lineTo(w-r,0f)
        for(i in 1..steps){lineTo(w-r+s*(i-1),s*(i-1));lineTo(w-r+s*i,s*(i-1));lineTo(w-r+s*i,s*i)}
        lineTo(w,h-r)
        for(i in 1..steps){lineTo(w-s*(i-1),h-r+s*(i-1));lineTo(w-s*(i-1),h-r+s*i);lineTo(w-s*i,h-r+s*i)}
        lineTo(r,h)
        for(i in 1..steps){lineTo(r-s*(i-1),h-s*(i-1));lineTo(r-s*i,h-s*(i-1));lineTo(r-s*i,h-s*i)}
        close()
    }
}

/** Minimum pixel step: below this a stepped corner would read as noise. */
internal val PIXEL_MIN_RADIUS=6.dp
