// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion

import android.animation.ValueAnimator
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr
import kotlinx.coroutines.*
import kotlin.math.*

@Stable
class PinTransitionController(private val scope: CoroutineScope,private val reduceMotion: ()->Boolean,private val impact:()->Unit={}) {
    var tripId: String? by mutableStateOf(null);private set
    val progress=Animatable(0f)
    val busy get()=tripId!=null
    private var job: Job?=null
    private var generation=0L
    fun begin(id: String,commit: suspend ()->Unit) {
        if(busy) return
        val token=++generation
        tripId=id
        job=scope.launch {
            try {
                progress.snapTo(0f)
                val reduced=reduceMotion() || coroutineContext[androidx.compose.ui.MotionDurationScale]?.scaleFactor==0f
                if(!reduced) {
                    progress.animateTo(PIN_IMPACT_FRACTION,tween((PIN_TRANSITION_MILLIS*PIN_IMPACT_FRACTION).toInt(),easing=LinearEasing))
                    ensureActive();impact()
                    progress.animateTo(PIN_COMMIT_FRACTION,tween((PIN_TRANSITION_MILLIS*(PIN_COMMIT_FRACTION-PIN_IMPACT_FRACTION)).toInt(),easing=LinearEasing))
                } else progress.snapTo(1f)
                ensureActive();commit()
                if(reduced) impact() else progress.animateTo(1f,tween((PIN_TRANSITION_MILLIS*(1-PIN_COMMIT_FRACTION)).toInt(),easing=LinearEasing))
            } finally { if(token==generation) {tripId=null;withContext(NonCancellable) {progress.snapTo(0f)}} }
        }
    }
    fun cancel() {generation++;job?.cancel();tripId=null}
}

@Composable fun rememberPinTransitionController(): PinTransitionController {
    val scope=rememberCoroutineScope();val context=LocalContext.current;val view=androidx.compose.ui.platform.LocalView.current
    val quiet=rememberUpdatedState(dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion)
    return remember(scope,context,view) { PinTransitionController(scope,{
        quiet.value ||
        !ValueAnimator.areAnimatorsEnabled() || (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled
    },{if(!quiet.value) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)}) }
}

@Composable fun PinMorphButton(tripId: String,controller: PinTransitionController,onClick: ()->Unit) {
    val color=MaterialTheme.colorScheme.secondaryContainer
    IconButton(onClick,enabled=!controller.busy,modifier=Modifier.size(48.dp).testTag("pin-$tripId")) {
        Box(Modifier.size(40.dp),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val p=if(controller.tripId==tripId) controller.progress.value else 0f
                val elastic=(p+sin(PI*p).toFloat()*.12f).coerceIn(0f,1.1f)
                val exponent=2f+2f*elastic
                val radius=size.minDimension/2
                val shape=Path()
                for(i in 0..128) {
                    val angle=2*PI*i/128
                    fun component(value: Double)=(sign(value)*abs(value).pow(2.0/exponent)*radius).toFloat()
                    val x=size.width/2+component(cos(angle));val y=size.height/2+component(sin(angle))
                    if(i==0) shape.moveTo(x,y) else shape.lineTo(x,y)
                }
                shape.close();drawPath(shape,color)
            }
            AppIcon(R.drawable.ic_pin,tr("Verbindung anpinnen"),Modifier.size(23.dp).graphicsLayer {
                val p=if(controller.tripId==tripId) controller.progress.value else 0f
                rotationZ=-18f*(1-p)
                translationY=(sin(PI*p)*-4+3*p).toFloat()*density
                scaleX=1f+.08f*sin(PI*p).toFloat();scaleY=scaleX
            })
        }
    }
}

/** Bottom-anchored silhouette: room to grow upward while the resting FAB sits close to the edge. */
@OptIn(ExperimentalFoundationApi::class)
@Composable fun PinFloatingActionButton(tripId:String,controller:PinTransitionController,
    modifier:Modifier=Modifier,onLongClick:(()->Unit)?=null,onClick:()->Unit) {
    val color=MaterialTheme.colorScheme.primaryContainer
    val ink=MaterialTheme.colorScheme.onPrimaryContainer
    val shadowInk=androidx.compose.ui.graphics.lerp(Color.Black,MaterialTheme.colorScheme.primary,.10f)
    val interactions=remember {androidx.compose.foundation.interaction.MutableInteractionSource()}
    val pressed by interactions.collectIsPressedAsState()
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val touchScale by androidx.compose.animation.core.animateFloatAsState(if(pressed && !controller.busy) .975f else 1f,
        if(quiet) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio=.82f,stiffness=550f),label="pin-press-weight")
    val progress=if(controller.tripId==tripId) controller.progress.value else 0f
    val frame=pinFabFrame(progress)
    val formed=((progress-.27f)/.30f).coerceIn(0f,1f)
    val shape=androidx.compose.foundation.shape.RoundedCornerShape(frame.corner.dp)
    val centerY=144f-8f-frame.height/2
    Box(modifier.size(112.dp,144.dp).testTag("timeline-pin-fab"),contentAlignment=Alignment.Center) {
        Box(Modifier.align(Alignment.TopCenter).offset(y=(144f-8f-frame.height).dp).size(frame.width.dp,frame.height.dp)
            .testTag("pin-$tripId").combinedClickable(interactionSource=interactions,indication=null,enabled=!controller.busy,
                onLongClick=onLongClick,onLongClickLabel=tr("Verbindung vormerken"),
                role=androidx.compose.ui.semantics.Role.Button,onClick=onClick)
            .semantics {contentDescription=tr("Verbindung anpinnen")}) {
            Box(Modifier.fillMaxSize().graphicsLayer {scaleX=touchScale;scaleY=frame.compression*touchScale}
                .dropShadow(shape,Shadow(radius=18.dp,spread=0.dp,offset=DpOffset(0.dp,5.dp),color=shadowInk.copy(alpha=.06f)))
                .dropShadow(shape,Shadow(radius=12.dp,spread=0.dp,offset=DpOffset(0.dp,3.dp),color=shadowInk.copy(alpha=.11f)))
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(color,Color.White,.035f),color)),shape)
                .testTag("pin-painted-body"))
        }
        Canvas(Modifier.fillMaxSize()) {
            val center=androidx.compose.ui.geometry.Offset(size.width/2,centerY.dp.toPx())
            val pinX=center.x+frame.pinX.dp.toPx()
            if(formed>0f) drawRoundRect(ink.copy(alpha=.14f*formed),
                androidx.compose.ui.geometry.Offset(pinX-8.dp.toPx(),center.y+11.dp.toPx()),
                androidx.compose.ui.geometry.Size(16.dp.toPx(),2.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
        }
        fun pinModifier(depth:Boolean)=Modifier.size(27.dp).graphicsLayer {
            cameraDistance=14f*density
            translationY=(centerY-72f+frame.pinY+(if(depth) 1.2f else 0f))*density
            translationX=(frame.pinX+(if(depth) .8f else 0f))*density
            rotationX=frame.rotationX;rotationY=frame.rotationY;rotationZ=frame.rotationZ
            scaleX=frame.pinScale;scaleY=frame.pinScale
        }
        AppIcon(R.drawable.ic_pin,null,pinModifier(true),tint=ink.copy(alpha=.17f))
        AppIcon(R.drawable.ic_pin,null,pinModifier(false),tint=ink)
        Canvas(Modifier.fillMaxSize()) {
            val center=androidx.compose.ui.geometry.Offset(size.width/2,centerY.dp.toPx())
            val left=(size.width-frame.width.dp.toPx())/2
            if(progress>.54f) {
                val top=center.y+13.dp.toPx();val bottom=center.y+frame.height.dp.toPx()/2
                val path=Path().apply {addRoundRect(androidx.compose.ui.geometry.RoundRect(left,top,size.width-left,bottom,
                    bottomLeftCornerRadius=androidx.compose.ui.geometry.CornerRadius(frame.corner.dp.toPx()),
                    bottomRightCornerRadius=androidx.compose.ui.geometry.CornerRadius(frame.corner.dp.toPx())))}
                drawPath(path,color)
                if(frame.impact>0f) drawLine(ink.copy(alpha=.24f*frame.impact),
                    androidx.compose.ui.geometry.Offset(center.x+frame.pinX.dp.toPx()-8.dp.toPx(),top),
                    androidx.compose.ui.geometry.Offset(center.x+frame.pinX.dp.toPx()+8.dp.toPx(),top),1.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)
            }
            // Miniature timeline belongs to the left half of the emerging card.
            if(formed>0f) {
                val x=left+17.dp.toPx();val top=center.y-29.dp.toPx();val step=19.dp.toPx()
                drawLine(ink.copy(alpha=.44f*formed),androidx.compose.ui.geometry.Offset(x,top),
                    androidx.compose.ui.geometry.Offset(x,top+3*step),1.6.dp.toPx(),androidx.compose.ui.graphics.StrokeCap.Round)
                repeat(4) {i ->
                    val point=androidx.compose.ui.geometry.Offset(x,top+i*step)
                    drawCircle(color,4.dp.toPx(),point)
                    drawCircle(ink.copy(alpha=.78f*formed),2.8.dp.toPx(),point,
                        style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

/** Theme-coloured Matrix rain bridges both timelines; the foreground FAB stays above it. */
@Composable fun PinMatrixOverlay(controller: PinTransitionController,modifier: Modifier=Modifier) {
    if(!controller.busy || dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion) return
    val color=MaterialTheme.colorScheme.primary
    val background=MaterialTheme.colorScheme.background
    val face=dev.ambivalenton.openv.ui.theme.appTypeface()
    val paint=remember(face) {android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {typeface=face?:android.graphics.Typeface.MONOSPACE}}
    Canvas(modifier.fillMaxSize().clearAndSetSemantics {testTag="pin-matrix-transition"}.pointerInput(controller) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false);down.consume()
            do {val event=awaitPointerEvent();event.changes.forEach {it.consume()}} while(event.changes.any {it.pressed})
        }
    }) {
        val p=controller.progress.value
        val intensity=sin(PI*((p-.12f)/.88f).coerceIn(0f,1f)).toFloat().coerceAtLeast(0f)
        drawRect(background.copy(alpha=.56f*intensity))
        val xStep=24.dp.toPx();val yStep=23.dp.toPx();paint.textSize=13.dp.toPx()
        val alphabet="ÖPENV0123456789·";val frame=(p*31).toInt()
        drawIntoCanvas {canvas ->
            for(column in 0..ceil(size.width/xStep).toInt()) {
                val length=4+column%4
                val lead=((p*size.height*1.8f+column*71.dp.toPx())%(size.height+length*yStep))-yStep
                repeat(length) {tail ->
                    val y=lead-tail*yStep
                    if(y in -yStep..size.height+yStep) {
                        paint.color=color.copy(alpha=intensity*(if(tail==0) .9f else .62f*(1-tail.toFloat()/length))).toArgb()
                        val character=alphabet[(column*11+tail*3+frame)%alphabet.length].toString()
                        canvas.nativeCanvas.drawText(character,column*xStep,y,paint)
                    }
                }
            }
        }
    }
}
