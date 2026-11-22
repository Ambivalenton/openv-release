// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.repository

import dev.ambivalenton.openv.localization.tr

import dev.ambivalenton.openv.data.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.*
import okhttp3.HttpUrl

/** VRR's public EFA JSON service. No embedded secrets and no synthetic fallback. */
class EfaTransitRepository(
    private val network: NetworkClient,
    private val base: HttpUrl = httpsBase(TimetableProfile.endpoint),
) : TransitRepository {
    override val name = tr(TimetableProfile.name)

    private suspend fun request(endpoint: String, args: Map<String, String>): JsonElement =
        network.get(
            base
                .newBuilder()
                .addPathSegment(endpoint)
                .apply {
                    addQueryParameter("outputFormat", "JSON")
                    addQueryParameter("language", dev.ambivalenton.openv.localization.serviceLanguage())
                    addQueryParameter("coordOutputFormat", "WGS84[dd.ddddd]")
                    addQueryParameter("stateless", "1")
                    args.forEach { (k, v) -> addQueryParameter(k, v) }
                }
                .build()
        )

    override suspend fun locations(query: String): List<Location> {
        val data =
            request(
                "XML_STOPFINDER_REQUEST",
                mapOf(
                    "type_sf" to "stop",
                    "name_sf" to query.trim().take(160),
                    "anyObjFilter_sf" to "2",
                    "anyMaxSizeHitList" to "8",
                    "reducedAnyTooManyObjFilter_sf" to "2",
                ),
            )
        val points = data.field("stopFinder").field("points")
        return (if (points is JsonObject && points["point"] != null) points["point"] else points)
            .elements()
            .mapNotNull { efaLocation(it, searchResult = true) }
            .distinctBy { it.id }
            .take(8)
    }

    /**
     * Fallback for addresses: the same EFA stop finder, restricted to streets and houses
     * (anyObjFilter 4 + 8). Its address directory reaches well beyond NRW. Only the typed text is sent.
     */
    override suspend fun addresses(query: String): List<Location> {
        val input = query.trim().take(160)
        if (input.length < 3) return emptyList()
        val data =
            request(
                "XML_STOPFINDER_REQUEST",
                mapOf(
                    "type_sf" to "any",
                    "name_sf" to input,
                    "anyObjFilter_sf" to "12",
                    "anyMaxSizeHitList" to "8",
                ),
            )
        val points = data.field("stopFinder").field("points")
        val rows = (if (points is JsonObject && points["point"] != null) points["point"] else points)
            .elements()
            .mapNotNull(::efaAddressRow)
        return acceptedAddressRows(input, rows)
    }

    override suspend fun nearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): List<Location> {
        require(
            latitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude.isFinite() &&
                longitude in -180.0..180.0
        )
        val radius = radiusMeters.coerceIn(50, 5000)
        val data =
            request(
                "XML_COORD_REQUEST",
                mapOf(
                    "coord" to coordinate(latitude, longitude),
                    "inclFilter" to "1",
                    "type_1" to "STOP",
                    "radius_1" to "$radius",
                    "max" to "200",
                ),
            )
        return data
            .field("pins")
            .elements()
            .mapNotNull(::efaLocation)
            .filter {
                it.hasCoordinates &&
                    distanceMeters(latitude, longitude, it.latitude!!, it.longitude!!) <= radius
            }
            .distinctBy { it.id }
            .sortedBy { distanceMeters(latitude, longitude, it.latitude!!, it.longitude!!) }
    }

    override suspend fun resolveStop(place: Location): Location {
        if (place.kind == LocationKind.STOP && place.provider == "VRR" && place.id != null)
            return place
        return super.resolveStop(place.copy(id = null, kind = LocationKind.COORDINATE))
    }

    override val supportsRouteFocus: Boolean get() = true

    override suspend fun journeyPage(query: JourneyQuery, cursor: String?): JourneyPage {
        require(query.products.isNotEmpty()) { tr("Mindestens ein Verkehrsmittel auswählen.") }
        val whenLocal = Instant.ofEpochMilli(query.time).atZone(efaZone)
        val args =
            mutableMapOf(
                "itdDate" to whenLocal.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                "itdTime" to whenLocal.format(DateTimeFormatter.ofPattern("HHmm")),
                "itdTripDateTimeDepArr" to if (query.timeMode == TimeMode.ARRIVAL) "arr" else "dep",
                "useRealtime" to "1",
                "calcOneDirection" to "1",
                "calcNumberOfTrips" to query.pageSize.coerceIn(1,12).toString(),
                "ptOptionsActive" to "1",
                "itOptionsActive" to if(query.ignoreWalking) "0" else "1",
                "changeSpeed" to if(query.ignoreWalking) "0" else when(query.walkingSpeed) {WalkingSpeed.RELAXED->"slow";WalkingSpeed.AVERAGE->"normal";WalkingSpeed.FAST->"fast"},
                "locationServerActive" to "1",
                // A strict request ([JourneyQuery.stopOnly]) walks to no other stop: the chosen
                // stop itself, or only the nearest stops of an address. The repository asks again without
                // it when nothing leaves within two hours (a moved stop is served by its replacement).
                "useProxFootSearch" to if (query.stopOnly) "0" else "1",
                "useProxFootSearchOrigin" to if (query.stopOnly) "0" else "1",
                "useProxFootSearchDestination" to if (query.stopOnly) "0" else "1",
                "trITMOTvalue100" to "15",
                "includedMeans" to "checkbox",
            )
        // The optimisation goal of this request (verified live at the VRR: LEASTWALKING
        // found a connection without the walk that LEASTTIME proposed).
        args["routeType"] = when (query.routeFocus) {
            RouteFocus.TIME -> "LEASTTIME"
            RouteFocus.FEWER_CHANGES -> "LEASTINTERCHANGE"
            RouteFocus.LESS_WALKING -> "LEASTWALKING"
        }
        args.putAll(efaLocationArgs("origin", query.origin))
        args.putAll(efaLocationArgs("destination", query.destination))
        efaProductArgs(query.products).forEach { (key, value) -> args[key] = value }
        var data = request("XML_TRIP_REQUEST2", args)
        // A directory stop the EFA does not know ("stop invalid") is asked for once more by its coordinates.
        val unknown = listOf("origin" to query.origin, "destination" to query.destination)
            .filter { (role, place) -> efaGlobalStopId(place) != null && !efaPointResolved(data, role) && place.hasCoordinates }
        if (unknown.isNotEmpty()) {
            unknown.forEach { (role, place) -> args.putAll(efaLocationArgs(role, place, globalIds = false)) }
            data = request("XML_TRIP_REQUEST2", args)
        }
        val messages =
            data.field("itdMessageList").elements().map { item ->
                item.field("message").elements().associate {
                    it.text("name").orEmpty() to it.text("value").orEmpty()
                }
            }
        if (
            messages.any {
                it["code"] == "-4007" || it["error"].orEmpty().contains("origin too close", true)
            }
        )
            throw AlreadyAtDestinationException()
        val trips = data.field("trips").elements()
        val parsed =
            trips.mapNotNull { parseEfaTrip(it) }.distinctBy { it.id }.sortedBy { it.startTime }
        // Keep unselected products as pagination anchors. The shared window filters them;
        // throwing them away here would turn a real page into an apparent empty interval.
        if (trips.isNotEmpty() && parsed.isEmpty())
            throw TransitException(
                tr("Die Verbindung konnte nicht vollständig gelesen werden. Bitte erneut versuchen."),
                FailureKind.INVALID_RESPONSE,
            )
        val error =
            data.field("itdMessageList").elements().firstOrNull { it.text("type") == "error" }
        if (parsed.isEmpty() && error != null)
            throw TransitException(
                tr("EFA hat keine Verbindung gefunden. Orte, Zeit und Verkehrsmittel prüfen."),
                // H06: a start or destination the service could not identify is evidence of missing
                // coverage; with both places identified, the answer is a valid "no connection".
                if (efaUnidentified(data.field("origin")) || efaUnidentified(data.field("destination")))
                    FailureKind.NO_COVERAGE else FailureKind.NO_CONNECTION,
            )
        return JourneyPage(parsed)
    }

    override suspend fun departures(stop: Location): List<Departure> = departures(stop, 30)

    override suspend fun departures(stop: Location, limit: Int): List<Departure> {
        val resolved = resolveStop(stop)
        val data =
            request(
                "XML_DM_REQUEST",
                mapOf(
                    "type_dm" to "stop",
                    "name_dm" to resolved.id!!,
                    "useRealtime" to "1",
                    "mode" to "direct",
                    "limit" to limit.coerceIn(1, 30).toString(),
                    "deleteAssignedStops_dm" to "0",
                ),
            )
        return data
            .field("departureList")
            .elements()
            .mapNotNull { parseEfaDeparture(it, resolved) }
            .distinctBy { it.id }
            .sortedBy { it.time }
    }

    // EFA has no stable refresh token here; UI repeats the original JourneyQuery instead.
    override suspend fun refresh(trip: Trip): Trip =
        throw TransitException(tr("Verbindung über die Suche aktualisieren."))
}

internal val efaZone: ZoneId = ZoneId.of("Europe/Berlin")

internal fun coordinate(lat: Double, lon: Double): String =
    String.format(Locale.ROOT, "%.7f:%.7f:WGS84[dd.ddddd]", lon, lat)

/**
 * The nationwide stop ID (DHID, e.g. "de:05916:7501") of a stop from the local directory,
 * reduced to the stop itself (no platform), as the VRR EFA accepts it. Sent as a stop, the journey
 * starts at the stop; sent as coordinates, the EFA adds a walk from a nearby address.
 */
internal fun efaGlobalStopId(location: Location): String? {
    if (location.kind != LocationKind.STOP || location.isDevicePosition) return null
    val parts = (location.dhid ?: location.id)?.split(':') ?: return null
    if (parts.size < 3 || !Regex("[a-z]{2}").matches(parts[0]) || !Regex("\\d{5}").matches(parts[1]) || parts[2].isBlank()) return null
    return parts.take(3).joinToString(":")
}

/** Whether the EFA resolved the [role] ("origin", "destination") of a trip request to a point. */
internal fun efaPointResolved(data: JsonElement?, role: String): Boolean {
    val points = data.field(role).field("points")
    return points != null && points !is kotlinx.serialization.json.JsonNull
}

internal fun efaLocationArgs(role: String, location: Location, globalIds: Boolean = true): Map<String, String> =
    when {
        location.kind == LocationKind.STOP && location.provider == "VRR" && location.id != null ->
            mapOf("type_$role" to "stop", "name_$role" to location.id)
        globalIds && efaGlobalStopId(location) != null ->
            mapOf("type_$role" to "stop", "name_$role" to efaGlobalStopId(location)!!)
        location.hasCoordinates ->
            mapOf(
                "type_$role" to "coord",
                "name_$role" to coordinate(location.latitude!!, location.longitude!!),
            )
        else -> throw TransitException(tr("Ort ohne Koordinaten. Bitte neu aus der Suche auswählen."))
    }

internal fun efaProductArgs(products: Set<LineType>): Map<String, String> = buildMap {
    val modes = mutableSetOf<Int>()
    if (LineType.REGIONAL in products || LineType.ICE in products) modes += 0
    if (LineType.SUBURBAN in products) modes += 1
    if (LineType.SUBWAY in products) modes += 2
    if (LineType.TRAM in products) modes += setOf(3, 4)
    if (LineType.BUS in products) modes += setOf(5, 6, 7)
    if (LineType.FERRY in products) modes += 9
    if (LineType.OTHER in products) modes += setOf(8, 10, 11)
    modes.forEach { put("inclMOT_$it", "on") }
    put("lineRestriction", if (LineType.ICE in products) "400" else "403")
}

/**
 * A stop of the EFA. Only search results ([searchResult]) carry the kind of place for the list symbol;
 * legs and departure boards stay exactly as stored, so comparisons with saved journeys are unaffected.
 */
internal fun efaLocation(point: JsonElement, searchResult: Boolean = false): Location? {
    val ref = point.field("ref")
    val coord = (ref.text("coords") ?: point.text("coords"))?.split(',')
    val lat = coord?.getOrNull(1)?.toDoubleOrNull() ?: point.text("y")?.toDoubleOrNull()
    val lon = coord?.getOrNull(0)?.toDoubleOrNull() ?: point.text("x")?.toDoubleOrNull()
    val attrs = point.field("attrs").elements()
    val withPlace = attrs.firstOrNull { it.text("name") == "STOP_NAME_WITH_PLACE" }.text("value")
    val place = point.text("locality") ?: ref.text("place") ?: point.text("place")
    val raw = point.text("nameWithPlace") ?: point.text("name") ?: point.text("desc") ?: return null
    val title =
        withPlace ?: if (place != null && !raw.startsWith(place, true)) "$place, $raw" else raw
    return Location(
            title,
            point.text("stateless") ?: ref.text("id") ?: point.text("id"),
            lat,
            lon,
            "VRR",
            LocationKind.STOP,
            city = place?.takeIf { it.isNotBlank() },
            category = if (searchResult) efaStopCategory(efaModes(point), title) else null,
        )
        .takeIf { it.routable }
}

/** Transport modes of an EFA point: "0,1,5" as text, a list, or rapidJSON product classes. */
internal fun efaModes(point: JsonElement): Set<Int> {
    fun numbers(value: JsonElement?, depth: Int = 0): List<Int> = when {
        depth > 2 -> emptyList()
        value is JsonPrimitive -> value.text()?.split(Regex("[^0-9]+"))?.mapNotNull { it.toIntOrNull() }.orEmpty()
        value is JsonArray -> value.flatMap { numbers(it, depth + 1) }
        value is JsonObject -> numbers(value["mode"], depth + 1) + numbers(value["type"], depth + 1)
        else -> emptyList()
    }
    return (numbers(point.field("modes")) + numbers(point.field("productClasses")) + numbers(point.field("ref").field("modes")))
        .filter { it in 0..20 }.toSet()
}

/** EFA streets and houses use coordinates for routing. Their IDs are not unique. */
internal fun efaAddressRow(point: JsonElement): PhotonRow? {
    val type = point.text("anyType") ?: return null
    if (type !in setOf("singlehouse", "street", "address")) return null
    val ref = point.field("ref")
    val coord = (ref.text("coords") ?: point.text("coords"))?.split(',')
    val lat = coord?.getOrNull(1)?.toDoubleOrNull() ?: return null
    val lon = coord.getOrNull(0)?.toDoubleOrNull() ?: return null
    val place = (ref.text("place") ?: point.text("locality"))?.trim()?.takeIf { it.isNotBlank() }
    val street = (point.text("street") ?: point.text("object"))?.trim()?.takeIf { it.isNotBlank() }
    val number = point.text("buildingNumber")?.trim()?.takeIf { it.isNotBlank() }
    val postcode = point.text("postcode")?.trim()?.takeIf { it.isNotBlank() }
    val title = if (street != null)
        listOfNotNull(listOfNotNull(street, number).joinToString(" "), listOfNotNull(postcode, place).joinToString(" ").ifBlank { null }).joinToString(", ")
    else point.text("name")?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val location = Location(title, latitude = lat, longitude = lon, provider = "VRR", kind = LocationKind.ADDRESS,
        detail = tr("VRR · Adresse"), city = place, category = efaObjectCategory(type))
    return PhotonRow(location, number).takeIf { location.routable && inGermanSearchScope(location) }
}

/**
 * Same rules as the Photon search: with a house number the number must match the house number field
 * ("12" finds "12", "12a", "10-14", never "120"). A street without the wanted number stays selectable,
 * clearly marked; the ranking later drops it when a real address was found.
 */
internal fun acceptedAddressRows(query: String, rows: List<PhotonRow>): List<Location> {
    val house = splitHouseNumber(query)
    if (house == null) return rows.map { it.location }.filter { placeMatch(query, it) != null }.distinctBy(::placeIdentity)
    val (street, number) = house
    val exact = rows.filter { it.houseNumber != null && houseNumberMatches(number, it.houseNumber) && placeMatch(street, it.location) != null }
        .map { it.location }
    if (exact.isNotEmpty()) return exact.distinctBy(::placeIdentity)
    return rows.filter { it.houseNumber == null && placeMatch(street, it.location) != null }.take(5).map { row ->
        row.location.copy(searchTerms = listOfNotNull(row.location.name, row.location.city, number).joinToString(" "),
            note = tr("Hausnummer %1\$s nicht im Kartenmaterial – ohne Hausnummer", number))
    }.distinctBy(::placeIdentity)
}

/** Handles EFA's numeric date objects, compact stamps and service-day hours >= 24. */
internal fun efaDate(date: String?, clock: String?): Long? =
    runCatching {
            if (date == null || clock == null) return null
            val d =
                if ('.' in date) LocalDate.parse(date, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                else LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE)
            val digits = clock.replace(":", "")
            val hours = digits.take(2).toLong()
            val minutes = digits.drop(2).take(2).toLong()
            val seconds = digits.drop(4).take(2).toLongOrNull() ?: 0
            require(hours in 0..47 && minutes in 0..59 && seconds in 0..59)
            d.atStartOfDay()
                .plusHours(hours)
                .plusMinutes(minutes)
                .plusSeconds(seconds)
                .atZone(efaZone)
                .toInstant()
                .toEpochMilli()
        }
        .getOrNull()

internal fun efaObjectDate(o: JsonElement?): Long? {
    val year = o.number("year") ?: return null
    val month = o.number("month") ?: return null
    val day = o.number("day") ?: return null
    val hour = o.number("hour") ?: return null
    val minute = o.number("minute") ?: return null
    return efaDate(
        "%04d%02d%02d".format(Locale.ROOT, year, month, day),
        "%02d%02d".format(Locale.ROOT, hour, minute),
    )
}

internal fun efaType(mode: JsonElement?): LineType {
    val number = mode.text("symbol") ?: mode.text("number") ?: ""
    val description =
        (mode.text("product").orEmpty() +
                " " +
                mode.text("name").orEmpty() +
                " " +
                mode.text("trainType").orEmpty())
            .uppercase(Locale.ROOT)
    val type = mode.number("type") ?: mode.number("motType")
    return when {
        type == 100 || type == 99 || description.contains("FUSS") || description.contains("FUß") ->
            LineType.WALK
        number.matches(Regex("(ICE|IC|EC|RJ|TGV|FLX|FLIXTRAIN).*", RegexOption.IGNORE_CASE)) ||
            type in setOf(14, 15, 16) -> LineType.ICE
        number.matches(Regex("S\\s*\\d.*")) || type == 1 -> LineType.SUBURBAN
        number.matches(Regex("U\\s*\\d.*")) || type == 2 -> LineType.SUBWAY
        number.matches(Regex("(RE|RB|IRE|RRX)\\s*\\d.*")) || type in setOf(0, 13, 18, 19) ->
            LineType.REGIONAL
        type in setOf(3, 4) -> LineType.TRAM
        type in setOf(5, 6, 7, 17) -> LineType.BUS
        type == 9 -> LineType.FERRY
        else -> LineType.OTHER
    }
}

/** H06: EFA lists the places it identified under `points`; none at all means it does not know the place. */
internal fun efaUnidentified(odv: JsonElement?): Boolean {
    if (odv !is JsonObject) return false
    val points = odv["points"]
    val listed = (if (points is JsonObject && points["point"] != null) points["point"] else points).elements()
    return listed.none { it is JsonObject && (it.text("name") != null || it.field("ref") != null) }
}

internal fun efaNotices(value: JsonElement?): List<String> {
    fun clean(s: String) =
        s.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
    val found = mutableListOf<String>()
    fun visit(v: JsonElement?, depth: Int) {
        if (depth > 6) return
        when (v) {
            is JsonObject ->
                v.forEach { (k, item) ->
                    if (k in setOf("content", "subject", "additionalText", "infoLinkText"))
                        item.text()?.let { if (it.isNotBlank()) found += clean(it) }
                    else if (item is JsonObject || item is JsonArray) visit(item, depth + 1)
                }
            is JsonArray -> v.forEach { visit(it, depth + 1) }
            else -> Unit
        }
    }
    visit(value, 0)
    return found.filter { it.isNotBlank() }.distinct().take(12)
}

internal fun parseEfaTrip(raw: JsonElement, receivedAt: Long = System.currentTimeMillis()): Trip? {
    val trip = raw.field("trip")?.takeIf { it is JsonObject } ?: raw
    val legs = trip.field("legs").elements()
    if (legs.isEmpty()) return null
    val segments =
        legs.mapIndexed { i, leg ->
            val points = leg.field("points").elements()
            val first = points.firstOrNull() ?: return null
            val last = points.lastOrNull() ?: return null
            val origin = efaLocation(first) ?: return null
            val destination = efaLocation(last) ?: return null
            fun pointTime(p: JsonElement, realtime: Boolean): Long? {
                val dt = p.field("dateTime")
                return if (realtime)
                    efaDate(dt.text("rtDate"), dt.text("rtTimeSec") ?: dt.text("rtTime"))
                else efaDate(dt.text("date"), dt.text("timeSec") ?: dt.text("time"))
            }
            val plannedStart = pointTime(first, false) ?: return null
            val plannedEnd = pointTime(last, false) ?: return null
            val monitored = leg.text("realtimeStatus")?.split(Regex("[^A-Z_]+"))?.contains("MONITORED") == true
            val mode = leg.field("mode")
            val type = efaType(mode)
            val controlled = leg.field("controlled")
            val cancelled =
                leg.text("realtimeStatus")?.contains("CANCEL") == true ||
                    controlled.number("delayMinutes") == -9999
            // null = no forecast for this event. Missing realtime is not "0 minutes late".
            fun predicted(p: JsonElement, planned: Long, key: String): Long? {
                if (!monitored) return null
                pointTime(p, true)?.let { return it }
                val delay = controlled.number(key)?.takeIf { it in -180..1440 } ?: return null
                return planned + delay * 60_000L
            }
            val startForecast = predicted(first, plannedStart, "delayMinutes")
            val endForecast = predicted(last, plannedEnd, "delayMinutesArr")
            val start = startForecast ?: plannedStart
            // Without an arrival forecast, a known positive departure delay is carried forward as
            // a labelled estimate instead of silently showing the timetable arrival.
            val carried = startForecast?.let { it - plannedStart }?.takeIf { it > 0 && endForecast == null }
            val end = endForecast ?: (plannedEnd + (carried ?: 0L))
            if (end < start) return null
            var lastDelay = startForecast?.let { it - plannedStart }
            val stops =
                leg.field("stopSeq").elements().mapNotNull { stop ->
                    val loc = efaLocation(stop) ?: return@mapNotNull null
                    val ref = stop.field("ref")
                    fun seqTime(key: String): Long? {
                        val text = ref.text(key) ?: return null
                        return efaDate(text.substringBefore(' '), text.substringAfter(' '))
                    }
                    val plannedArrival = seqTime("arrDateTimeSec") ?: seqTime("arrDateTime")
                    val plannedDeparture = seqTime("depDateTimeSec") ?: seqTime("depDateTime")
                    // EFA reports call forecasts as minute delays with explicit validity flags.
                    fun forecast(planned: Long?, delayKey: String, validKey: String): Long? {
                        if (!monitored || planned == null || ref.text(validKey) != "1") return null
                        val minutes = ref.text(delayKey)?.toIntOrNull()?.takeIf { it in -180..1440 } ?: return null
                        return planned + minutes * 60_000L
                    }
                    val arrivalForecast = forecast(plannedArrival, "arrDelay", "arrValid")
                    val departureForecast = forecast(plannedDeparture, "depDelay", "depValid")
                    (departureForecast?.minus(plannedDeparture ?: 0) ?: arrivalForecast?.minus(plannedArrival ?: 0))?.let { lastDelay = it }
                    val carry = lastDelay?.takeIf { it > 0 } ?: 0L
                    Stopover(
                        loc,
                        arrivalForecast ?: plannedArrival?.plus(carry),
                        departureForecast ?: plannedDeparture?.plus(carry),
                        stop.text("platformName"),
                        plannedArrivalTime = plannedArrival,
                        plannedDepartureTime = plannedDeparture,
                        realtime = arrivalForecast != null || departureForecast != null,
                    )
                }
            val label =
                if (type == LineType.WALK) tr("Zu Fuß")
                else mode.text("symbol") ?: mode.text("number") ?: mode.text("name") ?: type.label
            TripSegment(
                "${origin.id}-${destination.id}-$plannedStart-$i",
                origin,
                destination,
                start,
                end,
                type,
                label,
                plannedStart,
                plannedEnd,
                DelayInfo(
                    startForecast?.let { ((it - plannedStart) / 1000).toInt() },
                    endForecast?.let { ((it - plannedEnd) / 1000).toInt() },
                    cancelled,
                ),
                // The current platform wins; the planned one is a fallback, never an invented value.
                first.text("platformName")?.takeIf { it.isNotBlank() } ?: first.text("plannedPlatformName")?.takeIf { it.isNotBlank() },
                last.text("platformName")?.takeIf { it.isNotBlank() } ?: last.text("plannedPlatformName")?.takeIf { it.isNotBlank() },
                mode.text("destination"),
                stops,
                efaNotices(leg.field("hints")) + efaNotices(leg.field("infos")),
                leg.number("distance") ?: leg.field("footpath").number("distance"),
                realtimeDeparture=startForecast != null,
                serviceId=mode.field("diva").let {diva->diva.text("tripCode")?.takeIf {it.isNotBlank() && it!="0"}?.let {code->
                    diva.text("stateless")?.takeIf {it.isNotBlank()}?.let {line->"VRR:$line:$code:${Instant.ofEpochMilli(plannedStart).atZone(efaZone).toLocalDate()}"}}},
                routeShape=efaJourneyShape(leg),
                departureSource=if (startForecast != null) TimeSource.PROGNOSIS else TimeSource.PLANNED,
                arrivalSource=when { endForecast != null -> TimeSource.PROGNOSIS; carried != null -> TimeSource.CARRIED; else -> TimeSource.PLANNED },
                realtimeReceivedAt=receivedAt.takeIf { startForecast != null || endForecast != null },
                forecastChangedAt=receivedAt.takeIf { startForecast != null || endForecast != null },
            )
        }
    // The alighting stop is part of the offer: two results may share boarding stop, line and time
    // but end at different stops without any footpath. Their IDs must not collide.
    val id = segments.joinToString("|") { "${it.origin.id}:${it.lineName}:${it.plannedStartTime}>${it.destination.id}" }
    return Trip(
        id,
        segments,
        tr(TimetableProfile.name),
        receivedAt,
        notices = efaNotices(trip.field("infos")),
    )
}

internal fun parseEfaDeparture(raw: JsonElement, fallback: Location): Departure? {
    val planned = efaObjectDate(raw.field("dateTime")) ?: return null
    val line = raw.field("servingLine") ?: return null
    val monitored = raw.text("realtimeTripStatus")?.contains("MONITORED") == true
    val delay = line.number("delay")?.takeIf { it in -180..1440 }
    val predicted =
        if (monitored) efaObjectDate(raw.field("realDateTime")) ?: planned + (delay ?: 0) * 60_000L
        else planned
    val cancelled =
        raw.text("realtimeTripStatus")?.contains("CANCEL") == true || line.number("delay") == -9999
    val name = line.text("symbol") ?: line.text("number") ?: line.text("name") ?: return null
    val stop =
        Location(
            raw.text("stopName") ?: fallback.name,
            raw.text("stopID") ?: fallback.id,
            raw.text("y")?.toDoubleOrNull() ?: fallback.latitude,
            raw.text("x")?.toDoubleOrNull() ?: fallback.longitude,
            "VRR",
            LocationKind.STOP,
        )
    return Departure(
        "${stop.id}-${line.text("stateless")}-$planned-${line.text("direction")}",
        stop,
        name,
        efaType(line),
        line.text("direction") ?: tr("Richtung unbekannt"),
        planned,
        predicted,
        DelayInfo(
            if (monitored) ((predicted - planned) / 1000).toInt() else null,
            cancelled = cancelled,
        ),
        raw.text("platformName"),
        efaNotices(line.field("hints")) + efaNotices(raw.field("tripInfos")),
    )
}
