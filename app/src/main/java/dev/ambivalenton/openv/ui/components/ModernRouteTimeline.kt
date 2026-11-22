// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.ThemedTonalButton

import dev.ambivalenton.openv.localization.tr

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.rendering.iconResource
import dev.ambivalenton.openv.ui.theme.timelineColorPalette
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * A continuous, color-coded rail. Rows measure naturally; the rail draws behind measured content.
 */
@Composable
fun ModernRouteTimeline(trip: Trip, modifier: Modifier = Modifier) {
    val settings=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current
    val display=remember(trip,settings.walkingSpeed,settings.ignoreWalking) {assembleTimeline(trip,settings.copy(timelineShowWalking=true)).trip}
    ModernRouteTimelineContent(display,modifier)
}

@Composable private fun ModernRouteTimelineContent(trip:Trip,modifier:Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by
        produceState(System.currentTimeMillis(), lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    value = System.currentTimeMillis()
                    delay(30_000)
                }
            }
        }
    // 2.5: notices naming a boarding or alighting stop come first, already open. They stay
    // in the timeline as well; hiding the card only ends the emphasis. Every recognised notice marks
    // its place on the rail with an exclamation mark.
    val notices=LocalJourneyNotices.current
    val hits=remember(trip,notices.dismissed) {dev.ambivalenton.openv.data.model.scanJourneyNotices(trip,notices.dismissed)}
    val recognised=remember(trip) {dev.ambivalenton.openv.data.model.scanJourneyNotices(trip).map {it.text.trim()}.toSet()}
    Column(modifier.fillMaxWidth()) {
        JourneyNoticeCards(hits,Modifier.padding(bottom=12.dp))
        val visitAtBoundary=remember(trip.waypointLegs,trip.waypointVisits) {
            var count=0
            trip.waypointLegs.dropLast(1).mapIndexedNotNull {index,leg->
                count+=leg.segmentCount
                trip.waypointVisits.getOrNull(index)?.let {(count-1) to (index to it)}
            }.toMap()
        }
        trip.segments.forEachIndexed { index, segment ->
            // A walk that reaches the boarding stop at most two minutes before the departure
            // shows that stop once, with the departure time.
            val joined = trip.segments.getOrNull(index + 1)?.let { next -> walkMeetsRide(segment, next) } == true
            // ...and a walk that leaves the stop where a ride just arrived (within two minutes) starts
            // without naming the stop a second time.
            val continues = trip.segments.getOrNull(index - 1)?.let { previous -> rideMeetsWalk(previous, segment) } == true
            RouteSegment(segment, before = index > 0, after = index < trip.segments.lastIndex, recognised = recognised, hideEnd = joined,
                quietStart = continues)
            if (!joined && trip.segments.getOrNull(index + 1)?.let { rideMeetsWalk(segment, it) } != true) trip.segments.getOrNull(index + 1)?.let { next ->
                val gap = next.startTime - segment.endTime
                if (gap > 0) {
                    val ongoing = now in segment.endTime until next.startTime
                    val minutes =
                        ceil((if (ongoing) next.startTime - now else gap) / 60000.0).toInt()
                    TimelineRow(
                        MaterialTheme.colorScheme.outlineVariant,
                        before = true,
                        after = true,
                        dotted = true,
                        node = 0,
                        time = null,
                    ) {
                        visitAtBoundary[index]?.let {(number,visit)->
                            FittedText(tr("Zwischenstopp %1\$s · %2\$s",number+1,visit.location.name),
                                style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            if (ongoing) tr("Noch %1\$s Min.", minutes) else if(visitAtBoundary[index]!=null)
                                tr("%1\$s Min. am Zwischenstopp",minutes) else tr("%1\$s Min. Umstieg", minutes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (gap < 0)
                    TimelineRow(
                        MaterialTheme.colorScheme.error,
                        before = true,
                        after = true,
                        dotted = true,
                        node = 0,
                        time = null,
                    ) {
                        Text(
                            tr("Anschluss unsicher · %1\$s Min. Überschneidung", ceil(-gap / 60000.0).toInt()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
            }
        }
        trip.notices.forEach {
            Text(
                it,
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteSegment(segment: TripSegment, before: Boolean, after: Boolean, recognised: Set<String> = emptySet(), hideEnd: Boolean = false,
    quietStart: Boolean = false) {
    val settings=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current
    var stopsExpanded by rememberSaveable(segment.id) { mutableStateOf(false) }
    var navigationError by remember(segment.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val navigationMode=LocalWalkingNavigationMode.current
    val ink = timelineColorPalette()[segment.lineType]
    val color by animateColorAsState(ink.rail, calmSpec(spring()), label = tr("Linienfarbe"))
    val density=LocalDensity.current
    var originHeight by remember(segment.id) {mutableIntStateOf(0)}
    // The notices of a ride stand before it, where they matter; a recognised one carries the
    // exclamation mark on the rail.
    val notices = !segment.isWalk && segment.notices.isNotEmpty()
    Box(Modifier.fillMaxWidth()) {
    Column {
    if (notices) {
        val flagged = segment.notices.any { it.trim() in recognised }
        TimelineRow(color, before = before, after = true, dotted = false, node = if (flagged) NODE_NOTICE else 0, time = null,
            modifier = Modifier.testTag(if (flagged) "notice-mark-${segment.id}" else "notices-${segment.id}"), nodeY = 30.dp) {
            SegmentNotices(segment.notices)
        }
    }
    TimelineRow(
        color,
        before || notices,
        !segment.isWalk,
        false,
        node = if (quietStart) 0 else 2,
        modifier=if(segment.isWalk) Modifier.onSizeChanged {originHeight=it.height} else Modifier,
        time = if (quietStart) null else clockTime(segment.startTime),
        planned = if (quietStart) null else segment.plannedStartTime.takeIf { it != segment.startTime }?.let(::clockTime),
    ) {
        if (!quietStart) FittedText(
            segment.origin.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (segment.isWalk) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon(R.drawable.ic_walk, null, Modifier.size(18.dp))
                Text(
                    tr("%1\$s Min. Fußweg%2\$s", segment.durationMinutes, segment.distanceMeters?.let { " · $it m" }.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ThemedTonalButton(
                {
                    navigationError =
                        openWalkingNavigation(context,segment,navigationMode).exceptionOrNull()?.message
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                AppIcon(R.drawable.ic_external, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    navigationMode.actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                )
            }
            navigationError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    color = ink.container,
                    contentColor = ink.content,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppIcon(
                            segment.lineType.iconResource(),
                            segment.lineType.label,
                            Modifier.size(20.dp),
                        )
                        Text(segment.lineName, style = MaterialTheme.typography.labelLarge)
                    }
                }
                DelayBadge(segment.delay)
            }
            segment.direction?.let {
                Text(
                    tr("Richtung %1\$s", it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            platformName(segment.departurePlatform,segment.lineType)?.let {
                Text(platformLine(tr("Abfahrt: %1\$s", PLATFORM_MARK), it), Modifier.testTag("departure-platform"),
                    style = MaterialTheme.typography.labelLarge)
            }
            if (segment.stops.size > 2)
                TextButton(
                    { stopsExpanded = !stopsExpanded },
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("intermediate-stops-${segment.id}")
                            .semantics {
                                stateDescription =
                                    if (stopsExpanded) tr("Ausgeklappt") else tr("Eingeklappt")
                            },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    FittedText(
                        if (stopsExpanded) tr("Zwischenhalte ausblenden")
                        else tr("%1\$s Zwischenhalte anzeigen", segment.stops.size - 2),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge.copy(lineHeight = 22.sp),
                    )
                    Spacer(Modifier.width(8.dp))
                    AppIcon(
                        if (stopsExpanded) R.drawable.ic_collapse else R.drawable.ic_expand,
                        null,
                        Modifier.size(20.dp),
                    )
                }
        }
    }
    CalmVisibility(
        stopsExpanded,
        enter = expandVertically(spring()) + fadeIn(),
        exit = shrinkVertically(spring()) + fadeOut(),
    ) {
        Column {
            segment.stops.drop(1).dropLast(1).forEach { stop ->
                TimelineRow(
                    color,
                    before = true,
                    after = true,
                    dotted = false,
                    node = 1,
                    time = (stop.departureTime ?: stop.arrivalTime)?.let(::clockTime),
                ) {
                    Text(stop.location.name, style = MaterialTheme.typography.bodyMedium)
                    platformName(stop.platform,segment.lineType)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (stop.cancelled)
                        Text(
                            tr("Halt entfällt"),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                }
            }
        }
    }
    if (!hideEnd) TimelineRow(
        color,
        before = !segment.isWalk,
        after = after,
        dotted = false,
        node = 2,
        time = clockTime(segment.endTime),
        planned = segment.plannedEndTime.takeIf { it != segment.endTime }?.let(::clockTime),
    ) {
        FittedText(segment.destination.name, style = MaterialTheme.typography.titleSmall)
        platformName(segment.arrivalPlatform,segment.lineType)?.let {
            Text(
                platformLine(tr("Ankunft: %1\$s", PLATFORM_MARK), it),
                Modifier.testTag("arrival-platform"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!segment.isWalk)
            Text(
                tr("%1\$s Min. Fahrt", segment.durationMinutes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        // A walk's own notices (rare) stay with its end.
        if (segment.isWalk && segment.notices.isNotEmpty()) SegmentNotices(segment.notices)
    }
    }
    if(segment.isWalk && originHeight>0) {
        val timeWidth=if(density.fontScale>=1.5f) 0.dp else (50*density.fontScale).dp
        val height=(with(density) {originHeight.toDp()}-16.dp).coerceAtLeast(1.dp)
        WalkingFootprintTrack(color,Modifier.offset(x=timeWidth,y=29.dp).width(52.dp).height(height)
            .testTag("route-walk-footprints-${segment.id}"),trackKey="detail:${segment.id}",
            glyphScale=settings.footprints.normalized().barScale,straight=true)
    }
    }

}

/** The rail mark of a recognised notice (an exclamation mark in the theme's accent). */
private const val NODE_NOTICE = 3

/** Where the platform goes in a translated "Abfahrt: %1$s" line. */
private const val PLATFORM_MARK = "\uE000"

/** "Abfahrt: Gleis 3" with the platform in the theme's accent colour. */
@Composable private fun platformLine(template: String, platform: String): androidx.compose.ui.text.AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    return androidx.compose.ui.text.buildAnnotatedString {
        val at = template.indexOf(PLATFORM_MARK)
        if (at < 0) { append(template.replace(PLATFORM_MARK, platform)); return@buildAnnotatedString }
        append(template.substring(0, at))
        withStyle(androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(platform) }
        append(template.substring(at + PLATFORM_MARK.length))
    }
}

/** node: 0 = connector, 1 = intermediate stop, 2 = boarding/alighting, 3 = recognised notice ([nodeY]: its centre). */
@Composable
private fun TimelineRow(
    color: Color,
    before: Boolean,
    after: Boolean,
    dotted: Boolean,
    node: Int,
    time: String?,
    planned: String? = null,
    modifier:Modifier=Modifier,
    nodeY: androidx.compose.ui.unit.Dp = 21.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val stackedTime = LocalDensity.current.fontScale >= 1.5f
    val timeWidth = if (stackedTime) 0.dp else (50 * LocalDensity.current.fontScale).dp
    Row(
        modifier.fillMaxWidth().drawBehind {
            val x =
                if (layoutDirection == LayoutDirection.Ltr) (timeWidth + 26.dp).toPx()
                else size.width - (timeWidth + 26.dp).toPx()
            val center = nodeY.toPx()
            // The notice mark cuts into the rail itself, so rail and mark share one layer.
            val layered = node == NODE_NOTICE
            if (layered) drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), androidx.compose.ui.graphics.Paint())
            if(dotted) {
                val start=if(before)0f else center
                val end=if(after)size.height else center
                val dots=uniformTimelineDots((end-start).coerceAtLeast(0f),1.5.dp.toPx(),8.dp.toPx())
                dots.centers.forEach {y->drawCircle(color,dots.radius,Offset(x,start+y))}
            } else {
            if (before)
                drawLine(color, Offset(x, 0f), Offset(x, center), 3.dp.toPx())
            if (after)
                drawLine(
                    color,
                    Offset(x, center),
                    Offset(x, size.height),
                    3.dp.toPx(),
                )
            }
            if (node == NODE_NOTICE) {
                // A disc in the rail's own colour; the exclamation mark is cut out of it and a
                // thin transparent ring parts the rail around it, so the disc reads as one.
                drawCircle(Color.Black, 12.5.dp.toPx(), Offset(x, center), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                drawCircle(color, 9.5.dp.toPx(), Offset(x, center))
                val w = 3.dp.toPx()
                drawRoundRect(Color.Black, Offset(x - w / 2, center - 6.5.dp.toPx()), androidx.compose.ui.geometry.Size(w, 8.dp.toPx()),
                    androidx.compose.ui.geometry.CornerRadius(w / 2), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                drawCircle(Color.Black, 1.9.dp.toPx(), Offset(x, center + 5.dp.toPx()), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                drawContext.canvas.restore()
            } else if (node > 0) {
                drawCircle(color, (if (node == 2) 6 else 3).dp.toPx(), Offset(x, center))
                if (node == 2) drawCircle(surface, 3.dp.toPx(), Offset(x, center))
            }
        },
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (!stackedTime)
            Column(Modifier.width(timeWidth).padding(top = 12.dp)) {
                time?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                planned?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        Spacer(Modifier.width(52.dp))
        Column(
            Modifier.weight(1f).padding(top = 10.dp, bottom = if (node == 1) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (stackedTime && time != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        time,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    planned?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun SegmentNotices(notices: List<String>) {
    var expanded by rememberSaveable(notices) { mutableStateOf(false) }
    val rows = remember(notices) { notices.distinct() }
    TextButton({ expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
        AppIcon(R.drawable.ic_external, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (expanded) tr("Hinweise schließen") else tr("%1\$s Hinweise", rows.size))
    }
    CalmVisibility(
        expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


/** A walk ends at the boarding stop of the next ride at most two minutes before it departs. */
internal fun walkMeetsRide(walk: TripSegment, ride: TripSegment): Boolean {
    if (!walk.isWalk || ride.isWalk) return false
    val gap = ride.startTime - walk.endTime
    if (gap !in 0..120_000) return false
    return planPlaceMatches(walk.destination, ride.origin) || isEnteredPlace(walk.destination, ride.origin) ||
        walk.destination.name.trim().equals(ride.origin.name.trim(), ignoreCase = true)
}

/** A walk leaves the stop where the ride before it arrived, at most two minutes later. */
internal fun rideMeetsWalk(ride: TripSegment, walk: TripSegment): Boolean {
    if (ride.isWalk || !walk.isWalk) return false
    val gap = walk.startTime - ride.endTime
    if (gap !in 0..120_000) return false
    return planPlaceMatches(ride.destination, walk.origin) || isEnteredPlace(ride.destination, walk.origin) ||
        ride.destination.name.trim().equals(walk.origin.name.trim(), ignoreCase = true)
}
