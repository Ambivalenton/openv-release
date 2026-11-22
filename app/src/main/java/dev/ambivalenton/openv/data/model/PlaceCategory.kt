// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.data.model

import java.util.Locale

/**
 * What kind of place a search result is, for the symbol in front of it. Presentation only:
 * routing, identities and stored data never depend on it. Each source tells as much as it knows
 * (EFA object type and transport modes, OpenStreetMap key/value, REST type and products); the local
 * stop directory and stored places fall back to the name.
 */
enum class PlaceCategory(val labelKey: String) {
    FAVORITE("Favorit"),
    STATION("Bahnhof"),
    SUBWAY("U-Bahn-Haltestelle"),
    TRAM("Straßenbahnhaltestelle"),
    BUS("Bushaltestelle"),
    FERRY("Fähranleger"),
    STOP("Haltestelle"),
    AIRPORT("Flughafen"),
    ADDRESS("Adresse"),
    STREET("Straße"),
    LOCALITY("Ort oder Stadtteil"),
    FOOD("Restaurant oder Imbiss"),
    CAFE("Café"),
    SHOP("Geschäft"),
    HEALTH("Gesundheit"),
    EDUCATION("Schule oder Hochschule"),
    PARK("Park oder Grünfläche"),
    SPORT("Sport und Freizeit"),
    CULTURE("Kultur und Sehenswürdigkeit"),
    WORK("Büro oder Behörde"),
    POI("Ort"),
    COORDINATE("Koordinate"),
}

/** The category shown for [location]: what its source reported, otherwise derived from kind and name. */
fun placeCategory(location: Location): PlaceCategory {
    if (location.favoriteLabel != null) return PlaceCategory.FAVORITE
    location.category?.let { return it }
    return when (location.kind) {
        LocationKind.STOP -> stopCategoryFromName(location.name)
        LocationKind.ADDRESS -> if (location.name.substringBefore(',').any(Char::isDigit)) PlaceCategory.ADDRESS else PlaceCategory.STREET
        LocationKind.POI -> PlaceCategory.POI
        LocationKind.COORDINATE -> PlaceCategory.COORDINATE
    }
}

private val stationWords = Regex("(?i)(^|[\\s,(/-])(hbf|hauptbahnhof|bf|bhf|bahnhof|stadtbahnhof|s-bahnhof)([\\s,)./-]|$)")
private val busStationWords = Regex("(?i)(^|[\\s,(/-])(zob|busbahnhof|omnibusbahnhof)([\\s,)./-]|$)")
private val airportWords = Regex("(?i)(flughafen|airport)")

/** Names are the only hint the local directory has. Bus stations are checked before railway stations. */
internal fun stopCategoryFromName(name: String): PlaceCategory = when {
    airportWords.containsMatchIn(name) -> PlaceCategory.AIRPORT
    busStationWords.containsMatchIn(name) -> PlaceCategory.BUS
    stationWords.containsMatchIn(name) -> PlaceCategory.STATION
    else -> PlaceCategory.STOP
}

/**
 * EFA transport modes of a stop (0 train, 1 S-Bahn, 2 U-Bahn, 3 Stadtbahn, 4 tram, 5–7 bus, 9 ferry,
 * 10–11 on-demand). Rail wins over urban rail, urban rail over bus; without modes the name decides.
 */
internal fun efaStopCategory(modes: Collection<Int>, name: String): PlaceCategory {
    val named = stopCategoryFromName(name)
    if (named == PlaceCategory.AIRPORT) return named
    return when {
        modes.any { it == 0 || it == 1 } -> PlaceCategory.STATION
        modes.any { it == 2 } -> PlaceCategory.SUBWAY
        modes.any { it == 3 || it == 4 } -> PlaceCategory.TRAM
        modes.any { it in 5..7 || it in 10..11 } -> if (named == PlaceCategory.STATION) named else PlaceCategory.BUS
        modes.any { it == 9 } -> PlaceCategory.FERRY
        else -> named
    }
}

/** EFA object types of the stop finder ("anyType"): street, house, locality, point of interest. */
internal fun efaObjectCategory(type: String?): PlaceCategory? = when (type?.lowercase(Locale.ROOT)) {
    "singlehouse", "address" -> PlaceCategory.ADDRESS
    "street" -> PlaceCategory.STREET
    "loc", "locality", "suburb", "place" -> PlaceCategory.LOCALITY
    "poi", "poihierarchy" -> PlaceCategory.POI
    else -> null
}

/** OpenStreetMap key/value pairs as reported by Photon. Unknown values stay a plain place. */
internal fun osmCategory(key: String?, value: String?, hasHouseNumber: Boolean, hasStreet: Boolean): PlaceCategory {
    val k = key?.lowercase(Locale.ROOT).orEmpty(); val v = value?.lowercase(Locale.ROOT).orEmpty()
    return when {
        k == "railway" && v in setOf("station", "halt") -> PlaceCategory.STATION
        k == "railway" && v == "subway_entrance" -> PlaceCategory.SUBWAY
        k == "railway" && v == "tram_stop" -> PlaceCategory.TRAM
        k == "highway" && v == "bus_stop" -> PlaceCategory.BUS
        k == "amenity" && v == "bus_station" -> PlaceCategory.BUS
        k == "amenity" && v == "ferry_terminal" -> PlaceCategory.FERRY
        k == "public_transport" && v == "station" -> PlaceCategory.STATION
        k == "public_transport" -> PlaceCategory.STOP
        k == "aeroway" && v in setOf("aerodrome", "terminal", "airport") -> PlaceCategory.AIRPORT
        k == "amenity" && v in setOf("restaurant", "fast_food", "food_court", "pub", "bar", "biergarten", "ice_cream") -> PlaceCategory.FOOD
        k == "amenity" && v == "cafe" -> PlaceCategory.CAFE
        k == "shop" || (k == "amenity" && v == "marketplace") -> PlaceCategory.SHOP
        k == "amenity" && v in setOf("hospital", "clinic", "doctors", "dentist", "pharmacy") -> PlaceCategory.HEALTH
        k == "healthcare" -> PlaceCategory.HEALTH
        k == "amenity" && v in setOf("school", "university", "college", "kindergarten", "library") -> PlaceCategory.EDUCATION
        k == "leisure" && v in setOf("park", "garden", "nature_reserve", "playground") -> PlaceCategory.PARK
        k in setOf("natural", "landuse") && v in setOf("wood", "forest", "grass", "meadow", "recreation_ground") -> PlaceCategory.PARK
        k == "leisure" -> PlaceCategory.SPORT
        k == "tourism" && v in setOf("museum", "attraction", "artwork", "gallery", "zoo", "theme_park", "viewpoint") -> PlaceCategory.CULTURE
        k == "historic" -> PlaceCategory.CULTURE
        k == "amenity" && v in setOf("theatre", "cinema", "arts_centre", "townhall", "place_of_worship", "community_centre") -> PlaceCategory.CULTURE
        k == "office" || (k == "amenity" && v in setOf("police", "courthouse", "post_office")) -> PlaceCategory.WORK
        k == "place" && v in setOf("house", "building") -> PlaceCategory.ADDRESS
        k == "place" || k == "boundary" -> PlaceCategory.LOCALITY
        k == "building" -> if (hasHouseNumber) PlaceCategory.ADDRESS else PlaceCategory.POI
        k == "highway" -> if (hasHouseNumber) PlaceCategory.ADDRESS else PlaceCategory.STREET
        hasHouseNumber -> PlaceCategory.ADDRESS
        hasStreet && k.isEmpty() -> PlaceCategory.STREET
        else -> PlaceCategory.POI
    }
}

/** transport.rest-style products of a stop or station. */
internal fun restStopCategory(products: Map<String, Boolean>, name: String): PlaceCategory {
    fun has(vararg keys: String) = keys.any { products[it] == true }
    return when {
        has("nationalExpress", "national", "regionalExpress", "regional", "suburban", "express") -> PlaceCategory.STATION
        has("subway") -> PlaceCategory.SUBWAY
        has("tram") -> PlaceCategory.TRAM
        has("bus") -> PlaceCategory.BUS
        has("ferry") -> PlaceCategory.FERRY
        else -> stopCategoryFromName(name)
    }
}
