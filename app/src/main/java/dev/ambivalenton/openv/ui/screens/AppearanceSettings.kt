// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*
import kotlin.math.roundToInt

@Composable internal fun AppAppearanceSettings(settings: AppSettings,update: ((AppSettings)->AppSettings)->Unit) {
    var picker by rememberSaveable {mutableStateOf(false)}
    AppThemeSelector(settings) {picker=true}
    if(picker) AppThemePicker(settings.appTheme,{theme->update {it.copy(appTheme=theme)}},{picker=false},
        settings=settings,update=update)
}

@Composable internal fun SettingsRadioRow(title: String,selected: Boolean,onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).selectable(selected,role=Role.RadioButton,onClick=onClick)
        .padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
        RadioButton(selected,onClick=null)
        FittedText(title,Modifier.padding(start=12.dp),style=MaterialTheme.typography.bodyLarge)
    }
}

@Composable internal fun ReadabilitySettings(settings: AppSettings,update: ((AppSettings)->AppSettings)->Unit) {
    var preview by remember(settings.readability) {mutableStateOf(settings.readability)}
    AppFontSetting(settings,update)
    AppThemeThumbnail(settings.copy(readability=preview))
    Text(tr("%1\$s · %2\$s %3\$s",tr("Schriftgröße"),(preview.textScale*100f).roundToInt(),"%"),style=MaterialTheme.typography.labelLarge)
    Slider(preview.textScale,{preview=preview.copy(textScale=it)},valueRange=.85f..1.3f,
        onValueChangeFinished={update {it.copy(readability=it.readability.copy(textScale=preview.textScale))}},
        modifier=Modifier.semantics {contentDescription=tr("Schriftgröße")})
    Text(tr("Die System-Schriftgröße bleibt zusätzlich wirksam."),style=MaterialTheme.typography.bodySmall)
    Text(tr("%1\$s · %2\$s %3\$s",tr("Eckradien"),(preview.cornerScale*100f).roundToInt(),"%"),style=MaterialTheme.typography.labelLarge)
    Slider(preview.cornerScale,{preview=preview.copy(cornerScale=it)},valueRange=.25f..1.75f,
        onValueChangeFinished={update {it.copy(readability=it.readability.copy(cornerScale=preview.cornerScale))}},
        modifier=Modifier.semantics {contentDescription=tr("Eckradien")})
    FittedText(tr("Kontrast"),style=MaterialTheme.typography.titleSmall)
    Column(Modifier.selectableGroup()) {
        SettingsRadioRow(tr("Designstandard"),settings.readability.contrast==AppContrast.STANDARD) {
            update {it.copy(readability=it.readability.copy(contrast=AppContrast.STANDARD))}
        }
        SettingsRadioRow(tr("Hoher Textkontrast"),settings.readability.contrast==AppContrast.HIGH) {
            update {it.copy(readability=it.readability.copy(contrast=AppContrast.HIGH))}
        }
    }
    TextButton({update {it.copy(readability=AppReadability(),fontId=null)}}) {Text(tr("Lesbarkeit zurücksetzen"))}
}
