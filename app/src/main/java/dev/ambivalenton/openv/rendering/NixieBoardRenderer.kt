// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import kotlin.math.*

data class NixieClockLayout(val tubes:List<RectF>,val colonX:Float)
fun nixieClockLayout(left:Float,top:Float,width:Float,height:Float):NixieClockLayout {
    val gap=maxOf(1f,height*.055f);val colon=maxOf(3f,height*.18f)
    val cell=((width-2*gap-colon)/4).coerceAtLeast(1f)
    val starts=listOf(left,left+cell+gap,left+2*cell+gap+colon,left+3*cell+2*gap+colon)
    return NixieClockLayout(starts.map {RectF(it,top,it+cell,top+height)},left+2*cell+gap+colon/2)
}

/** Four cathodes, one measured digit per tube. The colon never consumes a digit cell.
 * Text and glow are clipped to their tube; narrow widgets retain one clear information baseline. */
fun drawNixieDepartureRow(canvas:Canvas,row:Departure,left:Float,top:Float,width:Float,height:Float,
    colors:BoardColors,fontScale:Float,compact:Boolean,customTypeface:Typeface?=null) {
    val ink=colors.foreground.toInt();val accent=colors.accent.toInt();val bg=colors.background.toInt()
    fun alpha(c:Int,a:Int)=(c and 0xffffff) or (a.coerceIn(0,255) shl 24)
    val clockWidth=minOf(maxOf(68f,height*1.8f),width*.42f)
    val tubeHeight=(height-6).coerceIn(8f,48f*fontScale.coerceIn(1f,2f))
    val geometry=nixieClockLayout(left,top+(height-tubeHeight)/2,clockWidth,tubeHeight)
    val digits=clockTime(row.time).filter {it.isDigit()}.takeLast(4).padStart(4,'0')
    val fill=Paint(Paint.ANTI_ALIAS_FLAG);val text=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {typeface=customTypeface?:Typeface.MONOSPACE;textAlign=Paint.Align.CENTER}
    geometry.tubes.forEachIndexed {i,r->
        canvas.save();canvas.clipRect(r)
        val bright=Color.red(ink)*.2126+Color.green(ink)*.7152+Color.blue(ink)*.0722>135
        fill.color=if(bright) Color.rgb(Color.red(bg)/2,Color.green(bg)/2,Color.blue(bg)/2) else Color.rgb(246,242,234)
        fill.style=Paint.Style.FILL;canvas.drawRoundRect(r,4f,4f,fill)
        fill.style=Paint.Style.STROKE;fill.strokeWidth=.8f;fill.color=alpha(ink,65)
        canvas.drawRoundRect(RectF(r).apply {inset(.6f,.6f)},4f,4f,fill)
        fill.style=Paint.Style.FILL
        text.textSize=1f;val unit=text.measureText("8").coerceAtLeast(.1f)
        text.textSize=minOf(tubeHeight*.7f,(r.width()-3).coerceAtLeast(2f)/unit)
        val baseline=r.centerY()-(text.fontMetrics.ascent+text.fontMetrics.descent)/2
        text.color=alpha(ink,15);canvas.drawText("8",r.centerX(),baseline,text)
        text.color=alpha(ink,if(row.delay.cancelled) 90 else 55);text.style=Paint.Style.STROKE;text.strokeWidth=2f
        canvas.drawText(digits[i].toString(),r.centerX(),baseline,text)
        text.style=Paint.Style.FILL;text.color=alpha(ink,if(row.delay.cancelled) 150 else 255)
        canvas.drawText(digits[i].toString(),r.centerX(),baseline,text)
        fill.color=alpha(Color.WHITE,24);canvas.drawRoundRect(r.left+1.5f,r.top+2,r.left+2.8f,r.bottom-3,1f,1f,fill)
        canvas.restore()
    }
    fill.color=ink
    val centre=top+height/2;val dot=maxOf(.8f,tubeHeight*.027f)
    canvas.drawCircle(geometry.colonX,centre-tubeHeight*.12f,dot,fill);canvas.drawCircle(geometry.colonX,centre+tubeHeight*.12f,dot,fill)
    val x=left+clockWidth+8f;val available=(left+width-x).coerceAtLeast(1f)
    fun label(value:String,cy:Float,size:Float,color:Int,maxHeight:Float,bold:Boolean) {
        val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {this.color=color;textSize=size;typeface=Typeface.create(customTypeface?:Typeface.create("sans-serif-condensed",Typeface.NORMAL),if(bold) Typeface.BOLD else Typeface.NORMAL)}
        val fh=p.fontMetrics.descent-p.fontMetrics.ascent
        if(fh>maxHeight) p.textSize*=maxHeight/fh
        val fitted=TextUtils.ellipsize(value,p,available,TextUtils.TruncateAt.END).toString()
        canvas.save();canvas.clipRect(x,top,left+width,top+height)
        canvas.drawText(fitted,x,cy-(p.fontMetrics.ascent+p.fontMetrics.descent)/2,p);canvas.restore()
    }
    val status=if(row.delay.cancelled) tr("Fällt aus") else row.delay.minutes?.takeIf {it>0}?.let {"+$it"}.orEmpty()
    // Operational status precedes the ellipsized destination, including in small widgets.
    if(compact) label(listOf(row.lineName,status,row.destination).filter {it.isNotBlank()}.joinToString(" · "),centre,11*fontScale,accent,height-3,true)
    else {
        label(listOf(row.lineName,status).filter {it.isNotBlank()}.joinToString(" · "),top+height*.31f,12*fontScale,accent,height*.4f,true)
        label(row.destination,top+height*.73f,10*fontScale,ink,height*.4f,false)
    }
}
