// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import kotlin.math.*

/** Static, deterministic Canvas artwork is shared by the app, previews and Android widgets.
 * Decoration never participates in measuring live data. Every row reserves its status column. */
val creativeBoardDesigns = setOf(BoardDesign.COMIC, BoardDesign.AIRPORT_GATE, BoardDesign.PUNCH_CARD,
    BoardDesign.VINYL, BoardDesign.PIXEL_QUEST, BoardDesign.CHALKBOARD, BoardDesign.CLOCKWORK, BoardDesign.PAPER_DIORAMA)

fun drawCreativeBoardDesign(canvas: Canvas, model: BoardPresentation, w: Float, h: Float,
    layout: BoardLayout, fontScale: Float, headerAction: Boolean): Boolean {
    if (model.design !in creativeBoardDesigns) return false
    val p=layout.padding; val ink=model.colors.foreground.toInt(); val accent=model.colors.accent.toInt()
    val bg=model.colors.background.toInt(); val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    val compact=layout.singleLine && !model.isDesignPreview
    val scale=if(compact) minOf(1.1f,layout.rowHeight/20f) else fontScale.coerceIn(1f,2f)
    val mono=model.design in setOf(BoardDesign.PUNCH_CARD,BoardDesign.PIXEL_QUEST,BoardDesign.CLOCKWORK)
    val face=model.typeface ?: if(mono) Typeface.MONOSPACE else if(model.design==BoardDesign.CHALKBOARD) Typeface.SERIF else Typeface.SANS_SERIF
    fun alpha(c:Int,a:Int)=(c and 0xffffff) or (a.coerceIn(0,255) shl 24)
    fun fill(color:Int) {paint.color=color;paint.style=Paint.Style.FILL;paint.pathEffect=null;paint.shader=null}
    fun stroke(color:Int,width:Float=1f) {fill(color);paint.style=Paint.Style.STROKE;paint.strokeWidth=width}
    fun text(value:String,x:Float,cy:Float,size:Float,width:Float,maxHeight:Float,color:Int=ink,bold:Boolean=false,fit:Boolean=false) {
        val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color=color;textSize=size.coerceAtLeast(1f);typeface=Typeface.create(face,if(bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val fh=tp.fontMetrics.descent-tp.fontMetrics.ascent
        if(fh>maxHeight) tp.textSize*=maxHeight.coerceAtLeast(1f)/fh
        if(fit && tp.measureText(value)>width.coerceAtLeast(1f)) tp.textSize*=width.coerceAtLeast(1f)/tp.measureText(value)
        val shown=TextUtils.ellipsize(value,tp,width.coerceAtLeast(1f),TextUtils.TruncateAt.END).toString()
        canvas.save();canvas.clipRect(x,cy-maxHeight/2,x+width.coerceAtLeast(1f),cy+maxHeight/2)
        canvas.drawText(shown,x,cy-(tp.fontMetrics.ascent+tp.fontMetrics.descent)/2,tp);canvas.restore()
    }
    fun gear(cx:Float,cy:Float,r:Float,color:Int) {
        stroke(color,maxOf(.65f,r*.10f));canvas.drawCircle(cx,cy,r*.63f,paint);canvas.drawCircle(cx,cy,r*.25f,paint)
        repeat(12) {n->val a=n*PI/6
            canvas.drawLine(cx+cos(a).toFloat()*r*.66f,cy+sin(a).toFloat()*r*.66f,
                cx+cos(a).toFloat()*r,cy+sin(a).toFloat()*r,paint)}
    }
    // Low contrast textures sit behind the information, and are fixed across data refreshes.
    when(model.design) {
        BoardDesign.COMIC -> {
            fill(alpha(accent,28));for(y in 4 until h.toInt() step 9) for(x in 4 until w.toInt() step 9)
                canvas.drawCircle(x.toFloat()+if(y%18==4) 0f else 4f,y.toFloat(),.8f,paint)
        }
        BoardDesign.PUNCH_CARD -> {
            stroke(alpha(ink,28),.6f);for(y in 8 until h.toInt() step 12) canvas.drawLine(3f,y.toFloat(),w-3,y.toFloat(),paint)
        }
        BoardDesign.VINYL -> {
            stroke(alpha(accent,22),.7f);repeat(9) {canvas.drawCircle(w-8,h+4,28f+it*7,paint)}
        }
        BoardDesign.PIXEL_QUEST -> {
            fill(alpha(accent,22));repeat(24) {i->val x=((i*79+11)%maxOf(1,w.toInt())).toFloat()
                val y=((i*53+7)%maxOf(1,h.toInt())).toFloat();canvas.drawRect(x,y,x+2,y+2,paint)}
        }
        BoardDesign.CHALKBOARD -> {
            stroke(alpha(ink,24),.7f);canvas.drawRoundRect(3f,3f,w-3,h-3,12f,12f,paint)
            canvas.drawRoundRect(5f,5f,w-5,h-5,10f,10f,paint)
        }
        BoardDesign.CLOCKWORK -> {
            gear(w-10,11f,35f,alpha(accent,28));gear(w-48,h+3,25f,alpha(accent,23))
        }
        BoardDesign.PAPER_DIORAMA -> {
            repeat(3) {i->val y=h-10-i*7f
                val wave=Path().apply {moveTo(0f,h);lineTo(0f,y);cubicTo(w*.3f,y-18,w*.6f,y+13,w,y-7);lineTo(w,h);close()}
                fill(alpha(accent,12+i*8));canvas.drawPath(wave,paint)}
        }
        else->Unit
    }
    if(!model.isDesignPreview) {
        val hx=if(model.design==BoardDesign.AIRPORT_GATE) p+17 else p
        if(model.design==BoardDesign.AIRPORT_GATE) {
            stroke(accent,1.5f);val cy=p+9
            canvas.drawLine(p,cy,p+10,cy,paint);canvas.drawLine(p+6,cy-4,p+10,cy,paint);canvas.drawLine(p+6,cy+4,p+10,cy,paint)
        }
        text(model.title,hx,p+layout.header*.4f,14*scale,w-p-hx-if(headerAction) 30f else 0f,layout.header*.72f,accent,true)
    }
    var top=p+layout.header
    val visible=model.rows.take(layout.rows)
    if(visible.isEmpty()) text(model.emptyMessage,p,(top+10*scale).coerceAtMost(h-p-6),11*scale,w-2*p,20f,ink)
    visible.forEachIndexed {index,row->
        val rh=layout.rowHeight-layout.journeyLinkHeight;val gap=if(compact) 1.5f else 3f
        val rect=RectF(p,top+gap,w-p,top+rh-gap)
        canvas.save();canvas.clipRect(p-2,top,w-p+2,top+rh)
        var left=p+5f;var right=w-p-5f
        var clockWidth=minOf(96f*scale,(right-left)*.36f)
        val clock=if(row.delay.cancelled) tr("Fällt aus") else clockTime(row.time)
        val status=if(row.delay.cancelled) "" else row.delay.minutes?.takeIf {it>0}?.let {" +$it"}.orEmpty()
        when(model.design) {
            BoardDesign.COMIC -> {
                fill(alpha(ink,70));canvas.drawRoundRect(rect.left+2,rect.top+2,rect.right+2,rect.bottom+2,5f,5f,paint)
                fill(bg);canvas.drawRoundRect(rect,5f,5f,paint)
                stroke(ink,1.2f);canvas.drawRoundRect(rect,5f,5f,paint)
                val cx=right-clockWidth-2
                fill(alpha(accent,25));canvas.drawRoundRect(cx,rect.top+2,rect.right-2,rect.bottom-2,4f,4f,paint)
                if(!compact) {fill(accent);canvas.drawPath(Path().apply {moveTo(cx+8,rect.bottom-5);lineTo(cx+8,rect.bottom+1);lineTo(cx+15,rect.bottom-5);close()},paint)}
            }
            BoardDesign.AIRPORT_GATE -> {
                val badge=if(compact) 30f*scale else minOf(50f*scale,rh*.9f)
                fill(alpha(accent,22));canvas.drawRect(p,rect.top,p+badge,rect.bottom,paint)
                text(row.lineName,p+4,rect.centerY(),if(compact) 11*scale else 16*scale,badge-8,rh*.65f,accent,true,true)
                stroke(alpha(ink,38),.6f);canvas.drawLine(p,rect.bottom,w-p,rect.bottom,paint)
                left=p+badge+7
            }
            BoardDesign.PUNCH_CARD -> {
                fill(bg);canvas.drawPath(Path().apply {moveTo(rect.left+5,rect.top);lineTo(rect.right,rect.top);lineTo(rect.right,rect.bottom)
                    lineTo(rect.left,rect.bottom);lineTo(rect.left,rect.top+5);close()},paint)
                stroke(alpha(ink,60),.7f);canvas.drawRect(rect,paint)
                val holes=if(compact) 2 else 6
                repeat(holes) {n->fill(alpha(if(((row.lineName.hashCode() ushr n) and 1)==1) ink else accent,if(n%2==0) 95 else 40))
                    val x=p+5+(n%2)*5f;val y=rect.top+4+(n/2)*5f;canvas.drawRect(x,y,x+2.5f,y+3,paint)}
                left=p+18
                stroke(alpha(accent,90),.6f);canvas.drawLine(left-3,rect.top+3,left-3,rect.bottom-3,paint)
            }
            BoardDesign.VINYL -> {
                val r=minOf(if(compact) 8f*scale else 18f*scale,rh*.37f);val cx=p+r+3;val cy=rect.centerY()
                fill(alpha(ink,20));canvas.drawCircle(cx,cy,r,paint)
                stroke(alpha(ink,65),.7f);repeat(4) {canvas.drawCircle(cx,cy,r*(.45f+it*.16f),paint)}
                fill(accent);canvas.drawCircle(cx,cy,r*.32f,paint);fill(bg);canvas.drawCircle(cx,cy,1f,paint)
                stroke(alpha(accent,75),.7f);canvas.drawArc(cx-r,cy-r,cx+r,cy+r,215f,50f,false,paint)
                left=cx+r+8;stroke(alpha(ink,26),.6f);canvas.drawLine(left,rect.bottom,w-p,rect.bottom,paint)
            }
            BoardDesign.PIXEL_QUEST -> {
                val u=if(compact) 1.3f else minOf(2.3f,rh/20);val x=p+2;val y=rect.centerY()-4*u
                fill(accent)
                listOf(RectF(x+2*u,y,x+9*u,y+u),RectF(x+u,y+u,x+10*u,y+6*u),RectF(x,y+6*u,x+11*u,y+7*u),
                    RectF(x+2*u,y+7*u,x+4*u,y+9*u),RectF(x+7*u,y+7*u,x+9*u,y+9*u)).forEach {canvas.drawRect(it,paint)}
                fill(bg);canvas.drawRect(x+3*u,y+2*u,x+8*u,y+4*u,paint)
                fill(alpha(accent,65));canvas.drawRect(p,rect.bottom-1,w-p,rect.bottom,paint)
                for(xx in p.toInt() until (w-p).toInt() step 9) canvas.drawRect(xx.toFloat(),rect.bottom-2,xx+2f,rect.bottom+1,paint)
                left=x+12*u+6
            }
            BoardDesign.CHALKBOARD -> {
                stroke(alpha(ink,82),.65f);paint.pathEffect=DashPathEffect(floatArrayOf(3f,3f),index.toFloat())
                canvas.drawLine(p+3,rect.bottom,w-p-3,rect.bottom,paint);paint.pathEffect=null
                val tx=right-clockWidth-4
                val cy=if(compact) rect.centerY() else rect.top+rect.height()*.30f
                val ry=minOf(10f*scale,if(compact) rect.height()*.40f else rh*.18f)
                stroke(alpha(accent,110),.7f);canvas.drawOval(tx-3,cy-ry,rect.right,cy+ry,paint)
                canvas.drawArc(tx-4,cy-ry-1,rect.right+1,cy+ry+1,18f,120f,false,paint)
            }
            BoardDesign.CLOCKWORK -> {
                val r=minOf(if(compact) 8f else 15f*scale,rh*.33f);val cx=p+r+3
                gear(cx,rect.centerY(),r,accent);left=cx+r+7
                stroke(alpha(accent,80),.8f);canvas.drawRoundRect(left-4,rect.top,right+2,rect.bottom,3f,3f,paint)
                if(!compact) {fill(accent);canvas.drawCircle(right-1,rect.top+3,1f,paint);canvas.drawCircle(right-1,rect.bottom-3,1f,paint)}
            }
            BoardDesign.PAPER_DIORAMA -> {
                fill(alpha(ink,18));canvas.drawRoundRect(rect.left+1.5f,rect.top+2,rect.right+1.5f,rect.bottom+2,2f,2f,paint)
                fill(bg);canvas.drawRect(rect,paint);fill(alpha(accent,13+index%3*5));canvas.drawRect(rect,paint)
                fill(alpha(accent,85));canvas.drawPath(Path().apply {moveTo(rect.right-8,rect.top);lineTo(rect.right,rect.top+8);lineTo(rect.right-8,rect.top+8);close()},paint)
                fill(alpha(accent,45));canvas.drawRect(p,rect.top,p+2,rect.bottom,paint)
                right-=7
            }
            else->Unit
        }
        clockWidth=minOf(clockWidth,(right-left)*.46f).coerceAtLeast(1f)
        val clockX=right-clockWidth
        val hasLineBadge=model.design==BoardDesign.AIRPORT_GATE
        if(compact) {
            text((if(hasLineBadge) "" else row.lineName+"  ")+row.destination,left,rect.centerY(),11*scale,
                (clockX-left-6).coerceAtLeast(1f),rh-4,ink,true)
            text(clock+status,clockX,rect.centerY(),11*scale,clockWidth,rh-4,accent,true,true)
        } else {
            val first=rect.top+rect.height()*.30f;val second=rect.top+rect.height()*.76f
            text(if(hasLineBadge) platformName(row.platform,row.lineType).orEmpty() else row.lineName,
                left,first,12*scale,(clockX-left-6).coerceAtLeast(1f),rh*.36f,accent,true)
            text(clock+status,clockX,first,14*scale,clockWidth,rh*.38f,accent,true,true)
            text(row.destination,left,second,12*scale,right-left,rh*.37f,ink,model.design in setOf(BoardDesign.COMIC,BoardDesign.PIXEL_QUEST))
        }
        canvas.restore();top+=layout.rowHeight
    }
    if(!model.isDesignPreview && layout.footer>0) text(model.caption,p,h-p-layout.footer*.2f,8*scale,w-2*p,layout.footer,accent)
    return true
}
