// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.AppIcon
import dev.ambivalenton.openv.ui.components.DynamicSettingsTile
import dev.ambivalenton.openv.ui.components.SettingsTileKind
import dev.ambivalenton.openv.ui.components.secretHold

enum class SettingsPage(val titleKey: String, val detailKey: String="") {
    ROOT("Einstellungen"),
    // Formerly the fold-out "Modi & Extras" on the root page.
    MODES("Modi & Verhalten","Schwarz/Weiß, Ruhe-, Strom- und Planermodus, Minimalismus, Gesten und Datensicherung"),
    DESIGN("Design & Darstellung","App-Design, Anzeigetafel und Schrift"),
    APP_THEME("App-Design","Designs und eigene Farben"),
    BOARD("Anzeigetafel-Design","Design, Farben und Anzeigeoptionen"),
    SENTENCES("Übersicht-Design","Wähle, welche Angaben die Übersicht zeigt."),
    TYPOGRAPHY("Schrift & Lesbarkeit","Schriftgröße, Lesbarkeit und Formen"),
    TIMELINE("Balkendiagramm-Design","Formen, Linien und Farben deiner Verbindungen"),
    NETWORK_PLAN("Netzplan-Design","Linien, Stationen und Fahrzeuge im Favoriten-Netzplan"),
    WIDGET("Favoriten-Widget","Favoriten auf dem Startbildschirm"),
    FAVORITES("Favoriten","Orte, Symbole und Zeitregeln"),
    TRAVEL("Fahrt & Navigation","Suche, Suchtreffer und Fußwege"),
    SEARCH("Verbindungssuche","Verbindungswahl, Puffer und Abfahrtsumkreis"),
    SEARCH_PRIORITY("Reihenfolge der Suchtreffer","Favoriten, Haltestellen und Adressen"),
    WALKING_TIMELINE("Fußwege im Balkendiagramm","Anzeige und Gehgeschwindigkeit"),
    WALKING("Fußwege & Navigation","Gehtempo, Gehzeiten und Navigation"),
    SERVICES("Dienste & Daten","Fahrplan, Ortssuche, Karten und Suchverlauf"),
    INFO("Info & Hilfe","Anleitungen, Begriffe und rechtliche Informationen"),
    LEGAL("Rechtliches","Datenschutz, Herausgeber und Lizenzen"),
    HELP("Hilfe","Einführung und Reichweite"),
    APP_LOGO("App-Logo","Dein Symbol auf dem Startbildschirm");

    val parent: SettingsPage? get()=when(this) {
        ROOT -> null
        APP_THEME,BOARD,SENTENCES,TYPOGRAPHY,TIMELINE,NETWORK_PLAN,APP_LOGO -> DESIGN
        WIDGET -> null
        // "Startpunkt" is gone: the start field in Suchen offers the manual start and the GPS switch.
        SEARCH,SEARCH_PRIORITY,WALKING_TIMELINE,WALKING -> TRAVEL
        // Services and data are found under info & help.
        LEGAL,HELP,SERVICES -> INFO
        else -> ROOT
    }
    val kind: SettingsTileKind get()=if(this==SEARCH_PRIORITY) SettingsTileKind.INLINE else SettingsTileKind.PAGE
    val children: List<SettingsPage> get()=entries.filter {it.parent==this && it!=WALKING_TIMELINE}
}

@Stable class SettingsNavigationState(initial: SettingsPage=SettingsPage.ROOT) {
    var page by mutableStateOf(if(initial==SettingsPage.WALKING_TIMELINE)SettingsPage.WALKING else if(initial==SettingsPage.WIDGET)SettingsPage.DESIGN else if(initial in setOf(SettingsPage.LEGAL,SettingsPage.HELP)) SettingsPage.INFO else initial)
        private set
    fun open(destination: SettingsPage) {page=if(destination==SettingsPage.WALKING_TIMELINE)SettingsPage.WALKING else if(destination==SettingsPage.WIDGET)SettingsPage.DESIGN else if(destination in setOf(SettingsPage.LEGAL,SettingsPage.HELP)) SettingsPage.INFO else destination}
    /** M11: the frame a search hit points to; the page consumes it once it has scrolled there. */
    var anchor by mutableStateOf<String?>(null)
    fun openAt(destination: SettingsPage, target: String?) {open(destination);anchor=target}
    fun reset() {page=SettingsPage.ROOT}
    fun back(): Boolean {
        val parent=page.parent ?: return false
        page=parent
        return true
    }
}

@Composable fun rememberSettingsNavigationState(): SettingsNavigationState = rememberSaveable(
    saver=Saver(save={it.page.name},restore={SettingsNavigationState(SettingsPage.entries.firstOrNull {page->page.name==it} ?: SettingsPage.ROOT)})
) {SettingsNavigationState()}

@Composable internal fun SettingsMenu(page:SettingsPage,onOpen:(SettingsPage)->Unit,
    excluded:Set<SettingsPage> = emptySet(),onSecretHold:((SettingsPage)->Unit)?=null,inlineContent:@Composable (SettingsPage)->Unit={}) {
    page.children.filterNot {it in excluded || it==SettingsPage.WALKING_TIMELINE}.forEach {child->
        // 2.8: three seconds on "Design & Darstellung" open the hidden window; a tap still opens the page.
        val secret=if(child==SettingsPage.DESIGN && onSecretHold!=null) Modifier.secretHold {onSecretHold(child)} else Modifier
        Box(secret) {
            DynamicSettingsTile(tr(child.titleKey),tr(child.detailKey),child.kind,{onOpen(child)},
                tag="settings-${child.name}") {inlineContent(child)}
        }
    }
}
