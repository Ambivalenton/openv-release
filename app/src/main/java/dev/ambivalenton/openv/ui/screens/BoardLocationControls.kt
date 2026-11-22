// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.ambivalenton.openv.data.model.AppSettings
import dev.ambivalenton.openv.localization.tr

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun BoardLocationControls(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    Text(tr("Die Anzeigetafel nutzt eine feste Haltestelle oder deinen zuletzt freigegebenen Standort. Du kannst den Standort jederzeit manuell aktualisieren."))
}
