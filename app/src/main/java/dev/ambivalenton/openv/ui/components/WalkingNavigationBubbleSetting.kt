// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.*
import kotlin.math.roundToInt
@Composable internal fun WalkingNavigationBubbleSetting(selected:WalkingNavigationMode,onSelect:(WalkingNavigationMode)->Unit) {
    val last=WalkingNavigationMode.entries.lastIndex
    var value by rememberSaveable {mutableFloatStateOf(selected.ordinal.toFloat())}
    var displayed by rememberSaveable {mutableIntStateOf(selected.ordinal)}
    LaunchedEffect(selected){value=selected.ordinal.toFloat()}
    val calm=LocalAppSettings.current.reduceMotion
    val position by animateFloatAsState(value/last,tween(if(calm)0 else 160),label="action-bubble-position")
    LaunchedEffect(position){val notch=(position*last).roundToInt().coerceIn(0,last);if(kotlin.math.abs(position*last-notch)<.035f)displayed=notch}
    val colors=MaterialTheme.colorScheme
    SettingsCard(Modifier.testTag("walking-navigation-setting")){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(tr("Fußweg-Navigation"),style=MaterialTheme.typography.titleSmall)
            BoxWithConstraints(Modifier.fillMaxWidth().height(100.dp)){
                val width=minOf(210.dp,maxWidth);val thumb=12.dp+(maxWidth-24.dp)*position
                val left=(thumb-width/2).coerceIn(0.dp,maxWidth-width);val fraction=((thumb-left)/width).coerceIn(0f,1f)
                Surface(Modifier.offset(x=left).width(width).fillMaxHeight().testTag("walking-navigation-bubble"),shape=SliderSpeechShape(appCorner(18.dp),fraction,position<=.001f,position>=.999f),color=colors.primaryContainer,contentColor=colors.onPrimaryContainer,border=BorderStroke(.8.dp,colors.primary.copy(alpha=.45f)),shadowElevation=2.dp){
                    Box(Modifier.appPanelDecoration().padding(start=12.dp,end=12.dp,top=10.dp,bottom=20.dp),contentAlignment=Alignment.Center){
                        AnimatedContent(displayed,transitionSpec={fadeIn(tween(if(calm)0 else 160)) togetherWith fadeOut(tween(if(calm)0 else 110))},label="action-bubble-text"){index->
                            BasicText(WalkingNavigationMode.entries[index].title,Modifier.fillMaxWidth().testTag("walking-navigation-value"),style=MaterialTheme.typography.bodyMedium.copy(color=colors.onPrimaryContainer,textAlign=TextAlign.Center,lineHeight=TextUnit.Unspecified),maxLines=3,autoSize=TextAutoSize.StepBased(minFontSize=10.sp,maxFontSize=MaterialTheme.typography.bodyMedium.fontSize,stepSize=.5.sp))
                        }
                    }
                }
            }
            Slider(value,{value=it.coerceIn(0f,last.toFloat())},valueRange=0f..last.toFloat(),steps=last-1,onValueChangeFinished={onSelect(WalkingNavigationMode.entries[value.roundToInt().coerceIn(0,last)])},modifier=Modifier.testTag("walking-navigation-slider").semantics {stateDescription=WalkingNavigationMode.entries[displayed].title})
        }
    }
}
