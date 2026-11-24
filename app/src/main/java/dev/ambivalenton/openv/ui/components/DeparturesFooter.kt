// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.hints.gestureHint

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr

/** One line at compact widths; 48 dp hit targets remain independent of the smaller type. */
@Composable fun DeparturesFooter(onStops: () -> Unit, onDesign: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp).testTag("departures-footer"),
        verticalAlignment=Alignment.CenterVertically) {
        TextButton(onStops,Modifier.weight(2f).heightIn(min=48.dp).testTag("footer-stops")
            .semantics { contentDescription=tr("Haltestellen-Liste") },contentPadding=PaddingValues(horizontal=4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_list,null,Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                FittedText(tr("Haltestellen-Liste"),style=MaterialTheme.typography.labelSmall.copy(fontSize=12.sp),maxLines=1)
            }
        }
        TextButton(onDesign,Modifier.weight(1f).heightIn(min=48.dp).testTag("footer-design")
            .gestureHint(dev.ambivalenton.openv.data.model.GestureHintKeys.BOARD_DESIGN,dev.ambivalenton.openv.ui.hints.HintKind.PULSE,2200L)
            .semantics { contentDescription=tr("Design") },contentPadding=PaddingValues(horizontal=4.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_palette,null,Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                FittedText(tr("Design"),style=MaterialTheme.typography.labelSmall.copy(fontSize=12.sp),maxLines=1)
            }
        }
    }
}

/** One action of an expanded stop: add it to the Haltestellen-Liste (a member shows a tick). */
@Composable fun DepartureStopActions(member:Boolean,enabled:Boolean,onAdd:()->Unit) {
    TextButton(onAdd,Modifier.fillMaxWidth().heightIn(min=48.dp).pressScale().testTag("departure-board-action"),enabled=enabled && !member) {
        AppIcon(if(member)R.drawable.ic_check else R.drawable.ic_add,null,Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        FittedText(if(member)tr("In der Haltestellen-Liste") else tr("Zur Haltestellen-Liste hinzufügen"),style=MaterialTheme.typography.labelLarge)
    }
}
