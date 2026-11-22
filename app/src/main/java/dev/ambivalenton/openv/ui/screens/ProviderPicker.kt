// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.components.AppModalBottomSheet as ModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderPicker(
    settings: AppSettings,
    onSave: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FittedText(tr("Datenquelle"), style = MaterialTheme.typography.headlineSmall)
            ProviderNotice(settings.engine)
            TransitSourceAttribution()
            ThemedButton({ onSave { it } }, Modifier.fillMaxWidth()) {
                Text(tr("Übernehmen"))
            }
        }
    }
}
