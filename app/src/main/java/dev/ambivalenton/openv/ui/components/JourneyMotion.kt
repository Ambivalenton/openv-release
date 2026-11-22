// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion

import android.animation.ValueAnimator
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Native haptics respect Android's user setting; no raw vibrator or extra permission. */
class ChartHaptics(private val view: View) {
    private var lastCrossing = Long.MIN_VALUE
    fun crossing() {
        val now = SystemClock.uptimeMillis()
        if (lastCrossing != Long.MIN_VALUE && now-lastCrossing < 50) return
        lastCrossing=now
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK)
    }
    fun locked() { view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END
        else if(Build.VERSION.SDK_INT>=27) HapticFeedbackConstants.VIRTUAL_KEY_RELEASE else HapticFeedbackConstants.VIRTUAL_KEY) }
}

@Composable fun rememberChartHaptics(): ChartHaptics {
    val view=LocalView.current
    return remember(view) { ChartHaptics(view) }
}

@Stable class JourneyWiggle {
    val amplitude=Animatable(0f)
    val phase=Animatable(0f)
}

/** Draw-only state: waves redraw Canvas, not the whole route. Inactive lines settle to zero. */
@Composable fun rememberJourneyWiggle(active: Boolean, updateToken: Any?): JourneyWiggle {
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val motion=remember { JourneyWiggle() }
    var previous by remember { mutableStateOf(updateToken) }
    var previouslyActive by remember {mutableStateOf(active)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active,if(active) null else updateToken,lifecycle,quiet) {
        if (quiet || !ValueAnimator.areAnimatorsEnabled() || coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
            motion.amplitude.snapTo(0f);return@LaunchedEffect
        }
        val stopped=previouslyActive && !active
        previouslyActive=active
        val changed=previous != updateToken
        previous=updateToken
        if (!active && (!changed || stopped)) { motion.amplitude.animateTo(0f,tween(320));return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            motion.amplitude.animateTo(1f,tween(160))
            if(active) {
                var started:Long?=null
                while(isActive) {
                    val frame=withInfiniteAnimationFrameNanos {it}
                    val start=started ?: frame.also {started=it}
                    val scale=coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                    if(scale<=0f) {motion.amplitude.snapTo(0f);return@repeatOnLifecycle}
                    val duration=(2_600_000_000.0*scale).toLong().coerceAtLeast(1L)
                    motion.phase.snapTo(((frame-start)%duration).toFloat()/duration*(2*PI).toFloat())
                }
            } else {
                motion.phase.animateTo(motion.phase.value+(2*PI).toFloat(),tween(2600,easing=LinearEasing))
                motion.amplitude.animateTo(0f,tween(420))
            }
        }
    }
    return motion
}

@Composable fun rememberJourneyNow(): Long {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    return produceState(System.currentTimeMillis(),lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { value=System.currentTimeMillis();delay(1000) }
        }
    }.value
}

/** One continuous phase while moving; refresh tokens cannot restart the wave. */
@Composable fun JourneyWiggleProgress(progress:Float,active:Boolean,updateToken:Any?,modifier:Modifier=Modifier,urgent:Boolean=false,imminent:Boolean=false) {
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
    val primary=MaterialTheme.colorScheme.primary
    val mono=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.monochrome ||
        maxOf(primary.red,primary.green,primary.blue)-minOf(primary.red,primary.green,primary.blue)<.055f
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    val color=if(imminent&&!mono)Color(0xffb00020)else if(mono)if(dark)Color.White.copy(alpha=if(urgent)1f else .65f) else Color.Black.copy(alpha=if(urgent).95f else .6f)
        else androidx.compose.ui.graphics.lerp(primary,if(urgent)MaterialTheme.colorScheme.error else Color(0xff378957),.65f)
    val track=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.2f)
    val phase=if(active&&!quiet) {
        val animation=rememberInfiniteTransition(label="journey-motion")
        animation.animateFloat(0f,(2*PI).toFloat(),infiniteRepeatable(tween(if(imminent)2600 else if(urgent)3600 else 7200,easing=LinearEasing),RepeatMode.Restart),label="journey-wave").value
    }else 0f
    Canvas(modifier.fillMaxWidth().height(if(imminent)32.dp else 18.dp).progressSemantics(progress.coerceIn(0f,1f))) {
        val y=size.height/2;val path=Path();val end=if(imminent)size.width else size.width*progress.coerceIn(0f,1f)
        var x=0f
        while(x<=size.width) {
            val wave=if(active&&!quiet)sin(x/30.dp.toPx()*(2*PI).toFloat()-phase)*(if(imminent)7.dp else 3.dp).toPx() else 0f
            if(x==0f)path.moveTo(x,y+wave)else path.lineTo(x,y+wave)
            x+=2.dp.toPx()
        }
        drawPath(path,track,style=Stroke(2.dp.toPx()))
        if(active)drawPath(path,color.copy(alpha=if(urgent).16f else .07f),style=Stroke(if(imminent)20.dp.toPx()else if(urgent)11.dp.toPx()else 8.dp.toPx()))
        drawPath(path,color.copy(alpha=if(active).42f else .20f),style=Stroke(2.dp.toPx()))
        clipRect(right=end) {drawPath(path,color,style=Stroke((if(imminent)7.dp else 3.dp).toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round))}
    }
}
