// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import kotlin.math.*

/** All variants consume the same real departures and the same small-widget layout contract. */
fun drawAdditionalBoardDesign(canvas:Canvas,model:BoardPresentation,w:Float,h:Float,
    layout:BoardLayout,fontScale:Float,headerAction:Boolean):Boolean {
    if(model.design !in setOf(BoardDesign.NIXIE,BoardDesign.WAYFINDER,BoardDesign.TRAM_ROLLSIGN,
        BoardDesign.PRINTED_SCHEDULE,BoardDesign.DEPARTURE_CARDS,BoardDesign.RADAR)) return false
    val p=layout.padding;val ink=model.colors.foreground.toInt();val accent=model.colors.accent.toInt()
    val bg=model.colors.background.toInt();val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    val scale=if(layout.singleLine) minOf(1.1f,layout.rowHeight/20f) else fontScale.coerceIn(1f,2f)
    val face=model.typeface ?: when(model.design) {
        BoardDesign.PRINTED_SCHEDULE->Typeface.SERIF
        BoardDesign.NIXIE->Typeface.MONOSPACE
        BoardDesign.TRAM_ROLLSIGN->Typeface.create("sans-serif-condensed",Typeface.BOLD)
        else->Typeface.SANS_SERIF
    }
    fun alpha(c:Int,a:Int)=(c and 0xffffff) or (a.coerceIn(0,255) shl 24)
    fun text(value:String,x:Float,y:Float,size:Float,color:Int=ink,maxWidth:Float=w-p-x,bold:Boolean=false,fit:Boolean=false) {
        val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color=color;textSize=size.coerceAtLeast(5f);typeface=Typeface.create(face,if(bold) Typeface.BOLD else Typeface.NORMAL)
        }
        if(fit && tp.measureText(value)>maxWidth.coerceAtLeast(1f)) tp.textSize*=maxWidth.coerceAtLeast(1f)/tp.measureText(value)
        val fitted=TextUtils.ellipsize(value,tp,maxWidth.coerceAtLeast(1f),TextUtils.TruncateAt.END).toString()
        canvas.drawText(fitted,x,y,tp)
    }
    if(model.design==BoardDesign.RADAR) {
        paint.color=alpha(accent,24);paint.style=Paint.Style.STROKE;paint.strokeWidth=.8f
        val cx=w*.8f;val cy=h*.6f
        repeat(5) {canvas.drawCircle(cx,cy,w*(it+1)*.14f,paint)}
        canvas.drawLine(0f,cy,w,cy,paint);canvas.drawLine(cx,0f,cx,h,paint);paint.style=Paint.Style.FILL
    }
    if(!model.isDesignPreview) text(model.title,p,p+17*scale,15*scale,accent,w-2*p-if(headerAction) 30f else 0f,true)
    var top=p+layout.header
    val visible=model.rows.take(layout.rows)
    if(visible.isEmpty()) text(model.emptyMessage,p,(top+15*scale).coerceAtMost(h-p),12*scale)
    visible.forEachIndexed {index,row->
        val rh=layout.rowHeight-layout.journeyLinkHeight;val baseline=top+rh*.61f
        val clock=if(row.delay.cancelled) tr("Fällt aus") else clockTime(row.time)
        val line=row.lineName;val destination=row.destination
        val late=if(row.delay.cancelled) "" else row.delay.minutes?.takeIf {it>0}?.let {" +$it"}.orEmpty()
        // Compact widgets reserve one baseline per departure, matching BoardLayout.singleLine.
        // Decorative elements remain, while line/destination/time can no longer collide vertically.
        if(layout.singleLine && !model.isDesignPreview && model.design!=BoardDesign.NIXIE) {
            var left=p+3
            val clockWidth=minOf(88f*scale,w*.34f)
            val right=w-p-clockWidth
            when(model.design) {
                BoardDesign.WAYFINDER-> {
                    paint.color=alpha(accent,90)
                    canvas.drawPath(Path().apply {moveTo(p,top+rh*.25f);lineTo(p+rh*.25f,top+rh*.25f)
                        lineTo(p+rh*.45f,top+rh*.5f);lineTo(p+rh*.25f,top+rh*.75f);lineTo(p,top+rh*.75f);close()},paint)
                    left=p+rh*.55f
                }
                BoardDesign.TRAM_ROLLSIGN-> {
                    paint.color=alpha(ink,16);canvas.drawRect(p-2,top+1,w-p+2,top+rh-2,paint)
                }
                BoardDesign.PRINTED_SCHEDULE-> {
                    paint.color=alpha(ink,45);paint.strokeWidth=.6f
                    canvas.drawLine(p,top+rh,w-p,top+rh,paint)
                }
                BoardDesign.DEPARTURE_CARDS-> {
                    paint.color=alpha(ink,if(index%2==0) 16 else 28)
                    canvas.drawRoundRect(p-2,top+2,w-p+2,top+rh-3,4f,4f,paint)
                    paint.color=alpha(accent,36);canvas.drawRect(right-3,top+2,w-p+2,top+rh-3,paint)
                }
                BoardDesign.RADAR-> {
                    paint.color=accent;paint.style=Paint.Style.STROKE;paint.strokeWidth=1f
                    canvas.drawCircle(p+5,top+rh*.5f,4f,paint);paint.style=Paint.Style.FILL;left=p+14
                }
                else->Unit
            }
            text("$line  $destination",left,baseline,11*scale,ink,(right-left-7).coerceAtLeast(1f),true)
            text(clock+late,right+2,baseline,11*scale,accent,clockWidth-2,true,fit=true)
            top+=layout.rowHeight
            return@forEachIndexed
        }
        when(model.design) {
            BoardDesign.NIXIE-> {
                drawNixieDepartureRow(canvas,row,p,top,w-2*p,rh,model.colors,scale,layout.singleLine,model.typeface)
            }
            BoardDesign.WAYFINDER-> {
                val arrow=Path().apply {moveTo(p,top+rh*.2f);lineTo(p+rh*.35f,top+rh*.2f);lineTo(p+rh*.58f,top+rh*.5f)
                    lineTo(p+rh*.35f,top+rh*.8f);lineTo(p,top+rh*.8f);close()}
                paint.color=alpha(accent,90);canvas.drawPath(arrow,paint)
                val x=p+rh*.7f
                text(line,x,top+rh*.42f,12*scale,ink,w*.3f,true)
                text(clock+late,w-p-minOf(86f*scale,w*.34f),top+rh*.42f,13*scale,accent,minOf(86f*scale,w*.34f),true,fit=true)
                text(destination,x,top+rh*.82f,12*scale,ink,bold=true)
            }
            BoardDesign.TRAM_ROLLSIGN-> {
                paint.color=alpha(ink,16);canvas.drawRect(p-3,top+1,w-p+3,top+rh-2,paint)
                val box=minOf(maxOf(rh*.9f,50f*scale),w*.24f);paint.color=ink;canvas.drawRect(p,top+4,p+box,top+rh-5,paint)
                text(line,p+3,baseline,13*scale,bg,box-6,true)
                text(destination.uppercase(),p+box+8,top+rh*.45f,12*scale,ink,bold=true)
                text(clock+late,p+box+8,top+rh*.83f,11*scale,accent,fit=true)
                paint.color=alpha(ink,25);paint.strokeWidth=.5f;canvas.drawLine(p,top+rh-1,w-p,top+rh-1,paint)
            }
            BoardDesign.PRINTED_SCHEDULE-> {
                val x1=p+w*.2f;val x2=p+w*.37f
                paint.color=alpha(ink,45);paint.strokeWidth=.6f
                canvas.drawLine(p,top+rh,w-p,top+rh,paint)
                canvas.drawLine(x1,top,x1,top+rh,paint);canvas.drawLine(x2,top,x2,top+rh,paint)
                text(clock,p+1,baseline,12*scale,ink,w*.18f,true,fit=true)
                text(line,x1+5,baseline,11*scale,accent,w*.15f,true)
                text(destination,x2+5,top+rh*.46f,12*scale,ink,bold=true)
                if(!layout.singleLine) text(listOfNotNull(platformName(row.platform,row.lineType),late.trim().takeIf {it.isNotEmpty()}).joinToString(" · "),x2+5,top+rh*.83f,9*scale)
            }
            BoardDesign.DEPARTURE_CARDS-> {
                paint.color=alpha(ink,if(index%2==0) 16 else 28)
                canvas.drawRoundRect(p-2,top+2,w-p+2,top+rh-3,7f,7f,paint)
                val cw=minOf(w*.35f,84f*scale);paint.color=alpha(accent,36)
                canvas.drawPath(Path().apply {moveTo(w-p-cw,top+2);lineTo(w-p+2,top+2);lineTo(w-p+2,top+rh-3);lineTo(w-p-cw+8,top+rh-3);lineTo(w-p-cw,top+rh-11);close()},paint)
                text(line,p+6,top+rh*.42f,11*scale,accent,w*.5f,true)
                text(destination,p+6,top+rh*.82f,12*scale,ink,w-2*p-cw-12)
                text(clock+late,w-p-cw+5,baseline,13*scale,ink,cw-9,true,fit=true)
            }
            BoardDesign.RADAR-> {
                val radius=minOf(9f*scale,rh*.24f);val x=p+radius
                paint.color=accent;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.4f;canvas.drawCircle(x,top+rh*.5f,radius,paint)
                canvas.drawArc(x-radius,top+rh*.5f-radius,x+radius,top+rh*.5f+radius,-90f,220f+index*15f,false,paint)
                paint.style=Paint.Style.FILL
                val start=x+radius+7;val tw=minOf(86f*scale,w*.34f);val tx=w-p-tw
                text(line,start,top+rh*.43f,12*scale,accent,(tx-start-7).coerceAtLeast(1f),true)
                text(clock+late,tx,top+rh*.43f,12*scale,accent,tw,true,fit=true)
                text(destination,start,top+rh*.84f,12*scale)
            }
            else->Unit
        }
        top+=layout.rowHeight
    }
    if(!model.isDesignPreview && layout.footer>0) text(model.caption,p,h-p,8*scale,accent)
    return true
}
