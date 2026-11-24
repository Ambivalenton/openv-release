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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import dev.ambivalenton.openv.data.model.reduceMotion
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
    val minutes=remember(trips,durations) {trips.associate {it.id to ((durations[it.id] ?: it.totalDuration)+59_999)/60_000}}
    // Build 114: the connections at least half in view decide the average, the shortest and the longest.
    val inView by remember(trips,state) {derivedStateOf {
        val info=state.layoutInfo
        info.visibleItemsInfo.filter {item->
            val shown=minOf(item.offset+item.size,info.viewportEndOffset)-maxOf(item.offset,info.viewportStartOffset)
            item.size>0 && shown*2>=item.size
        }.mapNotNull {trips.getOrNull(it.index)?.id}
    }}
    val looks=remember(minutes,inView) {durationLooks(minutes,inView)}
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
                    val look=looks[trip.id] ?: DurationLook()
                    DurationBadge(
                        minutes = minutes[trip.id] ?: 0L,
                        description = tr("Gesamtdauer für Verbindung %1\$s: %2\$s Minuten",index+1,minutes[trip.id] ?: 0L)+when(look.mark) {
                            DurationMark.SHORTEST->", "+tr("kürzeste im Blick")
                            DurationMark.LONGEST->", "+tr("längste im Blick")
                            DurationMark.NONE->""
                        },
                        modifier = Modifier.padding(2.dp).testTag("duration-${trip.id}"),
                        look = look,
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

/** The neutral badge: dark slate with white digits, as before Build 114. */
internal val DurationBadgeBase = Color(0xFF263C42)

/**
 * Background and ink of a badge for [look] (Build 114): neutral slate, leaning a little towards the
 * accent for shorter and towards a quiet grey for longer connections; the shortest in view in the
 * accent itself, the longest clearly greyed out. Every pair stays readable (ink chosen by contrast).
 */
@Composable
internal fun durationColors(look: DurationLook): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < .5f
    fun inkOn(c: Color) = if (c.luminance() > .36f) Color(0xFF15191C) else Color.White
    return when (look.mark) {
        DurationMark.SHORTEST -> scheme.primary to scheme.onPrimary
        DurationMark.LONGEST -> if (dark) Color(0xFF34373B) to Color(0xFFAEB2B7) else Color(0xFFE2E4E7) to Color(0xFF5B6066)
        DurationMark.NONE -> {
            val bg = if (look.tint < 0f) lerp(DurationBadgeBase, scheme.primary, -look.tint)
                else lerp(DurationBadgeBase, Color(0xFF62676D), look.tint)
            bg to inkOn(bg)
        }
    }
}

/** Ink-based placement gives equal margins even when digits have side bearings or the unit ends in a dot. */
@Composable
internal fun DurationBadge(minutes: Long, description: String, modifier: Modifier = Modifier,
    look: DurationLook = DurationLook(), onClick: () -> Unit) {
    val number = minutes.toString()
    val unit = tr("Min.")
    val face=dev.ambivalenton.openv.ui.theme.appTypeface()
    val numberPaint = remember(face) { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.create(face?:Typeface.DEFAULT,Typeface.BOLD) } }
    val unitPaint = remember(face) { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=face?:Typeface.DEFAULT } }
    val numberBounds = remember { Rect() }
    val unitBounds = remember { Rect() }
    // Build 114: size and colour follow the connection's place among those in view, always by a spring.
    val quiet = LocalAppSettings.current.reduceMotion
    val (targetBackground, targetInk) = durationColors(look)
    val background by animateColorAsState(targetBackground, if (quiet) snap() else spring(dampingRatio = .82f, stiffness = 260f), label = "duration-background")
    val ink by animateColorAsState(targetInk, if (quiet) snap() else spring(dampingRatio = .82f, stiffness = 260f), label = "duration-ink")
    val scale by animateFloatAsState(look.scale, if (quiet) snap() else spring(dampingRatio = .62f, stiffness = 320f), label = "duration-scale")
    Canvas(modifier.fillMaxSize()
        .clickable(onClickLabel=tr("Verbindungsdetails öffnen"),onClick=onClick)
        .semantics { contentDescription=description }) {
        // The average connection fills 86 % of its cell; shorter ones grow towards the whole cell.
        val fraction = (.86f * scale).coerceIn(.6f, 1f)
        val w = size.width * fraction; val h = size.height * fraction
        val boxLeft = (size.width - w) / 2f; val boxTop = (size.height - h) / 2f
        drawRoundRect(background, Offset(boxLeft, boxTop), Size(w, h), CornerRadius(6.dp.toPx() * fraction))
        numberPaint.color = ink.toArgb(); unitPaint.color = ink.toArgb()
        val gap = 2.dp.toPx() * fraction
        val availableWidth = (w-8.dp.toPx()*fraction).coerceAtLeast(1f)
        val availableHeight = (h-8.dp.toPx()*fraction).coerceAtLeast(1f)
        var font = 21.sp.toPx() * fraction / .86f
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
