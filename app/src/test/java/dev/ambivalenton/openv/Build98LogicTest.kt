// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv

import dev.ambivalenton.openv.data.model.*
import org.junit.Assert.*
import org.junit.Test

/** The defaults of the new add-favourite flow and the step after it. */
class Build98LogicTest {
    private fun stop(name: String, id: String, lat: Double = 51.5, lon: Double = 7.2, city: String? = null) =
        Location(name, id, lat, lon, "VRR", LocationKind.STOP, city = city)
    private fun address(name: String, city: String? = "Herne") =
        Location(name, latitude = 51.53, longitude = 7.22, kind = LocationKind.ADDRESS, city = city)

    @Test fun namesAreShortButNeverMutilated() {
        assertEquals("Zeche Julia", smartFavoriteName(stop("Herne, Zeche Julia", "j")))
        assertEquals("Zeche Julia", smartFavoriteName(stop("Herne, Zeche Julia", "j", city = "Herne")))
        assertEquals("Bochum Hbf", smartFavoriteName(stop("Bochum Hbf", "b", city = "Bochum")))
        assertEquals("Bahnhofstr. 3, 44623 Herne", smartFavoriteName(address("Bahnhofstr. 3, 44623 Herne", city = null)))
        assertTrue(smartFavoriteName(address("Eine sehr lange Adresse mit vielen Wörtern und Zusätzen 12")).length <= 32)
    }

    @Test fun symbolsFollowThePlaceWithoutGuessingFromStreetNames() {
        assertEquals(FavoriteIcon.TRAIN, guessFavoriteIcon(stop("Herne Bf", "h")))
        assertEquals(FavoriteIcon.TRAIN, guessFavoriteIcon(stop("Dortmund Hbf", "d")))
        assertEquals(FavoriteIcon.SCHOOL, guessFavoriteIcon(stop("Bochum Ruhr-Universität", "r")))
        assertEquals(FavoriteIcon.FLIGHT, guessFavoriteIcon(stop("Düsseldorf Flughafen", "f")))
        assertEquals(FavoriteIcon.STOP, guessFavoriteIcon(stop("Herne Zeche Julia", "j")))
        assertEquals("Bahnhofstraße is no station", FavoriteIcon.PLACE, guessFavoriteIcon(address("Bahnhofstraße 3")))
        assertEquals("Gartenstraße is no park", FavoriteIcon.PLACE, guessFavoriteIcon(address("Gartenstraße 4")))
        assertEquals(FavoriteIcon.LOCAL_HOSPITAL, guessFavoriteIcon(address("St. Anna Hospital")))
        assertEquals(FavoriteIcon.SUPERMARKET, guessFavoriteIcon(address("REWE Markt Herne")))
        // Found by rendering the flow: a city that contains "kirche" is no church.
        assertEquals(FavoriteIcon.STOP, guessFavoriteIcon(stop("Gelsenkirchen, Zoom Erlebniswelt", "z")))
        assertEquals(FavoriteIcon.WORSHIP, guessFavoriteIcon(address("Pauluskirche Bochum", city = null)))
        assertEquals(FavoriteIcon.SCHOOL, guessFavoriteIcon(stop("Herne Gesamtschule", "g")))
    }

    @Test fun aRoleNamesTheFavouriteOtherwiseThePlaceDoes() {
        val julia = stop("Herne, Zeche Julia", "j")
        assertEquals(FavoriteIcon.WORK.title, favoriteNameFor(FavoriteIcon.WORK, julia))
        assertEquals(FavoriteIcon.HOME.title, favoriteNameFor(FavoriteIcon.HOME, null))
        assertEquals("Zeche Julia", favoriteNameFor(FavoriteIcon.TRAIN, julia))
        assertEquals(FavoriteIcon.TRAIN.title, favoriteNameFor(FavoriteIcon.TRAIN, null))
    }

    @Test fun theSuggestedColourStandsApartFromTheOthers() {
        val red = 0xFFD32F2FL; val green = 0xFF388E3CL; val blue = 0xFF1976D2L; val orange = 0xFFF57C00L
        val others = listOf(Favorite(label = "A", location = stop("A", "a"), accentSeed = red),
            Favorite(label = "B", location = stop("B", "b"), accentSeed = orange))
        assertEquals("farthest from red and orange", blue, suggestFavoriteAccent(others, listOf(red, orange, green, blue)))
        assertEquals(red, suggestFavoriteAccent(emptyList(), listOf(red, green)))
        assertNull(suggestFavoriteAccent(others, emptyList()))
        // Unset colours of other favourites do not count.
        assertEquals(red, suggestFavoriteAccent(listOf(Favorite(label = "C", location = stop("C", "c"))), listOf(red, green)))
    }

    @Test fun suggestionsAreRecentDestinationsAndBoardStopsButNeverFavourites() {
        val home = stop("Herne Bf", "h"); val uni = stop("Bochum Ruhr-Universität", "r", 51.44, 7.26)
        val zoo = stop("Gelsenkirchen Zoom", "z", 51.54, 7.06); val board = stop("Herne Archäologiemuseum", "m", 51.536, 7.22)
        fun searched(place: Location, count: Long, at: Long) = SearchHistory(JourneyQuery(home, place, at), at, count)
        val store = StoredState(favorites = listOf(Favorite("h", "Zuhause", home)),
            history = listOf(searched(zoo, 1, 2_000), searched(uni, 5, 1_000), searched(home, 9, 3_000)),
            boardStops = listOf(board, home))
        val suggestions = store.favoritePlaceSuggestions()
        assertEquals(listOf("Bochum Ruhr-Universität", "Gelsenkirchen Zoom", "Herne Archäologiemuseum"), suggestions.map { it.place.name })
        assertEquals(listOf(true, true, false), suggestions.map { it.recent })
        assertEquals(2, store.favoritePlaceSuggestions(limit = 2).size)
    }

    @Test fun theFirstLineGoesHomeOrToTheNearestFavourite() {
        val home = Favorite("h", "Zuhause", stop("Herne Bf", "h"), icon = FavoriteIcon.HOME)
        val near = Favorite("n", "Nachbar", stop("Herne Mitte", "n", 51.535, 7.21))
        val far = Favorite("f", "Weit", stop("Köln Hbf", "k", 50.94, 6.96))
        val added = Favorite("a", "Arbeit", stop("Herne Wanne", "w", 51.53, 7.15))
        fun network(vararg ids: String, edges: List<NetworkEdge> = emptyList()) =
            TransitNetwork(ids.mapIndexed { i, id -> NetworkNode(id, GridPoint(i, 0)) }, edges)
        val withHome = StoredState(favorites = listOf(far, near, home, added), network = network("f", "n", "h", "a"))
        assertEquals("h", withHome.connectionPartnerFor("a")?.id)
        val withoutHome = StoredState(favorites = listOf(far, near, added), network = network("f", "n", "a"))
        assertEquals("n", withoutHome.connectionPartnerFor("a")?.id)
        // Alone, or already connected: nothing to suggest.
        assertNull(StoredState(favorites = listOf(added), network = network("a")).connectionPartnerFor("a"))
        val connected = StoredState(favorites = listOf(home, added), network = network("h", "a", edges = listOf(NetworkEdge("e", "h", "a"))))
        assertNull(connected.connectionPartnerFor("a"))
    }
}
