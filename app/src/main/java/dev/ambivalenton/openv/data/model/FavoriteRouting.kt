// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import kotlinx.serialization.Serializable

/** Separate from LineType: existing palettes, stored line types and provider mappings stay stable. */
@Serializable enum class FavoriteTransport(val lineType:LineType,private val key:String) {
    SUBURBAN(LineType.SUBURBAN,"S-Bahn"), SUBWAY(LineType.SUBWAY,"U-Bahn"),
    TRAM(LineType.TRAM,"Tram"), BUS(LineType.BUS,"Bus"),
    REGIONAL_EXPRESS(LineType.REGIONAL,"Regionalexpress · RE / RRX / IRE"),
    REGIONAL_TRAIN(LineType.REGIONAL,"Regionalbahn · RB"),
    OTHER_REGIONAL(LineType.REGIONAL,"Weitere Regionalzüge"),
    LONG_DISTANCE(LineType.ICE,"Fernverkehr · ICE / IC / EC"),
    FERRY(LineType.FERRY,"Fähre"), OTHER(LineType.OTHER,"Sonstige");
    val title get()=tr(key)
}

fun favoriteTransport(leg:TripSegment):FavoriteTransport {
    if(leg.isLongDistance())return FavoriteTransport.LONG_DISTANCE
    if(leg.lineType==LineType.REGIONAL) {
        val name=leg.lineName.trim().uppercase(java.util.Locale.ROOT)
        return when {
            Regex("^(IRE|RE|RRX)(?:\\s|\\d|$)").containsMatchIn(name)->FavoriteTransport.REGIONAL_EXPRESS
            Regex("^RB(?:\\s|\\d|$)").containsMatchIn(name)->FavoriteTransport.REGIONAL_TRAIN
            else->FavoriteTransport.OTHER_REGIONAL
        }
    }
    return FavoriteTransport.entries.firstOrNull {it.lineType==leg.lineType} ?: FavoriteTransport.OTHER
}

fun Trip.matchesTransportFilter(filter:Set<FavoriteTransport>?):Boolean =
    filter==null || segments.filterNot {it.isWalk}.let {rides->rides.isNotEmpty() && rides.all {favoriteTransport(it) in filter}}

data class FavoriteDirection(val origin:Location,val destination:Location)

/** A fixed pair, never a route from the user's arbitrary position. Exact ties keep the saved direction. */
fun Favorite.directionFrom(position:Location?):FavoriteDirection {
    val other=counterpole ?: return FavoriteDirection(position ?: location,location)
    val toFavorite=position?.let {distanceMeters(it,location)}
    val toOther=position?.let {distanceMeters(it,other)}
    return if(toFavorite!=null && toOther!=null && toFavorite<toOther)
        FavoriteDirection(location,other) else FavoriteDirection(other,location)
}

fun Favorite.journeyQuery(position:Location?,settings:AppSettings,time:Long,
    mode:TimeMode=TimeMode.DEPARTURE):JourneyQuery {
    val direction=directionFrom(position)
    val products=transportFilter?.map {it.lineType}?.toSet() ?: settings.products
    return JourneyQuery(direction.origin,direction.destination,time,mode,products,
        walkingSpeed=settings.walkingSpeed,ignoreWalking=settings.ignoreWalking,transportFilter=transportFilter)
}

fun Favorite.forDestination(destination:Location?):Favorite =
    if(counterpole!=null && destination!=null && sameLocation(destination,counterpole))
        copy(label=originLabel?:counterpole.name,location=counterpole,counterpole=location,
            accentSeed=originAccentSeed,originAccentSeed=accentSeed,originLabel=label) else this

fun Favorite.hasValidRoutingOptions()= (counterpole==null || counterpole.routable && !sameLocation(location,counterpole)) &&
    (transportFilter==null || transportFilter.isNotEmpty())
