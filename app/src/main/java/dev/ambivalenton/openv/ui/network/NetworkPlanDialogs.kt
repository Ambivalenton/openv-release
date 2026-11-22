// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.FavoriteBadge
import dev.ambivalenton.openv.ui.components.FittedText

/** Below the plan: edit hints, the tray and the undo of a removal. */
@Composable
fun NetworkPlanFooter(store: StoredState, editing: Boolean, trayPick: String?,
                      onTrayPick: (String) -> Unit, onUndo: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    // No summary line ("Ab …: …") below the plan any more; the times sit on the lines.
    if (editing) {
        if (dev.ambivalenton.openv.ui.components.hintsShown)
            Text(tr("Kurz halten und ziehen verschiebt · Wischen von Favorit zu Favorit verbindet · Tippen bearbeitet · Linie antippen wählt die Richtung"),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        val tray = store.favorites.filter { store.network.node(it.id)?.point == null }
        if (tray.isNotEmpty()) {
            Text(tr("Ablage"), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tray.forEach { f ->
                    FilterChip(trayPick == f.id, { onTrayPick(f.id) }, label = { Text(f.label, maxLines = 1) },
                        leadingIcon = { FavoriteBadge(f, size = 24.dp, singleShape = true) }, modifier = Modifier.testTag("network-tray-${f.id}"))
                }
            }
            if (dev.ambivalenton.openv.ui.components.hintsShown) Text(if (trayPick != null) tr("Tippe auf einen freien Rasterpunkt, um den Favoriten dort abzulegen.")
                else tr("Tippe auf einen Favoriten in der Ablage und dann auf einen freien Rasterpunkt."),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
    if (onUndo != null) Surface(Modifier.fillMaxWidth().testTag("network-undo"), shape = MaterialTheme.shapes.medium, color = scheme.inverseSurface) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Verbindung entfernt"), Modifier.weight(1f), color = scheme.inverseOnSurface)
            TextButton(onUndo) { Text(tr("Rückgängig"), color = scheme.inversePrimary) }
        }
    }
}

/** Tap on a line in edit mode: both directions, one way each, or remove. */
@Composable
fun EdgeDirectionDialog(store: StoredState, edgeId: String, onDirection: (DirectionMode) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val edge = store.network.edges.firstOrNull { it.id == edgeId } ?: return onDismiss()
    val a = store.favorites.firstOrNull { it.id == edge.aId }?.label ?: return onDismiss()
    val b = store.favorites.firstOrNull { it.id == edge.bId }?.label ?: return onDismiss()
    AlertDialog(onDismissRequest = onDismiss, title = { FittedText(tr("Verbindung %1\$s – %2\$s", a, b)) },
        text = {
            Column(Modifier.testTag("edge-direction-menu")) {
                listOf(DirectionMode.BOTH to tr("Beide Richtungen (%1\$s ↔ %2\$s)", a, b),
                    DirectionMode.A_TO_B to tr("Von %1\$s nach %2\$s (%1\$s → %2\$s)", a, b),
                    DirectionMode.B_TO_A to tr("Von %1\$s nach %2\$s (%1\$s → %2\$s)", b, a)).forEach { (mode, title) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(edge.direction == mode, role = Role.RadioButton) { onDirection(mode) }
                        .testTag("edge-direction-${mode.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(edge.direction == mode, null)
                        Spacer(Modifier.width(12.dp))
                        Text(title)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onRemove, Modifier.fillMaxWidth().testTag("edge-remove")) { Text(tr("Verbindung entfernen"), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(tr("Fertig")) } })
}

/** Gesture-free way to draw a line (TalkBack action "Verbinden mit …"). */
@Composable
fun ConnectPickerDialog(store: StoredState, fromId: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val from = store.favorites.firstOrNull { it.id == fromId } ?: return onDismiss()
    AlertDialog(onDismissRequest = onDismiss, title = { FittedText(tr("%1\$s verbinden mit …", from.label)) },
        text = {
            Column(Modifier.testTag("connect-picker")) {
                store.favorites.filter { it.id != fromId }.forEach { f ->
                    val rejection = store.edgeRejection(fromId, f.id)
                    ListItem(headlineContent = { Text(f.label) },
                        supportingContent = rejection?.let { r -> { Text(when (r) {
                            EdgeRejection.ALREADY_CONNECTED -> tr("Bereits verbunden")
                            EdgeRejection.SAME_LOCATION -> tr("Gleicher Ort")
                            EdgeRejection.NOT_PLACED -> tr("In der Ablage")
                            EdgeRejection.LIMIT -> tr("Maximale Anzahl an Linien erreicht")
                            EdgeRejection.SAME_NODE -> ""
                        }) } },
                        leadingContent = { FavoriteBadge(f, size = 32.dp, singleShape = true) },
                        modifier = Modifier.fillMaxWidth().clickable(enabled = rejection == null, role = Role.Button) { onPick(f.id) })
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(tr("Abbrechen")) } })
}
