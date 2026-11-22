// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.AppAlertDialog as AlertDialog

import dev.ambivalenton.openv.localization.tr

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import dev.ambivalenton.openv.R

val sameLocationMessages = dev.ambivalenton.openv.data.model.arrivalMessages

@Composable
fun SameLocationEasterEggDialog(messageIndex: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FittedText(tr("Ziel erreicht! 📍")) },
        text = {
            Text(sameLocationMessages[Math.floorMod(messageIndex, sameLocationMessages.size)])
        },
        confirmButton = { TextButton(onDismiss) { Text(tr("Angekommen")) } },
    )
}

/** N01: the inline answer for the same start and destination; [onChange] leads into the destination field. */
@Composable
fun SameLocationEasterEgg(messageIndex: Int, modifier: Modifier = Modifier, onChange: (() -> Unit)? = null) {
    AppCard(
        modifier.fillMaxWidth().calmContentSize().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        shape = dev.ambivalenton.openv.ui.theme.appCorner(32.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    AppIcon(R.drawable.ic_place, null, Modifier.size(30.dp))
                }
            }
            Text(
                tr("Start und Ziel sind gleich."),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            onChange?.let {
                ThemedButton(it, Modifier.heightIn(min = 48.dp).testTag("same-location-change")) { Text(tr("Ziel ändern")) }
            }
            // The arrival joke stays, as an extra line below the hint.
            Text(
                sameLocationMessages[Math.floorMod(messageIndex, sameLocationMessages.size)],
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
