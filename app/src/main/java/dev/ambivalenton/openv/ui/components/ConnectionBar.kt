// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.localization.tr

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.theme.timelineColorPalette

/** Fractional geometry stays proportional: short legs are not inflated to fit labels. */
data class TimelinePiece(
    val segment: TripSegment,
    val startFraction: Float,
    val endFraction: Float,
)

fun timelinePieces(trip: Trip): List<TimelinePiece> {
    val duration = trip.totalDuration.toDouble()
    if (duration <= 0) return emptyList()
    return trip.segments
        .sortedBy { it.startTime }
        .mapNotNull { s ->
            val start = ((s.startTime - trip.startTime) / duration).toFloat().coerceIn(0f, 1f)
            val end = ((s.endTime - trip.startTime) / duration).toFloat().coerceIn(0f, 1f)
            if (end > start) TimelinePiece(s, start, end) else null
        }
}

@Composable
fun ConnectionBar(trip: Trip, modifier: Modifier = Modifier) {
    val palette = timelineColorPalette()
    val pieces = remember(trip) { timelinePieces(trip) }
    val text = rememberTextMeasurer()
    val walkColor = palette[LineType.WALK].rail
    val delayColor = MaterialTheme.colorScheme.error
    val description =
        remember(trip) {
            trip.segments.joinToString(". ") {
                tr("%1\$s, %2\$s bis %3\$s, %4\$s Minuten", it.lineName, clockTime(it.startTime), clockTime(it.endTime), it.durationMinutes) +
                    (it.delay.minutes?.let { d -> tr(", %1\$s", if(d>0)tr("plus %1\$s Minuten", d) else tr("planmäßig")) }
                        ?: tr(", keine Echtzeitprognose"))
            }
        }
    Canvas(modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = description }) {
        pieces.forEach { p ->
            val x = p.startFraction * size.width
            val right = p.endFraction * size.width
            val width = right - x
            if (p.segment.isWalk) {
                drawLine(
                    walkColor.copy(alpha = .65f),
                    Offset(x, size.height / 2),
                    Offset(right, size.height / 2),
                    4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
                    cap = StrokeCap.Round,
                )
            } else {
                val color = palette[p.segment.lineType].container
                val content = palette[p.segment.lineType].content
                drawRoundRect(
                    color,
                    Offset(x, 8.dp.toPx()),
                    Size(width, 32.dp.toPx()),
                    CornerRadius(minOf(8.dp.toPx(), width / 2)),
                )
                val label =
                    text.measure(
                        AnnotatedString(p.segment.lineName),
                        TextStyle(color = content, fontSize = 12.sp),
                        maxLines = 1,
                    )
                if (label.size.width + 12.dp.toPx() < width && label.size.height <= 28.dp.toPx())
                    drawText(
                        label,
                        topLeft =
                            Offset(
                                x + (width - label.size.width) / 2,
                                (size.height - label.size.height) / 2,
                            ),
                    )
                if ((p.segment.delay.departureSeconds ?: 0) > 0 || p.segment.delay.cancelled)
                    drawCircle(
                        delayColor,
                        5.dp.toPx(),
                        Offset((right - 4.dp.toPx()).coerceAtLeast(x), 7.dp.toPx()),
                    )
            }
        }
    }
}

/** Kept as a small compatibility wrapper for the connection loading placeholders. */
@Composable fun ConnectionBarShimmer(modifier: Modifier = Modifier) = ShimmerLoadingBar(modifier)
