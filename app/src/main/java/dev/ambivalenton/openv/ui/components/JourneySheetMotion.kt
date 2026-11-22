// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Slop guards click/axis ownership, never the visible movement. A fling needs 12 dp
 * of deliberate travel (one eighth of the 96 dp distance threshold), not 2 * touchSlop. */
fun shouldCloseJourneySheet(distance:Float,velocity:Float,threshold:Float,flingThreshold:Float,slop:Float):Boolean {
    if(!distance.isFinite() || !velocity.isFinite() || distance<=0f) return false
    if(velocity<=-flingThreshold) return false
    return distance>=threshold || (distance>=threshold*.125f && velocity>=flingThreshold)
}

/** A dismissal is a finite exit, not a spring waiting for an invisible rest threshold.
 * Gesture tracking and cancelled/short pulls still use the original spring. */
const val SHEET_EXIT_MAX_MILLIS=180
internal fun sheetExitDurationMillis(remaining:Float,travel:Float,velocity:Float):Int {
    if(remaining<=.5f || travel<=0f) return 0
    val fraction=(remaining/travel).coerceIn(0f,1f)
    val normal=(SHEET_EXIT_MAX_MILLIS*sqrt(fraction)).roundToInt().coerceIn(64,SHEET_EXIT_MAX_MILLIS)
    return if(velocity.isFinite() && velocity>0f)
        min(normal,(remaining/velocity*1000f).roundToInt().coerceAtLeast(48)) else normal
}

/** Stationary window coordinates, fixed content size and drawing-only translation. */
@Stable class JourneySheetMotion(val height:Float,val threshold:Float,val flingThreshold:Float,val slop:Float,
    initiallyShown:Boolean=false,private val onClosing:()->Unit={},private val reduceMotion:Boolean=false) {
    var offset by mutableFloatStateOf(if(initiallyShown) 0f else height);private set
    var closing by mutableStateOf(false);private set
    private var animation:Job?=null
    private var notified=false
    private var disposed=false
    fun dispose() {disposed=true;animation?.cancel();cancelContentFling()}
    var contentFling:Job?=null
        private set
    fun cancelContentFling() {contentFling?.cancel();contentFling=null}
    fun contentFling(job:Job) {cancelContentFling();contentFling=job}
    fun grab() {if(!closing && !notified && !disposed) {animation?.cancel();animation=null}}
    fun show(scope:CoroutineScope) {if(!disposed) animate(scope,0f,false,0f,{})}
    fun restore(value:Float) {if(!closing && !notified && !disposed) offset=value.coerceIn(0f,height)}
    fun drag(delta:Float):Float {
        if(closing || notified || disposed || !delta.isFinite()) return 0f
        grab()
        val before=offset;offset=(offset+delta).coerceIn(0f,height)
        return offset-before
    }
    fun release(scope:CoroutineScope,velocity:Float,onClose:()->Unit) {
        if(closing || notified || disposed || offset<=0f) return
        if(shouldCloseJourneySheet(offset,velocity,threshold,flingThreshold,slop)) close(scope,onClose,velocity)
        else animate(scope,0f,false,velocity,{})
    }
    fun cancelDrag(scope:CoroutineScope) {
        if(!notified && !closing && !disposed && offset>0f) animate(scope,0f,false,0f,{})
    }
    fun close(scope:CoroutineScope,onClose:()->Unit,velocity:Float=0f) {
        if(closing || notified || disposed) return
        closing=true;onClosing();cancelContentFling()
        val speed=velocity.coerceAtLeast(0f)
        if(sheetExitDurationMillis(height-offset,height,speed)==0) {
            animation?.cancel();animation=null
            finishClose(onClose)
        } else animate(scope,height,true,speed,onClose)
    }
    private fun finishClose(onClose:()->Unit) {
        if(disposed || notified) return
        offset=height;notified=true;onClose()
    }
    private fun animate(scope:CoroutineScope,target:Float,dismiss:Boolean,velocity:Float,onClose:()->Unit) {
        animation?.cancel()
        if(reduceMotion) {offset=target;if(dismiss) finishClose(onClose);return}
        val exitMillis=if(dismiss) sheetExitDurationMillis(target-offset,height,velocity) else 0
        animation=scope.launch {
            val value=Animatable(offset)
            if(dismiss) {
                value.animateTo(target,tween(exitMillis,easing=FastOutLinearInEasing)) {
                    offset=this.value.coerceIn(0f,height)
                }
            } else {
                value.animateTo(target,spring(dampingRatio=.9f,stiffness=480f),
                    initialVelocity=velocity.coerceIn(-height*10f,height*10f)) {offset=this.value.coerceIn(0f,height)}
            }
            offset=target
            if(dismiss) finishClose(onClose)
        }
    }
}

class JourneySheetScrollTarget(val consume:(Float)->Float)
@Composable fun rememberJourneyScrollTarget(state:ScrollState)=remember(state) {JourneySheetScrollTarget(state::dispatchRawDelta)}
@Composable fun rememberJourneyScrollTarget(state:LazyListState)=remember(state) {JourneySheetScrollTarget(state::dispatchRawDelta)}

/** Observe on the stationary parent. Before exclusive axis ownership, vertical
 * displacement is already visible. Changing to horizontal intent rolls that tiny preview
 * back; the pager receives its original unconsumed events. No slop is subtracted.
 * Only captured vertical moves are consumed. A tap, scrim or multi-touch is never a drag. */
@Composable fun Modifier.journeySheetWindowGestures(motion:JourneySheetMotion,enabled:Boolean,sheetTop:Float,
    headerHeight:Float,target:JourneySheetScrollTarget?,onClose:()->Unit):Modifier {
    val scope=rememberCoroutineScope();val close by rememberUpdatedState(onClose)
    val scroll by rememberUpdatedState(target)
    val top by rememberUpdatedState(sheetTop);val headerEnd by rememberUpdatedState(headerHeight)
    val decay=rememberSplineBasedDecay<Float>()
    return if(!enabled) this else pointerInput(motion) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            if(down.position.y<top+motion.offset || motion.closing) return@awaitEachGesture
            val destination=scroll // Do not change scroll owner midway through a pager transition.
            val header=down.position.y<top+motion.offset+headerEnd
            val coasting=!header && motion.contentFling?.isActive==true
            motion.cancelContentFling();motion.grab()
            if(coasting) down.consume()
            val originalOffset=motion.offset
            val tracker=VelocityTracker().apply {addPosition(down.uptimeMillis,down.position)}
            var total=Offset.Zero
            var applied=0f
            var previewScroll=0f
            var captured=false
            var horizontal=false
            var scrolled=false
            var finished=false
            fun route(delta:Float) {
                if(motion.offset>0f) {
                    val rest=delta-motion.drag(delta)
                    if(rest<0f && !header) previewScroll+=destination?.consume?.invoke(-rest) ?: 0f
                } else if(header) {
                    if(delta>0f) motion.drag(delta)
                } else {
                    val consumed=destination?.consume?.invoke(-delta) ?: 0f
                    previewScroll+=consumed
                    if(abs(consumed)>.1f) scrolled=true
                    val rest=delta+consumed
                    if(rest>0f) motion.drag(rest)
                }
            }
            fun undoPreview() {
                motion.restore(originalOffset)
                if(previewScroll!=0f) destination?.consume?.invoke(-previewScroll)
                previewScroll=0f;applied=0f;scrolled=false
            }
            try {
                while(true) {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    if(event.changes.count {it.pressed}>1) break
                    val change=event.changes.firstOrNull {it.id==down.id} ?: break
                    // Android CANCEL is delivered as an already-consumed synthetic release.
                    if(change.isConsumed) break
                    change.historical.forEach {tracker.addPosition(it.uptimeMillis,it.position)}
                    tracker.addPosition(change.uptimeMillis,change.position)
                    total+=change.position-change.previousPosition
                    if(!captured && !horizontal) {
                        if(abs(total.x)>abs(total.y)) {
                            undoPreview()
                            if(abs(total.x)>motion.slop) horizontal=true
                        } else {
                            route(total.y-applied);applied=total.y
                            if(abs(total.y)>motion.slop) captured=true
                        }
                    } else if(captured) {route(total.y-applied);applied=total.y}
                    if(captured || coasting) change.consume()
                    if(!change.pressed) {
                        finished=true
                        if(!captured && !horizontal && abs(total.y)>abs(total.x) &&
                            shouldCloseJourneySheet(motion.offset,tracker.calculateVelocity().y,motion.threshold,motion.flingThreshold,motion.slop)) {
                            captured=true;change.consume()
                        }
                        if(!captured) {undoPreview();motion.cancelDrag(scope)}
                        else {
                            val velocity=tracker.calculateVelocity().y
                            if(motion.offset>0f) motion.release(scope,if(scrolled && velocity>0f) 0f else velocity,close)
                            else if(!header && destination!=null && abs(velocity)>1f) {
                                motion.contentFling(scope.launch {
                                    var previous=0f
                                    AnimationState(0f,-velocity).animateDecay(decay) {
                                        val delta=value-previous;previous=value
                                        if(abs(destination.consume(delta)-delta)>.5f) cancelAnimation()
                                    }
                                })
                            }
                        }
                        break
                    }
                    // Child horizontal scroll/long-press selection can claim an undecided gesture.
                    if(!captured && !horizontal) {
                        val final=awaitPointerEvent(PointerEventPass.Final)
                        if(final.changes.any {it.id==down.id && it.isConsumed} && !coasting) break
                    }
                }
            } finally {
                if(!finished) {if(!captured) undoPreview();motion.cancelDrag(scope)}
            }
        }
    }
}
