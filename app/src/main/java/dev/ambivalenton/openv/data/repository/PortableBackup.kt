// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.StoredState
import dev.ambivalenton.openv.data.model.SearchHistory
import dev.ambivalenton.openv.data.model.TimetableProfile
import dev.ambivalenton.openv.data.preferences.mergeSearchHistory

internal fun portableBackup(state: StoredState): StoredState = state.copy(
    savedJourneys = emptyList(),
    history = mergeSearchHistory(state.history + state.savedJourneys.map { SearchHistory(it.query, it.savedAt) }),
    settings = TimetableProfile.normalize(state.settings),
)
