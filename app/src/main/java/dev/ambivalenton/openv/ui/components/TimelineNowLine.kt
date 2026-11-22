// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.data.model.clockTime
import dev.ambivalenton.openv.ui.theme.*
import kotlin.math.roundToInt

/** Exactly the projection used by the chart's axis, including its diagonal pan and zoom. */
internal fun timelineNowY(time:Long,origin:Long,minutePx:Float,paddingPx:Float,
    horizontalPx:Float,alignmentPx:Float,scrollPx:Int):Float =
    paddingPx+((time-origin)/60_000.0*minutePx).toFloat()-
        horizontalPx*TIMELINE_DIAGONAL_RATIO+alignmentPx-scrollPx

/** Last drawing layer: leaves every tap and swipe to the chart beneath it. */
@Composable internal fun TimelineNowLine(origin:Long,minuteDp:Float,padding:Dp,vertical:ScrollState,
    horizontal:()->Float,alignment:()->Float) {
    val density=LocalDensity.current
    TimelineNowLine {now->timelineNowY(now,origin,minuteDp*density.density,with(density){padding.toPx()},horizontal(),alignment(),vertical.value)}
}

/** The now line at [yOf] (px for a time), read while drawing, so it glides with the chart. */
@Composable internal fun TimelineNowLine(yOf:(Long)->Float) {
    val now=rememberWallClock(tickMillis=1000).toInstant().toEpochMilli()
    val density=LocalDensity.current
    val theme=LocalAppVisuals.current.theme
    val ink=MaterialTheme.colorScheme.primary.copy(alpha=(theme?.glassOpacity ?: 1f).coerceIn(.3f,1f))
    val dashShape=appCorner(5.dp)
    var height by remember {mutableIntStateOf(0)}
    Row(Modifier.fillMaxWidth().graphicsLayer {
        translationY=yOf(now)-height/2f
    }.onSizeChanged {height=it.height}.padding(end=20.dp).testTag("timeline-now-line"),
        verticalAlignment=Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f).height(2.5.dp)) {
            val count=((maxWidth+8.dp)/30.dp).toInt().coerceAtLeast(1)
            Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                repeat(count) {Spacer(Modifier.weight(1f).fillMaxHeight()
                    .shadow(2.dp,dashShape,clip=false).background(ink,dashShape))}
            }
        }
        Spacer(Modifier.width(20.dp))
        Text(clockTime(now),Modifier.testTag("timeline-now-clock"),color=ink,maxLines=1,
            style=MaterialTheme.typography.labelLarge.copy(fontWeight=FontWeight.Bold,
                shadow=Shadow(ink.copy(alpha=.35f),Offset(0f,with(density){2.dp.toPx()}),with(density){3.dp.toPx()})))
    }
}
