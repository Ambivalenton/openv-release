// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.rendering.iconResource
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.TimelineInk
import dev.ambivalenton.openv.ui.theme.timelineColorPalette
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * A continuous, colour-coded rail. Build 105: the rail is drawn once for the whole journey, from point
 * to point. The rows only report where their point sits; a line therefore ends exactly at the stop it
 * belongs to, whatever stands between two points (notices, a transfer, a walk and its button), and a
 * walk's footprints fill exactly the way between its two points.
 */
@Composable
fun ModernRouteTimeline(trip: Trip, modifier: Modifier = Modifier) {
    val settings = LocalAppSettings.current
    val display = remember(trip, settings.walkingSpeed, settings.ignoreWalking) {
        assembleTimeline(trip, settings.copy(timelineShowWalking = true)).trip
    }
    ModernRouteTimelineContent(display, modifier)
}

@Composable private fun ModernRouteTimelineContent(trip: Trip, modifier: Modifier) {
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
    val notices = LocalJourneyNotices.current
    val hits = remember(trip, notices.dismissed) { scanJourneyNotices(trip, notices.dismissed) }
    val recognised = remember(trip) { scanJourneyNotices(trip).map { it.text.trim() }.toSet() }
    val quiet = LocalAppSettings.current.reduceMotion
    val plan = remember(trip, recognised) { railPlan(trip, recognised) }
    // The rail draws itself in once per opened connection (not on a refresh of the same one).
    var revealed by rememberSaveable(trip.id) { mutableStateOf(false) }
    val rail = remember(trip.id) { TimelineRail(if (revealed || quiet) 1f else 0f) }
    LaunchedEffect(rail, quiet) {
        if (quiet) rail.reveal.snapTo(1f)
        else if (!revealed) rail.reveal.animateTo(1f, tween(TIMELINE_REVEAL_MILLIS, easing = RevealEasing))
        revealed = true
    }
    val palette = timelineColorPalette()
    val ink = trip.segments.map { palette[it.lineType] }
    Column(modifier.fillMaxWidth()) {
        JourneyNoticeCards(hits, Modifier.padding(bottom = 12.dp))
        val visitAtBoundary = remember(trip.waypointLegs, trip.waypointVisits) {
            var count = 0
            trip.waypointLegs.dropLast(1).mapIndexedNotNull { index, leg ->
                count += leg.segmentCount
                trip.waypointVisits.getOrNull(index)?.let { (count - 1) to (index to it) }
            }.toMap()
        }
        Box(Modifier.fillMaxWidth().onPlaced { rail.origin = it }.testTag("route-rail")) {
            RailCanvas(plan, rail, ink, Modifier.matchParentSize())
            Column {
                trip.segments.forEachIndexed { index, segment ->
                    val shape = plan.shapes[index]
                    RouteSegment(rail, index, segment, shape, ink[index])
                    if (!shape.joined && plan.shapes.getOrNull(index + 1)?.continues != true) trip.segments.getOrNull(index + 1)?.let { next ->
                        val gap = next.startTime - segment.endTime
                        if (gap > 0) {
                            val ongoing = now in segment.endTime until next.startTime
                            val minutes = ceil((if (ongoing) next.startTime - now else gap) / 60000.0).toInt()
                            TimelineRow(rail, point = null, time = null) {
                                visitAtBoundary[index]?.let { (number, visit) ->
                                    FittedText(tr("Zwischenstopp %1\$s · %2\$s", number + 1, visit.location.name),
                                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                RailChip(
                                    if (ongoing) tr("Noch %1\$s Min.", minutes) else if (visitAtBoundary[index] != null)
                                        tr("%1\$s Min. am Zwischenstopp", minutes) else tr("%1\$s Min. Umstieg", minutes),
                                    if (ongoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    if (ongoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (gap < 0)
                            TimelineRow(rail, point = null, time = null) {
                                RailChip(tr("Anschluss unsicher · %1\$s Min. Überschneidung", ceil(-gap / 60000.0).toInt()),
                                    MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                            }
                    }
                }
            }
            plan.links.forEach { link ->
                if (link.style == RailStyle.WALK) key(link.from, link.to) {
                    WalkRail(rail, plan, link, ink[link.segment].rail, trip.segments[link.segment].id)
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
private fun RouteSegment(rail: TimelineRail, index: Int, segment: TripSegment, shape: SegmentShape, ink: TimelineInk) {
    var stopsExpanded by rememberSaveable(segment.id) { mutableStateOf(false) }
    // Only a list the user opens springs in stop by stop; a restored one is simply there.
    var stopsOpenedHere by remember(segment.id) { mutableStateOf(false) }
    var navigationError by remember(segment.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val navigationMode = LocalWalkingNavigationMode.current
    val quiet = LocalAppSettings.current.reduceMotion
    // The notices of a ride stand before it, where they matter; a recognised one carries the
    // exclamation mark on the rail (on the way to the stop, never on the ride's own line).
    if (shape.notices) {
        TimelineRow(rail, point = if (shape.flaggedNotice) railNotice(index) else null, time = null,
            modifier = Modifier.testTag(if (shape.flaggedNotice) "notice-mark-${segment.id}" else "notices-${segment.id}"),
            nodeY = NOTICE_Y) {
            SegmentNotices(segment.notices)
        }
    }
    TimelineRow(
        rail,
        point = if (shape.continues) null else railStart(index),
        time = if (shape.continues) null else clockTime(segment.startTime),
        planned = if (shape.continues) null else segment.plannedStartTime.takeIf { it != segment.startTime }?.let(::clockTime),
    ) {
        if (!shape.continues) FittedText(
            segment.origin.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (segment.isWalk) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_walk, null, Modifier.size(18.dp))
                Text(
                    tr("%1\$s Min. Fußweg%2\$s", segment.durationMinutes, segment.distanceMeters?.let { " · $it m" }.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ThemedTonalButton(
                {
                    navigationError =
                        openWalkingNavigation(context, segment, navigationMode).exceptionOrNull()?.message
                },
                modifier = Modifier.pressScale(.94f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                AppIcon(R.drawable.ic_external, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
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
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = ink.container,
                    contentColor = ink.content,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
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
            platformName(segment.departurePlatform, segment.lineType)?.let {
                Text(platformLine(tr("Abfahrt: %1\$s", PLATFORM_MARK), it), Modifier.testTag("departure-platform"),
                    style = MaterialTheme.typography.labelLarge)
            }
            if (segment.stops.size > 2)
                RailToggle(
                    stopsExpanded,
                    if (stopsExpanded) tr("Zwischenhalte ausblenden") else tr("%1\$s Zwischenhalte anzeigen", segment.stops.size - 2),
                    { stopsOpenedHere = true; stopsExpanded = !stopsExpanded },
                    Modifier.testTag("intermediate-stops-${segment.id}"),
                )
        }
    }
    CalmVisibility(
        stopsExpanded,
        // Build 116: open from and close towards the top. Closing towards the bottom (the default) slid the
        // rows up out of the clip – and with them the stop points, which the rail draws outside the clip.
        enter = expandVertically(spring(dampingRatio = .86f, stiffness = 360f, visibilityThreshold = androidx.compose.ui.unit.IntSize.VisibilityThreshold),
            expandFrom = Alignment.Top) + fadeIn(tween(160)),
        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 520f, visibilityThreshold = androidx.compose.ui.unit.IntSize.VisibilityThreshold),
            shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
    ) {
        val closing = transition.targetState == androidx.compose.animation.EnterExitState.PostExit
        Column {
            segment.stops.forEachIndexed { k, stop ->
                if (k == 0 || k == segment.stops.lastIndex) return@forEachIndexed
                val key = railPass(index, k)
                // Stop by stop the points pop onto the line and their names glide in behind them.
                val appear = remember(key) { Animatable(if (quiet || !stopsOpenedHere) 1f else 0f) }
                LaunchedEffect(appear) {
                    if (appear.value < 1f) {
                        delay((k - 1).coerceAtMost(10) * PASS_STAGGER_MILLIS)
                        appear.animateTo(1f, spring(dampingRatio = .5f, stiffness = 520f))
                    }
                }
                // Closing: the points shrink away on the spot at once, instead of riding along with the rows.
                LaunchedEffect(closing) {
                    if (closing) appear.animateTo(0f, if (quiet) snap() else spring(dampingRatio = 1f, stiffness = 1400f))
                }
                DisposableEffect(rail, key) {
                    rail.appear[key] = appear
                    onDispose { rail.appear.remove(key) }
                }
                TimelineRow(
                    rail,
                    point = key,
                    time = (stop.departureTime ?: stop.arrivalTime)?.let(::clockTime),
                    compact = true,
                    appear = appear,
                ) {
                    Text(stop.location.name, style = MaterialTheme.typography.bodyMedium)
                    platformName(stop.platform, segment.lineType)?.let {
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
    if (!shape.joined) TimelineRow(
        rail,
        point = railEnd(index),
        time = clockTime(segment.endTime),
        planned = segment.plannedEndTime.takeIf { it != segment.endTime }?.let(::clockTime),
    ) {
        FittedText(segment.destination.name, style = MaterialTheme.typography.titleSmall)
        platformName(segment.arrivalPlatform, segment.lineType)?.let {
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

/** Where the platform goes in a translated "Abfahrt: %1$s" line. */
private const val PLATFORM_MARK = ""

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

// ---------------------------------------------------------------------------------------------------
// The plan: which points the rail has and what runs between two of them. Pure, shared with the rows.
// ---------------------------------------------------------------------------------------------------

/** What runs between two points of the rail. */
internal enum class RailStyle { RIDE, WALK, WAIT, CONFLICT }

/** A point on the rail: boarding/alighting, first/last point of the journey, intermediate stop, recognised notice. */
internal enum class RailMark(val radius: Dp) { STOP(6.5.dp), TERMINUS(8.dp), PASS(4.25.dp), NOTICE(12.5.dp) }

@Immutable internal data class RailNodeSpec(val key: String, val mark: RailMark, val segment: Int, val cancelled: Boolean = false)
@Immutable internal data class RailLink(val from: String, val to: String, val style: RailStyle, val segment: Int)

/** The rows a segment shows: [joined] (a walk that ends at the boarding stop of the next ride), [continues] (a walk that starts where a ride just arrived). */
@Immutable internal data class SegmentShape(val joined: Boolean, val continues: Boolean, val notices: Boolean, val flaggedNotice: Boolean)

@Immutable internal class RailPlan(val nodes: List<RailNodeSpec>, val links: List<RailLink>, val shapes: List<SegmentShape>) {
    private val byKey = nodes.associateBy { it.key }
    fun mark(key: String): RailMark? = byKey[key]?.mark
}

internal fun railStart(segment: Int) = "start:$segment"
internal fun railEnd(segment: Int) = "end:$segment"
internal fun railPass(segment: Int, stop: Int) = "pass:$segment:$stop"
internal fun railNotice(segment: Int) = "notice:$segment"

/**
 * Points and links in rail order. A ride's line runs from its boarding to its alighting point; a walk's
 * footprints from where it starts to where it ends (the ride's points when it joins or continues one);
 * everything between two segments (a transfer, a wait, a recognised notice of the next ride) is a dotted link.
 */
internal fun railPlan(trip: Trip, recognised: Set<String>): RailPlan {
    val segments = trip.segments
    val shapes = segments.mapIndexed { i, segment ->
        val notices = !segment.isWalk && segment.notices.isNotEmpty()
        SegmentShape(
            joined = segments.getOrNull(i + 1)?.let { walkMeetsRide(segment, it) } == true,
            continues = segments.getOrNull(i - 1)?.let { rideMeetsWalk(it, segment) } == true,
            notices = notices,
            flaggedNotice = notices && segment.notices.any { it.trim() in recognised },
        )
    }
    val nodes = mutableListOf<RailNodeSpec>()
    val links = mutableListOf<RailLink>()
    var last: String? = null
    var pending: RailStyle? = null
    var pendingSegment = -1
    fun point(key: String, mark: RailMark, segment: Int) {
        nodes += RailNodeSpec(key, mark, segment)
        val from = last
        if (from != null && pending != null) links += RailLink(from, key, pending!!, pendingSegment)
        last = key
    }
    segments.forEachIndexed { i, segment ->
        val shape = shapes[i]
        if (i > 0 && !shape.continues && !shapes[i - 1].joined) {
            // Between two segments: a transfer or wait, dotted (red when the times overlap).
            pending = if (segment.startTime < segments[i - 1].endTime) RailStyle.CONFLICT else RailStyle.WAIT
            pendingSegment = i
        }
        if (shape.flaggedNotice) point(railNotice(i), RailMark.NOTICE, i)
        val style = if (segment.isWalk) RailStyle.WALK else RailStyle.RIDE
        if (!shape.continues) point(railStart(i), RailMark.STOP, i)
        pending = style; pendingSegment = i
        if (!segment.isWalk) segment.stops.forEachIndexed { k, stop ->
            if (k != 0 && k != segment.stops.lastIndex) nodes += RailNodeSpec(railPass(i, k), RailMark.PASS, i, stop.cancelled)
        }
        if (!shape.joined) point(railEnd(i), RailMark.STOP, i)
    }
    // The journey's very first and last point stand out a little: where it starts, where it arrives.
    val ends = nodes.indices.filter { nodes[it].mark == RailMark.STOP }
    listOfNotNull(ends.firstOrNull(), ends.lastOrNull()).distinct().forEach { nodes[it] = nodes[it].copy(mark = RailMark.TERMINUS) }
    return RailPlan(nodes, links, shapes)
}

// ---------------------------------------------------------------------------------------------------
// The rail itself: where the points are (reported by the rows) and how it is drawn.
// ---------------------------------------------------------------------------------------------------

/** A point's centre and its row's bounds, in the rail's coordinates. */
@Immutable internal data class RailPoint(val y: Float, val top: Float, val bottom: Float)

@Stable internal class TimelineRail(initialReveal: Float) {
    /** The box the rail is drawn in; rows report their points relative to it. */
    var origin: LayoutCoordinates? = null
    val points = mutableStateMapOf<String, RailPoint>()
    /** 0 → 1: the rail draws itself in from the top. */
    val reveal = Animatable(initialReveal)
    /** Intermediate stops that spring onto the line when their list opens. */
    val appear = HashMap<String, Animatable<Float, AnimationVector1D>>()

    fun topOf(row: LayoutCoordinates): Float? {
        val box = origin ?: return null
        if (!box.isAttached || !row.isAttached) return null
        return runCatching { box.localPositionOf(row, Offset.Zero).y }.getOrNull()
    }
    fun report(key: String, row: LayoutCoordinates, top: Float, centre: Float) {
        val point = RailPoint(top + centre, top, top + row.size.height)
        if (points[key] != point) points[key] = point
    }
    /** How far down the rail is drawn (px); infinite once it is complete. */
    fun limit(): Float {
        val r = reveal.value
        if (r >= 1f) return Float.POSITIVE_INFINITY
        return r * (origin?.size?.height ?: 0)
    }
    /** 0 → 1 as the drawn rail reaches a row starting at [top]. */
    fun shown(top: Float, lead: Float, span: Float): Float {
        val limit = limit()
        if (limit == Float.POSITIVE_INFINITY) return 1f
        if (top.isNaN()) return 0f
        return ((limit - top + lead) / span).coerceIn(0f, 1f)
    }
    /** A point pops (with a small overshoot) as the drawn rail reaches it. */
    fun pop(y: Float, lead: Float, span: Float): Float {
        val limit = limit()
        if (limit == Float.POSITIVE_INFINITY) return 1f
        return backOut(((limit - y + lead) / span).coerceIn(0f, 1f))
    }
    /** The free way between two points (px), outside their marks and a small gap. */
    fun span(plan: RailPlan, link: RailLink, density: androidx.compose.ui.unit.Density): Pair<Float, Float>? {
        val a = points[link.from] ?: return null
        val b = points[link.to] ?: return null
        with(density) {
            val top = a.y + (plan.mark(link.from)?.radius ?: 0.dp).toPx() + RAIL_GAP.toPx()
            val bottom = b.y - (plan.mark(link.to)?.radius ?: 0.dp).toPx() - RAIL_GAP.toPx()
            return if (bottom > top) top to bottom else null
        }
    }
}

/** Overshoot easing for the points' pop (a spring's first swing, without its tail). */
private fun backOut(t: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f; val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

/** Draws in briskly, then settles; gentler at the start than Material's emphasized curve, so the top rows are seen arriving too. */
private val RevealEasing = CubicBezierEasing(.3f, .55f, .2f, 1f)
private const val TIMELINE_REVEAL_MILLIS = 760
private const val PASS_STAGGER_MILLIS = 34L
/** Width of the rail column between the times and the texts; the rail runs through its middle. */
private val RAIL_COLUMN = 52.dp
private val RAIL_WIDTH = 4.dp
/** Air between a point's mark and the dots or footprints that lead to it. */
private val RAIL_GAP = 4.dp
private val NODE_Y = 21.dp
/** A ride's wash starts this far before the rail and has these corners. */
private val LEG_INSET = 20.dp
private val LEG_CORNER = 20.dp
private val NOTICE_Y = 30.dp

@Composable private fun timeColumnWidth(): Dp {
    val density = LocalDensity.current
    return if (density.fontScale >= 1.5f) 0.dp else (50 * density.fontScale).dp
}

@Composable private fun RailCanvas(plan: RailPlan, rail: TimelineRail, ink: List<TimelineInk>, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val wait = scheme.outline.copy(alpha = .55f)
    val conflict = scheme.error
    val centre = timeColumnWidth() + RAIL_COLUMN / 2
    val dark = scheme.surface.luminance() < .4f
    // Each ride is one calm block: a faint wash of its line colour behind its stops and texts.
    Spacer(modifier.drawBehind {
        val left = if (layoutDirection == LayoutDirection.Ltr) centre.toPx() - LEG_INSET.toPx() else 0f
        val right = if (layoutDirection == LayoutDirection.Ltr) size.width else size.width - centre.toPx() + LEG_INSET.toPx()
        val limit = rail.limit()
        for (link in plan.links) {
            if (link.style != RailStyle.RIDE) continue
            val a = rail.points[link.from] ?: continue
            val b = rail.points[link.to] ?: continue
            val top = a.top + 2.dp.toPx()
            val bottom = minOf(b.bottom - 8.dp.toPx(), limit + 24.dp.toPx())
            if (bottom <= top) continue
            val tint = ink[link.segment].rail
            // Bright lines (yellow, light green) get less wash, so no block turns muddy on a dark card.
            val wash = (if (dark) .14f else .085f) * (1f - .45f * tint.luminance())
            drawRoundRect(tint.copy(alpha = wash), Offset(left, top), Size(right - left, bottom - top),
                CornerRadius(LEG_CORNER.toPx()))
        }
    })
    // Offscreen, so the holes of the marks show what lies below (the card or a ride's wash).
    Spacer(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawBehind {
        val x = if (layoutDirection == LayoutDirection.Ltr) centre.toPx() else size.width - centre.toPx()
        val limit = rail.limit()
        for (link in plan.links) {
            val a = rail.points[link.from] ?: continue
            val b = rail.points[link.to] ?: continue
            if (b.y <= a.y) continue
            when (link.style) {
                RailStyle.RIDE -> {
                    val end = minOf(b.y, limit)
                    if (end > a.y) drawLine(ink[link.segment].rail, Offset(x, a.y), Offset(x, end), RAIL_WIDTH.toPx(), StrokeCap.Round)
                }
                RailStyle.WAIT, RailStyle.CONFLICT -> {
                    val (top, bottom) = rail.span(plan, link, this) ?: continue
                    val dots = uniformTimelineDots(bottom - top, 1.6.dp.toPx(), 8.dp.toPx())
                    val color = if (link.style == RailStyle.CONFLICT) conflict else wait
                    dots.centers.forEach { y -> if (top + y <= limit) drawCircle(color, dots.radius, Offset(x, top + y)) }
                }
                RailStyle.WALK -> Unit // footprints: [WalkRail]
            }
        }
        for (node in plan.nodes) {
            val p = rail.points[node.key] ?: continue
            val scale = rail.pop(p.y, 10.dp.toPx(), 46.dp.toPx()) * (rail.appear[node.key]?.value ?: 1f)
            if (scale <= .01f) continue
            drawRailMark(node.mark, if (node.cancelled) conflict else ink[node.segment].rail, Offset(x, p.y), scale)
        }
    })
}

/** The marks are cut out of the rail with [BlendMode.Clear]: their holes show the card, not a guessed colour. */
private fun DrawScope.drawRailMark(mark: RailMark, color: Color, at: Offset, scale: Float) {
    fun r(dp: Float) = dp.dp.toPx() * scale
    val clear = BlendMode.Clear
    when (mark) {
        RailMark.STOP -> {
            drawCircle(color, r(6.5f), at)
            drawCircle(Color.Black, r(3.25f), at, blendMode = clear)
        }
        RailMark.TERMINUS -> {
            drawCircle(color, r(8f), at)
            drawCircle(Color.Black, r(5.25f), at, blendMode = clear)
            drawCircle(color, r(2.75f), at)
        }
        RailMark.PASS -> {
            drawCircle(color, r(4.25f), at)
            drawCircle(Color.Black, r(1.75f), at, blendMode = clear)
        }
        RailMark.NOTICE -> {
            // A disc in the rail's own colour; the exclamation mark is cut out of it and a thin
            // transparent ring parts the rail around it, so the disc reads as one.
            drawCircle(Color.Black, r(12.5f), at, blendMode = clear)
            drawCircle(color, r(9.5f), at)
            val w = r(3f)
            drawRoundRect(Color.Black, Offset(at.x - w / 2, at.y - r(6.5f)), Size(w, r(8f)), CornerRadius(w / 2), blendMode = clear)
            drawCircle(Color.Black, r(1.9f), Offset(at.x, at.y + r(5f)), blendMode = clear)
        }
    }
}

/** A walk's footprints, exactly between its two points; drawn in as the rail reaches them. */
@Composable private fun WalkRail(rail: TimelineRail, plan: RailPlan, link: RailLink, color: Color, segmentId: String) {
    val settings = LocalAppSettings.current
    val inset = timeColumnWidth()
    Box(Modifier
        .layout { measurable, _ ->
            val span = rail.span(plan, link, this)
            val width = RAIL_COLUMN.roundToPx()
            val height = span?.let { (it.second - it.first).roundToInt() } ?: 0
            val placeable = measurable.measure(Constraints.fixed(width, height.coerceAtLeast(0)))
            layout(width, placeable.height) { placeable.placeRelative(inset.roundToPx(), span?.first?.roundToInt() ?: 0) }
        }
        .drawWithContent {
            val limit = rail.limit()
            if (limit == Float.POSITIVE_INFINITY) drawContent()
            else {
                val top = rail.span(plan, link, this)?.first ?: return@drawWithContent
                clipRect(bottom = limit - top) { this@drawWithContent.drawContent() }
            }
        }) {
        WalkingFootprintTrack(color, Modifier.fillMaxSize().testTag("route-walk-footprints-$segmentId"),
            trackKey = "detail:$segmentId:${link.from}", glyphScale = settings.footprints.normalized().barScale, straight = true)
    }
}

/**
 * One row of the timeline: time, the rail column and the texts. A row with a [point] reports where that
 * point sits ([nodeY] below its top); the rail itself is drawn by [RailCanvas]. While the rail draws itself
 * in, the row glides up into place as the line reaches it; [appear] lets an intermediate stop spring in.
 */
@Composable
private fun TimelineRow(
    rail: TimelineRail,
    point: String?,
    time: String?,
    planned: String? = null,
    modifier: Modifier = Modifier,
    nodeY: Dp = NODE_Y,
    compact: Boolean = false,
    appear: Animatable<Float, AnimationVector1D>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val stackedTime = density.fontScale >= 1.5f
    val timeWidth = timeColumnWidth()
    val top = remember { floatArrayOf(Float.NaN) }
    if (point != null) DisposableEffect(rail, point) { onDispose { rail.points.remove(point) } }
    val centre = with(density) { nodeY.toPx() }
    val ltr = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Ltr
    Row(
        modifier.fillMaxWidth()
            .onGloballyPositioned { row ->
                val at = rail.topOf(row) ?: return@onGloballyPositioned
                top[0] = at
                if (point != null) rail.report(point, row, at, centre)
            }
            .graphicsLayer {
                val shown = rail.shown(top[0], 28.dp.toPx(), 72.dp.toPx())
                val a = appear?.value ?: 1f
                alpha = shown * a.coerceIn(0f, 1f)
                translationY = (1f - shown) * 14.dp.toPx()
                if (appear != null) translationX = (1f - a) * -12.dp.toPx() * if (ltr) 1f else -1f
            },
    ) {
        if (!stackedTime)
            Column(Modifier.width(timeWidth).padding(top = 12.dp)) {
                time?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (compact) FontWeight.Medium else FontWeight.Bold,
                        color = if (compact) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                        maxLines = 1,
                    )
                }
                planned?.let {
                    // The timetable time the forecast replaces.
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                        maxLines = 1,
                    )
                }
            }
        Spacer(Modifier.width(RAIL_COLUMN))
        Column(
            Modifier.weight(1f).padding(top = 10.dp, bottom = if (compact) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (stackedTime && time != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                }
            }
            content()
        }
    }
}

/** A small tonal label on the rail's way: a transfer's minutes or an uncertain connection. */
@Composable private fun RailChip(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = dev.ambivalenton.openv.ui.theme.appCorner(12.dp)) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(R.drawable.ic_clock, null, Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * "6 Zwischenhalte anzeigen" / "10 Hinweise": the label slides to its other wording and one chevron turns
 * with a spring. The touch area bleeds 8 dp to the start, so the text lines up with the texts above it.
 */
@Composable private fun RailToggle(open: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val quiet = LocalAppSettings.current.reduceMotion
    val turn by animateFloatAsState(if (open) 180f else 0f, if (quiet) snap() else spring(dampingRatio = .45f, stiffness = 420f),
        label = "rail-toggle-chevron")
    TextButton(
        onClick,
        modifier = modifier
            .layout { measurable, constraints ->
                val bleed = 8.dp.roundToPx()
                val wide = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.maxWidth + bleed, maxWidth = constraints.maxWidth + bleed)
                    else constraints
                val placeable = measurable.measure(wide)
                layout((placeable.width - bleed).coerceAtLeast(0), placeable.height) { placeable.placeRelative(-bleed, 0) }
            }
            .heightIn(min = 48.dp)
            .semantics { stateDescription = if (open) tr("Ausgeklappt") else tr("Eingeklappt") },
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
    ) {
        AnimatedContent(
            label,
            Modifier.weight(1f),
            transitionSpec = {
                if (quiet) ContentTransform(EnterTransition.None, ExitTransition.None)
                else ContentTransform(
                    fadeIn(tween(180, delayMillis = 60)) + slideInVertically(spring(dampingRatio = .7f, stiffness = 500f)) { it / 2 },
                    fadeOut(tween(100)) + slideOutVertically(tween(140)) { -it / 2 },
                    sizeTransform = SizeTransform(clip = false),
                )
            },
            contentAlignment = Alignment.CenterStart,
            label = "rail-toggle-label",
        ) { text ->
            FittedText(text, style = MaterialTheme.typography.labelLarge.copy(lineHeight = 22.sp))
        }
        Spacer(Modifier.width(8.dp))
        AppIcon(R.drawable.ic_expand, null, Modifier.size(20.dp).graphicsLayer { rotationZ = turn })
    }
}

@Composable
private fun SegmentNotices(notices: List<String>) {
    var expanded by rememberSaveable(notices) { mutableStateOf(false) }
    val rows = remember(notices) { notices.distinct() }
    RailToggle(expanded, if (expanded) tr("Hinweise schließen") else if (rows.size == 1) tr("1 Hinweis") else tr("%1\$s Hinweise", rows.size),
        { expanded = !expanded })
    CalmVisibility(
        expanded,
        enter = expandVertically(spring(dampingRatio = .86f, stiffness = 360f, visibilityThreshold = androidx.compose.ui.unit.IntSize.VisibilityThreshold)) +
            fadeIn(tween(180)),
        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 520f, visibilityThreshold = androidx.compose.ui.unit.IntSize.VisibilityThreshold)) +
            fadeOut(tween(120)),
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
