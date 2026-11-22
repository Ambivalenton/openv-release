// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.appCorner

/**
 * 2.4: a titled frame of the settings pages (the same surface as the frames of "Modi & Verhalten").
 */
@Composable internal fun SettingsFrame(title: String, tag: String, subtitle: String? = null, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth().testTag(tag), shape = appCorner(24.dp), color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            content()
        }
    }
}

/**
 * H04/2.4: the restriction of transport modes as a short informative line ("Verkehrsmittel · Ohne
 * Bus"); null while the default modes apply. Long-distance services are off by default.
 */
fun productRestrictionLine(products: Set<LineType>): String? {
    val missing = localProducts.filter { it !in products }
    val longDistance = LineType.ICE in products
    if (missing.isEmpty() && !longDistance) return null
    val parts = buildList {
        if (missing.isNotEmpty()) add(tr("Ohne %1\$s", missing.joinToString(", ") { it.label }))
        if (longDistance) add(tr("mit Fernverkehr"))
    }
    return tr("Verkehrsmittel · %1\$s", parts.joinToString(" · "))
}

/**
 * H04/2.4: the transport modes for further searches, as tags including long-distance services. The
 * choice is a draft until "Filter anwenden"; "Abbrechen", Android back or leaving the page keep the
 * last applied choice. At least one mode is required; "Standard wiederherstellen" sets the default.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun TransportModeFrame(applied: Set<LineType>, onApply: (Set<LineType>) -> Unit, modifier: Modifier = Modifier) {
    var draft by remember(applied) { mutableStateOf(applied) }
    val changed = draft != applied
    SettingsFrame(tr("Verkehrsmittel"), "settings-transport-modes", tr("Gilt für weitere Suchen"), modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            selectableProducts.forEach { type ->
                val selected = type in draft
                FilterChip(selected, { draft = if (selected) draft - type else draft + type },
                    label = { Text(type.label) },
                    leadingIcon = if (selected) { { AppIcon(R.drawable.ic_check, null, Modifier.size(18.dp)) } } else null,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("transport-tag-${type.name.lowercase()}")
                        .semantics { stateDescription = if (selected) tr("Aktiv") else tr("Nicht aktiv") })
            }
        }
        Text(tr("Fernverkehr (ICE, IC, EC und FlixTrain) ist anfangs ausgeschaltet."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (draft.isEmpty()) Text(tr("Wähle mindestens ein Verkehrsmittel."), Modifier.testTag("transport-empty")
            .semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton({ draft = localProducts }, Modifier.heightIn(min = 48.dp).testTag("transport-default"),
                enabled = draft != localProducts) { Text(tr("Standard wiederherstellen")) }
            if (changed) TextButton({ draft = applied }, Modifier.heightIn(min = 48.dp).testTag("transport-cancel")) { Text(tr("Abbrechen")) }
            ThemedButton({ onApply(draft) }, Modifier.heightIn(min = 48.dp).testTag("transport-apply"),
                enabled = changed && draft.isNotEmpty()) { Text(tr("Filter anwenden")) }
        }
        val line = productRestrictionLine(applied)
        Text(if (changed) tr("Noch nicht angewendet.") else line ?: tr("Alle Nahverkehrsmittel werden gesucht."),
            Modifier.testTag("transport-status"), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 2.4: "Fahrt & Navigation → Verbindungssuche", grouped by effect: transport modes first, then the
 * preferences for choosing a connection, the companion's alternatives and the nearby departures.
 */
@Composable internal fun ConnectionSearchSettings(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit,
    hiddenNotices: Int = 0, onRestoreNotices: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().testTag("connection-search-settings"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TransportModeFrame(settings.products, { chosen ->
            update { it.copy(products = chosen, includeLongDistance = LineType.ICE in chosen) }
        })
        SettingsFrame(tr("Präferenzen"), "settings-search-preferences", routingPreferencesSummary()) {
            RoutingPreferenceToggles(settings.overview) { change -> update { it.copy(overview = change(it.overview)) } }
        }
        SettingsFrame(tr("Vorlauf bis zur Abfahrt"), "settings-search-buffer",
            tr("So viel Zeit soll mindestens bis zur Abfahrt bleiben. Ein negativer Wert zeigt auch Fahrten, die laut Fahrplan gerade erst abgefahren sind – etwa wenn du schon an der Haltestelle stehst.")) {
            OverviewBufferSetting(settings.overview) { change -> update { it.copy(overview = change(it.overview)) } }
        }
        SettingsFrame(tr("Unterwegs"), "settings-search-alternatives") {
            ToggleRow(tr("Alternative Fahrten vorschlagen"),
                tr("Zeigt bessere Fahrten kurz vor der Abfahrt und unterwegs als kurze Meldung. Alternativen im angepinnten Zeitstrahl bleiben immer verfügbar."),
                settings.alternativeSuggestions) { v -> update { it.copy(alternativeSuggestions = v) } }
        }
        SettingsFrame(tr("Abfahrten in der Nähe"), "settings-search-radius", tr("Umkreis, in dem Haltestellen in deiner Nähe gesucht werden.")) {
            RadiusSlider(settings.departureRadiusMeters) { meters -> update { it.copy(departureRadiusMeters = meters) } }
        }
        // 2.5: hidden notices can be shown again later.
        SettingsFrame(tr("Fahrplanhinweise"), "settings-search-notices",
            tr("Hinweise zu verlegten oder entfallenden Halten stehen hervorgehoben oben im Zeitstrahl. Unpassende kannst du dort zurückstufen; die Markierung im Balken bleibt.")) {
            Text(if (hiddenNotices == 0) tr("Alle Hinweise werden hervorgehoben.") else tr("%1\$s Hinweise nicht mehr hervorgehoben.", hiddenNotices),
                style = MaterialTheme.typography.bodyMedium)
            if (hiddenNotices > 0) TextButton(onRestoreNotices, Modifier.heightIn(min = 48.dp).testTag("restore-notices")) { Text(tr("Alle wieder hervorheben")) }
        }
    }
}
