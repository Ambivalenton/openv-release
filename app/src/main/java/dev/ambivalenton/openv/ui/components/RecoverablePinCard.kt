// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.border
import dev.ambivalenton.openv.ui.theme.cardElevation
import dev.ambivalenton.openv.ui.theme.appFrameShadow
import dev.ambivalenton.openv.ui.theme.appFrame
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr

@Composable fun RecoverablePinCard(candidate: RecoverablePin,transition: PinTransitionController?,onPreview: ()->Unit,onPin: ()->Unit) {
    val session=candidate.session
    AppElevatedCard(onClick=onPreview,shape=dev.ambivalenton.openv.ui.theme.appCorner(22.dp),
        colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer),
        modifier=Modifier.fillMaxWidth().testTag("recoverable-${session.id}")) {
        Row(Modifier.padding(start=16.dp,end=8.dp,top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FittedText(session.favorite.label,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text(tr("Zuletzt angeheftet"),style=MaterialTheme.typography.labelSmall)
                // M07: what a tap on this entry does.
                Text(tr("Reisebegleitung wieder öffnen"),Modifier.testTag("recoverable-action"),style=MaterialTheme.typography.labelMedium,
                    color=MaterialTheme.colorScheme.primary)
                Text(session.trip.segments.filterNot {it.isWalk}.joinToString(" · ") {it.lineName}+
                    " · "+clockTime(session.trip.endTime),style=MaterialTheme.typography.bodyMedium)
            }
            if(transition!=null) PinMorphButton(session.trip.id,transition,onPin)
        }
    }
}
