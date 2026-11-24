// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.AppDialog as Dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.rendering.boardPresentation
import dev.ambivalenton.openv.ui.theme.timelineColorPalette
import dev.ambivalenton.openv.ui.theme.appSystemHeader
import dev.ambivalenton.openv.ui.theme.systemHeaderInk

@Composable fun DesignPickerHeader(title:String,onDismiss:()->Unit) {
    val ink=systemHeaderInk()
    Row(Modifier.fillMaxWidth().appSystemHeader().padding(start=24.dp,end=12.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
        FittedText(title,Modifier.weight(1f),color=ink,style=MaterialTheme.typography.titleLarge)
        IconButton(onDismiss) {AppIcon(R.drawable.ic_close,tr("Auswahl schließen"),tint=ink)}
    }
}

data class PaletteField(val key:String,val title:String,val initial:Long,val default:Long)

/** Role picker stays mounted behind the editor; every dismissal reverses the same FAB morph. */
@Composable private fun DesignPaletteDialog(id:String,title:String,fields:List<PaletteField>,onDismiss:()->Unit,
    colorsFor:(PaletteField)->DesignColorSet,onSave:(Map<String,Long>)->Unit,origin:PaletteMorphOrigin?,preview:@Composable (Map<String,Long>)->Unit) {
    var values by remember(id) {mutableStateOf(fields.associate {it.key to it.initial})}
    var editing by remember(id) {mutableStateOf<String?>(null)}
    var closing by remember(id) {mutableStateOf(false)}
    MixingPaletteOverlay(title,fields,values,origin,{editing=it},{
        val defaults=fields.associate {it.key to it.default};values=defaults;onSave(defaults);closing=true
    },onDismiss,closing)
    editing?.let {key->
        val field=fields.first {it.key==key}
        ColorPickerSheet(field.title,values[key],tr("Designstandard"),
            {color->val next=values+(key to (color ?: field.default));values=next;editing=null;onSave(next);closing=true},
            {editing=null},designColors=colorsFor(field),previewContent={color->preview(values+(key to (color ?: field.default)))})
    }
}

@Composable fun AppThemePaletteDialog(settings:AppSettings,theme:AppTheme,onDismiss:()->Unit,update:((AppSettings)->AppSettings)->Unit,origin:PaletteMorphOrigin?=null) {
    val defaults=theme.defaultColors();val current=settings.appThemeColors[theme] ?: defaults
    fun AppThemeColors.values()=mapOf("background" to background,"surface" to surface,"accent" to accent,"secondary" to secondary,"text" to text)
    val names=mapOf("background" to tr("Hintergrundfarbe"),"surface" to tr("Flächenfarbe"),"accent" to tr("Akzentfarbe"),"secondary" to tr("Zweiter Akzent"),"text" to tr("Textfarbe"))
    fun colors(v:Map<String,Long>)=AppThemeColors(v.getValue("background"),v.getValue("surface"),v.getValue("accent"),v.getValue("secondary"),v.getValue("text"))
    val fields=current.values().map {(key,color)->PaletteField(key,names.getValue(key),color,defaults.values().getValue(key))}
    DesignPaletteDialog("app-${theme.name}",tr("Palette · %1\$s",theme.title),fields,onDismiss,
        // Build 113: the design's own colour world, not a family shared with many designs.
        {field->ThemeColorWorld.set(theme,paletteRole(field.key),field.default)},{v->
        val chosen=colors(v)
        update {it.copy(appTheme=theme,appThemeColors=if(chosen==defaults) it.appThemeColors-theme else it.appThemeColors+(theme to chosen))}
    },origin) {v->AppThemeThumbnail(settings.copy(appTheme=theme,appThemeColors=settings.appThemeColors+(theme to colors(v))))}
}

@Composable fun BoardPaletteDialog(state:StoredState,design:BoardDesign,onDismiss:()->Unit,update:((AppSettings)->AppSettings)->Unit,origin:PaletteMorphOrigin?=null) {
    val defaults=boardPresentation(state.copy(stopColors=emptyMap(),settings=state.settings.copy(board=state.settings.board.copy(design=design,
        customColors=emptyMap(),forceMaterialInApp=false))),MaterialTheme.colorScheme).colors
    val current=state.settings.board.customColors[design] ?: defaults
    fun BoardColors.values()=mapOf("text" to foreground,"background" to background,"accent" to accent)
    fun colors(v:Map<String,Long>)=BoardColors(v.getValue("text"),v.getValue("background"),v.getValue("accent"))
    val names=mapOf("text" to tr("Textfarbe"),"background" to tr("Hintergrundfarbe"),"accent" to tr("Akzentfarbe"))
    DesignPaletteDialog("board-${design.name}",tr("Palette · %1\$s",design.title),current.values().map {(key,color)->
        PaletteField(key,names.getValue(key),color,defaults.values().getValue(key))},onDismiss,
        {field->designColors(design.title,boardColorFamily(design,state.settings.appTheme),paletteRole(field.key),field.default,
            androidx.compose.ui.graphics.Color(defaults.background).luminance()<.3f)},{v->
        val chosen=colors(v)
        update {it.copy(board=it.board.copy(customColors=if(chosen==defaults) it.board.customColors-design else it.board.customColors+(design to chosen)))}
    },origin) {v->BoardDesignStrip(state.copy(settings=state.settings.copy(board=state.settings.board.copy(
        customColors=state.settings.board.customColors+(design to colors(v))))),design)}
}

@Composable fun TimelinePaletteDialog(settings:AppSettings,theme:TimelineTheme,onDismiss:()->Unit,update:((AppSettings)->AppSettings)->Unit,origin:PaletteMorphOrigin?=null) {
    val dark=MaterialTheme.colorScheme.background.luminance()<.3f
    val saved=settings.timelinePalette(theme)
    val current=timelineColorPalette(theme,saved.seed,saved.lineColors)
    val defaults=timelineColorPalette(theme,null,emptyMap())
    fun argb(color:androidx.compose.ui.graphics.Color)=color.toArgb().toLong() and 0xffffffffL
    val fields=LineType.entries.map {type->PaletteField(type.name,type.label,argb(current[type].container),argb(defaults[type].container))}
    fun overrides(v:Map<String,Long>)=TimelinePaletteOverride(saved.seed,LineType.entries.associateWith {v.getValue(it.name)})
    DesignPaletteDialog("timeline-${theme.name}",tr("Palette · %1\$s",theme.title),fields,onDismiss,
        {field->settings.appTheme?.takeIf {theme==TimelineTheme.APP_THEME || theme==TimelineTheme.MONET}
            ?.let {app->ThemeColorWorld.set(app,DesignColorRole.ACCENT,field.default)}
            ?: designColors(theme.title,timelineColorFamily(theme,settings.appTheme),DesignColorRole.ACCENT,field.default,dark)},{v->
        val reset=fields.all {v[it.key]==it.default}
        update {it.withTimelinePalette(theme,if(reset) null else overrides(v))}
    },origin) {v->TimelineDesignPreview(settings.withTimelinePalette(theme,overrides(v)).selectTimelineTheme(theme))}
}
