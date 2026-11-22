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

/** Brief feedback on activation, with fixed hit areas and a quiet static alternative. */
@Composable fun PillDestinationIcon(tab:PillDestination,ink:Color,modifier:Modifier=Modifier,selected:Boolean=false) {
    val still=LocalAppSettings.current.reduceMotion||!ValueAnimator.areAnimatorsEnabled()
    val progress=remember {Animatable(1f)}
    LaunchedEffect(selected,tab.timeline,still) {
        if(selected&&!still){progress.snapTo(0f);progress.animateTo(1f,tween(560,easing=FastOutSlowInEasing))}else progress.snapTo(1f)
    }
    val p=progress.value
    AnimatedContent(tab.timeline,modifier,label="pill-timeline-icon",transitionSpec={
        (if(still)EnterTransition.None togetherWith ExitTransition.None else
            (fadeIn(tween(180))+scaleIn(tween(220),initialScale=.86f)) togetherWith fadeOut(tween(120))).using(SizeTransform(clip=false))
    }) {timeline->
        if(!timeline && tab.icon==R.drawable.ic_overview && p<1f && !still)Canvas(Modifier.size(24.dp)) {
            val shift=p*6.dp.toPx()
            clipRect {
                for(i in -1..3) {
                    val y=(5+i*6).dp.toPx()-shift
                    val width=if((i+4)%3==2)11.dp.toPx()else 16.dp.toPx()
                    drawLine(ink,Offset(4.dp.toPx(),y),Offset(4.dp.toPx()+width,y),2.dp.toPx())
                }
            }
        }else AppIcon(if(timeline)R.drawable.ic_journey_timeline else tab.icon,null,Modifier.graphicsLayer {
            if(!still&&!timeline)when(tab.icon) {
                R.drawable.ic_train->{translationX=sin(PI*p).toFloat()*6.dp.toPx();translationY=-abs(sin(PI*p*4)).toFloat()*1.dp.toPx()*(1-p)}
                R.drawable.ic_search->{scaleX=1+.24f*sin(PI*p).toFloat();scaleY=scaleX;rotationZ=-9*sin(PI*p).toFloat()}
            }
        },tint=ink)
    }
}
