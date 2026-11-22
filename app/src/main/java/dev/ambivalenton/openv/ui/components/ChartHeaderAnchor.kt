// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.runtime.*

/** Measured layout coordinates only. The duration row never translates over the query header. */
@Stable class ChartHeaderAnchor {
    var layoutBottomInRoot: Float? by mutableStateOf(null)
        private set
    private var leftBarProjection: (() -> Float)? by mutableStateOf(null)

    /** Unclipped bar start, used only to decide when a downward drag may reveal the query. */
    val leftBarGapPx: Float?
        get() = layoutBottomInRoot?.let { bottom ->
            leftBarProjection?.invoke()?.minus(bottom)?.takeIf { it.isFinite() }
        }

    fun measured(bottom: Float) { if (bottom.isFinite()) layoutBottomInRoot = bottom }
    internal fun trackLeftBar(projection: () -> Float) { leftBarProjection = projection }
    internal fun stopTracking(projection: () -> Float) {
        if (leftBarProjection === projection) leftBarProjection = null
    }
}
