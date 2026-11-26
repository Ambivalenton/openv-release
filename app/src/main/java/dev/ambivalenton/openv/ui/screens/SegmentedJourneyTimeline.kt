// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.ui.components.*
import dev.ambivalenton.openv.ui.theme.timelineColorPalette

/** Progress changes colour, never row geometry. Refresh tokens do not drive motion or visibility. */
@Suppress("UNUSED_PARAMETER")
@Composable fun FadingJourneyTimeline(focus: JourneyFocus,active: Boolean,updateToken: Any?,activeLegId: String?=null,transfers:List<TransferNode> = emptyList(),
    walkingDirections:Map<String,WalkingDirectionsState> = emptyMap(),onWalkingRetry:()->Unit={},activeMotionKey:String?=activeLegId,presence:DevicePresence?=null,favorite:Favorite?=null) {
    val current=journeyTimelineVisibleFrom(focus.stops,focus.nextIndex,activeLegId)
    val phase=key(activeMotionKey) {rememberSegmentPhase(activeLegId!=null,2_800)}
    val palette=timelineColorPalette()
    Column(Modifier.fillMaxWidth().testTag("focus-wiggle-timeline")) {
        focus.stops.forEachIndexed {index,stop->key(stop.key) {
            val transfer=index>0 && focus.stops[index-1].legId!=stop.legId
            AnimatedVisibility(stop.key !in focus.passedStopKeys,enter=fadeIn(calmSpec(tween(200))),exit=fadeOut(calmSpec(tween(240)))+shrinkVertically(calmSpec(tween(300)))) {
            Column {
            if(index==0 || focus.stops.getOrNull(index-1)?.legId!=stop.legId) {
                transfers.firstOrNull {it.departingLegId==stop.legId}?.let {node->
                    val previousWalk=walkingDirections.entries.lastOrNull {it.key.substringBefore(':').toIntOrNull()?.let {i->i>(node.arrivingLegId.substringBefore(':').toIntOrNull()?:-1) && i<(stop.legId.substringBefore(':').toIntOrNull()?:0)}==true}?.value
                    TransferNavigationCard(node,previousWalk)
                }
            }
            PinnedTimelineStopRow(stop,palette[stop.lineType].rail.copy(alpha=if(index<current) .28f else 1f),
                active=stop.legId==activeLegId,phase={phase.value},
                first=index==0 || transfer,
                last=index==focus.stops.lastIndex || focus.stops.getOrNull(index+1)?.legId!=stop.legId,
                faded=index==current && index>0,transfer=transfer,
                endpointColor=(when {favorite?.counterpole?.let {sameLocation(it,stop.location)}==true->favorite.originAccentSeed;favorite!=null && sameLocation(favorite.location,stop.location)->favorite.accentSeed;else->null})?.let {androidx.compose.ui.graphics.Color(it)}?:androidx.compose.ui.graphics.Color.Unspecified)
            }}
            if(stop.lineType==LineType.WALK && (index==0 || focus.stops[index-1].legId!=stop.legId)) {
                walkingDirections[stop.legId]?.let {directions->
                    PinnedWalkingDirectionRows(stop.legId,directions,palette[stop.lineType].rail,stop.legId==activeLegId,{phase.value},onWalkingRetry,
                        presence=presence,completed=focus.stops.filter {it.legId==stop.legId}.all {it.key in focus.passedStopKeys})
                }
            }
        }}
    }
}

/** A single text measurement supplies the first-line centre and font diameter before drawing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun PinnedTimelineStopRow(stop: JourneyFocusStop,color: androidx.compose.ui.graphics.Color,
    active: Boolean,phase: ()->Float,first: Boolean,last: Boolean,faded: Boolean,transfer: Boolean,endpointColor:androidx.compose.ui.graphics.Color=androidx.compose.ui.graphics.Color.Unspecified) {
    val density=LocalDensity.current;val measurer=rememberTextMeasurer()
    val nameStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.SemiBold,
        platformStyle=PlatformTextStyle(includeFontPadding=false))
    val timeStyle=MaterialTheme.typography.bodyMedium.copy(fontFamily=dev.ambivalenton.openv.ui.theme.LocalAppVisuals.current.customFont?:FontFamily.Monospace,
        platformStyle=PlatformTextStyle(includeFontPadding=false))
    val fontPx=with(density) {nameStyle.fontSize.toPx()}
    val railWidth=with(density) {(fontPx+2*STOP_RING_GAP_PX+8f).toDp()}.coerceAtLeast(116.dp)
    val timeWidthPx=remember(timeStyle,density) {measurer.measure("00:00",timeStyle).size.width}
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top=if(transfer) 24.dp else 0.dp).testTag("focus-stop-${stop.key}")) {
        val labelWidth=with(density) {(maxWidth.roundToPx()-railWidth.roundToPx()-timeWidthPx-10.dp.roundToPx()).coerceAtLeast(1)}
        val name=remember(stop.location,stop.lineType) {displayStopName(stop.location,stop.lineType)}
        val fittedStyle=remember(name,nameStyle,labelWidth,density,stop.lineType) {
            val base=if(!stop.location.isRailStationName())nameStyle else {
                val natural=measurer.measure(name,nameStyle,softWrap=false,maxLines=1).size.width
                nameStyle.copy(fontSize=nameStyle.fontSize*railStopNameScale(natural.toFloat(),labelWidth.toFloat(),true))
            }
            // Build 124: a long name first gets a little smaller on one line; only then it takes a second line,
            // broken between words, after "-" or "/" ("DO-Nette/" | "Oestrich S") – never inside a word
            // ("Westfalenpar" | "k"). Smaller still only when even that does not fit.
            fun fits(scale:Float,lines:Int):Boolean {
                val l=measurer.measure(name,base.copy(fontSize=base.fontSize*scale),softWrap=lines>1,maxLines=lines,
                    constraints=Constraints(maxWidth=labelWidth))
                return !l.didOverflowWidth && !l.didOverflowHeight && l.lineCount<=lines && dev.ambivalenton.openv.ui.components.breaksOnlyBetweenWords(l)
            }
            val scale=listOf(1f,.94f,.88f,.82f).firstOrNull {fits(it,1)}
                ?: listOf(1f,.94f,.88f,.82f,.76f).firstOrNull {fits(it,2)}
                ?: listOf(.76f,.7f,.64f,.58f,.52f).firstOrNull {fits(it,1)||fits(it,2)} ?: .5f
            if(scale==1f) base else base.copy(fontSize=base.fontSize*scale)
        }
        val layout=remember(name,fittedStyle,labelWidth,density) {
            measurer.measure(name,fittedStyle,softWrap=false,maxLines=1,constraints=Constraints(maxWidth=labelWidth))
        }
        val firstCentre=(layout.getLineTop(0)+layout.getLineBottom(0))/2
        // Keep the entire halo inside this rail layer, so the previous row cannot paint into it.
        val topInsetPx=(stopCutoutRadiusPx(fontPx)-firstCentre).coerceAtLeast(0f)
        val topInset=with(density) {topInsetPx.toDp()}
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min=58.dp),verticalAlignment=Alignment.Top) {
            SegmentStopRail(color,active,phase,first,last,faded,
                Modifier.width(railWidth).fillMaxHeight().testTag("focus-mode-${stop.key}"),fontPx,firstCentre+topInsetPx,
                walking=stop.lineType==LineType.WALK)
            Column(Modifier.weight(1f).padding(top=topInset,end=10.dp,bottom=14.dp)) {
                // Build 122: no ticker – long names and lines wrap onto a second line.
                Text(name,Modifier.fillMaxWidth().testTag("focus-label-${stop.key}").semantics {contentDescription=stop.location.name},
                    style=fittedStyle,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Clip)
                Text(stop.line,style=MaterialTheme.typography.labelSmall,color=color,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text(clockTime(stop.time),Modifier.padding(top=topInset).width(with(density) {timeWidthPx.toDp()}),style=timeStyle,maxLines=1,
                color=if(stop.cancelled) MaterialTheme.colorScheme.error else if(endpointColor!=androidx.compose.ui.graphics.Color.Unspecified)endpointColor else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
