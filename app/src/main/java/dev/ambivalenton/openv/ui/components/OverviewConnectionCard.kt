// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.theme.cardElevation
import dev.ambivalenton.openv.ui.theme.appFrameShadow
import dev.ambivalenton.openv.ui.theme.appFrame
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.OverviewEntry
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import dev.ambivalenton.openv.ui.theme.appCorner

@OptIn(ExperimentalLayoutApi::class)
@Composable fun OverviewConnectionCard(entry:OverviewEntry,settings:OverviewSettings,onOpen:()->Unit,modifier:Modifier=Modifier,
    onSearchConnections:()->Unit=onOpen) {
    val appSettings=LocalAppSettings.current
    val trip=entry.trip?.let {if(appSettings.ignoreWalking)assembleTimeline(it,appSettings).trip else it}
    val fields=trip?.let {overviewCardFields(entry.favorite.forDestination(entry.query?.destination),it,settings,entry.weather)}.orEmpty()
    val accent=entry.favorite.accentSeed?.let {Color(it)}?:MaterialTheme.colorScheme.primary
    val originAccent=(if(entry.favorite.counterpole!=null)entry.favorite.originAccentSeed else entry.favorite.accentSeed)?.let {Color(it)}?:MaterialTheme.colorScheme.primary
    AppCard(shape=appCorner(28.dp),elevation=CardDefaults.cardElevation(defaultElevation=3.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),
        modifier=modifier.fillMaxWidth().testTag("overview-card-${entry.favorite.id}")
            .combinedClickable(enabled=trip!=null,role=androidx.compose.ui.semantics.Role.Button,
                onLongClickLabel=tr("Weitere Verbindungen suchen"),onLongClick=onSearchConnections,onClick=onOpen)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                FavoriteBadge(entry.favorite,size=42.dp,singleShape=true,shadow=true)
                FittedText(fields.firstOrNull {it.id=="destination"}?.value?:tr("Verbindung"),Modifier.weight(1f),color=accent,style=MaterialTheme.typography.titleLarge)
            }
            if(trip==null)Text(tr(if(entry.loading)"Verbindung wird gesucht …" else "Keine Verbindung verfügbar."),style=MaterialTheme.typography.bodyMedium)
            else {
                if(trip.cancelled)Text(tr("Fällt aus"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    fields.filterNot {it.id=="destination"}.forEach {field->
                        Surface(shape=appCorner(12.dp),color=MaterialTheme.colorScheme.surfaceContainer,
                            modifier=Modifier.widthIn(min=90.dp,max=260.dp).testTag("overview-card-${field.id}")) {
                            Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
                                FittedText(field.label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(field.value,style=if(field.id in setOf("departure","arrival"))MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                    color=when(field.id){"departure","origin","platforms"->originAccent;"arrival","destination"->accent;else->MaterialTheme.colorScheme.onSurface})
                            }
                        }
                    }
                }
                if(fields.any {it.id=="weather"}) {
                    val uri=LocalUriHandler.current
                    TextButton({uri.openUri("https://open-meteo.com/")}){Text(tr("Wetter: Open-Meteo"),style=MaterialTheme.typography.labelSmall)}
                }
            }
        }
    }
}
