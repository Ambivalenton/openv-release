// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.readableForeground
import kotlin.math.*

internal fun displayBoardTheme(m:BoardPresentation):AppTheme?=m.appStyle ?: eraBoardThemes[m.design] ?: when(m.design) {
    BoardDesign.APP_THEME,BoardDesign.APP_INTEGRATED->AppTheme.CLASSIC_DARK
    BoardDesign.RETRO_LCD->AppTheme.POCKET_CONSOLE
    else->null
}
internal fun displayBoardTechnology(m:BoardPresentation,theme:AppTheme)=
    if(m.design==BoardDesign.RETRO_LCD)BoardDisplayTechnology.REFLECTIVE_LCD else boardDisplayTechnology(theme)

/** Every adaptive board is a display, with its app artwork and an era-appropriate screen material. */
fun drawEraBoardDesign(c:Canvas,m:BoardPresentation,w:Float,h:Float,l:BoardLayout,fontScale:Float,headerAction:Boolean):Boolean {
    val theme=displayBoardTheme(m) ?: return false
    val technology=displayBoardTechnology(m,theme)
    val lcd=technology==BoardDisplayTechnology.REFLECTIVE_LCD
    // Reflective surfaces (LCD, e-ink) shade rows darker; emitters lighten them.
    val reflective=lcd || technology==BoardDisplayTechnology.E_INK
    val analogue=technology in setOf(BoardDisplayTechnology.VFD,BoardDisplayTechnology.MONO_CRT,BoardDisplayTechnology.COLOR_CRT,
        BoardDisplayTechnology.NIXIE,BoardDisplayTechnology.PLASMA)
    val bg=m.colors.background.toInt();val ink=m.colors.foreground.toInt();val signal=m.colors.accent.toInt()
    val pad=l.padding
    val scale=if(l.singleLine)min(1.1f,l.rowHeight/20)else fontScale.coerceIn(1f,2f)
    val face=m.typeface ?: when {
        // Dot modules and flaps use a compact grotesk, as real modules do.
        technology.dotText || technology==BoardDisplayTechnology.SPLIT_FLAP->Typeface.create("sans-serif-condensed",Typeface.BOLD)
        lcd->Typeface.MONOSPACE
        m.themeTypeface!=null->m.themeTypeface
        theme.font==ThemeFont.MONO->Typeface.MONOSPACE
        theme.font==ThemeFont.SERIF->Typeface.SERIF
        theme.font==ThemeFont.ROUNDED->Typeface.create("sans-serif-rounded",Typeface.NORMAL)
        else->Typeface.SANS_SERIF
    }
    val text=DisplayText(c,face,technology,bg)
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    fun fill(color:Int,alpha:Int=255){p.shader=null;p.style=Paint.Style.FILL;p.color=ColorUtils.setAlphaComponent(color,alpha)}
    drawDisplayGlass(c,theme,technology,w,h,m.colors,m.appDecoration && m.design!=BoardDesign.RETRO_LCD)
    if(!m.isDesignPreview) {
        var headerInk=signal
        if(theme.isOperatingSystem) {
            val basePalette=SystemSurfacePalette(bg,m.appSurface?.toInt() ?: bg,theme.accent.toInt(),m.appSecondary?.toInt() ?: signal,ink)
            val headerText=readableForeground(m.colors.foreground,
                systemHeaderColor(theme,basePalette).toLong() and 0xffffffffL).toInt()
            val palette=basePalette.copy(text=headerText)
            drawSystemHeader(c,theme,w,pad+l.header,palette)
            headerInk=systemHeaderForeground(theme,palette)
        } else if(theme==AppTheme.DECADE_90) {
            // A 90s colour monitor carries its own desktop title bar, rather than an 80s neon rail.
            fill(0xff425a63.toInt());c.drawRect(0f,0f,w,pad+l.header,p)
            fill(0xffb9c6c5.toInt());c.drawRect(1f,1f,w-1f,2f,p)
            repeat(3){n->fill(0xffb9c6c5.toInt(),160);val x=w-pad-7-n*11
                c.drawRect(x,pad+2,x+6,pad+8,p)}
            headerInk=Color.WHITE
        } else {
            // Theme signatures stay visible above the rows instead of hiding behind opaque cards.
            val y=pad+l.header-3
            fill(signal,if(analogue)125 else 180)
            when(theme.texture) {
                ThemeTexture.RETRO_70->{repeat(3){n->fill(if(n==1)theme.secondary.toInt() else signal,130-n*20)
                    c.drawRoundRect(pad+n*6,y-6+n*2,w-pad-n*6,y-4+n*2,1f,1f,p)}}
                ThemeTexture.RETRO_80,ThemeTexture.GRID->{c.drawLine(pad,y,w*.64f,y,p.apply {strokeWidth=1.4f})
                    fill(theme.secondary.toInt(),180);c.drawLine(w*.67f,y,w-pad,y,p)}
                ThemeTexture.RENAISSANCE,ThemeTexture.BAROQUE,ThemeTexture.DECO,ThemeTexture.NOUVEAU->{
                    c.drawRect(pad,y,w-pad,y+.6f,p);c.drawCircle(w/2,y,2.2f,p)
                }
                else->{
                    // The rainbow designs carry their flag as the header rule.
                    val stripes=dev.ambivalenton.openv.ui.theme.prideStripes(theme)
                    if(stripes.isEmpty())c.drawRect(pad,y,w-pad,y+if(analogue).6f else 1f,p)
                    else {val seg=(w-2*pad)/stripes.size;stripes.forEachIndexed {i,s->fill(s.toInt());c.drawRect(pad+i*seg,y-1f,pad+(i+1)*seg+.3f,y+1.5f,p)}}
                }
            }
        }
        val topInset=boardCornerInset(theme,m.appCornerScale,pad,top=true)
        text.draw(m.title,pad+topInset,pad+l.header*.40f,w-2*pad-topInset-if(theme==AppTheme.DECADE_90)40f else if(headerAction)28f else 0f,14*scale,l.header*.75f,headerInk,true)
    }
    var top=pad+l.header
    val rows=m.rows.take(l.rows)
    if(rows.isEmpty())text.draw(m.emptyMessage,pad,(top+16).coerceAtMost(h-pad-8),w-2*pad,12*scale,26*scale,ink)
    rows.forEachIndexed {index,row->
        val rh=l.rowHeight-l.journeyLinkHeight
        val left=pad+4;val right=w-pad-4;val cw=right-left
        c.save();c.clipRect(pad,top,w-pad,top+rh)
        if(index%2==1){fill(if(reflective)Color.BLACK else signal,if(reflective)5 else if(analogue)7 else 10);c.drawRect(pad,top,w-pad,top+rh,p)}
        fill(signal,if(analogue)32 else if(reflective)48 else 42);c.drawRect(pad,top+rh-.7f,w-pad,top+rh-.25f,p)
        if(m.isDesignPreview) {
            val clockWidth=if(lcd)min(84f,cw*.35f)else 0f
            text.draw(row.destination,left,top+rh/2,cw-clockWidth-4,16*scale,rh*.7f,ink,true)
            if(lcd)drawDisplayDigits(c,"12:48",right-clockWidth,top+rh/2-12f,24f,ink,true)
        } else {
            val clockWidth=min(94*scale,cw*.35f)
            val lineWidth=min(56*scale,cw*.21f)
            val clock=if(row.delay.cancelled)tr("Fällt aus")else clockTime(row.time)
            val late=row.delay.minutes?.takeIf {it>0 && !row.delay.cancelled}?.let {" +$it"}.orEmpty()
            val timeInk=if(row.delay.cancelled)readableForeground(0xffe66572,m.colors.background).toInt()else signal
            fun time(x:Float,cy:Float,width:Float,height:Float) {
                if((lcd || technology==BoardDisplayTechnology.VFD || technology==BoardDisplayTechnology.SEGMENT_LED) && !row.delay.cancelled) {
                    val digitHeight=min(17*scale,min(height*.86f,width/2.85f))
                    drawDisplayDigits(c,clock,x,cy-digitHeight/2,digitHeight,timeInk,lcd)
                    if(late.isNotEmpty())text.draw(late,x+digitHeight*2.85f,cy,width-digitHeight*2.85f,9*scale,height,timeInk)
                }else if(technology==BoardDisplayTechnology.NIXIE && !row.delay.cancelled) {
                    val tubeHeight=min(19*scale,min(height*.92f,width/(nixieWidth(clock,1f)+.05f)))
                    drawNixieTime(c,clock,x,cy,tubeHeight,timeInk)
                    val used=nixieWidth(clock,tubeHeight)
                    if(late.isNotEmpty())text.draw(late,x+used,cy,width-used,9*scale,height,timeInk)
                }else text.draw(clock+late,x,cy,width,13*scale,height,timeInk,true)
            }
            if(l.singleLine) {
                text.draw(row.lineName,left,top+rh/2,lineWidth-5,11.5f*scale,rh*.8f,signal,true,true)
                text.draw(row.destination,left+lineWidth,top+rh/2,cw-lineWidth-clockWidth-4,11.5f*scale,rh*.8f,ink)
                time(right-clockWidth,top+rh/2,clockWidth,rh*.8f)
            } else if(l.narrow) {
                val detail=row.platform?.let {platformName(it,row.lineType)} ?: row.lineType.label
                text.draw(row.lineName+" · "+detail,left,top+rh*.18f,cw,10.5f*scale,rh*.27f,signal,true)
                text.draw(row.destination,left,top+rh*.46f,cw,12*scale,rh*.31f,ink,true)
                time(left,top+rh*.79f,cw,rh*.30f)
            } else {
                if(technology in setOf(BoardDisplayTechnology.OLED,BoardDisplayTechnology.MICRO_LED)) {
                    val badge=min(45*scale,cw*.19f)
                    fill(signal,22);c.drawRoundRect(left,top+rh*.17f,left+badge-4,top+rh*.82f,if(theme.shapeStyle==ThemeShape.CUT)0f else 5f,5f,p)
                    text.draw(row.lineName,left,top+rh*.5f,badge-4,11*scale,rh*.55f,signal,true,true)
                    text.draw(row.destination,left+badge,top+rh*.31f,cw-clockWidth-badge-5,12.5f*scale,rh*.48f,ink,true)
                    text.draw(row.platform?.let {platformName(it,row.lineType)} ?: row.lineType.label,left+badge,top+rh*.72f,cw-clockWidth-badge-5,10.2f*scale,rh*.38f,ink)
                } else {
                    text.draw(row.lineName+" · "+row.destination,left,top+rh*.31f,cw-clockWidth-5,12.5f*scale,rh*.48f,ink,true)
                    text.draw(row.platform?.let {platformName(it,row.lineType)} ?: row.lineType.label,left,top+rh*.72f,cw-clockWidth-5,10.2f*scale,rh*.38f,ink)
                }
                time(right-clockWidth,top+rh*.5f,clockWidth,rh*.75f)
            }
        }
        c.restore();top+=l.rowHeight
    }
    val bottomInset=boardCornerInset(theme,m.appCornerScale,pad,top=false)
    if(!m.isDesignPreview && l.footer>0)text.draw(m.caption,pad+bottomInset,h-pad-l.footer/2,w-2*pad-bottomInset-if(headerAction)28f else 0f,10*scale,l.footer*.85f,ink)
    if(headerAction)text.draw("⋮",w-26,18f,20f,18f,24f,signal,true)
    return true
}

/** Strongly rounded or leaf-shaped hosts cut into the board's corners; title and caption step
 * in by the part of the corner curve that reaches past the padding (0 for ordinary radii). */
internal fun boardCornerInset(theme:AppTheme,cornerScale:Float,pad:Float,top:Boolean):Float {
    if(theme.hasSquareCorners) return 0f
    val r=24f*theme.radiusScale*cornerScale
    val effective=when(theme.shapeStyle) {
        ThemeShape.LEAF->if(top) r*1.5f else r*.22f
        ThemeShape.ASYMMETRIC->if(top) r else r*.25f
        ThemeShape.CUT,ThemeShape.BEVEL->r*.55f
        ThemeShape.SQUIRCLE->r*1.2f
        else->r
    }
    return (effective*.36f-pad*.55f).coerceIn(0f,22f)
}
