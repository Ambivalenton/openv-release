// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.glance.state.GlanceStateDefinition
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The two widget families never share a Glance state file, even for the same numeric ID. */
abstract class NamespacedWidgetState(private val namespace: String) :
    GlanceStateDefinition<Preferences> {
    private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

    override fun getLocation(context: Context, fileKey: String): File {
        val safeKey = fileKey.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        return File(context.filesDir, "datastore/${namespace}_$safeKey.preferences_pb")
    }

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<Preferences> {
        val file = getLocation(context.applicationContext, fileKey)
        return stores.computeIfAbsent(file.absolutePath) {
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            ) {
                file
            }
        }
    }
}

object QuickWidgetStateDefinition : NamespacedWidgetState("openv_quick_v2")

object DepartureBoardStateDefinition : NamespacedWidgetState("openv_departure_board_v2")
