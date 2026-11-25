// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.hints.gestureHint

import androidx.compose.animation.core.VisibilityThreshold

import dev.ambivalenton.openv.localization.tr

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.theme.timelineColorPalette
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.flow.first
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun timelineColumnWidth(fontScale: Float): Dp =
    ((56 * fontScale.coerceIn(.85f, 1.6f) - 8f) * 1.1f + 8f).dp

/** Includes the same 4 dp gutter on either side in the real chart and settings previews. */
internal fun timelineRideBarWidth(fontScale:Float):Dp=timelineColumnWidth(fontScale)-8.dp

internal const val TIMELINE_DP_PER_MINUTE = 3.4f
internal const val TIMELINE_DIAGONAL_RATIO = .4f

private data class TimelineTransform(val minuteDp: Float, val diagonalPx: Float)

/** Absolute dimensions do not depend on the loaded alternatives or time-window extent. */
internal fun timelineTimeOffset(time: Long, origin: Long): Dp =
    (((time - origin) / 60_000.0) * TIMELINE_DP_PER_MINUTE).toFloat().dp

internal fun timelineAxisWidth(fontScale: Float): Dp = (18 * fontScale.coerceAtLeast(1f)).dp

data class TimelineWindow(val start: Long, val end: Long) {
    val minutes
        get() = ((end - start) / 60000f).coerceAtLeast(1f)

    fun fraction(time: Long) =
        ((time - start).toDouble() / (end - start).coerceAtLeast(1)).coerceIn(0.0, 1.0).toFloat()
}

/** The shared axis uses actual predicted timestamps; delays are never added twice. */
fun timelineWindow(trips: List<Trip>): TimelineWindow {
    val start = trips.minOfOrNull { it.startTime } ?: 0L
    val end = trips.maxOfOrNull { it.endTime } ?: start + 3600000L
    val quantum = 15 * 60000L
    return TimelineWindow(
        Math.floorDiv(start, quantum) * quantum,
        ((ceil(end.toDouble() / quantum).toLong() * quantum).coerceAtLeast(
            Math.floorDiv(start, quantum) * quantum + quantum
        )),
    )
}

/** One column per alternative, common vertical time axis: the classic Öffi comparison layout. */
@Composable
fun OeffiTimelineGraph(
    trips: List<Trip>,onTripSelected:(Trip)->Unit,modifier:Modifier=Modifier,
    onTripLongPress:((Trip)->Unit)?=null,scrollState:LazyListState=rememberLazyListState(),
    minimumHeight:Dp=0.dp,onSnapped:((Trip)->Unit)?=null,headerAnchor:ChartHeaderAnchor?=null,
    onPullToPast:(()->Unit)?=null,
    onPullToFuture:(()->Unit)?=null,
    staleTripIds:Set<String> = emptySet(),
    onEdgeReached:((later:Boolean)->Unit)?=null,
    /** Columns shared with the duration row; computed from the width when absent. */
    columns:ChartColumns?=null,
    /** Where (px, in root) the lowest arrival may end, e.g. just above the navigation pill. */
    fitBottomInRoot:(()->Float)?=null,
) {
    val settings=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current
    val projected=remember(trips,settings.timelineShowWalking,settings.walkingSpeed,settings.ignoreWalking) {trips.map {assembleTimeline(it,settings).trip}}
    val original=remember(trips) {trips.associateBy {it.id}}
    if(projected.isNotEmpty() && projected.all {it.segments.isEmpty()}) {
        Text(tr("Diese Verbindung enthält nur Fußwege."),modifier);return
    }
    key(settings.timelineShowWalking,settings.walkingSpeed,settings.ignoreWalking) {
    OeffiTimelineGraphBody(projected,{onTripSelected(original[it.id] ?: it)},modifier,
        onTripLongPress?.let {callback->{t:Trip->callback(original[t.id] ?: t)}},scrollState,minimumHeight,
        onSnapped?.let {callback->{t:Trip->callback(original[t.id] ?: t)}},headerAnchor,onPullToPast,onPullToFuture,staleTripIds,onEdgeReached,
        columns,fitBottomInRoot)
    }
}

@Composable
private fun OeffiTimelineGraphBody(
    trips: List<Trip>,
    onTripSelected: (Trip) -> Unit,
    modifier: Modifier = Modifier,
    onTripLongPress: ((Trip) -> Unit)? = null,
    scrollState: LazyListState = rememberLazyListState(),
    minimumHeight: Dp = 0.dp,
    onSnapped: ((Trip)->Unit)? = null,
    headerAnchor: ChartHeaderAnchor? = null,
    onPullToPast:(()->Unit)?=null,
    onPullToFuture:(()->Unit)?=null,
    staleTripIds:Set<String> = emptySet(),
    onEdgeReached:((later:Boolean)->Unit)?=null,
    columnsOverride: ChartColumns? = null,
    fitBottomInRoot: (() -> Float)? = null,
) {
    val palette = timelineColorPalette()
    val settings=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current
    val barStyle=dev.ambivalenton.openv.ui.theme.LocalTimelineTheme.current.style
    // The same outline the bars cast their shadow with; time chips are cut along it.
    val chipCutArtwork=if(barStyle==TimelineBarStyle.APP_THEME) AppChartArtwork(dev.ambivalenton.openv.ui.theme.LocalAppVisuals.current,
        MaterialTheme.colorScheme,dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion) else null
    val chipBarSilhouette=remember(barStyle,chipCutArtwork) {TimelineBarSilhouette(barStyle,chipCutArtwork)}
    /** The same accent fills every footprint, including its parts crossing a clock. */
    val footprintAccent=footprintAccentColor(MaterialTheme.colorScheme.primary,settings.monochrome)
    if (trips.isEmpty()) return
    val window = remember(trips) { timelineWindow(trips) }
    val origin = window.start
    // Unique even if a caller passes repeated IDs; stable because it derives from trip IDs.
    val renderKeys = remember(trips) { uniqueRenderKeys(trips.map { it.id }) }
    val fontScale = LocalDensity.current.fontScale
    val density = LocalDensity.current
    var viewportTop by remember { mutableFloatStateOf(0f) }
    var measured by remember { mutableStateOf(false) }
    val glyphScale=settings.footprints.normalized().barScale
    // Half a sole: the outermost footprint may reach that far beyond its track.
    val fringeDp=with(density) {(FOOTPRINT_LENGTH_PX/2*glyphScale).toDp().value}
    // Visible steps in front of a chip that covers the rest of a short walk (about two strides).
    val approachDp=with(density) {(FOOTPRINT_STRIDE_PX*1.6f*glyphScale.coerceIn(.7f,2f)).toDp().value}.coerceAtLeast(12f)
    val quietMode=settings.reduceMotion
    // 2.5: sections whose boarding or alighting is named by a notice (hidden notices left out).
    val noticeActions=LocalJourneyNotices.current
    // The band stays even when a notice is no longer emphasised in the timeline.
    val noticeMarksByTrip=remember(trips) {
        trips.associate {it.id to dev.ambivalenton.openv.data.model.noticeMarks(dev.ambivalenton.openv.data.model.scanJourneyNotices(it))}
    }
    val haptics=rememberChartHaptics()
    var moving by remember { mutableStateOf(false) }
    val movingNow by rememberUpdatedState(moving)
    var lockedId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(trips.getOrNull(scrollState.firstVisibleItemIndex)?.id ?: trips.first().id) }
    val focused = trips.firstOrNull { it.id == lockedId } ?: trips.first()
    LaunchedEffect(focused.id) { onSnapped?.invoke(focused) }
    // One vertical projection for every column, the grid, the axis and the now line.
    val extents = remember(trips, fontScale, fringeDp, approachDp) { ChartExtentTable(trips, fontScale, fringeDp, approachDp) }
    val projection = remember { ChartProjection() }
    BoxWithConstraints(
        modifier.fillMaxWidth().then(if (minimumHeight > 0.dp) Modifier.height(minimumHeight) else Modifier.fillMaxHeight())
            .onGloballyPositioned { viewportTop=it.positionInRoot().y; measured=true }
            .clipToBounds().testTag("timeline-viewport")
    ) {
        val axisWidth = timelineAxisWidth(fontScale)
        val axisPx = with(density) { axisWidth.roundToPx() }
        val columns = columnsOverride ?: remember(constraints.maxWidth, axisPx, fontScale) {
            chartColumns(constraints.maxWidth - axisPx, with(density) { timelineColumnWidth(fontScale).toPx() })
        }
        val columnPx = columns.widthPx
        val columnWidth = with(density) { columnPx.toDp() }
        val barWidth = columnWidth - 8.dp
        val heightPx = constraints.maxHeight.toFloat()
        val lastIndex = (trips.size - columns.count).coerceAtLeast(0)
        val latestFitBottom by rememberUpdatedState(fitBottomInRoot)
        val topDp = CHART_TOP_GAP_DP
        fun position() = scrollState.firstVisibleItemIndex + scrollState.firstVisibleItemScrollOffset / columnPx.toFloat()
        fun availableDp(): Float {
            val bottom = (latestFitBottom?.invoke()?.minus(viewportTop) ?: heightPx).coerceIn(0f, heightPx)
            return bottom / density.density - topDp
        }
        val target = { chartFrameAt(position(), trips, extents, columns.count, origin, topDp, availableDp(), lastIndex) }
        // The frame follows its target with a spring; the loop sleeps while it is reached.
        LaunchedEffect(projection, extents, columns, lastIndex, quietMode) {
            snapshotFlow { measured }.first { it }
            if (!projection.placed) projection.snapTo(target())
            while (true) {
                snapshotFlow { target() }.first { !projection.converged(it) }
                if (quietMode) { projection.snapTo(target()); continue }
                var last = -1L
                do {
                    val going = withFrameNanos { now ->
                        val dt = if (last < 0) 1 / 60f else (now - last) / 1e9f
                        last = now
                        val goal = target()
                        projection.step(goal, dt)
                        !projection.converged(goal)
                    }
                } while (going)
            }
        }
        // Where a time stands (inside its bar or as a chip outside) is decided only at rest. While
        // the chart moves, every element follows the live frame with the layout it had when the motion
        // began, so nothing flips between inside and outside; at rest, changes glide (see ClockMorph).
        var restScale by remember(projection) { mutableFloatStateOf(Float.NaN) }
        LaunchedEffect(projection) {
            snapshotFlow { Triple(projection.placed, projection.moving || moving, projection.scale) }
                .collect { (placed, busy, s) -> if (placed && (!busy || restScale.isNaN())) restScale = round(s * 100f) / 100f }
        }
        val composeScale = if (restScale.isNaN()) projection.scale else restScale
        fun yPx(time: Long) = projection.yDp((time - origin) / 60_000f) * density.density
        // The leftmost visible column, projected exactly as it is drawn (for the query header).
        val leftBarTopInRoot by rememberUpdatedState<() -> Float>({
            viewportTop + yPx(trips[scrollState.firstVisibleItemIndex.coerceIn(trips.indices)].startTime)
        })
        DisposableEffect(headerAnchor) {
            val projectionOfLeft = { leftBarTopInRoot() }
            headerAnchor?.trackLeftBar(projectionOfLeft)
            onDispose { headerAnchor?.stopTracking(projectionOfLeft) }
        }
        LaunchedEffect(scrollState,columnPx) {
            var previous: Int?=null
            snapshotFlow { chartCrossingBucket(scrollState.firstVisibleItemIndex*columnPx+scrollState.firstVisibleItemScrollOffset.toFloat(),0f,columnPx.toFloat()) }
                .distinctUntilChanged().collect { bucket ->
                    if (previous!=null && previous!=bucket && movingNow) haptics.crossing()
                    previous=bucket
                }
        }
        // Phase 4 of the loading plan: about 80 % towards an edge requests one batch in the direction
        // the user actually scrolled. Prepending pages or live updates never count as user movement.
        val edgeCallback by rememberUpdatedState(onEdgeReached)
        LaunchedEffect(scrollState,columnPx) {
            var lastPx:Float?=null;var lastTotal=-1;var direction=0;var armed=false
            snapshotFlow {
                val info=scrollState.layoutInfo
                ChartEdgeProbe(scrollState.firstVisibleItemIndex,info.visibleItemsInfo.lastOrNull()?.index ?: -1,info.totalItemsCount,
                    scrollState.firstVisibleItemIndex*columnPx+scrollState.firstVisibleItemScrollOffset.toFloat())
            }.distinctUntilChanged().collect {probe->
                val previous=lastPx
                if(previous!=null && probe.total==lastTotal && movingNow) {
                    val moved=probe.position-previous
                    if(kotlin.math.abs(moved)>.5f) {direction=if(moved>0)1 else -1;armed=true}
                }
                lastPx=probe.position;lastTotal=probe.total
                if(!armed)return@collect
                val later=chartEdgeRequest(probe.firstVisible,probe.lastVisible,probe.total,direction) ?: return@collect
                armed=false
                edgeCallback?.invoke(later)
            }
        }
        // A restored or resized position always rests on a whole column.
        LaunchedEffect(columnPx, trips.size) {
            if (scrollState.firstVisibleItemScrollOffset != 0 && !scrollState.isScrollInProgress) {
                val index = (position() + .5f).toInt().coerceIn(0, lastIndex)
                scrollState.scrollToItem(index)
            }
        }
        val gridColor = MaterialTheme.colorScheme.outlineVariant
        val axisInk = MaterialTheme.colorScheme.onSurfaceVariant
        val measurer = rememberTextMeasurer(cacheSize = 48)
        val axisStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
        val marks = remember(window) { chartAxisMarks(window) }
        Box(Modifier.fillMaxSize().axisLockedChartScroll(scrollState,
            onMotion={moving=it},onPullToPast=onPullToPast,onPullToFuture=onPullToFuture,
            lastIndex={lastIndex},
            onSettled={index -> lockedId=trips[index.coerceIn(trips.indices)].id;haptics.locked()})
            .testTag("connection-chart")) {
            // Grid and time axis: drawn straight from the frame, marks of finer steps fade in as room appears.
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = if (projection.placed) 1f else 0f }.testTag("timeline-plot")) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
                val axisCentre = axisPx / 2f
                for (mark in marks) {
                    val y = projection.yDp((mark.time - origin) / 60_000f) * density.density
                    if (y < -24.dp.toPx() || y > size.height + 24.dp.toPx()) continue
                    val alpha = chartMarkAlpha(mark.minuteOfDay, projection.scale)
                    if (alpha <= .01f) continue
                    drawLine(gridColor.copy(alpha = gridColor.alpha * alpha), Offset(axisPx.toFloat(), y), Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(), pathEffect = dash)
                    val label = measurer.measure(clockTime(mark.time), axisStyle)
                    rotate(-90f, Offset(axisCentre, y)) {
                        drawText(label, axisInk, Offset(axisCentre - label.size.width / 2f, y - label.size.height / 2f), alpha = alpha)
                    }
                }
            }
            Row(Modifier.fillMaxSize()) {
                Spacer(Modifier.width(axisWidth))
                // Each lazy item has the same required width, even while pages are appended.
                LazyRow(
                    modifier = Modifier.width(with(density) { (columnPx * columns.count).toDp() }).fillMaxHeight()
                        .graphicsLayer { alpha = if (projection.placed) 1f else 0f }.testTag("connection-columns"),
                    state = scrollState,
                    userScrollEnabled = false,
                ) {
                    itemsIndexed(trips, key = { index, _ -> renderKeys[index] }) { index, trip ->
                        val marks = noticeMarksByTrip[trip.id].orEmpty()
                        Box(
                            Modifier
                                // 2.2: when a bar leaves (cancelled, departed), the others move up calmly.
                                .animateItem(fadeInSpec = if (quietMode) null else androidx.compose.animation.core.tween(220),
                                    placementSpec = if (quietMode) null else androidx.compose.animation.core.spring(dampingRatio = .9f, stiffness = 260f,
                                        visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold),
                                    fadeOutSpec = if (quietMode) null else androidx.compose.animation.core.tween(180))
                                .requiredWidth(columnWidth)
                                .fillMaxHeight().cachedJourneyTint(trip.id in staleTripIds)
                                // Build 115: on the first column in view, a hold ring shows what a long press does.
                                .then(if (onTripLongPress != null && index == scrollState.firstVisibleItemIndex)
                                    Modifier.gestureHint(dev.ambivalenton.openv.data.model.GestureHintKeys.CHART_HOLD, dev.ambivalenton.openv.ui.hints.HintKind.HOLD, 2400L) else Modifier)
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    role = Role.Button,
                                    onClickLabel = tr("Verbindungsdetails öffnen"),
                                    onLongClickLabel = onTripLongPress?.let {settings.chartLongPress.title},
                                    onLongClick = onTripLongPress?.let { action -> { action(trip) } },
                                    onClick = { onTripSelected(trip) },
                                )
                                .semantics(mergeDescendants = true) {
                                    contentDescription =
                                        tr("Verbindung %1\$s, %2\$s bis %3\$s, %4\$s, %5\$s. ", index + 1, clockTime(trip.startTime), clockTime(trip.endTime),
                                            transferCount(trip.transfers), minutesText((trip.totalDuration + 59_999) / 60_000)) +
                                            trip.segments.withIndex().joinToString(". ") { (i, it) ->
                                                tr("%1\$s von %2\$s nach %3\$s, %4\$s bis %5\$s", it.lineName, it.origin.name, it.destination.name, clockTime(it.startTime), clockTime(it.endTime)) +
                                                    // 2.5: the notice mark is spoken, e.g. "Einstieg verlegt".
                                                    (marks[i]?.let { label -> ", $label" } ?: "")
                                            } +
                                            // K01: a saved, not yet confirmed bar says so (not only by its paler colour).
                                            (if (trip.id in staleTripIds) ". " + tr("Gespeicherter Stand %1\$s Uhr", clockTime(trip.fetchedAt)) else "")
                                }
                        ) {
                            val q = composeScale
                            val clocks = remember(trip, q, fontScale) { timelineClockLabels(trip, q, fontScale) }
                            val tracks = remember(trip, q, fontScale, clocks) { timelineWalkTracks(trip, q, fontScale, clocks, fringeDp, approachDp) }
                            val layouts=remember(trip.id) {mutableStateMapOf<String,FootprintClockText>()}
                            /** Offset of [time] below the departure at the layout scale, in dp. */
                            fun offQ(time: Long) = (time - trip.startTime) / 60_000f * q
                            fun minutes(time: Long) = (time - trip.startTime) / 60_000f
                            val clockHeight=timelineClockHeight(fontScale).dp
                            val d = density.density
                            // Each clock with the constant that places its box relative to the live position of its time.
                            val placedClocks = remember(clocks, q) { clocks.map { PlacedClock(it, it.topDp - offQ(it.time)) } }
                            val morph = rememberClockMorph(placedClocks, quietMode, ready = !restScale.isNaN())
                            val previous = morph.previous.associateBy { it.label.key }
                            val current = morph.current.associateBy { it.label.key }
                            /** The live box top of [clock]; while a change glides it travels from its former place. */
                            fun topOf(clock: PlacedClock): () -> Float {
                                val from = previous[clock.label.key]?.constant
                                val to = current[clock.label.key]?.constant
                                return {
                                    val t = morph.progress
                                    val c = when {
                                        from != null && to != null -> from + (to - from) * t
                                        else -> clock.constant
                                    }
                                    yPx(clock.label.time) + c * d
                                }
                            }
                            /** Present (1) unless it is fading in (new or changed form) or out (gone or changed form). */
                            fun alphaOf(clock: PlacedClock, leaving: Boolean): () -> Float = { morph.alpha(clock, leaving) }
                            val leaving = morph.previous.filter { old -> current[old.label.key]?.label?.placement != old.label.placement }
                            // Chips, bars and every clock are drawn first; footprints cross them above.
                            @Composable fun Chip(placed: PlacedClock, leavingChip: Boolean) {
                                val clock = placed.label
                                val segment=trip.segments[clock.segmentIndex]
                                val accent=if(clock.walking) null else palette[segment.lineType].rail
                                // Under a filled bar the chip continues up to half the bar (at most half its
                                // width): deep enough for every corner radius, never out of the far side.
                                val tuck=if(clock.walking || barStyle.open) 0.dp
                                    else minOf((offQ(segment.endTime)-offQ(segment.startTime)).dp/2,barWidth/2)
                                // Every ride bar near the chip, in the coordinates of its label box.
                                val boxTop = offQ(clock.time) + placed.constant
                                val cut=if(barStyle.open) null else with(density) {
                                    val bars=trip.segments.filterNot {it.isWalk}.map {ride->
                                        val top=(offQ(ride.startTime)-boxTop).dp.toPx()
                                        Rect(4.dp.toPx(),top,(4.dp+barWidth).toPx(),
                                            top+(offQ(ride.endTime)-offQ(ride.startTime)).dp.coerceAtLeast(1.dp).toPx())
                                    }.filter {it.bottom>=-tuck.toPx()-1f && it.top<=(clockHeight+tuck).toPx()+1f}
                                    if(bars.isEmpty()) null else ChipBarCut(chipBarSilhouette,bars)
                                }
                                TimelineClockChip(clockTime(clock.time),clock.placement,accent,
                                    Modifier.liveSpan(topOf(placed), alpha = alphaOf(placed, leavingChip)).fillMaxWidth().height(clockHeight)
                                        .clearAndSetSemantics {if (!leavingChip) {testTag="timeline-${clock.key}-${trip.id}";this[TimelineClockForm]=clock.placement}},
                                    maxWidth=barWidth,tuck=tuck,cut=cut,
                                    onPlaced=if (leavingChip) null else {_,text ->
                                        // Into column coordinates at the layout scale (footprints use the same).
                                        val shift=Offset(0f,with(density) {boxTop.dp.toPx()})
                                        val digits=text?.translated(shift)
                                        if(digits!=null && layouts["chip-${clock.key}"]!=digits) layouts["chip-${clock.key}"]=digits
                                    })
                            }
                            @Composable fun Inside(placed: PlacedClock, leavingClock: Boolean) {
                                val clock = placed.label
                                val ink=palette[trip.segments[clock.segmentIndex].lineType].content
                                val labelTag="timeline-${clock.key}-${trip.id}"
                                val boxTop = offQ(clock.time) + placed.constant
                                InsideBarClock(clockTime(clock.time),ink,Modifier.liveSpan(topOf(placed), alpha = alphaOf(placed, leavingClock)).fillMaxWidth()
                                    .clearAndSetSemantics {if (!leavingClock) {testTag=labelTag;this[TimelineClockForm]=clock.placement}}) {result ->
                                    if (leavingClock) return@InsideBarClock
                                    val item=FootprintClockText(result,Offset(0f,with(density) {boxTop.dp.toPx()}),ink)
                                    if(layouts[clock.key]!=item) layouts[clock.key]=item
                                }
                            }
                            fun outsideOf(list: List<PlacedClock>, walking: Boolean) =
                                list.filter { it.label.placement != TimelineClockPlacement.INSIDE && it.label.walking == walking }
                            leaving.let { outsideOf(it, false) }.forEach { key("out-${it.label.key}") { Chip(it, true) } }
                            outsideOf(morph.current, false).forEach { key("chip-${it.label.key}") { Chip(it, false) } }
                            trip.segments.forEachIndexed { segmentIndex,segment ->
                                if(!segment.isWalk) key("bar-$segmentIndex") {
                                    val h=(offQ(segment.endTime)-offQ(segment.startTime)).dp
                                    // Times inside a long bar keep their rows free of the line name;
                                    // when that changes at rest, the room opens or closes smoothly.
                                    val holdsStart = morph.current.any {it.label.segmentIndex==segmentIndex && it.label.placement==TimelineClockPlacement.INSIDE && it.label.time==segment.startTime}
                                    val holdsEnd = morph.current.any {it.label.segmentIndex==segmentIndex && it.label.placement==TimelineClockPlacement.INSIDE && it.label.time==segment.endTime}
                                    val insetSpec = if (quietMode || morph.provisional) androidx.compose.animation.core.snap<Dp>() else androidx.compose.animation.core.tween(CLOCK_MORPH_MS)
                                    val insideTop by androidx.compose.animation.core.animateDpAsState(if (holdsStart) clockHeight+2.dp else 0.dp, insetSpec, label = "bar-inset-top")
                                    val insideBottom by androidx.compose.animation.core.animateDpAsState(if (holdsEnd) clockHeight+2.dp else 0.dp, insetSpec, label = "bar-inset-bottom")
                                    val span = minutes(segment.endTime) - minutes(segment.startTime)
                                    TimelineBar(barStyle,palette[segment.lineType],segment.lineName,
                                        modifier=Modifier.offset(x=4.dp)
                                            .liveSpan({ yPx(segment.startTime) }, { (span * projection.scale * d).coerceAtLeast(d) })
                                            .width(barWidth)
                                            .clearAndSetSemantics {testTag="timeline-segment-${trip.id}-$segmentIndex"},
                                        showLabel=h>=4.dp,delayInfo=segment.timelineDelay(),showDelayLabel=h-insideTop-insideBottom>=44.dp,
                                        noticeMark=segmentIndex in marks,
                                        labelInsetTop=insideTop,labelInsetBottom=insideBottom,
                                    )
                                }
                            }
                            // Frameless times inside long bars, 1 px contrast contour and a light shadow.
                            leaving.filter { it.label.placement == TimelineClockPlacement.INSIDE }.forEach { key("in-out-${it.label.key}") { Inside(it, true) } }
                            morph.current.filter { it.label.placement == TimelineClockPlacement.INSIDE }.forEach { key("in-${it.label.key}") { Inside(it, false) } }
                            // Walking clocks also sit beneath the accent-coloured footprints.
                            outsideOf(leaving, true).forEach { key("walk-out-${it.label.key}") { Chip(it, true) } }
                            outsideOf(morph.current, true).forEach { key("walk-${it.label.key}") { Chip(it, false) } }
                            // Final layer: accent soles with a 4 px contour over digits and chip borders.
                            tracks.forEach {track -> key("track-${track.segmentIndex}") {
                                val segment=trip.segments[track.segmentIndex]
                                val x=columnWidth/2-barWidth/2
                                val origin0=with(density) {Offset(x.toPx(),track.topDp.dp.toPx())}
                                // Only entries of clocks that still exist (zooming or a refresh changes them).
                                val keys=clocks.map {it.key}.toSet()
                                val covered=layouts.filterKeys {it.removePrefix("chip-") in keys}.values.map {it.translated(-origin0)}
                                // The track's ends follow the walk's own start and end times; a change at rest glides
                                // together with the times (the first layout after opening is taken as it is).
                                val spec = if (quietMode || morph.provisional) androidx.compose.animation.core.snap<Float>() else androidx.compose.animation.core.tween(CLOCK_MORPH_MS)
                                val topConstant by androidx.compose.animation.core.animateFloatAsState(track.topDp - offQ(segment.startTime), spec, label = "track-top")
                                val bottomConstant by androidx.compose.animation.core.animateFloatAsState(track.bottomDp - offQ(segment.endTime), spec, label = "track-bottom")
                                val top = { yPx(segment.startTime) + topConstant * d }
                                WalkingFootprintTrack(footprintAccent,
                                    Modifier.offset(x=x).liveSpan(top, { (yPx(segment.endTime) + bottomConstant * d - top()).coerceAtLeast(d) })
                                        .width(barWidth)
                                        .clearAndSetSemantics {testTag="timeline-footprints-${trip.id}-${track.segmentIndex}"},
                                    trackKey="${trip.id}:${segment.id}",clocks=covered,glyphScale=glyphScale)
                            } }
                        }
                    }
                }
            }
        }
        TimelineNowLine({ now -> projection.yDp((now - origin) / 60_000f) * density.density })
    }
}

/** Gap between the duration row and the highest time chip of the leftmost column. */
internal const val CHART_TOP_GAP_DP = 6f

/** A mark of the time axis: its time and its minute within the local day (for the step it belongs to). */
internal data class ChartAxisMark(val time: Long, val minuteOfDay: Int)

/** Quarter-hour marks around the whole chart; the projection decides which ones show. */
internal fun chartAxisMarks(window: TimelineWindow, zone: ZoneId = dev.ambivalenton.openv.data.model.transitZone): List<ChartAxisMark> {
    var mark = Instant.ofEpochMilli(window.start - 3 * 3_600_000L).atZone(zone).withSecond(0).withNano(0)
    mark = mark.withMinute(mark.minute / 15 * 15)
    val end = window.end + 3 * 3_600_000L
    return buildList {
        while (mark.toInstant().toEpochMilli() <= end && size < 400) {
            add(ChartAxisMark(mark.toInstant().toEpochMilli(), mark.hour * 60 + mark.minute))
            mark = mark.plusMinutes(15)
        }
    }
}

/**
 * The element's top (px, from the column top) and optionally its height come from the live
 * frame, read during layout and drawing only. Nothing recomposes while the chart moves; the element
 * may extend beyond its column and is never centred by an oversized measurement.
 */
private fun Modifier.liveSpan(top: () -> Float, height: (() -> Float)? = null, alpha: (() -> Float)? = null): Modifier =
    this.layout { measurable, constraints ->
        val h = height?.invoke()?.let { kotlin.math.ceil(it).toInt().coerceAtLeast(1) }
        val placeable = measurable.measure(if (h == null) constraints
            else androidx.compose.ui.unit.Constraints(constraints.minWidth, constraints.maxWidth, h, h))
        layout(placeable.width, placeable.height.coerceAtMost(constraints.maxHeight)) { placeable.place(0, 0) }
    }.graphicsLayer {
        translationY = top()
        if (alpha != null) {
            // Shadow, contour and a chip's tuck reach beyond the element's box; an offscreen layer
            // would cut them off while it fades, so the alpha goes onto each drawing instead.
            val a = alpha()
            if (a < 1f) compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            this.alpha = a
        }
    }

/** 2 px stroke centred on the glyph edge: the fill covers the inner half, exactly 1 physical pixel of
 * contour in the opposite ink stays visible. The contour layer carries a light drop shadow. */
internal const val INSIDE_CLOCK_CONTOUR_PX=2f

/** A departure or arrival time inside its bar, without a frame. */
@Composable internal fun InsideBarClock(text:String,ink:Color,modifier:Modifier=Modifier,onTextLayout:(androidx.compose.ui.text.TextLayoutResult)->Unit={}) {
    val density=LocalDensity.current
    val contour=if(ink.luminance()>.5f) Color.Black else Color.White
    val base=MaterialTheme.typography.labelMedium.copy(fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
    val shadow=with(density) {androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha=.38f),Offset(0f,1.dp.toPx()),1.5.dp.toPx())}
    Box(modifier) {
        Text(text,Modifier.fillMaxWidth(),style=base.copy(shadow=shadow,
            drawStyle=androidx.compose.ui.graphics.drawscope.Stroke(INSIDE_CLOCK_CONTOUR_PX,join=androidx.compose.ui.graphics.StrokeJoin.Round)),
            color=contour,maxLines=1)
        Text(text,Modifier.fillMaxWidth(),style=base,color=ink,maxLines=1,onTextLayout=onTextLayout)
    }
}

/** The form a time is shown in (for tests; not read out, the column has its own description). */
internal val TimelineClockForm = androidx.compose.ui.semantics.SemanticsPropertyKey<TimelineClockPlacement>("TimelineClockForm")

/** Duration of the glide when times change between inside a bar and a chip (only at rest). */
internal const val CLOCK_MORPH_MS = 320

/** A clock and the constant (dp) that places its box relative to the live position of its time. */
internal data class PlacedClock(val label: TimelineClockLabel, val constant: Float)

/**
 * The clocks a column shows, and while a change glides ([morphing]), the ones it showed before
 * ([previous]) and how far the glide is ([progress] 0…1). All four change together in one
 * snapshot, so no frame ever shows the new progress with the old lists (that made every time blink).
 */
@Stable internal class ClockMorph(initial: List<PlacedClock>, provisional: Boolean = false) {
    /** The shown clocks were laid out before the chart first came to rest (invisible); the next change snaps. */
    var provisional = provisional
    var current by mutableStateOf(initial)
    var previous by mutableStateOf(emptyList<PlacedClock>())
    var morphing by mutableStateOf(false)
    var progress by mutableFloatStateOf(1f)
    /** Starts a glide from what is shown now to [target]. */
    fun begin(target: List<PlacedClock>) = androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
        previous = current; current = target; morphing = true; progress = 0f
    }
    /** Shows [target] at once (also ends a glide). */
    fun settle(target: List<PlacedClock>) = androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
        previous = emptyList(); current = target; morphing = false; progress = 1f
    }
    /** Opacity of a clock: leaving ones fade out, new ones (or ones changing form) fade in, the rest stay. */
    fun alpha(clock: PlacedClock, leaving: Boolean): Float {
        if (!morphing) return 1f
        if (leaving) return 1f - progress
        val before = previous.firstOrNull { it.label.key == clock.label.key }
        return if (before != null && before.label.placement == clock.label.placement) 1f else progress
    }
}

@Composable internal fun rememberClockMorph(target: List<PlacedClock>, quiet: Boolean, ready: Boolean = true): ClockMorph {
    val morph = remember { ClockMorph(target, !ready) }
    LaunchedEffect(target, ready) {
        if (target == morph.current) { if (ready) morph.provisional = false; return@LaunchedEffect }
        if (quiet || morph.provisional) { morph.settle(target); morph.provisional = !ready; return@LaunchedEffect }
        morph.begin(target)
        try {
            androidx.compose.animation.core.animate(0f, 1f, animationSpec = androidx.compose.animation.core.tween(CLOCK_MORPH_MS,
                easing = androidx.compose.animation.core.FastOutSlowInEasing)) { value, _ -> morph.progress = value }
        } finally { if (morph.current == target) morph.settle(target) }
    }
    return morph
}
