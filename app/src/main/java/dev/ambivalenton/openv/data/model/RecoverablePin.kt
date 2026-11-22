// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RecoverablePin(val session: PinnedJourneySession,val closedAt: Long,val presence: DevicePresence?=null)

/** Timetable feasibility plus fresh telemetry when boarding an already moving leg is uncertain. */
fun recoverablePinIsUsable(candidate: RecoverablePin,now: Long,presence: DevicePresence?=candidate.presence): Boolean {
    val observation=listOfNotNull(presence,candidate.presence).mapNotNull {crediblePresence(it,now,90_000)}.maxByOrNull {it.capturedAt}
    val trip=candidate.session.trip
    if (trip.segments.isEmpty() || trip.endTime<=now || now-candidate.closedAt !in 0..6*3_600_000L) return false
    val remaining=trip.segments.filter { it.endTime>now }
    if (remaining.any { it.delay.cancelled } || unreachablePinnedTransfer(trip.copy(segments=remaining),candidate.session.routing.ignoreWalking,candidate.session.routing.walkingSpeed)!=null) return false
    val next=remaining.firstOrNull { !it.isWalk } ?: remaining.firstOrNull() ?: return false
    if(next.isWalk) {
        val fix=observation
        return if(fix!=null) (distanceMeters(fix.location,next.destination) ?: Double.MAX_VALUE)/1.3 <= (next.endTime-now)/1000.0+180
            else now-candidate.closedAt<=120_000 || now<=next.startTime
    }
    if (now<=next.startTime) {
        val fix=observation ?: return true
        val walking=distanceMeters(fix.location,next.origin) ?: return false
        return walking/1.4 <= (next.startTime-now)/1000.0+60
    }
    val fix=observation
    if (fix!=null) {
        val projection=projectOntoStops(focusStops(trip.copy(segments=listOf(next))),fix.location)
        return projection!=null && projection.cross<=maxOf(150.0,fix.accuracyMeters*2.0)
    }
    // A very recent close of an ongoing companion can be undone without claiming GPS boarding.
    return now-candidate.closedAt<=120_000
}

fun eligibleRecoverablePins(rows: List<RecoverablePin>,now: Long,active: PinnedJourneySession?=null,
    presence: DevicePresence?=null): List<RecoverablePin> = rows.sortedByDescending { it.closedAt }
    .filter { recoverablePinIsUsable(it,now,presence ?: it.presence) && (active==null || !samePinnedJourney(active.trip,it.session.trip)) }
    .distinctBy { row -> row.session.trip.segments.filterNot { it.isWalk }.joinToString { "${it.lineName}:${it.plannedStartTime}:${it.origin.id ?: it.origin.name}:${it.origin.latitude}:${it.origin.longitude}:${it.destination.id ?: it.destination.name}" } }.take(8)
