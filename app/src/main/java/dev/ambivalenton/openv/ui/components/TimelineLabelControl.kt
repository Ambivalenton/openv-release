// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.*
import kotlin.math.roundToInt

/** Continuous size/opacity range followed by three stable, tactile effect detents. */
@Composable fun TimelineTextSlider(value:Float,onChange:(Float)->Unit,onCommit:()->Unit) {
    val haptics=LocalHapticFeedback.current
    val controller=remember {TimelineLabelSnapController(value)}
    val appearance=timelineLabelAppearance(value)
    val description=when(appearance.effect) {
        TimelineLabelEffect.SHADOW->tr("100 Prozent · Schatten")
        TimelineLabelEffect.BADGE->tr("100 Prozent · Badge und Schatten")
        TimelineLabelEffect.PLAIN->tr("Größe %1\$s Prozent, Deckkraft %2\$s Prozent",
            (appearance.scale*100).roundToInt(),(appearance.opacity*100).roundToInt())
    }
    val tick=MaterialTheme.colorScheme.primary
    Column {
        Slider(value=value,onValueChange={raw->
            val next=controller.dragTo(raw)
            if(next>=1f && next!=value) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(next)
        },valueRange=0f..TIMELINE_LABEL_BADGE,onValueChangeFinished=onCommit,
            modifier=Modifier.testTag("timeline-label-slider").semantics {
                contentDescription=tr("Größe, Deckkraft und Texteffekt der Liniennamen")
                stateDescription=description
            })
        Canvas(Modifier.fillMaxWidth().height(4.dp).padding(horizontal=10.dp)) {
            timelineLabelDetents.forEach {drawCircle(tick,2.dp.toPx(),Offset(size.width*it/TIMELINE_LABEL_BADGE,size.height/2))}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TimelineLabelControl(settings:AppSettings,onDraftChange:(Float)->Unit={},onCommit:(Float)->Unit) {
    var value by rememberSaveable(settings.timelineLabelControl) {
        mutableFloatStateOf(normalizedTimelineLabelControl(settings.timelineLabelControl))
    }
    val appearance=timelineLabelAppearance(value)
    val fontScale=LocalDensity.current.fontScale
    Surface(Modifier.fillMaxWidth().testTag("timeline-label-card"),shape=designPreviewShape(28.dp),
        color=MaterialTheme.colorScheme.surfaceContainerHigh,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            FittedText(tr("Text im Balken"),style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) {
                    TimelineTextSlider(value,{value=it;onDraftChange(it)}, {onCommit(snappedTimelineLabelControl(value))})
                }
                OpenVTheme(settings.copy(timelineLabelControl=value)) {
                    val palette=timelineColorPalette()
                    TimelineBar(settings.timelineTheme.style,palette[LineType.REGIONAL],"RE1",
                        Modifier.width(timelineRideBarWidth(fontScale)).height(68.dp).testTag("timeline-label-live-preview"))
                }
            }
            // Keep the sample aligned with the slider even with 200% system text.
            // Fixed status lines preserve card height while changing effects.
            Text(when {
                value==0f->tr("Ausgeblendet")
                appearance.effect==TimelineLabelEffect.SHADOW->tr("100 % · Schatten")
                appearance.effect==TimelineLabelEffect.BADGE->tr("100 % · Badge + Schatten")
                else->tr("Größe %1\$s%% · Deckkraft %2\$s%%",(appearance.scale*100).roundToInt(),(appearance.opacity*100).roundToInt())
            },style=MaterialTheme.typography.bodySmall,minLines=3,maxLines=3)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                val titles=listOf(tr("100 %"),tr("Schatten"),tr("Badge + Schatten"))
                timelineLabelDetents.forEachIndexed {index,detent->
                    FilterChip(value==detent,{
                        value=detent;onDraftChange(detent);onCommit(detent)
                    },label={Text(titles[index])},modifier=Modifier.testTag("timeline-label-detent-$index"))
                }
            }
            Text(tr("Bis 100 %: Größe und Deckkraft. Danach: Schatten → Badge + Schatten."),
                style=MaterialTheme.typography.bodySmall)
        }
    }
}
