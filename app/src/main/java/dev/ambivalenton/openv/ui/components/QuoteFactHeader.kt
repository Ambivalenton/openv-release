// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.theme.LocalAppSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HeaderSwapGate(private val debounceMillis: Long=300) {
    private var last: Long?=null
    fun accept(uptimeMillis: Long): Boolean {
        val previous=last
        if(previous!=null && uptimeMillis-previous in 0 until debounceMillis) return false
        last=uptimeMillis;return true
    }
}

/** A custom bounded flash eliminates the rectangular default ripple, including on textured themes. */
@Composable fun QuoteFactHeader(text: String,style: TextStyle,onSwap: ()->Unit,modifier: Modifier=Modifier,
    onOpenHistory:(()->Unit)?=null) {
    val gate=remember {HeaderSwapGate()};val view=LocalView.current
    val interactions=remember {MutableInteractionSource()};val flash=remember {Animatable(0f)}
    val neutral=MaterialTheme.colorScheme.onSurface
    LaunchedEffect(interactions) {
        var job:Job?=null
        interactions.interactions.collect {event->if(event is PressInteraction.Press) {
            job?.cancel();job=launch {flash.snapTo(1f);flash.animateTo(0f,tween(180))}
        }}
    }
    val base=androidx.compose.ui.platform.LocalViewConfiguration.current
    val holdConfig=remember(base) {object:androidx.compose.ui.platform.ViewConfiguration by base {override val longPressTimeoutMillis=3_000L}}
    val held=remember {Animatable(0f)}
    LaunchedEffect(interactions) {
        var progress:Job?=null
        interactions.interactions.collect {event->when(event) {
            is PressInteraction.Press->{progress?.cancel();progress=launch {held.snapTo(0f);held.animateTo(1f,tween(3_000,easing=LinearEasing))}}
            is PressInteraction.Release,is PressInteraction.Cancel->{progress?.cancel();held.snapTo(0f)}
            else->Unit
        }}
    }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalViewConfiguration provides holdConfig) {
    QuoteFrame(LocalAppSettings.current.overview.quoteDecoration,modifier.fillMaxWidth()) {ink->
    Box(Modifier.fillMaxWidth().testTag("quote-fact-swap").clip(RoundedCornerShape(16.dp))
        .drawBehind {
            drawRoundRect(neutral.copy(alpha=.10f*flash.value),cornerRadius=CornerRadius(16.dp.toPx()))
            if(onOpenHistory!=null && held.value>0f) drawRect(neutral.copy(alpha=.6f),
                topLeft=androidx.compose.ui.geometry.Offset(8.dp.toPx(),size.height-3.dp.toPx()),
                size=androidx.compose.ui.geometry.Size((size.width-16.dp.toPx()).coerceAtLeast(0f)*held.value,2.dp.toPx()))
        }
        .combinedClickable(interactionSource=interactions,indication=null,role=Role.Button,
            onLongClickLabel=if(onOpenHistory!=null) tr("Textchronik öffnen") else null,
            onLongClick=onOpenHistory?.let {open->{view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);open()}},
            onClickLabel=tr("Zitat oder Fakt wechseln"),onClick={
            if(gate.accept(SystemClock.uptimeMillis())) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);onSwap()
            }
        }).padding(horizontal=8.dp,vertical=8.dp)) {ScrambleText(text,style.copy(color=ink),color=ink)}
    }
    }
}
