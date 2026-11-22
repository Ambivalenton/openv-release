// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.ambivalenton.openv.data.model.FootprintStyle
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.*
import kotlin.random.Random

data class RollingTrackGeometry(val ribbons:List<Path>,val mask:Path,val start:Offset,val end:Offset)

internal fun rollingCycleMillis(style:FootprintStyle)=if(style==FootprintStyle.WHEELCHAIR)18_000L else 9_000L
/** A smooth push and coast rhythm with strictly positive forward velocity. */
internal fun rollingMotionPhase(style:FootprintStyle,phase:Float):Float {
    val p=phase.coerceIn(0f,1f)
    return if(style==FootprintStyle.WHEELCHAIR)(p+.35f*sin(p*8f*PI.toFloat())/(8f*PI.toFloat())).coerceIn(0f,1f) else p
}

/** The head draws a continuous tyre trace. Older rear sections fade before the newer front. */
fun rollingTrackAlpha(position:Float,phase:Float):Float {
    if(!position.isFinite() || !phase.isFinite())return 0f
    val age=phase.coerceIn(0f,1f)*1.7f-position
    fun smooth(t:Float)=t.coerceIn(0f,1f).let {it*it*(3-2*it)}
    return when {age<=0f || age>=.65f->0f;age<.025f->smooth(age/.025f);age<.32f->1f;else->1f-smooth((age-.32f)/.33f)}
}

/** Whole ribbons have shared continuous edges, avoiding antialiased seams between tiny strips. */
fun rollingTrackGeometry(width:Float,height:Float,seed:Long,sizeScale:Float,straight:Boolean,
    style:FootprintStyle,horizontal:Boolean=false):RollingTrackGeometry? {
    val route=walkingTrajectory(if(horizontal)height else width,if(horizontal)width else height,seed,sizeScale,straight)?:return null
    if(route.length<=.01f)return null
    val offsets=if(style==FootprintStyle.WHEELCHAIR)listOf(-7f,7f)else listOf(0f)
    val half=(if(style==FootprintStyle.WHEELCHAIR)1.35f else 1f)*route.scale
    val count=96
    fun point(index:Int,offset:Float,edge:Float=0f):Offset {
        val p=route.at(route.length*index/count)
        val lateral=offset*route.scale+edge
        val x=p.x+cos(p.heading)*lateral
        val y=p.y-sin(p.heading)*lateral
        return if(horizontal)Offset(y,height-x)else Offset(x,y)
    }
    val paths=offsets.map {offset->Path().apply {
        for(i in 0..count) {val p=point(i,offset,half);if(i==0)moveTo(p.x,p.y)else lineTo(p.x,p.y)}
        for(i in count downTo 0) {val p=point(i,offset,-half);lineTo(p.x,p.y)}
        close()
    }}
    val mask=Path().apply {paths.forEach {addPath(it)}}
    return RollingTrackGeometry(paths,mask,point(0,0f),point(count,0f))
}

fun DrawScope.drawRollingTrace(geometry:RollingTrackGeometry,color:Color,phase:Float?=null,clocks:List<FootprintClockText> = emptyList()) {
    if(phase==null){drawFootprintOverClocks(geometry.mask,color,1f,clocks);return}
    fun brush(ink:Color,shift:Offset=Offset.Zero):Brush {
        val stops=Array(65) {i->val position=i/64f;position to ink.copy(alpha=ink.alpha*rollingTrackAlpha(position,phase))}
        return Brush.linearGradient(*stops,start=geometry.start-shift,end=geometry.end-shift)
    }
    drawFootprintClockOutline(geometry.mask,clocks) {outline->
        drawPath(geometry.mask,brush(outline.copy(alpha=color.alpha)),style=Stroke(FOOTPRINT_OUTLINE_PX*2,join=StrokeJoin.Round))
    }
    drawPath(geometry.mask,brush(color))
}

@Composable internal fun RollingWalkingTrack(color:Color,modifier:Modifier,trackKey:String,clocks:List<FootprintClockText>,
    sizeScale:Float,straight:Boolean,style:FootprintStyle,horizontal:Boolean,glyphScale:Float,speed:Float,
    accentZones:List<androidx.compose.ui.geometry.RoundRect> = emptyList(),accent:Color=color) {
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    val configured=LocalFootprintSeed.current
    val seed=remember(trackKey,configured){walkingRouteSeed(configured?:Random.nextLong(),trackKey)}
    var cycle by remember(trackKey){mutableLongStateOf(0L)}
    val progress=remember(trackKey){mutableFloatStateOf(0f)}
    var moving by remember{mutableStateOf(ValueAnimator.areAnimatorsEnabled())}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val factor=if(glyphScale.isFinite())glyphScale.coerceIn(.7f,2f)else 1f
    val geometry=remember(viewport,cycle,seed,sizeScale,factor,straight,style,horizontal) {
        rollingTrackGeometry(viewport.width.toFloat(),viewport.height.toFloat(),seed+cycle*0x9e3779bL,sizeScale*factor,straight,style,horizontal)
    }
    // Size is deliberately absent: dragging the slider keeps the current route and travel phase.
    LaunchedEffect(lifecycle,viewport,seed,trackKey,straight,style,horizontal,speed) {
        moving=ValueAnimator.areAnimatorsEnabled() && (coroutineContext[MotionDurationScale]?.scaleFactor?:1f)>0f
        if(!moving || viewport.width<=0 || viewport.height<=0)return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous:Long?=null
            while(isActive) {
                val frame=withInfiniteAnimationFrameNanos {it}
                val delta=previous?.let {frame-it}?:0L;previous=frame
                val motionScale=coroutineContext[MotionDurationScale]?.scaleFactor?:1f
                if(motionScale<=0f){moving=false;return@repeatOnLifecycle}
                var phase=progress.floatValue+delta/(rollingCycleMillis(style)*1_000_000f)*speed.coerceIn(.25f,2f)/motionScale
                if(phase>=1f){phase%=1f;cycle++}
                progress.floatValue=phase
            }
        }
    }
    Box(modifier.onSizeChanged {viewport=it}.drawWithCache {
        val zones=accentZonePath(accentZones)
        onDrawBehind {geometry?.let {g->withAccentZones(zones,color,accent) {ink->drawRollingTrace(g,ink,if(moving)rollingMotionPhase(style,progress.floatValue) else null,clocks)}}}
    })
}

