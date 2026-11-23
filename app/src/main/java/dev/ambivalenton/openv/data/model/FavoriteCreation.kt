// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import kotlin.math.abs
import kotlin.math.min

enum class FavoriteCreationStage { LOCATION, ICON, NAME }
fun favoriteCreationStage(location:Location?,icon:FavoriteIcon?)=when {
    location?.routable!=true->FavoriteCreationStage.LOCATION
    icon==null->FavoriteCreationStage.ICON
    else->FavoriteCreationStage.NAME
}
fun favoriteNameAfterIconChoice(current:String,wasSuggested:Boolean,suggestion:String)=
    if(wasSuggested || current.isBlank()) suggestion else current

/** M02: the name a freshly chosen place suggests (the place's own name, at most 80 characters). */
fun suggestedFavoriteName(place: Location): String = place.name.trim().take(80)

/**
 * A short, friendly name for the badge: "Herne, Zeche Julia" → "Zeche Julia",
 * "Bochum, Universitätsstr. 150" → "Universitätsstr. 150". The city prefix goes only when something
 * remains; at most 32 characters.
 */
fun smartFavoriteName(place: Location): String {
    val name = place.name.trim()
    val city = place.city?.trim().orEmpty()
    // Only a city before a comma goes ("Bochum Hbf" stays as it is); a stop without a known city loses
    // a short first part before its only comma ("Herne, Zeche Julia"), an address keeps its name.
    val withoutCity = when {
        city.isNotEmpty() && name.startsWith("$city,", ignoreCase = true) -> name.substring(city.length + 1)
        city.isEmpty() && place.kind == LocationKind.STOP && name.count { it == ',' } == 1 && name.indexOf(',') in 2..24 -> name.substringAfter(',')
        else -> name
    }.trim().ifEmpty { name }
    return withoutCity.take(32).trim()
}

/**
 * The symbol a place's name suggests. Strong words (Hbf, Flughafen, Universität, Klinik …)
 * count everywhere; softer ones (Park, See, Café …) only for places that are not a street address
 * or a stop, so "Gartenstraße 4" stays a place. Stops fall back to "Haltestelle", other places to "Ort".
 */
fun guessFavoriteIcon(place: Location): FavoriteIcon {
    val text = (place.name + " " + place.favoriteLabel.orEmpty()).lowercase()
    // Whole words only: "Gelsenkirchen" is no church and "Bahnhofstraße" no station.
    val all = text.split(Regex("[^a-z0-9äöüß]+")).filter { it.isNotBlank() }
    val streetWord = Regex("""(str|straße|strasse|weg|allee|platz|gasse|ring|ufer)$""")
    val street = place.kind != LocationKind.STOP && all.any { streetWord.containsMatchIn(it) && it.length > 4 }
    val words = if (street) all.filterNot { streetWord.containsMatchIn(it) && it.length > 4 } else all
    strongIcon(words)?.let { return it }
    if (place.kind != LocationKind.STOP && !street) {
        fun ends(vararg w: String) = words.any { t -> w.any { t.endsWith(it) } }
        fun word(vararg w: String) = words.any { t -> t in w }
        when {
            ends("park") -> return FavoriteIcon.PARK
            word("see", "strand", "badesee") -> return FavoriteIcon.LAKE
            word("rewe", "edeka", "aldi", "lidl", "kaufland", "netto", "penny") || ends("supermarkt", "einkaufszentrum") -> return FavoriteIcon.SUPERMARKET
            ends("fitness", "sporthalle", "turnhalle", "sportplatz", "gym") -> return FavoriteIcon.FITNESS_CENTER
            word("café", "cafe") || ends("kaffee", "café", "cafe") -> return FavoriteIcon.COFFEE
            ends("bäckerei") -> return FavoriteIcon.BAKERY
            ends("restaurant", "pizzeria", "trattoria") -> return FavoriteIcon.RESTAURANT
        }
    }
    return if (place.kind == LocationKind.STOP) FavoriteIcon.STOP else FavoriteIcon.PLACE
}

private fun strongIcon(words: List<String>): FavoriteIcon? {
    fun word(vararg w: String) = words.any { t -> t in w }
    fun ends(vararg w: String) = words.any { t -> w.any { t.endsWith(it) } }
    fun starts(vararg w: String) = words.any { t -> w.any { t.startsWith(it) } }
    return when {
        starts("flughafen", "airport") -> FavoriteIcon.FLIGHT
        word("hbf", "bf") || ends("bahnhof") -> FavoriteIcon.TRAIN
        word("uni") || starts("universität", "hochschul", "campus") || ends("schule", "gymnasium", "kolleg") -> FavoriteIcon.SCHOOL
        starts("klinik", "krankenhaus", "hospital") || ends("klinik", "klinikum", "krankenhaus", "spital", "hospital") -> FavoriteIcon.LOCAL_HOSPITAL
        ends("stadion", "arena") -> FavoriteIcon.STADIUM
        ends("hallenbad", "freibad", "schwimmbad", "spaßbad", "erlebnisbad") -> FavoriteIcon.SWIMMING
        word("kino") || ends("kino") || starts("cinestar", "cinemaxx", "filmpalast") -> FavoriteIcon.CINEMA
        ends("theater", "oper", "opernhaus", "konzerthaus", "philharmonie") -> FavoriteIcon.THEATER
        ends("bibliothek", "bücherei") -> FavoriteIcon.LIBRARY
        ends("rathaus", "bürgeramt") -> FavoriteIcon.TOWN_HALL
        word("dom") || ends("kirche", "moschee", "synagoge", "kapelle", "münster") -> FavoriteIcon.WORSHIP
        word("kita") || ends("kindergarten", "kindertagesstätte") -> FavoriteIcon.KINDERGARTEN
        ends("apotheke") -> FavoriteIcon.PHARMACY
        starts("hotel", "hostel", "jugendherberge") || ends("hotel") -> FavoriteIcon.HOTEL
        else -> null
    }
}

/** Symbols that name the place by themselves ("Arbeit", "Mama"); others keep the place's name. */
private val namingIcons = setOf(FavoriteIcon.HOME, FavoriteIcon.WORK, FavoriteIcon.SCHOOL, FavoriteIcon.FRIENDS, FavoriteIcon.PARENTS,
    FavoriteIcon.PARTNER, FavoriteIcon.MOTHER, FavoriteIcon.FATHER, FavoriteIcon.GRANDPARENTS, FavoriteIcon.SIBLINGS,
    FavoriteIcon.FITNESS_CENTER, FavoriteIcon.CLUB, FavoriteIcon.DOCTOR, FavoriteIcon.KINDERGARTEN, FavoriteIcon.SHOPPING_BAG)

/** The suggested name after choosing [choice]: its own title for a role, else the place's short name. */
fun favoriteNameFor(choice: FavoriteIcon, place: Location?): String =
    if (choice in namingIcons || place?.routable != true) choice.title else smartFavoriteName(place)

/** The symbols "Was ist das für ein Ort?" offers first, in this order. */
val favoriteIntentIcons = listOf(FavoriteIcon.HOME, FavoriteIcon.WORK, FavoriteIcon.SCHOOL, FavoriteIcon.FRIENDS,
    FavoriteIcon.PARENTS, FavoriteIcon.PARTNER, FavoriteIcon.FITNESS_CENTER, FavoriteIcon.SHOPPING_BAG,
    FavoriteIcon.TRAIN, FavoriteIcon.STOP, FavoriteIcon.FAVORITE)

/**
 * The colour that stands apart most from the favourites' colours (largest smallest hue
 * distance; unset colours do not count). Null without a palette.
 */
fun suggestFavoriteAccent(existing: List<Favorite>, palette: List<Long>): Long? {
    if (palette.isEmpty()) return null
    val used = existing.mapNotNull { it.accentSeed }.map(::hueOf)
    if (used.isEmpty()) return palette.first()
    return palette.maxBy { candidate ->
        val hue = hueOf(candidate)
        used.minOf { u -> min(abs(hue - u), 360f - abs(hue - u)) }
    }
}

private fun hueOf(argb: Long): Float {
    val r = (argb shr 16 and 255) / 255f; val g = (argb shr 8 and 255) / 255f; val b = (argb and 255) / 255f
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
    if (d < 1e-4f) return 0f
    val h = when (max) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
    return (h + 360f) % 360f
}

/**
 * Build 101: a favourite while it is being created, in one place for the sheet and the first-launch setup.
 * Every step keeps what the person chose themselves: a symbol tapped first ("Arbeit") stays and names
 * the favourite, a typed name is never replaced by a suggestion, and changing the place only updates
 * what is still a suggestion.
 */
@kotlinx.serialization.Serializable
data class FavoriteDraft(
    val location: Location? = null,
    val icon: FavoriteIcon? = null,
    val label: String = "",
    val nameIsSuggested: Boolean = true,
    val iconChosen: Boolean = false,
    val accentSeed: Long? = null,
    val shape: IconStyle = IconStyle.SQUIRCLE,
) {
    /** A place is enough: name and symbol are suggested at once. */
    val ready: Boolean get() = location?.routable == true && label.isNotBlank()

    fun withPlace(place: Location?): FavoriteDraft {
        if (place?.routable != true) return copy(location = place)
        val suggested = nameIsSuggested || label.isBlank()
        val symbol = if (iconChosen && icon != null) icon else guessFavoriteIcon(place)
        val name = when {
            !suggested -> label
            iconChosen && icon != null -> favoriteNameFor(icon, place)
            else -> smartFavoriteName(place)
        }
        return copy(location = place, icon = symbol, label = name, nameIsSuggested = suggested)
    }

    /** [choice] null takes a symbol tapped before back; the place suggests one again. */
    fun withIcon(choice: FavoriteIcon?): FavoriteDraft {
        val suggested = nameIsSuggested || label.isBlank()
        if (choice == null) {
            val place = location?.takeIf { it.routable }
            return copy(icon = place?.let(::guessFavoriteIcon), iconChosen = false,
                label = if (suggested) place?.let(::smartFavoriteName).orEmpty() else label, nameIsSuggested = suggested)
        }
        return copy(icon = choice, iconChosen = true, label = if (suggested) favoriteNameFor(choice, location) else label,
            nameIsSuggested = suggested)
    }

    fun withName(text: String): FavoriteDraft = copy(label = text.take(80), nameIsSuggested = false)

    fun favorite(id: String = newId()): Favorite = Favorite(id = id, label = label.trim().take(80),
        location = requireNotNull(location), icon = icon ?: FavoriteIcon.PLACE, accentSeed = accentSeed, shape = shape)
}

/** The roles a place can be asked for first ("Wo ist Arbeit?"); ones already used go to the end. */
fun favoriteRoleChoices(existing: List<Favorite>): List<FavoriteIcon> {
    val roles = listOf(FavoriteIcon.HOME, FavoriteIcon.WORK, FavoriteIcon.SCHOOL, FavoriteIcon.FRIENDS,
        FavoriteIcon.PARENTS, FavoriteIcon.PARTNER, FavoriteIcon.FITNESS_CENTER, FavoriteIcon.SHOPPING_BAG)
    val used = existing.map { it.icon }.toSet()
    return roles.filter { it !in used } + roles.filter { it in used }
}

/** A suggestion of the add-favourite sheet with where it comes from. */
data class FavoritePlaceSuggestion(val place: Location, val recent: Boolean)

/**
 * Places worth saving: the most searched and most recent destinations, then the current
 * board stop and the board's places – never one that already is a favourite. At most [limit].
 */
fun StoredState.favoritePlaceSuggestions(limit: Int = 6): List<FavoritePlaceSuggestion> {
    val taken = favorites.map { it.location }
    fun fresh(place: Location) = place.routable && !place.isDevicePosition && taken.none { sameLocation(it, place) }
    val recent = history.sortedWith(compareByDescending<SearchHistory> { it.count }.thenByDescending { it.searchedAt })
        .map { it.query.destination }.filter(::fresh).map { FavoritePlaceSuggestion(it, true) }
    val nearby = (listOfNotNull(departureBoard.stop) + boardStops).filter(::fresh).map { FavoritePlaceSuggestion(it, false) }
    val result = mutableListOf<FavoritePlaceSuggestion>()
    for (s in recent + nearby) {
        if (result.size >= limit) break
        if (result.none { sameLocation(it.place, s.place) }) result += s
    }
    return result
}

/**
 * The favourite a new one most likely connects with: "Zuhause" first, otherwise the
 * nearest one. Null when there is none or the line already exists or is not allowed.
 */
fun StoredState.connectionPartnerFor(newId: String): Favorite? {
    val added = favorites.firstOrNull { it.id == newId } ?: return null
    val others = favorites.filter { it.id != newId && network.node(it.id)?.point != null && edgeRejection(newId, it.id) == null }
    if (network.node(newId)?.point == null) return null
    return others.firstOrNull { it.icon == FavoriteIcon.HOME }
        ?: others.minByOrNull { distanceMeters(it.location, added.location) ?: Double.MAX_VALUE }
}
