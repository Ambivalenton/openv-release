// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object QuietMaterialMotion:MotionScheme {
    override fun <T> defaultSpatialSpec():FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec():FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec():FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec():FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec():FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec():FiniteAnimationSpec<T> = snap()
}
/**
 * Size animation without a permanent clip. Compose's animateContentSize clips its content
 * to a rectangle at all times, which cut off every shadow, glow and bulge drawn outside a card or
 * control (settings tiles, the search controls, the departure/arrival pill). Here only a growing edge
 * is clipped, and only while it grows: content that is not yet uncovered stays hidden, everything
 * else (sides, top, shadows below a shrinking or resting block) is drawn freely.
 */
@Composable fun Modifier.calmContentSize(spec:FiniteAnimationSpec<IntSize> = spring(visibilityThreshold=IntSize.VisibilityThreshold),
    alignment:androidx.compose.ui.Alignment=androidx.compose.ui.Alignment.TopStart):Modifier {
    if(LocalAppSettings.current.reduceMotion) return this
    val scope=rememberCoroutineScope()
    val state=remember {CalmSizeState()}
    state.spec=spec
    return this.drawWithContent {
        val animation=state.animation
        if(animation==null || !animation.isRunning || (!state.growsWidth && !state.growsHeight)) drawContent()
        else {
            val open=OPEN_EDGE
            clipRect(-open,-open,if(state.growsWidth) size.width else size.width+open,
                if(state.growsHeight) size.height else size.height+open) {this@drawWithContent.drawContent()}
        }
    }.layout {measurable,constraints->
        val placeable=measurable.measure(constraints)
        val target=IntSize(placeable.width,placeable.height)
        val shown=state.follow(target,scope)
        val width=constraints.constrainWidth(shown.width);val height=constraints.constrainHeight(shown.height)
        layout(width,height) {placeable.place(alignment.align(target,IntSize(width,height),layoutDirection))}
    }
}

/** Far enough outside any composable that shadows and glows are never touched. */
private const val OPEN_EDGE=100_000f

private class CalmSizeState {
    var spec:FiniteAnimationSpec<IntSize> = spring(visibilityThreshold=IntSize.VisibilityThreshold)
    var animation:Animatable<IntSize,AnimationVector2D>?=null
    var growsWidth by mutableStateOf(false)
    var growsHeight by mutableStateOf(false)
    fun follow(target:IntSize,scope:kotlinx.coroutines.CoroutineScope):IntSize {
        val current=animation ?: Animatable(target,IntSize.VectorConverter,IntSize(1,1)).also {animation=it}
        if(current.targetValue!=target) {
            val now=current.value
            growsWidth=target.width>now.width;growsHeight=target.height>now.height
            scope.launch {current.animateTo(target,spec)}
        }
        return current.value
    }
}

/** The sole deliberate motion exception in quiet mode: a slow, constant-speed loading rail.
 * There is no travelling animation when idle or when Android disables animator motion. */
@Composable fun CalmLoadingRail(modifier:Modifier=Modifier,isLoading:Boolean=true,description:String=tr("Verbindungen werden geladen")) {
    val moving=isLoading && ValueAnimator.areAnimatorsEnabled()
    val progress=if(moving) rememberInfiniteTransition(label="calm-loading").animateFloat(0f,1f,
        infiniteRepeatable(tween(4_800,easing=LinearEasing)),label="calm-loading-position").value else .5f
    val base=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.14f)
    val ink=MaterialTheme.colorScheme.primary.copy(alpha=.75f)
    Canvas(modifier.fillMaxWidth().height(18.dp).testTag("calm-loading-bar").semantics {
        if(isLoading) {contentDescription=description;progressBarRangeInfo=ProgressBarRangeInfo.Indeterminate}
    }) {
        val y=size.height/2;val thickness=2.dp.toPx()
        drawLine(base,Offset(0f,y),Offset(size.width,y),thickness,StrokeCap.Round)
        if(isLoading) clipRect {
            val band=size.width*.18f;val x=-band+progress*(size.width+band)
            drawLine(ink,Offset(x,y),Offset(x+band,y),thickness,StrokeCap.Round)
        }
    }
}

@Composable fun <T> calmSpec(normal:FiniteAnimationSpec<T>):FiniteAnimationSpec<T> =
    if(LocalAppSettings.current.reduceMotion) snap() else normal

@Composable fun CalmVisibility(visible:Boolean,modifier:Modifier=Modifier,
    enter:androidx.compose.animation.EnterTransition=androidx.compose.animation.fadeIn()+androidx.compose.animation.expandVertically(),
    exit:androidx.compose.animation.ExitTransition=androidx.compose.animation.fadeOut()+androidx.compose.animation.shrinkVertically(),
    label:String="calm-visibility",content:@Composable androidx.compose.animation.AnimatedVisibilityScope.()->Unit) {
    val quiet=LocalAppSettings.current.reduceMotion
    androidx.compose.animation.AnimatedVisibility(visible,modifier,if(quiet) androidx.compose.animation.EnterTransition.None else enter,
        if(quiet) androidx.compose.animation.ExitTransition.None else exit,label,content)
}
@Composable fun CalmVisibility(visibleState:MutableTransitionState<Boolean>,modifier:Modifier=Modifier,
    enter:androidx.compose.animation.EnterTransition=androidx.compose.animation.fadeIn()+androidx.compose.animation.expandVertically(),
    exit:androidx.compose.animation.ExitTransition=androidx.compose.animation.fadeOut()+androidx.compose.animation.shrinkVertically(),
    label:String="calm-visibility",content:@Composable androidx.compose.animation.AnimatedVisibilityScope.()->Unit) {
    val quiet=LocalAppSettings.current.reduceMotion
    androidx.compose.animation.AnimatedVisibility(visibleState,modifier,if(quiet) androidx.compose.animation.EnterTransition.None else enter,
        if(quiet) androidx.compose.animation.ExitTransition.None else exit,label,content)
}
