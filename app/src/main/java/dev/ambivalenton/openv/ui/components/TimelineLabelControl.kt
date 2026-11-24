// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.*
import kotlin.math.roundToInt

/** The three looks of the line names, with the stored value each one stands for. */
private val labelStyles=listOf(
    Triple(TimelineLabelEffect.PLAIN,TIMELINE_LABEL_NORMAL,"Schlicht"),
    Triple(TimelineLabelEffect.SHADOW,TIMELINE_LABEL_SHADOW,"Mit Schatten"),
    Triple(TimelineLabelEffect.BADGE,TIMELINE_LABEL_BADGE,"Plakette"),
)

/**
 * Build 114 · "Text im Balken", rebuilt: a switch shows or hides the line names, three tiles show the
 * three looks as they appear in the bars, and only the plain look has a slider for size and opacity
 * (the stored value is unchanged: 0 hidden, up to 1 plain, then shadow and badge).
 */
@Composable fun TimelineLabelControl(settings:AppSettings,onDraftChange:(Float)->Unit={},onCommit:(Float)->Unit) {
    var value by rememberSaveable(settings.timelineLabelControl) {
        mutableFloatStateOf(normalizedTimelineLabelControl(settings.timelineLabelControl))
    }
    // The look to return to when the names are switched on again.
    var lastShown by rememberSaveable {mutableFloatStateOf(value.takeIf {it>0f} ?: TIMELINE_LABEL_NORMAL)}
    fun choose(next:Float,commit:Boolean=true) {
        value=next;if(next>0f) lastShown=next
        onDraftChange(next);if(commit) onCommit(snappedTimelineLabelControl(next))
    }
    val appearance=timelineLabelAppearance(value)
    val fontScale=LocalDensity.current.fontScale
    SettingsCard(Modifier.testTag("timeline-label-card")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            FrameSwitchHeader(tr("Text im Balken"),tr("Liniennamen wie „RE1“ in den Balken"),value>0f,"timeline-label-switch") {on->
                choose(if(on) lastShown else 0f)
            }
            FrameFold(value>0f) {
                ChoiceCaption(tr("Stil"))
                Row(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    labelStyles.forEachIndexed {index,(effect,detent,title)->
                        val selected=appearance.effect==effect
                        val colors=MaterialTheme.colorScheme
                        Surface(Modifier.weight(1f).selectable(selected,role=Role.RadioButton) {
                                choose(if(effect==TimelineLabelEffect.PLAIN && appearance.effect==TimelineLabelEffect.PLAIN) value else
                                    if(effect==TimelineLabelEffect.PLAIN) lastShown.takeIf {it<=1f} ?: detent else detent)
                            }.testTag("timeline-label-detent-$index"),
                            shape=appCorner(18.dp),color=if(selected) colors.primaryContainer else colors.surfaceContainerHigh,
                            border=BorderStroke(if(selected) 2.dp else 1.dp,if(selected) colors.primary else colors.outlineVariant)) {
                            Column(Modifier.padding(vertical=10.dp,horizontal=6.dp),horizontalAlignment=Alignment.CenterHorizontally,
                                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                // Each tile shows its look in the current bar design.
                                OpenVTheme(settings.copy(timelineLabelControl=if(effect==TimelineLabelEffect.PLAIN && appearance.effect==effect) value else detent)) {
                                    val palette=timelineColorPalette()
                                    TimelineBar(settings.timelineTheme.style,palette[LineType.REGIONAL],"RE1",
                                        Modifier.width(timelineRideBarWidth(fontScale).coerceAtMost(44.dp)).height(64.dp)
                                            .then(if(index==0) Modifier.testTag("timeline-label-live-preview") else Modifier))
                                }
                                FittedText(tr(title),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelMedium,maxLines=2)
                            }
                        }
                    }
                }
                FrameFold(appearance.effect==TimelineLabelEffect.PLAIN) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(tr("Größe und Deckkraft"),Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                        Text(tr("%1\$s %%",(value.coerceIn(0f,1f)*100).roundToInt()),style=MaterialTheme.typography.labelLarge,
                            color=MaterialTheme.colorScheme.primary)
                    }
                    Slider(value.coerceIn(.1f,1f),{choose(it,commit=false)},valueRange=.1f..1f,
                        onValueChangeFinished={onCommit(snappedTimelineLabelControl(value))},
                        modifier=Modifier.testTag("timeline-label-slider").semantics {
                            contentDescription=tr("Größe und Deckkraft der Liniennamen")
                            stateDescription=tr("Größe %1\$s Prozent, Deckkraft %2\$s Prozent",
                                (appearance.scale*100).roundToInt(),(appearance.opacity*100).roundToInt())
                        })
                    Row(Modifier.fillMaxWidth()) {
                        Text(tr("Klein und blass"),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tr("Groß und kräftig"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
