// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable enum class WalkingSpeed(val metersPerSecond:Double,private val titleKey:String) {
    RELAXED(.9,"Entspannt"), AVERAGE(1.25,"Durchschnittlich"), FAST(1.7,"Schnell");
    val title get()=tr(titleKey)
}

/** Distance is authoritative when supplied by the route provider; otherwise scale its duration.
 * Vehicle timestamps and the underlying Trip are never rewritten by display preferences. */
fun walkingDurationMillis(segment:TripSegment,speed:WalkingSpeed):Long {
    val original=(segment.endTime-segment.startTime).coerceAtLeast(0L)
    if(!segment.isWalk) return original
    val meters=segment.distanceMeters?.takeIf {it>0}
    return (if(meters!=null) meters/speed.metersPerSecond*1000.0
        else original.toDouble()*segment.walkingSpeedHint.metersPerSecond/speed.metersPerSecond)
        .roundToLong().coerceIn(0L,7L*24*60*60*1000)
}

data class TimelineAssembly(val trip:Trip,val walksShown:Boolean,val infeasibleTransferIds:Set<String>)

/** Backward access legs, forward egress legs, and fixed timetable edges in between. */
fun assembleTimeline(trip:Trip,settings:AppSettings):TimelineAssembly {
    val original=trip.segments
    if(settings.ignoreWalking || !settings.timelineShowWalking) return TimelineAssembly(trip.copy(segments=original.filterNot {it.isWalk}),false,emptySet())
    val first=original.indexOfFirst {!it.isWalk};val last=original.indexOfLast {!it.isWalk}
    val parts=original.toMutableList();val conflicts=mutableSetOf<String>()
    fun place(index:Int,start:Long,end:Long) {
        val s=original[index]
        parts[index]=s.copy(startTime=start,endTime=end,plannedStartTime=start,plannedEndTime=end)
    }
    if(first<0) {
        var anchor=trip.startTime
        original.forEachIndexed {i,s->val end=anchor+walkingDurationMillis(s,settings.walkingSpeed);place(i,anchor,end);anchor=end}
    } else {
        var anchor=original[first].startTime
        for(i in first-1 downTo 0) {val start=anchor-walkingDurationMillis(original[i],settings.walkingSpeed);place(i,start,anchor);anchor=start}
        anchor=original[last].endTime
        for(i in last+1..original.lastIndex) {val end=anchor+walkingDurationMillis(original[i],settings.walkingSpeed);place(i,anchor,end);anchor=end}
        var previousEnd=original[first].endTime
        for(i in first+1 until last) {
            val s=original[i]
            if(!s.isWalk) {previousEnd=s.endTime;continue}
            val start=maxOf(previousEnd,s.startTime)
            val end=start+walkingDurationMillis(s,settings.walkingSpeed)
            place(i,start,end);previousEnd=end
            val nextRide=original.drop(i+1).firstOrNull {!it.isWalk}
            if(nextRide!=null && end>nextRide.startTime) conflicts+=s.id
        }
    }
    val notes=if(conflicts.isEmpty()) trip.notices else (trip.notices+tr("Mit diesem Gehtempo ist ein Anschluss zu knapp.")).distinct()
    return TimelineAssembly(trip.copy(segments=parts,notices=notes),true,conflicts)
}
