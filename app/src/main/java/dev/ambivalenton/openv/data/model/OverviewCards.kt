// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr
import kotlin.math.ceil
import kotlin.math.roundToInt

data class OverviewCardField(val id:String,val label:String,val value:String)
/** The compact view shares the switches of the sentence view; disabled facts have no hidden slots. */
fun overviewCardFields(favorite:Favorite,trip:Trip,settings:OverviewSettings,weather:ArrivalWeather?=null):List<OverviewCardField> = buildList {
    val first=trip.segments.firstOrNull {!it.isWalk}?:trip.segments.firstOrNull()
    fun field(id:String,label:String,value:String?) {if(!value.isNullOrBlank())add(OverviewCardField(id,tr(label),value))}
    fun minutes(ms:Long)=tr("%1\$s Min.",ceil(ms.coerceAtLeast(0)/60_000.0).toInt())
    if(settings.showDestination)field("destination","Ziel",favorite.label)
    if(settings.showDepartureTime)field("departure","Abfahrt",first?.let {clockTime(it.startTime)})
    if(settings.showArrivalTime)field("arrival","Ankunft",clockTime(trip.endTime))
    if(settings.showOriginStop)field("origin","Start",first?.origin?.name)
    if(settings.showDuration)field("duration","Gesamtdauer",minutes(trip.totalDuration))
    if(settings.showTransfers)field("transfers","Umstiege",trip.transfers.toString())
    if(settings.showWaitTimes) {
        val vehicles=trip.segments.withIndex().filterNot {it.value.isWalk}
        val wait=vehicles.zipWithNext().sumOf {(before,after)->
            val walking=trip.segments.subList(before.index+1,after.index).filter {it.isWalk}.sumOf {(it.endTime-it.startTime).coerceAtLeast(0)}
            (after.value.startTime-before.value.endTime-walking).coerceAtLeast(0)
        }
        field("waiting","Wartezeit",minutes(wait))
    }
    if(settings.showWalks)field("walking","Fußwege",minutes(trip.segments.filter {it.isWalk}.sumOf {(it.endTime-it.startTime).coerceAtLeast(0)}))
    if(settings.showTransport)field("transport","Verkehrsmittel",trip.segments.filterNot {it.isWalk}.map {it.lineType.label}.distinct().joinToString(" · ").ifEmpty {tr("Zu Fuß")} )
    if(settings.showLineNames)field("lines","Linien",trip.segments.filterNot {it.isWalk}.map {it.lineName}.distinct().joinToString(" · "))
    if(settings.showPlatforms)field("platforms","Gleis / Bussteig",trip.segments.filterNot {it.isWalk}.mapNotNull {leg->platformName(leg.departurePlatform,leg.lineType)?.let {"${leg.lineName}: $it"}}.joinToString(" · "))
    if(settings.showWeather&&weather?.covers(trip.endTime)==true)field("weather","Wetter",tr("%1\$s °C · gefühlt %2\$s °C",weather.temperatureC.roundToInt(),weather.apparentTemperatureC.roundToInt()))
}
