// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import dev.ambivalenton.openv.data.repository.StateSerializer
import org.junit.Assert.*
import org.junit.Test

/** "In Übersicht anzeigen" keeps up to five favourites in the overview. */
class Build81OverviewPinsTest {
    private val favorites = (1..7).map { Favorite(id = "f$it", label = "Ort $it", location = Location("Ort $it", "s$it", 51.0 + it / 100.0, 7.0, "TEST")) }
    private val state = favorites.fold(StoredState(favorites = favorites)) { s, f -> s.withNetworkNodeFor(f.id) }

    @Test fun upToFivePinsInPinningOrderAndASixthIsNotAdded() {
        var s = state
        (1..6).forEach { s = s.toggleOverviewFavorite("f$it") }
        assertEquals((1..5).map { "f$it" }, s.overviewPins)
        assertTrue(s.overviewPinsFull)
        // Releasing one makes room again; the order of the others is kept.
        s = s.toggleOverviewFavorite("f2").toggleOverviewFavorite("f7")
        assertEquals(listOf("f1", "f3", "f4", "f5", "f7"), s.overviewPins)
    }

    @Test fun pinsSurviveSerialisationAndOldSinglePinsAreRead() {
        val pinned = state.toggleOverviewFavorite("f3").toggleOverviewFavorite("f1")
        val back = StateSerializer.json.decodeFromString(StoredState.serializer(), StateSerializer.json.encodeToString(StoredState.serializer(), pinned))
        assertEquals(listOf("f3", "f1"), back.overviewPins)
        val legacy = StateSerializer.json.decodeFromString(StoredState.serializer(),
            StateSerializer.json.encodeToString(StoredState.serializer(), state.copy(overviewFavoriteId = "f4")))
        assertEquals(listOf("f4"), legacy.overviewPins)
        // The first change writes the list and clears the legacy field.
        val next = legacy.toggleOverviewFavorite("f5")
        assertEquals(listOf("f4", "f5"), next.overviewPins)
        assertNull(next.overviewFavoriteId)
    }

    @Test fun deletedFavouritesDropOutOfThePins() {
        val pinned = state.toggleOverviewFavorite("f1").toggleOverviewFavorite("f2")
        assertEquals(listOf("f2"), pinned.copy(favorites = favorites.drop(1)).overviewPins)
    }
}
