// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.animation.ValueAnimator
import android.graphics.Paint
import android.graphics.Path as NativePath
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.ColorUtils
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.color.utilities.Hct
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appCorner
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

/** Coordinates of the actual pressed FAB, across both hosted and native windows. */
@Stable class PaletteMorphOrigin { var screenBounds:Rect? by mutableStateOf(null) }
/** Retain the paint hue, selecting the closest darker/lighter HCT tone that meets text contrast. */
internal fun paletteLabelColor(color:Int):Int {
    val opaque=color or 0xff000000.toInt();val source=Hct.fromInt(opaque)
    return (1..99).map{Hct.from(source.hue,source.chroma,it.toDouble()).toInt()}
        .filter{ColorUtils.calculateContrast(it,opaque)>=4.5}
        .minByOrNull{abs(Hct.fromInt(it).tone-source.tone)}
        ?:if(ColorUtils.calculateContrast(-1,opaque)>=4.5)-1 else 0xff000000.toInt()
}
private fun interpolate(a:Float,b:Float,t:Float)=a+(b-a)*t
private fun interpolate(a:Rect,b:Rect,t:Float)=Rect(interpolate(a.left,b.left,t),interpolate(a.top,b.top,t),interpolate(a.right,b.right,t),interpolate(a.bottom,b.bottom,t))
@Composable internal fun MixingPaletteOverlay(title:String,fields:List<PaletteField>,values:Map<String,Long>,origin:PaletteMorphOrigin?,
    onEdit:(String)->Unit,onReset:()->Unit,onDismiss:()->Unit,closeRequested:Boolean=false) {
    // Build 108: the palette springs out of the button – its width a little ahead of its height, so it
    // swells like a drop – and settles with a soft overshoot; the wells pop in one after another.
    // Closing gathers the wells first and draws the palette back without bouncing.
    val count=fields.size+1
    val grow=remember{Animatable(0f)};val rise=remember{Animatable(0f)}
    val pops=remember(count){List(count){Animatable(0f)}}
    var closing by remember{mutableStateOf(false)}
    val finish by rememberUpdatedState(onDismiss)
    val calm=LocalAppSettings.current.reduceMotion||!ValueAnimator.areAnimatorsEnabled()
    LaunchedEffect(closeRequested){if(closeRequested)closing=true}
    LaunchedEffect(closing,calm){
        if(!closing){
            launch{grow.animateTo(1f,if(calm)snap()else spring(PALETTE_GROW_DAMPING,PALETTE_GROW_STIFFNESS))}
            launch{rise.animateTo(1f,if(calm)snap()else spring(PALETTE_RISE_DAMPING,PALETTE_RISE_STIFFNESS))}
            pops.forEachIndexed{i,pop->launch{if(!calm)delay(PALETTE_WELL_DELAY+i*PALETTE_WELL_STAGGER);pop.animateTo(1f,if(calm)snap()else spring(PALETTE_WELL_DAMPING,PALETTE_WELL_STIFFNESS))}}
        }else{
            coroutineScope{
                pops.asReversed().forEachIndexed{i,pop->launch{if(!calm)delay(i*12L);pop.animateTo(0f,if(calm)snap()else spring(1f,1100f,.01f))}}
                launch{if(!calm)delay(50);grow.animateTo(0f,if(calm)snap()else spring(1f,PALETTE_CLOSE_STIFFNESS,.004f))}
                launch{if(!calm)delay(50);rise.animateTo(0f,if(calm)snap()else spring(1f,PALETTE_CLOSE_STIFFNESS*.85f,.004f))}
            }
            finish()
        }
    }
    val requestClose={closing=true}
    AppDialog(requestClose,DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false),onTabDismiss={finish()},behindNavigation=true,drawScrim=false){
        ReleaseDialogWindowOnDispose(ownsScrim=true)
        val view=LocalView.current;val density=LocalDensity.current;val scheme=MaterialTheme.colorScheme
        var screenOffset by remember{mutableStateOf(Offset.Zero)}
        val seed=remember(fields.map{it.key}){Random.nextInt()}
        val labelColors=remember(values){values.mapValues{paletteLabelColor(it.value.toInt())}}
        val defaults=remember(fields){fields.map{it.default.toInt()}.distinct().let{if(it.size==1)it+it else it}}
        val swirl=remember(defaults){paletteSwirl(defaults)}
        val resetLabel=tr("Farben zurücksetzen")
        val type=MaterialTheme.typography.labelMedium
        val face=LocalFontFamilyResolver.current.resolve(type.fontFamily,type.fontWeight?:FontWeight.Medium,FontStyle.Normal,FontSynthesis.All).value as Typeface
        val labelSize=with(density){type.fontSize.toPx()}
        BoxWithConstraints(Modifier.fillMaxSize().testTag("design-palette-dialog").onGloballyPositioned{coordinates->
            val screen=IntArray(2);val window=IntArray(2);view.getLocationOnScreen(screen);view.getLocationInWindow(window)
            screenOffset=coordinates.boundsInWindow().topLeft+Offset((screen[0]-window[0]).toFloat(),(screen[1]-window[1]).toFloat())
        }.semantics{paneTitle=title;stateDescription=if(closing)tr("Palette wird geschlossen")else tr("Mischpalette");dismiss{requestClose();true}}){
            val totalW=with(density){maxWidth.toPx()};val totalH=with(density){maxHeight.toPx()}
            val clearance=with(density){LocalNavigationClearance.current.toPx()}
            val start=origin?.screenBounds?.translate(-screenOffset.x,-screenOffset.y)?:Rect(totalW-80*density.density,totalH-clearance-76*density.density,totalW-24*density.density,totalH-clearance-20*density.density)
            val placement=mixingPalettePlacement(totalW,totalH,start,count,clearance,density.density)
            val target=placement.bounds;val w=target.width;val h=target.height;val wide=placement.wide
            val spots=remember(count,seed,wide){mixingPaletteSpots(count,seed,wide)}
            val px=grow.value.coerceAtLeast(0f);val py=rise.value.coerceAtLeast(0f)
            val p=((px+py)/2f).coerceIn(0f,1f);val q=mixingPaletteHole(count,wide)
            val width=interpolate(start.width,w,px);val height=interpolate(start.height,h,py)
            // The palette grows out of the button's corner: its grip reaches the button early, so the
            // swelling body (and its overshoot) never runs past the screen edge.
            val lead=(p/.35f).coerceIn(0f,1f).let{it*it*(3-2*it)}
            val pivot=Offset(interpolate(.5f,q.x,lead),interpolate(.5f,q.y,lead))
            val edge=4*density.density
            val loose=Rect(placement.anchor.x-pivot.x*width,placement.anchor.y-pivot.y*height,placement.anchor.x+(1-pivot.x)*width,placement.anchor.y+(1-pivot.y)*height)
            val current=loose.translate(min(0f,totalW-edge-loose.right)+max(0f,edge-loose.left),min(0f,totalH-edge-loose.bottom)+max(0f,edge-loose.top))
            val alpha=((p-.30f)/.55f).coerceIn(0f,1f);val active=p>=.96f&&!closing
            val holeRadius=mixingPaletteHoleRadius(w,h,density.density)
            val region=remember(w,h,wide,count,holeRadius){android.graphics.Region().apply{
                setPath(mixingPalettePath(w,h,1f,wide,count,holeRadius),android.graphics.Region(0,0,ceil(w).toInt(),ceil(h).toInt()))
            }}
            Canvas(Modifier.fillMaxSize().pointerInput(target,region,closing){detectTapGestures{point->
                if(!closing&&!region.contains((point.x-target.left).toInt(),(point.y-target.top).toInt()))requestClose()
            }}){
                drawRect(Color.Black.copy(alpha=.38f*p))
                drawIntoCanvas{canvas->
                    val native=canvas.nativeCanvas;val saved=native.save()
                    native.translate(current.left,current.top);native.scale(current.width/w,current.height/h)
                    val path=mixingPalettePath(w,h,p,wide,count,holeRadius)
                    val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.color=lerp(scheme.primaryContainer,scheme.surfaceContainerHigh,p).copy(alpha=1f).toArgb()
                    paint.setShadowLayer(14*density.density,0f,6*density.density,0x44000000);native.drawPath(path,paint);paint.clearShadowLayer()
                    native.save();native.clipPath(path)
                    paint.shader=android.graphics.LinearGradient(0f,0f,w,h,intArrayOf(
                        lerp(scheme.primaryContainer,scheme.surfaceContainerHigh,.40f).copy(alpha=1f).toArgb(),
                        scheme.surfaceContainerHigh.copy(alpha=1f).toArgb(),
                        lerp(scheme.surfaceContainerHigh,scheme.secondaryContainer,.30f).copy(alpha=1f).toArgb()),null,android.graphics.Shader.TileMode.CLAMP)
                    paint.alpha=(255*p).roundToInt();native.drawPath(path,paint);paint.alpha=255;paint.shader=null;native.restore()
                    paint.color=scheme.outlineVariant.copy(alpha=.8f*p).toArgb();paint.style=Paint.Style.STROKE;paint.strokeWidth=density.density;native.drawPath(path,paint);paint.style=Paint.Style.FILL
                    if(alpha>0f)spots.forEachIndexed{index,spot->
                        val pop=pops[index].value
                        if(pop<=.002f)return@forEachIndexed
                        val center=Offset(spot.x*w,spot.y*h);val radius=spot.radius*w
                        val wellSaved=native.save();native.scale(pop,pop,center.x,center.y)
                        val alpha=alpha*pop.coerceIn(0f,1f)
                        val reset=index==fields.size
                        val color=if(reset)defaults.first()else values.getValue(fields[index].key).toInt()
                        paint.color=Color(color).copy(alpha=alpha).toArgb()
                        if(reset){paint.shader=android.graphics.BitmapShader(swirl,android.graphics.Shader.TileMode.CLAMP,android.graphics.Shader.TileMode.CLAMP).apply{
                            setLocalMatrix(android.graphics.Matrix().apply{setScale(radius*2/swirl.width,radius*2/swirl.height);postTranslate(center.x-radius,center.y-radius)})
                        };paint.alpha=(255*alpha).roundToInt()}
                        paint.setShadowLayer(3*density.density,0f,2*density.density,0x44000000);native.drawCircle(center.x,center.y,radius,paint);paint.clearShadowLayer();paint.shader=null;paint.alpha=255
                        val textColor=if(reset)paletteLabelColor(defaults.first())else labelColors.getValue(fields[index].key)
                        paint.style=Paint.Style.STROKE;paint.strokeWidth=density.density;paint.color=Color(textColor).copy(alpha=.2f*alpha).toArgb();native.drawCircle(center.x,center.y,radius-.5f*density.density,paint);paint.style=Paint.Style.FILL
                        drawPaletteWellLabel(native,paint,if(reset)resetLabel else fields[index].title,center,radius,textColor,alpha,face,labelSize,reset)
                        native.restoreToCount(wellSaved)
                    }
                    native.restoreToCount(saved)
                }
            }
            if(p<.45f)Box(Modifier.offset{IntOffset((placement.anchor.x-12*density.density).roundToInt(),(placement.anchor.y-12*density.density).roundToInt())}
                .size(24.dp).graphicsLayer{this.alpha=(1-p/.45f).coerceIn(0f,1f)},contentAlignment=Alignment.Center){AppIcon(R.drawable.ic_palette,null,tint=scheme.onPrimaryContainer)}
            spots.forEachIndexed{index,spot->
                val radius=spot.radius*w;val reset=index==fields.size
                Box(Modifier.offset{IntOffset((target.left+spot.x*w-radius).roundToInt(),(target.top+spot.y*h-radius).roundToInt())}
                    .size(with(density){(radius*2).toDp()}).testTag(if(reset)"palette-reset"else "palette-field-${fields[index].key}")
                    .clickable(enabled=active,role=Role.Button,interactionSource=remember{MutableInteractionSource()},indication=null){if(reset)onReset()else onEdit(fields[index].key)}
                    .semantics{contentDescription=if(reset)resetLabel else fields[index].title})
            }
            if(alpha>0f){
                IconButton(requestClose,Modifier.offset{IntOffset((placement.anchor.x-24*density.density).roundToInt(),(placement.anchor.y-24*density.density).roundToInt())}
                    .size(48.dp).graphicsLayer{this.alpha=alpha;scaleX=current.width/w;scaleY=current.height/h}.testTag("palette-close"),enabled=!closing){AppIcon(R.drawable.ic_close,tr("Palette schließen"),tint=scheme.onSurface)}
            }
        }
    }
}

internal const val PALETTE_GROW_DAMPING=.6f
internal const val PALETTE_GROW_STIFFNESS=320f
internal const val PALETTE_RISE_DAMPING=.66f
internal const val PALETTE_RISE_STIFFNESS=230f
internal const val PALETTE_WELL_DAMPING=.55f
internal const val PALETTE_WELL_STIFFNESS=540f
internal const val PALETTE_WELL_DELAY=130L
internal const val PALETTE_WELL_STAGGER=36L
internal const val PALETTE_CLOSE_STIFFNESS=620f

private fun drawPaletteWellLabel(canvas:android.graphics.Canvas,paint:Paint,title:String,center:Offset,radius:Float,color:Int,alpha:Float,face:Typeface,labelSize:Float,reset:Boolean){
    paint.color=Color(color).copy(alpha=alpha).toArgb();paint.typeface=face
    if(reset){
        val words=title.split(' ');val lines=if(words.size>1)listOf(words.first(),words.drop(1).joinToString(" "))else listOf(title)
        val arcRadius=radius*.73f
        lines.forEachIndexed{i,line->
            val arc=NativePath().apply{addArc(RectF(center.x-arcRadius,center.y-arcRadius,center.x+arcRadius,center.y+arcRadius),if(i==0)207f else 153f,if(i==0)126f else -126f)}
            val length=arcRadius*126f*PI.toFloat()/180
            paint.textSize=min(labelSize,radius*.32f)
            if(paint.measureText(line)>length*.94f)paint.textSize*=length*.94f/paint.measureText(line)
            val offset=(length-paint.measureText(line))/2
            val halo=if(ColorUtils.calculateLuminance(color)>.4)0xff17202b.toInt()else 0xfff5f8fc.toInt()
            paint.style=Paint.Style.STROKE;paint.strokeWidth=maxOf(1.5f,paint.textSize*.15f);paint.color=Color(halo).copy(alpha=.8f*alpha).toArgb();canvas.drawTextOnPath(line,arc,offset,0f,paint)
            paint.style=Paint.Style.FILL;paint.color=Color(color).copy(alpha=alpha).toArgb();canvas.drawTextOnPath(line,arc,offset,0f,paint)
        }
    }else{
        val arcRadius=radius*.74f;val arc=NativePath().apply{addArc(RectF(center.x-arcRadius,center.y-arcRadius,center.x+arcRadius,center.y+arcRadius),207f,126f)}
        val length=arcRadius*126f*PI.toFloat()/180;paint.textSize=min(labelSize,radius*.31f)
        val measured=paint.measureText(title);if(measured>length*.93f)paint.textSize*=length*.93f/measured
        canvas.drawTextOnPath(title,arc,(length-paint.measureText(title))/2,0f,paint)
    }
}
