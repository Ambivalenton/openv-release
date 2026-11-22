// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.tutorial

import dev.ambivalenton.openv.ui.components.ThemedButton

import dev.ambivalenton.openv.data.model.reduceMotion

import android.os.Build
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalViewConfiguration
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.ui.components.AppIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.localization.tr
import kotlinx.coroutines.*
import kotlin.math.*

const val TUTORIAL_BLUR_RADIUS_DP=20f
val TutorialBlurReadyKey=SemanticsPropertyKey<Boolean>("TutorialBlurReady")
val TutorialBlurFailureKey=SemanticsPropertyKey<String>("TutorialBlurFailure")
val TutorialCaptureCountKey=SemanticsPropertyKey<Int>("TutorialCaptureCount")
val TutorialCardModeKey=SemanticsPropertyKey<String>("TutorialCardMode")
val TutorialArrowKey=SemanticsPropertyKey<TutorialArrow>("TutorialArrow")

/** Hardware Gaussian blur, or a RAM-only software snapshot; the target is always redrawn sharply. */
@Composable fun FirstUseTutorialHost(path:TutorialPath?,modifier:Modifier=Modifier,modal:Boolean=false,
    content:@Composable ()->Unit) {
    val latestContent by rememberUpdatedState(content)
    val hostedContent=remember {movableContentOf {latestContent()}}
    val state=LocalTutorialState.current
    if(state==null) {Box(modifier) {hostedContent()};return}
    val hostOwner=remember {Any()}
    DisposableEffect(state,modal) {
        if(modal) state.acquireModal(hostOwner)
        onDispose {state.releaseOverlay(hostOwner);if(modal) state.releaseModal(hostOwner)}
    }
    val canOwn=(modal || !state.popupActive) && LocalTutorialSurfaceActive.current
    // Completed/irrelevant hosts allocate no graphics layer, bitmap or pointer observer.
    if(path==null || !state.canStart(path) || !canOwn) {
        DisposableEffect(state,path?.id) {
            path?.let {state.cancel(it.id)}
            onDispose {}
        }
        Box(modifier) {hostedContent()};return
    }
    val density=LocalDensity.current
    var softwareCanvas by remember {mutableStateOf(false)}
    val view=LocalView.current
    val direction=LocalLayoutDirection.current
    val safeInsets=WindowInsets.safeDrawing
    val useHardware=Build.VERSION.SDK_INT>=31 && view.isHardwareAccelerated && !softwareCanvas
    var origin by remember {mutableStateOf(Offset.Zero)}
    var extent by remember {mutableStateOf(IntSize.Zero)}
    var fitted by remember {mutableStateOf(false)}
    val layer=rememberGraphicsLayer()
    var sourceBitmap by remember {mutableStateOf<Bitmap?>(null)}
    var sourceKey by remember {mutableStateOf<String?>(null)}
    var snapshot by remember {mutableStateOf<ImageBitmap?>(null)}
    var blurFailure by remember {mutableStateOf<String?>(null)}
    var captureCount by remember {mutableIntStateOf(0)}
    val targetRects=path.steps.map {state.targets[it.target]?.translate(-origin)}
    val available=targetRects.all {r->
        r!=null && r.width>0 && r.height>0 && r.left>=0 && r.top>=0 && r.right<=extent.width+1 && r.bottom<=extent.height+1
    }
    LaunchedEffect(state,path.id,available) {
        if(available) state.begin(path) else state.cancel(path.id)
    }
    DisposableEffect(state,path.id) {onDispose {state.cancel(path.id)}}
    val owns=available && state.session?.id==path.id
    val blur=owns && fitted
    val target=if(owns) state.step?.target?.let {state.targets[it]?.translate(-origin)} else null
    val radius=with(density) {TUTORIAL_BLUR_RADIUS_DP.dp.toPx()}
    val backdrop=if(modal) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.background
    val captureKey=if(blur && !useHardware) "${state.session?.id}:${extent.width}:${extent.height}:$radius:$backdrop" else null
    LaunchedEffect(captureKey,sourceBitmap) {
        snapshot=null;blurFailure=null
        if(captureKey==null) {sourceBitmap=null;sourceKey=null}
        else if(sourceKey==captureKey && sourceBitmap!=null) {
            val bitmap=sourceBitmap!!
            try {snapshot=withContext(Dispatchers.Default) {softwareTutorialBlur(bitmap,radius).asImageBitmap()}}
            catch(e:CancellationException) {throw e} catch(e:Exception) {blurFailure=e.toString()}
        }
    }
    Box(modifier.semantics {this[TutorialBlurReadyKey]=!blur || useHardware || snapshot!=null
        this[TutorialCaptureCountKey]=captureCount
        blurFailure?.let {this[TutorialBlurFailureKey]=it}}.onGloballyPositioned {
        origin=it.positionInWindow();extent=it.size
    }) {
        Box(Modifier.fillMaxSize().drawWithContent {
            if(!blur) {drawContent();return@drawWithContent}
            if(!drawContext.canvas.nativeCanvas.isHardwareAccelerated) softwareCanvas=true
            // Capture only on entering a path/size; Pin -> Share reuses the same blur. Redirect the public DrawContext canvas so
            // pre-31 Android and software-rendered windows need no unsupported RenderNode copy.
            if(captureKey!=null && sourceKey!=captureKey && size.width>0 && size.height>0) {
                val bitmap=createBitmap(size.width.roundToInt(),size.height.roundToInt(),Bitmap.Config.ARGB_8888)
                val previousCanvas=drawContext.canvas
                val previousLayer=drawContext.graphicsLayer
                try {
                    val bitmapCanvas=android.graphics.Canvas(bitmap).apply {drawColor(backdrop.toArgb())}
                    drawContext.canvas=Canvas(bitmapCanvas)
                    drawContext.graphicsLayer=null
                    this@drawWithContent.drawContent()
                    sourceKey=captureKey;sourceBitmap=bitmap;captureCount++
                } finally {drawContext.canvas=previousCanvas;drawContext.graphicsLayer=previousLayer}
            }
            layer.renderEffect=null
            layer.record {this@drawWithContent.drawContent()}
            run {
                val hole=target?.let {tutorialSpotlight(it,size.width,size.height,density.density)}
                val pathHole=hole?.let {Path().apply {addRoundRect(RoundRect(it,CornerRadius(TutorialDimensions.cornerRadius.dp.toPx())))}}
                fun DrawScope.backgroundBlur() {
                    if(useHardware) {layer.renderEffect=BlurEffect(radius,radius,TileMode.Clamp);drawLayer(layer)}
                    else snapshot?.let {image->drawImage(image,dstSize=IntSize(size.width.roundToInt(),size.height.roundToInt()),filterQuality=FilterQuality.Medium)}
                        ?: run {drawLayer(layer);drawRect(Color.Black.copy(alpha=.8f))}
                }
                if(pathHole!=null) {
                    clipPath(pathHole,ClipOp.Difference) {backgroundBlur()}
                    clipPath(pathHole) {this@drawWithContent.drawContent()}
                } else backgroundBlur()
            }
        }.then(if(blur) Modifier.clearAndSetSemantics {} else Modifier)) {hostedContent()}
        if(owns && target!=null && state.step!=null) {
            // Insets and targets are converted from this window into the same host space.
            // The floating navigation pill is not a system inset: without its clearance the hint
            // card could be placed underneath it (build 67).
            val navigation=with(density) {dev.ambivalenton.openv.ui.components.LocalNavigationClearance.current.toPx()}
            val safe=Rect(
                max(0f,safeInsets.getLeft(density,direction)-origin.x),
                max(0f,safeInsets.getTop(density)-origin.y),
                min(extent.width.toFloat(),view.rootView.width-safeInsets.getRight(density,direction)-origin.x),
                (min(extent.height.toFloat(),view.rootView.height-safeInsets.getBottom(density)-origin.y)-navigation)
                    .coerceAtLeast(extent.height*.5f),
            )
            FirstUseTutorialOverlay(state,target,Modifier.fillMaxSize(),targetRects.filterNotNull(),safe) {visible->
                fitted=visible;state.overlayVisible(hostOwner,path.id,visible)
            }
        }
    }
}

/** The overlay is an input owner only while a measured card fits. Canvas/blur never
 * install pointer handlers. Removing the card removes its hit-test node in the same tree. */
@Composable fun FirstUseTutorialOverlay(state:TutorialState,target:Rect,modifier:Modifier=Modifier,
    targets:List<Rect> = listOf(target),safeBounds:Rect?=null,onFitChanged:(Boolean)->Unit={}) {
    if(state.step==null) return
    val scope=rememberCoroutineScope()
    val density=LocalDensity.current
    val slop=LocalViewConfiguration.current.touchSlop
    val fitCallback by rememberUpdatedState(onFitChanged)
    val placed=remember {mutableStateOf<TutorialPlacement?>(null)}
    val fitOwner=remember {Any()}
    DisposableEffect(state,fitOwner) {onDispose {fitCallback(false)}}
    val group=tutorialTargetGroup(target,targets,64f*density.density)
    val pulse=if(dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion) remember {mutableFloatStateOf(0f)} else rememberInfiniteTransition(label="tutorial-halo").animateFloat(0f,1f,
        infiniteRepeatable(tween(1800,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="tutorial-halo")
    fun next(skip:Boolean=false) {scope.launch {state.advance(skip)}}
    BackHandler(enabled=placed.value!=null) {next(true)}
    val input=if(placed.value==null) Modifier else Modifier.pointerInput(state.session?.id,state.stepIndex) {
        awaitEachGesture {
            // Buttons get the Main pass first. All remaining background input belongs
            // to this visible guide; underlying Pin/Share never enter its hit-test chain.
            val down=awaitFirstDown(requireUnconsumed=false)
            if(down.isConsumed) return@awaitEachGesture
            down.consume()
            var moved=false
            var pressed=true
            while(pressed) {
                val event=awaitPointerEvent()
                val current=event.changes.firstOrNull {it.id==down.id} ?: break
                if((current.position-down.position).getDistance()>slop || event.changes.size>1) moved=true
                event.changes.forEach {it.consume()}
                pressed=current.pressed
                if(!pressed && !moved && placed.value?.hole?.contains(current.position)==true) next()
            }
        }
    }
    SubcomposeLayout(modifier.testTag("first-use-tutorial").then(input).semantics {
        isTraversalGroup=true;paneTitle=tr("Kurze Anleitung")
        placed.value?.let {p->tutorialArrow(p,p.bubbleBounds,density.density)?.let {this[TutorialArrowKey]=it}}
    }) {constraints->
        val width=constraints.maxWidth;val height=constraints.maxHeight
        val safe=(safeBounds ?: Rect(0f,0f,width.toFloat(),height.toFloat())).intersect(Rect(0f,0f,width.toFloat(),height.toFloat()))
        val margin=TutorialDimensions.margin*density.density
        val gap=TutorialDimensions.compactCorridor*density.density
        val usable=(safe.width-2f*margin).coerceAtLeast(1f)
        // Standard, wider and lateral candidates: each is measured at its actual width,
        // with unconstrained height, at the USER's font scale. No guesses or text shrinking.
        val widths=listOf(min(360f*density.density,usable),usable,
            group.left-safe.left-margin-gap-8f*density.density,
            safe.right-group.right-margin-gap-8f*density.density)
            .filter {it>=min(180f*density.density,usable)}.map {it.roundToInt()}.distinct()
        var chosen:TutorialPlacement?=null
        var selectedMode=TutorialCardMode.STANDARD
        for(mode in TutorialCardMode.entries) {
            for(w in widths) {
                val measured=subcompose(mode to w) {
                    TutorialExplanationCard(state,mode,{},{},measurementOnly=true)
                }.single().measure(Constraints.fixedWidth(w))
                val p=tutorialPlacement(target,width.toFloat(),height.toFloat(),measured.height.toFloat(),density.density,
                    group,safe,w.toFloat())
                if(p!=null) {chosen=p;selectedMode=mode;break}
            }
            if(chosen!=null) break
        }
        val p=chosen
        val card=if(p!=null) subcompose("visible-card") {
            TutorialExplanationCard(state,selectedMode,{next()},{next(true)})
        }.single().measure(Constraints.fixedWidth(p.bubbleWidth.roundToInt())) else null
        val backdrop=if(p!=null) subcompose("backdrop") {
            val arrow=remember(p,density.density) {tutorialArrow(p,p.bubbleBounds,density.density)}
            val shaft=remember(arrow) {arrow?.let {a->Path().apply {moveTo(a.start.x,a.start.y);cubicTo(a.control1.x,a.control1.y,a.control2.x,a.control2.y,a.tip.x,a.tip.y)}}}
            val head=remember(arrow) {arrow?.let {a->Path().apply {moveTo(a.headLeft.x,a.headLeft.y);lineTo(a.tip.x,a.tip.y);lineTo(a.headRight.x,a.headRight.y)}}}
            val veilPaint=remember {Paint()}
            Canvas(Modifier.fillMaxSize()) {
                val hole=p.hole
                drawContext.canvas.saveLayer(Rect(Offset.Zero,size),veilPaint)
                drawRect(Color.Black.copy(alpha=.48f))
                drawRoundRect(Color.Transparent,hole.topLeft,hole.size,CornerRadius(TutorialDimensions.cornerRadius.dp.toPx()),blendMode=BlendMode.Clear)
                drawContext.canvas.restore()
                drawRoundRect(Color.White.copy(alpha=.42f+.06f*pulse.value),hole.topLeft,hole.size,
                    CornerRadius(TutorialDimensions.cornerRadius.dp.toPx()),style=Stroke(1.4.dp.toPx()))
                val stroke=TutorialDimensions.arrowStroke*density.density
                fun leader(path:Path) {
                    drawPath(path,Color.Black.copy(alpha=.22f),style=Stroke(stroke+2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                    drawPath(path,Color.White.copy(alpha=.96f),style=Stroke(stroke,cap=StrokeCap.Round,join=StrokeJoin.Round))
                }
                shaft?.let {leader(it)};head?.let {leader(it)}
            }
        }.single().measure(Constraints.fixed(width,height)) else null
        layout(width,height) {
            // No fitting card means no veil, blur, or input blocking. Retry on resize.
            placed.value=p;fitCallback(p!=null)
            backdrop?.place(0,0)
            if(p!=null) card?.place(p.bubbleLeft.roundToInt(),p.bubbleTop.roundToInt())
        }
    }
}

private enum class TutorialCardMode { STANDARD, COMPACT, ESSENTIAL }

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TutorialExplanationCard(state:TutorialState,mode:TutorialCardMode,onNext:()->Unit,onSkip:()->Unit,measurementOnly:Boolean=false) {
    val compact=mode!=TutorialCardMode.STANDARD
    Surface(if(measurementOnly) Modifier.clearAndSetSemantics {} else
        Modifier.testTag("tutorial-explanation").semantics {this[TutorialCardModeKey]=mode.name},
        color=MaterialTheme.colorScheme.surfaceContainerHigh,contentColor=MaterialTheme.colorScheme.onSurface,
        shape=MaterialTheme.shapes.large,shadowElevation=6.dp) {
        Column(Modifier.padding(if(compact) 12.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(if(compact) 6.dp else 12.dp)) {
            if(mode!=TutorialCardMode.ESSENTIAL && (state.session?.steps?.size ?: 0)>1) Text(tr("Schritt %1\$s von %2\$s",state.stepIndex+1,state.session?.steps?.size ?: 1),style=MaterialTheme.typography.labelSmall)
            StableTutorialCopy(state.session?.steps.orEmpty(),state.stepIndex,mode)
            if(state.saveError) Text(tr("Speichern fehlgeschlagen. Bitte erneut versuchen."),color=MaterialTheme.colorScheme.error)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                if((state.session?.steps?.size ?: 0)>1) {
                if(mode==TutorialCardMode.ESSENTIAL) IconButton(onSkip,Modifier.then(if(measurementOnly) Modifier else Modifier.testTag("tutorial-skip")),enabled=!state.saving) {AppIcon(R.drawable.ic_close,tr("Überspringen"))}
                else TextButton(onSkip,enabled=!state.saving,modifier=Modifier.then(if(measurementOnly) Modifier else Modifier.testTag("tutorial-skip"))) {Text(tr("Überspringen"))}
                }
                ThemedButton(onNext,enabled=!state.saving,modifier=Modifier.then(if(measurementOnly) Modifier else Modifier.testTag("tutorial-next"))) {
                    StableTutorialActionLabel(state.stepIndex<(state.session?.steps?.lastIndex ?: 0))
                }
            }
        }
    }
}

/** Same height for Pin -> Share; every step uses the same font and measured width. */
@Composable private fun StableTutorialCopy(steps:List<TutorialStep>,index:Int,mode:TutorialCardMode) {
    Layout(content={
        steps.forEachIndexed {i,current->
            Column(if(i==index) Modifier else Modifier.clearAndSetSemantics {},verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(mode!=TutorialCardMode.ESSENTIAL) Text(tr(current.titleKey),Modifier.semantics {heading()},
                    style=if(mode==TutorialCardMode.STANDARD) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                Text(tr(current.bodyKey),style=MaterialTheme.typography.bodyMedium)
            }
        }
    }) {measurables,constraints->
        val measured=measurables.map {it.measure(constraints.copy(minHeight=0,maxHeight=Constraints.Infinity))}
        layout(constraints.constrainWidth(measured.maxOfOrNull {it.width} ?: 0),measured.maxOfOrNull {it.height} ?: 0) {
            measured.getOrNull(index)?.placeRelative(0,0)
        }
    }
}

@Composable private fun StableTutorialActionLabel(hasNext:Boolean) {
    Layout(content={Text(tr("Weiter"));Text(tr("Verstanden"))}) {items,constraints->
        val measured=items.map {it.measure(constraints.copy(minWidth=0,minHeight=0))}
        layout(measured.maxOf {it.width},measured.maxOf {it.height}) {
            val chosen=measured[if(hasNext) 0 else 1]
            chosen.placeRelative((measured.maxOf {it.width}-chosen.width)/2,0)
        }
    }
}
