// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.WalkingNavigationMode

@Composable fun WalkingNavigationRadioGroup(selected: WalkingNavigationMode,onSelect: (WalkingNavigationMode)->Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup().testTag("walking-navigation-options")) {
        WalkingNavigationMode.entries.forEach {mode->
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("walking-option-${mode.name}")
                .selectable(selected=selected==mode,role=Role.RadioButton,onClick={onSelect(mode)}).padding(vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected==mode,null)
                Column(Modifier.weight(1f).padding(start=8.dp)) {
                    Text(mode.title,style=MaterialTheme.typography.bodyLarge)
                    Text(mode.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
