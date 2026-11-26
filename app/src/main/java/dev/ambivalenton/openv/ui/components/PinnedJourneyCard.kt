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
import androidx.compose.ui.draw.clipToBounds
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
    showSource:Boolean=true,
    /** Build 116: the running journey fills the overview – larger type and room. */
    expanded:Boolean=false) {
    val session=state.session?:return
    val now=rememberJourneyNow();val t=MaterialTheme.typography;val scheme=MaterialTheme.colorScheme
    // [focusTransform]: the bubble's mini card names the next change or destination (build 81).
    val focus=assistantDisplayFocus(state,now)?.let {focusTransform?.invoke(it,now)?:it}?:return
    val arrivalColor=session.favorite.accentSeed?.let {androidx.compose.ui.graphics.Color(it)}?:scheme.primary
    val nextColor=when(focus.nextPlace){session.favorite.counterpole?.name->session.favorite.originAccentSeed?.let {androidx.compose.ui.graphics.Color(it)}?:scheme.primary;session.favorite.location.name->arrivalColor;else->scheme.primary}
    val summary=assistantTravelSummary(session,now,focus)
    val alert=state.alert?.takeIf {state.status!=PinnedTrackingStatus.PAUSED}
    val imminent=alert!=null
    val mode=LocalWalkingNavigationMode.current;val context=androidx.compose.ui.platform.LocalContext.current
    val walking=if(session.routing.ignoreWalking)null else session.trip.segments.getOrNull(focus.legIndex?:-1)?.takeIf {it.isWalk}
    var navigationError by remember(session.id){mutableStateOf<String?>(null)}
    AppCard(onClick=onOpen,shape=dev.ambivalenton.openv.ui.theme.appCorner(24.dp),
        modifier=modifier.testTag(testTag),
        colors=CardDefaults.cardColors(containerColor=scheme.surfaceContainerHigh),
        // Shadow and background belong to the rounded card itself, never to a square parent.
        elevation=CardDefaults.cardElevation(defaultElevation=3.dp)) {
        Column(Modifier.then(if(expanded) Modifier.fillMaxSize() else Modifier).padding(start=18.dp,end=10.dp,top=8.dp,bottom=12.dp),
            verticalArrangement=Arrangement.spacedBy(if(expanded) 10.dp else 5.dp)) {
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
            // Build 116: every fact once – the step in focus, the onward ride with its platform, the arrival only when it moves.
            val brief=pinnedJourneyBrief(session,focus,now,alert)
            PinnedJourneyBriefView(brief,nextColor,arrivalColor,expanded,now)
            // Build 125: the question how the ride went comes at its end (or when a due ride has not come yet),
            // not right after boarding, where it only filled the page.
            val askNow=brief.kind==BriefStepKind.BOARD || brief.stage==BriefStage.SOON
            val quietStatus=navigationError?:if(state.status==PinnedTrackingStatus.PAUSED)tr("Reisebegleitung pausiert")else summary.notice?.takeIf {alert==null}
            if(!quietStatus.isNullOrBlank())Text(quietStatus,Modifier.fillMaxWidth().testTag("card-status"),style=t.bodySmall,
                color=scheme.onSurfaceVariant)
            // The page: the facts at the top, the rest of the way in the middle, correction and progress at the bottom.
            if(expanded) {
                // The rest of the way takes the free middle; on a short screen it is cut at the bottom, never the actions below.
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    // Build 124: measured at its own height (cut below), never squeezed: a tight height made the stop names shrink to nothing.
                    // Build 125: only the rides the brief does not show already (it shows the current one and the onward one).
                    val shownRides=(if(session.trip.segments.getOrNull(focus.legIndex ?: -1)?.isWalk==false)1 else 0)+(if(brief.next!=null)1 else 0)
                    Column(Modifier.wrapContentHeight(Alignment.Top,unbounded=true)) {Spacer(Modifier.height(8.dp));JourneyRemainingSteps(session,focus.legIndex ?: 0,skipRides=shownRides)}
                }
                // Build 125: the rating only when it has something to say (a safe journey needs no line of its own).
                JourneyReliabilityLine(session,onlyWhenNotable=true)
                // Build 117: the person's own report on the ride in focus (once it is under way or due).
                if(askNow) session.trip.segments.getOrNull(focus.legIndex ?: -1)?.takeIf {!it.isWalk && (!focus.boarding || now>=it.plannedStartTime) &&
                    stationRideKey(it) !in session.completedRideKeys}?.let {RideFeedbackRow(session,it)}
            }
            // Build 117: at a station (start, change or on the way to it) up to three other connections.
            if(choicePoint(brief)) JourneyChoicesRow(state,visibleJourneyChoices(state,now),expanded)
            // Sparse manual correction: only for a fresh alighting hypothesis or a clearly overdue ride.
            val correction=alightingCorrection(session,now)
            if(onCorrectAlighting!=null && (correction.offerStillAboard || correction.offerAlighted))
                Row(Modifier.fillMaxWidth().testTag("alighting-correction"),horizontalArrangement=Arrangement.End) {
                    if(correction.offerStillAboard)TextButton({onCorrectAlighting(false)}) {Text(tr("Ich bin noch im Fahrzeug"))}
                    if(correction.offerAlighted)TextButton({onCorrectAlighting(true)}) {Text(tr("Ich bin ausgestiegen"))}
                }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                // Build 123: the wave in the mood of the moment (boarding, riding on time or late, walking, tight change …).
                JourneyWave(focus.progress,pinnedWaveMood(session,focus,brief,state.status,imminent,now),
                    Modifier.weight(1f).semantics{contentDescription=tr("Reisefortschritt")})
                if(!imminent){Spacer(Modifier.width(8.dp));AppIcon(R.drawable.ic_place,null,Modifier.size(18.dp))}
            }
        }
    }
}

