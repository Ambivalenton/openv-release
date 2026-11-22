// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.NetworkCountdown
import dev.ambivalenton.openv.ui.components.AppIcon
import dev.ambivalenton.openv.ui.components.DesignPickerWindow
import dev.ambivalenton.openv.ui.components.FittedText
import dev.ambivalenton.openv.ui.components.ToggleRow
import java.time.ZonedDateTime

/*
 * Design & Darstellung → Netzplan-Design (build 70), built like the bar chart and board designs: a
 * card with a preview of the current design opens a window with the switch for the moving vehicles
 * and the list of designs, each with its own preview of the same small sample plan.
 */

private fun sampleFavorite(id: String, label: String, icon: FavoriteIcon, shape: IconStyle, accent: Long) =
    Favorite(id = id, label = label, location = Location(label, latitude = 51.5, longitude = 7.2, kind = LocationKind.COORDINATE),
        icon = icon, accentSeed = accent, shape = shape)

/** A fixed sample plan of five favourites on a 6 × 3 grid: the same for every design and every person. */
internal val networkPreviewStore: StoredState by lazy {
    val favorites = listOf(
        sampleFavorite("preview-home", tr("Zuhause"), FavoriteIcon.HOME, IconStyle.HEXAGON, 0xFF00897B),
        sampleFavorite("preview-work", tr("Arbeit"), FavoriteIcon.WORK, IconStyle.SQUIRCLE, 0xFF3949AB),
        sampleFavorite("preview-school", tr("Schule / Uni"), FavoriteIcon.SCHOOL, IconStyle.CIRCLE, 0xFFEF6C00),
        sampleFavorite("preview-fitness", tr("Fitness"), FavoriteIcon.FITNESS_CENTER, IconStyle.ROUNDED, 0xFFC62828),
        sampleFavorite("preview-cafe", tr("Café"), FavoriteIcon.COFFEE, IconStyle.PILL, 0xFF8E24AA),
    )
    // A small ring without crossings: every line, bend and vehicle stays visible even in a narrow card.
    val points = listOf(GridPoint(0, 1), GridPoint(5, 0), GridPoint(2, 0), GridPoint(2, 2), GridPoint(5, 2))
    StoredState(
        favorites = favorites,
        network = TransitNetwork(
            nodes = favorites.zip(points).map { (f, p) -> NetworkNode(f.id, p) },
            edges = listOf(
                NetworkEdge("preview-line-1", "preview-home", "preview-school"),
                NetworkEdge("preview-line-2", "preview-school", "preview-work"),
                NetworkEdge("preview-line-3", "preview-home", "preview-fitness"),
                NetworkEdge("preview-line-4", "preview-fitness", "preview-cafe"),
                NetworkEdge("preview-line-5", "preview-work", "preview-cafe", DirectionMode.A_TO_B),
            ),
            columns = 6, rows = 3,
        ),
    )
}

/** The sample plan in [design]; vehicles move only when [animate] (and the motion settings allow it). */
@Composable fun NetworkPlanDesignPreview(design: NetworkPlanDesign, vehicles: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val now = remember { ZonedDateTime.now() }
    // The sheet reaches 6 dp beyond the plan; the padding keeps it inside the card.
    Box(modifier.fillMaxWidth().padding(6.dp).testTag("network-design-preview-${design.name}"), contentAlignment = Alignment.Center) {
        NetworkPlan(networkPreviewStore, now, editing = false, anchorId = null, countdown = NetworkCountdown(), trayPick = null,
            actions = NetworkPlanActions(), preview = NetworkPlanPreview(design, vehicles, animate))
    }
}

/** First entry of Design & Darstellung for the plan: the whole card opens the design window. */
@Composable fun NetworkPlanDesignSelector(settings: AppSettings, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("network-design-selector"), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_subway, null, Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    FittedText(tr("Netzplan-Design"), style = MaterialTheme.typography.titleMedium)
                    Text(settings.networkPlanDesign.title, style = MaterialTheme.typography.bodySmall)
                }
                AppIcon(R.drawable.ic_chevron_right, tr("Design-Auswahl öffnen"))
            }
            NetworkPlanDesignPreview(settings.networkPlanDesign, settings.networkVehicles, animate = false)
        }
    }
}

@Composable fun NetworkPlanDesignSettings(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    NetworkPlanDesignSelector(settings) { picker = true }
    if (picker) NetworkPlanDesignPicker(settings, update) { picker = false }
}

/** The design window: the vehicle switch on top, below it the list of designs with previews. */
@Composable fun NetworkPlanDesignPicker(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, onDismiss: () -> Unit) {
    DesignPickerWindow(tr("Netzplan-Design wählen"), "network-design-picker", onDismiss, false, null) {
        item(key = "network-vehicles") {
            Surface(Modifier.fillMaxWidth().testTag("network-vehicles-switch"), shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    ToggleRow(tr("Animierte Verkehrsmittel anzeigen"),
                        tr("Kleine Züge und Busse fahren über die Linien. Sie sind Schmuck, keine echten Fahrzeugpositionen."),
                        settings.networkVehicles) { enabled -> update { it.copy(networkVehicles = enabled) } }
                }
            }
        }
        item(key = "network-design-heading") {
            FittedText(tr("Netzplan-Design"), Modifier.padding(top = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        }
        items(NetworkPlanDesign.entries, key = { it.name }) { design ->
            val selected = settings.networkPlanDesign == design
            Surface(Modifier.fillMaxWidth().testTag("network-design-${design.name}")
                .selectable(selected, role = Role.RadioButton, onClick = { update { it.copy(networkPlanDesign = design) } }),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FittedText(design.title, style = MaterialTheme.typography.titleMedium)
                    // Only the chosen design moves its vehicles: the switch above shows its effect there.
                    NetworkPlanDesignPreview(design, settings.networkVehicles, animate = selected)
                    Text(design.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
