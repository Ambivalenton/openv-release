// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.appCorner
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/*
 * One import flow for Settings and the first-launch wizard, and a verified export.
 * The document picker accepts any file type: providers label JSON as text, binary or not at all,
 * and the content is checked anyway. Every failure names its cause; nothing is changed before the
 * user confirms the preview.
 */

/** Reads a picked document; falls back to the asset descriptor for providers without a plain stream. */
internal fun readBackupBytes(resolver:ContentResolver,uri:Uri):ByteArray {
    val input:InputStream=try {
        resolver.openInputStream(uri) ?: resolver.openAssetFileDescriptor(uri,"r")?.createInputStream()
    } catch(e:SecurityException) {
        throw BackupFormatException(tr("Öpen-V hat keinen Zugriff auf diese Datei. Bitte die Datei erneut über „Datei auswählen“ öffnen."),e)
    } catch(e:IOException) {
        throw BackupFormatException(tr("Die Datei konnte nicht geöffnet werden. Liegt sie in einem Cloud-Ordner, lade sie bitte zuerst auf das Gerät."),e)
    } ?: throw BackupFormatException(tr("Die Datei konnte nicht geöffnet werden."))
    return input.use {stream->
        val output=ByteArrayOutputStream();val buffer=ByteArray(16_384);var total=0
        while(true) {
            val n=stream.read(buffer);if(n<0)break
            total+=n
            if(total>MAX_BACKUP_BYTES)throw BackupFormatException(tr("Die Datei ist größer als 16 MB und damit keine Öpen-V-Sicherung."))
            output.write(buffer,0,n)
        }
        output.toByteArray()
    }
}

/**
 * Writes [bytes] and reads them back. Some providers do not support truncating writes ("wt"), others
 * ignore truncation; both used to leave an empty or mixed file behind while the app reported an
 * error in small print. A file that does not read back identically is deleted where possible.
 */
internal fun writeVerifiedBackup(resolver:ContentResolver,uri:Uri,bytes:ByteArray) {
    var written=false
    for(mode in listOf("wt","w","rwt")) {
        try {
            val out=resolver.openOutputStream(uri,mode) ?: continue
            out.use {it.write(bytes);it.flush()}
            written=true;break
        } catch(_:IllegalArgumentException) {} catch(_:UnsupportedOperationException) {} catch(_:java.io.FileNotFoundException) {}
    }
    val verified=written && runCatching {readBackupBytes(resolver,uri).contentEquals(bytes)}.getOrDefault(false)
    if(!verified) {
        runCatching {DocumentsContract.deleteDocument(resolver,uri)}
        throw IOException("backup not verified")
    }
}

/** State of one import: picking, reading, preview with notes, confirmation and the result. */
@Stable class BackupImportFlow internal constructor(private val scope:CoroutineScope,private val service:AppBackupService,
    private val resolver:ContentResolver,private val onImported:()->Unit) {
    var busy by mutableStateOf(false);internal set
    var preview by mutableStateOf<BackupImportPreview?>(null);internal set
    /** M14: the data the import would replace; null while it cannot be read (K02) or before a preview. */
    var current by mutableStateOf<dev.ambivalenton.openv.data.model.StoredState?>(null);internal set
    var error by mutableStateOf<String?>(null);internal set
    var result by mutableStateOf<String?>(null);internal set
    internal var launch:()->Unit={}
    fun pick() {if(!busy){error=null;result=null;launch()}}
    fun discard() {if(!busy)preview=null}
    internal fun read(uri:Uri) {
        busy=true;error=null;result=null;preview=null
        scope.launch {
            try {
                val inspected=withContext(Dispatchers.IO) {AppBackupCodec.inspect(readBackupBytes(resolver,uri))}
                current=service.currentState()
                preview=inspected
            }
            catch(e:CancellationException) {throw e}
            catch(e:BackupFormatException) {error=e.message}
            catch(_:Exception) {error=tr("Diese Datei ist keine gültige oder unterstützte Öpen-V-Sicherung. Deine Daten wurden nicht verändert.")}
            finally {busy=false}
        }
    }
    fun confirm() {
        val document=preview?.document ?: return
        if(busy)return
        busy=true
        scope.launch {
            try {
                val icon=service.import(document);preview=null;onImported()
                result=if(icon.isSuccess)tr("Sicherung vollständig importiert.") else tr("Daten importiert. Der Launcher konnte das App-Logo noch nicht aktualisieren.")
            }
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {error=tr("Die Sicherung konnte nicht gespeichert werden. Deine bisherigen Daten bleiben erhalten. Bitte erneut versuchen.")}
            finally {busy=false}
        }
    }
}

@Composable fun rememberBackupImport(onImported:()->Unit={}):BackupImportFlow {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val service=remember(context) {(context.applicationContext as OpenVApp).koin.get<AppBackupService>()}
    val latest by rememberUpdatedState(onImported)
    val flow=remember(service,scope) {BackupImportFlow(scope,service,context.contentResolver) {latest()}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri:Uri?->if(uri!=null)flow.read(uri)}
    flow.launch={picker.launch(arrayOf("*/*"))}
    return flow
}

/** M14: one row of the comparison "now → backup" (pure, unit-tested). */
data class BackupComparisonRow(val label:String,val current:Int?,val backup:Int) {
    val text:String get()=if(current==null) tr("%1\$s: %2\$s",label,backup) else tr("%1\$s: %2\$s → %3\$s",label,current,backup)
}

fun backupComparison(current:dev.ambivalenton.openv.data.model.StoredState?,backup:dev.ambivalenton.openv.data.model.StoredState):List<BackupComparisonRow> = listOf(
    BackupComparisonRow(tr("Favoriten"),current?.favorites?.size,backup.favorites.size),
    BackupComparisonRow(tr("Netzplanlinien"),current?.network?.edges?.size,backup.network.edges.size),
    BackupComparisonRow(tr("Vorgemerkte Fahrten"),current?.savedJourneys?.size,backup.savedJourneys.size),
    BackupComparisonRow(tr("Suchverlauf"),current?.history?.size,backup.history.size),
    BackupComparisonRow(tr("Haltestellen-Liste"),current?.boardStops?.size,backup.boardStops.size),
    BackupComparisonRow(tr("Persönliche Themes"),current?.personalThemes?.size,backup.personalThemes.size),
)

/**
 * What the file contains and what was repaired or skipped, before anything is replaced. M14: with
 * [current] each row compares the present data with the backup ("Favoriten 8 → 5").
 */
@Composable fun BackupImportSummary(preview:BackupImportPreview,modifier:Modifier=Modifier,
    current:dev.ambivalenton.openv.data.model.StoredState?=null) {
    val state=preview.document.state
    Column(modifier.testTag("backup-import-summary"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(preview.document.exportedAt>0) Text(tr("Sicherung vom %1\$s",java.time.Instant.ofEpochMilli(preview.document.exportedAt)
            .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))),
            style=MaterialTheme.typography.labelLarge)
        if(current!=null) Text(tr("Jetzt → Sicherung"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        backupComparison(current,state).forEach {row->
            val fewer=row.current!=null && row.backup<row.current
            Text(row.text,Modifier.testTag("backup-compare-row"),fontWeight=if(fewer) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
        }
        if(preview.notes.isNotEmpty()) Surface(Modifier.fillMaxWidth().testTag("backup-import-notes"),shape=appCorner(16.dp),
            color=MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(tr("Hinweise zur Datei"),style=MaterialTheme.typography.labelLarge)
                preview.notes.forEach {Text("• $it",style=MaterialTheme.typography.bodySmall)}
            }
        }
        Text(tr("Die vorhandenen Daten werden durch diese Sicherung ersetzt. Auch das gespeicherte App-Logo wird übernommen."),
            style=MaterialTheme.typography.bodySmall)
        if(current!=null && backupComparison(current,state).any {it.current!=null && it.backup<it.current})
            Text(tr("Die Sicherung enthält weniger Einträge als jetzt. Speichere vorher eine Sicherung, wenn du die neueren Daten behalten möchtest."),
                Modifier.testTag("backup-import-fewer"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    }
}

/** A clearly visible outcome (success or the concrete cause), read aloud by TalkBack. */
@Composable fun BackupMessage(text:String,failure:Boolean,modifier:Modifier=Modifier) {
    Surface(modifier.fillMaxWidth().testTag(if(failure)"backup-error" else "backup-status").semantics {liveRegion=LiveRegionMode.Polite},
        shape=appCorner(16.dp),color=if(failure)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor=if(failure)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(text,Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)
    }
}
