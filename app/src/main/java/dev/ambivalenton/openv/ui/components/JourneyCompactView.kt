// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.sharing.JourneyShareData
import dev.ambivalenton.openv.sharing.JourneyShareRenderer
import dev.ambivalenton.openv.sharing.LiveJourneyShareRenderer
import dev.ambivalenton.openv.sharing.ShareCardColors
import dev.ambivalenton.openv.ui.theme.readableForeground
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/** Opens the compact view of a journey (details header, long press on a card or a chart bar). */
@Composable fun rememberJourneyCompactView(): (Trip)->Unit {
    var shown by remember {mutableStateOf<Trip?>(null)}
    shown?.let {trip->JourneyCompactSheet(trip) {shown=null}}
    return remember {{trip:Trip->shown=trip}}
}

/** Opens the compact view of the pinned live journey; it follows new live data while open. */
@Composable fun rememberLiveJourneyCompactView(state:PinnedJourneyState):()->Unit {
    var open by remember {mutableStateOf(false)}
    if(open) LiveJourneyCompactSheet(state) {open=false}
    return remember {{open=true}}
}

/** "Daten: … · Stand: …", with the provider's own real-time timestamp where it sent one. */
fun journeyCompactSource(trip:Trip,zone:ZoneId=dev.ambivalenton.openv.data.model.transitZone):String {
    val fetched=Instant.ofEpochMilli(trip.fetchedAt).atZone(zone)
    val date=DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(fetched)
    val rides=trip.segments.filterNot {it.isWalk}
    val provider=rides.mapNotNull {it.realtimeGeneratedAt}.maxOrNull()
    val realtime=rides.any {it.realtimeReceivedAt!=null || it.realtimeDeparture}
    return listOfNotNull(tr("Daten: %1\$s",trip.source),tr("Stand: %1\$s, %2\$s Uhr",date,clockTime(trip.fetchedAt)),
        when {
            provider!=null->tr("Echtzeit des Anbieters: %1\$s Uhr",clockTime(provider))
            !realtime->tr("ohne Echtzeitangaben")
            else->null
        }).joinToString(" · ")
}

/** Source line of the live card: the last successful live update, not the moment of opening. */
fun liveCompactSource(session:PinnedJourneySession):String =
    tr("Daten: %1\$s",session.trip.source)+" · "+tr("Letzter Live-Stand: %1\$s Uhr",clockTime(session.lastSuccessAt))

@Composable private fun JourneyCompactSheet(trip:Trip,onDismiss:()->Unit) {
    val context=LocalContext.current
    val colors=ShareCardColors.from(MaterialTheme.colorScheme)
    val data=remember(trip) {runCatching {JourneyShareData.from(trip)}}
    val card=data.getOrNull()
    CompactCardSheet(colors,JourneyShareRenderer.WIDTH,trip to colors,
        card?.let {d->{scale:Float->JourneyShareRenderer.render(context,d,colors,scale)}},
        card?.let(JourneyShareRenderer::description).orEmpty(),
        data.exceptionOrNull()?.message,journeyCompactSource(trip),onDismiss)
}

/** Build 116: the pinned journey shows the same compact summary as any connection – with its live times –
 * instead of a card of its own; the source line names the last live update. */
@Composable private fun LiveJourneyCompactSheet(state:PinnedJourneyState,onDismiss:()->Unit) {
    val session=state.session
    if(session==null) {LaunchedEffect(Unit) {onDismiss()};return}
    val context=LocalContext.current
    val colors=ShareCardColors.from(MaterialTheme.colorScheme)
    val data=remember(session.trip) {runCatching {JourneyShareData.from(session.trip)}}
    val card=data.getOrNull()
    CompactCardSheet(colors,JourneyShareRenderer.WIDTH,session.trip to colors,
        card?.let {d->{scale:Float->JourneyShareRenderer.render(context,d,colors,scale)}},
        card?.let(JourneyShareRenderer::description).orEmpty(),
        data.exceptionOrNull()?.message,liveCompactSource(session),onDismiss)
}

/** Earlier builds exported share images into the private cache; nothing is exported any more. */
/**
 * Full-height sheet in the card's frame colour: the card keeps its compact proportions, is centred
 * horizontally and vertically (scrolling only if it is taller than the screen) and the source line
 * sits at the very bottom. Swiping the handle (or the content at its top) down closes the sheet.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable internal fun CompactCardSheet(colors:ShareCardColors,logicalWidth:Int,renderKey:Any,render:((Float)->Bitmap)?,
    description:String,message:String?,source:String,onDismiss:()->Unit) {
    val context=LocalContext.current
    // Build 116: the compact view takes the whole height; the navigation pill steps aside meanwhile.
    RequestPillAside()
    val ground=Color(colors.background)
    val ink=Color(readableForeground(colors.text.toLong() and 0xffffffffL,colors.background.toLong() and 0xffffffffL))
    val shape=MaterialTheme.shapes.extraLarge.copy(bottomStart=CornerSize(0),bottomEnd=CornerSize(0))
    AppModalBottomSheet(onDismiss,Modifier.fillMaxHeight().testTag("journey-compact-view"),shape=shape,
        containerColor=ground,contentColor=ink,dragHandle={BottomSheetDefaults.DragHandle(color=ink.copy(alpha=.45f))}) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val viewport=maxHeight
            val width=minOf(maxWidth-24.dp,460.dp).coerceAtLeast(120.dp)
            val pixels=with(LocalDensity.current) {width.roundToPx()}
            val image by produceState<ImageBitmap?>(null,renderKey,pixels) {
                value=render?.let {draw->withContext(Dispatchers.Default) {draw(pixels.toFloat()/logicalWidth).asImageBitmap()}}
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().heightIn(min=viewport),contentAlignment=Alignment.Center) {
                    val shown=image
                    when {
                        message!=null->Text(message,Modifier.padding(32.dp).testTag("journey-compact-message"),
                            style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center)
                        shown!=null->Image(shown,description,Modifier.width(width).testTag("journey-compact-card"),
                            contentScale=ContentScale.FillWidth)
                    }
                }
            }
        }
        Text(source,Modifier.fillMaxWidth().navigationBarsPadding().padding(start=24.dp,end=24.dp,top=8.dp,bottom=14.dp)
            .testTag("journey-compact-source"),style=MaterialTheme.typography.labelMedium,color=ink,textAlign=TextAlign.Center)
    }
}
