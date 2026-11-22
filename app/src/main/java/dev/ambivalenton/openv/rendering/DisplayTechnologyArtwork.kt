// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import dev.ambivalenton.openv.data.model.BoardDisplayTechnology
import kotlin.math.*

/*
 * Physical display technologies of the adaptive departure board. Every cell of a dot display
 * becomes a diode, a flip disc or a plasma cell, lit or unlit – like a real module. The text
 * comes from the hand-set [MatrixFont]. Masks are cached and bounded; nothing animates or downloads.
 */

/** Lit cells of [text] on a module of [rows] rows (the 9-row matrix font, centred), at most [cols] columns. */
private class DotMask(val cols:Int,val rows:Int,val lit:BooleanArray,val used:Int)

private object DotMasks {
    private val cache=object:LruCache<String,DotMask>(256) {}
    @Synchronized fun get(text:String,cols:Int,rows:Int):DotMask {
        val key="$text|$cols|$rows"
        cache.get(key)?.let {return it}
        // The hand-set matrix font instead of a scaled-down typeface (which turned to noise).
        val shown=MatrixFont.fit(text,cols)
        val (width,bits)=MatrixFont.mask(shown)
        val pad=((rows-MatrixFont.ROWS)/2).coerceAtLeast(0)
        val lit=BooleanArray(cols*rows)
        for(row in 0 until MatrixFont.ROWS) for(col in 0 until min(width,cols)) {
            val r=row+pad;if(r<rows && bits[row*width+col]) lit[r*cols+col]=true
        }
        return DotMask(cols,rows,lit,if(shown.isEmpty()) 0 else min(width,cols)).also {cache.put(key,it)}
    }
}

/** True for technologies whose text is built from dots. */
internal val BoardDisplayTechnology.dotText get()=this in setOf(BoardDisplayTechnology.LED_MATRIX,
    BoardDisplayTechnology.FLIP_DOT,BoardDisplayTechnology.PLASMA,BoardDisplayTechnology.SEGMENT_LED)

/**
 * Text as a dot module between x and x+[width], vertically centred on [cy], fitted into [height].
 * [size] is the nominal text size the module replaces; the dot pitch follows from it (7 dots of cap
 * height). Lit LEDs have a soft halo, a coloured lens and a bright core; unlit LEDs are
 * dark lenses with a faint rim; flip discs show their painted face and the shadow of the pivot;
 * plasma cells glow square with a thin dark grid.
 */
internal fun drawDotText(c:Canvas,technology:BoardDisplayTechnology,value:String,x:Float,cy:Float,width:Float,size:Float,height:Float,
    color:Int,face:Typeface,center:Boolean) {
    val rows=if(technology==BoardDisplayTechnology.PLASMA) 11 else MatrixFont.ROWS
    val pitch=min(size*1.18f/MatrixFont.CAP,height/rows).coerceAtLeast(.9f)
    val cols=floor(width/pitch).toInt().coerceIn(1,360)
    val mask=DotMasks.get(value,cols,rows)
    val shownCols=if(center)mask.used else cols
    val left=if(center)x+(width-mask.used*pitch)/2f else x
    val top=cy-rows*pitch/2f
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    fun lit(row:Int,col:Int)=mask.lit[row*mask.cols+col]
    when(technology) {
        BoardDisplayTechnology.FLIP_DOT->{
            val back=0xff1d1f1b.toInt()
            for(row in 0 until rows) for(col in 0 until shownCols) {
                val cx=left+(col+.5f)*pitch;val y=top+(row+.5f)*pitch;val r=pitch*.44f
                // Socket, then the disc: painted face (lit) or black back, each with its pivot shadow.
                p.color=0xff0c0d0b.toInt();c.drawCircle(cx,y,r*1.06f,p)
                val on=lit(row,col)
                p.color=if(on)color else back;c.drawCircle(cx,y,r,p)
                p.color=ColorUtils.setAlphaComponent(Color.BLACK,if(on)60 else 40);c.drawRect(cx-r,y-pitch*.04f,cx+r,y+pitch*.04f,p)
                if(on){p.color=ColorUtils.setAlphaComponent(Color.WHITE,80);c.drawCircle(cx-r*.32f,y-r*.34f,r*.26f,p)}
            }
        }
        BoardDisplayTechnology.PLASMA->{
            p.color=ColorUtils.setAlphaComponent(color,12)
            for(row in 0 until rows) for(col in 0 until shownCols) {
                val cx=left+(col+.5f)*pitch;val y=top+(row+.5f)*pitch
                c.drawRect(cx-pitch*.42f,y-pitch*.42f,cx+pitch*.42f,y+pitch*.42f,p)
            }
            p.color=ColorUtils.setAlphaComponent(color,90);p.maskFilter=BlurMaskFilter(pitch*.9f,BlurMaskFilter.Blur.NORMAL)
            for(row in 0 until rows) for(col in 0 until shownCols) if(lit(row,col)) {
                val cx=left+(col+.5f)*pitch;val y=top+(row+.5f)*pitch
                c.drawRect(cx-pitch*.5f,y-pitch*.5f,cx+pitch*.5f,y+pitch*.5f,p)
            }
            p.maskFilter=null
            val core=ColorUtils.blendARGB(color,Color.WHITE,.25f)
            for(row in 0 until rows) for(col in 0 until shownCols) if(lit(row,col)) {
                val cx=left+(col+.5f)*pitch;val y=top+(row+.5f)*pitch
                p.color=color;c.drawRect(cx-pitch*.42f,y-pitch*.42f,cx+pitch*.42f,y+pitch*.42f,p)
                p.color=core;c.drawRect(cx-pitch*.24f,y-pitch*.24f,cx+pitch*.24f,y+pitch*.24f,p)
            }
        }
        else->{
            // Unlit lenses across the whole field the text may use: the module reads as hardware.
            val dark=ColorUtils.blendARGB(color,Color.BLACK,.86f)
            for(row in 0 until rows) for(col in 0 until shownCols) {
                val cx=left+(col+.5f)*pitch;val y=top+(row+.5f)*pitch
                p.color=dark;c.drawCircle(cx,y,pitch*.36f,p)
                p.color=ColorUtils.setAlphaComponent(Color.WHITE,10);c.drawCircle(cx-pitch*.1f,y-pitch*.1f,pitch*.12f,p)
            }
            // Halo of the lit LEDs in one blurred pass, then lens and core.
            p.color=ColorUtils.setAlphaComponent(color,110);p.maskFilter=BlurMaskFilter(pitch*.75f,BlurMaskFilter.Blur.NORMAL)
            for(row in 0 until rows) for(col in 0 until shownCols) if(lit(row,col))
                c.drawCircle(left+(col+.5f)*pitch,top+(row+.5f)*pitch,pitch*.5f,p)
            p.maskFilter=null
            val core=ColorUtils.blendARGB(color,Color.WHITE,.45f)
            for(row in 0 until rows) for(col in 0 until shownCols) if(lit(row,col)) {
                val cx=left+(col+.5f)*pitch;val y=top+(row+.5f)*pitch
                p.color=color;c.drawCircle(cx,y,pitch*.38f,p)
                p.color=core;c.drawCircle(cx-pitch*.05f,y-pitch*.05f,pitch*.17f,p)
            }
        }
    }
}

/**
 * Split-flap text: one module per character. Like a real Solari module – a charcoal flap
 * with rounded corners in a black frame, the upper half catching the light and the lower half in
 * shade, a dark gap at the fold with the two hinge pins, and white condensed type whose lower half
 * sits a touch darker on the lower flap. The cell keeps the original width rule, so layouts stay.
 */
internal fun drawFlapText(c:Canvas,value:String,x:Float,cy:Float,width:Float,size:Float,height:Float,color:Int,background:Int,face:Typeface,bold:Boolean) {
    val upper=value.uppercase()
    // A field that is a little too narrow (a time, a line) gets slightly smaller modules before anything is cut.
    val h=min(size*1.32f,height).let {full->if(upper.isNotEmpty() && upper.length*full*.66f>width) max(full*.72f,width/(upper.length*.66f)) else full}
    val cell=h*.66f
    val count=floor(width/cell+.01f).toInt().coerceIn(1,80)
    val text=upper.let {if(it.length>count) it.take((count-1).coerceAtLeast(1))+"…" else it}
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface=Typeface.create(face,if(bold)Typeface.BOLD else Typeface.NORMAL);this.color=color;textAlign=Paint.Align.CENTER
        textSize=h*.74f
    }
    val base=ColorUtils.blendARGB(background,Color.WHITE,.10f)
    val upperTop=ColorUtils.blendARGB(base,Color.WHITE,.07f);val upperBottom=base
    val lowerTop=ColorUtils.blendARGB(base,Color.BLACK,.18f);val lowerBottom=ColorUtils.blendARGB(base,Color.BLACK,.08f)
    val top=cy-h/2f;val gap=max(.6f,h*.035f);val r=h*.09f
    val baseline=cy-(tp.fontMetrics.ascent+tp.fontMetrics.descent)/2f
    text.forEachIndexed {i,ch->
        val l=x+i*cell+.45f;val rgt=x+(i+1)*cell-.45f
        // Frame shadow below the module.
        p.shader=null;p.color=ColorUtils.setAlphaComponent(Color.BLACK,110);c.drawRoundRect(l,top+gap*.8f,rgt,top+h+gap*.8f,r,r,p)
        // Upper and lower flap with their own light.
        c.save();c.clipRect(l,top,rgt,cy)
        p.shader=LinearGradient(0f,top,0f,cy,upperTop,upperBottom,Shader.TileMode.CLAMP);c.drawRoundRect(l,top,rgt,top+h,r,r,p);c.restore()
        c.save();c.clipRect(l,cy,rgt,top+h)
        p.shader=LinearGradient(0f,cy,0f,top+h,lowerTop,lowerBottom,Shader.TileMode.CLAMP);c.drawRoundRect(l,top,rgt,top+h,r,r,p);c.restore()
        p.shader=null
        if(!ch.isWhitespace()) {
            val fit=min(1f,(cell*.86f)/tp.measureText(ch.toString()).coerceAtLeast(1f))
            tp.textScaleX=fit
            c.save();c.clipRect(l,top,rgt,cy);tp.color=color;c.drawText(ch.toString(),(l+rgt)/2f,baseline,tp);c.restore()
            c.save();c.clipRect(l,cy,rgt,top+h);tp.color=ColorUtils.blendARGB(color,Color.BLACK,.14f);c.drawText(ch.toString(),(l+rgt)/2f,baseline,tp);c.restore()
            tp.textScaleX=1f
        }
        // The fold: a dark gap, a thin highlight on the lower flap's edge, and the hinge pins.
        p.color=ColorUtils.blendARGB(background,Color.BLACK,.55f);c.drawRect(l,cy-gap/2f,rgt,cy+gap/2f,p)
        p.color=ColorUtils.setAlphaComponent(Color.WHITE,22);c.drawRect(l+r*.5f,cy+gap/2f,rgt-r*.5f,cy+gap/2f+max(.35f,gap*.35f),p)
        p.color=ColorUtils.blendARGB(background,Color.WHITE,.30f)
        c.drawRect(l-.2f,cy-gap*.9f,l+gap*.9f,cy+gap*.9f,p);c.drawRect(rgt-gap*.9f,cy-gap*.9f,rgt+.2f,cy+gap*.9f,p)
    }
}

/** Nixie tubes for a clock time: dark glass, the unlit digit stack and one glowing numeral. */
internal fun drawNixieTime(c:Canvas,value:String,x:Float,cy:Float,height:Float,color:Int) {
    val tube=height*.62f;val gap=height*.08f
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface=Typeface.create(Typeface.SERIF,Typeface.NORMAL);textAlign=Paint.Align.CENTER;textSize=height*.78f
        style=Paint.Style.STROKE;strokeWidth=max(.8f,height*.045f)
    }
    var xx=x;val top=cy-height/2f
    value.forEach {ch->
        if(ch==':') {
            p.color=color;p.setShadowLayer(height*.08f,0f,0f,color)
            c.drawCircle(xx+gap,cy-height*.14f,height*.035f,p);c.drawCircle(xx+gap,cy+height*.14f,height*.035f,p)
            p.clearShadowLayer();xx+=gap*2;return@forEach
        }
        // Glass envelope and mesh.
        p.color=0x28ffd9b0;c.drawRoundRect(xx,top,xx+tube,top+height,tube*.45f,tube*.45f,p)
        p.color=0x14ffb070;p.strokeWidth=.5f
        var mx=xx+tube*.2f;while(mx<xx+tube*.85f){c.drawLine(mx,top+height*.12f,mx,top+height*.88f,p);mx+=max(1.5f,tube*.14f)}
        val baseline=cy-(tp.fontMetrics.ascent+tp.fontMetrics.descent)/2f
        // The unlit cathodes behind.
        tp.color=0x1fff9a4a;tp.clearShadowLayer()
        c.drawText("8",xx+tube/2f,baseline,tp)
        tp.color=color;tp.setShadowLayer(height*.12f,0f,0f,color)
        c.drawText(ch.toString(),xx+tube/2f,baseline,tp)
        tp.clearShadowLayer()
        xx+=tube+gap
    }
}

/** Width the nixie row needs for [value] at [height]. */
internal fun nixieWidth(value:String,height:Float)=value.sumOf {if(it==':')(height*.16f).toDouble() else (height*.70f).toDouble()}.toFloat()

/** Static glass/paper materials of the build 81 technologies; false = not one of them. */
internal fun drawTechnologyGlass(c:Canvas,technology:BoardDisplayTechnology,w:Float,h:Float,bg:Int,signal:Int):Boolean {
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    when(technology) {
        BoardDisplayTechnology.LED_MATRIX,BoardDisplayTechnology.FLIP_DOT->{
            p.shader=LinearGradient(0f,0f,0f,h,ColorUtils.blendARGB(bg,Color.WHITE,.03f),ColorUtils.blendARGB(bg,Color.BLACK,.25f),Shader.TileMode.CLAMP)
            c.drawRect(0f,0f,w,h,p)
        }
        BoardDisplayTechnology.SPLIT_FLAP->{
            p.color=bg;c.drawRect(0f,0f,w,h,p)
        }
        BoardDisplayTechnology.SEGMENT_LED->{
            p.shader=LinearGradient(0f,0f,w*.3f,h,ColorUtils.blendARGB(bg,signal,.06f),bg,Shader.TileMode.CLAMP)
            c.drawRect(0f,0f,w,h,p)
        }
        BoardDisplayTechnology.E_INK->{
            p.shader=LinearGradient(0f,0f,w,h,ColorUtils.blendARGB(bg,Color.WHITE,.25f),ColorUtils.blendARGB(bg,Color.BLACK,.05f),Shader.TileMode.CLAMP)
            c.drawRect(0f,0f,w,h,p);p.shader=null
            // Fixed paper grain: a deterministic sprinkle, bounded.
            p.color=0x0f000000
            var i=0;while(i<900){val gx=((i*7919)%997)/997f*w;val gy=((i*104729)%991)/991f*h;c.drawRect(gx,gy,gx+.6f,gy+.6f,p);i++}
        }
        BoardDisplayTechnology.NIXIE->{
            p.shader=RadialGradient(w*.5f,h*.45f,max(w,h)*.7f,intArrayOf(ColorUtils.blendARGB(bg,signal,.08f),bg,Color.BLACK),
                floatArrayOf(0f,.6f,1f),Shader.TileMode.CLAMP)
            c.drawRect(0f,0f,w,h,p)
        }
        BoardDisplayTechnology.PLASMA->{
            p.shader=LinearGradient(0f,0f,0f,h,ColorUtils.blendARGB(bg,signal,.07f),bg,Shader.TileMode.CLAMP)
            c.drawRect(0f,0f,w,h,p);p.shader=null
            p.color=0x10000000;var y=1f;while(y<h){c.drawRect(0f,y,w,y+.5f,p);y+=2f}
        }
        else->return false
    }
    p.shader=null
    return true
}
