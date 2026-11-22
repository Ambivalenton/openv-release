// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.border

import androidx.compose.foundation.background

import dev.ambivalenton.openv.ui.components.ThemedOutlinedButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.ambivalenton.openv.OpenVApp
import dev.ambivalenton.openv.ui.ColorPickerViewModel
import dev.ambivalenton.openv.ui.theme.*
import dev.ambivalenton.openv.localization.tr

@Composable fun FavoriteColorControls(selected:Long?,pickerOpen:Boolean,onPickerOpen:()->Unit,
    onPickerDismiss:()->Unit,onSelected:(Long)->Unit) {
    val settings=LocalAppSettings.current
    val accent=MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xffffffffL
    val presets=remember(settings.appTheme,settings.appThemeColors,accent) {favoriteThemeColors(settings,accent)}
    val app=LocalContext.current.applicationContext as? OpenVApp
    val model:ColorPickerViewModel?=if(app!=null && LocalViewModelStoreOwner.current!=null)
        viewModel(factory=remember(app) {viewModelFactory {initializer {app.koin.get<ColorPickerViewModel>()}}}) else null
    val persisted=model?.history?.collectAsStateWithLifecycle()?.value
    var localRecent by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(emptyList<Long>())}
    fun choose(value:Long) {
        onSelected(value)
        localRecent=(listOf(value)+localRecent).distinct().take(12)
        model?.save(value) {}
    }
    Column(Modifier.fillMaxWidth().testTag("favorite-color-grid").selectableGroup(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        presets.chunked(5).forEachIndexed {row,colors->
            Row(Modifier.fillMaxWidth().testTag("favorite-color-row-$row"),horizontalArrangement=Arrangement.SpaceBetween) {
                colors.forEachIndexed {column,color->
                    ColorSwatch(color.color,tr(color.nameKey),selected==color.color,
                        Modifier.testTag("favorite-color-${row*5+column}")) {choose(color.color)}
                }
            }
        }
        ThemedOutlinedButton(onPickerOpen,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("favorite-custom-color")) {
            Text(tr("Eigene Farbe auswählen"))
        }
        // M16: a very light or very dark own colour gets a readability preview on the real surface:
        // names keep the normal text colour, the colour itself shows in the outlined symbol.
        selected?.let {seed->
            val surface=MaterialTheme.colorScheme.surfaceContainerLow
            val color=androidx.compose.ui.graphics.Color(seed)
            if(dev.ambivalenton.openv.ui.theme.contrastRatio(color,surface)<3.0)
                Surface(Modifier.fillMaxWidth().testTag("favorite-color-readability"),shape=MaterialTheme.shapes.medium,color=surface) {
                    Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(Modifier.size(22.dp).background(color,androidx.compose.foundation.shape.CircleShape)
                            .border(1.5.dp,MaterialTheme.colorScheme.outline,androidx.compose.foundation.shape.CircleShape))
                        Text(tr("Diese Farbe hebt sich hier kaum ab. Namen bleiben in normaler Textfarbe lesbar, die Farbe erscheint im Symbol mit Rand."),
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface)
                    }
                }
        }
    }
    if(pickerOpen) AdvancedColorDialog(selected ?: accent,false,
        {choose(it);onPickerDismiss()},onPickerDismiss,
        themeAccentColor=accent,recentColors=persisted?.recent ?: localRecent)
}
