// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import kotlin.math.min

/*
 * The material layer of the design languages. Every effect is a static vector drawn inside
 * the composable's own draw pass: no bitmaps, no animation clock, no platform elevation shadow (which
 * hung on concave outlines, see build 67). Quiet mode and "Hoher Textkontrast" remove all decoration
 * except outlines, so the structure of a surface never depends on an effect.
 */

/** Colours a language needs, resolved once from the active scheme. */
@Immutable data class LanguageInk(val language:DesignLanguage,val dark:Boolean,val background:Color,val surface:Color,
    val primary:Color,val secondary:Color,val tertiary:Color,val onSurface:Color,val outline:Color,val outlineVariant:Color,
    val quiet:Boolean) {
    val spec get()=language.spec
    val active get()=language!=DesignLanguage.MATERIAL
    /** Hard offset shadows: black ink on light designs, the accent on dark ones (expressionist colour shadow). */
    val hardInk get()=if(dark) primary.copy(alpha=.85f) else onSurface
}

@Composable fun languageInk():LanguageInk {
    val visuals=LocalAppVisuals.current;val scheme=MaterialTheme.colorScheme
    val quiet=LocalAppSettings.current.reduceMotion || visuals.readability.contrast==AppContrast.HIGH
    return scheme.languageInk(visuals.language,quiet)
}
fun ColorScheme.languageInk(language:DesignLanguage,quiet:Boolean=false)=LanguageInk(language,background.luminance()<.3f,
    background,surface,primary,secondary,tertiary,onSurface,outline,outlineVariant,quiet)

/** Fill of a card-like surface: [base] is the colour the call site asked for. */
fun LanguageInk.fill(base:Color):Color=when(spec.fill) {
    LanguageFill.CONTAINER->base
    LanguageFill.BACKGROUND->background
    LanguageFill.SURFACE->if(dark) lerp(surface,onSurface,.04f) else lerp(surface,Color.White,.6f)
    LanguageFill.GLASS->(if(dark) lerp(surface,onSurface,.05f) else lerp(surface,Color.White,.45f)).copy(alpha=if(dark).70f else .62f)
    LanguageFill.TINTED->lerp(base,primary,if(dark).12f else .09f)
}.let {if(language==DesignLanguage.MATERIAL) base else it}

/** Outline of a card-like surface, or null where the language draws none (or draws it itself). */
fun LanguageInk.border():BorderStroke?=when(language) {
    DesignLanguage.MATERIAL,DesignLanguage.FLAT,DesignLanguage.SOFT,DesignLanguage.CLAY,DesignLanguage.SWISS,
    DesignLanguage.PAPER,DesignLanguage.EDITORIAL,DesignLanguage.TERMINAL,DesignLanguage.STITCH->null
    DesignLanguage.GLASS->BorderStroke(1.dp,Brush.linearGradient(listOf(Color.White.copy(alpha=if(dark).30f else .9f),
        Color.White.copy(alpha=if(dark).06f else .25f),onSurface.copy(alpha=.10f))))
    DesignLanguage.AURORA->BorderStroke(spec.border.dp,Brush.linearGradient(listOf(primary,secondary,tertiary)))
    DesignLanguage.NEON->BorderStroke(spec.border.dp,primary.copy(alpha=.9f))
    DesignLanguage.CHROME->BorderStroke(1.dp,Brush.verticalGradient(listOf(Color.White.copy(alpha=.95f),
        lerp(onSurface,background,.55f),Color.White.copy(alpha=.7f))))
    DesignLanguage.OUTLINE->BorderStroke(1.dp,outline)
    DesignLanguage.BENTO->BorderStroke(1.dp,outlineVariant.copy(alpha=.6f))
    DesignLanguage.BRUTAL,DesignLanguage.INK,DesignLanguage.PIXEL->BorderStroke(spec.border.dp,onSurface)
}

/** Outer effect of a card-like surface (shadow, glow or stacked layers), drawn outside its bounds. */
fun Modifier.languageShadow(shape:Shape,ink:LanguageInk,lifted:Boolean=true):Modifier {
    if(!ink.active || !lifted) return this
    if(ink.quiet) return when(ink.spec.shadow) {
        // Structure stays: the offset ink of neo-brutalism is part of the contour.
        LanguageShadow.HARD,LanguageShadow.STEPPED->hardShadow(shape,ink.hardInk,if(ink.spec.shadow==LanguageShadow.STEPPED)3f else 4f)
        else->this
    }
    return when(ink.spec.shadow) {
        LanguageShadow.NONE->this
        LanguageShadow.SOFT->dropShadow(shape,Shadow(radius=10.dp,offset=DpOffset(0.dp,3.dp),
            color=lerp(Color.Black,ink.primary,.15f).copy(alpha=if(ink.dark).34f else .10f)))
        LanguageShadow.NEUMORPHIC->this
            .dropShadow(shape,Shadow(radius=12.dp,offset=DpOffset(5.dp,5.dp),color=Color.Black.copy(alpha=if(ink.dark).50f else .15f)))
            .dropShadow(shape,Shadow(radius=12.dp,offset=DpOffset((-5).dp,(-5).dp),color=Color.White.copy(alpha=if(ink.dark).055f else .92f)))
        LanguageShadow.CLAY->dropShadow(shape,Shadow(radius=18.dp,offset=DpOffset(0.dp,8.dp),
            color=lerp(ink.primary,Color.Black,.25f).copy(alpha=if(ink.dark).40f else .22f)))
        LanguageShadow.HARD->hardShadow(shape,ink.hardInk,4f)
        LanguageShadow.STEPPED->hardShadow(shape,ink.hardInk,3f)
        LanguageShadow.GLOW->dropShadow(shape,Shadow(radius=14.dp,color=ink.primary.copy(alpha=if(ink.dark).34f else .22f)))
        LanguageShadow.LAYERED->drawWithCache {
            val outline=shape.createOutline(size,layoutDirection,this)
            val back=lerp(ink.background,ink.secondary,if(ink.dark).30f else .22f)
            val middle=lerp(ink.background,ink.primary,if(ink.dark).26f else .18f)
            onDrawBehind {
                translate(5.dp.toPx(),6.dp.toPx()) {drawOutline(outline,back)}
                translate(2.5.dp.toPx(),3.dp.toPx()) {drawOutline(outline,middle)}
            }
        }
    }
}

/** A shadow without blur: the outline translated by [offsetDp] in ink. Works for every outline. */
fun Modifier.hardShadow(shape:Shape,color:Color,offsetDp:Float):Modifier=drawWithCache {
    val outline=shape.createOutline(size,layoutDirection,this)
    val o=offsetDp.dp.toPx()
    onDrawBehind {translate(o,o) {drawOutline(outline,color)}}
}

/** Inner effects drawn above the fill and below the content: sheen, clay light, stripes and rules. */
fun Modifier.languageInnerMaterial(shape:Shape,ink:LanguageInk):Modifier {
    if(!ink.active) return this
    val inner=if(!ink.quiet && ink.spec.shadow==LanguageShadow.CLAY) this
        .innerShadow(shape,Shadow(radius=10.dp,offset=DpOffset(0.dp,5.dp),color=Color.White.copy(alpha=if(ink.dark).10f else .55f)))
        .innerShadow(shape,Shadow(radius=12.dp,offset=DpOffset(0.dp,(-5).dp),color=Color.Black.copy(alpha=if(ink.dark).30f else .08f)))
    else this
    return inner.drawWithCache {
        val outline=shape.createOutline(size,layoutDirection,this)
        val path=Path().apply {addOutline(outline)}
        val w=size.width;val h=size.height
        onDrawBehind {
            when(ink.language) {
                DesignLanguage.GLASS->if(!ink.quiet) clipPath(path) {
                    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha=if(ink.dark).10f else .34f),Color.Transparent),
                        start=Offset.Zero,end=Offset(w*.55f,h*.9f)))
                }
                DesignLanguage.CHROME->if(!ink.quiet) clipPath(path) {
                    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha=.55f),.48f to Color.White.copy(alpha=.10f),
                        .5f to Color.Transparent,1f to Color.Black.copy(alpha=.06f)))
                }
                DesignLanguage.SWISS->clipPath(path) {drawRect(ink.primary,size=Size(min(5.dp.toPx(),w*.04f),h))}
                DesignLanguage.EDITORIAL->{
                    drawLine(ink.onSurface,Offset(0f,.75.dp.toPx()),Offset(w,.75.dp.toPx()),1.5.dp.toPx())
                    drawLine(ink.onSurface.copy(alpha=.35f),Offset(0f,h-.5.dp.toPx()),Offset(w,h-.5.dp.toPx()),1.dp.toPx())
                }
                DesignLanguage.TERMINAL->drawOutline(outline,ink.primary.copy(alpha=.7f),style=Stroke(1.dp.toPx(),
                    pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),3.dp.toPx()))))
                // Cloth: a fine weave under a running stitch just inside the edge, in the thread colour.
                DesignLanguage.STITCH->{
                    if(!ink.quiet) clipPath(path) {drawWeave(ink.onSurface.copy(alpha=if(ink.dark).05f else .045f),4.dp.toPx())}
                    drawSeam(outline,lerp(ink.secondary,ink.onSurface,.15f).copy(alpha=.75f),5.dp.toPx(),1.2.dp.toPx())
                }
                else->Unit
            }
        }
    }
}

/** The design's single backdrop accent. Plain is plain; none of these repeat as a pattern. */
internal fun DrawScope.drawLanguageBackdrop(theme:AppTheme,bg:Color,primary:Color,secondary:Color) {
    val w=size.width;val h=size.height;val dark=bg.luminance()<.3f
    when(theme.backdrop ?: theme.language.spec.backdrop) {
        LanguageBackdrop.PLAIN->Unit
        LanguageBackdrop.TINT->drawRect(Brush.verticalGradient(listOf(bg,lerp(bg,secondary,if(dark).06f else .05f))))
        // Build 111: glass designs float over a field of coloured light – four large soft lights in the
        // design's own hues, kept near the ground's brightness so every text on it stays readable.
        LanguageBackdrop.GLOW->if(theme.language==DesignLanguage.GLASS) drawGlassField(bg,primary,secondary,dark) else {
            val r1=maxOf(w,h)*.62f;val c1=Offset(w*.08f,h*.12f)
            drawCircle(Brush.radialGradient(listOf(primary.copy(alpha=if(dark).20f else .16f),Color.Transparent),c1,r1),r1,c1)
            val r2=maxOf(w,h)*.7f;val c2=Offset(w*.98f,h*.78f)
            drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha=if(dark).18f else .15f),Color.Transparent),c2,r2),r2,c2)
        }
        LanguageBackdrop.AURORA->{
            val band=Path().apply {moveTo(0f,h*.05f);cubicTo(w*.35f,h*.18f,w*.6f,-h*.02f,w,h*.12f);lineTo(w,h*.34f)
                cubicTo(w*.62f,h*.2f,w*.3f,h*.42f,0f,h*.3f);close()}
            drawPath(band,Brush.linearGradient(listOf(primary.copy(alpha=.0f),primary.copy(alpha=if(dark).16f else .10f),
                secondary.copy(alpha=if(dark).18f else .11f),secondary.copy(alpha=0f)),start=Offset(0f,0f),end=Offset(w,h*.3f)))
        }
        LanguageBackdrop.HORIZON->{
            // One large, faint disc low on the right: a sun, a moon or an ensō – never a texture.
            val r=min(w,h)*.42f;val c=Offset(w*.86f,h*.74f)
            drawCircle(lerp(bg,primary,if(dark).10f else .075f),r,c)
            drawCircle(lerp(bg,secondary,if(dark).10f else .06f),r*.62f,c+Offset(-r*.18f,r*.12f))
        }
        LanguageBackdrop.CRESCENT->{
            val r=min(w,h)*.22f;val c=Offset(w*.82f,h*.16f)
            val moon=Path().apply {addOval(Rect(c,r))}
            val bite=Path().apply {addOval(Rect(c+Offset(r*.42f,-r*.18f),r*.92f))}
            drawPath(Path.combine(PathOperation.Difference,moon,bite),primary.copy(alpha=if(dark).14f else .10f))
        }
        LanguageBackdrop.DOTS->{
            val step=28.dp.toPx();val ink=primary.copy(alpha=.10f);val r=.9.dp.toPx()
            val cols=(w/step).toInt().coerceAtMost(60);val rows=(h/step).toInt().coerceAtMost(120)
            for(x in 1..cols) for(y in 1..rows) drawCircle(ink,r,Offset(x*step,y*step))
        }
    }
}

/** Flag colours of the rainbow designs; drawn as a slim band, no longer as a full background. */
fun prideStripes(theme:AppTheme?):List<Long> = when(theme) {
    AppTheme.RAINBOW_CLASSIC->listOf(0xFFE40303,0xFFFF8C00,0xFFFFED00,0xFF008026,0xFF2446AD,0xFF732982)
    AppTheme.RAINBOW_SKY->listOf(0xFF5BCEFA,0xFFF5A9B8,0xFFFFFFFF,0xFFF5A9B8,0xFF5BCEFA)
    AppTheme.RAINBOW_BERRY->listOf(0xFFD60270,0xFFD60270,0xFF9B4F96,0xFF0038A8,0xFF0038A8)
    AppTheme.RAINBOW_CITRUS->listOf(0xFFFF218C,0xFFFFD800,0xFF21B1FF)
    AppTheme.RAINBOW_MOON->listOf(0xFFFFF430,0xFFFFFFFF,0xFF9C59D1,0xFF2C2C2C)
    AppTheme.RAINBOW_SUNSET->listOf(0xFFD52D00,0xFFEF7627,0xFFFF9A56,0xFFFFFFFF,0xFFD162A4,0xFFB55690,0xFFA30262)
    AppTheme.RAINBOW_TIDAL->listOf(0xFF078D70,0xFF26CEAA,0xFF98E8C1,0xFFFFFFFF,0xFF7BADE2,0xFF5049CC,0xFF3D1A78)
    AppTheme.RAINBOW_AMETHYST->listOf(0xFF141414,0xFFA3A3A3,0xFFFFFFFF,0xFF800080)
    AppTheme.RAINBOW_FERN->listOf(0xFF3DA542,0xFFA7D379,0xFFFFFFFF,0xFFA9A9A9,0xFF242424)
    AppTheme.RAINBOW_SOLAR->listOf(0xFFFFD800,0xFF7902AA,0xFFFFD800)
    AppTheme.RAINBOW_FLUID->listOf(0xFFFF75A2,0xFFFFFFFF,0xFFBE18D6,0xFF202020,0xFF333EBD)
    AppTheme.RAINBOW_MEADOW->listOf(0xFF202020,0xFFB9B9B9,0xFFFFFFFF,0xFFB8F483,0xFFFFFFFF,0xFFB9B9B9,0xFF202020)
    AppTheme.RAINBOW_LILAC->listOf(0xFFB57EDC,0xFFFFFFFF,0xFF4A8123)
    else->emptyList()
}

/** A horizontal band of [stripes] between [top] and [top]+[height] (thin vertical segments, left to right). */
fun DrawScope.drawStripeBand(stripes:List<Long>,top:Float,height:Float,left:Float=0f,right:Float=size.width) {
    if(stripes.isEmpty() || height<=0f) return
    val segment=(right-left)/stripes.size
    stripes.forEachIndexed {i,c->drawRect(Color(c),Offset(left+i*segment,top),Size(segment+.5f,height))}
}

/** Header treatment below the status bar ([top]) of a language theme. */
internal fun DrawScope.drawLanguageHeader(theme:AppTheme,ink:LanguageInk,top:Float) {
    val w=size.width;val h=size.height;val bottom=h
    when(theme.language.spec.header) {
        LanguageHeader.NONE->Unit
        LanguageHeader.HAIRLINE->drawLine(ink.outlineVariant,Offset(0f,bottom-.5.dp.toPx()),Offset(w,bottom-.5.dp.toPx()),1.dp.toPx())
        LanguageHeader.RULE->drawRect(ink.onSurface,Offset(0f,bottom-2.5.dp.toPx()),Size(w,2.5.dp.toPx()))
        LanguageHeader.DOUBLE->{
            drawRect(ink.onSurface,Offset(16.dp.toPx(),bottom-5.dp.toPx()),Size(w-32.dp.toPx(),1.5.dp.toPx()))
            drawRect(ink.onSurface.copy(alpha=.7f),Offset(16.dp.toPx(),bottom-1.75.dp.toPx()),Size(w-32.dp.toPx(),.75.dp.toPx()))
        }
        LanguageHeader.BLOCK->drawRect(ink.primary,Offset(0f,top),Size(w,(bottom-top).coerceAtLeast(0f)))
        LanguageHeader.GRADIENT->drawRect(Brush.horizontalGradient(listOf(ink.primary,ink.secondary,ink.tertiary)),
            Offset(0f,bottom-2.dp.toPx()),Size(w,2.dp.toPx()))
        LanguageHeader.DASHED->drawLine(ink.primary.copy(alpha=.8f),Offset(0f,bottom-1.dp.toPx()),Offset(w,bottom-1.dp.toPx()),1.dp.toPx(),
            pathEffect=PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),4.dp.toPx())))
        LanguageHeader.GLASS->{
            drawRect(lerp(ink.surface,Color.White,if(ink.dark).04f else .5f).copy(alpha=if(ink.dark).45f else .55f),Offset(0f,0f),Size(w,h))
            drawLine(Color.White.copy(alpha=if(ink.dark).14f else .8f),Offset(0f,bottom-.5.dp.toPx()),Offset(w,bottom-.5.dp.toPx()),1.dp.toPx())
        }
    }
    // Rainbow designs: the flag as a slim band at the lower edge of the header.
    val stripes=prideStripes(theme)
    if(stripes.isNotEmpty()) drawStripeBand(stripes,bottom-4.dp.toPx(),4.dp.toPx())
}

/** Ink of header text and icons: on the coloured block of Swiss designs it follows the block. */
fun LanguageInk.headerContent(default:Color):Color=if(active && spec.header==LanguageHeader.BLOCK) contrastContentColor(primary) else default

/** A woven ground: fine warp and weft threads every [step]. */
internal fun DrawScope.drawWeave(thread:Color,step:Float) {
    val w=size.width;val h=size.height;var x=0f;var n=0
    while(x<w && n<400){drawLine(thread,Offset(x,0f),Offset(x,h),step*.35f);x+=step;n++}
    var y=0f;n=0
    while(y<h && n<400){drawLine(thread.copy(alpha=thread.alpha*.7f),Offset(0f,y),Offset(w,y),step*.35f);y+=step;n++}
}

/** A running stitch [inset] inside [outline]: the same shape, scaled about its centre, dashed. */
internal fun DrawScope.drawSeam(outline:Outline,thread:Color,inset:Float,width:Float) {
    val w=size.width;val h=size.height
    if(w<=inset*3 || h<=inset*3) return
    withTransform({scale((w-2*inset)/w,(h-2*inset)/h,center)}) {
        drawOutline(outline,thread,style=Stroke(width,cap=StrokeCap.Round,
            pathEffect=PathEffect.dashPathEffect(floatArrayOf(width*4.2f,width*3f))))
    }
}

/** Colours of the four lights behind glass (HCT tone near the ground's own, so text stays readable). */
internal fun glassFieldColors(bg:Color,primary:Color,secondary:Color,dark:Boolean):List<Color> {
    val ground=dev.ambivalenton.openv.color.utilities.Hct.fromInt(bg.toArgb())
    val a=dev.ambivalenton.openv.color.utilities.Hct.fromInt(primary.toArgb())
    val b=dev.ambivalenton.openv.color.utilities.Hct.fromInt(secondary.toArgb())
    // Lights a little brighter than a dark ground or a little deeper than a light one: colour, not contrast.
    val tone=if(dark) (ground.tone+14).coerceIn(18.0,34.0) else (ground.tone-9).coerceIn(78.0,90.0)
    fun light(hue:Double,chroma:Double)=Color(dev.ambivalenton.openv.color.utilities.Hct.from(hue,chroma.coerceIn(28.0,56.0),tone).toInt())
    val mid=((a.hue+b.hue)/2+if(kotlin.math.abs(a.hue-b.hue)>180) 180 else 0)%360
    return listOf(light(a.hue,a.chroma),light(b.hue,b.chroma),light(mid,(a.chroma+b.chroma)/2),light((a.hue+150)%360,a.chroma*.8))
}
internal fun glassFieldStrength(dark:Boolean)=if(dark) .85f else .75f

/** Light behind glass: four soft lights in the design's hues at the ground's own brightness (HCT tone). */
internal fun DrawScope.drawGlassField(bg:Color,primary:Color,secondary:Color,dark:Boolean) {
    val w=size.width;val h=size.height;val m=maxOf(w,h)
    val colors=glassFieldColors(bg,primary,secondary,dark)
    val places=listOf(Offset(w*.05f,h*.1f) to m*.72f,Offset(w*1.02f,h*.42f) to m*.66f,Offset(w*.18f,h*.86f) to m*.7f,Offset(w*.88f,h*1.04f) to m*.5f)
    val strength=glassFieldStrength(dark)
    colors.zip(places).forEach {(color,place)->
        val (c,r)=place
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha=strength),color.copy(alpha=strength*.45f),Color.Transparent),c,r),r,c)
    }
}
