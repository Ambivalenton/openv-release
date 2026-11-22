// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.appGlossary
import dev.ambivalenton.openv.localization.tr
import java.text.Normalizer
import java.util.Locale

internal fun glossarySearchKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace("ß", "ss")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GlossarySheet(onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = appGlossary.map { it.categoryKey }.distinct()
    val needle = glossarySearchKey(query.trim())
    val entries = appGlossary.filter { (category == null || it.categoryKey == category) &&
        (needle.isEmpty() || glossarySearchKey(tr(it.titleKey) + " " + tr(it.descriptionKey)).contains(needle)) }
    AppModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxSize().imePadding().testTag("app-glossary")) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Begriffe kurz erklärt"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onDismiss) { AppIcon(R.drawable.ic_close, tr("Schließen")) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("app-glossary-list"),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "glossary-controls") {
                    Text(tr("Ein kleines Wörterbuch für deine Wege. Tippe auf einen Begriff, um mehr zu entdecken."), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp).testTag("glossary-query-field"),
                        label = { Text(tr("Begriff suchen")) }, singleLine = true,
                        trailingIcon = if (query.isNotEmpty()) { { IconButton({ query = "" }) { AppIcon(R.drawable.ic_close, tr("Suche leeren")) } } } else null)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(category == null, { category = null }, label = { Text(tr("Alle")) })
                        categories.forEach { key -> FilterChip(category == key, { category = key }, label = { Text(tr(key)) }) }
                    }
                }
                if (entries.isEmpty()) item(key = "glossary-empty") {
                    Text(tr("Kein passender Begriff. Versuche ein anderes Wort oder wähle Alle."), Modifier.padding(12.dp))
                }
                items(entries, key = { "term:${it.id}" }) { entry ->
                    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
                    Surface(Modifier.fillMaxWidth().testTag("glossary-${entry.id}"), shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column {
                            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
                                .semantics { stateDescription = tr(if (expanded) "Ausgeklappt" else "Eingeklappt") }
                                .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(tr(entry.categoryKey), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(tr(entry.titleKey), style = MaterialTheme.typography.titleMedium)
                                }
                                AppIcon(if (expanded) R.drawable.ic_collapse else R.drawable.ic_expand, null)
                            }
                            if (expanded) Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                HelpText(tr(entry.descriptionKey))
                            }
                        }
                    }
                }
            }
        }
    }
}

