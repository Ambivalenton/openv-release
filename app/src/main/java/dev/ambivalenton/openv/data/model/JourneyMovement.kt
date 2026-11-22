// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlinx.serialization.Serializable

/** Local cadence evidence only. Raw accelerometer samples are never stored or transmitted. */
data class DeviceActivityEvidence(val capturedAt:Long,val steps:Int,val spanMillis:Long,val cadenceHz:Float) {
    fun walking(now:Long)=now-capturedAt in 0..15_000 && steps>=4 && spanMillis>=1_200 && cadenceHz in .6f..3.2f
}
@Serializable enum class JourneyMovementMode { UNKNOWN,STILL,ON_FOOT,VEHICLE }
@Serializable data class JourneyMovementState(val mode:JourneyMovementMode=JourneyMovementMode.UNKNOWN,
    val speedMetersPerSecond:Float?=null,val observedAt:Long=0,val stepsConfirmed:Boolean=false)
@Serializable data class ActiveJourneyAnchor(val key:String,val index:Int,val confirmedAt:Long)
fun journeyLegKey(index:Int,leg:TripSegment)=if(leg.isWalk) "$index:WALK:${leg.plannedStartTime}" else stationRideKey(leg)

/** Hysteresis requires several fresh observations, not one low-speed bus sample or GPS jump. */
fun inferJourneyMovement(observations:List<DevicePresence>,activity:DeviceActivityEvidence?,now:Long,
    previous:JourneyMovementState=JourneyMovementState()):JourneyMovementState {
    val fixes=observations.distinctBy {it.capturedAt}.filter {crediblePresence(it,now,60_000)!=null}.sortedBy {it.capturedAt}.takeLast(9)
    val last=fixes.lastOrNull()
    val reported=fixes.mapNotNull {p->p.speedMetersPerSecond?.takeIf {it.isFinite() && it in 0f..110f}?.let {p.capturedAt to it}}
    val derived=if(reported.size>=2) emptyList() else fixes.mapNotNull {b->
        val a=fixes.firstOrNull {candidate->b.capturedAt-candidate.capturedAt in 15_000..50_000 && candidate.accuracyMeters<=50f && b.accuracyMeters<=50f}?:return@mapNotNull null
        val seconds=(b.capturedAt-a.capturedAt)/1000.0
        val distance=distanceMeters(a.location,b.location)?:return@mapNotNull null
        if(distance<maxOf(12.0,(a.accuracyMeters+b.accuracyMeters)*.55))null
        else (distance/seconds).takeIf {it in .1..110.0}?.let {b.capturedAt to it.toFloat()}
    }
    val speeds=if(reported.size>=2) reported else derived
    val latest=speeds.takeLast(4)
    val speed=latest.map {it.second}.sorted().let {it.getOrNull(it.size/2)}
    val span=if(latest.size>=2) latest.last().first-latest.first().first else 0
    val cloud=fixes.takeLast(4)
    val stillCloud=reported.isEmpty() && cloud.size>=4 && cloud.last().capturedAt-cloud.first().capturedAt>=12_000 &&
        cloud.all {it.accuracyMeters<=25f} && cloud.all {a->cloud.all {b->
            (distanceMeters(a.location,b.location)?:Double.MAX_VALUE)<=maxOf(6.0,minOf(12.0,(a.accuracyMeters+b.accuracyMeters)*.3))}}
    val steps=activity?.walking(now)==true
    val mode=when {
        latest.size>=2 && span>=8_000 && latest.takeLast(2).all {it.second>=4.2f}->JourneyMovementMode.VEHICLE
        steps && last!=null && (speed==null || speed<=3.8f)->JourneyMovementMode.ON_FOOT
        latest.size>=3 && span>=8_000 && latest.all {it.second in .55f..2.6f}->JourneyMovementMode.ON_FOOT
        ((latest.size>=3 && span>=8_000 && latest.all {it.second<.35f}) || stillCloud) && !steps->JourneyMovementMode.STILL
        else->null
    }
    if(mode!=null) return JourneyMovementState(mode,speed,last?.capturedAt ?: now,steps)
    return previous.takeIf {now-it.observedAt in 0..90_000} ?: JourneyMovementState()
}
fun activeOnFootLeg(session:PinnedJourneySession,now:Long):IndexedValue<TripSegment>? {
    if(session.movement.mode!=JourneyMovementMode.ON_FOOT || now-session.movement.observedAt !in 0..90_000) return null
    val last=session.trip.segments.withIndex().filter {!it.value.isWalk && stationRideKey(it.value) in session.completedRideKeys}.maxByOrNull {it.index} ?: return null
    return session.trip.segments.withIndex().firstOrNull {it.index>last.index && it.value.isWalk}
        ?.takeIf {walk->session.trip.segments.subList(last.index+1,walk.index).none {!it.isWalk}}
}
fun stableActiveJourneyLeg(session:PinnedJourneySession):String? {
    val anchor=session.activeLegAnchor ?: return null
    val entry=session.trip.segments.withIndex().firstOrNull {journeyLegKey(it.index,it.value)==anchor.key} ?: return null
    if(entry.value.delay.cancelled || (!entry.value.isWalk && stationRideKey(entry.value) in session.completedRideKeys)) return null
    return "${entry.index}:${entry.value.id}"
}

/** Complete an alighting once, then retain a stable motion owner through GNSS gaps. */
fun rememberJourneyMovement(session:PinnedJourneySession,fix:DevicePresence?,observations:List<DevicePresence>,
    activity:DeviceActivityEvidence?,now:Long):PinnedJourneySession {
    val movement=inferJourneyMovement(observations,activity,now,session.movement)
    var updated=session.copy(movement=movement)
    val current=crediblePresence(fix,now,60_000)
    if(movement.mode==JourneyMovementMode.ON_FOOT && current!=null && now-movement.observedAt in 0..30_000) {
        val completed=session.trip.segments.withIndex().filter {(_,leg)->
            !leg.isWalk && stationRideKey(leg) !in session.completedRideKeys && !leg.delay.cancelled &&
                now>=minOf(leg.startTime,leg.plannedStartTime) &&
                (distanceMeters(current.location,leg.destination) ?: Double.MAX_VALUE) in maxOf(18.0,current.accuracyMeters*.65)..450.0 &&
                ((distanceMeters(current.location,leg.origin) ?: 0.0)>250.0 || now>=leg.endTime) &&
                (movement.stepsConfirmed || now>=leg.endTime+30_000)
        }.maxByOrNull {it.index}
        if(completed!=null) {
            val key=stationRideKey(completed.value)
            updated=updated.copy(completedRideKeys=updated.completedRideKeys+key,
                stationArrival=updated.stationArrival?.takeUnless {it.rideKey==key})
        }
    }
    if(stationArrivalRide(updated,now)!=null) return updated.copy(activeLegAnchor=null)
    val walking=activeOnFootLeg(updated,now)
    val measured=activeJourneyLeg(updated.trip,now,current)
    val candidate=walking ?: updated.trip.segments.withIndex().firstOrNull {"${it.index}:${it.value.id}"==measured}
    val previous=stableActiveJourneyLeg(updated)
    if(candidate==null) return if(previous==null) updated.copy(activeLegAnchor=null) else updated
    if(!candidate.value.isWalk && stationRideKey(candidate.value) in updated.completedRideKeys) return updated
    val same=updated.activeLegAnchor?.key==journeyLegKey(candidate.index,candidate.value)
    val old=updated.activeLegAnchor?.let {a->updated.trip.segments.withIndex().firstOrNull {journeyLegKey(it.index,it.value)==a.key}?.value}
    val canSwitch=previous==null || same || (candidate.value.isWalk && movement.mode==JourneyMovementMode.ON_FOOT &&
        (old==null || old.isWalk || stationRideKey(old) in updated.completedRideKeys)) ||
        (!candidate.value.isWalk && (movement.mode==JourneyMovementMode.VEHICLE || (current?.speedMetersPerSecond ?: 0f)>=3.5f))
    return if(canSwitch) updated.copy(activeLegAnchor=if(same) updated.activeLegAnchor else
        ActiveJourneyAnchor(journeyLegKey(candidate.index,candidate.value),candidate.index,now)) else updated
}
fun VisualJourneyProgress.withMovement(session:PinnedJourneySession,now:Long):VisualJourneyProgress {
    val base=copy(completedRideKeys=session.completedRideKeys)
    if(stationArrivalRide(session,now)!=null) return base.copy(stableLegId=null)
    val id=activeOnFootLeg(session,now)?.let {"${it.index}:${it.value.id}"} ?: stableActiveJourneyLeg(session) ?: return base
    val first=focusStops(session.trip).indexOfFirst {it.legId==id}.coerceAtLeast(0)
    return base.copy(stableLegId=id,nextIndex=maxOf(first,nextIndex))
}
