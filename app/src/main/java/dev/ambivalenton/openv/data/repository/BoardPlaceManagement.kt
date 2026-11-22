// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.localization.tr

import dev.ambivalenton.openv.data.model.*
import java.text.Collator
import java.util.Locale

/** Haltestellen-Liste: the places of all favourites belong to it automatically (build 67, as before
 * build 66), followed by the stops added by hand. A removed favourite place is remembered in
 * [StoredState.boardExcludedKeys], so it stays removed. The network plan never influences it. */
fun boardPlaces(state: StoredState): List<Location> =
    (state.favorites.map { it.location }.filter { it.routable } + state.boardStops)
        .distinctBy(::boardLocationKey)
        .filterNot { boardLocationKey(it) in state.boardExcludedKeys }

fun activeBoardSelection(state: StoredState, includeRotation:Boolean=true): Location? =
    (state.rotatingBoardStop?.takeIf {includeRotation && state.settings.board.rotationSeconds>0}
        ?: state.settings.board.selectedStop)?.takeIf {
    val key = boardLocationKey(it)
    key !in state.boardExcludedKeys && key !in state.boardDisabledKeys && boardPlaces(state).any {place->boardLocationKey(place)==key}
}

/**
 * M10: whether refreshing the board needs the device location: only the automatic choice from the
 * list without a fixed stop and without a manual start does.
 */
fun boardRefreshUsesLocation(state: StoredState): Boolean =
    activeBoardSelection(state, includeRotation = false) == null &&
        !(state.settings.originMode == OriginMode.MANUAL && state.settings.manualOrigin?.routable == true)

/** M10: the refresh action named after what it does in the current mode. */
fun boardRefreshLabel(state: StoredState): String =
    if (boardRefreshUsesLocation(state)) tr("Standort und Abfahrten aktualisieren") else tr("Abfahrten aktualisieren")

fun sortedBoardPlaces(state: StoredState, gps: Location?): List<Location> {
    val names = Collator.getInstance(Locale.getDefault())
    fun title(place: Location) = place.name
    return boardPlaces(state).sortedWith(Comparator { a, b ->
        val active = (boardLocationKey(a) in state.boardDisabledKeys).compareTo(boardLocationKey(b) in state.boardDisabledKeys)
        val distance = if (state.settings.board.listSort == BoardStopSort.DISTANCE && gps?.hasCoordinates == true)
            (distanceMeters(gps, a) ?: Double.MAX_VALUE).compareTo(distanceMeters(gps, b) ?: Double.MAX_VALUE)
        else 0
        when {
            active != 0 -> active
            distance != 0 -> distance
            else -> names.compare(title(a), title(b)).takeIf { it != 0 }
                ?: boardLocationKey(a).compareTo(boardLocationKey(b))
        }
    })
}

/** A new or moved favourite place joins the Haltestellen-Liste again, even if that place had been
 * removed from it before; unrelated edits never bring a removed place back. */
internal fun StoredState.withFavorite(favorite: Favorite): StoredState {
    val previous = favorites.firstOrNull { it.id == favorite.id }
    val key = boardLocationKey(favorite.location)
    val newPlace = previous == null || boardLocationKey(previous.location) != key
    return copy(favorites=if(previous==null)favorites+favorite else favorites.map {if(it.id==favorite.id)favorite else it},
        boardExcludedKeys=if(newPlace)boardExcludedKeys-key else boardExcludedKeys)
        .withNetworkNodeFor(favorite.id)
}

/** Removes the favourite with its network node, its lines and a location override pointing at it. */
internal fun StoredState.withoutFavorite(id:String):StoredState {
    favorites.firstOrNull {it.id==id} ?: return this
    return copy(favorites=favorites.filterNot {it.id==id}).withoutNetworkNode(id)
}

const val MAX_BOARD_PLACES=120

/** Copies the former favourite places (and pair endpoints) once into the board list, so
 * nothing disappears when the projection ends. Idempotent via [StoredState.boardStopsDecoupled]. */
fun StoredState.migrateBoardStops():StoredState {
    if(boardStopsDecoupled)return this
    val projected=favorites.flatMap {listOfNotNull(it.location,it.counterpole)}.filter {it.routable}
    val merged=(projected+boardStops).distinctBy(::boardLocationKey).filterNot {boardLocationKey(it) in boardExcludedKeys}.take(MAX_BOARD_PLACES)
    return copy(boardStops=merged,boardExcludedKeys=emptySet(),boardStopsDecoupled=true)
}

internal fun StoredState.withoutBoardPlace(place: Location): StoredState {
    val key = boardLocationKey(place)
    return copy(
        boardStops = boardStops.filterNot { boardLocationKey(it) == key },
        // A favourite's place is a member by itself: removing it must be remembered.
        boardExcludedKeys = if (favorites.any { boardLocationKey(it.location) == key }) boardExcludedKeys + key else boardExcludedKeys - key,
        boardDisabledKeys = boardDisabledKeys - key,
        settings = settings.copy(board = settings.board.copy(selectedStop = settings.board.selectedStop?.takeUnless { boardLocationKey(it) == key })),
    )
}

internal fun StoredState.withBoardPlaceEnabled(place: Location, enabled: Boolean): StoredState {
    val key = boardLocationKey(place)
    if (boardPlaces(this).none { boardLocationKey(it) == key }) return this
    return copy(
        boardDisabledKeys = if (enabled) boardDisabledKeys - key else boardDisabledKeys + key,
        // Keep a temporarily hidden manual selection as a list member, but never route to it.
    )
}

/** A removed list member must disappear immediately, even while refresh is paused or in flight. */
internal fun validDepartureBoard(state:StoredState):DepartureBoardState =
    state.departureBoard.takeIf {boardCandidates(state).isNotEmpty()&&it.configurationKey==boardConfigurationKey(state)}?:DepartureBoardState()

internal fun validBoardState(state:StoredState):StoredState {
    val members=boardPlaces(state).map(::boardLocationKey).toSet()
    val selected=state.settings.board.selectedStop?.takeIf {boardLocationKey(it) in members}
    val rotating=state.rotatingBoardStop?.takeIf {state.settings.board.rotationSeconds>0 &&
        boardLocationKey(it) in members && boardLocationKey(it) !in state.boardDisabledKeys}
    val clean=if(selected==state.settings.board.selectedStop && rotating==state.rotatingBoardStop)state else
        state.copy(rotatingBoardStop=rotating,settings=state.settings.copy(board=state.settings.board.copy(selectedStop=selected)))
    val board=validDepartureBoard(clean)
    return if(board==clean.departureBoard)clean else clean.copy(departureBoard=board)
}

/** Rotation uses only enabled list members and leaves endpoint choice explicit. */
fun nextRotatingBoardStop(state:StoredState):Location? {
    val places=sortedBoardPlaces(state,state.routingPosition()).filter {boardLocationKey(it) !in state.boardDisabledKeys}
    if(places.isEmpty())return null
    val current=(activeBoardSelection(state) ?: state.departureBoard.stop
        ?: state.routingPosition()?.let {position->places.minByOrNull {distanceMeters(position,it) ?: Double.MAX_VALUE}})?.let(::boardLocationKey)
    val index=places.indexOfFirst {boardLocationKey(it)==current}
    return places[(index+1).mod(places.size)]
}
