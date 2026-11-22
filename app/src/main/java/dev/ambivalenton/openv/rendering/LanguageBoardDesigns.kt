// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.rendering

import android.graphics.*
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.organicBlob
import dev.ambivalenton.openv.ui.theme.readableForeground
import dev.ambivalenton.openv.ui.theme.squirclePath
import kotlin.math.*

/** App colour roles a design-language board needs beyond foreground, ground and accent. */
data class BoardLanguageRoles(val surface:Int,val onSurface:Int,val outline:Int,val outlineVariant:Int,
    val primary:Int,val fill:Int,val onFill:Int,val secondaryFill:Int,val onSecondaryFill:Int)

fun ColorScheme.boardLanguageRoles():BoardLanguageRoles {
    val ground=background.copy(alpha=1f)
    fun solid(c:androidx.compose.ui.graphics.Color)=c.compositeOver(ground).toArgb()
    return BoardLanguageRoles(solid(surfaceContainer),onSurface.toArgb(),solid(outline),solid(outlineVariant),primary.toArgb(),
        solid(primaryContainer),onPrimaryContainer.toArgb(),solid(secondaryContainer),onSecondaryContainer.toArgb())
}

/** Roles from the theme itself, for previews and boards rendered without a live scheme. */
private fun fallbackRoles(theme:AppTheme,colors:BoardColors):BoardLanguageRoles {
    val ink=colors.foreground.toInt();val bg=colors.background.toInt()
    fun onFill(c:Int)=if(ColorUtils.calculateContrast(0xff181a1c.toInt(),c)>=ColorUtils.calculateContrast(0xfff3f2ee.toInt(),c)) 0xff181a1c.toInt() else 0xfff3f2ee.toInt()
    val fill=theme.accent.toInt();val second=theme.secondary.toInt()
    return BoardLanguageRoles(theme.surface.toInt(),ink,ink,ColorUtils.blendARGB(ink,bg,.7f),colors.accent.toInt(),fill,onFill(fill),second,onFill(second))
}

private fun readable(text:Int,on:Int)=readableForeground(text.toLong() and 0xffffffffL,on.toLong() and 0xffffffffL).toInt()

/**
 * A board in the material of the app's design language: printed rather than lit, with the same frames,
 * fills, badges and typography as the app. Layouts (single line, narrow, wide), row heights and hit
 * areas are the shared [BoardLayout]; only the painting differs. Bounded, static and deterministic.
 */
internal fun drawLanguageBoardDesign(c:Canvas,m:BoardPresentation,w:Float,h:Float,l:BoardLayout,fontScale:Float,
    headerAction:Boolean,theme:AppTheme):Boolean {
    val language=theme.themeLanguage
    val roles=m.appRoles ?: fallbackRoles(theme,m.colors)
    val bg=m.colors.background.toInt();val ink=m.colors.foreground.toInt();val accent=m.colors.accent.toInt()
    val pad=l.padding
    val scale=if(l.singleLine)min(1.1f,l.rowHeight/20)else fontScale.coerceIn(1f,2f)
    val face=m.typeface ?: m.themeTypeface ?: when(theme.font) {
        ThemeFont.MONO->Typeface.MONOSPACE;ThemeFont.SERIF->Typeface.SERIF
        ThemeFont.ROUNDED->Typeface.create("sans-serif-rounded",Typeface.NORMAL);else->Typeface.SANS_SERIF
    }
    val text=DisplayText(c,face,BoardDisplayTechnology.OLED,flat=language!=ThemeLanguage.GLOW_EDGE)
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    val radius=(12f*theme.radiusScale*m.appCornerScale).coerceIn(0f,16f)
    fun fill(color:Int){p.reset();p.isAntiAlias=true;p.style=Paint.Style.FILL;p.color=color}

    // The host card clips the bitmap with the theme's corners (24 dp × radius); keep text clear of them.
    val corner=24f*theme.radiusScale*m.appCornerScale*if(theme.shapeStyle==ThemeShape.SQUIRCLE) 1.2f else 1f
    val (cornerTop,cornerTopEnd,cornerBottom)=when(theme.shapeStyle) {
        ThemeShape.PEBBLE->Triple(corner*1.35f,corner*.55f,corner*.7f)
        ThemeShape.ASYMMETRIC->Triple(corner,corner*.25f,corner*.25f)
        else->Triple(corner,corner,corner)
    }
    fun clearance(radius:Float,edge:Float):Float {
        val r=radius.coerceAtMost(min(w,h)/2);if(edge>=r) return 0f
        return r-sqrt(r*r-(r-edge)*(r-edge))
    }
    val titleX=max(pad,clearance(cornerTop,pad+2f)+4f)
    val actionInset=max(0f,clearance(cornerTopEnd,6f)-6f)
    val footerX=max(pad,clearance(cornerBottom,pad+2f)+4f)
    if(language==ThemeLanguage.TONAL){fill(roles.surface);c.drawRect(0f,0f,w,h,p)}
    drawLanguageBoardGround(c,theme,w,h,m.colors,roles,m.appDecoration)

    // Header: the language's own title colour and closing rule (or a colour block in pop designs).
    val headerBottom=pad+l.header
    if(!m.isDesignPreview) {
        var headerInk=when(language) {
            ThemeLanguage.CLAY,ThemeLanguage.ORGANIC,ThemeLanguage.GLOW_EDGE->accent
            else->ink
        }
        when(language) {
            ThemeLanguage.COLOR_BLOCK->{fill(roles.fill);c.drawRect(0f,0f,w,headerBottom-2f,p);headerInk=roles.onFill}
            ThemeLanguage.NEO_BRUTAL->{fill(roles.outline);c.drawRect(pad,headerBottom-4f,w-pad,headerBottom-1.5f,p)}
            ThemeLanguage.OUTLINE->{fill(roles.outline);c.drawRect(pad,headerBottom-3f,w-pad,headerBottom-1.75f,p)}
            ThemeLanguage.GLOW_EDGE->{fill(ColorUtils.setAlphaComponent(Color.WHITE,24));c.drawRect(pad,headerBottom-2f,w-pad,headerBottom-1f,p)}
            ThemeLanguage.JAPANDI->{fill(ColorUtils.setAlphaComponent(accent,200));c.drawRect(titleX,headerBottom-3f,titleX+22f,headerBottom-2f,p)}
            else->Unit
        }
        text.draw(m.title,titleX,pad+l.header*.40f,w-titleX-pad-if(headerAction)28f+actionInset else 0f,14*scale,l.header*.75f,headerInk,true)
    }

    // Rows: the shared layout, painted on language cards.
    var top=headerBottom
    val rows=m.rows.take(l.rows)
    if(rows.isEmpty())text.draw(m.emptyMessage,pad,(top+16).coerceAtMost(h-pad-8),w-2*pad,12*scale,26*scale,ink)
    val room=when(language) {ThemeLanguage.NEO_BRUTAL->3f;ThemeLanguage.SOFT_RELIEF,ThemeLanguage.CLAY,ThemeLanguage.ORGANIC->3.5f;else->0f}
    rows.forEachIndexed {index,row->
        val card=RectF(pad+room*.4f,top+1.5f+room*.3f,w-pad-room,top+l.rowHeight-1.5f-room)
        val cardColor=drawLanguageRowCard(c,p,language,theme,card,radius,index,rows.size,roles,bg)
        val onCard=if(cardColor==null) ink else readable(roles.onSurface,cardColor)
        val accentOnCard=readable(accent,cardColor ?: bg)
        val rh=l.rowHeight-l.journeyLinkHeight
        val left=card.left+if(cardColor==null && language!=ThemeLanguage.GLOW_EDGE) 2f else 7f
        val right=card.right-if(cardColor==null) 2f else 7f
        val cw=(right-left).coerceAtLeast(1f)
        c.withClip(card.left,top,card.right,top+rh) {
        if(m.isDesignPreview) {
            text.draw(row.destination,left,top+rh/2,cw,16*scale,rh*.7f,onCard,true)
        } else {
            val clockWidth=min(94*scale,cw*.35f)
            val lineWidth=min(56*scale,cw*.21f)
            val clock=if(row.delay.cancelled)tr("Fällt aus")else clockTime(row.time)
            val late=row.delay.minutes?.takeIf {it>0 && !row.delay.cancelled}?.let {" +$it"}.orEmpty()
            val timeInk=when {
                row.delay.cancelled || late.isNotEmpty()->readable(0xffd0343f.toInt(),cardColor ?: bg)
                language in setOf(ThemeLanguage.NEO_BRUTAL,ThemeLanguage.COLOR_BLOCK,ThemeLanguage.OUTLINE,ThemeLanguage.JAPANDI)->onCard
                else->accentOnCard
            }
            fun badge(x:Float,cy:Float,width:Float,height:Float,size:Float) {
                val box=RectF(x,cy-height/2,x+width,cy+height/2)
                val badgeInk=drawLanguageBadge(c,p,language,theme,box,radius,roles,accentOnCard)
                text.draw(row.lineName,x,cy,width,size,height*.9f,badgeInk,true,true)
            }
            if(l.singleLine) {
                badge(left,top+rh/2,lineWidth-5,rh*.78f,11f*scale)
                text.draw(row.destination,left+lineWidth,top+rh/2,cw-lineWidth-clockWidth-4,11.5f*scale,rh*.8f,onCard)
                text.draw(clock+late,right-clockWidth,top+rh/2,clockWidth,13*scale,rh*.75f,timeInk,true)
            } else if(l.narrow) {
                val detail=row.platform?.let {platformName(it,row.lineType)} ?: row.lineType.label
                text.draw(row.lineName+" · "+detail,left,top+rh*.2f,cw,10.5f*scale,rh*.27f,accentOnCard,true)
                text.draw(row.destination,left,top+rh*.48f,cw,12*scale,rh*.31f,onCard,true)
                text.draw(clock+late,left,top+rh*.79f,cw,13*scale,rh*.30f,timeInk,true)
            } else {
                val badgeWidth=min(45*scale,cw*.19f)
                badge(left,top+rh*.5f,badgeWidth-5,rh*.62f,11*scale)
                text.draw(row.destination,left+badgeWidth,top+rh*.32f,cw-clockWidth-badgeWidth-5,12.5f*scale,rh*.48f,onCard,true)
                text.draw(row.platform?.let {platformName(it,row.lineType)} ?: row.lineType.label,left+badgeWidth,top+rh*.72f,
                    cw-clockWidth-badgeWidth-5,10.2f*scale,rh*.38f,onCard)
                text.draw(clock+late,right-clockWidth,top+rh*.5f,clockWidth,13*scale,rh*.75f,timeInk,true)
            }
        }
        }
        top+=l.rowHeight
    }
    if(!m.isDesignPreview && l.footer>0)text.draw(m.caption,footerX,h-pad-l.footer/2,w-footerX-pad,10*scale,l.footer*.85f,ink)
    if(headerAction)text.draw("⋮",w-26-actionInset,18f,20f,18f,24f,if(language==ThemeLanguage.COLOR_BLOCK) roles.onFill else accent,true)
    return true
}

/** Board ground: the same single quiet signature as the app background, or nothing. */
private fun drawLanguageBoardGround(c:Canvas,theme:AppTheme,w:Float,h:Float,colors:BoardColors,roles:BoardLanguageRoles,decoration:Boolean) {
    if(!decoration || w<=0f || h<=0f) return
    val p=Paint(Paint.ANTI_ALIAS_FLAG);val m=min(w,h);val dark=theme.dark
    val bg=colors.background.toInt();val accent=colors.accent.toInt()
    fun alpha(color:Int,a:Float)=ColorUtils.setAlphaComponent(color,(a*255).roundToInt().coerceIn(0,255))
    when(theme.themeLanguage) {
        ThemeLanguage.SOFT_RELIEF->{p.shader=LinearGradient(0f,0f,w,h,intArrayOf(alpha(Color.WHITE,if(dark).04f else .30f),
            Color.TRANSPARENT,alpha(Color.BLACK,if(dark).14f else .05f)),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,p)}
        ThemeLanguage.CLAY->{
            p.shader=RadialGradient(w*.96f,h*.05f,m*.8f,alpha(roles.fill,.20f),Color.TRANSPARENT,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,p)
            p.shader=RadialGradient(w*.04f,h*.98f,m*.7f,alpha(roles.secondaryFill,.18f),Color.TRANSPARENT,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,p)
        }
        ThemeLanguage.TONAL->{p.shader=LinearGradient(0f,0f,0f,h,bg,ColorUtils.blendARGB(bg,roles.primary,if(dark).07f else .06f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,p)}
        ThemeLanguage.ORGANIC->{p.color=alpha(roles.secondaryFill,if(dark).22f else .30f)
            c.drawPath(organicBlob(androidx.compose.ui.geometry.Offset(w*.94f,h*.06f),m*.34f,theme.ordinal).asAndroidPath(),p)}
        ThemeLanguage.GLOW_EDGE->{p.shader=RadialGradient(w*.5f,-h*.1f,max(w,h*.6f),intArrayOf(alpha(accent,.15f),alpha(accent,.04f),Color.TRANSPARENT),
            floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,p)}
        else->Unit
    }
    p.shader=null
}

/** The theme silhouette for a row card (cut, squircle, pebble or rounded corners). */
private fun languagePath(theme:AppTheme,box:RectF,radius:Float):Path {
    val r=radius.coerceAtMost(min(box.width(),box.height())/2)
    return when(theme.shapeStyle) {
        ThemeShape.CUT,ThemeShape.BEVEL->Path().apply {val k=r*.55f
            moveTo(box.left+k,box.top);lineTo(box.right-k,box.top);lineTo(box.right,box.top+k);lineTo(box.right,box.bottom-k)
            lineTo(box.right-k,box.bottom);lineTo(box.left+k,box.bottom);lineTo(box.left,box.bottom-k);lineTo(box.left,box.top+k);close()}
        ThemeShape.SQUIRCLE->Path(squirclePath(androidx.compose.ui.geometry.Size(box.width(),box.height()),r,r,r,r).asAndroidPath()).apply {offset(box.left,box.top)}
        ThemeShape.PEBBLE->Path().apply {val lim=min(box.width(),box.height())/2
            val a=min(r*1.35f,lim);val b=min(r*.55f,lim);val cc=min(r*1.2f,lim);val d=min(r*.7f,lim)
            addRoundRect(box,floatArrayOf(a,a,b,b,cc,cc,d,d),Path.Direction.CW)}
        else->Path().apply {addRoundRect(box,r,r,Path.Direction.CW)}
    }
}

/** Paints one row card; returns its fill colour, or null when the row stays on the board ground. */
private fun drawLanguageRowCard(c:Canvas,p:Paint,language:ThemeLanguage,theme:AppTheme,box:RectF,radius:Float,
    index:Int,count:Int,roles:BoardLanguageRoles,bg:Int):Int? {
    val dark=theme.dark
    val path=languagePath(theme,box,radius)
    fun fill(color:Int){p.reset();p.isAntiAlias=true;p.style=Paint.Style.FILL;p.color=color}
    fun stroke(color:Int,width:Float){p.reset();p.isAntiAlias=true;p.style=Paint.Style.STROKE;p.strokeWidth=width;p.color=color;p.strokeJoin=Paint.Join.ROUND}
    fun rule(color:Int,width:Float){if(index<count-1){fill(color);c.drawRect(box.left,box.bottom+1.5f-width,box.right,box.bottom+1.5f,p)}}
    return when(language) {
        ThemeLanguage.NEO_BRUTAL->{
            fill(if(dark) roles.fill else roles.outline);c.save();c.translate(2.5f,2.5f);c.drawPath(path,p);c.restore()
            fill(roles.surface);c.drawPath(path,p);stroke(roles.outline,1.5f);c.drawPath(path,p);roles.surface
        }
        ThemeLanguage.SOFT_RELIEF->{
            fill(roles.surface);p.setShadowLayer(3.5f,2f,2.2f,ColorUtils.setAlphaComponent(Color.BLACK,if(dark) 120 else 40));c.drawPath(path,p)
            p.setShadowLayer(3.5f,-1.8f,-1.8f,ColorUtils.setAlphaComponent(Color.WHITE,if(dark) 14 else 210));c.drawPath(path,p);p.clearShadowLayer()
            roles.surface
        }
        ThemeLanguage.CLAY->{
            val tint=ColorUtils.blendARGB(roles.surface,if(index%2==0) roles.fill else roles.secondaryFill,.28f)
            fill(tint);p.setShadowLayer(4f,0f,2.4f,ColorUtils.setAlphaComponent(ColorUtils.blendARGB(roles.fill,Color.BLACK,.35f),if(dark) 120 else 70))
            c.drawPath(path,p);p.clearShadowLayer()
            c.withClip(path) {
                p.shader=LinearGradient(0f,box.top,0f,box.top+box.height()*.5f,ColorUtils.setAlphaComponent(Color.WHITE,if(dark) 30 else 110),Color.TRANSPARENT,Shader.TileMode.CLAMP)
                c.drawRect(box,p);p.shader=null
            }
            tint
        }
        ThemeLanguage.OUTLINE->{rule(roles.outline,1f);null}
        ThemeLanguage.BENTO->{fill(roles.surface);c.drawPath(path,p);stroke(roles.outlineVariant,1f);c.drawPath(path,p);roles.surface}
        ThemeLanguage.TONAL->{val tone=ColorUtils.blendARGB(roles.surface,roles.primary,if(index%2==0) .04f else .10f)
            fill(tone);c.drawPath(path,p);tone}
        ThemeLanguage.COLOR_BLOCK->{fill(roles.surface);c.drawPath(path,p);roles.surface}
        ThemeLanguage.JAPANDI->{rule(roles.outlineVariant,1f);null}
        ThemeLanguage.ORGANIC->{
            val tint=ColorUtils.blendARGB(roles.surface,roles.secondaryFill,if(index%2==0) .10f else .22f)
            fill(tint);p.setShadowLayer(4f,0f,2f,ColorUtils.setAlphaComponent(ColorUtils.blendARGB(roles.primary,Color.BLACK,.4f),if(dark) 90 else 45))
            c.drawPath(path,p);p.clearShadowLayer();tint
        }
        ThemeLanguage.GLOW_EDGE->{
            fill(ColorUtils.blendARGB(bg,roles.surface,.85f));c.drawPath(path,p)
            p.reset();p.isAntiAlias=true;p.style=Paint.Style.STROKE;p.strokeWidth=1f
            p.shader=LinearGradient(0f,box.top,0f,box.bottom,ColorUtils.setAlphaComponent(Color.WHITE,46),ColorUtils.setAlphaComponent(Color.WHITE,12),Shader.TileMode.CLAMP)
            c.drawPath(path,p);p.shader=null;ColorUtils.blendARGB(bg,roles.surface,.85f)
        }
        ThemeLanguage.STANDARD->null
    }
}

/** Paints a line badge in the language; returns the ink for its label. */
private fun drawLanguageBadge(c:Canvas,p:Paint,language:ThemeLanguage,theme:AppTheme,box:RectF,radius:Float,
    roles:BoardLanguageRoles,accent:Int):Int {
    val r=(radius*.7f).coerceAtMost(min(box.width(),box.height())/2)
    val path=languagePath(theme,box,r)
    fun fill(color:Int){p.reset();p.isAntiAlias=true;p.style=Paint.Style.FILL;p.color=color}
    fun stroke(color:Int,width:Float){p.reset();p.isAntiAlias=true;p.style=Paint.Style.STROKE;p.strokeWidth=width;p.color=color}
    return when(language) {
        ThemeLanguage.NEO_BRUTAL->{fill(roles.fill);c.drawPath(path,p);stroke(roles.outline,1.25f);c.drawPath(path,p);roles.onFill}
        ThemeLanguage.CLAY,ThemeLanguage.COLOR_BLOCK,ThemeLanguage.TONAL,ThemeLanguage.BENTO,ThemeLanguage.SOFT_RELIEF,ThemeLanguage.ORGANIC->{
            fill(roles.fill);c.drawPath(path,p);roles.onFill}
        ThemeLanguage.OUTLINE->{stroke(roles.outline,1.1f);c.drawPath(path,p);roles.onSurface}
        ThemeLanguage.JAPANDI->{stroke(ColorUtils.setAlphaComponent(accent,210),1f);c.drawPath(path,p);accent}
        ThemeLanguage.GLOW_EDGE->{stroke(accent,1f);p.setShadowLayer(2.5f,0f,0f,ColorUtils.setAlphaComponent(accent,150));c.drawPath(path,p);p.clearShadowLayer();accent}
        ThemeLanguage.STANDARD->accent
    }
}
