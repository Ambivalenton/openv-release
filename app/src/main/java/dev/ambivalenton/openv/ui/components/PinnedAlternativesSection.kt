// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.ThemedTonalButton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.appCorner

/** This essential assistant content is independent of automatic comparison pop-ups. */
@Composable internal fun PinnedAlternativesSection(alternative:Trip?,onCompare:()->Unit,onRefresh:()->Unit) {
    if(alternative==null)return
    Surface(Modifier.fillMaxWidth().testTag("pinned-alternatives"),shape=appCorner(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Alternative Verbindungen"),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                TextButton(onRefresh) {Text(tr("Aktualisieren"))}
            }
            Text(tr("%1\$s → %2\$s",clockTime(alternative.firstBoardingTime()),clockTime(alternative.effectiveArrival())),
                    style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text(alternative.segments.filterNot {it.isWalk}.joinToString(" → "){it.lineName},style=MaterialTheme.typography.bodyMedium)
            Text(tr("%1\$s · %2\$s",minutesText((alternative.totalDuration+59_999)/60_000),transferCount(alternative.transfers)),style=MaterialTheme.typography.labelMedium)
            ThemedTonalButton(onCompare,Modifier.testTag("compare-pinned-alternative")){Text(tr("Verbindungen vergleichen"))}
        }
    }
}
