// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.localization.tr
import kotlin.math.abs
import kotlin.math.roundToInt

/** Within this distance of zero the thumb rests on zero. */
private const val ZERO_SNAP = .75f

/**
 * A slider whose range crosses zero. The track is parted at zero, a round dot marks it, the
 * part between zero and the thumb is filled; the thumb rests on zero when it comes within
 * [ZERO_SNAP]. Values move in half steps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ZeroSplitSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onChange: (Float) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val colors = MaterialTheme.colorScheme
    fun snap(raw: Float): Float { val half = (raw * 2f).roundToInt() / 2f; return if (abs(half) < ZERO_SNAP) 0f else half }
    Column(Modifier.fillMaxWidth().testTag("zero-split-slider")) {
        Text(tr("%1\$s · %2\$s %3\$s", label, String.format(locale, "%.1f", value).replace("-", "−"), unit), style = MaterialTheme.typography.labelLarge)
        Slider(value.coerceIn(range), { onChange(snap(it)) }, valueRange = range, steps = 0,
            modifier = Modifier.semantics { contentDescription = label },
            track = { state ->
                Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                    val span = range.endInclusive - range.start
                    fun x(v: Float) = (v - range.start) / span * size.width
                    val zero = x(0f); val now = x(state.value)
                    val h = size.height; val gap = 6.dp.toPx(); val r = CornerRadius(h / 2, h / 2); val small = CornerRadius(3.dp.toPx())
                    // Inactive parts left and right of zero, each with its own end.
                    drawRoundRect(colors.secondaryContainer, Offset(0f, 0f), Size((zero - gap).coerceAtLeast(0f), h), r)
                    drawRoundRect(colors.secondaryContainer, Offset(zero + gap, 0f), Size((size.width - zero - gap).coerceAtLeast(0f), h), r)
                    // The filled part runs from zero to the thumb.
                    if (state.value > 0f) drawRoundRect(colors.primary, Offset(zero + gap, 0f), Size((now - zero - gap).coerceAtLeast(0f), h), small)
                    if (state.value < 0f) drawRoundRect(colors.primary, Offset(now, 0f), Size((zero - gap - now).coerceAtLeast(0f), h), small)
                    drawCircle(if (state.value == 0f) colors.primary else colors.onSecondaryContainer, 4.dp.toPx(), Offset(zero, h / 2))
                }
            })
    }
}
