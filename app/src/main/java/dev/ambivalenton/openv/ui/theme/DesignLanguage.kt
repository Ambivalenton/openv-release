// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.data.model.*
import kotlin.math.*

/*
 * Shared vocabulary of the ten design languages (see ThemeLanguage). Everything here is
 * static vector drawing: no bitmaps, blur effects, timers or downloads. STANDARD themes never reach
 * these branches, so the existing catalogue renders exactly as before.
 */

// ---- Shapes ---------------------------------------------------------------------------------------

/** Continuous corner: one cubic per corner with long, soft handles. Convex at every size. */
class SquircleShape(topStart:CornerSize,topEnd:CornerSize,bottomEnd:CornerSize,bottomStart:CornerSize):
    CornerBasedShape(topStart,topEnd,bottomEnd,bottomStart) {
    constructor(radius:Dp):this(CornerSize(radius),CornerSize(radius),CornerSize(radius),CornerSize(radius))
    override fun copy(topStart:CornerSize,topEnd:CornerSize,bottomEnd:CornerSize,bottomStart:CornerSize)=
        SquircleShape(topStart,topEnd,bottomEnd,bottomStart)
    override fun createOutline(size:Size,topStart:Float,topEnd:Float,bottomEnd:Float,bottomStart:Float,layoutDirection:LayoutDirection):Outline {
        val ltr=layoutDirection==LayoutDirection.Ltr
        return Outline.Generic(squirclePath(size,if(ltr)topStart else topEnd,if(ltr)topEnd else topStart,
            if(ltr)bottomEnd else bottomStart,if(ltr)bottomStart else bottomEnd))
    }
    override fun equals(other:Any?)=other is SquircleShape && topStart==other.topStart && topEnd==other.topEnd &&
        bottomEnd==other.bottomEnd && bottomStart==other.bottomStart
    override fun hashCode()=((topStart.hashCode()*31+topEnd.hashCode())*31+bottomEnd.hashCode())*31+bottomStart.hashCode()
}

/** The visible apex matches a circular corner of the same radius; the curve starts 45 % earlier. */
internal const val SQUIRCLE_EXTENT=1.45f
private const val SQUIRCLE_HANDLE=.842f

internal fun squirclePath(size:Size,topLeft:Float,topRight:Float,bottomRight:Float,bottomLeft:Float):Path {
    val w=size.width;val h=size.height;val limit=min(w,h)/2f
    fun extent(r:Float)=(r*SQUIRCLE_EXTENT).coerceIn(0f,limit)
    val a=extent(topLeft);val b=extent(topRight);val c=extent(bottomRight);val d=extent(bottomLeft)
    val k=SQUIRCLE_HANDLE
    return Path().apply {
        moveTo(a,0f);lineTo(w-b,0f)
        cubicTo(w-b+b*k,0f,w,b-b*k,w,b);lineTo(w,h-c)
        cubicTo(w,h-c+c*k,w-c+c*k,h,w-c,h);lineTo(d,h)
        cubicTo(d-d*k,h,0f,h-d+d*k,0f,h-d);lineTo(0f,a)
        cubicTo(0f,a-a*k,a-a*k,0f,a,0f);close()
    }
}

/** Pebble: four unequal radii in a fixed rhythm, so every card, chip and bar shares one hand. */
internal fun pebbleShape(r:Dp)=androidx.compose.foundation.shape.RoundedCornerShape(
    topStart=r*1.35f,topEnd=r*.55f,bottomEnd=r*1.2f,bottomStart=r*.7f)

// ---- Typography ---------------------------------------------------------------------------------

/** Heading weight of a language; null keeps Material's own weights. */
internal val ThemeLanguage.headingWeight:FontWeight? get()=when(this) {
    ThemeLanguage.NEO_BRUTAL,ThemeLanguage.COLOR_BLOCK->FontWeight.ExtraBold
    ThemeLanguage.CLAY->FontWeight.Bold
    ThemeLanguage.BENTO,ThemeLanguage.ORGANIC,ThemeLanguage.GLOW_EDGE->FontWeight.SemiBold
    ThemeLanguage.SOFT_RELIEF,ThemeLanguage.TONAL->FontWeight.Medium
    ThemeLanguage.STANDARD,ThemeLanguage.OUTLINE,ThemeLanguage.JAPANDI->null
}
/** Airy capitals for Japandi, slightly tighter product type for glow-edge interfaces. */
internal val ThemeLanguage.headingTracking:TextUnit get()=when(this) {
    ThemeLanguage.JAPANDI->.6.sp
    ThemeLanguage.GLOW_EDGE->(-.15).sp
    else->TextUnit.Unspecified
}

/* Bundled typefaces of the design-language themes: AppTheme.headFace/bodyFace via themeFontFamily (ThemeFonts.kt). */

// ---- Colour roles -------------------------------------------------------------------------------

/** Ink of the design (not pure black or white) on a filled container; pure black or white when a
 * custom colour leaves the softer inks below 4.5:1 (one of the two always reaches it). */
internal fun languageInk(container:Color):Color {
    val dark=Color(0xFF181A1C);val light=Color(0xFFF3F2EE)
    val c=container.compositeOver(Color.White)
    val ink=if(contrastRatio(dark,c)>=contrastRatio(light,c)) dark else light
    return if(contrastRatio(ink,c)>=4.5) ink else contrastInk(c)
}

/** Container and outline roles of each language; text roles stay readable (≥ 4.5:1). */
internal fun designLanguageScheme(theme:AppTheme,colors:AppThemeColors,s:ColorScheme):ColorScheme {
    val bg=Color(colors.background);val surface=Color(colors.surface)
    val accent=Color(colors.accent);val second=Color(colors.secondary)
    val fg=s.onBackground
    fun legible(value:Color,on:Color)=Color(readableForeground(value.toArgb().toLong() and 0xffffffffL,on.toArgb().toLong() and 0xffffffffL))
    fun tinted(base:Color,tint:Color,t:Float)=lerp(base,tint,t)
    fun ColorScheme.containers(low:Color,mid:Color,high:Color,highest:Color)=copy(surfaceContainerLowest=bg,
        surfaceContainerLow=low,surfaceContainer=mid,surfaceContainerHigh=high,surfaceContainerHighest=highest,
        surfaceVariant=mid,surfaceBright=high)
    fun ColorScheme.filled(primary:Color,secondary:Color,tertiary:Color)=copy(
        primaryContainer=primary,onPrimaryContainer=languageInk(primary),
        secondaryContainer=secondary,onSecondaryContainer=languageInk(secondary),
        tertiaryContainer=tertiary,onTertiaryContainer=languageInk(tertiary))
    fun ColorScheme.toned(primary:Color,secondary:Color,tertiary:Color)=copy(
        primaryContainer=primary,onPrimaryContainer=legible(accent,primary),
        secondaryContainer=secondary,onSecondaryContainer=legible(second,secondary),
        tertiaryContainer=tertiary,onTertiaryContainer=legible(second,tertiary))
    fun ColorScheme.lines(outline:Color,variant:Color)=copy(outline=outline,outlineVariant=variant)
    return when(theme.themeLanguage) {
        ThemeLanguage.STANDARD->s
        ThemeLanguage.NEO_BRUTAL->s.containers(surface,surface,surface,surface)
            .filled(accent,second,tinted(surface,accent,.35f)).lines(fg,fg)
        ThemeLanguage.SOFT_RELIEF->s.containers(surface,surface,surface,surface)
            .toned(tinted(surface,accent,.10f),tinted(surface,second,.12f),tinted(surface,second,.08f))
            .lines(lerp(fg,bg,.55f),lerp(fg,bg,.82f))
        ThemeLanguage.CLAY->s.containers(surface,surface,tinted(surface,second,.16f),tinted(surface,accent,.12f))
            .filled(accent,second,tinted(surface,accent,.32f)).lines(lerp(fg,bg,.5f),lerp(fg,bg,.8f))
        ThemeLanguage.OUTLINE->s.containers(bg,bg,bg,bg)
            .toned(tinted(bg,accent,.12f),tinted(bg,second,.10f),tinted(bg,second,.08f)).lines(fg,lerp(fg,bg,.55f))
        ThemeLanguage.BENTO->s.containers(surface,surface,surface,surface)
            .toned(tinted(surface,accent,.14f),tinted(surface,second,.14f),tinted(surface,second,.10f))
            .lines(lerp(fg,bg,.45f),lerp(fg,surface,if(theme.dark).84f else .89f))
        ThemeLanguage.TONAL->s.containers(lerp(bg,surface,.5f),surface,tinted(surface,accent,.06f),tinted(surface,accent,.12f))
            .toned(tinted(bg,accent,if(theme.dark) .30f else .34f),tinted(bg,second,if(theme.dark) .28f else .32f),tinted(bg,second,.2f))
            .lines(lerp(legible(accent,bg),bg,.35f),lerp(accent,bg,.72f))
        ThemeLanguage.COLOR_BLOCK->s.containers(surface,surface,surface,surface)
            .filled(accent,second,tinted(surface,accent,.5f)).lines(lerp(fg,bg,.35f),lerp(fg,bg,.65f))
        ThemeLanguage.JAPANDI->s.containers(surface,surface,tinted(surface,second,.06f),tinted(surface,accent,.05f))
            .toned(tinted(surface,accent,.12f),tinted(surface,second,.14f),tinted(surface,second,.10f))
            .lines(lerp(fg,bg,.5f),lerp(fg,bg,.8f))
        ThemeLanguage.ORGANIC->s.containers(surface,surface,tinted(surface,second,.12f),tinted(surface,accent,.10f))
            .toned(tinted(surface,accent,.22f),tinted(surface,second,.2f),tinted(surface,second,.14f))
            .lines(lerp(fg,bg,.5f),lerp(fg,bg,.78f))
        ThemeLanguage.GLOW_EDGE->s.containers(surface,surface,tinted(surface,Color.White,.04f),tinted(surface,Color.White,.065f))
            .toned(tinted(surface,accent,.2f),tinted(surface,second,.14f),tinted(surface,second,.1f))
            .lines(lerp(fg,bg,.62f),lerp(fg,bg,.86f))
    }
}

// ---- Frames and shadows ---------------------------------------------------------------------------

sealed interface FrameShadow
/** Neo-brutalism: the same outline, offset, without blur. */
data class HardShadow(val offset:Dp,val color:Color):FrameShadow
/** Soft relief: shade bottom right and a highlight top left of the same material. */
data class ReliefShadow(val distance:Dp,val light:Color,val shade:Color):FrameShadow
/** Clay, organic and glow edges: a soft halo, optionally below the shape. */
data class HaloShadow(val spread:Dp,val color:Color,val drop:Float):FrameShadow

/** One frame vocabulary for cards, picker rows, the board card and the navigation bar. */
@Immutable data class AppFrame(val language:ThemeLanguage,val border:BorderStroke?,val shadow:FrameShadow?) {
    /** Languages with an own (or deliberately no) shadow switch the platform elevation off. */
    val replacesElevation:Boolean get()=language.ownsShadow || language.flat
}

private val NoFrame=AppFrame(ThemeLanguage.STANDARD,null,null)

/** Ink used for brutal contours on light designs, the bright fill on dark ones. */
internal fun brutalShadowInk(scheme:ColorScheme,dark:Boolean)=if(dark) scheme.primaryContainer else scheme.outline

fun appFrameFor(theme:AppTheme?,scheme:ColorScheme):AppFrame=appFrameFor(theme.themeLanguageOrStandard,theme?.dark==true,scheme)

fun appFrameFor(language:ThemeLanguage,dark:Boolean,scheme:ColorScheme):AppFrame {
    return when(language) {
        ThemeLanguage.STANDARD->NoFrame
        ThemeLanguage.NEO_BRUTAL->AppFrame(language,BorderStroke(2.5.dp,scheme.outline),HardShadow(4.dp,brutalShadowInk(scheme,dark)))
        ThemeLanguage.SOFT_RELIEF->AppFrame(language,BorderStroke(1.dp,Brush.linearGradient(listOf(
            Color.White.copy(alpha=if(dark).10f else .75f),Color.Transparent,Color.Black.copy(alpha=if(dark).30f else .06f)))),
            ReliefShadow(5.dp,Color.White.copy(alpha=if(dark).05f else .85f),Color.Black.copy(alpha=if(dark).55f else .16f)))
        ThemeLanguage.CLAY->AppFrame(language,BorderStroke(1.5.dp,Brush.verticalGradient(listOf(
            Color.White.copy(alpha=if(dark).22f else .85f),Color.White.copy(alpha=0f)))),
            HaloShadow(9.dp,lerp(scheme.primary,Color.Black,.25f).copy(alpha=if(dark).45f else .26f),.6f))
        ThemeLanguage.OUTLINE->AppFrame(language,BorderStroke(1.25.dp,scheme.outline),null)
        ThemeLanguage.BENTO->AppFrame(language,BorderStroke(1.dp,scheme.outlineVariant),null)
        ThemeLanguage.TONAL,ThemeLanguage.COLOR_BLOCK->AppFrame(language,null,null)
        ThemeLanguage.JAPANDI->AppFrame(language,BorderStroke(1.dp,scheme.outlineVariant),null)
        ThemeLanguage.ORGANIC->AppFrame(language,null,HaloShadow(7.dp,lerp(scheme.primary,Color.Black,.3f).copy(alpha=if(dark).40f else .18f),.55f))
        ThemeLanguage.GLOW_EDGE->AppFrame(language,BorderStroke(1.dp,Brush.verticalGradient(listOf(
            Color.White.copy(alpha=.17f),Color.White.copy(alpha=.05f)))),HaloShadow(10.dp,scheme.primary.copy(alpha=.16f),0f))
    }
}

@Composable fun appFrame():AppFrame {
    val theme=LocalAppVisuals.current.theme
    val scheme=MaterialTheme.colorScheme
    return remember(theme,scheme) {appFrameFor(theme,scheme)}
}

/** Card elevation: the language's own shadow (or none) replaces the platform shadow. */
@Composable fun AppFrame.cardElevation(default:CardElevation=CardDefaults.cardElevation()):CardElevation=
    if(replacesElevation) CardDefaults.cardElevation(0.dp,0.dp,0.dp,0.dp,0.dp,0.dp) else default

/** Border of a card: the language border, otherwise whatever the caller used before. */
fun AppFrame.cardBorder(default:BorderStroke?=null):BorderStroke?=border ?: default

private const val FRAME_SHADOW_STEPS=6

/** Shadow of [path], scaled around the path's own centre (works for inset or translated outlines). */
internal fun DrawScope.drawFrameShadow(path:Path,shadow:FrameShadow) {
    val box=path.getBounds()
    val w=box.width.coerceAtLeast(1f);val h=box.height.coerceAtLeast(1f)
    val pivot=box.center
    when(shadow) {
        is HardShadow->{val o=shadow.offset.toPx();translate(o,o) {drawPath(path,shadow.color)}}
        is ReliefShadow->{
            val d=shadow.distance.toPx()
            for(step in FRAME_SHADOW_STEPS downTo 1) {
                val spread=d*step/FRAME_SHADOW_STEPS
                withTransform({translate(d*.7f,d*.8f);scale(1f+2f*spread/w,1f+2f*spread/h,pivot)}) {
                    drawPath(path,shadow.shade.copy(alpha=shadow.shade.alpha/FRAME_SHADOW_STEPS))
                }
                withTransform({translate(-d*.7f,-d*.7f);scale(1f+2f*spread/w,1f+2f*spread/h,pivot)}) {
                    drawPath(path,shadow.light.copy(alpha=shadow.light.alpha/FRAME_SHADOW_STEPS))
                }
            }
        }
        is HaloShadow->{
            val s=shadow.spread.toPx()
            for(step in FRAME_SHADOW_STEPS downTo 1) {
                val spread=s*step/FRAME_SHADOW_STEPS
                withTransform({translate(0f,s*shadow.drop);scale(1f+2f*spread/w,1f+2f*spread/h,pivot)}) {
                    drawPath(path,shadow.color.copy(alpha=shadow.color.alpha/FRAME_SHADOW_STEPS))
                }
            }
        }
    }
}

/** Draws the frame shadow behind a composable of the given shape (outside its own clip). */
fun Modifier.appFrameShadow(frame:AppFrame,shape:Shape):Modifier=frame.shadow?.let {frameShadow(it,shape)} ?: this

/** Draws [shadow] behind a composable of the given shape. */
fun Modifier.frameShadow(shadow:FrameShadow,shape:Shape):Modifier=drawWithCache {
    val path=Path().apply {addOutline(shape.createOutline(size,layoutDirection,this@drawWithCache))}
    onDrawBehind {drawFrameShadow(path,shadow)}
}

/** Convenience for the common case: shadow plus border of the current language. */
@Composable fun Modifier.appFramed(shape:Shape):Modifier=appFrameShadow(appFrame(),shape)

// ---- Backgrounds, panels and headers --------------------------------------------------------------

/** One quiet signature per language instead of a repeated wallpaper; several languages stay plain. */
internal fun DrawScope.drawThemeLanguageBackdrop(theme:AppTheme,bg:Color,primary:Color,secondary:Color) {
    val w=size.width;val h=size.height;val m=size.minDimension
    if(w<=0f || h<=0f) return
    val dark=theme.dark
    when(theme.themeLanguage) {
        ThemeLanguage.SOFT_RELIEF->drawRect(Brush.linearGradient(listOf(
            Color.White.copy(alpha=if(dark).04f else .32f),Color.Transparent,Color.Black.copy(alpha=if(dark).14f else .04f)),
            Offset.Zero,Offset(w,h)))
        ThemeLanguage.CLAY->{
            val a=Offset(w*.96f,h*.06f);val b=Offset(w*.04f,h*.95f)
            drawCircle(Brush.radialGradient(listOf(primary.copy(alpha=if(dark).16f else .14f),Color.Transparent),a,m*.72f),m*.72f,a)
            drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha=if(dark).14f else .13f),Color.Transparent),b,m*.66f),m*.66f,b)
        }
        ThemeLanguage.TONAL->drawRect(Brush.verticalGradient(listOf(bg,lerp(bg,primary,if(dark).07f else .06f))))
        ThemeLanguage.JAPANDI->drawJapandiGesture(theme.ordinal%3,primary,secondary,dark)
        ThemeLanguage.ORGANIC->{
            drawPath(organicBlob(Offset(w*.92f,h*.1f),m*.42f,theme.ordinal),secondary.copy(alpha=if(dark).12f else .10f))
            drawPath(organicBlob(Offset(w*.06f,h*.88f),m*.24f,theme.ordinal+3),primary.copy(alpha=if(dark).10f else .07f))
        }
        ThemeLanguage.GLOW_EDGE->{
            val c=Offset(w*.5f,-h*.06f);val r=max(w,h*.55f)
            drawCircle(Brush.radialGradient(listOf(primary.copy(alpha=.13f),primary.copy(alpha=.04f),Color.Transparent),c,r),r,c)
        }
        else->Unit
    }
}

/** Ensō, horizon or a single stem: one gesture, drawn once, far from the reading area's centre. */
private fun DrawScope.drawJapandiGesture(kind:Int,primary:Color,secondary:Color,dark:Boolean) {
    val w=size.width;val h=size.height;val m=size.minDimension
    val ink=primary.copy(alpha=if(dark).12f else .085f)
    when(kind) {
        0->{val r=m*.34f;val c=Offset(w*.84f,h*.8f)
            drawArc(ink,-75f,312f,false,c-Offset(r,r),Size(r*2,r*2),style=Stroke(m*.028f,cap=StrokeCap.Round))}
        1->{val y=h*.74f
            drawLine(ink,Offset(w*.08f,y),Offset(w*.6f,y),m*.006f,StrokeCap.Round)
            drawCircle(secondary.copy(alpha=if(dark).16f else .12f),m*.055f,Offset(w*.76f,y-m*.08f))}
        else->{val x=w*.88f
            drawLine(ink,Offset(x,h*.58f),Offset(x+m*.02f,h*1.02f),m*.012f,StrokeCap.Round)
            rotate(-32f,Offset(x,h*.66f)) {drawOval(ink,Offset(x-m*.1f,h*.66f-m*.025f),Size(m*.1f,m*.05f))}
            rotate(28f,Offset(x,h*.72f)) {drawOval(ink,Offset(x,h*.72f-m*.022f),Size(m*.09f,m*.044f))}}
    }
}

/** Deterministic, convex-ish soft blob; the seed only varies the rhythm of its radii. */
internal fun organicBlob(center:Offset,radius:Float,seed:Int):Path {
    val points=8
    val radii=List(points) {i->radius*(.82f+.18f*sin(i*2.1f+seed*1.3f))}
    fun point(i:Int):Offset {val a=(i.toFloat()/points)*2f*PI.toFloat()-PI.toFloat()/2f
        return center+Offset(cos(a)*radii[(i+points)%points],sin(a)*radii[(i+points)%points])}
    return Path().apply {
        val first=(point(0)+point(1))/2f;moveTo(first.x,first.y)
        for(i in 1..points) {val p=point(i);val n=(point(i)+point(i+1))/2f;quadraticTo(p.x,p.y,n.x,n.y)}
        close()
    }
}

/** Light of the material inside panels and quote cards (drawn behind their text). */
internal fun DrawScope.drawLanguagePanel(theme:AppTheme,primary:Color,secondary:Color) {
    val w=size.width;val h=size.height
    if(w<=0f || h<=0f) return
    val dark=theme.dark
    when(theme.themeLanguage) {
        ThemeLanguage.SOFT_RELIEF->drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha=if(dark).05f else .30f),
            Color.Transparent,Color.Black.copy(alpha=if(dark).12f else .04f)),Offset.Zero,Offset(w,h)))
        ThemeLanguage.CLAY->{
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=if(dark).08f else .34f),Color.Transparent),0f,h*.45f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=if(dark).14f else .05f)),h*.6f,h))
        }
        ThemeLanguage.GLOW_EDGE->drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=.06f),Color.Transparent),0f,h*.35f))
        ThemeLanguage.ORGANIC->drawPath(organicBlob(Offset(w*.95f,h*.08f),min(w,h)*.38f,theme.ordinal),secondary.copy(alpha=if(dark).12f else .09f))
        ThemeLanguage.TONAL->drawRect(Brush.verticalGradient(listOf(Color.Transparent,primary.copy(alpha=.05f))))
        else->Unit
    }
}

/** Bottom rule of the top bar: ink for brutal and outline designs, hairlines elsewhere. */
internal fun DrawScope.drawLanguageHeaderRule(theme:AppTheme,scheme:ColorScheme) {
    val (width,color)=when(theme.themeLanguage) {
        ThemeLanguage.NEO_BRUTAL->2.5.dp.toPx() to scheme.outline
        ThemeLanguage.OUTLINE->1.25.dp.toPx() to scheme.outline
        ThemeLanguage.GLOW_EDGE->1.dp.toPx() to Color.White.copy(alpha=.09f)
        ThemeLanguage.JAPANDI,ThemeLanguage.BENTO->1.dp.toPx() to scheme.outlineVariant
        else->return
    }
    drawRect(color,Offset(0f,size.height-width),Size(size.width,width))
}

/** Card shape: design languages always use their own corners; classic designs keep [classic]. */
@Composable fun languageCardShape(classic:Shape,size:Dp):Shape=
    if(LocalAppVisuals.current.theme.themeLanguageOrStandard==ThemeLanguage.STANDARD) classic else appCorner(size)
