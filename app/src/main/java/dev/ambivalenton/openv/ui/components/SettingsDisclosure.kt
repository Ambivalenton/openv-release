// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.appCorner

@Composable internal fun SettingsDisclosure(title:String,tag:String,summary:String?=null,content:@Composable ColumnScope.()->Unit){
    var expanded by rememberSaveable(tag){mutableStateOf(false)}
    SettingsDisclosure(title,tag,summary,expanded,{expanded=it},content)
}

/** The same disclosure with its state held by the caller (it can be opened from elsewhere). */
@Composable internal fun SettingsDisclosure(title:String,tag:String,summary:String?=null,expanded:Boolean,onExpandedChange:(Boolean)->Unit,
    content:@Composable ColumnScope.()->Unit){
    val toggle={onExpandedChange(!expanded)}
    Surface(Modifier.fillMaxWidth().testTag(tag),shape=appCorner(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Column{
            Row(Modifier.fillMaxWidth().clickable(role=Role.Button){toggle()}.semantics {
                stateDescription=tr(if(expanded)"Ausgeklappt"else"Eingeklappt")
            }.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);summary?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                AppIcon(if(expanded)R.drawable.ic_collapse else R.drawable.ic_expand,tr(if(expanded)"Bereich einklappen"else"Bereich ausklappen"))
            }
            CalmVisibility(expanded){Column(Modifier.padding(horizontal=16.dp).padding(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
        }
    }
}
