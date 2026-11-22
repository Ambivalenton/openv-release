// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*

/** A bounded, stable window prevents accordion animation from resizing the native dialog.
 * All child pickers share the same overlay stack, so time selection stays above Extras. */
@Composable internal fun FavoriteOptionsDialog(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    AppDialog(onDismiss,DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            Surface(Modifier.widthIn(max=560.dp).fillMaxWidth(.94f).height(maxHeight*.85f),
                shape=dev.ambivalenton.openv.ui.theme.appCorner(28.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    FittedText(title,style=MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
                    TextButton(onDismiss,Modifier.align(Alignment.End)){Text(tr("Schließen"))}
                }
            }
        }
    }
}
