// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.reduceMotion

import dev.ambivalenton.openv.localization.tr

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** At both ends the whole highlight is outside the clip: identical pixels at the loop seam. */
internal fun shimmerCenter(progress: Float, width: Float, band: Float): Float =
    -band + progress * (width + 2 * band)

@Composable
fun ShimmerLoadingBar(
    modifier: Modifier = Modifier,
    description: String = tr("Verbindungen werden geladen"),
) {
    if(dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion) {CalmLoadingRail(modifier,true,description);return}
    val transition = rememberInfiniteTransition(label = tr("Sanftes Ladelicht"))
    val progress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
            label = tr("Lichtfluss"),
        )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val light = MaterialTheme.colorScheme.primaryContainer
    Canvas(
        modifier.fillMaxWidth().height(48.dp).semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
    ) {
        val corner = CornerRadius(16.dp.toPx())
        val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner)) }
        val band = size.width * .45f
        val x = shimmerCenter(progress, size.width, band)
        clipPath(path) {
            drawRect(base)
            drawRect(
                Brush.horizontalGradient(
                    listOf(base, light, base),
                    startX = x - band,
                    endX = x + band,
                )
            )
        }
    }
}
