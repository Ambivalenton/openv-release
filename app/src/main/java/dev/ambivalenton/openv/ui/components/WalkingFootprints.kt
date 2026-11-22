// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.animation.ValueAnimator
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawOutline as drawClockFrame
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlin.math.*

internal val LocalFootprintSeed=staticCompositionLocalOf<Long?> {null}

const val FOOTPRINT_LENGTH_PX=22.5f
const val FOOTPRINT_WIDTH_PX=10.5f
const val FOOTPRINT_TRACK_WIDTH_PX=31.5f
const val FOOTPRINT_FOOT_OFFSET_PX=7.5f
const val FOOTPRINT_STRIDE_PX=36f
const val FOOTSTEP_INTERVAL_MS=1_000
const val FOOTSTEP_LIFETIME_MS=2_400
const val FOOTSTEP_REST_MS=1_000

fun footstepCount(width:Float,height:Float,seed:Long=0L,sizeScale:Float=1f,straight:Boolean=false):Int =
    walkingFootsteps(width,height,seed,sizeScale,straight).size
fun footstepCycleMillis(count:Int)=((count.coerceAtLeast(1)-1)*FOOTSTEP_INTERVAL_MS+FOOTSTEP_LIFETIME_MS+FOOTSTEP_REST_MS)
private fun smooth(t:Float):Float {val x=t.coerceIn(0f,1f);return x*x*(3-2*x)}

/** Absolute lifetimes: future steps and expired steps are exactly transparent, including the rest. */
fun footstepAlpha(index:Int,count:Int,phase:Float):Float {
    if(index !in 0 until count || !phase.isFinite()) return 0f
    val age=phase.coerceIn(0f,1f)*footstepCycleMillis(count)-index*FOOTSTEP_INTERVAL_MS
    return when {
        age<=0f || age>=FOOTSTEP_LIFETIME_MS->0f
        age<350f->smooth(age/350f)
        age<900f->1f
        else->1f-smooth((age-900f)/(FOOTSTEP_LIFETIME_MS-900f))
    }
}

data class FootstepGeometry(val x:Float,val y:Float,val angle:Float,val scale:Float,val left:Boolean,val fore:Boolean?=null)
fun footstepGeometry(width:Float,height:Float,index:Int,seed:Long=0L):FootstepGeometry? =
    walkingFootsteps(width,height,seed).getOrNull(index)

/** Cached recognisable soles, toes down, slightly aligned to the cubic walking path. */
fun walkingFootprintsPath(width:Float,height:Float,onlyStep:Int?=null,seed:Long=0L):Path {
    val steps=walkingFootsteps(width,height,seed)
    return footprintShapesPath(if(onlyStep==null) steps else listOfNotNull(steps.getOrNull(onlyStep)))
}
fun footprintShapesPath(steps:List<FootstepGeometry>,style:FootprintStyle=FootprintStyle.SHOE):Path {
    if(style!=FootprintStyle.SHOE) return animalFootprintsPath(steps,style)
    val path=Path()
    for(g in steps) {
        fun point(u:Float,v:Float):androidx.compose.ui.geometry.Offset {
            val x=((if(g.left) u else 1-u)-.5f)*FOOTPRINT_WIDTH_PX*g.scale
            val y=(.5f-v)*FOOTPRINT_LENGTH_PX*g.scale
            return androidx.compose.ui.geometry.Offset(g.x+x*cos(g.angle)+y*sin(g.angle),g.y+y*cos(g.angle)-x*sin(g.angle))
        }
        fun move(u:Float,v:Float) {val p=point(u,v);path.moveTo(p.x,p.y)}
        fun line(u:Float,v:Float) {val p=point(u,v);path.lineTo(p.x,p.y)}
        fun cubic(a:Float,b:Float,c:Float,d:Float,e:Float,f:Float) {
            val p=point(a,b);val q=point(c,d);val z=point(e,f);path.cubicTo(p.x,p.y,q.x,q.y,z.x,z.y)
        }
        move(.22f,.02f);cubic(.02f,.04f,0f,.24f,.03f,.38f)
        cubic(.04f,.49f,.2f,.53f,.25f,.61f);line(.85f,.61f)
        cubic(.61f,.47f,.98f,.42f,1f,.25f);cubic(1f,.06f,.75f,0f,.52f,0f)
        cubic(.4f,0f,.3f,0f,.22f,.02f);path.close()
        move(.25f,.69f);line(.87f,.69f);line(.9f,.87f)
        cubic(.9f,1f,.73f,1f,.57f,1f);cubic(.3f,1f,.22f,.94f,.24f,.82f);path.close()
    }
    return path
}
fun DrawScope.drawWalkingFootprints(color:Color)=drawPath(walkingFootprintsPath(size.width,size.height),color)

/** The text's real glyphs, in track-local coordinates; no rectangular colour flash. */
data class FootprintClockFrame(val shape:Shape,val size:androidx.compose.ui.geometry.Size,
    val topLeft:Offset,val width:Float,val color:Color,val clipBounds:androidx.compose.ui.geometry.Rect) {
    fun translated(by:Offset)=copy(topLeft=topLeft+by,clipBounds=clipBounds.translate(by))
}
data class FootprintClockText(val layout:TextLayoutResult,val topLeft:Offset,val color:Color,
    val frame:FootprintClockFrame?=null) {
    fun translated(by:Offset)=copy(topLeft=topLeft+by,frame=frame?.translated(by))
}

/** A centred 8 px stroke leaves 4 physical pixels visible outside the coloured fill. */
const val FOOTPRINT_OUTLINE_PX=4f

fun footprintOutlineColor(text:Color):Color = if(text.luminance()<.5f) Color.White else Color.Black

fun footprintAccentColor(accent:Color,monochrome:Boolean):Color =
    if(monochrome || maxOf(accent.red,accent.green,accent.blue)-minOf(accent.red,accent.green,accent.blue)<.02f) Color.Gray else accent

/**
 * Parts of a track that cross a time chip on their way to the bar are drawn in [accent];
 * everywhere else the track keeps its [color]. [zones] is null when nothing is crossed.
 */
internal fun DrawScope.withAccentZones(zones:Path?,color:Color,accent:Color,draw:DrawScope.(Color)->Unit) {
    if(zones==null) {draw(color);return}
    clipPath(zones,androidx.compose.ui.graphics.ClipOp.Difference) {draw(color)}
    clipPath(zones) {draw(accent)}
}

internal fun accentZonePath(zones:List<androidx.compose.ui.geometry.RoundRect>):Path? =
    if(zones.isEmpty()) null else Path().apply {zones.forEach {addRoundRect(it)}}

private fun DrawScope.drawClockOutlineMask(bounds:androidx.compose.ui.geometry.Rect,ink:Color,
    mask:DrawScope.()->Unit,drawOutline:DrawScope.(Color)->Unit) {
    val canvas=drawContext.canvas
    canvas.saveLayer(bounds,Paint())
    try {
        mask()
        canvas.saveLayer(bounds,Paint().apply {blendMode=BlendMode.SrcIn})
        try {drawOutline(footprintOutlineColor(ink))} finally {canvas.restore()}
    } finally {canvas.restore()}
}

/** Four physical pixels outside the sole, over both clock glyphs and the visible chip contour. */
internal fun DrawScope.drawFootprintClockOutline(path:Path,clocks:List<FootprintClockText>,
    drawOutline:DrawScope.(Color)->Unit) {
    val bounds=path.getBounds().inflate(FOOTPRINT_OUTLINE_PX)
    for(clock in clocks) {
        val clockBounds=androidx.compose.ui.geometry.Rect(clock.topLeft,
            androidx.compose.ui.geometry.Size(clock.layout.size.width.toFloat(),clock.layout.size.height.toFloat()))
        if(bounds.overlaps(clockBounds)) drawClockOutlineMask(bounds,clock.color,
            {drawText(clock.layout,color=Color.White,topLeft=clock.topLeft)},drawOutline)
        clock.frame?.takeIf {bounds.overlaps(it.clipBounds)}?.let {frame->
            drawClockOutlineMask(bounds,frame.color,{
                clipRect(frame.clipBounds.left,frame.clipBounds.top,frame.clipBounds.right,frame.clipBounds.bottom) {
                    translate(frame.topLeft.x,frame.topLeft.y) {
                        drawClockFrame(frame.shape.createOutline(frame.size,layoutDirection,this),Color.White,style=Stroke(frame.width))
                    }
                }
            },drawOutline)
        }
    }
}

/** The fill stays in the theme accent; clocks only affect its fine outside contour. */
fun DrawScope.drawFootprintOverClocks(path:Path,color:Color,alpha:Float,clocks:List<FootprintClockText>) {
    if(alpha<=0f) return
    drawFootprintClockOutline(path,clocks) {outline->
        drawPath(path,outline.copy(alpha=color.alpha*alpha),style=Stroke(FOOTPRINT_OUTLINE_PX*2,join=StrokeJoin.Round))
    }
    drawPath(path,color.copy(alpha=color.alpha*alpha))
}

@Composable fun WalkingFootprintTrack(color:Color,modifier:Modifier=Modifier,trackKey:String="",
    clocks:List<FootprintClockText> = emptyList(),sizeScale:Float=1f,straight:Boolean=false,
    style:FootprintStyle?=null,horizontal:Boolean=false,glyphScale:Float=1f,
    accentZones:List<androidx.compose.ui.geometry.RoundRect> = emptyList(),accent:Color=color) {
    val settings=LocalAppSettings.current
    val preference=settings.footprints.normalized()
    val selectedStyle=style ?: preference.style
    val pathStraight=preference.path==FootprintPathMode.STRAIGHT
    val quiet=settings.reduceMotion
    if(quiet || !preference.enabled) {DottedWalkingTrack(color,modifier,horizontal,clocks,glyphScale,accentZones,accent);return}
    if(selectedStyle.continuous) {RollingWalkingTrack(color,modifier,trackKey,clocks,sizeScale,straight||pathStraight,selectedStyle,horizontal,glyphScale,preference.speed,accentZones,accent);return}
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    val configuredSeed=LocalFootprintSeed.current
    val nonce=remember(trackKey,configuredSeed) {walkingRouteSeed(configuredSeed ?: Random.nextLong(),trackKey)}
    var cycle by remember(trackKey) {mutableLongStateOf(0L)}
    val progress=remember(trackKey) {mutableFloatStateOf(0f)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var moving by remember {mutableStateOf(ValueAnimator.areAnimatorsEnabled())}
    val gaitScale=if(glyphScale.isFinite())glyphScale.coerceIn(.7f,2f)else 1f
    val latestScale by rememberUpdatedState(gaitScale)
    fun stepsFor(index:Long):List<TimedFootstep> {
        val steps=walkingGaitSteps(if(horizontal) viewport.height.toFloat() else viewport.width.toFloat(),
            if(horizontal) viewport.width.toFloat() else viewport.height.toFloat(),nonce+index*0x9e3779bL,sizeScale*latestScale,straight||pathStraight,selectedStyle)
        return if(horizontal) steps.map {step->step.copy(geometry=step.geometry.let {it.copy(x=it.y,y=viewport.height-it.x,angle=it.angle+PI.toFloat()/2)})} else steps
    }
    // Replan the whole stride and stance, rather than enlarging an already packed set of glyphs.
    val steps=remember(viewport,cycle,nonce,sizeScale,gaitScale,pathStraight,straight,horizontal,selectedStyle) {stepsFor(cycle)}
    val latestDuration by rememberUpdatedState(walkingGaitDuration(steps))
    LaunchedEffect(lifecycle,viewport,trackKey,nonce,sizeScale,pathStraight,straight,horizontal,preference.speed,selectedStyle) {
        moving=ValueAnimator.areAnimatorsEnabled() && (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f)>0f
        if(!moving || viewport.width<=0 || viewport.height<=0) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous:Long?=null;var current=cycle
            var remainingDelay=Random(nonce xor 0x52f3L).nextInt(600)*1_000_000L
            val cadence=.94+Random(nonce).nextDouble()*.14
            while(isActive) {
                val frame=withInfiniteAnimationFrameNanos {it}
                val delta=previous?.let {(frame-it).coerceAtLeast(0L)}?:0L;previous=frame
                val scale=coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                if(scale<=0f) {moving=false;return@repeatOnLifecycle}
                val effective=(delta-remainingDelay).coerceAtLeast(0L);remainingDelay=(remainingDelay-delta).coerceAtLeast(0L)
                var phase=progress.floatValue+effective.toFloat()/(latestDuration*1_000_000f)*(preference.speed.coerceIn(.25f,2f)/(scale*cadence)).toFloat()
                while(phase>=1f) {phase-=1f;current++}
                cycle=current
                progress.floatValue=phase
            }
        }
    }
    Box(modifier.onSizeChanged {viewport=it}.drawWithCache {
        val static=footprintShapesPath(steps.map {it.geometry},selectedStyle)
        val soles=steps.map {footprintShapesPath(listOf(it.geometry),selectedStyle)}
        val zones=accentZonePath(accentZones)
        onDrawBehind {
            withAccentZones(zones,color,accent) {ink->
                if(!moving) drawFootprintOverClocks(static,ink,1f,clocks) else if(steps.isNotEmpty()) {
                    val phase=progress.floatValue
                    val time=phase*walkingGaitDuration(steps)
                    steps.forEachIndexed {i,step->
                        val alpha=walkingGaitAlpha(step,time)
                        if(alpha>0f) {
                            val contact=footstepContactMask(step.geometry,time-step.bornMillis,selectedStyle)
                            if(contact==null) drawFootprintOverClocks(soles[i],ink,alpha,clocks)
                            else clipPath(contact) {drawFootprintOverClocks(soles[i],ink,alpha,clocks)}
                        }
                    }
                }
            }
        }
    })
}

/** A stable dotted rail replaces footprints when switched off, including in quiet mode. */
@Composable private fun DottedWalkingTrack(color:Color,modifier:Modifier,horizontal:Boolean,clocks:List<FootprintClockText>,glyphScale:Float,
    accentZones:List<androidx.compose.ui.geometry.RoundRect> = emptyList(),accent:Color=color) {
    Box(modifier.testTag("walking-static-dots").drawWithCache {
        val zones=accentZonePath(accentZones)
        onDrawBehind {withAccentZones(zones,color,accent) {color->
            val length=if(horizontal) size.width else size.height
            val scale=if(glyphScale.isFinite())glyphScale.coerceIn(.7f,2f)else 1f
            val spacing=10.dp.toPx()*scale;var at=spacing/2
            while(at<length) {
                val point=if(horizontal) Offset(at,size.height/2) else Offset(size.width/2,at)
                val radius=1.5.dp.toPx()*scale
                val dot=androidx.compose.ui.geometry.Rect(point-Offset(radius,radius),point+Offset(radius,radius))
                val coversClock=clocks.any {dot.overlaps(androidx.compose.ui.geometry.Rect(it.topLeft,
                    androidx.compose.ui.geometry.Size(it.layout.size.width.toFloat(),it.layout.size.height.toFloat())))}
                if(!coversClock) drawCircle(color,radius,point)
                at+=spacing
            }
        }}
    })
}

internal fun scaledFootprintGlyph(g:FootstepGeometry,scale:Float,width:Float,height:Float):FootstepGeometry {
    val factor=if(scale.isFinite())scale.coerceIn(.7f,2f)else 1f
    if(factor==1f)return g
    val size=g.scale*factor
    val radius=hypot(FOOTPRINT_LENGTH_PX/2,FOOTPRINT_WIDTH_PX/2)*size
    fun fit(position:Float,length:Float):Float=if(length<=2*radius)length/2 else position.coerceIn(radius,length-radius)
    return g.copy(x=fit(g.x,width),y=fit(g.y,height),scale=size)
}

