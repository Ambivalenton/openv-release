// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.StoredState
import dev.ambivalenton.openv.localization.tr

/** The whole preview card is the button; first control in both board settings entry points. */
@Composable fun BoardDesignSelector(state: StoredState,onClick: ()->Unit) {
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().testTag("board-design-selector"),
        shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.primaryContainer,
        contentColor=MaterialTheme.colorScheme.onPrimaryContainer,tonalElevation=3.dp,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.35f))) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_dashboard_outline,null,Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                    FittedText(tr("Anzeigetafel-Design"),style=MaterialTheme.typography.titleMedium)
                    Text(tr("Design wählen: %1\$s",state.settings.board.design.title),style=MaterialTheme.typography.bodySmall)
                }
                AppIcon(R.drawable.ic_chevron_right,tr("Design-Auswahl öffnen"))
            }
            BoardDesignStrip(state,state.settings.board.design)
        }
    }
}
