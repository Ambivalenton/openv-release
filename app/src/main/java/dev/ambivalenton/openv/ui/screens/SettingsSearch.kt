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
    val anchor: String? = null, val available: Boolean = true, val noteKey: String? = null,
    /** Build 121: for a hit in a page's own texts, the passage around the word (already translated). */
    val excerpt: String? = null)

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

/**
 * M11: matches by name first, then by the start of a synonym, then anywhere in one. Build 121: and in every
 * text that is written on a settings page ([texts], generated from the pages themselves) – "Fuß" finds the
 * footprints as well as the walking pace. A word counts at the start of the text, at the start of one of its
 * words, and inside a compound ("abdruck" in "Fußabdrücke"). One hit per page from its texts; short texts
 * (names of settings) become the hit's title, longer ones are shown as a passage under the page's name.
 */
fun searchSettings(query: String, index: List<SettingsSearchEntry> = settingsSearchIndex(),
    texts: Map<SettingsPage, List<String>> = settingsPageTexts): List<SettingsSearchEntry> {
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
    val curated = index.map { it to score(it) }.filter { it.second > 0 }
    val found = texts.mapNotNull { (page, list) ->
        list.mapNotNull { german -> textHit(german, key)?.let { german to it } }
            .maxWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenBy { -it.first.length })
            ?.let { (german, s) -> pageTextEntry(page, german, key) to s }
    }.filter { (hit, _) -> curated.none { (c, _) -> c.page == hit.page && searchKey(tr(c.titleKey)) == searchKey(tr(hit.titleKey)) } }
    // Names and synonyms first, then the pages' own texts.
    return (curated.map { it.first to it.second + 4 } + found)
        .sortedByDescending { it.second }.map { it.first }.distinctBy { it.page to (it.anchor ?: it.titleKey) }.take(10)
}

/** Placeholders ("%1\$s") are not words of a text. */
private fun plainText(text: String) = text.replace(Regex("%\\d+\\\$[sdf]|%%"), " ").replace(Regex("\\s+"), " ").trim()

/** How well [key] matches one text of a page (in the current language or in German), 0 = not at all. */
private fun textHit(german: String, key: String): Int? {
    fun of(text: String): Int {
        val t = searchKey(plainText(text))
        if (!t.contains(key)) return 0
        val short = t.length <= 34 && !t.endsWith(".")
        val base = when {
            t.startsWith(key) -> 3
            Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(key)).containsMatchIn(t) -> 2
            else -> 1
        }
        return base + if (short) 1 else 0
    }
    return maxOf(of(tr(german)), of(german)).takeIf { it > 0 }
}

/** The hit for a text found on [page]: a short text names the setting itself, a long one is a passage. */
private fun pageTextEntry(page: SettingsPage, german: String, key: String): SettingsSearchEntry {
    val shown = plainText(tr(german))
    // "Uhrzeitlinie · Text im Balken · Fußabdrücke": the part that holds the word.
    val part = shown.split(" · ").firstOrNull { searchKey(it).contains(key) } ?: shown
    val name = part.length <= 34 && !part.endsWith(".") && !german.contains('%')
    return if (name) SettingsSearchEntry(if (part == shown) german else part, page, emptyList())
    else SettingsSearchEntry(page.titleKey, page, emptyList(), excerpt = settingsExcerpt(shown, key))
}

/** About 70 characters of [text] around the first place of [key], cut at word boundaries. */
internal fun settingsExcerpt(text: String, key: String): String {
    if (text.length <= 72) return text
    val at = searchKey(text).indexOf(key).coerceAtLeast(0)
    var from = (at - 24).coerceAtLeast(0); var to = (from + 72).coerceAtMost(text.length)
    if (to == text.length) from = (to - 72).coerceAtLeast(0)
    if (from > 0) from = text.indexOf(' ', from).takeIf { it in 0 until at } ?.plus(1) ?: from
    if (to < text.length) to = text.lastIndexOf(' ', to).takeIf { it > at } ?: to
    return (if (from > 0) "…" else "") + text.substring(from, to).trim() + if (to < text.length) "…" else ""
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
                        // The hint shrinks to fit (wide theme fonts, large type) instead of ending in "…".
                        if (query.isEmpty()) dev.ambivalenton.openv.ui.components.FittedText(tr("Einstellungen durchsuchen"),
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
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
                        entry.excerpt?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    }
                    AppIcon(R.drawable.ic_chevron_right, null)
                }
            }
        }
    }
}
