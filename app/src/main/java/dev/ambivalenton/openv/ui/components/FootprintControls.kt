// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.ThemedOutlinedButton

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.launch

@Composable fun FootprintControls(settings:AppSettings,update:((AppSettings)->AppSettings)->Unit) {
    val p=settings.footprints.normalized()
    var speed by rememberSaveable(settings.quietMode,p.speed) {mutableFloatStateOf(p.speed)}
    var sizeControl by rememberSaveable(settings.quietMode,p.barScale) {mutableFloatStateOf(barFootprintControl(p.barScale))}
    var shapes by rememberSaveable {mutableStateOf(false)}
    SettingsCard(Modifier.testTag("footprint-controls")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            FittedText(tr("Fußabdrücke"),style=MaterialTheme.typography.titleMedium)
            if(settings.quietMode) Text(tr("Im Ruhemodus bleiben Fußwege als ruhige Punktlinie sichtbar."),style=MaterialTheme.typography.bodyMedium)
            Text(if(speed==0f) tr("Fußabdrücke aus · Punktlinie") else tr("Laufgeschwindigkeit · %1\$s %%",(speed*100).roundToInt()),
                style=MaterialTheme.typography.labelLarge)
            Slider(speed,{speed=it},valueRange=0f..2f,steps=7,enabled=!settings.quietMode,
                onValueChangeFinished={update {it.copy(footprints=it.footprints.copy(speed=speed))}},modifier=Modifier.testTag("footprint-speed"))
            Text(tr("Ganz links: Fußabdrücke aus. Die Verbindung bleibt als Punktlinie erkennbar."),style=MaterialTheme.typography.bodySmall)
            Text(tr("Fußabdruck-Größe"),style=MaterialTheme.typography.labelLarge)
            Slider(sizeControl,{sizeControl=it},valueRange=0f..1f,steps=0,
                onValueChangeFinished={update {it.copy(footprints=it.footprints.copy(barScale=barFootprintScale(sizeControl)))}},
                modifier=Modifier.testTag("footprint-bar-size").semantics {contentDescription=tr("Fußabdruck-Größe")})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(tr("Kleinste"),style=MaterialTheme.typography.labelSmall)
                Text(tr("Größte"),style=MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(p.path==FootprintPathMode.STRAIGHT,{update {it.copy(footprints=it.footprints.copy(path=FootprintPathMode.STRAIGHT))}},
                    label={Text(tr("Gerade"))},enabled=!settings.quietMode,modifier=Modifier.testTag("footprint-path-straight"))
                FilterChip(p.path==FootprintPathMode.RANDOM,{update {it.copy(footprints=it.footprints.copy(path=FootprintPathMode.RANDOM))}},
                    label={Text(tr("Zufällig"))},enabled=!settings.quietMode,modifier=Modifier.testTag("footprint-path-random"))
            }
            ThemedOutlinedButton({shapes=true},Modifier.fillMaxWidth().testTag("footprint-shape-open"),enabled=!settings.quietMode) {
                FootprintSample(p.style,Modifier.size(48.dp),barFootprintScale(sizeControl));Spacer(Modifier.width(12.dp));Text(tr("Form · %1\$s",p.style.title))
            }
            CompositionLocalProvider(LocalAppSettings provides settings.copy(footprints=p.copy(speed=speed))) {
                Box(Modifier.fillMaxWidth().height(54.dp).testTag("footprint-speed-preview")) {
                    WalkingFootprintTrack(MaterialTheme.colorScheme.primary,Modifier.fillMaxSize(),"footprint-speed-preview",horizontal=true,glyphScale=barFootprintScale(sizeControl))
                }
            }
        }
    }
    if(shapes) FootprintShapePicker(p.style,{shape->update {it.copy(footprints=it.footprints.copy(style=shape))};shapes=false},{shapes=false},barFootprintScale(sizeControl))
}

@Composable fun FootprintSample(style:FootprintStyle,modifier:Modifier=Modifier,glyphScale:Float=1f) {
    val color=MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val scale=size.height*.42f/FOOTPRINT_LENGTH_PX*glyphScale.coerceIn(.7f,2f)
        val foot=FootstepGeometry(size.width/2,size.height/2,0f,scale,true)
        val sample=if(style==FootprintStyle.CRUTCHES)listOf(foot.copy(fore=false),
            foot.copy(x=foot.x-9f*scale,y=foot.y-5f*scale,scale=scale*.28f,fore=true),
            foot.copy(x=foot.x+9f*scale,y=foot.y-5f*scale,scale=scale*.28f,fore=true,left=false))else listOf(foot)
        drawPath(footprintShapesPath(sample,style),color)
    }
}

@Composable fun FootprintShapePicker(selected:FootprintStyle,onSelect:(FootprintStyle)->Unit,onDismiss:()->Unit,glyphScale:Float=LocalAppSettings.current.footprints.normalized().barScale) {
    val scope=rememberCoroutineScope()
    val list=rememberLazyListState();val target=rememberJourneyScrollTarget(list)
    DirectModalSheet(onDismiss,target,"footprint-shape-picker") {motion,dismiss->
        Column(Modifier.fillMaxSize()) {
            DesignPickerHeader(tr("Fußabdruck-Form"),dismiss)
            LazyColumn(state=list,userScrollEnabled=false,modifier=Modifier.weight(1f).testTag("footprint-shape-list").semantics {
                    scrollBy {_,y->motion.cancelContentFling();list.dispatchRawDelta(y);true}
                    scrollToIndex {index->motion.cancelContentFling();scope.launch {list.scrollToItem(index)};true}
                },
                contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=24.dp+LocalNavigationClearance.current),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                FootprintGroup.entries.forEach {group->
                    item(key="group-${group.name}") {Text(tr(group.titleKey),Modifier.padding(top=12.dp,bottom=4.dp).semantics {heading()},style=MaterialTheme.typography.titleLarge)}
                    items(FootprintStyle.entries.filter {it.group==group},key={it.name}) {style->FootprintChoice(style,style==selected,glyphScale) {onSelect(style)}}
                }
            }
        }
    }
}
@Composable private fun FootprintChoice(style:FootprintStyle,selected:Boolean,glyphScale:Float,onClick:()->Unit) {
    var root by remember {mutableStateOf(Offset.Zero)}
    var textPosition by remember {mutableStateOf(Offset.Zero)}
    var layout by remember {mutableStateOf<TextLayoutResult?>(null)}
    val textColor=MaterialTheme.colorScheme.onSurface
    val footColor=MaterialTheme.colorScheme.primary
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=92.dp).testTag("footprint-option-${style.name}"),
        color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape=MaterialTheme.shapes.medium,border=androidx.compose.foundation.BorderStroke(if(selected) 2.dp else 1.dp,
            if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth().onGloballyPositioned {root=it.positionInRoot()}) {
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                FootprintSample(style,Modifier.size(48.dp),glyphScale)
                FittedText(style.title,Modifier.weight(1f).onGloballyPositioned {textPosition=it.positionInRoot()},onTextLayout={layout=it},
                    style=MaterialTheme.typography.titleMedium,color=textColor)
                if(selected) AppIcon(R.drawable.ic_check,tr("Ausgewählt"))
            }
            WalkingFootprintTrack(footColor,Modifier.matchParentSize(),"shape-choice-${style.name}",
                clocks=layout?.let {listOf(FootprintClockText(it,textPosition-root,textColor))} ?: emptyList(),style=style,horizontal=true,glyphScale=glyphScale)
        }
    }
}

