// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import androidx.datastore.core.CorruptionException
import dev.ambivalenton.openv.data.model.StoredState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * K02: decides what DataStore does with a store file it cannot read. Without an explicit decision the
 * damaged file stays untouched and the read fails again; a decision is used for exactly one read.
 */
class StoreRecoveryGate {
    private val pending = AtomicReference<StoredState?>(null)
    fun offer(state: StoredState) = pending.set(state)
    fun withdraw() = pending.set(null)
    /** Called by the store's corruption handler. */
    fun replacementFor(error: CorruptionException): StoredState = pending.getAndSet(null) ?: throw error
}

/** The app's one store gate (the DataStore delegate is process-wide as well). */
object StoreRecovery {
    val gate = StoreRecoveryGate()
}

/**
 * K02: keeps a protected copy of a damaged store and replaces the store only on an explicit decision:
 * a confirmed import or a confirmed empty start. The copy lives in app-private storage and is never
 * deleted by the app; at most [keep] distinct copies are kept (the oldest goes first).
 */
class LocalDataRecovery(
    private val storeFile: File,
    private val recoveryDir: File,
    private val gate: StoreRecoveryGate,
    private val locations: LocationRepository,
    private val keep: Int = 3,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The protected copy of the current damaged file, created once per distinct content; null if it could not be made. */
    suspend fun preserveDamaged(): File? = withContext(Dispatchers.IO) {
        runCatching {
            if (!storeFile.isFile) return@runCatching null
            val bytes = storeFile.readBytes()
            val digest = sha256(bytes)
            recoveryDir.mkdirs()
            copies().firstOrNull { runCatching { sha256(it.readBytes()) == digest }.getOrDefault(false) }?.let { return@runCatching it }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(clock()))
            var target = File(recoveryDir, "openv-v1-damaged-$stamp.json")
            var n = 2
            while (target.exists()) target = File(recoveryDir, "openv-v1-damaged-$stamp-${n++}.json")
            val temp = File(recoveryDir, target.name + ".tmp")
            temp.writeBytes(bytes)
            check(temp.renameTo(target) && sha256(target.readBytes()) == digest)
            copies().drop(keep).forEach { it.delete() }
            target
        }.getOrNull()
    }

    /** Protected copies, newest first. */
    fun copies(): List<File> = recoveryDir.listFiles { f -> f.isFile && f.name.startsWith("openv-v1-damaged-") && f.name.endsWith(".json") }
        .orEmpty().sortedByDescending { it.name }

    /** "Erneut laden": reads the file again without changing it. */
    fun retry() = locations.reload()

    /**
     * Prepares a confirmed import into a damaged store: the damaged file is copied first, then the
     * imported state is offered for the one read that replaces it. Returns false (nothing changed)
     * when no protected copy could be made.
     */
    suspend fun prepareReplacement(state: StoredState): Boolean {
        if (locations.storageProblem.value == null) return true
        if (preserveDamaged() == null && storeFile.exists()) return false
        gate.offer(state)
        return true
    }

    /** "Leer starten" after confirmation: only with a preserved copy of the damaged data. */
    suspend fun startEmpty(): Boolean {
        if (!prepareReplacement(StoredState())) return false
        return try { locations.readOnce(); locations.reload(); true }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { gate.withdraw(); false }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
