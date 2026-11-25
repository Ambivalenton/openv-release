// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

/** Only display values, no raw forecast clock/token; permission is independent from GPS freshness. */
data class PinnedHeaderPresentation(
    val locationLabel: String,val position: String,val action: String,val nextPlace: String,
    val time: String,val timeLabel: String,val platform: String,val delay: String,val arrival: String,
    val warning: String,val quality: String,val permissionRequired: Boolean,val paused: Boolean,val alternative: Boolean,
    val nextPlaceIsRail:Boolean=false,val gpsConnected:Boolean=false,
    val nextChange:String="",val notice:String="",
    /** Build 122: the platform of the next boarding changed – the line is highlighted. */
    val platformChanged:Boolean=false,
)
fun pinnedHeaderPresentation(state: PinnedJourneyState,focus: JourneyFocus,disruption: String?,now: Long,
    locationPermissionGranted: Boolean): PinnedHeaderPresentation {
    val paused=state.status==PinnedTrackingStatus.PAUSED
    val warning=if(paused)tr("Reisebegleitung pausiert")else ""
    val quality=""
    val (nextPlace,rail)=pinnedStopLabel(focus,state.session?.trip)
    return PinnedHeaderPresentation(tr("Reisebegleiter"),
        assistantStepDetail(focus),focus.heading(),nextPlace,clockTime(focus.nextTime),focus.timeLabel,state.session?.let {nextBoardingInstruction(it,focus)}.orEmpty(),
        focus.delay.orEmpty(),(state.session?.let {approximateMarker(projectSessionTimes(it,now).finalArrival)}.orEmpty())+clockTime(focus.arrival),
        // Build 122: on a phone set to another zone the card names the stops' local time.
        listOfNotNull(warning.takeIf {it.isNotBlank()},transitZoneNote(focus.arrival)).joinToString(" · "),quality,false,paused,
        state.session?.let {availablePinnedAlternative(it,now,state.presence)}!=null,rail,false,
        state.session?.let {assistantTravelSummary(it,now,focus).change}.orEmpty(),state.session?.let {assistantTravelSummary(it,now,focus).notice}.orEmpty(),
        state.session?.let {nextBoardingPlatformChanged(it,focus)}==true)
}
