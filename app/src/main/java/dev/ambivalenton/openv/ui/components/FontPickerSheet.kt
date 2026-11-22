// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.appFontFamily
import dev.ambivalenton.openv.ui.theme.appCorner

@Composable fun AppFontSetting(settings:AppSettings,update:((AppSettings)->AppSettings)->Unit) {
    var open by rememberSaveable {mutableStateOf(false)}
    val font=appFonts.firstOrNull {it.id==settings.fontId}
    val family=font?.let {appFontFamily(it.id)}?:dev.ambivalenton.openv.ui.theme.LocalAppVisuals.current.fontFamily
    Surface(onClick={open=true},modifier=Modifier.fillMaxWidth().testTag("app-font-select"),shape=appCorner(18.dp),
        color=MaterialTheme.colorScheme.surfaceContainerHigh,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(tr("Schriftart"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                FittedText(font?.name?:tr("Designstandard"),Modifier.fillMaxWidth().testTag("current-font-preview"),
                    style=MaterialTheme.typography.titleLarge.copy(fontFamily=family))
            }
            AppIcon(R.drawable.ic_expand,tr("Schriftart wählen"))
        }
    }
    Text(tr("Gilt auch für die Übersicht. Alle Schriften sind offline verfügbar."),style=MaterialTheme.typography.bodySmall)
    if(open)FontPickerSheet(settings.fontId,{id->update {it.copy(fontId=id)};open=false},{open=false})
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FontPickerSheet(selectedId:String?,onSelect:(String?)->Unit,onDismiss:()->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var license by rememberSaveable {mutableStateOf<String?>(null)}
    val context=LocalContext.current
    AppModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().testTag("font-picker")) {
            Row(Modifier.fillMaxWidth().padding(start=24.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                FittedText(tr("Schriftart wählen"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(onDismiss){AppIcon(R.drawable.ic_close,tr("Schließen"))}
            }
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=24.dp),singleLine=true,label={Text(tr("Schrift suchen"))})
            val searching=query.isNotBlank()
            val groups=remember(query) {groupedAppFonts(appFonts.filter {!searching||it.name.contains(query,ignoreCase=true)||
                tr(it.descriptionKey).contains(query,ignoreCase=true)||appFontCategory(it.id).title.contains(query,ignoreCase=true)})}
            // The section holding the current font starts open. With "Designstandard" (or a font that is
            // not in the catalogue) no section opens; the design's own entry shows the choice instead.
            val current=appFonts.firstOrNull {it.id==selectedId}
            var open by rememberSaveable {mutableStateOf(current?.let {setOf(appFontCategory(it.id).name)} ?: emptySet())}
            val designFamily=dev.ambivalenton.openv.ui.theme.LocalAppVisuals.current.fontFamily
            LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).imePadding().testTag("font-picker-list"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item(key="font-default") {
                    val chosen=current==null
                    Surface(shape=appCorner(20.dp),color=if(chosen)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier=Modifier.fillMaxWidth().testTag("font-default").semantics {selected=chosen}.clickable(role=Role.RadioButton){onSelect(null)}) {
                        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                FittedText(tr("Designstandard"),style=MaterialTheme.typography.headlineSmall.copy(fontFamily=designFamily),modifier=Modifier.fillMaxWidth())
                                Text(tr("Die Schrift, die zum gewählten Design gehört."),style=MaterialTheme.typography.bodySmall)
                            }
                            if(chosen) AppIcon(R.drawable.ic_check,null,Modifier.size(22.dp))
                        }
                    }
                }
                if(groups.isEmpty()) item(key="font-empty") {Text(tr("Keine passende Schrift."),style=MaterialTheme.typography.bodyMedium)}
                groups.forEach {(category,fonts)->
                    val expanded=searching || category.name in open
                    item(key="font-group-${category.name}") {
                        FontCategoryHeader(category,fonts.size,expanded,fonts.any {it.id==selectedId},Modifier.animateItem()) {
                            open=if(category.name in open) open-category.name else open+category.name
                        }
                    }
                    if(expanded) items(fonts,key={it.id}) {font->
                        val family=appFontFamily(font.id)
                        Surface(shape=appCorner(20.dp),color=if(selectedId==font.id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier=Modifier.animateItem().fillMaxWidth().testTag("font-${font.id}").semantics {selected=selectedId==font.id}.clickable(role=Role.RadioButton){onSelect(font.id)}) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                FittedText(font.name,style=MaterialTheme.typography.headlineSmall.copy(fontFamily=family),modifier=Modifier.fillMaxWidth())
                                Text(tr(font.descriptionKey),style=MaterialTheme.typography.bodySmall)
                                Text(font.copyright+" · OFL-1.1",style=MaterialTheme.typography.labelSmall)
                                TextButton({license=font.licenseAsset}){Text(tr("Lizenz anzeigen"))}
                            }
                        }
                    }
                }
            }
        }
    }
    license?.let {asset->
        val text=remember(asset){context.assets.open(asset).bufferedReader().use {it.readText()}}
        AppDialog({license=null}) {Surface(shape=appCorner(24.dp)) {
            Column(Modifier.heightIn(max=600.dp).padding(20.dp)) {
                FittedText(tr("Schriftlizenz"),style=MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f,fill=false)){item {Text(text,style=MaterialTheme.typography.bodySmall)}}
                TextButton({license=null}){Text(tr("Schließen"))}
            }
        }}
    }
}

/** A tappable section title: name, short description, number of fonts and an expand/collapse arrow. */
@Composable private fun FontCategoryHeader(category:AppFontCategory,count:Int,expanded:Boolean,holdsSelection:Boolean,
    modifier:Modifier,onToggle:()->Unit) {
    val turn by androidx.compose.animation.core.animateFloatAsState(if(expanded)180f else 0f,
        calmSpec(androidx.compose.animation.core.spring(dampingRatio=.7f,stiffness=500f)),label="font-group-arrow")
    Surface(onClick=onToggle,modifier=modifier.fillMaxWidth().testTag("font-group-${category.name}").semantics {
            stateDescription=tr(if(expanded)"Ausgeklappt" else "Eingeklappt")},
        shape=appCorner(20.dp),color=if(holdsSelection)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.heightIn(min=56.dp).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(category.title,style=MaterialTheme.typography.titleMedium)
                Text(category.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(count.toString(),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            AppIcon(R.drawable.ic_expand,tr(if(expanded)"Bereich einklappen" else "Bereich ausklappen"),
                Modifier.graphicsLayer {rotationZ=turn})
        }
    }
}
