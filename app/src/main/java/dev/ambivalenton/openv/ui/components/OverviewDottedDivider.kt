// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion

import dev.ambivalenton.openv.localization.tr

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** One indicator: spring-expanded dots carry a traveling pulse, then settle at 60% width. */
@Composable
fun OverviewDottedDivider(modifier: Modifier = Modifier, isLoading: Boolean = false) {
    if(dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion) {CalmLoadingRail(modifier,isLoading);return}
    val activity =
        animateFloatAsState(
            targetValue = if (isLoading) 1f else 0f,
            animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow),
            label = tr("Ladepunkte ausbreiten"),
        )
    // Keep the pulse alive during contraction, then remove the infinite animation entirely.
    val phase: State<Float> =
        if (isLoading || activity.value > .001f) {
            val transition = rememberInfiniteTransition(label = tr("Welle der Ladepunkte"))
            transition.animateFloat(
                0f,
                1f,
                infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
                label = tr("Punktwelle nach rechts"),
            )
        } else remember { mutableFloatStateOf(0f) }
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f)
    Box(
        modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier.fillMaxWidth().height(12.dp).testTag("overview-dotted-divider").semantics {
                if (isLoading) {
                    contentDescription = tr("Verbindungen werden geladen")
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
            }
        ) {
            val strength = activity.value.coerceIn(0f, 1f)
            val stroke = 2.dp.toPx()
            val restSpan = (size.width * .6f - stroke).coerceAtLeast(0f)
            val count = (floor(restSpan / 8.dp.toPx()).toInt() + 1).coerceAtLeast(2)
            val normalSpan = (count - 1) * 8.dp.toPx()
            val span = normalSpan + ((size.width - 4.dp.toPx()) - normalSpan) * strength
            val left = (size.width - span) / 2
            val waveCenter = -.35f + phase.value * 1.7f
            repeat(count) { i ->
                val fraction = i.toFloat() / (count - 1)
                val distance = abs(fraction - waveCenter)
                val wave =
                    if (distance >= .25f) 0f else ((cos(distance / .25f * PI) + 1) / 2).toFloat()
                val sway =
                    sin(2 * PI * (fraction - phase.value)).toFloat() *
                        sin(PI * fraction).toFloat() *
                        1.25.dp.toPx() *
                        strength
                val point =
                    Offset(
                        left + fraction * span + sway,
                        size.height / 2 - wave * strength * 1.5.dp.toPx(),
                    )
                drawLine(
                    color,
                    point,
                    point + Offset(.01f, 0f),
                    strokeWidth = stroke + wave * strength * 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
