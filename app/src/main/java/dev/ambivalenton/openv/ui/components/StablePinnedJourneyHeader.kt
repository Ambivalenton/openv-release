// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.components.ThemedTonalButton
import dev.ambivalenton.openv.ui.components.ThemedOutlinedButton

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.PinnedHeaderPresentation
import dev.ambivalenton.openv.data.model.reduceMotion
import dev.ambivalenton.openv.data.model.railStopNameScale
import dev.ambivalenton.openv.localization.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

fun hasWalkingLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        .any {ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED}

/** Sample burst updates at 4 Hz without starving a continuous GPS feed. Actions update immediately. */
@Composable fun rememberStablePinnedHeader(sessionId: String,input: PinnedHeaderPresentation): PinnedHeaderPresentation {
    var displayed by remember(sessionId) {mutableStateOf(input)}
    val latest by rememberUpdatedState(input)
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(sessionId,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            displayed=latest
            while(isActive) {delay(250);displayed=latest}
        }
    }
    LaunchedEffect(sessionId,input.permissionRequired,input.paused) {
        displayed=latest
    }
    return displayed
}

/**
 * Height of [lines] lines as the text engine really lays them out. An earlier version multiplied the sp line
 * height instead; with Android 14's non-linear font scaling (large system font) the drawn lines are
 * taller than that, the slot was a few pixels short and Compose ellipsised after the first line
 * ("Nächster Einstieg: …" without the platform).
 */
@Composable internal fun TextStyle.slotHeight(lines: Int=1): Dp {
    val density=LocalDensity.current;val measurer=rememberTextMeasurer()
    val style=this
    val measured=remember(style,lines,density) {
        measurer.measure(List(lines.coerceAtLeast(1)) {"ÄgjŶ"}.joinToString("\n"),
            style.copy(platformStyle=PlatformTextStyle(includeFontPadding=false)),softWrap=false).size.height
    }
    val line=if(lineHeight.isSp) lineHeight else (fontSize.value*1.35f).sp
    return with(density) {maxOf(line.toDp()*lines,measured.toDp())}+4.dp
}
/**
 * A text of the pinned view in its own slot. Build 122: no ticker any more. A text that does not fit one line
 * takes a second one when [grow] (the slot then keeps two lines while it stays on screen, so a changing value
 * never makes the card jump back); a slot that must stay one line high shrinks its type a little instead.
 */
@Composable internal fun PinnedTextSlot(value:String,style:TextStyle,lines:Int=1,color:Color=Color.Unspecified,
    tag:String,modifier:Modifier=Modifier,grow:Boolean=false,fitRail:Boolean=false,fallback:String?=null) {
    val measurer=rememberTextMeasurer();val density=LocalDensity.current
    val held=remember {androidx.compose.runtime.mutableIntStateOf(lines)}
    val quiet=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width=with(density){maxWidth.toPx()}
        val fitted=remember(value,style,width,fitRail,grow,lines,density) {
            val natural=measurer.measure(value,style,softWrap=false,maxLines=1).size.width.toFloat()
            val scale=when {
                fitRail->railStopNameScale(natural,width,true)
                // One fixed line: a slightly smaller type instead of a cut word.
                !grow && lines==1 && width>0f && natural>width->(width/natural).coerceIn(.72f,1f)
                else->1f
            }
            style.copy(fontSize=style.fontSize*scale,platformStyle=PlatformTextStyle(includeFontPadding=false))
        }
        val needed=if(!grow || width<=0f || value.isEmpty()) lines else remember(value,fitted,width) {
            measurer.measure(value,fitted,softWrap=true,maxLines=2,
                constraints=androidx.compose.ui.unit.Constraints(maxWidth=width.toInt().coerceAtLeast(1))).lineCount.coerceIn(lines,2)
        }
        LaunchedEffect(needed) {if(needed>held.intValue) held.intValue=needed}
        val shownLines=maxOf(lines,held.intValue,if(grow) 1 else lines)
        // The short form replaces a sentence that would be cut, so its key fact stays readable.
        val shown=remember(value,fallback,fitted,width,shownLines) {
            if(fallback.isNullOrEmpty() || value.isEmpty() || width<=0f) value
            else if(measurer.measure(value,fitted,overflow=TextOverflow.Ellipsis,softWrap=shownLines>1,maxLines=shownLines,
                    constraints=androidx.compose.ui.unit.Constraints(maxWidth=width.toInt().coerceAtLeast(1))).hasVisualOverflow) fallback
            else value
        }
        Box(Modifier.fillMaxWidth().height(style.slotHeight(shownLines))
            .animateContentSize(if(quiet) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio=.82f,stiffness=380f))) {
            Text(shown.ifEmpty {" "},Modifier.fillMaxWidth().testTag(tag),style=fitted,
                maxLines=shownLines,minLines=shownLines,softWrap=shownLines>1,overflow=TextOverflow.Ellipsis,color=color)
        }
    }
}
@Composable private fun FixedHeaderText(value:String,style:TextStyle,lines:Int=1,color:Color=Color.Unspecified,
    tag:String,modifier:Modifier=Modifier,fitRail:Boolean=false,grow:Boolean=false)=PinnedTextSlot(value,style,lines,color,tag,modifier,grow=grow,fitRail=fitRail)

/** Every volatile value owns a fixed, font-scaled slot. No size animation or conditional-height block. */
@Composable fun StablePinnedJourneyHeader(model: PinnedHeaderPresentation,onLocationPermission: ()->Unit,
    onResume: ()->Unit,onAlternative: ()->Unit,nextColor:Color=Color.Unspecified,arrivalColor:Color=Color.Unspecified) {
    val t=MaterialTheme.typography;val s=MaterialTheme.colorScheme
    val density=LocalDensity.current;val measurer=rememberTextMeasurer()
    val clockStyle=t.headlineLarge.copy(fontFamily=dev.ambivalenton.openv.ui.theme.LocalAppVisuals.current.customFont?:FontFamily.Monospace)
    val clockWidth=with(density) {measurer.measure("00:00",clockStyle).size.width.toDp()}+2.dp
    Column(Modifier.fillMaxWidth().testTag("stable-pinned-header"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(tr("Am Ziel"),Modifier.weight(1f),style=t.titleMedium)
            Text(model.arrival,style=t.displaySmall,maxLines=1,color=arrivalColor,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,
                modifier=Modifier.testTag("pin-arrival-time"))
        }
        if(model.position.isNotBlank())FixedHeaderText(model.position,t.bodyMedium,1,tag="pin-position",grow=true)
        FixedHeaderText(model.action,t.labelLarge,color=s.primary,tag="pin-next-action",grow=true)
        FixedHeaderText(model.nextPlace,t.headlineSmall,1,color=nextColor,tag="pin-next-place",fitRail=model.nextPlaceIsRail,grow=!model.nextPlaceIsRail)
        val timeHeight=maxOf(t.headlineLarge.slotHeight(),t.labelMedium.slotHeight()+t.titleMedium.slotHeight())
        Row(Modifier.fillMaxWidth().height(timeHeight),verticalAlignment=Alignment.Top) {
            FixedHeaderText(model.time,clockStyle,color=nextColor,tag="pin-next-time",modifier=Modifier.width(clockWidth))
            Column(Modifier.weight(1f).padding(start=10.dp)) {
                FixedHeaderText(model.timeLabel,t.labelMedium,tag="pin-time-label")
                FixedHeaderText(model.delay,t.titleMedium,color=s.error,tag="pin-delay")
            }
        }
        // Boarding information owns the whole width and may grow; a platform number is never ellipsised.
        // Build 122: a changed platform stands out – in the error colour on a soft tint that breathes in once.
        val changed=model.platformChanged
        val glow=remember {androidx.compose.animation.core.Animatable(0f)}
        val calm=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.reduceMotion
        LaunchedEffect(changed,model.platform) {
            if(!changed) glow.snapTo(0f)
            else if(calm) glow.snapTo(1f)
            else {glow.snapTo(0f);glow.animateTo(1.25f,androidx.compose.animation.core.spring(dampingRatio=.5f,stiffness=300f));glow.animateTo(1f,androidx.compose.animation.core.spring(dampingRatio=.7f,stiffness=200f))}
        }
        Text(model.platform.ifEmpty {" "},Modifier.fillMaxWidth().heightIn(min=t.titleSmall.slotHeight(2))
            .then(if(changed) Modifier.drawBehind {
                drawRoundRect(s.errorContainer.copy(alpha=(.75f*glow.value).coerceIn(0f,1f)),
                    topLeft=androidx.compose.ui.geometry.Offset(-6.dp.toPx(),0f),size=androidx.compose.ui.geometry.Size(size.width+12.dp.toPx(),size.height),
                    cornerRadius=androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
            } else Modifier).testTag("pin-platform"),
            style=t.titleSmall,minLines=2,softWrap=true,fontWeight=if(changed) androidx.compose.ui.text.font.FontWeight.Bold else null,
            color=if(changed) s.onErrorContainer else nextColor.takeUnless {it==Color.Unspecified}?:s.primary)
        if(model.nextChange.isNotBlank())Text(model.nextChange,Modifier.fillMaxWidth().testTag("pin-next-change"),style=t.bodyMedium)
        if(model.notice.isNotBlank())Text(model.notice,Modifier.fillMaxWidth().testTag("pin-connection-notice"),style=t.bodySmall,color=s.primary)
        if(model.warning.isNotBlank())Text(model.warning,Modifier.fillMaxWidth().testTag("pin-warning"),style=t.bodySmall,color=s.onSurfaceVariant)
        val actionHeight=maxOf(48.dp,t.labelLarge.slotHeight(2)+8.dp)
        if(model.permissionRequired || model.paused || model.alternative)Row(Modifier.fillMaxWidth().height(actionHeight),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(model.permissionRequired) ThemedOutlinedButton(onLocationPermission,Modifier.weight(1f).fillMaxHeight(),contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp)) {
                FittedText(tr("Standort freigeben"),textAlign=TextAlign.Center)
            }
            if(model.paused) ThemedTonalButton(onResume,Modifier.weight(1f).fillMaxHeight(),contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp)) {
                FittedText(tr("Fortsetzen"),textAlign=TextAlign.Center)
            }
            if(model.alternative) ThemedTonalButton(onAlternative,Modifier.weight(1f).fillMaxHeight(),contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp)) {
                FittedText(tr("Alternative verfolgen"),textAlign=TextAlign.Center)
            }
        }
    }
}


