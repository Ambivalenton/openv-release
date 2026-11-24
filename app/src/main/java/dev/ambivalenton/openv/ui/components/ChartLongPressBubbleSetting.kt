// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
internal class SliderSpeechShape(private val body:Shape,private val tipFraction:Float,private val atLeft:Boolean=false,private val atRight:Boolean=false):Shape {
    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
        val tail=with(density){10.dp.toPx()};val bodyHeight=(size.height-tail).coerceAtLeast(1f)
        val corner=body as? CornerBasedShape
        val ltr=layoutDirection==LayoutDirection.Ltr
        val edged=corner?.copy(bottomStart=if(if(ltr)atLeft else atRight)CornerSize(0.dp)else corner.bottomStart,
            bottomEnd=if(if(ltr)atRight else atLeft)CornerSize(0.dp)else corner.bottomEnd)?:body
        val outline=edged.createOutline(Size(size.width,bodyHeight),layoutDirection,density)
        val main=Path().apply {when(outline){is Outline.Generic->addPath(outline.path);is Outline.Rectangle->addRect(outline.rect);is Outline.Rounded->addRoundRect(outline.roundRect)}}
        val half=minOf(with(density){7.dp.toPx()},size.width/6)
        val x=(size.width*tipFraction).coerceIn(half,size.width-half)
        // At either endpoint the outside edge continues straight into the tail.
        // This keeps the adjoining corner square rather than leaving a rounded stub.
        val pointer=Path().apply {
            when {
                atLeft->{moveTo(0f,bodyHeight-2);lineTo(0f,size.height);lineTo(half*2,bodyHeight-2)}
                atRight->{moveTo(size.width-half*2,bodyHeight-2);lineTo(size.width,size.height);lineTo(size.width,bodyHeight-2)}
                else->{moveTo(x-half,bodyHeight-2);lineTo(x,size.height);lineTo(x+half,bodyHeight-2)}
            }
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union,main,pointer))
    }
}
@Composable internal fun ChartLongPressBubbleSetting(selected:ChartLongPressAction,onSelect:(ChartLongPressAction)->Unit,framed:Boolean=true) =
    StepBubbleSetting(tr("Einen Balken im Balkendiagramm gedrückt halten"),ChartLongPressAction.entries.map {it.title},selected.ordinal,
        {onSelect(ChartLongPressAction.entries[it])},"chart-long-press",framed=framed)

/**
 * A speech bubble above a slider with fixed stops (bar chart since build 64; network plan since
 * build 74). The bubble follows the thumb and names the stop; the text changes only once the thumb
 * rests on a stop. [description] optionally explains the shown stop below the slider.
 */
@Composable internal fun StepBubbleSetting(title:String,options:List<String>,selected:Int,onSelect:(Int)->Unit,tag:String,
    description:((Int)->String)?=null,framed:Boolean=true) {
    val last=options.lastIndex.coerceAtLeast(1)
    var value by rememberSaveable {mutableFloatStateOf(selected.toFloat())}
    var displayed by rememberSaveable {mutableIntStateOf(selected)}
    LaunchedEffect(selected){value=selected.toFloat()}
    val calm=LocalAppSettings.current.reduceMotion
    val haptics=androidx.compose.ui.platform.LocalHapticFeedback.current
    val position by animateFloatAsState(value/last,tween(if(calm)0 else 160),label="action-bubble-position")
    LaunchedEffect(position){val notch=(position*last).roundToInt().coerceIn(0,options.lastIndex);if(kotlin.math.abs(position*last-notch)<.035f)displayed=notch}
    val colors=MaterialTheme.colorScheme
    // Inside a frame of "Modi & Verhalten" no second frame of its own.
    val frame:@Composable (@Composable ()->Unit)->Unit=if(framed) {inner->SettingsCard(Modifier.testTag("$tag-setting")) {inner()}} else {inner->Box(Modifier.fillMaxWidth().testTag("$tag-setting")) {inner()}}
    frame {
        Column(Modifier.padding(if(framed) 14.dp else 0.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(title,style=MaterialTheme.typography.titleSmall)
            BoxWithConstraints(Modifier.fillMaxWidth().height(88.dp)){
                val width=minOf(210.dp,maxWidth);val thumb=12.dp+(maxWidth-24.dp)*position
                val left=(thumb-width/2).coerceIn(0.dp,maxWidth-width);val fraction=((thumb-left)/width).coerceIn(0f,1f)
                Surface(Modifier.offset(x=left).width(width).fillMaxHeight().testTag("$tag-bubble"),shape=SliderSpeechShape(appCorner(18.dp),fraction,position<=.001f,position>=.999f),color=colors.primaryContainer,contentColor=colors.onPrimaryContainer,border=BorderStroke(.8.dp,colors.primary.copy(alpha=.45f)),shadowElevation=2.dp){
                    Box(Modifier.appPanelDecoration().padding(start=12.dp,end=12.dp,top=10.dp,bottom=20.dp),contentAlignment=Alignment.Center){
                        AnimatedContent(displayed,transitionSpec={fadeIn(tween(if(calm)0 else 160)) togetherWith fadeOut(tween(if(calm)0 else 110))},label="action-bubble-text"){index->
                            // Build 113: shrinks and wraps between words only ("Kompaktansicht öffnen" never as "Kompaktansich/t").
                            FittedText(options[index.coerceIn(options.indices)],Modifier.fillMaxWidth().testTag("$tag-value"),color=colors.onPrimaryContainer,
                                textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyMedium,maxLines=3)
                        }
                    }
                }
            }
            Slider(value,{raw->
                val next=raw.coerceIn(0f,last.toFloat())
                if(!calm && next.roundToInt()!=value.roundToInt()) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                value=next
            },valueRange=0f..last.toFloat(),steps=last-1,onValueChangeFinished={onSelect(value.roundToInt().coerceIn(options.indices))},
                modifier=Modifier.testTag("$tag-slider").semantics {contentDescription=title;stateDescription=options[displayed.coerceIn(options.indices)]})
            description?.let {explain->
                Text(explain(displayed.coerceIn(options.indices)),Modifier.fillMaxWidth().testTag("$tag-description"),
                    style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            }
        }
    }
}
