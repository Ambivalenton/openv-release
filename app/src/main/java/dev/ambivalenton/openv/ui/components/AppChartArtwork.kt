// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.theme.*
import dev.ambivalenton.openv.rendering.*
import androidx.compose.ui.unit.Dp
import kotlin.math.*

/** The current app's actual shape, roles and vector textures; no parallel set of 48 imitations. */
data class AppChartArtwork(val visuals:AppVisuals,val scheme:ColorScheme,val quiet:Boolean=false)

internal fun appChartOutline(art:AppChartArtwork,size:Size,density:Density,direction:LayoutDirection)=
    art.visuals.surfaceShape(24.dp).createOutline(size,direction,density)

/** Border language of the app theme, shared by the ride bars and their time chips.
 * Graphic designs draw a solid ink outline, Art déco a finer double weight, all others a hairline. */
internal fun AppTheme?.chartGraphicBorder()=this in setOf(AppTheme.INDUSTRIAL,AppTheme.COMIC_POP,AppTheme.BRUTALIST,AppTheme.SWISS_METRO) ||
    themeLanguageOrStandard in setOf(ThemeLanguage.NEO_BRUTAL,ThemeLanguage.OUTLINE) || this?.language?.graphicOutline==true
internal fun AppTheme?.chartBorderWidth()=when {
    themeLanguageOrStandard==ThemeLanguage.OUTLINE->1.25.dp
    this?.language==DesignLanguage.PIXEL->2.dp
    this?.language==DesignLanguage.INK->1.5.dp
    chartGraphicBorder()->2.5.dp
    this==AppTheme.ART_DECO || this?.language==DesignLanguage.EDITORIAL || this?.language==DesignLanguage.AURORA->1.5.dp
    else->1.dp
}

private fun Outline.asChartPath()=Path().apply {when(val o=this@asChartPath) {
    is Outline.Generic->addPath(o.path);is Outline.Rounded->addRoundRect(o.roundRect);is Outline.Rectangle->addRect(o.rect)
}}

fun DrawScope.drawAppChartBar(ink:TimelineInk,art:AppChartArtwork,backdrop:FullThemeBackdrop?=null) {
    if(size.width<=0 || size.height<=0)return
    val theme=art.visuals.theme;val texture=theme?.texture?:ThemeTexture.NONE
    val path=appChartOutline(art,size,this,layoutDirection).asChartPath()
    val line=ink.rail;val w=size.width;val h=size.height
    val neon=theme in setOf(AppTheme.SYNTHWAVE,AppTheme.CYBERPUNK_NEON,AppTheme.RETRO_ARCADE)
    val graphic=theme.chartGraphicBorder()
    val paper=theme in setOf(AppTheme.PAPER_CUT,AppTheme.ORIGAMI,AppTheme.PARCHMENT,AppTheme.KRAFT_STUDIO,AppTheme.NEWSPRINT)
    val fill=if(neon)lerp(art.scheme.surfaceContainerLow,line,.15f) else ink.container
    if(theme?.isOperatingSystem==true){
        clipPath(path){drawIntoCanvas{drawSystemRideBar(it.nativeCanvas,theme,w,h,art.scheme.systemSurfacePalette(),ink.container.toArgb(),density)}}
        return
    }
    if(theme!=null && theme.themeLanguage!=ThemeLanguage.STANDARD){drawLanguageChartBar(ink,art,path,theme.themeLanguage);return}
    if(theme!=null && theme.language!=DesignLanguage.MATERIAL){drawLanguageChartBar(theme.language,path,ink,art);return}
    clipPath(path) {
        drawRect(fill)
        if(!art.quiet) when(texture) {
            ThemeTexture.GRID,ThemeTexture.BLUEPRINT->{
                val step=if(texture==ThemeTexture.BLUEPRINT)12.dp.toPx() else 16.dp.toPx()
                var x=0f;while(x<w){drawLine(line.copy(alpha=.15f),Offset(x,0f),Offset(x,h),.6.dp.toPx());x+=step}
                var y=0f;while(y<h){drawLine(line.copy(alpha=.15f),Offset(0f,y),Offset(w,y),.6.dp.toPx());y+=step}
                if(texture==ThemeTexture.BLUEPRINT)for(i in 0..(h/step).toInt().coerceAtMost(100))
                    drawLine(line.copy(alpha=.6f),Offset(0f,i*step),Offset(min(5.dp.toPx(),w*.2f),i*step),.7.dp.toPx())
            }
            ThemeTexture.PAPER,ThemeTexture.CONCRETE->{
                var y=3.dp.toPx();var row=0
                while(y<h){var x=(row%3)*2.dp.toPx();while(x<w){drawCircle(ink.content.copy(alpha=.10f),.55.dp.toPx(),Offset(x,y));x+=7.dp.toPx()};y+=7.dp.toPx();row++}
            }
            ThemeTexture.WOOD->{
                var y=4.dp.toPx();while(y<h){drawLine(line.copy(alpha=.16f),Offset(0f,y),Offset(w,y+5.dp.toPx()),.7.dp.toPx());y+=8.dp.toPx()}
                drawLine(ink.content.copy(alpha=.18f),Offset(0f,h/2),Offset(w,h/2),1.dp.toPx())
                drawRect(line.copy(alpha=.18f),Offset(0f,h/2-2.dp.toPx()),Size(min(4.dp.toPx(),w*.1f),4.dp.toPx()))
                drawRect(line.copy(alpha=.18f),Offset(w-min(4.dp.toPx(),w*.1f),h/2-2.dp.toPx()),Size(min(4.dp.toPx(),w*.1f),4.dp.toPx()))
            }
            ThemeTexture.GLASS->{
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha=.48f),Color.Transparent,line.copy(alpha=.10f))))
                drawLine(Color.White.copy(alpha=.65f),Offset(3.dp.toPx(),2.dp.toPx()),Offset(w-3.dp.toPx(),2.dp.toPx()),1.dp.toPx())
                drawLine(Color.White.copy(alpha=.24f),Offset(2.dp.toPx(),3.dp.toPx()),Offset(2.dp.toPx(),h-3.dp.toPx()),1.dp.toPx())
            }
            ThemeTexture.NONE->if(theme==AppTheme.BUBBLEGUM)
                drawRect(Brush.verticalGradient(listOf(art.scheme.primaryContainer,art.scheme.secondaryContainer)))
            else->drawDecorativeThemeTexture(texture,line,cachedBackdrop=backdrop,background=fill)
        }
        if(paper&&!art.quiet) {
            val fold=min(8.dp.toPx(),min(w,h)*.18f)
            drawPath(Path().apply {moveTo(w-fold,h);lineTo(w-fold,h-fold);lineTo(w,h-fold);close()},line.copy(alpha=.22f))
        }
        if(theme==AppTheme.POCKET_CONSOLE&&!art.quiet) {
            val unit=min(4.dp.toPx(),min(w,h)/5)
            for(i in 0..(h/(unit*3).coerceAtLeast(1f)).toInt().coerceAtMost(100))
                drawRect(line.copy(alpha=.32f),Offset(w-unit*2,i*unit*3),Size(unit,unit))
        }
        val border=theme.chartBorderWidth().toPx().coerceAtMost(min(w,h)/3)
        if(neon&&!art.quiet)drawPath(path,line.copy(alpha=.22f),style=Stroke(8.dp.toPx()))
        drawPath(path,if(graphic)art.scheme.outline else line.copy(alpha=if(neon).95f else .60f),style=Stroke(border*2))
    }
}

/**
 * Ride bars in the design languages. Fill and edge follow the language; the shape is the
 * theme's own outline, so bars, chips, cards and the board share one silhouette. Time bounds, column
 * width and snapping are unchanged. Every stroke is centred on the outline and clipped to it, so the
 * visible edge is exactly its nominal width.
 */
internal fun DrawScope.drawLanguageChartBar(ink:TimelineInk,art:AppChartArtwork,path:Path,language:ThemeLanguage) {
    val w=size.width;val h=size.height;val m=min(w,h)
    val quiet=art.quiet;val s=art.scheme
    fun edge(color:Color,width:Dp)=drawPath(path,color,style=Stroke((width.toPx()*2f).coerceAtMost(m)))
    fun edge(brush:Brush,width:Dp)=drawPath(path,brush,style=Stroke((width.toPx()*2f).coerceAtMost(m)))
    clipPath(path) {
        when(language) {
            ThemeLanguage.OUTLINE->drawRect(ink.rail.copy(alpha=.07f))
            else->drawRect(ink.container)
        }
        if(!quiet) when(language) {
            ThemeLanguage.SOFT_RELIEF->drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha=.38f),Color.Transparent,
                Color.Black.copy(alpha=.10f)),Offset.Zero,Offset(w,h)))
            ThemeLanguage.CLAY->{
                val glossH=min(h*.34f,w*1.1f)
                drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha=.55f),Color.White.copy(alpha=0f)),0f,glossH),
                    Offset(w*.16f,min(3.dp.toPx(),h*.05f)),Size(w*.68f,glossH))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.10f)),h*.62f,h))
            }
            ThemeLanguage.ORGANIC->drawCircle(Color.White.copy(alpha=.16f),m*.55f,Offset(w*.28f,min(h*.14f,w*.4f)))
            ThemeLanguage.GLOW_EDGE->drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=.12f),Color.Transparent),0f,max(h*.3f,1f)))
            ThemeLanguage.BENTO->drawLine(Color.White.copy(alpha=.45f),Offset(w*.2f,1.dp.toPx()),Offset(w*.8f,1.dp.toPx()),1.dp.toPx())
            else->Unit
        }
        when(language) {
            ThemeLanguage.NEO_BRUTAL->edge(s.outline,2.5.dp)
            ThemeLanguage.OUTLINE->{
                edge(ink.rail,1.5.dp)
                // Start and end ticks make an open bar readable as one ride even without a fill.
                val tick=min(5.dp.toPx(),h/4f);val stroke=1.5.dp.toPx()
                drawLine(ink.rail,Offset(w*.25f,tick),Offset(w*.75f,tick),stroke,StrokeCap.Round)
                drawLine(ink.rail,Offset(w*.25f,h-tick),Offset(w*.75f,h-tick),stroke,StrokeCap.Round)
            }
            ThemeLanguage.SOFT_RELIEF->edge(Brush.linearGradient(listOf(Color.White.copy(alpha=.65f),Color.Transparent,
                Color.Black.copy(alpha=.14f)),Offset.Zero,Offset(w,h)),1.dp)
            ThemeLanguage.CLAY->edge(Brush.verticalGradient(listOf(Color.White.copy(alpha=.7f),Color.White.copy(alpha=0f)),0f,max(h*.5f,1f)),1.5.dp)
            ThemeLanguage.BENTO->edge(lerp(ink.rail,ink.container,.55f).copy(alpha=.6f),1.dp)
            ThemeLanguage.TONAL->edge(ink.rail.copy(alpha=.32f),1.dp)
            ThemeLanguage.JAPANDI->{
                val y=.75.dp.toPx();val stroke=1.dp.toPx()
                drawLine(ink.rail.copy(alpha=.55f),Offset(0f,y),Offset(w,y),stroke)
                drawLine(ink.rail.copy(alpha=.55f),Offset(0f,h-y),Offset(w,h-y),stroke)
            }
            ThemeLanguage.GLOW_EDGE->edge(ink.rail.copy(alpha=.8f),1.dp)
            else->Unit
        }
    }
}

/**
 * Ride bars in the design language of the app theme. The bar keeps its time bounds; only
 * fill, outline and the small material details change. Quiet mode keeps fill and outline only.
 */
private fun DrawScope.drawLanguageChartBar(language:DesignLanguage,path:Path,ink:TimelineInk,art:AppChartArtwork) {
    val w=size.width;val h=size.height;val c=ink.container;val line=ink.rail;val quiet=art.quiet
    val bg=art.scheme.background;val onSurface=art.scheme.onSurface;val dark=bg.luminance()<.3f
    fun stroke(color:Color,width:Float)=drawPath(path,color,style=Stroke(width*2))   // clipped: half lies inside
    fun stroke(brush:Brush,width:Float)=drawPath(path,brush,style=Stroke(width*2))
    clipPath(path) {
        when(language) {
            DesignLanguage.MATERIAL,DesignLanguage.FLAT,DesignLanguage.BRUTAL->drawRect(c)
            DesignLanguage.SOFT->{drawRect(lerp(c,Color.White,if(dark).03f else .08f))
                if(!quiet){drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=if(dark).10f else .38f),Color.Transparent),endY=min(h*.45f,28.dp.toPx())))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=if(dark).22f else .10f)),startY=h-min(h*.4f,22.dp.toPx())))}}
            DesignLanguage.CLAY->{drawRect(Brush.verticalGradient(listOf(lerp(c,Color.White,.24f),c,lerp(c,Color.Black,.12f))))
                if(!quiet){val hh=min(9.dp.toPx(),h*.22f);drawOval(Color.White.copy(alpha=.38f),Offset(w*.18f,min(4.dp.toPx(),h*.1f)),Size(w*.64f,hh))}}
            DesignLanguage.GLASS->{drawRect(c.copy(alpha=.55f))
                if(!quiet)drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha=if(dark).18f else .45f),Color.Transparent),Offset.Zero,Offset(w,min(h,w*2f))))}
            DesignLanguage.AURORA->drawRect(Brush.verticalGradient(listOf(c,lerp(c,art.scheme.secondary,.35f))))
            DesignLanguage.SWISS->{drawRect(c);drawRect(line,size=Size(min(4.dp.toPx(),w*.22f),h))}
            DesignLanguage.EDITORIAL->{drawRect(lerp(c,bg,.5f))
                drawRect(line,size=Size(w,min(2.dp.toPx(),h/4f)));drawRect(line,Offset(0f,h-min(1.dp.toPx(),h/6f)),Size(w,min(1.dp.toPx(),h/6f)))}
            DesignLanguage.OUTLINE->drawRect(c.copy(alpha=.14f))
            DesignLanguage.BENTO->drawRect(c)
            DesignLanguage.INK->{drawRect(lerp(bg,c,.3f))
                if(!quiet){val step=5.dp.toPx();var x=-h;var n=0
                    while(x<w && n<400){drawLine(onSurface.copy(alpha=.28f),Offset(x,h),Offset(x+h,0f),.8.dp.toPx());x+=step;n++}}}
            DesignLanguage.TERMINAL->{drawRect(c.copy(alpha=.16f))
                val tick=min(4.dp.toPx(),w*.2f);var y=8.dp.toPx();var n=0
                while(y<h && n<200){drawLine(line,Offset(0f,y),Offset(tick,y),1.dp.toPx());y+=12.dp.toPx();n++}}
            DesignLanguage.NEON->drawRect(lerp(art.scheme.surfaceContainerLow,line,.15f))
            DesignLanguage.PIXEL->{drawRect(c)
                if(!quiet){val u=min(3.dp.toPx(),w/6f);var y=h-u*4;var row=0
                    while(y<h && row<8){var x=(row%2)*u;while(x<w){drawRect(lerp(c,Color.Black,.18f),Offset(x,y),Size(u,u));x+=u*2};y+=u;row++}}}
            DesignLanguage.CHROME->{drawRect(Brush.verticalGradient(0f to lerp(c,Color.White,.5f),.48f to lerp(c,Color.White,.12f),.5f to c,1f to lerp(c,Color.Black,.16f)))
                if(!quiet)drawLine(Color.White.copy(alpha=.8f),Offset(3.dp.toPx(),1.5.dp.toPx()),Offset(w-3.dp.toPx(),1.5.dp.toPx()),1.dp.toPx())}
            DesignLanguage.STITCH->{drawRect(c)
                if(!quiet) drawWeave(onSurface.copy(alpha=if(dark).06f else .05f),3.dp.toPx())
                val inset=min(3.dp.toPx(),min(w,h)*.2f)
                if(w>inset*3 && h>inset*3) drawRect(lerp(c,if(dark) Color.White else Color.Black,.35f).copy(alpha=.7f),Offset(inset,inset),Size(w-2*inset,h-2*inset),
                    style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),3.dp.toPx()))))}
            DesignLanguage.PAPER->{drawRect(c)
                if(!quiet){val fold=min(8.dp.toPx(),min(w,h)*.22f)
                    drawPath(Path().apply {moveTo(w-fold,h);lineTo(w-fold,h-fold);lineTo(w,h-fold);close()},lerp(c,Color.Black,.18f))}}
        }
        when(language) {
            DesignLanguage.BRUTAL->stroke(onSurface,2.5.dp.toPx())
            DesignLanguage.PIXEL->stroke(onSurface,2.dp.toPx())
            DesignLanguage.INK->stroke(onSurface,1.5.dp.toPx())
            DesignLanguage.OUTLINE->stroke(line,1.5.dp.toPx())
            DesignLanguage.GLASS->stroke(Color.White.copy(alpha=if(dark).35f else .8f),1.dp.toPx())
            DesignLanguage.AURORA->stroke(Brush.verticalGradient(listOf(line,art.scheme.secondary,art.scheme.tertiary)),1.5.dp.toPx())
            DesignLanguage.NEON->{if(!quiet)drawPath(path,line.copy(alpha=.22f),style=Stroke(8.dp.toPx()));stroke(line,1.2.dp.toPx())}
            DesignLanguage.TERMINAL->drawPath(path,line,style=Stroke(2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),3.dp.toPx()))))
            DesignLanguage.BENTO->stroke(lerp(c,Color.White,.35f).copy(alpha=.7f),1.dp.toPx())
            DesignLanguage.CHROME->stroke(Brush.verticalGradient(listOf(Color.White,lerp(onSurface,bg,.5f),Color.White)),1.dp.toPx())
            else->Unit
        }
    }
}

/** Drop shadow of a ride bar in a design language; null = no shadow at all (flat). */
internal fun languageBarShadow(art:AppChartArtwork?,ink:TimelineInk):FrameShadow? {
    val theme=art?.visuals?.theme ?: return null
    val dark=theme.dark
    return when(theme.themeLanguage) {
        ThemeLanguage.NEO_BRUTAL->HardShadow(2.5.dp,art.scheme.outline)
        ThemeLanguage.SOFT_RELIEF->ReliefShadow(2.5.dp,Color.White.copy(alpha=if(dark).05f else .8f),Color.Black.copy(alpha=if(dark).5f else .18f))
        ThemeLanguage.CLAY,ThemeLanguage.ORGANIC->HaloShadow(3.dp,lerp(ink.rail,Color.Black,.3f).copy(alpha=if(dark).45f else .25f),.6f)
        ThemeLanguage.GLOW_EDGE->HaloShadow(3.5.dp,ink.rail.copy(alpha=.32f),0f)
        else->null
    }
}
