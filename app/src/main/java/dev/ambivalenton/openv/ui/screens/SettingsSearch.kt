// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.TimetableProfile
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.AppIcon
import java.text.Normalizer
import java.util.Locale

/**
 * M11: one findable setting: its name, the page that holds it, the words people use for it (German
 * and English) and, where a page is long, the frame to scroll to. [available] hides options the
 * running edition does not have.
 */
data class SettingsSearchEntry(val titleKey: String, val page: SettingsPage, val words: List<String>,
    val anchor: String? = null, val available: Boolean = true, val noteKey: String? = null)

/** M11: the index over the existing settings pages; synonyms instead of a second structure. */
fun settingsSearchIndex(): List<SettingsSearchEntry> = listOf(
    SettingsSearchEntry("Schrift & Lesbarkeit", SettingsPage.TYPOGRAPHY,
        listOf("schrift", "schriftgröße", "große schrift", "text", "lesbar", "kontrast", "font", "large text", "contrast", "size", "readability")),
    SettingsSearchEntry("Sichern & Übertragen", SettingsPage.MODES,
        listOf("backup", "sicherung", "sichern", "export", "import", "übertragen", "wiederherstellen", "neues handy", "restore", "transfer"), anchor = "backup"),
    SettingsSearchEntry("Suchverlauf löschen", SettingsPage.SERVICES,
        listOf("cache", "verlauf", "history", "zwischenspeicher", "löschen", "gespeicherte fahrten", "delete", "clear")),
    SettingsSearchEntry("Server, Ortssuche & Karten", SettingsPage.SERVICES,
        listOf("wlan", "wifi", "wi-fi", "internet", "mobile daten", "datenverbrauch", "server", "karten", "map", "online", "netz", "network"),
        noteKey = "Öpen-V nutzt jede Internetverbindung; eine eigene WLAN-Einstellung gibt es nicht."),
    SettingsSearchEntry("Fahrplandienst wählen", SettingsPage.SERVICES,
        listOf("fahrplandienst", "anbieter", "provider", "datenquelle", "region", "service", "transit service"), available = TimetableProfile.serviceChoice),
    SettingsSearchEntry("Gehtempo", SettingsPage.WALKING,
        listOf("gehtempo", "gehgeschwindigkeit", "laufen", "gehen", "fußweg", "fußwege", "walking", "walking speed", "speed", "tempo", "langsam", "schnell")),
    SettingsSearchEntry("Verkehrsmittel", SettingsPage.SEARCH,
        listOf("filter", "verkehrsmittel", "bus", "bahn", "zug", "ice", "fernverkehr", "tram", "straßenbahn", "u-bahn", "transport", "modes", "long distance")),
    SettingsSearchEntry("Vorlauf bis zur Abfahrt", SettingsPage.SEARCH, listOf("puffer", "vorlauf", "buffer", "zeit bis zur abfahrt", "lead time")),
    SettingsSearchEntry("Fahrplanhinweise", SettingsPage.SEARCH, listOf("hinweis", "hinweise", "ausgeblendet", "notice", "notices", "wieder einblenden")),
    SettingsSearchEntry("Reihenfolge der Suchtreffer", SettingsPage.TRAVEL, listOf("reihenfolge", "suchtreffer", "sortierung", "priority", "order")),
    SettingsSearchEntry("Favoriten", SettingsPage.FAVORITES, listOf("favorit", "favoriten", "netzplan", "raster", "umkreis", "grid", "favourite", "favorite")),
    SettingsSearchEntry("App-Design", SettingsPage.APP_THEME,
        listOf("design", "theme", "farbe", "farben", "dunkel", "hell", "nacht", "tag", "dark", "light", "color", "colour")),
    SettingsSearchEntry("Tag & Nacht", SettingsPage.MODES, listOf("tag", "nacht", "sonnenaufgang", "sonnenuntergang", "automatisch", "day", "night", "sunrise", "sunset"), anchor = "day-night"),
    SettingsSearchEntry("Anzeigetafel-Design", SettingsPage.BOARD, listOf("anzeigetafel", "tafel", "abfahrten", "board", "departures", "widget")),
    SettingsSearchEntry("Balkendiagramm-Design", SettingsPage.TIMELINE, listOf("balken", "balkendiagramm", "diagramm", "zeitstrahl", "timeline", "chart")),
    SettingsSearchEntry("Netzplan-Design", SettingsPage.NETWORK_PLAN, listOf("netzplan", "linien", "fahrzeuge", "vehicles", "network plan")),
    SettingsSearchEntry("App-Logo", SettingsPage.APP_LOGO, listOf("logo", "symbol", "icon", "startbildschirm", "launcher")),
    SettingsSearchEntry("Planermodus", SettingsPage.MODES,
        listOf("planer", "planen", "ohne standort", "kein gps", "nur suchen", "navigationsleiste", "planner", "no location", "search only")),
    SettingsSearchEntry("Modi & Verhalten", SettingsPage.MODES,
        listOf("ruhemodus", "stromsparen", "stromsparmodus", "akku", "energie", "schwarz", "weiß", "minimalismus", "gesten", "quiet", "battery", "power")),
    SettingsSearchEntry("Info & Hilfe", SettingsPage.INFO,
        listOf("hilfe", "anleitung", "begriffe", "glossar", "datenschutz", "lizenz", "lizenzen", "impressum", "help", "privacy", "licence", "license")),
).filter { it.available }

/** Lower case without accents and with ß as ss, so "Größe" and "grosse" meet. */
internal fun searchKey(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT).replace("ß", "ss"), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "").trim()

/** M11: matches by name first, then by the start of a synonym, then anywhere in one. */
fun searchSettings(query: String, index: List<SettingsSearchEntry> = settingsSearchIndex()): List<SettingsSearchEntry> {
    val key = searchKey(query)
    if (key.length < 2) return emptyList()
    fun score(entry: SettingsSearchEntry): Int {
        val title = searchKey(tr(entry.titleKey)); val german = searchKey(entry.titleKey)
        val words = entry.words.map(::searchKey)
        return when {
            title.startsWith(key) || german.startsWith(key) -> 4
            words.any { it == key } -> 3
            title.contains(key) || german.contains(key) || words.any { it.startsWith(key) } -> 2
            words.any { it.contains(key) } -> 1
            else -> 0
        }
    }
    return index.map { it to score(it) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }.take(8)
}

/** M11: the visible path of a hit, e.g. "Einstellungen › Fahrt & Navigation › Fußwege & Navigation". */
fun settingsPath(page: SettingsPage): String = generateSequence(page) { it.parent }.toList().reversed()
    .joinToString(" › ") { tr(it.titleKey) }

/**
 * M11: the search over all settings pages, with the path of every hit and a direct jump. Without a
 * query, "Schrift & Lesbarkeit" and "Sichern & Übertragen" stay one tap away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SettingsSearch(onOpen: (SettingsSearchEntry) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val hits = remember(query) { searchSettings(query) }
    Column(Modifier.fillMaxWidth().testTag("settings-search"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Slim and one line: a small type, the theme's corners and a compact height.
        val shape = dev.ambivalenton.openv.ui.theme.appCorner(14.dp)
        val colors = MaterialTheme.colorScheme
        androidx.compose.foundation.text.BasicTextField(query, { query = it.take(60) },
            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings-search-field"),
            singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { hits.firstOrNull()?.let { focus.clearFocus(); query = ""; onOpen(it) } }),
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .border(1.dp, colors.outline, shape).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(R.drawable.ic_search, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                    Box(Modifier.weight(1f).padding(start = 10.dp)) {
                        if (query.isEmpty()) Text(tr("Einstellungen durchsuchen"), style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        inner()
                    }
                    if (query.isNotEmpty()) IconButton({ query = "" }) { AppIcon(R.drawable.ic_close, tr("Suche leeren"), Modifier.size(18.dp)) }
                }
            })
        if (query.isBlank()) Unit
        else if (hits.isEmpty()) Text(tr("Keine passende Einstellung gefunden."), Modifier.testTag("settings-search-empty")
            .semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
        else hits.forEach { entry ->
            Surface({ focus.clearFocus(); query = ""; onOpen(entry) }, Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .testTag("settings-hit-${entry.page.name}-${entry.anchor ?: entry.titleKey.hashCode()}"),
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(entry.titleKey), style = MaterialTheme.typography.titleSmall)
                        Text(settingsPath(entry.page), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        entry.noteKey?.let { Text(tr(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    AppIcon(R.drawable.ic_chevron_right, null)
                }
            }
        }
    }
}
