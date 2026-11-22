// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import kotlin.math.floor

internal data class UniformTimelineDots(val radius:Float,val centers:List<Float>)

/** One uninterrupted rhythm per connector; all circles fit completely inside even a short row. */
internal fun uniformTimelineDots(length:Float,radius:Float,spacing:Float):UniformTimelineDots {
    if(!length.isFinite() || !radius.isFinite() || !spacing.isFinite() || length<=0 || radius<=0 || spacing<=0)
        return UniformTimelineDots(0f,emptyList())
    val r=minOf(radius,length/2)
    val step=maxOf(spacing,2*r+.5f)
    val count=(floor(((length-2*r)/step).coerceAtLeast(0f)).toInt()+1).coerceAtMost(2048)
    val start=(length-(count-1)*step)/2
    return UniformTimelineDots(r,List(count){start+it*step})
}
