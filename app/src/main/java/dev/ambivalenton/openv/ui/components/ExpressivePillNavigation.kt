// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.appColorFamily
import dev.ambivalenton.openv.rendering.isOperatingSystem

import dev.ambivalenton.openv.data.model.DesignLanguage
import dev.ambivalenton.openv.data.model.LanguageShadow
import dev.ambivalenton.openv.ui.theme.fill
import dev.ambivalenton.openv.ui.theme.border
import dev.ambivalenton.openv.ui.theme.languageInk
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
import dev.ambivalenton.openv.data.model.reduceMotion

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import dev.ambivalenton.openv.rendering.*
import dev.ambivalenton.openv.ui.theme.systemSurfacePalette
import dev.ambivalenton.openv.ui.theme.drawFrameShadow
import dev.ambivalenton.openv.ui.theme.drawLanguagePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import dev.ambivalenton.openv.ui.hints.gestureHint
import androidx.compose.ui.graphics.StrokeJoin
import dev.ambivalenton.openv.data.model.ThemeLanguage
import dev.ambivalenton.openv.data.model.themeLanguageOrStandard
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer


data class PillDestination(val label: String,val icon: Int,val timeline:Boolean=false)

/**
 * Build 106: where the navigation pill steps aside (springs down and out): an opened connection – in the
 * results, or previewed from the overview or the board – and the plan while it is edited. These views need
 * the whole height and have their own way back. A running journey ([pinnedOpen]) keeps the pill, and so do
 * the pages and the results. Build 107: the settings too, except the page "App-Design" ([designPage]),
 * where the pill shows the colours being chosen.
 */
fun navigationPillAside(route:String,connectionOpen:Boolean,previewOpen:Boolean,pinnedOpen:Boolean,planEditing:Boolean,
    designPage:Boolean=false):Boolean =
    (route=="results" && connectionOpen) || (previewOpen && !pinnedOpen) || planEditing || (route=="settings" && !designPage)

/**
 * Build 116 · views that need the whole height while they show (the compact view of a connection) ask
 * the pill to step aside; it comes back when the last of them closes. App-wide, so sheets hosted in the
 * overlay reach it too.
 */
object PillAsideRequests {
    val owners = androidx.compose.runtime.mutableStateMapOf<Any, Unit>()
    val active get() = owners.isNotEmpty()
}

/** While this is composed, the navigation pill stays aside. */
@Composable fun RequestPillAside() {
    val owner = remember { Any() }
    androidx.compose.runtime.DisposableEffect(owner) { PillAsideRequests.owners[owner] = Unit; onDispose { PillAsideRequests.owners.remove(owner) } }
}

/** Geometry of the navigation pill, shared with layouts that keep a distance to it. The
 * visible pill is the box minus its inset on every side; the box sits [PillNavigationPadding] inside
 * the navigation area (above the system bar). */
internal val PillNavigationPadding=2.dp
internal val PillNavigationBox=84.dp
internal val PillNavigationInset=7.dp
/** Height of the visible pill and its top edge measured from the top of the navigation area. */
internal val PillNavigationHeight=PillNavigationBox-PillNavigationInset*2
internal val PillNavigationTop=PillNavigationPadding+PillNavigationInset

@Composable fun ExpressivePillNavigation(pager: PagerState,tabs: List<PillDestination>,selectedOverride: Int?,onSelect: (Int)->Unit)=
    ExpressivePillNavigationTo(pager,tabs,selectedOverride,null,onSelect)

/** [target]: The page a tap is taking the pager to; the capsule heads there until the pager has arrived. */
@Composable fun ExpressivePillNavigationTo(pager: PagerState,tabs: List<PillDestination>,selectedOverride: Int?,target: Int?,onSelect: (Int)->Unit) {
    if(tabs.isEmpty()) return
    // Build 115: once the pages have changed, the swipe hint is no longer needed.
    val hints=dev.ambivalenton.openv.ui.hints.LocalGestureHints.current
    val firstPage=remember {pager.settledPage}
    LaunchedEffect(pager.settledPage) {if(pager.settledPage!=firstPage) hints?.learn(dev.ambivalenton.openv.data.model.GestureHintKeys.PAGES)}
    val visuals=dev.ambivalenton.openv.ui.theme.LocalAppVisuals.current
    val roundness=if(visuals.theme?.shapeStyle in setOf(dev.ambivalenton.openv.data.model.ThemeShape.CUT,dev.ambivalenton.openv.data.model.ThemeShape.BEVEL)) .15f else visuals.radiusScale.coerceIn(0f,1f)
    val shellShape=dev.ambivalenton.openv.ui.theme.appCorner(42.dp)
    val selectionShape=dev.ambivalenton.openv.ui.theme.appCorner(35.dp)
    val density=androidx.compose.ui.platform.LocalDensity.current
    val direction=androidx.compose.ui.platform.LocalLayoutDirection.current
    val view=LocalView.current;val scope=rememberCoroutineScope()
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val shell=remember {PillShellMotion()};val flash=remember {Animatable(0f)}
    var flashTab by remember {mutableIntStateOf(-1)}
    var flashJob by remember {mutableStateOf<Job?>(null)}
    var requested by remember {mutableStateOf<Int?>(null)}
    var sawScroll by remember {mutableStateOf(false)}
    val selected=(selectedOverride ?: pager.currentPage).coerceIn(tabs.indices)
    val position=(selectedOverride?.toFloat() ?: (pager.currentPage+pager.currentPageOffsetFraction)).coerceIn(0f,tabs.lastIndex.toFloat())
    // Build 118: dragged along the pill, the capsule leans towards the finger and clicks into each tab (a mechanical switch).
    var lean by remember {mutableFloatStateOf(0f)}
    var tabPx by remember {mutableFloatStateOf(0f)}
    val aimed=target ?: requested
    val destination=(aimed?.toFloat() ?: position)+lean
    var reduced by remember {mutableStateOf(!ValueAnimator.areAnimatorsEnabled())}
    val head by animateFloatAsState(destination,if(quiet) snap() else spring(dampingRatio=PILL_HEAD_DAMPING,stiffness=650f),label="capsule-leading-edge")
    val tail by animateFloatAsState(destination,if(quiet) snap() else spring(dampingRatio=PILL_TAIL_DAMPING,stiffness=285f),label="capsule-trailing-edge")
    // Build 106: every symbol answers the capsule – a small hop where it passes underneath, a full hop
    // with a squash on landing where it arrives. Springs started by an impulse, drawn in the layer only.
    val hops=remember(tabs.size) {List(tabs.size) {Animatable(0f)}}
    val aim by rememberUpdatedState(destination)
    LaunchedEffect(hops,quiet) {
        if(quiet) return@LaunchedEffect
        val inside=BooleanArray(hops.size) {abs((head+tail)/2-it)<PILL_HIT_RADIUS}
        snapshotFlow {(head+tail)/2}.collect {middle->
            for(i in hops.indices) {
                val hit=abs(middle-i)<PILL_HIT_RADIUS
                if(hit && !inside[i] && !reduced) launch {
                    hops[i].animateTo(0f,spring(dampingRatio=.42f,stiffness=520f),
                        initialVelocity=PILL_HOP_VELOCITY*(if(abs(aim-i)<.01f) 1f else PILL_PASS_HOP))
                }
                inside[i]=hit
            }
        }
    }
    LaunchedEffect(pager.isScrollInProgress,requested,quiet) {
        reduced=quiet || !ValueAnimator.areAnimatorsEnabled() || coroutineContext[MotionDurationScale]?.scaleFactor==0f
        if(reduced || quiet) shell.amount.snapTo(0f)
        else if(pager.isScrollInProgress || requested!=null) shell.inflate() else shell.settle()
    }
    var last by remember {mutableIntStateOf(selectedOverride ?: pager.settledPage)}
    LaunchedEffect(pager,selectedOverride) {
        snapshotFlow {selectedOverride ?: pager.settledPage}.distinctUntilChanged().collect {page ->
            if(page!=last) {
                last=page
                if(!quiet) view.performHapticFeedback(if(Build.VERSION.SDK_INT>=34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
                if(!reduced && !quiet) launch {shell.land()}
            }
        }
    }
    LaunchedEffect(pager.settledPage,pager.isScrollInProgress,requested,selectedOverride) {
        if(pager.isScrollInProgress) sawScroll=true
        else if(sawScroll || (requested!=null && pager.settledPage==requested && selectedOverride==null)) {
            requested=null;sawScroll=false
        }
    }
    // A finger on the pages takes over from a tapped destination at once.
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect {if(it is DragInteraction.Start) requested=null}
    }
    val os=visuals.theme?.takeIf{it.isOperatingSystem}
    val surfacePalette=MaterialTheme.colorScheme.systemSurfacePalette()
    val xp=os==dev.ambivalenton.openv.data.model.AppTheme.OS_WIN_XP
    // The bar is built in the design language (fill, outline, outer effect, selection capsule).
    val lang=dev.ambivalenton.openv.ui.theme.languageInk()
    val scheme=MaterialTheme.colorScheme
    val bg=if(xp)scheme.primary else with(lang) {fill(scheme.surfaceContainerHigh)}
    val capsule=capsuleFill(lang,scheme)
    val capsuleInk=when(lang.language) {
        DesignLanguage.BRUTAL,DesignLanguage.PIXEL,DesignLanguage.SWISS->scheme.onPrimary
        DesignLanguage.INK->scheme.background
        DesignLanguage.NEON,DesignLanguage.OUTLINE,DesignLanguage.TERMINAL,DesignLanguage.EDITORIAL,DesignLanguage.SOFT,DesignLanguage.GLASS->scheme.primary
        else->scheme.onPrimaryContainer
    }
    // Colourful themes tint the capsule per destination and blend while it travels – wherever
    // the design language keeps the plain container capsule.
    // 2.7: all other themes get their three capsule colours from the active palette (greys in monochrome ones).
    val grey=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.monochrome ||
        visuals.theme?.let {!it.isOperatingSystem && appColorFamily(it)==dev.ambivalenton.openv.data.model.DesignColorFamily.INK}==true
    // The tones are worked out for the design a change is heading for and blend like the
    // rest of the app; worked out from the half-blended palette they jumped twice during a change.
    val target=dev.ambivalenton.openv.ui.theme.LocalThemeTargetScheme.current ?: scheme
    val targetLang=target.languageInk(lang.language,lang.quiet)
    val targetBg=if(xp)target.primary else with(targetLang) {fill(target.surfaceContainerHigh)}
    val tones=blendedCapsuleTones(rememberCapsuleTones(tabs.size,targetBg,target).takeIf {capsuleFill(targetLang,target)==target.primaryContainer}
        ?: rememberDestinationTones(tabs.size,targetBg,lang.language,grey,target),!quiet)
    val selectedInk=tones?.at(if(reduced||quiet) destination else (head+tail)/2)?.ink ?: capsuleInk
    val touch=MaterialTheme.colorScheme.onSurface.copy(alpha=.11f)
    // The bar speaks the design language too (shadow, rim, material and selection).
    val language=visuals.theme.themeLanguageOrStandard
    val frame=dev.ambivalenton.openv.ui.theme.appFrame()
    val languageTheme=visuals.theme
    val highContrast=visuals.readability.contrast==dev.ambivalenton.openv.data.model.AppContrast.HIGH
    val ink=MaterialTheme.colorScheme.outline
    val accent=MaterialTheme.colorScheme.primary
    val secondaryInk=MaterialTheme.colorScheme.secondary
    // The liquid layer above the bar needs the bar's outline, and the bar answers an impact.
    val stage=LocalLiquidStage.current
    if(stage!=null) {
        DisposableEffect(stage) {onDispose {stage.pill=null}}
        LaunchedEffect(stage,quiet) {
            var seen=stage.impacts
            snapshotFlow {stage.impacts}.collect {count->if(count!=seen) {seen=count;if(!quiet&&!reduced) launch {shell.land()}}}
        }
    }
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal=10.dp,vertical=PillNavigationPadding)) {
        Box(Modifier.fillMaxWidth().height(PillNavigationBox)
            .then(if(stage!=null) Modifier.onGloballyPositioned {c->
                val inset=PillNavigationInset.value*density.density;val p=c.positionInRoot()
                stage.pill=Rect(p.x+inset,p.y+inset,p.x+c.size.width-inset,p.y+c.size.height-inset)
            } else Modifier)
            .testTag("expressive-pill-navigation").selectableGroup()
            // Build 115: the pages can be swiped – a dot glides across the pill in the first hours of use.
            .gestureHint(dev.ambivalenton.openv.data.model.GestureHintKeys.PAGES,dev.ambivalenton.openv.ui.hints.HintKind.SWIPE,5200L)) {
            Canvas(Modifier.fillMaxSize()) {
                val inset=7.dp.toPx();val slot=(size.width-2*inset)/tabs.size
                val geometry=pillVisualGeometry(size.width,size.height,tabs.size,inset,
                    if(reduced || quiet) destination else head,if(reduced || quiet) destination else tail,if(reduced || quiet) 0f else shell.landing.value)
                val amount=if(reduced || quiet) 0f else shell.amount.value.coerceIn(-.42f,1.15f)*8.5.dp.toPx()
                val outline=themedPillPath(shellShape,Rect(inset,inset,size.width-inset,size.height-inset),density,direction,
                    geometry.inflationCenterX,geometry.inflationWidth,amount,roundness=roundness)
                // Several antialiased, low-opacity contour strokes form a soft shadow.
                // No offscreen rectangular shadow mask can leak into the footer on native renderers.
                val shadowOffset=4.dp.toPx()
                fun softShadow(ink:Color,strength:Float,dx:Float=0f,dy:Float=shadowOffset) = withTransform({translate(dx,dy)}) {
                    for(layer in 14 downTo 1) drawPath(outline,ink.copy(alpha=(if(quiet) .0035f else .0075f)*strength),
                        style=Stroke((layer*1.8f).dp.toPx(),join=StrokeJoin.Round))
                    drawPath(outline,ink.copy(alpha=(if(quiet) .035f else .075f)*strength))
                }
                if(language!=ThemeLanguage.STANDARD) frame.shadow?.let {drawFrameShadow(outline,it)}
                else if(!lang.active) softShadow(Color.Black,1f)
                else when(lang.spec.shadow) {
                    LanguageShadow.NONE->Unit
                    LanguageShadow.SOFT->softShadow(Color.Black,1f)
                    LanguageShadow.GLOW->softShadow(scheme.primary,2.2f,dy=0f)
                    LanguageShadow.CLAY->softShadow(scheme.primary,1.8f,dy=7.dp.toPx())
                    LanguageShadow.NEUMORPHIC->{softShadow(Color.Black,if(lang.dark)3f else 1.5f,5.dp.toPx(),5.dp.toPx())
                        softShadow(Color.White,if(lang.dark).6f else 6f,(-4).dp.toPx(),(-4).dp.toPx())}
                    LanguageShadow.HARD,LanguageShadow.STEPPED->{val o=4.dp.toPx();withTransform({translate(o,o)}) {drawPath(outline,lang.hardInk)}}
                    LanguageShadow.LAYERED->{
                        val bx=5.dp.toPx();val by=6.dp.toPx();val mx=2.5.dp.toPx();val my=3.dp.toPx()
                        withTransform({translate(bx,by)}) {drawPath(outline,androidx.compose.ui.graphics.lerp(scheme.background,scheme.secondary,.22f))}
                        withTransform({translate(mx,my)}) {drawPath(outline,androidx.compose.ui.graphics.lerp(scheme.background,scheme.primary,.18f))}
                    }
                }
                drawPath(outline,bg)
                clipPath(outline) {
                    // The shell swells beyond its box while it springs: the bar's fill reaches past it, its rules stay out.
                    if(os!=null)drawIntoCanvas{drawSystemHeader(it.nativeCanvas,os,size.width,size.height,surfacePalette,density.density,
                        bleed=16.dp.toPx(),edges=false)}
                    // Panel light follows the swelling too (at rest the outline stays inside the box: unchanged).
                    val swell=outline.getBounds().let {maxOf(0f,-it.top,it.bottom-size.height)}
                    if(languageTheme!=null && language!=ThemeLanguage.STANDARD && !quiet && !highContrast)
                        inset(0f,-swell,0f,-swell) {drawLanguagePanel(languageTheme,accent,secondaryInk)}
                    if(flashTab>=0 && flash.value>0) drawRoundRect(touch.copy(alpha=touch.alpha*flash.value),
                        Offset(inset+slot*flashTab,inset),Size(slot,size.height-2*inset),CornerRadius((size.height-2*inset)/2))
                    if(lang.active && !lang.quiet) inset(0f,-swell,0f,-swell) {when(lang.language) {
                        DesignLanguage.GLASS->drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=if(lang.dark).10f else .40f),Color.Transparent)))
                        DesignLanguage.CHROME->drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha=.6f),.5f to Color.White.copy(alpha=.08f),.51f to Color.Transparent,1f to Color.Black.copy(alpha=.05f)))
                        else->Unit
                    }}
                    val drop=themedPillPath(selectionShape,geometry.capsule,density,direction,strain=if(reduced||quiet)0f else head-tail,roundness=roundness)
                    drawPath(drop,tones?.at(if(reduced||quiet) destination else (head+tail)/2)?.fill ?: capsule)
                    when {
                        language!=ThemeLanguage.STANDARD->{
                            if(capsule==bg) drawPath(drop,capsuleInk.copy(alpha=.6f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                            drawLanguageSelection(language,drop,geometry.capsule,ink,accent,languageTheme?.dark==true)
                        }
                        lang.language in setOf(DesignLanguage.NEON,DesignLanguage.OUTLINE,DesignLanguage.TERMINAL,DesignLanguage.EDITORIAL)->
                            drawPath(drop,scheme.primary,style=Stroke(1.5.dp.toPx(),pathEffect=if(lang.spec.dashed)PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),3.dp.toPx())) else null))
                        lang.language in setOf(DesignLanguage.BRUTAL,DesignLanguage.PIXEL)->drawPath(drop,scheme.onSurface,style=Stroke(2.dp.toPx()))
                        capsule==bg->drawPath(drop,capsuleInk.copy(alpha=.6f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    }
                }
                frame.border?.let {rim->clipPath(outline) {drawPath(outline,rim.brush,style=Stroke(rim.width.toPx()*2f))}}
                // The bar's own outline lies above everything it encloses.
                with(lang) {border()}?.let {b->drawPath(outline,b.brush,style=Stroke(b.width.toPx(),
                    pathEffect=if(lang.spec.dashed)PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),4.dp.toPx())) else null))}
                if(lang.language==DesignLanguage.TERMINAL) drawPath(outline,scheme.primary.copy(alpha=.7f),style=Stroke(1.dp.toPx(),
                    pathEffect=PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),4.dp.toPx()))))
            }
            BoxWithConstraints(Modifier.fillMaxSize().padding(7.dp)) {
            // Build 113: the space a label has inside the capsule at rest (see elasticCapsuleRect):
            // below the symbol, at least 3 dp clear of the capsule's lower edge, and away from its sides
            // as far as its corners recede at the label's baseline (round, cut, hexagonal). Symbol and
            // label are centred together, so a smaller label sits higher, where the capsule is wider.
            // One size for all labels.
            val capsuleWidth=maxWidth/tabs.size*.82f
            val capsuleHeight=maxHeight*.70f
            val labelHeight=(capsuleHeight-24.dp-3.dp*2).coerceAtLeast(10.dp)
            val labelStyle=MaterialTheme.typography.labelMedium
            val measurer=androidx.compose.ui.text.rememberTextMeasurer()
            val labels=tabs.map {it.label}
            val labelSize=remember(labels,capsuleWidth,capsuleHeight,labelStyle,density,selectionShape) {
                with(density) {
                    val w=capsuleWidth.toPx();val h=capsuleHeight.toPx();val icon=24.dp.toPx()
                    sharedLabelSize(measurer,labels,labelStyle.copy(fontWeight=FontWeight.SemiBold,
                        lineHeight=androidx.compose.ui.unit.TextUnit.Unspecified),labelHeight.roundToPx()) {line->
                        val baseline=(h-icon-line)/2f+line*.2f
                        val inset=maxOf(capsuleSideInset(selectionShape,w,h,density,baseline),3.dp.toPx())+2.dp.toPx()
                        (w-2*inset).toInt()
                    }
                }
            }
            val labelWidth=(capsuleWidth-5.dp*2).coerceAtLeast(24.dp)
            Row(Modifier.fillMaxSize().onSizeChanged {tabPx=it.width.toFloat()/tabs.size}
                .detentSwipe(true,tabs.size,{aimed ?: selected},{tabPx},rtl=direction==androidx.compose.ui.unit.LayoutDirection.Rtl,
                    onLean={lean=it},onCommit={index->
                        if(!quiet) view.performHapticFeedback(if(Build.VERSION.SDK_INT>=34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
                        requested=index;onSelect(index)
                    })) {
                tabs.forEachIndexed {index,tab->
                    val interactions=remember {MutableInteractionSource()}
                    LaunchedEffect(interactions) {interactions.interactions.collect {event->
                        if(event is PressInteraction.Press && !quiet) {
                            flashJob?.cancel();flashTab=index
                            flashJob=scope.launch {flash.snapTo(1f);flash.animateTo(0f,tween(PILL_FLASH_MILLIS))}
                        }
                    }}
                    // Pressed, the symbol gives a little; let go, it springs back.
                    val pressed by interactions.collectIsPressedAsState()
                    val press by animateFloatAsState(if(pressed && !quiet) .86f else 1f,if(quiet) snap() else spring(dampingRatio=.45f,stiffness=700f),label="pill-press")
                    Column(Modifier.weight(1f).fillMaxHeight().selectable(selected=index==selected,
                        interactionSource=interactions,indication=null,role=Role.Tab,
                        // Every tap aims the capsule, also back at the page the pages are just
                        // leaving (it is still the current page until they pass the middle).
                        onClick={requested=index;onSelect(index)}),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        // System bars (e.g. silver Leopard) carry their own readable label colour.
                        val idle=if(os!=null)Color(systemHeaderForeground(os,surfacePalette)) else MaterialTheme.colorScheme.onSurfaceVariant
                        // Build 106: the colour follows the capsule – a symbol takes the capsule's ink as it slides underneath.
                        val cover=if(reduced||quiet) (if(index==selected) 1f else 0f) else (1f-abs((head+tail)/2-index)*1.3f).coerceIn(0f,1f)
                        val ink=androidx.compose.ui.graphics.lerp(idle,selectedInk,cover)
                        val hop=hops.getOrNull(index)
                        // Build 116: a tab that changes meaning ("Übersicht" → "Unterwegs") swaps symbol and word with a spring.
                        androidx.compose.animation.AnimatedContent(tab,transitionSpec={tabSwap(quiet)},
                            contentKey={it.label to it.icon},label="pill-tab-$index") {shown->
                        PillDestinationIcon(shown,ink,Modifier.size(24.dp).graphicsLayer {
                            val pose=hopPose(hop?.value ?: 0f,press)
                            transformOrigin=TransformOrigin(.5f,1f)
                            translationY=-5.dp.toPx()*pose.lift
                            scaleX=pose.scaleX;scaleY=pose.scaleY
                        }.testTag("pill-icon-$index-${if(shown.timeline) "timeline" else "default"}"),selected=index==selected)
                        }
                        // Build 113: the label stays inside the capsule with large type instead of running
                        // past its edges; a single word is never broken.
                        Box(Modifier.width(labelWidth).heightIn(max=labelHeight),contentAlignment=Alignment.Center) {
                            androidx.compose.animation.AnimatedContent(tab.label,transitionSpec={tabSwap(quiet)},label="pill-label-swap-$index") {label->
                            FittedText(label,style=labelStyle,fontSize=labelSize,
                                fontWeight=if(index==selected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=1,color=ink,
                                modifier=Modifier.testTag("pill-label-$index"))
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

/** The plain selection capsule of each design language (For any palette). */
private fun capsuleFill(lang:dev.ambivalenton.openv.ui.theme.LanguageInk,scheme:ColorScheme):Color=when(lang.language) {
    DesignLanguage.BRUTAL,DesignLanguage.PIXEL,DesignLanguage.SWISS->scheme.primary
    DesignLanguage.INK->scheme.onSurface
    DesignLanguage.NEON,DesignLanguage.OUTLINE,DesignLanguage.TERMINAL,DesignLanguage.EDITORIAL->scheme.primary.copy(alpha=.12f)
    DesignLanguage.SOFT->androidx.compose.ui.graphics.lerp(scheme.background,Color.Black,if(lang.dark).18f else .055f)
    DesignLanguage.GLASS->Color.White.copy(alpha=if(lang.dark).16f else .62f)
    else->scheme.primaryContainer
}

/** The selected capsule in each design language (pressed in, outlined, lit or glowing). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLanguageSelection(language:ThemeLanguage,
    drop:Path,bounds:Rect,ink:Color,accent:Color,dark:Boolean) {
    when(language) {
        ThemeLanguage.NEO_BRUTAL->drawPath(drop,ink,style=Stroke(2.dp.toPx(),join=StrokeJoin.Round))
        ThemeLanguage.OUTLINE->drawPath(drop,ink,style=Stroke(1.25.dp.toPx(),join=StrokeJoin.Round))
        ThemeLanguage.SOFT_RELIEF->clipPath(drop) {
            drawRect(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Black.copy(alpha=if(dark) .28f else .10f),Color.Transparent,
                Color.White.copy(alpha=if(dark) .07f else .45f)),bounds.topLeft,bounds.bottomRight))
        }
        ThemeLanguage.CLAY->clipPath(drop) {
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha=if(dark) .2f else .45f),Color.Transparent),
                bounds.top,bounds.top+bounds.height*.55f))
        }
        ThemeLanguage.GLOW_EDGE->drawPath(drop,accent.copy(alpha=.6f),style=Stroke(1.dp.toPx(),join=StrokeJoin.Round))
        else->Unit
    }
}

/** Build 116: the old symbol or word sinks and fades while the new one springs up. */
private fun <S> androidx.compose.animation.AnimatedContentTransitionScope<S>.tabSwap(quiet:Boolean):androidx.compose.animation.ContentTransform =
    if(quiet) androidx.compose.animation.fadeIn(snap()) togetherWith androidx.compose.animation.fadeOut(snap())
    else (androidx.compose.animation.fadeIn(spring(dampingRatio=.9f,stiffness=380f))+
        androidx.compose.animation.scaleIn(spring(dampingRatio=.55f,stiffness=420f),initialScale=.6f)+
        androidx.compose.animation.slideInVertically(spring(dampingRatio=.7f,stiffness=420f)) {it/3}) togetherWith
        (androidx.compose.animation.fadeOut(spring(stiffness=900f))+androidx.compose.animation.slideOutVertically(spring(stiffness=700f)) {-it/3}) using
        androidx.compose.animation.SizeTransform(clip=false)
