// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ambivalenton.openv.data.model.Trip
import dev.ambivalenton.openv.data.model.assembleTimeline
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.localization.tr
import kotlin.math.ceil

/** 30-dp header, 2-dp outer padding: the former 52-dp background is exactly halved to 26 dp. */
@Composable
fun TimelineDurationHeader(trips: List<Trip>, state: LazyListState, onSelected: (Trip) -> Unit,
    anchor: ChartHeaderAnchor? = null,
    /** The chart's own columns, so every badge stands exactly above its bar. */
    columns: ChartColumns? = null) {
    val scale = LocalDensity.current.fontScale
    val settings=LocalAppSettings.current
    val durations=remember(trips,settings.timelineShowWalking,settings.walkingSpeed,settings.ignoreWalking) {
        trips.associate {it.id to assembleTimeline(it,settings).trip.totalDuration}
    }
    Row(Modifier.fillMaxWidth().height(30.dp)
        .onGloballyPositioned { anchor?.measured(it.positionInRoot().y+it.size.height) }
        .background(MaterialTheme.colorScheme.background).testTag("trip-duration-header")) {
        Spacer(Modifier.width(timelineAxisWidth(scale)))
        val area = if (columns != null) Modifier.width(with(LocalDensity.current) { (columns.widthPx * columns.count).toDp() }) else Modifier.weight(1f)
        SubcomposeLayout(area.fillMaxHeight().clipToBounds()) { constraints ->
            val width = columns?.widthPx ?: timelineColumnWidth(scale).roundToPx().coerceAtLeast(1)
            val first = state.firstVisibleItemIndex.coerceAtMost(trips.lastIndex.coerceAtLeast(0))
            val start = (first-1).coerceAtLeast(0)
            val offset = state.firstVisibleItemScrollOffset
            val end = (first+ceil((constraints.maxWidth+offset).toDouble()/width).toInt()+1).coerceAtMost(trips.size)
            val cells = (start until end).map { index ->
                val trip = trips[index]
                subcompose(trip.id) {
                    DurationBadge(
                        minutes = ((durations[trip.id] ?: trip.totalDuration)+59_999)/60_000,
                        description = tr("Gesamtdauer für Verbindung %1\$s: %2\$s Minuten",index+1,((durations[trip.id] ?: trip.totalDuration)+59_999)/60_000),
                        modifier = Modifier.padding(2.dp).testTag("duration-${trip.id}"),
                        onClick = { onSelected(trip) },
                    )
                }.single().measure(Constraints.fixed(width,constraints.maxHeight))
            }
            layout(constraints.maxWidth,constraints.maxHeight) {
                cells.forEachIndexed { index,cell -> cell.placeRelative((start+index-first)*width-offset,0) }
            }
        }
    }
}

/** Ink-based placement gives equal margins even when digits have side bearings or the unit ends in a dot. */
@Composable
internal fun DurationBadge(minutes: Long, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val number = minutes.toString()
    val unit = tr("Min.")
    val face=dev.ambivalenton.openv.ui.theme.appTypeface()
    val numberPaint = remember(face) { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.create(face?:Typeface.DEFAULT,Typeface.BOLD);color=android.graphics.Color.WHITE } }
    val unitPaint = remember(face) { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=face?:Typeface.DEFAULT;color=android.graphics.Color.WHITE } }
    val numberBounds = remember { Rect() }
    val unitBounds = remember { Rect() }
    Canvas(modifier.fillMaxSize().background(Color(0xFF263C42),RoundedCornerShape(6.dp))
        .clickable(onClickLabel=tr("Verbindungsdetails öffnen"),onClick=onClick)
        .semantics { contentDescription=description }) {
        val gap = 2.dp.toPx()
        val availableWidth = (size.width-10.dp.toPx()).coerceAtLeast(1f)
        val availableHeight = (size.height-10.dp.toPx()).coerceAtLeast(1f)
        var font = 21.sp.toPx()
        fun measure() {
            numberPaint.textSize=font
            unitPaint.textSize=font/2f
            numberPaint.getTextBounds(number,0,number.length,numberBounds)
            unitPaint.getTextBounds(unit,0,unit.length,unitBounds)
        }
        measure()
        repeat(5) {
            val factor=minOf(1f,availableWidth/(numberBounds.width()+gap+unitBounds.width()).coerceAtLeast(1f),
                availableHeight/numberBounds.height().coerceAtLeast(1))
            if (factor<1f) { font*=factor*.99f;measure() }
        }
        fun Rect.ink()=DurationInk(left.toFloat(),top.toFloat(),right.toFloat(),bottom.toFloat())
        val place=durationPlacement(size.width,size.height,numberBounds.ink(),unitBounds.ink(),gap)
        drawContext.canvas.nativeCanvas.apply {
            drawText(number,place.numberX,place.baseline,numberPaint)
            drawText(unit,place.unitX,place.baseline,unitPaint)
        }
    }
}
