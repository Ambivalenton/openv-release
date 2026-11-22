// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

fun JourneyQuery.withWalking(settings:AppSettings)=copy(walkingSpeed=settings.walkingSpeed,ignoreWalking=settings.ignoreWalking)

/** Walking can be explicitly disregarded; vehicle chronology is always retained. */
fun Trip.hasUsableTransfers(query:JourneyQuery):Boolean {
    val rides=segments.withIndex().filterNot {it.value.isWalk}
    return rides.zipWithNext().all {(before,after)->
        val walk=if(query.ignoreWalking)0L else segments.subList(before.index+1,after.index)
            .filter {it.isWalk}.sumOf {walkingDurationMillis(it,query.walkingSpeed)}
        after.value.effectiveDeparture()>=before.value.effectiveArrival()+walk
    }
}

/** Recombine only explicitly supplied services at the SAME interchange; no fabricated timetable. */
fun ignoredWalkingConnections(trips:List<Trip>):List<Trip> {
    val result=linkedMapOf<String,Trip>()
    trips.take(24).forEach {left->
        val leftRides=left.segments.filterNot {it.isWalk}
        leftRides.dropLast(1).forEachIndexed leftCut@ {cut,arriving->
            trips.take(24).forEach {right->
                val rightRides=right.segments.filterNot {it.isWalk}
                rightRides.forEachIndexed rightJoin@ {join,departing->
                    val gap=departing.effectiveDeparture()-arriving.effectiveArrival()
                    if(gap !in 0..30*60_000L || !sameLocation(arriving.destination,departing.origin))return@rightJoin
                    if(!sameLocation(leftRides.first().origin,rightRides.first().origin) ||
                        !sameLocation(leftRides.last().destination,rightRides.last().destination))return@rightJoin
                    val parts=leftRides.take(cut+1)+rightRides.drop(join)
                    if(parts.size>8 || parts.map {it.id}.distinct().size!=parts.size ||
                        parts.zipWithNext().any {(a,b)->b.effectiveDeparture()<a.effectiveArrival()})return@rightJoin
                    val id="walk-free:"+parts.joinToString("|"){it.id}
                    if(result.size<64)result[id]=left.copy(id=id,segments=parts,refreshToken=null,
                        fetchedAt=minOf(left.fetchedAt,right.fetchedAt))
                }
            }
        }
    }
    return result.values.toList()
}

fun Trip.queryStart(ignoreWalking:Boolean)=if(ignoreWalking)segments.firstOrNull {!it.isWalk}?.startTime ?: startTime else startTime
fun Trip.queryEnd(ignoreWalking:Boolean)=if(ignoreWalking)segments.lastOrNull {!it.isWalk}?.endTime ?: endTime else endTime
