// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Shared by search, favourites, widgets, overview, waypoints and the assistant. */
class RoutingPreferencesRepository(private val base:TransitRepository,private val settings:AppSettings,
    private val clock:()->Long=System::currentTimeMillis):TransitRepository {
    override val name get()=base.name
    override val supportsRouteFocus get()=base.supportsRouteFocus
    override suspend fun locations(query:String)=base.locations(query)
    override suspend fun addresses(query:String)=base.addresses(query)
    override suspend fun nearby(latitude:Double,longitude:Double,radiusMeters:Int)=base.nearby(latitude,longitude,radiusMeters)
    override suspend fun resolveStop(place:Location)=base.resolveStop(place)
    override suspend fun departures(stop:Location)=base.departures(stop)
    override suspend fun departures(stop:Location,limit:Int)=base.departures(stop,limit)
    // A refreshed journey keeps dropped walks at the entered places dropped.
    override suspend fun refresh(trip:Trip)=base.refresh(trip).withWalksOf(trip)
    override fun journeyWindow(query:JourneyQuery,policy:PagingPolicy)=fetchJourneyWindow(this,query.withWalking(settings),policy)
    override suspend fun journeyPage(query:JourneyQuery,cursor:String?):JourneyPage {
        val effective=query.withWalking(settings)
        val preferences=settings.overview.routingPreferences
        val explicit=effective.origin.isExplicitStop() || effective.destination.isExplicitStop()
        val minimalWalking=!effective.ignoreWalking && preferences.effectiveWalking==WalkingPreference.MINIMAL
        // A chosen stop or "So wenig wie möglich laufen" first asks strictly. Only when nothing
        // fitting leaves (or arrives) within two hours of the requested time, the ordinary search answers
        // instead, with alternatives such as a walk to another stop. No hard transfer limit.
        if((explicit || minimalWalking) && effective.waypoints.isEmpty()) {
            val strict=effective.copy(stopOnly=true)
            val page=try {page(strict,cursor,preferences) {trip->strictFits(trip,effective)}}
                catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {null}
            if(page!=null && page.shown.any {strictTripWithinTwoHours(it,effective)}) return page
        }
        return page(effective,cursor,preferences,null)
    }

    /**
     * One provider page with the shared clean-up: walks at the entered places, usable transfers,
     * explicit stops. Leaning preferences ask the provider a second time with their own
     * goal ([routeFocuses]); both answers are weighed together and connections that a comparable,
     * clearly better one makes superfluous are marked hidden ([JourneyRanking.hiddenConnections]).
     */
    private suspend fun page(effective:JourneyQuery,cursor:String?,preferences:OverviewRoutingPreferences,strict:((Trip)->Boolean)?):JourneyPage {
        val focuses=if(cursor==null && effective.waypoints.isEmpty() && base.supportsRouteFocus) routeFocuses(preferences,effective) else emptyList()
        val raw=if(focuses.isEmpty()) base.journeyPage(effective,cursor) else coroutineScope {
            val main=async {base.journeyPage(effective,cursor)}
            // An extra answer is a bonus: its failure never costs the ordinary result.
            val extra=focuses.map {focus->async {
                try {base.journeyPage(effective.copy(routeFocus=focus),null).trips}
                catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {emptyList()}
            }}
            val first=main.await()
            val known=first.trips.mapTo(HashSet()) {historicalIdentity(it)}
            val added=extra.flatMap {it.await()}.filter {known.add(historicalIdentity(it))}
            JourneyPage((first.trips+added).sortedBy {it.startTime},first.nextCursor)
        }
        // A walk from the provider's point near the entered place to that very place is dropped.
        val page=raw.copyTripsWithWalkingHint(effective.walkingSpeed).let {JourneyPage(it.trips.map {trip->
            trip.withoutWalksAtEnteredPlaces(effective.origin,effective.destination)},it.nextCursor)}
        val extras=if(effective.ignoreWalking && effective.waypoints.isEmpty())ignoredWalkingConnections(page.trips) else emptyList()
        // Keep disallowed provider rows as pagination anchors. Window/selectors apply the filter.
        val usable=(page.trips+extras).filter {it.hasUsableTransfers(effective)}
        val kept=if(strict!=null) usable.filter(strict) else usable.preferExplicitStops(effective)
        val comparable=kept.filter {trip->trip.segments.all {it.isWalk || it.lineType in effective.products &&
            (LineType.ICE in effective.products || !it.isLongDistance())} && trip.matchesTransportFilter(effective.transportFilter)}
        return JourneyPage(kept,page.nextCursor,JourneyRanking.hiddenConnections(comparable,effective,preferences,clock()))
    }
}

/**
 * The extra provider goals of leaning preferences – fewer transfers for "Wenig" and
 * "Möglichst wenig", less walking for "Weniger" and "So wenig wie möglich" – at most two.
 */
internal fun routeFocuses(preferences:OverviewRoutingPreferences,query:JourneyQuery):List<RouteFocus> = buildList {
    if(preferences.effectiveTransfers in setOf(TransferPreference.FEWEST,TransferPreference.FEWER)) add(RouteFocus.FEWER_CHANGES)
    if(!query.ignoreWalking && preferences.effectiveWalking in setOf(WalkingPreference.MINIMAL,WalkingPreference.LESS)) add(RouteFocus.LESS_WALKING)
}

/** A strict answer keeps journeys that start and end at the chosen stops by vehicle. */
internal fun strictFits(trip:Trip,query:JourneyQuery):Boolean {
    val rides=trip.segments.count {!it.isWalk}
    if(rides==0 && (query.origin.isExplicitStop() || query.destination.isExplicitStop())) return false
    return trip.keepsExplicitStops(query)
}

/** The two hours after (departure) or before (arrival) the requested time. */
const val STRICT_STOP_WINDOW_MILLIS=2*60*60_000L
internal fun strictTripWithinTwoHours(trip:Trip,query:JourneyQuery):Boolean {
    val rides=trip.segments.filter {!it.isWalk}
    if(rides.isEmpty()) return false
    return if(query.timeMode==TimeMode.ARRIVAL) rides.last().effectiveArrival() in (query.time-STRICT_STOP_WINDOW_MILLIS)..query.time+60_000
        else rides.first().effectiveDeparture() in (query.time-60_000)..(query.time+STRICT_STOP_WINDOW_MILLIS)
}

private fun JourneyPage.copyTripsWithWalkingHint(speed:WalkingSpeed)=JourneyPage(trips.map {trip->
    trip.copy(segments=trip.segments.map {leg->if(leg.isWalk)leg.copy(walkingSpeedHint=speed)else leg})
},nextCursor)
