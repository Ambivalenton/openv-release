// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appCorner

/** The step in focus as a short verb. */
internal fun BriefStepKind.verb() = when (this) {
    BriefStepKind.BOARD -> tr("Einsteigen")
    BriefStepKind.ALIGHT -> tr("Aussteigen")
    BriefStepKind.WALK -> tr("Zu Fuß zu")
    BriefStepKind.ARRIVE -> tr("Ankunft")
}

/** Build 125 · the situations of the card, each with its own calm layout. */
internal enum class BriefPhase { BOARD, WALK, RIDE, CHANGE_SOON, ARRIVE, ARRIVE_SOON }

internal fun briefPhase(brief: PinnedJourneyBrief): BriefPhase = when (brief.kind) {
    BriefStepKind.BOARD -> BriefPhase.BOARD
    BriefStepKind.WALK -> BriefPhase.WALK
    BriefStepKind.ALIGHT -> if (brief.stage == BriefStage.SOON && brief.next != null) BriefPhase.CHANGE_SOON else BriefPhase.RIDE
    BriefStepKind.ARRIVE -> if (brief.stage == BriefStage.SOON) BriefPhase.ARRIVE_SOON else BriefPhase.ARRIVE
}

/**
 * Build 116/125 · the running journey, only what matters right now. On a ride: where and when it ends – the
 * line you sit in and the arrival platform are not repeated; the onward ride waits quietly in one line. Shortly
 * before a change (two stops or four minutes) the onward ride comes to the front with its platform, the
 * alighting shrinks to one line. Waiting to board: line, direction and platform, the minutes left. The times
 * stand in a column of their own, so a delay or a "ca." never pushes the place onto another line; a change of
 * situation cross-fades and the card's height glides. [expanded]: the overview-filling page, larger and airier.
 */
@Composable internal fun PinnedJourneyBriefView(brief: PinnedJourneyBrief, accent: Color, arrivalColor: Color, expanded: Boolean,
    now: Long = System.currentTimeMillis()) {
    val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    val quiet = LocalAppSettings.current.reduceMotion
    val gap = if (expanded) 16.dp else 8.dp
    val phase = briefPhase(brief)
    Column(Modifier.fillMaxWidth().animateContentSize(if (quiet) snap() else spring(dampingRatio = .9f, stiffness = 260f)),
        verticalArrangement = Arrangement.spacedBy(gap)) {
        brief.alert?.let { alert ->
            Surface(color = scheme.errorContainer, contentColor = scheme.onErrorContainer, shape = appCorner(12.dp)) {
                FittedText(alert, Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("card-alert"),
                    style = if (expanded) t.titleSmall else t.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        AnimatedContent(phase, transitionSpec = {
            if (quiet) fadeIn(snap()) togetherWith fadeOut(snap())
            else fadeIn(spring(dampingRatio = .9f, stiffness = 380f)) togetherWith fadeOut(spring(dampingRatio = 1f, stiffness = 700f))
        }, label = "brief-phase") { p ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
                when (p) {
                    BriefPhase.BOARD -> BoardStep(brief, accent, expanded, now)
                    BriefPhase.WALK -> {
                        StepHeader(BriefStepKind.WALK.verb(), brief.place, null, brief, accent, expanded, departure = true, now = now)
                        brief.next?.let { OnwardBox(it, accent, expanded) }
                    }
                    BriefPhase.RIDE -> {
                        StepHeader(tr("Ankunft"), brief.place, stopsLeft(brief), brief, accent, expanded)
                        brief.next?.let { OnwardLine(it, expanded) }
                    }
                    BriefPhase.CHANGE_SOON -> {
                        AlightLine(brief, expanded)
                        brief.next?.let { OnwardBox(it, accent, expanded) }
                    }
                    BriefPhase.ARRIVE, BriefPhase.ARRIVE_SOON -> StepHeader(
                        if (p == BriefPhase.ARRIVE_SOON) tr("Gleich aussteigen") else tr("Ankunft"), brief.place,
                        listOfNotNull(stopsLeft(brief), brief.finalWalkMinutes?.let { tr("dann %1\$s Min. zu Fuß", it) }).joinToString(" · ").ifEmpty { null },
                        brief, accent, expanded, labelColor = if (p == BriefPhase.ARRIVE_SOON) accent else null)
                }
                // The arrival at the destination, unless the step is the arrival itself; in red when it moved later.
                if (brief.kind != BriefStepKind.ARRIVE) FinalArrival(brief, arrivalColor, expanded)
            }
        }
    }
}

private fun stopsLeft(brief: PinnedJourneyBrief): String? = when (brief.stopsLeft) {
    0 -> null; 1 -> tr("noch ein Halt"); else -> tr("noch %1\$s Halte", brief.stopsLeft)
}

/** Label, place and a quiet detail on the left; the time in its own column on the right. */
@Composable private fun StepHeader(label: String, place: String, detail: String?, brief: PinnedJourneyBrief, accent: Color, expanded: Boolean,
    departure: Boolean = false, now: Long = 0L, labelColor: Color? = null) {
    val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FittedText(label, Modifier.testTag("card-action"), style = if (expanded) t.titleMedium else t.labelLarge,
                color = labelColor ?: scheme.onSurfaceVariant, fontWeight = if (labelColor != null) FontWeight.SemiBold else null, maxLines = 1)
            FittedText(place, Modifier.testTag("card-next-place"), style = if (expanded) t.headlineMedium else t.titleLarge,
                fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (detail != null) FittedText(detail, Modifier.testTag("card-step-detail"), style = if (expanded) t.bodyLarge else t.bodyMedium,
                color = scheme.onSurfaceVariant, maxLines = 2)
        }
        TimeColumn(brief, accent, expanded, if (departure) tr("ab") else tr("an"), if (departure) countdown(brief.time, now) else null)
    }
}

/**
 * The time of the step: a small caption above ("an", "ca. an", "ab"), the time, and one line below that is
 * always there (delay, minutes left or empty) – nothing in this column changes the card's layout.
 */
@Composable private fun TimeColumn(brief: PinnedJourneyBrief, accent: Color, expanded: Boolean, caption: String, idle: String?) {
    val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.End) {
        Text((if (brief.approximate) tr("ca.") + " " else "") + caption, style = if (expanded) t.titleMedium else t.labelLarge,
            color = scheme.onSurfaceVariant, maxLines = 1)
        Text(clockTime(brief.time), Modifier.testTag("card-time"),
            style = (if (expanded) t.displaySmall else t.headlineMedium).copy(fontWeight = FontWeight.Bold), color = accent, maxLines = 1)
        val late = brief.delayMinutes > 0
        Text(if (late) tr("+%1\$s Min.", brief.delayMinutes) else idle.orEmpty(), Modifier.testTag(if (late) "card-delay" else "card-time-note"),
            style = t.labelLarge, color = if (late) scheme.error else scheme.onSurfaceVariant, maxLines = 1)
    }
}

/** "in 3 Min." until the departure, "jetzt" at it. */
private fun countdown(time: Long, now: Long): String? {
    if (now <= 0L) return null
    val minutes = ((time - now) / 60_000L).toInt()
    return when { minutes > 90 -> null; minutes <= 0 -> tr("jetzt"); else -> tr("in %1\$s Min.", minutes) }
}

/** Waiting to board: the line and its direction, the platform to find, the minutes left. */
@Composable private fun BoardStep(brief: PinnedJourneyBrief, accent: Color, expanded: Boolean, now: Long) {
    val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FittedText(tr("Einsteigen in %1\$s", brief.place), Modifier.testTag("card-action"), style = if (expanded) t.titleMedium else t.labelLarge,
                color = scheme.onSurfaceVariant, maxLines = 2)
            FittedText(brief.direction?.let { tr("%1\$s Richtung %2\$s", brief.line, it) } ?: brief.line, Modifier.testTag("card-next-place"),
                style = if (expanded) t.headlineSmall else t.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
            // The platform is the one thing to find – highlighted, never cut.
            brief.platform?.let { FittedText(it, Modifier.testTag("card-platform"), style = if (expanded) t.titleLarge else t.titleMedium,
                fontWeight = FontWeight.Bold, color = accent, maxLines = 1) }
        }
        TimeColumn(brief, accent, expanded, tr("ab"), countdown(brief.time, now))
    }
}

/** Shortly before a change: the alighting in one line, the platform of arrival and stops left quietly below. */
@Composable private fun AlightLine(brief: PinnedJourneyBrief, expanded: Boolean) {
    val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FittedText(tr("Aussteigen in %1\$s", brief.place), Modifier.weight(1f).padding(end = 10.dp).testTag("card-next-place"),
                style = if (expanded) t.titleMedium else t.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text((if (brief.approximate) tr("ca.") + " " else "") + clockTime(brief.time), Modifier.testTag("card-time"),
                style = (if (expanded) t.titleMedium else t.titleSmall).copy(fontWeight = FontWeight.SemiBold), maxLines = 1,
                color = if (brief.delayMinutes > 0) scheme.error else scheme.onSurface)
        }
        val detail = listOfNotNull(brief.platform?.let { tr("Ankunft %1\$s", it) }, stopsLeft(brief))
        if (detail.isNotEmpty()) FittedText(detail.joinToString(" · "), Modifier.testTag("card-step-detail"), style = t.bodyMedium,
            color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

private fun transferNote(next: BriefBoarding, error: Color, quietInk: Color): Pair<String, Color>? = when (next.status) {
    TransferStatus.TIGHT -> tr("Umstieg knapp") to error
    TransferStatus.AT_RISK -> tr("Anschluss gefährdet") to error
    TransferStatus.BROKEN -> tr("Anschluss nicht erreichbar") to error
    else -> next.waitMinutes?.takeIf { it > 0 }?.let { tr("%1\$s Min. Umstieg", it) to quietInk }
}

/** On the ride, far from the change: the onward ride in one quiet line (a risk still speaks up in red). */
@Composable private fun OnwardLine(next: BriefBoarding, expanded: Boolean) {
    val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().testTag("card-next"), verticalAlignment = Alignment.CenterVertically) {
        FittedText(tr("Weiter mit %1\$s um %2\$s", next.line, clockTime(next.time)), Modifier.weight(1f).padding(end = 10.dp),
            style = if (expanded) t.titleSmall else t.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
        transferNote(next, scheme.error, scheme.onSurfaceVariant)?.let { (text, color) ->
            Text(text, Modifier.testTag("card-transfer"), style = t.labelMedium, color = color, maxLines = 1)
        }
    }
}

/** The onward ride in front: line, direction, the platform highlighted, its departure and the time to change. */
@Composable private fun OnwardBox(next: BriefBoarding, accent: Color, expanded: Boolean) {
    val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainerHighest, shape = appCorner(16.dp), modifier = Modifier.fillMaxWidth().testTag("card-next")) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = if (expanded) 12.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FittedText(tr("Weiter mit %1\$s", next.line), style = if (expanded) t.titleLarge else t.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                next.direction?.let { FittedText(tr("Richtung %1\$s", it), style = t.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2) }
                next.platform?.let { FittedText(it, Modifier.testTag("card-platform"), style = if (expanded) t.headlineSmall else t.titleLarge,
                    fontWeight = FontWeight.Bold, color = accent, maxLines = 1) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(clockTime(next.time), style = (if (expanded) t.headlineSmall else t.titleLarge).copy(fontWeight = FontWeight.Bold), maxLines = 1)
                transferNote(next, scheme.error, scheme.onSurfaceVariant)?.let { (text, color) ->
                    Text(text, Modifier.testTag("card-transfer"), style = t.labelMedium, color = color, maxLines = 1)
                }
            }
        }
    }
}

/** "Am Ziel 07:33" – in red with the minutes when it moved later. */
@Composable private fun FinalArrival(brief: PinnedJourneyBrief, arrivalColor: Color, expanded: Boolean) {
    val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    val later = brief.arrivalChange > 0
    Text(tr("Am Ziel %1\$s", (if (brief.arrivalApproximate) tr("ca.") + " " else "") + clockTime(brief.arrival)) +
        if (later) tr(" · %1\$s Min. später", brief.arrivalChange) else "",
        Modifier.fillMaxWidth().testTag("card-arrival"), style = (if (expanded) t.titleMedium else t.titleSmall).copy(fontWeight = FontWeight.SemiBold),
        color = if (later) scheme.error else arrivalColor, maxLines = 2)
}

/**
 * Build 116 · the rest of the way on the journey page: one quiet row per remaining ride (line, from →
 * to, times) and per walk (minutes). The ride in focus is marked; at most [limit] rows, then "+ n".
 */
@Composable internal fun JourneyRemainingSteps(session: PinnedJourneySession, fromIndex: Int, limit: Int = 5, skipRides: Int = 0) {
    val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    val all = session.trip.segments.withIndex().filter { (i, s) -> i >= fromIndex && stationRideKey(s) !in session.completedRideKeys }
    // Build 125: the rides shown above (and the walks before them) are not listed again.
    var skipped = 0
    val segments = all.dropWhile { (_, s) -> if (skipped >= skipRides) false else { if (!s.isWalk) skipped++; true } }
    if (if (skipRides > 0) segments.none { !it.value.isWalk } else segments.size <= 1) return
    Column(Modifier.fillMaxWidth().testTag("journey-remaining"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Dein weiterer Weg"), style = t.labelLarge, color = scheme.onSurfaceVariant)
        segments.take(limit).forEach { (i, s) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.isWalk) {
                    dev.ambivalenton.openv.ui.components.AppIcon(dev.ambivalenton.openv.R.drawable.ic_walk, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                    Text(tr("%1\$s Min. Fußweg", ((s.endTime - s.startTime + 59_999) / 60_000).coerceAtLeast(1)), style = t.bodyMedium,
                        color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                } else {
                    Surface(color = if (i == fromIndex) scheme.primaryContainer else scheme.surfaceContainerHighest, shape = appCorner(8.dp)) {
                        Text(s.lineName, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = t.labelLarge, fontWeight = FontWeight.Bold,
                            color = if (i == fromIndex) scheme.onPrimaryContainer else scheme.onSurface, maxLines = 1)
                    }
                    FittedText(tr("%1\$s → %2\$s", displayStopName(s.origin, s.lineType), displayStopName(s.destination, s.lineType)),
                        Modifier.weight(1f), style = t.bodyMedium, maxLines = 1)
                    Text("${clockTime(s.effectiveDeparture())}–${clockTime(s.effectiveArrival())}", style = t.labelLarge, maxLines = 1)
                }
            }
        }
        if (segments.size > limit) Text(tr("+ %1\$s weitere", segments.size - limit), style = t.labelMedium, color = scheme.onSurfaceVariant)
    }
}
