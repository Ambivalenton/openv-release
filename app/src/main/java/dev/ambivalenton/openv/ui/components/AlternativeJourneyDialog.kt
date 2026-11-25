// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.appCorner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun AlternativeJourneyDialog(session:PinnedJourneySession,alternative:Trip,onAccept:()->Unit,onKeep:()->Unit) {
    val complete=session.alternativeQuery?.let {adoptPinnedAlternative(session,it,alternative)?.trip}?:alternative
    val comparison=AlternativeComparison(session.trip,complete)
    var details by rememberSaveable(session.id,alternativeJourneyKey(alternative)) {mutableStateOf(false)}
    AppAlertDialog(onDismissRequest=onKeep,title={FittedText(tr("Alternative vergleichen"),style=MaterialTheme.typography.headlineSmall)},
        text={Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState())
            .testTag("alternative-comparison"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=appCorner(20.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Text(tr("Bisher"),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                        Text(tr("Alternative"),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                    if(comparison.departureChanged) ComparisonRow(tr("Abfahrt"),
                        comparisonTimes(session.trip.firstBoardingTime(),complete.firstBoardingTime()))
                    if(comparison.arrivalChanged) ComparisonRow(tr("Ankunft"),
                        comparisonTimes(session.trip.effectiveArrival(),complete.effectiveArrival()))
                    if(!comparison.departureChanged && !comparison.arrivalChanged)
                        Text(tr("Abfahrt und Ankunft bleiben gleich."),style=MaterialTheme.typography.bodyMedium)
                    if(comparison.originChanged) ComparisonRow(tr("Einstieg"),comparison.currentOrigin to comparison.offeredOrigin,large=false)
                    if(comparison.transfersChanged) ComparisonRow(tr("Umstiege"),session.trip.transfers.toString() to complete.transfers.toString())
                    if(comparison.durationChanged) ComparisonRow(tr("Gesamtdauer"),
                        tr("%1\$s Min.",comparison.currentMinutes) to tr("%1\$s Min.",comparison.offeredMinutes),large=false)
                }
            }
            TextButton({details=!details},Modifier.fillMaxWidth().testTag("alternative-details-toggle")) {
                Text(tr(if(details)"Details ausblenden" else "Verbindungsdetails"),Modifier.weight(1f))
                AppIcon(if(details)R.drawable.ic_collapse else R.drawable.ic_expand,null)
            }
            CalmVisibility(details) {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    ComparisonChain(tr("Bisherige Verbindung"),session.trip)
                    ComparisonChain(tr("Angebotene Alternative"),complete)
                }
            }
        }},confirmButton={TextButton(onAccept,Modifier.testTag("accept-alternative")){Text(tr("Alternative wählen"))}},
        dismissButton={TextButton(onKeep,Modifier.testTag("keep-current-journey")){Text(tr("Bisherige behalten"))}})
}
private fun comparisonTimes(current:Long,offered:Long):Pair<String,String> {
    val zone=dev.ambivalenton.openv.data.model.transitZone
    val a=Instant.ofEpochMilli(current).atZone(zone);val b=Instant.ofEpochMilli(offered).atZone(zone)
    val format=DateTimeFormatter.ofPattern(if(a.toLocalDate()==b.toLocalDate())"HH:mm" else "dd.MM. HH:mm")
    return format.format(a) to format.format(b)
}
@Composable private fun ComparisonRow(label:String,values:Pair<String,String>,large:Boolean=true) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(values.first,Modifier.weight(1f),style=if(large)MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium)
            Text(values.second,Modifier.weight(1f),style=if(large)MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable private fun ComparisonChain(title:String,trip:Trip) {
    Surface(shape=appCorner(18.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall)
            Text(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .format(Instant.ofEpochMilli(trip.firstBoardingTime()).atZone(dev.ambivalenton.openv.data.model.transitZone)),style=MaterialTheme.typography.labelMedium)
            Text(tr("Abfahrt %1\$s · Ankunft %2\$s",clockTime(trip.firstBoardingTime()),clockTime(trip.effectiveArrival())),style=MaterialTheme.typography.bodyMedium)
            Text(transferCount(trip.transfers),style=MaterialTheme.typography.bodySmall)
            trip.segments.forEach {leg->
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(leg.lineName.ifBlank {leg.lineType.label},style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    Text("${leg.origin.name} → ${leg.destination.name}",style=MaterialTheme.typography.bodySmall)
                    Text(listOfNotNull("${clockTime(leg.effectiveDeparture())} – ${clockTime(leg.effectiveArrival())}",
                        platformName(leg.departurePlatform,leg.lineType)).joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                }
            }
            trip.notices.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }
}
