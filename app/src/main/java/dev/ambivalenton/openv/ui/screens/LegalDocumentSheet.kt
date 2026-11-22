// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.HelpSection
import dev.ambivalenton.openv.data.model.parseHelpDocument
import dev.ambivalenton.openv.localization.serviceLanguage
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reading local help never contacts a server. Only tapped external links do. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegalDocumentSheet(document: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val (title, asset) = when (document) {
        "coverage" -> tr("Wo funktioniert Öpen-V?") to "coverage.txt"
        "help" -> tr("So nutzt du Öpen-V") to "help.txt"
        "privacy" -> tr("Datenschutzerklärung") to "privacy.txt"
        "licenses" -> tr("Lizenzen & Datenquellen") to "notices.txt"
        else -> tr("Herausgeber & Nutzung") to "publisher.txt"
    }
    val localizedAsset = "legal/" + (if (serviceLanguage() == "de") "" else "en/") + asset
    var failed by remember(localizedAsset) { mutableStateOf(false) }
    var attempt by remember(localizedAsset) { mutableIntStateOf(0) }
    val sections by produceState<List<HelpSection>?>(null, localizedAsset, attempt) {
        value = null
        failed = false
        val result = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(localizedAsset).bufferedReader().use { parseHelpDocument(it.readText()) } }
        }
        failed = result.isFailure
        value = result.getOrNull()
    }
    AppModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxSize().testTag("help-document-$document")) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onDismiss) { AppIcon(R.drawable.ic_close, tr("Schließen")) }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            if (failed) Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Der Text konnte nicht geöffnet werden."))
                TextButton({ attempt++ }) { Text(tr("Erneut versuchen")) }
            } else if (sections == null) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            } else LazyColumn(
                Modifier.weight(1f).testTag("help-document-list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sections.orEmpty(), key = { it.id }) { section ->
                    if (section.title == null) HelpBlocks(section.blocks)
                    else SettingsDisclosure(section.title, "document-$document-section-${section.id}") {
                        HelpBlocks(section.blocks)
                    }
                }
            }
        }
    }
}
