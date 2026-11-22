// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.screens

import dev.ambivalenton.openv.ui.components.ThemedButton

import dev.ambivalenton.openv.ui.components.FittedText

import dev.ambivalenton.openv.ui.components.AppModalBottomSheet as ModalBottomSheet

import dev.ambivalenton.openv.localization.tr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.AppSettings
import dev.ambivalenton.openv.data.repository.httpsBase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceSettingsSheet(
    settings: AppSettings,
    onSave: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var geo by rememberSaveable { mutableStateOf(settings.geocodingEndpoint) }
    var walking by rememberSaveable { mutableStateOf(settings.walkingRoutingEndpoint) }
    var tiles by rememberSaveable { mutableStateOf(settings.mapTileTemplate) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FittedText(tr("Server, Ortssuche & Karten"), style = MaterialTheme.typography.headlineSmall)
            dev.ambivalenton.openv.ui.components.TransitSourceAttribution()
            OutlinedTextField(
                geo,
                { geo = it.take(300) },
                label = { Text(tr("Photon-Basisadresse (HTTPS)")) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                tiles,
                { tiles = it.take(500) },
                label = { Text(tr("Raster-Kacheln: {z}/{x}/{y}")) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(walking,{walking=it.take(300)},label={Text(tr("Valhalla-Fußrouting (HTTPS)"))},modifier=Modifier.fillMaxWidth())
            Text(tr("Geh-Anweisungen: Valhalla mit OpenStreetMap. Öffentlicher FOSSGIS-Demodienst mit Anfragebegrenzung; für größere Veröffentlichungen eigenen Dienst konfigurieren."),style=MaterialTheme.typography.bodySmall)
            Text(
                tr("Fahrpläne kommen von VRR OpenService. Der Schwerpunkt liegt auf Rhein-Ruhr. Ortssuche, Karten, Gehführung und Wetter nutzen eigene Dienste mit eigenen Nutzungsbedingungen."),
                style = MaterialTheme.typography.bodySmall,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ThemedButton(
                {
                    try {
                        httpsBase(geo)
                        httpsBase(walking)
                        require(listOf("{z}", "{x}", "{y}").all { it in tiles }) {
                            tr("Die Kachel-Adresse benötigt {z}, {x} und {y}.")
                        }
                        httpsBase(tiles.replace("{z}", "1").replace("{x}", "1").replace("{y}", "1"))
                        onSave { it.copy(geocodingEndpoint=geo.trim(), mapTileTemplate=tiles.trim(), walkingRoutingEndpoint=walking.trim()) }
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    }
                },
                Modifier.fillMaxWidth(),
            ) {
                Text(tr("Speichern"))
            }
        }
    }
}
