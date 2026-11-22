// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.coroutines.coroutineContext
import kotlin.math.*
import kotlinx.coroutines.isActive

const val STOP_RING_GAP_PX=10f
const val TIMELINE_STROKE_WIDTH_PX=20f
fun stopDotRadiusPx(fontSizePx: Float)=fontSizePx/2f
fun stopCutoutRadiusPx(fontSizePx: Float)=stopDotRadiusPx(fontSizePx)+STOP_RING_GAP_PX

/** A single phase only exists for the active leg. Provider refreshes never trigger this clock. */
@Composable fun rememberSegmentPhase(active: Boolean,durationMillis:Int=1_400): Animatable<Float,AnimationVector1D> {
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val phase=remember {Animatable(0f)};val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active,lifecycle,durationMillis,quiet) {
        phase.snapTo(0f)
        if(quiet || !active || !ValueAnimator.areAnimatorsEnabled() || coroutineContext[MotionDurationScale]?.scaleFactor==0f) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var started:Long?=null
            while(isActive) {
                val frame=withInfiniteAnimationFrameNanos {it}
                val start=started ?: frame.also {started=it}
                val scale=coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                if(scale<=0f) {phase.snapTo(0f);return@repeatOnLifecycle}
                val duration=(durationMillis.coerceAtLeast(1)*1_000_000.0*scale).toLong().coerceAtLeast(1L)
                phase.snapTo(((frame-start)%duration).toFloat()/duration*(2*PI).toFloat())
            }
        }
    }
    return phase
}

/** Clear operates on this rail's own offscreen layer, including its fading gradient.
 * Dot diameter follows the adjacent font in actual pixels; halo=10 px; stroke=20 px.
 */
@Composable fun SegmentStopRail(color: Color,active: Boolean,phase: ()->Float,first: Boolean,last: Boolean,
    faded: Boolean,modifier: Modifier=Modifier,nodeDiameterPx: Float?=null,firstLineCenterPx: Float?=null,walking:Boolean=false) {
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val density=LocalDensity.current;val style=MaterialTheme.typography.bodyLarge
    val diameter=nodeDiameterPx ?: with(density) {style.fontSize.toPx()}
    val centerY=firstLineCenterPx ?: with(density) {style.lineHeight.toPx()/2}
    Canvas(modifier.graphicsLayer {compositingStrategy=CompositingStrategy.Offscreen}) {
        val center=Offset(size.width/2,centerY)
        val top=if(first) center.y else 0f;val bottom=if(last) center.y else size.height
        if(bottom>top) {
            val shift=if(active && !quiet) phase() else 0f
            val path=Path();var y=top
            while(y<bottom) {
                val envelope=sin(PI*(y/size.height)).toFloat().coerceAtLeast(0f)
                val wave=if(active && !quiet) sin(y/46.dp.toPx()*(2*PI).toFloat()-shift)*3.dp.toPx()*envelope else 0f
                if(y==top) path.moveTo(center.x+wave,y) else path.lineTo(center.x+wave,y)
                y+=1f
            }
            path.lineTo(center.x,bottom)
            val brush=if(faded) Brush.verticalGradient(listOf(Color.Transparent,color),endY=40.dp.toPx()) else Brush.verticalGradient(listOf(color,color))
            if(walking && active && !quiet) {
                val pulse=.75f+.25f*sin(shift)
                drawPath(path,color.copy(alpha=.10f*pulse),style=Stroke(TIMELINE_STROKE_WIDTH_PX+14.dp.toPx(),cap=StrokeCap.Round))
                drawPath(path,color.copy(alpha=.22f*pulse),style=Stroke(TIMELINE_STROKE_WIDTH_PX+7.dp.toPx(),cap=StrokeCap.Round))
            }
            drawPath(path,brush,style=Stroke(TIMELINE_STROKE_WIDTH_PX+(if(walking && active && !quiet) 3.dp.toPx() else 0f),cap=StrokeCap.Round))
        }
        drawCircle(Color.Transparent,stopCutoutRadiusPx(diameter),center,blendMode=BlendMode.Clear)
        drawCircle(color,stopDotRadiusPx(diameter),center)
    }
}
