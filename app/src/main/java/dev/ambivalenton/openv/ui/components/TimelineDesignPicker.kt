// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.*

val timelinePreviewLines=listOf("42","U1","RE1","S1")

@Composable fun TimelineDesignPreview(settings:AppSettings,modifier:Modifier=Modifier) {
    OpenVTheme(settings) {
        val theme=settings.timelineTheme;val palette=timelineColorPalette()
        val grid=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f)
        val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale
        Row(modifier.fillMaxWidth().height((146f+18f*(fontScale-1f).coerceAtLeast(0f)).dp)
            .clipDesignPreview().background(MaterialTheme.colorScheme.background)
            .drawBehind {for(f in listOf(.2f,.5f,.8f)) drawLine(grid,Offset(0f,size.height*f),Offset(size.width,size.height*f),1.dp.toPx())}
            .horizontalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
            listOf(LineType.BUS,LineType.SUBWAY,LineType.REGIONAL,LineType.SUBURBAN).forEachIndexed {index,type->
                Column(Modifier.width(timelineColumnWidth(fontScale)).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                        TimelineBar(theme.style,palette[type],timelinePreviewLines[index],
                            Modifier.width(timelineRideBarWidth(fontScale)).height(listOf(16,32,64,102)[index].dp))
                    }
                }
            }
        }
    }
}

@Composable fun TimelineDesignSelector(settings:AppSettings,onClick:()->Unit) {
    Surface(onClick=onClick,Modifier.fillMaxWidth().testTag("timeline-design-selector"),shape=MaterialTheme.shapes.large,
        color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.35f))) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_timeline_outline,null)
                Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                    FittedText(tr("Balkendiagramm-Design"),style=MaterialTheme.typography.titleMedium)
                    Text(settings.timelineTheme.titleFor(settings),style=MaterialTheme.typography.bodySmall)
                }
                AppIcon(R.drawable.ic_chevron_right,tr("Design-Auswahl öffnen"))
            }
            TimelineDesignPreview(settings)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TimelineDesignPicker(settings:AppSettings,onSelect:(TimelineTheme)->Unit,onDismiss:()->Unit,
    update:((AppSettings)->AppSettings)->Unit) {
    var palette by rememberSaveable {mutableStateOf<TimelineTheme?>(null)}
    val paletteOrigin=remember {PaletteMorphOrigin()}
    // Hoist the drag draft so every visible style sample uses the same value before persistence.
    var labelDraft by rememberSaveable(settings.timelineLabelControl) {
        mutableFloatStateOf(normalizedTimelineLabelControl(settings.timelineLabelControl))
    }
    val previewSettings=settings.copy(timelineLabelControl=labelDraft)
    DesignPickerWindow(tr("Balkendiagramm-Design wählen"),"timeline-design-picker",onDismiss,palette!=null,
        if(settings.timelineTheme==TimelineTheme.APP_THEME)null else ({palette=settings.timelineTheme}),paletteOrigin) {
            item(key="timeline-display-options") {
                SettingsDisclosure(tr("Text & Fußabdrücke"),"timeline-display-options",tr("Beschriftung und Spuren im Balkendiagramm")) {
                    TimelineLabelControl(previewSettings,onDraftChange={labelDraft=it}){value->update {it.copy(timelineLabelControl=value)}}
                    FootprintControls(settings,update)
                }
            }
            items(supportedTimelineThemes.filter { !settings.quietMode || it in quietTimelineThemes },key={it.name}) {theme->
                TimelineThemeChoice(theme,settings,previewSettings,onSelect)
            }
    }
    palette?.let {theme->TimelinePaletteDialog(previewSettings,theme,{palette=null},update,paletteOrigin)}
}

@Composable private fun TimelineThemeChoice(theme:TimelineTheme,settings:AppSettings,previewSettings:AppSettings,onSelect:(TimelineTheme)->Unit){
                val selected=settings.timelineTheme==theme
                Surface(Modifier.fillMaxWidth().testTag("timeline-design-${theme.name}").selectable(selected,
                    role=Role.RadioButton,onClick={onSelect(theme)}),shape=MaterialTheme.shapes.medium,
                    border=BorderStroke(if(selected) 2.dp else 1.dp,if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        FittedText(theme.titleFor(settings),style=MaterialTheme.typography.titleMedium)
                        TimelineDesignPreview(previewSettings.selectTimelineTheme(theme))
                        Text(theme.description,style=MaterialTheme.typography.bodySmall)
                    }
                }
}
