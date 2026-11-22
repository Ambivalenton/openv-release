// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState

internal data class ChartSnapFrame(val x: Float, val minuteScale: Float, val alignment: Float)
internal fun chartSnapFrame(from: ChartSnapFrame, to: ChartSnapFrame, fraction: Float): ChartSnapFrame {
    val t=fraction.coerceIn(0f,1f)
    fun mix(a: Float,b: Float)=a+(b-a)*t
    return ChartSnapFrame(mix(from.x,to.x),mix(from.minuteScale,to.minuteScale),mix(from.alignment,to.alignment))
}
/** ScreenY = topPadding + minutes*scale - diagonal - verticalScroll + alignment. */
internal fun chartAnchorAlignment(minutes: Float, minuteScale: Float, density: Float,
    x: Float, verticalScroll: Int, topPaddingPx: Float, anchorPx: Float): Float =
    anchorPx-topPaddingPx-minutes*minuteScale*density+x*TIMELINE_DIAGONAL_RATIO+verticalScroll

/** One frame clock, easing and cancellation path for both axes and the minute scale. */
internal suspend fun animateDualAxisSnap(state: LazyListState, columnPx: Float,
    from: ChartSnapFrame,to: ChartSnapFrame,animated:Boolean=true,onFrame: (ChartSnapFrame)->Unit) {
    state.scroll(MutatePriority.Default) {
        if(!animated) {
            scrollBy(to.x-(state.firstVisibleItemIndex*columnPx+state.firstVisibleItemScrollOffset))
            onFrame(to);return@scroll
        }
        Animatable(0f).animateTo(1f,tween(360,easing=FastOutSlowInEasing)) {
            val frame=chartSnapFrame(from,to,value)
            val actual=state.firstVisibleItemIndex*columnPx+state.firstVisibleItemScrollOffset
            scrollBy(frame.x-actual)
            onFrame(frame)
        }
    }
}
