// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.tutorial

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

/** Three separable box convolutions approximate Gaussian blur on a downsampled, RAM-only bitmap. */
internal fun softwareTutorialBlur(source:Bitmap,radiusPx:Float):Bitmap {
    val factor=6
    val width=max(1,source.width/factor);val height=max(1,source.height/factor)
    // Area prefilter avoids aliasing fine LED/pixel patterns into broad moiré bands.
    // Only a few source rows are buffered, rather than another full-screen pixel array.
    var pixels=IntArray(width*height)
    val band=IntArray(source.width*(factor+2))
    for(y in 0 until height) {
        val y0=y*source.height/height;val y1=(y+1)*source.height/height
        source.getPixels(band,0,source.width,0,y0,source.width,y1-y0)
        for(x in 0 until width) {
            val x0=x*source.width/width;val x1=(x+1)*source.width/width
            var a=0;var r=0;var g=0;var b=0
            for(row in 0 until y1-y0) for(col in x0 until x1) {
                val c=band[row*source.width+col];a+=c ushr 24;r+=c ushr 16 and 255;g+=c ushr 8 and 255;b+=c and 255
            }
            val n=(x1-x0)*(y1-y0)
            pixels[y*width+x]=((a/n) shl 24) or ((r/n) shl 16) or ((g/n) shl 8) or (b/n)
        }
    }
    val radius=(radiusPx/factor-.5f).roundToInt().coerceIn(1,12)
    fun pass(horizontal:Boolean) {
        val output=IntArray(pixels.size)
        val outer=if(horizontal) height else width;val inner=if(horizontal) width else height
        fun index(row:Int,col:Int)=if(horizontal) row*width+col else col*width+row
        for(row in 0 until outer) {
            var a=0;var r=0;var g=0;var b=0
            for(offset in -radius..radius) {val c=pixels[index(row,offset.coerceIn(0,inner-1))];a+=c ushr 24;r+=c ushr 16 and 255;g+=c ushr 8 and 255;b+=c and 255}
            val n=radius*2+1
            for(col in 0 until inner) {
                output[index(row,col)]=((a/n) shl 24) or ((r/n) shl 16) or ((g/n) shl 8) or (b/n)
                val old=pixels[index(row,(col-radius).coerceIn(0,inner-1))];val next=pixels[index(row,(col+radius+1).coerceIn(0,inner-1))]
                a+=(next ushr 24)-(old ushr 24);r+=(next ushr 16 and 255)-(old ushr 16 and 255)
                g+=(next ushr 8 and 255)-(old ushr 8 and 255);b+=(next and 255)-(old and 255)
            }
        }
        pixels=output
    }
    repeat(3) {pass(true);pass(false)}
    return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
}
