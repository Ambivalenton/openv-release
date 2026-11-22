// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.onboarding

import dev.ambivalenton.openv.ui.components.AppOutlinedCard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.FittedText
import dev.ambivalenton.openv.ui.theme.appCorner

/** Explains the favourite network plan with words only: no route query or location permission is
 * needed to read this page, and existing users are not forced through setup again. */
@Composable internal fun NetworkPlanGuide(holdAction: dev.ambivalenton.openv.data.model.NetworkHoldAction = dev.ambivalenton.openv.data.model.NetworkHoldAction.MOVE) {
    Text(tr("Deine Favoriten bilden einen eigenen Netzplan: Verbinde Orte, zwischen denen du regelmäßig fährst, mit Linien wie auf einem echten Liniennetz."),
        Modifier.testTag("onboarding-network-plan"))
    GuideSection(tr("Bearbeiten")) {
        Text(tr("Unter Suchen öffnet der Stift über dem Netzplan das Bearbeiten: Das Raster erscheint, die Namen werden sichtbar und die Favoriten wackeln leicht. Kurz halten und ziehen verschiebt einen Favoriten. Wischen von einem Favoriten zum anderen zieht eine Linie, zuerst in beide Richtungen."))
        Text(tr("Das Plus fügt einen Favoriten im nächsten freien Feld hinzu. Das Zahnrad öffnet die Favoriten-Einstellungen mit Umkreis und Rastergröße."))
        Text(tr("Tippe auf eine Linie, um die Richtung zu wählen: beide Richtungen, nur hin oder nur zurück. Tippe auf einen Favoriten, um ihn zu bearbeiten."))
    }
    GuideSection(tr("Unterwegs")) {
        Text(tr("Ein Tipp auf einen Favoriten sucht die Verbindung ab deinem Standort. Ein Tipp auf eine Linie sucht nur zwischen ihren beiden Orten, vom näheren zum weiteren."))
        // M01: the hint follows the hold action that is really set (from a fresh install: moving).
        Text(dev.ambivalenton.openv.ui.network.networkHoldHint(holdAction), Modifier.testTag("network-guide-hold"))
        Text(tr("„Hier starten“ in der Liste „Favoriten und Strecken“ fragt, ob der Start nur für diese Suche oder für alle Suchen gilt."))
        Text(tr("Die Namen der Favoriten sind ausgeblendet. Halte eine freie Stelle im Netzplan gedrückt, um sie zu sehen; beim Loslassen verschwinden sie wieder."))
    }
    GuideSection(tr("In der Nähe eines Favoriten")) {
        Text(tr("Bist du in der Nähe eines Favoriten (einstellbar, anfangs 500 Meter), rechnet die App ab dort. Die Übersicht zeigt dann deine Wege zu den verbundenen Favoriten, zum Beispiel den Arbeitsweg oder den Nachhauseweg."))
        Text(tr("Bei Zuhause – Bahnhof – Arbeit wird direkt zwischen Zuhause und Arbeit gesucht. Gibt es zusätzlich eine direkte Linie, zeigt die Übersicht die schnellere Verbindung."))
    }
}

@Composable private fun GuideSection(title:String,content:@Composable ColumnScope.()->Unit) {
    AppOutlinedCard(Modifier.fillMaxWidth(),shape=appCorner(20.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            FittedText(title,Modifier.semantics {heading()},style=MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
