// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.AppTheme
import kotlin.math.*

/** Original, offline interpretations of system materials, shared by app chrome, charts and boards. */
data class SystemSurfacePalette(val background:Int,val surface:Int,val accent:Int,val secondary:Int,val text:Int)
val AppTheme.isOperatingSystem get()=name.startsWith("OS_")
fun systemHeaderIsDark(t:AppTheme)=t in setOf(AppTheme.OS_WIN_XP,AppTheme.OS_WIN_95,AppTheme.OS_IOS_4,AppTheme.OS_NEXTSTEP,AppTheme.OS_OS2,AppTheme.OS_ANDROID_HOLO,AppTheme.OS_UBUNTU,AppTheme.OS_CDE,AppTheme.OS_AMIGA)
fun systemHeaderColor(t:AppTheme,p:SystemSurfacePalette)=when(t){
    AppTheme.OS_WIN_XP->p.accent
    AppTheme.OS_WIN_95->0xff000080.toInt();AppTheme.OS_AMIGA->0xff0055aa.toInt()
    AppTheme.OS_NEXTSTEP->0xff25272a.toInt();AppTheme.OS_OS2->0xff224d82.toInt()
    AppTheme.OS_ANDROID_HOLO->0xff10171d.toInt();AppTheme.OS_UBUNTU->0xff3c3038.toInt()
    AppTheme.OS_CDE->0xff48636c.toInt();AppTheme.OS_WIN_7->0xffbdd8ea.toInt()
    AppTheme.OS_IOS_4->0xff49678d.toInt();AppTheme.OS_IOS_GLASS->0xffeef3fb.toInt()
    AppTheme.OS_MAC_LEOPARD->0xffc8c8c8.toInt();AppTheme.OS_MAC_AQUA->0xffe8edf1.toInt()
    AppTheme.OS_MAC_CLASSIC->0xffeeeeee.toInt();AppTheme.OS_BEOS->0xffffd55a.toInt()
    AppTheme.OS_GNOME->0xffebebeb.toInt()
    else->p.surface
}
fun systemHeaderForeground(t:AppTheme,p:SystemSurfacePalette):Int {
    val background=systemHeaderColor(t,p)
    val requested=if(systemHeaderIsDark(t))Color.WHITE else p.text
    if(ColorUtils.calculateContrast(requested or Color.BLACK,background or Color.BLACK)>=4.5)return requested
    return if(ColorUtils.calculateContrast(Color.WHITE,background or Color.BLACK)>
        ColorUtils.calculateContrast(Color.BLACK,background or Color.BLACK))Color.WHITE else Color.BLACK
}

/**
 * [bleed] extends the fill beyond the top and bottom (the gradient keeps its place, its end colours
 * continue); [edges] = false leaves out the light and dark rules at the top and bottom. Build 115: the
 * navigation pill uses both – its shell swells past its box while it springs, and the rules then showed
 * as a light line inside the swelling.
 */
fun drawSystemHeader(c:Canvas,t:AppTheme,w:Float,h:Float,p:SystemSurfacePalette,density:Float=1f,bleed:Float=0f,edges:Boolean=true){
    val paint=Paint(Paint.ANTI_ALIAS_FLAG);val base=systemHeaderColor(t,p);val u=density
    fun fill(a:Int,b:Int=a,mid:Int?=null){paint.shader=LinearGradient(0f,0f,0f,h,if(mid==null)intArrayOf(a,b)else intArrayOf(a,mid,b),null,Shader.TileMode.CLAMP);c.drawRect(0f,-bleed,w,h+bleed,paint);paint.shader=null}
    fun edge(color:Int,y:Float){if(!edges)return;paint.color=color;paint.strokeWidth=u;c.drawLine(0f,y,w,y,paint)}
    when(t){
        AppTheme.OS_WIN_XP->{fill(ColorUtils.blendARGB(base,Color.WHITE,.07f),ColorUtils.blendARGB(base,Color.BLACK,.18f),base);edge(0xff9ec6ff.toInt(),u);edge(0xff16439b.toInt(),h-u)}
        AppTheme.OS_WIN_7->{fill(0xffe0eef8.toInt(),0xffa6c9df.toInt(),0xffc1dceb.toInt());paint.color=0x24ffffff;c.drawPath(Path().apply{moveTo(w*.06f,0f);lineTo(w*.28f,0f);lineTo(w*.48f,h);lineTo(w*.26f,h);close()},paint);edge(0xfff0faff.toInt(),u);edge(0xff7e9caf.toInt(),h-u)}
        AppTheme.OS_IOS_GLASS->{fill(0xd9ffffff.toInt(),0xbce5eefb.toInt(),0xc9f6f9ff.toInt());edge(0xf0ffffff.toInt(),u);edge(0x34516a86,h-u);paint.color=0x1ea8c7ee;c.drawOval(-w*.2f,-h*2f,w*.7f,h*1.2f,paint)}
        AppTheme.OS_IOS_4->{fill(0xff5e7899.toInt(),0xff294b70.toInt(),0xff4a6588.toInt());edge(0xffcad4df.toInt(),u);edge(0xff3b506e.toInt(),h-u)}
        AppTheme.OS_WIN_8->{fill(p.background);edge(ColorUtils.setAlphaComponent(p.accent,120),h-u)}
        AppTheme.OS_MAC_AQUA->{fill(0xfffefefe.toInt(),0xffd3d8dc.toInt(),0xffeff2f4.toInt());paint.color=0x16a2b9c8;var y=0f;while(y<h){c.drawRect(0f,y,w,y+u,paint);y+=3*u};edge(Color.WHITE,u);edge(0xff8b9ca7.toInt(),h-u)}
        AppTheme.OS_MAC_CLASSIC->{fill(base);paint.color=0xff999999.toInt();paint.strokeWidth=u;var y=4*u;while(y<h){c.drawLine(4*u,y,w-4*u,y,paint);y+=4*u};paint.color=base;c.drawRect(12*u,h*.12f,w-12*u,h*.88f,paint);edge(Color.BLACK,h-u)}
        AppTheme.OS_MAC_LEOPARD->{fill(0xffe8e8e8.toInt(),0xffb6b6b6.toInt(),0xffd2d2d2.toInt());edge(Color.WHITE,u);edge(0xff747474.toInt(),h-u)}
        AppTheme.OS_BEOS->{fill(0xffffe887.toInt(),0xfff0c744.toInt(),0xffffd95f.toInt());edge(Color.WHITE,u);edge(0xff8d792c.toInt(),h-u)}
        AppTheme.OS_GNOME->{fill(base);edge(0xffd0d0d0.toInt(),h-u)}
        AppTheme.OS_WIN_95->{fill(base);edge(0xff00004d.toInt(),h-u)}
        AppTheme.OS_AMIGA->{fill(base);edge(0xfff4f4f4.toInt(),u);edge(0xff111111.toInt(),h-u)}
        AppTheme.OS_ATARI->{fill(p.surface);edge(Color.WHITE,u);edge(0xff333333.toInt(),h-u)}
        AppTheme.OS_ANDROID_HOLO->{fill(base);edge(ColorUtils.setAlphaComponent(p.accent,230),h-2*u)}
        else->{fill(ColorUtils.blendARGB(base,Color.WHITE,.10f),ColorUtils.blendARGB(base,Color.BLACK,.14f));edge(ColorUtils.blendARGB(base,Color.WHITE,.55f),u);edge(ColorUtils.blendARGB(base,Color.BLACK,.60f),h-u)}
    }
}

/**
 * [outline] returns the host's real contour inset by the given amount. With it, every rim
 * follows the card's theme corners instead of a fixed radius that the card's clip would cut off.
 */
fun drawSystemPane(c:Canvas,t:AppTheme,w:Float,h:Float,p:SystemSurfacePalette,density:Float=1f,outline:((Float)->Path)?=null){
    val paint=Paint(Paint.ANTI_ALIAS_FLAG);val u=density;val r=min((if(t==AppTheme.OS_IOS_GLASS)36f else 18f)*u,min(w,h)/2)
    fun fill(a:Int,b:Int=a){paint.shader=LinearGradient(0f,0f,w,h,intArrayOf(a,b),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,paint);paint.shader=null}
    fun border(color:Int,inset:Float=0f,round:Float=r,width:Float=u){paint.color=color;paint.style=Paint.Style.STROKE;paint.strokeWidth=width
        val shaped=outline?.invoke(inset+width/2)
        if(shaped!=null)c.drawPath(shaped,paint) else c.drawRoundRect(inset+width/2,inset+width/2,w-inset-width/2,h-inset-width/2,round,round,paint)
        paint.style=Paint.Style.FILL}
    /** Classic raised bevel along the host contour: light upper-left half, dark lower-right half. */
    fun bevel(light:Int,dark:Int) {
        val path=outline?.invoke(u) ?: return
        paint.style=Paint.Style.STROKE;paint.strokeWidth=2*u;paint.color=light;c.drawPath(path,paint)
        c.save();c.clipPath(Path().apply {moveTo(w,0f);lineTo(w,h);lineTo(0f,h);close()})
        paint.color=dark;c.drawPath(path,paint);c.restore();paint.style=Paint.Style.FILL
    }
    when(t){
        AppTheme.OS_WIN_XP->{fill(ColorUtils.blendARGB(p.surface,Color.WHITE,.08f),p.surface);border(0xff7c9fca.toInt(),round=5*u)
            // The highlight stays on the straight top edge, clear of the host's corners.
            val lip=if(outline!=null)min(24*u,w/4) else 2*u
            paint.color=Color.WHITE;c.drawRect(lip,2*u,w-lip,3*u,paint)}
        AppTheme.OS_IOS_GLASS->{fill(ColorUtils.setAlphaComponent(ColorUtils.blendARGB(p.surface,Color.WHITE,.06f),220),ColorUtils.setAlphaComponent(p.surface,205));border(0xedffffff.toInt(),round=r);border(ColorUtils.setAlphaComponent(p.accent,28),2*u,(r-2*u).coerceAtLeast(0f));paint.shader=RadialGradient(w*.1f,0f,max(w,h)*.9f,intArrayOf(0x66ffffff,0x00ffffff),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,paint);paint.shader=null}
        AppTheme.OS_WIN_7->{fill(ColorUtils.setAlphaComponent(ColorUtils.blendARGB(p.surface,Color.WHITE,.06f),230),ColorUtils.setAlphaComponent(p.surface,215));border(0xddefffff.toInt(),round=8*u);border(0x5a527488,2*u,6*u)}
        AppTheme.OS_IOS_4->{fill(ColorUtils.blendARGB(p.surface,Color.WHITE,.15f),p.surface);border(0xff8795a5.toInt(),round=8*u);paint.color=0x20ffffff;var y=2*u;while(y<h){c.drawRect(0f,y,w,y+u,paint);y+=3*u}}
        AppTheme.OS_MAC_AQUA->{fill(p.surface,ColorUtils.blendARGB(p.surface,p.accent,.045f));paint.color=0x16a2b9c8;var y=0f;while(y<h){c.drawRect(0f,y,w,y+u,paint);y+=3*u};border(0xff9daebc.toInt(),round=8*u)}
        AppTheme.OS_MAC_CLASSIC->{fill(p.surface);border(Color.BLACK,round=2*u);border(Color.WHITE,u,2*u)}
        AppTheme.OS_MAC_LEOPARD->{fill(p.surface,ColorUtils.blendARGB(p.surface,Color.BLACK,.06f));border(0xffa1a1a1.toInt(),round=6*u);border(0x55ffffff,u,5*u)}
        AppTheme.OS_GNOME->{fill(p.surface);border(ColorUtils.setAlphaComponent(p.text,35),round=12*u)}
        AppTheme.OS_UBUNTU->{fill(p.surface);border(0xffaba59e.toInt(),round=6*u);border(0x88ffffff.toInt(),u,5*u)}
        AppTheme.OS_BEOS->{fill(p.surface);border(0xff606060.toInt(),round=2*u);border(Color.WHITE,u,1*u)}
        AppTheme.OS_WIN_8->{fill(p.surface);border(ColorUtils.setAlphaComponent(p.accent,90),round=0f)}
        AppTheme.OS_ANDROID_HOLO->{fill(p.surface);paint.color=p.accent;paint.strokeWidth=u;c.drawLine(0f,h-u,w,h-u,paint)}
        AppTheme.OS_WIN_95,AppTheme.OS_AMIGA,AppTheme.OS_ATARI,AppTheme.OS_NEXTSTEP,AppTheme.OS_OS2,AppTheme.OS_CDE->{
            fill(p.surface)
            val light=ColorUtils.blendARGB(p.surface,Color.WHITE,.75f);val dark=ColorUtils.blendARGB(p.surface,Color.BLACK,.65f)
            if(outline!=null)bevel(light,dark) else {
                paint.color=light;c.drawRect(0f,0f,w,2*u,paint);c.drawRect(0f,0f,2*u,h,paint)
                paint.color=dark;c.drawRect(0f,h-2*u,w,h,paint);c.drawRect(w-2*u,0f,w,h,paint)
            }
        }
        else->{fill(p.surface,ColorUtils.blendARGB(p.surface,p.background,.18f));border(ColorUtils.setAlphaComponent(p.text,60),round=if(t==AppTheme.OS_MAC_CLASSIC)3*u else r)}
    }
}

/** Material behind a real Compose button: semantics, hit target and ripple stay native.
 * Highlight bands are confined to the top rim, keeping the label on its contrast-paired base. */
fun drawSystemControl(c:Canvas,t:AppTheme,w:Float,h:Float,base:Int,enabled:Boolean,density:Float=1f){
    if(w<=0f||h<=0f)return
    val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    val u=density
    fun fill(a:Int,b:Int=a){paint.shader=LinearGradient(0f,0f,0f,h,intArrayOf(a,b),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,paint);paint.shader=null}
    fun edge(color:Int,top:Boolean){paint.color=color;paint.strokeWidth=u
        val inset=if(top)u else 2*u
        c.drawLine(inset,if(top)u else h-u,w-inset,if(top)u else h-u,paint)
        c.drawLine(if(top)u else w-u,inset,if(top)u else w-u,h-inset,paint)
    }
    if(!enabled){fill(base);return}
    when(t){
        AppTheme.OS_WIN_95,AppTheme.OS_AMIGA,AppTheme.OS_ATARI,AppTheme.OS_NEXTSTEP,AppTheme.OS_OS2,AppTheme.OS_CDE->{
            fill(base);edge(ColorUtils.blendARGB(base,Color.WHITE,.8f),true);edge(ColorUtils.blendARGB(base,Color.BLACK,.75f),false)
        }
        AppTheme.OS_WIN_8,AppTheme.OS_ANDROID_HOLO,AppTheme.OS_GNOME->{fill(base)}
        AppTheme.OS_MAC_CLASSIC->{fill(base);edge(Color.WHITE,true);edge(Color.BLACK,false)}
        AppTheme.OS_MAC_AQUA,AppTheme.OS_IOS_4,AppTheme.OS_IOS_GLASS,AppTheme.OS_WIN_7,AppTheme.OS_WIN_XP->{
            fill(ColorUtils.blendARGB(base,Color.WHITE,.06f),ColorUtils.blendARGB(base,Color.BLACK,.06f))
            paint.shader=LinearGradient(0f,0f,0f,h*.32f,intArrayOf(0x66ffffff,0x05ffffff),null,Shader.TileMode.CLAMP)
            c.drawRect(0f,0f,w,h*.32f,paint);paint.shader=null
            paint.color=0x99ffffff.toInt();paint.strokeWidth=u;c.drawLine(6*u,u,w-6*u,u,paint)
            if(t==AppTheme.OS_IOS_GLASS){paint.color=0x44ffffff;c.drawOval(-w*.2f,-h*.65f,w*.42f,h*.12f,paint)}
        }
        else->{fill(ColorUtils.blendARGB(base,Color.WHITE,.06f),ColorUtils.blendARGB(base,Color.BLACK,.06f));edge(0x66ffffff,true)}
    }
}

/** Progress-control vocabulary inside unchanged ride bounds. Transit colours retain their meaning. */
fun drawSystemRideBar(c:Canvas,t:AppTheme,w:Float,h:Float,p:SystemSurfacePalette,ride:Int,density:Float=1f){
    val paint=Paint(Paint.ANTI_ALIAS_FLAG);val u=density;val thin=min(u,min(w,h)/5);val radius=if(t==AppTheme.OS_IOS_GLASS)min(w,h)/2 else if(t in setOf(AppTheme.OS_WIN_8,AppTheme.OS_WIN_95,AppTheme.OS_AMIGA,AppTheme.OS_ATARI,AppTheme.OS_CDE))0f else min(5*u,min(w,h)/2)
    val bounds=RectF(thin,thin,w-thin,h-thin)
    paint.shader=LinearGradient(0f,0f,w,0f,intArrayOf(ColorUtils.blendARGB(ride,Color.WHITE,.42f),ride,ColorUtils.blendARGB(ride,Color.BLACK,.18f)),null,Shader.TileMode.CLAMP)
    c.drawRoundRect(bounds,radius,radius,paint);paint.shader=null
    if(t==AppTheme.OS_WIN_XP){
        paint.color=ColorUtils.setAlphaComponent(p.surface,235);val step=max(6*u,min(12*u,h/3));var y=step
        while(y<h-2*u){c.drawRect(2*u,y,w-2*u,y+2*u,paint);y+=step}
        paint.color=0xeef6f1df.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=2*u;c.drawRoundRect(bounds,radius,radius,paint)
    }else if(t in setOf(AppTheme.OS_IOS_GLASS,AppTheme.OS_WIN_7,AppTheme.OS_MAC_AQUA,AppTheme.OS_IOS_4)){
        paint.shader=LinearGradient(0f,0f,w,h,intArrayOf(0xaaffffff.toInt(),0x00ffffff,0x10ffffff),null,Shader.TileMode.CLAMP);c.drawRoundRect(bounds,radius,radius,paint);paint.shader=null
        paint.color=0xccffffff.toInt();paint.strokeWidth=u;paint.style=Paint.Style.STROKE;c.drawRoundRect(bounds,radius,radius,paint)
        paint.color=ColorUtils.setAlphaComponent(p.accent,55);paint.strokeWidth=u;c.drawRoundRect(RectF(2*u,2*u,w-2*u,h-2*u),max(0f,radius-u),max(0f,radius-u),paint)
    }else{paint.color=ColorUtils.blendARGB(ride,Color.BLACK,.35f);paint.style=Paint.Style.STROKE;paint.strokeWidth=thin;c.drawRoundRect(bounds,radius,radius,paint)}
}
