// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import dev.ambivalenton.openv.data.model.TimetableProfile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.ambivalenton.openv.data.model.AppPublication
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.BuildConfig
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*

/** A reading desk: guidance first, legal documents separate, project story on demand. */
@Composable
internal fun InfoHelpContent(onDocument: (String) -> Unit, onGlossary: () -> Unit,
                             onTutorials: () -> Unit, onSetup: () -> Unit, onEasterEgg: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Öpen-V", Modifier.clickable(role = Role.Button, onClick = onEasterEgg).heightIn(min = 48.dp),
                style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(tr("Deine Orte. Deine Wege. Deine App."), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(tr("Kostenlos, werbefrei und nach deinen Vorstellungen gestaltbar."), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    Section(tr("Anleitungen & Begriffe")) {
        HelpEntry(tr("So nutzt du Öpen-V"), tr("Vom ersten Favoriten bis zur Reisebegleitung."), "help-open-guide") { onDocument("help") }
        HelpEntry(tr("Begriffe kurz erklärt"), tr("Begriffe und Funktionen verständlich erklärt."), "help-open-glossary", onGlossary)
        HelpEntry(tr("Wo funktioniert Öpen-V?"), tr("Regionen, Offline-Nutzung und Grenzen der Auskunft."), "help-open-coverage") { onDocument("coverage") }
        SettingsDisclosure(tr("Noch einmal entdecken"), "help-restart", tr("Bedienhinweise und Einrichtung")) {
            TextButton(onTutorials) { Text(tr("Bedienhinweise erneut zeigen")) }
            TextButton(onSetup) { Text(tr("Einrichtung erneut öffnen")) }
        }
    }
    val context = LocalContext.current
    Section(tr("Rechtliches")) {
        if (TimetableProfile.sourceNotice) {
            HelpEntry(tr("Datenschutz im Browser"), tr("Datenschutzerklärung online lesen."), "help-open-privacy-web") {
                openPublicationLink(context, AppPublication.privacyUrl)
            }
            HelpEntry(tr("Support kontaktieren"), AppPublication.supportEmail, "help-open-support") {
                openPublicationLink(context, "mailto:" + AppPublication.supportEmail)
            }
        }
        HelpEntry(tr("Datenschutzerklärung"), tr("Was auf deinem Gerät bleibt und was Dienste erhalten."), "help-open-privacy") { onDocument("privacy") }
        HelpEntry(tr("Herausgeber & Nutzung"), tr("Verantwortliche Person und Hinweise zur Auskunft."), "help-open-publisher") { onDocument("legal") }
        HelpEntry(tr("Lizenzen & Datenquellen"), tr("Software, Schriften, Karten und Fahrplandaten."), "help-open-licenses") { onDocument("licenses") }
    }
    SettingsDisclosure(tr("Über Öpen-V"), "help-about", tr("Fahrplanauskunft für deine täglichen Wege")) {
        Text(tr("Speichere deine Lieblingsorte, verbinde sie im Netzplan und schau mit einem Tipp, wann der nächste Bus oder Zug fährt."))
        Text(tr("Öpen-V ist kostenlos, werbefrei und quelloffen. Die GPL-3.0-or-later erlaubt dir, den App-Code zu verändern und weiterzugeben. Für die eingebundenen Daten und Dienste gelten eigene Bedingungen."))
    }
    Text(tr("Version %1\$s · Build %2\$s", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HelpEntry(title: String, detail: String, tag: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).clickable(role = Role.Button, onClick = action)
        .heightIn(min = 64.dp).padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppIcon(R.drawable.ic_chevron_right, null, Modifier.padding(start = 12.dp))
    }
}


