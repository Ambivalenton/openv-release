// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.theme.TimelineInk
import dev.ambivalenton.openv.ui.theme.FullThemeBackdrop

fun TripSegment.timelineDelay()=delay.copy(departureSeconds=maxOf(delay.departureSeconds ?: 0,delay.arrivalSeconds ?: 0,
    ((startTime-plannedStartTime)/1000).toInt(),((endTime-plannedEndTime)/1000).toInt()))
/** Delay ink is composited into the actual theme artwork's alpha, including dots, rails and braids.
 * No independent stripe is placed beside a segment. Pattern remains meaningful without colour. */
fun DrawScope.drawTimelineBarWithDelay(style:TimelineBarStyle,ink:TimelineInk,delay:DelayInfo?,warning:Color,appArtwork:AppChartArtwork?=null,backdrop:FullThemeBackdrop?=null,
    notice:Color?=null) {
    val delayed=delay!=null && (delay.cancelled || (delay.minutes ?: 0)>0)
    if(!delayed && notice==null) {drawTimelineBar(style,ink,appArtwork,backdrop);return}
    drawIntoCanvas {canvas->
        canvas.saveLayer(Rect(Offset.Zero,size),Paint())
        drawTimelineBar(style,ink,appArtwork,backdrop)
        val spacing=7.dp.toPx()
        if(delayed) {
            val band=if(delay!!.cancelled) size.height else minOf(9.dp.toPx(),(size.height*.24f).coerceAtLeast(3.dp.toPx()))
            clipRect(top=(size.height-band).coerceAtLeast(0f)) {
                drawRect(warning.copy(alpha=.30f),blendMode=BlendMode.SrcAtop)
                var x=-band
                while(x<size.width+band) {
                    drawLine(warning,Offset(x,size.height),Offset(x+band,size.height-band),1.6.dp.toPx(),blendMode=BlendMode.SrcAtop)
                    if(delay.cancelled) drawLine(warning,Offset(x,size.height-band),Offset(x+band,size.height),1.6.dp.toPx(),blendMode=BlendMode.SrcAtop)
                    x+=spacing
                }
            }
        }
        // A timetable notice at a boarding or alighting stop: the same hatched band as a
        // delay, inside the bar at its upper edge, in its own colour and with stripes the other way.
        if(notice!=null) {
            val band=minOf(9.dp.toPx(),(size.height*.24f).coerceAtLeast(3.dp.toPx()))
            clipRect(bottom=band) {
                drawRect(notice.copy(alpha=.30f),blendMode=BlendMode.SrcAtop)
                var x=-band
                while(x<size.width+band) {
                    drawLine(notice,Offset(x,0f),Offset(x+band,band),1.6.dp.toPx(),blendMode=BlendMode.SrcAtop)
                    x+=spacing
                }
            }
        }
        canvas.restore()
    }
}
