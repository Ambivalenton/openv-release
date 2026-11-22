// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.theme.languageInnerMaterial
import dev.ambivalenton.openv.ui.theme.fill
import dev.ambivalenton.openv.ui.theme.border

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.localization.tr

enum class SettingsTileKind { PAGE, INLINE }

@Composable fun DynamicSettingsTile(title:String,detail:String,kind:SettingsTileKind,onOpen:()->Unit,
    modifier:Modifier=Modifier,tag:String="settings-tile",content:@Composable ()->Unit={}) {
    var expanded by rememberSaveable(tag) {mutableStateOf(false)}
    val rotation by animateFloatAsState(if(expanded) 180f else 0f,animationSpec=calmSpec(androidx.compose.animation.core.spring()),label="settings-chevron")
    // The tile takes the fill, outline, outer effect and inner material of the design language.
    val ink=dev.ambivalenton.openv.ui.theme.languageInk()
    val shape=MaterialTheme.shapes.large
    Surface(modifier.fillMaxWidth().calmContentSize().appSurfaceMaterial(shape),shape=shape,
        color=ink.fill(MaterialTheme.colorScheme.surfaceContainer),border=ink.border()) {
        Column(Modifier.languageInnerMaterial(shape,ink)) {
            Surface(onClick={if(kind==SettingsTileKind.INLINE) expanded=!expanded else onOpen()},
                modifier=Modifier.fillMaxWidth().testTag(tag).semantics {
                    if(kind==SettingsTileKind.INLINE) stateDescription=if(expanded) tr("Ausgeklappt") else tr("Eingeklappt")
                },color=if(ink.active) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,shape=MaterialTheme.shapes.large) {
                Row(Modifier.padding(18.dp).heightIn(min=40.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        FittedText(title,style=MaterialTheme.typography.titleMedium)
                        Text(detail,style=MaterialTheme.typography.bodySmall)
                    }
                    AppIcon(if(kind==SettingsTileKind.PAGE) R.drawable.ic_chevron_right else R.drawable.ic_expand,
                        if(kind==SettingsTileKind.PAGE) tr("Unterseite öffnen") else tr("Bereich aufklappen"),
                        Modifier.testTag("$tag-indicator").then(if(kind==SettingsTileKind.INLINE) Modifier.rotate(rotation) else Modifier))
                }
            }
            CalmVisibility(kind==SettingsTileKind.INLINE && expanded) {
                Column(Modifier.padding(start=18.dp,end=18.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {content()}
            }
        }
    }
}
