// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.localization.tr

import dev.ambivalenton.openv.data.model.*
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Photon supports autocomplete; the public Nominatim endpoint intentionally is not used. */
class GeocodingRepository(private val network: NetworkClient) {
    private val mutex = Mutex()
    private var lastRequest = 0L
    private val cache =
        object : LinkedHashMap<String, Pair<Long, List<Location>>>(32, .75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Pair<Long, List<Location>>>?
            ) = size > 60
        }

    suspend fun search(query:String,bias:Location?,endpoint:String):List<Location> {
        parseCoordinateInput(query)?.let {return listOf(it)}
        val input=query.trim();require(input.length in 2..160);val base=httpsBase(endpoint)
        // Preserve the typed spelling on the first request. Try a normalized spelling once if needed.
        val variants=photonSpellings(input)
        val key=listOf(base.toString(),dev.ambivalenton.openv.localization.serviceLanguage(),"DE-v3",variants.joinToString("/") {searchText(it)},bias?.latitude?.let {(it*100).toInt()},bias?.longitude?.let {(it*100).toInt()}).joinToString("|")
        return mutex.withLock {
            cache[key]?.takeIf {System.currentTimeMillis()-it.first in 0 until 10*60_000}?.let {return@withLock it.second}
            suspend fun request(text:String):List<PhotonRow> {
                val wait=1000-(android.os.SystemClock.elapsedRealtime()-lastRequest);if(wait>0)delay(wait)
                lastRequest=android.os.SystemClock.elapsedRealtime()
                val url=base.newBuilder().addPathSegment("api").addQueryParameter("q",text).addQueryParameter("countrycode","DE")
                    .addQueryParameter("limit","10").addQueryParameter("lang",dev.ambivalenton.openv.localization.serviceLanguage()).apply {
                        if(bias?.hasCoordinates==true&&inGermanSearchScope(bias)){addQueryParameter("lat",bias.latitude.toString());addQueryParameter("lon",bias.longitude.toString())}
                    }.build()
                return network.get(url).field("features").elements().filter(::isGermanPhotonFeature).mapNotNull(::parsePhotonRow)
                    .filter {inGermanSearchScope(it.location)}.distinctBy {placeIdentity(it.location)}
            }
            // With a house number the number itself is checked against Photon's house number field
            // ("12" also finds "12a" and the range "10-14"); words like "O2" or "2.0" in a shop name
            // no longer count as the house number (build 67, tested with live Photon answers).
            val house=splitHouseNumber(input)
            fun accepted(rows:List<PhotonRow>)=if(house==null) rows.filter {placeMatch(input,it.location)!=null}
                else rows.filter {houseNumberMatches(house.second,it.houseNumber) && placeMatch(house.first,it.location)!=null}
            var rows=emptyList<Location>()
            for(variant in variants) {
                rows=accepted(request(variant)).map {it.location}
                if(rows.isNotEmpty())break
            }
            // The map data does not know every house number; Photon then answers with the street only
            // (or with the number in other towns). Instead of "nothing found" the street itself is
            // offered, clearly marked: it is a real place close to the address.
            if(rows.isEmpty() && house!=null) {
                val (street,number)=house
                for(variant in photonSpellings(typedWithoutHouseNumber(input) ?: street)) {
                    rows=request(variant).filter {it.houseNumber==null && placeMatch(street,it.location)!=null}.take(5).map {row->row.location.copy(
                        searchTerms=listOfNotNull(row.location.name,row.location.city,number).joinToString(" "),
                        note=tr("Hausnummer %1\$s nicht im Kartenmaterial – ohne Hausnummer",number))}
                    if(rows.isNotEmpty())break
                }
            }
            rows.also {cache[key]=System.currentTimeMillis() to it}
        }
    }
}

fun parseCoordinateInput(input: String): Location? {
    val match =
        Regex("^\\s*(-?\\d{1,2}(?:\\.\\d+)?)\\s*[,;]\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$")
            .matchEntire(input) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lon = match.groupValues[2].toDoubleOrNull() ?: return null
    return Location(
            String.format(Locale.ROOT, "%.6f, %.6f", lat, lon),
            latitude = lat,
            longitude = lon,
            kind = LocationKind.COORDINATE,
        )
        .takeIf { it.hasCoordinates }
}

/** A Photon result with its own house number field (null for streets, places and most POIs). */
internal data class PhotonRow(val location: Location, val houseNumber: String?)

internal fun parsePhotonRow(feature: JsonElement): PhotonRow? =
    parsePhotonLocation(feature)?.let { PhotonRow(it, feature.field("properties").text("housenumber")?.takeIf { n -> n.isNotBlank() }) }

/** "12" = "12", "12a" or a range like "10-14"/"10–14a"; "12a" needs "12a" (spaces and case ignored). */
internal fun houseNumberMatches(wanted: String, actual: String?): Boolean {
    if (actual == null) return false
    val w = wanted.lowercase(Locale.ROOT).filterNot { it.isWhitespace() }
    val a = actual.lowercase(Locale.ROOT).filterNot { it.isWhitespace() }
    if (a == w) return true
    val number = w.toIntOrNull() ?: return false
    if (a.length == w.length + 1 && a.startsWith(w) && a.last().isLetter()) return true
    val range = Regex("^(\\d+)[a-z]?[-–/](\\d+)[a-z]?$").matchEntire(a) ?: return false
    val from = range.groupValues[1].toInt(); val to = range.groupValues[2].toInt()
    return from <= to && number in from..to
}

internal fun parsePhotonLocation(feature: JsonElement): Location? {
    val coordinates = feature.field("geometry").field("coordinates") as? JsonArray ?: return null
    val lon = coordinates.getOrNull(0)?.text()?.toDoubleOrNull() ?: return null
    val lat = coordinates.getOrNull(1)?.text()?.toDoubleOrNull() ?: return null
    val p = feature.field("properties")
    val street = listOfNotNull(p.text("street"), p.text("housenumber")).joinToString(" ")
    val city =
        listOfNotNull(p.text("postcode"), p.text("city") ?: p.text("town") ?: p.text("village"))
            .joinToString(" ")
    val named = p.text("name")
    val title =
        (listOfNotNull(
                named,
                street.takeIf { it.isNotBlank() && it != named },
                city.takeIf { it.isNotBlank() && it != named },
            ))
            .distinct()
            .joinToString(", ")
    val kind =
        if (
            p.text("osm_key") in setOf("railway", "public_transport", "highway") &&
                p.text("osm_value") in
                    setOf("station", "stop_position", "bus_stop", "tram_stop", "halt")
        )
            LocationKind.STOP
        else if (street.isNotBlank()) LocationKind.ADDRESS else LocationKind.POI
    // OSM node IDs must never be treated as provider stop IDs.
    return Location(
            title.ifBlank { p.text("country") ?: tr("Ort") },
            latitude = lat,
            longitude = lon,
            provider = "OSM",
            kind = kind,
            detail = tr("OpenStreetMap · Photon"),
            city = p.text("city") ?: p.text("town") ?: p.text("municipality"),
            category = osmCategory(p.text("osm_key"), p.text("osm_value"), p.text("housenumber") != null,
                p.text("street") != null).let { if (it == PlaceCategory.POI && p.text("type") in setOf("city", "district", "locality")) PlaceCategory.LOCALITY else it },
        )
        .takeIf { it.hasCoordinates }
}

internal fun isGermanPhotonFeature(feature:JsonElement):Boolean {
    val p=feature.field("properties");val code=p.text("countrycode")
    if(!code.isNullOrBlank())return code.equals("DE",ignoreCase=true)
    return p.text("country")?.lowercase(Locale.ROOT) in setOf("germany","deutschland")
}
