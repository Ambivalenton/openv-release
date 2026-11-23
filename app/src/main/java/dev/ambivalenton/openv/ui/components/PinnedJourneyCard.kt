// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.ui.theme.cardElevation
import dev.ambivalenton.openv.ui.theme.appFrameShadow
import dev.ambivalenton.openv.ui.theme.appFrame
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr

/** One font-scaled template owns every slot; source updates never replace its layout. */
/** With bubble mode on, the header order is: walking button, bubble button, close button. Child
 * buttons consume their own taps and never trigger the card. */
/** [headerActions] replaces the in-app buttons (the floating bubble uses the same card with its own
 * timeline button), so app and bubble share one design that follows the selected theme. */
@Composable fun PinnedJourneyCard(state:PinnedJourneyState,onOpen:()->Unit,onRemove:()->Unit,onBubble:(()->Unit)?=null,
    onCorrectAlighting:((alighted:Boolean)->Unit)?=null,modifier:Modifier=Modifier.fillMaxWidth(),
    testTag:String="pinned-journey-card",headerActions:(@Composable RowScope.()->Unit)?=null,
    focusTransform:((JourneyFocus,Long)->JourneyFocus)?=null,
    /** Build 102: false where the screen title already names the source (Übersicht). */
    showSource:Boolean=true) {
    val session=state.session?:return
    val now=rememberJourneyNow();val t=MaterialTheme.typography;val scheme=MaterialTheme.colorScheme
    // [focusTransform]: the bubble's mini card names the next change or destination (build 81).
    val focus=assistantDisplayFocus(state,now)?.let {focusTransform?.invoke(it,now)?:it}?:return
    val arrivalColor=session.favorite.accentSeed?.let {androidx.compose.ui.graphics.Color(it)}?:scheme.primary
    val nextColor=when(focus.nextPlace){session.favorite.counterpole?.name->session.favorite.originAccentSeed?.let {androidx.compose.ui.graphics.Color(it)}?:scheme.primary;session.favorite.location.name->arrivalColor;else->scheme.primary}
    val summary=assistantTravelSummary(session,now,focus)
    val (nextPlace,railName)=pinnedStopLabel(focus,session.trip)
    val alert=state.alert?.takeIf {state.status!=PinnedTrackingStatus.PAUSED}
    val imminent=alert!=null
    val mode=LocalWalkingNavigationMode.current;val context=androidx.compose.ui.platform.LocalContext.current
    val walking=if(session.routing.ignoreWalking)null else session.trip.segments.getOrNull(focus.legIndex?:-1)?.takeIf {it.isWalk}
    var navigationError by remember(session.id){mutableStateOf<String?>(null)}
    val status=navigationError?:alert?.message?:summary.notice?:if(state.status==PinnedTrackingStatus.PAUSED)tr("Reisebegleitung pausiert")else ""
    AppCard(onClick=onOpen,shape=dev.ambivalenton.openv.ui.theme.appCorner(24.dp),
        modifier=modifier.testTag(testTag),
        colors=CardDefaults.cardColors(containerColor=scheme.surfaceContainerHigh),
        // Shadow and background belong to the rounded card itself, never to a square parent.
        elevation=CardDefaults.cardElevation(defaultElevation=3.dp)) {
        Column(Modifier.padding(start=18.dp,end=10.dp,top=8.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FittedText(session.favorite.label,style=t.labelLarge)
                    if(showSource) TransitSourceAttribution(compact=true)
                }
                if(headerActions!=null)headerActions()
                else if(walking!=null)FilledTonalIconButton({navigationError=if(openWalkingNavigation(context,walking,mode).isFailure)tr("Fußnavigation konnte nicht geöffnet werden.")else null},
                    modifier=Modifier.size(42.dp).testTag("pinned-walking-navigation"),shape=CircleShape,
                    colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=scheme.primaryContainer,contentColor=scheme.onPrimaryContainer)) {
                    AppIcon(R.drawable.ic_walk,mode.actionLabel)
                }else Spacer(Modifier.size(42.dp))
                if(headerActions==null && onBubble!=null && dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.pinnedBubble)
                    IconButton(onBubble,Modifier.size(42.dp).testTag("pinned-bubble-button")) {AppIcon(R.drawable.ic_bubble,tr("Als schwebende Reisekarte anzeigen"))}
                if(headerActions==null)IconButton(onRemove,Modifier.size(42.dp).testTag("remove-pinned-card")){AppIcon(R.drawable.ic_close,tr("Reisebegleiter beenden"))}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
                Column(Modifier.weight(1f).padding(end=12.dp)) {
                    PinnedTextSlot(focus.heading(),t.titleMedium.copy(fontWeight=FontWeight.SemiBold),tag="card-action",marquee=true)
                    PinnedTextSlot(nextPlace,t.bodyLarge,tag="card-next-place",marquee=true,fitRail=railName)
                    PinnedTextSlot(nextBoardingInstruction(session,focus),t.labelLarge,lines=2,color=nextColor,tag="card-platform",
                        fallback=nextBoardingPlatform(session,focus))
                }
                Column(Modifier.width(112.dp),horizontalAlignment=Alignment.End) {
                    PinnedTextSlot(clockTime(focus.nextTime),t.headlineMedium.copy(fontWeight=FontWeight.Bold),color=nextColor,tag="card-time")
                    PinnedTextSlot(focus.timeLabel,t.labelSmall,tag="card-time-label")
                    PinnedTextSlot(focus.delay.orEmpty(),t.labelMedium,color=scheme.error,tag="card-delay")
                }
            }
            if(summary.change.isNotBlank())Text(summary.change,Modifier.fillMaxWidth().testTag("card-next-change"),style=t.bodyMedium)
            PinnedTextSlot(summary.arrival,t.titleSmall.copy(fontWeight=FontWeight.SemiBold),color=if(arrivalChangeMinutes(session,now)>0)scheme.error else arrivalColor,tag="card-arrival",marquee=true)
            if(status.isNotBlank())Text(status,Modifier.fillMaxWidth().testTag("card-status"),style=t.bodySmall,
                color=scheme.onSurfaceVariant)
            PinnedTextSlot(assistantStepDetail(focus),t.labelMedium,tag="card-remaining-stops")
            // Sparse manual correction: only for a fresh alighting hypothesis or a clearly overdue ride.
            val correction=alightingCorrection(session,now)
            if(onCorrectAlighting!=null && (correction.offerStillAboard || correction.offerAlighted))
                Row(Modifier.fillMaxWidth().testTag("alighting-correction"),horizontalArrangement=Arrangement.End) {
                    if(correction.offerStillAboard)TextButton({onCorrectAlighting(false)}) {Text(tr("Ich bin noch im Fahrzeug"))}
                    if(correction.offerAlighted)TextButton({onCorrectAlighting(true)}) {Text(tr("Ich bin ausgestiegen"))}
                }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                JourneyWiggleProgress(focus.progress,assistantWaveActive(session,now,state.status),
                    session.id,Modifier.weight(1f).semantics{contentDescription=tr("Reisefortschritt")},urgent=journeyHasDelay(session)||session.missedBoarding!=null,imminent=imminent)
                if(!imminent){Spacer(Modifier.width(8.dp));AppIcon(R.drawable.ic_place,null,Modifier.size(18.dp))}
            }
        }
    }
}

