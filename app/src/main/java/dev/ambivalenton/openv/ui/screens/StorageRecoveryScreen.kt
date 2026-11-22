// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.data.repository.LocalDataRecovery
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.appThemeBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * K02: shown instead of the whole app (no navigation, no tabs) while the local store cannot be read.
 * "Erneut laden" reads the unchanged file again, "Sicherung importieren" replaces it after the usual
 * preview and confirmation, and "Leer starten" needs its own confirmation and a preserved copy.
 * The damaged file is copied to app-private storage before anything replaces it.
 */
@Composable
fun StorageRecoveryScreen(detail: String?, recovery: LocalDataRecovery? = rememberLocalDataRecovery(), modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var copyName by remember { mutableStateOf<String?>(null) }
    var copyFailed by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(recovery) {
        val copy = recovery?.preserveDamaged()
        copyName = copy?.name; copyFailed = recovery != null && copy == null
    }
    val import = rememberBackupImport()
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val source = recovery?.copies()?.firstOrNull()
        if (uri != null && source != null) scope.launch {
            message = try {
                withContext(Dispatchers.IO) { writeVerifiedBackup(context.contentResolver, uri, source.readBytes()) }
                tr("Kopie der beschädigten Daten gespeichert.") to false
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
                tr("Die Kopie konnte nicht gespeichert werden.") to true
            }
        }
    }
    Box(modifier.fillMaxSize().appThemeBackground().testTag("storage-recovery")) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr("Deine lokalen Daten konnten nicht geöffnet werden."), Modifier.semantics { heading() }.testTag("storage-recovery-title"),
                style = MaterialTheme.typography.headlineSmall)
            Text(tr("Öpen-V hat die Datei nicht verändert. Du kannst sie erneut laden oder eine Sicherung importieren."),
                style = MaterialTheme.typography.bodyLarge)
            Text(when {
                copyName != null -> tr("Eine Kopie der beschädigten Daten bleibt geschützt auf diesem Gerät erhalten.")
                copyFailed -> tr("Von den beschädigten Daten konnte keine Kopie angelegt werden. Leer starten ist deshalb gesperrt.")
                else -> tr("Eine Kopie der beschädigten Daten wird angelegt …")
            }, Modifier.testTag("storage-recovery-copy"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThemedButton({ recovery?.retry() }, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("storage-recovery-retry"), enabled = !busy) {
                Text(tr("Erneut laden"))
            }
            ThemedTonalButton({ import.pick() }, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("storage-recovery-import"),
                enabled = !busy && !import.busy && copyName != null) { Text(tr("Sicherung importieren")) }
            import.preview?.let { preview ->
                Surface(shape = dev.ambivalenton.openv.ui.theme.appCorner(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr("Sicherung prüfen"), style = MaterialTheme.typography.titleMedium)
                        BackupImportSummary(preview, current = null)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(import::discard, Modifier.heightIn(min = 48.dp)) { Text(tr("Abbrechen")) }
                            ThemedButton({ import.confirm() }, Modifier.heightIn(min = 48.dp).testTag("storage-recovery-confirm-import"),
                                enabled = !import.busy) { Text(tr("Daten ersetzen")) }
                        }
                    }
                }
            }
            import.error?.let { BackupMessage(it, failure = true) }
            import.result?.let { BackupMessage(it, failure = false) }
            message?.let { (text, failure) -> BackupMessage(text, failure) }
            HorizontalDivider()
            Text(tr("Ohne Sicherung kannst du mit leeren Daten neu beginnen."), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ confirmEmpty = true }, Modifier.heightIn(min = 48.dp).testTag("storage-recovery-empty"),
                    enabled = !busy && copyName != null) { Text(tr("Leer starten")) }
                TextButton({ saveCopy.launch("openv-beschaedigte-daten.json") }, Modifier.heightIn(min = 48.dp).testTag("storage-recovery-save-copy"),
                    enabled = copyName != null) { Text(tr("Kopie speichern")) }
            }
            detail?.let {
                Text(tr("Technischer Hinweis: %1\$s", it), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirmEmpty) AppAlertDialog(onDismissRequest = { confirmEmpty = false }, modifier = Modifier.testTag("storage-recovery-empty-dialog"),
        title = { Text(tr("Leer starten?")) },
        text = { Text(tr("Öpen-V legt neue, leere Daten an. Favoriten, Netzplan und Einstellungen beginnen von vorn. Die beschädigten Daten bleiben als Kopie auf diesem Gerät erhalten.")) },
        confirmButton = { TextButton({
            confirmEmpty = false; busy = true
            scope.launch {
                val ok = recovery?.startEmpty() == true
                busy = false
                if (!ok) message = tr("Leer starten war nicht möglich. Deine bisherige Datei bleibt unverändert.") to true
            }
        }, Modifier.testTag("storage-recovery-empty-confirm")) { Text(tr("Leer starten")) } },
        dismissButton = { TextButton({ confirmEmpty = false }) { Text(tr("Abbrechen")) } })
}

@Composable
private fun rememberLocalDataRecovery(): LocalDataRecovery? {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as? OpenVApp)?.koin?.getOrNull<LocalDataRecovery>() }
}
