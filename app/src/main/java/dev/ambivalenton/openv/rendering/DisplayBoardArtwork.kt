// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import kotlin.math.*

/** Emissive colours retain each theme's hue; reflective LCDs retain their dark liquid crystals. */
internal fun themedDisplayColors(theme:AppTheme,source:BoardColors,secondary:Long=theme.secondary):BoardColors {
    fun blend(a:Long,b:Long,t:Float)=(ColorUtils.blendARGB(a.toInt(),b.toInt(),t).toLong() or 0xff000000L) and 0xffffffffL
    // A light emitter keeps the hue of the theme colour but shines bright and saturated.
    fun diode(color:Long):Long {
        val hct=dev.ambivalenton.openv.color.utilities.Hct.fromInt(color.toInt())
        return dev.ambivalenton.openv.color.utilities.Hct.from(hct.hue,max(hct.chroma,58.0),78.0).toInt().toLong() and 0xffffffffL
    }
    return when(boardDisplayTechnology(theme)) {
        BoardDisplayTechnology.REFLECTIVE_LCD->BoardColors(0xff253520,blend(source.background,0xffacb6a1,.38f),0xff253520)
        BoardDisplayTechnology.VFD->BoardColors(blend(source.accent,0xffffedd0,.72f),
            blend(0xff080c0b,source.background,.065f),blend(source.accent,0xffffcf88,.64f))
        BoardDisplayTechnology.MONO_CRT->{
            val phosphor=if(theme==AppTheme.SPACE_TERMINAL)blend(source.accent,0xffa7f5b4,.75f) else blend(source.accent,0xfff2edd4,.76f)
            BoardColors(phosphor,blend(0xff080d0c,source.background,.06f),phosphor)
        }
        BoardDisplayTechnology.COLOR_CRT->BoardColors(blend(secondary,0xfff1f9ff,.74f),
            blend(0xff080b16,source.background,.075f),blend(source.accent,0xfff5dbfb,.62f))
        BoardDisplayTechnology.TFT_LCD->BoardColors(0xfff0f8ff,blend(0xff102537,source.accent,.16f),blend(source.accent,0xffc7f1ff,.72f))
        BoardDisplayTechnology.OLED,BoardDisplayTechnology.MICRO_LED->BoardColors(blend(secondary,0xfff1f7f8,.79f),
            blend(0xff020508,source.background,.055f),blend(source.accent,0xffeaf7ff,.62f))
        // Diodes and cells glow in the theme's hue at full luminance.
        BoardDisplayTechnology.LED_MATRIX,BoardDisplayTechnology.SEGMENT_LED->BoardColors(diode(source.accent),
            blend(0xff040506,source.background,.04f),diode(secondary))
        BoardDisplayTechnology.FLIP_DOT->BoardColors(blend(diode(source.accent),0xffffe14d,.55f),0xff121410,blend(diode(secondary),0xffffe14d,.35f))
        BoardDisplayTechnology.SPLIT_FLAP->BoardColors(0xfff4f1e8,blend(0xff17191d,source.background,.06f),blend(diode(source.accent),0xfff4f1e8,.25f))
        BoardDisplayTechnology.E_INK->BoardColors(0xff1c1c1a,blend(0xffe6e5df,source.background,.12f),blend(source.accent,0xff1c1c1a,.4f))
        BoardDisplayTechnology.NIXIE->BoardColors(0xffffa04d,0xff100b09,blend(diode(source.accent),0xffffc890,.6f))
        BoardDisplayTechnology.PLASMA->BoardColors(0xffff7b2e,0xff1a0904,0xffffb07a)
    }
}

/** Whole-screen materials: the host card alone owns corners, with no nested bezel/frame. */
internal fun drawDisplayGlass(c:Canvas,theme:AppTheme,technology:BoardDisplayTechnology,
    w:Float,h:Float,colors:BoardColors,decoration:Boolean) {
    val bg=colors.background.toInt();val signal=colors.accent.toInt()
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    val crt=technology in setOf(BoardDisplayTechnology.MONO_CRT,BoardDisplayTechnology.COLOR_CRT,BoardDisplayTechnology.VFD)
    if(drawTechnologyGlass(c,technology,w,h,bg,signal)) return
    p.shader=if(crt)RadialGradient(w*.48f,h*.40f,max(w,h)*.72f,
        intArrayOf(ColorUtils.blendARGB(bg,signal,.075f),bg,ColorUtils.blendARGB(bg,Color.BLACK,.65f)),
        floatArrayOf(0f,.65f,1f),Shader.TileMode.CLAMP)
    else LinearGradient(0f,0f,w*.25f,h,
        intArrayOf(ColorUtils.blendARGB(bg,Color.WHITE,if(technology==BoardDisplayTechnology.REFLECTIVE_LCD).09f else .025f),bg,
            ColorUtils.blendARGB(bg,Color.BLACK,.13f)),null,Shader.TileMode.CLAMP)
    c.drawRect(0f,0f,w,h,p);p.shader=null
    if(decoration && theme.texture!=ThemeTexture.NONE) {
        // All app artwork participates, including circuits, routes, OS patterns and seasons.
        c.saveLayerAlpha(0f,0f,w,h,if(crt)215 else 235)
        drawAdaptiveBoardBackdrop(c,theme,w,h,ColorUtils.blendARGB(theme.secondary.toInt(),signal,.5f),bg)
        c.restore()
    }
    // Bounded static patterns; no timer, animation, random refresh or additional bitmap allocation.
    when(technology) {
        BoardDisplayTechnology.VFD->{
            p.color=ColorUtils.setAlphaComponent(signal,18);p.strokeWidth=.5f
            var x=3f;while(x<w){c.drawLine(x,0f,x,h,p);x+=9f}
            var y=3f;while(y<h){c.drawLine(0f,y,w,y,p);y+=5f}
        }
        BoardDisplayTechnology.MONO_CRT,BoardDisplayTechnology.COLOR_CRT->{
            p.color=0x2b000000;var y=1.7f
            while(y<h){c.drawRect(0f,y,w,y+.65f,p);y+=3.1f}
            if(technology==BoardDisplayTechnology.COLOR_CRT) {
                p.strokeWidth=.32f
                for(channel in 0..2){p.color=intArrayOf(0x0cfa687d,0x0c78ffaa,0x0c87aaff)[channel]
                    var x=channel*.8f;while(x<w){c.drawLine(x,0f,x,h,p);x+=3.2f}}
            }
        }
        BoardDisplayTechnology.REFLECTIVE_LCD->{
            p.color=0x10232d20;val step=max(3.5f,sqrt(w*h/3600f))
            var y=2f;while(y<h){var x=2f;while(x<w){c.drawRect(x,y,x+.6f,y+.6f,p);x+=step};y+=step}
        }
        BoardDisplayTechnology.TFT_LCD->{
            p.strokeWidth=.4f;p.color=0x0bffffff
            var x=1f;while(x<w){c.drawLine(x,0f,x,h,p);x+=2.5f}
        }
        BoardDisplayTechnology.MICRO_LED->{
            p.color=ColorUtils.setAlphaComponent(signal,26);val step=max(4f,sqrt(w*h/3600f))
            var y=2f;while(y<h){var x=2f;while(x<w){c.drawCircle(x,y,.48f,p);x+=step};y+=step}
        }
        else->Unit
    }
    // A broad reflection and edge fall-off suggest glass without rounding the host twice.
    if(technology in setOf(BoardDisplayTechnology.TFT_LCD,BoardDisplayTechnology.OLED,BoardDisplayTechnology.COLOR_CRT)) {
        p.shader=LinearGradient(0f,0f,w,h*.5f,intArrayOf(0x10ffffff,0x00ffffff),null,Shader.TileMode.CLAMP)
        val shine=Path().apply {moveTo(0f,0f);lineTo(w*.82f,0f);lineTo(w*.30f,h*.36f);lineTo(0f,h*.44f);close()}
        c.drawPath(shine,p);p.shader=null
    }
}

/** Reuses one measured paint across the board; luminous halos stay inside the row's hit area. */
internal class DisplayText(private val canvas:Canvas,private val face:Typeface,private val technology:BoardDisplayTechnology,
    private val background:Int=Color.BLACK,
    /** Printed (non-emissive) text of ThemeLanguage boards, without glow or LCD shadow. */
    private val flat:Boolean=false) {
    private val paint=TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    fun draw(value:String,x:Float,cy:Float,width:Float,size:Float,height:Float,color:Int,bold:Boolean=false,center:Boolean=false) {
        if(width<=0f || height<=0f || value.isEmpty())return
        // Dot modules and split flaps build their own characters.
        if(technology.dotText){drawDotText(canvas,technology,value,x,cy,width,size,height,color,face,center);return}
        if(technology==BoardDisplayTechnology.SPLIT_FLAP){drawFlapText(canvas,value,x,cy,width,size,height,color,background,face,bold);return}
        paint.reset();paint.isAntiAlias=true;paint.isSubpixelText=true
        paint.color=color;paint.textSize=size;paint.typeface=Typeface.create(face,if(bold)Typeface.BOLD else Typeface.NORMAL)
        val fh=paint.fontMetrics.descent-paint.fontMetrics.ascent
        if(fh>height)paint.textSize*=height/fh
        val shown=TextUtils.ellipsize(value,paint,width,TextUtils.TruncateAt.END).toString()
        paint.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
        val xx=if(center)x+width/2 else x
        val baseline=cy-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2
        val glow=when(technology) {
            BoardDisplayTechnology.VFD->1.9f
            BoardDisplayTechnology.MONO_CRT,BoardDisplayTechnology.COLOR_CRT->1.5f
            BoardDisplayTechnology.MICRO_LED->1.1f
            BoardDisplayTechnology.OLED->.7f
            BoardDisplayTechnology.TFT_LCD->.45f
            BoardDisplayTechnology.NIXIE->2.4f
            BoardDisplayTechnology.REFLECTIVE_LCD->0f
            else->-1f
        }
        canvas.save();canvas.clipRect(x-2,cy-height/2-1,x+width+2,cy+height/2+1)
        if(flat)paint.clearShadowLayer()
        else if(glow>0f)paint.setShadowLayer(glow,0f,0f,ColorUtils.setAlphaComponent(color,165))
        else if(glow==0f)paint.setShadowLayer(.45f,.55f,.7f,0x35202b20)
        canvas.drawText(shown,xx,baseline,paint)
        paint.clearShadowLayer();canvas.restore()
    }
}

/** Real seven-segment polygons, including dim unused segments instead of a font imitation. */
internal fun drawDisplayDigits(c:Canvas,value:String,x:Float,y:Float,height:Float,color:Int,lcd:Boolean=false) {
    val masks=intArrayOf(0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f)
    val p=Paint(Paint.ANTI_ALIAS_FLAG);val dw=height*.52f;val t=height*.095f
    var xx=x
    fun horizontal(left:Float,top:Float,right:Float)=Path().apply {
        moveTo(left,top+t/2);lineTo(left+t/2,top);lineTo(right-t/2,top);lineTo(right,top+t/2)
        lineTo(right-t/2,top+t);lineTo(left+t/2,top+t);close()
    }
    fun vertical(left:Float,top:Float,bottom:Float)=Path().apply {
        moveTo(left+t/2,top);lineTo(left+t,top+t/2);lineTo(left+t,bottom-t/2)
        lineTo(left+t/2,bottom);lineTo(left,bottom-t/2);lineTo(left,top+t/2);close()
    }
    value.forEach {char->
        p.clearShadowLayer();p.color=color
        if(char==':') {
            c.drawCircle(xx+height*.10f,y+height*.34f,t*.40f,p)
            c.drawCircle(xx+height*.10f,y+height*.70f,t*.40f,p);xx+=height*.20f
        } else if(char in '0'..'9') {
            val paths=listOf(horizontal(xx+t*.3f,y,xx+dw-t*.3f),
                vertical(xx+dw-t,y+t*1.25f,y+height/2-t*.75f),
                vertical(xx+dw-t,y+height/2+t*.75f,y+height-t*1.25f),
                horizontal(xx+t*.3f,y+height-t,xx+dw-t*.3f),
                vertical(xx,y+height/2+t*.75f,y+height-t*1.25f),
                vertical(xx,y+t*1.25f,y+height/2-t*.75f),horizontal(xx+t*.3f,y+height/2-t/2,xx+dw-t*.3f))
            paths.forEachIndexed {bit,path->
                val on=masks[char-'0'] and (1 shl bit)!=0
                p.color=ColorUtils.setAlphaComponent(color,if(on)255 else if(lcd)25 else 20)
                if(on && !lcd)p.setShadowLayer(1.4f,0f,0f,ColorUtils.setAlphaComponent(color,135))else p.clearShadowLayer()
                c.drawPath(path,p)
            }
            xx+=dw+height*.14f
        }
    }
}
