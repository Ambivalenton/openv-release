// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui

import dev.ambivalenton.openv.localization.tr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ambivalenton.openv.data.model.ColorHistory
import dev.ambivalenton.openv.data.repository.LocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Shared persistent palette state; the picker itself only owns an unsaved colour draft. */
class ColorPickerViewModel(private val locations: LocationRepository) : ViewModel() {
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    val history =
        locations.state
            .map { it.colorHistory }
            .catch { emit(ColorHistory()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ColorHistory())

    fun toggleFavorite(color: Long) {
        viewModelScope.launch {
            try {
                locations.toggleFavoriteColor(color)
                mutableError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableError.value = tr("Die Farb-Favoriten konnten nicht gespeichert werden.")
            }
        }
    }

    fun save(color: Long?, onSaved: () -> Unit) {
        viewModelScope.launch {
            try {
                color?.let { locations.rememberColor(it) }
                mutableError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A nonessential recent-colour history must not block the actual settings action.
                mutableError.value =
                    tr("Die zuletzt verwendeten Farben konnten nicht gespeichert werden.")
            }
            onSaved()
        }
    }
}
