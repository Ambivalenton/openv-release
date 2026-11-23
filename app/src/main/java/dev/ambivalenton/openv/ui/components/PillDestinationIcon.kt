// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import dev.ambivalenton.openv.data.model.reduceMotion
import android.animation.ValueAnimator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import kotlin.math.*
import kotlinx.coroutines.launch

/**
 * Brief feedback on activation, with fixed hit areas and a quiet static alternative. Build 106: the
 * symbols' own gestures are springs started by an impulse (the train pulls off and rocks back, the
 * magnifier swings, the overview's lines run up), so they settle naturally instead of ending on a timer.
 */
@Composable fun PillDestinationIcon(tab:PillDestination,ink:Color,modifier:Modifier=Modifier,selected:Boolean=false) {
    val still=LocalAppSettings.current.reduceMotion||!ValueAnimator.areAnimatorsEnabled()
    val swing=remember {Animatable(0f)}
    val lines=remember {Animatable(1f)}
    LaunchedEffect(selected,tab.timeline,still) {
        if(selected&&!still) {
            launch {swing.animateTo(0f,spring(dampingRatio=.38f,stiffness=380f),initialVelocity=ICON_SWING_VELOCITY)}
            launch {lines.snapTo(0f);lines.animateTo(1f,spring(dampingRatio=.82f,stiffness=260f))}
        } else {swing.snapTo(0f);lines.snapTo(1f)}
    }
    val p=lines.value
    AnimatedContent(tab.timeline,modifier,label="pill-timeline-icon",transitionSpec={
        (if(still)EnterTransition.None togetherWith ExitTransition.None else
            (fadeIn(tween(180))+scaleIn(spring(dampingRatio=.55f,stiffness=500f),initialScale=.7f)) togetherWith fadeOut(tween(120))).using(SizeTransform(clip=false))
    }) {timeline->
        if(!timeline && tab.icon==R.drawable.ic_overview && p<.999f && !still)Canvas(Modifier.size(24.dp)) {
            val shift=p*6.dp.toPx()
            clipRect {
                for(i in -1..3) {
                    val y=(5+i*6).dp.toPx()-shift
                    val width=if((i+4)%3==2)11.dp.toPx()else 16.dp.toPx()
                    drawLine(ink,Offset(4.dp.toPx(),y),Offset(4.dp.toPx()+width,y),2.dp.toPx())
                }
            }
        }else AppIcon(if(timeline)R.drawable.ic_journey_timeline else tab.icon,null,Modifier.graphicsLayer {
            if(!still&&!timeline) {
                val s=swing.value
                when(tab.icon) {
                    // Pulls off to the right, rocks back a little past its place, settles.
                    R.drawable.ic_train->{translationX=s*7.dp.toPx();rotationZ=-s*4f}
                    // The magnifier swings and grows for a moment, like a quick look around.
                    R.drawable.ic_search,R.drawable.ic_dashboard->{rotationZ=-14f*s;scaleX=1f+.18f*abs(s);scaleY=scaleX}
                }
            }
        },tint=ink)
    }
}

/** With spring .38/380 this impulse swings a symbol to about its full gesture once (peak ≈ 0.032 × impulse). */
private const val ICON_SWING_VELOCITY=30f
