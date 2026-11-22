// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository
import android.content.Context
import android.util.AtomicFile
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.launcher.LauncherIconManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.*
import java.io.File

@Serializable private data class ImportRecovery(val token:String,val previousTexts:List<SeenEditorialText>)

/** A durable journal makes the SQLite + DataStore import recoverable after process death. */
class AppBackupService(context:Context,private val locations:LocationRepository,
    private val editorial:EditorialHistoryRepository,private val icons:LauncherIconManager,
    /** K02: present in the app; it lets a confirmed import replace a damaged store. */
    private val recovery:LocalDataRecovery?=null){
    private val lock=Mutex()
    private val journal=AtomicFile(File(context.applicationContext.filesDir,"backup-import-recovery.json"))
    private suspend fun recoverLocked(){
        if(!journal.baseFile.exists())return
        val pending=AppBackupCodec.json.decodeFromString<ImportRecovery>(journal.readFully().decodeToString())
        // A damaged store is read directly (it throws instead of waiting for a repair).
        val token=try{locations.readOnce().lastImportToken}catch(e:CancellationException){throw e}catch(_:Exception){return}
        if(token!=pending.token)editorial.replaceAll(pending.previousTexts)
        journal.delete()
    }
    suspend fun recoverIfNeeded()=withContext(Dispatchers.IO){lock.withLock{recoverLocked()}}
    suspend fun export():ByteArray=withContext(Dispatchers.IO){lock.withLock{
        recoverLocked()
        AppBackupCodec.encode(locations.readOnce(),icons.current(),editorial.snapshot(),System.currentTimeMillis())
    }}
    /** M14: the current store for the import comparison; null while it cannot be read (K02). */
    suspend fun currentState():StoredState?=withContext(Dispatchers.IO){
        try{locations.readOnce()}catch(e:CancellationException){throw e}catch(_:Exception){null}
    }
    suspend fun import(document:AppBackupDocument)=withContext(Dispatchers.IO+NonCancellable){lock.withLock{
        // K02: into a damaged store only after its protected copy exists; the import replaces it.
        if(recovery!=null && !recovery.prepareReplacement(document.state.copy(launcherLogo=document.launcherLogo)))
            throw java.io.IOException("damaged store could not be preserved")
        recoverLocked();val token=newId()
        editorial.replaceForImport(document.textHistory,stage={old->
            val stream=journal.startWrite()
            try{stream.write(AppBackupCodec.json.encodeToString(ImportRecovery(token,old)).encodeToByteArray());journal.finishWrite(stream)}
            catch(e:Exception){journal.failWrite(stream);throw e}
        }){locations.replaceFromBackup(document.state.copy(launcherLogo=document.launcherLogo),token)}
        // The DataStore token is the durable commit marker; deleting the journal is only cleanup.
        runCatching{journal.delete()}
        // K02: screens waiting for a readable store read it again.
        locations.reload()
        icons.select(document.launcherLogo)
    }}
}
