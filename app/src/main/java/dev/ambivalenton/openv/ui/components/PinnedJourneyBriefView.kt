// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.AnimatedContent
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

/**
 * Build 116 · the running journey without repetition. One block for the step in focus (verb, place,
 * time; line, arrival platform and stops left quietly below), one block for the onward ride (line,
 * direction, platform highlighted, departure, transfer time), and the arrival only when it moves.
 * [expanded]: the overview-filling page, with larger type and more air.
 */
@Composable internal fun PinnedJourneyBriefView(brief: PinnedJourneyBrief, accent: Color, arrivalColor: Color, expanded: Boolean) {
    val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    val quiet = LocalAppSettings.current.reduceMotion
    val gap = if (expanded) 18.dp else 8.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        brief.alert?.let { alert ->
            Surface(color = scheme.errorContainer, contentColor = scheme.onErrorContainer, shape = appCorner(12.dp)) {
                FittedText(alert, Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag("card-alert"),
                    style = if (expanded) t.titleSmall else t.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        // The step in focus. Its content changes with a quiet cross-fade, never a jump.
        AnimatedContent(brief.kind to brief.place, transitionSpec = {
            if (quiet) fadeIn(spring(stiffness = 10_000f)) togetherWith fadeOut(spring(stiffness = 10_000f))
            else fadeIn(spring(dampingRatio = .9f, stiffness = 380f)) togetherWith fadeOut(spring(dampingRatio = 1f, stiffness = 700f))
        }, label = "brief-step") { (kind, place) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FittedText(kind.verb(), Modifier.testTag("card-action"), style = if (expanded) t.titleMedium else t.labelLarge,
                        color = scheme.onSurfaceVariant, maxLines = 1)
                    FittedText(place, Modifier.testTag("card-next-place"), style = if (expanded) t.headlineMedium else t.titleLarge,
                        fontWeight = FontWeight.SemiBold, maxLines = 2)
                    val quietLine = listOfNotNull(brief.line.takeIf { it.isNotBlank() },
                        brief.platform.takeIf { kind == BriefStepKind.ALIGHT || kind == BriefStepKind.ARRIVE }?.let { tr("Ankunft %1\$s", it) },
                        when (brief.stopsLeft) { 0 -> null; 1 -> tr("noch ein Halt"); else -> tr("noch %1\$s Halte", brief.stopsLeft) })
                    if (quietLine.isNotEmpty()) FittedText(quietLine.joinToString(" · "), Modifier.testTag("card-step-detail"),
                        style = if (expanded) t.bodyLarge else t.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2)
                    // Boarding: the platform is the one thing to find – highlighted, never cut.
                    if ((kind == BriefStepKind.BOARD) && brief.platform != null)
                        FittedText(brief.platform, Modifier.testTag("card-platform"), style = if (expanded) t.titleLarge else t.titleMedium,
                            fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text((if (brief.approximate) tr("ca.") + " " else "") + clockTime(brief.time), Modifier.testTag("card-time"),
                        style = (if (expanded) t.displaySmall else t.headlineMedium).copy(fontWeight = FontWeight.Bold), color = accent, maxLines = 1)
                    if (brief.delayMinutes > 0) Text(tr("+%1\$s Min.", brief.delayMinutes), Modifier.testTag("card-delay"),
                        style = t.labelLarge, color = scheme.error, maxLines = 1)
                }
            }
        }
        // The onward ride after a change: its platform is highlighted, its departure is the second time that matters.
        brief.next?.let { next ->
            Surface(color = scheme.surfaceContainerHighest, shape = appCorner(16.dp), modifier = Modifier.fillMaxWidth().testTag("card-next")) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = if (expanded) 12.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        FittedText(tr("Weiter mit %1\$s", next.line), style = if (expanded) t.titleMedium else t.titleSmall,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                        next.direction?.let { FittedText(tr("Richtung %1\$s", it), style = t.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1) }
                        next.platform?.let { FittedText(it, Modifier.testTag("card-platform"), style = if (expanded) t.titleLarge else t.titleMedium,
                            fontWeight = FontWeight.Bold, color = accent, maxLines = 1) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(clockTime(next.time), style = (if (expanded) t.headlineSmall else t.titleLarge).copy(fontWeight = FontWeight.Bold), maxLines = 1)
                        val transfer = when (next.status) {
                            TransferStatus.TIGHT -> tr("Umstieg knapp") to scheme.error
                            TransferStatus.AT_RISK -> tr("Anschluss gefährdet") to scheme.error
                            TransferStatus.BROKEN -> tr("Anschluss nicht erreichbar") to scheme.error
                            else -> next.waitMinutes?.takeIf { it > 0 }?.let { tr("%1\$s Min. Umstieg", it) to scheme.onSurfaceVariant }
                        }
                        transfer?.let { (text, color) -> Text(text, Modifier.testTag("card-transfer"), style = t.labelMedium, color = color, maxLines = 1) }
                    }
                }
            }
        }
        // The arrival only when it is not the step itself; in red when it moved later.
        if (brief.kind != BriefStepKind.ARRIVE) {
            val later = brief.arrivalChange > 0
            Text(tr("Am Ziel %1\$s", (if (brief.arrivalApproximate) tr("ca.") + " " else "") + clockTime(brief.arrival)) +
                if (later) tr(" · %1\$s Min. später", brief.arrivalChange) else "",
                Modifier.fillMaxWidth().testTag("card-arrival"), style = (if (expanded) t.titleMedium else t.titleSmall).copy(fontWeight = FontWeight.SemiBold),
                color = if (later) scheme.error else arrivalColor, maxLines = 2)
        }
    }
}

/**
 * Build 116 · the rest of the way on the journey page: one quiet row per remaining ride (line, from →
 * to, times) and per walk (minutes). The ride in focus is marked; at most [limit] rows, then "+ n".
 */
@Composable internal fun JourneyRemainingSteps(session: PinnedJourneySession, fromIndex: Int, limit: Int = 5) {
    val t = MaterialTheme.typography; val scheme = MaterialTheme.colorScheme
    val segments = session.trip.segments.withIndex().filter { (i, s) -> i >= fromIndex && stationRideKey(s) !in session.completedRideKeys }
    if (segments.size <= 1) return
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
