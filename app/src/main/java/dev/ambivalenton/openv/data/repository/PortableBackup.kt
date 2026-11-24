// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.StoredState
import dev.ambivalenton.openv.data.model.SearchHistory
import dev.ambivalenton.openv.data.model.TimetableProfile
import dev.ambivalenton.openv.data.preferences.mergeSearchHistory
import dev.ambivalenton.openv.data.reliability.ReliabilityMemory

internal fun portableBackup(state: StoredState): StoredState = state.copy(
    savedJourneys = emptyList(),
    history = mergeSearchHistory(state.history + state.savedJourneys.map { SearchHistory(it.query, it.savedAt) }),
    settings = TimetableProfile.normalize(state.settings),
    // Build 117: own punctuality reports never leave the device, not even in a backup file.
    reliability = ReliabilityMemory(),
)
