// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import kotlinx.coroutines.launch

/**
 * Import a backup right at the start of the first-launch wizard. A sheet rises from below
 * with a drag handle and – easier for first-time users – a close button. It explains in two sentences
 * what an import brings back, opens the document picker and shows what the file contains before
 * anything is replaced. After the import the wizard ends by itself: the backup is a completed setup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun OnboardingImportSheet(onDismiss:()->Unit) {
    val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    val scope=rememberCoroutineScope()
    val importer=rememberBackupImport()
    fun close() {if(!importer.busy) scope.launch {sheet.hide()}.invokeOnCompletion {onDismiss()}}
    ModalBottomSheet(onDismissRequest={if(!importer.busy)onDismiss()},sheetState=sheet,
        modifier=Modifier.testTag("onboarding-import-sheet"),dragHandle={BottomSheetDefaults.DragHandle()}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start=24.dp,end=12.dp,bottom=24.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Sicherung importieren"),Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.titleLarge)
                IconButton(::close,enabled=!importer.busy,modifier=Modifier.testTag("onboarding-import-close")) {
                    AppIcon(R.drawable.ic_close,tr("Schließen"))
                }
            }
            Column(Modifier.padding(end=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text(tr("Du hast Öpen-V schon benutzt? Mit deiner Sicherungsdatei holst du Favoriten, Netzplan, Suchverlauf, Einstellungen, Designs und persönliche Themes zurück."),
                    style=MaterialTheme.typography.bodyLarge)
                Text(tr("Die Datei erstellst du in der bisherigen App unter Einstellungen → Modi & Verhalten → Sichern & Übertragen → Sicherung speichern. Nach dem Wiederherstellen ist die Einrichtung abgeschlossen."),
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                importer.error?.let {BackupMessage(it,true)}
                importer.result?.let {BackupMessage(it,false)}
                importer.preview?.let {preview->
                    BackupImportSummary(preview,current=importer.current?.takeIf {it.favorites.isNotEmpty() || it.history.isNotEmpty()})
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End)) {
                        ThemedOutlinedButton({importer.pick()},enabled=!importer.busy) {Text(tr("Andere Datei"))}
                        ThemedButton({importer.confirm()},enabled=!importer.busy,modifier=Modifier.testTag("onboarding-import-confirm")) {Text(tr("Sicherung wiederherstellen"))}
                    }
                } ?: ThemedButton({importer.pick()},Modifier.fillMaxWidth().testTag("onboarding-import-pick"),enabled=!importer.busy) {
                    Text(if(importer.error==null) tr("Datei auswählen") else tr("Andere Datei auswählen"))
                }
                if(importer.busy) LinearProgressIndicator(Modifier.fillMaxWidth().semantics {contentDescription=tr("Sicherung wird verarbeitet")})
            }
        }
    }
}
