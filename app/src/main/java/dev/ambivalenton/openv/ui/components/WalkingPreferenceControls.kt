// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr

/**
 * M06: two groups by effect. "So wird gerechnet" changes which connections are found (pace,
 * leaving walking times out of the search); "So wird die Fahrt angezeigt" only changes the display
 * and never the route of an identical answer.
 */
@Composable fun TimelineWalkingSettings(settings:AppSettings,update:((AppSettings)->AppSettings)->Unit) {
    Column(Modifier.fillMaxWidth().testTag("timeline-walking-settings"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        SettingsCard(Modifier.testTag("walking-pace-card")) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(tr("So wird gerechnet"),Modifier.testTag("walking-calculation"),style=MaterialTheme.typography.titleMedium)
                Text(tr("Dein Gehtempo"),style=MaterialTheme.typography.titleSmall)
                var pace by androidx.compose.runtime.remember(settings.walkingSpeed) {
                    androidx.compose.runtime.mutableFloatStateOf(settings.walkingSpeed.ordinal.toFloat())
                }
                Text(WalkingSpeed.entries[pace.toInt().coerceIn(0,2)].title,style=MaterialTheme.typography.bodyMedium)
                Slider(pace,{pace=it},valueRange=0f..2f,steps=1,
                    onValueChangeFinished={update {it.copy(walkingSpeed=WalkingSpeed.entries[kotlin.math.round(pace).toInt().coerceIn(0,2)])}},
                    modifier=Modifier.testTag("walking-pace-slider"))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(tr("Entspannt"));Text(tr("Schnell"))
                }
                Text(tr("Dein Tempo gilt für die Verbindungssuche, Umstiege und Fußwegnavigation."),style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                ToggleRow(tr("Gehzeiten bei der Suche auslassen"),
                    tr("Umstiege können knapper sein: Die Suche prüft keine Gehzeiten, Fußwege werden ausgeblendet. Das Ergebnis weist darauf hin."),settings.ignoreWalking) {
                    enabled->update {it.copy(ignoreWalking=enabled)}
                }
            }
        }
        SettingsCard(Modifier.testTag("walking-display-card")) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(tr("So wird die Fahrt angezeigt"),Modifier.testTag("walking-display"),style=MaterialTheme.typography.titleMedium)
                Text(tr("Diese Schalter ändern nur die Darstellung, nicht die gefundenen Verbindungen."),style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                ToggleRow(tr("Fußwege im Balkendiagramm"),tr("Start und Ziel mit Gehzeit anzeigen"),settings.timelineShowWalking) {
                    show->update {it.copy(timelineShowWalking=show)}
                }
                ToggleRow(tr("Fußwegnavigation im Zeitstrahl"),tr("Abbiegehinweise mit OpenStreetMap · nur im angepinnten Zeitstrahl"),settings.detailedWalkingNavigation) {enabled->
                    update {it.copy(detailedWalkingNavigation=enabled)}
                }
                WalkingNavigationBubbleSetting(settings.walkingNavigation) {mode->update {it.copy(walkingNavigation=mode)}}
            }
        }
    }
}

@Composable fun SearchPrioritySettings(settings:AppSettings,update:((AppSettings)->AppSettings)->Unit) {SearchOrderSetting(settings,update)}
