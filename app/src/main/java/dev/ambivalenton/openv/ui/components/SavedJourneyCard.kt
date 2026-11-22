// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import dev.ambivalenton.openv.ui.theme.cardElevation
import dev.ambivalenton.openv.ui.theme.appFrameShadow
import dev.ambivalenton.openv.ui.theme.appFrame
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.appCorner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable fun SavedJourneyCard(saved:SavedJourney,onOpen:()->Unit,onRemove:()->Unit,onPin:()->Unit={}) {
    val haptic=LocalHapticFeedback.current
    val date=DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(saved.trip.firstBoardingTime()).atZone(ZoneId.systemDefault()))
    AppCard(shape=appCorner(24.dp),modifier=Modifier.fillMaxWidth().testTag("saved-journey-${saved.id}")
        .combinedClickable(onClick=onOpen,onLongClickLabel=tr("Verbindung anpinnen"),onLongClick={haptic.performHapticFeedback(HapticFeedbackType.LongPress);onPin()}),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                FittedText(saved.favorite.label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                IconButton(onRemove,Modifier.testTag("remove-saved-${saved.id}")){AppIcon(R.drawable.ic_close,tr("Vormerkung entfernen"))}
            }
            Text(tr("%1\$s · %2\$s → %3\$s",date,clockTime(saved.trip.firstBoardingTime()),clockTime(saved.trip.effectiveArrival())),style=MaterialTheme.typography.titleSmall)
            Text(saved.trip.segments.filterNot {it.isWalk}.joinToString(" → "){it.lineName},style=MaterialTheme.typography.bodyMedium)
            // 2.2: a saved journey shows a known cancellation where it is listed.
            val impossible=tripImpossibility(saved.trip,System.currentTimeMillis(),currentSearch=false)
            if(impossible!=null) Text(impossible.title(),Modifier.testTag("saved-journey-unavailable-${saved.id}"),
                style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.error)
            Text(tr("Vorgemerkt · noch kein Reisebegleiter aktiv"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
