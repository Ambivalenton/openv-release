// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.scrollToIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Build 114 · the way into the chart's own settings (now line, text in the bars, footprints). A
 * tonal settings row with a moving sample, the theme's corners and a chevron – clearly not one of
 * the designs listed below it under their own heading.
 */
@Composable internal fun TimelineDisplayEntry(settings:AppSettings,onOpen:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(onClick=onOpen,modifier=Modifier.fillMaxWidth().testTag("timeline-display-options"),shape=appCorner(28.dp),
        color=colors.secondaryContainer,contentColor=colors.onSecondaryContainer) {
        Row(Modifier.padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            // A small picture of what lies behind: two bars, the now line in its stroke and a dial.
            val stroke=settings.nowLine.resolve(settings.timelineTheme.style,settings.appTheme?.language).stroke
            val ink=nowLineInk(settings.nowLine)
            val bar=colors.primary.copy(alpha=.32f);val face=colors.surface;val hand=colors.onSurface
            Canvas(Modifier.size(48.dp).background(colors.surface,appCorner(16.dp)).padding(6.dp)) {
                val r=androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                drawRoundRect(bar,Offset(size.width*.04f,size.height*.06f),androidx.compose.ui.geometry.Size(size.width*.18f,size.height*.5f),r)
                drawRoundRect(bar,Offset(size.width*.3f,size.height*.42f),androidx.compose.ui.geometry.Size(size.width*.18f,size.height*.52f),r)
                inset(0f,size.height*.38f,size.width*.42f,size.height*.38f) {drawNowStroke(stroke,ink,1.8.dp.toPx())}
                inset(size.width*.58f,size.height*.29f,0f,size.height*.29f) {drawAnalogClock(10,9,0,false,face,hand,ink)}
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                FittedText(tr("Darstellung im Diagramm"),style=MaterialTheme.typography.titleMedium)
                Text(tr("Uhrzeitlinie · Text im Balken · Fußabdrücke"),style=MaterialTheme.typography.bodySmall,
                    color=colors.onSecondaryContainer.copy(alpha=.8f))
            }
            AppIcon(R.drawable.ic_chevron_right,null)
        }
    }
}

/** The heading above the list of designs (Build 114), so the settings row above never reads as one of them. */
@Composable internal fun DesignListHeading(title:String) {
    Text(title,Modifier.fillMaxWidth().padding(top=10.dp,start=4.dp).semantics {heading()},
        style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
}

/** Build 114 · the subpage: three frames, each with its own live sample. */
@Composable internal fun TimelineDisplaySheet(settings:AppSettings,update:((AppSettings)->AppSettings)->Unit,
    onLabelDraft:(Float)->Unit,onDismiss:()->Unit) {
    val scope=rememberCoroutineScope()
    val list=rememberLazyListState();val target=rememberJourneyScrollTarget(list)
    DirectModalSheet(onDismiss,target,"timeline-display-sheet") {motion,dismiss->
        // Build 120: a page of its own with its own close button – the navigation pill steps aside, it covered the tiles.
        RequestPillAside()
        Column(Modifier.fillMaxSize()) {
            DesignPickerHeader(tr("Darstellung im Diagramm"),dismiss)
            LazyColumn(state=list,userScrollEnabled=false,modifier=Modifier.weight(1f).testTag("timeline-display-list").semantics {
                    scrollBy {_,y->motion.cancelContentFling();list.dispatchRawDelta(y);true}
                    scrollToIndex {index->motion.cancelContentFling();scope.launch {list.scrollToItem(index)};true}
                },
                contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=24.dp+LocalNavigationClearance.current),
                verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item(key="now-line") {NowLineControls(settings,update)}
                item(key="label") {TimelineLabelControl(settings,onDraftChange=onLabelDraft) {value->update {it.copy(timelineLabelControl=value)}}}
                item(key="footprints") {FootprintControls(settings,update)}
            }
        }
    }
}

/**
 * The head of a frame with a switch (Build 114): title, one line on what it does, and the switch.
 * The whole row toggles; the content below folds away with a spring.
 */
@Composable internal fun FrameSwitchHeader(title:String,subtitle:String,checked:Boolean,tag:String,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).toggleable(checked,role=Role.Switch,onValueChange=onChange),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            FittedText(title,Modifier.semantics {heading()},style=MaterialTheme.typography.titleMedium)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked,null)
    }
}

/** A small caption above a group of choices. */
@Composable internal fun ChoiceCaption(text:String) {
    Text(text,Modifier.padding(top=2.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Folding content of a frame: grows and fades with a soft spring (none in quiet mode). */
@Composable internal fun FrameFold(visible:Boolean,content:@Composable ColumnScope.()->Unit) {
    val quiet=LocalAppSettings.current.reduceMotion
    AnimatedVisibility(visible,enter=if(quiet) fadeIn(spring(stiffness=10_000f)) else expandVertically(spring(dampingRatio=.86f,stiffness=420f))+fadeIn(),
        exit=if(quiet) fadeOut(spring(stiffness=10_000f)) else shrinkVertically(spring(dampingRatio=.9f,stiffness=520f))+fadeOut()) {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}

/** Build 114 · the now line: shown or not, its clock, stroke, weight, colour and seconds – with a live chart sample. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun NowLineControls(settings:AppSettings,update:((AppSettings)->AppSettings)->Unit) {
    val p=settings.nowLine
    fun set(change:(NowLinePreferences)->NowLinePreferences)=update {it.copy(nowLine=change(it.nowLine))}
    SettingsCard(Modifier.testTag("now-line-controls")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            FrameSwitchHeader(tr("Uhrzeitlinie"),tr("Zeigt im Balkendiagramm, wo „jetzt“ ist."),p.visible,"now-line-switch") {on->set {it.copy(visible=on)}}
            FrameFold(p.visible) {
                NowLinePreview(settings)
                ChoiceCaption(tr("Uhr"))
                FlowRow(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    NowClockStyle.entries.forEach {style->
                        FilterChip(p.clock==style,{set {it.copy(clock=style)}},label={Text(tr(style.titleKey))},
                            leadingIcon={ClockGlyph(style,settings)},modifier=Modifier.testTag("now-line-clock-${style.name}"))
                    }
                }
                ChoiceCaption(tr("Linie"))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    NowStroke.entries.forEach {stroke->StrokeTile(stroke,p,settings) {set {it.copy(stroke=stroke)}}}
                }
                ChoiceCaption(tr("Stärke"))
                // Build 120: chips wrap to the next line instead of being squeezed letter by letter.
                FlowRow(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    NowWeight.entries.forEach {weight->
                        FilterChip(p.weight==weight,{set {it.copy(weight=weight)}},label={Text(tr(weight.titleKey))},
                            modifier=Modifier.testTag("now-line-weight-${weight.name}"))
                    }
                }
                ChoiceCaption(tr("Farbe"))
                FlowRow(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    NowColor.entries.forEach {color->
                        val swatch=nowLineInk(NowLinePreferences(color=color))
                        FilterChip(p.color==color,{set {it.copy(color=color)}},label={Text(tr(color.titleKey))},
                            leadingIcon={Box(Modifier.size(14.dp).background(swatch,appCorner(7.dp)))},
                            modifier=Modifier.testTag("now-line-color-${color.name}"))
                    }
                }
                Row(Modifier.fillMaxWidth().testTag("now-line-seconds").toggleable(p.seconds,role=Role.Switch) {on->set {it.copy(seconds=on)}},
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Sekunden"),style=MaterialTheme.typography.bodyLarge)
                        Text(tr("Ziffern mit Sekunden, die Analoguhr mit Sekundenzeiger"),style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(p.seconds,null)
                }
            }
        }
    }
}

/** A short piece of the chart (bars of the current design) with the now line across it, live. */
@Composable private fun NowLinePreview(settings:AppSettings) {
    OpenVTheme(settings) {
        val palette=timelineColorPalette();val style=settings.timelineTheme.style
        val grid=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f)
        val now=rememberWallClock(tickMillis=1000).toInstant().toEpochMilli()
        val prefs=settings.nowLine.copy(visible=true)
        Box(Modifier.fillMaxWidth().height(108.dp).clipDesignPreview().background(MaterialTheme.colorScheme.background)
            .drawBehind {for(f in listOf(.25f,.75f)) drawLine(grid,Offset(0f,size.height*f),Offset(size.width,size.height*f),1.dp.toPx())}
            .testTag("now-line-preview")) {
            Row(Modifier.fillMaxSize().padding(horizontal=14.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                listOf(LineType.SUBURBAN to (.1f to .62f),LineType.BUS to (.3f to .95f),LineType.REGIONAL to (.0f to .48f)).forEachIndexed {i,(type,span)->
                    Box(Modifier.width(40.dp).fillMaxHeight()) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            TimelineBar(style,palette[type],listOf("S1","42","RE1")[i],
                                Modifier.offset(y=maxHeight*span.first).width(38.dp).height(maxHeight*(span.second-span.first)))
                        }
                    }
                }
            }
            NowLineRow(resolvedNowLine(prefs),prefs,now,nowLineInk(prefs),Modifier.fillMaxWidth().align(Alignment.Center).padding(start=8.dp,end=12.dp))
        }
    }
}

/** A tiny mark for each clock choice: digits, a dial, both, nothing, or the design's own. */
@Composable private fun ClockGlyph(style:NowClockStyle,settings:AppSettings) {
    val ink=MaterialTheme.colorScheme.onSurfaceVariant
    val shown=if(style==NowClockStyle.AUTO) settings.nowLine.copy(clock=NowClockStyle.AUTO).resolve(settings.timelineTheme.style,
        settings.appTheme?.language).clock else style
    Canvas(Modifier.size(18.dp)) {
        when(shown) {
            NowClockStyle.ANALOG,NowClockStyle.BOTH->drawAnalogClock(10,10,0,false,Color.Transparent,ink,ink)
            NowClockStyle.DIGITAL-> {
                val h=size.height*.42f
                for(i in 0..2) drawRoundRect(ink,Offset(size.width*(.08f+i*.32f),size.height/2-h/2),
                    androidx.compose.ui.geometry.Size(size.width*.22f,h),androidx.compose.ui.geometry.CornerRadius(2f))
            }
            else->drawLine(ink,Offset(size.width*.15f,size.height*.85f),Offset(size.width*.85f,size.height*.15f),2.dp.toPx())
        }
    }
}

/** One stroke to choose, drawn as it will look; "Passend zum Design" shows what the design picks. */
@Composable private fun StrokeTile(stroke:NowStroke,p:NowLinePreferences,settings:AppSettings,onClick:()->Unit) {
    val selected=p.stroke==stroke
    val colors=MaterialTheme.colorScheme
    val drawn=if(stroke==NowStroke.AUTO) p.copy(stroke=NowStroke.AUTO).resolve(settings.timelineTheme.style,settings.appTheme?.language).stroke else stroke
    val ink=nowLineInk(p)
    Surface(Modifier.width(92.dp).selectable(selected,role=Role.RadioButton,onClick=onClick).testTag("now-line-stroke-${stroke.name}"),
        shape=appCorner(16.dp),color=if(selected) colors.primaryContainer else colors.surfaceContainerHigh,
        border=BorderStroke(if(selected) 2.dp else 1.dp,if(selected) colors.primary else colors.outlineVariant)) {
        Column(Modifier.padding(horizontal=8.dp,vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {drawNowStroke(drawn,ink,2.5.dp.toPx()*p.weight.factor.coerceAtMost(1.3f))}
            FittedText(tr(stroke.titleKey),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelMedium)
        }
    }
}
