// SPDX-FileCopyrightText: 2026 Ambivalenton
// SPDX-License-Identifier: GPL-3.0-or-later
// Modified for the Öpen-V 1.0 FOSS release (2026-10-27).

package dev.ambivalenton.openv.data.model

import dev.ambivalenton.openv.localization.tr

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class TargetLocation(
    val name: String,
    val id: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val provider: String? = null,
    val kind: LocationKind = if (id != null) LocationKind.STOP else LocationKind.COORDINATE,
    val detail: String? = null,
    val isDevicePosition: Boolean = false,
    // Structured municipality only; never inferred by splitting a stop name.
    val city: String? = null,
    // Canonical directory ID, kept separate from the selected live service's opaque ID.
    val dhid: String? = null,
    // Query-only aliases from the local index; never written into favourites/history or sent as IDs.
    @kotlinx.serialization.Transient val searchTerms: String? = null,
    // Search presentation only. Routing always uses the original place, never a pair's endpoints.
    @kotlinx.serialization.Transient val favoriteLabel: String? = null,
    // Search presentation only: why this result is an approximation (e.g. street without the house number).
    @kotlinx.serialization.Transient val note: String? = null,
    // The kind of place its source reported (symbol in the list).
    @kotlinx.serialization.Transient val category: PlaceCategory? = null,
) {
    val hasCoordinates: Boolean
        get() =
            latitude?.let { it.isFinite() && it in -90.0..90.0 } == true &&
                longitude?.let { it.isFinite() && it in -180.0..180.0 } == true

    val routable: Boolean
        get() = name.isNotBlank() && (hasCoordinates || !id.isNullOrBlank())
}

typealias Location = TargetLocation

@Serializable
enum class LocationKind {
    STOP,
    ADDRESS,
    POI,
    COORDINATE,
}

@Serializable
enum class LineType(private val labelKey: String, val argb: Long, val contentArgb: Long) {
    SUBURBAN("S-Bahn", 0xFF14733B, 0xFFFFFFFF),
    SUBWAY("U-Bahn", 0xFF145AA8, 0xFFFFFFFF),
    TRAM("Tram", 0xFF8646A1, 0xFFFFFFFF),
    BUS("Bus", 0xFFF5C542, 0xFF211B00),
    REGIONAL("Regio", 0xFF007A82, 0xFFFFFFFF),
    ICE("ICE/IC", 0xFFB3261E, 0xFFFFFFFF),
    FERRY("Fähre", 0xFF006A7D, 0xFFFFFFFF),
    WALK("Fußweg", 0xFF6B7280, 0xFFFFFFFF),
    OTHER("Sonstige", 0xFF575D69, 0xFFFFFFFF);

    val label: String get() = tr(labelKey)

}

@Serializable
data class DelayInfo(
    val departureSeconds: Int? = null,
    val arrivalSeconds: Int? = null,
    val cancelled: Boolean = false,
) {
    val minutes: Int?
        get() = departureSeconds?.let { kotlin.math.ceil(it / 60.0).toInt() }
}

/**
 * Origin of a displayed event time. UNKNOWN is used by journeys stored before build 65 and is
 * interpreted from the DelayInfo fields. CARRIED means: no forecast for this event, the known
 * delay of an earlier event of the same service was carried forward. LOCAL is an on-device
 * estimate from the passenger's own route progress. A confirmed actual time is not offered by
 * the current providers and therefore not modelled as a separate source.
 */
@Serializable
enum class TimeSource { UNKNOWN, PLANNED, PROGNOSIS, CARRIED, LOCAL }

@Serializable
data class Stopover(
    val location: Location,
    val arrivalTime: Long? = null,
    val departureTime: Long? = null,
    val platform: String? = null,
    val cancelled: Boolean = false,
    // Timetable values stay available separately; null for journeys stored before build 65.
    val plannedArrivalTime: Long? = null,
    val plannedDepartureTime: Long? = null,
    /** True only if the provider supplied a forecast for this call (not a carried delay). */
    val realtime: Boolean = false,
)

@Serializable
data class TripSegment(
    val id: String,
    val origin: Location,
    val destination: Location,
    // Epoch milliseconds: the bar uses predicted times; planned values remain available separately.
    val startTime: Long,
    val endTime: Long,
    val lineType: LineType,
    val lineName: String,
    val plannedStartTime: Long = startTime,
    val plannedEndTime: Long = endTime,
    val delay: DelayInfo = DelayInfo(),
    val departurePlatform: String? = null,
    val arrivalPlatform: String? = null,
    val direction: String? = null,
    val stops: List<Stopover> = emptyList(),
    val notices: List<String> = emptyList(),
    val distanceMeters: Int? = null,
    val realtimeDeparture:Boolean = false,
    val serviceId:String? = null,
    val routeShape:List<RoutePoint> = emptyList(),
    val walkingSpeedHint:WalkingSpeed = WalkingSpeed.AVERAGE,
    // Event provenance and freshness per leg (build 65). A successful partial refresh only marks
    // the legs it actually received; a new HTTP response alone does not prove a new forecast.
    val departureSource:TimeSource = TimeSource.UNKNOWN,
    val arrivalSource:TimeSource = TimeSource.UNKNOWN,
    /** Wall-clock receipt of this leg's realtime data; null if the provider sent none. */
    val realtimeReceivedAt:Long? = null,
    /** The provider's own timestamp of its realtime data, if stated (e.g. HAFAS realtimeDataUpdatedAt). */
    val realtimeGeneratedAt:Long? = null,
    /** Receipt time of the response in which this leg's forecast values last changed. */
    val forecastChangedAt:Long? = null,
) {
    val isWalk: Boolean
        get() = lineType == LineType.WALK

    val durationMinutes: Long
        get() = ((endTime - startTime).coerceAtLeast(0) + 59999) / 60000
}

@Serializable
data class Trip(
    val id: String,
    val segments: List<TripSegment>,
    val source: String,
    val fetchedAt: Long,
    val refreshToken: String? = null,
    val notices: List<String> = emptyList(),
    val waypoints: List<Location> = emptyList(),
    val waypointVisits: List<WaypointVisit> = emptyList(),
    val waypointLegs: List<WaypointLegReference> = emptyList(),
    val waypointDwellMinutes: Map<String,Float> = emptyMap(),
) {
    val startTime: Long
        get() = segments.minOfOrNull { it.startTime } ?: 0

    val endTime: Long
        get() = segments.maxOfOrNull { it.endTime } ?: startTime

    val totalDuration: Long
        get() = (endTime - startTime).coerceAtLeast(0)

    val transfers: Int
        get() = (segments.count { !it.isWalk } - 1).coerceAtLeast(0)

    val cancelled: Boolean
        get() = segments.any { it.delay.cancelled }
}

@Serializable
data class Departure(
    val id: String,
    val stop: Location,
    val lineName: String,
    val lineType: LineType,
    val destination: String,
    val plannedTime: Long,
    val time: Long,
    val delay: DelayInfo,
    val platform: String? = null,
    val notices: List<String> = emptyList(),
    val journey: Trip? = null,
    val journeyQuery: JourneyQuery? = null,
)

@Serializable
enum class TimeMode {
    DEPARTURE,
    ARRIVAL,
}

@Serializable
data class JourneyQuery(
    val origin: Location,
    val destination: Location,
    val time: Long = System.currentTimeMillis(),
    val timeMode: TimeMode = TimeMode.DEPARTURE,
    val products: Set<LineType> = localProducts,
    val waypoints: List<Location> = emptyList(),
    val waypointDwellMinutes: Map<String,Float> = emptyMap(),
    val walkingSpeed: WalkingSpeed = WalkingSpeed.AVERAGE,
    val ignoreWalking: Boolean = false,
    val transportFilter: Set<FavoriteTransport>? = null,
    val pageSize:Int=6,
    /** M13: never written while it has its default, so stored queries and cache keys stay as they were. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val waypointOrder: WaypointOrder = WaypointOrder.OPTIMIZED,
    /**
     * No walk to another stop at an explicit stop and, with
     * "So wenig wie möglich laufen", only the nearest stops of an address or position.
     */
    @kotlinx.serialization.Transient val stopOnly: Boolean = false,
    /**
     * What the provider optimises for. The preferences ask a second time
     * with [RouteFocus.FEWER_CHANGES] or [RouteFocus.LESS_WALKING] and weigh both answers together.
     */
    @kotlinx.serialization.Transient val routeFocus: RouteFocus = RouteFocus.TIME,
)

/** The optimisation goal of one provider request (EFA: `routeType`). */
enum class RouteFocus { TIME, FEWER_CHANGES, LESS_WALKING }

/** M13: several waypoints are either ordered along the direction of travel or kept as entered. */
@Serializable
enum class WaypointOrder { OPTIMIZED, MANUAL }

val selectableProducts = LineType.entries.filter { it != LineType.WALK }.toSet()
val localProducts = selectableProducts - LineType.ICE

fun clockTime(time: Long): String =
    Instant.ofEpochMilli(time)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))

fun newId(): String = UUID.randomUUID().toString()
