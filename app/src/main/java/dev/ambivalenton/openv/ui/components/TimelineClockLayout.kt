// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.ui.components

import dev.ambivalenton.openv.data.model.Trip
import dev.ambivalenton.openv.data.model.TripSegment

/** Walks up to and including this duration show only the vehicle time, attached to its bar, and
 * their footprints run towards (or away from) it. Display only: the walk keeps its full duration
 * in every reachability and transfer calculation. Configurable starting value. */
const val SHORT_WALK_LABEL_MILLIS=3*60_000L

fun TripSegment.isShortWalkForLabels()=isWalk && endTime-startTime<=SHORT_WALK_LABEL_MILLIS

/** How a clock is drawn (build 73). Vehicle times outside their bar are chips welded to the bar
 * edge ([ABOVE_BAR]: departure, [BELOW_BAR]: arrival), so a footprint track never seems to start at
 * the time. Walking times float as their own chip at the walk's start or end. [INSIDE] is frameless
 * text at the top or bottom inside the bar, with a 1 px contrast contour and a light shadow.
 * Chips only for short bars; a bar with room for both times and its line name
 * ([timelineBarHoldsClocks]) always carries its times inside. */
enum class TimelineClockPlacement { ABOVE_BAR,BELOW_BAR,INSIDE,WALK_START,WALK_END }

/** Positions are relative to the trip start. Vehicle labels always take precedence.
 * [attachedWalkIndex] names a short walk whose footprints lead to this vehicle time.
 * [topDp] is the top of a box of the label height; chips align to the box edge facing their bar. */
data class TimelineClockLabel(val key:String,val segmentIndex:Int,val time:Long,val topDp:Float,
    val walking:Boolean=false,val insideVehicle:Boolean=false,val attachedWalkIndex:Int?=null,
    val placement:TimelineClockPlacement=TimelineClockPlacement.INSIDE)

/** Box height of one clock in dp; the chip with its padding fits exactly into it at 100 %. */
fun timelineClockHeight(fontScale:Float)=20f*fontScale

/** A bar this long holds its departure and arrival time inside (top and bottom) and keeps
 * the line name in between; shorter bars show the times as chips outside. */
fun timelineBarHoldsClocks(heightDp:Float,fontScale:Float)=heightDp>=timelineClockHeight(fontScale)*3

fun timelineClockLabels(trip:Trip,minuteDp:Float,fontScale:Float):List<TimelineClockLabel> {
    if(trip.segments.isEmpty()) return emptyList()
    val height=timelineClockHeight(fontScale)
    fun offset(time:Long)=((time-trip.startTime)/60_000.0*minuteDp).toFloat()
    val vehicles=buildList {
        trip.segments.forEachIndexed { index,segment ->
            if(segment.isWalk) return@forEachIndexed
            val previous=trip.segments.take(index).lastOrNull {!it.isWalk}
            val next=trip.segments.drop(index+1).firstOrNull {!it.isWalk}
            val y=offset(segment.startTime);val end=offset(segment.endTime);val h=end-y
            val outsideStart=previous==null || y-offset(previous.endTime)>=height*2
            val outsideEnd=next==null || offset(next.startTime)-end>=height*2
            val walkBefore=(index-1).takeIf {trip.segments.getOrNull(it)?.isShortWalkForLabels()==true}
            val walkAfter=(index+1).takeIf {trip.segments.getOrNull(it)?.isShortWalkForLabels()==true}
            // A long bar carries both times inside; only a short bar needs chips. Outside chips touch
            // the bar: the departure chip ends exactly at its top edge, the arrival chip starts at its bottom.
            val inside=timelineBarHoldsClocks(h,fontScale)
            if(inside || outsideStart) add(TimelineClockLabel("ride-start-$index",index,segment.startTime,
                if(inside) y+2f else y-height,insideVehicle=inside,attachedWalkIndex=walkBefore,
                placement=if(inside) TimelineClockPlacement.INSIDE else TimelineClockPlacement.ABOVE_BAR))
            if(inside || outsideEnd) add(TimelineClockLabel("ride-end-$index",index,segment.endTime,
                if(inside) end-height else end,insideVehicle=inside,attachedWalkIndex=walkAfter,
                placement=if(inside) TimelineClockPlacement.INSIDE else TimelineClockPlacement.BELOW_BAR))
        }
    }
    fun roomForWalking(top:Float)=vehicles.none {top<it.topDp+height+2f && top+height+2f>it.topDp}
    val walking=buildList {
        val first=trip.segments.first()
        if(first.isWalk && !first.isShortWalkForLabels()) {
            val top=offset(first.startTime)-height
            if(roomForWalking(top)) add(TimelineClockLabel("walk-start",0,first.startTime,top,walking=true,
                placement=TimelineClockPlacement.WALK_START))
        }
        val last=trip.segments.last()
        if(last.isWalk && !last.isShortWalkForLabels()) {
            val top=offset(last.endTime)+2f
            if(roomForWalking(top)) add(TimelineClockLabel("walk-end",trip.segments.lastIndex,last.endTime,top,walking=true,
                placement=TimelineClockPlacement.WALK_END))
        }
    }
    return vehicles+walking
}

/** Vertical extent of one walk's footprint track relative to the trip start, in dp. */
data class TimelineWalkTrack(val segmentIndex:Int,val topDp:Float,val bottomDp:Float)

/**
 * Footprint tracks for the chart. A track covers exactly the walk's own time on the time
 * axis, so a two-minute walk is drawn a seventh as long as a fourteen-minute one. It is no longer
 * stretched to the centre of a time chip, across the waiting time of a transfer or by a fixed
 * approach in front of a chip: with the fitted time axis those fixed lengths made every walk look
 * alike. Steps that pass under a chip are drawn across it in the accent colour (build 74).
 * [fringeDp] is half a footprint: the outermost glyph may extend that far beyond a free end; an end
 * that meets a vehicle bar stops exactly at the bar. [approachDp] is kept for callers and unused.
 */
@Suppress("UNUSED_PARAMETER")
fun timelineWalkTracks(trip:Trip,minuteDp:Float,fontScale:Float,labels:List<TimelineClockLabel>,
    fringeDp:Float,approachDp:Float):List<TimelineWalkTrack> {
    fun offset(time:Long)=((time-trip.startTime)/60_000.0*minuteDp).toFloat()
    return trip.segments.mapIndexedNotNull {index,segment ->
        if(!segment.isWalk) return@mapIndexedNotNull null
        val top=offset(segment.startTime);val bottom=offset(segment.endTime)
        val vehicleAbove=trip.segments.getOrNull(index-1)?.takeIf {!it.isWalk}
        val vehicleBelow=trip.segments.getOrNull(index+1)?.takeIf {!it.isWalk}
        val topAtBar=vehicleAbove!=null && segment.startTime<=vehicleAbove.endTime
        val bottomAtBar=vehicleBelow!=null && segment.endTime>=vehicleBelow.startTime
        TimelineWalkTrack(index,if(topAtBar) top else top-fringeDp,if(bottomAtBar) bottom else bottom+fringeDp)
    }
}

/** How far a column's clocks and footprints reach above the trip start (dp, never negative).
 * The snapping anchor keeps exactly this much room below the chart header. */
fun timelineColumnLead(trip:Trip,minuteDp:Float,fontScale:Float,fringeDp:Float,approachDp:Float):Float {
    val labels=timelineClockLabels(trip,minuteDp,fontScale)
    val tracks=timelineWalkTracks(trip,minuteDp,fontScale,labels,fringeDp,approachDp)
    val highest=minOf(labels.minOfOrNull {it.topDp} ?: 0f,tracks.minOfOrNull {it.topDp} ?: 0f)
    return (-highest).coerceAtLeast(0f)
}
