// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border

import dev.ambivalenton.openv.ui.components.AppModalBottomSheet as ModalBottomSheet

import dev.ambivalenton.openv.localization.tr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.*
import dev.ambivalenton.openv.ui.AppViewModel
import dev.ambivalenton.openv.ui.components.*
import androidx.compose.ui.platform.LocalConfiguration

/** All station membership controls live beside the board, with immediate, persistent actions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BoardStopsSheet(
    state: StoredState,
    vm: AppViewModel,
    onPermission: () -> Unit,
    onDismiss: () -> Unit,
    /** M08: the picked stop becomes the fixed stop of the board (works without location access). */
    chooseFixed: Boolean = false,
) {
    val locale = LocalConfiguration.current.locales[0]
    var searchVersion by rememberSaveable { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    val clock = rememberWallClock().toInstant().toEpochMilli()
    val gps = state.presence?.takeIf { it.isFresh(clock) }?.location
    val places = remember(state.favorites, state.boardStops, state.boardExcludedKeys,
        state.boardDisabledKeys, state.settings.board, gps, locale) { sortedBoardPlaces(state, gps) }
    fun label(place: Location) = state.favorites.firstOrNull {
        boardLocationKey(it.location) == boardLocationKey(place)
    }?.label ?: place.name
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false).imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "heading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FittedText(tr("Haltestellen-Liste"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onDismiss) { AppIcon(R.drawable.ic_close, tr("Haltestellen-Liste schließen")) }
                }
                Text(tr("Wähle, welche Orte auf deiner Anzeigetafel berücksichtigt werden."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "controls") {
                key(searchVersion) {
                    PlaceSearchField(
                        if (chooseFixed) tr("Feste Haltestelle wählen") else tr("Haltestelle hinzufügen"), null,
                        "board:${TimetableProfile.identity(state.settings)}",
                        vm::findBoardStops,
                        { stop ->
                            if (stop != null) {
                                if (chooseFixed) { vm.selectBoardStop(stop); onDismiss() } else vm.addBoardStop(stop)
                                focusManager.clearFocus()
                                searchVersion++
                            }
                        },
                        expressive = true,
                        fastSearch = remember(vm) {dev.ambivalenton.openv.ui.components.PlaceSearchGateway(
                            {query->vm.localLocationMatches(query).filter {it.kind==LocationKind.STOP}},vm::boardStopSuggestions,startsImmediately=true)},
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardStopSort.entries.forEach { sort ->
                        FilterChip(state.settings.board.listSort == sort,
                            { vm.settings { it.copy(board = it.board.copy(listSort = sort)) } },
                            label = { Text(sort.title) })
                    }
                }
                if (state.settings.board.listSort == BoardStopSort.DISTANCE && gps == null) {
                    Text(tr("Für die Entfernung fehlt ein frischer GPS-Standort. Bis dahin ist die Liste alphabetisch sortiert."),
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onPermission) { Text(tr("Standort freigeben / aktualisieren")) }
                }
            }
            item(key = "auto-selection") {
                // M08: the automatic choice only picks from this list; its reach is said before the tap.
                FilterChip(activeBoardSelection(state,includeRotation=false) == null,
                    { vm.selectBoardStop(null) },
                    label = { Text(tr("Nächstgelegene aus meiner Liste")) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("board-auto-selection"))
                Text(if (places.isEmpty()) tr("Noch leer: Füge Haltestellen hinzu, damit die Automatik eine davon wählen kann.")
                    else tr("Wählt die nächste aktive Haltestelle aus dieser Liste, nicht aus der Umgebung."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (places.isEmpty()) item(key = "empty") {
                Text(tr("Füge eine Haltestelle hinzu oder speichere einen Favoriten."), Modifier.padding(vertical = 20.dp))
            }
            items(places, key = ::boardLocationKey) { place ->
                val key = boardLocationKey(place)
                val favorite = state.favorites.firstOrNull { boardLocationKey(it.location) == key }
                val enabled = key !in state.boardDisabledKeys
                val title = label(place)
                // M16: the row keeps full contrast; the state is told by the box and the word "Ausgeblendet".
                Surface(Modifier.fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).toggleable(enabled, role = Role.Checkbox) {
                            vm.setBoardStopEnabled(place, it)
                        }.semantics { stateDescription = if (enabled) tr("Auf der Tafel") else tr("Ausgeblendet") }
                            .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(enabled, null)
                            // M16: the favourite's own colour shows in its mark; the name uses a colour
                            // readable on exactly this surface.
                            favorite?.let { f -> Box(Modifier.padding(start = 4.dp).size(14.dp).background(
                                f.accentSeed?.let { seed -> Color(seed) } ?: MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)) }
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                val distance = gps?.let { distanceMeters(it, place) }
                                val details = listOfNotNull(
                                    if (!enabled) tr("Ausgeblendet") else if (favorite != null) tr("Favorit") else null,
                                    if (title != place.name) place.name else null,
                                    distance?.let { if (it < 1000) tr("%1\$s m Luftlinie", it.toInt()) else String.format(locale, tr("%.1f km Luftlinie"), it / 1000) },
                                )
                                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton({ vm.selectBoardStop(place) }, enabled = enabled) {
                            AppIcon(R.drawable.ic_navigation, tr("%1\$s auf der Anzeigetafel anzeigen", title),
                                tint = if (state.settings.board.selectedStop?.let(::boardLocationKey) == key)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ vm.removeBoardStop(place) }) {
                            AppIcon(R.drawable.ic_delete, tr("%1\$s nur aus der Anzeigetafel entfernen", title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
