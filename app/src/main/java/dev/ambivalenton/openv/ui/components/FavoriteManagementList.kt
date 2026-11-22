// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import java.time.ZonedDateTime

@Composable
internal fun FavoriteManagementList(
    state: StoredState,
    now: ZonedDateTime,
    onEditFavorite: (Favorite) -> Unit,
    onEditLine: (String) -> Unit,
) {
    val entries = state.favoriteManagementEntries()
    entries.forEach { entry ->
        key(entry.id) {
            if (entry is FavoriteManagementEntry.Line && entries.firstOrNull { it is FavoriteManagementEntry.Line } == entry)
                Text(tr("Verbindungen"), Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium)
            val edit: () -> Unit = when (entry) {
                is FavoriteManagementEntry.Single -> ({ onEditFavorite(entry.favorite) })
                is FavoriteManagementEntry.Line -> ({ onEditLine(entry.id) })
            }
            val title = when (entry) {
                is FavoriteManagementEntry.Single -> entry.favorite.label
                is FavoriteManagementEntry.Line -> entry.title()
            }
            ListItem(
                headlineContent = { FittedText(title, style = MaterialTheme.typography.titleMedium) },
                supportingContent = {
                    Text(when (entry) {
                        is FavoriteManagementEntry.Single -> when (entry.favorite.hiddenReason(now, state.routingPresence(now.toInstant().toEpochMilli()))) {
                            FavoriteHiddenReason.AT_CURRENT_LOCATION -> tr("Du bist gerade hier")
                            FavoriteHiddenReason.TIME_WINDOW -> tr("Zeitabhängiger Standort aktiv")
                            null -> if (state.network.node(entry.favorite.id)?.point == null) tr("In der Ablage · noch nicht im Netzplan")
                                else entry.favorite.location.name
                        }
                        is FavoriteManagementEntry.Line -> when (entry.edge.direction) {
                            DirectionMode.BOTH -> tr("Beide Richtungen")
                            else -> tr("Eine Richtung")
                        }
                    }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                leadingContent = {
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        when (entry) {
                            is FavoriteManagementEntry.Single -> FavoriteBadge(entry.favorite, size = 48.dp, singleShape = true, shadow = true)
                            is FavoriteManagementEntry.Line -> Box(Modifier.size(60.dp, 40.dp)) {
                                FavoriteBadge(entry.a, Modifier.align(Alignment.CenterStart), size = 34.dp, singleShape = true, shadow = true)
                                FavoriteBadge(entry.b, Modifier.align(Alignment.CenterEnd), size = 34.dp, singleShape = true, shadow = true)
                            }
                        }
                    }
                },
                trailingContent = {
                    IconButton(edit, Modifier.size(48.dp).testTag("favorite-manage-edit-${entry.id}")) {
                        AppIcon(R.drawable.ic_edit, tr("%1\$s bearbeiten", title))
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("favorite-manage-${entry.id}")
                    .clickable(role = Role.Button, onClickLabel = tr("Bearbeiten"), onClick = edit),
            )
        }
    }
}
