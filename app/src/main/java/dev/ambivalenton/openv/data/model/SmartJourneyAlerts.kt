// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

/** One mapping supplies timeline cards, pinned cards and local Android notifications. */
data class TransferNode(val arrivingLegId:String,val departingLegId:String,val arrivalPlatform:String?,
    val departurePlatform:String?,val nextLine:String,val nextDirection:String,
    val changesMode:Boolean=false,val arrivalLocation:Location?=null,val departureLocation:Location?=null) {
    fun snippet():String = listOfNotNull(arrivalPlatform?.let {tr("Ankunft %1\$s",it)},
        tr("Weiter mit %1\$s Richtung %2\$s",nextLine,nextDirection),departurePlatform).joinToString(" · ")
}

fun platformName(raw:String?,type:LineType):String? = raw?.trim()?.takeIf {it.isNotEmpty()}?.let {input->
    val prefix=Regex("(?i)^(?:(?:[us][ -]?bahn)\\s+)?(?:gleis|steig|bussteig|bahnsteig|platform)\\b\\s*[:.]?\\s*")
    var value=input
    repeat(3){value=value.replaceFirst(prefix,"").trim()}
    val name=when(type){LineType.BUS->tr("Bussteig");LineType.TRAM->tr("Steig");else->tr("Gleis")}
    tr("%1\$s %2\$s",name,value)
}

fun transferNodes(trip:Trip):List<TransferNode> = trip.segments.withIndex().filterNot {it.value.isWalk}
    .zipWithNext().map { (before,after)->
        TransferNode("${before.index}:${before.value.id}","${after.index}:${after.value.id}",
            platformName(before.value.arrivalPlatform,before.value.lineType),
            platformName(after.value.departurePlatform,after.value.lineType),after.value.lineName,
            after.value.direction?.takeIf {it.isNotBlank()} ?: after.value.destination.name,
            changesMode=transferModeGroup(before.value.lineType)!=transferModeGroup(after.value.lineType),arrivalLocation=before.value.destination,departureLocation=after.value.origin)
    }

enum class JourneyAlertKind { BOARD, EXIT, TRANSFER }
enum class AlertEvidence { GPS, TIMETABLE }
data class JourneyAlert(val key:String,val kind:JourneyAlertKind,val evidence:AlertEvidence,
    val title:String,val message:String,val transfer:TransferNode?=null)

/** No boarding claim is inferred from time alone. Stale/inaccurate fixes cannot cause GPS alerts. */
fun smartJourneyAlert(session:PinnedJourneySession,now:Long,presence:DevicePresence?,progress:VisualJourneyProgress?=null):JourneyAlert? {
    val gps=if(session.usesServerProgress)null else currentJourneyGps(presence,now)
    if(gps!=null) return gpsJourneyAlert(session,now,gps,progress ?: resolvedJourneyProgress(advanceGpsJourney(session,gps,listOf(gps),now),now,gps))
    val trip=session.trip
    val fix=(if(session.usesServerProgress)null else crediblePresence(presence,now))?.takeIf {it.accuracyMeters<=80f}
    val matched=activeJourneyLeg(trip,now,fix)
    val spatial=progress?.takeIf {it.corrected && !it.estimated && now-it.computedAt in 0..15_000 && fix!=null}
        ?.let {focusStops(trip).getOrNull(it.nextIndex)?.legId}
    val active=matched ?: spatial
    val rides=trip.segments.withIndex().filterNot {it.value.isWalk || it.value.delay.cancelled || stationRideKey(it.value) in session.completedRideKeys}
    val transfers=transferNodes(trip)
    // Prefer the geometrically matched ride over a possibly premature provider arrival time.
    val boarding=rides.firstOrNull {candidate ->
        val leg=candidate.value
        val previous=rides.lastOrNull {it.index<candidate.index}?.value
        leg.startTime-now in 1..90_000 && (previous==null || now>=previous.endTime) &&
            (fix==null || (distanceMeters(fix.location,leg.origin) ?: Double.MAX_VALUE)+fix.accuracyMeters<=400)
    }
    val activeRide=rides.firstOrNull {"${it.index}:${it.value.id}"==active}
    // A clear in-vehicle spatial match wins over a timetable-only boarding candidate.
    val alighted=activeRide!=null && now>=activeRide.value.endTime && boarding!=null && (fix?.speedMetersPerSecond ?: 0f)<2f
    val ride=(if(alighted) boarding else activeRide) ?: boarding ?: rides.firstOrNull {now<=it.value.endTime+90_000} ?: return null
    val leg=ride.value;val id="${ride.index}:${leg.id}"
    val actionId="${ride.index}:${leg.lineType}:${leg.lineName}:${leg.plannedStartTime}"
    val nearStart=fix?.let {(distanceMeters(it.location,leg.origin) ?: Double.MAX_VALUE)+it.accuracyMeters<=400}==true
    val nearEnd=fix?.let {(distanceMeters(it.location,leg.destination) ?: Double.MAX_VALUE)+it.accuracyMeters<=400}==true
    if(now<leg.startTime) {
        if(leg.startTime-now>90_000 || (fix!=null && !nearStart)) return null
        return JourneyAlert("${session.id}:$actionId:board",JourneyAlertKind.BOARD,if(nearStart) AlertEvidence.GPS else AlertEvidence.TIMETABLE,
            tr("Einstieg steht bevor"),listOfNotNull(if(nearStart) tr("Jetzt Einstieg prüfen: %1\$s Richtung %2\$s",leg.lineName,leg.direction ?: leg.destination.name)
                else tr("Gleich einsteigen: %1\$s Richtung %2\$s",leg.lineName,leg.direction ?: leg.destination.name),
                platformName(leg.departurePlatform,leg.lineType)).joinToString(" · "))
    }
    val transfer=transfers.firstOrNull {it.arrivingLegId==id}
    val gpsExit=nearEnd && (active==id || (fix?.speedMetersPerSecond ?: 0f)>=2f || now>=leg.endTime-90_000)
    val timeExit=now in (leg.endTime-90_000)..leg.endTime
    // Credible rail GPS away from the destination vetoes an early timetable warning.
    if(!gpsExit && (!timeExit || (fix!=null && leg.lineType in GPS_PRIORITY_RAIL && !nearEnd))) return null
    val evidence=if(gpsExit) AlertEvidence.GPS else AlertEvidence.TIMETABLE
    val title=if(transfer!=null) tr("Umstieg steht bevor") else tr("Ausstieg steht bevor")
    val message=if(gpsExit) tr("Gleich aussteigen! Zielhalt: %1\$s",leg.destination.name)
        else tr("Gleich aussteigen! Zielhalt: %1\$s",leg.destination.name)
    return JourneyAlert("${session.id}:$actionId:exit",if(transfer!=null) JourneyAlertKind.TRANSFER else JourneyAlertKind.EXIT,
        evidence,title,listOfNotNull(message,transfer?.snippet()).joinToString(" · "),transfer)
}

/** A transfer snippet is useful as soon as the next stop is the transfer, before urgent alerts. */
fun pendingTransferForFocus(trip:Trip,focus:JourneyFocus):TransferNode? {
    val id=focus.stops.getOrNull(focus.nextIndex)?.legId ?: return null
    val arriving=trip.segments.withIndex().firstOrNull {"${it.index}:${it.value.id}"==id}?.value ?: return null
    if(arriving.isWalk || arriving.delay.cancelled || focus.line!=arriving.lineName || focus.nextPlace!=arriving.destination.name) return null
    return transferNodes(trip).firstOrNull {it.arrivingLegId==id}
}

/** Every mode uses a spatially selected, confirmed ride when GPS is available. */
private fun gpsJourneyAlert(session:PinnedJourneySession,now:Long,fix:DevicePresence,progress:VisualJourneyProgress):JourneyAlert? {
    if(progress.positionSource!=JourneyPositionSource.GPS) return null
    val id=progress.boardingLegId ?: progress.stableLegId ?: return null
    val ride=session.trip.segments.withIndex().firstOrNull {"${it.index}:${it.value.id}"==id} ?: return null
    val leg=ride.value
    if(leg.isWalk || leg.delay.cancelled || stationRideKey(leg) in session.completedRideKeys) return null
    val action="${session.id}:${ride.index}:${stationRideKey(leg)}"
    if(progress.boardingLegId!=null) {
        if(leg.effectiveDeparture()-now !in -90_000..90_000 || (distanceMeters(fix.location,leg.origin) ?: Double.MAX_VALUE)>STATION_EXIT_METERS) return null
        return JourneyAlert("$action:board",JourneyAlertKind.BOARD,AlertEvidence.GPS,tr("Einstieg steht bevor"),
            listOfNotNull(tr("Jetzt Einstieg prüfen: %1\$s Richtung %2\$s",leg.lineName,leg.direction ?: leg.destination.name),platformName(leg.departurePlatform,leg.lineType)).joinToString(" · "))
    }
    val next=focusStops(session.trip).getOrNull(progress.nextIndex) ?: return null
    if(!sameLocation(next.location,leg.destination) || (distanceMeters(fix.location,leg.destination) ?: Double.MAX_VALUE)+fix.accuracyMeters>maxOf(400.0,(fix.speedMetersPerSecond ?: 0f)*90.0)) return null
    val distance=distanceMeters(fix.location,leg.destination) ?: return null
    val speed=fix.speedMetersPerSecond ?: session.movement.speedMetersPerSecond ?: 0f
    if(distance+fix.accuracyMeters>100 && (speed<1f || distance/speed>90))return null
    val transfer=transferNodes(session.trip).firstOrNull {it.arrivingLegId==id}
    return JourneyAlert("$action:exit",if(transfer==null) JourneyAlertKind.EXIT else JourneyAlertKind.TRANSFER,AlertEvidence.GPS,
        if(transfer==null) tr("Ausstieg steht bevor") else tr("Umstieg steht bevor"),
        listOfNotNull(tr("Gleich aussteigen! Zielhalt: %1\$s",leg.destination.name),transfer?.snippet()).joinToString(" · "),transfer)
}

private fun transferModeGroup(type:LineType)=when(type){LineType.REGIONAL,LineType.SUBURBAN,LineType.ICE->"rail";else->type.name}
