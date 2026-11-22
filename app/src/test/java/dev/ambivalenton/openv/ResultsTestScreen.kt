// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv

import androidx.compose.runtime.*
import dev.ambivalenton.openv.data.model.Location
import dev.ambivalenton.openv.ui.AppUiState
import dev.ambivalenton.openv.ui.screens.ResultsScreen
import dev.ambivalenton.openv.ui.selectConnection

/** Interactive screen fixture follows the same explicit state/action contract as AppViewModel. */
@Composable
fun ResultsTestScreen(
    initialUi: AppUiState,
    onRetry: () -> Unit,
    onPermission: () -> Unit,
    onManual: () -> Unit,
    onSaveFavorite: (Location) -> Unit,
) {
    var ui by remember(initialUi) { mutableStateOf(initialUi) }
    ResultsScreen(
        ui,
        onRetry,
        onPermission,
        onManual,
        onSaveFavorite,
        { ui = ui.selectConnection(it) },
        { ui = ui.copy(isListViewVisible = true, selectedTripId = null) },
        { ui = ui.copy(isListViewVisible = false, selectedTripId = null) },
    )
}
