// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.repository.CONNECTION_EDGE_PREFETCH_FRACTION

internal data class ChartEdgeProbe(val firstVisible:Int,val lastVisible:Int,val total:Int,val position:Float)

/** true = load later connections, false = earlier ones, null = not yet near an edge.
 * [direction] is the user's last horizontal scroll direction (+1 later, -1 earlier). */
internal fun chartEdgeRequest(firstVisible:Int,lastVisible:Int,total:Int,direction:Int,
    fraction:Float=CONNECTION_EDGE_PREFETCH_FRACTION):Boolean? {
    if(total<=0 || lastVisible<0 || firstVisible<0)return null
    // A small tolerance keeps exact fractions such as 12/15 = 0.8 stable against float rounding.
    val later=(lastVisible+1).toDouble()/total
    val earlier=firstVisible.toDouble()/total
    return when {
        direction>0 && later>=fraction-1e-4->true
        direction<0 && earlier<=1.0-fraction+1e-4->false
        else->null
    }
}
