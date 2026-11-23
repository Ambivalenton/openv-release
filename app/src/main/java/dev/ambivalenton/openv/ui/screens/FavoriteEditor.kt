// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.screens

import androidx.compose.runtime.Composable
import dev.ambivalenton.openv.data.model.Favorite
import dev.ambivalenton.openv.data.model.Location

/** Kept as a source-compatible entry point for existing clients. */
@Composable
fun FavoriteEditor(
    existing: Favorite?,
    providerKey: String,
    search: suspend (String) -> List<Location>,
    onSave: (Favorite) -> Unit,
    onDismiss: () -> Unit,
    initialLocation: Location? = null,
    showSchedules:Boolean=true,
    onDelete:(()->Unit)?=null,
    networkRole:dev.ambivalenton.openv.data.model.NodeRole?=null,
    onNetworkRole:((dev.ambivalenton.openv.data.model.NodeRole)->Unit)?=null,
    scheduleConflict:((List<dev.ambivalenton.openv.data.model.HighlightRule>)->Favorite?)?=null,
    suggestions:List<dev.ambivalenton.openv.data.model.FavoritePlaceSuggestion> = emptyList(),
    otherFavorites:List<Favorite> = emptyList(),
    partnerFor:(String)->Favorite? = {null},
    onConnect:(String,String)->Boolean = {_,_->false},
) = dev.ambivalenton.openv.ui.dialogs.FavoriteEditDialog(existing, providerKey, search, onSave, onDismiss, initialLocation,showSchedules,
    onDelete=onDelete,networkRole=networkRole,onNetworkRole=onNetworkRole,scheduleConflict=scheduleConflict,
    suggestions=suggestions,otherFavorites=otherFavorites,partnerFor=partnerFor,onConnect=onConnect)

fun minuteOfDay(input: String) = dev.ambivalenton.openv.ui.dialogs.minuteOfDay(input)
