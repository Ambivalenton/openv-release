// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ambivalenton.openv.R
import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.localization.tr
import dev.ambivalenton.openv.ui.components.*

/** Dismissal minimizes the persisted session. Removal belongs only to the confirmed card action. */
@Composable fun PinnedJourneyDialog(
    state: PinnedJourneyState,onClose: ()->Unit,onResume: ()->Unit,onAlternative: ()->Unit,onLocationPermission: ()->Unit,
    animateEntry: Boolean=true,
    pinTransition:PinTransitionController?=null,
    onAlternativeSelected:((String,String)->Unit)?=null,
    autoAlternativePrompt:Boolean=true,
) {
    val session=state.session ?: return
    val now=rememberJourneyNow()
    val focus=assistantDisplayFocus(state,now)?:return
    var walkingRetry by remember(session.id) {mutableIntStateOf(0)}
    val walkingDirections=rememberPinnedWalkingDirections(session.trip,walkingRetry)
    val alternative=availablePinnedAlternative(session,now,state.presence)
    val alternativeKey=alternative?.let(::alternativeJourneyKey)
    var dismissedAlternative by rememberSaveable(session.id){mutableStateOf<String?>(null)}
    var showComparison by rememberSaveable(session.id){mutableStateOf(false)}
    var automaticComparison by rememberSaveable(session.id){mutableStateOf(false)}
    val suggest=dev.ambivalenton.openv.ui.theme.LocalAppSettings.current.alternativeSuggestions && state.arrival==null && alternativeSuggestionWindow(session,now)
    LaunchedEffect(alternativeKey,suggest){
        if(autoAlternativePrompt && suggest && alternative!=null && shouldPromptAssistantAlternative(session,alternative,now) && alternativeKey!=null && alternativeKey!=dismissedAlternative){automaticComparison=true;showComparison=true}
        if(!suggest && automaticComparison){showComparison=false;automaticComparison=false}
    }
    fun compare() {automaticComparison=false;if(autoAlternativePrompt)showComparison=true else onAlternative()}
    if(showComparison && alternative!=null)AlternativeJourneyDialog(session,alternative,
        onAccept={
            if(onAlternativeSelected!=null)onAlternativeSelected(session.id,alternativeJourneyKey(alternative))else onAlternative()
            dismissedAlternative=alternativeKey;showComparison=false
        },onKeep={dismissedAlternative=alternativeKey;showComparison=false})
    val scroll=rememberScrollState()
    val target=rememberJourneyScrollTarget(scroll)
    val handOff=rememberLiveJourneyHandOff(state)
    DirectModalSheet(onClose,target,"pinned-journey",fraction=1f,headerHeight=88.dp,
        animateEntry=animateEntry,color=MaterialTheme.colorScheme.background,onNavigationDismiss={}) {motion,minimize->
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().graphicsLayer {
                val transition=pinTransition
                val p=if(transition?.busy==true) ((transition.progress.value-PIN_COMMIT_FRACTION)/(1-PIN_COMMIT_FRACTION)).coerceIn(0f,1f) else 1f
                alpha=p;scaleX=.98f+.02f*p;scaleY=scaleX
            }.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))) {
                Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(start=22.dp,end=8.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    FittedText(session.favorite.label,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                    AnimatedIconButton(onResume,R.drawable.ic_refresh,tr("Aktuelle Daten laden"),IconTapMotion.SPIN)
                    AnimatedIconButton({handOff.run(Unit)},handOff.icon,handOff.label,IconTapMotion.POP,enabled=!handOff.busy)
                    IconButton(minimize,Modifier.testTag("close-pinned-journey")) {
                        AppIcon(R.drawable.ic_close,tr("Verbindung minimieren"))
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(scroll,enabled=false,overscrollEffect=null).directSheetScrollSemantics(scroll,motion::cancelContentFling).testTag("pinned-journey-scroll").padding(start=24.dp,end=24.dp,top=12.dp,bottom=96.dp+LocalNavigationClearance.current),
                    verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    val header=rememberStablePinnedHeader(session.id,pinnedHeaderPresentation(state,focus,null,now,
                        hasWalkingLocationPermission(LocalContext.current)))
                    val targetColor=when(focus.nextPlace){session.favorite.counterpole?.name->session.favorite.originAccentSeed;session.favorite.location.name->session.favorite.accentSeed;else->null}?.let {androidx.compose.ui.graphics.Color(it)}?:MaterialTheme.colorScheme.primary
                    StablePinnedJourneyHeader(header,onLocationPermission,onResume,::compare,nextColor=targetColor,
                        arrivalColor=session.favorite.accentSeed?.let {androidx.compose.ui.graphics.Color(it)}?:MaterialTheme.colorScheme.primary)
                    TransitSourceAttribution()
                    PinnedAlternativesSection(alternative,onCompare=::compare,onRefresh=onResume)
                    if(session.trip.waypoints.isNotEmpty()) Text(tr("Zwischenstopps: %1\$s",session.trip.waypoints.joinToString(" → ") {it.name}),
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
                    FadingJourneyTimeline(focus,state.refreshing,session.lastSuccessAt to state.presence,
                        activeLegId=focus.legIndex?.let {"$it:${session.trip.segments[it].id}"}.takeIf {state.status==PinnedTrackingStatus.RUNNING},
                        activeMotionKey=focus.legIndex?.let {"$it:${session.trip.segments[it].id}"},transfers=transferNodes(session.trip),favorite=session.favorite,walkingDirections=walkingDirections,onWalkingRetry={walkingRetry++},presence=state.presence)
                }
            }
            pinTransition?.let {PinMatrixOverlay(it)}
            }
    }
}


