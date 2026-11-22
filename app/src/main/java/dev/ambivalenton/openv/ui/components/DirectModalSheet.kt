// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion

import dev.ambivalenton.openv.ui.components.AppDialog as Dialog

import androidx.activity.compose.BackHandler
import android.view.Window
import android.view.WindowManager
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*

val DirectSheetOffsetKey=SemanticsPropertyKey<Float>("JourneySheetOffset")

/** The sheet's own scrim. Inside the app overlay it also takes over the host's 38 % layer, so
 * the resting darkness stays exactly the same (two black layers 44 % and 38 % = one of 65.28 %),
 * but all of it fades with the sheet. Before, the host layer stayed until the entry was removed
 * and then vanished in one frame at the end of every swipe-close. */
internal const val DIRECT_SHEET_SCRIM=.44f
internal const val DIRECT_SHEET_HOSTED_SCRIM=1f-(1f-DIRECT_SHEET_SCRIM)*(1f-.38f)

/** Native removal can be queued until the next UI traversal. Release input and
 * opacity atomically first, so even that interval has no dim layer or touch gate. */
private class DirectSheetWindow {
    private var window:Window?=null
    fun attach(value:Window?,ownsScrim:Boolean=true) {
        window=value
        value?.apply {
            if(ownsScrim) {clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);setDimAmount(0f)}
            setWindowAnimations(0)
        }
    }
    fun detach(value:Window?) {if(window===value) window=null}
    fun releaseInput() {
        window?.let {value->
            value.attributes=value.attributes.apply {
                flags=flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                alpha=0f;dimAmount=0f;windowAnimations=0
            }
        }
    }
}

/** Standard palette dialogs keep their native scrim, and release it on disposal. */
@Composable internal fun ReleaseDialogWindowOnDispose(ownsScrim:Boolean=false) {
    val window=(LocalView.current.parent as? DialogWindowProvider)?.window
    val owner=remember {DirectSheetWindow()}
    DisposableEffect(window) {
        owner.attach(window,ownsScrim=ownsScrim)
        onDispose {owner.releaseInput();owner.detach(window)}
    }
}

/** Shared by journey, favorite editor and live pin. Content supplies its existing scroll
 * state; this window owns touch routing and the single close completion. */
@Composable fun DirectModalSheet(onDismiss:()->Unit,scrollTarget:JourneySheetScrollTarget?,
    tag:String,gesturesEnabled:Boolean=true,fraction:Float=.95f,headerHeight:androidx.compose.ui.unit.Dp=64.dp,
    animateEntry:Boolean=true,color:Color=MaterialTheme.colorScheme.surfaceContainerLow,
    onNavigationDismiss:(()->Unit)?=null,
    content:@Composable (JourneySheetMotion,()->Unit)->Unit) {
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    val owner by rememberUpdatedState(onDismiss)
    val windowOwner=remember {DirectSheetWindow()}
    var removed by rememberSaveable(tag) {mutableStateOf(false)}
    var exitRequested by rememberSaveable(tag) {mutableStateOf(false)}
    if(removed) return
    val complete:()->Unit={windowOwner.releaseInput();removed=true;owner()}
    val scrim=if(LocalAppOverlay.current!=null) DIRECT_SHEET_HOSTED_SCRIM else DIRECT_SHEET_SCRIM
    Dialog(onDismissRequest={complete()},onTabDismiss={
        windowOwner.releaseInput();removed=true
        if(onNavigationDismiss!=null) onNavigationDismiss() else owner()
    },behindNavigation=true,drawScrim=false,properties=DialogProperties(usePlatformDefaultWidth=false,
        dismissOnBackPress=false,dismissOnClickOutside=false,decorFitsSystemWindows=false)) {
        // This sheet already draws/animates its own scrim. Native dimming and the
        // platform dialog exit transition would outlive it and retain modal input.
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            windowOwner.attach(window)
            onDispose {windowOwner.releaseInput();windowOwner.detach(window)}
        }
        BoxWithConstraints(Modifier.fillMaxSize().testTag("${tag.removeSuffix("-sheet")}-window")) {
            val density=LocalDensity.current
            val height=with(density) {maxHeight.toPx()}
            val slop=LocalViewConfiguration.current.touchSlop
            // A motion rebuilt during the exit (size change, e.g. the keyboard closing) starts
            // hidden: never at the resting position for a frame before it finishes closing.
            val motion=remember(height,density.density,fraction,quiet) {JourneySheetMotion((height*fraction).roundToInt().toFloat(),
                with(density) {96.dp.toPx()},with(density) {900.dp.toPx()},slop,(!animateEntry || quiet) && !exitRequested,
                onClosing={exitRequested=true},reduceMotion=quiet)}
            val scope=rememberCoroutineScope()
            LaunchedEffect(motion) {if(exitRequested) motion.close(scope,complete) else if(animateEntry && !quiet) motion.show(scope)}
            DisposableEffect(motion) {onDispose {motion.dispose()}}
            val dismiss:()->Unit={motion.close(scope,complete)}
            BackHandler(gesturesEnabled) {dismiss()}
            Box(Modifier.fillMaxSize().journeySheetWindowGestures(motion,gesturesEnabled && !motion.closing,
                height*(1f-fraction),with(density) {headerHeight.toPx()},scrollTarget,complete)) {
                Box(Modifier.fillMaxSize().graphicsLayer {alpha=(1f-motion.offset/motion.height).coerceIn(0f,1f)}
                    .background(Color.Black.copy(alpha=scrim)).clickable(enabled=gesturesEnabled,onClick=dismiss))
                Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(fraction)
                    .graphicsLayer {translationY=motion.offset}
                    .testTag(tag).semantics {this[DirectSheetOffsetKey]=motion.offset},color=color,
                    shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp)) {
                    content(motion,dismiss)
                }
            }
        }
    }
}

/** Accessibility scrolling and bring-into-view state survive custom touch routing. */
fun Modifier.directSheetScrollSemantics(scroll:ScrollState,onScroll:()->Unit={}):Modifier=semantics {
    verticalScrollAxisRange=ScrollAxisRange({scroll.value.toFloat()},{scroll.maxValue.toFloat()})
    scrollBy {_,y->onScroll();scroll.dispatchRawDelta(y);true}
}
