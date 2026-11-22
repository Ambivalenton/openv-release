// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.ambivalenton.openv.ui.theme.appCorner
import dev.ambivalenton.openv.ui.theme.favoriteColors
import dev.ambivalenton.openv.ui.theme.readableForeground
import dev.ambivalenton.openv.ui.components.resource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.AppAlertDialog
import dev.ambivalenton.openv.ui.components.AppIcon
import dev.ambivalenton.openv.ui.components.FittedText

/** M17: one searchable direction of a network line. */
data class NetworkRouteRow(val key: String, val edgeId: String, val from: Favorite, val to: Favorite, val bothWays: Boolean)

/** M17: every line in each direction it is set up for (both, only there or only back), in plan order. */
fun networkRouteRows(store: StoredState): List<NetworkRouteRow> {
    val byId = store.favorites.associateBy { it.id }
    return store.network.edges.flatMap { edge ->
        val a = byId[edge.aId] ?: return@flatMap emptyList()
        val b = byId[edge.bId] ?: return@flatMap emptyList()
        fun row(from: Favorite, to: Favorite) = NetworkRouteRow("${edge.id}:${from.id}", edge.id, from, to, edge.direction == DirectionMode.BOTH)
        when (edge.direction) {
            DirectionMode.BOTH -> listOf(row(a, b), row(b, a))
            DirectionMode.A_TO_B -> listOf(row(a, b))
            DirectionMode.B_TO_A -> listOf(row(b, a))
        }
    }
}

/** One line of the plan with its searchable directions (both, or the one it is set up for). */
data class NetworkRouteGroup(val edgeId: String, val a: Favorite, val b: Favorite, val directions: List<NetworkRouteRow>)

fun networkRouteGroups(store: StoredState): List<NetworkRouteGroup> {
    val byId = store.favorites.associateBy { it.id }
    val rows = networkRouteRows(store).groupBy { it.edgeId }
    return store.network.edges.mapNotNull { edge ->
        val a = byId[edge.aId] ?: return@mapNotNull null
        val b = byId[edge.bId] ?: return@mapNotNull null
        NetworkRouteGroup(edge.id, a, b, rows[edge.id].orEmpty())
    }
}

/**
 * The favourites' lines as a list in place of the network plan: each line in one frame
 * with its directions as large rows (names in the favourites' own colours, made readable on the
 * frame); favourites without a line follow as single destinations.
 */
@Composable
fun NetworkRouteList(store: StoredState, onRoute: (NetworkRouteRow) -> Unit, onFavorite: (Favorite) -> Unit, modifier: Modifier = Modifier) {
    val groups = remember(store.network.edges, store.favorites) { networkRouteGroups(store) }
    val linked = remember(groups) { groups.flatMap { listOf(it.a.id, it.b.id) }.toSet() }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < .3f
    val frame = scheme.surfaceContainerLow
    @Composable fun nameColor(favorite: Favorite): Color {
        val accent = favoriteColors(favorite.accentSeed, dark, scheme).accent
        return Color(readableForeground(accent.toArgb().toLong() and 0xffffffffL, frame.toArgb().toLong() and 0xffffffffL).toInt())
    }
    Column(modifier.fillMaxWidth().testTag("network-route-list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { group ->
            Surface(Modifier.fillMaxWidth().testTag("network-route-group-${group.edgeId}"), shape = appCorner(22.dp), color = frame,
                border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant)) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    group.directions.forEachIndexed { index, route ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = scheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button,
                            onClickLabel = tr("Verbindung von %1\$s nach %2\$s suchen", route.from.label, route.to.label)) { onRoute(route) }
                            .padding(horizontal = 16.dp, vertical = 10.dp).testTag("network-route-${route.key}"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppIcon(route.from.icon.resource(), null, Modifier.size(22.dp), tint = nameColor(route.from))
                            Text(androidx.compose.ui.text.buildAnnotatedString {
                                withStyle(androidx.compose.ui.text.SpanStyle(color = nameColor(route.from))) { append(route.from.label) }
                                append("  →  ")
                                withStyle(androidx.compose.ui.text.SpanStyle(color = nameColor(route.to))) { append(route.to.label) }
                            }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            AppIcon(route.to.icon.resource(), null, Modifier.size(22.dp), tint = nameColor(route.to))
                        }
                    }
                }
            }
        }
        val single = store.favorites.filter { it.id !in linked }
        if (single.isNotEmpty()) Surface(Modifier.fillMaxWidth().testTag("network-route-singles"), shape = appCorner(22.dp), color = frame,
            border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant)) {
            Column(Modifier.padding(vertical = 4.dp)) {
                single.forEachIndexed { index, favorite ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = scheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button,
                        onClickLabel = tr("Verbindung nach %1\$s suchen", favorite.label)) { onFavorite(favorite) }
                        .padding(horizontal = 16.dp, vertical = 10.dp).testTag("network-list-favorite-${favorite.id}"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(favorite.icon.resource(), null, Modifier.size(22.dp), tint = nameColor(favorite))
                        Text(favorite.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = nameColor(favorite))
                    }
                }
            }
        }
    }
}

/** The small picture of the list switch: three lines of a list, or a little plan. */
@Composable fun RouteListPicture(list: Boolean, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.primary; val soft = MaterialTheme.colorScheme.outline
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = w * .07f
        if (list) for (i in 0 until 3) {
            val y = h * (.28f + .22f * i)
            drawCircle(ink, stroke * .9f, androidx.compose.ui.geometry.Offset(w * .2f, y))
            drawLine(soft, androidx.compose.ui.geometry.Offset(w * .34f, y), androidx.compose.ui.geometry.Offset(w * .84f, y), stroke * .8f,
                androidx.compose.ui.graphics.StrokeCap.Round)
        } else {
            val p = listOf(androidx.compose.ui.geometry.Offset(w * .2f, h * .25f), androidx.compose.ui.geometry.Offset(w * .78f, h * .3f),
                androidx.compose.ui.geometry.Offset(w * .5f, h * .74f))
            drawLine(soft, p[0], p[1], stroke, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(soft, p[1], p[2], stroke, androidx.compose.ui.graphics.StrokeCap.Round)
            p.forEach { drawCircle(ink, stroke * 1.4f, it) }
        }
    }
}

/** M01: the hint for holding a favourite on the plan, taken from the current setting. */
fun networkHoldHint(action: NetworkHoldAction): String = when (action) {
    NetworkHoldAction.MOVE -> tr("Halte einen Favoriten gedrückt, um ihn zu verschieben.")
    NetworkHoldAction.LOCATION -> tr("Halte einen Favoriten gedrückt, um deinen Standort für die ganze App dorthin zu legen. Nochmals halten hebt das wieder auf.")
    NetworkHoldAction.PIN -> tr("Halte einen Favoriten gedrückt, um die nächste Verbindung dorthin direkt anzupinnen.")
    NetworkHoldAction.OVERVIEW -> tr("Halte einen Favoriten gedrückt, um ihn an die Übersicht zu heften. Nochmals halten löst ihn wieder.")
    NetworkHoldAction.NOTHING -> tr("Gedrückthalten eines Favoriten hat keine Wirkung. Bearbeiten kannst du über den Stift.")
}

/**
 * M17: a tap between close targets on the plan: each candidate as a clearly named row of at least
 * 48 dp, so the wrong route is not started by accident.
 */
@Composable
fun NetworkTargetChoiceDialog(store: StoredState, nodes: List<String>, lines: List<String>,
    onFavorite: (String) -> Unit, onLine: (String) -> Unit, onDismiss: () -> Unit) {
    val byId = store.favorites.associateBy { it.id }
    AppAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("network-target-choice"),
        title = { Text(tr("Welches Ziel meinst du?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                nodes.mapNotNull { byId[it] }.forEach { favorite ->
                    OutlinedButton({ onFavorite(favorite.id) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("network-choice-node-${favorite.id}")) {
                        Text(tr("Verbindung nach %1\$s suchen", favorite.label), Modifier.fillMaxWidth()) }
                }
                lines.mapNotNull { id -> store.network.edges.firstOrNull { it.id == id } }.forEach { edge ->
                    val a = byId[edge.aId]?.label.orEmpty(); val b = byId[edge.bId]?.label.orEmpty()
                    val label = when (edge.direction) {
                        DirectionMode.BOTH -> tr("Strecke %1\$s – %2\$s", a, b)
                        DirectionMode.A_TO_B -> tr("Strecke %1\$s → %2\$s", a, b)
                        DirectionMode.B_TO_A -> tr("Strecke %1\$s → %2\$s", b, a)
                    }
                    OutlinedButton({ onLine(edge.id) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("network-choice-line-${edge.id}")) {
                        Text(label, Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(tr("Abbrechen")) } })
}
