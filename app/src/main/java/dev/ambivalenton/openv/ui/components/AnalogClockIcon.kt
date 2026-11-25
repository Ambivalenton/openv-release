// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/**
 * The minutes of the day the hands should show. Moves to the equivalent of [minutes] nearest to
 * [previous] (within ±12 h), so 23:59 → 00:00 turns on by one minute instead of back by a day.
 */
internal fun nearestClockMinutes(previous: Float, minutes: Int): Float {
    var target = minutes.toFloat()
    while (target - previous > 720f) target -= 1440f
    while (previous - target > 720f) target += 1440f
    return target
}

/** Hand angles in degrees clockwise from twelve: hour hand first, minute hand second. */
internal fun clockHandAngles(minutes: Float): Pair<Float, Float> = (minutes * .5f) % 360f to (minutes * 6f) % 360f

/**
 * The clock symbol beside the time shows that time on its face. With no chosen time
 * ("Jetzt") it shows the current time and follows the minute tick of the shared wall clock.
 * Purely decorative for TalkBack; the time is spoken by the text next to it.
 */
@Composable fun AnalogClockIcon(timeMillis: Long?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val wall = rememberWallClock()
    val shown = if (timeMillis == null) wall else Instant.ofEpochMilli(timeMillis).atZone(dev.ambivalenton.openv.data.model.transitZone)
    val minutes = shown.hour * 60 + shown.minute
    val last = remember { floatArrayOf(minutes.toFloat()) }
    val target = remember(minutes) { nearestClockMinutes(last[0], minutes).also { last[0] = it } }
    val position by animateFloatAsState(target, MaterialTheme.motionScheme.slowSpatialSpec(), label = "clock-hands")
    Canvas(modifier.size(24.dp).testTag("analog-clock-${shown.hour}-${shown.minute}")) {
        val s = size.minDimension / 24f
        val c = center
        drawCircle(tint, 9f * s, c, style = Stroke(2f * s))
        val (hour, minute) = clockHandAngles(position)
        fun hand(degrees: Float, length: Float, width: Float) {
            val a = Math.toRadians(degrees.toDouble() - 90.0)
            drawLine(tint, c, c + Offset(cos(a).toFloat() * length * s, sin(a).toFloat() * length * s), width * s, StrokeCap.Round)
        }
        hand(hour, 4.4f, 2f)
        hand(minute, 6.4f, 1.5f)
        drawCircle(tint, 1.4f * s, c)
    }
}
