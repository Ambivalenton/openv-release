// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.min

internal const val COMPARISON_CHART_KEY = "trip-chart"
internal const val CHART_HEADER_REVEAL_GAP_DP = 4f

/** Result is a content-scroll delta: positive collapses the query, negative reveals it. */
internal fun chartHeaderPreScrollDelta(
    availableY: Float,
    collapseRemainingPx: Float,
    leftBarGapPx: Float?,
    revealGapPx: Float,
): Float = when {
    !availableY.isFinite() || !collapseRemainingPx.isFinite() || !revealGapPx.isFinite() -> 0f
    availableY < 0f -> min(-availableY, collapseRemainingPx.coerceAtLeast(0f))
    availableY > 0f && leftBarGapPx?.isFinite() == true && leftBarGapPx >= revealGapPx - 1f -> -availableY
    else -> 0f
}

/**
 * The query leaves first on upward drags. On the way back the actual left bar must reach
 * the duration-row anchor first. Remaining deltas and fling frames stay in Compose's normal
 * nested-scroll chain; horizontal gestures are never consumed here.
 */
@Composable internal fun rememberChartHeaderScroll(
    outer: LazyListState,
    anchor: ChartHeaderAnchor,
    revealGapPx: Float,
): NestedScrollConnection = remember(outer, anchor, revealGapPx) {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val chart = outer.layoutInfo.visibleItemsInfo.firstOrNull { it.key == COMPARISON_CHART_KEY }
                ?: return Offset.Zero
            val remaining = (chart.offset - outer.layoutInfo.viewportStartOffset).toFloat().coerceAtLeast(0f)
            val requested = chartHeaderPreScrollDelta(available.y, remaining, anchor.leftBarGapPx, revealGapPx)
            if (requested == 0f) return Offset.Zero
            // This connection already participates in nested dispatch: redispatching would recurse.
            val consumed = outer.dispatchRawDelta(requested)
            return Offset(0f, -consumed)
        }
    }
}
