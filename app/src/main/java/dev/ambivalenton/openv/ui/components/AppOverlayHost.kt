@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import dev.ambivalenton.openv.data.model.reduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.tutorial.LocalTutorialState
import dev.ambivalenton.openv.ui.tutorial.LocalTutorialSurfaceActive

internal class AppOverlay(val owner:Any,val dismiss:()->Unit,val dismissForTab:()->Unit,
    val properties:DialogProperties,val behindNavigation:Boolean=false,val drawScrim:Boolean=true,
    val scrimVisibility:()->Float={1f},val render:@Composable ()->Unit)

/** Dialogs share the activity composition and never cover the always-live bottom navigation. */
@Stable class AppOverlayState {
    internal val entries=mutableStateListOf<AppOverlay>()
    var navigationEpoch by mutableIntStateOf(0)
        private set
    val visibleCount get()=entries.size
    internal fun show(entry:AppOverlay) {entries.removeAll {it.owner===entry.owner};entries+=entry}
    internal fun remove(owner:Any) {entries.removeAll {it.owner===owner}}
    fun dismissForTab() {
        val closing=entries.toList().asReversed()
        entries.clear();navigationEpoch++
        closing.forEach {it.dismissForTab()}
    }
}
val LocalAppOverlay=staticCompositionLocalOf<AppOverlayState?> {null}

@Composable fun AppOverlayHost(state:AppOverlayState,modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    AppOverlayHost(state,modifier,0.dp,content=content)
}

@Composable fun AppOverlayHost(state:AppOverlayState,modifier:Modifier=Modifier,modalBottomPadding:Dp,content:@Composable ()->Unit) {
    AppOverlayHost(state,modifier,modalBottomPadding,foreground={},content=content)
}
@Composable fun AppOverlayHost(state:AppOverlayState,modifier:Modifier=Modifier,modalBottomPadding:Dp,
    foreground:@Composable BoxScope.()->Unit,content:@Composable ()->Unit) {
    Box(modifier.clipToBounds().testTag("app-overlay-host")) {
        Box(Modifier.fillMaxSize().then(if(state.entries.isNotEmpty()) Modifier.clearAndSetSemantics {} else Modifier)) {
            CompositionLocalProvider(LocalTutorialSurfaceActive provides state.entries.isEmpty()) {content()}
        }
        state.entries.toList().forEachIndexed {index,entry->
            key(entry.owner) {
                val top=index==state.entries.lastIndex
                BackHandler(top && entry.properties.dismissOnBackPress,entry.dismiss)
                Box(Modifier.fillMaxSize().testTag("app-overlay-$index").then(if(!top) Modifier.clearAndSetSemantics {} else
                    Modifier.semantics {isTraversalGroup=true;paneTitle=tr("Dialog");dismiss {entry.dismiss();true}}),
                    contentAlignment=Alignment.Center) {
                    // Read while drawing: a sheet that is dragged or slides away fades its scrim
                    // without recomposing, and is fully clear before its entry is removed.
                    Box(Modifier.fillMaxSize().drawBehind {
                        if(entry.drawScrim) drawRect(Color.Black.copy(alpha=.38f*entry.scrimVisibility().coerceIn(0f,1f)))
                    }
                        .clickable(enabled=top,indication=null,interactionSource=remember {androidx.compose.foundation.interaction.MutableInteractionSource()}) {
                            if(entry.properties.dismissOnClickOutside) entry.dismiss()
                        })
                    Box(Modifier.fillMaxSize().padding(bottom=if(entry.behindNavigation || WindowInsets.isImeVisible) 0.dp else modalBottomPadding),contentAlignment=Alignment.Center) {entry.render()}
                }
            }
        }
        // One feedback host above every hosted modal, preserving the single Snackbar queue.
        foreground()
    }
}

/** Captured locals retain nested themes, view-model owners and accessibility typography. */
@Composable fun AppDialog(onDismissRequest:()->Unit,properties:DialogProperties=DialogProperties(),
    onTabDismiss:(()->Unit)?=null,behindNavigation:Boolean=false,drawScrim:Boolean=true,
    scrimVisibility:(()->Float)?=null,content:@Composable ()->Unit) {
    val state=LocalAppOverlay.current
    if(state==null) {androidx.compose.ui.window.Dialog(onDismissRequest,properties=properties) {dev.ambivalenton.openv.ui.theme.MonochromeWindowEffect();content()};return}
    val owner=remember {Any()}
    val currentContent by rememberUpdatedState(content)
    val dismiss by rememberUpdatedState(onDismissRequest)
    val tabDismiss by rememberUpdatedState(onTabDismiss ?: onDismissRequest)
    val locals by rememberUpdatedState(currentCompositionLocalContext)
    val currentProperties by rememberUpdatedState(properties)
    val scrim by rememberUpdatedState(scrimVisibility)
    val tutorials=LocalTutorialState.current
    DisposableEffect(state,owner) {
        tutorials?.acquireModal(owner)
        state.show(AppOverlay(owner,{dismiss()},{tabDismiss()},properties,behindNavigation,drawScrim,{scrim?.invoke() ?: 1f}) {
            CompositionLocalProvider(locals) {
            CompositionLocalProvider(LocalTutorialSurfaceActive provides (state.entries.lastOrNull()?.owner===owner)) {
                Box((if(currentProperties.usePlatformDefaultWidth) Modifier.fillMaxWidth(.92f).widthIn(max=560.dp) else Modifier)
                    .pointerInput(Unit) {awaitPointerEventScope {while(true) awaitPointerEvent()}},contentAlignment=Alignment.Center) {
                    currentContent()
                }
            }
            }
        })
        onDispose {state.remove(owner);tutorials?.releaseModal(owner)}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppModalBottomSheet(onDismissRequest:()->Unit,modifier:Modifier=Modifier,
    sheetState:SheetState?=null,shape:Shape=MaterialTheme.shapes.extraLarge,
    containerColor:Color=MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor:Color=contentColorFor(containerColor),tonalElevation:Dp=0.dp,
    dragHandle:(@Composable ()->Unit)?={BottomSheetDefaults.DragHandle()},
    content:@Composable ColumnScope.()->Unit) {
    if(LocalAppOverlay.current==null) {
        ModalBottomSheet(onDismissRequest,modifier,sheetState ?: rememberModalBottomSheetState(),
            shape=shape,containerColor=containerColor,contentColor=contentColor,tonalElevation=tonalElevation,
            dragHandle=dragHandle,content={dev.ambivalenton.openv.ui.theme.MonochromeWindowEffect();content()})
        return
    }
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val scope=rememberCoroutineScope()
    val sheet=remember(scope) {HostedSheetDrag(scope)}
    SideEffect {sheet.reduceMotion=quiet}
    val threshold=with(LocalDensity.current){72.dp.toPx()}
    val dismiss by rememberUpdatedState(onDismissRequest)
    val nested=remember(threshold,sheet){object:NestedScrollConnection {
        override fun onPreScroll(available:Offset,source:NestedScrollSource):Offset {
            if(sheet.closing) return available
            if(sheet.offset>0f&&available.y<0){return Offset(0f,sheet.drag(maxOf(-sheet.offset,available.y)))}
            return Offset.Zero
        }
        override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset {
            if(source==NestedScrollSource.UserInput&&available.y>0){return Offset(0f,sheet.drag(available.y))}
            return Offset.Zero
        }
        override suspend fun onPreFling(available:Velocity):Velocity {
            if(sheet.closing) return available
            if(sheet.offset>0f){sheet.release(threshold,available.y) {dismiss()};return available}
            return Velocity.Zero
        }
    }}
    AppDialog(onDismissRequest,DialogProperties(usePlatformDefaultWidth=false),scrimVisibility=sheet::scrimVisibility) {
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned(sheet::measureContainer),contentAlignment=Alignment.BottomCenter) {
            Surface(modifier.fillMaxWidth().heightIn(max=maxHeight*.96f).onSizeChanged {sheet.sheetHeight=it.height.toFloat()}
                .graphicsLayer {translationY=sheet.offset;alpha=if(sheet.closed) 0f else 1f}
                .nestedScroll(nested),shape=shape,color=containerColor,
                contentColor=contentColor,tonalElevation=tonalElevation) {
                Column {Box(Modifier.fillMaxWidth().heightIn(min=32.dp).testTag("app-bottom-sheet-drag-handle")
                    .pointerInput(threshold,sheet){detectVerticalDragGestures(onDragEnd={sheet.release(threshold,0f) {dismiss()}},
                        onDragCancel={sheet.cancel()}) {change,dy->change.consume();sheet.drag(dy)}},
                    contentAlignment=Alignment.Center) {dragHandle?.invoke()};content()}
            }
        }
    }
}

/** Swipe-to-close of a hosted bottom sheet. The overlay entry is only removed in the frame after
 * the caller's state changes, so the sheet must already be out of sight when it reports the
 * dismissal. Earlier it snapped back to its resting position first, and that one frame showed the
 * whole sheet again (visible flicker after swiping "Design" or "Haltestellen-Liste" away). Now it
 * slides off screen like [DirectModalSheet] (same finite exit duration) and stays hidden. */
internal const val HOSTED_SHEET_REOPEN_MILLIS=400L
@Stable internal class HostedSheetDrag(private val scope:CoroutineScope) {
    var reduceMotion=false
    var offset by mutableFloatStateOf(0f);private set
    /** Out of sight and reported; drawn transparent until the overlay entry is gone. */
    var closed by mutableStateOf(false);private set
    /** Exit has started: further drags, scrolls and flings are ignored. */
    var closing=false;private set
    var sheetHeight=0f
    /** Distance from the sheet's container to the bottom of the screen (navigation bar area). */
    private var below=0f
    /** Travel from the resting position until the sheet's top edge leaves the screen. */
    val travel get()=sheetHeight+below
    private var animation:Job?=null

    fun measureContainer(coordinates:LayoutCoordinates) {
        if(!coordinates.isAttached) return
        val root=coordinates.findRootCoordinates().size.height
        below=(root-(coordinates.positionInRoot().y+coordinates.size.height)).coerceAtLeast(0f)
    }
    fun scrimVisibility():Float=when {
        closed -> 0f
        travel<=0f -> 1f
        else -> (1f-offset/travel).coerceIn(0f,1f)
    }
    fun drag(delta:Float):Float {
        if(closing || !delta.isFinite()) return 0f
        animation?.cancel();animation=null
        val before=offset;offset=(offset+delta).coerceAtLeast(0f)
        return offset-before
    }
    fun release(threshold:Float,velocity:Float,onClose:()->Unit) {
        if(closing) return
        if(offset>threshold) close(velocity,onClose) else settle(velocity)
    }
    fun cancel() {if(!closing) settle(0f)}
    private fun settle(velocity:Float) {
        animation?.cancel();animation=null
        if(reduceMotion || offset<=0f) {offset=0f;return}
        val limit=maxOf(travel,offset)*10f
        animation=scope.launch {
            Animatable(offset).animateTo(0f,spring(dampingRatio=.9f,stiffness=480f),
                initialVelocity=if(velocity.isFinite()) velocity.coerceIn(-limit,limit) else 0f) {offset=value.coerceAtLeast(0f)}
        }
    }
    private fun close(velocity:Float,onClose:()->Unit) {
        closing=true;animation?.cancel();animation=null
        val target=maxOf(travel,offset)
        fun finish() {
            offset=target;closed=true;onClose()
            // A removed sheet cancels this scope within a frame or two. A caller that keeps the
            // sheet (declines the dismissal) gets it back instead of an invisible input blocker.
            scope.launch {
                delay(HOSTED_SHEET_REOPEN_MILLIS)
                if(closed) {closed=false;closing=false;settle(0f)}
            }
        }
        val millis=if(reduceMotion) 0 else sheetExitDurationMillis(target-offset,target,
            if(velocity.isFinite()) velocity.coerceAtLeast(0f) else 0f)
        if(millis==0) {finish();return}
        animation=scope.launch {
            Animatable(offset).animateTo(target,tween(millis,easing=FastOutLinearInEasing)) {offset=value}
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppAlertDialog(onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,
    modifier:Modifier=Modifier,dismissButton:(@Composable ()->Unit)?=null,
    icon:(@Composable ()->Unit)?=null,title:(@Composable ()->Unit)?=null,text:(@Composable ()->Unit)?=null,
    shape:Shape=MaterialTheme.shapes.extraLarge,containerColor:Color=MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation:Dp=0.dp,properties:DialogProperties=DialogProperties()) {
    if(LocalAppOverlay.current==null) {
        AlertDialog(onDismissRequest,{dev.ambivalenton.openv.ui.theme.MonochromeWindowEffect();confirmButton()},modifier,dismissButton,icon,title,text,
            shape=shape,containerColor=containerColor,tonalElevation=tonalElevation,properties=properties)
        return
    }
    AppDialog(onDismissRequest,properties) {
        Surface(modifier,shape=shape,color=containerColor,tonalElevation=tonalElevation) {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                icon?.invoke()
                title?.let {ProvideTextStyle(MaterialTheme.typography.headlineSmall,it)}
                text?.let {ProvideTextStyle(MaterialTheme.typography.bodyMedium,it)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    dismissButton?.invoke();confirmButton()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppDatePickerDialog(onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,
    modifier:Modifier=Modifier,dismissButton:(@Composable ()->Unit)?=null,
    content:@Composable ColumnScope.()->Unit) {
    if(LocalAppOverlay.current==null) {
        DatePickerDialog(onDismissRequest,confirmButton,modifier,dismissButton=dismissButton,content={dev.ambivalenton.openv.ui.theme.MonochromeWindowEffect();content()});return
    }
    AppDialog(onDismissRequest) {
        Surface(modifier,shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column {content();Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.End) {
                dismissButton?.invoke();confirmButton()
            }}
        }
    }
}

/** Non-modal popups and menus close on same-tab taps as well. */
@Composable fun DismissOnTabSelection(onDismiss:()->Unit) {
    val state=LocalAppOverlay.current ?: return
    val initial=remember {state.navigationEpoch}
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state.navigationEpoch) {if(state.navigationEpoch!=initial) dismiss()}
}
