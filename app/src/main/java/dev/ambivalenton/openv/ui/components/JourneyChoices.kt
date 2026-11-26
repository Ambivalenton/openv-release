// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.reliability.ReliabilityLevel
import dev.ambivalenton.openv.data.reliability.ReliabilityMemory
import dev.ambivalenton.openv.data.reliability.rateTrip
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.sharing.JourneyShareData
import dev.ambivalenton.openv.sharing.JourneyShareRenderer
import dev.ambivalenton.openv.sharing.ShareCardColors
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appCorner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Build 117 · what the running journey page may do beyond showing: switch to an offered connection
 * and report a ride as on time or late. Provided by MainActivity; absent (null) in the bubble and in
 * previews, where neither appears.
 */
class JourneyChoiceActions(
    val memory: ReliabilityMemory,
    val onChoose: (sessionId: String, alternativeKey: String) -> Unit,
    val onReport: (segment: TripSegment, onTime: Boolean) -> Unit,
)

val LocalJourneyChoiceActions = staticCompositionLocalOf<JourneyChoiceActions?> { null }

/** The plain name of a level. */
fun ReliabilityLevel.title(): String = when (this) {
    ReliabilityLevel.VERY_SAFE -> tr("Sehr sicher")
    ReliabilityLevel.SAFE -> tr("Sicher")
    ReliabilityLevel.MEDIUM -> tr("Mittel")
    ReliabilityLevel.UNSAFE -> tr("Unsicher")
    ReliabilityLevel.VERY_UNSAFE -> tr("Sehr unsicher")
}

/** Five steps, filled from the left: five for "Sehr sicher", one for "Sehr unsicher". */
private fun ReliabilityLevel.filled() = 5 - ordinal

@Composable private fun ReliabilityLevel.color(): Color = when (this) {
    ReliabilityLevel.VERY_SAFE, ReliabilityLevel.SAFE -> MaterialTheme.colorScheme.primary
    ReliabilityLevel.MEDIUM -> MaterialTheme.colorScheme.onSurfaceVariant
    ReliabilityLevel.UNSAFE, ReliabilityLevel.VERY_UNSAFE -> MaterialTheme.colorScheme.error
}

/** A quiet meter of five short bars and the level's name; TalkBack reads "Verlässlichkeit geschätzt: …". */
@Composable fun ReliabilityMeter(level: ReliabilityLevel, modifier: Modifier = Modifier, label: Boolean = true) {
    val tint = level.color(); val track = MaterialTheme.colorScheme.outlineVariant
    val spoken = tr("Verlässlichkeit geschätzt: %1\$s", level.title())
    Row(modifier.clearAndSetSemantics { contentDescription = spoken }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { i -> Box(Modifier.size(width = 6.dp, height = 10.dp).clip(CircleShape).background(if (i < level.filled()) tint else track)) }
        }
        if (label) Text(level.title(), style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

/** Choices the controller found from the current station; only fresh ones (≤ 3 min) that still leave later. */
fun visibleJourneyChoices(state: PinnedJourneyState, now: Long): List<Trip> {
    val session = state.session ?: return emptyList()
    val choices = state.choices?.takeIf { it.sessionId == session.id } ?: return emptyList()
    return choices.trips.filter { now - it.fetchedAt in 0..180_000 && it.firstBoardingTime() > now }.take(3)
}

/** Whether [brief] stands at a station where switching makes sense: before boarding, walking to it, or arriving at a change. */
fun choicePoint(brief: PinnedJourneyBrief): Boolean = when (brief.kind) {
    BriefStepKind.BOARD, BriefStepKind.WALK -> true
    BriefStepKind.ALIGHT -> brief.next != null && brief.stopsLeft <= 1
    BriefStepKind.ARRIVE -> false
}

/** Build 122 · the line of a journey's first ride as people say it ("Bus 310", "RE1", "Tram 107"). */
fun firstLineLabel(trip: Trip): String {
    val ride = trip.segments.firstOrNull { !it.isWalk } ?: return ""
    val name = ride.lineName.trim()
    val numeric = name.firstOrNull()?.isDigit() == true
    return when {
        numeric && ride.lineType == LineType.BUS -> tr("Bus %1\$s", name)
        numeric && (ride.lineType == LineType.TRAM || ride.lineType == LineType.SUBWAY) -> tr("Bahn %1\$s", name)
        else -> name
    }
}

/**
 * Build 122 · the tightest change of [trip] in minutes (next departure − arrival − walking in between), or null
 * without a change. Shown concretely ("Nur 2 Min. zum Umsteigen") where it decides how safe a journey is.
 */
fun tightestTransferMinutes(trip: Trip): Int? {
    val rides = trip.segments.withIndex().filter { !it.value.isWalk }
    if (rides.size < 2) return null
    return rides.zipWithNext { (i, a), (j, b) ->
        val walk = trip.segments.subList(i + 1, j).sumOf { (it.plannedEndTime - it.plannedStartTime).coerceAtLeast(0) }
        ((b.effectiveDeparture() - a.effectiveArrival() - walk) / 60_000L).toInt()
    }.minOrNull()
}

/** Build 122 · "Nur 2 Min. zum Umsteigen" for a tight change (≤ 4 min), else null. */
fun tightTransferText(trip: Trip): String? = tightestTransferMinutes(trip)?.takeIf { it <= 4 }?.let {
    if (it <= 0) tr("Kaum Zeit zum Umsteigen") else tr("Nur %1\$s Min. zum Umsteigen", it)
}

/**
 * Build 117 · up to three other connections at a station. Build 122 (test report): every card first names its
 * line and departure, then the arrival, the changes (the tightest one concretely) and the reliability in words –
 * three buses arriving at the same time no longer look alike, also for TalkBack. The cards share the width they
 * have; with large type or little room they stand one below the other.
 */
@Composable fun JourneyChoicesRow(state: PinnedJourneyState, choices: List<Trip>, expanded: Boolean) {
    val actions = LocalJourneyChoiceActions.current ?: return
    val session = state.session ?: return
    val quiet = LocalAppSettings.current.reduceMotion
    var asked by remember(session.id) { mutableStateOf<Trip?>(null) }
    // The last non-empty list stays drawn while the row closes, so it does not empty first.
    var shown by remember(session.id) { mutableStateOf(choices) }
    if (choices.isNotEmpty()) shown = choices
    AnimatedVisibility(choices.isNotEmpty(),
        enter = if (quiet) fadeIn(spring(stiffness = 10_000f)) else fadeIn(spring(stiffness = 300f)) + expandVertically(spring(dampingRatio = .82f, stiffness = 320f), expandFrom = Alignment.Top),
        exit = if (quiet) fadeOut(spring(stiffness = 10_000f)) else fadeOut(spring(stiffness = 500f)) + shrinkVertically(spring(stiffness = 420f), shrinkTowards = Alignment.Top)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            // Each card needs about 132 dp for its line, departure and arrival; otherwise they stand in a column.
            val stacked = fontScale >= 1.3f || maxWidth / shown.size.coerceAtLeast(1) < 132.dp
            Column(Modifier.fillMaxWidth().testTag("journey-choices"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Andere Verbindungen"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val card: @Composable (Trip, Modifier) -> Unit = { trip, m -> JourneyChoiceCard(trip, actions.memory, expanded, stacked, m) { asked = trip } }
                if (stacked) shown.forEach { trip -> card(trip, Modifier.fillMaxWidth()) }
                else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { trip -> card(trip, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
    asked?.let { trip ->
        JourneyChoiceDialog(session, trip, state.choices?.query, actions.memory,
            onAccept = { asked = null; actions.onChoose(session.id, alternativeJourneyKey(trip)) }, onDismiss = { asked = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun JourneyChoiceCard(trip: Trip, memory: ReliabilityMemory, expanded: Boolean, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val level = remember(trip, memory) { rateTrip(trip, memory).level }
    val line = remember(trip) { firstLineLabel(trip) }
    val departure = clockTime(trip.firstBoardingTime())
    val arrival = clockTime(trip.effectiveArrival())
    val tight = remember(trip) { tightTransferText(trip) }
    val changes = if (trip.transfers == 0) tr("Ohne Umstieg") else transferCount(trip.transfers)
    val spoken = listOfNotNull(line.takeIf { it.isNotBlank() }, tr("ab %1\$s", departure), tr("Ankunft %1\$s", arrival), changes, tight,
        tr("Verlässlichkeit geschätzt: %1\$s", level.title())).joinToString(", ")
    Surface(onClick = onClick, modifier = modifier.testTag("journey-choice").clearAndSetSemantics { contentDescription = spoken },
        shape = appCorner(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        val pad = Modifier.padding(horizontal = 12.dp, vertical = if (expanded) 10.dp else 8.dp)
        val head = @Composable {
            // The departure moves to the next line rather than shrinking (large type).
            FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (line.isNotBlank()) Text(line, Modifier.align(Alignment.CenterVertically).background(MaterialTheme.colorScheme.secondaryContainer, appCorner(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                Text(tr("ab %1\$s", departure), Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        val arrivalText = @Composable {
            FittedText(tr("an %1\$s", arrival), style = (if (expanded) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                .copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
        val details = @Composable {
            FittedText(changes, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            tight?.let { FittedText(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, maxLines = 2) }
            // The level in words as well (not only colour and bars).
            ReliabilityMeter(level, label = true)
        }
        if (wide) Row(pad.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { head(); arrivalText() }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) { details() }
        } else Column(pad, verticalArrangement = Arrangement.spacedBy(4.dp)) { head(); arrivalText(); details() }
    }
}

/** The confirmation: new arrival against the current one, changes, reliability and the compact summary of the whole new journey. */
@Composable private fun JourneyChoiceDialog(session: PinnedJourneySession, trip: Trip, query: JourneyQuery?, memory: ReliabilityMemory,
    onAccept: () -> Unit, onDismiss: () -> Unit) {
    val complete = remember(session, trip, query) { query?.let { adoptPinnedAlternative(session, it, trip)?.trip } ?: trip }
    val level = remember(trip, memory) { rateTrip(trip, memory).level }
    val now = rememberJourneyNow()
    val current = projectSessionTimes(session, now).finalArrival.time
    AppAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("journey-choice-dialog"),
        title = { FittedText(tr("Verbindung wechseln?"), style = MaterialTheme.typography.headlineSmall, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val difference = ((trip.effectiveArrival() - current) / 60_000L).toInt()
                Text(tr("Ankunft %1\$s statt %2\$s", clockTime(trip.effectiveArrival()), clockTime(current)) +
                    when { difference > 0 -> tr(" · %1\$s Min. später", difference); difference < 0 -> tr(" · %1\$s Min. früher", -difference); else -> "" },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (trip.transfers == 0) tr("Ohne Umstieg") else transferCount(trip.transfers), style = MaterialTheme.typography.bodyMedium)
                    ReliabilityMeter(level)
                }
                CompactJourneyPicture(complete, Modifier.fillMaxWidth().heightIn(max = 420.dp))
            }
        },
        confirmButton = { TextButton(onAccept, Modifier.testTag("journey-choice-accept")) { Text(tr("Wechseln")) } },
        dismissButton = { TextButton(onDismiss, Modifier.testTag("journey-choice-keep")) { Text(tr("Abbrechen")) } })
}

/** The compact summary of [trip] as an image, the same as in the compact view; nothing while it renders. */
@Composable fun CompactJourneyPicture(trip: Trip, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = ShareCardColors.from(MaterialTheme.colorScheme)
    val card = remember(trip) { runCatching { JourneyShareData.from(trip) }.getOrNull() } ?: return
    BoxWithConstraints(modifier) {
        val pixels = with(LocalDensity.current) { maxWidth.roundToPx() }
        val image by produceState<ImageBitmap?>(null, trip, colors, pixels) {
            value = withContext(Dispatchers.Default) {
                val bitmap: Bitmap = JourneyShareRenderer.render(context, card, colors, pixels.toFloat() / JourneyShareRenderer.WIDTH)
                bitmap.asImageBitmap()
            }
        }
        image?.let { Image(it, JourneyShareRenderer.description(card), Modifier.fillMaxWidth().clip(appCorner(16.dp)).testTag("journey-choice-picture"),
            contentScale = ContentScale.Fit) }
    }
}

/**
 * Build 117 · the person's own report on the ride in focus ("pünktlich" / "verspätet"). Counts once
 * per ride, stays on the device and only adjusts the reliability estimate.
 */
@Composable fun RideFeedbackRow(session: PinnedJourneySession, segment: TripSegment) {
    val actions = LocalJourneyChoiceActions.current ?: return
    val key = stationRideKey(segment)
    var thanked by remember(key) { mutableStateOf(false) }
    if (actions.memory.hasReported(key) && !thanked) return
    Row(Modifier.fillMaxWidth().testTag("ride-feedback"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (thanked) {
            Text(tr("Danke, gemerkt."), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(2_500); thanked = false }
        } else {
            FittedText(tr("Wie fährt die %1\$s?", segment.lineName), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            for ((onTime, label) in listOf(true to tr("Pünktlich"), false to tr("Verspätet"))) {
                // Quiet buttons in the card's own surface: an offer, not a request.
                FilledTonalButton({ thanked = true; actions.onReport(segment, onTime) }, Modifier.heightIn(min = 40.dp).testTag(if (onTime) "ride-on-time" else "ride-late"),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) { Text(label, maxLines = 1) }
            }
        }
    }
}

/** Build 117 · the estimated reliability of the rest of the running journey, quietly on the journey page. */
@Composable fun JourneyReliabilityLine(session: PinnedJourneySession, onlyWhenNotable: Boolean = false) {
    val actions = LocalJourneyChoiceActions.current ?: return
    val rating = remember(session.trip, session.completedRideKeys, actions.memory) {
        rateTrip(session.trip, actions.memory) { stationRideKey(it) in session.completedRideKeys }
    }
    // Build 122: where a change decides it, said concretely ("Nur 2 Min. zum Umsteigen").
    val remaining = remember(session.trip, session.completedRideKeys) {
        session.trip.copy(segments = session.trip.segments.filterNot { !it.isWalk && stationRideKey(it) in session.completedRideKeys })
    }
    val tight = remember(remaining) { tightTransferText(remaining) }
    if (onlyWhenNotable && tight == null && rating.level.ordinal <= ReliabilityLevel.SAFE.ordinal) return
    Column(Modifier.fillMaxWidth().testTag("journey-reliability"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Verlässlichkeit"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReliabilityMeter(rating.level)
        }
        if (tight != null && rating.level.ordinal >= ReliabilityLevel.SAFE.ordinal)
            Text(tight, Modifier.testTag("journey-tight-transfer"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
