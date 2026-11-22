// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.appCorner
import dev.ambivalenton.openv.ui.components.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.*
import java.time.LocalDate

@Composable internal fun BackupTransferButtons(busy:Boolean,onExport:()->Unit,onImport:()->Unit){
    Surface(Modifier.fillMaxWidth().testTag("backup-transfer"),shape=appCorner(22.dp),
        color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Row(Modifier.height(IntrinsicSize.Min),verticalAlignment=Alignment.CenterVertically){
            // N03: everyday words instead of "Export"/"Import". The labels wrap only between
            // words (two lines at most) and get smaller before a word would be split.
            val padding=PaddingValues(horizontal=8.dp,vertical=8.dp)
            // The labels take the container's own ink, so they stay readable in every design.
            val ink=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContentColor=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.5f))
            TextButton(onExport,Modifier.weight(1f).heightIn(min=64.dp).testTag("backup-export"),enabled=!busy,contentPadding=padding,colors=ink){
                FittedText(tr("Sicherung speichern"),Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleMedium,
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
            VerticalDivider(Modifier.height(30.dp),color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.3f))
            TextButton(onImport,Modifier.weight(1f).heightIn(min=64.dp).testTag("backup-import"),enabled=!busy,contentPadding=padding,colors=ink){
                FittedText(tr("Sicherung wiederherstellen"),Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleMedium,
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
        }
    }
}

/** Android's document picker grants access to the selected file at the moment of the action.
 * Verified export (the written file is read back) and the shared tolerant import flow. */
@Composable internal fun BackupSettings(onImported:()->Unit={}){
    val context=LocalContext.current;val resolver=context.contentResolver
    val service=remember(context){(context.applicationContext as OpenVApp).koin.get<AppBackupService>()}
    val scope=rememberCoroutineScope()
    var exporting by remember{mutableStateOf(false)};var exportStatus by remember{mutableStateOf<Pair<String,Boolean>?>(null)}
    val importer=rememberBackupImport(onImported)
    val busy=exporting||importer.busy
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri:Uri?->
        if(uri!=null){exporting=true;exportStatus=null;scope.launch{
            try{
                val size=withContext(Dispatchers.IO){val bytes=service.export();writeVerifiedBackup(resolver,uri,bytes);bytes.size}
                exportStatus=tr("Sicherung gespeichert und geprüft (%1\$s KB).",(size+1023)/1024) to false
            }catch(e:CancellationException){throw e}catch(_:Exception){
                exportStatus=tr("Die Sicherung konnte an diesem Ort nicht vollständig gespeichert werden; es bleibt keine unvollständige Datei zurück. Bitte einen anderen Speicherort wählen, zum Beispiel „Downloads“ auf dem Gerät.") to true
            }finally{exporting=false}
        }}
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        BackupTransferButtons(busy,{exportStatus=null;export.launch("OepenV_Sicherung_${LocalDate.now()}.json")},{exportStatus=null;importer.pick()})
        Text(tr("Sichert Favoriten, Verlauf, Einstellungen und persönliche Themes als JSON-Datei. Die Datei enthält persönliche Orte und Verläufe."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        exportStatus?.let{(text,failure)->BackupMessage(text,failure)}
        importer.error?.let{
            BackupMessage(it,true)
            // M14: every refusal names its cause; the matching step is another file.
            TextButton({importer.pick()},Modifier.heightIn(min=48.dp).testTag("backup-import-other"),enabled=!busy){Text(tr("Andere Datei auswählen"))}
        }
        importer.result?.let{BackupMessage(it,false)}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().semantics{contentDescription=tr("Sicherung wird verarbeitet")})
    }
    // M14: compare before replacing; the current data can be saved first, only "Daten ersetzen" replaces.
    importer.preview?.let{preview->AlertDialog(onDismissRequest={importer.discard()},title={Text(tr("Sicherung wiederherstellen?"))},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            BackupImportSummary(preview,current=importer.current)
            TextButton({exportStatus=null;export.launch("OepenV_Sicherung_vorher_${LocalDate.now()}.json")},
                Modifier.heightIn(min=48.dp).testTag("backup-export-before-import"),enabled=!busy){Text(tr("Vorherige Sicherung exportieren"))}
            exportStatus?.let{(text,failure)->BackupMessage(text,failure)}
        }},
        confirmButton={TextButton({importer.confirm()},enabled=!busy,modifier=Modifier.testTag("backup-import-confirm")){Text(tr("Daten ersetzen"))}},
        dismissButton={TextButton({importer.discard()},enabled=!importer.busy){Text(tr("Abbrechen"))}})}
}
