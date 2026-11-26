// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

const val POWER_LOCATION_INTERVAL_MS=15*60_000L
const val POWER_SERVER_INTERVAL_MS=120_000L
const val POWER_GPS_CHECK_INTERVAL_MS=300_000L
val AppSettings.reduceMotion:Boolean get()=quietMode||powerSaving
fun locationCacheMillis(powerSaving:Boolean)=if(powerSaving)POWER_LOCATION_INTERVAL_MS else 60_000L
fun serverPollMillis(powerSaving:Boolean)=if(powerSaving)POWER_SERVER_INTERVAL_MS else 60_000L

/** Provider-owned progress with occasional position checks; checks never become route progress. */
fun PinnedJourneySession.withPowerSaving(enabled:Boolean):PinnedJourneySession {
    val changed=copy(routing=routing.copy(powerSaving=enabled))
    return if(!changed.usesServerProgress)changed else changed.withoutGpsEvidence()
}

val PinnedJourneySession.usesServerProgress:Boolean get()=routing.powerSaving || locationUnavailable
fun PinnedJourneySession.withoutGpsEvidence()=copy(gpsCursor=null,stationArrival=null,movement=JourneyMovementState(),
    activeLegAnchor=null,missedBoarding=null,lastTrackedPresence=null,observedCalls=emptyList(),rideDeviation=null,localArrival=null)
fun PinnedJourneySession.withLocationAvailability(available:Boolean):PinnedJourneySession =
    copy(locationUnavailable=!available).let {if(it.usesServerProgress)it.withoutGpsEvidence()else it}

/** Prefer the platform network provider for routine low-power fixes; gestures may request GPS. */
fun locationProviderNames(available:List<String>,precise:Boolean,powerSaving:Boolean,force:Boolean):List<String> {
    val allowed=available.filter {it=="network"||it=="fused"||(precise&&it=="gps")}.distinct()
    if(!powerSaving||force)return allowed
    return listOfNotNull(listOf("network","fused").firstOrNull {it in allowed}?:allowed.firstOrNull())
}
